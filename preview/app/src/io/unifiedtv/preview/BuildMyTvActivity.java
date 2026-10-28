package io.unifiedtv.preview;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;

/**
 * Build My TV / Entertainment Hub Builder (v3.4.5/3.4.6 UX-BUILD-01, SET-BUILD-01..11). Optional and remote-first: a
 * category overview, then short guided choices, Music first; every one can be skipped, and Home never needs any of it.
 * Opened from the guided setup's "Your apps" step (FirstRunActivity) and at any time from Settings > Apps & Shortcuts >
 * Build My TV (start()). Every page starts with one line saying what to do there, or that there is nothing to do (owner,
 * TCL build run 99). Music is one choice: one OK makes the Music app that Home's Music place opens. Other kinds of app are
 * ticked for Home (a tick shows what is on Home now); what isn't on this TV is one line of names unless Google Play can
 * open its page.
 *
 * Detect before asking (AppCatalogue, HubBuilder): installed, launchable apps are found through the launcher query the
 * manifest declares (<queries>). Install only ever opens the Google Play page of a trusted listing, after the household
 * says OK; nothing is downloaded, sideloaded or installed by Unified TV, and an app counts as installed only when the
 * rescan on return finds it. Subscriptions and sign-in stay in each provider's own app; no credentials are asked for.
 *
 * Arrange (SET-BUILD-04): chosen apps go into free dock places within the profile's dock size (DockCapacity, 6 or 8);
 * a full dock is never overwritten without the household picking the tile to replace, and the preview is saved to Home
 * only on Save. Kids (SET-BUILD-07): Kids choices are their own; only adult-allowed apps can be chosen, and the app store
 * is never offered. Live TV stays fail-closed whatever is chosen here: installed is not protected.
 */
/* OD-19: a setup screen, so the glass sidebar over a still Home (GlassScreen). */
public final class BuildMyTvActivity extends GlassScreen {
    /** The public way in, for Settings and Home. */
    public static void start(Activity a){try{a.startActivity(new Intent(a,BuildMyTvActivity.class));}catch(RuntimeException e){Toast.makeText(a,"Build My TV couldn't open",Toast.LENGTH_LONG).show();}}

    private SharedPreferences prefs;private String profile,room;private HubBuilder.State state;private boolean kids,reliable,storeHandoff;private Set<String> approved=new HashSet<>();
    private final Map<String,String> launchable=new HashMap<>();
    /** Category pages where the household said Yes and wants to see every service, not only the ones found. */
    private final Set<String> showAll=new HashSet<>();
    /** The dock preview being arranged (Arrange), what didn't fit, and the place picked up to move. */
    private List<String> arrangement;private final List<String> left=new ArrayList<>();private int picked=-1;
    private String focus="",at=HubBuilder.OVERVIEW;

