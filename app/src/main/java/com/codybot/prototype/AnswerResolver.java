package com.codybot.prototype;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnswerResolver {
    private static JSONObject localDb = null;

    public static synchronized void init(Context context) {
        if (localDb != null) return;
        try {
            InputStream is = context.getAssets().open("answers.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer); is.close();
            localDb = new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) { localDb = new JSONObject(); }
    }

    public static String normalizeText(String text) {
        if (text == null) return "";
        String clean = text.toLowerCase()
                .replaceAll("[^a-z0-9àèéìòùáéíóú\\s]", " ")
                .replaceAll("\\s+", " ").trim();
        return Normalizer.normalize(clean, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private static String cleanAnswer(String s) {
        if (s == null) return null;
        s = s.replaceAll("<[^>]+>", " ").replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'").replaceAll("&amp;", "&").trim();
        s = s.replaceAll("(?i)^(risposta|soluzione)\\s*[:\\-]?\\s*", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 60) return null;
        if (s.matches(".*[.!?].*")) return null;
        return s.toUpperCase();
    }

    private static boolean validAnswer(String answer, int expectedLength) {
        if (answer == null) return false;
        String letters = answer.replaceAll("[^A-ZÀÈÉÌÒÙ]", "");
        if (letters.isEmpty() || letters.length() > 30) return false;
        if (expectedLength > 0 && letters.length() != expectedLength) return false;
        String n = normalizeText(answer);
        return !n.equals("qui") && !n.equals("risposta") && !n.equals("soluzione") && !n.equals("non trovata");
    }

    public static String resolve(Context context, String clue, int expectedLength) {
        if (clue == null || clue.trim().isEmpty()) return "Nessun indizio letto";
        init(context);
        String cleanClue = normalizeText(clue);

        if (localDb != null) {
            try {
                if (localDb.has(cleanClue)) {
                    String ans = localDb.getString(cleanClue).toUpperCase().trim();
                    if (validAnswer(ans, expectedLength)) return ans;
                }
                Iterator<String> keys = localDb.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (cleanClue.contains(key) || key.contains(cleanClue)) {
                        String ans = localDb.getString(key).toUpperCase().trim();
                        if (validAnswer(ans, expectedLength)) return ans;
                    }
                }
            } catch (Exception ignored) {}
        }

        String online = onlineSearch(cleanClue, expectedLength);
        if (online != null) return online;
        return "NON TROVATA [archivio locale + ricerca online]";
    }

    private static String onlineSearch(String clue, int expectedLength) {
        HttpURLConnection c = null;
        try {
            String q = URLEncoder.encode("CodyCross soluzione " + clue + " " + (expectedLength > 0 ? expectedLength + " lettere" : ""), "UTF-8");
            URL u = new URL("https://html.duckduckgo.com/html/?q=" + q);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodyBot/3.8");
            c.setConnectTimeout(4500); c.setReadTimeout(4500); c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) html.append(line).append('\n');
            br.close();
            String text = html.toString().replaceAll("<[^>]+>", " ").replaceAll("&quot;", "\"")
                    .replaceAll("&#x27;", "'").replaceAll("&amp;", "&").replaceAll("\\s+", " ");
            Pattern p = Pattern.compile("(?i)(?:risposta|soluzione)[^A-ZÀÈÉÌÒÙ]{0,12}([A-ZÀÈÉÌÒÙ][A-ZÀÈÉÌÒÙ' -]{1,29})");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String candidate = cleanAnswer(m.group(1));
                if (validAnswer(candidate, expectedLength)) return candidate;
            }
        } catch (Exception ignored) {
        } finally { if (c != null) c.disconnect(); }
        return null;
    }
}
