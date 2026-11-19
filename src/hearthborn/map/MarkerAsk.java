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

package hearthborn.map;

import haven.*;
import hearthborn.L10n;
import java.awt.Color;
import java.util.function.BiConsumer;

/**
 * Name and color prompt for a marker placed from the compact map.
 *
 * @author Jagy2014
 */
public final class MarkerAsk {
    private static Window open;

    private MarkerAsk() {}

    public static void open(GameUI gui, Coord at, BiConsumer<String, Color> save) {
        if(gui == null || save == null) return;
        if(open != null) open.reqdestroy();
        Window dialog = new Window(Coord.z, L10n.tr("New marker"));
        open = dialog;
        int x = UI.scale(8);
        TextEntry name = dialog.add(new TextEntry(UI.scale(200), "") {
            {dshow = true;}
            public void activate(String text) {MarkerAsk.accept(dialog, text, dialog.getchild(BuddyWnd.GroupSelector.class), save);}
        }, Coord.of(x, UI.scale(8)));
        BuddyWnd.GroupSelector colors = dialog.add(new BuddyWnd.GroupSelector(0), Coord.of(x, name.c.y + name.sz.y + UI.scale(8)));
        int y = colors.c.y + colors.sz.y + UI.scale(8);
        dialog.add(new Button(UI.scale(96), "Save", false, () -> accept(dialog, name.text(), colors, save)), Coord.of(x, y));
        dialog.add(new Button(UI.scale(96), "Cancel", false, dialog::reqdestroy), Coord.of(x + UI.scale(104), y));
        dialog.pack();
        gui.add(dialog);
        int maxX = Math.max(0, gui.sz.x - dialog.sz.x);
        int maxY = Math.max(0, gui.sz.y - dialog.sz.y);
        dialog.move(Coord.of(Math.max(0, Math.min(at.x, maxX)), Math.max(0, Math.min(at.y, maxY))));
        gui.setfocus(name);
    }

    private static void accept(Window dialog, String title, BuddyWnd.GroupSelector colors, BiConsumer<String, Color> save) {
        if(dialog.parent == null) return;
        String name = title == null ? "" : title.trim();
        if(name.isEmpty()) name = "New marker";
        int group = colors == null ? 0 : colors.group;
        if(group < 0 || group >= BuddyWnd.gc.length) group = 0;
        dialog.reqdestroy();
        save.accept(name, BuddyWnd.gc[group]);
    }
}