    @Override public void onCreate(Bundle b){super.onCreate(b);wide(true);   // cards and the dock preview: a wide task
        prefs=getSharedPreferences("unified-tv-preview",0);profile=Profiles.current(prefs);kids=Profiles.kids(prefs);
        room=prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
        approved=KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,""));state=HubBuilder.load(prefs,profile);scan();
        // SET-BUILD-09: exactly one music app here and nothing chosen yet: it is the Music app, without a question.
        if(HubBuilder.autoMusic(state,found(AppCatalogue.MUSIC),reliable))save();
        // Resume where the household left off (SET-BUILD-06); a finished or never-started wizard opens at the overview.
        if(state.pendingInstall.isEmpty())show(HubBuilder.IN_PROGRESS.equals(state.status)?state.stepId:HubBuilder.OVERVIEW);
    }
    /** Back from the app store (or anything else): look again, then carry on from the saved step. */
    private boolean resumed;
    @Override protected void onResume(){super.onResume();
        if(state==null)return;
        // Back from a setup screen or the TV's own store: look again and redraw the same page, so focus is never stranded.
        if(state.pendingInstall.isEmpty()){if(resumed){approved=KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,""));scan();if(!HubBuilder.ARRANGE.equals(at))show(at);}resumed=true;return;}
        resumed=true;
        String e=state.pendingInstall;state.pendingInstall="";save();approved=KidsPolicy.apps(prefs.getString(KidsPolicy.APPS_PREF,""));scan();afterStore(e);
    }
    @Override public void onBackPressed(){if(HubBuilder.OVERVIEW.equals(at)||HubBuilder.DONE.equals(at))finish();else show(HubBuilder.OVERVIEW);}
    private void save(){HubBuilder.save(prefs,profile,state);}

    // --- Detection (SET-BUILD-01)
    private void scan(){
        launchable.clear();boolean failed=false;PackageManager pm=getPackageManager();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER})
            try{for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0))
                if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName()))launchable.put(r.activityInfo.packageName,String.valueOf(r.loadLabel(pm)));}catch(RuntimeException ex){failed=true;}
        reliable=AppCatalogue.reliable(failed,launchable.size());
        boolean store;try{store=pm.resolveActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.android.vending")),0)!=null;}catch(RuntimeException ex){store=false;}
        storeHandoff=store&&!kids;   // Kids never get the app store (as Music and Launch already rule)
    }
    private AppCatalogue.State stateOf(AppCatalogue.Entry e){return AppCatalogue.state(e,launchable,reliable,storeHandoff,AppCatalogue.ANDROID);}
    /** Kids may choose only what an adult allowed; Adults anything. */
    private boolean allowed(AppCatalogue.Entry e){if(!kids)return true;String p=AppCatalogue.installedPackage(e,launchable);return p!=null&&approved.contains(p);}
    private List<String> found(String category){List<String> out=new ArrayList<>();for(String id:AppCatalogue.detected(category,launchable))if(allowed(AppCatalogue.entry(id)))out.add(id);return out;}
    private String dockId(String entry){AppCatalogue.Entry e=AppCatalogue.entry(entry);return e==null?null:AppCatalogue.dockId(e,launchable);}

    // --- Pages
    // Owner, TCL build run 99: every page starts with one plain line saying what to do there, or that there is nothing to
    // do. Only things that do something can be focused; what was merely found is one line of text, not a list of rows.
    private void show(String step){
        if(!HubBuilder.OVERVIEW.equals(step)&&!HubBuilder.DONE.equals(step)&&!HubBuilder.ARRANGE.equals(step)&&AppCatalogue.category(step)==null)step=HubBuilder.OVERVIEW;
        HubBuilder.go(state,step);save();at=step;
        if(HubBuilder.OVERVIEW.equals(step))overview();else if(HubBuilder.ARRANGE.equals(step))arrange();else if(HubBuilder.DONE.equals(step))done();else category(AppCatalogue.category(step));
        focus="";
    }
    /** The page's first line: what to do here, in plain words. */
    private TextView lead(String words){return text(words,18);}
    /** A quieter note under the first line. */
    private TextView note15(String words){TextView t=text(words,15);t.setFocusable(false);return t;}
    /** What is on Home now (dock ids), so a tick shows what is really there. */
    private List<String> onHomeNow(){return DockPolicy.visible(HouseholdSettings.dock(this,room),DockCapacity.get(prefs,profile));}
    private void overview(){
        page("Build My TV","Optional");
        String resume=state.stepId;boolean going=HubBuilder.IN_PROGRESS.equals(state.status)&&AppCatalogue.category(resume)!=null;
        lead(going?"Carry on where you left off, or pick a kind of app below.":"Choose the apps you use, one kind at a time. Press Start with Music, or pick a kind of app below.");
        // Said once, here: nobody has to fill Home.
        note15("Home's places don't all need filling. Skip anything you don't use: Home works without it.");
        if(kids)note15("These choices are for Kids only, from the apps an adult has allowed.");
        button(going?"Carry on: "+AppCatalogue.category(resume).title:"Start with Music",()->show(going?resume:AppCatalogue.MUSIC));
        List<String> home=onHomeNow();LinearLayout row=null;int n=0;
        for(AppCatalogue.Category c:AppCatalogue.CATEGORIES){if(n++%4==0){row=new LinearLayout(this);column.addView(row);}
            Button card=card(c.glyph,null,c.title,summary(c,home),()->show(c.id));row.addView(card,weighted());}
        button("Arrange Home  ·  "+DockCapacity.get(prefs,profile)+" places",()->show(HubBuilder.ARRANGE));
        if(!HubBuilder.COMPLETED.equals(state.status))button("Skip for now",()->{state.status=HubBuilder.SKIPPED;state.stepId=HubBuilder.OVERVIEW;save();finish();});
        button("Close Build My TV",this::finish);   // back to where it was opened from: Home, Settings or the guided setup
        button("Start over",()->new AlertDialog.Builder(this).setTitle("Start over?").setMessage("Your choices in Build My TV are cleared. Home, its dock and your other settings stay as they are.")
            .setPositiveButton("Start over",(d,w)->{state=HubBuilder.startOver();showAll.clear();arrangement=null;left.clear();save();show(HubBuilder.OVERVIEW);}).setNegativeButton("Cancel",null).show());
    }
    private String summary(AppCatalogue.Category c,List<String> home){
        if(state.declinedCategories.contains(c.id))return "Not for us";
        if(AppCatalogue.MUSIC.equals(c.id)){AppCatalogue.Entry p=AppCatalogue.entry(state.preferred(c.id));if(p!=null)return p.name;}
        if(c.link)return state.selectedCategories.contains(c.id)?"Chosen":"Optional";
        if(!AppCatalogue.MUSIC.equals(c.id)){int on=0;for(String id:found(c.id))if(HubBuilder.ticked(state,id,dockId(id),home))on++;if(on>0)return on+" on Home";}
        int found=found(c.id).size();return !reliable?"Choose yourself":found==0?"None found":found+" found";
    }
    private void footer(String category){
        String next=AppCatalogue.next(category);
        button(next==null?"Next: arrange Home":"Next: "+AppCatalogue.category(next).title,()->show(next==null?HubBuilder.ARRANGE:next));
        button("All categories",()->show(HubBuilder.OVERVIEW));
    }
    private void category(AppCatalogue.Category c){
        page(c.title);
        if(c.link)setup(c);else if(AppCatalogue.MUSIC.equals(c.id))music();else apps(c);
        footer(c.id);
    }
    /** Nothing of this kind was found: Yes / No / Later, in a few presses (UX-BUILD-01). The question is the first line. */
    private boolean ask(AppCatalogue.Category c){
        if(state.selectedCategories.contains(c.id)||showAll.contains(c.id))return false;
        lead(c.question);
        note15("None was found on this TV.");
        button("Yes, show me",()->{HubBuilder.answer(state,c.id,true);showAll.add(c.id);save();show(c.id);});
        button("No thanks",()->{HubBuilder.answer(state,c.id,false);save();String next=AppCatalogue.next(c.id);show(next==null?HubBuilder.ARRANGE:next);});
        button("Do this later",()->{String next=AppCatalogue.next(c.id);show(next==null?HubBuilder.ARRANGE:next);});
        return true;
    }
    /** SET-BUILD-09/10: one OK on a music app makes it the Music app that Home's Music place opens. */
    private void music(){
        AppCatalogue.Category c=AppCatalogue.category(AppCatalogue.MUSIC);List<String> found=found(c.id);
        AppCatalogue.Entry chosen=AppCatalogue.entry(state.preferred(c.id));
        if(reliable&&found.isEmpty()&&chosen==null&&ask(c))return;
        List<String> choices=reliable?found:ids(AppCatalogue.entries(c.id));
        if(choices.isEmpty())lead(storeable(c,found).isEmpty()?"No music app is on this TV. Nothing to do here: press Next.":"No music app is on this TV yet. Pick one below to get it, or press Next.");
        else lead("Which music app do you use? Pick one — it opens from the Music place on Home.");
        if(!reliable)note15(AppCatalogue.State.DETECTION_UNAVAILABLE.words+" which apps are installed, so all of them are listed.");
        if(chosen!=null&&reliable&&!found.contains(chosen.id))note15(chosen.name+" is your Music app, but it isn't on this TV now. Pick another, or install it again.");
        grid(choices,id->{AppCatalogue.Entry e=AppCatalogue.entry(id);boolean mine=chosen!=null&&chosen.id.equals(id);
            return card(glyphOf(e),iconOf(e),e.name,mine?"✓  Your Music app":"Press OK to choose",()->{HubBuilder.chooseMusic(state,id);save();focus=id;show(c.id);});},
            id->(chosen!=null&&chosen.id.equals(id))?", your Music app":", press OK to make it your Music app");
        if(reliable)missing(c,found);
    }
    /** Streaming, YouTube and casting: tick the apps wanted on Home; the others stay in Apps. */
    private void apps(AppCatalogue.Category c){
        List<String> found=found(c.id);
        if(reliable&&found.isEmpty()&&ask(c))return;
        List<String> choices=reliable?found:ids(AppCatalogue.entries(c.id)),home=onHomeNow();
        if(choices.isEmpty())lead(storeable(c,found).isEmpty()?"None of these is on this TV. Nothing to do here: press Next.":"None of these is on this TV yet. Pick one below to get it, or press Next.");
        else lead("Tick the apps you want on Home. The others stay in Apps.");
        if(!reliable)note15(AppCatalogue.State.DETECTION_UNAVAILABLE.words+" which apps are installed, so all of them are listed.");
        if(AppCatalogue.CASTING.equals(c.id))note15("Some TVs also have Google Cast built in. Unified TV can't check that, so it isn't listed here.");
        if(AppCatalogue.YOUTUBE.equals(c.id))note15("SmartTube is an independent app, not made by YouTube. It isn't in Google Play, so Unified TV can only recognise it if you already have it.");
        grid(choices,id->{AppCatalogue.Entry e=AppCatalogue.entry(id);boolean on=HubBuilder.ticked(state,id,dockId(id),home);
            return card(glyphOf(e),iconOf(e),e.name,on?"✓  On Home":"In Apps",()->{
                if(on){HubBuilder.select(state,id,false);HubBuilder.place(state,id,false);}else{HubBuilder.select(state,id,true);HubBuilder.place(state,id,true);}
                arrangement=null;save();focus=id;show(c.id);});},
            id->HubBuilder.ticked(state,id,dockId(id),home)?", ticked: on Home":", not ticked: in Apps");
        if(reliable)missing(c,found);
    }
    private static List<String> ids(List<AppCatalogue.Entry> entries){List<String> out=new ArrayList<>();for(AppCatalogue.Entry e:entries)out.add(e.id);return out;}
    /** Cards three to a row; each card's spoken name says whether it is ticked. */
    private void grid(List<String> ids,java.util.function.Function<String,Button> make,java.util.function.Function<String,String> spoken){
        LinearLayout row=null;int n=0;
        for(String id:ids){if(n++%3==0){row=new LinearLayout(this);column.addView(row);}
            Button b=make.apply(id);b.setContentDescription(AppCatalogue.entry(id).name+spoken.apply(id));row.addView(b,weighted());if(id.equals(focus))focusOn(b);}
        if(row!=null)for(int i=n%3==0?3:n%3;i<3;i++){View gap=new View(this);gap.setFocusable(false);row.addView(gap,weighted());}
    }
    /** Services not on this TV with a trusted Google Play page: the only ones with something to press. */
    private List<AppCatalogue.Entry> storeable(AppCatalogue.Category c,List<String> found){
        List<AppCatalogue.Entry> out=new ArrayList<>();
        for(AppCatalogue.Entry e:AppCatalogue.entries(c.id))if(!found.contains(e.id)&&stateOf(e)==AppCatalogue.State.NOT_INSTALLED_STORE_AVAILABLE)out.add(e);
        return out;
    }
    /**
     * What isn't on this TV: a row only where Unified TV can open its Google Play page (after an OK); the rest is one
     * line of names, with nothing to press, because there is nothing Unified TV can do for them.
     */
    private void missing(AppCatalogue.Category c,List<String> found){
        List<AppCatalogue.Entry> store=storeable(c,found);List<String> elsewhere=new ArrayList<>();
        for(AppCatalogue.Entry e:AppCatalogue.entries(c.id)){AppCatalogue.State s=stateOf(e);
            if(!found.contains(e.id)&&!store.contains(e)&&s!=AppCatalogue.State.NOT_AVAILABLE_ON_PLATFORM&&s!=AppCatalogue.State.INSTALLED_AND_LAUNCHABLE)elsewhere.add(e.name);}
        for(AppCatalogue.Entry e:store){Button b=button("Get "+e.name+" from Google Play",()->install(e));if(e.id.equals(focus))focusOn(b);}
        if(!elsewhere.isEmpty())note15("Not on this TV: "+String.join(", ",elsewhere)+". "+(kids?"Ask an adult to add one.":"To add one, install it from your TV's app store, then come back: Unified TV looks again."));
    }
    /** Unified TV's own setup categories: a hand-over to the existing screen, and the Home place where there is one. */
    private void setup(AppCatalogue.Category c){
        switch(c.id){
            case AppCatalogue.LOCAL:{
                boolean ready=HomeShelf.configured(this);
                if(kids&&!ready){lead("Ask an adult to set up Films & TV. Nothing to do here: press Next.");break;}
                lead(ready?"Your Films & TV is set up. Choose whether Library has a place on Home, then press Next.":"Have films and TV on a home media server, such as a NAS? Press Find your media server. If not, press Next.");
                button(ready?"Open Your Films & TV":"Find your media server",()->{HubBuilder.answer(state,c.id,true);save();Launch.library(this);});
                if(ready)tile(HubBuilder.LIBRARY_TILE,"Library");
                break;}
            case AppCatalogue.LIVE:{
                String live=LiveTvProtection.livePackage(this);
                if(live.isEmpty()){lead("No Live TV app was found on this TV. Nothing to do here: press Next.");break;}
                if(kids&&!approved.contains(live)){lead("Live TV isn't on the Kids list. Ask an adult. Nothing to do here: press Next.");break;}
                lead("Choose whether Live TV has a place on Home, then press Next.");
                tile(HubBuilder.LIVE_TILE,"Live TV");
                note15("Live TV now: "+LiveTvProtection.word(LiveTvProtection.now(this))+". "+LiveTvProtection.RULE+" Choosing it here, or having the app installed, never unlocks it.");
                note15("Protection is set up in Settings > Live TV & Network Protection.");
                break;}
            case AppCatalogue.PHOTOS:{
                if(kids){lead("Kids shows Artwork, never personal photos. Nothing to do here: press Next.");break;}
                int chosen=HubBuilder.photoFolders(this);
                lead(chosen>0?(chosen==1?"1 photo folder is":chosen+" photo folders are")+" chosen for My Photos. Nothing to do here: press Next.":"Want your own photos on Wallpaper and in the Gallery? Press Choose photo folders. If not, press Next.");
                button(chosen>0?"Change photo folders":"Choose photo folders",()->{HubBuilder.answer(state,c.id,true);save();startActivity(new Intent(this,PhotoFoldersActivity.class));});
                break;}
            default:{   // Smart Home
                if(kids){lead("Ask an adult to set up Home Assistant. Nothing to do here: press Next.");break;}
                boolean ha=HubBuilder.homeAssistant(this);
                lead(ha?"Home Assistant is set up. Nothing to do here: press Next.":"Use Home Assistant for the doorbell, alarms and rooms? Press Set up Home Assistant. If not, press Next: Home works without it.");
                button(ha?"Home Assistant settings":"Set up Home Assistant",()->{HubBuilder.answer(state,c.id,true);save();startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key","ha"));});}
        }
    }
    /** Unified TV's own Live TV or Library place on Home, as an On/Off switch that shows what is really on Home. */
    private void tile(String id,String name){
        CheckBox b=switchRow(name+" on Home",HubBuilder.ticked(state,id,id,onHomeNow()),on->{HubBuilder.place(state,id,on);arrangement=null;save();});
        if(id.equals(focus))focusOn(b);
    }

    // --- Store handoff (SET-BUILD-03)
    private void install(AppCatalogue.Entry e){
        if(kids||AppCatalogue.storeUris(e)==null){elsewhere(e);return;}
        new AlertDialog.Builder(this).setTitle("Get "+e.name+"?")
            .setMessage("Google Play opens at "+e.name+". You install it there yourself: Unified TV never downloads or installs apps."+(e.signIn?"\n\n"+e.name+" may need its own subscription or sign-in, in its own app. Unified TV never asks for your password.":"")+"\n\nWhen you come back, Unified TV looks again.")
            .setPositiveButton("OK",(d,w)->openStore(e)).setNegativeButton("Cancel",null).show();
    }
    private void openStore(AppCatalogue.Entry e){
        String[] uris=AppCatalogue.storeUris(e);if(uris==null){elsewhere(e);return;}
        state.pendingInstall=e.id;save();
        for(String uri:uris)try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(uri)));return;}catch(RuntimeException ignored){}
        state.pendingInstall="";save();retry(e,"Google Play couldn't be opened on this TV.");
    }
    /** Installed only if the rescan finds it; otherwise Retry, Choose another or Skip. Focus returns to the same service. */
    private void afterStore(String id){
        AppCatalogue.Entry e=AppCatalogue.entry(id);if(e==null){show(HubBuilder.OVERVIEW);return;}
        focus=e.id;
        if(AppCatalogue.installedPackage(e,launchable)!=null){HubBuilder.select(state,e.id,true);save();show(e.category);note(e.name+" is installed");return;}
        show(e.category);retry(e,e.name+" isn't installed yet.");
    }
    private void retry(AppCatalogue.Entry e,String why){
        String next=AppCatalogue.next(e.category);
        new AlertDialog.Builder(this).setTitle(e.name).setMessage(why+" Nothing was changed.")
            .setPositiveButton("Retry",(d,w)->openStore(e)).setNeutralButton("Choose another",(d,w)->{showAll.add(e.category);show(e.category);})
            .setNegativeButton("Skip",(d,w)->show(next==null?HubBuilder.ARRANGE:next)).show();
    }
    private void elsewhere(AppCatalogue.Entry e){
        String why=kids?"Ask an adult to install apps.":"smarttube".equals(e.id)?"It isn't in Google Play, and Unified TV never downloads apps.":"Unified TV can't open a store page it has checked for it on this TV.";
        new AlertDialog.Builder(this).setTitle(e.name).setMessage(why+(kids?"":" If your TV's own app store offers "+e.name+", install it there, then come back: Unified TV looks again."))
            .setPositiveButton("Look again",(d,w)->{scan();focus=e.id;show(e.category);}).setNegativeButton("OK",null).show();
    }

    // --- Arrange (SET-BUILD-04) and finish
    private void arrange(){
        int capacity=DockCapacity.get(prefs,profile);List<String> current=HouseholdSettings.dock(this,room);
        if(arrangement==null){left.clear();List<String> base=new ArrayList<>(current);base.removeAll(HubBuilder.unwantedOnDock(state,this::dockId));
            arrangement=DockPolicy.place(room,base,HubBuilder.wantedOnDock(state,this::dockId),capacity,left);picked=-1;}
        List<String> shown=DockPolicy.visible(arrangement,capacity);
        page("Arrange Home");
        lead("To swap two places, press OK on a tile, then on the other place. Then press Save to Home.");
        note15("Home has "+capacity+" places, "+shown.size()+" in use. Nothing changes on Home until you save.");
        for(String id:new ArrayList<>(left)){
            text(DockCapacity.name(this,id)+" doesn't fit: Home is full.",18);
            LinearLayout row=new LinearLayout(this);column.addView(row);
            row.addView(styled("Replace a tile with it",()->replace(id,capacity)),weighted());
            row.addView(styled("Keep it in Apps",()->{left.remove(id);for(Map.Entry<String,String> x:new ArrayList<>(state.dockAssignments.entrySet()))if(id.equals(x.getKey())||id.equals(dockId(x.getKey())))HubBuilder.place(state,x.getKey(),false);save();arrange();}),weighted());
        }
        LinearLayout dock=new LinearLayout(this);column.addView(dock);
        for(int i=0;i<capacity;i++){final int at=i;String id=i<shown.size()?shown.get(i):null;
            Button slot=card(id==null?"mono":DockLabels.glyph(id),id==null?null:icon(id),id==null?"Empty":DockCapacity.name(this,id),at==picked?"Moving":String.valueOf(at+1),()->swap(at,shown.size()));
            if(id==null)slot.setAlpha(.55f);slot.setTextSize(capacity==DockCapacity.EIGHT?13:15);dock.addView(slot,weighted());if(at==picked||(picked<0&&at==0&&left.isEmpty()))focusOn(slot);}
        button("Save to Home",this::saveHome);
        button("All categories",()->show(HubBuilder.OVERVIEW));
    }
    private void swap(int at,int used){
        if(picked<0){if(at<used){picked=at;arrange();}return;}
        if(at<used&&at!=picked)arrangement=swapped(arrangement,picked,at);
        picked=-1;arrange();
    }
    private static List<String> swapped(List<String> l,int a,int b){List<String> out=new ArrayList<>(l);Collections.swap(out,a,b);return out;}
    private void replace(String id,int capacity){
        List<String> shown=DockPolicy.visible(arrangement,capacity);String[] names=new String[shown.size()];for(int i=0;i<names.length;i++)names[i]=DockCapacity.name(this,shown.get(i));
        new AlertDialog.Builder(this).setTitle("Replace which tile with "+DockCapacity.name(this,id)+"?").setItems(names,(d,n)->{
            String old=shown.get(n);arrangement=new ArrayList<>(Arrays.asList(DockPolicy.replace(room,arrangement,old,id).split(",")));left.remove(id);arrange();
            note(DockCapacity.name(this,old)+" leaves Home but stays installed and in Apps");
        }).setNegativeButton("Cancel",null).show();
    }
    private void saveHome(){
        int capacity=DockCapacity.get(prefs,profile);List<String> shown=DockPolicy.visible(arrangement,capacity);
        StringBuilder words=new StringBuilder();for(String id:shown){if(words.length()>0)words.append("  ·  ");words.append(DockCapacity.name(this,id));}
        new AlertDialog.Builder(this).setTitle("Save Home?").setMessage(shown.isEmpty()?"Home will have no app tiles.":"Home will show:\n"+words)
            .setPositiveButton("Save",(d,w)->{prefs.edit().putString("dock",String.join(",",arrangement)).apply();state.status=HubBuilder.COMPLETED;save();show(HubBuilder.DONE);})
            .setNegativeButton("Cancel",null).show();
    }
    private void done(){
        page("Your TV is set up");
        lead("Nothing to do here: press Done.");
        AppCatalogue.Entry music=AppCatalogue.entry(state.preferred(AppCatalogue.MUSIC));
        note15((music==null?"":"Music opens "+music.name+". ")+"Change anything at any time from Settings > Apps & Shortcuts > Build My TV.");
        button("Done",this::finish);
        button("Change something",()->show(HubBuilder.OVERVIEW));
    }

    // --- Views
    private LinearLayout.LayoutParams weighted(){LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1);l.setMargins(dp(4),dp(4),dp(4),dp(4));return l;}
    /** A large remote-friendly card: the glyph, a name and one line of state. Its spoken name is the same words. */
    private Button card(String glyph,Drawable fallback,String title,String sub,Runnable action){
        Button b=styled(title+"\n"+sub,action);b.setTextSize(17);b.setGravity(android.view.Gravity.CENTER);
        b.setCompoundDrawables(null,glyphDrawable(glyph,fallback,dp(48)),null,null);b.setCompoundDrawablePadding(dp(6));b.setContentDescription(title+". "+sub);
        return b;
    }
    private String glyphOf(AppCatalogue.Entry e){
        String installed=AppCatalogue.dockId(e,launchable);if(installed!=null&&!"mono".equals(DockLabels.glyph(installed)))return DockLabels.glyph(installed);
        if(e.packages.length>0&&!"mono".equals(DockLabels.glyph(e.packages[0])))return DockLabels.glyph(e.packages[0]);
        AppCatalogue.Category c=AppCatalogue.category(e.category);return installed==null&&c!=null?c.glyph:"mono";
    }
    private Drawable iconOf(AppCatalogue.Entry e){String p=AppCatalogue.installedPackage(e,launchable);return p==null?null:icon(p);}
    private Drawable icon(String pkg){if(!pkg.contains("."))return null;try{return getPackageManager().getApplicationIcon(pkg);}catch(Exception e){return null;}}
    /** The white glyph, drawn dark while its card has focus (the focused card is light). */
    private static Drawable glyphDrawable(String g,Drawable fallback,int size){
        Drawable inner=DockGlyphs.drawable(g,fallback);
        Drawable d=new Drawable(){
            boolean lit;final Paint dark=new Paint();{dark.setColorFilter(new PorterDuffColorFilter(0xff101827,PorterDuff.Mode.SRC_IN));}
            @Override public void draw(Canvas c){inner.setBounds(getBounds());if(!lit){inner.draw(c);return;}int l=c.saveLayer(new RectF(getBounds()),dark);inner.draw(c);c.restoreToCount(l);}
            @Override public boolean isStateful(){return true;}
            @Override protected boolean onStateChange(int[] states){boolean f=false;for(int s:states)if(s==android.R.attr.state_focused||s==android.R.attr.state_pressed)f=true;if(f==lit)return false;lit=f;invalidateSelf();return true;}
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(ColorFilter filter){}
            @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
        d.setBounds(0,0,size,size);return d;
    }
}
