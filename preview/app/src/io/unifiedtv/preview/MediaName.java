package io.unifiedtv.preview;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/**
 * File and folder names as people name them, read into a title, a year and, for programmes, a season and episode.
 * Pure: nothing here touches the network. Only what this returns (a clean title and year) is ever sent to a poster
 * service; the file name, its folder path and the server never are.
 *
 * <pre>
 * "Title (2019).mkv"                        Title · 2019
 * "Title.2019.1080p.BluRay.x264-GRP.mkv"   Title · 2019
 * "Show S01E02 Episode Name.mkv"           Show · season 1, episode 2 · Episode Name
 * "Show.1x02.mkv", "Show - Season 1 Episode 2"
 * "Season 1", "Series 2", "S03", "Specials" (folders)  a season number
 * "www.site.com - Title.2019.1080p", "Title-2019-1080p"   Title · 2019
 * "Title - Extended Edition (2019)"        Title · 2019 (an edition is not part of the title; the year after it still counts)
 * </pre>
 *
 * Samples and extras (owner's TCL, 27 October 2026: a film's folder often holds "sample.mkv" or "Title-sample.mkv"):
 * {@link #sample}, {@link #extra} and {@link #extrasFolder} name them, so the library can leave them out.
 *
 * A number at the very start is part of the title ("1917", "2001 A Space Odyssey"): a year only counts after a title.
 */
final class MediaName {
    private MediaName(){}
    static final class Parsed {
        String title="",year="",episodeTitle="";int season=-1,episode=-1;
        boolean episodic(){return episode>=0;}
        @Override public String toString(){return title+(year.isEmpty()?"":" ("+year+")")+(episode>=0?" S"+season+"E"+episode:"");}
    }
    private static final Pattern EXT=Pattern.compile("(?i)\\.(mkv|mp4|m4v|avi|ts|m2ts|mts|mov|wmv|mpg|mpeg|webm|iso|vob|divx|flv|3gp)$");
    private static final Pattern SXE=Pattern.compile("(?i)(?:^|[\\s\\[(])s(\\d{1,2})\\s?[ex](\\d{1,3})(?:\\s?-?\\s?e\\d{1,3})*(?=$|[\\s\\])-])");
    private static final Pattern NXN=Pattern.compile("(?i)(?:^|\\s)(\\d{1,2})x(\\d{2,3})(?=$|\\s)");
    private static final Pattern WORDS=Pattern.compile("(?i)(?:^|\\s)(?:season|series)\\s*(\\d{1,2})\\s*[,:-]?\\s*(?:episode|ep|e)\\s*(\\d{1,3})(?=$|\\s)");
    private static final Pattern YEAR=Pattern.compile("(?:^|[\\s(\\[])((?:19|20)\\d{2})(?=$|[\\s)\\]])");
    private static final Set<String> JUNK=new HashSet<>(Arrays.asList(
        "480p","576p","720p","1080p","1080i","2160p","4k","uhd","hdr","hdr10","hdr10+","dv","dovi","sdr","bluray","blu-ray","bdrip","brrip","bdremux","remux",
        "webrip","web-dl","webdl","web","hdtv","pdtv","dvdrip","dvd","dvdscr","hdrip","x264","x265","h264","h265","h.264","h.265","hevc","avc","xvid","divx",
        "aac","aac2","ac3","eac3","dd5","ddp5","dts","dts-hd","truehd","atmos","flac","mp3","5.1","7.1","2.0","10bit","8bit","proper","repack","extended",
        "unrated","uncut","remastered","imax","multi","multisub","subs","subbed","dubbed","limited","internal","complete","yify","yts","rarbg","amzn","nf","dsnp","hmax","atvp","itunes"));
    /** A leading web address that some downloads carry: "www.site.org - " or "[ www.site.org ] ". */
    private static final Pattern WEB=Pattern.compile("(?i)^\\s*[\\[(]?\\s*www\\.[^\\s\\])]+\\s*[\\])]?\\s*[-–:]?\\s*");
    /** "Director's Cut", "Extended Edition"...: an edition, never part of the title (cut there like a release tag). */
    private static final Pattern EDITION=Pattern.compile("(?i)(?<=\\s)(?:director'?s|directors|extended|theatrical|ultimate|special|collector'?s|collectors|anniversary|uncut|unrated|remastered|criterion|imax)\\s+(?:cut|edition|version)(?=$|[\\s)\\]])");
    /** A sample tag at the very end of the name ("sample", "Title-sample", "Title.sample", "Title sample", "[Sample]"). */
    private static final Pattern SAMPLE=Pattern.compile("(?i)(?:^|[-._\\s(\\[])samples?\\s*\\d*[)\\]]?$");
    /** An extras tag at the very end, after a separator: "Title - Trailer", "Title.Featurette.2", "Title (Behind the Scenes)". */
    private static final Pattern EXTRA_TAG=Pattern.compile("(?i)(?:\\s[-–]\\s|[-._(\\[])\\s*(?:official\\s+)?(?:trailers?|featurettes?|extras|behind[ ._-]the[ ._-]scenes|deleted[ ._-]scenes)\\s*\\d*[)\\]]?$");
    /** "Title 2019 Trailer" (a file name with its dots made spaces): trailer and featurette only, never "Hollywood Extras". */
    private static final Pattern EXTRA_WORD=Pattern.compile("(?i)\\S\\s+(?:official\\s+)?(?:trailer|featurette)\\s*\\d*$");
    private static final Set<String> EXTRA_NAMES=new HashSet<>(Arrays.asList("trailer","trailers","featurette","featurettes","extra","extras"));
    private static final Set<String> EXTRAS_FOLDERS=new HashSet<>(Arrays.asList("sample","samples","extra","extras","trailer","trailers","featurette","featurettes",
        "behind the scenes","deleted scenes","interviews","bonus features","special features"));
    private static final Pattern FILM_FOLDER=Pattern.compile("(?i)^(?:my |our |the )?(?:films?|movies?|cinema|feature films?)$");
    private static final Set<String> GENERIC=new HashSet<>(Arrays.asList("movie","film","video","main","feature","sample","trailer","title","episode","disc","dvd","bluray"));

