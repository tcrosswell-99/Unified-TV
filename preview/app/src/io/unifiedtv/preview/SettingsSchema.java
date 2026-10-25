package io.unifiedtv.preview;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The one settings schema (SET-006, SET-015): every household preference Unified TV keeps in its own preferences file,
 * with its section, type, allowed values and plain name. Restore defaults (SET-004), backup and import (SET-081),
 * corrupt-value recovery and the privacy dashboard all read this list, so a setting is described once.
 *
 * <p>Schema version: one key, {@link HouseholdSettings#VERSION}. Version 1 is HouseholdSettings' first migration (an
 * existing dock and rating table written down). Version 2 checks every known value: a value of the wrong type or outside
 * its allowed set is removed, so the code's own default applies again, and nothing else is touched. Keys this version
 * does not know are left alone, so a newer version's settings survive a downgrade.
 *
 * <p>What is deliberately not here, so no reset, import or recovery can ever change it: the adult PIN and its lock-out,
 * the current profile, this TV's room, every connection and key (they live encrypted in the Vault), the smoke and carbon
 * monoxide switches and "household alerts stopped" (module 03: nothing may switch alarms off or on behind the household's
 * back), and Live TV protection, which only an automatic check can unlock (SET-063; the old "liveProtection" record is
 * always removed). Pure: no Android types.
 */
