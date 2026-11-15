package io.unifiedtv.preview;
import java.net.URI;
import java.util.*;

/**
 * The saved list (LibraryIndex) turned into what a viewer browses: films, programmes grouped by series and season, and
 * poster rows. Pure, so the rows, the Kids scope, Continue Watching and search are all tested without a TV.
 *
 * Profiles (owner, 25 October 2026): Kids see only titles inside Kids folders whose rating suits Kids (KidsPolicy);
 * Adults see everything, Kids titles included, with a Kids row of their own.
 *
 * Rows, in order: Continue Watching (this profile's places on this TV, most recent first; a title watched to the end has
 * no place and drops off), Favourites (this profile's own, newest first, only when there are some: SET-032), Recently
 * added (titles found after the list was first made), Films, TV, and for Adults Kids.
 * Each row shows its first {@link #ROW_MAX} and opens "All" for the rest.
 *
 * Series: a name with S01E02 (or 1x02, "Season 1 Episode 2") is an episode; so is a file inside a "Season 1" folder, or
 * inside a programme's own folder in a folder named like TV ("TV", "Series", "Shows", "Programmes"). Episodes of one
 * programme share one tile. Anything else is a film.
 *
 * Films in folders (owner's TCL, 27 October 2026, Synology Media Server): many films sit in a folder of their own
 * ("Films/Title (2019)/Title.2019.1080p.mkv", perhaps with "sample.mkv"). Samples, trailers and extras are left out
 * ({@link #mains}), and a folder that comes down to one main video is that film: one tile, one OK plays it, named and
 * dated from the folder when the folder says more than the file ({@link #folderSaysMore}). A folder with several main
 * videos (a season, a collection) shows each one, as before. Inside a folder named for TV, programmes work as before;
 * a "Films" folder inside one is films.
 */
final class LibraryCatalogue {
    static final int ROW_MAX=30,CONTINUE_MAX=20,RECENT_MAX=30;
    /** A sample by size: under a tenth of its folder's largest video and under 300 MB. */
    static final long SAMPLE_MAX_BYTES=300L*1024*1024;static final int SAMPLE_SHARE=10;
    /** A sample by length: under five minutes beside a main video over forty. */
    static final long SHORT_MS=5*60_000L,FEATURE_MS=40*60_000L;
    /** At most this many files in one folder are taken for samples by size or length. */
    static final int SAMPLES_MAX=2;
    static final long RECENT_GRACE_MS=60_000;
    enum Kind{FILM,EPISODE}

    static final class Title {
        String stableId="",id="",root="",name="",year="",episodeName="",url="",art="",upnpClass="";
        Kind kind=Kind.FILM;int season=-1,episode=-1;boolean kids;long seen;int depth;final List<String> ratings=new ArrayList<>();
        Show show;String showName="",showYear="",showKey="";
        /** What a tile says: the film's name, or "S1 E2 · Name" for an episode. */
        String label(){
            if(kind==Kind.FILM)return name;
            String se=(season>=0?"S"+season+" ":"")+(episode>=0?"E"+episode:"");se=se.trim();
            return se.isEmpty()?(episodeName.isEmpty()?name:episodeName):episodeName.isEmpty()?se:se+" · "+episodeName;
        }
        CatalogueItem item(String udn){
            return new CatalogueItem("dlna",udn,id,kind==Kind.EPISODE?showName:name,year,kind==Kind.EPISODE?CatalogueItem.Kind.EPISODE:CatalogueItem.Kind.FILM,"",root,ratings,art);
        }
    }
    static final class Show {
        final String key,name,year;boolean kids=true;long seen;final List<Title> episodes=new ArrayList<>();String art="";
        Show(String key,String name,String year){this.key=key;this.name=name;this.year=year;}
        /** Seasons in order (-1, no season known, last). */
        List<Integer> seasons(){TreeSet<Integer> s=new TreeSet<>((a,b)->a<0?(b<0?0:1):b<0?-1:Integer.compare(a,b));for(Title t:episodes)s.add(t.season);return new ArrayList<>(s);}
    }
    /** One tile: a film or episode, or a whole programme. */
    static final class Card {
        final Title title;final Show show;long resume,duration,at;
        Card(Title t){title=t;show=null;}Card(Show s){title=null;show=s;}
        /** Stable across refreshes: the focus and the poster cache follow it. */
        String key(){return title!=null?"t:"+title.stableId:"s:"+show.key;}
        String name(){return title!=null?(title.kind==Kind.EPISODE?title.showName:title.name):show.name;}
        String year(){return title!=null?(title.kind==Kind.EPISODE?title.showYear:title.year):show.year;}
        /** The line under the name: an episode's number and name. */
        String detail(){return title!=null&&title.kind==Kind.EPISODE?title.label():"";}
        boolean kids(){return title!=null?title.kids:show.kids;}
        /** The poster to look up: a film's own, or its programme's for a series or an episode. */
        boolean tv(){return show!=null||(title!=null&&title.kind==Kind.EPISODE);}
        String lookupKey(){return show!=null?"s:"+show.key:title.kind==Kind.EPISODE?"s:"+title.showKey:"t:"+title.stableId;}
        String lookupTitle(){return show!=null?show.name:title.kind==Kind.EPISODE?title.showName:title.name;}
        String lookupYear(){return show!=null?show.year:title.kind==Kind.EPISODE?title.showYear:title.year;}
        /** The server's own picture for this tile, when it sent one. */
        String serverArt(){return title!=null?title.art:show.art;}
    }
    static final class Row {final String id,label;final List<Card> all;Row(String id,String label,List<Card> all){this.id=id;this.label=label;this.all=all;}
        List<Card> shown(){return all.size()>ROW_MAX?all.subList(0,ROW_MAX):all;}}
    static final class Catalogue {
        final List<Title> titles=new ArrayList<>();final Map<String,Show> shows=new LinkedHashMap<>();final List<Row> rows=new ArrayList<>();
        Row row(String id){for(Row r:rows)if(r.id.equals(id))return r;return null;}
        Card find(String key){for(Title t:titles)if(("t:"+t.stableId).equals(key))return new Card(t);for(Show s:shows.values())if(("s:"+s.key).equals(key))return new Card(s);return null;}
        boolean isEmpty(){return titles.isEmpty();}
    }

