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

import haven.Button;
import haven.Coord;
import haven.GItem;
import haven.GameUI;
import haven.Gob;
import haven.Inventory;
import haven.ItemInfo;
import haven.Loading;
import haven.MCache;
import haven.OCache;
import haven.UI;
import haven.Widget;
import haven.Window;
import haven.resutil.FoodInfo;
import hearthborn.L10n;
import hearthborn.inventory.BagTransfers;
import hearthborn.inventory.ContainerTools;
import hearthborn.quality.VesselInfo;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class TableSalting {
    private static final String SALT = "gfx/invobjs/salt";
    private static final double BARREL_RANGE = 3 * MCache.tilesz.x;

    private TableSalting() {}

    public static void update(Window window) {
        if(!window.visible() || !ContainerTools.foodTable(window)) return;
        SaltButton salt = window.getchild(SaltButton.class);
        Button feast = null;
        String localized = L10n.quiet("button", "Feast!");
        for(Widget child : window.children()) {
            if(child instanceof Button && child.visible) {
                Button button = (Button)child;
                if(button.text != null && ("Feast!".equals(button.text.text) || localized.equals(button.text.text))) {
                    feast = button;
                    break;
                }
            }
        }
        if(feast == null) {
            if(salt != null) salt.hide();
            return;
        }
        if(salt == null) salt = window.add(new SaltButton(window, feast.lg));
        salt.move(feast.c.add(feast.sz.x + UI.scale(8), (feast.sz.y - salt.sz.y) / 2));
        Coord required = window.csz().max(salt.c.add(salt.sz));
        if(!required.equals(window.csz())) window.resize(required);
        if(!salt.visible) salt.show();
    }

    private static final class SaltButton extends Button {
        SaltButton(Window window, boolean large) {
            super(UI.scale(110), "Salt All", large, () -> start(window));
            resize(Coord.of(Math.max(sz.x, text.sz().x + UI.scale(18)), sz.y));
            settip("Salt all food");
        }
    }

    private static void start(Window window) {
        GameUI gui = window.getparent(GameUI.class);
        if(gui == null) return;
        try {
            Inventory food = foodGrid(window);
            if(food == null) throw new IllegalStateException("Open the table's food inventory first.");
            ScriptRunner.start(new Task(gui, window, food));
        } catch(IllegalStateException failure) {
            gui.ui.error(L10n.tr(failure.getMessage()));
        }
    }

    private static Inventory foodGrid(Window window) {
        // The other grids on a table hold utensils or a tablecloth.
        Inventory food = null;
        for(Widget child : window.children()) {
            if(!(child instanceof Inventory) || !child.visible) continue;
            Inventory grid = (Inventory)child;
            if(grid.isz.x >= 6 && grid.isz.y >= 6 &&
                (food == null || grid.isz.x * grid.isz.y > food.isz.x * food.isz.y))
                food = grid;
        }
        return food;
    }

    private static boolean salted(List<ItemInfo> info) {
        String translated = L10n.quiet("label", "Salted");
        for(ItemInfo tip : info) {
            if(tip instanceof ItemInfo.AdHoc) {
                String text = ((ItemInfo.AdHoc)tip).str.text;
                if("Salted".equals(text) || translated.equals(text)) return true;
            }
        }
        return false;
    }

    static final class Task extends ScriptRunner.Task {
        private final Window window;
        private final Inventory food;
        private final Deque<GItem> targets = new ArrayDeque<>();
        private int completed;
        private boolean takingSalt;

        Task(GameUI gui, Window window, Inventory food) {
            super(gui);
            this.window = window;
            this.food = food;
            add(this::collect);
        }

        @Override
        void check() {
            if(window.parent == null || !window.visible() || food.parent != window)
                throw new IllegalStateException("The table was closed.");
        }

        boolean ownsSalt(GItem item) {
            if(item == held) return true;
            if(!takingSalt) return false;
            try {
                return SALT.equals(item.resource().name);
            } catch(Loading waiting) {
                return false;
            }
        }

        private void collect() {
            Deque<GItem> ready = new ArrayDeque<>();
            for(Widget child : food.children()) {
                if(!(child instanceof GItem)) continue;
                GItem item = (GItem)child;
                List<ItemInfo> info = item.info();
                if(item.contents == null && ItemInfo.find(FoodInfo.class, info) != null && !salted(info))
                    ready.add(item);
            }
            if(ready.isEmpty()) throw new IllegalStateException("There is no unsalted food on this table.");
            targets.addAll(ready);
            add(this::next);
        }

        private GItem findSalt() {
            if(gui.maininv != null) for(Widget child : gui.maininv.children()) {
                if(child instanceof GItem && SALT.equals(((GItem)child).resource().name))
                    return (GItem)child;
            }
            return null;
        }

        private Gob saltBarrel(Window barrel) {
            Long id = barrel.objectId();
            if(id == null || !barrel.visible() || gui.ui.sess == null) return null;
            Gob gob = gui.ui.sess.glob.oc.getgob(id);
            Gob player = gui.map.player();
            if(gob == null || gob.removed || !MilkScripts.barrel(gob) || player == null ||
                gob.rc.dist(player.rc) > BARREL_RANGE) return null;
            VesselInfo info = VesselInfo.read(barrel);
            String contents = info.contents();
            return info.ready() && !info.empty() && ("Salt".equalsIgnoreCase(contents) ||
                L10n.quiet("ingredient", "Salt").equalsIgnoreCase(contents)) ? gob : null;
        }

        private void drawSalt() {
            Window source = null;
            for(Widget child : gui.children()) {
                if(child instanceof Window && saltBarrel((Window)child) != null) {
                    source = (Window)child;
                    break;
                }
            }
            if(source == null) throw new IllegalStateException("No salt in the backpack or in an open nearby barrel.");
            Window barrel = source;
            add(() -> {
                Gob gob = saltBarrel(barrel);
                if(gob == null) throw new IllegalStateException("The salt barrel is no longer available.");
                if(hand() != null) throw new IllegalStateException("The cursor is holding another item.");
                if(gui.maininv == null || BagTransfers.free(gui.maininv, Coord.of(1, 1)) == null)
                    throw new IllegalStateException("Make room in the backpack to take salt.");
                Coord at = gob.rc.floor(OCache.posres);
                takingSalt = true;
                gui.map.wdgmsg("click", Coord.z, at, 3, UI.MOD_SHIFT, 0, (int)gob.id, at, 0, -1);
            }, () -> {
                GItem item = hand();
                if(item == null) return findSalt() != null;
                if(!SALT.equals(item.resource().name)) throw new IllegalStateException("The cursor is holding another item.");
                held = item;
                return true;
            }, 5);
            add(this::next);
        }

        private void next() {
            takingSalt = false;
            GItem target;
            while((target = targets.peekFirst()) != null &&
                (target.parent != food || salted(target.info())))
                targets.removeFirst();
            if(target == null) {
                returnHeld();
                add(() -> {}, () -> hand() == null || hand() != held, 3);
                add(() -> gui.ui.msg(L10n.format("Portions salted: %d.", completed)));
                return;
            }

            GItem salt = hand();
            if(salt == null) {
                salt = findSalt();
                if(salt == null) drawSalt();
                else {
                    takingSalt = true;
                    take(salt);
                    add(this::next);
                }
                return;
            }
            if(salt != held) throw new IllegalStateException("The cursor is holding another item.");

            GItem serving = target;
            boolean[] applied = {false};
            add(() -> {
                if(serving.parent != food) return;
                if(hand() == null) {
                    targets.addFirst(serving);
                    return;
                }
                if(hand() != held) throw new IllegalStateException("The cursor is holding another item.");
                serving.wdgmsg("itemact", 0);
                applied[0] = true;
            }, () -> {
                if(!applied[0] || serving.parent != food) return true;
                if(!salted(serving.info())) return false;
                completed++;
                return true;
            }, 5);
            targets.removeFirst();
            add(this::next);
        }
    }
}
