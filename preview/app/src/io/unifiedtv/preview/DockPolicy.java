package io.unifiedtv.preview;

import java.util.*;

/**
 * Spec 3.3 / P05-03 and SET-011 (owner, 25 October 2026): the dock is the household's own. A fresh install suggests up to
 * six tiles from apps actually on this TV, trying well-known apps in a fixed order; nothing is placed that isn't installed,
 * and fewer than six (or none) is fine. The suggestion follows what is installed until the household edits the dock; after
 * that its own edits stand exactly. An earlier install keeps the tiles it had (HouseholdSettings migration). Room-only
 * shortcuts never leak to another TV, a known app is never on Home twice under its package name, and nothing that isn't
 * a known id or a package name survives.
 *
 * "library" (owner, 25 October 2026) is Unified TV's own Library entry: offered in every room from Apps, and placed,
 * moved or removed like any tile; never added to a layout by itself.
 */
final class DockPolicy {
    /** Tiles Unified TV draws itself, with their palettes (AmbientMesh) and package names; not a default dock. */
    static final String[] KNOWN={"smartutube","netflix","disney","bbc","live","music"};
    static final String LIBRARY="library";
    /** How many tiles a fresh install suggests: six, whatever the dock size (DockCapacity); eight places are never auto-filled. */
    static final int SLOTS=6;
    private static final Map<String,String[]> PACKAGES=new LinkedHashMap<>();
    static{
        // SmartTube is not in the Play Store; these are the ids its own releases have used. org.smarttube.stable and
        // com.teamsmart.videomanager.tv are UNVERIFIED on a real TV (recognised only if installed; never installed by us).
        PACKAGES.put("smartutube",new String[]{"org.smarttube.beta","com.liskovsoft.smarttubetv.beta","com.liskovsoft.smarttubetv","org.smarttube.stable","com.teamsmart.videomanager.tv"});
        PACKAGES.put("netflix",new String[]{"com.netflix.ninja"});
        PACKAGES.put("disney",new String[]{"com.disney.disneyplus"});
        PACKAGES.put("bbc",new String[]{"uk.co.freeview.bbc","bbc.iplayer.android","bbc.iplayer.android.tv"});
        PACKAGES.put("music",new String[]{"com.amazon.mp3","com.amazon.music.tv"});
    }
    /**
     * The suggestion heuristic only: well-known TV apps in the order they are tried. An entry is a known tile id or a
     * package name; each is placed only when installed ("live" only when exactly one Live TV app is recognised).
     */
    static final String[] SUGGESTION_ORDER={"smartutube","netflix","disney","bbc","live","music",
        "com.google.android.youtube.tv","com.amazon.amazonvideo.livingroom","com.apple.atve.androidtv.appletv",
        "com.spotify.tv.android","com.plexapp.android","com.google.android.youtube.tvkids"};
    static String[] packages(String id){String[] p=PACKAGES.get(id);return p==null?new String[0]:p;}
    /** Unified TV's own entries for a room, offered in Apps so a removed one can always be put back. */
    static List<String> shortcuts(String room){
        List<String> d=new ArrayList<>(Arrays.asList(LIBRARY,"live"));
        if("sony".equals(room))d.add("xbox");
        if("frame".equals(room))d.add("tvplus");
        return d;
    }
    /** Up to six suggestions from what is installed, in the heuristic's order; possibly none. */
    static List<String> suggest(String room,Collection<String> installed,boolean liveTv){
        List<String> out=new ArrayList<>();Set<String> have=installed==null?Collections.emptySet():new HashSet<>(installed);
        for(String id:SUGGESTION_ORDER){
            if(out.size()>=SLOTS)break;
            boolean present=id.equals("live")?liveTv:PACKAGES.containsKey(id)?anyOf(packages(id),have):have.contains(id);
            if(present&&valid(room,id)&&!out.contains(id))out.add(id);
        }
        return out;
    }
    private static boolean anyOf(String[] packages,Set<String> have){for(String p:packages)if(have.contains(p))return true;return false;}
    /** A known app's package maps back to its tile id, so it can't appear twice. */
    static String canonical(String id){for(Map.Entry<String,String[]> e:PACKAGES.entrySet())for(String p:e.getValue())if(p.equals(id))return e.getKey();return id;}
    static boolean valid(String room,String id){
        if(id.equals("xbox"))return "sony".equals(room);
        if(id.equals("tvplus"))return "frame".equals(room);
        if(id.equals(LIBRARY))return true;
        return Arrays.asList(KNOWN).contains(id)||id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    }
    /** saved == null: never edited, so no tiles of its own (see the three-argument form). */
    static List<String> order(String room,String saved){return order(room,saved,Collections.emptyList());}
    /** saved == null: never edited, so the suggestion. "" is a deliberate empty Home, kept as the household left it. */
    static List<String> order(String room,String saved,List<String> suggested){
        if(saved==null){List<String> s=new ArrayList<>();for(String id:suggested){String c=canonical(id);if(valid(room,c)&&!s.contains(c))s.add(c);}return s;}
        List<String> result=new ArrayList<>();
        for(String raw:saved.split(",")){String id=canonical(raw.trim());if(!id.isEmpty()&&valid(room,id)&&!result.contains(id))result.add(id);}
        return result;
    }
    static String add(String room,List<String> dock,String id){String c=canonical(id);List<String> next=new ArrayList<>(dock);if(valid(room,c)&&!next.contains(c))next.add(c);return String.join(",",next);}
    static String remove(List<String> dock,String id){String c=canonical(id);List<String> next=new ArrayList<>(dock);next.remove(c);return String.join(",",next);}

