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
import haven.render.*;
/**
 * cupboard decal placement and unmodified decal colors.
 *
 * @author Jagy2014
 */
public final class ContainerDecals {
    public static boolean decal(String name){return "gfx/terobjs/items/parchment-decal".equals(name)||"gfx/terobjs/items/decal-chest".equals(name);}
    public static boolean decal(Gob.Overlay overlay){
        Resource resource=null;
        if(overlay.sm instanceof OCache.OlSprite)resource=((OCache.OlSprite)overlay.sm).res.get();
        else if(overlay.sm instanceof Sprite.Mill.FromRes)resource=((Sprite.Mill.FromRes)overlay.sm).res.get();
        if(resource==null && overlay.spr!=null)resource=overlay.spr.res;
        return resource!=null&&decal(resource.name);
    }
    public static boolean skipOffset(Resource resource,Class<?> layer,Object id){
        return layer==Skeleton.BoneOffset.class && "decal".equals(id) && resource.name.equals("gfx/terobjs/cupboard") && ContainerLook.enabled("decals-on-top");
    }
    public static void place(StaticSprite sprite,RenderTree.Slot slot){
        if(!ContainerLook.enabled("decals-on-top")||!sprite.res.name.equals("gfx/terobjs/items/parchment-decal")||!(sprite.owner instanceof Gob.Overlay))return;
        Gob gob=((Gob.Overlay)sprite.owner).gob;
        if("gfx/terobjs/cupboard".equals(hearthborn.util.GameObjects.resourceName(gob)))
            slot.cstate(Location.xlate(new Coord3f(-5,-5,17.5f)));
    }
}
