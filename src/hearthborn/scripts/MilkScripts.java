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
import hearthborn.navigation.LocalPathfinder;
import hearthborn.navigation.Walking;

import haven.*;
import haven.res.ui.croster.CattleId;
import hearthborn.L10n;
import hearthborn.quality.VesselInfo;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.math.*;
import java.util.*;
import java.util.function.*;

/**
 * Confirmed per-animal milking cycles for a selected pen and one fixed cistern.
 *
 * @author Jagy2014
 */
public final class MilkScripts {
    static boolean barrel(Gob gob){String name=GameObjects.resourceName(gob);return name!=null&&(name.equals("gfx/terobjs/barrel")||name.startsWith("gfx/terobjs/barrel-"));}
    static boolean cistern(Gob gob){String name=GameObjects.resourceName(gob);return name!=null&&(name.equals("gfx/terobjs/cistern")||name.startsWith("gfx/terobjs/cistern-"));}
    static String species(Gob gob){
        String name=GameObjects.resourceName(gob);if(name==null||!ScriptRunner.standing(gob))return null;
        String kind=name.startsWith("gfx/kritter/cattle/")?"cow":name.startsWith("gfx/kritter/sheep/")?"sheep":name.startsWith("gfx/kritter/goat/")?"goat":null;
        if(kind==null)return null;
        String adult=kind.equals("cow")?"cow":kind.equals("sheep")?"ewe":"nanny";
        String male=kind.equals("cow")?"bull":kind.equals("sheep")?"ram":"billy";
        String young=kind.equals("cow")?"calf":kind.equals("sheep")?"lamb":"kid";
        String leaf=name.substring(name.lastIndexOf('/')+1);
        if(Arrays.asList(male,young,"aurochs","mouflon","wildgoat").contains(leaf))return null;
        CattleId cattle=gob.getattr(CattleId.class);Object entry=cattle==null?null:cattle.entry();
        if(entry!=null){
            Boolean isMale=GameObjects.field(entry,male,Boolean.class),isYoung=GameObjects.field(entry,young,Boolean.class);
            if(Boolean.TRUE.equals(GameObjects.field(entry,"dead",Boolean.class))||Boolean.TRUE.equals(isMale)||Boolean.TRUE.equals(isYoung))return null;
            if(Boolean.FALSE.equals(isMale)&&Boolean.FALSE.equals(isYoung))return kind;
        }
        if(leaf.equals(adult))return kind;
        Drawable drawable=gob.getattr(Drawable.class);
        if(drawable instanceof Composite){
            Composite body=(Composite)drawable;
            List<Composited.MD> models=body.nmod!=null?body.nmod:body.comp==null?null:body.comp.cmod;
            if(models==null||models.isEmpty())throw new Loading();
            boolean female=false;
            for(Composited.MD model:models){String part=model.mod.get().basename();
                if(part.equals(male)||part.equals(male+"-fleece")||part.equals(young)||part.equals(young+"-fleece"))return null;
                if(part.equals(adult)||part.equals(adult+"-fleece"))female=true;
            }
            if(female)return kind;
        }
        return null;
    }
    static boolean animal(Gob gob){return species(gob)!=null;}
    static boolean nonLactating(Gob gob){
        CattleId cattle=gob.getattr(CattleId.class);
        return cattle!=null&&Boolean.FALSE.equals(GameObjects.field(cattle.entry(),"lactate",Boolean.class));
    }
    static String identity(Gob gob){CattleId cattle=gob.getattr(CattleId.class);return cattle==null?"gob:"+gob.id:"animal:"+cattle.id;}
    static final class Target {
        String key;final String kind;Gob gob;boolean visited;hearthborn.world.MilkMark.Status status;
        Target(Gob gob,String kind){this.key=identity(gob);this.gob=gob;this.kind=kind;}
        Gob resolve(List<Gob> objects){if(gob!=null&&!gob.removed){if(key.startsWith("gob:")){String updated=identity(gob);if(updated.startsWith("animal:"))key=updated;}return gob;}for(Gob current:objects)if(!current.removed&&key.equals(identity(current))){gob=current;return current;}return null;}
    }
    static List<Target> targets(List<Gob> objects,Area area){
        Map<String,Target> selected=new LinkedHashMap<>();Set<String> kinds=new HashSet<>();
        for(Gob gob:objects)if(placed(gob)&&area.contains(gob.rc.floor(MCache.tilesz))){
            String kind=species(gob);if(kind!=null){Target target=new Target(gob,kind);selected.putIfAbsent(target.key,target);kinds.add(kind);}
        }
        if(kinds.size()>1)throw new IllegalStateException("Select one pen containing one animal species.");
        if(selected.isEmpty())throw new IllegalStateException("No adult female cows, sheep or goats in the selected area.");
        return new ArrayList<>(selected.values());
    }
    static boolean placed(Gob gob){return !gob.removed&&gob.rc!=null&&gob.getattr(Following.class)==null;}
    static boolean carriedBy(Gob gob,Gob player){Following follow=gob.getattr(Following.class);return !gob.removed&&follow!=null&&follow.tgt==player.id;}
    static String milkType(String name){
        if(name==null)return null;String key=name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]","");
        switch(key){
        case "milk":case "cowmilk":case "cowsmilk":case "коровьемолоко":return "cow milk";
        case "sheepmilk":case "sheepsmilk":case "овечьемолоко":return "sheep milk";
        case "goatmilk":case "goatsmilk":case "козьемолоко":return "goat milk";
        default:return null;
        }
    }
    static boolean hasContents(Gob gob){
        String name=GameObjects.resourceName(gob);
        if(name==null)throw new Loading();
        if(name.startsWith("gfx/terobjs/barrel-")&&!name.endsWith("-opt"))return true;
        for(Gob.Overlay overlay:new ArrayList<>(gob.ols)){
            Resource res=null;
            if(overlay.sm instanceof OCache.OlSprite)res=((OCache.OlSprite)overlay.sm).res.get();
            else if(overlay.sm instanceof Sprite.Mill.FromRes)res=((Sprite.Mill.FromRes)overlay.sm).res.get();
            else if(overlay.spr!=null)res=overlay.spr.res;
            if(res!=null&&(res.name.startsWith("gfx/terobjs/barrel-")&&!res.name.endsWith("-opt")||res.name.startsWith("gfx/terobjs/subst/")))return true;
        }
        return false;
    }
    static String number(double value,int decimals){
        String text=BigDecimal.valueOf(value).setScale(decimals,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        return L10n.language().equals("ru")?text.replace('.',','):text;
    }
    static String collected(VesselInfo milk){
        String amount=number(milk.liters(),2),quality=number(milk.quality(),1);
        switch(milkType(milk.contents())){
        case "cow milk":return L10n.format("Collected %s liters of cow milk, quality %s.",amount,quality);
        case "sheep milk":return L10n.format("Collected %s liters of sheep milk, quality %s.",amount,quality);
        case "goat milk":return L10n.format("Collected %s liters of goat milk, quality %s.",amount,quality);
        default:throw new IllegalArgumentException(milk.contents());
        }
    }
    static List<Coord2d> dropPositions(Gob player,Gob barrel,List<Gob> objects){
        List<Gob> nearby=new ArrayList<>();
        for(Gob gob:objects)if(gob!=player&&gob!=barrel&&placed(gob)&&gob.rc.dist(player.rc)<55)nearby.add(gob);
        List<java.awt.geom.Area> blocks=hearthborn.world.ObjectGeometry.hitboxes(nearby,player);
        java.awt.geom.Area shape=new java.awt.geom.Area();
        for(java.awt.geom.Area box:hearthborn.world.ObjectGeometry.hitboxes(Collections.singletonList(barrel),null))shape.add(box);
        if(shape.isEmpty())shape=new java.awt.geom.Area(new Rectangle2D.Double(barrel.rc.x-5.5,barrel.rc.y-5.5,11,11));
        List<Coord2d> candidates=new ArrayList<>();
        // Try opposite sides before nearby angles; animals can move into a previously clear spot.
        for(double radius:new double[]{0,11,16.5,22})for(int i:new int[]{0,8,4,12,2,10,6,14,1,9,5,13,3,11,7,15}){
            if(radius==0&&i!=0)continue;
            double angle=i*Math.PI/8;Coord2d at=player.rc.add(Math.cos(angle)*radius,Math.sin(angle)*radius);
            java.awt.geom.Area moved=shape.createTransformedArea(AffineTransform.getTranslateInstance(at.x-barrel.rc.x,at.y-barrel.rc.y));
            boolean free=true;
            for(java.awt.geom.Area block:blocks){java.awt.geom.Area overlap=new java.awt.geom.Area(moved);overlap.intersect(block);if(!overlap.isEmpty()){free=false;break;}}
            if(free)candidates.add(at);
        }
        return candidates;
    }

    static class Task extends ScriptRunner.Task {
        Gob source,livestock,destination;Window opened;Set<Window> before;
        final List<Gob> cisterns=new ArrayList<>();int cisternIndex;
        boolean complete,received;double settled,nextMarkRefresh;VesselInfo milk;volatile Area pen;Runnable selectionCancel;
        List<Target> herd;Target current;int collected,skipped;double nextStuckNotice;
        final String run=UUID.randomUUID().toString();final List<Gob> marked=new ArrayList<>();
        Task(GameUI gui){super(gui);cleanup=()->{
            if(selectionCancel!=null){selectionCancel.run();selectionCancel=null;}
            if(gui.map!=null){Walking.cancel(gui.map);if(!complete&&gui.map.player()!=null)gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),1,0);}
            if(flower!=null&&gui.ui!=null&&gui.ui.root!=null){FlowerMenu menu=gui.ui.root.getchild(FlowerMenu.class);if(menu!=null)menu.choose(null);}
            flower=null;closeWindow();
            for(Gob gob:marked){hearthborn.world.MilkMark mark=gob.getattr(hearthborn.world.MilkMark.class);if(mark!=null&&run.equals(mark.run))mark.finish();}
        };}
        double now(){return Utils.rtime();}
        void status(String text){gui.showNotice(L10n.tr(text),java.awt.Color.WHITE);}
        @Override void error(String text){gui.showErrorNotice(L10n.tr(text));}
        @Override void cancelled(){status("Milk collection cancelled.");}
        void stuck(){
            if(now()<nextStuckNotice)return;
            nextStuckNotice=now()+10;
            gui.showNotice(L10n.tr("Milk collection is stuck. Retrying barrel placement."),new java.awt.Color(255,220,100));
        }
        Gob player(){return gui.map.player();}
        List<Gob> objects(){List<Gob> all=new ArrayList<>();synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)all.add(gob);}return all;}
        Gob nearest(Predicate<Gob> matches,String missing){
            Gob best=null;double distance=440;
            for(Gob gob:objects())try{if(placed(gob)&&matches.test(gob)&&gob.rc.dist(player().rc)<distance){best=gob;distance=gob.rc.dist(player().rc);}}catch(Loading pending){}
            if(best==null)throw new IllegalStateException(missing);return best;
        }
        @Override void check(){
            if(source!=null&&source.removed||destination!=null&&destination.removed)throw new IllegalStateException("Target disappeared.");
            if(source!=null){Following follow=source.getattr(Following.class);if(follow!=null&&follow.tgt!=player().id)throw new IllegalStateException("Another character is carrying the barrel.");}
            if(herd!=null&&now()>=nextMarkRefresh){nextMarkRefresh=now()+.5;List<Gob> loaded=objects();
                for(Target target:herd)if(target.status!=null){Gob gob=target.resolve(loaded);
                    if(gob!=null){hearthborn.world.MilkMark mark=gob.getattr(hearthborn.world.MilkMark.class);
                        if(mark==null||!run.equals(mark.run))mark(target,target.status);
                    }
                }
            }
        }
        void waitFor(Runnable action,BooleanSupplier ready,double seconds,String failure){
            double[] until={0};
            add(()->{action.run();until[0]=now()+seconds;},()->{
                if(ready.getAsBoolean())return true;
                if(now()>until[0])throw new IllegalStateException(failure);return false;
            },seconds+2);
        }
        void interact(Gob gob,int button){if(gob.removed)throw new IllegalStateException("Target disappeared.");click(gob,button);}
        void closeWindow(){if(opened!=null&&opened.parent!=null){opened.hide();opened.reqclose();}opened=null;}
        boolean findWindow(Gob owner,String caption){
            for(Window window:gui.children(Window.class))if(window.visible()&&
                (Objects.equals(window.objectId(),owner.id)||window.objectId()==null&&!before.contains(window)&&(caption.equals(window.cap)||L10n.text("window",caption).equals(window.cap)))){
                opened=window;return true;
            }
            return false;
        }
        void openBarrel(){
            waitFor(()->{
                before=Collections.newSetFromMap(new IdentityHashMap<>());before.addAll(gui.children(Window.class));
                opened=null;ObjectWindows.clicked(source);interact(source,3);
            },()->findWindow(source,"Barrel"),30,"Could not open the barrel. Milk collection stopped.");
        }
        void chooseDestination(){
            closeWindow();
            if(cisternIndex>=cisterns.size())throw new IllegalStateException("No empty cistern or cistern with this herd's milk within 40 tiles of the starting position.");
            Gob candidate=cisterns.get(cisternIndex++);
            if(!placed(candidate))throw new IllegalStateException("Target disappeared.");
            waitFor(()->{
                before=Collections.newSetFromMap(new IdentityHashMap<>());before.addAll(gui.children(Window.class));
                ObjectWindows.clicked(candidate);interact(candidate,3);
            },()->findWindow(candidate,"Cistern"),30,"Could not open the cistern to check its contents. Milk collection stopped.");
            VesselInfo[] contents={null};String[] signature={null};double[] quiet={0};
            waitFor(()->{},()->{
                if(opened==null||opened.parent==null||!opened.visible())throw new IllegalStateException("The cistern window was closed. Milk collection stopped.");
                VesselInfo observed=VesselInfo.read(opened);
                if(!observed.ready()){signature[0]=null;return false;}
                String current=observed.empty()+":"+observed.contents()+":"+observed.liters();
                if(!current.equals(signature[0])){signature[0]=current;quiet[0]=now()+.5;}
                if(now()<quiet[0])return false;
                contents[0]=observed;return true;
            },10,"Could not read the cistern contents. Milk collection stopped.");
            add(()->{
                VesselInfo observed=contents[0];closeWindow();
                if(!observed.empty()&&!Objects.equals(herd.get(0).kind+" milk",milkType(observed.contents()))){chooseDestination();return;}
                destination=candidate;
                status("Milk destination checked. Using the nearest compatible cistern from the starting position.");
                liftBarrel();queueNext();
            });
        }
        void liftBarrel(){
            waitFor(()->{closeWindow();gui.act("carry");interact(source,1);},()->carriedBy(source,player()),30,"Could not lift the barrel. Milk collection stopped.");
        }
        void stopMovement(){
            Walking.cancel(gui.map);
            gui.map.wdgmsg("click",Coord.z,player().rc.floor(OCache.posres),1,0);
        }
        void putDown(){
            List<Coord2d> tried=new ArrayList<>();double[] next={0},sent={0};
            waitFor(()->{
                if(!carriedBy(source,player()))throw new IllegalStateException("The barrel is no longer being carried.");
                stopMovement();next[0]=now()+.4;
            },()->{
                if(placed(source))return true;
                if(!carriedBy(source,player()))throw new IllegalStateException("The barrel is no longer being carried.");
                if(now()<next[0]||gui.prog!=null)return false;
                // Let a server-driven approach finish before trying a different spot.
                if(player().getattr(Moving.class)!=null&&now()-sent[0]<4)return false;
                Coord2d at=null;
                for(Coord2d candidate:dropPositions(player(),source,objects()))
                    if(tried.stream().noneMatch(old->old.dist(candidate)<2)){at=candidate;break;}
                if(at==null){stuck();next[0]=now()+1;return false;}
                if(!tried.isEmpty())stuck();
                stopMovement();tried.add(at);sent[0]=now();next[0]=now()+1.8;
                gui.map.wdgmsg("click",Coord.z,at.floor(OCache.posres),3,0);
                return placed(source);
            },30,"Could not put down the barrel. Milk collection stopped.");
        }
        void mark(Target target,hearthborn.world.MilkMark.Status status){
            target.status=status;Gob gob=target.resolve(objects());if(gob==null)return;
            gob.setattr(new hearthborn.world.MilkMark(gob,run,status));if(!marked.contains(gob))marked.add(gob);
        }
        void preflight(){
            for(Gob gob:objects())if(carriedBy(gob,player()))throw new IllegalStateException("Put down the carried object before collecting milk.");
            if(player().getattr(Following.class)!=null)throw new IllegalStateException("Dismount before collecting milk.");
            source=nearest(MilkScripts::barrel,"No barrel nearby.");
            // Freeze candidates and their order before area selection or walking changes player.rc.
            Coord2d origin=player().rc;cisterns.clear();cisternIndex=0;destination=null;
            boolean pending=false;
            for(Gob gob:objects())if(placed(gob)&&gob.rc.dist(origin)<440)try{
                if(GameObjects.resourceName(gob)==null){pending=true;continue;}
                if(cistern(gob))cisterns.add(gob);
            }catch(Loading loading){pending=true;}
            cisterns.sort(Comparator.comparingDouble((Gob gob)->gob.rc.dist(origin)).thenComparingLong(gob->gob.id));
            if(cisterns.isEmpty())throw new IllegalStateException(pending?"Nearby object information is still loading. Try collecting milk again.":"No cistern within 40 tiles of the starting position.");
        }
        void start(){
            preflight();
            SavedArea saved=MilkPens.load(gui).current(gui);
            if(saved==null){
                selectionCancel=gui.map.selectLocalArea(area->{pen=area;selectionCancel=null;},()->{
                    selectionCancel=null;if(ScriptRunner.current(gui)==this)ScriptRunner.cancel(gui);
                });
                status("Stand inside the collection area, then drag its boundaries on the map.");
                waitFor(()->{},()->pen!=null,120,"Collection area selection timed out.");
                add(()->{
                    targets(objects(),pen); // Do not persist an empty or mixed-species selection.
                    MilkPens.save(gui,pen,null);
                },()->true,10);
            }else{
                waitFor(()->{},()->{
                    pen=saved.resolve(gui.ui.sess.glob.map);
                    if(pen!=null&&!pen.contains(player().rc.floor(MCache.tilesz)))throw new IllegalStateException("Stand inside the collection area you are selecting.");
                    return pen!=null;
                },10,
                    "The saved collection area is not loaded. Move closer or change its boundaries.");
                status("Using the saved collection area.");
            }
            queueStart();
        }
        void start(Area area){preflight();pen=area;queueStart();}
        void queueStart(){
            waitFor(()->{},()->pen!=null,120,"Collection area selection timed out.");
            waitFor(()->{},()->{
                herd=targets(objects(),pen);
                for(Gob gob:objects())if(gob.getattr(hearthborn.world.MilkMark.class)!=null)gob.delattr(hearthborn.world.MilkMark.class);
                return true;
            },10,"Animal information is still loading. Milk collection stopped.");
            // Empty barrels have no contents overlay; no tooltip/window is needed.
            // An unresolved overlay resource throws Loading and must not count as empty.
            add(()->{
                if(hasContents(source))throw new IllegalStateException("The nearest barrel is not empty. Empty it before collecting milk.");
                status("Milk collection started.");
            });
            add(this::chooseDestination);
        }
        void queueNext(){add(this::nextAnimal,()->true,10);}
        void nextAnimal(){
            current=null;double distance=Double.POSITIVE_INFINITY;List<Gob> loaded=objects();
            for(Target target:herd)if(!target.visited){
                Gob gob=target.resolve(loaded);
                if(gob==null||!animal(gob)){target.visited=true;skipped++;continue;}
                if(nonLactating(gob)){target.visited=true;skipped++;mark(target,hearthborn.world.MilkMark.Status.EMPTY);continue;}
                double d=gob.rc.dist(player().rc);if(d<distance){current=target;distance=d;}
            }
            if(current==null){
                putDown();add(()->{
                    complete=true;status("Milk collection completed.");
                });return;
            }
            current.visited=true;livestock=current.gob;received=false;settled=0;mark(current,hearthborn.world.MilkMark.Status.CURRENT);
            double[] began={0},idleSince={0};Coord2d[] lastPosition={null};
            waitFor(()->{flower="Milk";began[0]=idleSince[0]=now();lastPosition[0]=player().rc;interact(livestock,3);},()->{
                if(!carriedBy(source,player()))throw new IllegalStateException("The barrel is no longer being carried.");
                Gob animal=current.gob!=null&&!current.gob.removed?current.gob:current.resolve(objects());
                boolean moving=player().getattr(Moving.class)!=null||player().rc.dist(lastPosition[0])>.1;
                lastPosition[0]=player().rc;
                if(moving)idleSince[0]=now();
                if(gui.prog!=null){settled=0;idleSince[0]=now();return false;}
                if(hasContents(source)){
                    if(settled==0)settled=now()+.6;
                    if(now()<settled)return false;flower=null;received=true;return true;
                }
                if(animal==null||nonLactating(animal)){flower=null;stopMovement();return true;}
                // A dry animal may reject RMB without even starting the approach.
                // Distance alone cannot distinguish that from waiting to arrive.
                if(!moving&&now()-idleSince[0]>=4){flower=null;stopMovement();return true;}
                if(now()-began[0]>28){flower=null;stopMovement();return true;}
                return false;
            },30,"No milk received from the animal. Milk collection stopped.");
            add(()->{
                if(!received){
                    if(gui.ui.root!=null){FlowerMenu menu=gui.ui.root.getchild(FlowerMenu.class);if(menu!=null)menu.choose(null);}
                    skipped++;mark(current,hearthborn.world.MilkMark.Status.EMPTY);queueNext();return;
                }
                inspectAndPour();
            });
        }
        void inspectAndPour(){
            putDown();openBarrel();
            waitFor(()->{},()->{
                VesselInfo observed=VesselInfo.read(opened);if(!observed.ready())return false;
                if(observed.empty()||milkType(observed.contents())==null)throw new IllegalStateException("The barrel contains no milk. Milk collection stopped.");
                if(observed.liters()<=0||observed.quality()<=0)return false;
                milk=observed;gui.ui.msg(collected(milk),new java.awt.Color(140,255,140),null);return true;
            },10,"Could not read the milk amount and quality. Milk remains in the barrel.");
            liftBarrel();
            waitFor(()->{settled=0;interact(destination,3);},()->{
                if(!carriedBy(source,player()))throw new IllegalStateException("The barrel is no longer being carried.");
                if(hasContents(source)||gui.prog!=null){settled=0;return false;}
                if(settled==0)settled=now()+.6;
                return now()>=settled;
            },30,"Could not confirm that all milk was poured into the cistern. Milk collection stopped.");
            add(()->{collected++;mark(current,hearthborn.world.MilkMark.Status.DONE);});queueNext();
        }
    }
    private MilkScripts(){}
}
