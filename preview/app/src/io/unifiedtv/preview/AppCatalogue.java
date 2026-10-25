package io.unifiedtv.preview;
import java.util.*;

/**
 * Build My TV's catalogue (v3.4.5/3.4.6 SET-BUILD-01, -02, -05): services grouped by purpose, as data. Pure.
 *
 * Nothing here claims a service is available in a region or on every TV. An entry only says which Android TV package ids
 * Unified TV recognises when the app is already installed, and whether a Google Play listing for that id is trusted
 * enough to offer a store handoff (storeListing; never for an id we have not verified, and never for an app that is not
 * in the store at all, such as SmartTube). Detection (state()) is honest: unknown is not absent.
 *
 * Categories that are Unified TV's own setup (Local Movies & TV, Family Photos, Smart Home, Live TV protection) have no
 * entries to install: they link to the existing setup screens. Live TV is never made safe by choosing an app here; it
 * stays fail-closed (LiveTvProtection).
 */
final class AppCatalogue {
    private AppCatalogue(){}
    static final String ANDROID="android",TIZEN="tizen";

    /** SET-BUILD-01's six states, with their stored names. */
    enum State {
        INSTALLED_AND_LAUNCHABLE("installed_and_launchable","Installed"),
        NOT_INSTALLED_STORE_AVAILABLE("not_installed_store_available","Not installed. Google Play can open its page"),
        NOT_INSTALLED_STORE_UNKNOWN("not_installed_store_unknown","Not installed. Look for it in your TV's app store"),
        NOT_AVAILABLE_ON_PLATFORM("not_available_on_platform","Not available for this TV"),
        NEEDS_CONFIGURATION("needs_configuration","Needs setting up"),
        DETECTION_UNAVAILABLE("detection_unavailable","This TV doesn't let Unified TV check");
        final String id,words;
        State(String id,String words){this.id=id;this.words=words;}
    }

    static final class Category {
        final String id,title,question,blurb,glyph;
        /** True for Unified TV's own setup (no apps to choose or install). */
        final boolean link;
        Category(String id,String title,String question,String blurb,String glyph,boolean link){this.id=id;this.title=title;this.question=question;this.blurb=blurb;this.glyph=glyph;this.link=link;}
    }

