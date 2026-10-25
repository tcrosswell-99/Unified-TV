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
import android.widget.*;
import java.util.*;

/**
 * Build My TV / Entertainment Hub Builder (v3.4.5/3.4.6 UX-BUILD-01, SET-BUILD-01..11). Optional and remote-first: a
 * category overview, then short guided choices, Music first; every one can be skipped, and Home never needs any of it.
 * Opened once from Home's first-run card and at any time from Settings > Apps & Shortcuts > Build My TV (start()).
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
    private void show(String step){
        if(!HubBuilder.OVERVIEW.equals(step)&&!HubBuilder.DONE.equals(step)&&!HubBuilder.ARRANGE.equals(step)&&AppCatalogue.category(step)==null)step=HubBuilder.OVERVIEW;
        HubBuilder.go(state,step);save();at=step;
        if(HubBuilder.OVERVIEW.equals(step))overview();else if(HubBuilder.ARRANGE.equals(step))arrange();else if(HubBuilder.DONE.equals(step))done();else category(AppCatalogue.category(step));
        focus="";
    }
    private void overview(){
        page("Build My TV");
        text("Optional. Tell Unified TV which services you use and it puts them where you want them. Skip anything: Home works without all of it.",18);
        if(kids)text("These choices are for Kids only, from the apps an adult has allowed.",16);
        String resume=state.stepId;boolean going=HubBuilder.IN_PROGRESS.equals(state.status)&&AppCatalogue.category(resume)!=null;
        button(going?"Carry on: "+AppCatalogue.category(resume).title:"Start with Music",()->show(going?resume:AppCatalogue.MUSIC));
        LinearLayout row=null;int n=0;
        for(AppCatalogue.Category c:AppCatalogue.CATEGORIES){if(n++%4==0){row=new LinearLayout(this);column.addView(row);}
            Button card=card(c.glyph,null,c.title,summary(c),()->show(c.id));row.addView(card,weighted());}
        button("Arrange Home  ·  "+DockCapacity.get(prefs,profile)+" places",()->show(HubBuilder.ARRANGE));
        if(!HubBuilder.COMPLETED.equals(state.status))button("Skip for now",()->{state.status=HubBuilder.SKIPPED;state.stepId=HubBuilder.OVERVIEW;save();finish();});
        button("Exit to Home",this::finish);
        button("Start over",()->new AlertDialog.Builder(this).setTitle("Start over?").setMessage("Your choices in Build My TV are cleared. Home, its dock and your other settings stay as they are.")
            .setPositiveButton("Start over",(d,w)->{state=HubBuilder.startOver();showAll.clear();arrangement=null;left.clear();save();show(HubBuilder.OVERVIEW);}).setNegativeButton("Cancel",null).show());
    }
    private String summary(AppCatalogue.Category c){
        if(state.declinedCategories.contains(c.id))return "Not for us";
        List<String> chosen=state.selected(c.id);
        if(!chosen.isEmpty()){AppCatalogue.Entry p=AppCatalogue.entry(state.preferred(c.id));return (p==null?"":p.name)+(chosen.size()>1?" and "+(chosen.size()-1)+" more":"");}
        if(c.link)return state.selectedCategories.contains(c.id)?"Chosen":"Optional";
        int found=found(c.id).size();return !reliable?"Choose yourself":found==0?"None found":found+" found";
    }
    private void footer(String category){
        String next=AppCatalogue.next(category);
        button(next==null?"Next: arrange Home":"Next: "+AppCatalogue.category(next).title,()->show(next==null?HubBuilder.ARRANGE:next));
        button("All categories",()->show(HubBuilder.OVERVIEW));
    }
    private void category(AppCatalogue.Category c){
        page(c.title);text(c.blurb,18);
        if(c.link)setup(c);else if(AppCatalogue.MUSIC.equals(c.id))music();else apps(c);
        footer(c.id);
    }
    /** Yes / No / Later, in a few presses (UX-BUILD-01). */
    private boolean ask(AppCatalogue.Category c){
        if(state.selectedCategories.contains(c.id)||showAll.contains(c.id))return false;
        text(c.question,24);
        button("Yes, show me",()->{HubBuilder.answer(state,c.id,true);showAll.add(c.id);save();show(c.id);});
        button("No thanks",()->{HubBuilder.answer(state,c.id,false);save();String next=AppCatalogue.next(c.id);show(next==null?HubBuilder.ARRANGE:next);});
        button("Do this later",()->{String next=AppCatalogue.next(c.id);show(next==null?HubBuilder.ARRANGE:next);});
        return true;
    }
    private void music(){
        AppCatalogue.Category c=AppCatalogue.category(AppCatalogue.MUSIC);List<String> found=found(c.id);
        HubBuilder.Music rule=HubBuilder.music(found,reliable,state.preferred(c.id));
        if(rule==HubBuilder.Music.AUTO_DEFAULT&&HubBuilder.autoMusic(state,found,reliable)){save();rule=HubBuilder.Music.KEEP;
            text(AppCatalogue.entry(found.get(0)).name+" is the only music app on this TV, so it is your Music app now. It stays in Apps unless you put it on the dock.",19);}
        else if(rule==HubBuilder.Music.KEEP){AppCatalogue.Entry p=AppCatalogue.entry(state.preferred(c.id));text((p==null?"Your chosen app":p.name)+" is your Music app"+(found.size()==1&&p!=null&&found.contains(p.id)?", the only one on this TV":"")+". It is on the dock only if you put it there. Change it below if you like.",19);}
        else if(rule==HubBuilder.Music.CHOOSE)text("This TV has "+found.size()+" music apps. Choose the ones to show, and which one is your Music app.",19);
        else if(rule==HubBuilder.Music.MANUAL)text(AppCatalogue.State.DETECTION_UNAVAILABLE.words+" which music apps are installed. Choose the ones you have.",19);
        else if(ask(c))return;
        if(rule==HubBuilder.Music.CHOOSE&&found.size()>1)button("Show all "+found.size(),()->{for(String id:found)HubBuilder.select(state,id,true);save();focus="all";show(c.id);});
        list(c,found,rule==HubBuilder.Music.MANUAL);
    }
    private void apps(AppCatalogue.Category c){
        List<String> found=found(c.id);
        if(!reliable)text(AppCatalogue.State.DETECTION_UNAVAILABLE.words+" which apps are installed. Choose the ones you have.",17);
        else if(found.isEmpty()&&ask(c))return;
        if(AppCatalogue.CASTING.equals(c.id))text("Some TVs also have Google Cast built in. Unified TV can't check that, so it isn't listed here.",16);
        if(AppCatalogue.YOUTUBE.equals(c.id))text("SmartTube is an independent app, not made by YouTube. It isn't in Google Play, so Unified TV can only recognise it if you already have it.",16);
        list(c,found,!reliable);
    }
    /** Found apps first, then the rest (unless the household only wants what it has). */
    private void list(AppCatalogue.Category c,List<String> found,boolean manual){
        for(String id:found)provider(AppCatalogue.entry(id),manual);
        List<AppCatalogue.Entry> more=new ArrayList<>();for(AppCatalogue.Entry e:AppCatalogue.entries(c.id))if(!found.contains(e.id))more.add(e);
        if(more.isEmpty())return;
        if(!found.isEmpty())text("More services",20);
        for(AppCatalogue.Entry e:more)provider(e,manual);
    }
    /** One service: its glyph, name and honest state; OK chooses it, installs it (with consent) or explains. */
    private void provider(AppCatalogue.Entry e,boolean manual){
        AppCatalogue.State s=stateOf(e);boolean chosen=state.selected(e.category).contains(e.id);
        LinearLayout row=new LinearLayout(this);column.addView(row);
        String sub=AppCatalogue.describe(e,s);Runnable action;
        if(kids&&s==AppCatalogue.State.INSTALLED_AND_LAUNCHABLE&&!allowed(e)){sub="Not on the Kids list. Ask an adult.";action=()->note(e.name+" isn't on the Kids list. Ask an adult.");}
        else if(s==AppCatalogue.State.INSTALLED_AND_LAUNCHABLE||(manual&&s==AppCatalogue.State.DETECTION_UNAVAILABLE)){sub=(chosen?"Chosen  ·  ":"")+sub;action=()->{HubBuilder.select(state,e.id,!chosen);save();focus=e.id;show(e.category);};}
        else if(s==AppCatalogue.State.NOT_INSTALLED_STORE_AVAILABLE)action=()->install(e);
        else if(s==AppCatalogue.State.NOT_AVAILABLE_ON_PLATFORM)action=()->note(e.name+" isn't available for this TV through Unified TV.");
        else action=()->elsewhere(e);
        Button main=card(glyphOf(e),iconOf(e),e.name,sub,action);row.addView(main,new LinearLayout.LayoutParams(0,-2,2));if(e.id.equals(focus))main.requestFocus();
        if(!chosen)return;
        boolean preferred=e.id.equals(state.preferred(e.category));
        Button def=styled(preferred?"Default":"Make default",()->{HubBuilder.prefer(state,e.id);save();focus=e.id;show(e.category);});def.setTextSize(16);row.addView(def,weighted());
        def.setContentDescription(e.name+(preferred?", the default":", make it the default"));
        boolean dock=state.onDock(e.id);
        Button place=styled(dock?"Add to dock":"Keep in Apps",()->{HubBuilder.place(state,e.id,!dock);arrangement=null;save();focus=e.id;show(e.category);});place.setTextSize(16);row.addView(place,weighted());
        place.setContentDescription(e.name+(dock?": add to the dock. Press OK to keep it in Apps instead":": keep in Apps. Press OK to add it to the dock"));
    }
    /** Unified TV's own setup categories: a link to the existing screen, and the dock tile where there is one. */
    private void setup(AppCatalogue.Category c){
        switch(c.id){
            case AppCatalogue.LOCAL:{
                boolean ready=HomeShelf.configured(this);text(AppCatalogue.linkState(ready).words+".",17);
                button(ready?"Open Your Films & TV":kids?"Ask an adult to set up Films & TV":"Find your home media server",()->{HubBuilder.answer(state,c.id,true);save();Launch.library(this);});
                tile(HubBuilder.LIBRARY_TILE,"Library");break;}
            case AppCatalogue.LIVE:{
                String live=LiveTvProtection.livePackage(this);
                text("Live TV opens only after Unified TV checks by itself, every time, that it can only connect through NordVPN. Choosing it here, or having the app installed, never unlocks it.",17);
                if(live.isEmpty()){text("No single Live TV app was recognised on this TV.",17);break;}
                TextView status=text("Live TV now: "+LiveTvProtection.word(LiveTvProtection.now(this,live)),19);status.setFocusable(false);
                text("Protection is set up in Settings > Live TV & Network Protection.",16);
                if(kids&&!approved.contains(live)){text("Live TV isn't on the Kids list. Ask an adult.",17);break;}
                tile(HubBuilder.LIVE_TILE,"Live TV");break;}
            case AppCatalogue.PHOTOS:{
                if(kids){text("Kids shows Artwork, never personal photos.",17);break;}
                int chosen=0;try{org.json.JSONArray r=new Vault(this).read("photos").optJSONArray("roots");chosen=r==null?0:r.length();}catch(Exception ignored){}
                text(AppCatalogue.linkState(chosen>0).words+(chosen>0?": "+chosen+" photo folders chosen.":"."),17);
                button(chosen>0?"Change photo folders":"Choose photo folders",()->{HubBuilder.answer(state,c.id,true);save();startActivity(new Intent(this,PhotoFoldersActivity.class));});break;}
            default:{   // Smart Home
                if(kids){text("Ask an adult to set up Home Assistant.",17);break;}
                boolean ha;try{org.json.JSONObject o=new Vault(this).read("ha");ha=o.optBoolean("enabled")&&!o.optString("token").isEmpty();}catch(Exception e){ha=false;}
                text(AppCatalogue.linkState(ha).words+". Home Assistant is optional; Home works without it.",17);
                button(ha?"Home Assistant settings":"Set up Home Assistant",()->{HubBuilder.answer(state,c.id,true);save();startActivity(new Intent(this,ServiceDiscoveryActivity.class).putExtra("key","ha"));});}
        }
        if(!state.declinedCategories.contains(c.id))button("Not for us",()->{HubBuilder.answer(state,c.id,false);save();String next=AppCatalogue.next(c.id);show(next==null?HubBuilder.ARRANGE:next);});
    }
    private void tile(String id,String name){
        boolean dock=state.onDock(id);
        Button b=button(name+(dock?" on the dock: yes":" on the dock: no"),()->{HubBuilder.place(state,id,!dock);arrangement=null;save();focus=id;show(at);});
        if(id.equals(focus))b.requestFocus();
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
        if(arrangement==null){left.clear();arrangement=DockPolicy.place(room,current,HubBuilder.wantedOnDock(state,this::dockId),capacity,left);picked=-1;}
        List<String> shown=DockPolicy.visible(arrangement,capacity);
        page("Arrange Home");
        text("Home has "+capacity+" places, "+shown.size()+" in use. Empty places stay empty. Press OK on a tile, then on another place, to swap them.",18);
        for(String id:new ArrayList<>(left)){
            text(DockCapacity.name(this,id)+" doesn't fit: Home is full.",18);
            LinearLayout row=new LinearLayout(this);column.addView(row);
            row.addView(styled("Replace a tile with it",()->replace(id,capacity)),weighted());
            row.addView(styled("Keep it in Apps",()->{left.remove(id);for(Map.Entry<String,String> x:new ArrayList<>(state.dockAssignments.entrySet()))if(id.equals(x.getKey())||id.equals(dockId(x.getKey())))HubBuilder.place(state,x.getKey(),false);save();arrange();}),weighted());
        }
        LinearLayout dock=new LinearLayout(this);column.addView(dock);
        for(int i=0;i<capacity;i++){final int at=i;String id=i<shown.size()?shown.get(i):null;
            Button slot=card(id==null?"mono":DockLabels.glyph(id),id==null?null:icon(id),id==null?"Empty":DockCapacity.name(this,id),at==picked?"Moving":String.valueOf(at+1),()->swap(at,shown.size()));
            if(id==null)slot.setAlpha(.55f);slot.setTextSize(capacity==DockCapacity.EIGHT?13:15);dock.addView(slot,weighted());if(at==picked||(picked<0&&at==0&&left.isEmpty()))slot.requestFocus();}
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
        AppCatalogue.Entry music=AppCatalogue.entry(state.preferred(AppCatalogue.MUSIC));
        text(music==null?"Change anything here at any time from Settings > Apps & Shortcuts > Build My TV.":"Music opens "+music.name+". Change anything at any time from Settings > Apps & Shortcuts > Build My TV.",18);
        button("Back to Home",this::finish);
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
