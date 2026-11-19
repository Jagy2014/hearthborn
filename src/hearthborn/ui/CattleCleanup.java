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
/**
 * Only the server's explicit roster death flag permits automatic removal.
 *
 * @author Jagy2014
 */
public final class CattleCleanup {
    public static boolean enabled(){return Utils.getprefb("hearthborn/croster/remove-dead",true);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/croster/remove-dead",value);}
    public static boolean dead(Object entry){
        try{
            java.lang.reflect.Field flag=entry.getClass().getField("dead");
            return flag.getType()==boolean.class && flag.getBoolean(entry);
        }catch(ReflectiveOperationException unavailable){return false;}
    }
}
