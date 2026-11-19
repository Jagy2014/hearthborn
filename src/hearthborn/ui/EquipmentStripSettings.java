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
 * Optional equipment views; both hands are always present.
 *
 * @author Jagy2014
 */
public final class EquipmentStripSettings extends Window {
    public static void open(GameUI gui){
        if(gui==null)return;
        EquipmentStripSettings window=gui.getchild(EquipmentStripSettings.class);
        if(window==null){window=new EquipmentStripSettings();gui.add(window,gui.sz.sub(window.sz).div(2).max(Coord.z));window.move(gui.sz.sub(HudInterfaceScale.size(window)).div(2).max(Coord.z));}
        window.show();window.raise();gui.setfocus(window);
    }
    public EquipmentStripSettings(){
        super(Coord.z,"Equipment strip slots");reqclose(this::reqdestroy);
        Widget previous=add(new Label("Both hand slots are always visible."),Coord.z);
        for(int slot:EquipmentStrip.SLOTS){
            if(slot==6||slot==7)continue;
            String title;
            switch(slot){
                case 5:title="Belt";break;
                case 11:title="Backpack";break;
                case 19:title="Left Hand Pouch";break;
                case 20:title="Right Hand Pouch";break;
                case 22:title="Shoulders / wings";break;
                default:title="Cape";break;
            }
            previous=add(new CheckBox(title).state(()->EquipmentStrip.slotEnabled(slot)).set(value->EquipmentStrip.slotEnabled(slot,value)),previous.pos("bl").adds(0,6));
        }
        add(new Button(UI.scale(120),"Close",this::reqdestroy),previous.pos("bl").adds(0,12));pack();
    }
}
