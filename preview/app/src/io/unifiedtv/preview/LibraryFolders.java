package io.unifiedtv.preview;
import android.content.Context;
import org.json.*;
import java.util.*;

/**
 * The folders that make up Your Films & TV, and which of them are for Kids (owner, 25 October 2026: "This folder is for
 * Kids" is part of choosing folders, not a separate hidden step; a folder can be removed again).
 *
 * Stored as before, encrypted in the Vault: "dlna" holds the server and every chosen folder (each with its server object
 * id, its name, the ids of the folders above it when it was chosen, and its Kids mark); "dlna-kids" holds the same server
 * with only the Kids folders. "dlna-kids" stays the one record Kids reads (KidsPolicy, the player's check, Kids settings),
 * so the Kids mark is true exactly when a folder is in it; saving here writes both. Removing a folder only takes it out
 * of the library: nothing on the media server is touched.
 *
 * Overlap: a folder and one inside it may both be chosen (for example "Video" and "Video/Kids" for Kids). Titles are
 * shown once (LibraryCatalogue removes repeats by item id and stream), and a title reachable through a Kids folder
 * counts as Kids.
 */
final class LibraryFolders {
    private LibraryFolders(){}
    static final String STORE="dlna";

    /** Reads and writes the encrypted records; tests replace it (Robolectric has no Android keystore). */
    interface Store{JSONObject read(String id)throws Exception;void save(String id,JSONObject value)throws Exception;void forget(String id);}
    static volatile Store testStore;
    static Store store(Context c){
        Store t=testStore;if(t!=null)return t;Vault v=new Vault(c);
        return new Store(){public JSONObject read(String id)throws Exception{return v.read(id);}public void save(String id,JSONObject value)throws Exception{v.save(id,value);}public void forget(String id){v.forget(id);}};
    }
    static JSONObject read(Context c,String id){try{return store(c).read(id);}catch(Exception e){return new JSONObject();}}

