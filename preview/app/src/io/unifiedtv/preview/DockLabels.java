package io.unifiedtv.preview;
import java.util.*;

/**
 * Dock tiles and their labels (v3.4.4 UX-ICON-01..03, UX-ICON-05/06, SET-026). Pure.
 *
 * Labels sit outside the tiles, by the household's choice per profile (Settings > Appearance & Home > Dock & Icons > App
 * label visibility), saved as dock.labelVisibility: focused_only (the default), always or hidden. Missing or unknown
 * values read as focused_only; Reset appearance writes focused_only again.
 *
 * Composition rule (no label twice): the title above the dock shows the focused app's name only when no label is shown
 * under its tile (hidden), so a focused app is always identifiable, and never in two places at once.
 *
 * Tiles never show a full-colour app icon: each draws a simple white glyph or brand mark (glyph(), drawn by DockGlyphs); an
 * app with no mark of its own draws its own banner, else its icon (both monochrome, filling the tile sensibly), else its name. With eight places
 * (DockCapacity) and Always show, a long name is shortened with an ellipsis to its own place; the one focused label may
 * use two places' width.
 */
final class DockLabels {
    private DockLabels(){}
    static final String KEY="dock.labelVisibility",FOCUSED_ONLY="focused_only",ALWAYS="always",HIDDEN="hidden";
    static final String[] VALUES={FOCUSED_ONLY,ALWAYS,HIDDEN};
    static String value(String saved){return ALWAYS.equals(saved)||HIDDEN.equals(saved)?saved:FOCUSED_ONLY;}
    static String label(String v){switch(value(v)){case ALWAYS:return "Always show labels";case HIDDEN:return "Hide labels";default:return "Focused only";}}
    /** Each profile keeps its own choice. */
    static String key(String profile){return KEY+"."+Profiles.stored(profile);}
    /** Whether tile i shows its label; focused is the focused tile, or -1 while focus is elsewhere (the pill, the Library). */
    static boolean shows(String v,int i,int focused){switch(value(v)){case ALWAYS:return true;case HIDDEN:return false;default:return i==focused;}}
    /** Whether the title above the dock names the focused app (the composition rule above). */
    static boolean titleAbove(String v){return HIDDEN.equals(value(v));}
    /** The name as it fits in {@code max} (as measured by {@code width}): whole, or shortened at the end with an ellipsis. */
    static String fit(String name,float max,java.util.function.ToDoubleFunction<String> width){
        String s=name==null?"":name.trim();if(width.applyAsDouble(s)<=max)return s;
        for(int n=s.length()-1;n>0;n--){String t=s.substring(0,n).trim()+"\u2026";if(width.applyAsDouble(t)<=max)return t;}
        return "\u2026";
    }

    /**
     * Owner decision, 25 October 2026 (supersedes UX-ICON-07's separate SmartTube mark), narrowed after the owner's Sony
     * (run 106, two YouTube tiles side by side): the household uses SmartTube as its YouTube, so while YouTube's own app is
     * NOT installed, SmartTube's tile is named "YouTube" and draws the YouTube symbol. When YouTube's own app is installed
     * too (YOUTUBE_APPS), SmartTube is named "SmartTube" and draws its own mark ("smarttube"), so the two never look alike.
     * Detection is unchanged (DockPolicy's SmartTube package ids); only what the household sees changes.
     */
    static final String YOUTUBE="YouTube",SMARTTUBE="SmartTube";
    /** YouTube's own app: the Android TV build, and the phone build some TVs carry. */
    static final String[] YOUTUBE_APPS={"com.google.android.youtube.tv","com.google.android.youtube"};
    /** Whether YouTube's own app is among these installed packages. */
    static boolean youtube(Collection<String> installed){if(installed!=null)for(String p:YOUTUBE_APPS)if(installed.contains(p))return true;return false;}
    /** Unified TV's own name for one of its tiles with YouTube's own app absent (see youtube()). */
    static String name(String id){return name(id,false);}
    /** Unified TV's own name for one of its tiles, as the household sees it; null for an ordinary installed app. */
    static String name(String id,boolean youtubeInstalled){
        switch(DockPolicy.canonical(id==null?"":id)){
            case "smartutube":return youtubeInstalled?SMARTTUBE:YOUTUBE;
            default:return null;
        }
    }
    /** The glyph with YouTube's own app absent (see youtube()). */
    static String glyph(String id){return glyph(id,false);}
    /**
     * The glyph each tile draws (DockGlyphs), by tile id or package; "mono" for an app without one of its own (DockGlyphs
     * then draws the app's own banner or icon, or its name). SmartTube shares the YouTube symbol only while YouTube's own
     * app is absent (see YOUTUBE above). Music services each get their own, so two on Home stay apart (SET-BUILD-10).
     * White brand marks (OD-20): YouTube, Netflix, Disney+, BBC iPlayer, Prime Video and Apple TV.
     */
    static String glyph(String id,boolean youtubeInstalled){
        switch(DockPolicy.canonical(id==null?"":id)){
            case "smartutube":return youtubeInstalled?"smarttube":"play";
            case "com.google.android.youtube.tv":case "com.google.android.youtube":case "com.google.android.youtube.tvkids":return "play";
            case "netflix":return "n";
            case "disney":return "arcplus";
            case "bbc":return "blocks";
            case "live":return "tvset";
            case "music":return "note";
            case "com.spotify.tv.android":return "eq";
            case "com.apple.android.music":return "singlenote";
            case "com.google.android.youtube.tvmusic":return "vinyl";
            case "xbox":return "controller";
            case "tvplus":return "screen";
            case "com.apple.atve.androidtv.appletv":return "appletv";
            case "com.amazon.amazonvideo.livingroom":return "prime";
            case "com.plexapp.android":return "chevron";
            case DockPolicy.LIBRARY:return "shelf";
            default:return "mono";
        }
    }
    /** An empty dock place while it has focus: OK chooses an app for it (DockPicker). */
    static final String ADD="plus",ADD_LABEL="Add an app";
    static final Set<String> GLYPHS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("play","smarttube","n","arcplus","blocks","tvset","note","singlenote","eq","vinyl","controller","screen","appletv","prime","playcircle","chevron","shelf","cast","photos","house","plus","mono")));
}
