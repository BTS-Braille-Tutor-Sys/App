package com.example.app1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isActivityVisible = false;

    private final int PERMISSION_CODE = 100;

    // 💡 [수정됨] wrongNoteBtn 변수 추가
    private Button studyBtn, testBtn, writeBtn, wrongNoteBtn, btnExit;

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        studyBtn = findViewById(R.id.studyBtn);
        testBtn = findViewById(R.id.testBtn);
        writeBtn = findViewById(R.id.writeBtn);
        wrongNoteBtn = findViewById(R.id.wrongNoteBtn); // 💡 [추가됨] 오답노트 버튼 연결
        btnExit = findViewById(R.id.btnExit);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, PERMISSION_CODE);
        }

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

                // 💡 [수정됨] 안내 멘트에 '오답 노트' 추가
                speakText("점자 학습 애플리케이션입니다. 메뉴는 학습, 시험, 쓰기, 오답 노트가 있습니다. 원하시는 메뉴를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
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

                    if (voiceInput.contains("학습") || voiceInput.contains("공부")) {
                        speakText("학습 모드로 진입합니다.", TextToSpeech.QUEUE_FLUSH);
                        if (studyBtn != null) startActivity(new Intent(MainActivity.this, StudyLevelActivity.class));
                    }
                    else if (voiceInput.contains("시험") || voiceInput.contains("테스트")) {
                        speakText("시험 모드로 진입합니다.", TextToSpeech.QUEUE_FLUSH);
                        if (testBtn != null) startActivity(new Intent(MainActivity.this, TestLevelActivity.class));
                    }
                    else if (voiceInput.contains("쓰기") || voiceInput.contains("작성")) {
                        speakText("쓰기 모드로 진입합니다.", TextToSpeech.QUEUE_FLUSH);
                        if (writeBtn != null) startActivity(new Intent(MainActivity.this, WriteActivity.class));
                    }
                    // 💡 [추가됨] STT 오답 노트 진입 로직
                    else if (voiceInput.contains("오답") || voiceInput.contains("오답노트") || voiceInput.contains("노트")) {
                        speakText("오답 노트로 진입합니다.", TextToSpeech.QUEUE_FLUSH);
                        if (wrongNoteBtn != null) startActivity(new Intent(MainActivity.this, WrongNoteActivity.class));
                    }
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("꺼줘")) {
                        speakText("앱을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) {
                            btnExit.performClick();
                        } else {
                            finishAffinity();
                            System.exit(0);
                        }
                    }
                    else {
                        speakText("잘 못 들었습니다. 학습, 시험, 쓰기, 오답 노트 중에서 다시 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }

            @Override
            public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    speakText("말씀이 없으십니다. 학습, 시험, 쓰기, 오답 노트 중에서 다시 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                }
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        if (studyBtn != null) {
            studyBtn.setOnClickListener(v -> {
                stopSTT();
                startActivity(new Intent(MainActivity.this, StudyLevelActivity.class));
            });
            studyBtn.setOnLongClickListener(v -> {
                speakText("학습 모드 선택 버튼입니다.", TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }

        if (testBtn != null) {
            testBtn.setOnClickListener(v -> {
                stopSTT();
                startActivity(new Intent(MainActivity.this, TestLevelActivity.class));
            });
            testBtn.setOnLongClickListener(v -> {
                speakText("시험 모드 선택 버튼입니다.", TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }

        if (writeBtn != null) {
            writeBtn.setOnClickListener(v -> {
                stopSTT();
                startActivity(new Intent(MainActivity.this, WriteActivity.class));
            });
            writeBtn.setOnLongClickListener(v -> {
                speakText("쓰기 모드 선택 버튼입니다. 기기와 연동하여 점자를 직접 입력합니다.", TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }

        // 💡 [추가됨] 오답 노트 버튼 클릭 이벤트
        if (wrongNoteBtn != null) {
            wrongNoteBtn.setOnClickListener(v -> {
                stopSTT();
                startActivity(new Intent(MainActivity.this, WrongNoteActivity.class));
            });
            wrongNoteBtn.setOnLongClickListener(v -> {
                speakText("오답 노트 선택 버튼입니다. 지금까지 틀린 문제와 통계를 확인합니다.", TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }

        mBluetoothHandler = new BluetoothHandler();
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
        if (isTtsReady) {
            speakText("메인 메뉴입니다. 학습, 시험, 쓰기, 오답 노트를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
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