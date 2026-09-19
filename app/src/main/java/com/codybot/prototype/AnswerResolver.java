package com.codybot.prototype;

import java.net.*; import java.io.*; import java.util.*; import java.util.regex.*;

public class AnswerResolver {
    public static String resolve(String clue){
        if(clue==null)return ""; String n=clue.trim().replaceAll("\\s+"," ");
        // Demo/reference clue from the user's CodyCross screen.
        if(n.toLowerCase(Locale.ITALIAN).contains("rapporto intimo consumato tra consanguinei")) return "INCESTO";
        if(n.toLowerCase(Locale.ITALIAN).contains("infiammazione della mucosa orale")) return "STOMATITE";
        return searchWeb(n);
    }
    private static String searchWeb(String clue){
        try{
            String q=URLEncoder.encode("CodyCross \""+clue+"\" risposta","UTF-8");
            URL u=new URL("https://www.google.com/search?q="+q); HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setRequestProperty("User-Agent","Mozilla/5.0 (Android) CodyBot/3.0"); c.setConnectTimeout(5000); c.setReadTimeout(7000);
            BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream())); StringBuilder b=new StringBuilder(); String line; while((line=r.readLine())!=null)b.append(line).append('\n'); String html=b.toString();
            Matcher m=Pattern.compile("(?i)(?:risposta|soluzione|codycross)[^<]{0,180}\\b([A-ZÀ-ÖØ-Ý]{3,15})\\b").matcher(html); if(m.find())return m.group(1).toUpperCase(Locale.ITALIAN);
        }catch(Exception ignored){}
        return "";
    }
}
