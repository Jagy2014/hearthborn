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
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import java.util.*;
import java.util.function.*;

/**
 * Automation helpers using the modern client's UI thread and inventory stacks.
 *
 * @author Jagy2014
 */
public final class ScriptRunner {
    private static final Map<GameUI,Task> active=new WeakHashMap<>();
    private static final ThreadLocal<Task> issuing=new ThreadLocal<>();
    private static class Step {
        final Runnable action;final BooleanSupplier ready;final double timeout;
        boolean issued;double until;
        Step(Runnable action,BooleanSupplier ready,double timeout){this.action=action;this.ready=ready;this.timeout=timeout;}
    }
    static class Task {
        final GameUI gui;final Deque<Step> steps=new ArrayDeque<>();
        GItem held;Inventory returnInventory;Coord returnCell;Runnable cleanup;boolean returnIssued;String flower;boolean fuelRun;Gob fuelTarget;String fuelResource;int fuelTotal,fuelAdded;boolean cheeseRun;String curdResource;GItem cheeseTray;int cheeseLeft;final Set<GItem> filledTrays=Collections.newSetFromMap(new IdentityHashMap<>());
        String collectionType;Coord2d collectionOrigin;int collected;Window collectionWindow;boolean hanging;
        final Set<Long> collectedObjects=new HashSet<>();
        Set<Window> previousWindows;
        Task(GameUI gui){this.gui=gui;}
        void check(){}
        boolean canIssue(){return true;}
        boolean acceptsHeldAtStart(){return false;}
        void error(String message){gui.ui.error(L10n.tr(message));}
        void cancelled(){}
        void add(Runnable action,BooleanSupplier ready,double timeout){steps.addLast(new Step(action,ready,timeout));}
        void add(Runnable action){add(action,()->true,3);}
        GItem hand(){return gui.vhand==null?null:gui.vhand.item;}
        void take(GItem item){add(()->item.wdgmsg("take",Coord.z,0),()->{if(hand()==null)return false;if(!resource(hand()).equals(resource(item)))throw new IllegalStateException("The cursor is holding another item.");held=hand();return true;},3);}
        void use(Gob target,int modifiers){if(hand()==null || held!=null&&hand()!=held)throw new IllegalStateException("The cursor is holding another item.");Coord at=target.rc.floor(OCache.posres);gui.map.wdgmsg("itemact",Coord.z,at,modifiers,0,(int)target.id,at,0,-1);}
        void click(Gob target,int button){Coord at=target.rc.floor(OCache.posres);gui.map.wdgmsg("click",Coord.z,at,button,0,0,(int)target.id,at,0,-1);}
        void startFuel(String targetName,String resource,int count){
            int available=resource.equals("gfx/invobjs/wblock")?countOwnedFuel(gui,resource):countFuel(gui.maininv,resource);
            String label=resource.equals("gfx/invobjs/wblock")?L10n.tr("Wood block"):GameObjects.name(resource);
            if(available<count)throw new IllegalStateException(L10n.format("Not enough %s in inventory: found %d, need %d.",label,available,count));
            Gob target=nearest(gui,path->path.contains(targetName),gob->gob.rc.dist(gui.map.player().rc)<=33);
            fuelRun=true;fuelTarget=target;fuelResource=resource;fuelTotal=count;
            target.setattr(new hearthborn.world.FuelHighlight(target));
            gui.ui.msg(L10n.format("Adding %d %s.",count,label));
            fuel(target,resource,count);
        }
        void fuel(Gob target,String resource,int left){
            if(left==0){returnHeld();add(()->{},()->hand()==null||hand()!=held,3);return;}
            if(target.removed)throw new IllegalStateException("Target disappeared.");
            GItem item=hand();
            if(item==null){
                item=resource.equals("gfx/invobjs/wblock")?findOwnedFuel(gui,resource):findFuel(gui.maininv,resource);
                if(item==null)throw new IllegalStateException("Not enough fuel in the inventory.");
                take(item);add(()->fuel(target,resource,left));return;
            }
            if(!matchesFuel(item,resource))throw new IllegalStateException("The cursor is holding another item.");
            held=item;final GItem previous=item;final int count=GameObjects.count(item);
            add(()->use(target,0),()->hand()==null||hand()!=previous||GameObjects.count(previous)<count,10);
            add(()->{fuelAdded++;fuel(target,resource,left-1);});
        }
        void startCollection(String type){
            collectionType=type;collectionOrigin=gui.map.player().rc;
            gui.ui.msg(L10n.tr(type.equals("dframe")?"Collecting finished products from drying frames.":"Collecting finished leather from tanning tubs."));
            collectNext();
        }
        void collectNext(){
            Gob target=null;Coord2d origin=gui.map.player().rc;
            for(Gob gob:gui.ui.sess.glob.oc)try{
                if(!collectedObjects.contains(gob.id)&&gob.rc.dist(collectionOrigin)<=110&&finished(gob,collectionType)
                    &&(target==null||gob.rc.dist(origin)<target.rc.dist(origin)))target=gob;
            }catch(Loading waiting){}
            if(target==null)return;
            openCollection(target,this::collectItems);
        }
        void openCollection(Gob chosen,Runnable ready){
            collectedObjects.add(chosen.id);
            previousWindows=Collections.newSetFromMap(new IdentityHashMap<>());
            previousWindows.addAll(gui.children(Window.class));collectionWindow=null;
            add(()->{
                hearthborn.util.ObjectWindows.clicked(chosen);
                Coord at=chosen.rc.floor(OCache.posres);
                Object[] click={Coord.z,at,3,0,0,(int)chosen.id,at,0,-1};
                if(!Walking.start(gui.map,chosen.rc,click,chosen))click(chosen,3);
            },()->{
                for(Window window:gui.children(Window.class))if(window.visible()
                    &&(Objects.equals(window.objectId(),chosen.id)||(window.objectId()==null&&!previousWindows.contains(window)&&collectionCaption(window.cap,collectionType)))){
                    collectionWindow=window;return true;
                }
                return false;
            },30);
            add(ready);
        }
        void startHanging(String type){
            collectionType=type;
            if(findHides(gui,null,collectionType.equals("dframe"))==null)throw new IllegalStateException(collectionType.equals("dframe")?"No raw hides in the inventory or bags.":"No dried hides in the inventory or bags.");
            hanging=true;collectionOrigin=gui.map.player().rc;
            gui.ui.msg(L10n.tr(collectionType.equals("dframe")?"Hanging raw hides on drying frames.":"Loading dried hides into tanning tubs."));hangNext();
        }
        void hangNext(){
            if(findHides(gui,null,collectionType.equals("dframe"))==null)return;
            Gob target=null;Coord2d origin=gui.map.player().rc;
            for(Gob gob:gui.ui.sess.glob.oc)try{
                if(!gob.removed&&!collectedObjects.contains(gob.id)&&gob.rc.dist(collectionOrigin)<=110
                    &&("gfx/terobjs/"+collectionType).equals(GameObjects.resourceName(gob))
                    &&(target==null||gob.rc.dist(origin)<target.rc.dist(origin)))target=gob;
            }catch(Loading ignored){}
            if(target!=null)openCollection(target,this::hangItems);
        }
        void hangItems(){
            if(collectionWindow==null||collectionWindow.parent==null)throw new IllegalStateException("The collection window was closed.");
            Inventory destination=collectionWindow.findchild(Inventory.class);
            if(destination==null||destination.wdgid()<0)throw new IllegalStateException("The drying frame inventory is not available.");
            GItem item=findHides(gui,destination,collectionType.equals("dframe"));
            if(item==null){collectionWindow.reqclose();collectionWindow=null;hangNext();return;}
            Widget original=item.parent;int before=GameObjects.count(item),inside=countItems(destination);
            add(()->item.wdgmsg("invxf2",0,1,destination.wdgid()),()->
                (item.parent!=original||GameObjects.count(item)<before)&&countItems(destination)>inside,5);
            add(()->{collected+=item.parent!=original?before:before-GameObjects.count(item);hangItems();});
        }
        void collectItems(){
            if(collectionWindow==null||collectionWindow.parent==null)throw new IllegalStateException("The collection window was closed.");
            WItem view=null;
            for(WItem candidate:collectionWindow.children(WItem.class))if(candidate.item.parent!=null&&finishedItem(candidate.item,collectionType)){view=candidate;break;}
            GItem item=view==null?find(collectionWindow,candidate->finishedItem(candidate,collectionType)):view.item;
            if(item==null)for(ISBox box:collectionWindow.children(ISBox.class)){
                String resource=box.resource().name;
                if(box.remaining()>0 && finishedResource(resource,collectionType)){
                    if(!fits(gui.maininv,Inventory.sqsz))throw new IllegalStateException("Not enough space in the inventory. Collection stopped.");
                    int before=box.remaining();
                    add(()->box.wdgmsg("xfer2",-1,UI.MOD_SHIFT),()->box.remaining()<before,5);
                    add(()->{collected+=before-box.remaining();collectItems();});return;
                }
            }
            if(item==null){
                final double until=Utils.rtime()+.7;
                add(()->{},()->Utils.rtime()>=until,2);
                add(()->{
                    if(find(collectionWindow,candidate->finishedItem(candidate,collectionType))!=null||
                        collectionWindow.children(WItem.class).stream().anyMatch(candidate->candidate.item.parent!=null&&finishedItem(candidate.item,collectionType))||
                        collectionWindow.children(ISBox.class).stream().anyMatch(box->box.remaining()>0&&finishedResource(box.resource().name,collectionType))){collectItems();return;}
                    collectionWindow.reqclose();collectionWindow=null;collectNext();
                });return;
            }
            if(view==null){
                for(WItem candidate:collectionWindow.children(WItem.class))if(candidate.item==item){view=candidate;break;}
                if(view==null)throw new Loading();
            }
            if(!fits(gui.maininv,view.sz))throw new IllegalStateException("Not enough space in the inventory. Collection stopped.");
            Widget original=item.parent;int before=GameObjects.count(item);
            add(()->item.wdgmsg("transfer",Coord.z,1),()->{
                if(item.parent!=original||GameObjects.count(item)<before)return true;
                return false;
            },4);
            add(()->{collected+=item.parent!=original?before:before-GameObjects.count(item);collectItems();});
        }
        void rope(){
            Gob animal=nearest(gui,path->path.startsWith("gfx/kritter/horse/")||tameable(path),ScriptRunner::standing);
            GItem rope=find(gui.maininv,i->resource(i).equals("gfx/invobjs/rope"));
            if(rope==null)throw new IllegalStateException("No rope in the inventory.");
            cleanup=()->{}; // The rope remains on the cursor, for leading the animal.
            take(rope);add(()->use(animal,0));
        }
        void clover(){
            Gob animal=nearest(gui,ScriptRunner::tameable,ScriptRunner::standing);
            GItem clover=find(gui.maininv,i->resource(i).equals("gfx/invobjs/clover"));
            if(clover==null)throw new IllegalStateException("No clover in the inventory.");
            take(clover);
            final int[] count={0};
            add(()->{count[0]=GameObjects.count(hand());use(animal,0);},()->hand()==null||hand()!=held||GameObjects.count(held)<count[0],10);
        }
        void fish(){
            GItem fish=find(gui.maininv,i->resource(i).startsWith("gfx/invobjs/fish-"));if(fish==null)return;
            final int count=GameObjects.count(fish);final String original=resource(fish);
            add(()->{flower="Butcher";gui.ui.lcc=gui.maininv.rootpos();fish.wdgmsg("iact",Coord.z,0);},()->flower==null,3);
            add(()->{},()->fish.parent==null||!resource(fish).equals(original)||GameObjects.count(fish)<count,10);
            add(this::fish);
        }
        void flower(Gob target,String option){
            add(()->{flower=option;click(target,3);},()->flower==null,10);
        }
        void torch(){
            Gob target=nearest(gui,path->Arrays.asList("gfx/terobjs/oven","gfx/terobjs/smelter","gfx/terobjs/steelcrucible","gfx/terobjs/kiln","gfx/terobjs/cauldron").contains(path));
            if(hand()==null){
                Equipory equipment=gui.equipment();WItem torch=null;int slot=-1;
                if(equipment!=null)for(Widget child=equipment.child;child!=null;child=child.next)if(child instanceof WItem&&resource(((WItem)child).item).equals("gfx/invobjs/torch-l")) {
                    for(int handSlot:new int[]{6,7})if(child.c.equals(Equipory.ecoords[handSlot].add(1,1))){torch=(WItem)child;slot=handSlot;break;}
                    if(torch!=null)break;
                }
                if(torch==null)throw new IllegalStateException("Equip a lit torch first.");
                final int originalSlot=slot;
                cleanup=()->{if(!returnIssued&&hand()!=null&&hand()==held){returnIssued=true;equipment.wdgmsg("drop",originalSlot);}};
                take(torch.item);
            }
            add(()->use(target,0),()->gui.prog!=null,10);
            add(()->{},()->gui.prog==null,60);
        }
        void cheese(){
            GItem curd=hand();
            if(curdResource==null){
                GItem candidate=find(gui.maininv,i->resource(i).startsWith("gfx/invobjs/curd")&&countCurd(gui.maininv,resource(i),null)>=4);
                if(candidate==null)throw new IllegalStateException("At least 4 pieces of the same curd type are required.");
                curdResource=resource(candidate);
            }
            if(curd!=null&&!resource(curd).equals(curdResource))throw new IllegalStateException("The cursor is holding another item.");
            if(cheeseLeft==0){
                int available=countCurd(gui.maininv,curdResource,curd)+(curd==null?0:GameObjects.count(curd));
                if(available<4){finishCheese();return;}
                cheeseTray=null;
                for(Widget window=gui.lchild;window!=null&&cheeseTray==null;window=window.prev)
                    if(window instanceof Window&&window.visible())cheeseTray=find(window,i->!filledTrays.contains(i)&&emptyCheeseTray(i));
                if(cheeseTray==null){finishCheese();return;}
                cheeseLeft=4;
                if(!cheeseRun){cheeseRun=true;gui.ui.msg(L10n.tr("Filling cheese trays with one curd type."));}
            }
            if(curd==null){
                curd=findFuel(gui.maininv,curdResource);
                if(curd==null)throw new IllegalStateException("Not enough curd to finish the current tray.");
                take(curd);add(this::cheese);return;
            }
            held=curd;final GItem target=cheeseTray,previous=curd;final int count=GameObjects.count(curd);final int[] consumed={0};
            add(()->{if(target.parent==null)throw new IllegalStateException("Target disappeared.");target.wdgmsg("itemact",0);},()->{
                if(hand()!=null&&hand()!=previous)throw new IllegalStateException("The cursor is holding another item.");
                int delta=hand()==null?count:count-GameObjects.count(previous);
                if(delta<=0)return false;consumed[0]=Math.min(cheeseLeft,delta);return true;
            },10);
            add(()->{cheeseLeft-=consumed[0];if(cheeseLeft==0)filledTrays.add(target);cheese();});
        }
        void finishCheese(){returnHeld();add(()->{},()->hand()==null||hand()!=held,3);}
        void returnHeld(){
            if(returnIssued||hand()==null||hand()!=held)return;
            Inventory inventory=returnInventory==null?gui.maininv:returnInventory;
            if(inventory==null)return;
            Coord size=gui.vhand.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
            for(int y=0;y<=inventory.isz.y-size.y;y++)for(int x=0;x<=inventory.isz.x-size.x;x++) {
                Coord cell=Coord.of(x,y);boolean free=true;
                for(int yy=y;yy<y+size.y;yy++)for(int xx=x;xx<x+size.x;xx++)
                    if(inventory.sqmask!=null&&inventory.sqmask[yy*inventory.isz.x+xx])free=false;
                for(Widget w=inventory.child;w!=null;w=w.next)if(w instanceof WItem){
                    Coord at=w.c.sub(1,1).div(Inventory.sqsz),extent=w.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
                    if(x<at.x+extent.x&&x+size.x>at.x&&y<at.y+extent.y&&y+size.y>at.y)free=false;
                }
                if(free){returnIssued=true;returnInventory=inventory;returnCell=cell;inventory.wdgmsg("drop",cell);return;}
            }
        }
    }
    private static void finish(GameUI gui,boolean success,boolean announce){
        Task task=active.remove(gui);if(task==null)return;
        if(task.cleanup!=null)task.cleanup.run();else task.returnHeld();
        if(!success&&announce)task.cancelled();
        if(task.collectionType!=null){
            if(gui.map!=null)Walking.cancel(gui.map);
            if(announce)gui.ui.msg(success?L10n.format(task.hanging?(task.collectionType.equals("ttub")?"Hides loaded: %d.":"Hides hung: %d."):"Collection completed: %d items.",task.collected):L10n.tr(task.hanging?(task.collectionType.equals("ttub")?"Hide loading cancelled.":"Hide hanging cancelled."):"Collection cancelled."));
        }
        if(task.fuelTarget!=null)task.fuelTarget.delattr(hearthborn.world.FuelHighlight.class);
        if(task.cheeseRun&&announce)gui.ui.msg(L10n.tr(success?"Cheese tray filling completed.":"Cheese tray filling cancelled."));
        if(task.fuelRun&&announce)gui.ui.msg(success?L10n.format("Fuel added: %d/%d.",task.fuelAdded,task.fuelTotal):L10n.tr("Fuel addition cancelled."));
    }
    public static void cancel(GameUI gui){finish(gui,false,true);}
    /** Manual map movement takes over from field automation immediately. */
    public static void manualFieldInput(GameUI gui){Task task=current(gui);if(task instanceof FieldTask||task instanceof ButcherScripts.Task||task instanceof ShearScripts.Task&&((ShearScripts.Task)task).working()||task instanceof WaterScripts.Task||task instanceof DigScripts.Task||task instanceof ScoutScripts.Task||task instanceof TrellisTask&&((TrellisTask)task).working())cancel(gui);}
    public static boolean fieldRunning(GameUI gui){return current(gui) instanceof FieldTask;}
    public static boolean managesDrinking(GameUI gui){Task task=current(gui);return task instanceof FieldTask||task instanceof ButcherScripts.Task||task instanceof TrellisTask||task instanceof WaterScripts.Task||task instanceof DigScripts.Task||task instanceof ScoutScripts.Task;}
    /** A manual inventory/stockpile command invalidates the field's seed reservation. */
    public static void fieldInput(Widget sender,String message){
        fieldInput(sender,message,new Object[0]);
    }
    public static void fieldInput(Widget sender,String message,Object... args){
        if((sender instanceof GameUI||sender instanceof MenuGrid)&&(message.equals("act")||message.equals("use"))){
            GameUI gui=sender instanceof GameUI?(GameUI)sender:sender.getparent(GameUI.class);Task task=current(gui);
            if((task instanceof TrellisTask||task instanceof ButcherScripts.Task||task instanceof ShearScripts.Task)&&issuing.get()!=task)cancel(gui);
        }
        if(!(sender instanceof GItem||sender instanceof Inventory||sender instanceof ISBox||sender instanceof MapView))return;
        if(!Arrays.asList("take","drop","itemact","iact","transfer","invxf","invxf2","xfer","xfer2","click","place","sel").contains(message))return;
        GameUI gui=sender.getparent(GameUI.class);Task task=current(gui);
        // Opening a bag only changes its window, not the butchering operation.
        if(task instanceof ButcherScripts.Task&&sender instanceof GItem&&message.equals("iact")
            &&((GItem)sender).contents instanceof Inventory&&args.length>=2
            &&args[1] instanceof Number&&((Number)args[1]).intValue()==0)return;
        Task issued=issuing.get();
        if(task instanceof ScoutScripts.Task&&issued!=null&&issued==((ScoutScripts.Task)task).probe)return;
        if((task instanceof FieldTask||task instanceof ButcherScripts.Task||task instanceof ShearScripts.Task||task instanceof WaterScripts.Task||task instanceof DigScripts.Task||task instanceof ScoutScripts.Task||task instanceof TableSalting.Task||task instanceof TrellisTask&&((TrellisTask)task).working())&&issued!=task)cancel(gui);
    }
    public static boolean hideScriptCursor(GameUI gui,GItem item){
        Task task=active.get(gui);
        return task!=null&&((task.fuelRun&&matchesFuel(item,task.fuelResource))
            ||(task.cheeseRun&&resource(item).equals(task.curdResource))
            ||task instanceof TableSalting.Task&&((TableSalting.Task)task).ownsSalt(item));
    }
    public static boolean running(GameUI gui){return active.containsKey(gui);}
    static Task current(GameUI gui){return active.get(gui);}
    static void start(Task task){
        GameUI gui=task.gui;
        if(running(gui))throw new IllegalStateException("Stop the current script first.");
        if(gui.map==null||gui.map.player()==null)throw new IllegalStateException("The map is not ready yet.");
        if(gui.fv!=null&&gui.fv.current!=null)throw new IllegalStateException("Leave combat before processing a field.");
        if(task.hand()!=null&&!task.acceptsHeldAtStart())throw new IllegalStateException("Empty the cursor before starting the script.");
        ShortcutRunner.cancel(gui);Walking.cancel(gui.map);
        if(gui.dreamHarvester!=null)gui.dreamHarvester.cancel();
        active.put(gui,task);
    }
    public static void tick(GameUI gui){
        Task task=active.get(gui);if(task==null)return;
        if(gui.map==null||gui.map.player()==null||gui.fv!=null&&gui.fv.current!=null){cancel(gui);return;}
        Step step=task.steps.peekFirst();if(step==null){finish(gui,true,true);return;}
        try {
            issuing.set(task);
            task.check();
            if(!step.issued&&!task.canIssue())return;
            if(step.until==0)step.until=Utils.rtime()+step.timeout;
            if(!step.issued){step.action.run();step.issued=true;}
            if(step.ready.getAsBoolean())task.steps.removeFirst();
            else if(Utils.rtime()>step.until)throw new IllegalStateException("The script timed out waiting for the server.");
        }catch(Loading loading){if(step.until!=0&&Utils.rtime()>step.until){finish(gui,false,false);task.error("The script timed out waiting for the server.");}}
        catch(IllegalStateException failure){finish(gui,false,false);task.error(failure.getMessage());}
        catch(RuntimeException failure){finish(gui,false,false);new Warning(failure,"Automation script failed").issue();task.error("The script could not complete the action.");}
        finally{issuing.remove();}
    }
    /** Runs one step of a task that another task drives; true when it has nothing left to do. Failures arrive as IllegalStateException. */
    static boolean advance(Task task){
        Step step=task.steps.peekFirst();if(step==null)return true;
        try {
            /* Keep the parent script as issuing (scout tick). Switching to the nested probe
             * made fieldInput cancel the scout on take/itemact. */
            task.check();
            if(!step.issued&&!task.canIssue())return false;
            if(step.until==0)step.until=Utils.rtime()+step.timeout;
            if(!step.issued){step.action.run();step.issued=true;}
            if(step.ready.getAsBoolean())task.steps.removeFirst();
            else if(Utils.rtime()>step.until)throw new IllegalStateException("The script timed out waiting for the server.");
        }catch(Loading loading){if(step.until!=0&&Utils.rtime()>step.until)throw new IllegalStateException("The script timed out waiting for the server.");}
        return task.steps.isEmpty();
    }
    public static void run(GameUI gui,String script){
        if(ButcherScripts.supports(script)){ButcherScripts.run(gui);return;}
        if(ShearScripts.supports(script)){ShearScripts.run(gui);return;}
        if(WaterScripts.supports(script)){WaterScripts.run(gui);return;}
        if(DigScripts.supports(script)){DigScripts.run(gui,script);return;}
        if(ScoutScripts.supports(script)){ScoutScripts.run(gui);return;}
        if("hands".equals(script)){hearthborn.ui.HandMenu.open(gui);return;}
        if(gui==null||gui.map==null||gui.map.player()==null)return;
        if(running(gui)){cancel(gui);return;}
        if(gui.fv!=null&&gui.fv.current!=null)return;
        ShortcutRunner.cancel(gui);
        if(gui.map!=null)Walking.cancel(gui.map);
        Task task=script.equals("milk")?new MilkScripts.Task(gui):script.equals("trellisharvest")||script.equals("trellisdestroy")?new TrellisTask(gui,script.equals("trellisdestroy")):VehicleScripts.supports(script)?new VehicleScripts.Task(gui,script):MiningCraftScripts.supports(script)?new MiningCraftScripts.Task(gui,script):new Task(gui);
        try {
            if(task.hand()!=null && !(script.equals("torch")&&resource(task.hand()).equals("gfx/invobjs/torch-l")))throw new IllegalStateException("Empty the cursor before starting the script.");
            switch(script){
                case "milk":((MilkScripts.Task)task).start();break;
                case "coracle":case "skis":((VehicleScripts.Task)task).start();break;
                case "cellar":case "roastspit":case "chipstone":((MiningCraftScripts.Task)task).start();break;
                case "drycollect":task.startCollection("dframe");break;
                case "leatherload":task.startHanging("ttub");break;
                case "dryhang":task.startHanging("dframe");break;
                case "leathercollect":task.startCollection("ttub");break;
                case "wood5":task.startFuel("terobjs/smokeshed","gfx/invobjs/wblock",5);break;
                case "coal12":task.startFuel("terobjs/smelter","gfx/invobjs/coal",12);break;
                case "coal9":task.startFuel("terobjs/smelter","gfx/invobjs/coal",9);break;
                case "rope":task.rope();break;
                case "clover":task.clover();break;
                case "shoo":task.flower(nearest(gui,path->path.matches("gfx/kritter/(horse|sheep|cattle|pig|goat)/.*"),ScriptRunner::standing),"Shoo");break;
                case "dismount":task.flower(nearest(gui,path->path.startsWith("gfx/kritter/horse/"),ScriptRunner::standing),"Dismount rider");break;
                case "fish":task.fish();break;
                case "trellisdestroy":case "trellisharvest":((TrellisTask)task).start();break;
                case "torch":task.torch();break;
                case "cheesetrayfiller":task.cheese();break;
                case "branchoven":task.startFuel("terobjs/oven","gfx/invobjs/branch",4);break;
                default:throw new IllegalArgumentException(script);
            }
            active.put(gui,task);
        }catch(IllegalStateException failure){task.error(failure.getMessage());}
    }
    public static void notice(UI ui,String message){
        if(message==null||ui.root==null)return;GameUI gui=ui.root.getchild(GameUI.class);Task task=active.get(gui);
        if(task instanceof ShearScripts.Task)((ShearScripts.Task)task).message(message);
        if(task instanceof MiningCraftScripts.Task&&((MiningCraftScripts.Task)task).script.equals("cellar")){
            String lower=message.toLowerCase(Locale.ROOT);
            if(lower.contains("too tired")||lower.contains("слишком устали"))((MiningCraftScripts.Task)task).error="Cellar digging: too tired to dig.";
        }
    }
    /**
     * The task that is waiting for a flower menu: the active script, or a nested scout
     * probe (water pour-out) that sets {@code flower} while the scout stays active.
     */
    static Task flowerTask(GameUI gui){
        Task task=active.get(gui);if(task==null)return null;
        if(task.flower!=null)return task;
        if(task instanceof ScoutScripts.Task){
            Task probe=((ScoutScripts.Task)task).probe;
            if(probe!=null&&probe.flower!=null)return probe;
        }
        return null;
    }
    public static boolean flowerPending(GameUI gui){return flowerTask(gui)!=null;}
    public static int flowerChoice(GameUI gui,String[] options){
        Task task=flowerTask(gui);if(task==null||task.flower==null)return -1;
        for(int i=0;i<options.length;i++)if(Arrays.asList(task.flower.split("\\|")).contains(options[i])){task.flower=null;return i;}
        return -1;
    }
    private static boolean rawHide(GItem item){
        String path=resource(item);
        return path.startsWith("gfx/invobjs/")&&path.endsWith("-blood")
            &&(path.contains("hide")||path.contains("skin")||path.contains("fur"));
    }
    private static GItem findHides(GameUI gui,Inventory destination,boolean raw){
        GItem item=findHide(gui.maininv,destination,raw);
        return item!=null?item:findHide(gui.equipment(),destination,raw);
    }
    private static GItem findHide(Widget container,Inventory destination,boolean raw){
        return find(container,item->{
            String path=resource(item);
            boolean dried=path.startsWith("gfx/invobjs/")&&!path.endsWith("-blood")&&!path.endsWith("-raw")
                &&(path.endsWith("hide")||path.endsWith("skin")||path.endsWith("fur"));
            if(!(raw?rawHide(item):dried)||item.contents!=null&&!item.contents.children(GItem.class).isEmpty())return false;
            if(destination==null)return true;
            GSprite sprite=item.spr();if(sprite==null)throw new Loading();
            return fits(destination,sprite.sz());
        });
    }
    private static int countItems(Widget widget){
        int count=0;
        for(GItem item:widget.children(GItem.class))if(item.parent!=null)count+=GameObjects.count(item);
        return count;
    }
    private static boolean collectionCaption(String caption,String type){
        for(String name:type.equals("dframe")?new String[]{"Drying Frame","Drying frame","Drying Rack","Frame","Rack"}:new String[]{"Tanning Tub","Tanning tub","Tub"})
            if(name.equals(caption)||L10n.text("window",name).equals(caption))return true;
        return false;
    }
    static boolean finished(Gob gob,String type){
        String name=GameObjects.resourceName(gob);
        if(gob.removed||name==null||!name.equals("gfx/terobjs/"+type))return false;
        if(type.equals("ttub")){
            ResDrawable drawn=gob.getattr(ResDrawable.class);if(drawn==null)return false;
            MessageBuf state=drawn.stateData();return !state.eom()&&(Sprite.decnum(state)&8)!=0;
        }
        boolean product=false;
        for(Gob.Overlay overlay:new ArrayList<>(gob.ols))if(overlay.sm instanceof OCache.OlSprite){
            String item=((OCache.OlSprite)overlay.sm).res.get().name;
            if(item.endsWith("-blood")||item.endsWith("-windweed")||item.endsWith("-fishraw"))return false;
            product=true;
        }
        return product;
    }
    static boolean finishedItem(GItem item,String type){
        return finishedResource(resource(item),type);
    }
    private static boolean finishedResource(String name,String type){
        if(type.equals("ttub"))return name.equals("gfx/invobjs/leather")||name.startsWith("gfx/invobjs/leather-");
        return !name.endsWith("-blood")&&!name.endsWith("-windweed")&&!name.endsWith("-fishraw");
    }
    static boolean fits(Inventory inventory,Coord pixels){
        if(inventory==null)return false;
        Coord size=pixels.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
        for(int y=0;y+size.y<=inventory.isz.y;y++)for(int x=0;x+size.x<=inventory.isz.x;x++){
            boolean free=true;
            for(int yy=y;yy<y+size.y;yy++)for(int xx=x;xx<x+size.x;xx++)
                if(inventory.sqmask!=null&&inventory.sqmask[yy*inventory.isz.x+xx])free=false;
            for(WItem occupied:inventory.children(WItem.class)){
                Coord at=occupied.c.sub(1,1).div(Inventory.sqsz),extent=occupied.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
                if(x<at.x+extent.x&&x+size.x>at.x&&y<at.y+extent.y&&y+size.y>at.y)free=false;
            }
            if(free)return true;
        }
        return false;
    }
    private static boolean tameable(String path){return Arrays.asList("gfx/kritter/horse/horse","gfx/kritter/sheep/sheep","gfx/kritter/cattle/cattle","gfx/kritter/boar/boar","gfx/kritter/goat/wildgoat").contains(path);}
    static boolean standing(Gob gob){
        Drawable drawable=gob.getattr(Drawable.class);
        if(drawable instanceof Composite){
            Composited comp=((Composite)drawable).comp;
            if(comp!=null&&comp.poses!=null&&comp.poses.mods!=null)for(Skeleton.PoseMod pose:comp.poses.mods){
                Resource.Layer layer=GameObjects.field(pose,"this$0",Resource.Layer.class);
                if(layer!=null){String path=layer.getres().name;if(path.contains("/dead")||path.contains("waterdead")||path.contains("/knock"))return false;}
            }
        }
        return true;
    }
    static boolean trellis(String path){return Arrays.asList("gfx/terobjs/plants/wine","gfx/terobjs/plants/pepper","gfx/terobjs/plants/hops","gfx/terobjs/plants/peas","gfx/terobjs/plants/cucumber").contains(path);}
    /** The growth stage of a crop and its last stage, or null when the plant has no readable state yet. */
    static int[] stage(Gob gob){
        ResDrawable drawable=gob.getattr(ResDrawable.class);if(drawable==null)return null;
        int max=0;for(FastMesh.MeshRes mesh:drawable.rres.layers(FastMesh.MeshRes.class))max=Math.max(max,mesh.id/10);
        MessageBuf state=drawable.stateData();return new int[]{state.eom()?-1:state.uint8(),max};
    }
    static boolean mature(Gob gob){
        ResDrawable drawable=gob.getattr(ResDrawable.class);if(drawable==null)return false;
        int max=0;for(FastMesh.MeshRes mesh:drawable.rres.layers(FastMesh.MeshRes.class))max=Math.max(max,mesh.id/10);
        MessageBuf state=drawable.stateData();return max>0&&!state.eom()&&state.uint8()==max;
    }
    static boolean matchesFuel(GItem item,String path){return path.equals("gfx/invobjs/wblock")&&(resource(item).equals(path)||resource(item).startsWith(path+"-"))||resource(item).equals(path)||path.equals("gfx/invobjs/coal")&&resource(item).equals("gfx/invobjs/blackcoal");}
    private static boolean fuelLeaf(GItem item){return item.contents==null||item.contents.children(GItem.class).isEmpty();}
    static int countOwnedFuel(GameUI gui,String path){
        int total=0;for(GItem item:ShortcutRunner.owned(gui))if(fuelLeaf(item)&&matchesFuel(item,path))total+=GameObjects.count(item);
        return total;
    }
    private static GItem findOwnedFuel(GameUI gui,String path){
        for(GItem item:ShortcutRunner.owned(gui))if(fuelLeaf(item)&&matchesFuel(item,path))return item;
        return null;
    }
    private static GItem findFuel(Widget container,String path){
        if(container==null)return null;
        for(Widget child=container.child;child!=null;child=child.next)if(child instanceof GItem){
            GItem item=(GItem)child;GItem nested=findFuel(item.contents,path);
            if(nested!=null)return nested;if(matchesFuel(item,path))return item;
        }
        return null;
    }
    private static int countFuel(Widget container,String path){
        if(container==null)return 0;int count=0;
        for(Widget child=container.child;child!=null;child=child.next)if(child instanceof GItem){
            GItem item=(GItem)child;int nested=countFuel(item.contents,path);
            count+=nested>0?nested:matchesFuel(item,path)?GameObjects.count(item):0;
        }
        return count;
    }
    private static boolean emptyCheeseTray(GItem item){
        String path=resource(item);
        if(!path.equals("gfx/invobjs/cheesetray")&&!path.equals("gfx/invobjs/cheesetray-empty"))return false;
        for(ItemInfo info:item.info())if(info instanceof ItemInfo.Contents&&!((ItemInfo.Contents)info).sub.isEmpty())return false;
        return item.contents==null||item.contents.children(GItem.class).isEmpty();
    }
    private static int countCurd(Widget container,String path,GItem exclude){
        if(container==null)return 0;int count=0;
        for(Widget child=container.child;child!=null;child=child.next)if(child instanceof GItem&&child!=exclude){
            GItem item=(GItem)child;int nested=countCurd(item.contents,path,exclude);
            count+=item.contents!=null&&!item.contents.children(GItem.class).isEmpty()?nested:resource(item).equals(path)?GameObjects.count(item):0;
        }
        return count;
    }
    private static String resource(GItem item){return item.resource().name;}
    private static GItem find(Widget container,Predicate<GItem> predicate){
        if(container==null)return null;
        for(Widget child=container.child;child!=null;child=child.next)if(child instanceof GItem){
            GItem item=(GItem)child;
            if(predicate.test(item))return item;
            GItem nested=find(item.contents,predicate);if(nested!=null)return nested;
        }else if(!(child instanceof WItem)){
            GItem nested=find(child,predicate);if(nested!=null)return nested;
        }
        return null;
    }
    private static Gob nearest(GameUI gui,Predicate<String> predicate){return nearest(gui,predicate,g->true);}
    private static Gob nearest(GameUI gui,Predicate<String> predicate,Predicate<Gob> eligible){
        Gob result=null;Gob player=gui.map.player();
        for(Gob gob:gui.ui.sess.glob.oc)try {
            String name=GameObjects.resourceName(gob);
            if(gob!=player&&!gob.removed&&name!=null&&predicate.test(name)&&eligible.test(gob)&&(result==null||gob.rc.dist(player.rc)<result.rc.dist(player.rc)))result=gob;
        }catch(Loading ignored){}
        if(result==null)throw new IllegalStateException("No suitable object nearby.");
        return result;
    }
    private ScriptRunner(){}
}
