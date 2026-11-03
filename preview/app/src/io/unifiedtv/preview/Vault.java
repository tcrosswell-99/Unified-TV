package io.unifiedtv.preview;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the entire connection record. No tokens, hosts or user IDs in plain preferences. */
final class Vault {
    private static final String ALIAS = "unified-tv-connections-v1";
    private final Context context;
    Vault(Context context) { this.context = context; }
    /**
     * Tests only (SET-005 round-trip test): a key in place of the Android Keystore's, which JVM tests don't have. Production
     * never sets it, so on a TV the key always comes from the Keystore below: non-exportable, AES-256-GCM, a fresh IV per
     * save and the record's id bound in as associated data. There is no plaintext fallback: without a key, save and read throw.
     */
    static volatile SecretKey testKey;
    private SecretKey key() throws Exception {
        SecretKey t = testKey; if (t != null) return t;
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    JSONObject read(String id) throws Exception {
        String raw = context.getSharedPreferences("vault", 0).getString(id, "");
        if (raw.isEmpty()) return new JSONObject();
        String[] bits = raw.split(":", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(bits[0], Base64.NO_WRAP)));
        cipher.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(bits[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8));
    }
    void save(String id, JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] data = cipher.doFinal(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String raw = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
        if (!context.getSharedPreferences("vault", 0).edit().putString(id, raw).commit()) throw new java.io.IOException();
    }
    void forget(String id) { context.getSharedPreferences("vault", 0).edit().remove(id).apply(); }
    static String mediaKey(Context c) { return "media-" + c.getSharedPreferences("unified-tv-preview",0).getString("profile","adult"); }
}
