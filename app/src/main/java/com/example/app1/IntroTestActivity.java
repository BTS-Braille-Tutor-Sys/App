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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class IntroTestActivity extends AppCompatActivity {

    private TextView questionCountText;
    private ImageView[] dots = new ImageView[12];
    private View brailleLayout2;
    private Button[] optionBtns = new Button[4];
    private Button btnExit;

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    private int score = 0;
    private int currentQuestionIndex = 0;
    private final int TOTAL_QUESTIONS = 5;
    private String correctAnswer = "";
    private String currentOptionsAudio = "";

    // 💡 [추가됨] 오답 노트 저장을 위해 현재 문제의 점자 패턴을 문자로 들고 있을 변수
    private String currentBitPatternStr = "";

    private ArrayList<String> studyData = new ArrayList<>();

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
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

    private List<Integer> questionIndices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro_test);

        questionCountText = findViewById(R.id.questionCountText);
        brailleLayout2 = findViewById(R.id.brailleLayout2);
        btnExit = findViewById(R.id.btnExit);

        for (int i = 0; i < 12; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resID);
        }

        optionBtns[0] = findViewById(R.id.option1);
        optionBtns[1] = findViewById(R.id.option2);
        optionBtns[2] = findViewById(R.id.option3);
        optionBtns[3] = findViewById(R.id.option4);

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

                speakText("입문 단계 시험을 시작합니다. 정답 번호를 말씀하시거나 화면을 터치해 주세요.", TextToSpeech.QUEUE_FLUSH);
                setQuestion();
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

                    if (voiceInput.contains("1") || voiceInput.contains("일") || voiceInput.contains("1번") || voiceInput.contains("첫")) {
                        stopSTT(); optionBtns[0].performClick();
                    } else if (voiceInput.contains("2") || voiceInput.contains("이") || voiceInput.contains("2번") || voiceInput.contains("두")) {
                        stopSTT(); optionBtns[1].performClick();
                    } else if (voiceInput.contains("3") || voiceInput.contains("삼") || voiceInput.contains("3번") || voiceInput.contains("세")) {
                        stopSTT(); optionBtns[2].performClick();
                    } else if (voiceInput.contains("4") || voiceInput.contains("사") || voiceInput.contains("4번") || voiceInput.contains("네")) {
                        stopSTT(); optionBtns[3].performClick();
                    } else if (voiceInput.contains("다시") || voiceInput.contains("반복")) {
                        stopSTT();
                        speakText("문제 " + (currentQuestionIndex + 1) + "번입니다.", TextToSpeech.QUEUE_FLUSH);
                        speakText(currentOptionsAudio, TextToSpeech.QUEUE_ADD);
                    }
                    else if (voiceInput.contains("종료") || voiceInput.contains("나가기") || voiceInput.contains("꺼줘")) {
                        speakText("시험을 종료합니다.", TextToSpeech.QUEUE_FLUSH);
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                        else {
                            finishAffinity();
                            System.exit(0);
                        }
                    }
                    else {
                        speakText("1번, 2번, 3번, 4번 중에서 정답을 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }

            @Override
            public void onError(int error) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Log.d("STT", "대기중");
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

        initData();

        for (int i = 0; i < studyData.size(); i++) questionIndices.add(i);
        Collections.shuffle(questionIndices);

        for (Button btn : optionBtns) {
            btn.setOnClickListener(v -> checkAnswer(((Button) v).getText().toString()));
            btn.setOnLongClickListener(v -> {
                speakText(getCharName(((Button) v).getText().toString()), TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }
    }

    private void initData() {
        studyData.clear();
        String[] consonants = {"ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"};
        Collections.addAll(studyData, consonants);
        String[] vowels = {"ㅏ", "ㅑ", "ㅓ", "ㅕ", "ㅗ", "ㅛ", "ㅜ", "ㅠ", "ㅡ", "ㅣ", "ㅐ", "ㅔ", "ㅒ", "ㅖ", "ㅘ", "ㅙ", "ㅚ", "ㅝ", "ㅞ", "ㅟ", "ㅢ"};
        Collections.addAll(studyData, vowels);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        Collections.addAll(studyData, numbers);
        String[] symbols = {".", ",", "?", "!", ":", ";"};
        Collections.addAll(studyData, symbols);
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

    private void setQuestion() {
        if (currentQuestionIndex >= TOTAL_QUESTIONS) {
            showResultDialog();
            return;
        }

        questionCountText.setText("문제 " + (currentQuestionIndex + 1) + " / " + TOTAL_QUESTIONS);
        speakText("문제 " + (currentQuestionIndex + 1) + "번입니다.", TextToSpeech.QUEUE_ADD);

        int targetIndex = questionIndices.get(currentQuestionIndex);
        correctAnswer = studyData.get(targetIndex);
        int[] pattern = braillePatterns[targetIndex];

        // 💡 [추가됨] 오답 발생 시 저장할 수 있도록 int 배열을 문자열("010010...")로 변환해 둡니다!
        StringBuilder sb = new StringBuilder();
        for (int bit : pattern) {
            sb.append(bit);
        }
        currentBitPatternStr = sb.toString();

        if (pattern.length > 6) {
            if (brailleLayout2 != null) brailleLayout2.setVisibility(View.VISIBLE);
            for (int i = 0; i < 12; i++) {
                if (dots[i] != null) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
            }
        } else {
            if (brailleLayout2 != null) brailleLayout2.setVisibility(View.GONE);
            for (int i = 0; i < 6; i++) {
                if (dots[i] != null) dots[i].setImageResource(pattern[i] == 1 ? R.drawable.dot_active : R.drawable.dot_inactive);
            }
        }

        if (mBluetoothHandler != null) {
            new Thread(() -> mBluetoothHandler.sendData(pattern)).start();
        }

        List<String> options = new ArrayList<>();
        options.add(correctAnswer);
        Random random = new Random();
        while (options.size() < 4) {
            String wrong = studyData.get(random.nextInt(studyData.size()));
            if (!options.contains(wrong)) options.add(wrong);
        }
        Collections.shuffle(options);

        for (int i = 0; i < 4; i++) optionBtns[i].setText(options.get(i));

        StringBuilder optionsAudio = new StringBuilder(" 보기입니다. ");
        for (int i = 0; i < 4; i++) {
            optionsAudio.append(i + 1).append("번 ").append(getCharName(options.get(i))).append(", ");
        }

        currentOptionsAudio = optionsAudio.toString();
        speakText(currentOptionsAudio, TextToSpeech.QUEUE_ADD);
    }

    private void checkAnswer(String selectedAnswer) {
        stopSTT();

        if (selectedAnswer.equals(correctAnswer)) {
            score++;
            speakText("정답입니다.", TextToSpeech.QUEUE_FLUSH);
        } else {
            speakText("틀렸습니다. 정답은 " + getCharName(correctAnswer) + " 입니다.", TextToSpeech.QUEUE_FLUSH);

            // 💡 [추가됨] 오답일 경우, 저장 함수를 호출합니다!
            // 입문 단계이므로 level은 "intro" 로 넘깁니다.
            saveWrongAnswerData("intro", correctAnswer, currentBitPatternStr);
        }

        currentQuestionIndex++;
        setQuestion();
    }

    private void showResultDialog() {
        String message;
        if (score == TOTAL_QUESTIONS) {
            android.content.SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isIntroPassed", true);
            editor.apply();

            message = "만점입니다! 축하합니다. 입문 과정을 마스터하여 기초 단계가 열렸습니다.";
        } else {
            message = "점수: " + score + " 점 입니다. 아쉽지만 다시 도전해보세요!";
        }

        speakText(message.replace("\n", " "), TextToSpeech.QUEUE_FLUSH);
        new AlertDialog.Builder(this)
                .setTitle("시험 결과")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("확인", (dialog, which) -> finish())
                .show();
    }

    // 💡 [추가됨] 오답 발생 시 내부 저장소(SharedPreferences)에 기록하는 함수
    private void saveWrongAnswerData(String level, String wrongWord, String bitPattern) {
        android.content.SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        // 1. 단계별 오답 카운트 +1 누적
        String countKey = "wrong_" + level + "_count";
        int currentCount = prefs.getInt(countKey, 0);
        editor.putInt(countKey, currentCount + 1);

        // 2. 틀린 단어를 리스트에 쉼표로 이어 붙이기
        String existingWords = prefs.getString("wrong_words_list", "");
        if (existingWords.isEmpty()) {
            editor.putString("wrong_words_list", wrongWord);
        } else {
            if (!existingWords.contains(wrongWord)) { // 중복 단어 방지
                editor.putString("wrong_words_list", existingWords + "," + wrongWord);
            }
        }

        // 3. 해당 단어의 점자 패턴 캐싱 ("pattern_가" = "000100110001")
        editor.putString("pattern_" + wrongWord, bitPattern);

        editor.apply(); // 저장!
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