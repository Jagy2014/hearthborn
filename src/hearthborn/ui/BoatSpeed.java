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
import hearthborn.util.GameObjects;

/**
 * The walk/run selector does nothing aboard a boat, so it stays hidden there.
 *
 * @author Jagy2014
 */
public final class BoatSpeed {
    private static final String[] POSES={"rowing","rowboat-d","dugoutidle","dugoutrowan","coracleidle","coraclerowan"};
    private BoatSpeed(){}
    public static void apply(Speedget speed){
        if(speed==null)return;
        boolean aboard=aboard(speed.getparent(GameUI.class));
        if(speed.visible==aboard)speed.show(!aboard);
    }
    public static boolean aboard(GameUI gui){
        if(gui==null)return false;
        if(meter(gui))return true;
        if(gui.map==null)return false;
        Gob player=gui.map.player();
        if(player==null)return false;
        Drawable drawable=player.getattr(Drawable.class);
        if(drawable instanceof Composite){
            Composite body=(Composite)drawable;
            for(String pose:POSES)try{if(body.hasPose(pose))return true;}catch(Loading loading){}
        }
        Following following=player.getattr(Following.class);
        if(following==null)return false;
        Gob vehicle;try{vehicle=following.tgt();}catch(RuntimeException failure){return false;}
        if(vehicle==null)return false;
        try{return boat(GameObjects.resourceName(vehicle));}catch(Loading loading){return false;}
    }
    static boolean boat(String name){
        if(name==null||!name.startsWith("gfx/terobjs/vehicle/"))return false;
        switch(name.substring("gfx/terobjs/vehicle/".length())){
            case "rowboat":case "dugout":case "coracle":case "knarr":case "snekkja":
            case "raft":case "raft-seg":case "spark":return true;
            default:return false;
        }
    }
    private static boolean meter(Widget widget){
        for(Widget child=widget.child;child!=null;child=child.next){
            if(child instanceof IMeter&&StatusMeters.isBoatMeter((IMeter)child))return true;
            if(meter(child))return true;
        }
        return false;
    }
}
