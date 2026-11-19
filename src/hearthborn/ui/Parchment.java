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
 * The tokens of the parchment theme, the light look of the interface. Every light colour of the interface comes from
 * here. There is no pure white: white is only the text of the main button.
 *
 * @author Jagy2014
 */
public final class Parchment {
    private Parchment() {}

    /** Window background. */
    public static final Color BG = new Color(0xF4ECD8);
    /** Fields, lists, buttons. */
    public static final Color PANEL = new Color(0xFBF6E9);
    /** The chosen row. */
    public static final Color SELECTED = new Color(0xEBDFC2);
    /** The caption tab of a window. */
    public static final Color TAB = new Color(0xE6D9B8);
    /** Lines and grids. */
    public static final Color LINE = new Color(0xCDBF9F);
    /** The track of a scroll bar. */
    public static final Color SCROLL_TRACK = new Color(0xE0D4B6);
    /** The frame of a box that is not ticked: a tone darker than {@link #LINE}, so it can be seen. */
    public static final Color CHECK_LINE = new Color(0xB8A984);
    /** The tooltip of a light theme is inverted: a dark brown plate with cream text, so it stands out from the sheet. */
    public static final Color TIP_BG = new Color(0x2B2218);
    public static final Color TIP_TEXT = new Color(0xF4ECD8);
    /** The source line and other links of a tooltip: warm amber, bright enough for the dark plate. */
    public static final Color TIP_LINK = new Color(0xE0B060);
    /** Window frame. */
    public static final Color FRAME = new Color(0x8A6A42);
    public static final Color TEXT = new Color(0x2B2218);
    public static final Color TEXT2 = new Color(0x6B5D48);
    /** Hints. */
    public static final Color DIM = new Color(0x9A8C74);
    /** Bars and washes. */
    public static final Color ACCENT = new Color(0xC4701C);
    /** The accent as text. */
    public static final Color ACCENT_TEXT = new Color(0x9A4F14);
    /** The main button; its text is white. */
    public static final Color BTN_PRIMARY = new Color(0xA85A10);
    public static final Color BTN_PRIMARY_HOVER = new Color(0xB8651A);
    public static final Color OK = new Color(0x4F7A2C);
    public static final Color DANGER = new Color(0xB03A2A);
    /** Quality colours on parchment. */
    public static final Color Q_GREEN = new Color(0x3F7A2A);
    public static final Color Q_PURPLE = new Color(0x6B45B0);
}
