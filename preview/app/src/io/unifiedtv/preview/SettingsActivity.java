package io.unifiedtv.preview;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Category focus previews the right panel; Right/OK enters it, Left returns. */
public final class SettingsActivity extends Screen {
    private final String[] categories={"Connections","Profiles","Notifications","Appearance","TV & performance","Privacy & security"};
    private final Button[] navigation=new Button[6];private LinearLayout details;private int category=-1;private String detail="";
    @Override public void onCreate(Bundle state){super.onCreate(state);autoFocusButtons=false;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(30),dp(16),dp(30),dp(18));root.setBackground(MaterialScene.backdrop(this));
        column=root;text("Settings",28);LinearLayout row=new LinearLayout(this);root.addView(row,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(12),dp(16),dp(12),dp(16));side.setBackground(MaterialScene.panel(this));row.addView(side,new LinearLayout.LayoutParams(dp(240),-1));
        ScrollView scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(24),dp(12),dp(24),dp(16));scroll.addView(details);scroll.setBackground(MaterialScene.panel(this));LinearLayout.LayoutParams pane=new LinearLayout.LayoutParams(0,-1,1);pane.setMargins(dp(20),0,0,0);row.addView(scroll,pane);
        column=side;
        for(int i=0;i<categories.length;i++){final int index=i;Button n=button(categories[i],()->enter());n.setTextSize(17);n.setId(View.generateViewId());navigation[i]=n;
            n.setOnFocusChangeListener((v,focused)->{if(focused&&category!=index)show(index);});
            n.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(e.getAction()==KeyEvent.ACTION_DOWN)enter();return true;}return false;});}
        for(int i=0;i<navigation.length;i++){navigation[i].setNextFocusUpId(navigation[Math.max(0,i-1)].getId());navigation[i].setNextFocusDownId(navigation[Math.min(5,i+1)].getId());}
        int selected=state==null?Math.max(0,getIntent().getIntExtra("category",0)):state.getInt("category",0);show(Math.min(5,selected));navigation[category].requestFocus();setContentView(root);
    }
    private void enter(){for(int i=0;i<details.getChildCount();i++){View v=details.getChildAt(i);if(v.isFocusable()){v.requestFocus();return;}}}
    @Override Button button(String title,Runnable action){Button b=super.button(title,action);b.setTextSize(17);b.setOnKeyListener((v,key,e)->{if(key==KeyEvent.KEYCODE_DPAD_LEFT){if(e.getAction()==KeyEvent.ACTION_DOWN&&category>=0)navigation[category].requestFocus();return true;}return false;});return b;}
    private void heading(String title,String description){details.removeAllViews();column=details;text(title,25);text(description,17);}
    private void show(int selected){category=selected;detail="";heading(categories[selected],new String[]{"Home Assistant, media libraries and services","Choose who is watching and protect adult settings","Manage household interruptions and camera alerts","Shape the look of Home and your Gallery","TV controls and a read-only performance check","Private connections, microphone use and access"}[selected]);
        switch(selected){
        case 0:
            button("Home Assistant",()->startActivity(new Intent(this,ServiceDiscoveryActivity.class)));
            button("Your Films & TV",()->startActivity(new Intent(this,DlnaLibraryActivity.class).putExtra("setup",true)));
            button("Media server accounts",()->new AlertDialog.Builder(this).setTitle("Media account").setItems(new String[]{"Adults","Kids"},(d,n)->connection(n==0?"media-adult":"media-child")).show());
            button("Connected services",this::services);
            airPlay();break;
        case 1:
            text("Who's watching: "+Profiles.label(Profiles.current(getSharedPreferences("unified-tv-preview",0))),18);
            button("Switch profile",()->Profiles.choose(this));button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
            button("Kids settings",()->EditLock.ask(this,()->startActivity(new Intent(this,KidsActivity.class))));
            text("Unified TV opens in the profile used last. To switch from Home, press Up, then Left to the profile name, then OK. Moving to Adults always asks for the adult PIN; Kids can be switched on once a PIN is set.",16);
            text("Kids limits what Unified TV shows and opens: chosen NAS folders, approved apps and an optional app guard. It is not a lock on Android and doesn't switch streaming-app accounts; Kids settings says exactly what it can and can't do.",16);break;
        case 2:
            button("Doorbell, reminders & room setup",()->EditLock.ask(this,()->startActivity(new Intent(this,HouseholdActivity.class))));
            button("Stop household alerts",()->{stopService(new Intent(this,HouseholdService.class));note("Household alerts stopped");});
            // Owner, 25 October 2026: person toasts can be kept off the screen while Kids is watching. Doorbell presses and
            // smoke or carbon monoxide alarms are never affected, in any profile.
            toggle("Show person alerts in Kids",AlertPolicy.KIDS_PERSON_PREF,true,on->{});
            text("Doorbell presses and smoke or carbon monoxide alarms always show, whoever is watching.",15);
            text("Smoke and carbon monoxide alarms come first, then the doorbell. They add to your alarms and never replace them. Native Samsung and third-party protected-video behaviour require separate device tests.",16);break;
        case 3:
            toggle("Reduce motion","reduceMotion");toggle("Keep the Home background still","stillBackground");toggle("High contrast","highContrast");toggle("Reduce transparency","opaque");toggle("Lightweight rendering","lightweight");
            button("Gallery & slideshow",()->startActivity(new Intent(this,GalleryActivity.class).putExtra("edit",true)));
            wallpaper();break;
        case 4:
            button("Picture, sound, network & Bluetooth",()->Launch.unlocked(this,new Intent(Settings.ACTION_SETTINGS),"Use the TV's own Settings"));
            button("Performance check",()->startActivity(new Intent(this,PerformanceActivity.class)));
            // Read-only checks for the owner; never on Home, never in Kids, always behind the PIN.
            if(!Profiles.kids(this))button("Owner diagnostics",()->EditLock.ask(this,()->startActivity(new Intent(this,OwnerDiagnosticsActivity.class))));
            // Spec 6.1: hardware key logging only in owner-authorised debug builds (the review build is one).
            if(RemoteCheckActivity.allowed(this))button("Check your remote's buttons",()->startActivity(new Intent(this,RemoteCheckActivity.class)));
            if(DefaultHome.available(this)){
                text(DefaultHome.isDefault(this)?"The Home button opens Unified TV.":"The Home button opens the original TV interface.",17);
                if(!DefaultHome.isDefault(this))button("Make Unified TV the Home screen",()->EditLock.ask(this,this::homeSetup));
            }else text("This is the Unified TV preview. It never changes the Home button; the separate Unified TV app can.",17);
            text("No automatic cleanup or factory package changes are performed.",16);
            // Spec 30: deliberately small and last; adult only; plain words. Only in the app that can be Home.
            if(DefaultHome.available(this)){Button original=button("Open original TV interface",()->EditLock.ask(this,this::original));original.setTextSize(14);original.setMinHeight(0);original.setAlpha(.8f);}
            break;
        case 5:
            button("Manage private connections",()->show(0));button("Set or change adult PIN",()->EditLock.ask(this,()->EditLock.set(this)));
            button("Live TV protection",()->EditLock.ask(this,this::protection));
            text("Credentials are encrypted locally and excluded from backup. Speech uses the TV's installed provider only after activation and may use its cloud service. General OpenAI discovery is not connected.",16);
            button("Integration readiness",()->new AlertDialog.Builder(this).setTitle("Preview limitations").setMessage("VPN tunnel visibility does not verify protected playback. NAS, Ring, reminders, microphone and manufacturer controls need their own acceptance tests. The Android APK cannot run on native Tizen.").setPositiveButton("Close",null).show());break;
        }
    }
    private void services(){detail="services";heading("Connected services","Available services on this Android TV");button("NordVPN",()->VpnGate.show(this));button("Ask your TV",()->startActivity(new Intent(this,VoiceActivity.class)));button("Amazon Music",()->startActivity(new Intent(this,MusicActivity.class)));button("AirPlay receiver",this::airplay);}
    private void airplay(){android.content.pm.PackageManager pm=getPackageManager();Intent q=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);for(android.content.pm.ResolveInfo ri:pm.queryIntentActivities(q,0))if(ri.loadLabel(pm).toString().toLowerCase(java.util.Locale.ROOT).contains("airpin")){String error=Launch.open(this,ri.activityInfo.packageName,"AirPin LITE");if(error!=null&&!error.isEmpty())note(error);return;}note("No supported receiver found");}
    /** Spec 7.1: AirPlay through AirPin LITE. Setup happens in AirPin itself; Unified TV only reports what it can confirm. */
    private void airPlay(){
        String pkg=AirPlayReceiver.installed(this);
        if(pkg==null){text("AirPlay from iPhone: install AirPin LITE from the TV's app store to receive AirPlay.",16);return;}
        text(AirPlayReceiver.ready?"AirPlay from iPhone: ready"+(AirPlayReceiver.advertisedAs.isEmpty()?".":", shown on iPhones as \""+AirPlayReceiver.advertisedAs+"\"."):"AirPlay from iPhone: AirPin LITE is installed but isn't offering AirPlay on the network right now. Open it once, and check its own setting to start with the TV.",16);
        button("Open AirPin LITE",()->{String error=Launch.open(this,pkg,"AirPin LITE");if(error!=null&&!error.isEmpty())note(error);});
    }
    /**
     * Spec 33: Settings → Appearance → Wallpaper. Artwork is bundled; the clock follows the picture (OD-03, OD-04). My Photos
     * (docs/design/MY_PHOTOS.md) is offered to the adult profile only, after a privacy confirmation; Child sees Artwork.
     */
    private void wallpaper(){
        SharedPreferences p=getSharedPreferences("unified-tv-preview",0);
        String profile=p.getString("profile",WallpaperPolicy.ADULT);boolean adult=WallpaperPolicy.photosAllowed(profile);
        String saved=WallpaperPolicy.source(p.getString("wallpaperSource",null)),source=WallpaperPolicy.sourceFor(saved,profile),clock=WallpaperPolicy.clock(p.getString("wallpaperClock",null));
        int idle=WallpaperPolicy.idle(p.getInt("wallpaperIdle",-1)),slide=WallpaperPolicy.slide(p.getInt("wallpaperSlide",0));
        text("Wallpaper: pictures and a clock when Home is left alone. Not Samsung Art Mode.",17);
        String[] sources=adult?new String[]{"Artwork","My Photos","Off"}:new String[]{"Artwork","Off"};
        button("Wallpaper: "+WallpaperPolicy.sourceLabel(source),()->choose("Wallpaper",sources,n->{String pick=sources[n];
            if(pick.equals("My Photos"))EditLock.ask(this,this::myPhotos);else p.edit().putString("wallpaperSource",pick.equals("Off")?WallpaperPolicy.OFF:WallpaperPolicy.ARTWORK).apply();}));
        if(WallpaperPolicy.PHOTOS.equals(saved)&&!adult)text("Kids shows Artwork, never personal photos.",16);
        if(WallpaperPolicy.PHOTOS.equals(source)){
            text(WallpaperPolicy.PHOTOS_LIMIT,16);
            int chosen=0;try{org.json.JSONArray r=new Vault(this).read("photos").optJSONArray("roots");chosen=r==null?0:r.length();}catch(Exception ignored){}
            button(chosen==0?"Choose photo folders":"Photo folders: "+chosen+" chosen",()->EditLock.ask(this,()->startActivity(new Intent(this,PhotoFoldersActivity.class))));
            toggle("Keep a copy for when the NAS is off","wallpaperKeepPhotos",on->{if(!on)new Thread(()->new PhotoCache(new java.io.File(getNoBackupFilesDir(),"photos"),PhotoCache.LIMIT).clear(),"photo-cache").start();});
            text("Copies stay on this TV only, up to about 200 MB. Without them, Artwork shows while the NAS is off.",15);
        }
        button("Clock: "+WallpaperPolicy.clockLabel(clock),()->choose("Clock",new String[]{WallpaperPolicy.clockLabel(WallpaperPolicy.AUTO),WallpaperPolicy.clockLabel(WallpaperPolicy.SIMPLE),WallpaperPolicy.clockLabel(WallpaperPolicy.NONE)},n->p.edit().putString("wallpaperClock",new String[]{WallpaperPolicy.AUTO,WallpaperPolicy.SIMPLE,WallpaperPolicy.NONE}[n]).apply()));
        String[] idles=new String[WallpaperPolicy.IDLE_MINUTES.length];for(int i=0;i<idles.length;i++)idles[i]=WallpaperPolicy.idleLabel(WallpaperPolicy.IDLE_MINUTES[i]);
        button("Start: "+WallpaperPolicy.idleLabel(idle).toLowerCase(java.util.Locale.UK),()->choose("Start the Wallpaper",idles,n->p.edit().putInt("wallpaperIdle",WallpaperPolicy.IDLE_MINUTES[n]).apply()));
        String[] slides=new String[WallpaperPolicy.SLIDE_SECONDS.length];for(int i=0;i<slides.length;i++)slides[i]=WallpaperPolicy.slideLabel(WallpaperPolicy.SLIDE_SECONDS[i]);
        button("Change picture every "+WallpaperPolicy.slideLabel(slide),()->choose("Change picture every",slides,n->p.edit().putInt("wallpaperSlide",WallpaperPolicy.SLIDE_SECONDS[n]).apply()));
        if(!WallpaperPolicy.OFF.equals(source))button("Start Wallpaper now",()->startActivity(new Intent(this,WallpaperActivity.class)));
    }
    /** Spec 33.2.6: a clear local privacy confirmation before personal photos appear on a shared TV. */
    private void myPhotos(){
        new AlertDialog.Builder(this).setTitle("Show my photos on this TV?").setMessage("Only the folders you choose are shown, and only in Adults; Kids shows Artwork. Nothing is uploaded.\n\n"+WallpaperPolicy.PHOTOS_LIMIT)
            .setPositiveButton("Show my photos",(d,w)->{getSharedPreferences("unified-tv-preview",0).edit().putString("wallpaperSource",WallpaperPolicy.PHOTOS).apply();
                int chosen=0;try{org.json.JSONArray r=new Vault(this).read("photos").optJSONArray("roots");chosen=r==null?0:r.length();}catch(Exception ignored){}
                if(chosen==0)startActivity(new Intent(this,PhotoFoldersActivity.class));if(category==3)show(3);})
            .setNegativeButton("Cancel",null).show();
    }
    private void choose(String title,String[] options,java.util.function.IntConsumer pick){
        new AlertDialog.Builder(this).setTitle(title).setItems(options,(d,n)->{pick.accept(n);show(3);}).show();
    }
    private void toggle(String name,String key){toggle(name,key,on->{});}
    private void toggle(String name,String key,java.util.function.Consumer<Boolean> after){toggle(name,key,false,after);}
    private void toggle(String name,String key,boolean byDefault,java.util.function.Consumer<Boolean> after){CheckBox c=new CheckBox(this);c.setText(name);c.setTextColor(-1);c.setTextSize(17);c.setPadding(8,dp(10),8,dp(10));c.setChecked(getSharedPreferences("unified-tv-preview",0).getBoolean(key,byDefault));c.setOnCheckedChangeListener((v,on)->{getSharedPreferences("unified-tv-preview",0).edit().putBoolean(key,on).apply();after.accept(on);});c.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_DPAD_LEFT){navigation[category].requestFocus();return true;}return false;});column.addView(c);}
    /** Recording is an adult's statement that the network-level check passed on this TV; versions are captured automatically. */
    private void protection(){
        String live=LiveTvProtection.livePackage(this);org.json.JSONObject saved=LiveTvProtection.saved(this);
        String state=saved!=null&&!saved.optString("live").isEmpty()?"Checked on "+java.text.DateFormat.getDateInstance().format(new java.util.Date(saved.optLong("checkedAt")))+". "+(LiveTvProtection.now(this,live)==LiveTvProtection.Decision.ALLOW?"Live TV can open while NordVPN is on.":LiveTvProtection.message(LiveTvProtection.now(this,live))):"Not checked. Live TV is locked.";
        new AlertDialog.Builder(this).setTitle("Live TV protection").setMessage(state+"\n\nRecord a check only after the Live TV protection check in the Unified TV guide has passed on this TV, with NordVPN set to block connections when it disconnects.")
            .setPositiveButton("Record a passed check",(d,w)->{try{LiveTvProtection.save(this,LiveTvProtection.record(live,LiveTvProtection.version(this,live),LiveTvProtection.version(this,LiveTvProtection.NORDVPN),System.currentTimeMillis()));note("Recorded. Live TV can open while NordVPN is on.");}catch(Exception e){note("Live TV and NordVPN must both be installed, and only one Live TV app");}})
            .setNeutralButton("Lock Live TV",(d,w)->{LiveTvProtection.save(this,null);note("Live TV is locked");}).setNegativeButton("Close",null).show();
    }
    /** Spec 28.3 onboarding: explain, let Android ask, then confirm what actually happened. */
    private void homeSetup(){
        new AlertDialog.Builder(this).setTitle("Make Unified TV the Home screen").setMessage("Pressing Home will open Unified TV instead of the original TV interface. The original interface stays on the TV, and you can switch back at any time from this screen.\n\nYour TV will ask you to choose: pick Unified TV.")
            .setPositiveButton("Continue",(d,w)->{homeAsked=true;settingsPass();DefaultHome.offer(this);}).setNegativeButton("Cancel",null).show();
    }
    private boolean homeAsked;
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==DefaultHome.REQUEST)homeChecked();}
    @Override protected void onResume(){super.onResume();if(homeAsked)homeChecked();}
    private void homeChecked(){
        homeAsked=false;boolean yes=DefaultHome.isDefault(this);
        // Not chosen: leave the TV exactly as it was, with no stray Home entry.
        if(!yes&&DefaultHome.offered(this))DefaultHome.withdraw(this);
        note(yes?"Done. The Home button now opens Unified TV.":"Nothing changed. The Home button still opens the original TV interface.");if(category==4)show(4);
    }
    private void original(){
        boolean ours=DefaultHome.isDefault(this);
        java.util.List<String> choices=new java.util.ArrayList<>();choices.add("Open original interface for now");if(ours)choices.add("Make original interface the default");choices.add("Cancel");
        new AlertDialog.Builder(this).setTitle("Original TV interface").setItems(choices.toArray(new String[0]),(d,n)->{
            String pick=choices.get(n);
            if(pick.startsWith("Open")){settingsPass();if(!DefaultHome.openOriginal(this))new AlertDialog.Builder(this).setMessage("This TV doesn't let Unified TV open the original interface directly."+(ours?" Choose 'Make original interface the default' instead.":" Press the Home button on your remote to get there.")).setPositiveButton("OK",null).show();}
            else if(pick.startsWith("Make"))new AlertDialog.Builder(this).setTitle("Make original interface the default").setMessage("From now on, pressing Home will open the original TV interface until you choose Unified TV again in Settings > TV & performance.")
                .setPositiveButton("Make it the default",(d2,w2)->{DefaultHome.withdraw(this);note("The Home button now opens the original TV interface.");show(4);}).setNegativeButton("Cancel",null).show();
        }).show();
    }
    private void connection(String key){startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key",key));}
    @Override public void onBackPressed(){if(!detail.isEmpty())show(category);else if(!navigation[category].hasFocus())navigation[category].requestFocus();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("category",category);}
    /** An adult is here behind the PIN: if Kids is on, the Kids guard lets system screens opened from here stay a while. */
    private void settingsPass(){if(Profiles.kids(this))getSharedPreferences("unified-tv-preview",0).edit().putLong(KidsGuard.PASS,System.currentTimeMillis()+KidsGuard.PASS_MS).apply();}
}
