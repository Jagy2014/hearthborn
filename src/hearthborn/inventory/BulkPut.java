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

package hearthborn.inventory;

import haven.*;
import hearthborn.util.GameObjects;
import java.util.*;

/**
 * Shift+click with an item on the cursor puts every alike item of the bag into a barrel, and every seed of the crop, whatever its quality, into a granary slot.
 * The server moves them all only when the barrel already holds that kind, and a granary slot takes one item per click.
 * Here the click is simply repeated for each next item of the kind, one at a time, until the bag has none left.
 *
 * @author Jagy2014
 */
public final class BulkPut extends Widget {
    static final String GRANARY = "haven.res.ui.grainslot.Grainslot";
    private static final double WAIT = 4, OPEN_WAIT = 40, ROW_WAIT = 2;
    private static final int LIMIT = 2000, BATCH = 8;

    private enum Phase {OPENING, SENT, TAKEN, BATCH}

    private final GameUI gui;
    private Widget sender;
    private String message;
    private final String resource, kind;
    private Object[] args;
    /** The granary building to walk to and open; null when the click was already on a barrel or a row of the open window. */
    private final Gob building;
    boolean clicked;
    private final Double quality;
    private GItem current;
    private Phase phase;
    private double waited;
    private int rounds;

    private BulkPut(GameUI gui, Widget sender, String message, Object[] args, Gob building, GItem held, String resource, String kind, Double quality) {
        super(Coord.z);
        this.gui = gui; this.sender = sender; this.message = message; this.args = args; this.building = building;
        this.resource = resource; this.kind = kind; this.quality = quality; this.current = held;
        this.phase = building == null ? Phase.SENT : Phase.OPENING;
    }

    public static boolean running(GameUI gui) {return gui != null && !gui.children(BulkPut.class).isEmpty();}

    /**
     * Called for every message the client sends; starts a repeat for a Shift click with an item of the cursor.
     * Returns true when the click is taken over and must not be sent: the granary building does not take an item by itself,
     * so the client walks to it, opens it and puts the items into the row that takes them.
     */
    public static boolean input(Widget sender, String message, Object... args) {
        try {
            GameUI gui = sender instanceof GameUI ? (GameUI)sender : sender.getparent(GameUI.class);
            if(gui == null || gui.vhand == null || running(gui)) return false;
            boolean granary = false, barrel = false;
            Gob building = null;
            if(sender.getClass().getName().equals(GRANARY)) {
                granary = ("drop".equals(message) || "itemiact".equals(message)) && args.length == 1 && shifted(args[0]);
                // The server answers a Shift drop by taking the next stack into the hand; the repeat does that itself, one stack at a time.
                if(granary) args[0] = 0;
            }
            else if(sender instanceof MapView && "itemact".equals(message) && args.length >= 5 && shifted(args[2])) {
                Gob gob = gob(gui, args[4]);
                String target = gob == null ? null : GameObjects.resourceName(gob);
                granary = granaryName(target);
                barrel = barrelName(target);
                if(granary) building = gob;
            }
            if(!granary && !barrel) return false;
            GItem held = gui.vhand.item;
            String resource = held.res.get().name, kind = InventoryActions.kind(gui.vhand);
            // A granary row of a crop holds one blended quality, so seeds of every quality of the crop go in; a barrel holds one quality and only alike items go.
            Double quality = null;
            // The row of an open window is answered by its own "drop"; the map's "itemact" is for the building only.
            gui.add(new BulkPut(gui, building == null ? sender : null, building == null ? message : "drop", building == null ? args.clone() : new Object[] {0}, building, held, resource, kind, quality));
            return building != null;
        } catch(Loading pending) {
            // The item is not described yet: this click goes the ordinary way.
            return false;
        }
    }

    /** Starts the walk to a granary and the putting of the cursor item into it; for the click on the building. */
    static BulkPut openGranary(GameUI gui, Gob building) {
        GItem held = gui.vhand.item;
        BulkPut put = new BulkPut(gui, null, "drop", new Object[] {0}, building, held, held.res.get().name,
            InventoryActions.kind(gui.vhand), null);
        gui.add(put);
        return put;
    }

