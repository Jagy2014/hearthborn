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

package hearthborn;

import haven.*;
import hearthborn.cheese.CheeseObserver;
import hearthborn.planner.ClaimSurvey;
import hearthborn.quality.QualityRoster;
import hearthborn.quality.RosterStore;
import hearthborn.quality.VesselInfo;
import hearthborn.storage.ContainerContents;
import hearthborn.storage.StonePileColors;
import hearthborn.timers.ClaimTimers;
import hearthborn.timers.ObjectTimers;
import hearthborn.util.ObjectWindows;
import hearthborn.world.ObjectNames;
import java.util.*;

/**
 * Dispatches received world/window observations to their owning feature modules.
 *
 * @author Jagy2014
 */
public final class ClientObservations {
    private static final Map<GameUI, Double> scanned = new WeakHashMap<>();

    public static void tick(GameUI gui) {
        if(gui.ui == null || gui.ui.sess == null)
            return;
        double now = Utils.rtime();
        if(now - scanned.getOrDefault(gui, 0.0) < .5)
            return;
        scanned.put(gui, now);
        ContainerContents.ui(gui);
        ObjectTimers.observe(gui);
        StonePileColors.tick(gui);
        CheeseObserver.beginScan();
        for(Widget child : gui.children()) {
            if(!(child instanceof Window) || !child.visible())
                continue;
            Window window = (Window)child;
            try {
                StonePileColors.observe(window, gui);
                CheeseObserver.observe(window, gui);
                if(vessel(window.cap)) {
                    Gob gob = ObjectWindows.target(window, gui, RosterStore::isVesselGob);
                    if(gob != null)
                        VesselInfo.observe(window, gob);
                }
                ObjectNames.observe(window, gui);
                ContainerContents.observe(window, gui);
                ClaimTimers.claimWindow(window, gui);
            } catch(Loading ignored) {}
        }
        List<Gob> objects = gui.ui.sess.glob.oc.snapshot();
        ObjectNames.discover(objects);
        ClaimSurvey.observe(gui);
        ClaimTimers.observe(gui, objects);
        for(Gob gob : objects) {
            try {
                QualityRoster.observe(gob);
            } catch(Loading ignored) {}
        }
    }

    private static boolean vessel(String caption) {
        for(String name : new String[] {"Barrel", "Cistern", "Demijohn"}) {
            if(name.equals(caption) || L10n.tr(name).equals(caption))
                return true;
        }
        return false;
    }

    private ClientObservations() {}
}
