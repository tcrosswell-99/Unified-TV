package io.unifiedtv.preview;
import java.util.*;

/**
 * Home's utility pill and its navigation (owner, 25 October 2026, SET-020/NAV-02; v3.4.4 UX-UTILITY-01..04, NAV-07,
 * SET-090..093). Pure: which icons the pill holds and in what order, where a single Up lands, how Left/Right move, the
 * household's configured order (Appearance & Home > Navigation & Shortcuts) and where a single Down lands in the Library.
 *
 * The pill holds, by default and in this order: AirPlay status, NordVPN state, voice, Settings. Text-free glyphs; a short
 * label appears outside the icon only on focus. Items not on this TV are omitted (no AirPin LITE: no AirPlay icon; no
 * NordVPN: no shield). The household may hide or reorder icons, but Settings can never be hidden, so a safe path always
 * remains. D-pad directions are fixed: Up is the pill, Down the Library, Left/Right within the row, Back to the dock.
 * The profile switch is deliberately not in the pill (Settings > Profiles & Family, and the voice command "switch profile").
 */
final class HomeUtilities {
    private HomeUtilities(){}
    static final String AIRPLAY="airplay",VPN="vpn",VOICE="voice",SETTINGS="settings",PREF="utilities",LANDING="libraryLanding";
    static final List<String> DEFAULT=Collections.unmodifiableList(Arrays.asList(AIRPLAY,VPN,VOICE,SETTINGS));
    /** The household's order (saved as "airplay,vpn,voice,settings"); unknown ids dropped; Settings always present. */
    static List<String> order(String saved){
        if(saved==null||saved.trim().isEmpty())return DEFAULT;
        List<String> out=new ArrayList<>();
        for(String raw:saved.split(",")){String id=raw.trim();if(DEFAULT.contains(id)&&!out.contains(id))out.add(id);}
        if(!out.contains(SETTINGS))out.add(SETTINGS);
        return Collections.unmodifiableList(out);
    }
    static String write(List<String> order){return String.join(",",order(String.join(",",order)));}
    /** The pill on this TV: the configured order without what isn't installed. Never empty: Settings stays. */
    static List<String> items(List<String> order,boolean airplayInstalled,boolean vpnInstalled){
        List<String> out=new ArrayList<>();
        for(String id:order(String.join(",",order))){if(id.equals(AIRPLAY)&&!airplayInstalled)continue;if(id.equals(VPN)&&!vpnInstalled)continue;out.add(id);}
        return Collections.unmodifiableList(out);
    }
    /** Shown or hidden in the household's order: hiding removes it, showing puts it back at its default place. Settings stays. */
    static List<String> toggle(List<String> order,String id){
        List<String> out=new ArrayList<>(order(String.join(",",order)));
        if(id.equals(SETTINGS)||!DEFAULT.contains(id))return out;
        if(!out.remove(id)){int at=0;for(String d:DEFAULT){if(d.equals(id))break;if(out.contains(d))at=out.indexOf(d)+1;}out.add(at,id);}
        return Collections.unmodifiableList(out);
    }
    /** One place earlier (-1) or later (+1); the ends stay put. */
    static List<String> move(List<String> order,String id,int delta){
        List<String> out=new ArrayList<>(order(String.join(",",order)));int i=out.indexOf(id),j=i+delta;
        if(i>=0&&j>=0&&j<out.size())Collections.swap(out,i,j);
        return Collections.unmodifiableList(out);
    }
    /** A single Up lands on the pill's first icon. */
    static int landing(List<String> items){return items.isEmpty()?-1:0;}
    /** Left/Right within the pill: clamped at the ends, never wrapping and never leaving it. */
    static int step(int index,int delta,int size){if(size<=0)return -1;return Math.max(0,Math.min(size-1,index+delta));}
    /** The same icon after the pill changed, by id; else its first icon. */
    static int keep(List<String> before,int index,List<String> after){
        if(index>=0&&index<before.size()){int i=after.indexOf(before.get(index));if(i>=0)return i;}
        return landing(after);
    }
    /** The short label shown outside the focused icon (and spoken). */
    static String label(String id,String vpnState,boolean airplayReady){return label(id,vpnState,airplayReady,false);}
    /** While a check is running and AirPlay isn't confirmed yet, it says so, rather than repeating an answer that may be stale. */
    static String label(String id,String vpnState,boolean airplayReady,boolean airplayChecking){
        switch(id){
            case AIRPLAY:return airplayReady?"AirPlay ready":airplayChecking?"AirPlay: checking AirPin…":"AirPlay: AirPin isn't offering AirPlay right now";
            case VPN:return vpnState;
            case VOICE:return "Ask your TV";
            case SETTINGS:return "Settings";
            default:return id;
        }
    }
    // --- one rule for the pill's status icons (owner's TCL, build run 99: the shield looked "on" with NordVPN off)
    /** Inactive or off: the same dim for every status icon. Active: bright. */
    static final int DIM=90,BRIGHT=245;
    /** The small mark inside or across an icon: an "off" slash, a "connected" tick, a "checking" dot, or nothing. */
    enum Mark{NONE,OFF,TICK,DOT}
    /** How a status icon is drawn: its alpha and its mark. */
    static final class Look{
        final int alpha;final Mark mark;
        Look(int alpha,Mark mark){this.alpha=alpha;this.mark=mark;}
        boolean bright(){return alpha==BRIGHT;}
        @Override public String toString(){return alpha+"/"+mark;}
    }
    /** The VPN shield: bright with a tick only while Android reports a VPN; otherwise dim, with a slash when it is off. */
    static Look vpnLook(VpnGate.State state){
        if(state==VpnGate.State.CONNECTED)return new Look(BRIGHT,Mark.TICK);
        if(state==VpnGate.State.CHECKING)return new Look(DIM,Mark.DOT);
        return new Look(DIM,state==VpnGate.State.DISCONNECTED?Mark.OFF:Mark.NONE);
    }
    /** AirPlay: bright while AirPin is offering AirPlay; otherwise dim with the same slash (a dot while it is being checked). */
    static Look airPlayLook(boolean ready,boolean checking){
        if(ready)return new Look(BRIGHT,Mark.NONE);
        return new Look(DIM,checking?Mark.DOT:Mark.OFF);
    }

    /** The microphone's focus label: local requests always work; general questions need the household's assistant. */
    static String voiceLabel(boolean assistant){return assistant?"Ask your TV":"Ask your TV · apps and films (no assistant set up)";}
    static String name(String id){switch(id){case AIRPLAY:return "AirPlay";case VPN:return "NordVPN";case VOICE:return "Voice";case SETTINGS:return "Settings";default:return id;}}

    /**
     * NAV-03: a single Down lands on the first meaningful title. With "continue" (the default), the first title with a
     * saved place on this TV (Continue Watching); otherwise, or with "first", the first title. resume[i] &gt; 0 marks a
     * saved place. With no titles at all, 0: the Library card (set up, reconnect or "ask an adult").
     */
    static int libraryLanding(long[] resume,String mode){
        if(resume==null||resume.length==0)return 0;
        if(!"first".equals(mode))for(int i=0;i<resume.length;i++)if(resume[i]>0)return i;
        return 0;
    }
}
