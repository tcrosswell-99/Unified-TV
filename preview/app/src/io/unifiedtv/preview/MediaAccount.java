package io.unifiedtv.preview;
import org.json.JSONObject;
final class MediaAccount {
    static void require(JSONObject account,String expectedUser,boolean child){
        if(expectedUser.isEmpty()||!expectedUser.equals(account.optString("Id")))throw new SecurityException("Use the selected user's own access token");
        if(child&&(account.optJSONObject("Policy")==null||account.optJSONObject("Policy").optBoolean("IsAdministrator")))throw new SecurityException("Use a restricted child account");
    }
}
