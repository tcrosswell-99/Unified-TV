package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;
import java.util.List;

/**
 * Settings pages about this TV itself, drawn inside the Settings sheet (OD-19):
 * <ul>
 * <li>Updates &amp; About &gt; What this TV can do (SET-006/SET-003): every feature in one of SET-003's five states, read
 *     from this TV by {@link Capabilities}, in plain words, with the next step where there is one;</li>
 * <li>Connections &amp; Devices &gt; Sound (SET-072): where the sound plays, read-only, and the TV's own sound settings;</li>
 * <li>Connections &amp; Devices &gt; Remote (SET-071): what the buttons do, and the remote button check where this build
 *     has it.</li>
 * </ul>
 */
final class DevicePage {
    private final SettingsActivity a;
    DevicePage(SettingsActivity a){this.a=a;}

    // --- SET-006 / SET-003
    void capabilities(){
        List<Capabilities.Capability> all=Capabilities.read(a);
        a.text("Read from this TV just now: its Android version, what it allows apps and what is set up. Never guessed from the TV's brand.",15);
        for(Capabilities.State s:Capabilities.ORDER){
            List<Capabilities.Capability> here=Capabilities.in(all,s);if(here.isEmpty())continue;
            a.section(s.words);
            for(Capabilities.Capability c:here){
                Button b=a.button(c.name,()->explain(c));b.setContentDescription(c.name+", "+s.words);
                if(c.go!=null)a.chevron(b);
                a.text(c.words,15);
            }
        }
    }
    /** OK on a feature: its next step, or (with none on this TV) its explanation again in a dialog. */
    private void explain(Capabilities.Capability c){
        if(c.go!=null){a.go(c.go);return;}
        new AlertDialog.Builder(a).setTitle(c.name).setMessage(c.state.words+". "+c.words).setPositiveButton("Close",null).show();
    }

    // --- SET-072
    void sound(){
        a.section("Sound");
        SoundOutput.Summary s=SoundOutput.now(a);
        a.text(s.line,17).setFocusable(false);
        if(s.note!=null)a.text(s.note,15);
        a.button("Open the TV's sound settings",this::openSound);
        a.text("Volume and where the sound goes are changed with the remote and in the TV's own settings. Unified TV only shows them.",15);
    }
    /**
     * The TV's own sound page where it has one, else its Settings (tried rather than looked up: Android 11+ hides other
     * apps' screens from a lookup). As Launch.unlocked: in Kids, the Kids guard lets the TV's Settings stay a while.
     */
    private void openSound(){
        if(Profiles.kids(a))a.getSharedPreferences(HouseholdSettings.PREFS,0).edit().putLong(KidsGuard.PASS,System.currentTimeMillis()+KidsGuard.PASS_MS).apply();
        try{a.startActivity(new Intent(android.provider.Settings.ACTION_SOUND_SETTINGS));}
        catch(RuntimeException e){Launch.unlocked(a,new Intent(android.provider.Settings.ACTION_SETTINGS),"Use the TV's own Settings");}
    }

    // --- SET-071
    void remote(){
        a.section("Remote");
        a.taskRow("What the buttons do",SettingsActivity.REMOTE_HELP,false,null);
        boolean home=DefaultHome.available(a)&&DefaultHome.isDefault(a);
        a.text("Home: "+RemoteHelp.home(DefaultHome.available(a),home),15);
        if(Capabilities.shown(a,Capabilities.REMOTE_CHECK))a.button("Check your remote's buttons",()->a.startActivity(new Intent(a,RemoteCheckActivity.class)));
    }
    /** The key help task: one read-only row per button, so the remote can move through (and scroll) the whole list. */
    void remoteHelp(){
        boolean launcher=DefaultHome.available(a);
        for(RemoteHelp.Key k:RemoteHelp.keys(launcher,launcher&&DefaultHome.isDefault(a))){
            Button b=a.button(k.button,()->{});b.setContentDescription(k.button+": "+k.meaning);
            a.text(k.meaning,15);
        }
        a.text("These directions are fixed. The TV keeps its own buttons: Unified TV never takes over Home, volume, power or input.",15);
        if(Capabilities.shown(a,Capabilities.REMOTE_CHECK))a.button("Check your remote's buttons",()->a.startActivity(new Intent(a,RemoteCheckActivity.class)));
    }
}
