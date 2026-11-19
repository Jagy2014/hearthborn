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
import hearthborn.navigation.Walking;
import hearthborn.util.GameObjects;
import hearthborn.world.ObjectGeometry;
import hearthborn.ui.AutoDrink;
import hearthborn.ui.DrinkSupply;
import java.awt.Color;
import java.util.*;

/**
 * Finish one corpse using fresh menus and server receipts, then visit the next.
 *
 * @author Jagy2014
 */
final class ButcherScripts {
    static final String[] STAGES={"Skin","Clean","Butcher","Collect Bones|Collect bones"};
    static final String BONES=STAGES[3];
    static final double REACH=20*MCache.tilesz.x;
    private static final String MENU="$butcher-menu$";
    static boolean supports(String id){return "butcher".equals(id);}
    static void run(GameUI gui){
        if(gui==null)return;
        ScriptRunner.Task previous=ScriptRunner.current(gui);
        if(previous instanceof Task){ScriptRunner.cancel(gui);return;}
        Task task=new Task(gui);
        try{
            if(gui.map==null||gui.map.player()==null)throw new IllegalStateException("The map is not ready yet.");
            if(gui.fv!=null&&gui.fv.current!=null)throw new IllegalStateException("Leave combat before starting animal butchering.");
            if(task.hand()!=null)throw new IllegalStateException("Empty the cursor before starting the script.");
            if(previous!=null)ScriptRunner.cancel(gui);
            ScriptRunner.start(task);task.start();
        }catch(IllegalStateException failure){task.error(failure.getMessage());}
    }
    static boolean carcass(Gob gob){
        if(gob==null||gob.removed||gob.rc==null||gob.getattr(Following.class)!=null)return false;
        String path=GameObjects.resourceName(gob);
        if(path==null)throw new Loading();
        if(!path.startsWith("gfx/kritter/"))return false;
        Drawable body=gob.getattr(Drawable.class);
        if(!(body instanceof Composite))return false;
        Composite model=(Composite)body;
        if(model.poseResources().isEmpty())throw new Loading();
        return model.poseContains("dead")||model.poseContains("knock");
    }
    static FlowerMenu.Petal petal(FlowerMenu menu,String action){
        if(menu!=null)for(String name:action.split("\\|"))for(FlowerMenu.Petal petal:menu.opts)if(name.equals(petal.name))return petal;
        return null;
    }
    static boolean offered(FlowerMenu menu,String action){return petal(menu,action)!=null;}
    static final class Target {
        final Gob gob;final Coord2d at;final String resource;
        int stage,actions,approachAttempt;boolean worked;
        final List<Coord2d> tried=new ArrayList<>();
        Target(Gob gob){this.gob=gob;at=gob.rc;resource=GameObjects.resourceName(gob);}
    }
    static class Task extends ScriptRunner.Task {
        final MapView map;
        final List<Target> targets=new ArrayList<>();
        Coord2d origin;MCache terrain;
        Target current;FlowerMenu ownedMenu;
        boolean acted,complete,loadingNotice;
        double scanBegan;
        int processed,skipped;
        double quiet=1.5,startWait=12,menuWait=6;
        Task(GameUI gui){
            super(gui);map=gui.map;
            cleanup=()->{
                flower=null;Walking.cancel(map);
                if(ownedMenu!=null&&ownedMenu.parent!=null)ownedMenu.choose(null);
                ownedMenu=null;
                if(acted&&!complete&&map.player()!=null)map.wdgmsg("click",Coord.z,map.player().rc.floor(OCache.posres),3,0);
            };
        }
        double now(){return Utils.rtime();}
        void tell(String english){notice(L10n.tr(english));}
        void notice(String text){gui.showNotice(text,Color.WHITE);if(gui.syslog!=null)gui.syslog.append(text,Color.WHITE);}
        @Override void error(String text){gui.showErrorNotice(L10n.tr(text));}
        @Override void cancelled(){tell("Animal butchering cancelled.");}
        List<Gob> objects(){
            OCache cache=gui.ui.sess.glob.oc;List<Gob> result=new ArrayList<>();
            synchronized(cache){for(Gob gob:cache)result.add(gob);}return result;
        }
        @Override void check(){
            if(gui.map!=map||terrain!=null&&gui.ui.sess.glob.map!=terrain)throw new IllegalStateException("The map changed. Start butchering again.");
            if(hand()!=null)throw new IllegalStateException("Put the held item away and start butchering again.");
        }
        void start(){
            tell("Animal butchering started. Looking for carcasses within 20 tiles.");
            add(this::prepare,()->true,20);
        }
        void prepare(){
            if(gui.prog!=null)throw new IllegalStateException("Finish the current action before starting butchering.");
            if(menu()!=null)throw new IllegalStateException("Close the current menu before starting butchering.");
            if(map.hasAreaSelection()||map.placementResource()!=null)throw new IllegalStateException("Finish the current placement or area selection first.");
            if(origin==null){origin=map.player().rc;terrain=gui.ui.sess.glob.map;scanBegan=now();}
            targets.clear();int pending=0;
            for(Gob gob:objects())if(gob!=map.player()&&gob.rc!=null&&gob.rc.dist(origin)<=REACH){
                try{if(carcass(gob))targets.add(new Target(gob));}
                catch(Loading waiting){pending++;}
            }
            if(targets.isEmpty()&&pending>0){
                if(now()-scanBegan>=3)throw new IllegalStateException("Carcass data is still loading. Try animal butchering again shortly.");
                if(!loadingNotice){loadingNotice=true;tell("Waiting for nearby carcass data to load.");}
                throw new Loading();
            }
            if(targets.isEmpty())throw new IllegalStateException("No animal carcasses within 20 tiles.");
            notice(L10n.format("Carcasses found: %d. Skin, clean, meat, then bones.",targets.size()));add(this::next);
        }
        boolean live(Target target){
            Gob gob=target.gob;
            return !gob.removed&&objects().contains(gob)&&gob.rc!=null&&gob.rc.dist(target.at)<1&&target.resource.equals(GameObjects.resourceName(gob))&&carcass(gob);
        }
        void next(){
            current=null;double distance=Double.POSITIVE_INFINITY;
            for(Iterator<Target> iterator=targets.iterator();iterator.hasNext();){Target target=iterator.next();
                if(!live(target)){iterator.remove();skipped++;continue;}
                double d=map.player().rc.dist(target.at);if(d<distance){current=target;distance=d;}
            }
            if(current==null){
                complete=true;notice(L10n.format("Animal butchering completed. Carcasses processed: %d. Skipped: %d.",processed,skipped));
                if(processed>0)gui.ui.sfx(UI.InfoMessage.sfx);
                return;
            }
            Target target=current;targets.remove(target);approach(target,0);
        }
        /** Route to clear ground beside the body, never to its occupied center. */
        List<Coord2d> approaches(Target target){
            List<java.awt.geom.Area> obstacles=ObjectGeometry.obstacles(objects(),map.player());
            List<Coord2d> result=new ArrayList<>();
            for(int ring=1;ring<=6;ring++)for(int direction=0;direction<16;direction++){
                double angle=direction*Math.PI/8;Coord2d point=target.at.add(Math.cos(angle)*ring*MCache.tilesz.x,Math.sin(angle)*ring*MCache.tilesz.x);
                boolean free=true;for(java.awt.geom.Area obstacle:obstacles)if(obstacle.contains(point.x,point.y)){free=false;break;}
                if(free)result.add(point);
            }
            Coord2d player=map.player().rc;
            result.sort(Comparator.comparingDouble(p->player.dist(p)+p.dist(target.at)*2));return result;
        }
        boolean startWalk(Coord2d point){
            Object[] click={Coord.z,point.floor(OCache.posres),1,Walking.enabled()?0:UI.MOD_CTRL};
            return Walking.startScript(map,point,click);
        }
        void approach(Target target,int attempt){
            if(!live(target)){finishTarget(target);return;}
            target.approachAttempt=attempt;
            if(attempt==0&&map.player().rc.dist(target.at)<=MCache.tilesz.x*2){inspect(target);return;}
            List<Coord2d> spots=approaches(target);
            spots.removeIf(point->target.tried.stream().anyMatch(previous->previous.dist(point)<MCache.tilesz.x));
            if(attempt>=3||spots.isEmpty()){skip(target);return;}
            Coord2d point=spots.get(0);target.tried.add(point);boolean[] routed={false};
            add(()->{acted=true;routed[0]=startWalk(point);},()->{
                if(!routed[0]||!live(target))return true;
                Walking.tickScript(map);return !Walking.scriptRunning(map);
            },120);
            add(()->{
                if(!live(target)){finishTarget(target);return;}
                if(routed[0]&&map.player().rc.dist(point)<=3){inspect(target);return;}
                if(!routed[0]&&map.player().rc.dist(target.at)<=MCache.tilesz.x*6){inspect(target);return;}
                approach(target,attempt+1);
            });
        }
        void retryApproach(Target target){
            // Cancel a queued native approach before issuing a different route.
            map.wdgmsg("click",Coord.z,map.player().rc.floor(OCache.posres),3,0);
            approach(target,target.approachAttempt+1);
        }
        void skip(Target target){skipped++;notice(L10n.format("Cannot reach this carcass. Skipping: %s.",GameObjects.name(target.resource)));add(this::next);}
        void finishTarget(Target target){if(target.worked)processed++;else skipped++;add(this::next);}
        FlowerMenu menu(){return gui.ui.root.findchild(FlowerMenu.class);}
        void inspect(Target target){
            if(!live(target)){finishTarget(target);return;}
            if(!busy()&&drink(target))return;
            if(target.actions>=64)throw new IllegalStateException("Butchering did not finish after repeated actions. Check the carcass and restart.");
            // Do not reuse a closing reply or permit global automatic flower choices.
            add(()->{},()->!busy()&&menu()==null,15);
            boolean[] received={false},retry={false};int[] selected={-1};double[] sent={0};long[] before={0};
            add(()->{
                if(!live(target))return;
                acted=true;flower=MENU;sent[0]=now();click(target.gob,3);
            },()->{
                if(!live(target)){flower=null;return true;}
                FlowerMenu menu=menu();
                if(menu==null){if(now()-sent[0]>menuWait){flower=null;retry[0]=true;return true;}return false;}
                ownedMenu=menu;received[0]=true;
                for(int stage=target.stage;stage<STAGES.length;stage++){
                    FlowerMenu.Petal petal=petal(menu,STAGES[stage]);if(petal==null)continue;
                    if(!ScriptRunner.fits(gui.maininv,Inventory.sqsz))throw new IllegalStateException("Not enough space in the inventory. Butchering stopped.");
                    selected[0]=stage;target.stage=stage;target.actions++;before[0]=inventoryState();sent[0]=now();
                    menu.choose(petal);ownedMenu=null;flower=null;return true;
                }
                menu.choose(null);ownedMenu=null;flower=null;return true;
            },15);
            add(()->{
                flower=null;
                if(retry[0]){retryApproach(target);return;}
                if(!received[0]||selected[0]<0){finishTarget(target);return;}
                awaitWork(target,selected[0],before[0],sent[0]);
            });
        }
        boolean busy(){
            if(gui.prog!=null)return true;
            Drawable body=map.player().getattr(Drawable.class);
            return body instanceof Composite&&(((Composite)body).poseContains("butcher")||((Composite)body).hasPose("skin")||((Composite)body).hasPose("clean"));
        }
        double stamina(){IMeter meter=gui.meter("stam");return meter==null||meter.meters().isEmpty()?-1:meter.meters().get(0).a;}
        boolean drink(Target target){
            int threshold=AutoDrink.below();double before=stamina();
            if(threshold==0||before<0||before>threshold/100.0)return false;
            DrinkSupply supply=DrinkSupply.collect(gui);if(supply.loading)throw new Loading();
            if(supply.water+supply.tea<=0)throw new IllegalStateException("Not enough drinking water. Refill and restart butchering.");
            add(()->AutoDrink.drink(gui),()->gui.prog!=null||stamina()>before+.001,15);
            add(()->{},()->gui.prog==null&&stamina()>before+.001,30);add(()->inspect(target));return true;
        }
        private long inventoryState(Widget owner,Set<GItem> visited){
            if(owner==null)return 0;long value=1;
            for(Widget child:owner.children())if(child instanceof GItem){GItem item=(GItem)child;if(visited.add(item))
                value=31*value+System.identityHashCode(item)+item.infoseq+item.num+inventoryState(item.contents,visited);
            }else if(!(child instanceof WItem))value=31*value+inventoryState(child,visited);
            return value;
        }
        long inventoryState(){Set<GItem> visited=Collections.newSetFromMap(new IdentityHashMap<>());return inventoryState(gui.maininv,visited)+inventoryState(gui.equipment(),visited);}
        void awaitWork(Target target,int stage,long before,double began){
            double[] changed={began};long[] previous={before};boolean[] seen={false},retry={false};
            add(()->{},()->{
                long state=inventoryState();
                if(state!=previous[0]){seen[0]=true;changed[0]=now();previous[0]=state;}
                if(busy()){seen[0]=true;changed[0]=now();return false;}
                if(!live(target))seen[0]=true;
                if(!seen[0]){
                    if(now()-began>startWait){retry[0]=true;return true;}
                    return false;
                }
                return now()-changed[0]>=quiet;
            },300);
            add(()->{if(retry[0]){retryApproach(target);return;}target.worked=true;if(stage==3)finishTarget(target);else inspect(target);});
        }
    }
    private ButcherScripts(){}
}
