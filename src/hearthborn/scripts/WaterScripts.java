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

import haven.*;
import haven.resutil.WaterTile;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.*;

/**
 * Measures the quality of the water around a boat: takes a waterskin, fills it from the nearest water, reads the quality,
 * shows it on the notice plate, puts the waterskin back and empties it. One press is one sample.
 *
 * @author Jagy2014
 */
final class WaterScripts {
    /** The reach, in tiles, in which water is looked for around the player. */
    static final int REACH = 4;
    /** Names of the item menu entry that pours a vessel out; the first one the server offers is taken. */
    static final String EMPTY = "Empty|Empty out|Pour out";
    private static final Pattern CONTENT = Pattern.compile("^(?:Contains:\\s*)?([0-9]+(?:[.,][0-9]+)?)\\s*(?:l|L|liters?|litres?)\\s+of\\s+(.+?)[.]?$");

    static boolean supports(String id) {return "water".equals(id);}

    static void run(GameUI gui) {
        if(gui == null) return;
        ScriptRunner.Task previous = ScriptRunner.current(gui);
        if(previous instanceof Task) {ScriptRunner.cancel(gui); return;}
        Task task = new Task(gui);
        try {
            if(gui.map == null || gui.map.player() == null) throw new IllegalStateException("The map is not ready yet.");
            if(gui.fv != null && gui.fv.current != null) throw new IllegalStateException("Leave combat before measuring water.");
            if(task.hand() != null) throw new IllegalStateException("Empty the cursor before starting the script.");
            if(previous != null) ScriptRunner.cancel(gui);
            ScriptRunner.start(task);
            task.start();
        } catch(IllegalStateException failure) {task.error(failure.getMessage());}
    }

    static boolean flask(String resource) {
        return resource != null && (resource.endsWith("/waterskin") || resource.endsWith("/waterflask"));
    }

    /** What a vessel holds: the amount and the name as the server words them ("3.00 l of Water"). */
    static final class Contents {
        final String amount, liquid;
        Contents(String amount, String liquid) {this.amount = amount; this.liquid = liquid;}
    }

    /** Null while the vessel is empty or its text has not arrived. */
    static Contents contents(List<ItemInfo> info) {
        for(ItemInfo tip : info) {
            if(tip instanceof ItemInfo.Contents) {
                Contents inner = contents(((ItemInfo.Contents)tip).sub);
                if(inner != null) return inner;
            }
            String text = tip instanceof ItemInfo.Name ? ((ItemInfo.Name)tip).str.text : tip instanceof ItemInfo.AdHoc ? ((ItemInfo.AdHoc)tip).str.text : null;
            Contents found = parse(text);
            if(found != null) return found;
        }
        return null;
    }

    static Contents parse(String text) {
        if(text == null) return null;
        Matcher match = CONTENT.matcher(text.trim());
        return match.matches() ? new Contents(match.group(1).replace(',', '.') + " l", match.group(2).trim()) : null;
    }

    /** The nearest water tile to the centre within the reach, never the centre itself (a boat's own tile is not a target). */
    static Coord nearestWater(Predicate<Coord> water, Coord center, int reach) {
        Coord best = null;
        double distance = Double.MAX_VALUE;
        for(int y = -reach; y <= reach; y++)
            for(int x = -reach; x <= reach; x++) {
                if(x == 0 && y == 0) continue;
                double d = Math.hypot(x, y);
                if(d > reach || d >= distance) continue;
                if(water.test(center.add(x, y))) {best = center.add(x, y); distance = d;}
            }
        return best;
    }

    static boolean water(MCache map, Coord tile) {
        int id = map.gettile(tile);
        Tiler tiler = map.tiler(id);
        if(tiler == null) throw new Loading();
        return tiler instanceof WaterTile;
    }

