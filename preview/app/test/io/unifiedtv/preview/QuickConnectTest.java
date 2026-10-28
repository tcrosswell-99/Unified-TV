package io.unifiedtv.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
public class QuickConnectTest {
    static JSONObject pending()throws Exception{return QuickConnect.pending("http://192.168.1.10:8096","3f2b0c1e-7a8d-4e2f-9c11-0a1b2c3d4e5f");}
    @Test public void codeAndSecretComeFromTheServer()throws Exception{
        QuickConnect.Start s=QuickConnect.start(new JSONObject().put("Code","482913").put("Secret","abc123"));assertEquals("482913",s.code);assertEquals("abc123",s.secret);
        assertThrows(IllegalArgumentException.class,()->QuickConnect.start(new JSONObject().put("Code","<script>").put("Secret","abc")));
        assertThrows(IllegalArgumentException.class,()->QuickConnect.start(new JSONObject().put("Code","482913")));
    }
    @Test public void approvedSignInBecomesASavedAccountWithoutTyping()throws Exception{
        JSONObject auth=new JSONObject().put("AccessToken","0123456789abcdef").put("User",new JSONObject().put("Id","u1").put("Name","Family").put("Policy",new JSONObject().put("IsAdministrator",true)));
        JSONObject account=QuickConnect.account(pending(),auth,false);
        assertEquals("0123456789abcdef",account.getString("token"));assertEquals("u1",account.getString("user"));assertEquals("http://192.168.1.10:8096",account.getString("base"));assertTrue(account.getBoolean("enabled"));assertEquals("media",account.getString("kind"));
    }
    @Test public void childProfileRefusesAnAdministratorApproval()throws Exception{
        JSONObject admin=new JSONObject().put("AccessToken","t").put("User",new JSONObject().put("Id","u1").put("Policy",new JSONObject().put("IsAdministrator",true)));
        assertThrows(SecurityException.class,()->QuickConnect.account(pending(),admin,true));
        JSONObject kid=new JSONObject().put("AccessToken","t").put("User",new JSONObject().put("Id","k1").put("Policy",new JSONObject().put("IsAdministrator",false)));
        assertEquals("k1",QuickConnect.account(pending(),kid,true).getString("user"));
    }
    @Test public void incompleteAnswerIsNotSaved()throws Exception{assertThrows(IllegalArgumentException.class,()->QuickConnect.account(pending(),new JSONObject().put("User",new JSONObject().put("Id","u1")),false));}
    @Test public void clientHeaderCarriesOnlyTheAppNameAndInstallId(){
        assertEquals("MediaBrowser Client=\"Unified TV\", Device=\"TV\", DeviceId=\"abc-123\", Version=\"0.5\"",LocalApi.clientHeader("abc-123",""));
        assertTrue(LocalApi.clientHeader("abc",  "0123abcd").endsWith(", Token=\"0123abcd\""));
        assertFalse("no header injection",LocalApi.clientHeader("a\"b\r\nX: y","t\"oken").contains("\n"));
        assertEquals("MediaBrowser Client=\"Unified TV\", Device=\"TV\", DeviceId=\"unified-tv\", Version=\"0.5\"",LocalApi.clientHeader("",""));
    }
}
