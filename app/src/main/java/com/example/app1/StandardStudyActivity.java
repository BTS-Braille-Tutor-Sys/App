package com.example.app1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
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
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class StandardStudyActivity extends AppCompatActivity {

    private TextView charText, statusText;
    private ImageView[] dots = new ImageView[12];
    private View brailleLayout1, brailleLayout2;
    private Button nextCharBtn, prevCharBtn, btnExit; // [핵심 추가] 공통 종료 버튼 변수
    private int currentIndex = 0;
    private ArrayList<String> studyData = new ArrayList<>();
    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isFirstLoad = true;

    // 현재 화면이 포그라운드에 있는지 확인하는 방어 플래그
    private boolean isActivityVisible = false;

    private final int[][] braillePatterns = {
            // [약자 0~11] 가~하, 것
            {1,1,0,1,0,1}, {1,0,0,1,0,0}, {0,1,0,1,0,0}, {1,0,0,0,1,0}, {0,0,0,1,1,0}, {1,1,1,0,0,0}, {0,0,0,1,0,1}, {1,1,0,1,0,0}, {1,1,0,0,1,0}, {1,0,0,1,1,0}, {0,1,0,1,1,0}, {0,0,0,1,1,1, 0,1,1,1,0,0},
            // [약자 12~25] 모음+자음 축약형
            {1,0,0,1,1,1}, {0,1,1,1,1,1}, {0,1,1,1,1,0}, {1,0,0,0,0,1}, {1,1,0,0,1,1}, {1,1,0,1,1,1}, {1,0,1,1,0,1}, {1,1,1,0,1,1}, {1,1,1,1,1,1}, {1,1,0,1,1,0}, {1,1,1,1,0,1}, {1,0,1,0,1,1}, {0,1,1,1,0,1}, {1,1,1,1,1,0},
            // [약어 26~32] 2칸짜리 접속사 축약
            {1,0,0,0,0,0, 0,1,1,1,0,0}, {1,0,0,0,0,0, 1,0,0,1,0,0}, {1,0,0,0,0,0, 0,1,0,0,1,0}, {1,0,0,0,0,0, 0,1,0,0,0,1}, {1,0,0,0,0,0, 1,0,1,1,1,0}, {1,0,0,0,0,0, 1,0,1,0,0,1}, {1,0,0,0,0,0, 1,0,0,0,1,1}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_standard_study);

        charText = findViewById(R.id.charText);
        statusText = findViewById(R.id.statusText);
        brailleLayout1 = findViewById(R.id.brailleLayout1);
        brailleLayout2 = findViewById(R.id.brailleLayout2);
        nextCharBtn = findViewById(R.id.nextCharBtn);
        prevCharBtn = findViewById(R.id.prevCharBtn);
        btnExit = findViewById(R.id.btnExit); // [핵심 추가] 종료 버튼 연결

        for (int i = 0; i < 12; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resID);
        }

        initData();

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

                speakText("기본 단계 학습을 시작합니다. 이동하려면 다음, 또는 이전이라고 말씀해주세요.", TextToSpeech.QUEUE_FLUSH);
                updateDisplay();
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

                    if (voiceInput.contains("다음") || voiceInput.contains("오른쪽")) {
                        stopSTT();
                        if (nextCharBtn != null) nextCharBtn.performClick();
                    } else if (voiceInput.contains("이전") || voiceInput.contains("왼쪽")) {
                        stopSTT();
                        if (prevCharBtn != null) prevCharBtn.performClick();
                    } else if (voiceInput.contains("다시") || voiceInput.contains("반복")) {
                        stopSTT();
                        speakText(studyData.get(currentIndex), TextToSpeech.QUEUE_FLUSH);
                    }
                    // [핵심 추가] STT 음성으로 앱 종료
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("꺼줘")) {
                        speakText("학습을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                        else {
                            finishAffinity(); // 쌓여있는 모든 화면을 한 번에 종료
                            System.exit(0);   // 앱 프로세스를 메모리에서 완전히 삭제 (확실한 종료)
                        }
                    }
                    else {
                        speakText("다음, 이전, 또는 다시 라고 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }

            @Override
            public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Log.d("STT_STUDY", "말씀 없음. 점자 학습 중 대기.");
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
            boolean success = mBluetoothHandler.connect(PI_ADDRESS);
            runOnUiThread(() -> {
                if (success) {
                    statusText.setText("상태 : 라즈베리파이 연결됨");
                    // [핵심 추가] 화면 켜질 때 파이 마이크 끄기
                    mBluetoothHandler.sendMicOff();
                    updateDisplay();
                } else {
                    statusText.setText("상태 : 연결 실패");
                }
            });
        }).start();

        // [핵심 추가] 공통 종료 버튼 클릭 로직
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                if (mBluetoothHandler != null) {
                    mBluetoothHandler.sendMicOn();
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
                finishAffinity(); // 쌓여있는 모든 화면을 한 번에 종료
                System.exit(0);   // 앱 프로세스를 메모리에서 완전히 삭제 (확실한 종료)
            });
        }

        prevCharBtn.setOnClickListener(v -> {
            stopSTT();
            if (currentIndex > 0) currentIndex--;
            else currentIndex = studyData.size() - 1;
            updateDisplay();
        });

        nextCharBtn.setOnClickListener(v -> {
            stopSTT();
            currentIndex = (currentIndex + 1) % studyData.size();
            updateDisplay();
        });
    }

    private void initData() {
        studyData.clear();
        Collections.addAll(studyData, "가", "나", "다", "마", "바", "사", "자", "카", "타", "파", "하", "것");
        Collections.addAll(studyData, "억", "언", "얼", "연", "열", "영", "옥", "온", "옹", "운", "울", "은", "을", "인");
        Collections.addAll(studyData, "그래서", "그러나", "그러면", "그러므로", "그런데", "그리고", "그리하여");
    }

    private void speakText(String text, int mode) {
        if (isTtsReady && tts != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            tts.speak(text, mode, params, "UniqueID");
        }
    }

    private void updateDisplay() {
        if (currentIndex < 0 || currentIndex >= studyData.size()) return;

        String currentChar = studyData.get(currentIndex);
        charText.setText(currentChar);

        if (currentIndex <= 25) {
            statusText.setText("카테고리: 약자");
        } else {
            statusText.setText("카테고리: 약어");
        }

        speakText(currentChar, isFirstLoad ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH);
        isFirstLoad = false;

        int[] pattern = braillePatterns[currentIndex];

        if (pattern.length > 6) {
            brailleLayout2.setVisibility(View.VISIBLE);
            for (int i = 0; i < 12; i++) {
                if (dots[i] != null) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
            }
        } else {
            brailleLayout2.setVisibility(View.GONE);
            for (int i = 0; i < 6; i++) {
                if (dots[i] != null) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
            }
        }

        if (mBluetoothHandler != null) {
            new Thread(() -> {
                mBluetoothHandler.sendData(pattern);
                mBluetoothHandler.sendStudyChar(studyData.get(currentIndex));
            }).start();
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

        // [핵심 추가] 강제 종료 시에도 마이크 복구 신호 전송
        if (mBluetoothHandler != null) {
            mBluetoothHandler.sendMicOn();
            try { Thread.sleep(100); } catch (Exception e) {}
            mBluetoothHandler.close();
        }
        super.onDestroy();
    }
}