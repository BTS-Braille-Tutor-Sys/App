package com.example.app1;

import android.content.Intent;
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

public class StudyLevelActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isFirstLoad = true;
    private boolean isActivityVisible = false;

    // [핵심 추가] levelAiBtn 추가
    private Button levelIntroBtn, levelBasicBtn, levelStandardBtn, levelAiBtn, btnExit;

    // 블루투스 통신 변수
    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study_level);

        levelIntroBtn = findViewById(R.id.levelIntroBtn);
        levelBasicBtn = findViewById(R.id.levelBasicBtn);
        levelStandardBtn = findViewById(R.id.levelStandardBtn);
        levelAiBtn = findViewById(R.id.levelAiBtn); // [핵심 추가] AI 심화 버튼 연결
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
                    speakText("학습 단계 선택 화면입니다. 단계는 입문, 기초, 기본, 심화 단계가 있습니다. 원하시는 단계를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    isFirstLoad = false;
                }
            } else {
                Log.e("TTS_ERROR", "StudyLevelActivity TTS 초기화 실패");
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.setPackage("com.google.android.googlequicksearchbox");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ko-KR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String voiceInput = matches.get(0).replace(" ", "");
                    Log.d("STT_RESULT", "인식된 말: " + voiceInput);

                    if (voiceInput.contains("입문")) {
                        stopSTT();
                        startActivity(new Intent(StudyLevelActivity.this, IntroStudyActivity.class));
                    } else if (voiceInput.contains("기초")) {
                        stopSTT();
                        if (levelBasicBtn.isEnabled()) {
                            startActivity(new Intent(StudyLevelActivity.this, BasicStudyActivity.class));
                        } else {
                            speakText("기초 단계는 아직 잠겨 있습니다. 입문 시험을 통과해야 합니다.", TextToSpeech.QUEUE_FLUSH);
                        }
                    } else if (voiceInput.contains("기본")) {
                        stopSTT();
                        if (levelStandardBtn.isEnabled()) {
                            startActivity(new Intent(StudyLevelActivity.this, StandardStudyActivity.class));
                        } else {
                            speakText("기본 단계는 아직 잠겨 있습니다. 기초 시험을 통과해야 합니다.", TextToSpeech.QUEUE_FLUSH);
                        }
                    }
                    // [핵심 추가] AI 심화 단계 STT 인식 로직
                    else if (voiceInput.contains("심화") || voiceInput.contains("에이아이") || voiceInput.contains("AI")) {
                        stopSTT();
                        startActivity(new Intent(StudyLevelActivity.this, AiStudyActivity.class));
                    }
                    // 완벽한 앱 종료 로직 반영
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("꺼줘") || voiceInput.contains("뒤로")) {
                        speakText("앱을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                        else {
                            finishAffinity();
                            System.exit(0);
                        }
                    }
                    else {
                        speakText("입문, 기초, 기본, 심화 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }

            @Override
            public void onError(int error) {
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

        // 블루투스 연결 및 마이크 끄기
        mBluetoothHandler = new BluetoothHandler();
        new Thread(() -> {
            boolean isConnected = mBluetoothHandler.connect(PI_ADDRESS);
            if (isConnected) {
                mBluetoothHandler.sendMicOff();
            }
        }).start();

        // 공통 완전 종료 버튼 클릭 로직
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                if (mBluetoothHandler != null) {
                    mBluetoothHandler.sendMicOn();
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
                finishAffinity();
                System.exit(0);
            });
        }

        levelIntroBtn.setOnClickListener(v -> {
            stopSTT();
            startActivity(new Intent(this, IntroStudyActivity.class));
        });
        levelIntroBtn.setOnLongClickListener(v -> {
            speakText("입문 단계 학습 버튼입니다. 초성 자음과 모음, 숫자, 기호를 학습합니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });

        levelBasicBtn.setOnClickListener(v -> {
            stopSTT();
            startActivity(new Intent(this, BasicStudyActivity.class));
        });
        levelBasicBtn.setOnLongClickListener(v -> {
            if (levelBasicBtn.isEnabled()) speakText("기초 단계 학습 버튼입니다. 현재 열려 있습니다.", TextToSpeech.QUEUE_FLUSH);
            else speakText("기초 단계 학습 버튼입니다. 현재 잠겨 있습니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });

        levelStandardBtn.setOnClickListener(v -> {
            stopSTT();
            startActivity(new Intent(this, StandardStudyActivity.class));
        });
        levelStandardBtn.setOnLongClickListener(v -> {
            if (levelStandardBtn.isEnabled()) speakText("기본 단계 학습 버튼입니다. 현재 열려 있습니다.", TextToSpeech.QUEUE_FLUSH);
            else speakText("기본 단계 학습 버튼입니다. 현재 잠겨 있습니다.", TextToSpeech.QUEUE_FLUSH);
            return true;
        });

        // [핵심 추가] AI 심화 버튼 클릭 이벤트
        levelAiBtn.setOnClickListener(v -> {
            stopSTT();
            startActivity(new Intent(StudyLevelActivity.this, AiStudyActivity.class));
        });
        levelAiBtn.setOnLongClickListener(v -> {
            speakText("에이아이 심화 단계 학습 버튼입니다. 무작위 단어를 생성하여 학습합니다.", TextToSpeech.QUEUE_FLUSH);
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

        android.content.SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);

        boolean isIntroPassed = prefs.getBoolean("isIntroPassed", false);
        if (isIntroPassed) {
            levelBasicBtn.setEnabled(true);
            levelBasicBtn.setText("기초 (열림)");
        } else {
            levelBasicBtn.setEnabled(false);
            levelBasicBtn.setText("기초 (잠김)");
        }

        boolean isBasicPassed = prefs.getBoolean("isBasicPassed", false);
        if (isBasicPassed) {
            levelStandardBtn.setEnabled(true);
            levelStandardBtn.setText("기본 (열림)");
        } else {
            levelStandardBtn.setEnabled(false);
            levelStandardBtn.setText("기본 (잠김)");
        }

        if (isTtsReady && !isFirstLoad) {
            speakText("학습 단계 선택 화면입니다. 입문, 기초, 기본, 심화 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
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