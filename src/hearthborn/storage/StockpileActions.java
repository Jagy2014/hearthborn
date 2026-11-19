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

package hearthborn.storage;

import haven.*;
import hearthborn.util.GameObjects;

/**
 * Prefer the held subtype on stockpiles, except for hide piles which retain native mixed filling.
 * A feeding trough treats Shift and Shift + Ctrl alike, as the server's Shift: the held item and the matching
 * seeds from the inventory go in, while Shift + Ctrl would also pour in every other item it accepts.
 *
 * @author Jagy2014
 */
public final class StockpileActions {
    public static final String TROUGH = "gfx/terobjs/trough";

    public static int itemModifiers(Gob target,int modifiers){
        if(target==null||modifiers!=UI.MOD_SHIFT&&modifiers!=(UI.MOD_SHIFT|UI.MOD_CTRL))return modifiers;
        try{
            return itemModifiers(GameObjects.resourceName(target),modifiers);
        }catch(Loading waiting){}
        return modifiers;
    }
    /** The modifiers to send for an item used on an object of the given resource. */
    public static int itemModifiers(String resource,int modifiers){
        if(resource==null||modifiers!=UI.MOD_SHIFT&&modifiers!=(UI.MOD_SHIFT|UI.MOD_CTRL))return modifiers;
        if(TROUGH.equals(resource))return UI.MOD_SHIFT;
        if("gfx/terobjs/stockpile-hide".equals(resource))return modifiers;
        if(resource.startsWith("gfx/terobjs/stockpile-"))return modifiers^UI.MOD_CTRL;
        return modifiers;
    }
    private StockpileActions(){}
}
