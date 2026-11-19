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
 * Matching gold resize stripes with a dark outline for either window corner.
 *
 * @author Jagy2014
 */
public final class ResizeMarks {
    public static void draw(GOut g, Coord corner, boolean left){
        int far=UI.scale(15),near=UI.scale(3),mid=UI.scale(9);
        Coord a=left?corner.add(near,-far):corner.sub(far,near);
        Coord b=left?corner.add(far,-near):corner.sub(near,far);
        Coord c=left?corner.add(near,-mid):corner.sub(mid,near);
        Coord d=left?corner.add(mid,-near):corner.sub(near,mid);
        g.chcolor(42,29,13,255);g.line(a,b,UI.scale(3));g.line(c,d,UI.scale(3));
        g.chcolor(233,193,112,255);g.line(a,b,UI.scale(1));g.line(c,d,UI.scale(1));
        g.chcolor();
    }
    private ResizeMarks(){}
}
