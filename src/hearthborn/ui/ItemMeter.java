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
import haven.Widget;
import haven.Window;
import hearthborn.L10n;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Icon percent for an item. A cooking pot shows its own meter and keeps the item's ripening meter off the icon.
 *
 * @author Jagy2014
 */
public final class ItemMeter {
    private static final Map<GItem, Reading> readings = new WeakHashMap<>();

    private ItemMeter() {}

    public static void widget(GItem item, int value) {
        if(item == null) return;
        reading(item).widget(value);
    }

    public static void info(GItem item, Double value) {
        if(item == null) return;
        reading(item).info(value);
    }

    public static Double shown(GItem item, boolean cooking) {
        Reading reading = item == null ? null : readings.get(item);
        if(reading == null) return item != null && item.meter > 0 ? item.meter / 100.0 : null;
        return reading.shown(cooking);
    }

    public static boolean cooking(Widget widget) {
        Window window = widget == null ? null : widget.getparent(Window.class);
        if(window == null || window.cap == null) return false;
        return named(window.cap, "Cauldron") || named(window.cap, "Oven");
    }

    /** In a pot the server meter is how cooked the item is, including zero. Elsewhere a positive meter wins. */
    public static Double shown(boolean cooking, boolean widgetSet, int widget, Double info) {
        if(cooking && widgetSet) return widget / 100.0;
        if(widget > 0) return widget / 100.0;
        return info;
    }

    private static boolean named(String caption, String english) {
        if(caption.equalsIgnoreCase(english)) return true;
        String translated = L10n.find("window", english);
        return translated != null && caption.equals(translated);
    }

    private static Reading reading(GItem item) {
        Reading reading = readings.get(item);
        if(reading == null) readings.put(item, reading = new Reading());
        return reading;
    }

    /** Testable progress of one item. */
    public static final class Reading {
        private boolean widgetSet;
        private int widget;
        private Double info;

        public void widget(int value) {
            widget = value;
            widgetSet = true;
        }

        public void info(Double value) {
            info = value;
        }

        public Double shown(boolean cooking) {
            return ItemMeter.shown(cooking, widgetSet, widget, info);
        }
    }
}
