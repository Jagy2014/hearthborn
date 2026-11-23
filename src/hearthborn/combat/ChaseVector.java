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

package hearthborn.combat;

import haven.*;
import haven.render.Pipe;
import java.awt.Color;

/**
 * Draws a line from a chasing gob to its Homing target: attack queues, pickups and follows.
 *
 * @author Jagy2014
 */
public final class ChaseVector extends GAttrib implements PView.Render2D {
    private static final String PREF = "hearthborn/combat/chase-vectors";
    private static volatile boolean enabled = Utils.getprefb(PREF, true);
    private static volatile GameUI gui;
    private static final Color SELF = new Color(255, 255, 255, 220);
    private static final Color PARTY = new Color(80, 220, 120, 220);
    private static final Color OTHER = new Color(255, 70, 70, 220);
    private static final String[] SKIP = {
        "gfx/terobjs/vehicle/cart",
        "gfx/terobjs/vehicle/wagon",
        "gfx/terobjs/vehicle/wheelbarrow",
    };

    public static boolean enabled() { return enabled; }
    public static void enabled(boolean value) { enabled = value; Utils.setprefb(PREF, value); }
    public static void bind(GameUI game) { gui = game; }

    public ChaseVector(Gob gob) { super(gob); }

    private static boolean skipped(String name) {
        if(name == null) return true;
        for(String prefix : SKIP) if(name.contains(prefix)) return true;
        return false;
    }

    /** Who owns this chase for colouring: the runner, or a party member riding it. */
    static Color tint(Gob chaser, GameUI gui) {
        if(gui == null) return OTHER;
        if(chaser.id == gui.plid) return SELF;
        if(riddenBy(chaser, gui.plid)) return SELF;
        if(chaser.glob.party != null && chaser.glob.party.memb != null) {
            if(chaser.glob.party.memb.containsKey(chaser.id)) return PARTY;
            for(long id : chaser.glob.party.memb.keySet())
                if(id != gui.plid && riddenBy(chaser, id)) return PARTY;
        }
        return OTHER;
    }

    private static boolean riddenBy(Gob mount, long riderId) {
        if(mount.glob == null || mount.glob.oc == null) return false;
        Gob rider = mount.glob.oc.getgob(riderId);
        if(rider == null) return false;
        Following follow = rider.getattr(Following.class);
        return follow != null && follow.tgt == mount.id;
    }

    public void draw(GOut g, Pipe state) {
        if(!enabled) return;
        Homing homing = gob.getattr(Homing.class);
        if(homing == null) return;
        Gob target = gob.glob.oc.getgob(homing.tgt);
        if(target == null || target.removed) return;
        GameUI game = gui;
        MapView map = game == null ? null : game.map;
        if(map == null) return;
        try {
            Drawable chaserDraw = gob.getattr(Drawable.class), targetDraw = target.getattr(Drawable.class);
            Resource from = chaserDraw == null ? null : chaserDraw.getres();
            Resource to = targetDraw == null ? null : targetDraw.getres();
            if(skipped(from == null ? null : from.name) || skipped(to == null ? null : to.name)) return;
            Coord3f a = map.screenxf(gob.getc()), b = map.screenxf(target.getc());
            if(a == null || b == null) return;
            Coord fromScreen = a.round2(), toScreen = b.round2();
            Color color = tint(gob, game);
            g.chcolor(Color.BLACK);
            g.line(fromScreen, toScreen, UI.scale(4));
            g.chcolor(color);
            g.line(fromScreen, toScreen, UI.scale(2));
            g.chcolor();
        } catch(Loading ignored) { }
    }
}
