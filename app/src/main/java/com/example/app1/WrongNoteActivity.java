package com.example.app1;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;

public class WrongNoteActivity extends AppCompatActivity {

    private BarChart barChart;
    private LinearLayout wrongListLayout;
    private Button btnExit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wrong_note);

        barChart = findViewById(R.id.wrongAnswerChart);
        wrongListLayout = findViewById(R.id.wrongListLayout);
        btnExit = findViewById(R.id.btnExit);

        if (btnExit != null) {
            btnExit.setOnClickListener(v -> finish());
        }

        // 저장소 데이터를 기반으로 차트와 목록 초기화
        setupChart();
        setupWrongList();
    }

    private void setupChart() {
        SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);

        // 💡 모든 모드의 기본 오답 수치를 0으로 초기화하여 실제 저장된 데이터만 반영합니다.
        int introCount = prefs.getInt("wrong_intro_count", 0);
        int basicCount = prefs.getInt("wrong_basic_count", 0);
        int standardCount = prefs.getInt("wrong_standard_count", 0);
        int aiCount = prefs.getInt("wrong_ai_count", 0);

        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, introCount));
        entries.add(new BarEntry(1, basicCount));
        entries.add(new BarEntry(2, standardCount));
        entries.add(new BarEntry(3, aiCount));

        BarDataSet dataSet = new BarDataSet(entries, "단계별 오답 횟수");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(14f);
        dataSet.setValueTextColor(Color.BLACK);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f); // 막대 두께 조절
        barChart.setData(data);

        // X축 레이블 및 스타일 설정
        String[] labels = new String[]{"입문", "기초", "기본", "AI 심화"};
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(14f);
        xAxis.setLabelCount(labels.length);

        // 차트 디자인 세부 설정
        barChart.getDescription().setEnabled(false);
        barChart.getLegend().setTextSize(14f);
        barChart.getAxisRight().setEnabled(false); // 우측 축 숨기기
        barChart.getAxisLeft().setGranularity(1f); // Y축 정수 단위 표현
        barChart.getAxisLeft().setAxisMinimum(0f); // Y축 최소값 고정
        barChart.setFitBars(true);

        // 화면 진입 시 그래프 애니메이션 효과 추가
        barChart.animateY(1000);
        barChart.invalidate();
    }

    private void setupWrongList() {
        SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);

        // 💡 임시 단어 목록을 삭제하고 빈 값("")을 기본값으로 둡니다.
        String wrongWordsStr = prefs.getString("wrong_words_list", "");

        // 오답 기록이 전혀 없을 때 예외 처리
        if (wrongWordsStr.trim().isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("🎉 아직 틀린 문제가 없습니다! 완벽해요!");
            emptyText.setTextSize(20f);
            emptyText.setTextColor(Color.GRAY);
            emptyText.setPadding(20, 40, 20, 40);
            emptyText.setGravity(android.view.Gravity.CENTER);
            wrongListLayout.addView(emptyText);
            return;
        }

        // 쉼표로 구분된 오답 단어들을 분리하여 동적으로 리스트 추가
        String[] words = wrongWordsStr.split(",");
        for (String word : words) {
            String targetWord = word.trim();
            if(targetWord.isEmpty()) continue;

            TextView tv = new TextView(this);
            tv.setText("❌ 오답 기록: " + targetWord);
            tv.setTextSize(20f);
            tv.setTextColor(Color.parseColor("#E53935"));
            tv.setPadding(20, 20, 20, 20);

            // 리스트 아이템 클릭 이벤트 리스너 설정
            tv.setOnClickListener(v -> showBraillePopup(targetWord));

            // 안드로이드 기본 클릭 피드백 효과 적용
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setClickable(true);

            wrongListLayout.addView(tv);
        }
    }

    private void showBraillePopup(String word) {
        SharedPreferences prefs = getSharedPreferences("BrailleAppSave", MODE_PRIVATE);

        // 💡 내부 저장소에서 단어에 일치하는 캐싱된 비트 패턴 수신
        String savedPattern = prefs.getString("pattern_" + word, "");

        if (savedPattern.isEmpty()) {
            Toast.makeText(this, "해당 단어의 점자 패턴 데이터를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog brailleDialog = new Dialog(this);
        brailleDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        brailleDialog.setContentView(R.layout.dialog_braille_review);

        if(brailleDialog.getWindow() != null) {
            brailleDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView wordText = brailleDialog.findViewById(R.id.wordText);
        wordText.setText(word);

        // 6개의 점자 칸(Cell) 레이아웃 참조 배열 초기화
        android.widget.GridLayout[] cells = new android.widget.GridLayout[6];
        for(int i = 0; i < 6; i++) {
            int resID = getResources().getIdentifier("cell" + (i + 1), "id", getPackageName());
            cells[i] = brailleDialog.findViewById(resID);
        }

        // 36개의 이미지 뷰(Dot) 참조 배열 초기화
        ImageView[] dots = new ImageView[36];
        for(int i = 0; i < 36; i++) {
            int resID = getResources().getIdentifier("dot" + (i + 1), "id", getPackageName());
            dots[i] = brailleDialog.findViewById(resID);
        }

        // 비트 문자열 길이에 따른 필요 칸 수 동적 계산 (6비트 = 1칸)
        int requiredCells = (int) Math.ceil(savedPattern.length() / 6.0);
        if (requiredCells > 6) requiredCells = 6;

        // 💡 필요한 칸만 가시화하고 나머지는 레이아웃에서 숨김(GONE) 처리
        for(int i = 0; i < 6; i++) {
            if (cells[i] != null) {
                if (i < requiredCells) {
                    cells[i].setVisibility(android.view.View.VISIBLE);
                } else {
                    cells[i].setVisibility(android.view.View.GONE);
                }
            }
        }

        // 불러온 0과 1의 비트 배열에 매칭하여 점자 활성화/비활성화 이미지 교체
        for(int i = 0; i < 36; i++) {
            if (dots[i] != null) {
                if (i < savedPattern.length() && savedPattern.charAt(i) == '1') {
                    dots[i].setImageResource(R.drawable.dot_active);
                } else {
                    dots[i].setImageResource(R.drawable.dot_inactive);
                }
            }
        }

        Button closeBtn = brailleDialog.findViewById(R.id.closeBtn);
        closeBtn.setOnClickListener(v -> brailleDialog.dismiss());

        brailleDialog.show();
    }
}