    static final class Entry {
        final String id,name,category;
        /** Android TV package ids, preferred first. */
        final String[] packages;
        /** The ids are confirmed Android TV packages (unverified ones are recognised when installed, never installed). */
        final boolean verified;
        /** A trusted Google Play listing for packages[0]: only then is Install offered (after the household says OK). */
        final boolean storeListing;
        /** Needs the provider's own subscription or sign-in, in its own app; Unified TV never asks for credentials. */
        final boolean signIn;
        /** Recognised by its installed name instead (lower case, spaces removed), for apps whose id we do not rely on. */
        final String labelHint;
        final Set<String> platforms;
        Entry(String id,String name,String category,String[] packages,boolean verified,boolean storeListing,boolean signIn,String labelHint,String... platforms){
            this.id=id;this.name=name;this.category=category;this.packages=packages;this.verified=verified;this.storeListing=storeListing&&verified;
            this.signIn=signIn;this.labelHint=labelHint;this.platforms=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(platforms)));
        }
    }

    static final String MUSIC="music",VIDEO="video",YOUTUBE="youtube",LIVE="live",LOCAL="local",CASTING="casting",PHOTOS="photos",SMART_HOME="smarthome";
    /** In the order Build My TV asks: Music first (SET-BUILD-02). */
    static final List<Category> CATEGORIES=Collections.unmodifiableList(Arrays.asList(
        new Category(MUSIC,"Music","Would you like music on your TV?","Your music app on Home, ready to play.","note",false),
        new Category(VIDEO,"Streaming video","Would you like streaming services on your TV?","Films and series from the services you use.","playcircle",false),
        new Category(YOUTUBE,"YouTube and alternative viewers","Would you like YouTube on your TV?","YouTube, or another viewer you prefer.","play",false),
        new Category(LOCAL,"Local Movies & TV","Do you have films and TV on a home media server?","Unified TV finds your home media server for you.","shelf",true),
        new Category(LIVE,"Live TV","Would you like Live TV on Home?","Live TV opens only once Unified TV has checked the protected connection itself.","broadcast",true),
        new Category(CASTING,"Casting and AirPlay","Would you like to show phones and tablets on the TV?","An AirPlay receiver app, where your TV has one.","cast",false),
        new Category(PHOTOS,"Family Photos","Would you like your own photos on the TV?","My Photos for Wallpaper and the Gallery, from your home network.","photos",true),
        new Category(SMART_HOME,"Smart Home","Do you use Home Assistant?","Doorbell, alarms and rooms, through Home Assistant.","house",true)));

    /*
     * Package ids. "verified" means the id is the well-known Android TV app's own; every other id is marked UNVERIFIED
     * here, is only recognised when already installed, and never gets a store handoff. No regional availability is
     * claimed for any of them.
     */
    static final List<Entry> ENTRIES=Collections.unmodifiableList(Arrays.asList(
        // Music
        new Entry("spotify","Spotify",MUSIC,new String[]{"com.spotify.tv.android"},true,true,true,null,ANDROID,TIZEN),
        // UNVERIFIED for TV: com.amazon.music.tv; com.amazon.mp3 is Amazon Music's phone and Fire id, which some TVs carry.
        new Entry("amazonmusic","Amazon Music",MUSIC,new String[]{"com.amazon.music.tv","com.amazon.mp3"},false,false,true,"amazonmusic",ANDROID,TIZEN),
        // UNVERIFIED for TV: com.google.android.youtube.tvmusic.
        new Entry("youtubemusic","YouTube Music",MUSIC,new String[]{"com.google.android.youtube.tvmusic"},false,false,true,"youtubemusic",ANDROID),
        // UNVERIFIED for TV: com.apple.android.music is Apple Music's phone id; a TV build may differ.
        new Entry("applemusic","Apple Music",MUSIC,new String[]{"com.apple.android.music"},false,false,true,"applemusic",ANDROID,TIZEN),
        // UNVERIFIED for TV: deezer.android.tv.
        new Entry("deezer","Deezer",MUSIC,new String[]{"deezer.android.tv","deezer.android.app"},false,false,true,"deezer",ANDROID,TIZEN),
        // UNVERIFIED for TV: com.aspiro.tidal is TIDAL's phone id.
        new Entry("tidal","TIDAL",MUSIC,new String[]{"com.aspiro.tidal"},false,false,true,"tidal",ANDROID,TIZEN),
        // Streaming video
        new Entry("netflix","Netflix",VIDEO,new String[]{"com.netflix.ninja"},true,true,true,null,ANDROID,TIZEN),
        new Entry("disney","Disney+",VIDEO,new String[]{"com.disney.disneyplus"},true,true,true,null,ANDROID,TIZEN),
        new Entry("primevideo","Prime Video",VIDEO,new String[]{"com.amazon.amazonvideo.livingroom"},true,true,true,null,ANDROID,TIZEN),
        // UNVERIFIED for TV: the BBC iPlayer ids below are the ones Unified TV already recognised (DockPolicy).
        new Entry("bbc","BBC iPlayer",VIDEO,new String[]{"bbc.iplayer.android","bbc.iplayer.android.tv","uk.co.freeview.bbc"},false,false,false,"iplayer",ANDROID,TIZEN),
        new Entry("appletv","Apple TV",VIDEO,new String[]{"com.apple.atve.androidtv.appletv"},true,true,true,null,ANDROID,TIZEN),
        // YouTube and alternative viewers
        new Entry("youtube","YouTube",YOUTUBE,new String[]{"com.google.android.youtube.tv"},true,true,false,null,ANDROID,TIZEN),
        new Entry("youtubekids","YouTube Kids",YOUTUBE,new String[]{"com.google.android.youtube.tvkids"},true,true,false,null,ANDROID,TIZEN),
        // SmartTube: not in Google Play and not on Tizen. Recognised if the household installed it; never installed by us.
        // Owner, 25 October 2026: it is the household's YouTube, so on Home its tile reads "YouTube" with the YouTube symbol
        // (DockLabels); here, where both can be chosen, its name says which YouTube it is.
        new Entry("smarttube","YouTube (SmartTube)",YOUTUBE,DockPolicy.packages("smartutube"),false,false,false,"smarttube",ANDROID),
        // Casting: AirPin is recognised by its installed name (as AirPlayReceiver does); no store handoff.
        new Entry("airpin","AirPin (AirPlay receiver)",CASTING,new String[0],false,false,false,"airpin",ANDROID)));

    static Category category(String id){for(Category c:CATEGORIES)if(c.id.equals(id))return c;return null;}
    static Entry entry(String id){for(Entry e:ENTRIES)if(e.id.equals(id))return e;return null;}
    static List<Entry> entries(String category){List<Entry> out=new ArrayList<>();for(Entry e:ENTRIES)if(e.category.equals(category))out.add(e);return out;}
    /** The category after this one in the guided order; null after the last. */
    static String next(String category){for(int i=0;i<CATEGORIES.size()-1;i++)if(CATEGORIES.get(i).id.equals(category))return CATEGORIES.get(i+1).id;return null;}

    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace(" ","");}
    /** The installed package that is this entry, from {package: label} of launchable apps; null when none is. */
    static String installedPackage(Entry e,Map<String,String> launchable){
        if(e==null||launchable==null)return null;
        for(String p:e.packages)if(launchable.containsKey(p))return p;
        if(e.labelHint!=null)for(Map.Entry<String,String> x:launchable.entrySet())if(norm(x.getValue()).contains(e.labelHint))return x.getKey();
        return null;
    }
    /**
     * Detection is reliable only when the TV answered the launcher query with something (a working TV always lists at
     * least its own settings or apps); an error or an empty answer is "unknown", never "not installed".
     */
    static boolean reliable(boolean failed,int launchableCount){return !failed&&launchableCount>0;}
    /** SET-BUILD-01's state for an app entry. */
    static State state(Entry e,Map<String,String> launchable,boolean reliable,boolean storeHandoff,String platform){
        if(!e.platforms.contains(platform))return State.NOT_AVAILABLE_ON_PLATFORM;
        if(!reliable)return State.DETECTION_UNAVAILABLE;
        if(installedPackage(e,launchable)!=null)return State.INSTALLED_AND_LAUNCHABLE;
        if(e.storeListing&&storeHandoff)return State.NOT_INSTALLED_STORE_AVAILABLE;
        return State.NOT_INSTALLED_STORE_UNKNOWN;
    }
    /** SET-BUILD-01's state for Unified TV's own setup categories. */
    static State linkState(boolean configured){return configured?State.INSTALLED_AND_LAUNCHABLE:State.NEEDS_CONFIGURATION;}
    /** The entries positively detected, in catalogue order. */
    static List<String> detected(String category,Map<String,String> launchable){
        List<String> out=new ArrayList<>();for(Entry e:entries(category))if(installedPackage(e,launchable)!=null)out.add(e.id);return out;
    }
    /** The dock id an installed entry takes: Unified TV's own tile where it has one (Netflix -> "netflix"), else its package. */
    static String dockId(Entry e,Map<String,String> launchable){String p=installedPackage(e,launchable);return p==null?null:DockPolicy.canonical(p);}
    /** The Play listing, market:// first and the web page as the fallback. Only for a trusted listing; null otherwise. */
    static String[] storeUris(Entry e){
        if(e==null||!e.storeListing||e.packages.length==0)return null;
        String p=e.packages[0];return new String[]{"market://details?id="+p,"https://play.google.com/store/apps/details?id="+p};
    }
    /** What the household is told about an entry: its state, and any sign-in it needs in its own app. */
    static String describe(Entry e,State s){
        String words=s.words;if(e.signIn&&s!=State.NOT_AVAILABLE_ON_PLATFORM)words+=". Needs its own subscription or sign-in, in its own app";
        return words;
    }
}
