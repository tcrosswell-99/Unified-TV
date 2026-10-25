package io.unifiedtv.preview;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/**
 * Dock size (v3.4.6 UX-DOCK-08..10, SET-027): how many places the dock has, 6 (the default) or 8, chosen per profile under
 * Settings > Appearance & Home > Dock & Icons > Dock size and saved as dock.capacity.adult / dock.capacity.child (the way
 * DockLabels keeps its choice), with dock.capacity.schema = 1. Anything else reads as the default.
 *
 * A capacity, not a mandate: 6 -> 8 only adds empty places (never filled for the household); 8 -> 6 asks which tiles
 * leave Home when more than six are on it (they stay installed and in Apps), and Cancel changes nothing. The household's
 * dock list itself is shared by both profiles, as it always was; a profile with fewer places shows the first ones.
 *
 * Migration: until any profile has chosen a size (no dock.capacity.schema yet), an earlier install that already had more
 * than six tiles on Home reads as 8, so nothing it could reach before disappears. Once a size has been chosen anywhere, a
 * profile that never chose one reads as 6, so the other profile's choice never leaks into it.
 *
 * Geometry: one centred glass dock either way. Eight places scale the six-place tile and spacing (SCALE_8), never eight
 * full-size tiles in a six-tile container; both stay inside the 5 % overscan safe area.
 */
final class DockCapacity {
    private DockCapacity(){}
    static final String KEY="dock.capacity",SCHEMA_KEY="dock.capacity.schema";
    static final int SCHEMA=1,SIX=6,EIGHT=8,DEFAULT=SIX;
    static final int[] VALUES={SIX,EIGHT};
    /** The six-place design, in Home's 1920 x 1080 space: tile, gap, the glass's side padding, and the safe area. */
    static final float TILE=190,GAP=24,PAD=35,SAFE=96,SCALE_8=.84f;

    // --- pure
    static String key(String profile){return KEY+"."+Profiles.stored(profile);}
    /** The saved value, or the default; before any size was chosen (the migration above) more than six tiles reads as 8. */
    static int value(String saved,int dockSize,boolean chosenAnywhere){
        if(String.valueOf(EIGHT).equals(saved))return EIGHT;
        if(String.valueOf(SIX).equals(saved))return SIX;
        return !chosenAnywhere&&dockSize>SIX?EIGHT:DEFAULT;
    }
    static String label(int capacity){return capacity==EIGHT?"8 icons":"6 icons (default)";}
    /** How many tiles must leave Home before the dock can shrink to {@code to} places. */
    static int mustLeave(int dockSize,int to){return Math.max(0,dockSize-to);}
    /** The dock without the tiles chosen to leave (they stay installed and in Apps). Order otherwise kept. */
    static List<String> without(List<String> dock,Collection<String> leaving){List<String> out=new ArrayList<>(dock);out.removeAll(leaving);return out;}
    /** A shrink is ready only once exactly the required number of tiles, all on Home, are chosen. */
    static boolean ready(List<String> dock,Collection<String> leaving,int to){
        return leaving.size()==mustLeave(dock.size(),to)&&dock.containsAll(leaving)&&new HashSet<>(leaving).size()==leaving.size();
    }
    static float scale(int capacity){return capacity==EIGHT?SCALE_8:1f;}
    /** The glass dock's width for this many places. */
    static float width(int capacity){float s=scale(capacity);return (capacity*TILE+(capacity-1)*GAP+2*PAD)*s;}
    /** Left edge of place i, centred on a screen {@code screen} wide. */
    static float slotLeft(int capacity,int i,float screen){float s=scale(capacity);float total=(capacity*TILE+(capacity-1)*GAP)*s;return (screen-total)/2+i*(TILE+GAP)*s;}
    /** The widest a label under place i may be: its own place with Always show, two places for the one focused label. */
    static float labelWidth(int capacity,boolean onlyOne){float s=scale(capacity);return (onlyOne?2*TILE+GAP:TILE+GAP)*s-10;}
    static int labelSize(int capacity){return capacity==EIGHT?24:27;}