    private static final java.util.regex.Pattern TV_ROOT=java.util.regex.Pattern.compile("(?i)\\b(tv|television|series|shows?|programmes?|programs?|box ?sets?|episodes?)\\b");
    static boolean tvRoot(String name){return name!=null&&TV_ROOT.matcher(name).find();}

    /** Builds the catalogue for whoever is watching, with no favourites. */
    static Catalogue build(LibraryIndex.Index index,LibraryFolders.Library lib,KidsPolicy.Scheme scheme,ResumeStore store,long now){
        return build(index,lib,scheme,store,Collections.emptyList(),now);
    }
    /** Builds the catalogue for whoever is watching; {@code favourites} are that profile's (Favourites), newest first. */
    static Catalogue build(LibraryIndex.Index index,LibraryFolders.Library lib,KidsPolicy.Scheme scheme,ResumeStore store,List<String> favourites,long now){
        Catalogue cat=new Catalogue();if(index==null||lib==null)return cat;
        URI origin=LibraryIndex.origin(lib.source);String udn=lib.udn();
        Map<String,String> rootNames=new HashMap<>();for(LibraryFolders.Folder f:lib.folders)rootNames.put(f.id,f.name);
        // One title per file: the same stream (or the same server id) reached through two chosen folders shows once,
        // and counts as Kids when either folder is for Kids. The deeper path wins: it says more about series and seasons.
        Map<String,LibraryIndex.Entry> byStream=new LinkedHashMap<>();Map<String,Boolean> kidsBy=new HashMap<>();Map<String,Long> newBy=new HashMap<>();Map<String,String> idToStream=new HashMap<>();
        for(LibraryFolders.Folder f:lib.folders){
            LibraryIndex.Root root=index.roots.get(f.id);if(root==null)continue;boolean kidsRoot=lib.kidsIds.contains(f.id);
            for(LibraryIndex.Entry e:root.entries){
                String k=idToStream.containsKey(e.id)?idToStream.get(e.id):e.url;
                LibraryIndex.Entry had=byStream.get(k);
                if(had==null||e.pathIds.size()>had.pathIds.size())byStream.put(k,e);
                idToStream.put(e.id,k);
                kidsBy.merge(k,kidsRoot,(a,b)->a||b);
                newBy.merge(k,root.created>0&&e.seen>root.created+RECENT_GRACE_MS?e.seen:0L,Math::max);
            }
        }
        // Folder by folder: samples and extras out; a folder that comes down to one main video is that film.
        Map<String,List<LibraryIndex.Entry>> byFolder=new LinkedHashMap<>();
        for(LibraryIndex.Entry e:byStream.values())byFolder.computeIfAbsent(e.root+"\n"+(e.pathIds.isEmpty()?"":e.pathIds.get(e.pathIds.size()-1)),k->new ArrayList<>()).add(e);
        Set<LibraryIndex.Entry> kept=Collections.newSetFromMap(new IdentityHashMap<>()),sole=Collections.newSetFromMap(new IdentityHashMap<>());
        for(List<LibraryIndex.Entry> group:byFolder.values()){LibraryIndex.Entry first=group.get(0);boolean tv=tvRoot(rootNames.get(first.root));
            if(tv)for(String n:first.pathNames)if(MediaName.filmsFolder(n)){tv=false;break;}
            List<LibraryIndex.Entry> mains=mains(group,tv);kept.addAll(mains);if(mains.size()==1&&!mains.get(0).pathIds.isEmpty())sole.add(mains.get(0));}
        for(Map.Entry<String,LibraryIndex.Entry> me:byStream.entrySet()){
            LibraryIndex.Entry e=me.getValue();boolean kids=kidsBy.getOrDefault(me.getKey(),false);
            if(!kept.contains(e))continue;
            Title t=classify(e,rootNames.get(e.root),origin,udn,sole.contains(e));t.kids=kids;t.seen=newBy.getOrDefault(me.getKey(),0L);
            if(lib.kids){
                // Kids: inside a Kids folder, and no rating Kids may not watch. The player checks again with the server.
                if(!kids||!KidsPolicy.allows(t.item(udn),lib.kidsIds,scheme))continue;
            }
            if(t.url.isEmpty())continue;
            cat.titles.add(t);
        }
        // Series.
        for(Title t:cat.titles){if(t.kind!=Kind.EPISODE)continue;Show s=cat.shows.get(t.showKey);
            if(s==null){s=new Show(t.showKey,t.showName,t.showYear);cat.shows.put(t.showKey,s);}
            s.episodes.add(t);t.show=s;s.kids&=t.kids;s.seen=Math.max(s.seen,t.seen);if(s.art.isEmpty())s.art=t.art;}
        for(Show s:cat.shows.values())s.episodes.sort(EPISODE_ORDER);
        cat.rows.addAll(rows(cat,lib.kids,store,favourites,now));
        return cat;
    }
    static final Comparator<Title> EPISODE_ORDER=(a,b)->{
        int c=Integer.compare(a.season<0?Integer.MAX_VALUE:a.season,b.season<0?Integer.MAX_VALUE:b.season);if(c!=0)return c;
        c=Integer.compare(a.episode<0?Integer.MAX_VALUE:a.episode,b.episode<0?Integer.MAX_VALUE:b.episode);if(c!=0)return c;
        return a.label().compareToIgnoreCase(b.label());};
    static final Comparator<Card> A_TO_Z=(a,b)->{int c=MediaName.normal(a.name()).compareTo(MediaName.normal(b.name()));return c!=0?c:a.year().compareTo(b.year());};
    /** Newest year first; titles with no year after, A to Z. */
    static final Comparator<Card> NEWEST=(a,b)->{int c=b.year().compareTo(a.year());return c!=0?c:A_TO_Z.compare(a,b);};

