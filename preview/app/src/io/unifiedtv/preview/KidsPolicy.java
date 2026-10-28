package io.unifiedtv.preview;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * What Kids may see and open (owner, 25 October 2026). Pure: the screens and the player ask, nothing here touches the
 * network or the TV.
 *
 * NAS: an adult, behind the PIN, chooses Kids folders on the media server (Vault "dlna-kids", kept apart from the
 * household library "dlna"). In Kids, browsing, the Home shelf, local resume and playback stay inside those folders, and
 * the player checks again at play time with the server itself (DlnaScope cannot prove a folder's parents; the server's
 * own parentID chain can). Hiding a poster is never the only wall.
 *
 * Ratings, where the server sends one (DIDL-Lite upnp:rating, the UPnP ContentDirectory field; Jellyfin-compatible
 * servers send OfficialRating the same way), are read against the household's rating scheme (a setting, SET-074: the
 * BBFC by default on a UK TV, otherwise the US film and TV ratings; an earlier install keeps the UK and US table it had):
 *
 *   UK (BBFC)         allowed: U, Uc, PG
 *   US (MPA and TV)   allowed: G, PG, TV-Y, TV-Y7 (and TV-Y7-FV), TV-G, TV-PG
 *   UK and US         allowed: both of the above
 *   refused           everything else that is a rating: 12, 12A, 15, 18, R18, PG-13, R, NC-17, TV-14, TV-MA, NR, Unrated,
 *                     any number (star scores look like age ratings and are refused too) and anything not in the
 *                     scheme's table (so a US TV-G is refused under the BBFC scheme: unknown is never allowed).
 *
 * A country or authority prefix ("GB-", "UK:", "US-", "BBFC ", "MPAA ", "Rated ") is ignored. An item with no rating at
 * all is allowed, and only ever inside a Kids folder. Any refused rating on an item (or, at play time, on a folder above
 * it) refuses it.
 *
 * Apps: in Kids, only apps an adult approved open from anywhere in Unified TV (Launch is the one gate). Adults: no limit.
 */
final class KidsPolicy {
    static final String STORE="dlna-kids",APPS_PREF="kidsApps";
    static final String ASK_ADULT="Ask an adult to choose Kids folders.";
    private KidsPolicy(){}

