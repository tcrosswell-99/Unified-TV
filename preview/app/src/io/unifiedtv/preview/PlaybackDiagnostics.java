package io.unifiedtv.preview;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * In-memory evidence for the latest playback (P05-07): time to first picture and the formats the TV was offered.
 * Never holds titles, addresses, folder names or file names, and is never written to logcat. A later evidence step reads {@link #last}.
 */
final class PlaybackDiagnostics {
    static final int AUDIO=1,VIDEO=2,TEXT=3;
    static final class Track {
        final int type,width,height,channels,sampleRate;final float frameRate;final String mime,codecs,language;final boolean supported,selected;
        Track(int type,String mime,String codecs,String language,int width,int height,float frameRate,int channels,int sampleRate,boolean supported,boolean selected){
            this.type=type;this.mime=clean(mime);this.codecs=clean(codecs);this.language=clean(language);this.width=width;this.height=height;this.frameRate=frameRate;this.channels=channels;this.sampleRate=sampleRate;this.supported=supported;this.selected=selected;
        }
    }
    static volatile PlaybackDiagnostics last;

    final String source;private final long openedAt;private long preparedAt=-1,firstFrameAt=-1;private long resumedFrom;
    private String container="",errorName="",errorKind="";private int seeks,rebuffers,retries;
    private final List<Track> tracks=new ArrayList<>();

    private PlaybackDiagnostics(String source,long openedAt){this.source=source;this.openedAt=openedAt;}
    /** Starts a new record for one opened title; the source is "nas" or "media-account", never an address. */
    static PlaybackDiagnostics begin(String source,long now){PlaybackDiagnostics d=new PlaybackDiagnostics(source,now);last=d;return d;}

    synchronized void prepared(long now,long resumeFrom){if(preparedAt<0)preparedAt=now;else retries++;resumedFrom=Math.max(0,resumeFrom);}
    synchronized void firstFrame(long now){if(firstFrameAt<0)firstFrameAt=now;}
    synchronized void seek(){seeks++;}
    /** Playback paused to load more after the first picture. */
    synchronized void rebuffer(){rebuffers++;}
    synchronized void failed(String codeName,PlaybackPolicy.Kind kind){errorName=clean(codeName);errorKind=kind.name();}
    synchronized void tracks(String containerMime,List<Track> offered){container=clean(containerMime);tracks.clear();tracks.addAll(offered);}
    /** Open-to-first-picture time in milliseconds, or -1 before the first picture. */
    synchronized long firstFrameMs(){return firstFrameAt<0?-1:firstFrameAt-openedAt;}
    synchronized int count(int type){int n=0;for(Track t:tracks)if(t.type==type)n++;return n;}

    synchronized JSONObject json(){
        JSONObject out=new JSONObject();
        try{
            out.put("source",source).put("firstFrameMs",firstFrameMs()).put("prepareToFirstFrameMs",preparedAt<0||firstFrameAt<0?-1:firstFrameAt-preparedAt)
                .put("resumedFromMs",resumedFrom).put("container",container).put("seeks",seeks).put("rebuffers",rebuffers).put("retries",retries)
                .put("audioTracks",count(AUDIO)).put("subtitleTracks",count(TEXT)).put("error",errorName).put("errorKind",errorKind);
            JSONArray list=new JSONArray();
            for(Track t:tracks){
                JSONObject o=new JSONObject().put("type",t.type==VIDEO?"video":t.type==AUDIO?"audio":t.type==TEXT?"subtitle":"other").put("mime",t.mime).put("codecs",t.codecs)
                    .put("supported",t.supported).put("selected",t.selected);
                if(t.type==VIDEO)o.put("width",t.width).put("height",t.height).put("frameRate",t.frameRate>0?Math.round(t.frameRate*1000)/1000.0:-1);
                if(t.type==AUDIO)o.put("channels",t.channels).put("sampleRate",t.sampleRate);
                if(t.type!=VIDEO)o.put("language",t.language);
                list.put(o);
            }
            out.put("tracks",list);
        }catch(Exception ignored){/* Values above are plain numbers and short strings. */}
        return out;
    }

    /** Short technical identifiers only: keeps codec strings, drops anything long enough to carry a path or name. */
    static String clean(String s){if(s==null)return "";s=s.trim();return s.length()>48||s.contains("//")?"":s;}

    /** "English · 5.1" for the sound menu; falls back to a numbered choice when the file says nothing. */
    static String soundLabel(String language,int channels,int index,Locale display){
        String name=languageName(language,display),layout=channels<=0?"":channels==1?"mono":channels==2?"stereo":channels==6?"5.1":channels==8?"7.1":channels+" channels";
        String base=name.isEmpty()?"Sound "+(index+1):name;return layout.isEmpty()?base:base+" · "+layout;
    }
    static String subtitleLabel(String language,int index,Locale display){
        String name=languageName(language,display);return name.isEmpty()?"Subtitles "+(index+1):name;
    }
    static String languageName(String language,Locale display){
        if(language==null||language.isEmpty()||language.equalsIgnoreCase("und"))return "";
        String name=Locale.forLanguageTag(language).getDisplayLanguage(display);
        return name==null||name.isEmpty()||name.equalsIgnoreCase(language)&&language.length()>3?"":name;
    }
}