    /** A file name (or a server's title, which is often the file name). */
    static Parsed parse(String raw){
        Parsed p=new Parsed();String s=raw==null?"":raw.trim();
        Matcher ext=EXT.matcher(s);if(ext.find()&&ext.start()>0)s=s.substring(0,ext.start());
        String web=WEB.matcher(s).replaceFirst("");if(!web.trim().isEmpty())s=web;
        s=spaces(s);
        s=EDITION.matcher(s).replaceAll("extended");
        // Bracketed tags such as [1080p] or {GRP} go, except a bracketed year.
        s=s.replaceAll("\\[(?!(?:19|20)\\d{2}\\])[^\\]]*\\]"," ").replaceAll("\\{[^}]*\\}"," ").replaceAll("\\s+"," ").trim();
        Matcher m=SXE.matcher(s);Matcher n=NXN.matcher(s);Matcher w=WORDS.matcher(s);int at=-1,end=-1;
        if(m.find()){p.season=num(m.group(1));p.episode=num(m.group(2));at=m.start();end=m.end();}
        else if(w.find()){p.season=num(w.group(1));p.episode=num(w.group(2));at=w.start();end=w.end();}
        else if(n.find()&&n.start()>0){p.season=num(n.group(1));p.episode=num(n.group(2));at=n.start();end=n.end();}
        if(at>=0){
            p.title=tidy(cutJunk(yearOff(s.substring(0,at),p)));
            p.episodeTitle=tidy(cutJunk(s.substring(end)));
            if(p.episodeTitle.matches("(?i)(?:episode|ep|part)\\s*\\d*"))p.episodeTitle="";
            return p;
        }
        String cut=cutJunk(s);
        p.title=tidy(yearOff(cut,p));
        if(p.title.isEmpty()){p.title=tidy(cut);p.year="";}
        // "Title - Extended Edition (2019)": the year sits after a tag that was cut; it is still this title's year.
        if(p.year.isEmpty()&&!p.title.isEmpty()&&cut.length()<s.length()){Parsed whole=new Parsed();yearOff(s,whole);p.year=whole.year;}
        return p;
    }
    /**
     * Takes the last year that follows some title, and drops it and everything after it: release tags are already gone,
     * so "Blade Runner 2049 (2017)" is "Blade Runner 2049" from 2017, and "1917" stays a title.
     */
    private static String yearOff(String s,Parsed p){
        Matcher y=YEAR.matcher(s);String title=null;
        while(y.find()){int start=y.start(1);String before=s.substring(0,start).replaceAll("[\\s(\\[\\-]+$","");
            if(before.trim().isEmpty())continue;
            p.year=y.group(1);title=before;}
        return title==null?s:title;
    }
    /** Cuts at the first release tag ("1080p", "BluRay", "x264"...): what follows is never part of a title. */
    private static String cutJunk(String s){
        String[] tokens=s.split(" ");StringBuilder out=new StringBuilder();
        for(int i=0;i<tokens.length;i++){String t=tokens[i].toLowerCase(Locale.ROOT).replaceAll("^[(\\[]+|[)\\]]+$","");
            String head=t.contains("-")?t.substring(0,t.indexOf('-')):t;
            if(i>0&&(JUNK.contains(t)||JUNK.contains(head)||t.matches("(?:ddp?|dts|aac)\\d.*")))break;
            if(out.length()>0)out.append(' ');out.append(tokens[i]);}
        return out.toString();
    }
    /**
     * Dots and underscores become spaces only where the name looks like a file name: underscores, or words joined by dots
     * with no spaces. "Mr. Smith" and "S.W.A.T." keep their dots; single letters joined by dots are kept together.
     */
    static String spaces(String s){
        if(s.isEmpty())return s;
        boolean file=s.contains("_");
        if(!file&&!s.contains(" ")&&s.contains(".")){for(String part:s.split("\\."))if(part.length()>=2){file=true;break;}}
        // "Title-2019-1080p": words joined by two or more hyphens and nothing else ("Spider-Man" keeps its one hyphen).
        if(!file&&!s.contains(" ")&&!s.contains(".")&&s.indexOf('-')!=s.lastIndexOf('-'))return s.replaceAll("-+"," ").replaceAll("\\s+"," ").trim();
        if(!file)return s.replaceAll("\\s+"," ").trim();
        String[] parts=s.split("[._]+");StringBuilder out=new StringBuilder();
        for(int i=0;i<parts.length;i++){String part=parts[i];if(part.isEmpty())continue;
            boolean letter=part.length()==1&&Character.isLetter(part.charAt(0));
            boolean joinPrev=letter&&i>0&&parts[i-1].length()==1&&Character.isLetter(parts[i-1].charAt(0));
            boolean joinNext=letter&&i+1<parts.length&&parts[i+1].length()==1&&Character.isLetter(parts[i+1].charAt(0));
            if(joinPrev)out.append(part).append('.');
            else{if(out.length()>0)out.append(' ');out.append(part);if(joinNext)out.append('.');}}
        return out.toString().replaceAll("\\s+"," ").trim();
    }
    private static String tidy(String s){
        // A trailing full stop goes, except the last of initials ("S.W.A.T.").
        boolean initials=s.trim().matches("(?:.*\\s)?(?:\\p{L}\\.){2,}");
        String t=(initials?s.trim():s.replaceAll("[\\s\\-–—:,.(\\[]+$","")).replaceAll("^[\\s\\-–—:,.)\\]]+","").replaceAll("\\(\\s*\\)","").replaceAll("\\s+"," ").trim();
        return t;
    }
    private static int num(String s){try{return Integer.parseInt(s);}catch(Exception e){return -1;}}

