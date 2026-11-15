package io.unifiedtv.adb;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * The key Unified TV signs in to the TV's own ADB with (the Optional clean-up only). Written from the ADB protocol as
 * Android documents it (system/core/adb/protocol.txt and the adbd key format), not copied from any ADB library.
 *
 * <p>adbd sends a 20-byte token; the host answers with an RSA signature that adbd checks as a SHA-1 PKCS#1 v1.5
 * signature, treating the token itself as the SHA-1 digest. So the token is not hashed again: it is prefixed with SHA-1's
 * DigestInfo and signed with raw PKCS#1 v1.5 padding ("NONEwithRSA"). The public key goes over in adbd's own format:
 * the old mincrypt {@code RSAPublicKey} struct (word count, n0inv, modulus, R² mod n, exponent; little-endian 32-bit
 * words), base64, then a space and a name, which the TV's "Allow USB debugging?" prompt shows as the computer.
 *
 * <p>The private key never leaves its {@link Signer}: nothing here logs, prints or exports it.
 */
public final class AdbKey {
    /** The name the TV shows in its prompt and in its list of allowed computers. */
    public static final String NAME = "unifiedtv@tv";
    /** ASN.1 DigestInfo for SHA-1 (RFC 8017 §9.2, note 1), put in front of the token before it is signed. */
    static final byte[] SHA1_DIGEST_INFO = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
    public static final int TOKEN_BYTES = 20;
    /** adbd's key size: 2048 bits, 64 words. */
    public static final int BITS = 2048, WORDS = BITS / 32;

    /** Signs with the private key, wherever it lives (the Android Keystore on a TV, a plain key in tests). */
    public interface Signer { byte[] sign(byte[] data) throws GeneralSecurityException; }

    private final Signer signer;
    private final RSAPublicKey publicKey;

    public AdbKey(Signer signer, RSAPublicKey publicKey) {
        if (publicKey.getModulus().bitLength() != BITS) throw new IllegalArgumentException("adbd needs a " + BITS + "-bit key");
        this.signer = signer; this.publicKey = publicKey;
    }

    /** A key held as a plain {@link PrivateKey} (the fallback store, and tests). */
    public static AdbKey of(PrivateKey key, RSAPublicKey publicKey) {
        return new AdbKey(data -> { Signature s = Signature.getInstance("NONEwithRSA"); s.initSign(key); s.update(data); return s.sign(); }, publicKey);
    }

    /** What is signed for a token: SHA-1's DigestInfo, then the token as the digest. */
    public static byte[] toSign(byte[] token) {
        if (token == null || token.length != TOKEN_BYTES) throw new IllegalArgumentException("token");
        byte[] out = new byte[SHA1_DIGEST_INFO.length + TOKEN_BYTES];
        System.arraycopy(SHA1_DIGEST_INFO, 0, out, 0, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, out, SHA1_DIGEST_INFO.length, TOKEN_BYTES);
        return out;
    }

    /** The AUTH SIGNATURE payload for adbd's token. */
    public byte[] signToken(byte[] token) throws GeneralSecurityException { return signer.sign(toSign(token)); }

    /** The AUTH RSAPUBLICKEY payload's text (without the trailing NUL the protocol adds). */
    public String publicKeyLine() { return encode(publicKey) + " " + NAME; }

    /** adbd's public key format, base64 without line breaks. */
    public static String encode(RSAPublicKey key) {
        BigInteger n = key.getModulus();
        if (n.bitLength() != BITS) throw new IllegalArgumentException("adbd needs a " + BITS + "-bit key");
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        // n0inv = -1 / n[0] mod 2^32, as mincrypt's Montgomery multiplication wants it.
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32)).mod(r32);
        BigInteger rr = BigInteger.ONE.shiftLeft(2 * BITS).mod(n);
        ByteBuffer b = ByteBuffer.allocate(4 + 4 + WORDS * 4 * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(WORDS);
        b.putInt(n0inv.intValue());
        words(b, n);
        words(b, rr);
        BigInteger e = key.getPublicExponent();
        if (e.signum() <= 0 || e.bitLength() > 31) throw new IllegalArgumentException("exponent");
        b.putInt(e.intValue());   // not intValueExact(): that arrived on Android only in API 31
        return Base64.getEncoder().encodeToString(b.array());
    }

    /** The struct's size in bytes: 524 for a 2048-bit key. */
    public static int structBytes() { return 4 + 4 + WORDS * 4 * 2 + 4; }

    private static void words(ByteBuffer b, BigInteger v) {
        BigInteger mask = BigInteger.ONE.shiftLeft(32).subtract(BigInteger.ONE);
        for (int i = 0; i < WORDS; i++) b.putInt(v.shiftRight(32 * i).and(mask).intValue());
    }

    /** The public key line as UTF-8 bytes with the NUL the protocol ends it with. */
    byte[] publicKeyPayload() {
        byte[] text = publicKeyLine().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[text.length + 1];
        System.arraycopy(text, 0, out, 0, text.length);
        return out;
    }
}
