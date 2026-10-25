package io.unifiedtv.preview;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/**
 * A setup screen as the right-hand glass sheet over a still Home (owner principle OD-11: every settings or setup screen
 * is the glass sidebar, never full screen), with Settings' own material and sizes (module 13: SettingsGlass tokens,
 * SettingsOverlay widths and motion). The activity uses SettingsGlassTheme (translucent), so Home stays visible.
 *
 * Subclasses build pages with {@link #page(String,String)}, rows, text and spinners; {@code text()} and {@code button()}
 * are styled for the sheet, so a screen written for {@link Screen} needs no other change. Back (or Left with nothing
 * further left) is {@link #onBackPressed()}.
 */
class GlassScreen extends Screen {
    SharedPreferences prefs;float unit;int screenWidth;
    FrameLayout sheet;LinearLayout header,body;ScrollView scroll;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);autoFocusButtons=false;prefs=getSharedPreferences(HouseholdSettings.PREFS,0);
        android.util.DisplayMetrics m=getResources().getDisplayMetrics();screenWidth=Math.max(1,m.widthPixels);unit=Math.max(.5f,Math.min(m.widthPixels/1920f,m.heightPixels/1080f));
        FrameLayout root=new FrameLayout(this);sheet=new FrameLayout(this);
        float radius=px(24);boolean solid=prefs.getBoolean("opaque",false)||prefs.getBoolean("highContrast",false);
        if(!solid){View frost=new SettingsGlass.Frost(this,prefs.getString("scene","neutral"),blurs(),shadow(),radius,screenWidth);
            if(blurs()&&Build.VERSION.SDK_INT>=Build.VERSION_CODES.S)frost.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(24),px(24),android.graphics.Shader.TileMode.CLAMP));
            sheet.addView(frost,new FrameLayout.LayoutParams(-1,-1));}
        View glass=new View(this);glass.setFocusable(false);glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);glass.setBackground(new SettingsGlass.Sheet(shadow(),radius,solid));sheet.addView(glass,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setPadding(Math.round(shadow())+px(56),px(44),px(56),px(24));
        header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);inner.addView(header,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this);scroll.setFocusable(false);scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);scroll.setVerticalFadingEdgeEnabled(true);scroll.setFadingEdgeLength(px(36));
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,px(8),0,px(24));scroll.addView(body,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams under=new LinearLayout.LayoutParams(-1,0,1);under.topMargin=px(14);inner.addView(scroll,under);
        sheet.addView(inner,new FrameLayout.LayoutParams(-1,-1));
        float dp=screenWidth/getResources().getDisplayMetrics().density;
        root.addView(sheet,new FrameLayout.LayoutParams(Math.round(SettingsOverlay.fraction(false,dp)*screenWidth+shadow()),-1,Gravity.END));
        setContentView(root);column=body;
        if(state==null)slideIn();
    }

    // --- sizes and material (module 13 tokens, as Settings)
    int px(float at1080){return Math.round(at1080*unit);}
    float textPx(float at1080){return at1080*unit*getResources().getConfiguration().fontScale;}
    float shadow(){return px(28);}
    boolean calm(){return prefs.getBoolean("reduceMotion",false)||!android.animation.ValueAnimator.areAnimatorsEnabled();}
    private boolean blurs(){return Build.VERSION.SDK_INT>=Build.VERSION_CODES.S&&!prefs.getBoolean("lightweight",false)&&!((android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE)).isLowRamDevice();}
    private void slideIn(){
        if(calm()){sheet.setAlpha(0);sheet.post(()->sheet.animate().alpha(1).setDuration(SettingsOverlay.CROSSFADE_MS).start());return;}
        sheet.setTranslationX(screenWidth);sheet.post(()->sheet.animate().translationX(0).setDuration(SettingsOverlay.OPEN_MS).setInterpolator(new DecelerateInterpolator(2f)).start());
    }
    /** Leaves with no second window animation: Home underneath is already in place. */
    void close(){finish();overridePendingTransition(0,0);}

    // --- pages and building blocks
    /** A new page in the same sheet: header with Back, title and optional subtitle; an empty body. */
    void page(String title,String sub){clear();head(title,sub);focusFirst();}
    @Override void page(String title){page(title,null);}
    void clear(){body.removeAllViews();column=body;scroll.scrollTo(0,0);}
    void head(String title,String sub){
        header.removeAllViews();
        ImageView back=new ImageView(this);back.setImageDrawable(new SettingsGlass.Glyph(SettingsGlass.Glyph.BACK,px(26)));back.setScaleType(ImageView.ScaleType.CENTER);back.setContentDescription("Back");
        back.setBackground(SettingsGlass.row(px(28),Math.max(2,px(2)),false,0));back.setFocusable(true);back.setFocusableInTouchMode(true);back.setClickable(true);back.setOnClickListener(v->onBackPressed());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(56),px(56));lp.setMarginEnd(px(18));header.addView(back,lp);
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        TextView t=label(title,36,SettingsGlass.TEXT);t.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD));words.addView(t);
        if(sub!=null&&!sub.isEmpty())words.addView(label(sub,21,SettingsGlass.SECONDARY));
        header.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    }
    TextView label(String s,float size,int colour){TextView t=new TextView(this);t.setText(s);t.setTextColor(colour);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(size));t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
    void group(String title){TextView t=label(title,18,SettingsGlass.GROUP);t.setLetterSpacing(.06f);t.setPadding(px(12),px(18),px(12),px(6));body.addView(t);column=body;}
    /** A raised translucent card that the following rows and text go into. */
    void card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(px(12),px(8),px(12),px(8));c.setBackground(SettingsGlass.card(px(18),prefs.getBoolean("highContrast",false)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=px(4);lp.bottomMargin=px(10);body.addView(c,lp);column=c;}
    LinearLayout.LayoutParams rowParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,px(4),0,px(4));return lp;}
    @Override Button button(String title,Runnable action){return row(title,null,false,action);}
    /** A focusable row: label on the left, an optional chip (a state actually known) and a chevron on the right. */
    Button row(String title,String chip,boolean chevron,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(26));b.setTextColor(SettingsGlass.textColours());b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setMinHeight(px(64));b.setMinimumHeight(px(64));b.setPadding(px(24),px(12),px(24),px(12));
        b.setBackground(SettingsGlass.row(px(14),Math.max(2,px(3)),prefs.getBoolean("highContrast",false),calm()?0:SettingsOverlay.FOCUS_MS));b.setStateListAnimator(null);b.setFocusable(true);b.setFocusableInTouchMode(true);
        if(chip!=null||chevron){b.setCompoundDrawablesRelative(null,null,new SettingsGlass.Trailing(chip,chevron,false,textPx(19)),null);b.setCompoundDrawablePadding(px(16));}
        b.setContentDescription(chip==null?title:title+", "+chip);
        b.setOnClickListener(v->action.run());column.addView(b,rowParams());
        if(autoFocusButtons&&getCurrentFocus()==null)b.requestFocus();
        return b;
    }
    /** Primary (white, 25 px) for a state line, secondary (blue-grey, 21 px) for notes, as Settings sizes them. */
    @Override TextView text(String s,int size){
        TextView t=new TextView(this);t.setText(s);boolean primary=size>=18;t.setTextColor(primary?SettingsGlass.TEXT:SettingsGlass.SECONDARY);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX,textPx(primary?25:21));t.setLineSpacing(0,1.18f);t.setPadding(px(12),px(8),px(12),px(8));column.addView(t);return t;
    }
    /** "Looking…" with a spinner: nothing is ever a blank wait. Hide its parent when done. */
    TextView busy(String words){
        LinearLayout line=new LinearLayout(this);line.setOrientation(LinearLayout.HORIZONTAL);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(px(12),px(8),px(12),px(8));
        ProgressBar spin=new ProgressBar(this,null,android.R.attr.progressBarStyleSmall);spin.setIndeterminate(true);spin.setContentDescription("Loading");line.addView(spin,new LinearLayout.LayoutParams(px(32),px(32)));
        TextView t=label(words,25,SettingsGlass.TEXT);t.setSingleLine(false);t.setPadding(px(16),0,0,0);line.addView(t,new LinearLayout.LayoutParams(0,-2,1));column.addView(line);return t;
    }
    boolean isInside(View v){for(View p=v;p!=null;p=p.getParent() instanceof View?(View)p.getParent():null)if(p==body||p==header)return true;return false;}
    static View firstFocusable(ViewGroup g){for(int i=0;i<g.getChildCount();i++){View c=g.getChildAt(i);if(c.getVisibility()!=View.VISIBLE)continue;if(c.isFocusable())return c;if(c instanceof ViewGroup){View f=firstFocusable((ViewGroup)c);if(f!=null)return f;}}return null;}
    /** Focus to the first control of the page, unless something in the sheet already has it. */
    void focusFirst(){body.post(()->{View now=getCurrentFocus();if(now!=null&&isInside(now)&&now.getParent()!=header)return;View f=firstFocusable(body);if(f!=null)f.requestFocus();});}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        // Left with nothing further left is Back, as in Settings.
        if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT&&e.getRepeatCount()==0){View f=getCurrentFocus();
            if(!(f instanceof EditText)&&(f==null||f.focusSearch(View.FOCUS_LEFT)==null||!isInside(f.focusSearch(View.FOCUS_LEFT)))){onBackPressed();return true;}}
        return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){close();}
}
