/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 */

package hearthborn.combat;

import haven.*;
import hearthborn.L10n;
import hearthborn.scripts.ShortcutRunner;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** A local targeting preview: cycling and mouse picking never send game commands. */
public final class TargetPreview {
    private static final Map<GameUI, Selection> selections = new WeakHashMap<>();

    private static final class Selection {
        WeakReference<Gob> target;
        boolean picking;
        int revision;

        Gob target() { return target == null ? null : target.get(); }

        void mark(Gob next) {
            Gob previous = target();
            if(previous != null) mark(previous,false);
            target = next == null ? null : new WeakReference<>(next);
            if(next != null) mark(next,true);
        }

        private void mark(Gob gob, boolean marked) {
            FighterMark highlight = gob.getattr(FighterMark.class);
            if(highlight != null) highlight.preview(marked);
        }
    }

    private TargetPreview() {}

    public static boolean clear(GameUI gui) {
        Selection selection = selections.remove(gui);
        if(selection == null) return false;
        selection.mark(null);
        return true;
    }

    public static boolean marked(GameUI gui) {
        Selection selection = selections.get(gui);
        return selection != null && (selection.target != null || selection.picking);
    }

    private static boolean ready(GameUI gui) {
        return gui != null && gui.map != null && gui.ui != null && gui.ui.sess != null;
    }

    private static Coord onScreen(GameUI gui, Gob gob) {
        try {
            if(!ShortcutRunner.wildTarget(gui, gob)) return null;
            return gui.map.targetScreenPoint(gob, 0);
        } catch(Loading pending) {
            return null;
        }
    }

    private static Gob resolve(GameUI gui) {
        if(!ready(gui)) return null;
        Selection selection = selections.get(gui);
        Gob target = selection == null ? null : selection.target();
        if(target == null || gui.ui.sess.glob.oc.getgob(target.id) != target) return null;
        return onScreen(gui, target) == null ? null : target;
    }

    static Gob forAction(GameUI gui) {
        Selection selection = selections.get(gui);
        if(selection != null && selection.picking) {
            gui.ui.msg(L10n.tr("Wait for the target to be marked."));
            return null;
        }
        Gob target = resolve(gui);
        if(target == null) {
            clear(gui);
            gui.ui.msg(L10n.tr("No marked wild animal on screen."));
        }
        return target;
    }

    public static void cycle(GameUI gui, boolean reverse) {
        if(!ready(gui)) return;
        Gob player = gui.map.player();
        if(player == null || player.rc == null) return;
        OCache cache = gui.ui.sess.glob.oc;
        List<Gob> candidates = new ArrayList<>();
        synchronized(cache) { for(Gob gob : cache) candidates.add(gob); }
        candidates.removeIf(gob -> gob == player || onScreen(gui, gob) == null);
        candidates.sort(Comparator.<Gob>comparingDouble(gob -> gob.rc.dist(player.rc)).thenComparingLong(gob -> gob.id));
        if(candidates.isEmpty()) {
            clear(gui);
            gui.ui.msg(L10n.tr("No wild animals on screen."));
            return;
        }
        Selection selection = selections.computeIfAbsent(gui, key -> new Selection());
        int current = candidates.indexOf(selection.target());
        int next = current < 0 ? 0 : Math.floorMod(current + (reverse ? -1 : 1), candidates.size());
        selection.mark(candidates.get(next));
        selection.picking = false;
        selection.revision++;
    }

    public static void underCursor(GameUI gui) {
        if(!ready(gui)) return;
        MapView map = gui.map;
        Coord at = map.rootxlate(gui.ui.mc);
        if(!at.isect(Coord.z, map.sz)) return;
        Selection selection = selections.computeIfAbsent(gui, key -> new Selection());
        int revision = ++selection.revision;
        selection.picking = true;
        map.pickTargetPreview(at, gob -> {
            if(selections.get(gui) != selection || selection.revision != revision) return;
            if(gui.map != map) {
                clear(gui);
                return;
            }
            selection.picking = false;
            if(onScreen(gui, gob) != null) selection.mark(gob);
            else {
                if(selection.target == null) clear(gui);
                gui.ui.msg(L10n.tr("Point at a living wild animal."));
            }
        });
    }

    public static void attack(GameUI gui) {
        if(!ready(gui)) return;
        Gob target = forAction(gui);
        if(target != null && WildAttack.request(gui, target)) clear(gui);
    }
}