    /** One chosen folder. */
    static final class Folder {
        final String id,name;final List<String> path;boolean kids;
        Folder(String id,String name,List<String> path,boolean kids){this.id=id==null?"":id;this.name=name==null||name.trim().isEmpty()?"Folder":name.trim();this.path=path==null?new ArrayList<>():new ArrayList<>(path);this.kids=kids;}
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("id",id).put("name",name).put("kids",kids);JSONArray p=new JSONArray();for(String s:path)p.put(s);o.put("path",p);}catch(JSONException ignored){}return o;}
        static Folder of(JSONObject o,boolean kids){List<String> p=new ArrayList<>();JSONArray a=o.optJSONArray("path");if(a!=null)for(int i=0;i<a.length()&&i<DlnaScope.MAX_DEPTH;i++)p.add(a.optString(i));return new Folder(o.optString("id"),o.optString("name"),p,kids);}
    }

    /** What the library reads for whoever is watching. */
    static final class Library {
        JSONObject source=new JSONObject();final List<Folder> folders=new ArrayList<>();final Set<String> kidsIds=new LinkedHashSet<>();boolean kids,ready;
        String udn(){return source.optString("udn");}
        String name(){return source.optString("name","Media server");}
    }
    static boolean sameServer(JSONObject a,JSONObject b){
        String ua=a.optString("udn"),ub=b.optString("udn");if(!ua.isEmpty()||!ub.isEmpty())return ua.equals(ub);
        return !a.optString("control").isEmpty()&&a.optString("control").equals(b.optString("control"));
    }
    /**
     * Kids: only the Kids folders (nothing at all until an adult has marked some). Adults: every chosen folder, Kids ones
     * included, plus any Kids folder chosen earlier from Kids settings on the same server.
     */
    static Library library(JSONObject household,JSONObject kidsRecord,boolean kids){
        Library l=new Library();l.kids=kids;
        Set<String> kidsIds=KidsPolicy.roots(kidsRecord);
        if(kids){
            l.ready=KidsPolicy.ready(kidsRecord);if(!l.ready)return l;l.source=kidsRecord;l.kidsIds.addAll(kidsIds);
            JSONArray a=kidsRecord.optJSONArray("roots");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!o.optString("id").isEmpty())l.folders.add(Folder.of(o,true));}
            return l;
        }
        boolean own=household.optBoolean("enabled")&&household.optJSONArray("roots")!=null&&!household.optString("control").isEmpty();
        boolean kidsHere=KidsPolicy.ready(kidsRecord)&&(!own||sameServer(household,kidsRecord));
        if(!own&&!kidsHere)return l;
        l.source=own?household:kidsRecord;l.ready=true;
        if(kidsHere)l.kidsIds.addAll(kidsIds);
        Set<String> seen=new HashSet<>();
        if(own){JSONArray a=household.optJSONArray("roots");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||o.optString("id").isEmpty()||!seen.add(o.optString("id")))continue;l.folders.add(Folder.of(o,l.kidsIds.contains(o.optString("id"))));}}
        if(kidsHere){JSONArray a=kidsRecord.optJSONArray("roots");for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o==null||o.optString("id").isEmpty()||!seen.add(o.optString("id")))continue;l.folders.add(Folder.of(o,true));}}
        return l;
    }
    static Library library(Context c){return library(read(c,STORE),read(c,KidsPolicy.STORE),Profiles.kids(c));}

    // --- the setup list (pure)
    /** The folders an adult manages in setup, Kids marks included, for this server. */
    static List<Folder> chosen(JSONObject household,JSONObject kidsRecord){
        Library l=library(household,kidsRecord,false);return l.folders;
    }
    /** The result of a change: the new list and, when worth saying, one plain sentence. */
    static final class Change{final List<Folder> folders;final String message;Change(List<Folder> f,String m){folders=f;message=m;}}
    static Folder find(List<Folder> folders,String id){for(Folder f:folders)if(f.id.equals(id))return f;return null;}
    /** Adds a folder (path: the ids above it, from the server's top). Says so plainly when it overlaps a chosen one. */
    static Change add(List<Folder> folders,String id,String name,List<String> path,boolean kids){
        List<Folder> out=new ArrayList<>(folders);
        if(id==null||id.isEmpty()||"0".equals(id))return new Change(out,"Open a folder first, then add it.");
        if(find(out,id)!=null)return new Change(out,"“"+find(out,id).name+"” is already in your library.");
        Folder added=new Folder(id,name,path,kids);out.add(added);
        for(Folder f:folders)if(added.path.contains(f.id))
            return new Change(out,"Added. It's inside “"+f.name+"”, which is already in your library, so its titles show once"+(kids&&!f.kids?", and count as Kids.":"."));
        for(Folder f:folders)if(f.path.contains(id))
            return new Change(out,"Added. It contains “"+f.name+"”, which is already in your library; titles in both show once.");
        return new Change(out,kids?"Added for Kids.":"Added.");
    }
    /** Takes a folder out of the library. Nothing on the media server is deleted. */
    static Change remove(List<Folder> folders,String id){
        List<Folder> out=new ArrayList<>();String name="";for(Folder f:folders){if(f.id.equals(id)){name=f.name;continue;}out.add(f);}
        return new Change(out,name.isEmpty()?"":"“"+name+"” is no longer in your library. Nothing was deleted from the media server.");
    }
    static Change kids(List<Folder> folders,String id,boolean kids){
        List<Folder> out=new ArrayList<>();String name="";for(Folder f:folders){Folder copy=new Folder(f.id,f.name,f.path,f.kids);if(f.id.equals(id)){copy.kids=kids;name=f.name;}out.add(copy);}
        return new Change(out,name.isEmpty()?"":kids?"“"+name+"” is for Kids.":"“"+name+"” is no longer for Kids.");
    }
    /** The two records to save for a server and its chosen folders; the second is null when no folder is for Kids. */
    static JSONObject[] records(JSONObject server,List<Folder> folders)throws JSONException{
        JSONObject household=serverOnly(server),kidsRecord=serverOnly(server);JSONArray all=new JSONArray(),kids=new JSONArray();
        for(Folder f:folders){all.put(f.json());if(f.kids)kids.put(f.json());}
        household.put("roots",all);kidsRecord.put("roots",kids);
        return new JSONObject[]{household,kids.length()==0?null:kidsRecord};
    }
    /** Only what is needed to reach a server again. */
    static JSONObject serverOnly(JSONObject from){JSONObject s=new JSONObject();try{for(String k:new String[]{"name","location","control","service","udn"})s.put(k,from.optString(k));s.put("enabled",true);}catch(JSONException ignored){}return s;}
    /** Saves both records; no folders left means no library (and no Kids folders). */
    static void save(Context c,JSONObject server,List<Folder> folders)throws Exception{
        Store s=store(c);JSONObject before;try{before=s.read(KidsPolicy.STORE);}catch(Exception e){before=new JSONObject();}
        // Kids folders chosen earlier on another media server are not this list's to clear.
        boolean otherServer=KidsPolicy.ready(before)&&!sameServer(before,server);
        if(folders.isEmpty()){s.forget(STORE);if(!otherServer)s.forget(KidsPolicy.STORE);return;}
        JSONObject[] r=records(server,folders);s.save(STORE,r[0]);
        if(r[1]!=null)s.save(KidsPolicy.STORE,r[1]);else if(!otherServer)s.forget(KidsPolicy.STORE);
    }
}
