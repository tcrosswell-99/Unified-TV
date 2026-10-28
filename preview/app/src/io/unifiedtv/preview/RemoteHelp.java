package io.unifiedtv.preview;

import android.view.KeyEvent;
import java.util.*;

/**
 * SET-071 key help, in plain words: what each remote button does in Unified TV, and what Home and Back do on this TV.
 * Shown in Connections &amp; Devices &gt; Remote and in the guided setup's remote step (SET-010). The Home button belongs
 * to Android: it opens Unified TV only when the household has made Unified TV the TV's Home screen; the preview never is.
 * Volume, mute, power, input and the voice button stay with the TV. Nothing here remaps a button (SET-091).
 */
final class RemoteHelp {
    private RemoteHelp(){}

    /** A button and what it does, in that order. */
    static final class Key {
        final String button,meaning;
        Key(String button,String meaning){this.button=button;this.meaning=meaning;}
        @Override public String toString(){return button+": "+meaning;}
    }

    /** The buttons in the order the help lists them. {@code launcherApp}: this app can be the Home screen; {@code isHome}: it is. */
    static List<Key> keys(boolean launcherApp,boolean isHome){
        List<Key> k=new ArrayList<>();
        k.add(new Key("OK","Open what's in focus, or choose it."));
        k.add(new Key("Hold OK","On Home, move or remove the app in focus. In All apps, add an app to Home."));
        k.add(new Key("Up","From the apps on Home, straight to the small icons at the top right (Settings is always there)."));
        k.add(new Key("Down","From the apps on Home, your Films & TV."));
        k.add(new Key("Left and Right","Along a row. In Settings, Left also steps back."));
        k.add(new Key("Back",back(launcherApp,isHome)));
        k.add(new Key("Menu","On Home, All apps."));
        k.add(new Key("Home",home(launcherApp,isHome)));
        k.add(new Key("Volume, mute, power, input and voice","The TV's own. Unified TV doesn't change them."));
        return k;
    }
    /** The guided setup's look at the remote: nothing to do there, so it says so first (owner, TCL build run 99). */
    static final String LOOK_ONLY="Nothing to do here — just a quick look at what your remote does. Press Next when you're ready.";
    /** The buttons Unified TV leaves alone, as one line under the glance. */
    static final String TV_KEYS="Volume, mute, power, input and voice stay the TV's own.";
    /** At most this many pairs, so the guided setup's remote step fits a 1080p screen without scrolling. */
    static final int GLANCE_MAX=8;

    /**
     * The same help as {@link #keys}, as short pairs for one glance (the guided setup's remote step): no more than
     * {@link #GLANCE_MAX}, each meaning a few words, and just as true for this TV (Back and Home depend on the app and on
     * whether Unified TV holds the Home button).
     */
    static List<Key> glance(boolean launcherApp,boolean isHome){
        List<Key> k=new ArrayList<>();
        k.add(new Key("OK","Open or choose"));
        k.add(new Key("Hold OK","Move or remove an app on Home"));
        k.add(new Key("Up","Settings and icons, top right"));
        k.add(new Key("Down","Your Films & TV"));
        k.add(new Key("Left and Right","Along a row"));
        k.add(new Key("Menu","All apps"));
        k.add(new Key("Back",launcherApp?"One step back. Stays in Unified TV":"One step back. On Home, closes the preview"));
        k.add(new Key("Home",!launcherApp?"The TV's own Home screen":isHome?"Back to Unified TV":"The TV's own Home screen, for now"));
        return k;
    }

    /** What Back does on Home (HRC-03): the launcher app stays open; the preview is an ordinary app and closes. */
    static String back(boolean launcherApp,boolean isHome){
        if(!launcherApp)return "One step back. On Home it closes this preview, as any TV app does; open Unified TV again from the TV's apps.";
        return "One step back. On Home it goes to the first app; Unified TV never closes itself from Home."+(isHome?"":" To use the TV's own Home screen, press Home.");
    }
    /** What the Home button does on this TV, honestly. */
    static String home(boolean launcherApp,boolean isHome){
        if(!launcherApp)return "Opens the TV's own Home screen. This preview never becomes the Home screen; open Unified TV again from the TV's apps.";
        if(isHome)return "Brings you back to Unified TV, with the same app in focus.";
        return "Opens the TV's own Home screen, because Unified TV isn't this TV's Home screen yet. You can choose it in Appearance & Home > Home button.";
    }

    /** The guided setup's live demonstration: what the button just pressed does on Home; null for a button it doesn't explain. */
    static String pressed(int keyCode){
        switch(keyCode){
            case KeyEvent.KEYCODE_DPAD_UP:return "Up: on Home, this goes straight to the icons at the top right.";
            case KeyEvent.KEYCODE_DPAD_DOWN:return "Down: on Home, this shows your Films & TV.";
            case KeyEvent.KEYCODE_DPAD_RIGHT:return "Right: on Home, this moves along the row of apps.";
            case KeyEvent.KEYCODE_DPAD_CENTER:case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:return "OK: this opens what's in focus. Hold it on Home to move an app.";
            case KeyEvent.KEYCODE_MENU:return "Menu: on Home, this opens All apps.";
            default:return null;
        }
    }
}
