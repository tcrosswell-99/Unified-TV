package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import java.util.*;

/**
 * The poster row that rises from Home on Down (spec 4.6), from the same saved list as the library (LibraryIndex), so it
 * appears at once and matches what the library shows: this profile's Continue Watching first (labelled "on this TV"),
 * then titles from the library's own rows in turn (Recently added, Films, TV, Kids). Never a folder name, never a guess.
 * Per profile (owner, 25 October 2026): Kids see only titles in folders marked for Kids, within the Kids ratings, and
 * nothing at all until an adult has marked some.
 */
final class HomeShelf {
    static final int MAX=18,ROOTS=3;
    static final class Poster {
        final String id,title,year,art,url,root;final boolean folder;
        /** This profile's saved place on this TV, in milliseconds; 0 when there is none. */
        long resume;
        /** The library tile behind it (its poster, its programme), when it came from the saved list. */
        LibraryCatalogue.Card card;
        Poster(String id,String title,String year,String art,String url,boolean folder,String root){this.id=id;this.title=title;this.year=year;this.art=art;this.url=url;this.folder=folder;this.root=root;}
        String label(){return (year.isEmpty()?title:title+" ("+year+")")+(resume>0?" · resume on this TV":"");}
        static Poster of(LibraryCatalogue.Card c){
            LibraryCatalogue.Title t=c.title;
            Poster p=new Poster(c.key(),c.name(),c.year(),c.serverArt(),t==null?"":t.url,t==null,t==null?"":t.root);p.card=c;p.resume=c.resume;return p;
        }
    }
    /** Continue Watching first, then one title from each row in turn, no tile twice, at most {@code max}. */
    static List<Poster> fromCatalogue(LibraryCatalogue.Catalogue cat,int max){
        List<Poster> out=new ArrayList<>();Set<String> seen=new HashSet<>();if(cat==null)return out;
        List<List<LibraryCatalogue.Card>> rows=new ArrayList<>();
        for(LibraryCatalogue.Row r:cat.rows){if(r.id.equals("continue")){for(LibraryCatalogue.Card c:r.all)if(out.size()<max&&seen.add(c.key()))out.add(Poster.of(c));}else rows.add(r.all);}
        for(int i=0;out.size()<max;i++){boolean any=false;
            for(List<LibraryCatalogue.Card> r:rows){if(i>=r.size())continue;any=true;LibraryCatalogue.Card c=r.get(i);
                if(seen.add(c.key()))out.add(Poster.of(c));if(out.size()>=max)break;}
            if(!any)break;}
        return out;
    }
    /** Interleave each collection's first page so one large folder doesn't crowd out the others; titles only, no duplicates. */
    static List<Poster> pick(List<List<DlnaClient.Item>> pages,List<String> roots,int max){
        List<Poster> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int i=0;out.size()<max;i++){boolean any=false;
            for(int r=0;r<pages.size();r++){List<DlnaClient.Item> page=pages.get(r);if(i>=page.size())continue;any=true;DlnaClient.Item item=page.get(i);
                if(item.title==null||item.title.trim().isEmpty()||!seen.add(item.id))continue;out.add(new Poster(item.id,item.title,item.year,item.art,item.url,item.folder,r<roots.size()?roots.get(r):""));if(out.size()>=max)break;}
            if(!any)break;}
        return out;
    }
    /** Titles with a saved place first (most recent order kept), then the rest as picked. */
    static List<Poster> resumeFirst(List<Poster> posters,ResumeStore store,long now){
        List<Poster> started=new ArrayList<>(),rest=new ArrayList<>();
        for(Poster p:posters){p.resume=p.folder||p.url.isEmpty()?0:store.position(ResumeStore.key(p.url),now);(p.resume>0?started:rest).add(p);}
        started.addAll(rest);return started;
    }
    /** Kids: only what KidsPolicy allows inside the Kids folder it came from. */
    static List<DlnaClient.Item> kidsOnly(List<DlnaClient.Item> items,String udn,String root,Set<String> kidsRoots){return kidsOnly(items,udn,root,kidsRoots,KidsPolicy.Scheme.UK_AND_US);}
    static List<DlnaClient.Item> kidsOnly(List<DlnaClient.Item> items,String udn,String root,Set<String> kidsRoots,KidsPolicy.Scheme scheme){
        List<DlnaClient.Item> out=new ArrayList<>();for(DlnaClient.Item i:items)if(KidsPolicy.allows(CatalogueItem.fromDlna(i,udn,root),kidsRoots,scheme))out.add(i);return out;
    }
    /**
     * The row for this profile, from the newest list there is: the saved one, or the titles the background job
     * (LibraryCollector) has found so far while it is still making it (owner's TCL, build run 99: folders saved in first-run
     * step 4, then Down showed only "View all"). Never waits for the media server: when the list is missing or old, the
     * job is started and Home hears from it as titles arrive. Runs off the main thread.
     */
    static List<Poster> load(Context c)throws Exception{
        LibraryFolders.Library lib=LibraryFolders.library(c);if(!lib.ready)return new ArrayList<>();
        LibraryIndex.Index index=LibraryIndex.latest(c,lib);
        LibraryCollector job=LibraryCollector.get(c);
        if(!job.status().busy()&&(index.size()==0||LibraryIndex.due(LibraryIndex.cached(c,lib),lib,System.currentTimeMillis())))job.start();
        LibraryCatalogue.Catalogue cat=LibraryCatalogue.build(index,lib,HouseholdSettings.scheme(c),ResumeStore.load(c),System.currentTimeMillis());
        return fromCatalogue(cat,MAX);
    }
    /**
     * The one line above Home's row, or "" for none: the background job's progress while the list is made or posters are
     * looked up ("Scanning your film and TV library — 2,000 videos found so far. You can keep watching."); otherwise, for
     * Adults with titles but no TMDB key, why the tiles have no posters (Posters.ROW_HINT; OK on it opens that setting).
     * Kids see nothing extra. Pure: Home works out the inputs off the main thread, never per frame.
     */
    static String line(LibraryCollector.Status job,boolean kids,boolean titles,boolean postersOn){
        String progress=job==null?"":job.home();
        if(!progress.isEmpty())return progress;
        return !kids&&titles&&!postersOn?Posters.ROW_HINT:"";
    }
    /**
     * Home's one line when the row couldn't be loaded. A VPN in the way is named as the likely cause (owner's TCL,
     * 25 October 2026), never "aren't answering", which reads as the media server being off.
     */
    static String unanswered(){return unanswered(LocalDiscovery.vpnInTheWay(),LocalDiscovery.vpnApp);}
    static String unanswered(boolean vpn,String vpnApp){
        if(!vpn)return "Your films and TV aren't answering right now. OK opens the Library to reconnect.";
        return ("NordVPN".equals(vpnApp)?"NordVPN":"A VPN")+" is blocking your home network. OK opens the Library to see how to fix it.";
    }
    /** A library is set up for whoever is watching: Kids folders (or a Kids media account) for Kids, the household's for Adults. */
    static boolean configured(Context c){
        try{
            if(LibraryFolders.library(c).ready)return true;
            return LibraryFolders.read(c,Vault.mediaKey(c)).optBoolean("enabled");
        }catch(Exception e){return false;}
    }
}
