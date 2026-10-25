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
 * Tiles never show text or a full-colour app icon: each draws a simple original glyph (glyph()); an app with no glyph of
 * its own draws its installed icon in monochrome, small, on the same glass tile.
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

    /** The original glyph each tile draws, by tile id or package; "mono" for an app without one of its own. */
    static String glyph(String id){
        switch(id){
            case "smartutube":case "com.google.android.youtube.tv":case "com.google.android.youtube.tvkids":return "play";
            case "netflix":return "n";
            case "disney":return "arcplus";
            case "bbc":return "blocks";
            case "live":return "broadcast";
            case "music":case "com.spotify.tv.android":return "note";
            case "xbox":return "controller";
            case "tvplus":case "com.apple.atve.androidtv.appletv":return "screen";
            case "com.amazon.amazonvideo.livingroom":return "playcircle";
            case "com.plexapp.android":return "chevron";
            case DockPolicy.LIBRARY:return "shelf";
            default:return "mono";
        }
    }
    static final Set<String> GLYPHS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("play","n","arcplus","blocks","broadcast","note","controller","screen","playcircle","chevron","shelf","mono")));
}
