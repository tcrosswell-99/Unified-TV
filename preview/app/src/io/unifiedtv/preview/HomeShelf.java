package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import java.util.*;

/** The poster row that rises from Home on Down (spec 4.6): a few titles from the household's own collections, never a guess. */
final class HomeShelf {
    static final int MAX=18,ROOTS=3;
    static final class Poster {
        final String id,title,year,art,url,root;final boolean folder;
        Poster(String id,String title,String year,String art,String url,boolean folder,String root){this.id=id;this.title=title;this.year=year;this.art=art;this.url=url;this.folder=folder;this.root=root;}
        String label(){return year.isEmpty()?title:title+" ("+year+")";}
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
    /** The saved DLNA collections only; nothing is shown from folders the household didn't choose. */
    static List<Poster> load(Context c)throws Exception{
        JSONObject source=new Vault(c).read("dlna");JSONArray roots=source.optJSONArray("roots");if(!source.optBoolean("enabled")||roots==null)return new ArrayList<>();
        DlnaClient.lan=LocalDiscovery.lan(c);List<List<DlnaClient.Item>> pages=new ArrayList<>();List<String> ids=new ArrayList<>();
        for(int i=0;i<Math.min(ROOTS,roots.length());i++){JSONObject root=roots.optJSONObject(i);if(root==null)continue;try{pages.add(DlnaClient.browse(source,root.optString("id"),0).items);ids.add(root.optString("id"));}catch(Exception ignored){}}
        return pick(pages,ids,MAX);
    }
    static boolean configured(Context c){
        try{if(new Vault(c).read("dlna").optBoolean("enabled"))return true;return new Vault(c).read(Vault.mediaKey(c)).optBoolean("enabled");}catch(Exception e){return false;}
    }
}
