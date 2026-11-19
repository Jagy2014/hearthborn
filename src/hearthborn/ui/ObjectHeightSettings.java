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
import hearthborn.world.ObjectHeight;
/**
 * @author Jagy2014
 */
public final class ObjectHeightSettings {
    public static OptWnd.Panel panel(OptWnd options,OptWnd.Panel back){
        OptWnd.Panel panel=options.new Panel();int y=0;
        for(boolean walls:new boolean[]{false,true}){
            final String title=walls?"Wall and fence height:":"Cupboard height:";
            Label label=panel.add(new Label(""),Coord.of(0,y));
            HSlider slider=panel.add(new HSlider(UI.scale(320),15,100,ObjectHeight.percent(walls)){
                public void changed(){ObjectHeight.percent(walls,val);label.settext(hearthborn.L10n.tr(title)+" "+val+"%");}
            },Coord.of(0,y+UI.scale(25)));slider.changed();y+=UI.scale(75);
        }
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),Coord.of(0,y+UI.scale(10)));panel.pack();return panel;
    }
    private ObjectHeightSettings(){}
}
