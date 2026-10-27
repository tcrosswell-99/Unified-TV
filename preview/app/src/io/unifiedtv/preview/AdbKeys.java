package io.unifiedtv.preview;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import io.unifiedtv.adb.AdbKey;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The key the Optional clean-up signs in to this TV's own ADB with (io.unifiedtv.adb.AdbKey). Kept on this TV only and
 * never logged, shown, exported, backed up or sent anywhere: the phone's undo file never contains it.
 *
 * <ol>
 * <li>First choice: the Android Keystore, non-exportable, RSA 2048 for signing with no digest and PKCS#1 v1.5 padding,
 *     which is exactly what adbd checks ("NONEwithRSA" over SHA-1's DigestInfo and the token). Supported since API 23;
 *     a test signature proves it on this TV before it is used.</li>
 * <li>If this TV's Keystore can't do that: a key made in software, stored encrypted by {@link Vault} (the Keystore's
 *     AES-GCM key), in app-private storage that backups exclude (data_extraction_rules.xml, allowBackup=false).</li>
 * <li>If even the Vault can't work: the same key in this app's private no-backup folder, protected by Android's app
 *     sandbox alone. It can only sign in to this TV, and only while the owner keeps that key allowed.</li>
 * </ol>
 * The owner can withdraw it at any time in the TV's Developer options ("Revoke USB debugging authorisations").
 */
final class AdbKeys {
    static final String ALIAS = "unified-tv-adb-v1", VAULT_ID = "adb-key", FILE = "adb-key.json";
    /** Tests only: a key in place of this TV's (JVM tests have no Keystore). Production never sets it. */
    static volatile AdbKey testKey;
    private AdbKeys() {}

    static synchronized AdbKey get(Context c) throws Exception {
        AdbKey t = testKey; if (t != null) return t;
        try { return keystore(); } catch (Exception | LinkageError ignored) { /* this TV's Keystore can't: the stored key below */ }
        return stored(c.getApplicationContext());
    }

    private static AdbKey keystore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            gen.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(AdbKey.BITS, RSAKeyGenParameterSpec.F4))
                .setDigests(KeyProperties.DIGEST_NONE).setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1).build());
            gen.generateKeyPair();
        }
        AdbKey key = AdbKey.of((PrivateKey) store.getKey(ALIAS, null), (RSAPublicKey) store.getCertificate(ALIAS).getPublicKey());
        key.signToken(new byte[AdbKey.TOKEN_BYTES]);   // proves NONEwithRSA with PKCS#1 padding works on this TV
        return key;
    }

    private static AdbKey stored(Context c) throws Exception {
        JSONObject saved;
        boolean vault = true;
        try { saved = new Vault(c).read(VAULT_ID); } catch (Exception e) { vault = false; saved = readFile(c); }
        if (saved.optString("private").isEmpty()) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(new RSAKeyGenParameterSpec(AdbKey.BITS, RSAKeyGenParameterSpec.F4));
            KeyPair pair = gen.generateKeyPair();
            saved = new JSONObject().put("private", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
                .put("public", Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            if (vault) { try { new Vault(c).save(VAULT_ID, saved); } catch (Exception e) { vault = false; } }
            if (!vault) writeFile(c, saved);
        }
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        PrivateKey priv = rsa.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(saved.getString("private"))));
        RSAPublicKey pub = (RSAPublicKey) rsa.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(saved.getString("public"))));
        return AdbKey.of(priv, pub);
    }

    private static File file(Context c) { return new File(c.getNoBackupFilesDir(), FILE); }
    private static JSONObject readFile(Context c) {
        try { return new JSONObject(new String(Files.readAllBytes(file(c).toPath()), StandardCharsets.UTF_8)); } catch (Exception e) { return new JSONObject(); }
    }
    private static void writeFile(Context c, JSONObject saved) throws Exception {
        File f = file(c), tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(saved.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync(); }
        if (!tmp.renameTo(f)) throw new java.io.IOException("key not saved");
    }
}
