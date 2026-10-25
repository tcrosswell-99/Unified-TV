package io.unifiedtv.preview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unified TV visual preview, deliberately NOT registered as Android's HOME activity.
 *
 * First milestone: neutral/coastal Home, brand-aware focus atmosphere, readable glass dock,
 * installed-app picker, remote-only pin/reorder/remove and saved per-device shortcuts.
 * No Ring, VPN tunnel control, cleanup, NAS or voice capabilities are claimed by this preview.
 */
public final class UnifiedPreviewActivity extends Activity {
    private Preview surface;
    private android.net.ConnectivityManager.NetworkCallback vpnWatcher;
    @Override protected void onStart(){super.onStart();LocalDiscovery.prefetch(this);AirPlayReceiver.checkedAt=0;AirPlayReceiver.refresh(this,()->{if(surface!=null)surface.postInvalidate();});VpnGate.invalidate();vpnWatcher=VpnGate.watch(this,()->{if(surface!=null)surface.invalidate();});}
    @Override protected void onStop(){VpnGate.unwatch(this,vpnWatcher);vpnWatcher=null;super.onStop();}
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    /** Idle on Home: the Wallpaper (spec 33; artwork by default), or with Wallpaper off, the owner's gallery if one is set. */
    private boolean wallpaperOn() { return !WallpaperPolicy.OFF.equals(WallpaperPolicy.source(getSharedPreferences("unified-tv-preview",0).getString("wallpaperSource",null))); }
    private final Runnable gallery = () -> {
        if (wallpaperOn()) { startActivity(new Intent(this, WallpaperActivity.class)); return; }
        try { if (new Vault(this).read("gallery").optJSONArray("images") != null)
            startActivity(new Intent(this, GalleryActivity.class)); } catch (Exception ignored) {}
    };
    @Override public void onUserInteraction() { super.onUserInteraction(); idleHandler.removeCallbacks(gallery);
        int minutes = wallpaperOn() ? WallpaperPolicy.idle(getSharedPreferences("unified-tv-preview",0).getInt("wallpaperIdle",-1)) : 5;
        if (minutes > 0) idleHandler.postDelayed(gallery, minutes * 60_000L); }
    @Override protected void onResume() { super.onResume(); onUserInteraction(); if(surface!=null){surface.refresh();if(surface.layer!=null)surface.layer.resume();}
        LiveTvGuard.stop(this);
        if(getIntent().getBooleanExtra("liveTvPaused",false)){getIntent().removeExtra("liveTvPaused");VpnGate.show(this,"Live TV paused. NordVPN disconnected, and Android has stopped Live TV's connection until it reconnects.");}
        guarded();
    }
    /** The Kids app guard brought Home back: say why, briefly and kindly, with the dock as it was. */
    private void guarded(){
        if(surface==null||!getIntent().hasExtra("kidsGuard"))return;String app=getIntent().getStringExtra("kidsGuard");getIntent().removeExtra("kidsGuard");
        surface.home();surface.info((app==null||app.isEmpty()?"That app":app)+" isn't on the Kids list. Ask an adult.");
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent);
        // As the chosen Home: the Home button lands on the main screen with the same app still in focus (spec 28.3).
        if (intent.hasCategory(Intent.CATEGORY_HOME) && surface != null) surface.home(); }
    // (A return from the Kids app guard arrives here too and is explained in onResume.)
    @Override protected void onPause() { idleHandler.removeCallbacks(gallery); if(surface!=null)surface.pause(); super.onPause(); }


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        HouseholdSettings.ensure(this);   // before Home writes anything: an earlier install keeps exactly what it had
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        surface = new Preview(this);
        // Back to front: the compositor-moved app light, then Home itself (transparent where the light shows through).
        AmbientLayer layer = new AmbientLayer(this);
        android.widget.FrameLayout stack = new android.widget.FrameLayout(this);
        stack.addView(layer); stack.addView(surface);
        setContentView(stack);
        surface.layer = layer; surface.aim(surface.currentScene(), false);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (hasWindowFocus() && surface != null && surface.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onDestroy() {
        idleHandler.removeCallbacksAndMessages(null);
        if (surface != null) surface.close();
        super.onDestroy();
    }

    private static final class Item {
        final String id, name, pkg;
        final int top, bottom;
        final Drawable icon;
        Item(String id, String name, String pkg, int top, int bottom, Drawable icon) {
            this.id = id; this.name = name; this.pkg = pkg;
            this.top = top; this.bottom = bottom; this.icon = icon;
        }
    }

    private static final class Preview extends View {
        private static final int W = 1920, H = 1080;
        private static final int WHITE = 0xFFFAFAFC;
        private static final int MUTED = 0xFFC8CCD5;
        private static final long HOLD_MS = 550, BG_WAIT_MS = 135, BG_FADE_MS = 400, SHELF_MS = 320, TITLE_MS = 160, TILE_MS = 185;
        private Activity activity;
        private final SharedPreferences prefs;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint type = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF rect = new RectF();
        private final List<Item> installed = new ArrayList<>();
        private final List<Item> dock = new ArrayList<>();
        private List<Item> beforeMove;
        private Bitmap coast;
        private int index = -1, appIndex = 0, menuIndex = 0, lifted = -1;
        private boolean apps = false, menu = false, moving = false;
        /**
         * The utility pill (owner, 25 October 2026; v3.4.4 UX-UTILITY, NAV-02): a single Up from the dock focuses its first
         * icon; Left/Right move across it; OK opens the focused utility directly (no menu); Down or Back returns to the same
         * tile. Which icons, in what order: HomeUtilities, from the household's Navigation & Shortcuts setting.
         */
        private boolean header = false; private int headerIndex = 0;
        private List<String> pill = HomeUtilities.DEFAULT;
        /** Read on refresh, not per frame: AirPin LITE's package (null if absent) and whether NordVPN is installed. */
        private String airPin; private boolean nordVpn;
        /** The household's clock and the alarm switch, refreshed each minute and on return to Home. */
        private java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        private java.time.format.DateTimeFormatter clockFormat = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        private boolean alarmsOff;
        private void household() {
            zone = HouseholdSettings.zone(activity); clockFormat = HouseholdSettings.clock(activity);
            alarmsOff = AlarmWatch.homeNotice(prefs.getBoolean(AlarmWatch.SHOWN, true), prefs.getBoolean(AlarmWatch.STOPPED, false));
            team = FixtureFeed.team(activity); track = FixtureFeed.track(activity);
        }
        private void utilities() {
            airPin = AirPlayReceiver.installed(activity);
            nordVpn = false; for (Item x : installed) if (LiveTvProtection.NORDVPN.equals(x.pkg)) nordVpn = true;
            List<String> next = HomeUtilities.items(HomeUtilities.order(prefs.getString(HomeUtilities.PREF, "")), airPin != null, nordVpn);
            headerIndex = HomeUtilities.keep(pill, headerIndex, next); pill = next;
        }
        private String labels() { return DockLabels.value(prefs.getString(DockLabels.key(Profiles.current(prefs)), DockLabels.FOCUSED_ONLY)); }
        private boolean okDown, held;
        private long focusAt, fadeAt;
        // Spec 32: an app's scene follows focus at once and crossfades in 400 ms; a title's poster scene waits for a short
        // settle (spec 4.4), because its artwork may still be decoding. The scene is an app id, "poster:<id>" or "neutral".
        private final Backdrops backdrops = new Backdrops();
        /** The compositor-moved app light behind this view (set by the activity); null in tests without it. */
        AmbientLayer layer;
        /** Points the light behind Home at this app; Lightweight hides it and the cached still frames are drawn instead. */
        void aim(String key, boolean animate) {
            if (layer == null) return;
            layer.setVisibility(light() ? GONE : VISIBLE);
            if (!light() && app(key)) layer.show(key, animate && fadeMs() > 0);
            layer.drift(drifting());
        }
        private String scene = "neutral", formerScene = null, sceneTitle = "";
        private long titleAt = 0;
        private boolean titleHidden = false;
        // Spec 4.6: Down raises a row of the household's own titles within Home; Up/Back return to the same app.
        private boolean shelf = false, postersLoading = false, libraryReady = false;
        private long shelfAt = 0, postersAt = 0;
        private int posterIndex = 0;
        private List<HomeShelf.Poster> posters = new ArrayList<>();
        private final java.util.Map<String, Bitmap> posterArt = java.util.Collections.synchronizedMap(new java.util.HashMap<>());
        private final java.util.concurrent.ExecutorService shelfWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
        private String message = "";
        private long messageUntil = 0;
        private long inputEvent;
        /**
         * Spec 7: the household team's fixtures (FixtureFeed: API-Football or the Home Assistant calendar, each with its own
         * refresh rules); the card itself is worked out each minute from them. No team and no calendar: no card.
         */
        private List<Fixtures.Fixture> fixtures = new ArrayList<>();
        private Fixtures.Team team; private org.json.JSONObject track = new org.json.JSONObject();
        private FixturePolicy.Card matchday() { return FixturePolicy.first(fixtures, java.time.Instant.now(), zone, clockFormat, team, track); }
        private long fixtureFetched;
        private final java.util.concurrent.ExecutorService fixtureWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
        private void fixture(){
            long now=System.currentTimeMillis();if(now-fixtureFetched<300000)return;fixtureFetched=now;
            try{fixtureWorker.execute(()->{
                List<Fixtures.Fixture> found;try{found=FixtureFeed.refresh(activity.getApplicationContext());}catch(Exception e){found=new ArrayList<>();}   // unreachable: the last read, never a guess
                final List<Fixtures.Fixture> result=found;final org.json.JSONObject moved=FixtureFeed.track(activity);handler.post(()->{fixtures=result;track=moved;invalidate();});
            });}catch(java.util.concurrent.RejectedExecutionException ignored){}
        }
        private final Runnable hold = () -> {
            if (okDown && !held) {
                held = true;
                if (!header && index >= 0 && index < dock.size()) { EditLock.ask(activity, () -> { menu = true; menuIndex = 0; invalidate(); }); }
            }
        };
        private final Runnable clockTick = new Runnable() {
            @Override public void run() {
                household();
                invalidate();
                fixture();
                AirPlayReceiver.refresh(activity, Preview.this::postInvalidate);
                casting();
                handler.postDelayed(this, 60_000L);
            }
        };
        private final Runnable commitFocus = () -> {
            if (apps || menu || moving) return;
            if (shelf) {
                if (posterIndex < posters.size()) { HomeShelf.Poster p = posters.get(posterIndex); commitScene("poster:" + p.id, p.label()); }
                else commitScene("neutral", libraryReady ? "View all your films and TV" : Profiles.kids(getContext()) ? "Ask an adult to choose Kids folders" : "Set up Your Films & TV");
            } else if (index >= 0 && index < dock.size()) commitScene(dock.get(index).id, dock.get(index).name);
            else commitScene("neutral", "");
        };

        /** One dissolve at a time; a new commit mid-dissolve keeps whichever scene is most visible, so nothing jumps. */
        private void commitScene(String key, String title) {
            long now = SystemClock.uptimeMillis();
            boolean fading = fadeAt != 0 && now - fadeAt < fadeMs();
            if (!key.equals(scene)) {
                if (!(fading && (now - fadeAt) < fadeMs() / 2 && formerScene != null)) formerScene = scene;
                scene = key; fadeAt = fadeMs() == 0 ? 0 : now; aim(key, true);
                if (!key.startsWith("poster:")) prefs.edit().putString("scene", key).apply();
            }
            // The dock's title, halo and light change together (spec 32.6); a title's name waits for its scene.
            sceneTitle = title; titleHidden = false; titleAt = now + (fadeMs() == 0 || app(key) ? 0 : fadeMs() / 3);
            speak(title);
            invalidate();
        }
        /**
         * Spec 4.11: Home is one drawn view, so a screen reader hears the focused item by name (and its place in the
         * dock) whenever focus settles. Only when accessibility is on; nothing is announced otherwise.
         */
        private String spoken = "";
        private void speak(String title) {
            android.view.accessibility.AccessibilityManager am = (android.view.accessibility.AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled() || title == null || title.isEmpty()) return;
            String words = title;
            if (header && headerIndex >= 0 && headerIndex < pill.size()) words = HomeUtilities.label(pill.get(headerIndex), VpnGate.statusLabel(VpnGate.current()), AirPlayReceiver.ready) + ". Press OK.";
            else if (!shelf && index >= 0 && index < dock.size()) words = title + ", " + (index + 1) + " of " + dock.size() + ". Hold OK to edit.";
            else if (shelf) words = title + (posterIndex < posters.size() ? ", " + (posterIndex + 1) + " of " + posters.size() : "");
            if (words.equals(spoken)) return;
            spoken = words; setContentDescription(words); announceForAccessibility(words);
        }
        private boolean calm() { return prefs.getBoolean("reduceMotion", false); }
        private long lastKey = SystemClock.uptimeMillis();
        /** OLED care applies in the Sony room (spec 2, TV-SONY). */
        private boolean oledRoom() { return "sony".equals(prefs.getString("room", Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony") ? "sony" : "tcl")); }
        private float oledDim(long now) { return oledRoom() ? DayMood.oledDim(now - lastKey) : 0; }
        private boolean light() { return prefs.getBoolean("lightweight", false); }
        private long fadeMs() { return calm() ? 0 : light() ? 200 : BG_FADE_MS; }
        private static boolean app(String key) { return !key.equals("neutral") && !key.startsWith("poster:"); }
        /** Slow drifting light (spec 32.1), off with reduced motion, lightweight rendering or a still background. */
        private boolean drifting() { return !calm() && !light() && !prefs.getBoolean("stillBackground", false); }
        /** A focus move: the tile answers at once, the old title steps back, and the scene follows after the settle. */
        private void moved() {
            focusAt = SystemClock.uptimeMillis(); titleHidden = true;
            handler.removeCallbacks(commitFocus); handler.postDelayed(commitFocus, shelf ? BG_WAIT_MS : 0);
        }

        Preview(Activity a) {
            super(a);
            activity = a;
            prefs = a.getSharedPreferences("unified-tv-preview", Context.MODE_PRIVATE);
            setFocusable(true);
            setFocusableInTouchMode(true);
            scanInstalled();
            loadDock();
            household(); utilities();
            handler.postDelayed(clockTick, 60_000L);
            try (InputStream in = a.getAssets().open("unified-preview-coast.png")) {
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 1;
                coast = BitmapFactory.decodeStream(in, null, opt);
                MaterialScene.wallpaper(coast);
            } catch (Exception ignored) { /* Original illustrated gradient is the safe offline fallback. */ }
            int saved = prefs.getInt("focus", 0);
            index = dock.isEmpty() ? -1 : Math.max(0, Math.min(dock.size()-1,saved));
            if(index>=0){scene=dock.get(index).id;sceneTitle=dock.get(index).name;}
            prefs.edit().putString("scene",scene).apply();
            setContentDescription("Unified TV Home. Left and Right choose apps. Up reaches the utilities and Settings. Down reveals your films and TV. Hold OK to edit.");
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(2);
        }

        void refresh() {
            handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,60000);
            fixtureFetched=0;fixture();
            if(moving)goBack();
            installed.clear();dock.clear();scanInstalled();loadDock();household();utilities();
            index=dock.isEmpty()?-1:Math.max(0,Math.min(dock.size()-1,prefs.getInt("focus",0)));
            if(!shelf){scene=index<0?"neutral":dock.get(index).id;sceneTitle=index<0?"":dock.get(index).name;titleHidden=false;titleAt=0;}
            aim(scene,false);
            formerScene=null;fadeAt=0;if(shelf)loadPosters();invalidate();
        }
        void home() {
            handler.removeCallbacks(hold); okDown = false; held = false; menu = false; apps = false; header = false;
            if (moving) goBack();
            if (shelf) { shelf = false; shelfAt = 0; }
            messageUntil = 0; moved(); invalidate();
        }
        String currentScene() { return scene; }
        void pause() {
            if (layer != null) layer.pause();
            handler.removeCallbacks(clockTick);handler.removeCallbacks(commitFocus);
            handler.removeCallbacks(hold); okDown=false;held=false;
            if(moving) goBack();
            prefs.edit().putInt("focus",Math.max(0,index)).apply();
        }
        void close() {
            fixtureWorker.shutdownNow();shelfWorker.shutdownNow();
            handler.removeCallbacksAndMessages(null);
            handler.removeCallbacks(hold);
            handler.removeCallbacks(commitFocus);
            handler.removeCallbacks(clockTick);
            if (coast != null) { coast.recycle(); coast = null; }
        }

        private void scanInstalled() {
            PackageManager pm = activity.getPackageManager();
            List<ResolveInfo> all = new ArrayList<>();
            for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent q = new Intent(Intent.ACTION_MAIN);
                q.addCategory(category);
                try { all.addAll(pm.queryIntentActivities(q, 0)); } catch (RuntimeException ignored) {}
            }
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : all) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName()) || !seen.add(pkg)) continue;
                String label;
                Drawable icon;
                try { label = String.valueOf(ri.loadLabel(pm)); icon = pm.getApplicationIcon(pkg); }
                catch (Exception e) { continue; }
                if (label.trim().isEmpty()) label = pkg;
                installed.add(new Item(pkg, label, pkg, 0xFF323E53, 0xFF161D2D, icon));
            }
            Collections.sort(installed, Comparator.comparing(it -> it.name.toLowerCase(Locale.ROOT)));
            List<Item> defaults=new ArrayList<>();
            for(String id:DockPolicy.KNOWN) defaults.add(defaultItem(id));
            defaults.add(defaultItem(DockPolicy.LIBRARY));
            if(Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")) defaults.add(defaultItem("xbox"));
            if(prefs.getString("room","").equals("frame")) defaults.add(defaultItem("tvplus"));
            Set<String> canonical=new HashSet<>();for(Item item:defaults)if(item.pkg!=null)canonical.add(item.pkg);
            installed.removeIf(item->canonical.contains(item.pkg));installed.addAll(0,defaults);
        }

        private Item installedMatch(String[] exact, String query) {
            for (String pkg : exact) for (Item x : installed) if (pkg.equals(x.pkg)) return x;
            for (Item x : installed) if (x.name.toLowerCase(Locale.ROOT).contains(query)) return x;
            return null;
        }

        private Item favourite(String id, String label, String[] packages, String query, int c1, int c2) {
            Item found = installedMatch(packages, query);
            return new Item(id, label, found == null ? null : found.pkg, c1, c2,
                    found == null ? null : found.icon);
        }

        private Item defaultItem(String id) {
            switch (id) {
                case "smartutube": return favourite(id, "SmartTube",
                        DockPolicy.packages("smartutube"), "smarttube",
                        0xFF45434A, 0xFF1E1E27);
                case "netflix": return favourite(id, "Netflix",
                        DockPolicy.packages("netflix"), "netflix", 0xFF28242B, 0xFF07090C);
                case "disney": return favourite(id, "Disney+",
                        DockPolicy.packages("disney"), "disney", 0xFF15356F, 0xFF071128);
                case "bbc": return favourite(id, "BBC",
                        DockPolicy.packages("bbc"), "iplayer",
                        0xFF494A51, 0xFF25262A);
                case "live": return new Item(id, "Live TV", null, 0xFF6D45A3, 0xFF27194F, null);
                case "music": return favourite(id, "Music",
                        DockPolicy.packages("music"), "amazon music",
                        0xFFE85C75, 0xFF8E2548);
                case "xbox": return new Item(id, "Xbox", null, 0xFF14783F, 0xFF07351E, null);
                case "tvplus": return new Item(id, "Samsung TV Plus", null, 0xFF154477, 0xFF07192F, null);
                case DockPolicy.LIBRARY: return new Item(id, "Library", null, 0xFF3A4A60, 0xFF141B27, null);
                default:
                    for (Item x : installed) if (x.id.equals(id)) return x;
                    return null;
            }
        }

        private void loadDock() {
            String room = prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sony")?"sony":"tcl");
            // Never edited: suggestions from the apps on this TV (SET-011). Edited: exactly the household's list.
            String saved=prefs.contains("dock")?prefs.getString("dock",""):null;List<String> suggested=new ArrayList<>();
            if(saved==null){Set<String> pkgs=new HashSet<>();for(Item x:installed)if(x.pkg!=null)pkgs.add(x.pkg);suggested=DockPolicy.suggest(room,pkgs,!LiveTvProtection.livePackage(activity).isEmpty());}
            for(String id:DockPolicy.order(room,saved,suggested)) {
                Item item=defaultItem(id);if(item!=null)dock.add(item);
            }
        }

        private void saveDock() {
            StringBuilder out = new StringBuilder();
            for (Item item : dock) {
                if (out.length() != 0) out.append(',');
                out.append(item.id);
            }
            prefs.edit().putString("dock", out.toString()).putInt("focus", index).apply();
        }

        boolean key(KeyEvent e) {
            if(e.getAction()==KeyEvent.ACTION_DOWN){inputEvent=e.getEventTime();boolean dimmed=oledDim(SystemClock.uptimeMillis())>0;lastKey=SystemClock.uptimeMillis();if(dimmed)invalidate();}
            int code = e.getKeyCode();
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT &&
                code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN &&
                code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER &&
                code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_MENU) return false;
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                if (down && e.getRepeatCount() == 0) {
                    okDown = true;
                    held = false;
                    handler.removeCallbacks(hold);
                    if (!moving && !menu && !shelf && !header) handler.postDelayed(hold, HOLD_MS);
                } else if (!down) {
                    handler.removeCallbacks(hold);
                    if (okDown && !held && !e.isCanceled()) confirm();
                    okDown = false;
                    held = false;
                }
                return true;
            }
            if (!down) return true;
            handler.removeCallbacks(hold);
            if (shelf) {
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_UP) closeShelf();
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = Math.max(0, Math.min(posters.size(), posterIndex + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    if (next != posterIndex) { posterIndex = next; shelfMoved = true; moved(); announceForAccessibility(posterIndex < posters.size() ? posters.get(posterIndex).title : "View all"); }
                }
                invalidate(); return true;
            }
            if (header) {
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_DOWN) { header = false; moved(); }
                else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int next = HomeUtilities.step(headerIndex, code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1, pill.size());
                    if (next != headerIndex) { headerIndex = next; speak(sceneTitle); }
                }
                invalidate(); return true;
            }
            if (code == KeyEvent.KEYCODE_BACK) { goBack(); return true; }
            // Menu opens every app directly (there is no Home menu any more: NAV-02).
            if (code == KeyEvent.KEYCODE_MENU) { if (!moving && !menu) activity.startActivity(new Intent(activity, AppsActivity.class)); return true; }
            if (menu) { int n = MENU.length;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) menuIndex = (menuIndex + n - 1) % n;
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) menuIndex = (menuIndex + 1) % n;
                invalidate(); return true; }
            if (moving) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) shift(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) shift(1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) { if (!pill.isEmpty()) { header = true; headerIndex = HomeUtilities.landing(pill); speak(sceneTitle); } }
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) openShelf();
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!dock.isEmpty()) {
                    int delta = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    index = index < 0 ? 0 : Math.max(0, Math.min(dock.size() - 1, index + delta));
                    announceForAccessibility(dock.get(index).name);
                    moved();
                }
            }
            invalidate();
            return true;
        }

        private void confirm() {
            if (shelf) { openEntry(); return; }
            if (header) { if (headerIndex >= 0 && headerIndex < pill.size()) utility(pill.get(headerIndex)); invalidate(); return; }
            if (menu) {
                menu = false;
                if (menuIndex == 0) { moving = true; beforeMove = new ArrayList<>(dock); lifted = index; }
                else if (menuIndex == 1 && index >= 0 && index < dock.size()) {
                    // Removing never uninstalls; the app stays in Apps and can be added back.
                    String name = dock.get(index).name; dock.remove(index); saveDock();
                    index = dock.isEmpty() ? -1 : Math.min(index, dock.size() - 1); moved(); info(name + " removed from Home");
                } else if (menuIndex == 2 && index >= 0 && index < dock.size()) {
                    Item item = dock.get(index); if (item.pkg != null) Launch.info(activity, item.pkg); else info(item.name + " is a Unified TV shortcut");
                }

            } else if (moving) {
                moving = false; lifted = -1; beforeMove = null; saveDock(); info("Home layout saved");

            } else if (index >= 0 && index < dock.size()) launch(dock.get(index));
            else info("Add apps to Home from Settings > Apps & Shortcuts");
            invalidate();
        }

        private void shift(int delta) {
            int other = index + delta;
            if (other < 0 || other >= dock.size()) return;
            Collections.swap(dock, index, other);
            index = lifted = other;
            invalidate();
        }

        private void goBack() {
            handler.removeCallbacks(hold);
            if (menu) menu = false;
            else if (moving) {
                moving = false; lifted = -1;
                if (beforeMove != null) {
                    String currentId = dock.get(index).id;
                    dock.clear(); dock.addAll(beforeMove);
                    index = 0;
                    for (int i = 0; i < dock.size(); i++) if (dock.get(i).id.equals(currentId)) index = i;
                }
                beforeMove = null;
            } else if (apps) apps = false;
            // At the main screen: as the TV's chosen Home, Back stays put with focus where it was (spec 28.3). Otherwise
            // Unified TV is just an app, so Back leaves it, the way any TV app does.
            else if (!DefaultHome.isDefault(activity)) { activity.finish(); return; }
            invalidate();
        }

        /** OK on a pill icon opens it directly (UX-UTILITY-03): AirPin LITE, NordVPN, voice, or Settings. */
        private void utility(String id) {
            switch (id) {
                case HomeUtilities.AIRPLAY: { String error = Launch.open(activity, airPin, "AirPin LITE"); if (error != null && !error.isEmpty()) info(error); break; }
                case HomeUtilities.VPN: VpnGate.connect(activity); break;
                case HomeUtilities.VOICE: activity.startActivity(new Intent(activity, VoiceActivity.class)); break;
                default: EditLock.ask(activity, () -> activity.startActivity(new Intent(activity, SettingsActivity.class))); break;
            }
        }
        private void launch(Item item) {
            if(DockPolicy.LIBRARY.equals(item.id)){Launch.library(activity);return;}
            if("music".equals(item.id)){activity.startActivity(new Intent(activity,MusicActivity.class));return;}
            if ("xbox".equals(item.id) || "tvplus".equals(item.id)) { String no = Launch.kidsRefusal(activity, item.id, item.name); if (no != null) { info(no); return; } HouseholdActivity.action(activity, item.id); return; }
            // Live TV and every other app go through one gate (Launch.open): protection, not appearance (docs/VPN_PROTECTION.md).
            boolean live = "live".equals(item.id);
            String pkg = live && item.pkg == null ? LiveTvProtection.livePackage(activity) : item.pkg;
            String why = Launch.open(activity, pkg, live ? "Live TV" : item.name);
            if (why != null && !why.isEmpty()) info(why);
        }

        private void info(String text) {
            message = text;
            messageUntil = SystemClock.uptimeMillis() + 3500L;
            invalidate();
            handler.postDelayed(this::invalidate, 3500);
        }

        @Override protected void onDraw(Canvas c) {
            if(inputEvent>0){InputStats.add(SystemClock.uptimeMillis()-inputEvent);inputEvent=0;}
            super.onDraw(c);
            c.save();
            c.scale(getWidth() / (float) W, getHeight() / (float) H);
            long now = SystemClock.uptimeMillis();
            drawBackground(c, now);
            // Spec 4.8: a faint time-of-day wash over the light; the app's own colours stay the source.
            if (!prefs.getBoolean("highContrast", false)) {
                java.time.ZonedDateTime clock = java.time.ZonedDateTime.now(zone);   // the household's clock (HouseholdSettings)
                int wash = DayMood.wash(clock.getHour() * 60 + clock.getMinute());
                if ((wash >>> 24) > 0) { paint.setShader(null); paint.setColor(wash); c.drawRect(0, 0, W, H, paint); }
            }
            // OLED care on the Sony: everything in front of the background orbits by two pixels every three minutes.
            boolean oled = oledRoom();
            if (oled) { int[] shift = DayMood.shift(System.currentTimeMillis()); c.translate(shift[0], shift[1]); }
            drawHeader(c);
            FixturePolicy.Card match = matchday();
            if (match != null) drawMatchday(c, match);
            float reveal = reveal(now);
            if (reveal > 0) {
                // The dock steps back and up, staying on the same plane, while the titles rise into view.
                int layer = c.saveLayerAlpha(0, 380, W, H, Math.round(255 - 150 * reveal));
                c.translate(0, -300 * reveal); c.scale(1 - .18f * reveal, 1 - .18f * reveal, 960, 876);
                drawDock(c, now, false); c.restoreToCount(layer);
                drawShelf(c, now, reveal);
            } else drawDock(c, now, true);
            drawTitle(c, now, reveal);
            if (menu) drawMenu(c);
            if (moving) drawHint(c, "Left / Right: move     OK: save     Back: cancel");

            if (messageUntil > now && !message.isEmpty()) drawNotice(c, message);
            float dim = oledDim(now);
            if (dim > 0) { paint.setShader(null); paint.setColor(Color.argb(Math.round(255 * dim), 0, 0, 0)); c.drawRect(-4, -4, W + 4, H + 4, paint); }
            // Owner exception to SET-053: while an adult has alarms off on this TV, a small notice stays, drawn last so
            // nothing on Home covers it, and never focusable.
            if (alarmsOff) alarmsOffNotice(c);
            c.restore();
            if (dim > 0 && dim < .45f) postInvalidateDelayed(33);   // the 10-second fade
            boolean animating = (fadeAt != 0 && now - fadeAt < fadeMs()) || (titleAt > now - TITLE_MS) || (shelfAt != 0 && now - shelfAt < SHELF_MS);
            if (animating) postInvalidateDelayed(16);
            // The light drifts in the AmbientLayer, moved by the compositor; this view only redraws when something changes.
            if (fadeAt != 0 && now - fadeAt >= fadeMs()) { fadeAt = 0; formerScene = null; }
            if (shelfAt != 0 && now - shelfAt >= SHELF_MS) shelfAt = 0;
        }

        private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * (2 - t); }
        /** 0 with the dock at rest, 1 with the titles fully raised. */
        private float reveal(long now) {
            if (shelfAt == 0 || calm()) return shelf ? 1 : 0;
            float t = ease((now - shelfAt) / (float) SHELF_MS);
            return shelf ? t : 1 - t;
        }

        /**
         * One crossfade at a time (spec 4.12): the outgoing scene, then the incoming one over it. Apps draw their live mesh
         * (a few moved radial shaders, nothing allocated per frame); titles draw one cached bitmap.
         */
        private void drawBackground(Canvas c, long now) {
            long fade = fadeMs();
            float t = fadeAt == 0 || fade == 0 ? 1 : Math.min(1, (now - fadeAt) / (float) fade);
            String former = formerScene == null ? "neutral" : formerScene;
            float e = t * t * (3 - 2 * t);   // ease in and out (spec 32.1)
            // App light lives in the AmbientLayer behind this view (unless Lightweight): here it is simply not drawn, so
            // a title's picture fades over it, or fades away from it, and app-to-app changes are the layer's alone.
            boolean layered = !light();
            if (layered && app(scene)) { if (t < 1 && !app(former)) drawScene(c, former, Math.round(255 * (1 - e)), 0, now); return; }
            if (t < 1 && !(layered && app(former))) drawScene(c, former, 255, 0, now);
            drawScene(c, scene, t < 1 ? Math.round(e * 255) : 255, t < 1 && !calm() && !light() && !app(scene) ? .02f * (1 - e) : 0, now);
        }

        private final RectF sceneRect = new RectF();
        /** An app's own branded atmosphere, a title's own poster expanded, or the neutral scene; never another item's image. */
        private void drawScene(Canvas c, String key, int alpha, float drift, long now) {
            Bitmap b = key.equals("neutral") ? null : key.startsWith("poster:") ? backdrops.get(key) : backdrops.app(key);
            if (b == null) {
                drawNeutral(c, alpha);
                // Spec 4.6 source order, last step: the neutral scene, dimmed, while the title itself stays named on screen.
                if (key.startsWith("poster:")) { paint.setShader(null); paint.setColor(0x66060a12); paint.setAlpha(alpha * 0x66 / 255); c.drawRect(0, 0, W, H, paint); paint.setAlpha(255); }
                return;
            }
            sceneRect.set(-W * drift / 2, -H * drift / 2, W + W * drift / 2, H + H * drift / 2);
            paint.setShader(null); paint.setColor(Color.WHITE); paint.setAlpha(alpha);
            c.drawBitmap(b, null, sceneRect, paint); paint.setAlpha(255);
        }

        /** The focused item's name, faded in once its scene has settled; hidden while focus is still moving. */
        private void drawTitle(Canvas c, long now, float reveal) {
            if (titleHidden || sceneTitle.isEmpty() || menu || moving || header) return;
            // No label twice (DockLabels): on the dock, the name sits above it only when tiles show no label of their own.
            if (!shelf && app(scene) && !DockLabels.titleAbove(labels())) return;
            int alpha = titleAt <= now - TITLE_MS ? 255 : titleAt > now ? 0 : Math.round(255 * (now - titleAt) / (float) TITLE_MS);
            if (alpha <= 0) { postInvalidateDelayed(16); return; }
            float y = 714 - 60 * reveal;
            text(c, sceneTitle.length() > 60 ? sceneTitle.substring(0, 57) + "…" : sceneTitle, 960, y, 48, (alpha << 24) | (WHITE & 0xFFFFFF), false, Paint.Align.CENTER);
        }

        private void drawNeutral(Canvas c, int alpha) {
            if (coast != null) {
                paint.setShader(null); paint.setColor(Color.WHITE); paint.setAlpha(alpha);
                c.drawBitmap(coast, null, new RectF(0, 0, W, H), paint);
                paint.setAlpha(255);
                return;
            }
            gradient(c, 0xFF788DA7, 0xFFD4ACA1, 0xFF384C61, alpha);
            paint.setShader(null);
            paint.setColor(0x907991A5);
            c.drawRect(0, 475, W, H, paint);
            Path surf = new Path();
            for (int j = 0; j < 6; j++) {
                surf.reset();
                int y = 530 + j * 95;
                surf.moveTo(0, y);
                for (int x = 0; x <= W; x += 40)
                    surf.lineTo(x, (float) (y + 13 * Math.sin(x / 160.0 + j)));
                paint.setColor(Color.argb(Math.min(170, alpha), 200, 220, 232));
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3 + j);
                c.drawPath(surf, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void gradient(Canvas c, int top, int middle, int bottom, int alpha) {
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(0, 0, W * .78f, H,
                    new int[]{top, middle, bottom}, new float[]{0, .7f, 1}, Shader.TileMode.CLAMP));
            paint.setAlpha(alpha);
            c.drawRect(0, 0, W, H, paint);
            paint.setAlpha(255); paint.setShader(null);
        }

        /** The clock (household zone, the TV's 12/24-hour choice) and the utility pill (UX-UTILITY-01): text-free glyphs. */
        private void drawHeader(Canvas c) {
            text(c, java.time.ZonedDateTime.now(zone).format(clockFormat), 96, 142, 66, WHITE, false, Paint.Align.LEFT);
            String vpnLabel = nordVpn ? VpnGate.label(activity) : "";   // keeps the VPN state fresh (sampled off the main thread)
            int n = pill.size(); if (n == 0) return;
            float right = 1817, left = right - (n * 96 + 32);
            glass(c, left, 62, right, 145, 30, 0x6A2B303B, 0x44FFFFFF);
            for (int i = 0; i < n; i++) {
                float cx = left + 64 + i * 96, cy = 104; String id = pill.get(i);
                if (header && i == headerIndex) {
                    // One restrained lens on the focused icon, with a crisp outline (stronger with high contrast); never a pulse.
                    rect.set(cx - 38, 70, cx + 38, 138);
                    paint.setShader(null); paint.setColor(0x3DFFFFFF); c.drawRoundRect(rect, 24, 24, paint);
                    boolean strong = prefs.getBoolean("highContrast", false);
                    outline.setColor(strong ? 0xFFFFFFFF : 0xD9F4F1F2); outline.setStrokeWidth(strong ? 4 : 3); c.drawRoundRect(rect, 24, 24, outline); outline.setStrokeWidth(2);
                }
                switch (id) {
                    case HomeUtilities.AIRPLAY: airPlay(c, cx, cy); break;
                    case HomeUtilities.VPN: shield(c, cx, cy, VpnGate.current()); break;
                    case HomeUtilities.VOICE: microphone(c, cx, cy); break;
                    default: gear(c, cx, cy); break;
                }
            }
            if (header && headerIndex >= 0 && headerIndex < n) {
                // The short label sits outside the icon, under the pill, only while it has focus.
                String words = HomeUtilities.label(pill.get(headerIndex), vpnLabel, AirPlayReceiver.ready);
                float cx = Math.min(left + 64 + headerIndex * 96, W - 80 - 6.5f * words.length());
                text(c, words, cx, 186, 23, WHITE, false, Paint.Align.CENTER);
            }
        }
        /**
         * The VPN shield (UX-UTILITY-02): monochrome, with a subtle mark for each state. A check only while Android reports
         * a connected VPN; a slash when disconnected; a dot while checking; nothing inside when unknown. It never says
         * Live TV is protected: that takes the automatic check (LiveTvProtection), which this TV cannot make.
         */
        private void shield(Canvas c, float cx, float cy, VpnGate.State state) {
            iconPaint(state == VpnGate.State.CONNECTED ? 245 : 200, false);
            glyph.reset(); glyph.moveTo(cx, cy - 20); glyph.lineTo(cx + 16, cy - 14); glyph.lineTo(cx + 16, cy - 1);
            glyph.quadTo(cx + 15, cy + 14, cx, cy + 21); glyph.quadTo(cx - 15, cy + 14, cx - 16, cy - 1); glyph.lineTo(cx - 16, cy - 14); glyph.close();
            c.drawPath(glyph, icon);
            if (state == VpnGate.State.CONNECTED) { glyph.reset(); glyph.moveTo(cx - 7, cy); glyph.lineTo(cx - 2, cy + 6); glyph.lineTo(cx + 8, cy - 6); c.drawPath(glyph, icon); }
            else if (state == VpnGate.State.DISCONNECTED) c.drawLine(cx - 9, cy + 9, cx + 9, cy - 9, icon);
            else if (state == VpnGate.State.CHECKING) { iconPaint(200, true); c.drawCircle(cx, cy, 3, icon); }
        }
        /** Owner exception to SET-053: the permanent "Alarms off on this TV" notice, top centre. */
        private void alarmsOffNotice(Canvas c) {
            type.setTextSize(24); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            float w = type.measureText(AlarmWatch.OFF_NOTICE) + 70;
            rect.set(960 - w / 2, 70, 960 + w / 2, 132); paint.setShader(null); paint.setColor(0xE0402018); c.drawRoundRect(rect, 31, 31, paint);
            outline.setColor(0xCCFFC9A8); outline.setStrokeWidth(2); c.drawRoundRect(rect, 31, 31, outline);
            paint.setColor(0xFFFFB37A); c.drawCircle(960 - w / 2 + 28, 101, 7, paint);
            text(c, AlarmWatch.OFF_NOTICE, 960 + 12, 110, 24, WHITE, true, Paint.Align.CENTER);
        }
        /**
         * Spec 7: a small glass card under the clock: the team's generated badge (its short name on its two colours; never a
         * crest), the match, then "Kick-off 19:45", "Kick-off moved to 19:45", "Moved to Sunday 3 November" or a red-dot
         * "Live". Never a score.
         */
        private void drawMatchday(Canvas c, FixturePolicy.Card m) {
            type.setTextSize(28); type.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            boolean badge = m.team != null && !m.team.shortName.isEmpty();
            float x = badge ? 186 : 116, w = Math.max(300, Math.min(780, type.measureText(m.match) + (x - 84) + 32));
            glass(c, 84, 178, 84 + w, 288, 24, 0x6A2B303B, 0x44FFFFFF);
            if (badge) badge(c, m.team, 140, 233);
            text(c, m.match, x, 224, 28, WHITE, true, Paint.Align.LEFT);
            if (m.live) { paint.setShader(null); paint.setColor(0xFFE5484D); c.drawCircle(x + 8, 257, 7, paint); text(c, "Live", x + 24, 266, 24, WHITE, false, Paint.Align.LEFT); }
            else text(c, m.when, x, 266, 24, MUTED, false, Paint.Align.LEFT);
        }
        /** The generated badge: a disc split into the team's two colours, its short name on top. Drawn here; nothing downloaded. */
        private void badge(Canvas c, Fixtures.Team t, float cx, float cy) {
            paint.setShader(null); paint.setColor(t.primary); c.drawCircle(cx, cy, 34, paint);
            rect.set(cx - 34, cy - 34, cx + 34, cy + 34); paint.setColor(t.secondary); c.drawArc(rect, 0, 180, true, paint);
            outline.setColor(0x80FFFFFF); outline.setStrokeWidth(2); c.drawCircle(cx, cy, 34, outline);
            rect.set(cx - 30, cy - 15, cx + 30, cy + 15); paint.setColor(t.primary); c.drawRoundRect(rect, 10, 10, paint);
            text(c, t.shortName, cx, cy + 8, t.shortName.length() > 3 ? 18 : 22, Fixtures.ink(t.primary), true, Paint.Align.CENTER);
        }
        private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path glyph = new Path();
        private void iconPaint(int alpha, boolean fill) {
            icon.setColor(WHITE); icon.setAlpha(alpha); icon.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
            icon.setStrokeWidth(3); icon.setStrokeCap(Paint.Cap.ROUND); icon.setStrokeJoin(Paint.Join.ROUND);
            icon.setShadowLayer(1.5f, 0, 1, 0x80000000);   // a microscopic shadow keeps it legible on bright pictures
        }
        /**
         * AirPlay ready (spec 7.1): a screen with an upward triangle, solid white at about 70 % with a tiny shadow. Drawn
         * only when AirPin LITE is confirmed to be offering AirPlay on the network; otherwise nothing is drawn at all.
         */
        /**
         * Owner design: while an iPhone is casting, the glyph brightens to full white with a soft halo. Known only when
         * AirPin reports a playing media session and the owner has allowed notification access; otherwise it stays at 70 %.
         */
        private volatile boolean casting;
        private void casting() {
            if (!AirPlayReceiver.ready) { if (casting) { casting = false; postInvalidate(); } return; }
            Thread t = new Thread(() -> {
                String pkg = AirPlayReceiver.installed(activity);
                boolean now = pkg != null && MediaWatch.sessionPlayingFor(activity, pkg);
                if (now != casting) { casting = now; postInvalidate(); }
            }, "airplay-casting");
            t.setDaemon(true); t.start();
        }
        private void airPlay(Canvas c, float cx, float cy) {
            // Installed but not offering AirPlay: dimmed, and its focus label says why (UX-UTILITY-02).
            if (casting && AirPlayReceiver.ready) { iconPaint(90, false); icon.setShadowLayer(14, 0, 0, 0x8CFFFFFF); airPlayShape(c, cx, cy); }
            int alpha = !AirPlayReceiver.ready ? 90 : casting ? 255 : 179;
            iconPaint(alpha, false);
            airPlayShape(c, cx, cy, alpha);
        }
        private void airPlayShape(Canvas c, float cx, float cy) { airPlayShape(c, cx, cy, -1); }
        private void airPlayShape(Canvas c, float cx, float cy, int fillAlpha) {
            glyph.reset();   // the screen, open at the bottom where the triangle sits
            glyph.moveTo(cx - 9, cy + 11); glyph.lineTo(cx - 18, cy + 11); glyph.quadTo(cx - 21, cy + 11, cx - 21, cy + 8);
            glyph.lineTo(cx - 21, cy - 12); glyph.quadTo(cx - 21, cy - 15, cx - 18, cy - 15); glyph.lineTo(cx + 18, cy - 15);
            glyph.quadTo(cx + 21, cy - 15, cx + 21, cy - 12); glyph.lineTo(cx + 21, cy + 8); glyph.quadTo(cx + 21, cy + 11, cx + 18, cy + 11);
            glyph.lineTo(cx + 9, cy + 11); c.drawPath(glyph, icon);
            if (fillAlpha >= 0) iconPaint(fillAlpha, true); else { icon.setStyle(Paint.Style.FILL); }
            glyph.reset(); glyph.moveTo(cx, cy + 3); glyph.lineTo(cx + 11, cy + 17); glyph.lineTo(cx - 11, cy + 17); glyph.close();
            c.drawPath(glyph, icon);
        }
        private void microphone(Canvas c, float cx, float cy) {
            iconPaint(235, false);
            c.drawRoundRect(cx - 7, cy - 18, cx + 7, cy + 4, 7, 7, icon);
            rect.set(cx - 13, cy - 8, cx + 13, cy + 12); c.drawArc(rect, 0, 180, false, icon);
            c.drawLine(cx, cy + 12, cx, cy + 19, icon); c.drawLine(cx - 7, cy + 19, cx + 7, cy + 19, icon);
        }
        private void gear(Canvas c, float cx, float cy) {
            iconPaint(235, false);
            c.drawCircle(cx, cy, 11, icon); c.drawCircle(cx, cy, 4.5f, icon);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4;
                c.drawLine(cx + (float) (Math.cos(a) * 13), cy + (float) (Math.sin(a) * 13), cx + (float) (Math.cos(a) * 18), cy + (float) (Math.sin(a) * 18), icon);
            }
        }

        private void drawDock(Canvas c, long now, boolean resting) {
            final int count = dock.size();
            if (count == 0) {
                glass(c, 230, 795, 1690, 1000, 36, 0x92434B57, 0x70FFFFFF);
                text(c, "No apps on Home yet. Add them in Settings > Apps & Shortcuts.", 960, 900, 38, WHITE, false, Paint.Align.CENTER);
                return;
            }
            int visible = Math.min(6,count);
            int first = Math.max(0,Math.min(count-visible,index-visible/2));
            float full = visible * 190 + Math.max(0, visible - 1) * 24 + 70;
            float left = (W - full) / 2;
            float top = 747, bottom = 1005;
            glass(c, left, top, left + full, bottom, 37, 0x54465669, 0x20FFFFFF);
            float gap = 24, cellWidth = 190;
            cellWidth = Math.min(190, cellWidth);
            float total = visible * cellWidth + (visible - 1) * gap;
            float start = (W - total) / 2;
            for (int i = first; i < first+visible; i++) {
                float x = start + (i-first) * (cellWidth + gap);
                float y = top + 31;
                boolean selected = i == index && !header;   // one focus at a time: in the top row the tile rests
                float scale = selected ? 1.07f : 1;
                if (selected && focusAt > 0 && !prefs.getBoolean("reduceMotion",false) && !prefs.getBoolean("lightweight",false)) {
                    float t = shelf ? 1 : Math.min(1, (now - focusAt) / (float) TILE_MS);
                    scale = 1 + .07f * (t * (2 - t));
                    if (t < 1) postInvalidateDelayed(16);
                }
                Item item = dock.get(i);
                c.save();
                c.scale(scale, scale, x + cellWidth / 2, y + 80);
                if(selected){
                    // A soft halo in the app's own colour behind the lifted tile (spec 32.3).
                    int glow=AmbientMesh.accent(item.id)&0x00FFFFFF;
                    paint.setShader(new android.graphics.RadialGradient(x+cellWidth/2,y+80,170,new int[]{0x5A000000|glow,0x00000000|glow},null,Shader.TileMode.CLAMP));
                    c.drawCircle(x+cellWidth/2,y+80,170,paint);paint.setShader(null);
                    c.save();c.translate(x+cellWidth/2,y+177);c.scale(1,.27f);
                    paint.setShader(new android.graphics.RadialGradient(0,0,155,new int[]{0x68000000,0x00000000},null,Shader.TileMode.CLAMP));
                    c.drawCircle(0,0,155,paint);c.restore();paint.setShader(null);c.translate(0,-12);
                }
                // UX-ICON-01..03: one neutral glass tile for every app, a simple white glyph on it; never text or a full-colour icon.
                card(c, x, y, x + cellWidth, y + 161, 27, 0x40FFFFFF, 0x1FFFFFFF);
                if(selected)glass(c,x,y,x+cellWidth,y+161,27,0,0);
                tileGlyph(c, item, x + cellWidth / 2, y + 80);
                if (selected) {
                    // A crisp, luminous outline on the focused tile, brighter still with high contrast.
                    boolean strong = prefs.getBoolean("highContrast",false);
                    outline.setColor(strong ? 0xFFFFFFFF : 0xD9F4F1F2); outline.setStrokeWidth(strong ? 4 : 3);
                    c.drawRoundRect(x - 5, y - 5, x + cellWidth + 5, y + 166, 31, 31, outline);
                    outline.setStrokeWidth(2);
                }
                c.restore();
                // UX-ICON-05: labels outside the tiles, as the profile chose (Focused only by default); the tiles never move.
                if (DockLabels.shows(labels(), i, header || shelf ? -1 : index)) text(c, item.name, x + cellWidth / 2, 982, 27, WHITE, false, Paint.Align.CENTER);
            }
            if (!resting) return;
            Path down=new Path();down.moveTo(947,1035);down.lineTo(960,1046);down.lineTo(973,1035);
            outline.setColor(0x9acbd8e7);outline.setStrokeWidth(2);c.drawPath(down,outline);
        }

        /**
         * The tile's glyph (DockLabels.glyph): original, simple vector symbols drawn here in white; no brand artwork is copied.
         * An app without one of its own shows its installed icon small and in greyscale, so it never reads as a full-colour square.
         */
        private final android.graphics.ColorMatrixColorFilter grey = new android.graphics.ColorMatrixColorFilter(new android.graphics.ColorMatrix(new float[]{
                .30f,.59f,.11f,0,20, .30f,.59f,.11f,0,20, .30f,.59f,.11f,0,20, 0,0,0,.9f,0}));
        private void tileGlyph(Canvas c, Item item, float cx, float cy) {
            String g = DockLabels.glyph(item.pkg != null && !DockPolicy.canonical(item.pkg).equals(item.id) ? item.pkg : item.id);
            if ("mono".equals(g) && item.pkg != null) g = DockLabels.glyph(item.pkg);
            iconPaint(240, false); icon.setStrokeWidth(5);
            switch (g) {
                case "shelf": libraryGlyph(c, cx, cy); break;
                case "play":   // a screen with a play triangle
                    rect.set(cx - 44, cy - 32, cx + 44, cy + 28); c.drawRoundRect(rect, 12, 12, icon); c.drawLine(cx - 18, cy + 40, cx + 18, cy + 40, icon);
                    iconPaint(240, true); glyph.reset(); glyph.moveTo(cx - 10, cy - 16); glyph.lineTo(cx + 16, cy - 2); glyph.lineTo(cx - 10, cy + 12); glyph.close(); c.drawPath(glyph, icon); break;
                case "n":   // a plain geometric N
                    icon.setStrokeWidth(13); icon.setStrokeCap(Paint.Cap.SQUARE); glyph.reset(); glyph.moveTo(cx - 20, cy + 38); glyph.lineTo(cx - 20, cy - 38); glyph.lineTo(cx + 20, cy + 38); glyph.lineTo(cx + 20, cy - 38); c.drawPath(glyph, icon); break;
                case "arcplus":   // an arc over a plus
                    rect.set(cx - 46, cy - 30, cx + 46, cy + 50); c.drawArc(rect, 200, 140, false, icon);
                    c.drawLine(cx, cy - 4, cx, cy + 32, icon); c.drawLine(cx - 18, cy + 14, cx + 18, cy + 14, icon); break;
                case "blocks":   // three square blocks
                    iconPaint(240, true); for (int k = -1; k <= 1; k++) { rect.set(cx + k * 32 - 13, cy - 13, cx + k * 32 + 13, cy + 13); c.drawRoundRect(rect, 3, 3, icon); } break;
                case "broadcast":   // a mast with waves
                    iconPaint(240, true); c.drawCircle(cx, cy - 6, 8, icon); iconPaint(240, false); icon.setStrokeWidth(5);
                    rect.set(cx - 26, cy - 32, cx + 26, cy + 20); c.drawArc(rect, 200, 140, false, icon); rect.set(cx - 42, cy - 48, cx + 42, cy + 36); c.drawArc(rect, 205, 130, false, icon);
                    c.drawLine(cx, cy + 2, cx - 16, cy + 40, icon); c.drawLine(cx, cy + 2, cx + 16, cy + 40, icon); break;
                case "note":   // a beamed pair of notes
                    iconPaint(240, true); c.drawCircle(cx - 20, cy + 26, 11, icon); c.drawCircle(cx + 22, cy + 18, 11, icon);
                    iconPaint(240, false); icon.setStrokeWidth(6); c.drawLine(cx - 10, cy + 26, cx - 10, cy - 30, icon); c.drawLine(cx + 32, cy + 18, cx + 32, cy - 38, icon); c.drawLine(cx - 10, cy - 30, cx + 32, cy - 38, icon); break;
                case "controller":   // a game pad
                    rect.set(cx - 46, cy - 22, cx + 46, cy + 26); c.drawRoundRect(rect, 22, 22, icon);
                    c.drawLine(cx - 28, cy + 2, cx - 12, cy + 2, icon); c.drawLine(cx - 20, cy - 6, cx - 20, cy + 10, icon);
                    iconPaint(240, true); c.drawCircle(cx + 18, cy - 2, 5, icon); c.drawCircle(cx + 30, cy + 8, 5, icon); break;
                case "screen":   // a TV
                    rect.set(cx - 44, cy - 32, cx + 44, cy + 26); c.drawRoundRect(rect, 10, 10, icon); c.drawLine(cx - 20, cy + 40, cx + 20, cy + 40, icon); break;
                case "playcircle":
                    c.drawCircle(cx, cy, 40, icon); iconPaint(240, true); glyph.reset(); glyph.moveTo(cx - 12, cy - 18); glyph.lineTo(cx + 20, cy); glyph.lineTo(cx - 12, cy + 18); glyph.close(); c.drawPath(glyph, icon); break;
                case "chevron":
                    icon.setStrokeWidth(10); glyph.reset(); glyph.moveTo(cx - 14, cy - 34); glyph.lineTo(cx + 18, cy); glyph.lineTo(cx - 14, cy + 34); c.drawPath(glyph, icon); break;
                default:
                    if (item.icon != null) {
                        Drawable d = item.icon.mutate(); d.setColorFilter(grey);
                        d.setBounds(Math.round(cx - 34), Math.round(cy - 34), Math.round(cx + 34), Math.round(cy + 34)); d.draw(c);
                    } else { c.drawCircle(cx, cy, 30, icon); }   // nothing to show yet: a plain ring, never letters
            }
            icon.setStrokeWidth(3); icon.setStrokeCap(Paint.Cap.ROUND);
        }
        /** Library: three posters on a shelf, drawn (no font glyph that a TV might lack). */
        private void libraryGlyph(Canvas c, float cx, float cy) {
            iconPaint(240, false); icon.setStrokeWidth(5);
            for (int k = -1; k <= 1; k++) { rect.set(cx + k * 30 - 12, cy - 34 + Math.abs(k) * 6, cx + k * 30 + 12, cy + 28); c.drawRoundRect(rect, 4, 4, icon); }
            c.drawLine(cx - 52, cy + 40, cx + 52, cy + 40, icon); icon.setStrokeWidth(3);
        }

        private final Path cardClip = new Path();
        /** One row of the household's own titles. The focused poster lifts; the rest recede but never vanish (spec 4.6). */
        private void drawShelf(Canvas c, long now, float reveal) {
            int entries = posters.size() + 1;
            float w = 186, h = 279, gap = 30, top = 735 + (1 - reveal) * 380;
            int focus = Math.min(posterIndex, entries - 1);
            for (int i = 0; i < entries; i++) {
                float x = 960 - w / 2 + (i - focus) * (w + gap);
                if (x + w < -40 || x > W + 40) continue;
                boolean selected = shelf && i == focus;
                float scale = 1;
                if (selected) {
                    float t = calm() ? 1 : Math.min(1, (now - focusAt) / (float) TILE_MS);
                    scale = 1 + .08f * ease(t);
                    if (t < 1) postInvalidateDelayed(16);
                }
                c.save(); c.scale(scale, scale, x + w / 2, top + h);
                if (selected) {
                    c.save(); c.translate(x + w / 2, top + h + 8); c.scale(1, .22f);
                    paint.setShader(new android.graphics.RadialGradient(0, 0, 150, new int[]{0x70000000, 0x00000000}, null, Shader.TileMode.CLAMP));
                    c.drawCircle(0, 0, 150, paint); c.restore(); paint.setShader(null); c.translate(0, -14);
                }
                if (i < posters.size()) poster(c, posters.get(i), x, top, w, h);
                else {
                    glass(c, x, top, x + w, top + h, 22, 0, 0);
                    text(c, libraryReady ? "View All" : Profiles.kids(prefs) ? "Ask an adult" : "Set up", x + w / 2, top + h / 2 - 6, 30, WHITE, true, Paint.Align.CENTER);
                    text(c, "Films & TV", x + w / 2, top + h / 2 + 32, 24, MUTED, false, Paint.Align.CENTER);
                }
                if (!selected && shelf) { paint.setShader(null); paint.setColor(0x52000000); rect.set(x, top, x + w, top + h); c.drawRoundRect(rect, 22, 22, paint); }
                if (selected && prefs.getBoolean("highContrast", false)) { outline.setColor(0xCCFFFFFF); outline.setStrokeWidth(3); c.drawRoundRect(x - 3, top - 3, x + w + 3, top + h + 3, 24, 24, outline); }
                c.restore();
            }
            if (posters.isEmpty() && postersLoading) text(c, "Finding your films and TV…", 960, top - 40, 26, MUTED, false, Paint.Align.CENTER);
            // NAV-05: an unreachable or empty library is a friendly state, never an address prompt; OK on the card reconnects.
            else if (posters.isEmpty() && libraryReady && postersAt > 0) text(c, Profiles.kids(prefs) ? "Nothing in the Kids folders yet. Ask an adult to add some." : "Your films and TV aren't answering right now. OK opens the Library to reconnect.", 960, top - 40, 26, MUTED, false, Paint.Align.CENTER);
        }

        /** A title's own server artwork, or a plain titled card: never another title's picture. */
        private void poster(Canvas c, HomeShelf.Poster p, float x, float top, float w, float h) {
            Bitmap art = posterArt.get(p.id);
            rect.set(x, top, x + w, top + h);
            if (art == null) {
                card(c, x, top, x + w, top + h, 22, 0xFF2B3446, 0xFF141A26);
                String t = p.title.length() > 34 ? p.title.substring(0, 32) + "…" : p.title;
                int split = t.length() > 16 ? Math.max(t.lastIndexOf(' ', 16), 8) : -1;
                if (split > 0) { text(c, t.substring(0, split), x + w / 2, top + h / 2 - 4, 24, WHITE, false, Paint.Align.CENTER); text(c, t.substring(split).trim(), x + w / 2, top + h / 2 + 26, 24, WHITE, false, Paint.Align.CENTER); }
                else text(c, t, x + w / 2, top + h / 2 + 8, 24, WHITE, false, Paint.Align.CENTER);
                return;
            }
            cardClip.reset(); cardClip.addRoundRect(rect, 22, 22, Path.Direction.CW);
            c.save(); c.clipPath(cardClip);
            float scale = Math.max(w / art.getWidth(), h / art.getHeight()), aw = art.getWidth() * scale, ah = art.getHeight() * scale;
            paint.setShader(null); paint.setColor(Color.WHITE);
            c.drawBitmap(art, null, new RectF(x + (w - aw) / 2, top + (h - ah) / 2, x + (w + aw) / 2, top + (h + ah) / 2), paint);
            c.restore();
        }

        /** NAV-03: the first meaningful title (Continue Watching when there is one, by default), else the Library card. */
        private boolean shelfMoved;
        private int landingPoster() {
            long[] resume = new long[posters.size()]; for (int i = 0; i < resume.length; i++) resume[i] = posters.get(i).resume;
            return HomeUtilities.libraryLanding(resume, prefs.getString(HomeUtilities.LANDING, "continue"));
        }
        private void openShelf() {
            if (menu || moving) return;
            shelf = true; shelfAt = calm() ? 0 : SystemClock.uptimeMillis(); shelfMoved = false;
            libraryReady = HomeShelf.configured(activity);   // once per opening, never per frame (it reads the encrypted store)
            posterIndex = landingPoster();
            moved(); loadPosters(); invalidate();
        }
        private void closeShelf() {
            shelf = false; shelfAt = calm() ? 0 : SystemClock.uptimeMillis();
            moved(); invalidate();
        }
        /** The saved collections' first titles, then each title's own artwork. Memory stays bounded: 18 posters at most. */
        private void loadPosters() {
            long now = SystemClock.uptimeMillis();
            if (postersLoading || (!posters.isEmpty() && now - postersAt < 10 * 60_000L)) return;
            postersLoading = true; invalidate();
            try { shelfWorker.execute(() -> {
                List<HomeShelf.Poster> found;
                try { found = HomeShelf.load(activity); } catch (Exception e) { found = new ArrayList<>(); }
                final List<HomeShelf.Poster> ready = found;
                handler.post(() -> { posters = ready; postersLoading = false; postersAt = SystemClock.uptimeMillis();
                    posterIndex = shelf && !shelfMoved ? landingPoster() : Math.min(posterIndex, posters.size()); if (shelf) { handler.removeCallbacks(commitFocus); handler.post(commitFocus); } invalidate(); });
                for (HomeShelf.Poster p : ready) {
                    if (p.art.isEmpty() || posterArt.containsKey(p.id) || Thread.currentThread().isInterrupted()) continue;
                    try {
                        Bitmap b = Images.decode(DlnaClient.request(p.art, null, null), 240, 360);
                        if (b == null) continue;
                        posterArt.put(p.id, b); backdrops.put("poster:" + p.id, Backdrops.fromPoster(b));
                        // A title already in focus gets its own backdrop with a dissolve, not a pop.
                        handler.post(() -> { if (scene.equals("poster:" + p.id)) { formerScene = "neutral"; fadeAt = fadeMs() == 0 ? 0 : SystemClock.uptimeMillis(); } invalidate(); });
                    } catch (Exception ignored) {}
                }
            }); } catch (java.util.concurrent.RejectedExecutionException e) { postersLoading = false; }
        }
        /** OK on a title plays it or opens its collection; the last card opens the full library or setup. A fade, never a flash. */
        private void openEntry() {
            Intent next;
            if (posterIndex < posters.size()) {
                HomeShelf.Poster p = posters.get(posterIndex);
                if (!p.folder && !p.url.isEmpty()) next = new Intent(activity, PlayerActivity.class).putExtra("dlnaUrl", p.url).putExtra("title", p.title).putExtra("dlnaItem", p.id).putExtra("root", p.root);
                else next = new Intent(activity, DlnaLibraryActivity.class).putExtra("folder", p.id).putExtra("name", p.title).putExtra("fromShelf", true).putExtra("root", p.root);
            } else { Launch.library(activity); return; }
            try { activity.startActivity(next); activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
            catch (RuntimeException e) { info("Unable to open Films & TV"); }
        }

        private static final String[] MENU = {"Move", "Remove from Home", "App information", "Cancel"};
        private void drawMenu(Canvas c) {
            paint.setShader(null); paint.setColor(0x92000000); c.drawRect(0, 0, W, H, paint);
            glass(c, 625, 330, 1295, 790, 35, 0xEB242B37, 0x70FFFFFF);
            text(c, index >= 0 && index < dock.size() ? dock.get(index).name : "Edit Home", 960, 415, 48, WHITE, false, Paint.Align.CENTER);
            String[] actions = MENU;
            for (int j = 0; j < actions.length; j++) {
                if (j == menuIndex) glass(c, 695, 497 + 58 * j, 1225, 550 + 58 * j, 17, 0x928A98AA, 0xAAFFFFFF);
                text(c, actions[j], 960, 534 + 58 * j, 31, WHITE, false, Paint.Align.CENTER);
            }
        }

        private void drawHint(Canvas c, String s) {
            text(c, s, 960, 1057, 21, 0xEEFFFFFF, false, Paint.Align.CENTER);
        }

        private void drawNotice(Canvas c, String s) {
            glass(c, 365, 230, 1555, 320, 24, 0xDF111A2B, 0x88FFFFFF);
            text(c, s.length() > 65 ? s.substring(0, 62) + "…" : s, 960, 287, 27,
                    WHITE, false, Paint.Align.CENTER);
        }

        private void card(Canvas c, float x, float y, float right, float bottom, float radius, int start, int end) {
            rect.set(x, y, right, bottom);
            paint.setShader(new LinearGradient(x, y, right, bottom, start, end, Shader.TileMode.CLAMP));
            paint.setColor(Color.WHITE);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
        }

        private void glass(Canvas c, float l, float t, float r, float b, float radius, int colour, int stroke) {
            rect.set(l,t,r,b);
            String key=scene.startsWith("poster:")?"neutral":scene;
            MaterialScene.glass(c,rect,radius,key,prefs.getBoolean("opaque",false)||prefs.getBoolean("highContrast",false));
        }

        private void text(Canvas c, String s, float x, float y, int size, int color, boolean bold, Paint.Align align) {
            type.setShader(null); type.setColor(color);
            type.setTextSize(size);
            type.setTypeface(Typeface.create(bold ? "sans-serif-medium" : size==66 ? "sans-serif-light" : "sans-serif", Typeface.NORMAL));
            type.setTextAlign(align);
            type.setShadowLayer(3, 0, 1, 0x52000000);
            c.drawText(s, x, y, type);
            type.clearShadowLayer();
        }
    }
}
