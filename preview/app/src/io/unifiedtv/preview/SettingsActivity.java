package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public final class SettingsActivity extends Screen {
    @Override public void onCreate(Bundle b) { super.onCreate(b); show(); }
    private void show() {
        page("Settings");
        text("Connections are private to this TV. Optional services can stay disconnected.",20);
        button("Connections · Home Assistant",()->connection("ha"));
        button("Connections · Your Films & TV · Adult",()->connection("media-adult"));
        button("Connections · Your Films & TV · Child",()->connection("media-child"));
        button("Household alerts & room actions",()->EditLock.ask(this,()->startActivity(new Intent(this,HouseholdActivity.class))));
        button("Ask your TV",()->startActivity(new Intent(this,VoiceActivity.class)));
        button("TV Performance & Cleanup",()->EditLock.ask(this,()->startActivity(new Intent(this,PerformanceActivity.class))));
        button("Appearance & accessibility",()->{
            String[] keys={"reduceMotion","highContrast","opaque","lightweight"};
            boolean[] values=new boolean[keys.length]; for(int i=0;i<keys.length;i++) values[i]=getSharedPreferences("unified-tv-preview",0).getBoolean(keys[i],false);
            new AlertDialog.Builder(this).setTitle("Appearance").setMultiChoiceItems(new String[]{"Reduce motion","High contrast","Reduce transparency","Lightweight rendering"},values,
                (d,which,on)->getSharedPreferences("unified-tv-preview",0).edit().putBoolean(keys[which],on).apply()).setPositiveButton("Done",null).show();
        });
        button("Adult / Child profile",()->chooseProfile(this));
        button("Choose gallery images",()->startActivity(new Intent(this,GalleryActivity.class).putExtra("edit",true)));
        button("Clear gallery selection",()->new AlertDialog.Builder(this).setMessage("Remove the gallery selection? Original files are kept.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->new Vault(this).forget("gallery")).show());
        button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
        button("TV picture, audio, Wi-Fi & Bluetooth",()->{
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch(Exception e) { note("Open the TV's own Settings with its remote"); }
        });
        button("AirPlay receiver setup",()->{
            // No assumption that an installed receiver is receiving or running after boot.
            android.content.pm.PackageManager pm=getPackageManager();
            Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for(android.content.pm.ResolveInfo ri:pm.queryIntentActivities(q,0)) {
                if(ri.loadLabel(pm).toString().toLowerCase(java.util.Locale.ROOT).contains("airpin")) {
                    Intent i=pm.getLeanbackLaunchIntentForPackage(ri.activityInfo.packageName);
                    if(i!=null) { startActivity(i); return; }
                }
            }
            note("AirPin LITE not found. Automatic startup and receiving status are not verified.");
        });
        button("Integration readiness",()->new AlertDialog.Builder(this).setTitle("Preview release gates")
            .setMessage("Live TV stays blocked: VPN routing and drop protection need device tests. Ring, Alexa overlays, voice, fixtures, Xbox and Samsung handoffs are pending. No cleanup runs in this app. Child mode separates media accounts; it does not lock Android or switch streaming-app accounts.")
            .setPositiveButton("Close",null).show());
        button("Back to Home",this::finish);
    }
    private void connection(String key) { EditLock.ask(this,()->startActivity(new Intent(this,ConnectionsActivity.class).putExtra("key",key))); }
    static void chooseProfile(android.app.Activity a) {
        new AlertDialog.Builder(a).setTitle("Who's watching?").setItems(new String[]{"Adult","Child"},(d,n)->{
            Runnable change=()->{ a.getSharedPreferences("unified-tv-preview",0).edit().putString("profile",n==0?"adult":"child").apply(); a.recreate(); };
            if(n==0) EditLock.ask(a,change);
            else if(a.getSharedPreferences("unified-tv-preview",0).getString("pin","").isEmpty())
                new AlertDialog.Builder(a).setMessage("Set an adult PIN in Settings before enabling Child mode. Use a separate restricted server account for child media.").setPositiveButton("OK",null).show();
            else change.run();
        }).show();
    }
}
