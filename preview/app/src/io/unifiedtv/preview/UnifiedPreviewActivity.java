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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

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
    @Override protected void onStart(){super.onStart();LocalDiscovery.prefetch(this);VpnGate.invalidate();vpnWatcher=VpnGate.watch(this,()->{if(surface!=null)surface.invalidate();});}
    @Override protected void onStop(){VpnGate.unwatch(this,vpnWatcher);vpnWatcher=null;super.onStop();}
    private final Handler idleHandler = new Handler(Looper.getMainLooper());
    private final Runnable gallery = () -> {
        try { if (new Vault(this).read("gallery").optJSONArray("images") != null)
            startActivity(new Intent(this, GalleryActivity.class)); } catch (Exception ignored) {}
    };
    @Override public void onUserInteraction() { super.onUserInteraction(); idleHandler.removeCallbacks(gallery); idleHandler.postDelayed(gallery,300000); }
    @Override protected void onResume() { super.onResume(); onUserInteraction(); if(surface!=null)surface.refresh(); }
    @Override protected void onPause() { idleHandler.removeCallbacks(gallery); if(surface!=null)surface.pause(); super.onPause(); }


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        surface = new Preview(this);
        setContentView(surface);
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
        private static final long HOLD_MS = 550, BG_WAIT_MS = 135, BG_FADE_MS = 380;
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
        private boolean okDown, held;
        private long focusAt, fadeAt;
        private Item atmosphere, former;
        private String message = "";
        private long messageUntil = 0;
        private long inputEvent;
        private String fixture="";
        private long fixtureFetched;
        private final java.util.concurrent.ExecutorService fixtureWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
        private void fixture(){
            long now=System.currentTimeMillis();if(now-fixtureFetched<900000)return;fixtureFetched=now;
            fixtureWorker.execute(()->{
                String label="";try{
                    org.json.JSONObject house=new Vault(activity).read("household"),ha=new Vault(activity).read("ha");String calendar=house.optString("calendar");
                    if(calendar.matches("calendar\\.[a-z0-9_]+")){
                        org.json.JSONObject attrs=LocalApi.get(ha,"/api/states/"+LocalApi.segment(calendar)).getJSONObject("attributes");
                        label=FixturePolicy.label(attrs.optString("start_time"),attrs.optString("end_time"),java.time.Instant.now());
                    }
                }catch(Exception ignored){}
                final String result=label;handler.post(()->{fixture=result;invalidate();});
            });
        }
        private final Runnable hold = () -> {
            if (okDown && !held) {
                held = true;
                if (index >= 0 && index < dock.size()) { EditLock.ask(activity, () -> { menu = true; menuIndex = 0; invalidate(); }); }
            }
        };
        private final Runnable clockTick = new Runnable() {
            @Override public void run() {
                invalidate();
                fixture();
                handler.postDelayed(this, 60_000L);
            }
        };
        private final Runnable commitFocus = () -> {
            if (apps || menu || moving || index < 0 || index >= dock.size()) return;
            Item next = dock.get(index);
            if (next == atmosphere) return;
            former = atmosphere;
            atmosphere = next;
            fadeAt = SystemClock.uptimeMillis();
            invalidate();
        };

        Preview(Activity a) {
            super(a);
            activity = a;
            prefs = a.getSharedPreferences("unified-tv-preview", Context.MODE_PRIVATE);
            setFocusable(true);
            setFocusableInTouchMode(true);
            scanInstalled();
            loadDock();
            handler.postDelayed(clockTick, 60_000L);
            try (InputStream in = a.getAssets().open("unified-preview-coast.png")) {
                BitmapFactory.Options opt = new BitmapFactory.Options();
                opt.inSampleSize = 1;
                coast = BitmapFactory.decodeStream(in, null, opt);
                MaterialScene.wallpaper(coast);
            } catch (Exception ignored) { /* Original illustrated gradient is the safe offline fallback. */ }
            int saved = prefs.getInt("focus", 0);
            index = dock.isEmpty() ? -1 : Math.max(0, Math.min(dock.size()-1,saved));
            if(index>=0) atmosphere=dock.get(index);
            prefs.edit().putString("scene",atmosphere==null?"neutral":atmosphere.id).apply();
            setContentDescription("Unified TV Home. Left and Right choose apps. Up opens controls. Down reveals your films and TV. Hold OK to edit.");
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(2);
        }

        void refresh() {
            handler.removeCallbacks(clockTick);handler.postDelayed(clockTick,60000);
            fixtureFetched=0;fixture();
            if(moving)goBack();
            installed.clear();dock.clear();scanInstalled();loadDock();
            index=dock.isEmpty()?-1:Math.max(0,Math.min(dock.size()-1,prefs.getInt("focus",0)));
            atmosphere=index<0?null:dock.get(index);former=null;fadeAt=0;invalidate();
        }
        void pause() {
            handler.removeCallbacks(clockTick);handler.removeCallbacks(commitFocus);
            handler.removeCallbacks(hold); okDown=false;held=false;
            if(moving) goBack();
            prefs.edit().putInt("focus",Math.max(0,index)).apply();
        }
        void close() {
            fixtureWorker.shutdownNow();
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
            Collections.sort(installed, Comparator.comparing(it -> it.name.toLowerCase(Locale.UK)));
            List<Item> defaults=new ArrayList<>();
            for(String id:new String[]{"smartutube","netflix","disney","bbc","live","music"}) defaults.add(defaultItem(id));
            if(Build.MANUFACTURER.toLowerCase(Locale.UK).contains("sony")) defaults.add(defaultItem("xbox"));
            if(prefs.getString("room","").equals("frame")) defaults.add(defaultItem("tvplus"));
            Set<String> canonical=new HashSet<>();for(Item item:defaults)if(item.pkg!=null)canonical.add(item.pkg);
            installed.removeIf(item->canonical.contains(item.pkg));installed.addAll(0,defaults);
        }

        private Item installedMatch(String[] exact, String query) {
            for (String pkg : exact) for (Item x : installed) if (pkg.equals(x.pkg)) return x;
            for (Item x : installed) if (x.name.toLowerCase(Locale.UK).contains(query)) return x;
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
                        new String[]{"org.smarttube.beta", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv"}, "smarttube",
                        0xFF45434A, 0xFF1E1E27);
                case "netflix": return favourite(id, "Netflix",
                        new String[]{"com.netflix.ninja"}, "netflix", 0xFF28242B, 0xFF07090C);
                case "disney": return favourite(id, "Disney+",
                        new String[]{"com.disney.disneyplus"}, "disney", 0xFF15356F, 0xFF071128);
                case "bbc": return favourite(id, "BBC",
                        new String[]{"uk.co.freeview.bbc", "bbc.iplayer.android", "bbc.iplayer.android.tv"}, "iplayer",
                        0xFF494A51, 0xFF25262A);
                case "live": return new Item(id, "Live TV", null, 0xFF6D45A3, 0xFF27194F, null);
                case "music": return favourite(id, "Music",
                        new String[]{"com.amazon.mp3", "com.amazon.music.tv"}, "amazon music",
                        0xFFE85C75, 0xFF8E2548);
                case "xbox": return new Item(id, "Xbox", null, 0xFF14783F, 0xFF07351E, null);
                case "tvplus": return new Item(id, "Samsung TV Plus", null, 0xFF154477, 0xFF07192F, null);
                default:
                    for (Item x : installed) if (x.id.equals(id)) return x;
                    return null;
            }
        }

        private void loadDock() {
            String defaults = "smartutube,netflix,disney,bbc,live,music";
            String room = prefs.getString("room",Build.MANUFACTURER.toLowerCase(Locale.UK).contains("sony")?"sony":"tcl");
            if(room.equals("sony")) defaults += ",xbox";
            if(room.equals("frame")) defaults += ",tvplus";
            for(String id:DockPolicy.order(room,prefs.getString("dock", defaults))) {
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
            if(e.getAction()==KeyEvent.ACTION_DOWN)inputEvent=e.getEventTime();
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
                    if (!moving && !menu) handler.postDelayed(hold, HOLD_MS);
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
            if (code == KeyEvent.KEYCODE_BACK) { goBack(); return true; }
            if (code == KeyEvent.KEYCODE_MENU) { if (!moving && !menu) controls(); return true; }
            if (menu) { if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) menuIndex = (menuIndex + 1) % 2;
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) menuIndex = (menuIndex + 1) % 2;
                invalidate(); return true; }
            if (moving) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) shift(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) shift(1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP) controls();
            else if (code == KeyEvent.KEYCODE_DPAD_DOWN) activity.startActivity(new Intent(activity,LibraryActivity.class).putExtra("shelf",true));
            else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (!dock.isEmpty()) {
                    int delta = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    index = index < 0 ? 0 : Math.max(0, Math.min(dock.size() - 1, index + delta));
                    focusAt = SystemClock.uptimeMillis();
                    handler.removeCallbacks(commitFocus);
                    // Never leave a different selected service behind the current focus.
                    former=atmosphere;atmosphere=dock.get(index);fadeAt=SystemClock.uptimeMillis();prefs.edit().putString("scene",atmosphere.id).apply();
                    announceForAccessibility(atmosphere.name);
                    handler.postDelayed(commitFocus, BG_WAIT_MS);
                }
            }
            invalidate();
            return true;
        }

        private void confirm() {
            if (menu) {
                menu = false;
                if (menuIndex == 0) { moving = true; beforeMove = new ArrayList<>(dock); lifted = index; }

            } else if (moving) {
                moving = false; lifted = -1; beforeMove = null; saveDock(); info("Home layout saved");

            } else if (index >= 0 && index < dock.size()) launch(dock.get(index));
            else info("Use Left or Right to choose an app, or Up to find apps");
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
            else { index = -1; atmosphere = null; former = null; fadeAt = 0; prefs.edit().putString("scene","neutral").apply(); }
            invalidate();
        }

        private void controls() {
            java.util.List<String> actions=new ArrayList<>(java.util.Arrays.asList("Arrange Home", "Settings", "Who's watching?", "Gallery", "Ask your TV"));
            if(!fixture.isEmpty())actions.add("Matchday Live TV - protection required");
            new android.app.AlertDialog.Builder(activity).setTitle("Home").setItems(
                actions.toArray(new String[0]), (dialog,which) -> {
                if(which==0) EditLock.ask(activity,()->{if(index<0)index=0;menu=true;menuIndex=0;invalidate();});
                if(which==1) EditLock.ask(activity,()->activity.startActivity(new Intent(activity,SettingsActivity.class)));
                if(which==2) SettingsActivity.chooseProfile(activity);
                if(which==3) activity.startActivity(new Intent(activity,GalleryActivity.class));
                if(which==4) activity.startActivity(new Intent(activity,VoiceActivity.class));
                if(which==5) VpnGate.show(activity);
            }).show();
        }
        private void launch(Item item) {
            if("music".equals(item.id)){activity.startActivity(new Intent(activity,MusicActivity.class));return;}
            if ("live".equals(item.id) || Endpoint.protectedApp(item.pkg == null ? "" : item.pkg, item.name, prefs.getString("livePackage",""))) {
                VpnGate.show(activity);
                return;
            }
            if ("xbox".equals(item.id)) {
                HouseholdActivity.action(activity,"xbox");
                return;
            }
            if ("tvplus".equals(item.id)) { HouseholdActivity.action(activity,"tvplus");return; }
            if (item.pkg == null) { info(item.name + " is not installed or configured"); return; }
            PackageManager pm = activity.getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(item.pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(item.pkg);
            if (intent == null) { info("This app has no supported launch action"); return; }
            try { activity.startActivity(intent); }
            catch (RuntimeException e) { info("Unable to open " + item.name); }
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
            drawHeader(c);
            if(!fixture.isEmpty()) {
                text(c,fixture,96,235,32,WHITE,false,Paint.Align.LEFT);
            }
            drawDock(c, now);
            if (menu) drawMenu(c);
            if (moving) drawHint(c, "Left / Right: move     OK: save     Back: cancel");

            if (messageUntil > now && !message.isEmpty()) drawNotice(c, message);
            c.restore();
            if (fadeAt != 0 && now - fadeAt < BG_FADE_MS) postInvalidateDelayed(16);
            else if (fadeAt != 0) { fadeAt = 0; former = null; }
        }

        private void drawBackground(Canvas c, long now) {
            if (atmosphere == null) { drawNeutral(c, 255); return; }
            if (fadeAt > 0 && now - fadeAt < BG_FADE_MS) {
                float progress = Math.max(0, Math.min(1, (now - fadeAt) / (float) BG_FADE_MS));
                if (former == null) drawNeutral(c, 255);
                else drawAtmosphere(c, former, 255);
                drawAtmosphere(c, atmosphere, (int) (progress * 255));
            } else drawAtmosphere(c, atmosphere, 255);
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

        private void drawAtmosphere(Canvas c, Item item, int alpha) {
            int layer=c.saveLayerAlpha(0,0,W,H,alpha);
            MaterialScene.mesh(c,W,H,item.id);c.restoreToCount(layer);
        }

        private void gradient(Canvas c, int top, int middle, int bottom, int alpha) {
            paint.setColor(Color.WHITE);
            paint.setShader(new LinearGradient(0, 0, W * .78f, H,
                    new int[]{top, middle, bottom}, new float[]{0, .7f, 1}, Shader.TileMode.CLAMP));
            paint.setAlpha(alpha);
            c.drawRect(0, 0, W, H, paint);
            paint.setAlpha(255); paint.setShader(null);
        }

        private void drawHeader(Canvas c) {
            SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.UK);
            df.setTimeZone(TimeZone.getTimeZone("Europe/London"));
            text(c, df.format(System.currentTimeMillis()), 96, 142, 66, WHITE, false, Paint.Align.LEFT);
            glass(c, 1498, 62, 1817, 145, 30, 0x6A2B303B, 0x44FFFFFF);
            text(c, VpnGate.label(activity), 1450, 114, 22, WHITE, false, Paint.Align.LEFT);
            text(c, "○", 1696, 115, 43, WHITE, false, Paint.Align.CENTER);
            text(c, "⚙", 1773, 114, 43, WHITE, false, Paint.Align.CENTER);
        }

        private void drawDock(Canvas c, long now) {
            final int count = dock.size();
            if (count == 0) {
                glass(c, 230, 795, 1690, 1000, 36, 0x92434B57, 0x70FFFFFF);
                text(c, "No Home apps. Press Up to choose some.", 960, 900, 38, WHITE, false, Paint.Align.CENTER);
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
                boolean selected = i == index;
                float scale = selected ? 1.07f : 1;
                if (selected && focusAt > 0 && !prefs.getBoolean("reduceMotion",false) && !prefs.getBoolean("lightweight",false)) {
                    float t = Math.min(1, (now - focusAt) / 185f);
                    scale = 1 + .07f * (t * (2 - t));
                    if (t < 1) postInvalidateDelayed(16);
                }
                Item item = dock.get(i);
                c.save();
                c.scale(scale, scale, x + cellWidth / 2, y + 80);
                if(selected){
                    c.save();c.translate(x+cellWidth/2,y+177);c.scale(1,.27f);
                    paint.setShader(new android.graphics.RadialGradient(0,0,155,new int[]{0x68000000,0x00000000},null,Shader.TileMode.CLAMP));
                    c.drawCircle(0,0,155,paint);c.restore();paint.setShader(null);c.translate(0,-12);
                }
                card(c, x, y, x + cellWidth, y + 161, 27, item.top, item.bottom);
                if(selected)glass(c,x,y,x+cellWidth,y+161,27,0,0);

                if (item.icon != null && item.pkg != null) {
                    int size = Math.round(Math.min(98, cellWidth - 60));
                    int cx = Math.round(x + cellWidth / 2), cy = Math.round(y + 82);
                    item.icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
                    item.icon.draw(c);
                } else {
                    String symbol = item.id.equals("smartutube") ? "▶" : item.id.equals("netflix") ? "N" :
                            item.id.equals("disney") ? "D+" : item.id.equals("bbc") ? "BBC" :
                            item.id.equals("live") ? "TV" : item.id.equals("music") ? "♫" :
                            item.id.equals("xbox") ? "X" : item.name.substring(0, Math.min(2, item.name.length()));
                    text(c, symbol, x + cellWidth / 2, y + 105, symbol.length() > 2 ? 53 : 80,
                            WHITE, true, Paint.Align.CENTER);
                }
                if (selected && prefs.getBoolean("highContrast",false)) {
                    outline.setColor(0x55FFFFFF);
                    c.drawRoundRect(x - 3, y - 3, x + cellWidth + 3, y + 164, 30, 30, outline);
                }
                c.restore();
                text(c, item.name, x + cellWidth / 2, 982, 27, WHITE, false, Paint.Align.CENTER);
            }
            if (index >= 0 && index < dock.size()) text(c, dock.get(index).name, 960, 714, 48, WHITE, false, Paint.Align.CENTER);
            Path down=new Path();down.moveTo(947,1035);down.lineTo(960,1046);down.lineTo(973,1035);
            outline.setColor(0x9acbd8e7);outline.setStrokeWidth(2);c.drawPath(down,outline);
        }

        private void drawMenu(Canvas c) {
            paint.setShader(null); paint.setColor(0x92000000); c.drawRect(0, 0, W, H, paint);
            glass(c, 625, 380, 1295, 690, 35, 0xEB242B37, 0x70FFFFFF);
            text(c, "Edit Home", 960, 465, 48, WHITE, false, Paint.Align.CENTER);
            String[] actions = {"Move", "Cancel"};
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
            String key=atmosphere==null?"neutral":atmosphere.id;
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
