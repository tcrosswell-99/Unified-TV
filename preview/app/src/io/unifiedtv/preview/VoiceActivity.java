package io.unifiedtv.preview;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.TypedValue;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.ArrayList;

/**
 * "Ask your TV" (PRODUCT_SPEC §14 and the voice row of the design table; DESIGN_SYSTEM "tiny glyph at rest; short-lived
 * waveform and answer only when activated"; AT-16, AT-51): the pill's microphone expands into a small glass listening pane
 * under the pill, over a stationary Home (a translucent window, the same glass as Settings). No takeover, no chat feed, no
 * suggestion chips. OK on Speak starts listening; the microphone is live only while the pane says "Listening", with a
 * moving level; Back stops listening, and Back again (or the pane's own timeout) closes it, returning to Home exactly as
 * it was (same tile, same light). An app opened from here closes the pane behind it.
 *
 * Listening happens inside the pane through the TV's own speech service (Android's SpeechRecognizer, after the household
 * allows the microphone once). Where the TV has no such service, or it hears nothing (owner's TCL, 25 October 2026: "I
 * didn't catch that" twice; a remote's microphone often reaches only the TV's own voice screen), Speak falls back by
 * itself to the TV's own voice screen (ACTION_RECOGNIZE_SPEECH) and returns to the pane with its words. Once that has
 * worked where the pane heard nothing, this TV goes straight to its own voice screen next time.
 *
 * With no household assistant set up (Assistant), the pane offers "Set up your assistant on your phone"; local requests
 * work either way.
 *
 * Honest limits (AGENTS rule 1): only a few TV requests are understood here (VoiceCommand). General questions to an AI
 * assistant (OpenAI or Claude) are NOT connected: that needs the household's own protected relay first (PRODUCT_SPEC
 * §14, §24.6), and the pane says so in plain words. No microphone listens in the background and no key is bundled.
 */
