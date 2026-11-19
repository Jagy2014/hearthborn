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
 * The original gold window moulding with straight sides and quiet corners.
 *
 * @author Jagy2014
 */
public final class HudFrame {
    public static void draw(GOut g,Coord size){
        UiTheme.background(g,size);
        Window.wbox.draw(g,Coord.z,size);
    }
}
