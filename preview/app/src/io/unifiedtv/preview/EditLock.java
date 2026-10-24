package io.unifiedtv.preview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import android.util.Base64;

final class EditLock {
    static boolean child(Context c) { return c.getSharedPreferences("unified-tv-preview",0).getString("profile","adult").equals("child"); }
    static void ask(Activity a,Runnable success) {
        String saved=a.getSharedPreferences("unified-tv-preview",0).getString("pin","");
        if (saved.isEmpty()) { if (!child(a)) success.run(); return; }
        if(a.getSharedPreferences("unified-tv-preview",0).getLong("pinWait",0)>System.currentTimeMillis()){
            android.widget.Toast.makeText(a,"Please wait before trying your PIN again",android.widget.Toast.LENGTH_SHORT).show();return;
        }
        EditText field=new EditText(a); field.setInputType(18); field.setHint("Adult PIN");
        new AlertDialog.Builder(a).setTitle("Adult access").setView(field).setNegativeButton("Cancel",null)
            .setPositiveButton("Unlock",(d,w)->{
                try {
                    String[] bits=saved.split(":"); byte[] salt=Base64.decode(bits[0],Base64.NO_WRAP);
                    byte[] expected=Base64.decode(bits[1],Base64.NO_WRAP), actual=hash(field.getText().toString(),salt);
                    if (MessageDigest.isEqual(expected,actual)) {a.getSharedPreferences("unified-tv-preview",0).edit().putInt("pinFailures",0).remove("pinWait").apply();success.run();}
                    else {
                        int failures=a.getSharedPreferences("unified-tv-preview",0).getInt("pinFailures",0)+1;
                        a.getSharedPreferences("unified-tv-preview",0).edit().putInt("pinFailures",failures).putLong("pinWait",failures>=5?System.currentTimeMillis()+30000:0).apply();
                        android.widget.Toast.makeText(a,failures>=5?"Please wait 30 seconds before trying again":"PIN not recognised",android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch(Exception e) { android.widget.Toast.makeText(a,"Unable to unlock",android.widget.Toast.LENGTH_SHORT).show(); }
            }).show();
    }
    static void set(Activity a) {
        EditText field=new EditText(a); field.setInputType(18); field.setHint("New PIN, at least 4 digits");
        new AlertDialog.Builder(a).setTitle("Protect editing and adult profile").setView(field).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->{
                try {
                    String pin=field.getText().toString(); if (!pin.matches("[0-9]{4,12}")) throw new IllegalArgumentException();
                    byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
                    a.getSharedPreferences("unified-tv-preview",0).edit().putString("pin",Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(hash(pin,salt),Base64.NO_WRAP)).apply();
                } catch(Exception e) { android.widget.Toast.makeText(a,"Use 4 to 12 digits",android.widget.Toast.LENGTH_SHORT).show(); }
            }).show();
    }
    private static byte[] hash(String pin,byte[] salt) throws Exception {
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(pin.toCharArray(),salt,120000,256)).getEncoded();
    }
}
