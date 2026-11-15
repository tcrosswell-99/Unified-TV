package io.unifiedtv.preview;

import java.util.Objects;

/**
 * SET-025 preview and revert: a change that could make Settings hard to read or use (a different text size, or switching
 * High contrast or Reduce transparency off) is applied at once as a preview and goes back by itself after
 * {@link #SECONDS} seconds unless the household chooses "Keep". Leaving Settings before that also puts it back, so a
 * change nobody could read can never stick. One preview at a time: starting another first keeps the earlier one only if
 * it was confirmed, otherwise puts it back. Pure; SettingsActivity runs the clock.
 */
final class PreviewRevert {
    static final int SECONDS=15;
    static final long MS=SECONDS*1000L;

    /** Whether changing {@code key} from {@code before} to {@code after} needs a preview. */
    static boolean risky(String key,Object before,Object after){
        if(Objects.equals(before,after))return false;
        if(Accessibility.TEXT_SIZE.equals(key))return true;
        if("highContrast".equals(key)||"opaque".equals(key))return Boolean.TRUE.equals(before)&&!Boolean.TRUE.equals(after);
        return false;
    }

    final String key;final Object before,after;final long until;
    PreviewRevert(String key,Object before,Object after,long now){this.key=key;this.before=before;this.after=after;this.until=now+MS;}
    /** Whole seconds left, never below 0. */
    int secondsLeft(long now){return (int)Math.max(0,(until-now+999)/1000);}
    boolean due(long now){return now>=until;}
    /** The line Settings shows while the preview runs. */
    String words(long now){
        SettingsSchema.Setting s=SettingsSchema.of(key);String name=s==null?key:s.name;
        return name+" is now "+(s==null?String.valueOf(after):s.say(after))+". Keep it? It goes back to "+(s==null?String.valueOf(before):s.say(before))+" in "+secondsLeft(now)+" seconds.";
    }
}
