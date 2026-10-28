package io.unifiedtv.preview;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * Build My TV / Entertainment Hub Builder (v3.4.5/3.4.6 SET-BUILD-02..10): the wizard's saved progress and its rules.
 * Pure apart from the small Android section at the end.
 *
 * Saved per profile as setup.hubBuilder.adult / setup.hubBuilder.child, a versioned JSON value:
 * {schemaVersion, status (not_started | in_progress | completed | skipped), stepId, selectedCategories,
 * declinedCategories, providerSelections {category: [entry]}, preferredProviderByCategory {category: entry},
 * dockAssignments {entry: "dock" | "apps"}, pendingInstall}. App identifiers and choices only: never a password, token
 * or account. Start over resets only this value; the dock, labels and every other setting stay as they are.
 *
 * Music (SET-BUILD-09): detect before asking. A saved choice is kept (no prompt after restarts or rescans, and never
 * replaced by the one-app rule). Detection unknown: a manual choice, because unknown is not absent. Exactly one music app
 * found and nothing chosen: it becomes the Music app without asking. Two or more: the household picks one, and one OK
 * makes it the Music app that Home's Music place opens; nothing is picked for them. None: ask "Would you like music on
 * your TV?". Other categories: the household ticks the apps it wants on Home; unticking one that is on Home takes it off
 * Home at Arrange (it stays installed and in Apps).
 */
final class HubBuilder {
    private HubBuilder(){}
    static final String KEY="setup.hubBuilder",OFFERED="setup.hubBuilder.offered";
    static final int SCHEMA=1;
    static final String NOT_STARTED="not_started",IN_PROGRESS="in_progress",COMPLETED="completed",SKIPPED="skipped";
    static final String OVERVIEW="overview",ARRANGE="arrange",DONE="done";
    static final String DOCK="dock",APPS="apps";
    /** Unified TV's own tiles that a setup category can place: Live TV and Library. */
    static final String LIVE_TILE="live",LIBRARY_TILE=DockPolicy.LIBRARY;

    static final class State {
        int schemaVersion=SCHEMA;String status=NOT_STARTED,stepId=OVERVIEW,pendingInstall="";
        final List<String> selectedCategories=new ArrayList<>(),declinedCategories=new ArrayList<>();
        final Map<String,List<String>> providerSelections=new LinkedHashMap<>();
        final Map<String,String> preferredProviderByCategory=new LinkedHashMap<>();
        final Map<String,String> dockAssignments=new LinkedHashMap<>();
        List<String> selected(String category){List<String> l=providerSelections.get(category);return l==null?Collections.emptyList():Collections.unmodifiableList(l);}
        String preferred(String category){String p=preferredProviderByCategory.get(category);return p==null?"":p;}
        boolean onDock(String entry){return DOCK.equals(dockAssignments.get(entry));}

        String toJson(){
            try{JSONObject o=new JSONObject();o.put("schemaVersion",schemaVersion).put("status",status).put("stepId",stepId).put("pendingInstall",pendingInstall)
                .put("selectedCategories",new JSONArray(selectedCategories)).put("declinedCategories",new JSONArray(declinedCategories));
                JSONObject sel=new JSONObject();for(Map.Entry<String,List<String>> e:providerSelections.entrySet())sel.put(e.getKey(),new JSONArray(e.getValue()));o.put("providerSelections",sel);
                o.put("preferredProviderByCategory",new JSONObject(preferredProviderByCategory)).put("dockAssignments",new JSONObject(dockAssignments));
                return o.toString();
            }catch(Exception e){return "{}";}
        }
    }