    // --- Dock size (DockCapacity, UX-DOCK-08..10): the household's list may be longer than a profile's places.
    /** The tiles a profile with this many places shows: the first ones, in order. */
    static List<String> visible(List<String> dock,int capacity){return new ArrayList<>(dock.subList(0,Math.min(dock.size(),Math.max(0,capacity))));}
    static boolean full(List<String> dock,int capacity){return dock.size()>=capacity;}
    /** Adds into a free place; null when every place is taken (the household chooses a replacement, never us). */
    static String add(String room,List<String> dock,String id,int capacity){
        String c=canonical(id);int at=dock.indexOf(c);
        if(at>=0&&at<capacity)return String.join(",",dock);
        if(full(dock,capacity))return null;
        return add(room,dock,id);
    }
    /** {@code id} takes {@code old}'s place; old leaves Home (it stays installed and in Apps). Unchanged if either is unusable. */
    static String replace(String room,List<String> dock,String old,String id){
        String c=canonical(id),o=canonical(old);List<String> next=new ArrayList<>(dock);int at=next.indexOf(o);
        if(at<0||c.equals(o)||!valid(room,c))return String.join(",",next);
        next.remove(c);at=next.indexOf(o);next.set(at,c);return String.join(",",next);
    }
    /**
     * The wanted tiles added in order while places are free; those that did not fit are added to {@code left}, for the
     * household to place by replacing a tile or to keep in Apps. Tiles already on Home stay where they are.
     */
    static List<String> place(String room,List<String> dock,List<String> wanted,int capacity,List<String> left){
        List<String> next=new ArrayList<>(dock);
        for(String id:wanted){String c=canonical(id);if(!valid(room,c)||visible(next,capacity).contains(c))continue;
            if(next.size()<capacity&&!next.contains(c))next.add(c);else if(left!=null&&!left.contains(c))left.add(c);}
        return next;
    }
    /** Moves the tile at {@code from} one place earlier (-1) or later (+1); unchanged at either end. */
    static List<String> move(List<String> dock,int from,int delta){
        List<String> next=new ArrayList<>(dock);int to=from+delta;
        if(from>=0&&from<next.size()&&to>=0&&to<next.size())Collections.swap(next,from,to);
        return next;
    }
}
