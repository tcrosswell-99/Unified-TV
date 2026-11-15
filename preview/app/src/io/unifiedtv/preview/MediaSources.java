package io.unifiedtv.preview;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/**
 * SET-031 media source management for Your Films & TV: a name for the media server on this TV only (the server itself is
 * never renamed), which source the library uses when a media server account is also set up, rescanning the chosen
 * folders, refreshing posters, forgetting the server, and the last problem in plain words. Discovery stays where it is
 * (LibrarySetupActivity: home network only, validated device descriptions); nothing here takes an address.
 *
 * Forgetting removes the chosen folders (Kids folders on the same server too), the saved list of titles and this TV's
 * name for it. Watch history stays (Privacy & Data clears it separately), and nothing on the media server is touched.
 */
final class MediaSources {
    private MediaSources(){}
    static final String PREFERRED="librarySource",NAME="library.sourceName",PROBLEM="library.problem",CHECKED="library.checked";
    static final String FOLDERS="dlna",ACCOUNT="account";
    static final int NAME_MAX=40;

    static boolean validName(String s){return s!=null&&s.length()<=NAME_MAX&&!s.matches("(?s).*[\\p{Cntrl}<>].*");}
    /** The local name, else the server's own, else plain "Media server". */
    static String name(String local,String server){
        if(local!=null&&!local.trim().isEmpty()&&validName(local.trim()))return local.trim();
        return server==null||server.trim().isEmpty()?"Media server":server.trim();
    }
    static String preferredLabel(Object saved){return ACCOUNT.equals(saved)?"A media server account":"Your chosen folders";}
    /** The last check in words, or null when there has been none or it worked. */
    static String problem(int failureKind){return failureKind<=0?null:DlnaClient.Failure.plain(failureKind);}
    static int kind(Exception e){return e instanceof DlnaClient.Failure?((DlnaClient.Failure)e).kind:DlnaClient.Failure.UNREACHABLE;}

    // --- Android
    static SharedPreferences prefs(Context c){return c.getSharedPreferences(HouseholdSettings.PREFS,0);}
    static LibraryFolders.Library library(Context c){return LibraryFolders.library(LibraryFolders.read(c,LibraryFolders.STORE),LibraryFolders.read(c,KidsPolicy.STORE),false);}
    static String name(Context c,LibraryFolders.Library lib){String local;try{local=prefs(c).getString(NAME,"");}catch(RuntimeException e){local="";}return name(local,lib.ready?lib.name():"");}
    /** Whether a media server account (the older Jellyfin-style sign-in) is also set up for Adults. */
    static boolean accountToo(Context c){try{return new Vault(c).read("media-adult").optBoolean("enabled");}catch(Exception e){return false;}}
    /** Walks the chosen folders again now (off the main thread); the number of titles, or throws with a Failure. */
    static int rescan(Context c,LibraryFolders.Library lib)throws Exception{
        try{int n=LibraryIndex.refresh(c,lib,true,null).size();prefs(c).edit().remove(PROBLEM).putLong(CHECKED,System.currentTimeMillis()).apply();EventLog.record(c,EventLog.CONNECTION,"Media server rescanned: "+n+" titles");return n;}
        catch(Exception e){prefs(c).edit().putInt(PROBLEM,kind(e)).putLong(CHECKED,System.currentTimeMillis()).apply();throw e;}
    }
    static String lastProblem(Context c){try{return problem(prefs(c).getInt(PROBLEM,0));}catch(RuntimeException e){return null;}}
    /** Forgets the server and its folders on this TV; watch history and the media server itself are untouched. */
    static void forget(Context c,LibraryFolders.Library lib)throws Exception{
        JSONObject server=LibraryFolders.serverOnly(lib.source);
        LibraryFolders.save(c,server,new java.util.ArrayList<>());LibraryIndex.clear(c);
        prefs(c).edit().remove(PREFERRED).remove(NAME).remove(PROBLEM).remove(CHECKED).apply();
        EventLog.record(c,EventLog.CONNECTION,"Media server forgotten on this TV");
    }
}
