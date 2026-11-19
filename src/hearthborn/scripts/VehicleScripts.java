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

package hearthborn.scripts;
import hearthborn.navigation.Walking;

import haven.*;
import haven.resutil.WaterTile;
import hearthborn.util.GameObjects;
import java.util.*;
import java.util.function.Predicate;

/**
 * Vehicle helpers using the cancellable UI-thread action queue.
 *
 * @author Jagy2014
 */
public final class VehicleScripts {
    static final String CORACLE="gfx/terobjs/vehicle/coracle", SKIS="gfx/terobjs/vehicle/skis-wilderness";
    static boolean supports(String script){return Arrays.asList("coracle","skis").contains(script);}
    static String name(Gob gob){String name=GameObjects.resourceName(gob);return name==null?"":name;}
    static boolean pose(Gob gob,String pose){Drawable d=gob.getattr(Drawable.class);return d instanceof Composite&&((Composite)d).hasPose(pose);}
    static long following(Gob gob){Following f=gob.getattr(Following.class);return f==null?-1:f.tgt;}
    static boolean item(GItem item,boolean coracle){
        Resource resource=item.resource();String path=resource.name;
        if(path.equals("gfx/invobjs/"+(coracle?"coracle":"wskisitem")))return true;
        Resource.Tooltip tooltip=resource.layer(Resource.tooltip);
        return tooltip!=null&&tooltip.original.equals(coracle?"Coracle":"Wilderness Skis");
    }
    static boolean deep(MCache map,Coord tile){
        int id=map.gettile(tile);Tiler tiler=map.tiler(id);Resource resource=map.tilesetr(id);
        if(tiler==null||resource==null)throw new Loading();
        return tiler instanceof WaterTile&&resource.name.contains("deep");
    }
    static boolean safeShore(Predicate<Coord> deep,Coord center){
        if(!deep.test(center))return true;
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if((x!=0||y!=0)&&!deep.test(center.add(x,y)))return true;
        return false;
    }
    static class Task extends ScriptRunner.Task {
        final String script;Gob target;
        Task(GameUI gui,String script){super(gui);this.script=script;cleanup=()->{
            if(gui.map!=null)Walking.cancel(gui.map);
            returnHeld();
        };}
        Gob player(){return gui.map.player();}
        boolean hasPose(Gob gob,String value){return pose(gob,value);}
        Gob nearest(Predicate<Gob> filter,double radius){
            Gob best=null;double distance=radius;
            for(Gob gob:gui.ui.sess.glob.oc){
                if(gob.removed||gob.rc==null||gob==player())continue;
                double d=gob.rc.dist(player().rc);
                if(d<=distance&&filter.test(gob)){best=gob;distance=d;}
            }
            return best;
        }
        String ridingType(){
            if(hasPose(player(),"coracleidle")||hasPose(player(),"coraclerowan"))return CORACLE;
            if(hasPose(player(),"skian-idle")||hasPose(player(),"skian-walk")||hasPose(player(),"skian-run"))return SKIS;
            if(hasPose(player(),"wagondrivan")||hasPose(player(),"wagonsittan"))return "gfx/terobjs/vehicle/wagon";
            return null;
        }
        Gob ridden(){
            long id=following(player());
            if(id>=0){Gob vehicle=gui.ui.sess.glob.oc.getgob(id);if(vehicle==null)throw new Loading();return vehicle;}
            String type=ridingType();
            if(type==null)return null;
            Gob vehicle=nearest(g->name(g).equals(type),55);
            if(vehicle==null)throw new IllegalStateException("The vehicle is occupied or unavailable.");
            return vehicle;
        }
        boolean aboard(Gob vehicle){return following(player())==vehicle.id||name(vehicle).equals(ridingType())&&vehicle.rc.dist(player().rc)<=55;}
        void interact(Gob gob,int button){
            if(gob.removed)throw new IllegalStateException("Target disappeared.");
            Coord at=gob.rc.floor(OCache.posres);
            Object[] args={Coord.z,at,button,0,0,(int)gob.id,at,0,-1};
            if(!Walking.start(gui.map,gob.rc,args,gob))click(gob,button);
        }
        void choose(Gob gob,String option){
            add(()->{flower=option;interact(gob,3);},()->flower==null,30);
        }
        void start(){add(()->portable(script.equals("coracle")));}
        boolean occupied(Gob vehicle){for(Gob gob:gui.ui.sess.glob.oc)if(!gob.removed&&following(gob)==vehicle.id)return true;return false;}
        boolean mountable(Gob gob,boolean coracle){
            if(!name(gob).equals(coracle?CORACLE:SKIS)||occupied(gob))return false;
            ResDrawable drawable=gob.getattr(ResDrawable.class);if(drawable==null)return false;
            MessageBuf state=drawable.stateData();return !state.eom()&&state.uint8()==(coracle?22:0);
        }
        void portable(boolean coracle){
            String type=coracle?CORACLE:SKIS;
            Gob riding=ridden();
            if(riding!=null){
                if(!name(riding).equals(type))throw new IllegalStateException("Leave the current vehicle first.");
                pickup(riding,coracle);return;
            }
            GItem carried=null;for(GItem item:ShortcutRunner.owned(gui))if(item(item,coracle)){carried=item;break;}
            if(carried==null){
                Gob nearby=nearest(g->mountable(g,coracle),66);
                if(nearby==null)throw new IllegalStateException(coracle?"No coracle in your belongings or nearby.":"No skis in your belongings or nearby.");
                mount(nearby,coracle);return;
            }
            final GItem source=carried;
            final Set<Long> before=new HashSet<>();
            add(()->{
                if(coracle)checkLaunch();
                before.clear();for(Gob gob:gui.ui.sess.glob.oc)if(name(gob).equals(type))before.add(gob.id);
                source.wdgmsg("drop",Coord.z);
            },()->{
                if(source.parent!=null)return false;
                target=nearest(g->!before.contains(g.id)&&mountable(g,coracle),55);
                return target!=null;
            },10);
            add(()->mount(target,coracle));
        }
        void checkLaunch(){
            MCache map=gui.ui.sess.glob.map;Coord tile=player().rc.floor(MCache.tilesz);int id=map.gettile(tile);
            String name=map.tilesetr(id).name;
            if(deep(map,tile)||!(map.tiler(id) instanceof WaterTile||name.matches("gfx/tiles/(bog|bogwater|fen|fenwater|swamp|swampwater|marsh|marshwater)")))
                throw new IllegalStateException("Stand in shallow water or a swamp before launching the coracle.");
        }
        void mount(Gob vehicle,boolean coracle){
            if(!mountable(vehicle,coracle))throw new IllegalStateException("The vehicle is occupied or unavailable.");
            target=vehicle;choose(vehicle,coracle?"Into the blue yonder!":"Ski off");
            add(()->{},()->aboard(vehicle),20);
        }
        boolean capeFree(){Equipory eq=gui.equipment();if(eq==null)return false;
            for(WItem view:eq.children(WItem.class))if(view.c.equals(Equipory.ecoords[21].add(1,1)))return false;
            return true;
        }
        void pickup(Gob vehicle,boolean coracle){
            if(coracle){MCache map=gui.ui.sess.glob.map;
                if(!safeShore(tile->deep(map,tile),player().rc.floor(MCache.tilesz)))throw new IllegalStateException("Move closer to shore before picking up the coracle.");
            }
            if(!capeFree()&&!ScriptRunner.fits(gui.maininv,Inventory.sqsz.mul(coracle?4:2,3)))
                throw new IllegalStateException("Not enough space in the inventory. Collection stopped.");
            Set<GItem> before=Collections.newSetFromMap(new IdentityHashMap<>());before.addAll(ShortcutRunner.owned(gui));
            target=vehicle;choose(vehicle,"Pick up");
            add(()->{},()->{
                if(aboard(vehicle))return false;
                if(hand()!=null){if(!item(hand(),coracle))throw new IllegalStateException("The cursor is holding another item.");held=hand();return true;}
                for(GItem item:ShortcutRunner.owned(gui))if(!before.contains(item)&&item(item,coracle))return true;
                return false;
            },15);
            add(()->returnHeld(),()->hand()==null,5);
        }
    }
    private VehicleScripts(){}
}