    /** Shift, with or without Ctrl: either one asks for every alike item. */
    private static boolean shifted(Object modifiers) {
        int mods = Utils.iv(modifiers) & (UI.MOD_SHIFT | UI.MOD_CTRL | UI.MOD_META);
        return mods == UI.MOD_SHIFT || mods == (UI.MOD_SHIFT | UI.MOD_CTRL);
    }

    private static Gob gob(GameUI gui, Object id) {
        return gui.ui.sess.glob.oc.getgob(Integer.toUnsignedLong(Utils.iv(id)));
    }

    /** The open window of the granary, with its rows; null while it is not there. */
    private Window window() {
        for(Window window : gui.children(Window.class)) {
            if(!window.visible()) continue;
            Long id = window.objectId();
            if(id != null ? id == building.id : false) return window;
            if(id == null && !granaryRows(window).isEmpty()) return window;
        }
        return null;
    }

    static List<Widget> granaryRows(Window window) {
        List<Widget> result = new ArrayList<>();
        for(Widget child : window.children(Widget.class))
            if(child.getClass().getName().equals(GRANARY)) result.add(child);
        return result;
    }

    /** The row for the items in hand: the one the server marks as taking them, else the one named like them. */
    private Widget row(Window window, boolean last) {
        // The row's picture is the crop's own seed resource, whatever the language of the names: that tells the rows of the crop.
        Widget same = null;
        double closest = Double.MAX_VALUE;
        for(Widget row : granaryRows(window)) {
            Indir<Resource> icon = GameObjects.field(row, "icon", Indir.class);
            String name;
            try {name = icon == null ? null : icon.get().name;} catch(Loading pending) {continue;}
            if(!resource.equals(name)) continue;
            Label label = GameObjects.field(row, "lbl", Label.class);
            double away = quality == null ? 0 : distance(label == null ? null : label.texts, quality);
            if(same == null || away < closest) {same = row; closest = away;}
        }
        if(same != null) return same;
        Widget named = null;
        List<Widget> taking = new ArrayList<>();
        for(Widget row : granaryRows(window)) {
            Button put = GameObjects.field(row, "pbtn", Button.class);
            Label label = GameObjects.field(row, "lbl", Label.class);
            if(put != null && put.visible()) taking.add(row);
            if(label != null && label.texts != null && label.texts.toLowerCase(Locale.ROOT).contains(kind.toLowerCase(Locale.ROOT))
                && (named == null || quality != null && closer(label.texts, named, quality))) named = row;
        }
        Widget best = null;
        double nearest = Double.MAX_VALUE;
        for(Widget row : taking) {
            Label label = GameObjects.field(row, "lbl", Label.class);
            double gap = quality == null ? (row == named ? 0 : 1) : distance(label == null ? null : label.texts, quality);
            if(best == null || gap < nearest) {best = row; nearest = gap;}
        }
        if(best != null) return best;
        return last ? named : null;
    }

    /** Whether the row's shown quality is nearer to {@code quality} than the quality shown on {@code other}. */
    private static boolean closer(String text, Widget other, double quality) {
        Label label = GameObjects.field(other, "lbl", Label.class);
        return distance(text, quality) < distance(label == null ? null : label.texts, quality);
    }

    /** How far the "Q 231.6" of a row's text is from the quality; far away when the text has none. */
    static double distance(String text, double quality) {
        if(text == null) return Double.MAX_VALUE;
        java.util.regex.Matcher shown = java.util.regex.Pattern.compile("Q\\s*([0-9]+(?:[.,][0-9]+)?)\\s*$").matcher(text.trim());
        if(!shown.find()) return Double.MAX_VALUE / 2;
        return Math.abs(Double.parseDouble(shown.group(1).replace(',', '.')) - quality);
    }

