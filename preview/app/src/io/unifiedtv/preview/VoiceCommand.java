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
     * Play on choice, {@link Echoes#TV} or {@link Echoes#EVERYWHERE}, or, for "… on <words>", those words as a possible
     * Echo name ({@link #maybeEcho()}). Song titles hold "on" too ("Carry On Wayward Son", "Shine On You Crazy Diamond"),
     * so the words are a place only when they name an Echo Home Assistant found (VoiceActivity checks); otherwise the whole
     * phrase ({@code whole}, {@code wholeFocus}) is played where this profile plays.
     */
    static final class Music {
        final String query,place,whole;final AmazonMusic.Focus focus,wholeFocus;
        Music(String query,AmazonMusic.Focus focus,String place){this(query,focus,place,query,focus);}
        Music(String query,AmazonMusic.Focus focus,String place,String whole,AmazonMusic.Focus wholeFocus){
            this.query=query;this.focus=focus;this.place=place;this.whole=whole;this.wholeFocus=wholeFocus;}
        /** True when {@code place} is only words after " on " that may or may not name an Echo. */
        boolean maybeEcho(){return !place.isEmpty()&&!Echoes.TV.equals(place)&&!Echoes.EVERYWHERE.equals(place);}
    }
    /** Other apps a "play … on …" may name: never sent to Amazon Music or an Echo. */
    private static final String[] OTHER_APPS={"netflix","bbc","iplayer","bbc iplayer","disney","disney plus","youtube","smarttube","spotify","live tv"};
    private static final String[][] FOCUS={{"the album ","ALBUM"},{"album ","ALBUM"},{"the playlist ","PLAYLIST"},{"my playlist ","PLAYLIST"},{"playlist ","PLAYLIST"},
        {"songs by ","ARTIST"},{"music by ","ARTIST"},{"the artist ","ARTIST"},{"artist ","ARTIST"},{"the song ","SONG"},{"song ","SONG"},{"the track ","SONG"}};
    private static final String[] AMAZON_MUSIC={" on amazon music"," in amazon music"," from amazon music"," with amazon music"};

    /** Pure: the request as music, or null when it isn't "play" and something. */
    static Music music(String request){
        if(request==null)return null;
        String s=request.trim().replaceAll("[.!?]+$","").trim();
        if(!starts(s,"play ")||s.toLowerCase(Locale.ROOT).contains("live tv"))return null;
        s=dropEnd(s.substring(5).trim(),AMAZON_MUSIC);String place="";
        if(ends(s," everywhere")){place=Echoes.EVERYWHERE;s=s.substring(0,s.length()-11).trim();}
        else{String here=endOf(s,new String[]{" on this tv"," on the tv"," on tv"," here"});
            if(here!=null){place=Echoes.TV;s=s.substring(0,s.length()-here.length()).trim();}}
        s=dropEnd(s,AMAZON_MUSIC);
        String[] whole=focus(s);if(whole[1].isEmpty())return null;
        if(!place.isEmpty())return new Music(whole[1],AmazonMusic.Focus.valueOf(whole[0]),place);
        int on=s.toLowerCase(Locale.ROOT).lastIndexOf(" on ");
        if(on>0){String where=s.substring(on+4).trim();
            for(String app:OTHER_APPS)if(where.equalsIgnoreCase(app))return null;
            String[] before=focus(dropEnd(s.substring(0,on).trim(),AMAZON_MUSIC));
            if(!before[1].isEmpty()&&!where.isEmpty())
                return new Music(before[1],AmazonMusic.Focus.valueOf(before[0]),where,whole[1],AmazonMusic.Focus.valueOf(whole[0]));}
        return new Music(whole[1],AmazonMusic.Focus.valueOf(whole[0]),"");
    }
    /** {focus name, the words without "the album", "songs by", "… playlist" and so on}. */
    private static String[] focus(String s){
        for(String[] f:FOCUS)if(starts(s,f[0]))return new String[]{f[1],s.substring(f[0].length()).trim()};
        if(ends(s," playlist"))return new String[]{"PLAYLIST",s.substring(0,s.length()-9).trim()};
        return new String[]{"ANY",s};
    }
    private static boolean starts(String s,String p){return s.regionMatches(true,0,p,0,p.length());}
    private static boolean ends(String s,String p){return s.length()>=p.length()&&s.regionMatches(true,s.length()-p.length(),p,0,p.length());}
    private static String endOf(String s,String[] options){for(String o:options)if(ends(s,o))return o;return null;}
    private static String dropEnd(String s,String[] options){String e=endOf(s,options);return e==null?s:s.substring(0,s.length()-e.length()).trim();}
}
