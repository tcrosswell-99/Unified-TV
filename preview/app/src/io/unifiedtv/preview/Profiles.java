package io.unifiedtv.preview;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Adults and Kids (owner, 25 October 2026): a lightweight layer over one Home. The TV opens in the last-used profile and
 * never asks on start. Moving to Adults always takes the adult PIN (EditLock, with its lock-out); moving to Kids takes
 * none, but Kids can only be switched on once a PIN exists. The stored values stay "adult" and "child" so earlier
 * settings (media accounts "media-adult"/"media-child", Wallpaper) keep working; only the words on screen changed.
 *
 * Quick switch (v3.4.4: not on Home's utility pill): the
 * first row of Settings > Profiles & Family opens this chooser, and so does the voice command "switch profile".
 */
final class Profiles {
    static final String ADULT="adult",CHILD="child",PREF="profile";
    private Profiles(){}
    /** Anything other than "child" is Adults, as it always was. */
    static String stored(String raw){return CHILD.equals(raw)?CHILD:ADULT;}
    static String label(String stored){return CHILD.equals(stored(stored))?"Kids":"Adults";}
    static String current(SharedPreferences p){return stored(p.getString(PREF,ADULT));}
    static boolean kids(SharedPreferences p){return CHILD.equals(current(p));}
    static boolean kids(Context c){return kids(c.getSharedPreferences("unified-tv-preview",0));}

    static final int SAME=0,NEEDS_PIN=1,ALLOWED=2,PIN_FIRST=3;
    /**
     * What a switch needs. To Adults: the PIN. To Kids: nothing, once a PIN exists (otherwise set one first). The one
     * exception: Kids with no PIN on record (only possible if the PIN record itself was lost) may return to Adults,
     * because there is nothing to check and Kids must never become a trap the household can't leave.
     */
    static int rule(String from,String to,boolean pinSet){
        from=stored(from);to=stored(to);
        if(from.equals(to))return SAME;
        if(ADULT.equals(to))return pinSet?NEEDS_PIN:ALLOWED;
        return pinSet?ALLOWED:PIN_FIRST;
    }
    /** "Who's watching?": Adults or Kids, the current one marked. */
    static void choose(Activity a){
        SharedPreferences p=a.getSharedPreferences("unified-tv-preview",0);String now=current(p);
        String[] names={label(ADULT)+(ADULT.equals(now)?"  ·  now":""),label(CHILD)+(CHILD.equals(now)?"  ·  now":"")};
        new AlertDialog.Builder(a).setTitle("Who's watching?").setItems(names,(d,n)->switchTo(a,n==0?ADULT:CHILD)).show();
    }
    static void switchTo(Activity a,String to){
        SharedPreferences p=a.getSharedPreferences("unified-tv-preview",0);
        Runnable change=()->{p.edit().putString(PREF,stored(to)).apply();a.recreate();};
        switch(rule(current(p),to,!p.getString("pin","").isEmpty())){
            case NEEDS_PIN:EditLock.ask(a,change);break;
            case ALLOWED:change.run();break;
            case PIN_FIRST:new AlertDialog.Builder(a).setTitle("Kids").setMessage("Set an adult PIN first (Settings > Profiles & Family), so that only an adult can leave Kids.").setPositiveButton("OK",null).show();break;
            default:break;
        }
    }
}
