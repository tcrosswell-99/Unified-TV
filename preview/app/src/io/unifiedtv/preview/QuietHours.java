package io.unifiedtv.preview;

import android.content.SharedPreferences;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * SET-052 quiet hours, on this TV only: between the two times, a confirmed person at the door does not get a toast.
 * Nothing else is quietened. Doorbell presses still open the camera (Unified TV plays no doorbell sound of its own, so
 * there is no chime to silence), and smoke and carbon monoxide alarms are never consulted against quiet hours at all.
 * A start later than the end runs overnight (22:00 to 07:00); equal times mean no quiet time.
 */
final class QuietHours {
    private QuietHours(){}
    static final String ON="quietHours",START="quietStart",END="quietEnd";
    static final int DEFAULT_START=22*60,DEFAULT_END=7*60,DAY=24*60;

    static boolean minute(int m){return m>=0&&m<DAY;}
    /** Whether {@code now} (minutes after midnight) falls inside start..end, overnight when start is later than end. */
    static boolean inside(int start,int end,int now){
        if(!minute(start)||!minute(end)||!minute(now)||start==end)return false;
        return start<end?now>=start&&now<end:now>=start||now<end;
    }
    /** A person toast may show: quiet hours off, or outside them. */
    static boolean personAllowed(boolean on,int start,int end,int now){return !on||!inside(start,end,now);}
    static String clock(Object minutes){int m=minutes instanceof Integer?(Integer)minutes:0;return String.format(Locale.ROOT,"%02d:%02d",m/60,m%60);}
    /** Every half hour, for the remote: 00:00, 00:30, … 23:30. */
    static int[] choices(){int[] out=new int[48];for(int i=0;i<out.length;i++)out[i]=i*30;return out;}

    static int start(SharedPreferences p){try{int v=p.getInt(START,DEFAULT_START);return minute(v)?v:DEFAULT_START;}catch(RuntimeException e){return DEFAULT_START;}}
    static int end(SharedPreferences p){try{int v=p.getInt(END,DEFAULT_END);return minute(v)?v:DEFAULT_END;}catch(RuntimeException e){return DEFAULT_END;}}
    static boolean on(SharedPreferences p){try{return p.getBoolean(ON,false);}catch(RuntimeException e){return false;}}
    /** Now, on the household's clock (its time zone setting). */
    static boolean personAllowed(SharedPreferences p,ZoneId zone,long now){
        LocalTime t=ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now),zone).toLocalTime();
        return personAllowed(on(p),start(p),end(p),t.getHour()*60+t.getMinute());
    }
    static String summary(SharedPreferences p){return on(p)?"From "+clock(start(p))+" until "+clock(end(p)):"Off";}
}
