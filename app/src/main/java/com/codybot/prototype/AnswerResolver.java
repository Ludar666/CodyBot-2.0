package com.codybot.prototype;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;

public class AnswerResolver {
    private static JSONObject localDb = null;

    public static synchronized void init(Context context) {
        if (localDb != null) return;
        try {
            InputStream is = context.getAssets().open("answers.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            localDb = new JSONObject(jsonStr);
        } catch (Exception e) {
            localDb = new JSONObject();
        }
    }

    public static String normalizeText(String text) {
        if (text == null) return "";
        String clean = text.toLowerCase()
                .replaceAll("[^a-z0-9àèéìòùáéíóú\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public static String resolve(Context context, String clue, int expectedLength) {
        if (clue == null || clue.trim().isEmpty()) {
            return "Nessun indizio letto";
        }

        init(context);
        String cleanClue = normalizeText(clue);

        if (localDb != null && localDb.has(cleanClue)) {
            try {
                String ans = localDb.getString(cleanClue).toUpperCase().trim();
                if (expectedLength <= 0 || ans.length() == expectedLength) {
                    return ans;
                }
            } catch (Exception ignored) {}
        }

        if (localDb != null) {
            try {
                Iterator<String> keys = localDb.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (cleanClue.contains(key) || key.contains(cleanClue)) {
                        String ans = localDb.getString(key).toUpperCase().trim();
                        if (expectedLength <= 0 || ans.length() == expectedLength) {
                            return ans;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return "Non in archivio (" + (expectedLength > 0 ? expectedLength + " lettere" : "?") + ")";
    }
}
