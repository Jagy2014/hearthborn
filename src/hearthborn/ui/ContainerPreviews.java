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
 * @author Jagy2014
 */
public final class ContainerPreviews {
    public static boolean shiftOnly(){return Utils.getprefb("hearthborn/inventory/preview-shift-only",true);}
    public static void shiftOnly(boolean value){Utils.setprefb("hearthborn/inventory/preview-shift-only",value);}
    public static boolean allowed(UI ui){return !shiftOnly() || (ui!=null && ui.modshift);}
    private ContainerPreviews(){}
}
