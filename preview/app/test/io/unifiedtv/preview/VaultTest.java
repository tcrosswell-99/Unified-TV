package io.unifiedtv.preview;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.file.Files;

/**
 * SET-005: the Vault keeps connection records (Home Assistant, media accounts, the TMDB key) encrypted, never in plain
 * preferences, and never backed up. JVM tests have no Android Keystore, so a test key stands in for the Keystore's
 * (Vault.testKey); everything else is the production code: AES-GCM, a fresh IV per save, the record's id bound in.
 */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
public class VaultTest {
    Context app;SharedPreferences raw;
    @Before public void start()throws Exception{
        app=RuntimeEnvironment.getApplication();raw=app.getSharedPreferences("vault",0);raw.edit().clear().commit();
        Vault.testKey=aes();
    }
    @After public void stop(){Vault.testKey=null;raw.edit().clear().commit();}
    static SecretKey aes()throws Exception{KeyGenerator g=KeyGenerator.getInstance("AES");g.init(256);return g.generateKey();}
    static JSONObject secret()throws Exception{return new JSONObject().put("enabled",true).put("base","https://ha.example.invalid:8123").put("token","eyJsecret-long-lived-token-value");}

    @Test public void aRecordComesBackExactly()throws Exception{
        new Vault(app).save("ha",secret());
        JSONObject back=new Vault(app).read("ha");
        assertEquals(secret().toString(),back.toString());
        assertEquals("nothing saved: an empty record, not an error",0,new Vault(app).read("household").length());
    }
    @Test public void nothingReadableIsStored()throws Exception{
        new Vault(app).save("ha",secret());String stored=raw.getString("ha","");
        assertTrue("IV:ciphertext",stored.matches("[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+"));
        for(String plain:new String[]{"eyJsecret","ha.example.invalid","token","8123"})assertFalse(plain+" in "+stored,stored.contains(plain));
        assertEquals("the GCM IV is 12 bytes",12,android.util.Base64.decode(stored.split(":")[0],android.util.Base64.NO_WRAP).length);
        new Vault(app).save("ha",secret());assertNotEquals("a fresh IV every save",stored,raw.getString("ha",""));
        for(String name:new String[]{"unified-tv-preview","vault"}){java.io.File f=new java.io.File(app.getDataDir(),"shared_prefs/"+name+".xml");
            if(f.isFile())assertFalse(name+" holds no token",new String(Files.readAllBytes(f.toPath()),"UTF-8").contains("eyJsecret"));}
        for(java.util.Map.Entry<String,?> e:app.getSharedPreferences("unified-tv-preview",0).getAll().entrySet())assertFalse(e.getKey(),String.valueOf(e.getValue()).contains("eyJsecret"));
    }
    @Test public void aRecordCannotBeMovedTamperedOrReadWithAnotherKey()throws Exception{
        new Vault(app).save("ha",secret());String stored=raw.getString("ha","");
        raw.edit().putString("media-adult",stored).commit();
        try{new Vault(app).read("media-adult");fail("a record copied under another id is refused (the id is bound in)");}catch(Exception expected){}
        String[] bits=stored.split(":");byte[] data=android.util.Base64.decode(bits[1],android.util.Base64.NO_WRAP);data[3]^=1;
        raw.edit().putString("ha",bits[0]+":"+android.util.Base64.encodeToString(data,android.util.Base64.NO_WRAP)).commit();
        try{new Vault(app).read("ha");fail("a changed byte is refused");}catch(Exception expected){}
        raw.edit().putString("ha",stored).commit();Vault.testKey=aes();
        try{new Vault(app).read("ha");fail("another key (another TV, or a copied file) can't read it");}catch(Exception expected){}
    }
    @Test public void forgetRemovesIt()throws Exception{
        new Vault(app).save("tmdb",new JSONObject().put("key","0123456789abcdef0123456789abcdef"));assertTrue(raw.contains("tmdb"));
        new Vault(app).forget("tmdb");assertFalse(raw.contains("tmdb"));assertEquals(0,new Vault(app).read("tmdb").length());
    }
    @Test public void withoutTheKeystoreNothingIsSavedInPlainText()throws Exception{
        Vault.testKey=null;   // production path: the Android Keystore, absent in JVM tests
        try{new Vault(app).save("ha",secret());fail("no key: save fails rather than storing plain text");}catch(Exception expected){}
        assertFalse(raw.contains("ha"));
    }
    @Test public void neitherFileIsBackedUp()throws Exception{
        String manifest=new String(Files.readAllBytes(new java.io.File(SidebarRuleTest.app(),"AndroidManifest.xml").toPath()),"UTF-8");
        assertTrue(manifest.contains("android:allowBackup=\"false\""));
        String rules=new String(Files.readAllBytes(new java.io.File(SidebarRuleTest.app(),"res/xml/data_extraction_rules.xml").toPath()),"UTF-8");
        for(String part:new String[]{"<cloud-backup>","<device-transfer>"}){String block=rules.substring(rules.indexOf(part));
            assertTrue(part+" excludes preferences",block.contains("<exclude domain=\"sharedpref\" path=\".\"/>"));assertTrue(part+" excludes files",block.contains("<exclude domain=\"file\" path=\".\"/>"));}
    }
}
