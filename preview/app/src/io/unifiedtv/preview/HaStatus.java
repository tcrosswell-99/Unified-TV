package io.unifiedtv.preview;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The five connection words of spec 11 "Connections", from what actually happened. Pure: no network here.
 * "Authorised" means only that Home Assistant accepted this TV's token for that request; nothing more is claimed.
 */
final class HaStatus {
    static final String NOT_CONFIGURED="Not configured",REACHABLE="Reachable",AUTH_REQUIRED="Authentication required",
        AUTHORISED="Authorised",OFFLINE="Offline";
    private HaStatus(){}

    /** Before any request: Not configured when there is nothing LocalApi would be allowed to call, otherwise null (ask the server). */
    static String before(JSONObject ha){
        if(ha==null||!ha.optBoolean("enabled")||ha.optString("base").isEmpty())return NOT_CONFIGURED;
        // LocalApi refuses plain HTTP without the owner's consent for that host; that is unfinished setup, not an outage.
        if(ha.optString("base").startsWith("http:")&&!ha.optBoolean("httpConsent"))return NOT_CONFIGURED;
        return null;
    }

    /** After a request: null error means Home Assistant answered and accepted the token. */
    static String after(Exception error){
        if(error==null)return AUTHORISED;
        if(error instanceof LocalApi.ApiError){int code=((LocalApi.ApiError)error).code;return code==401||code==403?AUTH_REQUIRED:REACHABLE;}
        // It answered, but not with something readable as Home Assistant's list.
        if(error instanceof JSONException)return REACHABLE;
        // A saved address that can no longer be resolved into a request is unfinished setup.
        if(error instanceof IllegalArgumentException)return NOT_CONFIGURED;
        // Timeouts, refused connections, unknown hosts, certificate failures (all IOException) and anything else: nothing usable answered.
        return OFFLINE;
    }

    /** One plain sentence for the household under the status word. */
    static String explain(String status){
        switch(status){
            case NOT_CONFIGURED:return "Home Assistant isn't set up on this TV yet.";
            case AUTH_REQUIRED:return "Home Assistant needs this TV to sign in again.";
            case REACHABLE:return "Home Assistant answered, but didn't share its list of devices.";
            case OFFLINE:return "Home Assistant can't be reached. Check it's switched on and on the same home network as this TV.";
            case AUTHORISED:return "Connected. Choose each item below.";
            default:return "";
        }
    }
}
