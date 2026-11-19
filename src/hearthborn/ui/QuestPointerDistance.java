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
import java.awt.Font;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tile distance drawn on the quest location marker.
 *
 * @author Jagy2014
 */
public final class QuestPointerDistance {
    private static final Text.Foundry FONT = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 12);
    private static final Map<Widget, Mark> marks = new WeakHashMap<>();

    private static final class Mark {
        int distance = Integer.MIN_VALUE;
        Tex texture;
    }

    public static int tiles(Coord2d from, Coord2d to) {
        if(from == null || to == null) return -1;
        double distance = from.dist(to) / MCache.tilesz.x;
        if(!Double.isFinite(distance)) return -1;
        return (int)Math.round(distance);
    }

    public static void paint(GOut g, Widget widget) {
        if(widget == null || !widget.getClass().getName().contains(".locptr.")) return;
        try {
            Object point = widget.getClass().getField("lc").get(widget);
            if(!(point instanceof Coord)) return;
            GameUI gui = widget.getparent(GameUI.class);
            if(gui == null || gui.map == null) return;
            Gob player = gui.map.player();
            if(player == null) return;
            int distance = tiles(player.rc, target(widget, gui));
            if(distance < 0) return;
            g.aimage(texture(widget, distance), (Coord)point, .5, .5);
        } catch(ReflectiveOperationException | ClassCastException ignored) {}
    }

    public static void forget(Widget widget) {
        Mark mark;
        synchronized(marks) { mark = marks.remove(widget); }
        if(mark != null && mark.texture != null) mark.texture.dispose();
    }

    private static Coord2d target(Widget widget, GameUI gui) throws ReflectiveOperationException {
        Object value = widget.getClass().getField("tc").get(widget);
        Coord2d target = value instanceof Coord2d ? (Coord2d)value : null;
        try {
            long id = ((Number)widget.getClass().getField("gobid").get(widget)).longValue();
            if(id != -1 && gui.ui != null && gui.ui.sess != null) {
                Gob gob = gui.ui.sess.glob.oc.getgob(id);
                if(gob != null) target = gob.rc;
            }
        } catch(NoSuchFieldException ignored) {}
        return target;
    }

    private static Tex texture(Widget widget, int distance) {
        Mark mark;
        synchronized(marks) {
            mark = marks.get(widget);
            if(mark == null) marks.put(widget, mark = new Mark());
        }
        if(mark.distance != distance || mark.texture == null) {
            if(mark.texture != null) mark.texture.dispose();
            mark.texture = new TexI(Utils.outline2(FONT.render(Integer.toString(distance), Color.WHITE).img, Color.BLACK));
            mark.distance = distance;
        }
        return mark.texture;
    }

    private QuestPointerDistance() {}
}
