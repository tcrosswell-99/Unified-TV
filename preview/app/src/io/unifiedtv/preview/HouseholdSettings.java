package io.unifiedtv.preview;
import android.content.Context;
import android.content.SharedPreferences;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Household settings, not hardcoding (owner, 25 October 2026, answering SET-011 and SET-074). Time follows the TV's own
 * time zone unless an adult overrides it; the 24-hour clock follows the TV's own setting; words and dates use the TV's
 * locale; the Kids rating scheme and the dock are settings with defaults from this TV, never one household's choices.
 *
 * Migration (SET-015): an install that already has settings keeps exactly what it showed before: its dock (the earlier
 * curated six are written down as its saved dock if it never edited one) and the UK and US rating table it used. Nothing
 * saved is ever overwritten. A fresh install gets suggestions from what is actually on the TV.
 */
final class HouseholdSettings {
    private HouseholdSettings(){}
    static final String PREFS="unified-tv-preview",VERSION="settingsVersion",ZONE="timeZone",RATINGS="ratingScheme";
    static final int CURRENT=1;
    /** The six tiles every earlier install started with: used only to write an existing install's dock down (migration). */
    static final String[] EARLIER_DOCK={"smartutube","netflix","disney","bbc","live","music"};

    // --- pure
    /** The household's zone: a valid override, else the TV's own. */
    static ZoneId zone(String override,ZoneId device){
        if(override!=null&&!override.trim().isEmpty())try{return ZoneId.of(override.trim());}catch(RuntimeException ignored){}
        return device==null?ZoneOffset.UTC:device;
    }
    static String clockPattern(boolean twentyFour){return twentyFour?"HH:mm":"h:mm";}
    /** Kids ratings by the TV's region: the BBFC in the UK, otherwise the US film and TV ratings. */
    static KidsPolicy.Scheme defaultScheme(String country){
        String c=country==null?"":country.trim().toUpperCase(Locale.ROOT);
        return c.equals("GB")||c.equals("UK")?KidsPolicy.Scheme.UK_BBFC:KidsPolicy.Scheme.US_MPA_TV;
    }
    /** The zones offered as an override: region/city names only, sorted; no "Etc/" or legacy aliases. */
    static List<String> zones(Collection<String> available){
        List<String> out=new ArrayList<>();
        for(String z:available)if(z.matches("(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/[A-Za-z_/-]+"))out.add(z);
        Collections.sort(out);return out;
    }
    /**
     * What to write once, as {key: value}; a null value removes the key. Empty when already migrated. "existing" is every
     * saved preference; a fresh install has none.
     */
    static Map<String,Object> migrate(Map<String,?> existing,String room,String country){
        Map<String,Object> out=new LinkedHashMap<>();
        Object v=existing.get(VERSION);if(v instanceof Integer&&(Integer)v>=CURRENT)return out;
        boolean earlier=!existing.isEmpty();
        if(earlier){
            if(!existing.containsKey("dock")){List<String> d=new ArrayList<>(Arrays.asList(EARLIER_DOCK));if("sony".equals(room))d.add("xbox");if("frame".equals(room))d.add("tvplus");out.put("dock",String.join(",",d));}
            if(!existing.containsKey(RATINGS))out.put(RATINGS,KidsPolicy.Scheme.UK_AND_US.id);
            // SET-063: an adult's "passed check" no longer unlocks Live TV; only an automatic check could. Forget the record.
            if(existing.containsKey("liveProtection"))out.put("liveProtection",null);
        }else out.put(RATINGS,defaultScheme(country).id);
        out.put(VERSION,CURRENT);
        return out;
    }

    // --- Android
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
    /** Runs the migration once, before anything else writes a setting. Safe to call from every entry point. */
    static synchronized void ensure(Context c){
        SharedPreferences p=prefs(c);if(p.getInt(VERSION,0)>=CURRENT)return;
        String room=p.getString("room",android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        Map<String,Object> changes=migrate(p.getAll(),room,Locale.getDefault().getCountry());
        SharedPreferences.Editor e=p.edit();
        for(Map.Entry<String,Object> x:changes.entrySet()){Object val=x.getValue();
            if(val==null)e.remove(x.getKey());else if(val instanceof Integer)e.putInt(x.getKey(),(Integer)val);else e.putString(x.getKey(),String.valueOf(val));}
        e.commit();
    }
    static ZoneId zone(Context c){return zone(prefs(c).getString(ZONE,""),ZoneId.systemDefault());}
    static boolean twentyFour(Context c){try{return android.text.format.DateFormat.is24HourFormat(c);}catch(RuntimeException e){return true;}}
    static DateTimeFormatter clock(Context c){return DateTimeFormatter.ofPattern(clockPattern(twentyFour(c)),Locale.getDefault());}
    /** A time of day on the household's clock. */
    static String time(Context c,long at){return Instant.ofEpochMilli(at).atZone(zone(c)).format(clock(c));}
    /** Every app with a TV or phone launcher entry, by package (Unified TV itself left out). */
    static Set<String> installedPackages(Context c){
        Set<String> out=new HashSet<>();android.content.pm.PackageManager pm=c.getPackageManager();
        for(String category:new String[]{android.content.Intent.CATEGORY_LEANBACK_LAUNCHER,android.content.Intent.CATEGORY_LAUNCHER})
            try{for(android.content.pm.ResolveInfo r:pm.queryIntentActivities(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(category),0))
                if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(c.getPackageName()))out.add(r.activityInfo.packageName);}catch(RuntimeException ignored){}
        return out;
    }
    /** The dock for this room: the household's saved one, or (never edited) the suggestion from installed apps. */
    static List<String> dock(Context c,String room){
        SharedPreferences p=prefs(c);String saved=p.contains("dock")?p.getString("dock",""):null;
        return DockPolicy.order(room,saved,saved!=null?Collections.<String>emptyList():DockPolicy.suggest(room,installedPackages(c),!LiveTvProtection.livePackage(c).isEmpty()));
    }
    static KidsPolicy.Scheme scheme(Context c){
        String saved=prefs(c).getString(RATINGS,"");
        return saved.isEmpty()?defaultScheme(Locale.getDefault().getCountry()):KidsPolicy.Scheme.of(saved);
    }
}
