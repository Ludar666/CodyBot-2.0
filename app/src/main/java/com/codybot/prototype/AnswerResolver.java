package com.codybot.prototype;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.SharedPreferences;

public class AnswerResolver {
    private static JSONObject localDb = null;
    private static final String PREFS="codybot_archive";
    private static final String KEY="answers";

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
        String clean = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase()
                .replaceAll("(?<=[a-z])0(?=[a-z])", "o")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return clean;
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

        String cached = getLearned(context, cleanClue, expectedLength);
        if (cached != null) return cached;

        String structured = structuredSearch(cleanClue, expectedLength);
        if (structured != null) { saveLearned(context, cleanClue, structured); return structured; }

        return "NON TROVATA [archivio locale + ricerca strutturata]";
    }

    /** Search CodyCrossSoluzioni first, with Cruciverba.io as backup. */
    private static String structuredSearch(String clue, int expectedLength) {
        String answer = searchCodyCrossSoluzioni(clue, expectedLength);
        if (answer != null) return answer;
        return searchCruciverba(clue, expectedLength);
    }

    private static String searchCodyCrossSoluzioni(String clue, int expectedLength) {
        String direct = fetchCodyCrossUrl("https://codycrosssoluzioni.com/" + buildSlug(clue), clue, expectedLength);
        if (direct != null) return direct;
        String api = searchCodyCrossWordPress(clue, expectedLength);
        if (api != null) return api;
        return searchDomain("site:codycrosssoluzioni.com " + clue, "codycrosssoluzioni.com", clue, expectedLength, true);
    }

    private static String searchCodyCrossWordPress(String clue, int expectedLength) {
        HttpURLConnection c = null;
        try {
            String encoded = URLEncoder.encode(clue, "UTF-8");
            URL u = new URL("https://codycrosssoluzioni.com/wp-json/wp/v2/search?search=" + encoded + "&per_page=8");
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodyBot/4.0");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) json.append(line);
            br.close();

            Matcher links = Pattern.compile("\"url\"\\s*:\\s*\"(https://codycrosssoluzioni\\.com/[^\"]+)\"",
                    Pattern.CASE_INSENSITIVE).matcher(json.toString());
            int tried = 0;
            while (links.find() && tried < 8) {
                String link = links.group(1).replace("\\/", "/");
                tried++;
                String answer = fetchCodyCrossUrl(link, clue, expectedLength);
                if (answer != null) return answer;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    private static String searchCruciverba(String clue, int expectedLength) {
        String direct = fetchCruciverbaUrl("https://cruciverba.io/" + buildSlug(clue), clue, expectedLength);
        if (direct != null) return direct;
        return searchDomain("site:cruciverba.io " + clue, "cruciverba.io", clue, expectedLength, false);
    }

    private static String searchDomain(String query, String domain, String clue, int expectedLength, boolean codySite) {
        HttpURLConnection c = null;
        try {
            String encoded = URLEncoder.encode(query, "UTF-8");
            URL u = new URL("https://html.duckduckgo.com/html/?q=" + encoded);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodyBot/4.0");
            c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) html.append(line).append('\n');
            br.close();
            Matcher links = Pattern.compile("uddg=([^&\\\"]+)", Pattern.CASE_INSENSITIVE).matcher(html.toString());
            int tried = 0;
            while (links.find() && tried < 8) {
                try {
                    String link = URLDecoder.decode(links.group(1), "UTF-8");
                    if (!link.startsWith("https://" + domain + "/")) continue;
                    tried++;
                    String answer = codySite ? fetchCodyCrossUrl(link, clue, expectedLength) : fetchCruciverbaUrl(link, clue, expectedLength);
                    if (answer != null) return answer;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {} finally { if (c != null) c.disconnect(); }
        return null;
    }

    private static String buildSlug(String clue) {
        return normalizeText(clue).replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-").replaceAll("-+", "-");
    }

    private static String fetchCodyCrossUrl(String pageUrl, String clue, int expectedLength) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(pageUrl);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodyBot/4.0");
            c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) html.append(line).append('\n');
            br.close();
            String text = html.toString().replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ").replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ").replaceAll("&quot;", "\\\"")
                    .replaceAll("&#39;", "'").replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ").trim();
            String normalizedPage = normalizeText(text);
            String[] words = clue.split(" "); int relevant = 0, found = 0;
            for (String word : words) { if (word.length() < 3) continue; relevant++; if (normalizedPage.contains(normalizeText(word))) found++; }
            if (relevant > 0 && found < Math.max(1, (int)Math.ceil(relevant * 0.60))) return null;

            Pattern p = Pattern.compile(
                    "(?is)La\\s+soluzione\\s+(?:è|e)\\s+([A-ZÀÈÉÌÒÙ][A-ZÀÈÉÌÒÙ' -]{1,49}?)\\s+(\\d{1,2})\\s+Lettere");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String candidate = cleanAnswer(m.group(1));
                int pageLength;
                try { pageLength = Integer.parseInt(m.group(2)); } catch (Exception ex) { pageLength = -1; }
                if (pageLength > 0 && candidate != null
                        && candidate.replaceAll("[^A-ZÀÈÉÌÒÙ]", "").length() != pageLength) continue;
                if (validAnswer(candidate, expectedLength)) return candidate;
            }
        } catch (Exception ignored) {} finally { if (c != null) c.disconnect(); }
        return null;
    }

    private static String fetchCruciverbaUrl(String pageUrl, String clue, int expectedLength) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(pageUrl);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) CodyBot/3.8");
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) html.append(line).append('\n');
            br.close();

            String text = html.toString()
                    .replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("&#39;", "'")
                    .replaceAll("&amp;", "&")
                    .replaceAll("\\s+", " ")
                    .trim();

            String normalizedPage = normalizeText(text);
            String[] words = clue.split(" ");
            int relevant = 0;
            int found = 0;
            for (String word : words) {
                if (word.length() < 3) continue;
                relevant++;
                if (normalizedPage.contains(normalizeText(word))) found++;
            }
            if (relevant > 0 && found < Math.max(1, (int)Math.ceil(relevant * 0.50))) return null;

            Pattern p = Pattern.compile(
                    "(?i)Risposta(?:\\s+di\\s+\\d+\\s+lettere)?\\s+([A-ZÀÈÉÌÒÙ][A-ZÀÈÉÌÒÙ' -]{1,29})\\s*\\(\\d+\\s+lettere\\)");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String candidate = cleanAnswer(m.group(1));
                if (validAnswer(candidate, expectedLength)) return candidate;
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    private static String getLearned(Context context,String clue,int expectedLength){
        try{
            SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            String json=p.getString(KEY,"{}");
            JSONObject db=new JSONObject(json);
            if(!db.has(clue))return null;
            String ans=db.getString(clue);
            return validAnswer(ans,expectedLength)?ans:null;
        }catch(Exception ignored){return null;}
    }

    private static void saveLearned(Context context,String clue,String answer){
        try{
            if(!validAnswer(answer,-1))return;
            SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            JSONObject db=new JSONObject(p.getString(KEY,"{}"));
            db.put(clue,answer);
            p.edit().putString(KEY,db.toString()).apply();
        }catch(Exception ignored){}
    }

    public static int archiveSize(Context context){
        try{return new JSONObject(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,"{}")).length();}
        catch(Exception ignored){return 0;}
    }

    public static void clearLearnedArchive(Context context){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static String onlineSearch(String clue, int expectedLength) {
        return null;
    }
}
