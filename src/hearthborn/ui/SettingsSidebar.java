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
import java.util.*;

/**
 * The settings window as one fixed-size sheet: the section buttons in a column on the left, the open section in a
 * scrolling pane on the right. Turn it off with the {@code hearthborn/settings/sidebar} preference to get the old
 * window that changes size for each section.
 *
 * @author Jagy2014
 */
public final class SettingsSidebar {
    public static final String PREF = "hearthborn/settings/sidebar";
    private SettingsSidebar() {}

    /** The groups of the section list: the heading, then the sections in the order they are listed. */
    private static final String[][] GROUPS = {
        {"INTERFACE", "Interface display", "Interface scale", "Item quality display", "Flower menu settings", "Keybindings"},
        {"GRAPHICS AND SOUND", "Video settings", "Camera", "World graphics", "Sound and notices"},
        {"GAME", "General settings", "Combat settings", "Plants and production", "Containers and contents", "Mining safety", "Auto-Drop Manager", "Object radii"},
    };
    static final Text.Foundry HEADING_FACE = new Text.Foundry(Text.sans, 10).aa(true);

    public static boolean enabled() { return Utils.getprefb(PREF, true); }

    /** Wide enough for the widest section (General settings, 820 in Russian). */
    public static int paneWidth() { return UI.scale(830); }
    private static int gap() { return UI.scale(12); }
    private static int rowGap() { return UI.scale(3); }

    /** The untranslated name a section button was made with. */
    private static String key(Button b) {
        if(b instanceof OptWnd.PButton) return ((OptWnd.PButton)b).title();
        return b.text != null && b.text.text.equals(hearthborn.L10n.text("button", "Interface display")) ? "Interface display" : "";
    }

    /** Lays the main panel's buttons out in one column and returns the empty pane for the sections. */
    public static Scrollport arrange(OptWnd options, SettingsSearch search) {
        List<Button> left = new ArrayList<>(), right = new ArrayList<>();
        for(Widget w = options.main.child; w != null; w = w.next)
            if(w instanceof Button) (w.c.x > 0 ? right : left).add((Button)w);
        left.sort(Comparator.comparingInt(b -> b.c.y));
        right.sort(Comparator.comparingInt(b -> b.c.y));
        // The left column holds the system sections first and, after a gap, the session buttons.
        int step = Integer.MAX_VALUE;
        for(int i = 1; i < left.size(); i++) step = Math.min(step, left.get(i).c.y - left.get(i - 1).c.y);
        List<Button> system = new ArrayList<>(), session = new ArrayList<>();
        for(int i = 0; i < left.size(); i++) {
            boolean after = !session.isEmpty() || (i > 0 && left.get(i).c.y - left.get(i - 1).c.y > step * 3 / 2);
            (after ? session : system).add(left.get(i));
        }
        // One list: the two old columns merged, with the related interface entries side by side.
        List<Button> sections = new ArrayList<>(system);
        sections.addAll(right);
        // The sections come in groups, each under a small grey heading; a section nobody listed joins the last group.
        Map<String, Button> byKey = new HashMap<>();
        for(Button b : sections) byKey.put(key(b), b);
        List<List<Button>> grouped = new ArrayList<>();
        Set<Button> placed = new HashSet<>();
        for(String[] group : GROUPS) {
            List<Button> list = new ArrayList<>();
            for(int i = 1; i < group.length; i++) {
                Button b = byKey.get(group[i]);
                if(b != null) { list.add(b); placed.add(b); }
            }
            grouped.add(list);
        }
        for(Button b : sections) if(!placed.contains(b)) grouped.get(grouped.size() - 1).add(b);
        int width = UI.scale(180);
        for(Button b : sections) width = Math.max(width, b.sz.x);
        width += UI.scale(14); // the longest name must not run into the line that closes the list
        int y = search.bottom();
        for(int g = 0; g < GROUPS.length; g++) {
            if(grouped.get(g).isEmpty()) continue;
            if(g > 0) y += UI.scale(8);
            Label heading = options.main.add(new Label(GROUPS[g][0], HEADING_FACE).tier(UiPalette.NOTE),
                                             Coord.of(ButtonLook.menuPad(), y));
            y += heading.sz.y + UI.scale(4);
            for(Button b : grouped.get(g)) {
                b.look(Button.Look.MENU);
                b.resize(Coord.of(width, b.sz.y));
                b.move(Coord.of(0, y));
                y += b.sz.y + rowGap();
            }
        }
        int bodyHeight = y - rowGap();
        int total = width + gap() + paneWidth();
        // Session buttons run along the bottom of the whole sheet.
        int barY = bodyHeight + UI.scale(10), bar = 0;
        for(Button b : session) bar = Math.max(bar, b.sz.y);
        final int barTop = barY;
        BARS.put(options, wide -> spread(session, wide, barTop));
        spread(session, total, barY);
        int bottom = session.isEmpty() ? bodyHeight : barY + bar;
        Coord whole = Coord.of(total, bottom);
        // The sheet draws the list's own panel and the lines between list, content and the bottom buttons.
        final int listWidth = width, listHeight = bodyHeight, barLine = session.isEmpty() ? -1 : barY - UI.scale(5);
        options.main.add(new Widget(whole) {
            public boolean checkhit(Coord c) { return false; }
            public void draw(GOut g) {
                g.chcolor(UiTheme.light() ? new java.awt.Color(0, 0, 0, 16) : new java.awt.Color(255, 255, 255, 9));
                g.frect(Coord.z, Coord.of(listWidth, listHeight));
                g.chcolor(UiPalette.border());
                int line = Math.max(1, UI.scale(1));
                g.frect(Coord.of(listWidth, 0), Coord.of(line, listHeight));
                if(barLine >= 0) g.frect(Coord.of(0, barLine), Coord.of(sz.x, line));
                g.chcolor();
            }
        }, Coord.z).lower();
        Scrollport pane = new Scrollport(Coord.of(paneWidth(), bodyHeight));
        search.sidebar(width, Coord.of(width + gap(), 0), paneWidth(), pane);
        pane.c = Coord.of(width + gap(), 0);
        return pane;
    }

    private static final Map<OptWnd, java.util.function.IntConsumer> BARS = new java.util.WeakHashMap<>();
    /** The bottom buttons share the whole width of the sheet, so that none of them stops short of the right edge. */
    private static void spread(List<Button> row, int total, int y) {
        if(row.isEmpty()) return;
        int space = gap() / 2, each = Math.max(1, (total - space * (row.size() - 1)) / row.size()), x = 0;
        for(int i = 0; i < row.size(); i++) {
            Button b = row.get(i);
            int width = i == row.size() - 1 ? Math.max(1, total - x) : each;
            b.resize(Coord.of(width, b.sz.y));
            b.move(Coord.of(x, y));
            x += width + space;
        }
    }

    /** Makes the pane (and the search results over it) this wide, and the sheet with it. */
    public static void narrow(OptWnd options, SettingsSearch search, int width) {
        Scrollport pane = options.pane;
        if(pane == null || width >= pane.sz.x) return;
        int total = pane.c.x + width;
        pane.resize(Coord.of(width, pane.sz.y));
        search.sidebar(pane.c.x - gap(), pane.c, width, pane);
        options.main.resize(Coord.of(total, options.main.sz.y));
        java.util.function.IntConsumer bar = BARS.get(options);
        if(bar != null) bar.accept(total);
    }
}
