package io.unifiedtv.preview;
import org.json.JSONObject;
import java.util.*;

/**
 * One title or folder, whatever it came from (owner, 25 October 2026: universal search later). The NAS library, the Home
 * shelf and the Kids policy map their own records to this, so a search index can be added later over one shape without
 * rebuilding them. No search, no streaming services, nothing stored here: it is a value, made on demand.
 *
 * Continue Watching still keys local places by the stream address (ResumeStore); an index would key them by stableId.
 */
final class CatalogueItem {
    enum Kind {FILM,EPISODE,SERIES,VIDEO,FOLDER}
    /** Where it came from: "dlna" (the NAS's own media server) or "media-account" (a signed-in library server). */
    final String source;
    /** Unique within the household: source, server and the server's own id. Never an address or a file path. */
    final String stableId;
    final String id,title,year,folder,root,poster;
    final Kind kind;
    /** Content ratings exactly as the source gave them (for example "PG", "TV-Y7", "15"); empty when it gave none. */
    final List<String> ratings;

    CatalogueItem(String source,String server,String id,String title,String year,Kind kind,String folder,String root,List<String> ratings,String poster){
        this.source=source==null?"":source;this.id=id==null?"":id;
        this.stableId=this.source+":"+(server==null?"":server)+":"+this.id;
        this.title=title==null?"":title;this.year=year==null?"":year;this.kind=kind==null?Kind.VIDEO:kind;
        this.folder=folder==null?"":folder;this.root=root==null?"":root;this.poster=poster==null?"":poster;
        this.ratings=ratings==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(ratings));
    }
    /** A DLNA entry from a Browse, found inside the saved collection {@code root} on the server {@code udn}. */
    static CatalogueItem fromDlna(DlnaClient.Item i,String udn,String root){
        return new CatalogueItem("dlna",udn,i.id,i.title,i.year,dlnaKind(i.upnpClass,i.folder),i.parent,root,i.ratings,i.art);
    }
    /** UPnP's own class names; anything not plainly a film is a video, never guessed to be an episode. */
    static Kind dlnaKind(String upnpClass,boolean folder){
        if(folder)return Kind.FOLDER;
        String c=upnpClass==null?"":upnpClass.toLowerCase(Locale.ROOT);
        if(c.startsWith("object.item.videoitem.movie"))return Kind.FILM;
        return Kind.VIDEO;
    }
    /** A signed-in library server's item (Jellyfin-compatible fields): its own type, year, rating and parent. */
    static CatalogueItem fromMediaAccount(JSONObject item,String server){
        String type=item.optString("Type");
        Kind kind="Movie".equals(type)?Kind.FILM:"Episode".equals(type)?Kind.EPISODE:"Series".equals(type)?Kind.SERIES:"Season".equals(type)||"Folder".equals(type)||"BoxSet".equals(type)?Kind.FOLDER:Kind.VIDEO;
        String rating=item.optString("OfficialRating","");int year=item.optInt("ProductionYear",0);
        JSONObject tags=item.optJSONObject("ImageTags");
        return new CatalogueItem("media-account",server,item.optString("Id"),item.optString("Name"),year>0?String.valueOf(year):"",kind,
            item.optString("ParentId"),"",rating.isEmpty()?null:Collections.singletonList(rating),tags!=null&&tags.has("Primary")?"Primary":"");
    }
}
