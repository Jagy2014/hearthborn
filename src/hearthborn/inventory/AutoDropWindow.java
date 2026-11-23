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
 * The rules of {@link AutoDrop} and of continuous dropping of chosen types: a switch, two conditions,
 * a quality limit per kind of mined item, and the types that the inventory drop button throws away while it is on.
 *
 * @author Jagy2014
 */
public final class AutoDropWindow extends Window {
    public static void open(GameUI gui) {
        if(gui == null) return;
        AutoDropWindow existing = gui.getchild(AutoDropWindow.class);
        // A window in its destruction animation cannot be revived by show().
        if(existing != null && !existing.visible()) {existing.destroy(); existing = null;}
        if(existing != null) {existing.show(); existing.raise(); return;}
        gui.add(new AutoDropWindow(gui), UI.scale(200, 100));
    }

    /** The window exists only on the client, so closing it cannot be left to the server. */
    public void reqclose() {reqdestroy();}

    private AutoDropWindow(GameUI gui) {
        super(Coord.z, "Auto-Drop Manager", true);
        Widget prev = add(new CheckBox("Enable Auto-Drop Items from Inventory").state(AutoDrop::enabled).set(value -> AutoDrop.enabled(value, ui)), UI.scale(0, 6));
        prev = add(new CheckBox("Include Other Container Inventories").state(AutoDrop::containers).set(AutoDrop::containers), prev.pos("bl").adds(0, 12));
        prev = add(new CheckBox("Only Drop when Pickaxe Cursor is Active").state(AutoDrop::pickaxeOnly).set(AutoDrop::pickaxeOnly), prev.pos("bl").adds(0, 2));
        prev = add(new Label("Mining specific items:"), prev.pos("bl").adds(0, 12));
        for(AutoDrop.Kind kind : AutoDrop.Kind.values()) {
            Widget box = add(new CheckBox(kind.label).state(kind::on).set(kind::on), prev.pos("bl").adds(0, kind == AutoDrop.Kind.STONES ? 10 : 6));
            add(new Label("Q <"), box.pos("ur").adds(0, 0).x(UI.scale(190)));
            add(new TextEntry(UI.scale(36), kind.limitText()) {
                protected void changed() {
                    String digits = text().replaceAll("[^\\d]", "");
                    if(!digits.equals(text())) {settext(digits); return;}
                    kind.below(digits);
                    super.changed();
                }
            }, box.pos("ur").adds(6, -2).x(UI.scale(212)));
            prev = box;
        }
        prev = add(new Label("Drop while the inventory button is on:"), prev.pos("bl").adds(0, 16));
        prev.settip(L10n.tr("Separate from mining rules above. Lists what is in the backpack (and types you already chose). Tick a row here to use the backpack drop button; quality is ignored."));
        add(new ContinuousDrop.TypeList(gui), prev.pos("bl").adds(0, 6));
        pack();
    }
}
