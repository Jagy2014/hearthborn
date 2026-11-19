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
import hearthborn.inventory.InventoryActions;

/**
 * Slippers for chasing a rabbit, armor boots for every other step.
 *
 * @author Jagy2014
 */
public final class BootSwitch {
    private static final String PREF = "hearthborn/combat/boot-switch";
    private static final int BOOTS = 15;
    public static final String SLIPPERS = "gfx/invobjs/bunnyslippers";
    public static final String[] ARMOR = {
        "gfx/invobjs/plateboots",
        "gfx/invobjs/armoredstriders",
        "gfx/invobjs/rangersboots",
        "gfx/invobjs/leatherboots"
    };
    public static final int KEEP = 0, TO_ARMOR = 1, TO_SLIPPERS = 2;

    public static boolean enabled() {return Utils.getprefb(PREF, true);}
    public static void enabled(boolean on) {Utils.setprefb(PREF, on);}

    /** World click. A right click on empty ground leaves the boots alone. */
    public static int worldChoice(int button, boolean object, boolean rabbit) {
        if(button == 1) return TO_ARMOR;
        if(button == 3 && object) return rabbit ? TO_SLIPPERS : TO_ARMOR;
        return KEEP;
    }

    /** Map click. Empty ground returns to armor boots. */
    public static int mapChoice(int button, boolean object, boolean rabbit) {
        if(!object) return TO_ARMOR;
        if(button == 3) return rabbit ? TO_SLIPPERS : TO_ARMOR;
        if(button == 1) return TO_ARMOR;
        return KEEP;
    }

    /** First armor pair to equip, or null when the worn pair is already the best one present. */
    public static String armorTarget(String worn, boolean[] present) {
        for(int i = 0; i < ARMOR.length; i++) {
            if(ARMOR[i].equals(worn)) return null;
            if(present != null && i < present.length && present[i]) return ARMOR[i];
        }
        return null;
    }

    public static void world(GameUI gui, Gob gob, int button) {
        Boolean rabbit = rabbit(gob);
        if(rabbit == null) return;
        apply(gui, worldChoice(button, gob != null, rabbit));
    }
    public static void map(GameUI gui, Gob gob, int button) {
        Boolean rabbit = rabbit(gob);
        if(rabbit == null) return;
        apply(gui, mapChoice(button, gob != null, rabbit));
    }

    private static void apply(GameUI gui, int choice) {
        if(choice == KEEP || !enabled() || gui == null || gui.vhand != null || gui.maininv == null) return;
        try {
            if(choice == TO_SLIPPERS) slippers(gui);
            else armor(gui);
        } catch(Loading ignored) {}
    }

    private static Boolean rabbit(Gob gob) {
        if(gob == null) return Boolean.FALSE;
        try {
            Drawable drawable = gob.getattr(Drawable.class);
            if(drawable == null) return Boolean.FALSE;
            Resource res = drawable.getres();
            return res != null && res.name.indexOf("/rabbit/") >= 0;
        } catch(Loading loading) {
            return null;
        }
    }

    private static void slippers(GameUI gui) {
        if(SLIPPERS.equals(resource(worn(gui)))) return;
        WItem next = find(gui, SLIPPERS);
        if(next != null) swap(gui, next);
    }

    private static void armor(GameUI gui) {
        boolean[] present = new boolean[ARMOR.length];
        for(int i = 0; i < ARMOR.length; i++) present[i] = find(gui, ARMOR[i]) != null;
        String target = armorTarget(resource(worn(gui)), present);
        if(target == null) return;
        WItem next = find(gui, target);
        if(next != null) swap(gui, next);
    }

    private static void swap(GameUI gui, WItem next) {
        WItem current = worn(gui);
        if(current != null) current.item.wdgmsg("transfer", Coord.z, 1);
        next.item.wdgmsg("transfer", Coord.z, 1);
    }

    private static WItem worn(GameUI gui) {
        Equipory equipory = gui.equipment();
        if(equipory == null) return null;
        Coord at = Equipory.ecoords[BOOTS].add(1, 1);
        for(WItem item : equipory.children(WItem.class))
            if(at.equals(item.c)) return item;
        return null;
    }

    private static WItem find(GameUI gui, String resource) {
        for(WItem item : InventoryActions.items(gui.maininv)) {
            if(!item.visible) continue;
            Resource res = item.item.res.get();
            if(res != null && resource.equals(res.name)) return item;
        }
        return null;
    }

    private static String resource(WItem item) {
        if(item == null || item.item == null || item.item.res == null) return null;
        Resource res = item.item.res.get();
        return res == null ? null : res.name;
    }

    private BootSwitch() {}
}
