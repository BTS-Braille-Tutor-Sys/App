package com.example.app1;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GeminiHelper {

    // 🚨 올려주신 로그에 있던 본인의 API 키를 여기에 정확히 넣어주세요 🚨
    private static final String API_KEY = "AQ.Ab8RN6KUYWcVwb3uB8rcKzkkkxd_Nd0Irpm0Td6SeSz4xAfbxg";
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    public interface GeminiCallback {
        void onSuccess(String word);
        void onError(String error);
    }

    public static void generateRandomWord(GeminiCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                // [수정] AI가 단어를 생성할 수 있도록 시간을 15초로 넉넉하게 줍니다.
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(30000);
                conn.setDoOutput(true);

                // [핵심] 무조건 2글자만 나오게 하는 프롬프트 적용
                String prompt = "점자 학습을 위한 2글자의 명사 한글 단어를 무작위로 딱 1개만 생성해줘. (예: 사과, 바다, 우유, 포도). 설명이나 부가적인 말, 기호, 마침표 없이 오직 2글자 단어 딱 하나만 대답해.";
                String jsonInputString = "{\"contents\": [{\"parts\":[{\"text\": \"" + prompt + "\"}]}]}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // 👇 [핵심 업그레이드] 서버의 응답 코드를 먼저 확인합니다! 👇
                int responseCode = conn.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 200번: 통신 대성공! 단어를 빼옵니다.
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONArray candidates = jsonResponse.getJSONArray("candidates");
                    JSONObject content = candidates.getJSONObject(0).getJSONObject("content");
                    String generatedText = content.getJSONArray("parts").getJSONObject(0).getString("text").trim();

                    new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(generatedText));

                } else {
                    // 🚨 구글 서버가 400, 403 등의 에러로 튕겨냈을 때! 🚨
                    // 구글이 알려주는 진짜 거절 사유를 억지로 읽어와서 로그캣에 빨갛게 띄웁니다.
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine.trim());
                    }

                    Log.e("GEMINI_CRITICAL", "서버 거절 코드: " + responseCode);
                    Log.e("GEMINI_CRITICAL", "에러 상세 내용: " + errorResponse.toString());

                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("서버 에러: " + responseCode));
                }

            } catch (Exception e) {
                // 앱 내부적인 오류 (인터넷 끊김 등)
                Log.e("GEMINI_CRITICAL", "앱 내부 통신 에러: ", e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onError(e.toString()));
            }
        }).start();
    }
}