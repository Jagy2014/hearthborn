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

package hearthborn.planner;
import hearthborn.storage.ContainerContents;
import hearthborn.world.ObjectNames;

import haven.*;
import hearthborn.io.LocalStore;
import hearthborn.util.GameObjects;
import hearthborn.world.*;
import java.io.Serializable;
import java.util.*;

/**
 * Last-seen claim snapshots use stable grid IDs and local object coordinates.
 *
 * @author Jagy2014
 */
public final class ClaimSurvey {
    private static volatile boolean scanEnabled=Utils.getprefb("hearthborn/claim-plan/scan",true);
    private static final Map<GameUI,ScanPresence> scanPresence=new WeakHashMap<>();
    private static final class ScanPresence {double until;boolean own;Glob world;Coord tile;}
    public static boolean enabled(){return scanEnabled;}
    public static void enabled(boolean on){
        scanEnabled=on;Utils.setprefb("hearthborn/claim-plan/scan",on);scanPresence.clear();
    }
    static boolean shouldScan(boolean enabled,boolean ownClaim){return enabled&&ownClaim;}
    static boolean ownedTile(MCache.Grid grid,Coord tile){
        if(grid==null)return false;
        Indir<Resource>[] refs=grid.ols;boolean[][] masks=grid.ol;
        Coord local=tile.sub(grid.ul);
        if(refs==null||masks==null||!local.isect(Coord.z,MCache.cmaps))return false;
        int cell=local.x+local.y*MCache.cmaps.x;
        for(int i=0;i<Math.min(refs.length,masks.length);i++)try{
            if(masks[i]==null||cell>=masks[i].length||!masks[i][cell])continue;
            MCache.ResOverlay overlay=refs[i].get().layer(MCache.ResOverlay.class);
            if(overlay!=null&&allowed(overlay.tags()))return true;
        }catch(Loading pending){}
        return false;
    }
    public static boolean scanning(GameUI gui){
        if(!scanEnabled||gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return false;
        Gob player=gui.map.player();if(player==null||player.rc==null)return false;
        Coord tile=player.rc.floor(MCache.tilesz);Glob world=gui.ui.sess.glob;
        ScanPresence state=scanPresence.computeIfAbsent(gui,key->new ScanPresence());
        double now=Utils.rtime();
        if(now<state.until&&state.world==world&&tile.equals(state.tile))return shouldScan(scanEnabled,state.own);
        state.until=now+.25;state.own=false;state.world=world;state.tile=tile;
        try{
            MCache.Grid grid=world.map.getgridt(tile);
            state.own=ownedTile(grid,tile);
            if(state.own&&inside(gui))state.own=false;
        }catch(Loading pending){state.own=false;}
        return shouldScan(scanEnabled,state.own);
    }

    static final class ObjectRecord implements Serializable {
        private static final long serialVersionUID=1;
        String resource,type,name,icon,number;double x,y,quality;
        byte[] picture;boolean ready;double[] footprint;long seen;
        String key(){return resource+":"+Math.round(x/OCache.posres.x)+":"+Math.round(y/OCache.posres.y);}
    }
    static final class GridRecord implements Serializable {
        private static final long serialVersionUID=1;
        byte[] terrain;String region;long id;int x,y;BitSet personal=new BitSet(),village=new BitSet();
        /** Personal claims that are not ours. Map borders use them; the journal does not. */
        BitSet foreign=new BitSet();
        Map<String,ObjectRecord> objects=new HashMap<>();
    }
    static final class Plan implements Serializable {
        private static final long serialVersionUID=1;
        String id,name,tag;long grid;int x,y;
        boolean interior; // Legacy room records are excluded when loading saved claims.
        java.util.List<GridRecord> snapshot;
        java.util.List<NamedArea> areas=new ArrayList<>();
    }
    static final class NamedArea implements Serializable {
        private static final long serialVersionUID=1;
        String id=UUID.randomUUID().toString(),name;int x,y,w,h;
    }
    private static String plansStore;
    private static final java.util.List<Plan> savedPlans=new ArrayList<>();
    static java.util.List<Plan> plans(){
        load();
        if(!Objects.equals(plansStore,store)){
            savedPlans.clear();plansStore=store;
            Object data=store==null?null:LocalStore.read(store+"-plans");
            if(data instanceof java.util.List)for(Object item:(java.util.List<?>)data)if(item instanceof Plan&&!legacyRoom((Plan)item))savedPlans.add((Plan)item);
        }
        return new ArrayList<>(savedPlans);
    }
    static java.util.List<Plan> plans(String tag){
        java.util.List<Plan> selected=new ArrayList<>();for(Plan plan:plans())if(tag.equals(plan.tag))selected.add(plan);return selected;
    }
    static boolean legacyRoom(Plan plan){
        if(plan.interior)return true;
        if(plan.snapshot!=null)for(GridRecord grid:plan.snapshot)if(grid.region!=null&&grid.region.startsWith("room:"))return true;
        return false;
    }
    static void savePlans(java.util.List<Plan> plans){
        if(store==null)return;
        savedPlans.clear();savedPlans.addAll(plans);plansStore=store;
        LocalStore.write(store+"-plans",new ArrayList<>(savedPlans));
    }
    static void removeArea(String planId,String areaId){
        java.util.List<Plan> all=plans();
        for(Plan plan:all)if(planId.equals(plan.id)&&plan.areas!=null)plan.areas.removeIf(area->areaId.equals(area.id));
        savePlans(all);
    }
    private static final Map<Long,GridRecord> records=new HashMap<>();
    private static String region;
    private static int generation;
    static String region(){return region;}
    static int generation(){load();return generation;}
    private static boolean loaded;private static double next,bordersNext;private static Glob context;
    private static String store;
    private static void bind(GameUI gui){
        String user=gui.ui.sess.user==null?"":gui.ui.sess.user.name;
        String identity=user+"\n"+gui.chrid;
        String key="claim-survey-owned-v2-"+java.util.UUID.nameUUIDFromBytes(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if(!key.equals(store)){store=key;records.clear();claimMemos.clear();loaded=false;region=null;context=null;next=0;generation++;}
    }
    /** The server marks the character's own claim with {@code own}. The journal keeps only those. */
    static boolean allowed(Collection<String> tags){
        return tags.contains("own")&&!tags.contains("!own")&&(tags.contains("cplot")||tags.contains("vlg"));
    }
    /** Every personal claim is drawn on the map, including ones that are not ours. */
    static boolean personalBorder(Collection<String> tags){return tags.contains("cplot")&&!allowed(tags);}
    private static boolean villageMember(GameUI gui){
        for(Polity polity:gui.children(Polity.class))if("Village".equals(polity.cap))return true;
        return false;
    }
    private static void save(){if(store!=null)LocalStore.write(store,new HashMap<>(records));}
    private static void load(){
        if(loaded||store==null)return;loaded=true;Object data=LocalStore.read(store);
        if(data instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)data).entrySet())if(entry.getKey() instanceof Long&&entry.getValue() instanceof GridRecord)records.put((Long)entry.getKey(),(GridRecord)entry.getValue());
    }
    static void prepare(GameUI gui){if(gui==null||gui.ui==null||gui.ui.sess==null)return;bind(gui);load();}
    static boolean sameSegment(GameUI gui,GridRecord grid,long segment){
        String live=segment(gui,grid.id);String wanted="segment:"+segment;
        if(live!=null)return live.equals(wanted);
        return wanted.equals(grid.region);
    }
    static Collection<GridRecord> records(){return records(region);}
    static Collection<GridRecord> records(String wanted){load();java.util.List<GridRecord> result=new ArrayList<>();for(GridRecord record:records.values())if(wanted!=null&&wanted.equals(record.region))result.add(record);return result;}
    static Collection<GridRecord> allRecords(){load();return new ArrayList<>(records.values());}
    static String recordRegion(GameUI gui,long grid){load();String known=segment(gui,grid);GridRecord saved=records.get(grid);return known!=null?known:saved==null?null:saved.region;}
    private static String segment(GameUI gui,long grid){
        if(gui.mapfile==null||gui.mapfile.file==null)return null;
        MapFile file=gui.mapfile.file;if(!file.lock.readLock().tryLock())return null;
        try{MapFile.GridInfo info=file.gridinfo.get(grid);return info==null?null:"segment:"+info.seg;}finally{file.lock.readLock().unlock();}
    }
    static byte terrain(String resource){
        if(resource==null)return 0;
        if(resource.matches("gfx/tiles/(.*water.*|.*deep.*|.*shallow.*|.*ocean.*)"))return 5;
        if(resource.contains("/paving/")||resource.equals("gfx/tiles/stone")||resource.equals("gfx/tiles/woodpave"))return 2;
        if(resource.equals("gfx/tiles/plowed")||resource.equals("gfx/tiles/field"))return 1;
        if(resource.equals("gfx/tiles/dirt"))return 3;
        return 0;
    }
    static void scan(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;Gob player=gui.map.player();if(player==null)return;
        if(!scanning(gui))return;
        bind(gui);
        double now=Utils.rtime();if(gui.ui.sess.glob==context&&now<next)return;context=gui.ui.sess.glob;next=now+2;load();
        Collection<MCache.Grid> grids=gui.ui.sess.glob.map.loadedGrids();boolean changed=false;
        // Older snapshots did not store their segment; recover it from stable grid IDs.
        for(GridRecord record:records.values()){String known=segment(gui,record.id);if(known!=null&&!known.equals(record.region)){record.region=known;changed=true;}}
        MCache.Grid playerGrid;
        try{playerGrid=gui.ui.sess.glob.map.getgridt(player.rc.floor(MCache.tilesz));}catch(Loading wait){return;}
        String current=segment(gui,playerGrid.id);GridRecord previous=records.get(playerGrid.id);
        if(current==null)current=previous!=null&&previous.region!=null?previous.region:"grid:"+playerGrid.id;
        if(previous!=null&&previous.region!=null&&!current.equals(previous.region)&&current.startsWith("segment:")){
            String oldRegion=previous.region;for(GridRecord record:records.values())if(oldRegion.equals(record.region))record.region=current;changed=true;
        }
        if(!Objects.equals(region,current)){region=current;generation++;}
        // Align only grids from this map segment. Equal coordinates in another layer are unrelated.
        for(MCache.Grid grid:grids){GridRecord old=records.get(grid.id);if(old==null||!current.equals(old.region))continue;
            int dx=grid.ul.x-old.x,dy=grid.ul.y-old.y;
            if(dx!=0||dy!=0){for(GridRecord record:records.values())if(current.equals(record.region)){record.x+=dx;record.y+=dy;}changed=true;}break;
        }
        Map<Long,byte[]> ground=new HashMap<>();Map<Integer,Byte> palette=new HashMap<>();
        for(MCache.Grid grid:grids){
            String gridSegment=segment(gui,grid.id);if(gridSegment!=null?!current.equals(gridSegment):grid.id!=playerGrid.id&&(records.get(grid.id)==null||!current.equals(records.get(grid.id).region)))continue;
            Indir<Resource>[] overlays=grid.ols;boolean[][] masks=grid.ol;
            if(overlays==null||masks==null)continue;BitSet personal=new BitSet(),village=new BitSet();
            for(int index=0;index<Math.min(overlays.length,masks.length);index++)try{
                MCache.ResOverlay overlay=overlays[index].get().layer(MCache.ResOverlay.class);if(overlay==null||!allowed(overlay.tags()))continue;
                BitSet target=overlay.tags().contains("cplot")?personal:overlay.tags().contains("vlg")?village:null;if(target==null||masks[index]==null)continue;
                boolean[] mask=masks[index];for(int cell=0;cell<Math.min(10000,mask.length);cell++)if(mask[cell])target.set(cell);
            }catch(Loading pending){}
            GridRecord record=records.get(grid.id);
            if(record==null&&personal.isEmpty()&&village.isEmpty())continue;
            if(record==null){record=new GridRecord();record.id=grid.id;record.x=grid.ul.x;record.y=grid.ul.y;records.put(grid.id,record);changed=true;}
            if(!current.equals(record.region)){record.region=current;record.x=grid.ul.x;record.y=grid.ul.y;changed=true;}
            // Claim overlays and visible objects may arrive in separate updates. Keep explored territory.
            personal.or(record.personal);village.or(record.village);
            byte[] terrain=record.terrain==null?new byte[10000]:record.terrain.clone();
            for(int cell=0;cell<10000;cell++)try{
                if(!personal.get(cell)&&!village.get(cell)){terrain[cell]=0;continue;}
                int id=grid.gettile(Coord.of(cell%100,cell/100));Byte kind=palette.get(id);
                if(kind==null){Resource tile=gui.ui.sess.glob.map.tilesetr(id);if(tile==null)continue;kind=terrain(tile.name);palette.put(id,kind);}
                Coord2d at=new Coord2d((grid.ul.x+cell%100+.5)*MCache.tilesz.x,(grid.ul.y+cell/100+.5)*MCache.tilesz.y);
                if(terrain[cell]!=4||kind>1||at.dist(player.rc)<55)terrain[cell]=kind;
            }catch(Loading ignored){}
            ground.put(grid.id,terrain);
            if(!personal.equals(record.personal)||!village.equals(record.village)){record.personal=personal;record.village=village;changed=true;}
        }
        synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)try{
            if(!placed(gob))continue;String resource=GameObjects.resourceName(gob),type=ClaimPlan.type(resource);boolean crop=resource!=null&&resource.startsWith("gfx/terobjs/plants/");if(type==null&&!crop)continue;
            MCache.Grid grid=gui.ui.sess.glob.map.getgridt(gob.rc.floor(MCache.tilesz));GridRecord record=records.get(grid.id);if(record==null||!current.equals(record.region))continue;
            Coord tile=gob.rc.floor(MCache.tilesz).sub(grid.ul);int cell=tile.x+tile.y*100;
            if(cell<0||cell>=10000||!record.personal.get(cell)&&!record.village.get(cell))continue;
            if(crop){byte[] terrain=ground.get(grid.id);if(terrain!=null)terrain[cell]=4;continue;}
            ObjectRecord object=new ObjectRecord();object.resource=resource;object.type=type;object.footprint=ClaimPlan.footprint(gob);object.name=ObjectNames.name(gob);if(type.equals("Rack"))object.number=ObjectNames.automaticNumber(gob);hearthborn.quality.RosterStore.Rec quality=hearthborn.quality.RosterStore.trackedGob(gob.id);if(quality!=null)object.quality=quality.q;object.icon=ContainerContents.rememberedIcon(gob);object.x=gob.rc.x-grid.ul.x*MCache.tilesz.x;object.y=gob.rc.y-grid.ul.y*MCache.tilesz.y;object.seen=System.currentTimeMillis();
            if(type.equals("Rack"))changed|=reconcileRack(record,object);
            String key=object.key();ObjectRecord old=record.objects.get(key);if(object.quality<=0&&old!=null)object.quality=old.quality;
            if(old==null||Double.compare(old.quality,object.quality)!=0||!Objects.equals(old.number,object.number)||!Objects.equals(old.type,object.type)||!Objects.equals(old.name,object.name)||!Objects.equals(old.icon,object.icon)||!Arrays.equals(old.footprint,object.footprint)||object.seen-old.seen>60000){record.objects.put(key,object);changed=true;}
        }catch(Loading ignored){}}
        // Absence from the object cache is not evidence of removal: objects unload or are still loading.
        for(Map.Entry<Long,byte[]> entry:ground.entrySet()){GridRecord record=records.get(entry.getKey());if(!Arrays.equals(record.terrain,entry.getValue())){record.terrain=entry.getValue();changed=true;}}
        if(changed){generation++;save();}
    }
    /** Loaded personal claims stay on the map even when the journal is not recording this visit. */
    private static void captureBorders(GameUI gui){
        Gob player=gui.map==null?null:gui.map.player();if(player==null||player.rc==null)return;
        double now=Utils.rtime();
        if(gui.ui.sess.glob==context&&now<bordersNext)return;
        bordersNext=now+2;
        MCache.Grid playerGrid;
        try{playerGrid=gui.ui.sess.glob.map.getgridt(player.rc.floor(MCache.tilesz));}
        catch(Loading wait){bordersNext=now;return;}
        String current=segment(gui,playerGrid.id);GridRecord previous=records.get(playerGrid.id);
        if(current==null)current=previous!=null&&previous.region!=null?previous.region:"grid:"+playerGrid.id;
        boolean changed=false;
        for(MCache.Grid grid:gui.ui.sess.glob.map.loadedGrids()){
            String gridSegment=segment(gui,grid.id);
            if(gridSegment!=null?!current.equals(gridSegment):grid.id!=playerGrid.id&&(records.get(grid.id)==null||!current.equals(records.get(grid.id).region)))continue;
            Indir<Resource>[] overlays=grid.ols;boolean[][] masks=grid.ol;if(overlays==null||masks==null)continue;
            BitSet seen=new BitSet();
            for(int index=0;index<Math.min(overlays.length,masks.length);index++)try{
                MCache.ResOverlay overlay=overlays[index].get().layer(MCache.ResOverlay.class);
                if(overlay==null||!personalBorder(overlay.tags())||masks[index]==null)continue;
                boolean[] mask=masks[index];for(int cell=0;cell<Math.min(10000,mask.length);cell++)if(mask[cell])seen.set(cell);
            }catch(Loading pending){}
            if(seen.isEmpty())continue;
            GridRecord record=records.get(grid.id);
            if(record==null){record=new GridRecord();record.id=grid.id;record.x=grid.ul.x;record.y=grid.ul.y;record.region=current;records.put(grid.id,record);changed=true;}
            if(record.foreign==null)record.foreign=new BitSet();
            int before=record.foreign.cardinality();record.foreign.or(seen);
            if(record.foreign.cardinality()!=before)changed=true;
        }
        if(changed){generation++;save();}
    }
    /** Carried objects have carrier coordinates, not a placed position on the plan. */
    static boolean placed(Gob gob){return gob.rc!=null&&!gob.removed&&gob.getattr(Following.class)==null;}
    static boolean reconcileRack(GridRecord current,ObjectRecord placed){
        if(placed.number==null)return false;
        boolean changed=false;
        for(GridRecord grid:records.values()){
            if(!Objects.equals(current.region,grid.region))continue;
            for(Iterator<ObjectRecord> it=grid.objects.values().iterator();it.hasNext();){
                ObjectRecord old=it.next();
                if(!Objects.equals(placed.resource,old.resource)||grid.id==current.id&&old.key().equals(placed.key()))continue;
                // Legacy records only contain the automatic display name. Custom aliases are not identities.
                boolean same=placed.number.equals(old.number)||old.number==null&&
                    (Objects.equals(old.name,"Rack "+placed.number)||Objects.equals(old.name,"Стойка "+placed.number)
                        ||Objects.equals(old.name,"Cheese Rack "+placed.number)||Objects.equals(old.name,"Сырная стойка "+placed.number));
                if(same){it.remove();changed=true;}
            }
        }
        return changed;
    }
    /** A moved rack must not remain as a saved object at its former position. */
    public static void moved(String owner){
        load();boolean changed=false;
        for(GridRecord grid:records.values())for(Iterator<ObjectRecord> it=grid.objects.values().iterator();it.hasNext();){
            ObjectRecord object=it.next();Coord local=new Coord2d(object.x,object.y).floor(OCache.posres);
            if(owner.equals(object.resource+":"+grid.id+":"+local.x+":"+local.y)){it.remove();changed=true;}
        }
        if(changed){generation++;save();}
    }
    public static void renamed(String owner,String name){
        load();boolean changed=false;
        for(GridRecord grid:records.values())for(ObjectRecord object:grid.objects.values()){
            Coord local=new Coord2d(object.x,object.y).floor(OCache.posres);
            if(owner.equals(object.resource+":"+grid.id+":"+local.x+":"+local.y)&&!Objects.equals(name,object.name)){object.name=name;changed=true;}
        }
        if(changed){generation++;save();}
    }
    private static final Map<GameUI,RoomPlan> roomObservers=new WeakHashMap<>();
    private static final Map<GameUI,Double> observedAt=new WeakHashMap<>();
    private static final class ClaimMemo {
        long fingerprint;Set<Coord> tiles;String plan;
        ClaimMemo(long fingerprint,Set<Coord> tiles,String plan){this.fingerprint=fingerprint;this.tiles=tiles;this.plan=plan;}
    }
    private static final Map<String,ClaimMemo> claimMemos=new HashMap<>();
    private static void rememberClaim(GameUI gui,String tag){
        Gob player=gui.map.player();if(player==null)return;Coord here=player.rc.floor(MCache.tilesz);
        Collection<GridRecord> grids=records();boolean claimed=false;long fingerprint=1;
        for(GridRecord grid:grids){
            BitSet mask=tag.equals("vlg")?grid.village:grid.personal;int x=here.x-grid.x,y=here.y-grid.y;
            if(x>=0&&x<100&&y>=0&&y<100&&mask.get(x+y*100))claimed=true;
            fingerprint+=Objects.hash(grid.id,grid.x,grid.y,mask);
        }
        if(!claimed)return;
        String memoKey=region+":"+tag;ClaimMemo memo=claimMemos.get(memoKey);
        if(memo!=null&&memo.fingerprint==fingerprint&&memo.tiles.contains(here))for(Plan plan:plans())if(plan.id.equals(memo.plan))return;
        Set<Coord> tiles=new HashSet<>();
        for(GridRecord grid:grids){BitSet mask=tag.equals("vlg")?grid.village:grid.personal;for(int cell=mask.nextSetBit(0);cell>=0;cell=mask.nextSetBit(cell+1))tiles.add(Coord.of(grid.x+cell%100,grid.y+cell/100));}
        if(!tiles.contains(here))return;Set<Coord> selected=ClaimPlan.component(tiles,here);
        java.util.List<Plan> all=plans();
        for(Plan plan:all)if(plan.snapshot==null&&tag.equals(plan.tag))for(GridRecord grid:grids)if(grid.id==plan.grid&&selected.contains(Coord.of(grid.x+plan.x,grid.y+plan.y))){claimMemos.put(memoKey,new ClaimMemo(fingerprint,selected,plan.id));return;}
        MCache.Grid grid=player.glob.map.getgridt(here);Plan plan=new Plan();plan.id=UUID.randomUUID().toString();plan.tag=tag;plan.grid=grid.id;plan.x=here.x-grid.ul.x;plan.y=here.y-grid.ul.y;
        plan.name=hearthborn.L10n.tr(tag.equals("vlg")?"Village claim":"Personal claim");
        if(tag.equals("vlg"))for(Polity polity:gui.children(Polity.class))if("Village".equals(polity.cap)){plan.name=polity.name;break;}
        plan.name+=" · "+(all.size()+1);all.add(plan);savePlans(all);claimMemos.put(memoKey,new ClaimMemo(fingerprint,selected,plan.id));
    }
    static final class EntryPoint {
        String region;long grid;double x,y,until;
    }
    static final class Position {
        boolean inside;EntryPoint pending,lastOutside,entrance;
    }
    private static final Map<GameUI,Position> positions=new WeakHashMap<>();
    private static Position position(GameUI gui){return positions.computeIfAbsent(gui,key->new Position());}
    static boolean inside(GameUI gui){
        if(gui==null||gui.map==null)return false;
        RoomPlan room=roomObservers.computeIfAbsent(gui,key->new RoomPlan(key.map));return room.indoors();
    }
    static boolean locationKnown(GameUI gui){
        if(gui==null||gui.map==null)return false;
        return roomObservers.computeIfAbsent(gui,key->new RoomPlan(key.map)).locationKnown();
    }
    static String entryRegion(EntryPoint entry){
        if(entry==null)return null;GridRecord saved=records.get(entry.grid);return saved!=null?saved.region:entry.region;
    }
    private static EntryPoint point(GameUI gui,Coord2d at){
        MCache.Grid grid=gui.ui.sess.glob.map.getgridt(at.floor(MCache.tilesz));EntryPoint point=new EntryPoint();
        point.grid=grid.id;point.region=recordRegion(gui,grid.id);point.x=at.x-grid.ul.x*MCache.tilesz.x;point.y=at.y-grid.ul.y*MCache.tilesz.y;return point;
    }
    public static void clickedEntrance(GameUI gui,Gob gob){
        if(gui==null||gob==null||inside(gui))return;
        String type=ClaimPlan.type(GameObjects.resourceName(gob));if(!"Building".equals(type)&&!RoomPlan.passage(type))return;
        try{EntryPoint entry=point(gui,gob.rc);entry.until=Utils.rtime()+45;position(gui).pending=entry;}catch(Loading waiting){}
    }
    static String displayRegion(GameUI gui){
        Position state=positions.get(gui);
        if(state!=null&&state.inside&&state.entrance!=null)return entryRegion(state.entrance);
        showCurrent(gui);return region;
    }
    /** Saved claims stay visible while scanning is paused. This only names the segment; it does not record new tiles. */
    static void showCurrent(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;
        Gob player=gui.map.player();if(player==null||player.rc==null||inside(gui))return;
        prepare(gui);
        try{
            MCache.Grid here=gui.ui.sess.glob.map.getgridt(player.rc.floor(MCache.tilesz));
            java.util.List<Long> ids=new ArrayList<>();for(GridRecord grid:records.values())ids.add(grid.id);ids.add(here.id);
            Map<Long,String> live=liveSegments(gui,ids);
            String current=live.get(here.id);boolean changed=false;
            if(current!=null)for(GridRecord grid:records.values()){
                String known=live.get(grid.id);if(known!=null&&!known.equals(grid.region)){grid.region=known;changed=true;}
            }
            if(current==null){GridRecord saved=records.get(here.id);current=saved==null?null:saved.region;}
            if(current!=null&&!current.equals(region)){region=current;changed=true;}
            if(changed){generation++;save();}
        }catch(Loading waiting){}
    }
    private static Map<Long,String> liveSegments(GameUI gui,Collection<Long> ids){
        Map<Long,String> result=new HashMap<>();MapFile file=gui.mapfile==null?null:gui.mapfile.file;if(file==null||!file.lock.readLock().tryLock())return result;
        try{for(Long id:ids){MapFile.GridInfo info=file.gridinfo.get(id);if(info!=null)result.put(id,"segment:"+info.seg);}}finally{file.lock.readLock().unlock();}
        return result;
    }
    static Map<Long,Coord> mapOrigins(GameUI gui,long segment){
        Map<Long,Coord> origins=new HashMap<>();MapFile file=gui==null||gui.mapfile==null?null:gui.mapfile.file;if(file==null)return origins;
        if(!file.lock.readLock().tryLock())return null; // Busy, rather than an empty segment.
        try{for(GridRecord grid:records.values()){MapFile.GridInfo info=file.gridinfo.get(grid.id);if(info!=null&&info.seg==segment)origins.put(grid.id,info.sc.mul(MCache.cmaps));}}
        finally{file.lock.readLock().unlock();}
        return origins;
    }
    static Coord2d marker(GameUI gui,String wanted){
        Position state=positions.get(gui);
        if(state!=null&&state.inside){
            EntryPoint entry=state.entrance;if(entry==null||!Objects.equals(wanted,entryRegion(entry)))return null;
            for(GridRecord grid:records(entryRegion(entry)))if(grid.id==entry.grid)return new Coord2d(grid.x*MCache.tilesz.x+entry.x,grid.y*MCache.tilesz.y+entry.y);
            return null;
        }
        Gob player=gui.map==null?null:gui.map.player();return player!=null&&Objects.equals(wanted,region)?player.rc:null;
    }
    static void entered(Position state,double now){
        if(!state.inside)state.entrance=state.lastOutside!=null?state.lastOutside:state.pending!=null&&now<=state.pending.until?state.pending:null;
        state.inside=true;
    }
    public static void observe(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;
        /* The player object can leave the object cache between two reads, so read it once. */
        Gob player=gui.map.player();if(player==null||player.rc==null)return;
        bind(gui);load();
        captureBorders(gui);
        Position state=position(gui);
        if(!locationKnown(gui)&&!scanning(gui))return;
        boolean indoors=inside(gui);double now=Utils.rtime();
        if(indoors){
            entered(state,now);return;
        }
        state.inside=false;state.entrance=null;
        showCurrent(gui);
        scan(gui);
        try{
            EntryPoint outside=point(gui,player.rc);
            GridRecord known=records.get(outside.grid);
            if(known!=null&&(!known.personal.isEmpty()||!known.village.isEmpty()))state.lastOutside=outside;
        }catch(Loading waiting){}
        if(!scanning(gui))return;
        if(now<observedAt.getOrDefault(gui,0.0))return;observedAt.put(gui,now+3);
        try{rememberClaim(gui,"cplot");rememberClaim(gui,"vlg");}catch(Loading waiting){}
    }
    private ClaimSurvey(){}
}
