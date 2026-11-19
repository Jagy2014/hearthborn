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
 * Presentation scale relative to the immutable size of the loaded UI assets.
 *
 * @author Jagy2014
 */
public final class LiveScale {
    private static volatile double requested=Double.NaN;
    public static double current(){return Double.isNaN(requested)?UI.scale(1.0):requested;}
    public static double ratio(){return current()/UI.scale(1.0);}
    public static void apply(double value){
        if(!Double.isFinite(value))return;
        requested=Utils.clip(value,1.0,Math.max(1.0,UI.maxscale()));
        Utils.setprefd("hearthborn/interface-scale",requested);
    }
    public static Coord logicalSize(Coord physical){return physical.div(ratio()).max(Coord.of(1,1));}
    public static Coord point(Coord point,Coord from,Coord to){
        if(from.x<=0 || from.y<=0)return point;
        return Coord.of((int)Math.round(point.x*(double)to.x/from.x),(int)Math.round(point.y*(double)to.y/from.y));
    }
    private LiveScale(){}
}