    private void openBuilding() {
        if(building.removed) {end("building gone"); return;}
        Window window = window();
        if(window == null) {
            if(!clicked) {
                clicked = true; waited = 0;
                hearthborn.util.ObjectWindows.clicked(building);
                if(gui.map != null) {
                    Coord at = building.rc.floor(OCache.posres);
                    gui.map.wdgmsg("click", Coord.z, at, 3, 0, 0, (int)building.id, at, 0, -1);
                }
            } else if(waited > OPEN_WAIT) {
                gui.ui.error(hearthborn.L10n.tr("The granary did not open."));
                end("window did not open");
            }
            return;
        }
        List<Widget> all = granaryRows(window);
        if(all.isEmpty()) return;
        Widget row = row(window, rowWait > ROW_WAIT);
        if(row == null) {
            if(rowWait > ROW_WAIT) {
                report(window);
                gui.ui.error(hearthborn.L10n.tr("No granary row takes these seeds."));
                end("no row");
            }
            return;
        }
        sender = row; phase = Phase.SENT; waited = 0;
        sender.wdgmsg(message, args);
    }
    private double rowWait;

    /** What the window held when no row fitted, for the log: the hand's item and every row's picture, text and Put button. */
    private void report(Window window) {
        StringBuilder line = new StringBuilder("no row: hand=").append(resource).append(" kind=").append(kind).append(" quality=").append(quality);
        for(Widget row : granaryRows(window)) {
            Indir<Resource> icon = GameObjects.field(row, "icon", Indir.class);
            Label label = GameObjects.field(row, "lbl", Label.class);
            Button put = GameObjects.field(row, "pbtn", Button.class);
            String name;
            try {name = icon == null ? null : icon.get().name;} catch(Loading pending) {name = "loading";}
            line.append(" | ").append(name).append(" / ").append(label == null ? null : label.texts).append(" / put=").append(put != null && put.visible());
        }
        try {hearthborn.io.Logs.append("granary.log", java.time.Instant.now() + " " + line + "\n");} catch(java.io.IOException | RuntimeException unwritable) {}
    }

    /** The granary building itself: clicking it with seeds in hand puts them in like a click on one of its rows. */
    static boolean granaryName(String name) {
        return name != null && (name.equals("gfx/terobjs/granary") || name.startsWith("gfx/terobjs/granary-"));
    }

    static boolean barrelName(String name) {
        return name != null && (name.equals("gfx/terobjs/barrel") || name.startsWith("gfx/terobjs/barrel-"));
    }

    /** Alike items of the bag, other than the one that is moving now. */
    static List<WItem> alike(Inventory inv, GItem moving, String resource, String kind, Double quality) {
        List<WItem> result = new ArrayList<>();
        for(WItem candidate : InventoryActions.items(inv)) {
            if(!candidate.visible || candidate.item == moving || !candidate.item.res.get().name.equals(resource)) continue;
            if(!InventoryActions.kind(candidate).equals(kind)) continue;
            if(quality != null && Math.abs(InventoryActions.quality(candidate) - quality) > 0.005) continue;
            result.add(candidate);
        }
        return result;
    }

    private int moved;

    private void end() {end("done");}

    /** Ends the repeat and notes in the log why, and how many stacks went in and how many of the crop stayed behind in other qualities. */
    private void end(String why) {
        if(parent == null) return;
        try {
            int same = 0, other = 0;
            if(gui.maininv != null) for(WItem item : InventoryActions.items(gui.maininv)) {
                if(!item.visible || item.item == current || !item.item.res.get().name.equals(resource)) continue;
                if(quality == null || Math.abs(InventoryActions.quality(item) - quality) <= 0.005) same++; else other++;
            }
            hearthborn.io.Logs.append("granary.log", java.time.Instant.now() + " end: " + why + " moved=" + moved + " resource=" + resource + " quality=" + quality
                + " left-alike=" + same + " left-other-quality=" + other + " via=" + (building != null ? "building" : "row") + "\n");
        } catch(java.io.IOException | RuntimeException unwritable) {}
        destroy();
    }

    /** Whether the item in the hand is of the kind that is being put in. */
    private boolean matches(WItem hand) {
        return hand.item.res.get().name.equals(resource) && InventoryActions.kind(hand).equals(kind)
            && (quality == null || Math.abs(InventoryActions.quality(hand) - quality) <= 0.005);
    }

