package io.unifiedtv.preview;

import java.util.Locale;

/** Pure player rules: remote seek steps, local resume points, stall limits and plain failure wording. No Android or Media3 types. */
final class PlaybackPolicy {
    private PlaybackPolicy(){}
    static final long STEP_MS=10_000;
    /** Buffering longer than this with play requested is treated as a stalled connection, never an endless spinner. */
    static final long STALL_MS=30_000;
    /** Shorter positions are not worth offering to resume; nearer the end than this counts as watched. */
    static final long MIN_RESUME_MS=30_000,END_MARGIN_MS=90_000;

    /** Seek for one D-pad press. Held keys repeat about 20 times a second, so only every fourth repeat moves, faster the longer it is held. */
    static long seekStep(int repeatCount){
        if(repeatCount<=0)return STEP_MS;
        if(repeatCount%4!=0)return 0;
        return repeatCount>=100?60_000:repeatCount>=40?30_000:STEP_MS;
    }
    /** Never before the start, never past the last second when the length is known. A negative or unknown duration leaves the end open. */
    static long seekTarget(long position,long delta,long duration){
        long target=Math.max(0,position+delta);
        if(duration>0)target=Math.min(target,Math.max(0,duration-1000));
        return target;
    }
    /** True when this position is near enough the end to count as watched, so the saved place on this TV is cleared. */
    static boolean finished(long position,long duration){
        return duration>0&&position>=duration-Math.min(END_MARGIN_MS,duration/10);
    }
    /** The place to offer on this TV, or 0 to start from the beginning. */
    static long resumePoint(long saved,long duration){
        if(saved<MIN_RESUME_MS||finished(saved,duration))return 0;
        return saved;
    }
    static boolean stalled(long bufferingSince,long now){return bufferingSince>0&&now-bufferingSince>=STALL_MS;}

    /** PICTURE when the file has video but none this TV can decode, SOUND likewise for audio, otherwise null. */
    static Kind unplayable(java.util.List<PlaybackDiagnostics.Track> tracks){
        boolean video=false,videoOk=false,audio=false,audioOk=false;
        for(PlaybackDiagnostics.Track t:tracks){
            if(t.type==PlaybackDiagnostics.VIDEO){video=true;videoOk|=t.supported;}
            if(t.type==PlaybackDiagnostics.AUDIO){audio=true;audioOk|=t.supported;}
        }
        return video&&!videoOk?Kind.PICTURE:audio&&!audioOk?Kind.SOUND:null;
    }
    /** 42:10 or 1:02:03, for "Resume from …" labels. */
    static String clock(long ms){
        long s=Math.max(0,ms)/1000,h=s/3600,m=(s%3600)/60,sec=s%60;
        return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,sec):String.format(Locale.ROOT,"%d:%02d",m,sec);
    }

    enum Kind{NETWORK,REFUSED,MISSING,FORMAT,PICTURE,SOUND,PROTECTED,STALLED,OTHER}
    /** Media3 groups PlaybackException codes by thousands: 2xxx input/output, 3xxx container parsing, 4xxx decoding, 5xxx audio output, 6xxx DRM. */
    static Kind kind(int errorCode){
        switch(errorCode){
            case 1003:case 2001:case 2002:case 2000:return Kind.NETWORK;          // TIMEOUT, IO_NETWORK_CONNECTION_FAILED/TIMEOUT, IO_UNSPECIFIED
            case 2004:case 2006:case 2007:return Kind.REFUSED;                    // IO_BAD_HTTP_STATUS, IO_NO_PERMISSION, IO_CLEARTEXT_NOT_PERMITTED
            case 2005:case 2008:return Kind.MISSING;                              // IO_FILE_NOT_FOUND, IO_READ_POSITION_OUT_OF_RANGE
            case 2003:return Kind.FORMAT;                                         // IO_INVALID_HTTP_CONTENT_TYPE
            default:break;
        }
        int group=errorCode/1000;
        if(group==3)return Kind.FORMAT;
        if(group==4)return Kind.PICTURE;
        if(group==5)return Kind.SOUND;
        if(group==6)return Kind.PROTECTED;
        return Kind.OTHER;
    }
    /** Whether trying the same title again could help. A format this TV can't play won't change on Retry. */
    static boolean retryable(Kind kind){return kind==Kind.NETWORK||kind==Kind.STALLED||kind==Kind.OTHER||kind==Kind.REFUSED;}
    static String message(Kind kind){
        switch(kind){
            case NETWORK:return "The media server stopped answering. Check that it is switched on and that the TV is connected, then try again.";
            case STALLED:return "This film stopped loading. The connection to the media server may be too slow or has dropped.";
            case REFUSED:return "The media server would not send this film to the TV. Check its media sharing settings for this TV.";
            case MISSING:return "This film is no longer where the media server said it was. It may have been moved or renamed.";
            case FORMAT:return "This file's format isn't supported by the TV's player.";
            case PICTURE:return "This TV can't play the picture format of this file.";
            case SOUND:return "This TV can't play the sound format of this file.";
            case PROTECTED:return "This film is copy-protected and can't be played here.";
            default:return "This film could not be played.";
        }
    }
}
