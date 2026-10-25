package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import java.util.*;

/**
 * The poster row that rises from Home on Down (spec 4.6): a few titles from the household's own collections, never a guess.
 * Per profile (owner, 25 October 2026): Adults see the household library ("dlna"); Kids see only the Kids folders
 * ("dlna-kids") with the Kids rating table applied, and nothing at all until an adult has chosen Kids folders. Titles
 * with a place saved on this TV, in this profile's own store, come first and say so ("resume on this TV"): a local place,
 * never called Continue Watching from the server (spec 9).
 */
final class HomeShelf {
    static final int MAX=18,ROOTS=3;
    static final class Poster {
        final String id,title,year,art,url,root;final boolean folder;
        /** This profile's saved place on this TV, in milliseconds; 0 when there is none. */
        long resume;
        Poster(String id,String title,String year,String art,String url,boolean folder,String root){this.id=id;this.title=title;this.year=year;this.art=art;this.url=url;this.folder=folder;this.root=root;}
        String label(){return (year.isEmpty()?title:title+" ("+year+")")+(resume>0?" · resume on this TV":"");}
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
    /** The saved collections of this profile only; nothing is shown from folders the household didn't choose. */
    static List<Poster> load(Context c)throws Exception{
        boolean kids=Profiles.kids(c);
        JSONObject source=new Vault(c).read(kids?KidsPolicy.STORE:"dlna");JSONArray roots=source.optJSONArray("roots");if(!source.optBoolean("enabled")||roots==null)return new ArrayList<>();
        Set<String> kidsRoots=KidsPolicy.roots(source);KidsPolicy.Scheme scheme=HouseholdSettings.scheme(c);
        DlnaClient.lan=LocalDiscovery.lan(c);List<List<DlnaClient.Item>> pages=new ArrayList<>();List<String> ids=new ArrayList<>();
        for(int i=0;i<Math.min(ROOTS,roots.length());i++){JSONObject root=roots.optJSONObject(i);if(root==null)continue;String id=root.optString("id");
            try{List<DlnaClient.Item> items=DlnaClient.browse(source,id,0).items;pages.add(kids?kidsOnly(items,source.optString("udn"),id,kidsRoots,scheme):items);ids.add(id);}catch(Exception ignored){}}
        return resumeFirst(pick(pages,ids,MAX),ResumeStore.load(c),System.currentTimeMillis());
    }
    /** A library is set up for whoever is watching: Kids folders (or a Kids media account) for Kids, the household's for Adults. */
    static boolean configured(Context c){
        try{
            if(Profiles.kids(c))return KidsPolicy.ready(new Vault(c).read(KidsPolicy.STORE))||new Vault(c).read(Vault.mediaKey(c)).optBoolean("enabled");
            if(new Vault(c).read("dlna").optBoolean("enabled"))return true;return new Vault(c).read(Vault.mediaKey(c)).optBoolean("enabled");
        }catch(Exception e){return false;}
    }
}
