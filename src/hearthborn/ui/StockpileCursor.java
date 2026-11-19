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

import haven.GItem;
import haven.GameUI;
import haven.ISBox;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;
import hearthborn.L10n;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Name and quality on the cursor after an item is taken from a stockpile.
 *
 * @author Jagy2014
 */
public final class StockpileCursor {
    private static final double WAIT = 3;
    private static final Map<GameUI, Double> pending = new WeakHashMap<>();
    private static final Map<GItem, Boolean> shown = Collections.synchronizedMap(new WeakHashMap<GItem, Boolean>());

    private StockpileCursor() {}

    public static void taking(Widget sender, String message, Object... args) {
        if(!(sender instanceof ISBox) || !takesToCursor(message, args)) return;
        Window window = sender.getparent(Window.class);
        if(window == null || !stockpile(window.cap)) return;
        GameUI gui = sender.getparent(GameUI.class);
        arm(gui);
    }

    public static boolean takesToCursor(String message, Object... args) {
        if("click".equals(message)) return true;
        if(!"xfer2".equals(message) || args.length < 2) return false;
        return Utils.iv(args[0]) < 0 && (Utils.iv(args[1]) & UI.MOD_SHIFT) == 0;
    }

    public static void arm(GameUI gui) { arm(gui, Utils.rtime() + WAIT); }

    static void arm(GameUI gui, double until) {
        if(gui == null || gui.vhand != null || (gui.hand != null && !gui.hand.isEmpty())) return;
        pending.put(gui, until);
    }

    public static void taken(GameUI gui, GItem item) {
        if(gui == null || item == null) return;
        Double until = pending.remove(gui);
        if(until != null && Utils.rtime() <= until) shown.put(item, Boolean.TRUE);
    }

    public static boolean shows(GItem item) { return item != null && shown.containsKey(item); }

    private static boolean stockpile(String caption) {
        return "Stockpile".equals(caption) || L10n.text("window", "Stockpile").equals(caption);
    }
}
