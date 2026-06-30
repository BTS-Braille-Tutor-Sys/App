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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AiTestActivity extends AppCompatActivity {

    private TextView questionCountText;
    private ImageView[] dots = new ImageView[36]; // 6칸 확장
    private Button[] optionBtns = new Button[4];
    private Button btnExit;

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    private int score = 0;
    private int currentQuestionIndex = 0;
    private final int TOTAL_QUESTIONS = 5;
    private String correctAnswer = "";
    private String currentOptionsAudio = "";

    // 💡 [추가됨] 라즈베리파이에서 번역해준 점자 패턴을 오답 저장을 위해 기억해둘 변수
    private String currentBitPatternStr = "";

    // 시험에 쓸 단어들을 담을 바구니
    private ArrayList<String> testWords = new ArrayList<>();

    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;

    private boolean isTtsReady = false;
    private boolean isTtsSpeaking = false;
    private boolean isActivityVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_test);

        questionCountText = findViewById(R.id.questionCountText);
        btnExit = findViewById(R.id.btnExit);

        for (int i = 0; i < 36; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resID);
        }

        optionBtns[0] = findViewById(R.id.option1);
        optionBtns[1] = findViewById(R.id.option2);
        optionBtns[2] = findViewById(R.id.option3);
        optionBtns[3] = findViewById(R.id.option4);

        prepareTestWords();

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

                speakText("에이아이 심화 시험을 시작합니다. 정답 번호를 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                setQuestion();
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

                    if (voiceInput.contains("1") || voiceInput.contains("일") || voiceInput.contains("첫")) {
                        stopSTT(); optionBtns[0].performClick();
                    } else if (voiceInput.contains("2") || voiceInput.contains("이") || voiceInput.contains("두")) {
                        stopSTT(); optionBtns[1].performClick();
                    } else if (voiceInput.contains("3") || voiceInput.contains("삼") || voiceInput.contains("세")) {
                        stopSTT(); optionBtns[2].performClick();
                    } else if (voiceInput.contains("4") || voiceInput.contains("사") || voiceInput.contains("네")) {
                        stopSTT(); optionBtns[3].performClick();
                    } else if (voiceInput.contains("다시") || voiceInput.contains("반복")) {
                        stopSTT();
                        speakText("문제 " + (currentQuestionIndex + 1) + "번입니다.", TextToSpeech.QUEUE_FLUSH);
                        speakText(currentOptionsAudio, TextToSpeech.QUEUE_ADD);
                    } else if (voiceInput.contains("종료") || voiceInput.contains("나가기")) {
                        stopSTT();
                        if (btnExit != null) btnExit.performClick();
                    } else {
                        speakText("1번, 2번, 3번, 4번 중에서 말씀해 주세요.", TextToSpeech.QUEUE_FLUSH);
                    }
                }
            }
            @Override public void onError(int error) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        mBluetoothHandler = new BluetoothHandler();

        // 💡 [핵심 추가] 라즈베리파이에서 번역된 비트가 도착하면, 그 비트를 나중을 위해 기억(캐싱)해 둡니다.
        mBluetoothHandler.setBluetoothListener(pattern -> runOnUiThread(() -> {
            String cleanPattern = pattern.replace("STUDY:", "").replace("TOUCH:", "").trim();
            currentBitPatternStr = cleanPattern; // 틀렸을 때를 대비해 패턴을 임시 보관!
            updateBrailleDots(cleanPattern);
        }));

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

        for (Button btn : optionBtns) {
            btn.setOnClickListener(v -> checkAnswer(((Button) v).getText().toString()));
            btn.setOnLongClickListener(v -> {
                speakText(((Button) v).getText().toString(), TextToSpeech.QUEUE_FLUSH);
                return true;
            });
        }
    }

    private void prepareTestWords() {
        ArrayList<String> savedWords = WordManager.loadWords(this);
        String[] fallbackWords = {"사과", "바다", "우유", "포도", "나무", "나비", "모자", "구두"};

        for (String w : savedWords) {
            if (!testWords.contains(w) && !w.trim().isEmpty()) {
                testWords.add(w.trim());
            }
        }

        int fIndex = 0;
        while (testWords.size() < 10) {
            if (!testWords.contains(fallbackWords[fIndex])) {
                testWords.add(fallbackWords[fIndex]);
            }
            fIndex = (fIndex + 1) % fallbackWords.length;
        }

        Collections.shuffle(testWords);
    }

    private void setQuestion() {
        if (currentQuestionIndex >= TOTAL_QUESTIONS) {
            showResultDialog();
            return;
        }

        questionCountText.setText("문제 " + (currentQuestionIndex + 1) + " / " + TOTAL_QUESTIONS);
        speakText("문제 " + (currentQuestionIndex + 1) + "번입니다.", TextToSpeech.QUEUE_ADD);

        clearBrailleDots();

        // 새로운 문제가 시작되므로 이전 패턴 정보는 초기화
        currentBitPatternStr = "";

        correctAnswer = testWords.get(currentQuestionIndex);

        if (mBluetoothHandler != null) {
            mBluetoothHandler.sendStudyChar(correctAnswer);
        }

        List<String> options = new ArrayList<>();
        options.add(correctAnswer);

        Random random = new Random();
        while (options.size() < 4) {
            String wrong = testWords.get(random.nextInt(testWords.size()));
            if (!options.contains(wrong)) {
                options.add(wrong);
            }
        }
        Collections.shuffle(options);

        StringBuilder optionsAudio = new StringBuilder(" 보기입니다. ");
        for (int i = 0; i < 4; i++) {
            optionBtns[i].setText(options.get(i));
            optionsAudio.append(i + 1).append("번 ").append(options.get(i)).append(", ");
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
            speakText("틀렸습니다. 정답은 " + correctAnswer + " 입니다.", TextToSpeech.QUEUE_FLUSH);

            // 💡 [추가됨] AI 모드이므로 첫 번째 인자에 "ai" 단어를 넘겨 기록을 쌓습니다.
            // currentBitPatternStr 안에는 라즈베리파이가 방금 보내줬던 점자 코드가 들어있습니다!
            saveWrongAnswerData("ai", correctAnswer, currentBitPatternStr);
        }

        currentQuestionIndex++;
        setQuestion();
    }

    // 💡 [추가됨] 오답 발생 시 내부 저장소(SharedPreferences)에 기록하는 함수
    private void saveWrongAnswerData(String level, String wrongWord, String bitPattern) {
        // 라즈베리파이에서 응답을 늦게 줘서 패턴이 아직 비어있다면 저장 시 오류가 생길 수 있으므로 방어 코드 추가
        if (bitPattern == null || bitPattern.isEmpty()) {
            bitPattern = "100000010000001000000100000010000001"; // 통신 실패 시 기본 더미 패턴
        }

        android.content.SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        String countKey = "wrong_" + level + "_count";
        int currentCount = prefs.getInt(countKey, 0);
        editor.putInt(countKey, currentCount + 1);

        String existingWords = prefs.getString("wrong_words_list", "");
        if (existingWords.isEmpty()) {
            editor.putString("wrong_words_list", wrongWord);
        } else {
            if (!existingWords.contains(wrongWord)) {
                editor.putString("wrong_words_list", existingWords + "," + wrongWord);
            }
        }

        editor.putString("pattern_" + wrongWord, bitPattern);
        editor.apply();
    }

    private void updateBrailleDots(String pattern) {
        for (int i = 0; i < 36; i++) {
            if (i < pattern.length() && pattern.charAt(i) == '1') {
                dots[i].setImageResource(R.drawable.dot_active);
            } else {
                dots[i].setImageResource(R.drawable.dot_inactive);
            }
        }
    }

    private void clearBrailleDots() {
        for (int i = 0; i < 36; i++) {
            if (dots[i] != null) dots[i].setImageResource(R.drawable.dot_inactive);
        }
    }

    private void showResultDialog() {
        String message = "점수: " + score + " 점 입니다. 수고하셨습니다!";
        speakText(message, TextToSpeech.QUEUE_FLUSH);

        new AlertDialog.Builder(this)
                .setTitle("시험 결과")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("확인", (dialog, which) -> finish())
                .show();
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