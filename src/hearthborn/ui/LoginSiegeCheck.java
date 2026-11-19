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
 * Ask the server once per game session; the server decides which claims are under siege.
 *
 * @author Jagy2014
 */
public final class LoginSiegeCheck {
    private boolean checked;
    public static boolean enabled(){return Utils.getprefb("hearthborn/login/check-siege",true);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/login/check-siege",value);}
    public void tick(GameUI gui){
        if(checked || gui.menu==null || gui.map==null)return;
        checked=true;
        if(enabled())gui.menu.wdgmsg("act","siegeptr");
    }
    public static boolean clear(String message){
        if(message==null)return false;
        String text=message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("there are no claims under siege")
            ||text.contains("здесь нет владений под осадой")
            ||text.contains("нет клаймов под осадой");
    }
}