    static List<Row> rows(Catalogue cat,boolean kids,ResumeStore store,long now){return rows(cat,kids,store,Collections.emptyList(),now);}
    static List<Row> rows(Catalogue cat,boolean kids,ResumeStore store,List<String> favourites,long now){
        List<Row> out=new ArrayList<>();
        List<Card> cont=continueWatching(cat.titles,store,now);if(!cont.isEmpty())out.add(new Row("continue","Continue Watching",cont));
        List<Card> fav=favourites(cat,favourites);if(!fav.isEmpty())out.add(new Row("favourites","Favourites",fav));
        List<Card> recent=recent(cat);if(!recent.isEmpty())out.add(new Row("recent","Recently added",recent));
        List<Card> films=new ArrayList<>(),tv=new ArrayList<>(),kidsRow=new ArrayList<>();
        for(Title t:cat.titles)if(t.kind==Kind.FILM)(t.kids&&!kids?kidsRow:films).add(new Card(t));
        for(Show s:cat.shows.values())(s.kids&&!kids?kidsRow:tv).add(new Card(s));
        films.sort(NEWEST);tv.sort(A_TO_Z);kidsRow.sort(A_TO_Z);
        if(!films.isEmpty())out.add(new Row("films","Films",films));
        if(!tv.isEmpty())out.add(new Row("tv","TV",tv));
        if(!kidsRow.isEmpty())out.add(new Row("kids","Kids",kidsRow));
        return out;
    }
    /** This profile's places on this TV, most recent first. Keyed by the title's server id; earlier places by its stream. */
    static List<Card> continueWatching(List<Title> titles,ResumeStore store,long now){
        List<Card> out=new ArrayList<>();if(store==null)return out;
        for(Title t:titles){ResumeStore.Place p=store.place(ResumeStore.key(t.stableId),now);if(p==null)p=store.place(ResumeStore.key(t.url),now);
            if(p==null)continue;Card c=new Card(t);c.resume=p.position;c.duration=p.duration;c.at=p.at;out.add(c);}
        out.sort((a,b)->Long.compare(b.at,a.at));
        return out.size()>CONTINUE_MAX?new ArrayList<>(out.subList(0,CONTINUE_MAX)):out;
    }
    /** SET-032: the profile's favourites found in this catalogue, in the saved order (newest first); others are left out. */
    static List<Card> favourites(Catalogue cat,List<String> keys){
        List<Card> out=new ArrayList<>();if(keys==null||keys.isEmpty())return out;
        Set<String> wanted=new HashSet<>(keys);Map<String,Card> found=new HashMap<>();
        for(Title t:cat.titles){String k="t:"+t.stableId;if(wanted.contains(k))found.put(k,new Card(t));}
        for(Show s:cat.shows.values()){String k="s:"+s.key;if(wanted.contains(k))found.put(k,new Card(s));}
        for(String k:keys){Card c=found.get(k);if(c!=null)out.add(c);}
        return out;
    }
    /** Titles that arrived after the list was first made, newest first; a programme's new episodes share its tile. */
    static List<Card> recent(Catalogue cat){
        List<Title> fresh=new ArrayList<>();for(Title t:cat.titles)if(t.seen>0)fresh.add(t);
        fresh.sort((a,b)->Long.compare(b.seen,a.seen));
        List<Card> out=new ArrayList<>();Set<String> shows=new HashSet<>();
        for(Title t:fresh){if(out.size()>=RECENT_MAX)break;if(t.kind==Kind.EPISODE){if(shows.add(t.showKey))out.add(new Card(t.show));}else out.add(new Card(t));}
        return out;
    }

