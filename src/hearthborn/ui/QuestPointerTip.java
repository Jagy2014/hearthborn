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
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds live distance to the server's location-pointer tooltip.
 *
 * @author Jagy2014
 */
public final class QuestPointerTip {
    private static final Map<Widget, Cached> cache = new WeakHashMap<>();
    private static class Cached {
        final String text;
        final boolean rich;
        final Text rendered;
        Cached(String text, boolean rich) {
            this.text = text;
            this.rich = rich;
            rendered = rich ? RichText.render(text, UI.scale(300)) : Text.render(text);
        }
    }
    private static boolean pointer(Widget widget) {
        for(Class<?> type = widget.getClass(); type != null; type = type.getSuperclass()) {
            FromResource source = type.getAnnotation(FromResource.class);
            if((source != null && source.name().equals("ui/locptr")) ||
               type.getName().equals("haven.res.ui.locptr.Pointer")) return true;
        }
        return false;
    }
    private static Object field(Widget widget, String name) throws ReflectiveOperationException {
        Field field = widget.getClass().getField(name);
        return field.get(widget);
    }
    public static Object decorate(Widget widget, Object original) {
        if(widget == null || original == null || !pointer(widget)) return original;
        String name;
        boolean rich = false;
        if(original instanceof Widget.KeyboundTip) {
            Widget.KeyboundTip tip = (Widget.KeyboundTip)original;
            name = tip.base;
            rich = tip.rich;
        } else if(original instanceof String) {
            name = (String)original;
        } else return original;
        if(name == null || name.isEmpty()) return original;
        GameUI gui = widget.getparent(GameUI.class);
        if(gui == null || gui.map == null) return original;
        try {
            Gob player = gui.map.player();
            if(player == null) return original;
            Coord2d target = (Coord2d)field(widget, "tc");
            long id = ((Number)field(widget, "gobid")).longValue();
            if(id != -1 && gui.ui != null && gui.ui.sess != null) {
                Gob gob = gui.ui.sess.glob.oc.getgob(id);
                if(gob != null) target = gob.rc;
            }
            if(target == null) return original;
            double distance = player.rc.dist(target) / MCache.tilesz.x;
            if(!Double.isFinite(distance)) return original;
            String text = name + String.format(Locale.ROOT, " (%.1f %s)", distance,
                hearthborn.L10n.language().equals("ru") ? "м" : "m");
            Cached cached = cache.get(widget);
            if(cached == null || !cached.text.equals(text) || cached.rich != rich) {
                if(cached != null) cached.rendered.tex().dispose();
                cached = new Cached(text, rich);
                cache.put(widget, cached);
            }
            return cached.rendered.tex();
        } catch(ReflectiveOperationException | ClassCastException | Loading ignored) {
            return original;
        }
    }
}