    /** A season folder's number ("Season 1", "Series 02", "S3", "Staffel 2" is not read); "Specials" is season 0; -1 otherwise. */
    static int seasonFolder(String name){
        String n=name==null?"":spaces(name.trim()).toLowerCase(Locale.ROOT);
        if(n.matches("specials?|extras?"))return 0;
        Matcher m=Pattern.compile("^(?:season|series|s)\\s*(\\d{1,2})$").matcher(n);
        return m.find()?num(m.group(1)):-1;
    }
    /** An episode number from a name with no SxxEyy: "01 - Pilot", "Episode 3", "E04", "Ep 5 The Name". */
    static int episodeNumber(String raw){
        String s=spaces(raw==null?"":raw.trim()).replaceAll(EXT.pattern(),"");
        Matcher m=Pattern.compile("(?i)^(?:episode|ep|e)?\\s*(\\d{1,3})(?=$|[\\s.\\-])").matcher(s);
        if(m.find())return num(m.group(1));
        m=Pattern.compile("(?i)(?:^|\\s)(?:episode|ep)\\s*(\\d{1,3})(?=$|\\s)").matcher(s);
        return m.find()?num(m.group(1)):-1;
    }
    private static String stem(String name){String s=name.trim();Matcher ext=EXT.matcher(s);if(ext.find()&&ext.start()>0)s=s.substring(0,ext.start());return s.trim();}
    /**
     * A sample: the whole name is "sample", or it ends with a sample tag ("Film-sample.mkv", "Some.Film.sample.mkv",
     * "Some Film sample"), in any case. "Free Samples (2012)" and "Sample People" are titles.
     */
    static boolean sample(String name){return name!=null&&SAMPLE.matcher(stem(name)).find();}
    /**
     * A trailer, featurette or extras clip by its name: the whole name ("trailer.mkv"), or a tag at its very end after a
     * separator ("Title - Trailer.mkv", "Some.Film.2019.Featurette.mkv", "Title (Behind the Scenes)"), or "... Trailer" /
     * "... Featurette" after a space. "Trailer Park Boys", "The Extras (2020)" and "Hollywood Extras" are titles, and an
     * episode (S01E02) never counts, so a programme called "Extras" stays.
     */
    static boolean extra(String name){
        if(name==null)return false;String s=stem(name);if(s.isEmpty())return false;
        boolean hit=EXTRA_NAMES.contains(spaces(s).toLowerCase(Locale.ROOT))||EXTRA_TAG.matcher(s).find()||EXTRA_WORD.matcher(spaces(s)).find();
        return hit&&!parse(name).episodic();
    }
    /** A folder that holds only extras or samples ("Sample", "Extras", "Featurettes", "Behind The Scenes"...), by its whole name. */
    static boolean extrasFolder(String name){return name!=null&&EXTRAS_FOLDERS.contains(spaces(name.trim()).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT));}
    /** A folder named for films as a kind ("Films", "Movies", "My Films"): never one film's own name. */
    static boolean filmsFolder(String name){return name!=null&&FILM_FOLDER.matcher(spaces(name.trim())).matches();}
    /** Words that only describe a copy, never a title: release tags, "3D", editions, resolutions. */
    private static final Set<String> COPY_WORDS=new HashSet<>(Arrays.asList("3d","hsbs","sbs","hou","ou","dl","directors","director","cut","edition","extended",
        "theatrical","version","special","ultimate","collectors","anniversary","criterion","open","matte","fullscreen","widescreen"));
    /** Whether normalised words ({@link #normal}) only describe a copy ("3d", "extended edition", "1080p bluray"). */
    static boolean copyWordsOnly(String words){
        String w=words==null?"":words.trim();if(w.isEmpty())return false;
        for(String x:w.split(" "))if(!(JUNK.contains(x)||COPY_WORDS.contains(x)||x.matches("\\d{3,4}[pi]")))return false;
        return true;
    }
    /** A generic word that tells nothing about the title ("movie.mkv" in a folder named after the film). */
    static boolean generic(String title){return title==null||title.trim().isEmpty()||GENERIC.contains(title.trim().toLowerCase(Locale.ROOT));}

    /** For comparing titles: lower case, no accents, "&" as "and", no punctuation, no leading article. */
    static String normal(String s){
        if(s==null)return "";
        String t=Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace("&"," and ");
        t=t.replaceAll("['’`]","").replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
        return t.replaceFirst("^(the|a|an) ","");
    }
}