    /**
     * The main videos of one folder (all its entries share one path): never a sample or an extra by name ("sample.mkv",
     * "Title-sample.mkv", "Title - Trailer.mkv", anything in a "Sample" or "Extras" folder), nor one far smaller than the
     * folder's largest (under a tenth and under 300 MB), nor one under five minutes beside a main video over forty. Sizes
     * and lengths are the server's (DLNA res@size, res@duration); without them only the names count.
     *
     * Size and length count only where one clear main video stands out (review, 27 October 2026): never for loose files in
     * a chosen folder, a season folder, a folder in a TV folder ({@code tv}) or an episode; every size or length the server
     * sent must agree; and at most {@link #SAMPLES_MAX} files go. A short film, a short episode or an SD copy beside an HD
     * one stays.
     */
    static List<LibraryIndex.Entry> mains(List<LibraryIndex.Entry> folder){return mains(folder,false);}
    static List<LibraryIndex.Entry> mains(List<LibraryIndex.Entry> folder,boolean tv){
        List<LibraryIndex.Entry> out=new ArrayList<>();if(folder==null||folder.isEmpty())return out;
        // One path for the whole folder: an extras folder in it (a "Sample" one always; "Extras" unless seasons sit below it,
        // as in a programme called Extras).
        List<String> path=folder.get(0).pathNames;boolean sampleFolder=false,extrasFolder=false;
        for(int i=0;i<path.size();i++){String n=path.get(i);if(!MediaName.extrasFolder(n))continue;if(MediaName.sample(n))sampleFolder=true;else if(!seasonBelow(path,i))extrasFolder=true;}
        if(sampleFolder)return out;
        List<LibraryIndex.Entry> named=new ArrayList<>();long biggest=0,longest=0;
        for(LibraryIndex.Entry e:folder){
            if(MediaName.sample(e.title)||MediaName.extra(e.title))continue;
            if(extrasFolder&&!MediaName.parse(e.title).episodic())continue;
            named.add(e);biggest=Math.max(biggest,e.size);longest=Math.max(longest,e.duration);
        }
        if(tv||path.isEmpty()||MediaName.seasonFolder(path.get(path.size()-1))>=0)return named;
        List<LibraryIndex.Entry> small=new ArrayList<>();
        for(LibraryIndex.Entry e:named){if(measuredSample(e,biggest,longest)&&!MediaName.parse(e.title).episodic())small.add(e);else out.add(e);}
        return out.size()==1&&!small.isEmpty()&&small.size()<=SAMPLES_MAX?out:named;
    }
    /** Far smaller and far shorter than the folder's main video, by every measure the server sent (at least one). */
    private static boolean measuredSample(LibraryIndex.Entry e,long biggest,long longest){
        boolean size=e.size>0&&biggest>0,length=e.duration>0&&longest>0;if(!size&&!length)return false;
        if(size&&!(e.size*SAMPLE_SHARE<biggest&&e.size<SAMPLE_MAX_BYTES))return false;
        return !length||(e.duration<SHORT_MS&&longest>FEATURE_MS);
    }
    /** A numbered clip rather than a title: "01 - Part one", "2. Second half", "Part 1", "Disc 2". */
    private static final java.util.regex.Pattern NUMBERED=java.util.regex.Pattern.compile("(?i)^(?:\\d{1,3}\\s*[-.:)]\\s*\\S|(?:episode|ep|part|pt|disc|cd)\\s*\\d)");
    private static boolean seasonBelow(List<String> path,int at){for(int j=at+1;j<path.size();j++)if(MediaName.seasonFolder(path.get(j))>=0)return true;return false;}
    /**
     * For a folder holding one film: whether the folder's name is the better title. Yes when the file's is generic
     * ("movie.mkv"), the same title, a scene name that no longer reads as one ("grp-ttl1080p"), a numbered clip ("01 -
     * Part one"), or the folder's title followed only by words about the copy ("Title 3D"); and when the folder gives a year the file agrees
     * with or lacks. Never a folder named for films as a kind ("Films") or a season.
     */
    static boolean folderSaysMore(MediaName.Parsed file,String fileRaw,MediaName.Parsed folder,String folderRaw){
        if(folder==null||MediaName.normal(folder.title).isEmpty()||MediaName.generic(folder.title)||MediaName.filmsFolder(folderRaw)||MediaName.seasonFolder(folderRaw)>=0)return false;
        String f=MediaName.normal(file.title),d=MediaName.normal(folder.title);
        if(MediaName.generic(file.title)||f.isEmpty()||f.equals(d))return true;
        if(!folder.year.isEmpty()&&(file.year.isEmpty()||Math.abs(Integer.parseInt(file.year)-Integer.parseInt(folder.year))<=1))return true;
        String stem=fileRaw==null?"":fileRaw.trim();
        if(!stem.contains(" ")&&stem.equals(stem.toLowerCase(Locale.ROOT))&&stem.contains("-"))return true;
        if(NUMBERED.matcher(stem).find()&&!f.startsWith(d))return true;
        // "Title 3D" in "Title": only words that describe the copy. "Harry Potter and the Philosopher's Stone" in "Harry Potter" keeps its own.
        return f.startsWith(d+" ")&&MediaName.copyWordsOnly(f.substring(d.length()+1));
    }
    /** One saved file as a film or an episode, with a clean name (MediaName). */
    static Title classify(LibraryIndex.Entry e,String rootName,URI origin,String udn){return classify(e,rootName,origin,udn,false);}
    /** {@code sole}: the only main video in its folder, so a film there may take its folder's name (folderSaysMore). */
    static Title classify(LibraryIndex.Entry e,String rootName,URI origin,String udn,boolean sole){
        Title t=new Title();t.id=e.id;t.root=e.root;t.stableId="dlna:"+(udn==null?"":udn)+":"+e.id;t.url=LibraryIndex.absolute(e.url,origin);t.art=LibraryIndex.absolute(e.art,origin);
        t.upnpClass=e.upnpClass;t.ratings.addAll(e.ratings);t.depth=e.pathIds.size();
        MediaName.Parsed p=MediaName.parse(e.title);
        // The nearest folder above that is not a season folder: the programme's (or a film's own) folder.
        int seasonAt=-1,showAt=-1;
        for(int i=e.pathNames.size()-1;i>=0;i--){int s=MediaName.seasonFolder(e.pathNames.get(i));if(s>=0){if(seasonAt<0)seasonAt=i;continue;}showAt=i;break;}
        MediaName.Parsed folder=showAt>=0?MediaName.parse(e.pathNames.get(showAt)):null;
        int folderSeason=seasonAt>=0?MediaName.seasonFolder(e.pathNames.get(seasonAt)):-1;
        // A folder named for TV holds programmes, except under a "Films" folder inside it.
        boolean tvRoot=tvRoot(rootName);if(tvRoot)for(String n:e.pathNames)if(MediaName.filmsFolder(n)){tvRoot=false;break;}
        if(p.episodic()){
            t.kind=Kind.EPISODE;t.season=p.season;t.episode=p.episode;t.episodeName=p.episodeTitle;
            boolean useFolder=folder!=null&&(MediaName.generic(p.title)||MediaName.normal(p.title).equals(MediaName.normal(folder.title)));
            t.showName=useFolder?folder.title:p.title;t.showYear=useFolder&&!folder.year.isEmpty()?folder.year:p.year;
            if(t.showName.isEmpty()&&folder!=null){t.showName=folder.title;t.showYear=folder.year;}
        }else if(folder!=null&&(seasonAt>=0||(tvRoot&&showAt>=0))){
            // A file in a "Season 1" folder, or in a programme's folder inside a TV folder.
            t.kind=Kind.EPISODE;MediaName.Parsed top=tvRoot&&seasonAt<0?MediaName.parse(e.pathNames.get(0)):folder;
            t.showName=top.title;t.showYear=top.year;t.season=folderSeason;t.episode=MediaName.episodeNumber(e.title);
            String rest=p.title.replaceFirst("(?i)^(?:episode|ep|e)?\\s*\\d{1,3}\\s*[-.:]?\\s*","").trim();
            t.episodeName=MediaName.normal(rest).equals(MediaName.normal(t.showName))?"":rest;
        }else{
            t.kind=Kind.FILM;t.name=p.title;t.year=p.year;
            // "Title (2019)/Title.mkv": the folder knows the year; "Title (2019)/movie.mkv": the folder knows the title.
            if(sole&&folder!=null&&folderSaysMore(p,e.title,folder,e.pathNames.get(showAt))){t.name=folder.title;if(!folder.year.isEmpty())t.year=folder.year;}
            else if(folder!=null&&(MediaName.generic(p.title)||(p.year.isEmpty()&&MediaName.normal(p.title).equals(MediaName.normal(folder.title))))){
                if(MediaName.generic(p.title))t.name=folder.title;if(t.year.isEmpty())t.year=folder.year;}
        }
        if(t.kind==Kind.EPISODE){
            if(t.showName.isEmpty())t.showName=rootName==null||rootName.isEmpty()?"Programme":rootName;
            t.name=t.showName;t.showKey=MediaName.normal(t.showName)+(t.showYear.isEmpty()?"":"|"+t.showYear);
        }
        if(t.name.isEmpty())t.name=DlnaClient.title(e.title);
        return t;
    }

