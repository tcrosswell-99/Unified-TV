package io.unifiedtv.preview;

import java.util.*;

/** The approved set is fixed; only its order is owner-editable. */
final class DockPolicy {
    static List<String> order(String room,String saved) {
        List<String> approved=new ArrayList<>(Arrays.asList("smartutube","netflix","disney","bbc","live","music"));
        if("sony".equals(room))approved.add("xbox");
        if("frame".equals(room))approved.add("tvplus");
        List<String> result=new ArrayList<>();
        for(String id:saved.split(","))if(approved.contains(id)&&!result.contains(id))result.add(id);
        for(String id:approved)if(!result.contains(id))result.add(id);
        return result;
    }
}
