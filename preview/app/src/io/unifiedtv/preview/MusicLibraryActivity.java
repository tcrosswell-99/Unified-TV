package io.unifiedtv.preview;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Button;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * "Your Amazon Music" (OD-27, best effort): the playlists, recently played and other shelves Amazon Music's own
 * MediaBrowserService shares, as rows with each entry's own artwork. OK on a shelf opens it in the same sheet; OK on a
 * track or playlist asks Amazon Music to play it (playFromMediaId) and Now Playing shows it. Choosing what to play is a
 * page of choices, so this is the glass sidebar over a still Home (OD-19), not a full-screen page.
 *
 * <p>If Amazon Music refuses to connect, has nothing to share, or doesn't answer within MusicLibrary.ANSWER_MS, the page
 * says so plainly and the Music page hides this entry; the result is kept for Owner diagnostics. Kids: only when an adult
 * approved Amazon Music; no content filter of Unified TV's own is invented.
 */
public final class MusicLibraryActivity extends GlassScreen implements MusicLibrary.Listener {
    static volatile MusicLibrary.Factory library = MusicLibrary.Browser::open;
    static final String TITLE = "Your Amazon Music", REFUSED_WORDS = "Amazon Music doesn't share its library with other apps on this TV. Search by name on the Music page instead.";
    static final int ART_PX = 88;
    private MusicLibrary lib; private String root;
    private final ArrayDeque<String[]> trail = new ArrayDeque<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean gone;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        page(TITLE, "Amazon Music's own library");
        String no = Launch.kidsRefusal(this, AmazonMusic.installed(this) == null ? "" : AmazonMusic.installed(this), AmazonMusic.NAME);
        if (no != null) { card(); text(no, 20); button("Back", this::finish); return; }
        lib = library.open(this, this);
        if (lib == null) { refused(); return; }
        card(); busy("Opening your Amazon Music library…");
        lib.connect();
    }

    @Override public void connected(String rootId) { if (gone) return; root = rootId; trail.clear(); trail.push(new String[]{rootId, TITLE}); lib.children(rootId); }
    @Override public void refused() {
        if (gone) return;
        AmazonMusic.browsing(this, false);
        page(TITLE, null); card(); text(REFUSED_WORDS, 20); button("Back", this::finish);
    }
    @Override public void children(String parent, List<MusicLibrary.Item> items) {
        if (gone) return;
        if (parent.equals(root) && items.isEmpty()) { refused(); return; }
        if (parent.equals(root)) AmazonMusic.browsing(this, true);
        String[] here = trail.peek();
        page(here == null ? TITLE : here[1], parent.equals(root) ? "Amazon Music's own library" : TITLE);
        card();
        if (items.isEmpty()) text("Nothing here yet.", 20);
        int size = px(ART_PX);
        for (MusicLibrary.Item item : items) {
            String chip = item.browsable ? null : "Play";
            Button b = row(item.title.isEmpty() ? "Untitled" : item.title, chip, item.browsable, () -> choose(item));
            if (!item.subtitle.isEmpty()) b.setText(b.getText() + "\n" + item.subtitle);
            Drawable blank = new ColorDrawable(0x33FFFFFF); blank.setBounds(0, 0, size, size);
            b.setCompoundDrawablesRelative(blank, null, b.getCompoundDrawablesRelative()[2], null);
            b.setCompoundDrawablePadding(px(20));
            if (item.icon != null || item.iconUri != null) art(b, item, size);
        }
    }
    /** The entry's own picture, decoded off the main thread at the row's size. */
    private void art(Button b, MusicLibrary.Item item, int size) {
        try {
            worker.execute(() -> {
                Bitmap bmp = MusicArt.load(getApplicationContext(), item.icon, item.iconUri, size, size);
                if (bmp == null) return;
                runOnUiThread(() -> { if (gone) return; Drawable d = new BitmapDrawable(getResources(), bmp); d.setBounds(0, 0, size, size);
                    b.setCompoundDrawablesRelative(d, null, b.getCompoundDrawablesRelative()[2], null); });
            });
        } catch (RejectedExecutionException ignored) { /* closing */ }
    }
    private void choose(MusicLibrary.Item item) {
        if (item.browsable) { trail.push(new String[]{item.id, item.title}); page(item.title, TITLE); card(); busy("Opening…"); lib.children(item.id); return; }
        if (!item.playable) { note("Amazon Music doesn't play that from here."); return; }
        if (!lib.play(item.id)) { note("Amazon Music didn't accept that. Try it in the Amazon Music app."); return; }
        startActivity(NowPlayingActivity.waiting(this, item.title, AmazonMusic.Focus.ANY, AmazonMusic.Route.LIBRARY, ""));
    }
    @Override public void onBackPressed() {
        if (lib != null && trail.size() > 1) { trail.pop(); String[] up = trail.peek(); page(up[1], null); card(); busy("Opening…"); lib.children(up[0]); return; }
        super.onBackPressed();
    }
    @Override protected void onDestroy() { gone = true; if (lib != null) lib.close(); worker.shutdownNow(); super.onDestroy(); }
}
