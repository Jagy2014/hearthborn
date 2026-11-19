/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.ui;

import haven.*;
import java.awt.Color;

/**
 * The accent of the interface: the colour of the active entry, the focus, the main button, the tick, the slider and the
 * window frame. Every colour of the accent comes from here and nowhere else, so a new theme is one more constant. The
 * choice takes effect at once: {@link #set} bumps {@link UiTheme#generation()} and widgets that cache what they draw
 * draw it again. Each theme has one set of colours for the dark interface and one for the parchment (light) one.
 *
 * @author Jagy2014
 */
public enum AccentTheme {
    GOLD("Gold theme", false, null,
         new Colors().accent(0xF0C35A).check(0x8FD68F).ring(0xDAB970, 0xFFEBA8),
         new Colors().accent(Parchment.ACCENT_TEXT).selected(Parchment.SELECTED).bar(Parchment.ACCENT).check(Parchment.OK)
             .primary(Parchment.BTN_PRIMARY, Parchment.BTN_PRIMARY_HOVER).ring(Parchment.LINE, Parchment.ACCENT)
             .frame(Parchment.FRAME).tab(Parchment.TAB)),
    BLUE("Blue theme", true, 0.572f,
         new Colors().accent(0x4DA6D3).selected(0x0D2231).check(0x4DA6D3).primary(0x1D6AA6, 0x2A82C6).slider(0x1D6AA6, 0x4DA6D3, 0x7CC0E3)
             .ring(0x2F86C4, 0x7CC0E3).frame(0x1D6AA6).tab(0x0D2231),
         new Colors().accent(0x1D6AA6).selected(0xC9DDEE).check(0x1D6AA6).primary(0x1D6AA6, 0x2A82C6).slider(0x1D6AA6, 0x1D6AA6, 0x2A82C6)
             .ring(0x7CC0E3, 0x1D6AA6).frame(0x1D6AA6).tab(0xC9DDEE));

    /** The colours of one theme on one background; a colour that is not set keeps the neutral look. */
    static final class Colors {
        Color accent, selected, bar, check, primary, primaryHover, sliderFill, sliderHandle, sliderLit, ring, hoverRing, frame, tab;
        Colors accent(int rgb) { return accent(new Color(rgb)); }
        Colors accent(Color c) { accent = c; return this; }
        Colors selected(int rgb) { return selected(new Color(rgb)); }
        Colors selected(Color c) { selected = c; return this; }
        Colors bar(Color c) { bar = c; return this; }
        Colors check(int rgb) { return check(new Color(rgb)); }
        Colors check(Color c) { check = c; return this; }
        Colors primary(int face, int hover) { return primary(new Color(face), new Color(hover)); }
        Colors primary(Color face, Color hover) { primary = face; primaryHover = hover; return this; }
        Colors slider(int fill, int handle, int lit) { sliderFill = new Color(fill); sliderHandle = new Color(handle); sliderLit = new Color(lit); return this; }
        Colors ring(int ring, int hover) { return ring(new Color(ring), new Color(hover)); }
        Colors ring(Color ring, Color hover) { this.ring = ring; hoverRing = hover; return this; }
        Colors frame(int rgb) { return frame(new Color(rgb)); }
        Colors frame(Color c) { frame = c; return this; }
        Colors tab(int rgb) { return tab(new Color(rgb)); }
        Colors tab(Color c) { tab = c; return this; }
    }

    /** Display name, as it is in the language catalogs. */
    public final String label;
    /** True for a theme whose windows are always drawn as a thin flat line instead of the gold moulding. */
    public final boolean flat;
    private final Float hue;
    private final Colors dark, light;

    AccentTheme(String label, boolean flat, Float hue, Colors dark, Colors light) {
        this.label = label;
        this.flat = flat;
        this.hue = hue;
        this.dark = dark;
        this.light = light;
    }

    public static final String PREF = "hearthborn/ui/accent";
    private static AccentTheme current;

    public static synchronized AccentTheme current() {
        if(current == null) {
            current = GOLD;
            String saved = Utils.getpref(PREF, "gold");
            for(AccentTheme theme : values())
                if(theme.name().equalsIgnoreCase(saved)) current = theme;
        }
        return current;
    }

    /** Switches the accent now and remembers it. */
    public static void set(AccentTheme theme) {
        synchronized(AccentTheme.class) {
            if(theme == current()) return;
            current = theme;
        }
        Utils.setpref(PREF, theme.name().toLowerCase(java.util.Locale.ROOT));
        UiTheme.refresh();
    }

    public static boolean blue() { return current() == BLUE; }
    /** Windows are drawn as a thin line: always in a flat theme, and on parchment, where the gold moulding does not belong. */
    public static boolean flatFrames() { return current().flat || UiTheme.light(); }

    private Colors set() { return UiTheme.light() ? light : dark; }

    /** Active entry, focus: text and lines. */
    public Color accent() { return set().accent; }
    /** The face of the active menu entry, or null to wash the accent over it. */
    public Color selected() { return set().selected; }
    /** The bar at the edge of the active menu entry. */
    public Color bar() { return set().bar != null ? set().bar : set().accent; }
    /** The tick of an enabled box. */
    public Color check() { return set().check; }
    /** Face of the main button, or null when it is only outlined in the accent. */
    public Color primary(boolean hover) { Colors s = set(); return hover && s.primaryHover != null ? s.primaryHover : s.primary; }
    /** Caption of the main button: white on a filled one, otherwise the ordinary ink (null). */
    public Color primaryInk() { return set().primary != null ? Color.WHITE : null; }
    /** The filled part of the slider; null keeps the neutral slider. */
    public Color sliderFill() { return set().sliderFill; }
    public Color sliderHandle(boolean lit) { Colors s = set(); return lit ? s.sliderLit : s.sliderHandle; }
    /** The line of the window frame and the caption tab. */
    public Color frame() { Colors s = set(); return s.frame != null ? s.frame : s.accent; }
    /** The fill of the caption tab. */
    public Color tab() { Colors s = set(); return s.tab != null ? s.tab : UiTheme.BACKGROUND; }
    /** The ring of buttons and system medallions. */
    public Color ring(boolean hover) { Colors s = set(); return hover ? s.hoverRing : s.ring; }
    /** What a preview of this theme on the given background shows: the same colours as {@link #frame()} and the rest, but not for the current background. */
    public Color frameOn(boolean light) { Colors s = light ? this.light : dark; return s.frame != null ? s.frame : s.accent; }
    public Color tabOn(boolean light) { Colors s = light ? this.light : dark; return s.tab != null ? s.tab : light ? Parchment.BG : Color.BLACK; }
    public Color primaryOn(boolean light) { return (light ? this.light : dark).primary; }
    public Color accentOn(boolean light) { return (light ? this.light : dark).accent; }
    /** The colour of the caption of a window of this theme. */
    public Color title() { return accent(); }
    /** The hue the gold pictures (title, close button) are moved to, or null to keep them. */
    public Float hue() { return hue; }
}
