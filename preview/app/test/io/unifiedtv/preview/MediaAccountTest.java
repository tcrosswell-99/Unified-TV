package io.unifiedtv.preview;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class MediaAccountTest {
    @Test public void matchingNonAdminChildIsAccepted()throws Exception{MediaAccount.require(new JSONObject("{\"Id\":\"child\",\"Policy\":{\"IsAdministrator\":false}}"),"child",true);}
    @Test public void globalOrWrongUserTokenIsRejected()throws Exception{assertThrows(SecurityException.class,()->MediaAccount.require(new JSONObject("{\"Id\":\"adult\"}"),"child",true));}
    @Test public void adminOrMissingPolicyCannotBeChild()throws Exception{assertThrows(SecurityException.class,()->MediaAccount.require(new JSONObject("{\"Id\":\"child\",\"Policy\":{\"IsAdministrator\":true}}"),"child",true));assertThrows(SecurityException.class,()->MediaAccount.require(new JSONObject("{\"Id\":\"child\"}"),"child",true));}
}