    // --- search, on this TV, in the saved list only
    /** Films, programmes and episodes whose names match every word typed (word starts), best matches first. */
    static List<Card> search(Catalogue cat,String query){
        String q=MediaName.normal(query);List<Card> out=new ArrayList<>();if(q.isEmpty()||cat==null)return out;
        String[] words=q.split(" ");List<Object[]> hits=new ArrayList<>();
        for(Title t:cat.titles)if(t.kind==Kind.FILM){int s=score(MediaName.normal(t.name),q,words);if(s>=0)hits.add(new Object[]{s,new Card(t)});}
        for(Show s:cat.shows.values()){int sc=score(MediaName.normal(s.name),q,words);if(sc>=0)hits.add(new Object[]{sc,new Card(s)});
            else for(Title e:s.episodes){int es=score(MediaName.normal(e.episodeName),q,words);if(es>=0)hits.add(new Object[]{es+4,new Card(e)});}}
        hits.sort((a,b)->{int c=Integer.compare((Integer)a[0],(Integer)b[0]);return c!=0?c:A_TO_Z.compare((Card)a[1],(Card)b[1]);});
        for(Object[] h:hits){out.add((Card)h[1]);if(out.size()>=200)break;}
        return out;
    }
    /** 0 the whole name, 1 the name starts with it, 2 every word starts a word, 3 found inside; -1 no match. */
    static int score(String name,String q,String[] words){
        if(name.isEmpty())return -1;if(name.equals(q))return 0;if(name.startsWith(q))return 1;
        String[] have=name.split(" ");boolean all=true;
        for(String w:words){boolean found=false;for(String h:have)if(h.startsWith(w)){found=true;break;}if(!found){all=false;break;}}
        if(all)return 2;
        return q.length()>=3&&name.contains(q)?3:-1;
    }
}
