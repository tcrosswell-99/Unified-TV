package io.unifiedtv.preview;

import java.util.*;

/**
 * SET-004 restore defaults, in two levels, both from SettingsSchema so nothing outside it can be reset by accident.
 *
 * <ul>
 * <li><b>A section's "Restore defaults"</b> (one Settings category): its own settings go back to their defaults, after a
 *     confirmation that lists, in plain words, what changes and what is kept.</li>
 * <li><b>"Reset Unified TV settings"</b> (Privacy & Data, adult PIN): every setting in the schema. Connections and keys
 *     (Home Assistant, the film poster and football keys, media servers) are kept unless the household explicitly ticks
 *     "Also forget connections and keys"; watch history and the event log are cleared separately in Privacy & Data.</li>
 * </ul>
 * Neither ever touches the adult PIN, the current profile, this TV's room, the smoke and carbon monoxide switches, whether
 * household alerts are stopped, or Live TV (which stays locked until the app's own check passes): a reset can never
 * switch alarms off or unlock Live TV (module 03). Pure.
 */
final class SettingsReset {
    private SettingsReset(){}
    /** Things no reset changes, said once in every confirmation. */
    static final String KEPT_ALWAYS="your adult PIN, the profile in use, smoke and carbon monoxide alarms, and Live TV protection (it stays locked until the TV checks it itself)";
    /**
     * The Vault records "Also forget connections and keys" removes, with what each is in words. Not "household": it holds
     * the smoke and carbon monoxide switch, which no reset may change (forgetting Home Assistant already stops its alerts).
     */
    static final String[][] CONNECTIONS={{"ha","Home Assistant"},{TmdbKey.VAULT,"the film poster key"},{ApiFootball.VAULT,"the football key"},
        {LibraryFolders.STORE,"your media server folders"},{KidsPolicy.STORE,"the Kids folders"},{"media-adult","the Adults media account"},{"media-child","the Kids media account"},
        {"photos","photo folders"},{"gallery","Gallery pictures"}};

    static final class Plan {
        final List<String> keys=new ArrayList<>(),changes=new ArrayList<>(),kept=new ArrayList<>();
        /** The confirmation's message: what goes back to its default, then what is kept. */
        String message(){
            StringBuilder b=new StringBuilder();
            b.append(changes.isEmpty()?"Everything here is already at its default.":"Goes back to its default: "+String.join(", ",changes)+".");
            if(!kept.isEmpty())b.append("\n\nKept: ").append(String.join("; ",kept)).append('.');
            return b.toString();
        }
    }
    /** One section: only its settings that differ from their defaults are listed as changes (all of its keys are removed). */
    static Plan section(int section,Map<String,?> now){
        Plan p=new Plan();
        for(SettingsSchema.Setting s:SettingsSchema.section(section)){if(!SettingsSchema.managed(s.key)||!resettable(s))continue;p.keys.add(s.key);if(now.containsKey(s.key)&&!Objects.equals(now.get(s.key),s.fallback))p.changes.add(s.name);}
        p.kept.add(keptWords(section));p.kept.add(KEPT_ALWAYS);
        return p;
    }
    /** Every section at once; with {@code connections}, the connection records too. */
    static Plan all(Map<String,?> now,boolean connections){
        Plan p=new Plan();
        for(SettingsSchema.Setting s:SettingsSchema.ALL){if(!SettingsSchema.managed(s.key))continue;p.keys.add(s.key);if(now.containsKey(s.key)&&!Objects.equals(now.get(s.key),s.fallback))p.changes.add(s.name);}
        if(connections){List<String> names=new ArrayList<>();for(String[] c:CONNECTIONS)names.add(c[1]);p.changes.add("connections and keys ("+String.join(", ",names)+"); you set them up again on your phone");}
        else p.kept.add("connections and keys (Home Assistant, film posters, football, media servers)");
        p.kept.add("watch history and the event log (cleared separately in Privacy & Data)");p.kept.add(KEPT_ALWAYS);
        return p;
    }
    /** A section's defaults never include the team: like a connection, it is chosen, not a look. */
    static boolean resettable(SettingsSchema.Setting s){return !FixtureFeed.TEAM.equals(s.key);}
    private static String keptWords(int section){
        switch(section){
            case SettingsSchema.APPEARANCE:return "your apps on Home, Wallpaper photo folders and every connection";
            case SettingsSchema.APPS:return "every app on the TV (nothing is installed or uninstalled) and Kids' approved apps";
            case SettingsSchema.LIBRARY:return "your media server, its folders and your watch history";
            case SettingsSchema.NOTIFICATIONS:return "Home Assistant, the doorbell chosen and whether household alerts are on";
            case SettingsSchema.PROFILES:return "Kids folders and the Kids app guard";
            default:return "every connection";
        }
    }
    /** Sections that have a "Restore defaults" row: any with a resettable setting. */
    static boolean offered(int section){for(SettingsSchema.Setting s:SettingsSchema.section(section))if(resettable(s))return true;return false;}
}
