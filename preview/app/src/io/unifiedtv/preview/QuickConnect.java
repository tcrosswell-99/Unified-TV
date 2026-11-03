package io.unifiedtv.preview;
import org.json.JSONObject;
import java.util.UUID;

/** Jellyfin Quick Connect: the TV shows a code, the viewer approves it in the Jellyfin app on their phone. Nobody types a token or user ID. */
final class QuickConnect {
    static final class Start {final String secret,code;Start(String secret,String code){this.secret=secret;this.code=code;}}
    /** A connection that can ask the server for a code: no token yet, the client identity Jellyfin requires. */
    static JSONObject pending(String base,String device)throws Exception{return new JSONObject().put("base",base).put("enabled",true).put("kind","media").put("httpConsent",true).put("device",device);}
    static String device(android.content.Context c){android.content.SharedPreferences p=c.getSharedPreferences("unified-tv-preview",0);String id=p.getString("deviceId","");if(id.isEmpty()){id=UUID.randomUUID().toString();p.edit().putString("deviceId",id).apply();}return id;}
    static Start start(JSONObject reply){String code=reply.optString("Code"),secret=reply.optString("Secret");if(!code.matches("[0-9A-Za-z]{4,12}")||secret.isEmpty()||secret.length()>512)throw new IllegalArgumentException("Quick Connect unavailable");return new Start(secret,code);}
    static Start initiate(JSONObject pending)throws Exception{
        // 10.9+ starts with POST; 10.7-10.8 with GET.
        try{return start(LocalApi.postJson(pending,"/QuickConnect/Initiate",new JSONObject()));}
        catch(LocalApi.ApiError e){if(e.code!=404&&e.code!=405)throw e;return start(LocalApi.get(pending,"/QuickConnect/Initiate"));}
    }
    static boolean approved(JSONObject pending,String secret)throws Exception{return LocalApi.get(pending,"/QuickConnect/Connect?Secret="+LocalApi.segment(secret)).optBoolean("Authenticated");}
    static JSONObject authenticate(JSONObject pending,String secret)throws Exception{return LocalApi.postJson(pending,"/Users/AuthenticateWithQuickConnect",new JSONObject().put("Secret",secret));}
    /** The saved account: the server's answer, held to the same child-account rule as typed credentials. */
    static JSONObject account(JSONObject pending,JSONObject auth,boolean child)throws Exception{
        String token=auth.optString("AccessToken");JSONObject user=auth.optJSONObject("User");if(token.isEmpty()||token.length()>512||user==null)throw new IllegalArgumentException("Sign-in was not completed");
        MediaAccount.require(user,user.optString("Id"),child);
        return new JSONObject(pending.toString()).put("token",token).put("user",user.getString("Id")).put("name",user.optString("Name"));
    }
}
