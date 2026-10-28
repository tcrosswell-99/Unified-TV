package io.unifiedtv.preview;

import java.util.*;

/**
 * SET-021 and SET-BUILD-06: apps on Home that are no longer installed. Home keeps their place (never silently removed or
 * reinstalled); Settings > Apps & Shortcuts lists them, plainly, with a choice per tile: replace it with an installed app,
 * remove it from Home (nothing is uninstalled), or keep it for now. Unified TV's own tiles (Library, Xbox, Samsung TV
 * Plus) are never "missing"; Live TV is missing only when no Live TV app is recognised. Pure.
 */
final class DockRepair {
    private DockRepair(){}

    /** Whether a dock tile has an app behind it. {@code launchable} is {package: label} of installed apps. */
    static boolean installed(String id,Map<String,String> launchable,boolean liveTv){
        if(id.equals(DockPolicy.LIBRARY)||id.equals("xbox")||id.equals("tvplus"))return true;
        if(id.equals("live"))return liveTv;
        String[] known=DockPolicy.packages(id);
        if(known.length>0){for(String p:known)if(launchable.containsKey(p))return true;
            // Home also recognises a known app by its installed name (an id this list doesn't know yet).
            String hint=hint(id);if(hint!=null)for(String label:launchable.values())if(label!=null&&label.toLowerCase(Locale.ROOT).contains(hint))return true;
            return false;}
        return launchable.containsKey(id);
    }
    private static String hint(String id){switch(id){case "smartutube":return "smarttube";case "netflix":return "netflix";case "disney":return "disney";case "bbc":return "iplayer";case "music":return "amazon music";default:return null;}}
    /** The tiles on Home with no app behind them, in dock order. */
    static List<String> missing(List<String> dock,Map<String,String> launchable,boolean liveTv){
        List<String> out=new ArrayList<>();for(String id:dock)if(!installed(id,launchable,liveTv))out.add(id);return out;
    }
    /** A tile's name in words: Unified TV's own names, else the installed label, else the package id. */
    static String name(String id,Map<String,String> launchable){
        switch(id){case "smartutube":return DockLabels.name("smartutube");case "netflix":return "Netflix";case "disney":return "Disney+";case "bbc":return "BBC iPlayer";
            case "music":return "Music";case "live":return "Live TV";case "xbox":return "Xbox";case "tvplus":return "Samsung TV Plus";case DockPolicy.LIBRARY:return "Library";default:break;}
        String l=launchable.get(id);return l==null||l.trim().isEmpty()?id:l;
    }
    /** Installed apps that could take a missing tile's place: not on Home already (by tile id), sorted by name. */
    static List<String> candidates(String room,List<String> dock,Map<String,String> launchable){
        Set<String> on=new HashSet<>();for(String id:dock)on.add(DockPolicy.canonical(id));
        List<String> out=new ArrayList<>();
        for(String pkg:launchable.keySet()){String id=DockPolicy.canonical(pkg);if(!on.contains(id)&&DockPolicy.valid(room,id)&&!out.contains(id))out.add(id);}
        out.sort(Comparator.comparing(id->name(id,launchable).toLowerCase(Locale.ROOT)));
        return out;
    }
}
