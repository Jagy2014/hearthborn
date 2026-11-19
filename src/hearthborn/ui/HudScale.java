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
 * The same local magnification as the action grid, on top of the user's UI scale.
 *
 * @author Jagy2014
 */
public final class HudScale {
    public static Coord size(Coord size){return ActionMenuFrame.scaledSize(size);}
    public static Coord logical(Coord point){return ActionMenuFrame.logical(point);}
    public static GOut graphics(GOut g,Coord size){return ActionMenuFrame.graphics(g,size);}
    private HudScale(){}
}
