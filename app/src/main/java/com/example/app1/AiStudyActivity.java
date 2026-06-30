package com.example.app1;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class AiStudyActivity extends AppCompatActivity {

    private TextView charText, statusText;
    private Button generateAiBtn, btnExit;
    private View brailleLayout2;
    // [수정] 점 개수를 36개로 확장
    private ImageView[] dots = new ImageView[36];

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isActivityVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_study);

        charText = findViewById(R.id.charText);
        statusText = findViewById(R.id.statusText);
        generateAiBtn = findViewById(R.id.generateAiBtn);
        btnExit = findViewById(R.id.btnExit);
        brailleLayout2 = findViewById(R.id.brailleLayout2);

        // [수정] 36개의 점자 뷰 연결
        for (int i = 0; i < 36; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resID);
        }

        // 초기에는 모든 점을 초기화 (빈 상태)
        clearBrailleDots();

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

                speakText("AI 심화 학습을 시작합니다. 새로운 단어를 생성하시려면 생성, 또는 다음이라고 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.setPackage("com.google.android.googlequicksearchbox");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ko-KR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String voiceInput = matches.get(0).replace(" ", "");

                    if (voiceInput.contains("생성") || voiceInput.contains("다음") || voiceInput.contains("만들어")) {
                        stopSTT();
                        if (generateAiBtn != null) generateAiBtn.performClick();
                    }
                    else if (voiceInput.contains("다시") || voiceInput.contains("반복")) {
                        stopSTT();
                        speakText(charText.getText().toString(), TextToSpeech.QUEUE_FLUSH);
                    }
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("꺼줘")) {
                        speakText("AI 학습을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                        else {
                            finishAffinity();
                            System.exit(0);
                        }
                    } else {
                        speakText("단어 생성, 다시, 또는 종료 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }

            @Override
            public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Log.d("STT", "AI 대기중");
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

        // [핵심 추가] 라즈베리파이에서 번역된 비트가 날아올 때 점자 UI를 업데이트하는 리스너!
        mBluetoothHandler.setBluetoothListener(new BluetoothHandler.BluetoothListener() {
            @Override
            public void onBitPatternReceived(String pattern) {
                runOnUiThread(() -> {
                    // 예: "100101001010..." 형태로 데이터가 들어옴
                    Log.d("BRAILLE_UPDATE", "라즈베리파이 수신 패턴: " + pattern);
                    updateBrailleDots(pattern);
                });
            }
        });

        new Thread(() -> {
            boolean isConnected = mBluetoothHandler.connect(PI_ADDRESS);
            if (isConnected) {
                mBluetoothHandler.sendMicOff();
            }
        }).start();

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

        generateAiBtn.setOnClickListener(v -> {
            stopSTT();
            generateNewAiWord();
        });
    }

    private void generateNewAiWord() {
        charText.setText("생성 중...");
        speakText("AI가 새로운 단어를 만들고 있습니다. 잠시만 기다려 주세요.", TextToSpeech.QUEUE_FLUSH);
        generateAiBtn.setEnabled(false);
        clearBrailleDots(); // 새 단어 요청 시 기존 점자 지우기

        GeminiHelper.generateRandomWord(new GeminiHelper.GeminiCallback() {
            @Override
            public void onSuccess(String word) {
                generateAiBtn.setEnabled(true);
                charText.setText(word);
                speakText("생성된 단어는 " + word + " 입니다.", TextToSpeech.QUEUE_FLUSH);

                // 단어를 라즈베리파이에 쏘면, 파이에서 번역 후 위의 onBitPatternReceived 리스너로 응답이 옵니다!
                if (mBluetoothHandler != null) {
                    mBluetoothHandler.sendStudyChar(word);
                }
                WordManager.saveWord(AiStudyActivity.this, word);
            }

            @Override
            public void onError(String error) {
                generateAiBtn.setEnabled(true);
                Log.e("AI_ERROR", "제미나이 호출 실패: " + error);

                String backupWord = "사과";
                charText.setText(backupWord);
                speakText("통신이 지연되어 기본 단어인 " + backupWord + " 로 학습합니다.", TextToSpeech.QUEUE_FLUSH);

                if (mBluetoothHandler != null) {
                    mBluetoothHandler.sendStudyChar(backupWord);
                }
                WordManager.saveWord(AiStudyActivity.this, backupWord);
            }
        });
    }

    // [추가] 파이에서 받은 "100101..." 비트 스트링으로 화면 점자를 켜는 메서드
    private void updateBrailleDots(String pattern) {
        // 들어온 패턴 길이만큼 반복하며 1이면 켜고 0이면 끔 (최대 36개)
        for (int i = 0; i < 36; i++) {
            if (i < pattern.length() && pattern.charAt(i) == '1') {
                dots[i].setImageResource(R.drawable.dot_active);
            } else {
                dots[i].setImageResource(R.drawable.dot_inactive);
            }
        }
    }

    // [추가] 점자 화면을 모두 끄는 메서드
    private void clearBrailleDots() {
        for (int i = 0; i < 36; i++) {
            if (dots[i] != null) {
                dots[i].setImageResource(R.drawable.dot_inactive);
            }
        }
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