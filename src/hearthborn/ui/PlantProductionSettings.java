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

package hearthborn.ui;

import haven.*;

/**
 * One section for what grows and what is being made: plants first, production and readiness under them.
 *
 * @author Jagy2014
 */
public final class PlantProductionSettings {
    public static OptWnd.Panel panel(OptWnd options, OptWnd.Panel back) {
        String[] reset = new String[PlantSettings.RESET.length + ProductionSettings.RESET.length];
        System.arraycopy(PlantSettings.RESET, 0, reset, 0, PlantSettings.RESET.length);
        System.arraycopy(ProductionSettings.RESET, 0, reset, PlantSettings.RESET.length, ProductionSettings.RESET.length);
        OptWnd.Panel panel = options.new Panel().resettable(reset);
        Widget plants = PlantSettings.fill(panel);
        Widget production = ProductionSettings.fill(options, panel, Coord.of(0, plants.pos("bl").y + UI.scale(25)));
        int bottom = production.pos("bl").y;
        panel.add(options.new PButton(UI.scale(200), "Back", 27, back), Coord.of(0, bottom + UI.scale(20)));
        panel.pack();
        return panel;
    }
    private PlantProductionSettings() {}
}
