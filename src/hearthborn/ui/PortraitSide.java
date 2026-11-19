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
 * The portrait cluster can sit in the top-right corner as a horizontal mirror.
 *
 * @author Jagy2014
 */
public final class PortraitSide {
    private static volatile Boolean onRight;
    private PortraitSide(){}
    public static boolean onRight(){
        if(onRight==null)onRight=Utils.getprefb("hearthborn/hud/portrait-right",false);
        return onRight;
    }
    public static void onRight(boolean value){onRight=value;Utils.setprefb("hearthborn/hud/portrait-right",value);}
}
