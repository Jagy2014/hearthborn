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
import hearthborn.map.MapDisplay;

/**
 * The shore scout's settings and its start and stop button. The settings are read when the scout starts.
 *
 * @author Jagy2014
 */
final class ScoutWindow extends Window {
    private final GameUI gui;
    private final Label every, status, best;
    private final Button toggle;
    private boolean running;
    private int shown = -1;
    private String shownBest = "";

    ScoutWindow(GameUI gui) {
        super(Coord.z, "Shore scout");
        this.gui = gui;
        int gap = UI.scale(6);
        Widget water = add(new CheckBox("Water").state(() -> Utils.getprefb(ScoutScripts.WATER_PREF, true)).set(on -> Utils.setprefb(ScoutScripts.WATER_PREF, on)), Coord.z);
        Widget clay = add(new CheckBox("Clay").state(() -> Utils.getprefb(ScoutScripts.CLAY_PREF, true)).set(on -> Utils.setprefb(ScoutScripts.CLAY_PREF, on)), water.pos("bl").adds(0, gap));
        clay.settip("Clay is only dug in shallow water.");
        Widget mussels = add(new CheckBox("Mussels").state(() -> Utils.getprefb(ScoutScripts.MUSSELS_PREF, true)).set(on -> Utils.setprefb(ScoutScripts.MUSSELS_PREF, on)), clay.pos("bl").adds(0, gap));
        mussels.settip(L10n.format(
            "At each stop, after water/clay samples, picks mussels within %d tiles (Shift+right-click). Only when mussels run alone may the boat sail up to %d tiles to reach them - so samples keep their spot.",
            ScoutScripts.MUSSEL_PICK_TILES, ScoutScripts.MUSSEL_TILES));
        Widget markers = add(new CheckBox("Show sample markers").state(() -> MapDisplay.enabled("samples")).set(on -> MapDisplay.enabled("samples", on)), mussels.pos("bl").adds(0, gap));
        Widget side = add(new CheckBox("Shore on the right").state(() -> Utils.getprefb(ScoutScripts.RIGHT_PREF, true)).set(on -> Utils.setprefb(ScoutScripts.RIGHT_PREF, on)), markers.pos("bl").adds(0, gap));
        side.settip("Which side of the boat the shore stays on while it sails along it.");
        every = add(new Label(""), side.pos("bl").adds(0, gap * 2));
        HSlider slider = add(new HSlider(UI.scale(200), ScoutScripts.MIN_EVERY, ScoutScripts.MAX_EVERY, ScoutScripts.every()) {
            public void changed() {Utils.setprefi(ScoutScripts.EVERY_PREF, val); label();}
        }, every.pos("bl").adds(0, gap));
        toggle = add(new Button(UI.scale(200), "Start", false, () -> ScoutScripts.start(gui)), slider.pos("bl").adds(0, gap * 2));
        status = add(new Label(""), toggle.pos("bl").adds(0, gap));
        best = add(new Label(""), status.pos("bl").adds(0, gap));
        label();
        pack();
    }

    private void label() {
        every.settext(L10n.format("Sample every %d tiles (2–%d)", ScoutScripts.every(), ScoutScripts.MAX_EVERY));
    }

    private static String bestLine(Double water, Double clay) {
        StringBuilder text = new StringBuilder();
        if(water != null)
            text.append(L10n.format("Best water: %s", Utils.odformat(water, 1)));
        if(clay != null) {
            if(text.length() > 0) text.append('\n');
            text.append(L10n.format("Best clay: %s", Utils.odformat(clay, 1)));
        }
        return text.toString();
    }

    /** Refresh labels after the scout path/markers are cleared on the map. */
    static void clearKept(GameUI gui) {
        if(gui == null) return;
        ScoutWindow window = gui.getchild(ScoutWindow.class);
        if(window != null) window.refresh(MapDisplay.scoutSummary(gui));
    }

    private void refresh(MapDisplay.Summary summary) {
        int samples = summary == null || summary.taken <= 0 && summary.water == null && summary.clay == null ? -1 : summary.taken;
        Double water = summary == null ? null : summary.water;
        Double clay = summary == null ? null : summary.clay;
        String bestText = bestLine(water, clay);
        shown = samples;
        shownBest = bestText;
        status.settext(samples < 0 ? "" : L10n.format("Samples taken: %d", Math.max(0, samples)));
        best.settext(bestText);
        pack();
    }

    public void tick(double dt) {
        super.tick(dt);
        ScriptRunner.Task task = ScriptRunner.current(gui);
        boolean now = task instanceof ScoutScripts.Task;
        if(now != running) {running = now; toggle.change(now ? "Stop" : "Start");}
        MapDisplay.Summary summary = now
            ? new MapDisplay.Summary(((ScoutScripts.Task)task).samples, ((ScoutScripts.Task)task).bestWater, ((ScoutScripts.Task)task).bestClay)
            : MapDisplay.scoutSummary(gui);
        int samples = summary.taken <= 0 && summary.water == null && summary.clay == null ? -1 : summary.taken;
        String bestText = bestLine(summary.water, summary.clay);
        if(samples != shown || !bestText.equals(shownBest))
            refresh(summary);
    }

    /** Closing the window stops the scout. */
    public void reqclose() {
        if(ScriptRunner.current(gui) instanceof ScoutScripts.Task) ScriptRunner.cancel(gui);
        ui.destroy(this);
    }
}
