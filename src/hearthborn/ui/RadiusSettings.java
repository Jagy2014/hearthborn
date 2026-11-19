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
import hearthborn.world.GobMarks;

/**
 * @author Jagy2014
 */
public final class RadiusSettings {
    public static OptWnd.Panel panel(OptWnd options,OptWnd.Panel back){
        OptWnd.Panel panel=options.new Panel();
        CheckBox show=new CheckBox("Show object radius").suffix(RadiusSettings::key);
        show.state(GobMarks::objects).set(GobMarks::objects);
        Widget enabled=panel.add(show,Coord.z);
        enabled.settip("The radius command switches only objects marked By command. Always-visible ranges stay on.");
        int top=enabled.sz.y+UI.scale(18);
        panel.add(new Label("By command"),UI.scale(220,0).add(0,top));
        panel.add(new Label("Always"),UI.scale(340,0).add(0,top));
        int y=top+UI.scale(28);
        for(GobMarks.RadiusType type:GobMarks.RadiusType.values()){
            panel.add(new Label(type.label),Coord.of(0,y+UI.scale(3)));
            panel.add(new CheckBox("").state(()->GobMarks.radiusMode(type)==1).set(value->GobMarks.radiusMode(type,value?1:0)),Coord.of(UI.scale(245),y));
            panel.add(new CheckBox("").state(()->GobMarks.radiusMode(type)==2).set(value->GobMarks.radiusMode(type,value?2:0)),Coord.of(UI.scale(355),y));
            y+=UI.scale(32);
        }
        panel.add(new Label("Uncheck both options to hide an object's radius."),Coord.of(0,y+UI.scale(8)));
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),Coord.of(0,y+UI.scale(45)));panel.pack();return panel;
    }
    /** The key of the radius command as it stands now, for the end of the first line. */
    static String key(){
        KeyMatch key=hearthborn.input.GlobalKeys.binding("TOGGLE_GOB_RADIUS").key();
        return key==null||key==KeyMatch.nil?hearthborn.L10n.tr("no key"):key.name();
    }
    private RadiusSettings(){}
}
