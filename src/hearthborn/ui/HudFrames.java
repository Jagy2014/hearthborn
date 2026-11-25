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

import haven.Utils;
import haven.Window;

/** A visual-only preference for the portrait and dockable HUD windows. */
public final class HudFrames {
    private static final String PREF = "hearthborn/ui/hide-hud-frames";
    private static volatile boolean hidden = Utils.getprefb(PREF, false);

    public static boolean hidden(){return hidden;}

    public static void hidden(boolean value){
        hidden=value;
        Utils.setprefb(PREF,value);
    }

    /** Keep the painted sides in line with the compact map's tile viewport. */
    static int sideInset(Window.Deco deco,boolean right){
        boolean inset=deco instanceof SmallWindowDeco&&((SmallWindowDeco)deco).hidesHudFrame()
            ||deco instanceof PlainDeco&&hidden();
        return inset?(right?Window.wbox.bbroff().x:Window.wbox.btloff().x):0;
    }

    private HudFrames(){}
}
