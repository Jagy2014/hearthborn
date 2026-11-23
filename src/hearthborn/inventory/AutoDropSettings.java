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
import hearthborn.L10n;

/**
 * The rules of {@link AutoDrop} as a section of the game settings: a switch, two conditions, and a quality limit per kind of mined item.
 *
 * @author Jagy2014
 */
public final class AutoDropSettings {
    public static OptWnd.Panel panel(OptWnd options, OptWnd.Panel back) {
        OptWnd.Panel panel = options.new Panel().resettable("hearthborn/autodrop/");
        Widget prev = panel.add(new CheckBox("Enable Auto-Drop Items from Inventory").state(AutoDrop::enabled).set(value -> AutoDrop.enabled(value, panel.ui)), Coord.z);
        prev = panel.add(new CheckBox("Include Other Container Inventories").state(AutoDrop::containers).set(AutoDrop::containers), prev.pos("bl").adds(0, 12));
        prev = panel.add(new CheckBox("Only Drop when Pickaxe Cursor is Active").state(AutoDrop::pickaxeOnly).set(AutoDrop::pickaxeOnly), prev.pos("bl").adds(0, 2));
        prev = panel.add(new Label("Mining specific items:"), prev.pos("bl").adds(0, 12));
        for(AutoDrop.Kind kind : AutoDrop.Kind.values()) {
            Widget box = panel.add(new CheckBox(kind.label).state(kind::on).set(kind::on), prev.pos("bl").adds(0, kind == AutoDrop.Kind.STONES ? 10 : 6));
            panel.add(new Label("Q <"), box.pos("ur").adds(0, 0).x(UI.scale(190)));
            panel.add(new TextEntry(UI.scale(36), kind.limitText()) {
                protected void changed() {
                    String digits = text().replaceAll("[^\\d]", "");
                    if(!digits.equals(text())) {settext(digits); return;}
                    kind.below(digits);
                    super.changed();
                }
            }, box.pos("ur").adds(6, -2).x(UI.scale(212)));
            prev = box;
        }
        prev = panel.add(new Label("Drop while the inventory button is on:"), prev.pos("bl").adds(0, 16));
        prev.settip(L10n.tr("Separate from mining rules above. Lists what is in the backpack (and types you already chose). Tick a row here to use the backpack drop button; quality is ignored."));
        GameUI gui = panel.ui == null ? null : panel.ui.root.getchild(GameUI.class);
        Widget list = panel.add(new ContinuousDrop.TypeList(gui), prev.pos("bl").adds(0, 6));
        panel.add(options.new PButton(UI.scale(200), "Back", 27, back), list.pos("bl").adds(0, 20));
        panel.pack();
        return panel;
    }
    private AutoDropSettings() {}
}
