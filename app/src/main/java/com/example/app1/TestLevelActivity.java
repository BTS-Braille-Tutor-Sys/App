package com.example.app1;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class TestLevelActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isFirstLoad = true;
    private boolean isActivityVisible = false;

    // [추가됨] testAiBtn 변수
    private Button testIntroBtn, testBasicBtn, testStandardBtn, testAiBtn, btnExit;

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_level);

        testIntroBtn = findViewById(R.id.testIntroBtn);
        testBasicBtn = findViewById(R.id.testBasicBtn);
        testStandardBtn = findViewById(R.id.testStandardBtn);
        testAiBtn = findViewById(R.id.testAiBtn); // [추가됨] AI 시험 버튼 연결
        btnExit = findViewById(R.id.btnExit);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                isTtsReady = true;

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) { isTtsSpeaking = true; }
                    @Override
                    public void onDone(String utteranceId) {
                        isTtsSpeaking = false;
                        runOnUiThread(() -> startListeningVoice());
                    }
                    @Override
                    public void onError(String utteranceId) { isTtsSpeaking = false; }
                });

                if (isFirstLoad) {
                    speakText("시험 단계 선택 화면입니다. 단계는 입문, 기초, 기본, 심화 단계가 있습니다. 원하시는 단계를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    isFirstLoad = false;
                }
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.setPackage("com.google.android.googlequicksearchbox");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String voiceInput = matches.get(0).replace(" ", "");

                    if (voiceInput.contains("입문")) {
                        stopSTT();
                        startActivity(new Intent(TestLevelActivity.this, IntroTestActivity.class));
                    } else if (voiceInput.contains("기초")) {
                        stopSTT();
                        if (testBasicBtn.isEnabled()) {
                            startActivity(new Intent(TestLevelActivity.this, BasicTestActivity.class));
                        } else {
                            speakText("기초 시험은 아직 잠겨 있습니다. 입문 시험을 통과해야 합니다.", TextToSpeech.QUEUE_FLUSH);
                        }
                    } else if (voiceInput.contains("기본")) {
                        stopSTT();
                        if (testStandardBtn.isEnabled()) {
                            startActivity(new Intent(TestLevelActivity.this, StandardTestActivity.class));
                        } else {
                            speakText("기본 시험은 아직 잠겨 있습니다. 기초 시험을 통과해야 합니다.", TextToSpeech.QUEUE_FLUSH);
                        }
                    }
                    // [추가됨] AI 심화 시험 STT 인식 로직
                    else if (voiceInput.contains("심화") || voiceInput.contains("에이아이") || voiceInput.contains("AI")) {
                        stopSTT();
                        startActivity(new Intent(TestLevelActivity.this, AiTestActivity.class));
                    }
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("뒤로")) {
                        speakText("앱을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                    } else {
                        speakText("입문, 기초, 기본, 심화 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }
            @Override public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speakText("입문, 기초, 기본, 심화 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                }
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        mBluetoothHandler = new BluetoothHandler();
        new Thread(() -> {
            if (mBluetoothHandler.connect(PI_ADDRESS)) {
                mBluetoothHandler.sendMicOff();
            }
        }).start();

        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                if (mBluetoothHandler != null) {
                    mBluetoothHandler.sendMicOn();
                    try { Thread.sleep(100); } catch (Exception e) {}
                }
                finishAffinity();
                System.exit(0);
            });
        }

        testIntroBtn.setOnClickListener(v -> {
            stopSTT(); startActivity(new Intent(this, IntroTestActivity.class));
        });
        testIntroBtn.setOnLongClickListener(v -> {
            speakText("입문 시험 버튼입니다.", TextToSpeech.QUEUE_FLUSH); return true;
        });

        testBasicBtn.setOnClickListener(v -> {
            stopSTT(); startActivity(new Intent(this, BasicTestActivity.class));
        });
        testBasicBtn.setOnLongClickListener(v -> {
            if (testBasicBtn.isEnabled()) speakText("기초 시험 버튼입니다. 현재 열려 있습니다.", TextToSpeech.QUEUE_FLUSH);
            else speakText("기초 시험 버튼입니다. 현재 잠겨 있습니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });

        testStandardBtn.setOnClickListener(v -> {
            stopSTT(); startActivity(new Intent(this, StandardTestActivity.class));
        });
        testStandardBtn.setOnLongClickListener(v -> {
            if (testStandardBtn.isEnabled()) speakText("기본 시험 버튼입니다. 현재 열려 있습니다.", TextToSpeech.QUEUE_FLUSH);
            else speakText("기본 시험 버튼입니다. 현재 잠겨 있습니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });

        // [추가됨] AI 심화 시험 버튼 클릭 이벤트
        testAiBtn.setOnClickListener(v -> {
            stopSTT();
            startActivity(new Intent(TestLevelActivity.this, AiTestActivity.class));
        });
        testAiBtn.setOnLongClickListener(v -> {
            speakText("에이아이 심화 시험 버튼입니다. 내가 모은 단어장으로 시험을 봅니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });
    }

    private void speakText(String text, int mode) {
        if (isTtsReady && tts != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            tts.speak(text, mode, params, "UniqueID");
        }
    }

    private void startListeningVoice() {
        if (!isTtsSpeaking && speechRecognizer != null && isActivityVisible) {
            speechRecognizer.startListening(speechRecognizerIntent);
        }
    }

    private void stopSTT() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActivityVisible = true;
        stopSTT();

        SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);

        boolean isIntroPassed = prefs.getBoolean("isIntroPassed", false);
        if (isIntroPassed) {
            testBasicBtn.setEnabled(true); testBasicBtn.setText("기초 (열림)");
        } else {
            testBasicBtn.setEnabled(false); testBasicBtn.setText("기초 (잠김)");
        }

        boolean isBasicPassed = prefs.getBoolean("isBasicPassed", false);
        if (isBasicPassed) {
            testStandardBtn.setEnabled(true); testStandardBtn.setText("기본 (열림)");
        } else {
            testStandardBtn.setEnabled(false); testStandardBtn.setText("기본 (잠김)");
        }

        if (isTtsReady && !isFirstLoad) {
            speakText("시험 단계 선택 화면입니다. 원하시는 단계를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        stopSTT();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        if (mBluetoothHandler != null) {
            mBluetoothHandler.sendMicOn();
            try { Thread.sleep(100); } catch (Exception e) {}
            mBluetoothHandler.close();
        }
        super.onDestroy();
    }
}