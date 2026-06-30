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
import java.util.Collections;
import java.util.Locale;

public class IntroStudyActivity extends AppCompatActivity {

    private TextView charText, statusText;
    private ImageView[] dots = new ImageView[12];
    private View brailleLayout1, brailleLayout2;
    private Button nextCharBtn, prevCharBtn, btnExit; // [추가] btnExit 변수 선언
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
            // [자음 0~13]
            {0,0,0,1,0,0}, {1,0,0,1,0,0}, {0,1,0,1,0,0}, {0,0,0,0,1,0}, {1,0,0,0,1,0},
            {0,0,0,1,1,0}, {0,0,0,0,0,1}, {1,1,0,1,1,0}, {0,0,0,1,0,1}, {0,0,0,0,1,1},
            {1,1,0,1,0,0}, {1,1,0,0,1,0}, {1,0,0,1,1,0}, {0,1,0,1,1,0},
            // [모음 14~34]
            {1,1,0,0,0,1}, {0,0,1,1,1,0}, {0,1,1,1,0,0}, {1,0,0,0,1,1}, {1,0,1,0,0,1},
            {0,0,1,1,0,1}, {1,0,1,1,0,0}, {1,0,0,1,0,1}, {0,1,0,1,0,1}, {1,0,1,0,1,0},
            {1,1,1,0,1,0}, {1,0,1,1,1,0}, {0,0,1,1,1,0, 1,1,1,0,1,0}, {0,0,1,1,0,0}, {1,1,1,0,0,1},
            {1,1,1,0,0,1, 1,1,1,0,1,0}, {1,0,1,1,1,1}, {1,1,1,1,0,0}, {1,1,1,1,0,0, 1,1,1,0,1,0},
            {1,0,1,1,0,0, 1,1,1,0,1,0}, {0,1,0,1,1,1},
            // [숫자 35~44]
            {0,0,1,1,1,1, 1,0,0,0,0,0}, {0,0,1,1,1,1, 1,1,0,0,0,0}, {0,0,1,1,1,1, 1,0,0,1,0,0},
            {0,0,1,1,1,1, 1,0,0,1,1,0}, {0,0,1,1,1,1, 1,0,0,0,1,0}, {0,0,1,1,1,1, 1,1,0,1,0,0},
            {0,0,1,1,1,1, 1,1,0,1,1,0}, {0,0,1,1,1,1, 1,1,0,0,1,0}, {0,0,1,1,1,1, 0,1,0,1,0,0},
            {0,0,1,1,1,1, 0,1,0,1,1,0},
            // [기호 45~50]
            {0,1,0,0,1,1}, {0,1,0,0,0,0}, {0,1,1,0,0,1}, {0,1,1,0,1,0}, {0,1,0,0,1,0}, {0,1,1,0,0,0}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro_study);

        charText = findViewById(R.id.charText);
        statusText = findViewById(R.id.statusText);
        brailleLayout1 = findViewById(R.id.brailleLayout1);
        brailleLayout2 = findViewById(R.id.brailleLayout2);
        nextCharBtn = findViewById(R.id.nextCharBtn);
        prevCharBtn = findViewById(R.id.prevCharBtn);
        btnExit = findViewById(R.id.btnExit); // [추가] XML에 include한 공통 종료 버튼 연결

        for (int i = 0; i < 12; i++) {
            int resId = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resId);
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

                speakText("입문 단계 학습을 시작합니다. 이동하려면 다음, 또는 이전이라고 말씀해주세요.", TextToSpeech.QUEUE_FLUSH);
                updateDisplay(true);
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
                        updateDisplay(false);
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
            boolean isConnected = mBluetoothHandler.connect(PI_ADDRESS);
            if (isConnected) {
                // [핵심 추가] 화면 켜질 때 파이 마이크 끄기
                mBluetoothHandler.sendMicOff();
            }
        }).start();

        // [핵심 추가] 공통 종료 버튼 클릭 시 마이크 켜고 종료
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

        nextCharBtn.setOnClickListener(v -> {
            stopSTT();
            currentIndex = (currentIndex + 1) % studyData.size();
            updateDisplay(true);
        });

        prevCharBtn.setOnClickListener(v -> {
            stopSTT();
            currentIndex = (currentIndex > 0) ? currentIndex - 1 : studyData.size() - 1;
            updateDisplay(true);
        });
    }

    private void initData() {
        studyData.clear();
        Collections.addAll(studyData, "ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ");
        Collections.addAll(studyData, "ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅒ", "ㅖ", "ㅘ", "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ");
        Collections.addAll(studyData, "1", "2", "3", "4", "5", "6", "7", "8", "9", "0");
        Collections.addAll(studyData, ".", ",", "?", "!", ":", ";");
    }

    private String getCharName(String ch) {
        switch (ch) {
            case "ㄱ": return "기역"; case "ㄴ": return "니은"; case "ㄷ": return "디귿";
            case "ㄹ": return "리을"; case "ㅁ": return "미음"; case "ㅂ": return "비읍";
            case "ㅅ": return "시옷"; case "ㅇ": return "이응"; case "ㅈ": return "지읒";
            case "ㅊ": return "치읓"; case "ㅋ": return "키읔"; case "ㅌ": return "티긑";
            case "ㅍ": return "피읍"; case "ㅎ": return "히읗";
            case ".": return "마침표"; case ",": return "쉼표"; case "?": return "물음표";
            case "!": return "느낌표"; case ":": return "쌍점"; case ";": return "쌍반점";
            default: return ch;
        }
    }

    private void speakText(String text, int mode) {
        if (isTtsReady && tts != null) {
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UniqueID");
            tts.speak(text, mode, params, "UniqueID");
        }
    }

    private void updateDisplay(boolean sendBT) {
        String currentChar = studyData.get(currentIndex);
        charText.setText(currentChar);

        String ttsText = getCharName(currentChar);
        if (currentIndex <= 13) {
            statusText.setText("카테고리: 자음");
            ttsText = "초성 " + ttsText;
        } else if (currentIndex <= 34) statusText.setText("카테고리: 모음");
        else if (currentIndex <= 44) statusText.setText("카테고리: 숫자");
        else statusText.setText("카테고리: 기호");

        speakText(ttsText, isFirstLoad ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH);
        isFirstLoad = false;

        int[] pattern = braillePatterns[currentIndex];
        if (pattern.length > 6) {
            brailleLayout2.setVisibility(View.VISIBLE);
            for (int i = 0; i < 12; i++) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
        } else {
            brailleLayout2.setVisibility(View.GONE);
            for (int i = 0; i < 6; i++) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
        }

        // 데이터 전송 방식은 기존 그대로 유지 (패턴 전송)
        if (sendBT && mBluetoothHandler != null) {
            new Thread(() -> mBluetoothHandler.sendData(pattern)).start();
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