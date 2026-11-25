/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.journal;

import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.Text;
import java.awt.Color;

final class JournalStyle {
    static final Color PANEL = new Color(29, 26, 21);
    static final Color SURFACE = new Color(22, 20, 16);
    static final Color TILE = new Color(33, 29, 23);
    static final Color EDGE = new Color(65, 56, 41);
    static final Color HOVER = new Color(43, 37, 27);
    static final Color SELECTED = new Color(49, 40, 21);
    static final Color INK = new Color(232, 224, 205);
    static final Color MUTED = new Color(174, 163, 140);
    static final Color FAINT = new Color(131, 122, 106);
    static final Color GOLD = new Color(237, 198, 106);
    static final Text.Foundry BODY = new Text.Foundry(Text.sans, 12).aa(true);
    static final Text.Foundry DETAIL = new Text.Foundry(Text.sans, 11).aa(true);
    static final Text.Foundry TITLE = new Text.Foundry(Text.serif, 18).aa(true);

    static void fill(GOut g, Coord pos, Coord size, Color color) {
        g.chcolor(color);
        g.frect(pos, size);
        g.chcolor();
    }

    static void border(GOut g, Coord pos, Coord size, Color color) {
        g.chcolor(color);
        g.rect(pos, size);
        g.chcolor();
    }

    static Label label(String text, int width, Text.Foundry font, Color color) {
        Label label = new Label(text, Math.max(1, width), font);
        label.setcolor(color);
        return label;
    }

    private JournalStyle() {}
}
