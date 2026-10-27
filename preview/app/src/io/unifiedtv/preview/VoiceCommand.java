package io.unifiedtv.preview;
import java.util.Locale;
/**
 * Unrecognised speech never acquires authority to execute household services. Live TV ("live") is recognised only to go
 * through the same gate as the dock (Launch.open: OD-26's rule and the Kids list); voice is never a way round it.
 */
final class VoiceCommand {
    static String classify(String request){
        String q=request.trim().toLowerCase(Locale.ROOT);
        if(q.contains("live tv")||q.contains("reamsmart")||q.contains("smarters")||q.contains("iptv"))return "live";
        if(q.equals("switch profile"))return "profile";
        if(q.equals("show camera")||q.equals("show ring camera"))return "camera";
        if(q.startsWith("find "))return "find";
        if(q.equals("open bbc"))return "bbc";
        if(q.equals("open netflix"))return "netflix";
        if(q.equals("open disney")||q.equals("open disney plus"))return "disney";
        if(music(request)!=null)return "music";
        return "discovery";
    }

    /**
     * "Play …" (OD-27): what to play in Amazon Music, what kind of thing, and where. {@code place} is "" for this profile's
     * Play on choice, {@link Echoes#TV}, {@link Echoes#EVERYWHERE}, or an Echo's name as spoken ("play jazz on kitchen").
     */
    static final class Music {
        final String query,place;final AmazonMusic.Focus focus;
        Music(String query,AmazonMusic.Focus focus,String place){this.query=query;this.focus=focus;this.place=place;}
    }
    /** Other apps a "play … on …" may name: never sent to Amazon Music or an Echo. */
    private static final String[] OTHER_APPS={"netflix","bbc","iplayer","bbc iplayer","disney","disney plus","youtube","smarttube","spotify","live tv"};
    private static final String[][] FOCUS={{"the album ","ALBUM"},{"album ","ALBUM"},{"the playlist ","PLAYLIST"},{"my playlist ","PLAYLIST"},{"playlist ","PLAYLIST"},
        {"songs by ","ARTIST"},{"music by ","ARTIST"},{"the artist ","ARTIST"},{"artist ","ARTIST"},{"the song ","SONG"},{"song ","SONG"},{"the track ","SONG"}};

    /** Pure: the request as music, or null when it isn't "play" and something. */
    static Music music(String request){
        if(request==null)return null;
        String s=request.trim().replaceAll("[.!?]+$","").trim();
        if(!starts(s,"play ")||s.toLowerCase(Locale.ROOT).contains("live tv"))return null;
        s=s.substring(5).trim();String place="";
        s=dropEnd(s,new String[]{" on amazon music"," in amazon music"," from amazon music"," with amazon music"});
        if(ends(s," everywhere")){place=Echoes.EVERYWHERE;s=s.substring(0,s.length()-11).trim();}
        else{String here=endOf(s,new String[]{" on this tv"," on the tv"," on tv"," here"});
            if(here!=null){place=Echoes.TV;s=s.substring(0,s.length()-here.length()).trim();}
            else{int on=s.toLowerCase(Locale.ROOT).lastIndexOf(" on ");
                if(on>0){String where=s.substring(on+4).trim();
                    for(String app:OTHER_APPS)if(where.equalsIgnoreCase(app))return null;
                    place=where;s=s.substring(0,on).trim();}}}
        s=dropEnd(s,new String[]{" on amazon music"," in amazon music"," from amazon music"});
        AmazonMusic.Focus focus=AmazonMusic.Focus.ANY;
        for(String[] f:FOCUS)if(starts(s,f[0])){focus=AmazonMusic.Focus.valueOf(f[1]);s=s.substring(f[0].length()).trim();break;}
        if(focus==AmazonMusic.Focus.ANY&&ends(s," playlist")){focus=AmazonMusic.Focus.PLAYLIST;s=s.substring(0,s.length()-9).trim();}
        return s.isEmpty()?null:new Music(s,focus,place);
    }
    private static boolean starts(String s,String p){return s.regionMatches(true,0,p,0,p.length());}
    private static boolean ends(String s,String p){return s.length()>=p.length()&&s.regionMatches(true,s.length()-p.length(),p,0,p.length());}
    private static String endOf(String s,String[] options){for(String o:options)if(ends(s,o))return o;return null;}
    private static String dropEnd(String s,String[] options){String e=endOf(s,options);return e==null?s:s.substring(0,s.length()-e.length()).trim();}
}
