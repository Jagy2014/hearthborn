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

package hearthborn.world;
import haven.*;
import haven.render.*;
/**
 * Visual height only, matching vertical object scaling.
 *
 * @author Jagy2014
 */
public final class ObjectHeight extends GAttrib implements Gob.SetupMod {
    private static volatile int cupboards=Utils.getprefi("hearthborn/height/cupboards",100),walls=Utils.getprefi("hearthborn/height/walls",100);
    private int applied=100;private Pipe.Op transform;
    public ObjectHeight(Gob gob){super(gob);}
    public static int percent(boolean wall){return Utils.clip(wall?walls:cupboards,15,100);}
    public static void percent(boolean wall,int value){value=Utils.clip(value,15,100);if(wall)walls=value;else cupboards=value;known.clear();Utils.setprefi("hearthborn/height/"+(wall?"walls":"cupboards"),value);}
    private static final java.util.concurrent.ConcurrentHashMap<String,Integer> known=new java.util.concurrent.ConcurrentHashMap<>();
    /** Asked for every object on every tick, and the answer only depends on the resource and the two settings. */
    public static int percent(String resource){
        Integer value=known.get(resource);
        if(value==null){value=compute(resource);known.put(resource,value);}
        return value;
    }
    private static int compute(String resource){
        if(resource.startsWith("gfx/terobjs/arch/brickwall") && resource.contains("gate"))return 100;
        if(resource.equals("gfx/terobjs/cupboard")||resource.equals("gfx/terobjs/metalcabinet"))return percent(false);
        if(resource.startsWith("gfx/terobjs/arch/") && (resource.contains("palisade")||resource.contains("brickwall")||resource.contains("fence")))return percent(true);
        return 100;
    }
    public void ctick(double dt){
        try{Drawable drawable=gob.getattr(Drawable.class);Resource resource=drawable==null?null:drawable.getres();int value=resource==null?100:percent(resource.name);
            if(value!=applied){applied=value;transform=value==100?null:new Location(new Matrix4f(1,0,0,0,0,1,0,0,0,0,value/100f,0,0,0,0,1));}
        }catch(Loading ignored){}
    }
    public Pipe.Op gobstate(){return transform;}
}