    /** Takes the next alike stack of the bag into the hand; false when there is none. */
    private boolean takeNext() {
        List<WItem> next = alike(gui.maininv, current, resource, kind, quality);
        if(next.isEmpty()) return false;
        current = next.get(0).item;
        current.wdgmsg("take", Coord.z);
        phase = Phase.TAKEN; waited = 0;
        return true;
    }

    public void tick(double dt) {
        super.tick(dt);
        if(sender != null && sender.parent == null) {
            if(building == null) {end("row removed"); return;}
            // The window of the granary was rebuilt: open it again and go on from the row of the new one.
            sender = null; phase = Phase.OPENING; clicked = false; rowWait = 0; waited = 0; opening = false;
        }
        if(gui.maininv == null || ++rounds > LIMIT) {end("limit"); return;}
        waited += dt;
        try {
            if(phase == Phase.OPENING) {
                if(gui.vhand == null) {
                    // The hand is empty after a rebuild: bring the next stack first.
                    if(!opening) {
                        opening = true;
                        if(!takeNext()) {end(); return;}
                        phase = Phase.OPENING;
                    } else if(waited > WAIT) end("the next stack did not come to the hand");
                    return;
                }
                opening = false;
                if(!matches(gui.vhand)) {end("another item came to the hand"); return;}
                current = gui.vhand.item;
                if(window() != null) rowWait += dt;
                openBuilding();
            } else if(phase == Phase.SENT) {
                if(gui.vhand != null) {
                    if(gui.vhand.item != current && current != null && matches(gui.vhand)) {
                        // The server put the stack away and took the next one into the hand: put that one in as well.
                        current = gui.vhand.item; waited = 0; moved++;
                        sender.wdgmsg(message, args);
                        return;
                    }
                    if(waited > WAIT) end("the server kept the stack in hand");
                    return;
                }
                moved++;
                if(!sendBatch()) end();
            } else if(phase == Phase.BATCH) {
                batchTick();
            } else {
                if(gui.vhand == null) {if(waited > WAIT) end("the next stack did not come to the hand"); return;}
                // The hand holds a new item once the server has handed the stack over, not the one that was clicked.
                if(!matches(gui.vhand)) {end("another item came to the hand"); return;}
                current = gui.vhand.item;
                phase = Phase.SENT; waited = 0;
                sender.wdgmsg(message, args);
            }
        } catch(Loading pending) {
            if(waited > WAIT) end("loading");
        }
    }
    private boolean opening;

    /** The stacks of the batch in flight, and how many of them were still in the bag at the last look. */
    private List<GItem> batch = new ArrayList<>();
    private int lastLeft;

    /**
     * Sends the next few stacks in one go: for each, "take it into the hand" and "drop the hand on the row". The server answers
     * messages in order, so there is no waiting for each stack to arrive; false when no stack of the bag is left.
     */
    private boolean sendBatch() {
        List<WItem> next = alike(gui.maininv, current, resource, kind, quality);
        if(next.isEmpty()) return false;
        batch = new ArrayList<>();
        for(WItem view : next) {
            if(batch.size() >= BATCH || sender == null || sender.parent == null) break;
            view.item.wdgmsg("take", Coord.z);
            sender.wdgmsg(message, args);
            batch.add(view.item);
        }
        if(batch.isEmpty()) return false;
        current = batch.get(batch.size() - 1);
        lastLeft = batch.size(); phase = Phase.BATCH; waited = 0;
        return true;
    }

    private void batchTick() {
        if(gui.vhand != null) {
            if(waited > WAIT * 2) end("the server kept the stack in hand");
            return;
        }
        int left = 0;
        for(WItem view : InventoryActions.items(gui.maininv)) if(batch.contains(view.item)) left++;
        if(left != lastLeft) {lastLeft = left; waited = 0;}
        if(left > 0) {
            if(waited > WAIT) end("the stacks did not leave the bag");
            return;
        }
        moved += batch.size();
        if(!sendBatch()) end();
    }
}
