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
 * Portrait and status bars stay at the action-grid size, then follow portrait scale instead of panel scale.
 *
 * @author Jagy2014
 */
public final class StatusScale {
    public static final double SCALE=1.40;
    public static double factor(){return SCALE*PortraitScale.current();}
    public static Coord size(Coord size){return ToolbarScale.size(size,factor());}
    public static Coord logical(Coord point){return ToolbarScale.logical(point,factor());}
    public static GOut graphics(GOut g,Coord size){return ToolbarScale.graphics(g,size,factor());}
    private StatusScale(){}
}