    private static final Set<String> STATUSES=new HashSet<>(Arrays.asList(NOT_STARTED,IN_PROGRESS,COMPLETED,SKIPPED));
    private static final java.util.regex.Pattern ID=java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_.]{0,120}");
    private static boolean id(String s){return s!=null&&ID.matcher(s).matches();}
    private static boolean step(String s){return OVERVIEW.equals(s)||ARRANGE.equals(s)||DONE.equals(s)||AppCatalogue.category(s)!=null;}
    private static void strings(JSONArray a,List<String> into){if(a==null)return;for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(AppCatalogue.category(s)!=null&&!into.contains(s))into.add(s);}}
    /** Reads a saved value; anything missing, malformed or unknown falls back to a fresh start, field by field. */
    static State parse(String json){
        State s=new State();if(json==null||json.trim().isEmpty())return s;
        JSONObject o;try{o=new JSONObject(json);}catch(Exception e){return s;}
        String status=o.optString("status",NOT_STARTED);s.status=STATUSES.contains(status)?status:NOT_STARTED;
        String step=o.optString("stepId",OVERVIEW);s.stepId=step(step)?step:OVERVIEW;
        String pending=o.optString("pendingInstall","");s.pendingInstall=AppCatalogue.entry(pending)!=null?pending:"";
        strings(o.optJSONArray("selectedCategories"),s.selectedCategories);strings(o.optJSONArray("declinedCategories"),s.declinedCategories);
        JSONObject sel=o.optJSONObject("providerSelections");
        if(sel!=null)for(Iterator<String> k=sel.keys();k.hasNext();){String cat=k.next();if(AppCatalogue.category(cat)==null)continue;JSONArray a=sel.optJSONArray(cat);if(a==null)continue;
            List<String> l=new ArrayList<>();for(int i=0;i<a.length();i++){AppCatalogue.Entry e=AppCatalogue.entry(a.optString(i,""));if(e!=null&&e.category.equals(cat)&&!l.contains(e.id))l.add(e.id);}
            if(!l.isEmpty())s.providerSelections.put(cat,l);}
        JSONObject pref=o.optJSONObject("preferredProviderByCategory");
        if(pref!=null)for(Iterator<String> k=pref.keys();k.hasNext();){String cat=k.next();String e=pref.optString(cat,"");if(s.selected(cat).contains(e))s.preferredProviderByCategory.put(cat,e);}
        JSONObject dock=o.optJSONObject("dockAssignments");
        if(dock!=null)for(Iterator<String> k=dock.keys();k.hasNext();){String e=k.next();String v=dock.optString(e,"");if(id(e)&&(DOCK.equals(v)||APPS.equals(v)))s.dockAssignments.put(e,v);}
        return s;
    }

    // --- Choices
    static void begin(State s){if(!IN_PROGRESS.equals(s.status))s.status=IN_PROGRESS;}
    /** The step to resume at: a category, Arrange or Done. The overview is a place to look, not a step, so it is not saved. */
    static void go(State s,String stepId){if(!step(stepId)||OVERVIEW.equals(stepId))return;s.stepId=stepId;if(!DONE.equals(stepId))begin(s);}
    /** Turns a provider on or off for its category; the default follows (the first chosen, or the next one left). */
    static void select(State s,String entry,boolean on){
        AppCatalogue.Entry e=AppCatalogue.entry(entry);if(e==null)return;
        List<String> l=s.providerSelections.computeIfAbsent(e.category,k->new ArrayList<>());
        if(on){if(!l.contains(entry))l.add(entry);if(!s.selectedCategories.contains(e.category))s.selectedCategories.add(e.category);s.declinedCategories.remove(e.category);}
        else{l.remove(entry);s.dockAssignments.remove(entry);}
        if(l.isEmpty()){s.providerSelections.remove(e.category);s.preferredProviderByCategory.remove(e.category);}
        else if(!l.contains(s.preferred(e.category)))s.preferredProviderByCategory.put(e.category,l.get(0));
    }
    /** The one default for its category (selecting it if needed). */
    static void prefer(State s,String entry){AppCatalogue.Entry e=AppCatalogue.entry(entry);if(e==null)return;select(s,entry,true);s.preferredProviderByCategory.put(e.category,entry);}
    /** Add to dock or Keep in Apps, for a chosen provider or for Unified TV's own Live TV / Library tile. */
    static void place(State s,String entry,boolean dock){if(id(entry))s.dockAssignments.put(entry,dock?DOCK:APPS);}
    static void answer(State s,String category,boolean yes){
        if(AppCatalogue.category(category)==null)return;begin(s);
        if(yes){if(!s.selectedCategories.contains(category))s.selectedCategories.add(category);s.declinedCategories.remove(category);}
        else{if(!s.declinedCategories.contains(category))s.declinedCategories.add(category);s.selectedCategories.remove(category);for(String e:new ArrayList<>(s.selected(category)))select(s,e,false);}
    }
    /** Start over: the wizard's own choices only, in progress from the overview. Nothing else is touched. */
    static State startOver(){State s=new State();s.status=IN_PROGRESS;return s;}

    // --- Music (SET-BUILD-09/10)
    enum Music {KEEP,AUTO_DEFAULT,CHOOSE,ASK,MANUAL}
    static Music music(List<String> detected,boolean reliable,String preferred){
        if(preferred!=null&&!preferred.isEmpty()&&(detected.contains(preferred)||!reliable))return Music.KEEP;
        if(!reliable)return Music.MANUAL;
        if(detected.size()==1)return Music.AUTO_DEFAULT;
        return detected.isEmpty()?Music.ASK:Music.CHOOSE;
    }
    /**
     * Applies the one-app rule: true when it chose the Music app. Only while nothing is chosen: a choice the household made
     * is never replaced behind their back, even if the TV briefly doesn't list that app (owner, TCL build run 99: a chosen
     * YouTube Music must not jump back to Amazon Music). The Music place on Home opens it; nothing else on Home changes.
     */
    static boolean autoMusic(State s,List<String> detected,boolean reliable){
        if(!s.preferred(AppCatalogue.MUSIC).isEmpty()||music(detected,reliable,"")!=Music.AUTO_DEFAULT)return false;
        chooseMusic(s,detected.get(0));return true;
    }
    /**
     * One OK on a music app makes it the Music app (owner, TCL build run 99): the only music app chosen and the one the
     * Music place on Home opens ({@link #musicPackage}). No separate "make default" step, and pressing it again keeps it.
     */
    static void chooseMusic(State s,String entry){
        AppCatalogue.Entry e=AppCatalogue.entry(entry);if(e==null||!AppCatalogue.MUSIC.equals(e.category))return;
        begin(s);
        for(String old:new ArrayList<>(s.selected(AppCatalogue.MUSIC)))if(!old.equals(entry))s.dockAssignments.remove(old);
        List<String> only=new ArrayList<>();only.add(entry);s.providerSelections.put(AppCatalogue.MUSIC,only);s.preferredProviderByCategory.put(AppCatalogue.MUSIC,entry);
        if(!s.selectedCategories.contains(AppCatalogue.MUSIC))s.selectedCategories.add(AppCatalogue.MUSIC);s.declinedCategories.remove(AppCatalogue.MUSIC);
    }

    /** What OK on Home's Music place does (SET-BUILD-10, OD-27). */
    enum MusicTile {NOW_PLAYING,MUSIC_PAGE,CHOSEN_APP}
    /**
     * A chosen Music app other than Amazon Music always opens, whatever else is playing or paused (owner, TCL build run
     * 99: it jumped back to Amazon Music when Amazon Music had a paused session). With Amazon Music chosen, or nothing
     * chosen, Amazon Music's own session opens Now Playing, else the Music page, as before.
     */
    static MusicTile musicTile(String chosenPackage,boolean chosenIsAmazon,boolean amazonSession){
        if(chosenPackage!=null&&!chosenIsAmazon)return MusicTile.CHOSEN_APP;
        return amazonSession?MusicTile.NOW_PLAYING:MusicTile.MUSIC_PAGE;
    }

    // --- Arrange (SET-BUILD-04)
    /** True when the household took this entry (or Live TV / Library tile) off Home in Build My TV. */
    static boolean offHome(State s,String entry){return APPS.equals(s.dockAssignments.get(entry));}
    /**
     * Whether an entry shows ticked ("on Home"): the household ticked it here, or it is already on Home ({@code onHomeNow},
     * by dock id) and they haven't taken it off.
     */
    static boolean ticked(State s,String entry,String dockId,Collection<String> onHomeNow){
        return s.onDock(entry)||(!offHome(s,entry)&&dockId!=null&&onHomeNow.contains(dockId));
    }
    /** The dock ids the household took off Home here, so Arrange leaves them out (they stay installed and in Apps). */
    static List<String> unwantedOnDock(State s,java.util.function.Function<String,String> dockIdOf){
        List<String> out=new ArrayList<>();
        for(Map.Entry<String,String> x:s.dockAssignments.entrySet()){if(!APPS.equals(x.getValue()))continue;String e=x.getKey();
            String d=e.equals(LIVE_TILE)||e.equals(LIBRARY_TILE)?e:dockIdOf.apply(e);if(d!=null&&!out.contains(d))out.add(d);}
        return out;
    }
    /**
     * The dock ids the household asked to put on Home, in the guided order with each category's default first. An entry
     * maps to its installed dock id ({@code dockIdOf}); one that isn't installed is skipped (never placed unverified).
     */
    static List<String> wantedOnDock(State s,java.util.function.Function<String,String> dockIdOf){
        List<String> out=new ArrayList<>();
        for(AppCatalogue.Category c:AppCatalogue.CATEGORIES){
            List<String> l=new ArrayList<>(s.selected(c.id));String p=s.preferred(c.id);if(l.remove(p))l.add(0,p);
            if(AppCatalogue.LIVE.equals(c.id))l.add(LIVE_TILE);if(AppCatalogue.LOCAL.equals(c.id))l.add(LIBRARY_TILE);
            for(String e:l){if(!s.onDock(e))continue;String d=e.equals(LIVE_TILE)||e.equals(LIBRARY_TILE)?e:dockIdOf.apply(e);if(d!=null&&!out.contains(d))out.add(d);}
        }
        return out;
    }

    // --- Android
    static String key(String profile){return KEY+"."+Profiles.stored(profile);}
    static State load(SharedPreferences p,String profile){return parse(p.getString(key(profile),null));}
    static void save(SharedPreferences p,String profile,State s){p.edit().putString(key(profile),s.toJson()).apply();}
    /** A fresh install for the guided setup (FirstRun.shouldStart): Adults, no dock saved yet, never offered, never started.
     *  OFFERED also covers the one-time Home card earlier versions showed, so a TV that saw it isn't asked again. */
    static boolean shouldOffer(SharedPreferences p){
        return !Profiles.kids(p)&&!p.getBoolean(OFFERED,false)&&!p.contains("dock")&&NOT_STARTED.equals(load(p,Profiles.ADULT).status);
    }
    static void markOffered(SharedPreferences p){p.edit().putBoolean(OFFERED,true).apply();}
    /** The installed package of this profile's chosen Music app, from {package: label} of launchable apps; null if none. */
    static String musicPackage(SharedPreferences p,String profile,Map<String,String> launchable){
        String e=load(p,profile).preferred(AppCatalogue.MUSIC);return e.isEmpty()?null:AppCatalogue.installedPackage(AppCatalogue.entry(e),launchable);
    }
    /** Photo folders chosen for My Photos (0 when none, or when they can't be read). */
    static int photoFolders(android.content.Context c){
        try{JSONArray r=new Vault(c).read("photos").optJSONArray("roots");return r==null?0:r.length();}catch(Exception e){return 0;}
    }
    /** Home Assistant is set up (an address and a sign-in saved); SettingsActivity.haForTests stands in for the keystore in tests. */
    static boolean homeAssistant(android.content.Context c){
        Boolean t=SettingsActivity.haForTests;if(t!=null)return t;
        try{JSONObject o=new Vault(c).read("ha");return o.optBoolean("enabled")&&!o.optString("token").isEmpty();}catch(Exception e){return false;}
    }
}
