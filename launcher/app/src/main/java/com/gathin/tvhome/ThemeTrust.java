package com.gathin.tvhome;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Who wrote the theme's config.json. It sits in Android/data, which before Android 11 is shared storage: any app
 * the user gave the storage permission can write it, and config.json maps remote buttons to apps (app_keys), sets
 * the VPN badge, the Live TV app, hidden apps and the stock-home redirect. On Android 10 tools/theme.sh always
 * writes it through ThemeProvider (WRITE_SECURE_SETTINGS, which only the adb shell has), and the provider records
 * the SHA-256 of what was written, so a file with other bytes is someone else's and is ignored. Android 8 and 9
 * have no `content write`, so the tools can only cp the file and nothing tells their copy from another app's: the
 * check stays off there, as it is from Android 11, where Android/data belongs to the app and the shell alone.
 */
final class ThemeTrust {
    /** The provider's record of the config.json it last wrote, in the app's internal files. */
    static final String RECORD_NAME = "config.json.sha256";

    private ThemeTrust() {}

    /** Whether config.json has to match the provider's record on this Android version (API 29, Android 10). */
    static boolean checked(int sdk) {
        return sdk == 29;
    }

    /**
     * Whether the overlay can be applied. Unchecked versions take it as before. Otherwise it has to be the bytes the
     * provider recorded, or be the same JSON as the internal copy the launcher kept from the last overlay it applied
     * (a theme pushed before this check existed keeps working after the update).
     */
    static boolean trusted(int sdk, String sha, String recorded, String overlayJson, String copyJson) {
        if (!checked(sdk)) return true;
        if (sha != null && sha.equals(recorded)) return true;
        return overlayJson != null && overlayJson.equals(copyJson);
    }

    /** Lower-case hex SHA-256, or null if the platform has none (it always does). */
    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
