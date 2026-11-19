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
import hearthborn.util.GameObjects;
import hearthborn.ui.DrinkSupply;
import java.util.*;
import java.util.function.*;

/**
 * Mining and crafting scripts using the shared, cancellable UI-thread runner.
 *
 * @author Jagy2014
 */
final class MiningCraftScripts {
    static boolean supports(String id){return Arrays.asList("cellar","roastspit","chipstone").contains(id);}
    static String name(Gob gob){String value=GameObjects.resourceName(gob);return value==null?"":value;}
    static String name(GItem item){return item.resource().name;}
    static boolean boulder(Gob gob){
        String path=name(gob);if(!path.startsWith("gfx/terobjs/bumlings/"))return false;
        return !Arrays.asList("amber","amethyst","diamond","dustjewel","emerald","jade","moonstone","onyx","opal","ruby","sapphire","topaz","turquoise").contains(path.substring(path.lastIndexOf('/')+1));
    }
    static final String[] FOOD={"rabbit-clean","fish-","chicken-cleaned","bat-clean","mole-clean","rockdove-cleaned","mallard-cleaned","squirrel-clean","kebabraw"};
    static boolean spitFood(GItem item){String path=name(item);for(String food:FOOD)if(path.startsWith("gfx/invobjs/"+food))return true;return false;}
    static Gob.Overlay spit(Gob gob){for(Gob.Overlay overlay:gob.ols)if("gfx/terobjs/roastspit".equals(GameObjects.overlayName(overlay)))return overlay;return null;}
    static String spitState(Gob.Overlay overlay){
        Collection<?> slots=GameObjects.field(overlay,"slots",Collection.class);if(slots==null||slots.isEmpty())throw new Loading();
        Object[] roots=GameObjects.field(slots.iterator().next(),"children",Object[].class);
        if(roots==null||roots.length==0||roots[0]==null)throw new Loading();
        Object[] nested=GameObjects.field(roots[0],"children",Object[].class);
        if(nested==null||nested.length<=2)return "";
        return nested[2]==null?"":String.valueOf(nested[2]);
    }
    static class Task extends ScriptRunner.Task {
        final String script;Gob fireplace;String error;boolean travelling;
        final Set<Long> skipped=new HashSet<>();int misses;
        Task(GameUI gui,String script){super(gui);this.script=script;cleanup=()->{
            if(gui.map!=null){Walking.cancel(gui.map);if(!travelling)gui.map.wdgmsg("click",Coord.z,gui.map.player()==null?Coord.z:gui.map.player().rc.floor(OCache.posres),3,0);}
            returnHeld();
        };}
        @Override void take(GItem item){returnIssued=false;super.take(item);}
        void start(){
            switch(script){
            case "chipstone":add(()->chipNext(false));break;
            case "cellar":if(nearest(g->name(g).equals("gfx/terobjs/arch/cellardoor"),Double.MAX_VALUE)==null)throw new IllegalStateException("No cellar door nearby.");add(this::cellar);break;
            case "roastspit":fireplace=nearest(g->name(g).equals("gfx/terobjs/pow")&&spit(g)!=null,22);if(fireplace==null)throw new IllegalStateException("No fireplace with a roasting spit nearby.");add(this::roast);break;
            }
        }
        @Override void check(){
            if(error!=null)throw new IllegalStateException(error);
            if(!script.equals("cellar"))return;
            double hp=meter("hp",1);if(hp<0)hp=meter("hp",0);
            if(hp>=0&&hp<.02){travelling=true;gui.act("travel","hearth");throw new IllegalStateException("Cellar digging: low health, travelling home.");}
            double energy=meter("nrj",0);if(energy>=0&&energy<.25)throw new IllegalStateException("Cellar digging: low energy.");
        }
        double meter(String part,int index){IMeter bar=gui.meter(part);return bar==null||bar.meters().size()<=index?-1:bar.meters().get(index).a;}
        Gob nearest(Predicate<Gob> predicate,double radius){
            Gob best=null;double distance=radius;synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc){
                if(gob.removed||gob.rc==null)continue;double d=gob.rc.dist(gui.map.player().rc);if(d>distance)continue;
                if(predicate.test(gob)){best=gob;distance=d;}
            }}return best;
        }
        GItem item(Predicate<GItem> predicate){for(GItem item:ShortcutRunner.owned(gui))if(predicate.test(item))return item;return null;}
        void delay(double seconds){double[] end={0};add(()->end[0]=Utils.rtime()+seconds,()->Utils.rtime()>=end[0],seconds+2);}
        void walk(Coord2d destination,double range,double timeout){add(()->{
            if(gui.map.player().rc.dist(destination)>range)gui.map.wdgmsg("click",Coord.z,destination.floor(OCache.posres),1,0);
        },()->gui.map.player().rc.dist(destination)<=range,timeout);}
        void near(Gob target,double range){Coord2d from=gui.map.player().rc,delta=target.rc.sub(from);Coord2d dest=delta.abs()>.1?target.rc.sub(delta.mul(8/delta.abs())):target.rc;walk(dest,range-8,20);}
        void choose(Gob target,Gob.Overlay overlay,String... options){add(()->{
            if(target.removed)throw new IllegalStateException("Target disappeared.");
            flower=String.join("|",options);Coord at=target.rc.floor(OCache.posres);
            gui.map.wdgmsg("click",Coord.z,at,3,0,0,(int)target.id,at,overlay==null?0:overlay.id,-1);
        },()->flower==null,10);}
        void work(BooleanSupplier terminal,boolean wrap){
            double[] began={0},max={0};boolean[] seen={false};
            add(()->began[0]=Utils.rtime(),()->{
                if(terminal.getAsBoolean())return true;
                if(gui.prog!=null){seen[0]=true;double value=gui.prog.prog;if(wrap&&value<max[0])return true;max[0]=Math.max(max[0],value);return false;}
                return seen[0]||Utils.rtime()-began[0]>4;
            },120);
        }
        void dropHand(){add(()->{if(hand()!=null){held=hand();gui.map.wdgmsg("drop",Coord.z,gui.map.player().rc.floor(OCache.posres),0);}},()->hand()==null,3);}
        void drinkThen(Runnable continuation){
            double stamina=meter("stam",0);if(stamina<0||stamina>=.4){continuation.run();return;}
            GItem supply=item(i->{DrinkSupply d=new DrinkSupply();d.add(i);return d.tea>0||d.water>0;});
            if(supply==null){continuation.run();return;}
            add(()->{flower="Drink";supply.wdgmsg("iact",Coord.z,0);},()->flower==null,5);work(()->false,false);add(continuation);
        }
        Gob carried(){
            Gob player=gui.map.player();Following follow=player.getattr(Following.class);if(follow!=null){Gob target=follow.tgt();if(target!=null&&boulder(target))return target;}
            return nearest(g->{Following f=g.getattr(Following.class);return boulder(g)&&f!=null&&f.tgt==player.id;},Double.MAX_VALUE);
        }
        void dropCarried(Runnable continuation){
            if(carried()==null){continuation.run();return;}
            Coord2d from=gui.map.player().rc;Coord2d[] spots={from,from.add(11,0),from.add(0,11),from.add(-11,0),from.add(0,-11)};
            for(Coord2d at:spots){double[] until={0};add(()->{until[0]=Utils.rtime()+1.5;if(carried()!=null)gui.map.wdgmsg("click",Coord.z,at.floor(OCache.posres),3,0);},()->carried()==null||Utils.rtime()>=until[0],3);}
            add(()->{if(carried()!=null)throw new IllegalStateException("Could not drop the carried boulder.");continuation.run();});
        }
        void cellar(){check();drinkThen(()->dropCarried(()->chipNext(true)));}
        void chipNext(boolean digging){
            Gob rock=nearest(g->boulder(g)&&g.getattr(Following.class)==null&&!skipped.contains(g.id),digging?Double.MAX_VALUE:55);
            if(rock==null){if(digging)enterCellar();return;}
            near(rock,18);dropHand();choose(rock,null,"Chip stone","Chip");
            boolean[] began={false};
            work(()->{if(gui.prog!=null)began[0]=true;return rock.removed;},false);dropHand();
            add(()->{if(rock.removed||began[0])misses=0;else if(++misses>=3){skipped.add(rock.id);misses=0;}if(digging)cellar();else chipNext(false);});
        }
        void enterCellar(){
            Gob door=nearest(g->name(g).equals("gfx/terobjs/arch/cellardoor"),Double.MAX_VALUE);
            if(door==null)throw new IllegalStateException("No cellar door nearby.");
            // The server chooses the door's entrance; don't route to its mesh center.
            choose(door,null,"Enter","Go down","Climb down","Go up","Climb up","Exit","Leave");
            work(()->false,false);add(this::cellar);
        }
        void roast(){
            if(fireplace.removed)throw new IllegalStateException("The roasting spit disappeared.");Gob.Overlay overlay=spit(fireplace);if(overlay==null)throw new IllegalStateException("The roasting spit disappeared.");
            String state=spitState(overlay);
            if(state.contains("roast")){delay(.4);choose(fireplace,overlay,"Carve");work(()->false,false);add(this::roast);return;}
            if(state.contains("raw")){delay(.4);choose(fireplace,overlay,"Roast");work(()->spitState(overlay).contains("roast"),true);add(this::roast);return;}
            GItem food=item(MiningCraftScripts::spitFood);if(food==null)return;
            take(food);delay(.2);add(()->{Coord at=fireplace.rc.floor(OCache.posres);gui.map.wdgmsg("itemact",Coord.z,at,0,0,(int)fireplace.id,at,overlay.id,-1);},()->hand()==null,5);
            add(this::roast);
        }

    }
    private MiningCraftScripts(){}
}
