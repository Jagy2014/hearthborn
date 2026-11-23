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
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.world.ObjectHighlight;
import java.util.*;

/**
 * Local sightings only: the server's siegeptr response does not enumerate these objects.
 *
 * @author Jagy2014
 */
public final class NearbySiegeCheck {
    private double next;
    public static boolean enabled(){return Utils.getprefb("hearthborn/siege/nearby",true);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/siege/nearby",value);}
    public static String kind(String resource){
        if("gfx/terobjs/vehicle/bram".equals(resource))return "Battering ram";
        if("gfx/terobjs/vehicle/wreckingball".equals(resource))return "Wrecking ball";
        if("gfx/terobjs/vehicle/catapult".equals(resource))return "Catapult";
        return null;
    }
    private static final class Sighting extends GAttrib {
        String resource;
        boolean announced,approached;
        Sighting(Gob gob){super(gob);}
        void resource(String value){
            if(!Objects.equals(resource,value)){resource=value;announced=false;approached=false;}
        }
        public void ctick(double dt){try{resource(GameObjects.resourceName(gob));}catch(Loading pending){}}
    }
    private static Sighting sighting(Gob gob,String resource){
        Sighting state=gob.getattr(Sighting.class);
        if(state==null){state=new Sighting(gob);gob.setattr(state);}
        state.resource(resource);return state;
    }
    public static void lostSight(Gob gob){
        Sighting state=gob.getattr(Sighting.class);
        if(state!=null){state.announced=false;state.approached=false;}
    }
    public static boolean ownsIconSound(String resource){
        return kind(resource)!=null||"gfx/terobjs/vehicle/wreckingball-fold".equals(resource);
    }
    public static boolean pointerVisible(Gob gob,Coord2d player){
        if(gob.removed||gob.rc==null)return false;
        String resource=GameObjects.resourceName(gob);
        if(kind(resource)==null){Sighting old=gob.getattr(Sighting.class);if(old!=null)old.resource(resource);return false;}
        Sighting state=sighting(gob,resource);
        if(player!=null&&player.dist(gob.rc)<3*MCache.tilesz.x)state.approached=true;
        return !state.approached;
    }
    private static java.util.function.Consumer<UI> sound(GameUI gui,Gob gob){
        if(gui.iconconf==null)return null;
        GobIcon icon=gob.getattr(GobIcon.class);
        if(icon==null)icon=hearthborn.map.RadarIcons.icon(gob,null);
        if(icon==null)return null;
        GobIcon.Setting setting=gui.iconconf.get(icon.icon());
        return setting!=null&&setting.notify?setting.notification():null;
    }
    static void report(GameUI gui,Gob gob,Gob player){
        if(gob.removed||gob.rc==null)return;
        String resource=GameObjects.resourceName(gob),kind=kind(resource);
        if(kind==null){Sighting old=gob.getattr(Sighting.class);if(old!=null)old.resource(resource);return;}
        Sighting state=sighting(gob,resource);if(state.announced)return;
        java.util.function.Consumer<UI> sound=sound(gui,gob);
        state.announced=true;
        gob.setattr(new ObjectHighlight(gob));
        String message=L10n.format("Nearby: %s - %d tiles away (loaded objects only).",L10n.tr(kind),(int)Math.ceil(gob.rc.dist(player.rc)/MCache.tilesz.x));
        gui.ui.msg(message,java.awt.Color.WHITE,sound==null?UI.InfoMessage.sfx:null);
        if(sound!=null)sound.accept(gui.ui);
    }
    public void tick(GameUI gui){
        if(!enabled()||gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;
        Gob player=gui.map.player();if(player==null||player.rc==null||Utils.rtime()<next)return;next=Utils.rtime()+2;
        List<Gob> objects=new ArrayList<>();OCache cache=gui.ui.sess.glob.oc;
        synchronized(cache){for(Gob gob:cache)objects.add(gob);}
        for(Gob gob:objects)try{report(gui,gob,player);}catch(Loading pending){}catch(Resource.NoSuchResourceException missing){hearthborn.map.RadarIcons.absent(missing.name);}
    }
}
