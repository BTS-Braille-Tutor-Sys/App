package com.example.app1;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;

public class WordManager {

    // 기존에 단계 잠금 해제에 쓰시던 저장소 이름을 그대로 공유합니다.
    private static final String PREF_NAME = "BrailleAppSave";
    private static final String KEY_WORDS = "AiSavedWords"; // 단어장 꼬리표

    // 1. 제미나이가 만든 단어를 단어장에 추가(저장)하는 기능
    public static void saveWord(Context context, String newWord) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedWords = prefs.getString(KEY_WORDS, "");

        if (savedWords.isEmpty()) {
            // 처음 저장하는 경우
            prefs.edit().putString(KEY_WORDS, newWord).apply();
        } else {
            // "사과나무"가 있다고 "사과"를 중복으로 착각하지 않도록 정확하게 쪼개서 검사
            String[] words = savedWords.split(",");
            for (String w : words) {
                if (w.equals(newWord)) {
                    return; // 완전히 똑같은 단어가 이미 있으면 패스!
                }
            }
            // 중복이 아니면 쉼표(,)로 꼬리표처럼 이어 붙이기
            prefs.edit().putString(KEY_WORDS, savedWords + "," + newWord).apply();
        }
    }

    // 2. 단어장에 적힌 단어들을 쪼개서 리스트(배열)로 가져오는 기능 (시험 볼 때 사용)
    public static ArrayList<String> loadWords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String savedWords = prefs.getString(KEY_WORDS, "");

        ArrayList<String> wordList = new ArrayList<>();
        if (!savedWords.isEmpty()) {
            // 쉼표를 기준으로 가위로 싹둑싹둑 잘라서 리스트에 쏙 넣습니다.
            String[] words = savedWords.split(",");
            wordList.addAll(Arrays.asList(words));
        }
        return wordList;
    }

    // 3. (보너스) 단어장 싹 비우기 - 나중에 테스트/시연할 때 유용합니다.
    public static void clearWords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_WORDS).apply();
    }
}