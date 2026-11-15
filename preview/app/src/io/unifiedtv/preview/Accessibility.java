package io.unifiedtv.preview;

import android.content.SharedPreferences;

/**
 * SET-073 accessibility, the parts Unified TV owns: larger text (Settings, setup sheets and Home's labels), longer
 * on-screen times for toasts and the doorbell camera, alongside the existing High contrast and Reduce motion switches.
 * States are always said in words (On/Off, "Locked", "Not set up"), never by colour alone. The TV's own accessibility
 * settings (screen reader, captions, its own font size) are the TV's: Unified TV follows them and changes none.
 */
final class Accessibility {
    private Accessibility(){}
    static final String TEXT_SIZE="textSize",LONGER="longerTimeouts";
    static final String NORMAL="normal",LARGE="large",LARGEST="largest";
    static final String[] SIZES={NORMAL,LARGE,LARGEST};
    /** Longer on-screen times double a toast's or the camera's time; "only with Back" stays as it is. */
    static final int LONGER_FACTOR=2;

    static String size(String saved){return LARGE.equals(saved)||LARGEST.equals(saved)?saved:NORMAL;}
    static float scale(String saved){switch(size(saved)){case LARGE:return 1.2f;case LARGEST:return 1.4f;default:return 1f;}}
    static String label(Object saved){switch(size(saved instanceof String?(String)saved:null)){case LARGE:return "Large";case LARGEST:return "Largest";default:return "Standard";}}
    /** A toast's or card's time on screen: as designed, or longer when the household asked. 0 (wait for Back) stays 0. */
    static long time(long designed,boolean longer){return designed<=0?designed:longer?designed*LONGER_FACTOR:designed;}

    static float scale(SharedPreferences p){try{return scale(p.getString(TEXT_SIZE,NORMAL));}catch(RuntimeException e){return 1f;}}
    static boolean longer(SharedPreferences p){try{return p.getBoolean(LONGER,false);}catch(RuntimeException e){return false;}}
    static long time(SharedPreferences p,long designed){return time(designed,longer(p));}
}
