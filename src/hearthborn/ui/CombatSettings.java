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
import hearthborn.combat.ArcheryAim;
import hearthborn.combat.AutoCombat;
import hearthborn.combat.ChaseVector;
import hearthborn.combat.CombatPrefs;

/**
 * Combat display and control settings.
 *
 * @author Jagy2014
 */
public final class CombatSettings {
    private CombatSettings() {}

    public static OptWnd.Panel panel(OptWnd options) {
        OptWnd.Panel panel = options.new Panel().resettable("hearthborn/combat/");
        int y0 = 0;
        int step = UI.scale(22);
        int gap = UI.scale(8);
        int nest = UI.scale(24);
        int col = UI.scale(340);
        int y = y0;

        y = box(panel, 0, y, step, "Use new combat UI", CombatPrefs.alt(), CombatPrefs::alt,
            "Keeps the cooldown, openings and moves in the fixed layout. Off, they follow your character.");
        y = box(panel, 0, y, step, "Allow dragging combat UI", CombatPrefs.drag(), CombatPrefs::drag,
            "Drag the cluster by the cooldown circle.");
        panel.add(new Button(UI.scale(220), "Reset combat UI position").action(() -> CombatPrefs.shift(Coord.z)), nest, y);
        y += UI.scale(34) + gap;
        y = box(panel, 0, y, step, "Auto peace on combat start", CombatPrefs.peace(), CombatPrefs::peace,
            "Offers peace when a fight starts and the opponent is aggressive. Useful for taming.");
        y = box(panel, 0, y, step, "Re-aggro animals", CombatPrefs.reaggro(), CombatPrefs::reaggro,
            "If an animal leaves the fight on its own, attack it again within bow range and keep the compact combat UI up.");
        y = box(panel, 0, y, step, "Auto-pick attacks by openings", AutoCombat.enabled(), AutoCombat::enabled,
            "Picks the deck attack that matches the animal's largest opening. Never attacks players.");
        y = box(panel, 0, y, step, "Always mark current target", CombatPrefs.mark(), CombatPrefs::mark,
            "Marks the target even when it is the only one.");
        y = box(panel, 0, y, step, "Show chase vectors", ChaseVector.enabled(), ChaseVector::enabled,
            "Lines from chasers to their targets: attack queues, pickups and follows. White is you, green is party, red is others.");
        y = box(panel, 0, y, step, "Show archery aim range", ArcheryAim.enabled(), ArcheryAim::enabled,
            "While drawing a bow or aiming a spear or sling, shows max reach and the aim sector toward the cursor.");
        y = pair(panel, 0, y, step, nest, "Highlight party members in combat",
            CombatPrefs.colorParty(), CombatPrefs::colorParty, CombatPrefs.ringParty(), CombatPrefs::ringParty);
        y = pair(panel, 0, y, step, nest, "Highlight self in combat",
            CombatPrefs.colorSelf(), CombatPrefs::colorSelf, CombatPrefs.ringSelf(), CombatPrefs::ringSelf);
        y = pair(panel, 0, y, step, nest, "Highlight enemies in combat",
            CombatPrefs.colorEnemy(), CombatPrefs::colorEnemy, CombatPrefs.ringEnemy(), CombatPrefs::ringEnemy);
        int left = y;

        y = y0;
        y = box(panel, col, y, step, "Show combat info", CombatPrefs.info(), CombatPrefs::info,
            "Draws initiative and openings on the fighter.");
        Label offset = panel.add(new Label("Combat info vertical offset"), col + nest, y);
        Label value = panel.add(new Label(Integer.toString(CombatPrefs.height())), offset.pos("ur").adds(6, 0));
        y += step;
        panel.add(new HSlider(UI.scale(160), 1, 35, CombatPrefs.height()) {
            public void changed() {
                CombatPrefs.height(val);
                value.settext(Integer.toString(val));
            }
        }, col + nest, y);
        y += UI.scale(28);
        y = box(panel, col, y, step, "Simplified combat openings", CombatPrefs.simple(), CombatPrefs::simple,
            "Shows openings as a color and a number.");
        y = box(panel, col, y, step, "Display combat keys", CombatPrefs.keys(), CombatPrefs::keys, null);
        y = box(panel, col, y, step, "Show combat damage", CombatPrefs.damage(), CombatPrefs::damage,
            "Adds up the floating damage numbers on each fighter.");
        y = box(panel, col, y, step, "Clear player damage after combat", CombatPrefs.clearPlayer(), CombatPrefs::clearPlayer, null);
        y = box(panel, col, y, step, "Clear all damage after combat", CombatPrefs.clearAll(), CombatPrefs::clearAll, null);
        y = box(panel, col, y, step, "Show draggable health and stamina", CombatPrefs.bars(), CombatPrefs::bars, null);
        y = box(panel, col + nest, y, step, "Only during combat", CombatPrefs.barsCombat(), CombatPrefs::barsCombat, null);
        y = box(panel, col + nest, y, step, "Lock them from clicks and dragging", CombatPrefs.lockBars(), CombatPrefs::lockBars, null);

        int bottom = Math.max(left, y) + UI.scale(16);
        Widget back = panel.add(options.new PButton(UI.scale(200), "Back", 27, options.main), 0, bottom);
        panel.pack();
        back.c = Coord.of((panel.sz.x - back.sz.x) / 2, back.c.y);
        SettingsColumns.stack(panel, col);
        return(panel);
    }

    private static int box(OptWnd.Panel panel, int x, int y, int step, String text, boolean on, java.util.function.Consumer<Boolean> set, String tip) {
        CheckBox box = new CheckBox(text) {{ a = on; }};
        box.changed(set);
        if(tip != null)
            box.settip(L10n.text("tooltip", tip));
        panel.add(box, x, y);
        return(y + step);
    }

    private static int pair(OptWnd.Panel panel, int x, int y, int step, int nest, String title,
                            boolean color, java.util.function.Consumer<Boolean> setColor,
                            boolean ring, java.util.function.Consumer<Boolean> setRing) {
        panel.add(new Label(title), x, y);
        y += step;
        CheckBox byColor = new CheckBox("By coloring") {{ a = color; }};
        byColor.changed(setColor);
        panel.add(byColor, x + nest, y);
        y += step;
        CheckBox byRing = new CheckBox("By ring") {{ a = ring; }};
        byRing.changed(setRing);
        panel.add(byRing, x + nest, y);
        return(y + step);
    }
}
