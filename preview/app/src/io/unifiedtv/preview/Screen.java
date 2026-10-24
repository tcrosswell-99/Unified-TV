package io.unifiedtv.preview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.*;

/** Native focusable controls provide D-pad, keyboard and TalkBack semantics. */
class Screen extends Activity {
    LinearLayout column;
    int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    void page(String title) {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xff101827);
        column=new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(48,28,48,36);
        scroll.addView(column); setContentView(scroll); text(title,30);
    }
    TextView text(String s,int size) { TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size); t.setPadding(8,6,8,8); column.addView(t); return t; }
    Button button(String s,Runnable action) {
        Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(20); b.setMinHeight(58);
        b.setOnClickListener(v->action.run()); column.addView(b,new LinearLayout.LayoutParams(-1,-2)); return b;
    }
    EditText field(String label,String value,boolean secret) {
        text(label,18); EditText e=new EditText(this); e.setText(value); e.setTextColor(Color.WHITE); e.setTextSize(20);
        e.setSingleLine(true); e.setSelectAllOnFocus(true); e.setContentDescription(label);
        e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        e.setInputType(secret ? 129 : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        column.addView(e,new LinearLayout.LayoutParams(-1,58)); return e;
    }
    void note(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
