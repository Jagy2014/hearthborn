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
 * Portrait, health bars and the other meters, independent of panel scale.
 *
 * @author Jagy2014
 */
public final class PortraitScale {
    private static volatile double value=load();
    private static double load(){
        double saved=Utils.getprefd("hearthborn/ui/portrait-scale",1);
        return Double.isFinite(saved)?Utils.clip(saved,.5,1.5):1;
    }
    public static double current(){return value;}
    public static void apply(UI ui,double scale){
        if(!Double.isFinite(scale))return;
        value=Utils.clip(scale,.5,1.5);
        Utils.setprefd("hearthborn/ui/portrait-scale",value);
        if(ui==null)return;
        GameUI gui=ui.root.getchild(GameUI.class);
        if(gui!=null)gui.applyPortraitScale();
    }
    private PortraitScale(){}
}
