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
import hearthborn.world.CropSprites;
import hearthborn.world.PlantGrowth;
import hearthborn.world.PlantVisibility;

/**
 * @author Jagy2014
 */
public final class PlantSettings {
    /** The preference groups the plants half of the combined section puts back to their defaults. */
    static final String[] RESET = {"hearthborn/plants/","hearthborn/world/flavor-mode","hearthborn/world/hitboxes"};
    /** Puts the plant and vegetation controls on {@code panel} from its top-left corner; returns the lowest one. */
    static Widget fill(OptWnd.Panel panel) {
        Widget previous = panel.add(new CheckBox("Hide trees").state(() -> PlantVisibility.hidden(false)).set(v -> PlantVisibility.hidden(false, v)), Coord.z);
        previous.settip("Replaces trees with their stumps. Enable the next option to hide bushes with the same shortcut.");
        previous = panel.add(new CheckBox("Hide bushes together with trees").state(PlantVisibility::includeBushes).set(PlantVisibility::includeBushes), previous.pos("bl").adds(0, 8));
        previous.settip("Uses small mulberry stumps for bushes when tree hiding is enabled.");
        previous=panel.add(new CheckBox("Hide flavor objects including ambient sounds").state(()->hearthborn.world.FlavorObjects.mode()==1).set(value->hearthborn.world.FlavorObjects.mode(value?1:0)),previous.pos("bl").adds(0,12));
        previous=panel.add(new CheckBox("Hide flavor objects but keep sounds").state(()->hearthborn.world.FlavorObjects.mode()==2).set(value->hearthborn.world.FlavorObjects.mode(value?2:0)),previous.pos("bl").adds(0,8));
        previous.settip("Decorative terrain objects are hidden. Ambient sounds remain. Changes apply immediately.");
        previous = panel.add(new CheckBox("Show crop growth stages").state(() -> PlantGrowth.enabled("crops")).set(v -> PlantGrowth.enabled("crops", v)), previous.pos("bl").adds(0, 15));
        previous.settip("Shows the current and final growth stages, for example 3/5, in colours from red to green. Fully grown crops are always shown; hold Space to show the other stages.");
        previous = panel.add(new CheckBox("Show tree growth").state(() -> PlantGrowth.enabled("trees")).set(v -> PlantGrowth.enabled("trees", v)), previous.pos("bl").adds(0, 8));
        previous = panel.add(new CheckBox("Show bush growth").state(() -> PlantGrowth.enabled("bushes")).set(v -> PlantGrowth.enabled("bushes", v)), previous.pos("bl").adds(0, 8));
        previous = panel.add(new CheckBox("Show growth above the large tree threshold").state(PlantGrowth::big).set(PlantGrowth::big), previous.pos("bl").adds(0, 8));
        previous.settip("Fully grown trees and bushes stay unlabelled until their growth reaches the chosen threshold.");
        Label threshold = panel.add(new Label(L10n.format("Large tree threshold: %d%%", PlantGrowth.threshold())), previous.pos("bl").adds(0, 10));
        previous = panel.add(new HSlider(UI.scale(220), 101, 200, PlantGrowth.threshold()) {
            public void changed() {
                PlantGrowth.threshold(val);
                threshold.settext(L10n.format("Large tree threshold: %d%%", val));
            }
        }, threshold.pos("bl").adds(0, 5));
        previous = panel.add(new CheckBox("Simple crops").state(CropSprites::simple).set(CropSprites::simple), previous.pos("bl").adds(0, 18));
        previous.settip("Draws one plant in the centre of each tile, including trellis crops. Requires an area reload; leave the area and return.", true);
        previous=panel.add(new CheckBox("Show hitboxes").state(hearthborn.map.CollisionOverlay::enabled).set(hearthborn.map.CollisionOverlay::enabled),previous.pos("bl").adds(0,12));
        return previous;
    }
    private PlantSettings() {}
}