    // --- Android
    /** This profile's dock size. */
    static int get(SharedPreferences p,String profile){
        String saved=p.getString(key(profile),null);int size=0;boolean chosen=p.contains(SCHEMA_KEY);
        if(saved==null&&!chosen&&p.contains("dock"))for(String id:p.getString("dock","").split(","))if(!id.trim().isEmpty())size++;
        return value(saved,size,chosen);
    }
    static void set(SharedPreferences p,String profile,int capacity){
        freeze(p,p.edit()).putString(key(profile),String.valueOf(capacity==EIGHT?EIGHT:SIX)).putInt(SCHEMA_KEY,SCHEMA).apply();
    }
    /** The first choice anywhere writes down what each profile showed until then, so the migration never flips the other. */
    private static SharedPreferences.Editor freeze(SharedPreferences p,SharedPreferences.Editor e){
        if(!p.contains(SCHEMA_KEY))for(String pr:new String[]{Profiles.ADULT,Profiles.CHILD})if(!p.contains(key(pr)))e.putString(key(pr),String.valueOf(get(p,pr)));
        return e;
    }
    /** Shrinks to six: the chosen tiles leave the household's dock (never uninstalled) and the size is saved, together. */
    static boolean shrink(SharedPreferences p,String profile,List<String> dock,Collection<String> leaving){
        if(!ready(dock,leaving,SIX))return false;
        freeze(p,p.edit()).putString("dock",String.join(",",without(dock,leaving))).putString(key(profile),String.valueOf(SIX)).putInt(SCHEMA_KEY,SCHEMA).apply();
        return true;
    }
    private static String room(SharedPreferences p){return p.getString("room",android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");}
    /** A tile's name for a dialog: Unified TV's own names, else the installed app's label. */
    static String name(Context c,String id){
        switch(id){case "smartutube":return "SmartTube";case "netflix":return "Netflix";case "disney":return "Disney+";case "bbc":return "BBC";case "live":return "Live TV";
            case "music":return "Music";case "xbox":return "Xbox";case "tvplus":return "Samsung TV Plus";case DockPolicy.LIBRARY:return "Library";default:break;}
        try{android.content.pm.PackageManager pm=c.getPackageManager();return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(id,0)));}catch(Exception e){return id;}
    }
    /**
     * Settings > Appearance & Home > Dock & Icons > Dock size, for the current profile. 6 -> 8 saves at once. 8 -> 6 with
     * more than six tiles on Home lets the household pick which leave, previews the six that stay and asks to confirm;
     * Cancel or Back at any step changes nothing. {@code done} runs only after a change was saved.
     */
    static void choose(Activity a,Runnable done){
        SharedPreferences p=a.getSharedPreferences("unified-tv-preview",0);String profile=Profiles.current(p);int now=get(p,profile);
        String[] names={label(SIX),label(EIGHT)};
        new AlertDialog.Builder(a).setTitle("Dock size").setSingleChoiceItems(names,now==EIGHT?1:0,(d,n)->{d.dismiss();
            int to=VALUES[n];if(to==now)return;
            List<String> dock=HouseholdSettings.dock(a,room(p));
            if(to==EIGHT||mustLeave(dock.size(),to)==0){set(p,profile,to);if(to==EIGHT)android.widget.Toast.makeText(a,"Two empty places added. Add apps to them from Apps.",android.widget.Toast.LENGTH_LONG).show();if(done!=null)done.run();return;}
            pickLeaving(a,dock,new boolean[dock.size()],done);
        }).setNegativeButton("Cancel",null).show();
    }
    private static void pickLeaving(Activity a,List<String> dock,boolean[] picked,Runnable done){
        int n=mustLeave(dock.size(),SIX);String[] names=new String[dock.size()];for(int i=0;i<names.length;i++)names[i]=name(a,dock.get(i));
        new AlertDialog.Builder(a).setTitle("Choose "+n+" to move off Home").setMultiChoiceItems(names,picked,(d,i,on)->picked[i]=on)
            .setPositiveButton("Preview",(d,w)->{List<String> leaving=new ArrayList<>();for(int i=0;i<picked.length;i++)if(picked[i])leaving.add(dock.get(i));
                if(!ready(dock,leaving,SIX)){android.widget.Toast.makeText(a,"Choose exactly "+n+" (you chose "+leaving.size()+")",android.widget.Toast.LENGTH_LONG).show();pickLeaving(a,dock,picked,done);return;}
                confirm(a,dock,leaving,picked,done);})
            .setNegativeButton("Cancel",null).show();
    }
    private static void confirm(Activity a,List<String> dock,List<String> leaving,boolean[] picked,Runnable done){
        StringBuilder stay=new StringBuilder();for(String id:without(dock,leaving)){if(stay.length()>0)stay.append("  ·  ");stay.append(name(a,id));}
        StringBuilder gone=new StringBuilder();for(String id:leaving){if(gone.length()>0)gone.append(" and ");gone.append(name(a,id));}
        new AlertDialog.Builder(a).setTitle("Home with 6 icons").setMessage("Home will show:\n"+stay+"\n\n"+gone+" stay installed and in Apps, and can be put back on Home at any time.")
            .setPositiveButton("Use 6 icons",(d,w)->{SharedPreferences p=a.getSharedPreferences("unified-tv-preview",0);if(shrink(p,Profiles.current(p),dock,leaving)&&done!=null)done.run();})
            .setNeutralButton("Change",(d,w)->pickLeaving(a,dock,picked,done)).setNegativeButton("Cancel",null).show();
    }
}
