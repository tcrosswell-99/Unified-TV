package io.unifiedtv.preview;

import android.content.Context;
import io.unifiedtv.adb.AdbKey;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.File;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import javax.crypto.KeyGenerator;
import static org.junit.Assert.*;

/** The clean-up's ADB key keeps one identity, so the TV asks only once (JVM tests have no Android Keystore). */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 30)
public class AdbKeysTest {
    private Context app;

    @Before public void setUp() {
        app = RuntimeEnvironment.getApplication();
        AdbKeys.testKey = null; Vault.testKey = null;
        app.getSharedPreferences("vault", 0).edit().clear().commit();
        File f = new File(app.getNoBackupFilesDir(), AdbKeys.FILE); if (f.exists()) assertTrue(f.delete());
    }
    @After public void tearDown() { AdbKeys.keystoreForTests = null; Vault.testKey = null; }

    private static AdbKey fresh() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA"); g.initialize(2048); KeyPair p = g.generateKeyPair();
        return AdbKey.of(p.getPrivate(), (RSAPublicKey) p.getPublic());
    }

    @Test public void oneKeystoreFailureIsRetriedNotSwitchedAway() throws Exception {
        AdbKey keystore = fresh(); int[] calls = {0};
        AdbKeys.keystoreForTests = () -> { if (calls[0]++ == 0) throw new java.security.ProviderException("busy"); return keystore; };
        assertSame("the Keystore key, after one retry", keystore, AdbKeys.get(app));
        assertEquals(2, calls[0]);
        assertFalse("no second key was made", new File(app.getNoBackupFilesDir(), AdbKeys.FILE).exists());
    }

    @Test public void theStoredKeyStaysTheSame() throws Exception {
        AdbKeys.keystoreForTests = () -> { throw new java.security.KeyStoreException("none"); };
        Vault.testKey = KeyGenerator.getInstance("AES").generateKey();
        String first = AdbKeys.get(app).publicKeyLine();
        assertEquals(first, AdbKeys.get(app).publicKeyLine());
    }

    @Test public void aKeyInTheFileIsUsedBeforeANewOneIsMade() throws Exception {
        AdbKeys.keystoreForTests = () -> { throw new java.security.KeyStoreException("none"); };
        String first = AdbKeys.get(app).publicKeyLine();   // no Vault key: the file
        assertTrue(new File(app.getNoBackupFilesDir(), AdbKeys.FILE).exists());
        Vault.testKey = KeyGenerator.getInstance("AES").generateKey();   // the Vault works now, but holds nothing
        assertEquals("same identity: the TV doesn't ask again", first, AdbKeys.get(app).publicKeyLine());
    }
}
