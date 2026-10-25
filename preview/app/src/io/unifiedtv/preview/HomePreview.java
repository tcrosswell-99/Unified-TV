package io.unifiedtv.preview;

/**
 * SET-GLASS-09 / GLASS-04: which saved settings change how Home looks, so that Home, paused under the translucent Settings
 * sheet, shows an appearance change at once without moving (module 13 §A). Pure: Home's preference listener asks this
 * what a changed key needs, and does only that. Nothing here re-lays Home out: the dock, labels, light and clock are drawn
 * in place, so a change repaints them where they already are.
 *
 * <ul>
 * <li>{@link #DOCK}: the dock's tiles or size (the household's list, this profile's 6 or 8 places, or the profile itself,
 *     which brings that profile's size and labels).</li>
 * <li>{@link #LIGHT}: the app light behind Home ({@link AmbientLayer#MOTION_KEYS}: Reduce motion, Keep still, Lightweight).</li>
 * <li>{@link #PILL}: the utility pill's icons and order.</li>
 * <li>{@link #CLOCK}: the household's time zone, so the clock shows the new time.</li>
 * <li>{@link #LOOK}: read as Home draws (label visibility, high contrast, reduced transparency, text size): a repaint.</li>
 * <li>{@link #ALL}: every setting at once (Android reports a cleared file with no key).</li>
 * </ul>
 * The Wallpaper choice is deliberately not here: active Home never shows the Wallpaper (module 05, "no photographic
 * wallpaper behind the active dock"), so there is nothing behind the sheet to change; it is read when Home next idles.
 */
final class HomePreview {
    private HomePreview(){}
    static final int NONE=0,LOOK=1,DOCK=2,LIGHT=3,PILL=4,CLOCK=5,ALL=6;

    static int of(String key){
        if(key==null)return ALL;
        if(key.equals("dock")||key.equals(Profiles.PREF)||key.startsWith(DockCapacity.KEY))return DOCK;
        if(AmbientLayer.MOTION_KEYS.contains(key))return LIGHT;
        if(key.equals(HomeUtilities.PREF))return PILL;
        if(key.equals(HouseholdSettings.ZONE))return CLOCK;
        if(key.startsWith(DockLabels.KEY)||key.equals("highContrast")||key.equals("opaque")||key.equals(Accessibility.TEXT_SIZE))return LOOK;
        return NONE;
    }
}