public final class VoiceActivity extends Activity {
    static final int MIC = 31, SYSTEM_VOICE = 9;
    /** Listening stops by itself after this long, so a microphone is never left open (PRODUCT_SPEC §14). */
    static final long LISTEN_MS = 10_000;
    /** The pane closes by itself after this long without a key (PRODUCT_SPEC: "automatically remove the panel"). */
    static final long IDLE_MS = 45_000;
    static final String READY = "Say or type what you'd like.", LISTENING = "Listening…",
        LIMITS = "I can open an app, find a film or programme, play music, show the doorbell camera or switch profile. General questions aren't connected yet.",
        SPEECH = "Your TV's own speech service turns what you say into words.";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::close, stopListening = () -> stopListening(true);
    private SharedPreferences prefs; private float unit;
    private TextView state, answer; private EditText query; private Button speak; private Level level; private View pane;
    private SpeechRecognizer recogniser; private boolean listening;
    /** This press of Speak: the latest partial words heard, and whether the TV's own voice screen was already tried. */
    private String heard = ""; private boolean systemTried, paneFailed;
    static final String SYSTEM_FIRST = "voice.systemScreen", SET_UP = "Set up your assistant on your phone";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("unified-tv-preview", 0);
        unit = getResources().getDisplayMetrics().widthPixels / 1920f;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout root = new FrameLayout(this);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); pane = box;
        box.setPadding(px(30), px(24), px(30), px(26));
        box.setBackground(new Pane(px(30), solid(), prefs.getString("scene", "neutral")));
        // Header: the microphone (its state and level) and the name.
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        level = new Level(this); head.addView(level, new LinearLayout.LayoutParams(px(64), px(64)));
        TextView title = label("Ask your TV", 30, SettingsGlass.TEXT); title.setPadding(px(18), 0, 0, 0); head.addView(title);
        box.addView(head);
        state = label(READY, 26, SettingsGlass.TEXT); state.setPadding(0, px(18), 0, 0); box.addView(state);
        answer = label("", 21, SettingsGlass.SECONDARY); answer.setPadding(0, px(6), 0, 0); answer.setVisibility(View.GONE); box.addView(answer);
        query = new EditText(this); query.setHint("Or type it here"); query.setHintTextColor(0x99C3CEDD); query.setTextColor(SettingsGlass.TEXT);
        query.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(22)); query.setSingleLine(true); query.setSaveEnabled(false); query.setContentDescription("Type a request");
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); query.setImeOptions(EditorInfo.IME_ACTION_GO);
        query.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        query.setBackground(SettingsGlass.row(px(18), Math.max(2, px(2)), strong(), calm() ? 0 : SettingsOverlay.FOCUS_MS)); query.setPadding(px(20), px(12), px(20), px(12));
        query.setOnEditorActionListener((v, action, e) -> { ask(); return true; });
        LinearLayout.LayoutParams field = new LinearLayout.LayoutParams(-1, -2); field.setMargins(0, px(20), 0, 0); box.addView(query, field);
        LinearLayout actions = new LinearLayout(this);
        speak = button("Speak", this::speak); Button go = button("Ask", this::ask);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1); half.setMargins(0, 0, px(8), 0); actions.addView(speak, half);
        half = new LinearLayout.LayoutParams(0, -2, 1); half.setMargins(px(8), 0, 0, 0); actions.addView(go, half);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2); row.setMargins(0, px(14), 0, 0); box.addView(actions, row);
        if (!Assistant.configured(this)) {
            // OD-17: the assistant is set up on the phone; local requests keep working without it.
            Button setup = button(SET_UP, this::setUpAssistant); LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(-1, -2); wide.setMargins(0, px(14), 0, 0); box.addView(setup, wide);
        }
        TextView note = label(LIMITS + " " + (Assistant.configured(this) ? Assistant.SAVED_NOT_TESTED : Assistant.NOT_SET_UP) + " " + SPEECH, 16, SettingsGlass.GROUP); note.setPadding(0, px(16), 0, 0); box.addView(note);
        // Under the pill, right-aligned with it (Home's pill ends 103 px from the right at 1080p), never over the dock.
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(px(660), -2, Gravity.TOP | Gravity.END); at.setMargins(0, px(168), px(95), 0);
        root.addView(box, at);
        setContentView(root);
        speak.requestFocus();
        if (saved == null && !calm()) { box.setAlpha(0f); box.setScaleX(.94f); box.setScaleY(.94f); box.setPivotX(px(600)); box.setPivotY(0);
            box.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); }
        handler.postDelayed(idle, IDLE_MS);
    }
    private int px(float at1080) { return Math.round(at1080 * unit); }
    private boolean calm() { return prefs.getBoolean("reduceMotion", false) || !AmbientLayer.tvAnimations(); }
    private boolean strong() { return prefs.getBoolean("highContrast", false); }
    private boolean solid() { return prefs.getBoolean("opaque", false) || strong(); }
    private TextView label(String s, float size, int colour) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(colour); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setLineSpacing(0, 1.12f); return t;
    }
    private Button button(String s, Runnable action) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(22)); b.setTextColor(SettingsGlass.textColours());
        b.setBackground(SettingsGlass.row(px(18), Math.max(2, px(2)), strong(), calm() ? 0 : SettingsOverlay.FOCUS_MS)); b.setStateListAnimator(null);
        b.setMinHeight(px(60)); b.setMinimumHeight(px(60)); b.setFocusable(true); b.setOnClickListener(v -> action.run()); return b;
    }
    /** For tests: what the pane says now, and whether the microphone is live. */
    String stateText() { return state.getText().toString(); }
    String answerText() { return answer.getText().toString(); }
    boolean listening() { return listening; }
    View pane() { return pane; }

    @Override public void onUserInteraction() { super.onUserInteraction(); handler.removeCallbacks(idle); handler.postDelayed(idle, IDLE_MS); }
    /** Back closes the microphone first (it is the one thing live), then the pane: one layer per press. */
    @Override public void onBackPressed() { if (listening) { stopListening(false); show(READY, ""); } else close(); }
    private void close() { stopListening(false); finish(); overridePendingTransition(0, 0); }
    @Override protected void onPause() { stopListening(false); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); if (recogniser != null) { recogniser.destroy(); recogniser = null; } super.onDestroy(); }

    private void show(String now, String more) {
        state.setText(now); answer.setText(more); answer.setVisibility(more == null || more.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // --- Listening
    private void setUpAssistant() {
        try { startActivity(new Intent(this, SettingsActivity.class).putExtra("task", "phone-" + PhoneService.ASSISTANT)); } catch (RuntimeException ignored) {}
        close();
    }
    /**
     * Errors after which the TV's own voice screen is tried: nothing matched, nothing heard, no audio or a client fault.
     * Permission and busy errors are explained instead.
     */
    static boolean fallBack(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT;
    }
    /** What to use from a listen: its final words, else the last partial words heard, else "" (then fall back). */
    static String words(String result, String partial) { return result != null && !result.trim().isEmpty() ? result.trim() : partial == null ? "" : partial.trim(); }
    private void speak() {
        if (listening) { stopListening(true); return; }
        heard = ""; systemTried = false; paneFailed = false;
        if (prefs.getBoolean(SYSTEM_FIRST, false)) { systemVoice(); return; }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC); return; }
            listen();
        } else systemVoice();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
        else show("The microphone isn't allowed for Unified TV.", "Type your request instead, or allow the microphone in the TV's app settings.");
    }
    private void listen() {
        try {
            if (recogniser == null) { recogniser = SpeechRecognizer.createSpeechRecognizer(this); recogniser.setRecognitionListener(listener); }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1).putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            recogniser.startListening(i);
            listening = true; level.listening(true, calm()); speak.setText("Stop"); show(LISTENING, "");
            handler.removeCallbacks(stopListening); handler.postDelayed(stopListening, LISTEN_MS);
        } catch (RuntimeException e) { listening = false; level.listening(false, calm()); systemVoice(); }
    }
    /** Ends listening; with {@code finishWords}, whatever was heard so far is still used. */
    private void stopListening(boolean finishWords) {
        handler.removeCallbacks(stopListening);
        if (!listening) return;
        listening = false; level.listening(false, calm()); speak.setText("Speak");
        if (recogniser != null) try { if (finishWords) recogniser.stopListening(); else recogniser.cancel(); } catch (RuntimeException ignored) {}
    }
    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle b) { if (listening) show(LISTENING, ""); }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float db) { level.level(db); }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { if (listening) { stopListening(true); show("One moment…", ""); } }
        @Override public void onError(int error) {
            stopListening(false);
            String partial = words(null, heard);
            if (!partial.isEmpty() && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { use(partial); return; }
            if (fallBack(error) && !systemTried) { paneFailed = true; systemVoice(); return; }
            show(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "I didn't catch that." : "Listening isn't working right now.",
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "Allow the microphone for Unified TV in the TV's app settings, or type your request." : "Press Speak to try again, or type your request.");
        }
        @Override public void onResults(Bundle b) {
            stopListening(false); String words = words(first(b), heard);
            if (!words.isEmpty()) { use(words); return; }
            if (!systemTried) { paneFailed = true; systemVoice(); return; }
            show("I didn't catch that.", "Press Speak to try again, or type your request.");
        }
        @Override public void onPartialResults(Bundle b) { String words = first(b); if (!words.isEmpty()) heard = words; if (!words.isEmpty() && listening) show("“" + words + "”", ""); }
        @Override public void onEvent(int type, Bundle b) {}
    };
    private static String first(Bundle b) { ArrayList<String> words = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); return words == null || words.isEmpty() || words.get(0) == null ? "" : words.get(0).trim(); }
    private void use(String words) { query.setText(words); ask(); }
    /** No speech service inside the pane, or it heard nothing: the TV's own voice screen, which then returns here. */
    private void systemVoice() {
        systemTried = true; show("Listening with your TV's voice screen…", "");
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try { startActivityForResult(i, SYSTEM_VOICE); } catch (RuntimeException e) { show("This TV has no voice input for apps.", "Type your request with the remote instead."); }
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != SYSTEM_VOICE) return;
        handler.removeCallbacks(idle); handler.postDelayed(idle, IDLE_MS);   // back over Home, the pane's own time starts again
        ArrayList<String> got = result == RESULT_OK && data != null ? data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) : null;
        String words = got == null || got.isEmpty() || got.get(0) == null ? "" : got.get(0).trim();
        if (words.isEmpty()) { show("I didn't catch that.", "Press Speak to try again, or type your request."); return; }
        // The pane heard nothing but the TV's own screen did: on this TV, go straight there next time.
        if (paneFailed) prefs.edit().putBoolean(SYSTEM_FIRST, true).apply();
        use(words);
    }

    // --- Requests (VoiceCommand: an allowlist; nothing unrecognised acquires any authority)
    void ask() {
        String text = query.getText().toString().trim().replaceAll("[.!?]+$", "").trim(); if (text.isEmpty()) return;
        String command = VoiceCommand.classify(text);
        state.setText("“" + text + "”");
        switch (command) {
            case "live": {
                // The same gate as the dock and Apps (OD-26's rule, then the Kids list): voice is never a way round it.
                String why = Launch.open(this, LiveTvProtection.livePackage(this), "Live TV");
                if (why == null) close(); else show("“" + text + "”", why.isEmpty() ? LiveTvProtection.message(LiveTvProtection.now(this)) : why);
                return; }
            case "profile": Profiles.choose(this); return;
            case "camera": startService(new Intent(this, HouseholdService.class).setAction("camera")); show("“" + text + "”", "Asking for the doorbell camera. It appears once the doorbell is set up in Settings."); return;
            case "find": startActivity(new Intent(this, LibraryActivity.class).putExtra("search", text.substring(5).trim())); close(); return;
            case "music": music(text); return;
            default: break;
        }
        String pkg = command.equals("bbc") ? "bbc.iplayer.android.tv" : command.equals("netflix") ? "com.netflix.ninja" : command.equals("disney") ? "com.disney.disneyplus" : "";
        if (!pkg.isEmpty()) {
            String name = command.equals("bbc") ? "BBC iPlayer" : command.equals("netflix") ? "Netflix" : "Disney+";
            // The same gate as the dock and Apps (Live TV protection and the Kids list): voice is never a way round it.
            if (getPackageManager().getLeanbackLaunchIntentForPackage(pkg) == null && getPackageManager().getLaunchIntentForPackage(pkg) == null) { show("“" + text + "”", name + " isn't on this TV. Open Apps to find what is."); return; }
            String why = Launch.open(this, pkg, name);
            if (why == null) close(); else if (!why.isEmpty()) show("“" + text + "”", why);
            return;
        }
        show("“" + text + "”", "That isn't something I can do yet. Try “Open BBC”, “Open Netflix”, “Play” and an artist, “Show camera”, “Switch profile”, or “Find” and a title. General questions aren't connected yet.");
    }

    /**
     * "Play …" (OD-27): Amazon Music on this TV through its own media controls (AmazonMusic), or on an Echo through Home
     * Assistant (Echoes). Saying where ("everywhere", "on the kitchen Echo", "on this TV") also becomes this profile's Play
     * on choice. Echo speakers keep their Kids rule; the network is never touched on the main thread.
     */
    private void music(String text) {
        VoiceCommand.Music m = VoiceCommand.music(text); if (m == null) return;
        String said = "“" + text + "”", place = m.place.isEmpty() ? Echoes.target(prefs) : m.place;
        if (Echoes.TV.equals(place)) {
            if (!m.place.isEmpty()) Echoes.choose(prefs, Echoes.TV, "");
            AmazonMusic.Result r = AmazonMusic.play(this, m.query, m.focus);
            if (r.words.isEmpty()) close(); else show(said, r.words);
            return;
        }
        if (!Echoes.allowed(prefs)) { show(said, Echoes.KIDS); return; }
        show(said, "Asking your Echo for “" + m.query + "”…");
        android.content.Context app = getApplicationContext();
        Thread t = new Thread(() -> {
            Echoes.Found f = Echoes.finder.find(app); String target = place, name = Echoes.EVERYWHERE.equals(place) ? "Everywhere" : place, no = f.problem;
            if (no == null && !Echoes.EVERYWHERE.equals(place) && !place.matches(Echoes.ENTITY)) {
                Echoes.Echo e = Echoes.named(f.echoes, place);
                if (e == null) no = "I couldn't find an Echo called “" + place + "”."; else { target = e.id; name = e.name; }
            }
            if (no == null) no = Echoes.play(app, Echoes.ha(app), target, m.query, f);
            final String problem = no, chosen = target, chosenName = name;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (problem != null) { show(said, problem); return; }
                Echoes.choose(prefs, chosen, chosenName);
                startActivity(NowPlayingActivity.waiting(this, m.query, m.focus, AmazonMusic.Route.ECHO, ""));
                close();
            });
        }, "voice-echo");
        t.setDaemon(true); t.start();
    }

    /** The pane's glass: a soft still of Home's own light, a dark blue-charcoal tint, a top sheen, rim and hairline edge (as Settings). */
    static final class Pane extends Drawable {
        private final float radius; private final boolean solid; private final String scene;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path(); private final RectF box = new RectF(); private Shader tint, sheen; private Bitmap frost;
        Pane(float radius, boolean solid, String scene) { this.radius = radius; this.solid = solid; this.scene = scene; line.setStyle(Paint.Style.STROKE); }
        @Override protected void onBoundsChange(Rect b) {
            box.set(b); shape.reset(); shape.addRoundRect(box, radius, radius, Path.Direction.CW);
            tint = new LinearGradient(0, b.top, 0, b.bottom, solid ? 0xF2111A28 : 0xB8121B2A, solid ? 0xF50B1220 : 0xC80B1220, Shader.TileMode.CLAMP);
            sheen = new LinearGradient(0, b.top, 0, b.top + b.height() * .3f, solid ? 0x08FFFFFF : 0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            if (!solid && frost == null) { frost = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888); AmbientMesh.still(new Canvas(frost), 96, 64, scene); }
        }
        @Override public void draw(Canvas c) {
            if (box.isEmpty()) return;
            c.save(); c.clipPath(shape);
            if (frost != null) { fill.setShader(null); fill.setAlpha(110); c.drawBitmap(frost, null, box, fill); fill.setAlpha(255); }
            fill.setShader(tint); c.drawRect(box, fill); fill.setShader(sheen); c.drawRect(box.left, box.top, box.right, box.top + box.height() * .3f, fill); fill.setShader(null);
            c.restore();
            line.setStrokeWidth(solid ? 2.5f : 1.5f); line.setColor(solid ? 0xE6FFFFFF : SettingsGlass.EDGE); c.drawRoundRect(box, radius, radius, line);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * The microphone and its state: at rest an outline microphone in a faint ring; listening, a filled white disc with five
     * level bars that follow the voice (understated; still bars with Reduce motion). It moves only while listening.
     */
    static final class Level extends View {
        private static final float[] SHAPE = {.55f, .85f, 1f, .8f, .5f};
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF r = new RectF();
        private boolean on, still; private float target, shown;
        Level(android.content.Context c) { super(c); setFocusable(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); p.setStrokeCap(Paint.Cap.ROUND); }
        void listening(boolean now, boolean calm) { on = now; still = calm; target = shown = 0; invalidate(); }
        /** The recogniser's level in dB (about -2 to 10): eased, so the bars move calmly. */
        void level(float db) { target = Math.max(0f, Math.min(1f, (db + 2f) / 12f)); }
        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, s = Math.min(w, h) / 64f;
            if (on) {
                p.setStyle(Paint.Style.FILL); p.setColor(0xFFF4F7FB); c.drawCircle(cx, cy, 31 * s, p);
                p.setColor(0xFF0D1522); shown += (target - shown) * .35f;
                for (int i = 0; i < 5; i++) {
                    float k = still ? .45f : Math.max(.18f, shown * SHAPE[i]);
                    float half = 15 * s * k + 3 * s, x = cx + (i - 2) * 8 * s;
                    p.setStrokeWidth(4.5f * s); p.setStyle(Paint.Style.STROKE); c.drawLine(x, cy - half, x, cy + half, p);
                }
                if (!still) postInvalidateDelayed(40);   // only while listening: never an endless animation
                return;
            }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * s); p.setColor(0x59FFFFFF); c.drawCircle(cx, cy, 30 * s, p);
            p.setColor(0xFFF4F7FB); p.setStrokeWidth(3.2f * s);
            r.set(cx - 7 * s, cy - 18 * s, cx + 7 * s, cy + 4 * s); c.drawRoundRect(r, 7 * s, 7 * s, p);
            r.set(cx - 13 * s, cy - 8 * s, cx + 13 * s, cy + 12 * s); c.drawArc(r, 0, 180, false, p);
            c.drawLine(cx, cy + 12 * s, cx, cy + 19 * s, p); c.drawLine(cx - 7 * s, cy + 19 * s, cx + 7 * s, cy + 19 * s, p);
        }
    }
}
