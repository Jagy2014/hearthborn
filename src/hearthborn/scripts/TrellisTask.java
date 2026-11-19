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

import haven.*;
import hearthborn.L10n;
import hearthborn.inventory.BagTransfers;
import hearthborn.navigation.Walking;
import hearthborn.util.GameObjects;
import hearthborn.ui.AutoDrink;
import hearthborn.ui.DrinkSupply;
import java.awt.Color;
import java.util.*;

/**
 * One confirmed plant at a time in a freshly selected rectangle; never target bare trellises.
 *
 * @author Jagy2014
 */
class TrellisTask extends ScriptRunner.Task {
    final boolean destroy;
    Area area;Runnable selectionCancel;MCache terrain;
    final Map<Coord,Long> grids=new HashMap<>();
    final List<Target> targets=new ArrayList<>();
    boolean prepared,acted,complete;int done;
    static final class Target {
        final Gob gob;final String resource;final Coord2d at;
        Target(Gob gob,String resource){this.gob=gob;this.resource=resource;this.at=gob.rc;}
    }
    TrellisTask(GameUI gui,boolean destroy){
        super(gui);this.destroy=destroy;
        cleanup=()->{
            if(selectionCancel!=null){selectionCancel.run();selectionCancel=null;}
            if(gui.map!=null){
                Walking.cancel(gui.map);
                if(acted&&gui.map.player()!=null)gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);
            }
            if(flower!=null&&gui.ui.root!=null){FlowerMenu menu=gui.ui.root.getchild(FlowerMenu.class);if(menu!=null)menu.choose(null);}
            flower=null;
        };
    }
    double now(){return Utils.rtime();}
    boolean working(){return prepared;}
    void notice(String text){gui.showNotice(L10n.tr(text),Color.WHITE);}
    @Override void error(String text){gui.showErrorNotice(L10n.tr(text));}
    @Override void cancelled(){notice("Trellis operation cancelled.");}
    List<Gob> objects(){
        OCache cache=gui.ui.sess.glob.oc;List<Gob> result=new ArrayList<>();
        synchronized(cache){for(Gob gob:cache)result.add(gob);}return result;
    }
    Set<Gob> liveObjects(){Set<Gob> result=Collections.newSetFromMap(new IdentityHashMap<>());result.addAll(objects());return result;}
    void start(){
        if(BagTransfers.running(gui))throw new IllegalStateException("Finish the inventory transfer first.");
        selectionCancel=gui.map.selectLocalArea(selected->{area=selected;selectionCancel=null;},()->{
            selectionCancel=null;if(ScriptRunner.current(gui)==this)ScriptRunner.cancel(gui);
        });
        notice(destroy?"Select the trellis area: all plants inside will be destroyed. Escape cancels.":"Select the trellis area to harvest ripe plants. Escape cancels.");
        add(()->{},()->area!=null,120);
        add(this::prepare,()->true,15);
        // End a previously active native action cursor before opening a Harvest menu.
        add(()->{acted=true;gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);});
        add(this::next);
    }
    void prepare(){
        if(!area.positive()||(long)area.sz().x*area.sz().y>1024)throw new IllegalStateException("Select a trellis area of at most 1024 tiles.");
        terrain=gui.ui.sess.glob.map;grids.clear();targets.clear();
        for(int y=area.ul.y;y<area.br.y;y++)for(int x=area.ul.x;x<area.br.x;x++){
            Coord tile=Coord.of(x,y);MCache.Grid grid=terrain.getgridt(tile);if(grid==null)throw new Loading();
            grids.put(grid.gc,grid.id);
        }
        for(Gob gob:objects())if(!gob.removed&&gob.rc!=null&&gob.getattr(Following.class)==null&&area.contains(gob.rc.floor(MCache.tilesz))){
            String resource=GameObjects.resourceName(gob);if(resource==null)throw new Loading();
            if(ScriptRunner.trellis(resource)&&(destroy||ScriptRunner.mature(gob)))targets.add(new Target(gob,resource));
        }
        if(targets.isEmpty())throw new IllegalStateException(destroy?"No trellis plants in the selected area.":"No ripe trellis plants in the selected area.");
        prepared=true;
    }
    @Override void check(){
        if(!prepared)return;
        if(hand()!=null)throw new IllegalStateException("Empty the cursor before starting the script.");
        if(gui.ui.sess.glob.map!=terrain)throw new IllegalStateException("The map changed. Select the trellis area again.");
        for(Map.Entry<Coord,Long> entry:grids.entrySet()){
            MCache.Grid grid=terrain.getgridt(entry.getKey().mul(MCache.cmaps));
            if(grid==null)throw new Loading();
            if(grid.id!=entry.getValue())throw new IllegalStateException("The map changed. Select the trellis area again.");
        }
    }
    boolean gone(Target target,Set<Gob> live){
        if(target.gob.removed||!live.contains(target.gob))return true;
        String resource=GameObjects.resourceName(target.gob);if(resource==null)throw new Loading();
        return !ScriptRunner.trellis(resource);
    }
    boolean eligible(Target target,Set<Gob> live){
        if(gone(target,live))return false;
        if(!target.resource.equals(GameObjects.resourceName(target.gob))||!target.at.equals(target.gob.rc))
            throw new IllegalStateException("A trellis plant changed unexpectedly. Select the area again.");
        return destroy||ScriptRunner.mature(target.gob);
    }
    void next(){
        // Several trellises can share a tile. Keep every gob, then visit the nearest remaining plant.
        Target best=null;double distance=Double.POSITIVE_INFINITY;Set<Gob> live=liveObjects();
        for(Iterator<Target> it=targets.iterator();it.hasNext();){Target target=it.next();
            if(!eligible(target,live)){it.remove();done++;continue;}
            double d=target.at.dist(gui.map.player().rc);
            if(d<distance){best=target;distance=d;}
        }
        if(best==null){complete=true;gui.showNotice(L10n.format(destroy?"Trellis plants destroyed: %d.":"Trellis plants harvested: %d.",done),Color.WHITE);return;}
        if(drinkIfNeeded())return;
        final Target target=best;boolean[] sent={false};double[] settled={0},deadline={0};BitSet[] previous={null};
        add(()->{
            if(!eligible(target,liveObjects()))return;
            if(!destroy&&(gui.maininv==null||!ScriptRunner.fits(gui.maininv,Inventory.sqsz)))
                throw new IllegalStateException("Not enough space in the inventory. Collection stopped.");
            acted=true;sent[0]=true;deadline[0]=now()+60;
            if(destroy){gui.act("destroy");click(target.gob,1);}
            else {flower="Harvest";click(target.gob,3);}
        },()->{
            if(!sent[0])return true;
            Set<Gob> loaded=liveObjects();
            boolean finished=gone(target,loaded)||!destroy&&!ScriptRunner.mature(target.gob);
            if(!finished||gui.prog!=null){settled[0]=0;
                if(now()>deadline[0])throw new IllegalStateException("The trellis plant did not change. Check access, tools and inventory space.");
                return false;
            }
            // A scythe/server batch can update neighbouring plants after the clicked plant.
            BitSet changed=new BitSet(targets.size());
            for(int i=0;i<targets.size();i++){Target other=targets.get(i);
                if(gone(other,loaded)||!destroy&&!ScriptRunner.mature(other.gob))changed.set(i);
            }
            if(settled[0]==0||!changed.equals(previous[0])){previous[0]=changed;settled[0]=now()+.5;}
            return now()>=settled[0];
        },65);
        add(()->{
            flower=null;targets.remove(target);done++;
            if(destroy&&sent[0])gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);
        });
        // Planning may wait for resources. Retrying it must not repeat acknowledgement or cancellation.
        add(this::next);
    }
    double stamina(){IMeter meter=gui.meter("stam");return meter==null||meter.meters().isEmpty()?-1:meter.meters().get(0).a;}
    boolean drinkIfNeeded(){
        int threshold=AutoDrink.below();double before=stamina();
        if(threshold==0||before<0||before>threshold/100.0)return false;
        DrinkSupply supply=DrinkSupply.collect(gui);if(supply.loading)throw new Loading();
        if(supply.water+supply.tea<=0)throw new IllegalStateException("Not enough drinking water. Refill before processing trellises.");
        add(()->AutoDrink.drink(gui),()->gui.prog!=null||stamina()>before+.001,15);
        add(()->{},()->gui.prog==null&&stamina()>before+.001,30);add(this::next);return true;
    }
}
