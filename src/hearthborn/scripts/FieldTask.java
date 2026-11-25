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
import hearthborn.QualityOverlay;
import hearthborn.inventory.BagTransfers;
import hearthborn.inventory.InventoryActions;
import hearthborn.navigation.Walking;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import hearthborn.world.ObjectGeometry;
import hearthborn.ui.AutoDrink;
import hearthborn.ui.DrinkSupply;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.*;

/**
 * One acknowledged operation at a time; all seed selection follows the complete harvest.
 *
 * @author Jagy2014
 */
class FieldTask extends ScriptRunner.Task {
    private static final Set<String> STORAGE=new HashSet<>(Arrays.asList("cupboard","chest","largechest","woodenchest","bonechest","coffer","crate","linencrate","metalcabinet","stonecasket"));
    final WheatFields book;final WheatFields.Field field;
    CropProfile profile;
    final boolean harvestOnly;
    private final boolean plantOnly;
    Area land,seedArea,productArea;
    boolean paused,complete,ownsPlacement;
    private int plantedTiles;
    /** Quality of the seeds this run actually planted, weighted by the seeds used; known only for batches whose consumption could be read exactly. */
    private double sownQuality;private int sownSeeds;private double sownLowest=Double.POSITIVE_INFINITY,sownHighest;
    private boolean plantingExhausted,preserveExisting;
    private boolean preparing=true;
    private final FieldQualityCheck qualityCheck=new FieldQualityCheck();
    private FieldQualityCheck.Prompt qualityPrompt;
    private double busySince;
    private String waitingFor="Checking the saved field";
    String reserveStatus="";
    private Window opened;
    private Gob openedFor;
    private boolean closeOpened;
    private boolean loadingSeeds=true,nativePlanting,plantInterrupted,nativeHarvesting,harvestInterrupted;
    private final List<Gob> stores=new ArrayList<>();
    private final List<Seed> census=new ArrayList<>();
    private final Set<Long> fullPiles=new HashSet<>();
    /** Piles found full, kept for the whole session so that the next run does not have to find them out again. */
    private static final Map<Glob,Set<Long>> rememberedFull=Collections.synchronizedMap(new WeakHashMap<>());
    private Set<Long> remembered(){
        synchronized(rememberedFull){return rememberedFull.computeIfAbsent(gui.ui.sess.glob,glob->Collections.synchronizedSet(new HashSet<>()));}
    }
    private boolean isFull(Gob pile){return fullPiles.contains(pile.id)||remembered().contains(pile.id);}
    private void markFull(Gob pile){fullPiles.add(pile.id);remembered().add(pile.id);}
    private final Set<Coord> blockedPileSites=new HashSet<>(),blockedPileApproaches=new HashSet<>();
    private final Map<Coord,Integer> pileSiteRouteFailures=new HashMap<>();
    private int failedPileWalks;
    private static final class Seed {
        final Gob store;final GItem item;final WheatFields.Lot lot;
        Seed(Gob store,GItem item){this.store=store;this.item=item;this.lot=lot(item);}
    }
    FieldTask(GameUI gui,WheatFields book,WheatFields.Field field){
        this(gui,book,field,field.harvestOnly);
    }
    FieldTask(GameUI gui,WheatFields book,WheatFields.Field field,boolean harvestOnly){
        this(gui,book,field,harvestOnly,false);
    }
    FieldTask(GameUI gui,WheatFields book,WheatFields.Field field,boolean harvestOnly,boolean plantOnly){
        super(gui);this.book=book;this.field=field;
        this.harvestOnly=harvestOnly;this.plantOnly=plantOnly;
        cleanup=()->{
            if(qualityPrompt!=null){qualityPrompt.close();qualityPrompt=null;}
            if(gui.map!=null){
                Walking.cancel(gui.map);
                if(ownsPlacement)gui.map.wdgmsg("place",Coord.z,0,3,0);
                if(!complete&&gui.map.player()!=null)gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);
                if(nativePlanting||nativeHarvesting)gui.map.cancelServerArea();
            }
            returnHeld();closeWindow();
        };
    }
    static FieldTask current(GameUI gui){ScriptRunner.Task task=ScriptRunner.current(gui);return task instanceof FieldTask?(FieldTask)task:null;}
    static void launch(GameUI gui,WheatFields book,WheatFields.Field field){
        launch(gui,book,field,false);
    }
    static void launch(GameUI gui,WheatFields book,WheatFields.Field field,boolean harvestOnly){
        start(new FieldTask(gui,book,field,harvestOnly));
    }
    static void launchPlanting(GameUI gui,WheatFields book,WheatFields.Field field){
        start(new FieldTask(gui,book,field,false,true));
    }
    private static void start(FieldTask task){
        GameUI gui=task.gui;
        try{
            if(BagTransfers.running(gui))throw new IllegalStateException("Finish the inventory transfer first.");
            ScriptRunner.start(task);task.add(task::prepare,()->true,30);
        }catch(IllegalStateException failure){task.error(failure.getMessage());}
        catch(Loading pending){task.error("Items are still loading. Try again.");}
    }
    /** One root, or a stack made only of roots, of the crop being processed. */
    private static boolean leftoverRoot(GItem item,CropProfile crop){
        if(item==null||crop==null||!crop.rootCrop)return false;
        if(crop.seed.equals(item.resource().name))return true;
        if(!QualityOverlay.stacked(item)||item.contents==null)return false;
        List<GItem> members=new ArrayList<>(item.contents.children(GItem.class));
        if(members.isEmpty())return false;
        for(GItem member:members)if(!crop.seed.equals(member.resource().name))return false;
        return true;
    }
    boolean acceptsHeldAtStart(){
        if(hand()==null||field.land==null)return false;
        Area area=field.land.resolve(gui.ui.sess.glob.map);if(area==null)return false;
        CropProfile.Detection detected=CropProfile.inspect(gui,area);
        return leftoverRoot(hand(),field.cropPinned||detected.crop==null?CropProfile.get(field.cropId):detected.crop);
    }
    /** Native root harvesting can leave its last root, or a few, on the cursor when the pack is full. Drop only
     * unowned roots of this crop; planting/pile/swap items held by this task stay protected. */
    private boolean dropHarvestCarrot(Runnable then){
        if(hand()==held||!leftoverRoot(hand(),profile))return false;
        GItem leftover=hand();
        await("Dropping the leftover root crop",()->{
            if(hand()!=leftover)throw new IllegalStateException("The cursor is holding another item.");
            gui.map.wdgmsg("drop",Coord.z,gui.map.player().rc.floor(OCache.posres),0);
        },()->hand()==null,5);
        add(then);return true;
    }
    boolean canIssue(){
        if(qualityPrompt!=null){busySince=0;return false;}
        if(paused||gui.prog==null){busySince=0;return !paused;}
        if(busySince==0)busySince=now();
        if(now()-busySince>120)throw new IllegalStateException("The script timed out waiting for the server.");
        return false;
    }
    double now(){return Utils.rtime();}
    static String describe(WheatFields.Field field){
        switch(field.phase){
        case READY:return field.summary()+" · "+L10n.tr("Ready to process field.");
        case HARVEST:return L10n.format("Harvest: %d/%d",field.harvested.cardinality(),field.tiles());
        case PLANT:return L10n.format("Planting: %d/%d",field.planted.cardinality(),field.tiles());
        case COLLECT:return L10n.tr("Collecting and storing the harvest product.");
        case STORE:return L10n.tr("Returning remaining seeds to storage.");
        case DONE:return L10n.tr("Field completed. The next launch starts a new harvest.");
        default:throw new AssertionError(field.phase);
        }
    }
    boolean waitingForQuality(){return qualityPrompt!=null;}
    String progress(){return preparing?L10n.tr(waitingFor):describe(field);}
    String status(){return field.title()+" · "+(harvestOnly?L10n.tr("Harvest only")+" · ":"")+(waitingForQuality()?L10n.tr("Waiting for planting confirmation"):progress())+(paused?" · "+L10n.tr("Paused"):"");}
    void notice(String text){gui.showNotice(text,Color.WHITE);}
    void error(String message){
        String text=message.equals("The script timed out waiting for the server.")
            ?L10n.format("No server response: %s. Check access and resume.",L10n.tr(waitingFor)):L10n.tr(message);
        gui.showErrorNotice(field.title()+": "+text);
    }
    private void await(String stage,Runnable action,java.util.function.BooleanSupplier ready,double timeout){
        add(()->{waitingFor=stage;action.run();},ready,timeout);
    }
    void take(GItem item){add(()->waitingFor="Taking an item from inventory");super.take(item);}
    void cancelled(){notice(L10n.tr("Field processing stopped. Progress saved."));}
    List<Gob> objects(){
        OCache cache=gui.ui.sess.glob.oc;List<Gob> result=new ArrayList<>();
        synchronized(cache){for(Gob gob:cache)result.add(gob);}return result;
    }
    static boolean inside(Gob gob,Area area){return !gob.removed&&gob.rc!=null&&gob.getattr(Following.class)==null&&area.contains(gob.rc.floor(MCache.tilesz));}
    static boolean storage(Gob gob){String path=GameObjects.resourceName(gob);return path!=null&&path.startsWith("gfx/terobjs/")&&STORAGE.contains(path.substring("gfx/terobjs/".length()));}
    private boolean fieldPlant(Gob gob){
        return CropProfile.plantResource(gob)!=null;
    }
    Gob crop(Coord tile){
        Gob found=null;
        for(Gob gob:objects())if(!gob.removed&&gob.rc!=null&&gob.rc.floor(MCache.tilesz).equals(tile)&&fieldPlant(gob)){
            if(found!=null)throw new IllegalStateException("More than one crop occupies a field tile.");found=gob;
        }
        return found;
    }
    /** One cache snapshot per full-area scan, rather than copying every visible object for each tile. */
    private Gob[] fieldCrops(){
        Gob[] crops=new Gob[field.tiles()];
        for(Gob gob:objects())if(!gob.removed&&gob.rc!=null){
            Coord tile=gob.rc.floor(MCache.tilesz);
            if(land.contains(tile)&&fieldPlant(gob)){
                int index=(tile.y-land.ul.y)*field.land.width+tile.x-land.ul.x;
                if(crops[index]!=null)throw new IllegalStateException("More than one crop occupies a field tile.");
                crops[index]=gob;
            }
        }
        return crops;
    }
    void check(){
        if(land!=null&&(!land.equals(field.land.resolve(gui.ui.sess.glob.map))||!seedArea.equals(field.seeds.resolve(gui.ui.sess.glob.map))||!productArea.equals(field.straw.resolve(gui.ui.sess.glob.map))))
            throw new IllegalStateException("A saved field area is not loaded. Move closer and resume.");
        if(BagTransfers.running(gui))throw new IllegalStateException("Finish the inventory transfer first.");
        if(gui.fv!=null&&gui.fv.current!=null)throw new IllegalStateException("Leave combat before processing a field.");
        if(paused&&nativePlanting&&!plantInterrupted){
            plantInterrupted=true;gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);gui.map.cancelServerArea();
        }
        if(nativeHarvesting&&!harvestInterrupted){
            int threshold=AutoDrink.below();double stamina=stamina();
            boolean cursorCarrot=hand()!=held&&leftoverRoot(hand(),profile);
            if(paused||cursorCarrot||freeSlots(gui.maininv)<8||threshold>0&&stamina>=0&&stamina<=threshold/100.0)stopHarvest();
        }
    }
    private void stopHarvest(){
        harvestInterrupted=true;gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);gui.map.cancelServerArea();
    }
    private Area resolve(SavedArea saved){Area area=saved.resolve(gui.ui.sess.glob.map);if(area==null)throw new IllegalStateException("A saved field area is not loaded. Move closer and resume.");return area;}
    private static boolean overlap(Area a,Area b){return a.ul.x<b.br.x&&a.br.x>b.ul.x&&a.ul.y<b.br.y&&a.br.y>b.ul.y;}
    void prepare(){
        if(field.land==null||field.seeds==null||field.straw==null)throw new IllegalStateException("Select the field, seed storage and harvest stockpile areas first.");
        if(gui.maininv==null)throw new IllegalStateException("The inventory is not ready yet.");
        if(gui.map.placementResource()!=null)throw new IllegalStateException("Finish the current placement before processing a field.");
        if(gui.map.hasAreaSelection())throw new IllegalStateException("Finish the current area selection first.");
        land=resolve(field.land);seedArea=resolve(field.seeds);productArea=resolve(field.straw);
        if(overlap(land,productArea))throw new IllegalStateException("The harvest stockpile area must be outside the field.");
        waitingFor="Checking field plants";
        CropProfile.Detection detected=CropProfile.inspect(gui,land);
        boolean fresh=field.phase==WheatFields.Phase.READY||field.phase==WheatFields.Phase.DONE;
        if(plantOnly&&!fresh&&field.phase!=WheatFields.Phase.PLANT)
            throw new IllegalStateException("Finish the interrupted field cycle before sowing.");
        profile=CropProfile.get(field.cropId);
        if(!field.cropPinned){
            if(detected.mixed)throw new IllegalStateException("Choose the crop to plant in this mixed field before starting.");
            if(detected.crop!=null){
                if(!fresh&&profile!=null&&profile!=detected.crop)throw new IllegalStateException("The crop changed during an unfinished cycle. Check the field before resuming.");
                profile=detected.crop;
            }
        }
        if(profile==null)throw new IllegalStateException("Choose a crop for the empty field before starting.");
        boolean sameCrop=profile.id.equals(field.cropId);
        field.cropId=profile.id;
        preserveExisting=detected.mixed||detected.crop!=null&&detected.crop!=profile;
        if(preserveExisting){
            String text=L10n.format("Planting %s in empty tiles only. Existing crops will be left growing.",profile.label());
            Color color=new Color(235,195,95);gui.showNotice(text,color);if(gui.syslog!=null)gui.syslog.append(text,color);
        }
        Coord2d origin=new Coord2d(land.ul).add(new Coord2d(land.sz()).mul(.5));
        for(Area area:Arrays.asList(seedArea,productArea))for(Coord corner:Arrays.asList(area.ul,area.br.sub(1,1),Coord.of(area.ul.x,area.br.y-1),Coord.of(area.br.x-1,area.ul.y)))
            if(new Coord2d(corner).dist(origin)>40)throw new IllegalStateException("Keep the storage areas within forty tiles of the field.");
        stores.clear();
        for(Gob gob:objects())if(inside(gob,seedArea)&&storage(gob))stores.add(gob);
        stores.sort(Comparator.comparingDouble(gob->gob.rc.dist(gui.map.player().rc)));
        if(stores.isEmpty())throw new IllegalStateException("No seed cupboards or chests in the selected storage area.");
        boolean harvesting=!plantOnly&&(fresh||field.phase==WheatFields.Phase.HARVEST
            ||field.harvestOnly!=harvestOnly||harvestOnly&&field.phase==WheatFields.Phase.PLANT);
        Gob[] crops=fieldCrops();
        for(int index=0;index<field.tiles();index++){
            Gob crop=crops[index];
            if(harvesting||fresh)continue; // Empty and young tiles are reconciled together after validation.
            // Resume from actual empty cells. A manually planted crop is simply occupied,
            // even if it was not present in the saved planting progress.
            field.planted.set(index,crop!=null);
        }
        if(hand()!=null){
            if(dropHarvestCarrot(this::prepare))return;
            throw new IllegalStateException("Empty the cursor before starting the script.");
        }
        if(fresh){
            double[] minimum=field.plantedMinQ,maximum=field.plantedMaxQ;
            field.reset();
            // A new run recounts live plants, but keeps known sowing quality for
            // plants left growing after an earlier run ran out of material.
            if(sameCrop&&minimum!=null&&maximum!=null&&minimum.length==field.tiles()&&maximum.length==field.tiles()){
                for(int index=0;index<field.tiles();index++){
                    Gob crop=crops[index];
                    if(crop==null||!profile.crop.equals(GameObjects.resourceName(crop))||harvesting&&!preserveExisting&&ScriptRunner.mature(crop))minimum[index]=maximum[index]=0;
                }
                field.plantedMinQ=minimum;field.plantedMaxQ=maximum;
            }
        }
        if(plantOnly){
            // Sowing skips the harvest phase; occupied tiles are already satisfied.
            field.harvested.set(0,crops.length);
            for(int index=0;index<crops.length;index++) field.planted.set(index,crops[index]!=null);
            field.phase=WheatFields.Phase.PLANT;
        }
        field.harvestOnly=harvestOnly;
        if(harvesting)field.phase=WheatFields.Phase.HARVEST;
        if(harvesting)reconcileHarvest();
        if(harvesting&&!preserveExisting)reportRipeness(crops);
        else if(!harvestOnly&&field.planted.cardinality()<field.tiles()&&
                (field.phase==WheatFields.Phase.STORE||field.phase==WheatFields.Phase.COLLECT&&!profile.rootCrop))
            field.phase=profile.rootCrop?WheatFields.Phase.COLLECT:WheatFields.Phase.PLANT;
        field.pending=-1;preparing=false;book.save(gui);notice(status());
        Runnable resume=()->{
            if(field.phase==WheatFields.Phase.HARVEST)harvest();
            else if(field.phase==WheatFields.Phase.PLANT){
                if(profile.rootCrop)bufferRoots(this::plant);
                else depositSeeds(0,()->scanSeeds(this::plant));
            }
            else if(field.phase==WheatFields.Phase.COLLECT)collect();
            else if(profile.rootCrop)bufferRoots(this::finishSeeds);
            else finishSeeds();
        };
        // Plant/collect checkpoints already read the live stock. Only preflight an
        // actual harvest here; an empty field must not inspect the cupboard twice.
        if(profile.rootCrop||field.phase!=WheatFields.Phase.HARVEST)resume.run();else scanSeeds(resume);
    }
    static WheatFields.Lot lot(GItem item){
        Double quality=QualityOverlay.qualityOf(item);
        if(quality==null||!Double.isFinite(quality)||quality<0)throw new Loading();
        int count=GameObjects.count(item);
        return new WheatFields.Lot(quality,count);
    }
    static List<GItem> items(Widget owner,String resource){
        List<GItem> result=new ArrayList<>();if(owner==null)return result;
        for(Widget child=owner.child;child!=null;child=child.next)if(child instanceof GItem){
            GItem item=(GItem)child;
            String path=item.resource().name;
            if(path.equals("gfx/invobjs/stack")||item.contents!=null&&item.contents.getClass().getName().endsWith("ItemStack")){
                if(item.contents==null||item.contents.children(GItem.class).isEmpty())throw new Loading();
                result.addAll(items(item.contents,resource));
            }else if(resource.equals(path))result.add(item);
        }
        return result;
    }
    private List<GItem> bag(String resource){return items(gui.maininv,resource);}
    private int seedCount(){int count=0;for(GItem item:bag(profile.seed))count+=GameObjects.count(item);return count;}
    private Coord free(Inventory inventory){
        return free(inventory,Coord.of(1,1));
    }
    private BitSet occupied(Inventory inventory){
        BitSet occupied=new BitSet(inventory.isz.x*inventory.isz.y);
        for(WItem item:InventoryActions.items(inventory)){
            Coord at=InventoryActions.cell(item),extent=item.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
            for(int y=Math.max(0,at.y);y<Math.min(inventory.isz.y,at.y+extent.y);y++)
                for(int x=Math.max(0,at.x);x<Math.min(inventory.isz.x,at.x+extent.x);x++)occupied.set(y*inventory.isz.x+x);
        }
        for(int index=0;index<inventory.isz.x*inventory.isz.y;index++)
            if(inventory.sqmask!=null&&inventory.sqmask[index])occupied.set(index);
        return occupied;
    }
    private Coord reserveSpace(Inventory inventory,BitSet occupied,Coord size){
        for(int y=0;y+size.y<=inventory.isz.y;y++)for(int x=0;x+size.x<=inventory.isz.x;x++){
            boolean fits=true;
            for(int yy=y;yy<y+size.y;yy++)for(int xx=x;xx<x+size.x;xx++)if(occupied.get(yy*inventory.isz.x+xx))fits=false;
            if(!fits)continue;
            for(int yy=y;yy<y+size.y;yy++)occupied.set(yy*inventory.isz.x+x,yy*inventory.isz.x+x+size.x);
            return Coord.of(x,y);
        }
        return null;
    }
    private Coord free(Inventory inventory,Coord size){return reserveSpace(inventory,occupied(inventory),size);}
    private int freeSlots(Inventory inventory){return inventory.isz.x*inventory.isz.y-occupied(inventory).cardinality();}
    private int seedRoom(Inventory inventory){
        if(profile.seedSize.equals(Coord.of(1,1)))return freeSlots(inventory);
        BitSet used=occupied(inventory);int count=0;
        while(reserveSpace(inventory,used,profile.seedSize)!=null)count++;
        return count;
    }
    private boolean scythe(){
        Equipory equipment=gui.equipment();
        if(equipment!=null)for(GItem item:equipment.children(GItem.class)){
            String resource=item.resource().name;
            // Equipped tools may use their small inventory resource rather than the full icon.
            if(resource.equals("gfx/invobjs/scythe")||resource.equals("gfx/invobjs/small/scythe"))return true;
        }
        return false;
    }
    private void closeWindow(){if(closeOpened&&opened!=null&&opened.parent!=null){opened.hide();opened.reqclose();}opened=null;openedFor=null;closeOpened=false;}
    private boolean caption(Window window,Gob gob){
        String resource=GameObjects.resourceName(gob);
        String[] names=profile.pile.equals(resource)?new String[]{"Stockpile"}:new String[]{"Cupboard","Chest","Large Chest","Wooden Chest","Bone Chest","Coffer","Crate","Linen Crate","Metal Cabinet","Stone Casket"};
        for(String name:names)for(String title:new String[]{name,L10n.text("window",name)})
            if(title.equals(window.cap)||window.cap!=null&&window.cap.startsWith(title+" "))return true;
        return false;
    }
    private void open(Gob gob,Runnable then){
        if(gob.removed)throw new IllegalStateException("A seed container or stockpile disappeared. Check the saved areas.");
        if(!(storage(gob)&&inside(gob,seedArea)||profile.pile.equals(GameObjects.resourceName(gob))&&inside(gob,productArea)))
            throw new IllegalStateException("A seed container or stockpile disappeared. Check the saved areas.");
        if(openedFor==gob&&opened!=null&&opened.parent!=null&&opened.visible()){add(then);return;}
        if(gui.map.player().rc.dist(gob.rc)>MCache.tilesz.x){
            closeWindow();
            approachObject(gob,profile.pile.equals(GameObjects.resourceName(gob))?"Walking to the stockpile":"Walking to seed storage",()->open(gob,then));return;
        }
        closeWindow();Set<Window> before=Collections.newSetFromMap(new IdentityHashMap<>());
        openedFor=gob;
        await(profile.pile.equals(GameObjects.resourceName(gob))?"Opening the stockpile":"Opening seed storage",()->{
            for(Window window:gui.children(Window.class))if(window.visible()&&Objects.equals(window.objectId(),gob.id)){opened=window;return;}
            for(Window window:gui.children(Window.class))if(window.visible())before.add(window);
            ObjectWindows.clicked(gob);click(gob,3);
        },()->{
            if(opened!=null)return true;
            for(Window window:gui.children(Window.class))if(window.visible()&&(Objects.equals(window.objectId(),gob.id)
                ||window.objectId()==null&&!before.contains(window)&&caption(window,gob))){
                opened=window;closeOpened=!before.contains(window);return true;
            }
            return false;
        },30);
        // Server widget creation and item creation are separate queue commands.
        // Wait for a quiet, fully readable inventory instead of treating its first empty frame as a census.
        final long[] previous={Long.MIN_VALUE};final double[] changed={0};
        await(profile.pile.equals(GameObjects.resourceName(gob))?"Reading stockpile capacity":"Reading seed storage",()->{},()->{
            if(opened==null||opened.parent==null||!opened.visible())throw new IllegalStateException(profile.pile.equals(GameObjects.resourceName(gob))?"The stockpile window was closed.":"The seed container was closed.");
            long signature=1;boolean found=false;
            for(Inventory inventory:opened.children(Inventory.class)){
                found=true;for(GItem item:inventory.children(GItem.class)){
                    signature=31*signature+System.identityHashCode(item);
                    signature=31*signature+item.infoseq;
                }
                for(GItem item:items(inventory,profile.seed)){WheatFields.Lot lot=lot(item);signature=31*signature+lot.count;signature=31*signature+Double.doubleToLongBits(lot.quality);}
            }
            for(ISBox box:opened.children(ISBox.class)){found=true;box.resource();signature=31*signature+box.remaining();signature=31*signature+box.capacity();}
            if(!found)throw new Loading();
            if(signature!=previous[0]){previous[0]=signature;changed[0]=now();return false;}
            return now()-changed[0]>=.5;
        },10);
        add(then);
    }
    private Inventory inventory(){if(opened==null||opened.parent==null||!opened.visible())throw new IllegalStateException("The seed container was closed.");Inventory inventory=opened.findchild(Inventory.class);if(inventory==null)throw new Loading();return inventory;}
    private void scanSeeds(Runnable then){census.clear();scan(0,then);}
    private void scan(int index,Runnable then){
        if(index==stores.size()){
            List<Seed> found=new ArrayList<>();for(GItem item:bag(profile.seed))found.add(new Seed(null,item));census.addAll(found);
            then.run();return;
        }
        Gob store=stores.get(index);
        open(store,()->{List<Seed> found=new ArrayList<>();for(GItem item:items(inventory(),profile.seed))found.add(new Seed(store,item));census.addAll(found);
            scan(index+1,then);});
    }
    /** Harvest progress describes the live area, not a count of clicks: a scythe can remove many crops. */
    private void reconcileHarvest(){
        BitSet cleared=new BitSet(),growing=new BitSet();Gob[] crops=fieldCrops();
        for(int index=0;index<field.tiles();index++){
            Gob crop=crops[index];
            if(crop==null)cleared.set(index);
            else if(preserveExisting||!profile.crop.equals(GameObjects.resourceName(crop))||!ScriptRunner.mature(crop)){cleared.set(index);growing.set(index);}
        }
        if(!cleared.equals(field.harvested)||!growing.equals(field.planted)){
            field.harvested.clear();field.harvested.or(cleared);field.planted.clear();field.planted.or(growing);book.save(gui);
        }
    }
    /** Plants of this crop that are still growing when a run starts; they are left alone, and the run says so. */
    private int growingAtStart;
    /** A field without a ripe plant has nothing to harvest. Say that, with the stages, instead of ending in silence. */
    private void reportRipeness(Gob[] crops){
        int ripe=0,growing=0,empty=0,other=0;Map<String,Integer> stages=new TreeMap<>(Comparator.reverseOrder());
        for(Gob crop:crops){
            if(crop==null){empty++;continue;}
            if(!profile.crop.equals(GameObjects.resourceName(crop))){other++;continue;}
            if(ScriptRunner.mature(crop)){ripe++;continue;}
            growing++;int[] at=ScriptRunner.stage(crop);
            stages.merge(at==null?"?":(at[0]<0?"?":Integer.toString(at[0]))+"/"+at[1],1,Integer::sum);
        }
        growingAtStart=growing;
        // What the run saw when it started, so a field that is "done" at once can be explained afterwards.
        if(gui.syslog!=null)gui.syslog.append(field.title()+" · "+L10n.format("Field census: %d ripe, %d growing, %d empty, %d other, of %d tiles.",ripe,growing,empty,other,crops.length),new Color(170,170,170));
        if(ripe==0&&growing==0&&other==0){
            String text=field.title()+" · "+L10n.format("No crops were found in this field (%d tiles empty). If plants stand there, they are not loaded yet: move closer and run again.",empty);
            Color color=new Color(235,195,95);gui.showNotice(text,color);if(gui.syslog!=null)gui.syslog.append(text,color);
            return;
        }
        if(ripe>0||growing==0)return;
        StringBuilder list=new StringBuilder();
        for(Map.Entry<String,Integer> entry:stages.entrySet()){if(list.length()>0)list.append(", ");list.append(entry.getKey()).append("\u00d7").append(entry.getValue());}
        String text=field.title()+" · "+L10n.format("No plant is ripe yet: %d still growing (stage %s). Nothing to harvest.",growing,list.toString());
        Color color=new Color(235,195,95);gui.showNotice(text,color);if(gui.syslog!=null)gui.syslog.append(text,color);
    }
    private long harvestSignature(){
        long signature=seedCount();
        for(Gob crop:fieldCrops()){
            signature=31*signature+(crop==null?0:crop.id);
            signature=31*signature+(crop!=null&&ScriptRunner.mature(crop)?1:0);
        }
        if(profile.rootCrop)for(Gob gob:objects())if(inside(gob,land)&&profile.ground.equals(GameObjects.resourceName(gob)))signature=31*signature+gob.id;
        return signature;
    }
    private void harvest(){
        if(dropHarvestCarrot(this::harvest))return;
        if(drinkIfNeeded(this::harvest))return;
        reconcileHarvest();
        int index=field.harvested.nextClearBit(0);
        if(index>=field.tiles()){
            field.phase=harvestOnly||profile.rootCrop?WheatFields.Phase.COLLECT:WheatFields.Phase.PLANT;field.pending=-1;book.save(gui);
            if(profile.rootCrop)collect();
            else depositSeeds(0,harvestOnly?this::collect:()->scanSeeds(this::plant));return;
        }
        boolean scythe=scythe();
        if(freeSlots(gui.maininv)<(scythe||profile.rootCrop?8:1)){
            if(bag(profile.seed).isEmpty())throw new IllegalStateException(scythe?"Free eight inventory slots for a scythe harvest and resume the field.":"Free an inventory slot and resume the field.");
            if(profile.rootCrop)bufferRoots(this::harvest);
            else depositSeeds(0,this::harvest);return;
        }
        if(scythe){harvestArea(index);return;}
        Coord tile=field.tile(land,index);Gob[] target={null};int[] before={0};
        long[] signature={Long.MIN_VALUE};double[] changed={0};
        await("Harvesting a crop",()->{
            Gob live=crop(tile);
            if(live==null||!ScriptRunner.mature(live))return; // A late neighbouring harvest already cleared it.
            before[0]=seedCount();target[0]=live;
            field.pending=index;book.save(gui);flower="Harvest";click(live,3);
        },()->{
            if(target[0]==null)return true;
            if(crop(tile)!=null||!profile.rootCrop&&seedCount()<=before[0]||gui.prog!=null)return false;
            long current=harvestSignature();
            if(current!=signature[0]){signature[0]=current;changed[0]=now();return false;}
            return now()-changed[0]>=.5; // Gob removals and seed packets arrive separately, including scythe neighbours.
        },45);
        add(()->{
            if(target[0]!=null)field.lastHarvest=System.currentTimeMillis();
            flower=null;field.pending=-1;book.save(gui);harvest();
        });
    }
    /** Let the server walk and reap continuously; only pause for supplies or manual interruption. */
    private void harvestArea(int index){
        boolean[] activated={false};int[] beforeSeeds={0},beforeCleared={0};
        long[] signature={Long.MIN_VALUE};double[] changed={0};
        await("Starting area harvesting",()->{
            if(gui.map.hasAreaSelection())throw new IllegalStateException("Finish the current area selection first.");
            Gob live=null;for(Gob crop:fieldCrops())if(crop!=null&&ScriptRunner.mature(crop)){live=crop;break;}
            if(live==null)return;
            beforeSeeds[0]=seedCount();beforeCleared[0]=field.harvested.cardinality();
            field.pending=index;book.save(gui);activated[0]=true;nativeHarvesting=true;harvestInterrupted=false;
            Coord at=live.rc.floor(OCache.posres);
            gui.map.wdgmsg("click",Coord.z,at,3,UI.MOD_SHIFT,0,(int)live.id,at,0,-1);
        },()->!activated[0]||harvestInterrupted||gui.map.submitServerArea(land,0),10);
        await("Harvesting the selected area",()->changed[0]=now(),()->{
            if(!activated[0])return true;
            reconcileHarvest();long current=harvestSignature();
            if(current!=signature[0]){signature[0]=current;changed[0]=now();}
            if(now()-changed[0]>60&&!harvestInterrupted)throw new IllegalStateException("Area harvesting stopped making progress. Check the field and resume.");
            boolean received=profile.rootCrop||field.harvested.cardinality()==beforeCleared[0]||seedCount()>beforeSeeds[0];
            return (harvestInterrupted||field.harvested.cardinality()==field.tiles())&&received&&gui.prog==null&&now()-changed[0]>=.5;
        },600);
        add(()->{
            if(activated[0]&&field.harvested.cardinality()>beforeCleared[0])field.lastHarvest=System.currentTimeMillis();
            nativeHarvesting=false;harvestInterrupted=false;field.pending=-1;book.save(gui);harvest();
        });
    }
    /** Only seed packets move to this explicit zone; unrelated inventory items never move. */
    private void depositSeeds(int storeIndex,Runnable then){
        if(profile.rootCrop&&dropHarvestCarrot(()->depositSeeds(storeIndex,then)))return;
        if(hand()!=null){putBack(()->depositSeeds(storeIndex,then));return;}
        List<GItem> seeds=bag(profile.seed);
        if(seeds.isEmpty()){then.run();return;}
        if(storeIndex>=stores.size())throw new IllegalStateException("The seed storage is full. Add room and resume the field.");
        open(stores.get(storeIndex),()->{
            Inventory destination=inventory();int room=seedRoom(destination);
            if(room==0){closeWindow();depositSeeds(storeIndex+1,then);return;}
            List<GItem> batch=new ArrayList<>(bag(profile.seed));
            if(batch.size()>room)batch=batch.subList(0,room);
            transferBatch(batch,destination,gui.maininv,profile.seed,()->depositSeeds(storeIndex,then));
        });
    }
    /** A bounded top-N buffer: store only the best roots for the still-empty field tiles.
     * The rest is product, including roots evicted by a better later harvest/pickup batch. */
    private void bufferRoots(Runnable then){
        if(dropHarvestCarrot(()->bufferRoots(then)))return;
        if(hand()!=null){putBack(()->bufferRoots(then));return;}
        if(harvestOnly){
            closeWindow();storeProduct(then);return;
        }
        scanSeeds(()->balanceRoots(then));
    }
    private Set<GItem> keptRoots(){
        List<Seed> ordered=new ArrayList<>(census);
        ordered.sort(Comparator.comparingDouble((Seed seed)->seed.lot.quality).reversed().thenComparing(seed->seed.store==null));
        Set<GItem> keep=Collections.newSetFromMap(new IdentityHashMap<>());
        int left=field.tiles()-field.planted.cardinality();
        for(Seed seed:ordered)if(left>0){keep.add(seed.item);left-=seed.lot.count;}
        return keep;
    }
    /** Reuse the census and open cupboard during this checkpoint. All chosen roots
     * must leave the backpack before native Shift-all product unloading is safe. */
    private void balanceRoots(Runnable then){
        rememberSeeds(null,gui.maininv);Set<GItem> keep=keptRoots();
        for(GItem item:bag(profile.seed))if(keep.contains(item)){stashRoots(0,then);return;}
        if(!bag(profile.product).isEmpty()){
            closeWindow();storeProduct(()->balanceRoots(then));return;
        }
        // N is the planting requirement, not a limit on the owner's stored stock.
        // Evict stored roots only when stashRoots needs room for a better reserve.
        // In particular, 192 equal roots in a full cupboard must stay there: unload
        // the backpack, then fetch only what planting needs.
        then.run();
    }
    private void stashRoots(int index,Runnable then){
        if(index>=stores.size())throw new IllegalStateException("The seed storage is full. Add room and resume the field.");
        Gob store=stores.get(index);
        open(store,()->{
            Inventory destination=inventory();rememberSeeds(store,destination);rememberSeeds(null,gui.maininv);
            Set<GItem> keep=keptRoots();List<GItem> chosen=new ArrayList<>(),worse=new ArrayList<>();
            for(GItem item:bag(profile.seed))if(keep.contains(item))chosen.add(item);
            if(chosen.isEmpty()){balanceRoots(then);return;}
            int room=seedRoom(destination);
            Runnable updated=()->{rememberSeeds(store,destination);balanceRoots(then);};
            if(room>0){
                transferBatch(chosen.subList(0,Math.min(room,chosen.size())),destination,gui.maininv,profile.seed,updated);return;
            }
            for(GItem item:items(destination,profile.seed))if(!keep.contains(item))worse.add(item);
            if(worse.isEmpty()){stashRoots(index+1,then);return;}
            room=seedRoom(gui.maininv);
            if(room==0){swapRoot(chosen.get(0),worse.get(0),updated);return;}
            transferBatch(worse.subList(0,Math.min(room,worse.size())),gui.maininv,destination,profile.seed,updated);
        });
    }
    private WItem rootOwner(Inventory inventory,GItem root){
        for(WItem owner:InventoryActions.items(inventory))
            if(owner.item==root||owner.item.contents!=null&&root.hasparent(owner.item.contents))return owner;
        throw new IllegalStateException("The reserved seed packet changed. Stop and resume to compare seeds again.");
    }
    /** An entirely full backpack can still exchange one chosen shell with a worse
     * stored shell, using the same native occupied-cell swaps as inventory sorting. */
    private void swapRoot(GItem better,GItem worse,Runnable then){
        WItem donor=rootOwner(gui.maininv,better),destination=rootOwner(inventory(),worse);
        Coord original=InventoryActions.cell(donor),target=InventoryActions.cell(destination);
        Inventory store=inventory();
        take(donor.item);
        await("Moving seeds between inventories",()->store.wdgmsg("drop",target),()->hand()==null||hand()!=held,5);
        await("Returning the held item to inventory",()->{if(hand()!=null){held=hand();gui.maininv.wdgmsg("drop",original);}},()->hand()==null,5);
        add(then);
    }
    /** Native explicit-destination transfers, issued together and acknowledged before the next batch. */
    private List<GItem> transferCommands(List<GItem> chosen,Widget source,Widget target,String resource){
        if(!(source instanceof Inventory)||!(target instanceof Inventory))return chosen;
        Set<GItem> selected=Collections.newSetFromMap(new IdentityHashMap<>());selected.addAll(chosen);
        List<GItem> commands=new ArrayList<>();
        for(WItem owner:InventoryActions.items((Inventory)source)){
            if(selected.contains(owner.item)){commands.add(owner.item);continue;}
            if(owner.item.contents==null)continue;
            List<GItem> members=items(owner.item.contents,resource);
            if(members.isEmpty())continue;
            if(selected.containsAll(members))commands.add(owner.item);
            else for(GItem member:members)if(selected.contains(member)){
                // Extract at most one member per partial shell. The server can replace
                // that shell and its remaining members after an extraction; replan after receipt.
                commands.add(member);break;
            }
        }
        return commands;
    }
    private void transferBatch(List<GItem> batch,Widget target,Widget source,String resource,Runnable then){
        if(batch.isEmpty()){then.run();return;}
        if(dropHarvestCarrot(()->transferBatch(batch,target,source,resource,then)))return;
        List<GItem> chosen=new ArrayList<>(batch);int targetId=target.wdgid();
        if(targetId<0)throw new IllegalStateException("The destination inventory is not ready yet.");
        int[] before={0},atTarget={0};long[] last={Long.MIN_VALUE};double[] changed={0};
        await("Moving seeds between inventories",()->{
            if(hand()!=null)throw new IllegalStateException("Empty the cursor before starting the script.");
            // GItem.addchild places stack contents in a separate ContentsWindow under
            // GameUI. They are owned by this inventory through GItem.contents, not parents.
            Set<GItem> live=Collections.newSetFromMap(new IdentityHashMap<>());live.addAll(items(source,resource));
            for(GItem item:chosen)if(!live.contains(item)||!resource.equals(item.resource().name))throw new IllegalStateException("The reserved seed packet changed. Stop and resume to compare seeds again.");
            // Resolve the whole batch before issuing any mutation.
            for(GItem item:chosen){item.info();if(resource.equals(profile.seed))lot(item);}
            List<GItem> commands=transferCommands(chosen,source,target,resource);
            if(target instanceof Inventory){
                Inventory destination=(Inventory)target;BitSet space=occupied(destination);List<GItem> fitting=new ArrayList<>();
                for(GItem command:commands){
                    Coord size=profile.seedSize;
                    if(source instanceof Inventory)for(WItem owner:InventoryActions.items((Inventory)source))if(owner.item==command)
                        size=size.max(owner.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz));
                    if(reserveSpace(destination,space,size)==null)break;
                    fitting.add(command);
                }
                commands=fitting;
            }
            if(commands.isEmpty())throw new IllegalStateException("Free space for the planting material and resume the field.");
            before[0]=countResource(source,resource);
            atTarget[0]=target instanceof ISBox?((ISBox)target).remaining():countResource(target,resource);
            for(GItem item:commands)item.wdgmsg("invxf2",0,1,targetId);
        },()->{
            if(target.parent==null||source.parent==null)throw new IllegalStateException("The seed container was closed.");
            int left=countResource(source,resource);
            int received=target instanceof ISBox?((ISBox)target).remaining():countResource(target,resource);
            long signature=31L*left+received;
            if(signature!=last[0]){last[0]=signature;changed[0]=now();}
            return left<before[0]&&received-atTarget[0]==before[0]-left&&now()-changed[0]>=.5;
        },15);
        add(then);
    }
    private void putBack(Runnable then){
        if(hand()==null){then.run();return;}
        if(hand()!=held)throw new IllegalStateException("The cursor is holding another item.");
        Coord size=gui.vhand.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
        if(profile.seed.equals(hand().resource().name)||hand().contents!=null&&!items(hand().contents,profile.seed).isEmpty())size=size.max(profile.seedSize);
        Coord cell=free(gui.maininv,size);if(cell==null)throw new IllegalStateException("Free space for the planting material and resume the field.");
        await("Returning the held item to inventory",()->gui.maininv.wdgmsg("drop",cell),()->hand()==null,5);add(then);
    }
    private void plant(){
        if(hand()!=null){if(!profile.seed.equals(hand().resource().name))throw new IllegalStateException("The cursor is holding another item.");held=hand();putBack(this::plant);return;}
        if(drinkIfNeeded(this::plant))return;
        if(field.harvested.cardinality()!=field.tiles())throw new IllegalStateException("Finish harvesting the whole field before planting.");
        int index=field.planted.nextClearBit(0);
        if(index>=field.tiles()){
            putBack(()->{field.phase=WheatFields.Phase.COLLECT;field.pending=-1;book.save(gui);depositSeeds(0,this::collect);});return;
        }
        rememberSeeds(null,gui.maininv);
        List<Seed> wanted=plantingSeeds();
        if(wanted.isEmpty()){
            plantingExhausted=true;field.phase=WheatFields.Phase.COLLECT;field.pending=-1;book.save(gui);
            depositSeeds(0,this::collect);return;
        }
        if(!checkPlantingQuality(wanted))return;
        List<GItem> loaded=bag(profile.seed);loaded.sort(Comparator.comparingDouble((GItem item)->lot(item).quality).reversed());
        GItem best=null;for(GItem item:loaded)if(lot(item).count>=profile.seedsPerTile){best=item;break;}
        if(best==null&&!loadingSeeds){
            loadingSeeds=true;depositSeeds(0,()->scanSeeds(this::plant));return;
        }
        if(loadingSeeds){
            int room=seedRoom(gui.maininv);
            List<Seed> fetch=new ArrayList<>();
            for(Seed seed:wanted)if(seed.store!=null&&fetch.size()<room)fetch.add(seed);
            if(!fetch.isEmpty()){
                Gob source=fetch.get(0).store;
                open(source,()->{
                    rememberSeeds(source,inventory());rememberSeeds(null,gui.maininv);
                    List<GItem> batch=new ArrayList<>();int slots=seedRoom(gui.maininv),chosen=0;
                    for(Seed seed:plantingSeeds())if(seed.store!=null&&chosen++<slots&&seed.store==source)batch.add(seed.item);
                    if(batch.isEmpty()){plant();return;}
                    Inventory from=inventory();
                    transferBatch(batch,gui.maininv,from,profile.seed,()->{rememberSeeds(source,from);plant();});
                });return;
            }
            if(best==null)throw new IllegalStateException("Free an inventory slot and resume the field.");
            loadingSeeds=false;closeWindow();
        }
        // Homogeneous inventory permits native refill across all packets. For mixed qualities,
        // bound the section to the chosen packet so the server never has to guess the next quality.
        WheatFields.Lot highest=lot(best);boolean uniform=true;int capacity=0;
        for(GItem item:loaded){WheatFields.Lot packet=lot(item);uniform&=packet.quality==highest.quality;capacity+=packet.count/profile.seedsPerTile;}
        // Every carried root has already survived the global top-N selection. Its native
        // refill order may vary safely; all of this load is reserved for this planting.
        Area section=plantingSection(uniform||profile.rootCrop?capacity:highest.count/profile.seedsPerTile);
        if(!section.contains(gui.map.player().rc.floor(MCache.tilesz))){walkToPlant(section,this::plant);return;}
        sow(best,section);
    }
    private boolean checkPlantingQuality(List<Seed> wanted){
        double minimum=Double.POSITIVE_INFINITY,maximum=-1;
        for(Seed seed:wanted){minimum=Math.min(minimum,seed.lot.quality);maximum=Math.max(maximum,seed.lot.quality);}
        double record=qualityCheck.betterRecord(profile,maximum);if(record<0)return true;
        qualityCheck.report(gui,field.title(),profile,minimum,maximum,record);
        if(!FieldQualityCheck.confirmation()||qualityCheck.approved(minimum,record))return true;
        final double floor=minimum;
        qualityPrompt=gui.add(new FieldQualityCheck.Prompt(field.title(),profile,minimum,maximum,record,proceed->{
            qualityPrompt=null;
            if(proceed)qualityCheck.approve(floor,record);else ScriptRunner.cancel(gui);
        }));
        Coord size=hearthborn.ui.HudInterfaceScale.size(qualityPrompt);
        qualityPrompt.move(gui.sz.sub(size).div(2).max(Coord.z));qualityPrompt.raise();gui.setfocus(qualityPrompt);
        // No server timeout while the player is deciding. Re-read live stock after approval.
        add(this::plant);return false;
    }
    /** Approach with an empty cursor before the native planting selector takes control. */
    void walkToPlant(Area section,Runnable then){
        Coord player=gui.map.player().rc.floor(MCache.tilesz);
        Coord tile=Coord.of(Math.max(section.ul.x,Math.min(player.x,section.br.x-1)),Math.max(section.ul.y,Math.min(player.y,section.br.y-1)));
        Coord2d goal=new Coord2d(tile).add(.5,.5).mul(MCache.tilesz);
        await("Walking to the field",()->{
            Object[] click={Coord.z,goal.floor(OCache.posres),1,Walking.enabled()?0:UI.MOD_CTRL};
            if(!Walking.startScript(gui.map,goal,click))throw new IllegalStateException("Cannot start a route to this field right now.");
        },()->{
            if(paused){
                Walking.cancel(gui.map);
                gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);
                return true;
            }
            Walking.tickScript(gui.map);
            if(Walking.scriptRunning(gui.map))return false;
            if(!section.contains(gui.map.player().rc.floor(MCache.tilesz)))
                throw new IllegalStateException("Could not reach the field. Check the route and resume.");
            return true;
        },120);
        // Re-read live cells and seed widgets after arriving or resuming a paused approach.
        add(then);
    }
    private void rememberSeeds(Gob store,Widget owner){
        List<Seed> found=new ArrayList<>();for(GItem item:items(owner,profile.seed))found.add(new Seed(store,item));
        census.removeIf(seed->seed.store==store);census.addAll(found);
    }
    private List<Seed> plantingSeeds(){
        List<WheatFields.Lot> lots=new ArrayList<>();for(Seed seed:census)lots.add(seed.lot);
        List<WheatFields.Lot> reserved=WheatFields.reserveAvailable(lots,field.tiles()-field.planted.cardinality(),profile.seedsPerTile);
        int count=0;for(WheatFields.Lot lot:reserved)count+=lot.count;
        reserveStatus=reserved.isEmpty()?L10n.tr("No planting material remains.")
            :L10n.format("Seeds reserved: %d · quality %.1f–%.1f",count,reserved.get(reserved.size()-1).quality,reserved.get(0).quality);
        List<Seed> ordered=new ArrayList<>(census),result=new ArrayList<>();
        ordered.sort(Comparator.comparingDouble((Seed seed)->seed.lot.quality).reversed().thenComparing(seed->seed.store!=null));
        int left=field.tiles()-field.planted.cardinality();
        for(Seed seed:ordered){int plants=Math.min(left,seed.lot.count/profile.seedsPerTile);if(plants>0){result.add(seed);left-=plants;}}
        return result;
    }
    /** Largest initial complete rows that fit the loaded quality batch, otherwise an initial row segment. */
    private Area plantingSection(int capacity){
        int first=field.planted.nextClearBit(0),row=first/field.land.width,rows=0,used=0;
        for(int y=row;y<field.land.height;y++){
            int needed=0;for(int x=0;x<field.land.width;x++)if(!field.planted.get(y*field.land.width+x))needed++;
            if(used+needed>capacity)break;used+=needed;rows++;
        }
        if(rows>0)return Area.sized(land.ul.add(0,row),Coord.of(field.land.width,rows));
        int start=first%field.land.width,end=start;
        while(end<field.land.width){if(!field.planted.get(row*field.land.width+end)&&--capacity<0)break;end++;}
        return new Area(land.ul.add(start,row),land.ul.add(end,row+1));
    }
    private int carriedSeeds(){return seedCount()+(hand()!=null&&profile.seed.equals(hand().resource().name)?GameObjects.count(hand()):0);}
    private Map<Double,Integer> carriedSeedQualities(){
        Map<Double,Integer> result=new HashMap<>();
        for(GItem item:bag(profile.seed)){WheatFields.Lot lot=lot(item);result.merge(lot.quality,lot.count,Integer::sum);}
        if(hand()!=null&&profile.seed.equals(hand().resource().name)){
            WheatFields.Lot lot=lot(hand());result.merge(lot.quality,lot.count,Integer::sum);
        }
        return result;
    }
    private void sow(GItem seed,Area section){
        BitSet expected=new BitSet();int[] before={0};long[] last={Long.MIN_VALUE};double[] changed={0};
        Map<Double,Integer> beforeQuality=new HashMap<>();
        boolean[] activated={false};
        await("Starting area planting",()->{
            if(gui.map.hasAreaSelection())throw new IllegalStateException("Finish the current area selection first.");
            expected.clear();
            Gob[] crops=fieldCrops();
            for(Coord tile:section){int index=(tile.y-land.ul.y)*field.land.width+tile.x-land.ul.x;
                if(crops[index]==null)expected.set(index);
                else field.planted.set(index);
            }
            if(!bag(profile.seed).contains(seed)||lot(seed).count<profile.seedsPerTile)throw new IllegalStateException("The reserved seed packet changed. Stop and resume to compare seeds again.");
            before[0]=carriedSeeds();field.pending=expected.nextSetBit(0);book.save(gui);
            if(expected.isEmpty())return;
            beforeQuality.clear();beforeQuality.putAll(carriedSeedQualities());
            Set<Double> plantingQualities=new HashSet<>();
            for(GItem item:bag(profile.seed))if(profile.rootCrop||item==seed)plantingQualities.add(lot(item).quality);
            List<WheatFields.Lot> stock=new ArrayList<>();
            for(Seed available:census)if(plantingQualities.contains(available.lot.quality))stock.add(available.lot);
            FieldQualityCheck.recordPlanting(profile,stock);
            activated[0]=true;
            nativePlanting=true;plantInterrupted=false;seed.wdgmsg("iact",Coord.z,UI.MOD_SHIFT);
        },()->!activated[0]||plantInterrupted||gui.map.submitServerArea(section,0),10);
        await("Planting the selected area",()->changed[0]=now(),()->{
            Gob[] crops=fieldCrops();int planted=0;BitSet actual=(BitSet)field.planted.clone();
            for(int index=expected.nextSetBit(0);index>=0;index=expected.nextSetBit(index+1))
                if(crops[index]!=null&&profile.crop.equals(GameObjects.resourceName(crops[index]))&&!ScriptRunner.mature(crops[index])){actual.set(index);planted++;}
            if(!actual.equals(field.planted)){
                plantedTiles+=actual.cardinality()-field.planted.cardinality();
                field.planted.clear();field.planted.or(actual);book.save(gui);
            }
            if(hand()!=null){if(!profile.seed.equals(hand().resource().name))throw new IllegalStateException("The cursor is holding another item.");held=hand();}
            int used=before[0]-carriedSeeds();long signature=31L*planted+used;
            if(last[0]!=signature){last[0]=signature;changed[0]=now();}
            if(now()-changed[0]>30&&!plantInterrupted)throw new IllegalStateException("Area planting stopped making progress. Check the field and resume.");
            boolean finished=(plantInterrupted||planted==expected.cardinality())&&used==planted*profile.seedsPerTile&&gui.prog==null&&now()-changed[0]>=.5;
            if(finished&&planted>0){
                Map<Double,Integer> remaining=carriedSeedQualities();
                double minimum=Double.POSITIVE_INFINITY,maximum=0;int consumed=0;boolean gained=false;
                for(Map.Entry<Double,Integer> entry:remaining.entrySet())if(entry.getValue()>beforeQuality.getOrDefault(entry.getKey(),0))gained=true;
                for(Map.Entry<Double,Integer> entry:beforeQuality.entrySet()){
                    int count=entry.getValue()-remaining.getOrDefault(entry.getKey(),0);
                    if(count>0){consumed+=count;minimum=Math.min(minimum,entry.getKey());maximum=Math.max(maximum,entry.getKey());}
                }
                if(!gained&&consumed==used){
                    for(Map.Entry<Double,Integer> entry:beforeQuality.entrySet()){
                        int count=entry.getValue()-remaining.getOrDefault(entry.getKey(),0);
                        if(count>0){sownQuality+=entry.getKey()*count;sownSeeds+=count;sownLowest=Math.min(sownLowest,entry.getKey());sownHighest=Math.max(sownHighest,entry.getKey());}
                    }
                    BitSet known=(BitSet)expected.clone();known.and(actual);
                    field.notePlantingQuality(known,minimum,maximum);book.save(gui);
                }
            }
            return finished;
        },300);
        add(()->{nativePlanting=false;plantInterrupted=false;field.pending=-1;book.save(gui);plant();});
    }
    /** Native bulk pickup leaves its last item on the cursor when the pack fills up. It is this crop's own harvest, so adopt it. */
    private boolean adoptCursorProduct(){
        if(hand()==null||hand()==held||profile.rootCrop||!profile.product.equals(hand().resource().name))return false;
        held=hand();return true;
    }
    private boolean carryingProduct(){return hand()!=null&&hand()==held&&profile.product.equals(hand().resource().name);}
    private void collect(){
        if(profile.rootCrop){collectRoots();return;}
        if(adoptCursorProduct()){storeProduct();return;}
        if(drinkIfNeeded(this::collect))return;
        List<GItem> straw=bag(profile.product);Gob target=null;
        for(Gob gob:objects())if((inside(gob,land)||inside(gob,productArea))&&profile.dropsOnGround(GameObjects.resourceName(gob))&&!unreachable.contains(gob.id)
            &&(target==null||gob.rc.dist(gui.map.player().rc)<target.rc.dist(gui.map.player().rc)))target=gob;
        if(target==null){
            if(!extrasInBag().isEmpty()){depositExtras(0,this::collect);return;}
            reportUnreachable();if(straw.isEmpty())finishSeeds();else storeProduct();return;
        }
        if(free(gui.maininv)==null){
            if(!extrasInBag().isEmpty()){depositExtras(0,this::collect);return;}
            if(straw.isEmpty())throw new IllegalStateException("Free an inventory slot and resume the field.");storeProduct();return;
        }
        pickup(target,this::collect);
    }
    /** Roots on the ground take part in quality selection before any planting starts. */
    private void collectRoots(){
        if(dropHarvestCarrot(this::collectRoots))return;
        if(drinkIfNeeded(this::collectRoots))return;
        Gob target=null;
        for(Gob gob:objects())if((inside(gob,land)||inside(gob,productArea))&&profile.ground.equals(GameObjects.resourceName(gob))&&!unreachable.contains(gob.id)
            &&(target==null||gob.rc.dist(gui.map.player().rc)<target.rc.dist(gui.map.player().rc)))target=gob;
        if(target==null){
            reportUnreachable();
            bufferRoots(()->{
                if(!harvestOnly&&!plantingExhausted&&field.planted.cardinality()<field.tiles()){
                    field.phase=WheatFields.Phase.PLANT;field.pending=-1;book.save(gui);plant();
                }else finishSeeds();
            });return;
        }
        if(seedRoom(gui.maininv)<2){
            if(bag(profile.seed).isEmpty())throw new IllegalStateException("Free an inventory slot and resume the field.");
            bufferRoots(this::collectRoots);return;
        }
        pickup(target,this::collectRoots);
    }
    /** What the harvest dropped besides the main product (poppy pods), as carried in the pack. */
    private List<GItem> extrasInBag(){
        List<GItem> found=new ArrayList<>();
        for(String resource:profile.extraItems())found.addAll(bag(resource));
        return found;
    }
    /** The extra drops go to the seed storage, which the field already has, one container after another. */
    private void depositExtras(int storeIndex,Runnable then){
        if(hand()!=null){putBack(()->depositExtras(storeIndex,then));return;}
        List<GItem> loose=extrasInBag();
        if(loose.isEmpty()){then.run();return;}
        if(storeIndex>=stores.size())throw new IllegalStateException("The seed storage is full. Add room and resume the field.");
        String resource=loose.get(0).resource().name;
        open(stores.get(storeIndex),()->{
            Inventory destination=inventory();int room=freeSlots(destination);
            if(room==0){closeWindow();depositExtras(storeIndex+1,then);return;}
            List<GItem> batch=new ArrayList<>(bag(resource));
            if(batch.size()>room)batch=batch.subList(0,room);
            transferBatch(batch,destination,gui.maininv,resource,()->depositExtras(storeIndex,then));
        });
    }
    private boolean bulkPickup=true;
    /** Ground items the character could not get to (an object sticks out into the way); they are skipped, not waited for. */
    private final Set<Long> unreachable=new HashSet<>();
    private boolean reportedUnreachable;
    /** Nothing moves and nothing is picked up for this long: the way to the item is blocked. */
    private static final double STALL=10;
    private void giveUp(Gob item){unreachable.add(item.id);}
    /** A stall with a full pack is just the pack: the harvest is stored first, and the item stays wanted. */
    private void giveUpIfRoom(Gob item){if(free(gui.maininv)!=null)giveUp(item);}
    private void reportUnreachable(){
        if(reportedUnreachable||unreachable.isEmpty())return;
        int left=0;for(Gob gob:objects())if(unreachable.contains(gob.id)&&!gob.removed&&profile.ground.equals(GameObjects.resourceName(gob)))left++;
        if(left==0)return;
        reportedUnreachable=true;
        gui.showErrorNotice(field.title()+": "+L10n.format("Could not reach %d items on the ground, something blocks the way. They are skipped.",left));
    }
    private List<Gob> groundItems(Area zone){
        List<Gob> found=new ArrayList<>();
        for(Gob gob:objects())if(profile.ground.equals(GameObjects.resourceName(gob))&&inside(gob,zone))found.add(gob);
        return found;
    }
    private void stopPickup(){
        gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);gui.map.cancelServerArea();
    }
    /** The server may ask for an area after a Shift click; give it the tiles that hold the harvest. */
    private boolean submitPickupArea(Area zone){
        List<Gob> items=groundItems(zone);if(items.isEmpty())return true;
        Coord ul=items.get(0).rc.floor(MCache.tilesz),br=ul;
        for(Gob item:items){Coord tile=item.rc.floor(MCache.tilesz);ul=Coord.of(Math.min(ul.x,tile.x),Math.min(ul.y,tile.y));br=Coord.of(Math.max(br.x,tile.x),Math.max(br.y,tile.y));}
        try{return gui.map.submitServerArea(new Area(ul,br.add(1,1)),0);}
        catch(IllegalStateException tooLarge){return false;}
    }
    /** Shift + right-click on a ground item makes the server walk and collect the whole harvest quickly,
     * instead of one click per item. A server that does not collect in bulk still picks the clicked item,
     * and a stalled or refused bulk pickup falls back to single clicks for the rest of the run. */
    private void pickupAll(Gob chosen,Runnable then){
        approachObject(chosen,"Walking to the harvest",()->{
            if(chosen.removed||!objects().contains(chosen)){then.run();return;}
            Area zone=inside(chosen,land)?land:productArea;
            long[] signature={Long.MIN_VALUE};double[] changed={0};boolean[] selected={false},stopped={false};
            await("Picking up the harvest",()->{
                changed[0]=now();
                Coord at=chosen.rc.floor(OCache.posres);
                gui.map.wdgmsg("click",Coord.z,at,3,UI.MOD_SHIFT,0,(int)chosen.id,at,0,-1);
            },()->{
                if(paused){stopPickup();return true;}
                if(!selected[0]&&gui.map.hasAreaSelection()){
                    selected[0]=true;
                    if(!submitPickupArea(zone)){bulkPickup=false;stopPickup();return true;}
                }
                long current=31L*groundItems(zone).size()+countResource(gui.maininv,profile.product)+gui.map.player().rc.floor(OCache.posres).hashCode();
                if(current!=signature[0]){signature[0]=current;changed[0]=now();}
                if(gui.prog!=null||gui.map.hasAreaSelection())return false;
                // A full pack, or nothing left on the ground, ends the server's gathering walk. Cancel it and let the
                // character stand still, or it keeps walking and overrides the next route to the stockpile.
                if(!stopped[0]&&(free(gui.maininv)==null||groundItems(zone).isEmpty())){stopped[0]=true;stopPickup();changed[0]=now();signature[0]=Long.MIN_VALUE;return false;}
                if(now()-changed[0]>=.8&&(chosen.removed||!objects().contains(chosen)))return true;
                if(now()-changed[0]>STALL){
                    // Stuck against something: leave the item nearest to the character and go on one by one.
                    Gob stuck=null;
                    for(Gob item:groundItems(zone))if(stuck==null||item.rc.dist(gui.map.player().rc)<stuck.rc.dist(gui.map.player().rc))stuck=item;
                    if(stuck!=null)giveUpIfRoom(stuck);
                    bulkPickup=false;stopPickup();return true;
                }
                return false;
            },120);
            add(then);
        },()->{giveUp(chosen);add(then);});
    }
    private void pickup(Gob chosen,Runnable then){
        if(bulkPickup&&!profile.rootCrop&&profile.ground.equals(GameObjects.resourceName(chosen))){pickupAll(chosen,then);return;}
        String carried=profile.carried(GameObjects.resourceName(chosen));
        approachObject(chosen,"Walking to the harvest",()->{
            if(chosen.removed||!objects().contains(chosen)){then.run();return;}
            int before=countResource(gui.maininv,carried);
            long[] signature={Long.MIN_VALUE};double[] changed={0};
            await("Picking up the harvest",()->{changed[0]=now();click(chosen,3);},()->{
                if(!objects().contains(chosen)&&countResource(gui.maininv,carried)>before)return true;
                long current=31L*countResource(gui.maininv,carried)+gui.map.player().rc.floor(OCache.posres).hashCode();
                if(current!=signature[0]||gui.prog!=null){signature[0]=current;changed[0]=now();}
                if(now()-changed[0]>STALL){giveUpIfRoom(chosen);return true;}
                return false;
            },30);
            add(then);
        },()->{giveUp(chosen);add(then);});
    }
    private static int countResource(Widget owner,String resource){int count=0;for(GItem item:items(owner,resource))count+=GameObjects.count(item);return count;}
    private void storeProduct(){
        storeProduct(this::collect);
    }
    private void storeProduct(Runnable then){
        adoptCursorProduct();
        List<GItem> straw=bag(profile.product);if(straw.isEmpty()&&!carryingProduct()){closeWindow();then.run();return;}
        Gob target=null;
        for(Gob gob:objects())if(inside(gob,productArea)&&profile.pile.equals(GameObjects.resourceName(gob))&&!isFull(gob)
            &&(target==null||gob.rc.dist(gui.map.player().rc)<target.rc.dist(gui.map.player().rc)))target=gob;
        if(target==null){
            // Out of room for a new pile: a pile remembered as full may have been emptied since, so look once.
            Gob again=null;
            if(!pileSiteAvailable())for(Gob gob:objects())
                if(inside(gob,productArea)&&profile.pile.equals(GameObjects.resourceName(gob))&&remembered().contains(gob.id)&&!fullPiles.contains(gob.id)
                    &&(again==null||gob.rc.dist(gui.map.player().rc)<again.rc.dist(gui.map.player().rc)))again=gob;
            if(again!=null){Gob look=again;recheckPile(look,()->storeProduct(then));return;}
            createPile(straw.isEmpty()?null:straw.get(0),then);return;
        }
        Gob chosen=target;approachPile(chosen,()->fillPile(chosen,then));
    }
    private boolean pileSiteAvailable(){
        try{pilePosition();return true;}catch(IllegalStateException none){return false;}
    }
    /** Opens a pile that was remembered as full: it is forgotten if it has room again, kept as full otherwise. */
    private void recheckPile(Gob pile,Runnable then){
        open(pile,()->{
            ISBox box=opened.findchild(ISBox.class);if(box==null)throw new Loading();
            boolean usable=profile.product.equals(box.resource().name)&&box.remaining()<box.capacity();
            closeWindow();
            if(usable)remembered().remove(pile.id);else fullPiles.add(pile.id);
            then.run();
        });
    }
    private void approachPile(Gob pile,Runnable then){
        if(pile.removed)throw new IllegalStateException("A seed container or stockpile disappeared. Check the saved areas.");
        approachObject(pile,"Walking to the stockpile",then);
    }
    private void approachObject(Gob target,String stage,Runnable then){approachObject(target,stage,then,null);}
    /** With a {@code blocked} action, a route that cannot be made or ends short runs it instead of stopping the field. */
    private void approachObject(Gob target,String stage,Runnable then,Runnable blocked){
        if(target.removed||!objects().contains(target)){add(then);return;}
        if(gui.map.player().rc.dist(target.rc)<=MCache.tilesz.x){then.run();return;}
        walkToSpot(target.rc,target,stage,()->approachObject(target,stage,then,blocked),blocked);
    }
    private void walkToSpot(Coord2d goal,Gob interaction,String stage,Runnable then){
        walkToSpot(goal,interaction,stage,then,null);
    }
    private void walkToSpot(Coord2d goal,Gob interaction,String stage,Runnable then,Runnable blocked){
        boolean[] failed={false};
        await(stage,()->{
            Object[] click={Coord.z,goal.floor(OCache.posres),1,Walking.enabled()?0:UI.MOD_CTRL};
            if(!Walking.startScript(gui.map,goal,click,interaction)){
                if(blocked!=null){failed[0]=true;return;}
                throw new IllegalStateException("Cannot start a route to the destination. Check access and resume.");
            }
        },()->{
            if(failed[0])return true;
            if(paused){Walking.cancel(gui.map);gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0);return true;}
            Walking.tickScript(gui.map);if(Walking.scriptRunning(gui.map))return false;
            double reach=interaction==null?2:MCache.tilesz.x;
            if(gui.map.player().rc.dist(goal)>reach){
                if(blocked!=null){failed[0]=true;return true;}
                throw new IllegalStateException("Could not reach the destination. Check access and resume.");
            }
            return true;
        },120);
        add(()->{if(failed[0])blocked.run();else then.run();});
    }
    /** Native Shift item-interaction deposits held harvest and matching inventory items.
     * An empty-hand Shift click withdraws from the pile instead. */
    private void fillPile(Gob chosen,Runnable then){
        if(hand()==null){
            if(seedRoom(gui.maininv)==0){
                open(chosen,()->{
                    ISBox box=opened.findchild(ISBox.class);if(box==null)throw new Loading();
                    if(!profile.product.equals(box.resource().name)){markFull(chosen);closeWindow();storeProduct(then);return;}
                    boolean full=box.remaining()>=box.capacity();closeWindow();
                    if(full){markFull(chosen);storeProduct(then);}
                    else {take(bag(profile.product).get(0));add(()->fillPile(chosen,then));}
                });return;
            }
            take(bag(profile.product).get(0));add(()->fillPile(chosen,then));return;
        }
        int[] before={0},last={-1};double[] started={0},changed={0};
        await("Filling the stockpile",()->{
            if(!inside(chosen,productArea)||!profile.pile.equals(GameObjects.resourceName(chosen)))
                throw new IllegalStateException("A seed container or stockpile disappeared. Check the saved areas.");
            before[0]=carriedProduct();started[0]=changed[0]=now();
            if(hand()!=held||hand()==null||!profile.product.equals(hand().resource().name))throw new IllegalStateException("The cursor is holding another item.");
            use(chosen,UI.MOD_SHIFT);
        },()->{
            int left=carriedProduct();
            if(left!=last[0]){last[0]=left;changed[0]=now();}
            if(left<before[0])return gui.prog==null&&now()-changed[0]>=.75;
            // No receipt can mean either a full pile or an unreachable one. Inspect rather than guess.
            return now()-started[0]>=30||gui.prog==null&&gui.map.player().rc.dist(chosen.rc)<=MCache.tilesz.x*2&&now()-changed[0]>=2;
        },45);
        add(()->putBack(()->{
            if(bag(profile.product).isEmpty()){closeWindow();then.run();}
            else inspectPile(chosen,before[0],then);
        }));
    }
    /** The exact remaining capacity is server data in ISBox; a closed gob does not provide it. */
    private void inspectPile(Gob chosen,int before,Runnable then){
        open(chosen,()->{
            ISBox box=opened.findchild(ISBox.class);if(box==null)throw new Loading();
            if(!profile.product.equals(box.resource().name)){markFull(chosen);closeWindow();storeProduct(then);return;}
            if(box.remaining()>=box.capacity()){markFull(chosen);closeWindow();storeProduct(then);return;}
            if(countResource(gui.maininv,profile.product)>=before)
                throw new IllegalStateException("Could not transfer the harvest to the stockpile. Check access and resume.");
            closeWindow();fillPile(chosen,then);
        });
    }
    private boolean pileGroundSafe(Coord tile){
        if(land.contains(tile)||!productArea.contains(tile))return false;
        if(!profile.rootCrop)return true;
        MCache map=gui.ui.sess.glob.map;Resource floor=map.tilesetr(map.gettile(tile));
        if(floor==null)throw new Loading();
        return !floor.name.equals("gfx/tiles/field")&&!floor.name.equals("gfx/tiles/plowed");
    }
    /** Every second tile leaves an aisle. Ground-use of a root must never target farmland. */
    Coord2d pilePosition(){
        List<java.awt.geom.Area> obstacles=ObjectGeometry.hitboxes(objects(),gui.map.player());
        // The aisle grid first; when it is used up, any other free tile of the area.
        for(int pass=0;pass<2;pass++)
            for(int y=productArea.ul.y;y<productArea.br.y;y++)for(int x=productArea.ul.x;x<productArea.br.x;x++){
                boolean grid=(x-productArea.ul.x)%2==0&&(y-productArea.ul.y)%2==0;
                if(grid!=(pass==0))continue;
                Coord2d at=pileSite(x,y,obstacles);
                if(at!=null)return at;
            }
        throw new IllegalStateException("The harvest stockpile area is full or blocked. Make room and resume.");
    }
    private Coord2d pileSite(int x,int y,List<java.awt.geom.Area> obstacles){
        if(blockedPileSites.contains(Coord.of(x,y)))return null;
        if(!pileGroundSafe(Coord.of(x,y)))return null;
        Coord2d at=Coord2d.of(x+.5,y+.5).mul(MCache.tilesz);
        Rectangle2D footprint=new Rectangle2D.Double(at.x-5,at.y-5,10,10);
        for(java.awt.geom.Area obstacle:obstacles)if(obstacle.intersects(footprint))return null;
        for(Gob gob:objects())if(inside(gob,productArea)&&profile.pile.equals(GameObjects.resourceName(gob))&&gob.rc.dist(at)<MCache.tilesz.x)return null;
        return at;
    }
    private void createPile(GItem straw,Runnable then){
        waitingFor="Choosing stockpile ground";
        Coord2d position,standing;
        do{
            position=pilePosition();standing=pileStandingPosition(position);
            if(standing==null)blockedPileSites.add(position.floor(MCache.tilesz));
        }while(standing==null);
        final Coord2d site=position,approach=standing;
        if(gui.map.player().rc.dist(standing)>2){
            walkToSpot(standing,null,"Walking to new stockpile ground",()->{
                if(gui.map.player().rc.dist(approach)>2)createPile(straw,then);
                else placePile(straw,site,then);
            },()->{
                blockedPileApproaches.add(approach.floor(OCache.posres));
                Coord tile=site.floor(MCache.tilesz);
                if(pileSiteRouteFailures.merge(tile,1,Integer::sum)>=3)blockedPileSites.add(tile);
                if(++failedPileWalks>=8)throw new IllegalStateException("Could not reach the destination. Check access and resume.");
                createPile(straw,then);
            });return;
        }
        placePile(straw,position,then);
    }
    /** Keep the character outside the new pile, on a clear side with an unobstructed short approach. */
    private Coord2d pileStandingPosition(Coord2d position){
        List<java.awt.geom.Area> obstacles=ObjectGeometry.obstacles(objects(),gui.map.player());
        Coord2d best=null;double radius=MCache.tilesz.x*1.5;
        for(int i=0;i<16;i++){
            double angle=i*Math.PI/8;Coord2d at=position.add(Math.cos(angle)*radius,Math.sin(angle)*radius);boolean clear=true;
            if(blockedPileApproaches.contains(at.floor(OCache.posres)))continue;
            for(java.awt.geom.Area obstacle:obstacles)for(int n=0;n<=10;n++){
                Coord2d p=at.add(position.sub(at).mul(n/10.0));
                if(obstacle.contains(p.x,p.y)){clear=false;break;}
            }
            if(clear&&(best==null||gui.map.player().rc.dist(at)<gui.map.player().rc.dist(best)))best=at;
        }
        return best;
    }
    private void placePile(GItem straw,Coord2d position,Runnable then){
        failedPileWalks=0;
        Set<Long> before=new HashSet<>();for(Gob gob:objects())before.add(gob.id);
        closeWindow();
        Gob[] created={null};int[] carried={0},last={-1};double[] changed={0};
        if(!carryingProduct())take(straw);
        await("Activating stockpile placement with the held item",()->{
            if(hand()!=held||!profile.product.equals(held.resource().name))throw new IllegalStateException("The cursor is holding another item.");
            if(!pileGroundSafe(position.floor(MCache.tilesz)))throw new IllegalStateException("Choose an unplowed harvest stockpile area outside the field.");
            // A normal held-item RMB activates the preview; Shift applies to the
            // subsequent placement LMB which deposits all matching inventory roots.
            ownsPlacement=true;gui.map.wdgmsg("itemact",Coord.z,position.floor(OCache.posres),0);
        },()->profile.pile.equals(gui.map.placementResource()),10);
        await("Placing and filling a new stockpile",()->{
            carried[0]=carriedProduct();
            gui.map.wdgmsg("place",position.floor(OCache.posres),0,1,UI.MOD_SHIFT);
        },()->{
            for(Gob gob:objects())if(!gob.removed&&!before.contains(gob.id)&&profile.pile.equals(GameObjects.resourceName(gob))&&gob.rc.dist(position)<3){created[0]=gob;break;}
            int left=carriedProduct();
            if(left!=last[0]){last[0]=left;changed[0]=now();}
            return created[0]!=null&&left<carried[0]&&gui.prog==null&&now()-changed[0]>=.75;
        },15);
        await("Finishing stockpile placement",()->{
            if(gui.map.placementResource()!=null)gui.map.wdgmsg("place",position.floor(OCache.posres),0,3,0);
        },()->gui.map.placementResource()==null,10);
        add(()->{
            ownsPlacement=false;
            putBack(()->{if(!bag(profile.product).isEmpty())inspectPile(created[0],carried[0],then);else then.run();});
        });
    }
    private int carriedProduct(){return countResource(gui.maininv,profile.product)+(hand()!=null&&profile.product.equals(hand().resource().name)?GameObjects.count(hand()):0);}
    /** The quality of the seeds planted in this run: one figure when they were all alike, otherwise the average. */
    String sownQualityNote(){
        if(sownSeeds<=0)return "";
        if(sownLowest==sownHighest)return " "+L10n.format("Seed quality: %s.",number(sownLowest));
        return " "+L10n.format("Average seed quality: %s.",number(sownQuality/sownSeeds));
    }
    private static String number(double value){return Math.abs(value-Math.rint(value))<.05?String.valueOf((long)Math.rint(value)):String.format(java.util.Locale.ROOT,"%.1f",value);}
    private void finishSeeds(){
        field.phase=WheatFields.Phase.STORE;book.save(gui);
        depositSeeds(0,()->scanSeeds(()->{
            int seeds=0;for(Seed lot:census)seeds+=lot.lot.count;
            reserveStatus=L10n.format("Seeds in storage: %d · next planting needs %d",seeds,field.tiles()*profile.seedsPerTile);
            int remaining=field.tiles()-field.planted.cardinality();boolean partial=!harvestOnly&&remaining>0;
            field.phase=WheatFields.Phase.DONE;book.save(gui);complete=true;
            String message=field.title()+" · "+(partial
                ?L10n.format("Field processing completed: planting material ran out. Planted tiles: %d. Empty tiles remaining: %d.",plantedTiles,remaining)
                :L10n.format("Field processing completed. Planted tiles: %d.",plantedTiles));
            message+=sownQualityNote();
            if(growingAtStart>0)message+=" "+L10n.format("Plants still growing: %d.",growingAtStart);
            notice(message);if(gui.syslog!=null)gui.syslog.append(message,Color.WHITE);
            // The run can take minutes, and the player is often looking elsewhere: say so out loud.
            gui.ui.sfx(UI.InfoMessage.sfx);
        }));
    }
    private double stamina(){IMeter meter=gui.meter("stam");return meter==null||meter.meters().isEmpty()?-1:meter.meters().get(0).a;}
    private boolean drinkIfNeeded(Runnable then){
        int threshold=AutoDrink.below();double before=stamina();
        if(threshold==0||before<0||before>threshold/100.0)return false;
        DrinkSupply supply=DrinkSupply.collect(gui);
        if(supply.loading)throw new Loading();
        if(supply.water+supply.tea<=0)throw new IllegalStateException("Not enough drinking water. Refill and resume the field.");
        if(hand()!=null){putBack(()->scanSeeds(()->{if(!drinkIfNeeded(then))then.run();}));return true;}
        await("Drinking",()->AutoDrink.drink(gui),()->gui.prog!=null||stamina()>before+.001,15);
        await("Drinking",()->{},()->gui.prog==null&&stamina()>before+.001,30);add(then);return true;
    }
}
