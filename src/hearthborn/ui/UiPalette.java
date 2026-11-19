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

import java.awt.Color;

/**
 * The colours of the settings window, in one place. Yellow means exactly one thing, "active or main": the open section,
 * a focused field, the main button. The accent colours come from {@link AccentTheme}. Everything else is neutral. The light theme keeps the same roles on parchment.
 *
 * @author Jagy2014
 */
public final class UiPalette {
    private UiPalette() {}

    /** Dark theme, as designed. */
    public static final Color BACKGROUND = new Color(0x0B, 0x0B, 0x0B);
    public static final Color TEXT = new Color(0xD8, 0xD3, 0xC4);
    public static final Color TEXT2 = new Color(0xA8, 0xA3, 0x94);
    public static final Color HINT = new Color(0x6F, 0x6B, 0x60);
    public static final Color BORDER = new Color(0x3A, 0x3A, 0x3A);
    public static final Color DANGER = new Color(0xE8, 0x60, 0x4C);

    /** Light theme: the same roles in colours that read on parchment. */
    private static final Color L_TEXT = Parchment.TEXT, L_TEXT2 = Parchment.TEXT2, L_HINT = Parchment.DIM;
    private static final Color L_BORDER = Parchment.LINE;
    private static final Color L_DANGER = Parchment.DANGER;

    private static boolean light() { return UiTheme.light(); }

    public static Color text() { return light() ? L_TEXT : TEXT; }
    public static Color text2() { return light() ? L_TEXT2 : TEXT2; }
    public static Color hint() { return light() ? L_HINT : HINT; }
    public static Color border() { return light() ? L_BORDER : BORDER; }
    /** A neutral line a step lighter, for a hovered field. */
    public static Color borderLit() { return light() ? Parchment.DIM : new Color(0x5A, 0x57, 0x50); }
    public static Color accent() { return AccentTheme.current().accent(); }
    public static Color danger() { return light() ? L_DANGER : DANGER; }
    public static Color check() { return AccentTheme.current().check(); }
    /** The panel behind a field. */
    public static Color field() { return light() ? Parchment.PANEL : new Color(0x12, 0x12, 0x11); }

    public static Color withAlpha(Color color, int alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha); }
    /** The track of a scroll bar. */
    public static Color scrollTrack() { return light() ? Parchment.SCROLL_TRACK : new Color(0x1A, 0x1A, 0x19); }
    /** The thumb of a scroll bar: a line colour, the frame colour under the pointer. */
    public static Color scrollThumb(boolean hover) { return light() ? (hover ? Parchment.FRAME : Parchment.LINE) : (hover ? borderLit() : border()); }
    /** The frame of an unticked box. */
    public static Color checkLine() { return light() ? Parchment.CHECK_LINE : border(); }
    /** Light wash over a hovered row. */
    public static Color hoverFill() { return light() ? new Color(0x2B, 0x22, 0x18, 18) : new Color(255, 255, 255, 16); }
    /** Light wash of the accent behind the open section. */
    public static Color accentFill() {
        Color face = AccentTheme.current().selected();
        return face != null ? face : withAlpha(accent(), light() ? 46 : 34);
    }
    /** The dark red face of the log out button. */
    public static Color dangerFill() { return light() ? new Color(0xF1, 0xDA, 0xCF) : new Color(0x3A, 0x15, 0x10); }

    /** Text tiers of the settings window: names and titles, captions, explanations. */
    public static final int TITLE = 1, CAPTION = 2, NOTE = 3;
    public static Color tier(int tier) {
        return tier == TITLE ? text() : tier == CAPTION ? text2() : hint();
    }
}