    enum Rating {ALLOWED,REFUSED,NONE}
    /** The rating schemes as data: each one's allowed ratings, written as normalised below. */
    enum Scheme {
        UK_BBFC("uk","UK (BBFC)","U","UC","PG"),
        US_MPA_TV("us","US (film and TV)","G","PG","TV-Y","TV-Y7","TV-Y7-FV","TV-G","TV-PG"),
        UK_AND_US("uk_us","UK and US","U","UC","PG","G","TV-Y","TV-Y7","TV-Y7-FV","TV-G","TV-PG");
        final String id,label;final Set<String> allowed;
        Scheme(String id,String label,String... allowed){this.id=id;this.label=label;this.allowed=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(allowed)));}
        /** A saved id; anything unknown is the strictest scheme, never a looser one. */
        static Scheme of(String id){for(Scheme s:values())if(s.id.equals(id))return s;return UK_BBFC;}
        /** In words for Kids settings: "U, Uc and PG". */
        String allowedWords(){List<String> w=new ArrayList<>();for(String r:allowed)w.add(r.equals("UC")?"Uc":r);Collections.sort(w);
            return w.size()<2?String.join("",w):String.join(", ",w.subList(0,w.size()-1))+" and "+w.get(w.size()-1);}
    }
    /** One rating as a source wrote it, against the UK and US table (earlier installs). */
    static Rating rating(String raw){return rating(raw,Scheme.UK_AND_US);}
    /** One rating as a source wrote it, against the household's scheme. */
    static Rating rating(String raw,Scheme scheme){
        if(raw==null)return Rating.NONE;
        String r=raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\u2010-\\u2015]","-").replaceAll("\\s+"," ");
        if(r.isEmpty())return Rating.NONE;
        for(String before="";!before.equals(r);){before=r;r=r.replaceFirst("^(?:(?:GB|UK|US|USA|BBFC|MPAA)\\s*[-:/ ]\\s*|RATED\\s+)","").trim();}
        if(r.startsWith("TV"))r=r.replaceFirst("^TV[\\s-]*","TV-").replaceFirst("^TV-Y7[\\s-]*FV$","TV-Y7-FV");
        return (scheme==null?Scheme.UK_BBFC:scheme).allowed.contains(r)?Rating.ALLOWED:Rating.REFUSED;
    }
    /** Several ratings on one item: any refused one refuses it. */
    static Rating rating(Collection<String> all){return rating(all,Scheme.UK_AND_US);}
    static Rating rating(Collection<String> all,Scheme scheme){
        Rating out=Rating.NONE;if(all==null)return out;
        for(String r:all){Rating one=rating(r,scheme);if(one==Rating.REFUSED)return Rating.REFUSED;if(one==Rating.ALLOWED)out=Rating.ALLOWED;}
        return out;
    }

    /** The Kids folders' object ids in a saved record. */
    static Set<String> roots(JSONObject record){
        Set<String> out=new LinkedHashSet<>();JSONArray a=record==null?null:record.optJSONArray("roots");
        if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&!r.optString("id").isEmpty())out.add(r.optString("id"));}
        return out;
    }
    /** No Kids folders means Kids sees "Ask an adult", never the household library. */
    static boolean ready(JSONObject record){return record!=null&&record.optBoolean("enabled")&&!record.optString("control").isEmpty()&&!roots(record).isEmpty();}
    /** Browsing and the Home shelf: inside a Kids folder, and no refused rating. */
    static boolean allows(CatalogueItem item,Set<String> kidsRoots){return allows(item,kidsRoots,Scheme.UK_AND_US);}
    static boolean allows(CatalogueItem item,Set<String> kidsRoots,Scheme scheme){
        if(item==null||kidsRoots==null||!kidsRoots.contains(item.root))return false;
        return rating(item.ratings,scheme)!=Rating.REFUSED;
    }

    interface Lookup{CatalogueItem get(String id)throws Exception;}
    /**
     * Play time: walks the server's own parent chain from the title up to a Kids folder. Refused when the chain ends,
     * loops or runs too deep before reaching one, or when the title or any folder on the way carries a refused rating.
     */
    static boolean playable(String itemId,Set<String> kidsRoots,Lookup lookup)throws Exception{return playable(itemId,kidsRoots,lookup,Scheme.UK_AND_US);}
    static boolean playable(String itemId,Set<String> kidsRoots,Lookup lookup,Scheme scheme)throws Exception{
        if(itemId==null||itemId.isEmpty()||kidsRoots==null||kidsRoots.isEmpty())return false;
        Set<String> seen=new HashSet<>();String id=itemId;
        for(int depth=0;depth<=DlnaScope.MAX_DEPTH;depth++){
            if(depth>0&&kidsRoots.contains(id))return true;
            if(!seen.add(id))return false;
            CatalogueItem here=lookup.get(id);if(here==null||rating(here.ratings,scheme)==Rating.REFUSED)return false;
            String parent=here.folder;if(parent.isEmpty()||"-1".equals(parent))return false;
            id=parent;
        }
        return false;
    }

    // --- apps
    /** The approved list: package names, plus Unified TV's own HDMI shortcuts ("xbox", "tvplus"). Junk never survives. */
    static Set<String> apps(String saved){
        Set<String> out=new LinkedHashSet<>();if(saved==null)return out;
        for(String raw:saved.split(",")){String id=raw.trim();if(id.equals("xbox")||id.equals("tvplus")||id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))out.add(id);}
        return out;
    }
    static String toggle(String saved,String id){Set<String> a=apps(saved);if(!a.remove(id))a.add(id);return String.join(",",apps(String.join(",",a)));}
    /** Adults: anything. Kids: Unified TV itself and approved apps only. */
    static boolean appAllowed(boolean kids,Set<String> approved,String id,String self){
        if(!kids)return true;
        if(id==null||id.isEmpty())return false;
        return id.equals(self)||(approved!=null&&approved.contains(id));
    }
}
