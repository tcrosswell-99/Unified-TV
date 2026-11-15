package io.unifiedtv.preview;

import io.nayuki.qrcodegen.QrCode;
import org.junit.Test;
import static org.junit.Assert.*;
import java.math.BigInteger;

/**
 * The vendored QR encoder against known vectors: each matrix below was produced by this encoder and independently
 * decoded back to its text by zxing-cpp (a separate implementation), and its version and error-correction level match
 * segno's for the same input. Rows are hexadecimal, most significant bit = leftmost module.
 */
public class QrCodeTest {
    static final String[][] VECTORS = {
        {"https://dashboard.api-football.com/register", "MEDIUM", "4", "QUARTILE",
         "1fdc26a7f,104d55d41,1742e0f5d,1746d655d,17508925d,105172541,1fd55557f,453f00,fecca531,a3fafa6f,266b85d6,9949cd5f,1c5006bb,e938ca6f,15def48e6,18aad2edc,563017b1,17223766d,f5201836,c8d04fff,ef56d5bb,1493e3f45,164e710a2,17310981f,12f45c7f1,1d63b15,1fdeb4356,105bc3f1d,175ada5fa,1755aff95,1751fcd6c,105e9d8dc,1fc3143e2"},
        {"http://192.168.1.20:51234/AbCdEfGhIjKlMnOpQrStUv/", "MEDIUM", "4", "MEDIUM",
         "1fd0c997f,1059d6941,1752d3a5d,174a5585d,17563865d,10417e741,1fd55557f,ef0300,13f6dd497,506cb79c,e57a1e9f,41db67a7,1d45a8a71,836c7ea0,3646f0b4,69a555ed,14de2427c,b14aa1d4,1c7b2fb0d,9b4436be,35776228,1a1400070,17c8c7b17,148bab386,1df5947fb,1319116,1fd6aa354,10592f517,1758193f3,17525ce0b,1748ea33b,10408421f,1fdb7d764"},
        {"Hello, world!", "LOW", "1", "MEDIUM",
         "1fc17f,104a41,17575d,17505d,17595d,105e41,1fd57f,1400,17ce7c,3319d,25dce,ca7ac,1bd161,10f8,1fcde6,1055ae,175bd3,175438,175f64,10459c,1fda52"}};

    private static String rows(QrCode q) {
        StringBuilder b = new StringBuilder();
        for (int y = 0; y < q.size; y++) { BigInteger r = BigInteger.ZERO; for (int x = 0; x < q.size; x++) r = r.shiftLeft(1).or(q.getModule(x, y) ? BigInteger.ONE : BigInteger.ZERO); if (y > 0) b.append(','); b.append(r.toString(16)); }
        return b.toString();
    }

    @Test public void knownVectors() {
        for (String[] v : VECTORS) {
            QrCode q = QrCode.encodeText(v[0], QrCode.Ecc.valueOf(v[1]));
            assertEquals(v[0], Integer.parseInt(v[2]), q.version);
            assertEquals(v[0], QrCode.Ecc.valueOf(v[3]), q.errorCorrectionLevel);
            assertEquals(v[0], 17 + 4 * q.version, q.size);
            assertEquals(v[0], v[4], rows(q));
        }
    }

    @Test public void finderPatternsSitInThreeCorners() {
        QrCode q = QrCode.encodeText("http://192.168.1.20:51234/AbCdEfGhIjKlMnOpQrStUv/", QrCode.Ecc.MEDIUM);
        int n = q.size;
        for (int[] c : new int[][]{{0, 0}, {n - 7, 0}, {0, n - 7}}) for (int d = 0; d < 7; d++) {
            assertTrue(q.getModule(c[0] + d, c[1])); assertTrue(q.getModule(c[0] + d, c[1] + 6)); assertTrue(q.getModule(c[0], c[1] + d)); assertTrue(q.getModule(c[0] + 6, c[1] + d));
        }
        assertFalse("separator", q.getModule(7, 0)); assertFalse("outside", q.getModule(-1, 0));
    }

    @Test public void modulesAreWholePixelsWithAQuietZone() {
        assertEquals("version 4 (33 modules) in 330 px: 8 px a module", 8, QrView.moduleSize(330, 33));
        assertEquals("never below one pixel", 1, QrView.moduleSize(10, 33));
        assertTrue("symbol and quiet zone fit", QrView.moduleSize(330, 33) * (33 + 2 * QrView.QUIET) <= 330);
    }
}