    static class Task extends ScriptRunner.Task {
        final MapView map;
        Double quality;
        Task(GameUI gui) {
            super(gui);
            map = gui.map;
            cleanup = () -> {flower = null; if(held != null) returnHeld();};
        }
        /** The result on the notice plate and in the System chat, without the message sound: samples come one after another. */
        void say(String text) {
            gui.showPlainNotice(text, java.awt.Color.WHITE);
            if(gui.syslog != null) gui.syslog.append(text, java.awt.Color.WHITE);
        }
        @Override void cancelled() {say(L10n.tr("Water measurement cancelled."));}
        @Override void check() {
            if(gui.map != map) throw new IllegalStateException("The map changed. Start measuring again.");
        }
        Gob player() {return map.player();}
        /** An empty waterskin of the player's own. */
        GItem flask(boolean filled) {
            for(GItem item : ShortcutRunner.owned(gui)) {
                if(!WaterScripts.flask(item.res.get().name)) continue;
                List<ItemInfo> info = item.info();
                if(info.isEmpty()) throw new Loading();
                if((contents(info) != null) == filled) return item;
            }
            return null;
        }
        GItem returned;
        String sampleResource;
        /** Read all vessel metadata before taking anything; pending contents are never treated as empty. */
        void prepare() {
            boolean any = false, empty = false;
            for(GItem item : ShortcutRunner.owned(gui)) {
                if(!WaterScripts.flask(item.res.get().name)) continue;
                any = true;
                List<ItemInfo> info = item.info();
                if(info.isEmpty()) throw new Loading();
                if(contents(info) == null) empty = true;
            }
            if(!any) throw new IllegalStateException("No waterskin in the inventory.");
            if(!empty) throw new IllegalStateException("No empty waterskin in the inventory.");
        }
        /** The receipt in our exact return slot, never another filled vessel from the bags. */
        GItem sample() {
            if(returnInventory == null || returnCell == null) return null;
            for(WItem view : returnInventory.children(WItem.class)) {
                if(view.parent != returnInventory || !view.c.sub(1, 1).div(Inventory.sqsz).equals(returnCell)) continue;
                GItem item = view.item;
                if(!item.res.get().name.equals(sampleResource)) throw new IllegalStateException("The cursor is holding another item.");
                if(item.info().isEmpty()) throw new Loading();
                return contents(item.info()) == null ? null : item;
            }
            return null;
        }
        void start() {
            add(this::prepare, () -> true, 15);
            add(() -> {
                GItem empty = flask(false);
                if(empty == null) throw new IllegalStateException("No empty waterskin in the inventory.");
                sampleResource = empty.res.get().name;
                if(empty.parent instanceof Inventory) returnInventory = (Inventory)empty.parent;
                empty.wdgmsg("take", Coord.z, 0);
            }, () -> {
                if(hand() == null) return false;
                held = hand();
                if(!held.res.get().name.equals(sampleResource)) throw new IllegalStateException("The cursor is holding another item.");
                return true;
            }, 5);
            add(() -> {
                GItem inHand = hand();
                if(inHand == null || !inHand.res.get().name.equals(sampleResource) || contents(inHand.info()) != null) throw new IllegalStateException("The cursor is holding another item.");
                held = inHand;
                probe();
            }, () -> {
                GItem inHand = hand();
                if(inHand == null) throw new IllegalStateException("The waterskin left the cursor.");
                return contents(inHand.info()) != null && QualityOverlay.qualityOf(inHand) != null;
            }, 12);
            add(() -> record());
            add(() -> returnHeld(), () -> {
                if(hand() != null) return false;
                returned = sample();
                return returned != null;
            }, 5);
            add(() -> pourOut(), () -> flower == null && (returned.parent == null || !returned.info().isEmpty() && contents(returned.info()) == null), 12);
        }
        void probe() {
            MCache grid = gui.ui.sess.glob.map;
            Coord here = player().rc.floor(MCache.tilesz);
            Coord sampled = nearestWater(tile -> water(grid, tile), here, REACH);
            if(sampled == null) throw new IllegalStateException("No water within reach.");
            Coord2d centre = Coord2d.of(sampled.x, sampled.y).mul(MCache.tilesz).add(MCache.tilesz.div(2));
            map.wdgmsg("itemact", Coord.z, centre.floor(OCache.posres), 0);
        }
        void record() {
            GItem inHand = hand();
            if(inHand == null) throw new IllegalStateException("The waterskin left the cursor.");
            held = inHand;
            quality = QualityOverlay.qualityOf(inHand);
            Contents found = contents(inHand.info());
            say(L10n.format("Water quality: %s (%s of %s)", quality == null ? "?" : Utils.odformat(quality, 1),
                found == null ? "?" : found.amount, found == null ? "?" : found.liquid));
            hearthborn.map.MapDisplay.sample(gui, "water", quality);
        }
        void pourOut() {
            GItem full = returned;
            if(full == null || full.parent != returnInventory) throw new IllegalStateException("The waterskin left the cursor.");
            flower = EMPTY;
            gui.ui.lcc = gui.maininv.rootpos();
            full.wdgmsg("iact", Coord.z, 0);
        }
    }

    private WaterScripts() {}
}