final class SettingsSchema {
    private SettingsSchema(){}
    static final String KEY=HouseholdSettings.VERSION;
    static final int VERSION=2;
    /** Sections: the Settings categories (SettingsActivity indices), so each category can restore its own defaults. */
    static final int APPEARANCE=0,LIBRARY=1,PROFILES=2,APPS=3,NOTIFICATIONS=4,SMART_HOME=5,PRIVACY=8;
    /** A record that once claimed Live TV was protected (SET-063): never honoured, always removed. */
    static final String LIVE_PROTECTION="liveProtection";
    /** Never in this schema, never reset, imported or exported (see the class comment). */
    static final Set<String> NEVER=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("pin","pinFailures","pinWait","profile","room",
        AlarmWatch.SHOWN,AlarmWatch.STOPPED,AlarmWatch.DETECTORS,AlarmWatch.CONFIGURED,LIVE_PROTECTION,KEY,"deviceId")));

    enum Type{BOOL,INT,STRING}
    static final class Setting {
        final String key,name;final int section;final Type type;final Object fallback;final Predicate<Object> valid;final Function<Object,String> words;
        /** In an ordinary backup (SET-081); every setting here is safe to export, but a few are this TV's own. */
        final boolean export;
        Setting(String key,String name,int section,Type type,Object fallback,Predicate<Object> valid,Function<Object,String> words,boolean export){
            this.key=key;this.name=name;this.section=section;this.type=type;this.fallback=fallback;this.valid=valid;this.words=words;this.export=export;
        }
        /** The right Java type and an allowed value. */
        boolean accepts(Object v){
            if(v==null)return false;
            switch(type){case BOOL:if(!(v instanceof Boolean))return false;break;case INT:if(!(v instanceof Integer))return false;break;default:if(!(v instanceof String))return false;}
            return valid==null||valid.test(v);
        }
        /** A value in plain words, for "what changes" lists. */
        String say(Object v){if(v==null)v=fallback;if(words!=null)return words.apply(v);if(type==Type.BOOL)return Boolean.TRUE.equals(v)?"On":"Off";return String.valueOf(v);}
    }

    static final List<Setting> ALL;
    private static final Map<String,Setting> BY_KEY=new LinkedHashMap<>();
    static{
        List<Setting> s=new ArrayList<>();
        // Appearance & Home
        s.add(bool("reduceMotion","Reduce motion",APPEARANCE,false));
        s.add(bool("stillBackground","Keep the Home background still",APPEARANCE,false));
        s.add(bool("lightweight","Lightweight rendering",APPEARANCE,false));
        s.add(bool("highContrast","High contrast",APPEARANCE,false));
        s.add(bool("opaque","Reduce transparency",APPEARANCE,false));
        s.add(choice(Accessibility.TEXT_SIZE,"Text size",APPEARANCE,Accessibility.NORMAL,Accessibility::label,Accessibility.SIZES));
        s.add(bool(Accessibility.LONGER,"Longer on-screen times",APPEARANCE,false));
        s.add(new Setting(HouseholdSettings.ZONE,"Time zone",APPEARANCE,Type.STRING,"",v->((String)v).isEmpty()||((String)v).matches("[A-Za-z]+(/[A-Za-z0-9_+-]+){1,2}"),v->((String)v).isEmpty()?"Follow this TV":(String)v,true));
        s.add(choice("wallpaperSource","Wallpaper",APPEARANCE,WallpaperPolicy.ARTWORK,v->WallpaperPolicy.sourceLabel((String)v),WallpaperPolicy.ARTWORK,WallpaperPolicy.PHOTOS,WallpaperPolicy.OFF));
        s.add(choice("wallpaperClock","Wallpaper clock",APPEARANCE,WallpaperPolicy.AUTO,v->WallpaperPolicy.clockLabel((String)v),WallpaperPolicy.AUTO,WallpaperPolicy.SIMPLE,WallpaperPolicy.NONE));
        s.add(number("wallpaperIdle","Wallpaper starts after",APPEARANCE,WallpaperPolicy.idle(-1),v->WallpaperPolicy.idleLabel((Integer)v),WallpaperPolicy.IDLE_MINUTES));
        s.add(number("wallpaperSlide","Wallpaper picture changes every",APPEARANCE,WallpaperPolicy.slide(0),v->WallpaperPolicy.slideLabel((Integer)v),WallpaperPolicy.SLIDE_SECONDS));
        s.add(number("gallerySeconds","Gallery time per image",APPEARANCE,45,v->v+" seconds",15,30,45,60,120));
        s.add(bool("wallpaperKeepPhotos","Keep a copy of photos for when the NAS is off",APPEARANCE,false));
        s.add(new Setting(HomeUtilities.PREF,"Home utilities",APPEARANCE,Type.STRING,"",v->((String)v).isEmpty()||((String)v).matches("[a-z]+(,[a-z]+)*"),v->{List<String> n=new ArrayList<>();for(String id:HomeUtilities.order((String)v))n.add(HomeUtilities.name(id));return String.join(", ",n);},true));
        s.add(choice(HomeUtilities.LANDING,"Down from the dock lands on",APPEARANCE,"continue",v->"first".equals(v)?"The first title":"Continue Watching","continue","first"));
        for(String p:new String[]{Profiles.ADULT,Profiles.CHILD})
            s.add(choice(DockLabels.key(p),"App labels for "+Profiles.label(p),APPEARANCE,DockLabels.FOCUSED_ONLY,v->DockLabels.label((String)v),DockLabels.VALUES));
        // Apps & Shortcuts: the dock (null means "suggest from installed apps"), and each profile's dock size
        s.add(new Setting("dock","Apps on Home",APPS,Type.STRING,"",v->((String)v).isEmpty()||((String)v).matches("[A-Za-z][A-Za-z0-9_.]*(,[A-Za-z][A-Za-z0-9_.]*)*")&&((String)v).length()<=2000,v->((String)v).isEmpty()?"Suggested from this TV's apps":((String)v).split(",").length+" apps",true));
        for(String p:new String[]{Profiles.ADULT,Profiles.CHILD})
            s.add(choice(DockCapacity.key(p),"Dock size for "+Profiles.label(p),APPS,"6",v->DockCapacity.label(Integer.parseInt((String)v)),"6","8"));
        // Library & Media
        s.add(choice(PlaybackPrefs.RESUME,"When a film was left part-way",LIBRARY,PlaybackPrefs.ASK,PlaybackPrefs::resumeLabel,PlaybackPrefs.RESUMES));
        s.add(bool(PlaybackPrefs.AUTOPLAY,"Play the next episode automatically",LIBRARY,false));
        s.add(choice(PlaybackPrefs.SUBTITLES,"Subtitle size",LIBRARY,Accessibility.NORMAL,Accessibility::label,Accessibility.SIZES));
        s.add(choice(MediaSources.PREFERRED,"Films & TV come from",LIBRARY,MediaSources.FOLDERS,MediaSources::preferredLabel,MediaSources.FOLDERS,MediaSources.ACCOUNT));
        s.add(new Setting(MediaSources.NAME,"Media server name on this TV",LIBRARY,Type.STRING,"",v->MediaSources.validName((String)v),v->((String)v).isEmpty()?"Its own name":(String)v,true));
        // Profiles & Family: safe profile settings (never the PIN, never the current profile)
        s.add(choice(HouseholdSettings.RATINGS,"Kids ratings",PROFILES,KidsPolicy.Scheme.UK_BBFC.id,v->KidsPolicy.Scheme.of((String)v).label,schemeIds()));
        s.add(new Setting(KidsPolicy.APPS_PREF,"Apps approved for Kids",PROFILES,Type.STRING,"",v->((String)v).isEmpty()||((String)v).matches("[A-Za-z][A-Za-z0-9_.]*(,[A-Za-z][A-Za-z0-9_.]*)*")&&((String)v).length()<=4000,v->((String)v).isEmpty()?"None":((String)v).split(",").length+" apps",true));
        // Notifications & Doorbell (never the alarm switches)
        s.add(choice(DoorbellCamera.CLOSE,"Doorbell camera closes",NOTIFICATIONS,DoorbellCamera.CLOSE_30,v->DoorbellCamera.closeLabel((String)v),DoorbellCamera.CLOSES));
        s.add(choice(DoorbellCamera.LAYOUT,"Doorbell camera layout",NOTIFICATIONS,DoorbellCamera.OVERLAY,v->DoorbellCamera.layoutLabel((String)v),DoorbellCamera.LAYOUTS));
        s.add(bool(AlertPolicy.KIDS_PERSON_PREF,"Show person alerts in Kids",NOTIFICATIONS,true));
        s.add(bool(QuietHours.ON,"Quiet hours",NOTIFICATIONS,false));
        s.add(new Setting(QuietHours.START,"Quiet hours start",NOTIFICATIONS,Type.INT,QuietHours.DEFAULT_START,v->QuietHours.minute((Integer)v),v->QuietHours.clock((Integer)v),true));
        s.add(new Setting(QuietHours.END,"Quiet hours end",NOTIFICATIONS,Type.INT,QuietHours.DEFAULT_END,v->QuietHours.minute((Integer)v),v->QuietHours.clock((Integer)v),true));
        // Smart Home: the team is a choice, like a connection: exported, never reset by a section's defaults
        s.add(new Setting(FixtureFeed.TEAM,"Your team",SMART_HOME,Type.STRING,"",v->((String)v).isEmpty()||((String)v).length()<=2000&&((String)v).startsWith("{"),v->{Fixtures.Team t=Fixtures.Team.read((String)v);return t==null?"None":t.displayName;},true));
        // Privacy & Data
        s.add(bool(EventLog.ON,"Keep an event log",PRIVACY,true));
        ALL=Collections.unmodifiableList(s);
        for(Setting x:ALL)BY_KEY.put(x.key,x);
    }
    private static String[] schemeIds(){KidsPolicy.Scheme[] all=KidsPolicy.Scheme.values();String[] ids=new String[all.length];for(int i=0;i<all.length;i++)ids[i]=all[i].id;return ids;}
    private static Setting bool(String key,String name,int section,boolean fallback){return new Setting(key,name,section,Type.BOOL,fallback,null,null,true);}
    private static Setting choice(String key,String name,int section,String fallback,Function<Object,String> words,String... allowed){
        Set<String> ok=new HashSet<>(Arrays.asList(allowed));return new Setting(key,name,section,Type.STRING,fallback,ok::contains,words,true);
    }
    private static Setting number(String key,String name,int section,int fallback,Function<Object,String> words,int... allowed){
        Set<Integer> ok=new HashSet<>();for(int a:allowed)ok.add(a);return new Setting(key,name,section,Type.INT,fallback,ok::contains,words,true);
    }
    static Setting of(String key){return BY_KEY.get(key);}
    /** The settings of one section (a Settings category), in the order they appear. */
    static List<Setting> section(int section){List<Setting> out=new ArrayList<>();for(Setting s:ALL)if(s.section==section)out.add(s);return out;}

    /**
     * What to write so the saved preferences are on this schema version, as {key: value} (null removes the key). Runs the
     * earlier migration first (HouseholdSettings.migrate: an earlier install keeps its dock and rating table), then removes
     * any known value that is corrupt, so its default applies. Unknown keys and the NEVER keys are left as they are; the
     * Live TV attestation record is always removed. Empty when there is nothing to do.
     */
    static Map<String,Object> migrate(Map<String,?> existing,String room,String country){
        Map<String,Object> out=new LinkedHashMap<>();
        Object v=existing.get(KEY);int from=v instanceof Integer?(Integer)v:0;
        if(from<HouseholdSettings.CURRENT){out.putAll(HouseholdSettings.migrate(existing,room,country));out.remove(KEY);}
        Map<String,Object> after=new HashMap<>(existing);for(Map.Entry<String,Object> e:out.entrySet()){if(e.getValue()==null)after.remove(e.getKey());else after.put(e.getKey(),e.getValue());}
        out.putAll(repair(after));
        if(existing.containsKey(LIVE_PROTECTION))out.put(LIVE_PROTECTION,null);
        if(from<VERSION)out.put(KEY,VERSION);
        return out;
    }
    /** Corrupt known values to remove (as {key: null}); the code's default then applies. Unknown keys are not judged. */
    static Map<String,Object> repair(Map<String,?> values){
        Map<String,Object> out=new LinkedHashMap<>();
        for(Map.Entry<String,?> e:values.entrySet()){Setting s=BY_KEY.get(e.getKey());if(s!=null&&!s.accepts(e.getValue()))out.put(e.getKey(),null);}
        return out;
    }
    /** Whether a key may ever be written by a reset, an import or a repair. */
    static boolean managed(String key){return BY_KEY.containsKey(key)&&!NEVER.contains(key);}
}
