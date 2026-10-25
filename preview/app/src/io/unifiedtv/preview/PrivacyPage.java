package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.TextView;
import java.util.*;

/**
 * Settings > Privacy & Data, inside the Settings sheet (OD-19): the privacy dashboard (SET-080), the event log (SET-055),
 * settings backup on the phone (SET-081) and "Reset Unified TV settings" (SET-004). Every clear or reset asks first and
 * says what goes and what stays; the reset and the backup need the adult PIN.
 */
final class PrivacyPage {
    private final SettingsActivity a;
    PrivacyPage(SettingsActivity a){this.a=a;}
    private SharedPreferences prefs(){return a.getSharedPreferences(HouseholdSettings.PREFS,0);}

    /** The category's detail. */
    void build(){
        boolean kids=Profiles.kids(a);
        a.text("Everything below stays on this TV. Nothing is sent anywhere unless you connect a service, and there is no usage tracking.",16);
        a.section("Stored on this TV");
        int adults=PrivacyData.history(a,Profiles.ADULT),children=PrivacyData.history(a,Profiles.CHILD);
        a.text("Settings: your choices for Home, the dock, playback, notifications and Kids.",15);
        if(kids)a.text("An adult clears these from Adults.",15);
        item("Watch history for Adults: "+PrivacyData.count(adults,"title","titles"),adults>0&&!kids,()->confirm("Clear Adults' watch history?","Continue Watching and the places saved for Adults are forgotten on this TV. Kids' places stay.",()->PrivacyData.clearHistory(a,Profiles.ADULT),"Adults' watch history cleared."));
        item("Watch history for Kids: "+PrivacyData.count(children,"title","titles"),children>0&&!kids,()->confirm("Clear Kids' watch history?","Continue Watching and the places saved for Kids are forgotten on this TV. Adults' places stay.",()->PrivacyData.clearHistory(a,Profiles.CHILD),"Kids' watch history cleared."));
        long posters=PrivacyData.bytes(PrivacyData.posters(a)),library=PrivacyData.bytes(PrivacyData.library(a)),photos=PrivacyData.bytes(PrivacyData.photos(a));
        item("Film posters and details: "+PrivacyData.size(posters),posters>0&&!kids,()->confirm("Clear film posters and details?","Posters, backgrounds and summaries kept on this TV are deleted. They come back from TMDB as you browse, if its key is set up.",()->PrivacyData.clearPosters(a),"Film posters cleared."));
        item("Saved list of library titles: "+PrivacyData.size(library),library>0&&!kids,()->confirm("Clear the saved list of titles?","The library reads your media server's folders again next time it opens. Nothing on the media server changes.",()->PrivacyData.clearLibrary(a),"The saved list of titles was cleared."));
        item("Photo copies for the Wallpaper: "+PrivacyData.size(photos),photos>0&&!kids,()->confirm("Delete photo copies?","Copies of your photos kept for when the NAS is off are deleted. Your photos on the NAS are not touched.",()->PrivacyData.clearPhotos(a),"Photo copies deleted."));
        a.text("Doorbell camera pictures are never saved. The live view is shown and then gone.",15);
        a.section("Connected services");
        a.text("Home Assistant: "+(SetupPages.haSaved(a)?"connected":"not set up")+" · Film posters: "+(TmdbKey.saved(a)?"key saved":"not set up")+" · Football: "+(FixtureFeed.apiConfigured(a)?"key saved":"not set up")+" · Media server: "+(MediaSources.library(a).ready?"set up":"not set up"),16);
        a.text("Keys and sign-ins are encrypted on this TV and never shown, exported or backed up. Remove each one where it is set up.",15);
        if(!kids)a.chevron(a.button("Manage Home Assistant and football",()->a.show(SettingsActivity.SMART_HOME)));
        a.section("Event log");
        int events=EventLog.recent(a).size();
        a.taskRow("Event log: "+(EventLog.on(a)?PrivacyData.count(events,"event","events"):"off"),SettingsActivity.EVENTS,false,null);
        a.text("Alerts shown, doorbell presses and connection changes from the last 7 days. Never camera pictures.",15);
        if(kids){a.text("Backup and reset are for Adults.",15);return;}
        a.section("Backup");
        a.chevron(a.button("Back up or restore settings on your phone",()->EditLock.ask(a,()->a.expandTask(SettingsActivity.PHONE_BACKUP))));
        a.text("Saves your choices to a file on your phone. Passwords, keys, the PIN and camera pictures are never in it.",15);
        a.section("Reset");
        a.button("Reset Unified TV settings",()->EditLock.ask(a,this::resetAll));
        a.text("Connections and keys are kept unless you choose to forget them too. Alarms stay as they are and Live TV stays protected.",15);
    }
    private void item(String words,boolean clearable,Runnable clear){if(clearable)a.button(words,clear);else a.text(words,17).setFocusable(false);}
    private void confirm(String title,String message,Runnable action,String done){
        new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("Clear",(d,w)->{action.run();a.note(done);a.refresh();}).setNegativeButton("Cancel",null).show();
    }

