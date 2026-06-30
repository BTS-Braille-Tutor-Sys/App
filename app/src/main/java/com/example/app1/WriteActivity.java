package com.example.app1;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class WriteActivity extends AppCompatActivity {

    // 💡 36개의 점을 담을 거대한 배열 생성!
    private ImageView[] dots = new ImageView[36];
    private TextView statusText;
    private Button btnExit;

    private BluetoothHandler mBluetoothHandler;
    private final String PI_ADDRESS = "2C:CF:67:B7:88:DC";

    private TextToSpeech tts;
    private boolean isTtsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_write_mode);

        statusText = findViewById(R.id.statusText);
        btnExit = findViewById(R.id.btnExit);

        // 💡 반복문을 통해 dot1부터 dot36까지 한 방에 연결합니다!
        for (int i = 0; i < 36; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = findViewById(resID);
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                isTtsReady = true;
                speakText("쓰기 모드입니다. 기기의 점자 센서를 터치해 보세요.");
            }
        });

        mBluetoothHandler = new BluetoothHandler();

        mBluetoothHandler.setBluetoothListener(message -> {
            runOnUiThread(() -> {
                // 라즈베리파이에서 "TOUCH:1001..." 신호가 오면
                if (message != null && message.startsWith("TOUCH:")) {
                    String pattern = message.replace("TOUCH:", "").trim();
                    updateBrailleDots(pattern);
                }
            });
        });

        new Thread(() -> {
            mBluetoothHandler.connect(PI_ADDRESS);
        }).start();

        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                speakText("쓰기 모드를 종료합니다.");
                finish();
            });
        }
    }

    // 💡 넘어온 36자리 문자열을 분석해서 36개의 점 색깔을 동시에 바꿉니다
    private void updateBrailleDots(String pattern) {
        // 라즈베리파이에서 보낸 신호가 정확히 36자리인지 확인
        if (pattern.length() >= 36) {
            for (int i = 0; i < 36; i++) {
                if (pattern.charAt(i) == '1') {
                    dots[i].setImageResource(R.drawable.dot_active);
                } else {
                    dots[i].setImageResource(R.drawable.dot_inactive);
                }
            }
        }
    }

    private void speakText(String text) {
        if (isTtsReady && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UniqueID");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (mBluetoothHandler != null) { mBluetoothHandler.close(); }
        super.onDestroy();
    }
}