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
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import hearthborn.util.GameObjects;
import java.util.*;

/**
 * Measures the quality of the clay, the sand or the soil under the player: digs the tile the player stands on (a boat in shallow water
 * for clay, the beach for sand, open ground for soil), reads the quality of what came out, shows it on the notice plate and throws it away.
 * One press is one dig. Every dig of a tile gives material of one quality.
 *
 * @author Jagy2014
 */
final class DigScripts {
    /** Seconds to wait for the dug material to show up in the inventory after the progress bar is gone. */
    static final double ARRIVAL = 4;

    enum Material {
        CLAY("clay", "Clay quality: %s (%d pcs)", "No clay was dug here.", "The inventory already holds clay. Drop it first, so the new clay can be told apart."),
        SAND("sand", "Sand quality: %s (%d pcs)", "No sand was dug here.", "The inventory already holds sand. Drop it first, so the new sand can be told apart."),
        SOIL("soil", "Soil quality: %s (%d pcs)", "No soil was dug here.", "The inventory already holds soil. Drop it first, so the new soil can be told apart.");

        final String id, result, nothing, occupied;
        Material(String id, String result, String nothing, String occupied) {
            this.id = id; this.result = result; this.nothing = nothing; this.occupied = occupied;
        }

        /** Whether an inventory item (a resource such as "gfx/invobjs/clay-gray") is this material, not a jar or a pipe made of it. */
        boolean matches(String resource) {
            if(resource == null) return false;
            String name = resource.substring(resource.lastIndexOf('/') + 1);
            return this == CLAY ? name.equals("clay") || name.startsWith("clay-") : name.equals(id);
        }

        static Material of(String id) {
            for(Material material : values()) if(material.id.equals(id)) return material;
            return null;
        }
    }

    static boolean supports(String id) {return Material.of(id) != null;}

    static void run(GameUI gui, String id) {
        if(gui == null) return;
        ScriptRunner.Task previous = ScriptRunner.current(gui);
        if(previous instanceof Task) {ScriptRunner.cancel(gui); return;}
        Task task = new Task(gui, Material.of(id));
        try {
            if(gui.map == null || gui.map.player() == null) throw new IllegalStateException("The map is not ready yet.");
            if(gui.fv != null && gui.fv.current != null) throw new IllegalStateException("Leave combat before digging.");
            if(task.hand() != null) throw new IllegalStateException("Empty the cursor before starting the script.");
            if(previous != null) ScriptRunner.cancel(gui);
            ScriptRunner.start(task);
            task.start();
        } catch(IllegalStateException failure) {task.error(failure.getMessage());}
    }

    private static String name(GItem item) {
        if(item.res == null) throw new Loading();
        return item.res.get().name;
    }

    static class Task extends ScriptRunner.Task {
        final MapView map;
        final Material material;
        Double quality;
        boolean digging, began;
        double clicked, ended;

        Task(GameUI gui, Material material) {
            super(gui);
            this.map = gui.map;
            this.material = material;
            cleanup = this::leave;
        }
        /** The dig cursor stays selected on the server: without leaving it the next click on the map would dig instead of sail. */
        void leave() {interrupt();}
        /** The result on the notice plate and in the System chat, without the message sound: samples come one after another. */
        void say(String text) {
            gui.showPlainNotice(text, java.awt.Color.WHITE);
            if(gui.syslog != null) gui.syslog.append(text, java.awt.Color.WHITE);
        }
        @Override void cancelled() {say(L10n.tr("Digging cancelled."));}
        @Override void check() {
            if(gui.map != map) throw new IllegalStateException("The map changed. Start measuring again.");
        }
        Gob player() {return map.player();}
        /** The dug pieces: single items of the material. */
        List<GItem> pieces() {
            List<GItem> items = new ArrayList<>();
            for(GItem item : ShortcutRunner.owned(gui))
                if(material.matches(name(item)) && (item.contents == null || item.contents.children(GItem.class).isEmpty())) items.add(item);
            return items;
        }
        /** What can be thrown away in one go: a stack holding the material counts once, its pieces do not. */
        List<GItem> found() {
            List<GItem> things = new ArrayList<>();
            for(GItem piece : pieces()) {
                GItem thing = piece;
                for(GItem item : ShortcutRunner.owned(gui))
                    if(item != piece && item.contents == piece.parent && QualityOverlay.stacked(item)) thing = item;
                if(!things.contains(thing)) things.add(thing);
            }
            return things;
        }
        /** Stops the digging: leaves the dig cursor and, while the progress bar runs, breaks off the action with a click on the spot. */
        void interrupt() {
            if(gui.map != map || map.player() == null) return;
            Coord at = map.player().rc.floor(OCache.posres);
            if(!digging) return;
            map.wdgmsg("click", Coord.z, at, 3, 0);
            digging = false;
            if(gui.prog != null) map.wdgmsg("click", Coord.z, at, 1, 0);
        }
        void prepare() {
            if(!found().isEmpty()) throw new IllegalStateException(material.occupied);
        }
        void start() {
            add(this::prepare, () -> true, 15);
            add(() -> {
                digging = true;
                gui.act("dig");
            });
            add(() -> {
                clicked = Utils.rtime();
                map.wdgmsg("click", Coord.z, player().rc.floor(OCache.posres), 1, 0);
            }, () -> {
                if(gui.prog != null) began = true;
                return began || Utils.rtime() - clicked > 5;
            }, 8);
            add(() -> {
                if(!began) throw new IllegalStateException("Digging did not start. Is a shovel equipped?");
            });
            // One dig is enough: when the progress bar starts over the digging repeats, so it is broken off there.
            double[] peak = {0};
            add(() -> {}, () -> {
                if(gui.prog == null) return true;
                double value = gui.prog.prog;
                if(value + 0.2 < peak[0]) {interrupt(); return true;}
                peak[0] = Math.max(peak[0], value);
                return false;
            }, 90);
            add(() -> ended = Utils.rtime(), () -> {
                List<GItem> items = found();
                if(!items.isEmpty() && QualityOverlay.qualityOf(items.get(0)) != null) return true;
                return Utils.rtime() - ended > ARRIVAL;
            }, 15);
            add(this::record);
        }
        void record() {
            interrupt();
            List<GItem> items = found();
            if(items.isEmpty()) {say(L10n.tr(material.nothing)); return;}
            quality = QualityOverlay.qualityOf(items.get(0));
            int amount = 0;
            for(GItem piece : pieces()) amount += GameObjects.count(piece);
            say(L10n.format(material.result, quality == null ? "?" : Utils.odformat(quality, 1), amount));
            hearthborn.map.MapDisplay.sample(gui, material.id, quality);
            throwAway(0);
        }
        /** Throws the dug pieces on the ground (into the water from a boat) one by one until none are left. */
        void throwAway(int round) {
            List<GItem> left = found();
            if(left.isEmpty()) return;
            if(round >= 60) throw new IllegalStateException("The dug material could not be thrown away.");
            GItem item = left.get(0);
            int before = GameObjects.count(item);
            add(() -> item.wdgmsg("drop", Coord.z), () -> item.parent == null || GameObjects.count(item) < before, 5);
            add(() -> throwAway(round + 1));
        }
    }

    private DigScripts() {}
}
