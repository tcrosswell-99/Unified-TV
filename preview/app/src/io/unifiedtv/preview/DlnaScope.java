package io.unifiedtv.preview;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * The saved viewer's walls (spec 27.3, P05-05): browsing starts inside an owner-selected collection and Back never climbs above it.
 * A DLNA object ID proves nothing about its parents, so a folder opened directly is accepted only for a saved root, or from Home's
 * poster row together with the saved root it was found in.
 */
final class DlnaScope {
    private DlnaScope(){}
    static final int MAX_DEPTH=32;

    static final class Frame {
        final String id,name;final int offset;
        Frame(String id,String name,int offset){this.id=id==null?"":id;this.name=name==null?"":name;this.offset=Math.max(0,offset);}
    }

    /** The saved root with this id, or null. */
    static JSONObject root(JSONArray roots,String id){
        if(roots==null||id==null||id.isEmpty())return null;
        for(int i=0;i<roots.length();i++){JSONObject r=roots.optJSONObject(i);if(r!=null&&id.equals(r.optString("id")))return r;}
        return null;
    }
    /** Where a direct "open this folder" request may start, or null to ignore it and show the saved collections. */
    static Frame start(JSONArray roots,String folder,String name,boolean fromShelf,String rootId){
        if(folder==null||folder.isEmpty()||folder.length()>1024)return null;
        JSONObject own=root(roots,folder);
        if(own!=null)return new Frame(folder,own.optString("name","Collection"),0);
        JSONObject parent=fromShelf?root(roots,rootId):null;
        if(parent==null)return null;
        String title=DlnaClient.title(name==null?"":name);if(title.length()>80)title=title.substring(0,80);
        return new Frame(folder,title.isEmpty()?parent.optString("name","Collection"):title,0);
    }

    /** The folders opened from a starting folder; the first frame is the floor. */
    static final class Trail {
        private final ArrayDeque<Frame> stack=new ArrayDeque<>();private String root="";
        /** Starts at a folder inside the saved root {@code rootId} (the folder itself when it is the root). */
        void start(Frame f,String rootId){stack.clear();stack.push(f);root=rootId==null?"":rootId;}
        String rootId(){return root;}
        void open(Frame child){if(stack.size()<MAX_DEPTH)stack.push(child);else{stack.pop();stack.push(child);}}
        /** The current folder moved to another page of its entries. */
        void page(int offset){Frame top=stack.pop();stack.push(new Frame(top.id,top.name,offset));}
        Frame current(){return stack.peek();}
        boolean isEmpty(){return stack.isEmpty();}
        int depth(){return stack.size();}
        /** Leaves the current folder: returns its parent, or null at the starting folder (show the saved collections, never the server root). */
        Frame back(){if(stack.isEmpty())return null;stack.pop();return stack.peek();}
        void clear(){stack.clear();root="";}
        /** For onSaveInstanceState: ids, names and page offsets only. */
        String save(){
            JSONArray out=new JSONArray();out.put(root);
            for(Iterator<Frame> it=stack.descendingIterator();it.hasNext();){Frame f=it.next();try{out.put(new JSONObject().put("id",f.id).put("name",f.name).put("offset",f.offset));}catch(Exception ignored){}}
            return out.toString();
        }
        /** Restores a saved trail, but only when its root is still one of the saved roots. */
        static Trail restore(String saved,JSONArray roots){
            Trail t=new Trail();
            try{JSONArray a=new JSONArray(saved);t.root=a.getString(0);
                for(int i=1;i<a.length()&&i<=MAX_DEPTH;i++){JSONObject f=a.getJSONObject(i);t.stack.push(new Frame(f.optString("id"),f.optString("name"),f.optInt("offset")));}
            }catch(Exception e){t.stack.clear();}
            if(t.stack.isEmpty()||DlnaScope.root(roots,t.root)==null)t.clear();
            return t;
        }
    }
}
