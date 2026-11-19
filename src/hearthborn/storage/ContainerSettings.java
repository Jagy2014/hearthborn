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

package hearthborn.storage;
import hearthborn.ui.ColorChooser;
import hearthborn.ui.ItemProtection;
import hearthborn.ui.ObjectHeightSettings;

import haven.*;
import hearthborn.L10n;
import hearthborn.storage.ContainerLook;
import java.awt.Color;

/**
 * @author Jagy2014
 */
public final class ContainerSettings {
    public static OptWnd.Panel panel(OptWnd options, OptWnd.Panel back) {
        OptWnd.Panel panel=options.new Panel().resettable("hearthborn/containers/","hearthborn/height/");
        Widget previous=null;
        previous=panel.add(options.new PButton(UI.scale(300),"Container overview controls",'o',()->hearthborn.inventory.OverviewSettings.panel(options,panel)));
        previous=check(panel,previous,"Show barrel contents","labels","Shows live contents on barrels, demijohns and cisterns. Clears the label when the vessel is empty.");
        previous=panel.add(new Button(UI.scale(300),hearthborn.storage.ContainerContents.modeLabel(),false,()->{}) {public void click(){hearthborn.storage.ContainerContents.mode((hearthborn.storage.ContainerContents.mode()+1)%3);change(hearthborn.storage.ContainerContents.modeLabel());}},previous.pos("bl").adds(0,8));
        previous=check(panel,previous,"Shorten contents labels","short-labels","Removes common filler words from contents labels.");
        previous=check(panel,previous,"Display container fullness","fullness","Uses server states to colour empty and full containers. No percentage is guessed.");
        previous=check(panel,previous,"Cupboard use default materials","default-materials","All cupboards and metal cabinets will have their default look.");
        previous=check(panel,previous,"Show cupboard decals on top","decals-on-top","Moves cupboard decals from the door to the top. Reload the area or reapply the decal after changing this setting.");
        previous=panel.add(new CheckBox("Container decal pickup protection").state(ItemProtection::decals).set(ItemProtection::decals),previous.pos("bl").adds(0,8)).settip("Hold Ctrl or Shift to pick up a container decal.");
        previous=check(panel,previous,"Highlight partially filled containers","partial","When disabled, partially filled containers keep their normal colour.");
        previous=check(panel,previous,"Highlight finished objects","finished","Highlights drying racks, tanning tubs and curding tubs when their contents are ready.");
        previous=panel.add(options.new PButton(UI.scale(250),"Object heights",'h',()->ObjectHeightSettings.panel(options,panel)),previous.pos("bl").adds(0,12));
        previous=panel.add(new Label("Container colours"),previous.pos("bl").adds(0,15));
        String[] titles={"Empty","Empty glass","Partially filled","Full","Finished"};
        for(ContainerLook.State state:ContainerLook.State.values()) {
            int y=previous.c.y+previous.sz.y+UI.scale(6);
            Widget swatch=panel.add(new ColorChooser.Swatch(titles[state.ordinal()],true,() -> ContainerLook.color(state),color -> {ContainerLook.color(state,color);ContainerLook.changed();}),Coord.of(0,y));
            previous=panel.add(new Label(titles[state.ordinal()]),Coord.of(UI.scale(40),y+UI.scale(3)));
            if(previous.sz.y<swatch.sz.y)previous=swatch;
        }
        previous=panel.add(new Button(UI.scale(200),"Reset container colours").action(() -> {ContainerLook.resetColors();ContainerLook.changed();}),previous.pos("bl").adds(0,12));
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),previous.pos("bl").adds(0,12));
        panel.pack();return panel;
    }
    private static Widget check(Widget panel,Widget previous,String title,String key,String tip) {
        return panel.add(new CheckBox(title).state(() -> ContainerLook.enabled(key)).set(value -> {
            ContainerLook.enabled(key,value);ContainerLook.changed();
        }),previous==null?Coord.z:previous.pos("bl").adds(0,8)).settip(tip,true);
    }
    private ContainerSettings() {}
}
