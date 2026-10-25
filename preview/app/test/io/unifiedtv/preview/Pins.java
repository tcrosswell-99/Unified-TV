package io.unifiedtv.preview;

import android.app.AlertDialog;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Test help: an adult PIN saved as EditLock saves one (salted PBKDF2), and typing it into EditLock's "Adult access" box. */
final class Pins {
    private Pins(){}
    /** The stored record for {@code pin}: "salt:hash", Base64, as EditLock.set writes it. */
    static String record(String pin)throws Exception{
        byte[] salt=new byte[16];for(int i=0;i<salt.length;i++)salt[i]=(byte)(i*7+3);
        byte[] hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(pin.toCharArray(),salt,120000,256)).getEncoded();
        return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(hash,Base64.NO_WRAP);
    }
    static EditText field(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText e=field(((ViewGroup)v).getChildAt(i));if(e!=null)return e;}return null;}
    /** Types {@code pin} into the open "Adult access" dialog and presses Unlock. */
    static void type(AlertDialog d,String pin){field(d.getWindow().getDecorView()).setText(pin);d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();}
}
