package io.unifiedtv.preview;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.util.*;

/**
 * SET-072, read-only: where this TV's sound is going, in plain words ("TV speakers", "HDMI ARC", "Bluetooth: Echo
 * Studio"). Unified TV never changes the output or the volume: those belong to the TV's own settings and remote.
 *
 * Android 13 and later say which output media is playing through (getAudioDevicesForAttributes). Earlier versions only
 * list the outputs connected, so Unified TV names the likely one, as Android usually routes media, and says it is a
 * likely answer, not a certain one. Names come from Android's own output types, never from the TV's brand.
 */
final class SoundOutput {
    private SoundOutput(){}

    /** One output as Android reports it: its type (AudioDeviceInfo.TYPE_*) and product name. */
    static final class Out {
        final int type;final String name;
        Out(int type,String name){this.type=type;this.name=name==null?"":name.trim();}
    }
    // Types newer than minSdk 28, as literals (the values are fixed by the platform).
    static final int BLE_HEADSET=26,BLE_SPEAKER=27,HDMI_EARC=29,BLE_BROADCAST=30;

    /** The plain name for an output, or null for one that isn't for listening (earpiece, telephony, internal routes). */
    static String words(Out o){
        switch(o.type){
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:return "TV speakers";
            case HDMI_EARC:return "HDMI eARC"+device(o);
            case AudioDeviceInfo.TYPE_HDMI_ARC:return "HDMI ARC"+device(o);
            case AudioDeviceInfo.TYPE_HDMI:return "HDMI"+device(o);
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:case BLE_HEADSET:case BLE_SPEAKER:case BLE_BROADCAST:
                return o.name.isEmpty()?"A Bluetooth device":"Bluetooth: "+o.name;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:case AudioDeviceInfo.TYPE_WIRED_HEADSET:return "Headphones";
            case AudioDeviceInfo.TYPE_USB_DEVICE:case AudioDeviceInfo.TYPE_USB_HEADSET:case AudioDeviceInfo.TYPE_USB_ACCESSORY:
                return o.name.isEmpty()?"USB audio":"USB: "+o.name;
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:return "Digital audio out (optical)";
            case AudioDeviceInfo.TYPE_LINE_ANALOG:case AudioDeviceInfo.TYPE_AUX_LINE:return "Audio line out";
            case AudioDeviceInfo.TYPE_HEARING_AID:return o.name.isEmpty()?"A hearing aid":"Hearing aid: "+o.name;
            default:return null;
        }
    }
    /** A sound system's own name after an HDMI output, when Android gives a useful one. */
    private static String device(Out o){
        String n=o.name;if(n.isEmpty()||n.equalsIgnoreCase("hdmi")||n.equalsIgnoreCase("hdmi arc")||n.equalsIgnoreCase("hdmi earc"))return "";
        return " ("+n+")";
    }
    /** How Android usually prefers outputs for media when it doesn't say: higher first. */
    static int rank(int type){
        switch(type){
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:case BLE_HEADSET:case BLE_SPEAKER:case BLE_BROADCAST:case AudioDeviceInfo.TYPE_HEARING_AID:return 7;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:case AudioDeviceInfo.TYPE_WIRED_HEADSET:return 6;
            case AudioDeviceInfo.TYPE_USB_DEVICE:case AudioDeviceInfo.TYPE_USB_HEADSET:case AudioDeviceInfo.TYPE_USB_ACCESSORY:return 5;
            case HDMI_EARC:case AudioDeviceInfo.TYPE_HDMI_ARC:return 4;
            case AudioDeviceInfo.TYPE_HDMI:return 3;
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:case AudioDeviceInfo.TYPE_LINE_ANALOG:case AudioDeviceInfo.TYPE_AUX_LINE:return 2;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:return 1;
            default:return 0;
        }
    }
    /** The listening outputs, by plain name, each once, in Android's order. */
    static List<String> names(List<Out> outs){LinkedHashSet<String> s=new LinkedHashSet<>();for(Out o:outs){String w=words(o);if(w!=null)s.add(w);}return new ArrayList<>(s);}
    /** The output media most likely plays through, when Android doesn't say; null with no listening output. */
    static Out likely(List<Out> outs){Out best=null;for(Out o:outs)if(words(o)!=null&&(best==null||rank(o.type)>rank(best.type)))best=o;return best;}

    /** What the Connections page says: the main line, then a note. */
    static final class Summary {
        final String line,note;
        Summary(String line,String note){this.line=line;this.note=note;}
    }
    /** {@code current}: what Android says media plays through now (null when it doesn't say); {@code connected}: all outputs. */
    static Summary summary(List<Out> current,List<Out> connected){
        List<String> now=current==null?Collections.<String>emptyList():names(current);
        List<String> all=names(connected);
        if(!now.isEmpty())return new Summary("Sound plays through: "+String.join(" and ",now),all.size()>now.size()?"Also connected: "+others(all,now)+".":null);
        Out guess=likely(connected);
        if(guess==null)return new Summary("Sound output: not reported","This TV doesn't tell apps where its sound goes.");
        String g=words(guess);
        return new Summary("Sound probably plays through: "+g,(all.size()>1?"Connected: "+String.join(" · ",all)+". ":"")+"This Android version doesn't tell apps for certain which one is playing.");
    }
    private static String others(List<String> all,List<String> now){List<String> o=new ArrayList<>(all);o.removeAll(now);return String.join(" · ",o);}

    // --- on the TV
    static List<Out> read(Context c){
        List<Out> out=new ArrayList<>();
        try{AudioManager am=(AudioManager)c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);if(am==null)return out;
            for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))out.add(of(d));}
        catch(RuntimeException ignored){}
        return out;
    }
    /** Android 13+: the outputs media is routed to right now; null on earlier versions (they don't say). */
    static List<Out> current(Context c){
        if(Build.VERSION.SDK_INT<33)return null;
        List<Out> out=new ArrayList<>();
        try{AudioManager am=(AudioManager)c.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);if(am==null)return null;
            AudioAttributes media=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
            for(AudioDeviceInfo d:am.getAudioDevicesForAttributes(media))out.add(of(d));}
        catch(RuntimeException e){return null;}
        return out;
    }
    /** One device as an Out; a device whose name can't be read is still listed, unnamed. */
    private static Out of(AudioDeviceInfo d){
        String name;try{CharSequence n=d.getProductName();name=n==null?"":n.toString();}catch(RuntimeException e){name="";}
        return new Out(d.getType(),name);
    }
    static Summary now(Context c){return summary(current(c),read(c));}
}
