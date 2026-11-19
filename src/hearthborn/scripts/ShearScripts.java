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
import java.awt.Color;
import java.util.*;
import java.util.function.*;

/**
 * Shears sheep and goats of one pen. The pen is the saved area containing the player, so animals behind a fence are never visited.
 *
 * @author Jagy2014
 */
final class ShearScripts {
    static final String SHEAR="Shear wool",SHOO="Shoo";
    private static final String MENU="$shear-menu$";
    static final int ATTEMPTS=4,SHOOS=3;
    static boolean supports(String id){return "shear".equals(id);}
    static void run(GameUI gui){
        if(gui==null)return;
        ScriptRunner.Task previous=ScriptRunner.current(gui);
        if(previous instanceof Task){ScriptRunner.cancel(gui);return;}
        Task task=new Task(gui);
        try{
            if(gui.map==null||gui.map.player()==null)throw new IllegalStateException("The map is not ready yet.");
            if(gui.fv!=null&&gui.fv.current!=null)throw new IllegalStateException("Leave combat before shearing.");
            if(task.hand()!=null)throw new IllegalStateException("Empty the cursor before starting the script.");
            if(previous!=null)ScriptRunner.cancel(gui);
            ScriptRunner.start(task);task.start();
        }catch(IllegalStateException failure){task.error(failure.getMessage());}
    }
    /** Domestic sheep and goats only; wild ones are never in a pen. */
    static boolean domestic(Gob gob){
        String name=GameObjects.resourceName(gob);
        if(name==null)throw new Loading();
        if(!name.startsWith("gfx/kritter/sheep/")&&!name.startsWith("gfx/kritter/goat/"))return false;
        String leaf=name.substring(name.lastIndexOf('/')+1);
        return !leaf.equals("mouflon")&&!leaf.equals("wildgoat");
    }
    /** An animal carries a "-fleece" model part exactly while it has wool. Unloaded models throw {@link Loading}. */
    static boolean fleece(Gob gob){
        Drawable drawable=gob.getattr(Drawable.class);
        if(!(drawable instanceof Composite))throw new Loading();
        Composite body=(Composite)drawable;
        List<Composited.MD> models=body.nmod!=null?body.nmod:body.comp==null?null:body.comp.cmod;
        if(models==null||models.isEmpty())throw new Loading();
        for(Composited.MD model:models)if(model.mod.get().basename().endsWith("-fleece"))return true;
        return false;
    }
    static boolean woolly(Gob gob){try{return fleece(gob);}catch(Loading unknown){return true;}}
    static boolean candidate(Gob gob,Area pen){
        return MilkScripts.placed(gob)&&pen.contains(gob.rc.floor(MCache.tilesz))&&domestic(gob)&&ScriptRunner.standing(gob);
    }
    /** Distance from a point to the segment a-b. */
    static double lineDistance(Coord2d p,Coord2d a,Coord2d b){
        double dx=b.x-a.x,dy=b.y-a.y,length=dx*dx+dy*dy;
        if(length==0)return p.dist(a);
        double t=Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/length));
        return p.dist(new Coord2d(a.x+t*dx,a.y+t*dy));
    }
    static final class Target {
        final long id;Gob gob;int tries,shoos;final Set<Long> shooed=new HashSet<>();
        Target(Gob gob){this.id=gob.id;this.gob=gob;}
        Gob resolve(List<Gob> objects){
            if(gob!=null&&!gob.removed)return gob;
            for(Gob current:objects)if(!current.removed&&current.id==id){gob=current;return current;}
            return null;
        }
    }
    static class Task extends ScriptRunner.Task {
        final MapView map;
        final List<Target> targets=new ArrayList<>(),deferred=new ArrayList<>();
        volatile Area pen;Runnable selectionCancel;
        Target current;FlowerMenu ownedMenu;
        boolean complete,acted,equipped;int sheared,declined,unreached,pass=1;
        String failure;double scanBegan;
        Task(GameUI gui){
            super(gui);map=gui.map;
            cleanup=()->{
                if(selectionCancel!=null){selectionCancel.run();selectionCancel=null;}
                flower=null;Walking.cancel(map);
                if(ownedMenu!=null&&ownedMenu.parent!=null)ownedMenu.choose(null);
                ownedMenu=null;
                if(held!=null)returnHeld();
                if(acted&&!complete&&map.player()!=null)map.wdgmsg("click",Coord.z,map.player().rc.floor(OCache.posres),1,0);
            };
        }
        double now(){return Utils.rtime();}
        /** Manual input stops the run, except while the pen is still being drawn on the map. */
        boolean working(){return selectionCancel==null;}
        void tell(String english){notice(L10n.tr(english));}
        void notice(String text){gui.showNotice(text,Color.WHITE);if(gui.syslog!=null)gui.syslog.append(text,Color.WHITE);}
        @Override void error(String text){gui.showErrorNotice(L10n.tr(text));}
        @Override void cancelled(){tell("Shearing cancelled.");}
        Gob player(){return map.player();}
        List<Gob> objects(){
            OCache cache=gui.ui.sess.glob.oc;List<Gob> result=new ArrayList<>();
            synchronized(cache){for(Gob gob:cache)result.add(gob);}return result;
        }
        FlowerMenu menu(){return gui.ui.root.findchild(FlowerMenu.class);}
        /** Server messages that make the whole run pointless. */
        void message(String text){
            String lower=text.toLowerCase(Locale.ROOT);
            if(lower.contains("sharp tool")||lower.contains("острый инструмент"))failure="Hold the shears in your hand to shear wool.";
        }
        @Override void check(){
            if(failure!=null)throw new IllegalStateException(failure);
            if(gui.map!=map)throw new IllegalStateException("The map changed. Start shearing again.");
            if(equipped&&hand()!=null)throw new IllegalStateException("Put the held item away and start shearing again.");
        }
        void waitFor(Runnable action,BooleanSupplier ready,double seconds,String text){
            double[] until={0};
            add(()->{action.run();until[0]=now()+seconds;},()->{
                if(ready.getAsBoolean())return true;
                if(now()>until[0])throw new IllegalStateException(text);return false;
            },seconds+2);
        }
        // ---- start: pen, shears, herd ----
        void preflight(){
            if(gui.prog!=null)throw new IllegalStateException("Finish the current action before starting shearing.");
            if(menu()!=null)throw new IllegalStateException("Close the current menu before starting shearing.");
            if(map.hasAreaSelection()||map.placementResource()!=null)throw new IllegalStateException("Finish the current placement or area selection first.");
            if(player().getattr(Following.class)!=null)throw new IllegalStateException("Dismount before shearing.");
        }
        /** Shear a pen that is already known. */
        void start(Area area){preflight();pen=area;add(this::scan,()->true,10);}
        void start(){
            preflight();
            SavedArea saved=MilkPens.load(gui).current(gui);
            if(saved==null){
                selectionCancel=map.selectLocalArea(area->{pen=area;selectionCancel=null;},()->{
                    selectionCancel=null;if(ScriptRunner.current(gui)==this)ScriptRunner.cancel(gui);
                });
                tell("Stand inside the collection area, then drag its boundaries on the map.");
                waitFor(()->{},()->pen!=null,120,"Collection area selection timed out.");
                add(()->{
                    herd(pen); // Do not persist an empty selection.
                    MilkPens.save(gui,pen,null);
                },()->true,10);
            }else{
                waitFor(()->{},()->{
                    pen=saved.resolve(gui.ui.sess.glob.map);
                    if(pen!=null&&!pen.contains(player().rc.floor(MCache.tilesz)))throw new IllegalStateException("Stand inside the collection area you are selecting.");
                    return pen!=null;
                },10,"The saved collection area is not loaded. Move closer or change its boundaries.");
                tell("Using the saved collection area.");
            }
            add(this::scan,()->true,10);
        }
        /** Animals of the pen with their wool state: {all, with wool}. Throws Loading while models are missing and nothing is known. */
        int[] herd(Area area){
            int all=0,woolly=0,pending=0;
            for(Gob gob:objects())try{
                if(!candidate(gob,area))continue;
                all++;if(fleece(gob))woolly++;
            }catch(Loading unknown){pending++;}
            if(all+pending==0)throw new IllegalStateException("No sheep or goats in the selected area.");
            if(pending>0&&woolly==0)throw new Loading();
            return new int[]{all,woolly};
        }
        boolean shears(GItem item){
            try{String name=item.getres().name;return name.equals("gfx/invobjs/shears")||name.equals("gfx/invobjs/small/shears");}
            catch(Loading unknown){return false;}
        }
        GItem handShears(){
            Equipory gear=gui.equipment();if(gear==null)return null;
            for(WItem view:gear.children(WItem.class))
                if((view.c.equals(Equipory.ecoords[6].add(1,1))||view.c.equals(Equipory.ecoords[7].add(1,1)))&&shears(view.item))return view.item;
            return null;
        }
        int freeHand(){
            Equipory gear=gui.equipment();if(gear==null)return -1;
            boolean left=false,right=false;
            for(WItem view:gear.children(WItem.class)){
                if(view.c.equals(Equipory.ecoords[6].add(1,1)))left=true;
                if(view.c.equals(Equipory.ecoords[7].add(1,1)))right=true;
            }
            return !left?6:!right?7:-1;
        }
        void equip(Runnable then){
            if(handShears()!=null){equipped=true;then.run();return;}
            GItem item=null;
            for(GItem candidate:ShortcutRunner.owned(gui))if(candidate.parent instanceof Inventory&&shears(candidate)){item=candidate;break;}
            if(item==null)throw new IllegalStateException("No shears in your inventory.");
            int slot=freeHand();
            if(slot<0)throw new IllegalStateException("Free one hand to take the shears.");
            GItem shears=item;
            add(()->shears.wdgmsg("take",Coord.z),()->{
                if(gui.vhand==null)return false;
                if(gui.vhand.item!=shears)throw new IllegalStateException("The cursor is holding another item.");
                held=shears;return true;
            },3);
            add(()->gui.equipment().wdgmsg("drop",slot),()->gui.vhand==null,3);
            add(()->{held=null;equipped=true;},()->handShears()!=null,5);
            add(then);
        }
        void scan(){
            if(scanBegan==0)scanBegan=now();
            int[] count=herd(pen);
            targets.clear();
            for(Gob gob:objects())try{if(candidate(gob,pen)&&fleece(gob))targets.add(new Target(gob));}catch(Loading unknown){}
            notice(L10n.format("Sheep and goats in the area: %d, with wool: %d.",count[0],count[1]));
            if(targets.isEmpty()){complete=true;tell("All sheep and goats in this area are already sheared.");return;}
            tell("Shearing started.");
            add(()->equip(this::next),()->true,10);
        }
        // ---- one animal at a time ----
        void next(){
            current=null;double distance=Double.POSITIVE_INFINITY;List<Gob> loaded=objects();
            for(Iterator<Target> it=targets.iterator();it.hasNext();){
                Target target=it.next();Gob gob=target.resolve(loaded);
                if(gob==null||!pen.contains(gob.rc.floor(MCache.tilesz))||!woolly(gob)||!MilkScripts.placed(gob)){it.remove();continue;}
                double d=gob.rc.dist(player().rc);if(d<distance){current=target;distance=d;}
            }
            if(current==null){
                if(!deferred.isEmpty()&&pass<2){
                    pass++;
                    for(Target target:deferred){target.tries=0;target.shoos=0;targets.add(target);}
                    deferred.clear();add(this::next);return;
                }
                complete=true;unreached+=deferred.size();
                notice(L10n.format("Shearing finished. Sheared: %d. Not offered: %d. Not reached: %d.",sheared,declined,unreached));
                if(sheared>0)gui.ui.sfx(UI.InfoMessage.sfx);
                return;
            }
            attempt(current);
        }
        void drop(Target target){targets.remove(target);}
        void attempt(Target target){
            if(target.resolve(objects())==null){drop(target);add(this::next);return;}
            if(target.tries>=ATTEMPTS){
                drop(target);
                if(pass<2)deferred.add(target);else unreached++;
                add(this::next);return;
            }
            target.tries++;
            int[] outcome={0}; // 1: asked to shear, 2: not offered, 3: no menu
            double[] sent={0};
            add(()->{},()->!busy()&&menu()==null,15);
            add(()->{
                Gob gob=target.resolve(objects());if(gob==null)return;
                acted=true;flower=MENU;sent[0]=now();click(gob,3);
            },()->{
                if(target.resolve(objects())==null){flower=null;outcome[0]=3;return true;}
                FlowerMenu menu=menu();
                if(menu==null){if(now()-sent[0]>6){flower=null;outcome[0]=3;return true;}return false;}
                ownedMenu=menu;
                FlowerMenu.Petal petal=ButcherScripts.petal(menu,SHEAR);
                if(petal!=null){outcome[0]=1;menu.choose(petal);}else{outcome[0]=2;menu.choose(null);}
                ownedMenu=null;flower=null;return true;
            },15);
            add(()->{
                flower=null;
                switch(outcome[0]){
                case 1:await(target);break;
                case 2:declined++;drop(target);add(this::next);break; // sheared already, or not shearable
                default:add(()->attempt(target),()->true,3);
                }
            });
        }
        boolean busy(){return gui.prog!=null;}
        /** Wait for the server to walk to the animal and shear it; detect a blocked path. */
        void await(Target target){
            double began=now();double[] idleSince={began},progress={0};Coord2d[] last={player().rc};
            int[] state={0}; // 1: sheared, 2: blocked, 3: retry
            add(()->{},()->{
                Gob gob=target.resolve(objects());
                if(gob==null){state[0]=3;return true;}
                if(gui.prog!=null){progress[0]=now();idleSince[0]=now();return false;}
                if(!woolly(gob)){state[0]=1;return true;}
                boolean moving=player().getattr(Moving.class)!=null||player().rc.dist(last[0])>.1;
                last[0]=player().rc;
                if(moving){idleSince[0]=now();return false;}
                double idle=now()-idleSince[0];
                if(progress[0]>0){if(idle>=3){state[0]=3;return true;}return false;}
                if(idle>=2.5&&player().rc.dist(gob.rc)>MCache.tilesz.x*3){state[0]=2;return true;}
                if(idle>=6){state[0]=3;return true;}
                if(now()-began>45){state[0]=3;return true;}
                return false;
            },60);
            add(()->{
                switch(state[0]){
                case 1:sheared++;drop(target);add(this::next);break;
                case 2:unblock(target);break;
                default:attempt(target);
                }
            });
        }
        // ---- blocked path: shoo the animal in the way ----
        Gob blocker(Target target,Gob goal){
            Gob best=null;double score=Double.POSITIVE_INFINITY;Coord2d from=player().rc;
            for(Gob gob:objects())try{
                if(gob==goal||gob==player()||!MilkScripts.placed(gob)||target.shooed.contains(gob.id))continue;
                String name=GameObjects.resourceName(gob);
                if(name==null||!name.startsWith("gfx/kritter/")||!ScriptRunner.standing(gob))continue;
                double d=gob.rc.dist(from);if(d>MCache.tilesz.x*4)continue;
                double off=lineDistance(gob.rc,from,goal.rc);
                if(off>MCache.tilesz.x*1.6&&d>MCache.tilesz.x*2)continue;
                double s=off*2+d;if(s<score){score=s;best=gob;}
            }catch(Loading unknown){}
            return best;
        }
        void unblock(Target target){
            Gob goal=target.resolve(objects());
            Gob blocker=goal==null||target.shoos>=SHOOS?null:blocker(target,goal);
            if(blocker==null){
                // Nobody to move, or it has nowhere to go: pause once and try again, then give up on this animal.
                double[] until={0};
                add(()->until[0]=now()+1.5,()->now()>=until[0],4);
                add(()->attempt(target));return;
            }
            target.shoos++;target.shooed.add(blocker.id);
            int[] outcome={0};double[] sent={0};
            add(()->{},()->!busy()&&menu()==null,15);
            add(()->{
                if(blocker.removed)return;
                acted=true;flower=MENU;sent[0]=now();click(blocker,3);
            },()->{
                if(blocker.removed){flower=null;outcome[0]=3;return true;}
                FlowerMenu menu=menu();
                if(menu==null){if(now()-sent[0]>6){flower=null;outcome[0]=3;return true;}return false;}
                ownedMenu=menu;
                FlowerMenu.Petal petal=ButcherScripts.petal(menu,SHOO);
                if(petal!=null){outcome[0]=1;menu.choose(petal);}else{outcome[0]=2;menu.choose(null);}
                ownedMenu=null;flower=null;return true;
            },15);
            double[] settle={0};
            add(()->settle[0]=now()+(outcome[0]==1?2.5:.5),()->now()>=settle[0],6);
            add(()->attempt(target));
        }
    }
    private ShearScripts(){}
}
