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

package hearthborn.scripts;
import hearthborn.navigation.LocalPathfinder;

import haven.*;
import java.util.*;

/**
 * Harvest nearby dream catchers, visiting each at most twice.
 *
 * @author Jagy2014
 */
public final class DreamHarvester {
    private final GameUI gui;
    private final List<Gob> pending = new ArrayList<>();
    private final Set<Long> unreachable = new HashSet<>();
    private List<Coord2d> path;
    private int waypoint;
    private Coord2d lastPosition;
    private double stalled;
    private Gob target;
    private MapView map;
    private FlowerMenu selected;
    private int attempts;
    private double remaining;

    public DreamHarvester(GameUI gui) { this.gui = gui; }

    public static Gob nearest(Iterable<Gob> objects, Coord2d player) {
        Gob nearest = null;
        for(Gob gob : objects) {
            if(gob.removed) continue;
            try {
                Drawable drawable = gob.getattr(Drawable.class);
                Resource res = drawable == null ? null : drawable.getres();
                if(res != null && res.name.startsWith("gfx/terobjs/dreca") &&
                   (nearest == null || gob.rc.dist(player) < nearest.rc.dist(player))) nearest = gob;
            } catch(Loading ignored) { }
        }
        return nearest;
    }

    public static boolean escape(UI ui, int key) {
        if(key != java.awt.event.KeyEvent.VK_ESCAPE) return false;
        GameUI gui = ui.root.findchild(GameUI.class);
        if(gui == null || gui.dreamHarvester.target == null) return false;
        FlowerMenu menu = ui.root.findchild(FlowerMenu.class);
        gui.dreamHarvester.cancel();
        if(menu != null) menu.choose(null);
        ui.msg(hearthborn.L10n.tr("Dream harvest stopped."));
        return true;
    }

    public void toggle() {
        if(target != null) { cancel(); gui.ui.msg(hearthborn.L10n.tr("Dream harvest stopped.")); return; }
        if(gui.map == null || gui.maininv == null || gui.map.player() == null) return;
        if(gui.ui.root.findchild(FlowerMenu.class) != null) {
            gui.error(hearthborn.L10n.tr("Close the action menu first.")); return;
        }
        if(gui.vhand != null) { gui.error(hearthborn.L10n.tr("Empty the cursor first.")); return; }
        pending.clear();
        synchronized(gui.ui.sess.glob.oc) {
            pending.addAll(inRange(gui.ui.sess.glob.oc, gui.map.player().rc));
        }
        pending.removeIf(gob -> unreachable.contains(gob.id));
        if(pending.isEmpty()) {
            unreachable.clear();
            gui.error(hearthborn.L10n.tr("No available dream catchers within 20 tiles. Start again to retry.")); return;
        }
        map = gui.map;
        gui.ui.msg(hearthborn.L10n.format("Dream harvest: %d catchers to visit.", pending.size()));
        next();
    }

    public static List<Gob> inRange(Iterable<Gob> objects, Coord2d origin) {
        List<Gob> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        double radius = 20 * MCache.tilesz.x;
        for(Gob gob : objects) {
            if(!gob.removed && gob.rc.dist(origin) <= radius && seen.add(gob.id) &&
               nearest(Collections.singletonList(gob), origin) != null) result.add(gob);
        }
        return result;
    }

    private void next() {
        if(map == null || map.player() == null) { cancel(); return; }
        target = nearest(pending, map.player().rc);
        if(target == null) {
            cancel();
            gui.ui.msg(hearthborn.L10n.tr("Dream harvest completed."));
            return;
        }
        pending.remove(target);
        attempts = 0;
        if(gui.indoors()){path=null;click();return;}
        List<java.awt.geom.Area> obstacles;
        synchronized(gui.ui.sess.glob.oc) {
            obstacles = hearthborn.world.ObjectGeometry.obstacles(gui.ui.sess.glob.oc, map.player());
        }
        path = LocalPathfinder.route(map.player().rc, target.rc, obstacles);
        waypoint = 0;
        lastPosition = map.player().rc;
        stalled = 0;
        if(path == null) { skip(); return; }
        if(path.isEmpty()) { path = null; click(); } else move();
    }

    private void skip() {
        unreachable.add(target.id);
        gui.ui.msg(hearthborn.L10n.tr("Dream catcher is unreachable. Skipping."));
        path = null;
        next();
    }

    private void move() {
        map.wdgmsg("click", Coord.z, path.get(waypoint).floor(OCache.posres), 1, 0);
    }

    private void click() {
        if(target == null) return;
        attempts++;
        selected = null;
        remaining = 30;
        lastPosition = map.player().rc;
        stalled = 0;
        Coord location = target.rc.floor(OCache.posres);
        map.wdgmsg("click", map.rootxlate(gui.ui.mc), location, 3, 0,
                   0, (int)target.id, location, 0, -1);
    }

    public static FlowerMenu.Petal harvest(FlowerMenu menu) {
        for(FlowerMenu.Petal petal : menu.opts)
            if(petal.name.equals("Harvest") || petal.name.equals(hearthborn.L10n.tr("Harvest"))) return petal;
        return null;
    }

    public void tick(double dt) {
        if(target == null) return;
        if(gui.map != map || gui.ui == null || gui.ui.sess == null || gui.vhand != null) {
            cancel(); return;
        }
        if(target.removed) { next(); return; }
        Gob player = map.player();
        if(player == null) { cancel(); return; }
        if(path != null) {
            if(gui.indoors()){path=null;click();return;}
            if(player.rc.dist(path.get(waypoint)) < 2) {
                if(++waypoint == path.size()) { path = null; click(); }
                else { stalled = 0; lastPosition = player.rc; move(); }
            } else {
                if(player.rc.dist(lastPosition) > 0.5) { lastPosition = player.rc; stalled = 0; }
                else stalled += dt;
                if(stalled > 3) skip();
            }
            return;
        }
        remaining -= dt;
        if(selected == null) {
            if(player.rc.dist(lastPosition) > 0.5) { lastPosition = player.rc; stalled = 0; }
            else stalled += dt;
        }
        FlowerMenu menu = gui.ui.root.findchild(FlowerMenu.class);
        if(selected == null) {
            if(menu != null) {
                FlowerMenu.Petal petal = harvest(menu);
                selected = menu;
                unreachable.remove(target.id);
                remaining = petal == null ? 0 : 0.75;
                if(petal == null) attempts = 2;
                menu.choose(petal);
            } else if(stalled >= 1 && player.rc.dist(target.rc) <= MCache.tilesz.x) {
                // Empty catchers may send no flower menu at all.
                next();
            } else if(remaining <= 0 || stalled > 3) {
                skip();
            }
        } else if(remaining <= 0 && menu != selected) {
            if(menu != null) { cancel(); return; }
            if(attempts < 2) click(); else next();
        } else if(remaining < -5) {
            cancel();
        }
    }

    public void cancel() {
        if(map != null && gui.ui != null && gui.ui.sess != null &&
           gui.map == map && map.player() != null)
            map.wdgmsg("click", Coord.z, map.player().rc.floor(OCache.posres), 3, 0);
        path = null;
        pending.clear();
        target = null; map = null; selected = null; remaining = 0;
    }
}
