package io.unifiedtv.preview;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.*;

/**
 * SET-032 Favourites: titles and programmes a profile keeps, chosen in Your Films &amp; TV by holding OK on a tile. Each
 * profile has its own list (module 06: Adults and Kids each keep their own favourites); nothing is shared or synced.
 * A favourite is kept by the tile's key ({@link LibraryCatalogue.Card#key()}): a film or episode by its server id
 * ("t:dlna:&lt;server&gt;:&lt;item id&gt;"), a programme by its own key, so renaming a file on the NAS loses nothing and a
 * different server's title of the same name is never confused with it. Newest first; at most {@link #MAX}.
 *
 * The Favourites row (LibraryCatalogue) shows only favourites in this profile's own catalogue, so a Kids favourite outside
 * the Kids folders or ratings never appears, and a title gone from the NAS simply drops out of the row.
 */
final class Favourites {
    private Favourites(){}
    static final String KEY="favourites";
    static final int MAX=200;
    static String key(String profile){return KEY+"."+Profiles.stored(profile);}

    // --- pure
    /** The saved list (a JSON array of keys, newest first); anything unreadable is an empty list. */
    static List<String> parse(String saved){
        List<String> out=new ArrayList<>();if(saved==null||saved.isEmpty())return out;
        try{JSONArray a=new JSONArray(saved);for(int i=0;i<a.length()&&out.size()<MAX;i++){String k=a.optString(i,"");if(!k.isEmpty()&&!out.contains(k))out.add(k);}}catch(Exception ignored){}
        return out;
    }
    static String write(List<String> keys){JSONArray a=new JSONArray();for(int i=0;i<Math.min(MAX,keys.size());i++)a.put(keys.get(i));return a.toString();}
    /** Added at the front, or removed if it was there. */
    static List<String> toggle(List<String> keys,String key){
        List<String> out=new ArrayList<>(keys);if(!out.remove(key)){out.add(0,key);while(out.size()>MAX)out.remove(out.size()-1);}return out;
    }

    // --- this profile
    static List<String> load(SharedPreferences p){return parse(p.getString(key(Profiles.current(p)),""));}
    static boolean has(SharedPreferences p,String key){return load(p).contains(key);}
    /** Adds or removes {@code key} for the current profile; true when it is now a favourite. */
    static boolean toggle(SharedPreferences p,String key){
        List<String> next=toggle(load(p),key);p.edit().putString(key(Profiles.current(p)),write(next)).apply();return next.contains(key);
    }
    /** For Settings (Privacy &amp; Data, or a profile's reset): how many {@code profile} keeps, and forgetting them all. */
    static int count(SharedPreferences p,String profile){return parse(p.getString(key(profile),"")).size();}
    static void clear(SharedPreferences p,String profile){p.edit().remove(key(profile)).apply();}
    /** The words on the hold-OK menu. */
    static String action(boolean favourite){return favourite?"Remove from Favourites":"Add to Favourites";}
}
