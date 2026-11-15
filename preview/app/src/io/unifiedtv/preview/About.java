package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Settings > Updates & About (SET-082, SET-084): what this build is, the open-source licences of what it bundles, and
 * the artwork's attribution, read from the same catalogue the Wallpaper uses (assets/wallpaper/catalogue.json). Pure.
 */
final class About {
    private About(){}
    /** Runtime libraries packaged in the app (preview/app/build.gradle and their transitive runtime dependencies). */
    static final String[][] LIBRARIES={
        {"Unified TV","MIT License","Original project code; upstream MIT, Free My TV and CleanYourTV notices preserved"},
        {"AndroidX Media3 ExoPlayer, HLS and UI 1.5.1","Apache License 2.0","Android Open Source Project, github.com/androidx/media"},
        {"AndroidX libraries used by Media3 (annotation, core, collection and others)","Apache License 2.0","Android Open Source Project"},
        {"Guava (used by Media3)","Apache License 2.0","Google, github.com/google/guava"},
        {"OkHttp 4.12.0","Apache License 2.0","Square, github.com/square/okhttp"},
        {"Okio","Apache License 2.0","Square, github.com/square/okio"},
        {"Kotlin standard library (used by OkHttp)","Apache License 2.0","JetBrains, github.com/JetBrains/kotlin"},
        {"QR Code generator library (Java)","MIT License","Copyright (c) Project Nayuki, nayuki.io/page/qr-code-generator-library; draws phone setup's QR codes"},
    };
    static String licences(){
        StringBuilder b=new StringBuilder();
        for(String[] l:LIBRARIES)b.append(b.length()==0?"":"\n\n").append(l[0]).append("\n").append(l[1]).append(" · ").append(l[2]);
        // TMDB's terms ask for this notice wherever its data or pictures are used (Films & TV posters, by title).
        b.append("\n\nFilm and programme posters (optional, with your own key): The Movie Database (TMDB), themoviedb.org. ").append(Tmdb.ATTRIBUTION);
        // OD-22: football fixtures and team names come from TheSportsDB by default; API-Football only with the household's own key.
        b.append("\n\n").append(SportsDb.ATTRIBUTION).append(" Used through its free public API; no account. Scores, crests and pictures from it are never shown.");
        b.append("\n\nMore football detail (optional, with your own key): API-Football (api-football.com, API-SPORTS).");
        return b.append("\n\nApp icons on the Apps screen come from the installed apps themselves. Home's tile symbols, the team badge and the utility icons are drawn by Unified TV; no brand artwork or club crest is bundled. The YouTube, Netflix, Disney+ and BBC iPlayer names and marks belong to their owners; their simple white marks are drawn only to identify the app a tile opens.").toString();
    }
    /** "Title: credit" for each picture in the artwork catalogue; empty when it can't be read. */
    static List<String> artwork(String catalogue){
        List<String> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(catalogue);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String title=o.optString("title").trim(),credit=o.optString("credit").trim();if(title.isEmpty()&&credit.isEmpty())continue;
            out.add((title.isEmpty()?o.optString("file"):title)+(credit.isEmpty()?"":": "+credit));}}catch(Exception ignored){}
        return out;
    }
    /** "Build 1a2b3c4 · run 57 · 2026-10-25" for this build (BuildConfig, from CI; "local" for a build made elsewhere). */
    static String build(){return build(BuildConfig.BUILD_SHA,BuildConfig.BUILD_RUN,BuildConfig.BUILD_DATE);}
    static String build(String sha,String run,String date){
        String s=sha==null||!sha.matches("[0-9a-f]{7}")?"local":sha,r=run==null||!run.matches("[0-9]{1,10}")?"local":run;
        return "Build "+s+" · run "+r+(date==null||date.isEmpty()?"":" · "+date);
    }
    /** The app's identity in words: which of the two apps this is (they are separately installed packages). */
    static String flavour(String pkg){
        if(pkg==null)return "";
        String review=pkg.endsWith(".devicecheck")?" (review build)":"";
        if(pkg.startsWith("io.unifiedtv.launcher"))return "Unified TV launcher"+review+": can become the Home screen when you choose it";
        return "Unified TV Preview"+review+": never the Home screen";
    }
}
