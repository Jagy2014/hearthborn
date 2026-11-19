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
import haven.resutil.WaterTile;
/**
 * @author Jagy2014
 */
public final class ItemProtection {
    public static boolean drops(){return Utils.getprefb("hearthborn/protection/drops",false);}
    public static void drops(boolean value){Utils.setprefb("hearthborn/protection/drops",value);}
    public static boolean water(){return Utils.getprefb("hearthborn/protection/water",false);}
    public static void water(boolean value){Utils.setprefb("hearthborn/protection/water",value);}
    /** Drops into water are refused unless Ctrl is held, whatever the general drop protection says. */
    public static boolean protectWater(MCache map,Coord2d at,int modifiers){
        if(!water()||(modifiers&UI.MOD_CTRL)!=0)return false;
        try{
            Coord tile=at.floor(MCache.tilesz);
            return map.tiler(map.gettile(tile)) instanceof WaterTile;
        }catch(Loading ignored){return false;}
    }
    public static boolean decals(){return Utils.getprefb("hearthborn/protection/decals",false);}
    public static void decals(boolean value){Utils.setprefb("hearthborn/protection/decals",value);}
    public static boolean protectDrop(int modifiers){return drops() && (modifiers & UI.MOD_CTRL)==0 && (modifiers & (UI.MOD_SHIFT|UI.MOD_META))!=(UI.MOD_SHIFT|UI.MOD_META);}
    public static boolean protectDecal(UI ui,Gob.Overlay overlay){
        if(!decals() || overlay.spr==null || overlay.spr.res==null)return false;
        String name=overlay.spr.res.name;
        if(!name.equals("gfx/terobjs/items/parchment-decal") && !name.equals("gfx/terobjs/items/decal-chest"))return false;
        return ui!=null && !ui.modctrl && !ui.modshift;
    }
    public static Object[] clickargs(UI ui,ClickData click) {
        Object[] args=click.clickargs();
        if(decals() && click.ci instanceof Gob.GobClick && args.length>=5)
            for(Object node:click.array())if(node instanceof Gob.Overlay && protectDecal(ui,(Gob.Overlay)node)) {
                args=args.clone();args[0]=0;args[3]=0;args[4]=-1;break;
            }
        return args;
    }
    private ItemProtection(){}
}