    /** The event log task: most recent first, and its own switch and clear. */
    void events(){
        boolean on=EventLog.on(a);
        a.toggle("Keep an event log",EventLog.ON,true,yes->{if(!yes)EventLog.clear(a);a.refresh();});
        List<EventLog.Event> all=EventLog.recent(a);
        if(!on)a.text("The event log is off. Nothing is recorded.",16);
        else if(all.isEmpty())a.text("Nothing recorded in the last 7 days.",16);
        else{a.button("Clear the event log",()->new AlertDialog.Builder(a).setTitle("Clear the event log?").setMessage("Every event recorded on this TV is deleted.").setPositiveButton("Clear",(d,w)->{EventLog.clear(a);a.note("Event log cleared.");a.refresh();}).setNegativeButton("Cancel",null).show());
            for(EventLog.Event e:all){TextView t=a.text(OwnerChecks.when(a,e.at)+" · "+e.words,16);t.setFocusable(true);t.setFocusableInTouchMode(true);}}
        a.text("Kept on this TV for 7 days, at most 200 events. Never sent anywhere, never in a backup.",15);
    }

    /** SET-004's top level: every setting in the schema, connections only when ticked. */
    private void resetAll(){
        boolean[] forget={false};
        SettingsReset.Plan[] plan={SettingsReset.all(prefs().getAll(),false)};
        new AlertDialog.Builder(a).setTitle("Reset Unified TV settings?")
            .setMultiChoiceItems(new String[]{"Also forget connections and keys"},forget,(d,which,checked)->forget[0]=checked)
            .setPositiveButton("Next",(d,w)->{plan[0]=SettingsReset.all(prefs().getAll(),forget[0]);
                new AlertDialog.Builder(a).setTitle(forget[0]?"Reset settings and forget connections?":"Reset settings?").setMessage(plan[0].message())
                    .setPositiveButton("Reset",(d2,w2)->apply(plan[0],forget[0])).setNegativeButton("Cancel",null).show();})
            .setNegativeButton("Cancel",null).show();
    }
    private void apply(SettingsReset.Plan plan,boolean forget){
        SharedPreferences.Editor e=prefs().edit();for(String k:plan.keys)e.remove(k);e.remove(SettingsSchema.LIVE_PROTECTION).commit();
        if(forget){Vault v=new Vault(a);for(String[] c:SettingsReset.CONNECTIONS)v.forget(c[0]);
            try{a.stopService(new android.content.Intent(a,HouseholdService.class));}catch(RuntimeException ignored){}
            LibraryIndex.clear(a);EventLog.record(a,EventLog.CONNECTION,"Connections and keys forgotten by a reset");}
        EventLog.record(a,EventLog.SETTINGS,"Unified TV settings reset");
        a.note(forget?"Settings reset and connections forgotten.":"Settings reset. Connections were kept.");a.restyle();
    }

    /** After the phone sent a settings file: what would change, then Apply (adult PIN) or Discard. */
    void incoming(){
        SettingsBackup.Incoming in=SettingsBackup.waiting();
        if(in==null){a.text("No settings file is waiting.",16);a.button("Done",a::back);return;}
        List<String> changes=SettingsBackup.changes(prefs().getAll(),in);
        a.group("What would change");a.card();
        if(changes.isEmpty())a.text("Nothing: this TV already has these settings.",17);
        for(String c:changes)a.text(c,16);
        if(in.ignored>0)a.text(in.ignored==1?"1 entry in the file was left out: this TV can't use it.":in.ignored+" entries in the file were left out: this TV can't use them.",15);
        if(in.newer)a.text("The file is from a newer version of Unified TV; only the settings this version knows are used.",15);
        a.text("Kept: connections and keys, your PIN, watch history, alarms and Live TV protection.",15);
        if(!changes.isEmpty())a.button("Apply these changes",()->EditLock.ask(a,()->{
            SharedPreferences.Editor e=prefs().edit();for(Map.Entry<String,Object> x:SettingsBackup.apply(in).entrySet())HouseholdSettings.put(e,x.getKey(),x.getValue());e.commit();
            SettingsBackup.discard();EventLog.record(a,EventLog.SETTINGS,"Settings restored from a backup file");a.note("Settings restored.");a.restyle();a.back();}));
        a.button(changes.isEmpty()?"Done":"Discard",()->{SettingsBackup.discard();a.back();});
    }
}
