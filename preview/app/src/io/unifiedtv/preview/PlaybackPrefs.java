package io.unifiedtv.preview;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.*;

/**
 * SET-033 playback preferences for Your Films & TV (the embedded Media3 player): what happens when a film was left
 * part-way (ask, always resume, start over), playing the next episode automatically (off by default), and subtitle size
 * (Media3's own subtitle view scales them; the file's subtitles must exist for there to be anything to size). Nothing
 * here applies to other apps. Next episode follows the library's own order (season, then episode) within one programme,
 * and the player checks Kids folders and ratings again for every title it opens.
 */
final class PlaybackPrefs {
    private PlaybackPrefs(){}
    static final String RESUME="resumePrompt",AUTOPLAY="autoplayNext",SUBTITLES="subtitleSize";
    static final String ASK="ask",ALWAYS="resume",START="start";
    static final String[] RESUMES={ASK,ALWAYS,START};
    /** Seconds the "Next episode" card waits before playing it (OK plays it now, Back returns to the library). */
    static final int NEXT_SECONDS=10;
    /** At most this many following episodes travel with a title, so a long series never makes a huge intent. */
    static final int QUEUE=20;

    static String resume(String saved){return ALWAYS.equals(saved)||START.equals(saved)?saved:ASK;}
    static String resumeLabel(Object saved){switch(resume(saved instanceof String?(String)saved:null)){case ALWAYS:return "Always resume";case START:return "Always start from the beginning";default:return "Ask each time";}}
    /** Where to start a NAS title with a saved place {@code here}: the place, 0, or -1 to ask. */
    static long start(String saved,long here){if(here<=0)return 0;switch(resume(saved)){case ALWAYS:return here;case START:return 0;default:return -1;}}
    /** Media3's subtitle size as a fraction of the picture's height (its default is 0.0533). */
    static float subtitleFraction(String saved){switch(Accessibility.size(saved)){case Accessibility.LARGE:return .07f;case Accessibility.LARGEST:return .09f;default:return .0533f;}}

    /** The episodes after {@code current} in the programme's order, at most QUEUE; empty for a film or the last episode. */
    static List<LibraryCatalogue.Title> following(List<LibraryCatalogue.Title> episodes,LibraryCatalogue.Title current){
        List<LibraryCatalogue.Title> out=new ArrayList<>();if(episodes==null||current==null)return out;
        int at=episodes.indexOf(current);if(at<0)return out;
        for(int i=at+1;i<episodes.size()&&out.size()<QUEUE;i++){LibraryCatalogue.Title t=episodes.get(i);if(t.url!=null&&!t.url.isEmpty())out.add(t);}
        return out;
    }
    static String title(LibraryCatalogue.Title t){return t.kind==LibraryCatalogue.Kind.EPISODE?t.showName+" "+t.label():t.name;}

    /** The player for a title, with the following episodes when it is one (each re-checked by the player before it plays). */
    static Intent player(Context c,LibraryCatalogue.Title t){
        Intent play=new Intent(c,PlayerActivity.class).putExtra("dlnaUrl",t.url).putExtra("title",title(t)).putExtra("dlnaItem",t.id).putExtra("root",t.root).putExtra("stableId",t.stableId);
        List<LibraryCatalogue.Title> next=t.kind==LibraryCatalogue.Kind.EPISODE&&t.show!=null?following(t.show.episodes,t):Collections.emptyList();
        if(!next.isEmpty()){int n=next.size();String[] url=new String[n],id=new String[n],root=new String[n],stable=new String[n],name=new String[n];
            for(int i=0;i<n;i++){LibraryCatalogue.Title x=next.get(i);url[i]=x.url;id[i]=x.id;root[i]=x.root;stable[i]=x.stableId;name[i]=title(x);}
            play.putExtra("nextUrl",url).putExtra("nextItem",id).putExtra("nextRoot",root).putExtra("nextStable",stable).putExtra("nextTitle",name);}
        return play;
    }
    /** The intent for the first queued episode, carrying the rest of the queue; null when there is none. */
    static Intent next(Context c,Intent from){
        String[] url=from.getStringArrayExtra("nextUrl"),id=from.getStringArrayExtra("nextItem"),root=from.getStringArrayExtra("nextRoot"),stable=from.getStringArrayExtra("nextStable"),name=from.getStringArrayExtra("nextTitle");
        if(url==null||id==null||root==null||stable==null||name==null||url.length==0)return null;
        int n=Math.min(Math.min(url.length,id.length),Math.min(Math.min(root.length,stable.length),name.length));if(n==0)return null;
        Intent play=new Intent(c,PlayerActivity.class).putExtra("dlnaUrl",url[0]).putExtra("title",name[0]).putExtra("dlnaItem",id[0]).putExtra("root",root[0]).putExtra("stableId",stable[0]);
        if(n>1)play.putExtra("nextUrl",Arrays.copyOfRange(url,1,n)).putExtra("nextItem",Arrays.copyOfRange(id,1,n)).putExtra("nextRoot",Arrays.copyOfRange(root,1,n))
            .putExtra("nextStable",Arrays.copyOfRange(stable,1,n)).putExtra("nextTitle",Arrays.copyOfRange(name,1,n));
        return play;
    }

    static String resume(SharedPreferences p){try{return resume(p.getString(RESUME,ASK));}catch(RuntimeException e){return ASK;}}
    static boolean autoplay(SharedPreferences p){try{return p.getBoolean(AUTOPLAY,false);}catch(RuntimeException e){return false;}}
    static float subtitleFraction(SharedPreferences p){try{return subtitleFraction(p.getString(SUBTITLES,Accessibility.NORMAL));}catch(RuntimeException e){return subtitleFraction((String)null);}}
}
