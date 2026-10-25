package io.unifiedtv.preview;

import java.util.*;

/**
 * Spec 3.3 / P05-03: a fresh install gets the curated defaults; after that the owner's own edits stand. Room-only
 * shortcuts never leak to another TV, a curated app is never on Home twice under its package name, and nothing that
 * isn't a curated id or a package name survives.
 *
 * "library" (owner, 25 October 2026) is Unified TV's own Library entry: not in the defaults (existing layouts are
 * unchanged), offered in every room from Apps, and placed, moved or removed like any tile.
 */
final class DockPolicy {
    static final String[] CURATED={"smartutube","netflix","disney","bbc","live","music"};
    static final String LIBRARY="library";
    private static final Map<String,String[]> PACKAGES=new LinkedHashMap<>();
    static{
        PACKAGES.put("smartutube",new String[]{"org.smarttube.beta","com.liskovsoft.smarttubetv.beta","com.liskovsoft.smarttubetv"});
        PACKAGES.put("netflix",new String[]{"com.netflix.ninja"});
        PACKAGES.put("disney",new String[]{"com.disney.disneyplus"});
        PACKAGES.put("bbc",new String[]{"uk.co.freeview.bbc","bbc.iplayer.android","bbc.iplayer.android.tv"});
        PACKAGES.put("music",new String[]{"com.amazon.mp3","com.amazon.music.tv"});
    }
    static String[] packages(String id){String[] p=PACKAGES.get(id);return p==null?new String[0]:p;}
    static List<String> defaults(String room){
        List<String> d=new ArrayList<>(Arrays.asList(CURATED));
        if("sony".equals(room))d.add("xbox");
        if("frame".equals(room))d.add("tvplus");
        return d;
    }
    /** A curated app's package maps back to its curated id, so it can't appear twice. */
    static String canonical(String id){for(Map.Entry<String,String[]> e:PACKAGES.entrySet())for(String p:e.getValue())if(p.equals(id))return e.getKey();return id;}
    static boolean valid(String room,String id){
        if(id.equals("xbox"))return "sony".equals(room);
        if(id.equals("tvplus"))return "frame".equals(room);
        if(id.equals(LIBRARY))return true;
        return Arrays.asList(CURATED).contains(id)||id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    }
    /** saved == null: never edited, so the curated defaults. "" is a deliberate empty Home, kept as the owner left it. */
    static List<String> order(String room,String saved){
        if(saved==null)return defaults(room);
        List<String> result=new ArrayList<>();
        for(String raw:saved.split(",")){String id=canonical(raw.trim());if(!id.isEmpty()&&valid(room,id)&&!result.contains(id))result.add(id);}
        return result;
    }
    static String add(String room,List<String> dock,String id){String c=canonical(id);List<String> next=new ArrayList<>(dock);if(valid(room,c)&&!next.contains(c))next.add(c);return String.join(",",next);}
    static String remove(List<String> dock,String id){String c=canonical(id);List<String> next=new ArrayList<>(dock);next.remove(c);return String.join(",",next);}
}
