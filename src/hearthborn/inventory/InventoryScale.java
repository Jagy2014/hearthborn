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

package hearthborn.inventory;

import haven.*;
import hearthborn.ui.ToolbarScale;

/**
 * Keep server cells and item geometry native; enlarge only the inventory view.
 *
 * @author Jagy2014
 */
public final class InventoryScale {
    public static final double SCALE=1.1;
    /** Furnace and herbalist cells carry several marks, so the grid is a little larger than a bag. Food tables stay at {@link #SCALE}. */
    public static final double OVEN=1.25;
    /** Containers and furnaces open a notch larger than the equipment window, which keeps {@link #OVEN}. */
    public static final double CONTAINER=1.35;
    public static Coord size(Coord size){return size(size,SCALE);}
    public static Coord size(Coord size,double scale){return Coord.of((int)Math.ceil(size.x*scale),(int)Math.ceil(size.y*scale));}
    public static Coord logical(Coord point){return logical(point,SCALE);}
    public static Coord logical(Coord point,double scale){return Coord.of((int)Math.floor(point.x/scale),(int)Math.floor(point.y/scale));}
    public static GOut graphics(GOut g,Coord size){return graphics(g,size,SCALE);}
    public static GOut graphics(GOut g,Coord size,double scale){return ToolbarScale.graphics(g,size,scale);}
    private InventoryScale(){}

    /** How much the cell an item sits in is magnified: its inventory, the equipment window, or the stack popup around it. */
    public static double zoomOf(haven.Widget item){
        for(haven.Widget up=item.parent;up!=null;up=up.parent){
            if(up instanceof haven.Inventory)return ((haven.Inventory)up).viewScale();
            if(up instanceof haven.Equipory)return haven.Equipory.SCALE;
            if(up instanceof ScaledContents)return ((ScaledContents)up).scale();
            if(up instanceof haven.Window)break;
        }
        return 1;
    }
}
