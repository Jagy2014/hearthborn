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
import hearthborn.L10n;

/**
 * How the world is lit, which server effects are drawn and which objects are hidden.
 *
 * @author Jagy2014
 */
public final class GraphicsSettings {
    public static OptWnd.Panel panel(OptWnd options) {
        OptWnd.Panel panel = options.new Panel().resettable("hearthborn/world/effect/","hearthborn/world/night-vision","hearthborn/world/day","hearthborn/world/no-precipitation","hearthborn/hide/","hearthborn/world/grid-color");
        Widget previous = panel.add(new CheckBox("Always day").state(WorldDisplay::day).set(WorldDisplay::day), Coord.z);
        previous.settip("Lights the world as in daytime, whatever the server time is.");
        previous = panel.add(new CheckBox("Hide rain and snowfall").state(WorldDisplay::clear).set(WorldDisplay::clear), previous.pos("bl").adds(0, 6));
        previous.settip("Hides falling rain and snow; snow on the ground stays visible.");
        Label vision = panel.add(new Label(L10n.format("Night vision: %d%%", WorldDisplay.vision())), previous.pos("bl").adds(0, 12));
        previous = panel.add(new HSlider(UI.scale(220), 0, WorldDisplay.MAX_VISION, WorldDisplay.vision()) {
            public void changed() {
                WorldDisplay.vision(val);
                vision.settext(L10n.format("Night vision: %d%%", val));
            }
        }, vision.pos("bl").adds(0, 4));
        previous.settip("Mixes white into the scene light so nights and caves stay readable. Zero leaves the server light as it is.");
        previous = panel.add(new Label("Server effects"), previous.pos("bl").adds(0, 16));
        for(WorldDisplay.Effect effect : WorldDisplay.EFFECTS) {
            previous = panel.add(new CheckBox(effect.label).state(effect::hidden).set(effect::hidden), previous.pos("bl").adds(0, 6));
            previous.settip(effect.tip);
        }
        previous = panel.add(new Label("Grid lines on the ground (Ctrl+G)"), previous.pos("bl").adds(0, 16));
        int swatchY = previous.c.y + previous.sz.y + UI.scale(6);
        Widget gridSwatch = panel.add(new ColorChooser.Swatch("Grid lines", true, WorldDisplay::gridColor, color -> {
            WorldDisplay.gridColor(color);
            GameUI gui = options.getparent(GameUI.class);
            if(gui != null && gui.map != null) gui.map.refreshgrid();
        }), Coord.of(0, swatchY));
        panel.add(new Label("Colour and opacity of the lines"), Coord.of(UI.scale(40), swatchY + UI.scale(3)));
        previous = gridSwatch;
        previous = HidingSettings.add(panel, previous, UI.scale(16));
        panel.add(options.new PButton(UI.scale(200), "Back", 27, options.main), previous.pos("bl").adds(0, 20));
        panel.pack();
        return panel;
    }
    private GraphicsSettings() {}
}
