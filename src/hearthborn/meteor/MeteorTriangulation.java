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

package hearthborn.meteor;

import haven.*;
import hearthborn.L10n;
import hearthborn.io.LocalStore;
import hearthborn.ui.WorldIdentity;
import java.io.Serializable;
import java.util.*;

/**
 * Star Gaze observations, anchored to immutable server grid IDs rather than session coordinates.
 *
 * @author Jagy2014
 */
public final class MeteorTriangulation {
    private static final String GAZE="paginae/seid/stargaze";
    private static final long RESPONSE_WINDOW=15000;
    private static final int MAX_MEASUREMENTS=128;
    private static final Map<Glob,Pending> pending=new WeakHashMap<>();
    private static final Map<Session,Boolean> menus=new WeakHashMap<>();
    private static final Map<String,Survey> saved=new HashMap<>();
    private static boolean loaded;
    private static int generation;

    public static final class Observation implements Serializable {
        private static final long serialVersionUID=1;
        public final long grid,time;
        public final double x,y,start,end;
        Observation(long grid,double x,double y,double start,double end,long time){
            this.grid=grid;this.x=x;this.y=y;this.start=start;this.end=end;this.time=time;
        }
        boolean valid(){return Double.isFinite(x)&&Double.isFinite(y)&&x>=0&&y>=0&&x<100&&y<100&&
            Double.isFinite(start)&&Double.isFinite(end)&&end>start&&end-start<=Math.PI&&time>0;}
    }
    private static final class Survey implements Serializable {
        private static final long serialVersionUID=1;
        final List<Observation> observations=new ArrayList<>();
        boolean ended;
    }
    static final class Pending {
        final String world;
        final long player,grid,started;
        final Coord2d at,local;
        boolean confirmed,ambiguous;
        Observation observation;
        Pending(String world,long player,long grid,Coord2d at,Coord2d local,long started){
            this.world=world;this.player=player;this.grid=grid;this.at=at;this.local=local;this.started=started;
        }
        boolean alive(long now){return now>=started&&now-started<=RESPONSE_WINDOW;}
        void accept(Observation value){
            if(observation==null)observation=value;
            else if(!same(observation,value))ambiguous=true;
        }
        boolean ready(long now){return alive(now)&&confirmed&&!ambiguous&&observation!=null;}
    }
    static boolean same(Observation a,Observation b){
        return a.grid==b.grid&&Math.hypot(a.x-b.x,a.y-b.y)<.1&&Math.abs(a.start-b.start)<1e-8&&Math.abs(a.end-b.end)<1e-8;
    }
    private static String key(UI ui){
        if(ui==null||ui.sess==null)return null;
        String world=WorldIdentity.name(ui.sess);if(world==null)return null;
        NamedSocketAddress server=Bootstrap.authserv.get();
        return server.host.toLowerCase(Locale.ROOT)+":"+server.port+"\n"+world;
    }
    private static void load(){
        if(loaded)return;loaded=true;
        Object stored=LocalStore.read("meteor-triangulation-v2");
        if(stored instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)stored).entrySet())
            if(entry.getKey() instanceof String&&entry.getValue() instanceof Survey){
                Survey survey=(Survey)entry.getValue();
                if(survey.observations!=null&&survey.observations.size()<=MAX_MEASUREMENTS&&
                    survey.observations.stream().allMatch(value->value!=null&&value.valid()))saved.put((String)entry.getKey(),survey);
            }
    }
    private static void save(){generation++;LocalStore.write("meteor-triangulation-v2",new HashMap<>(saved));}
    public static int generation(){return generation;}
    public static List<Observation> observations(UI ui){
        load();Survey survey=saved.get(key(ui));
        return survey==null?Collections.emptyList():new ArrayList<>(survey.observations);
    }
    public static void clear(UI ui){
        load();String key=key(ui);if(key!=null&&saved.remove(key)!=null)save();
        if(ui!=null&&ui.sess!=null)synchronized(pending){pending.remove(ui.sess.glob);}
    }
    /** Confirmed action removal clears this event immediately; initial absence and loading do not. */
    public static void menu(UI ui,Boolean available){
        if(ui==null||ui.sess==null||available==null)return;
        load();Boolean previous=menus.put(ui.sess,available);String key=key(ui);Survey survey=saved.get(key);
        // Also discard completed surveys written by older clients, which retained directions until the next event.
        // A login after the fall sees no previous state, so an elapsed forecast with no Star Gaze action also ends the event.
        // Every Star Gaze that belongs to a live event refreshes the forecast into the future, so this cannot drop a current survey.
        if((!available&&(Boolean.TRUE.equals(previous)||(survey!=null&&StarFall.forecastElapsed(ui))))||(survey!=null&&survey.ended))clear(ui);
    }
    static boolean action(GameUI gui,Widget sender,String message,Object[] args){
        if("act".equals(message))return args.length>=2&&"seid".equals(args[0])&&"stargaze".equals(args[1]);
        if("use".equals(message)&&sender==gui.menu&&args.length>0){
            MenuGrid.Pagina page=gui.menu.paginafor(args[0],null);
            return page!=null&&page.res!=null&&GAZE.equals(page.res().name);
        }
        if("belt".equals(message)&&args.length>0&&args[0] instanceof Number&&gui.belt!=null){
            int slot=((Number)args[0]).intValue();if(slot<0||slot>=gui.belt.length)return false;
            GameUI.BeltSlot value=gui.belt[slot];
            if(value instanceof GameUI.ResBeltSlot)return GAZE.equals(((GameUI.ResBeltSlot)value).getres().name);
            if(value instanceof GameUI.PagBeltSlot)return GAZE.equals(((GameUI.PagBeltSlot)value).pag.res().name);
        }
        return false;
    }
    /** Observes actual outgoing commands, including hotbar commands; never casts magic itself. */
    public static void outgoing(UI ui,Widget sender,String message,Object[] args){
        if(ui==null||ui.sess==null||ui.sess.glob==null||sender==null)return;
        GameUI gui=sender instanceof GameUI?(GameUI)sender:sender.getparent(GameUI.class);
        if(gui==null||!("act".equals(message)||"belt".equals(message)||("use".equals(message)&&sender==gui.menu)))return;
        Glob glob=ui.sess.glob;
        synchronized(pending){pending.remove(glob);}
        try{
            if(!action(gui,sender,message,args)||gui.map==null)return;
            Gob player=gui.map.player();String world=key(ui);
            if(player==null||player.rc==null||world==null)return;
            Coord2d at=new Coord2d(player.rc.x,player.rc.y);
            MCache.Grid grid=glob.map.getgridt(at.floor(MCache.tilesz));
            Pending cast=new Pending(world,player.id,grid.id,at,at.div(MCache.tilesz).sub(new Coord2d(grid.ul)),System.currentTimeMillis());
            synchronized(pending){pending.put(glob,cast);}
        }catch(Loading unavailable){}
    }
    /** Called on the sprite loader thread. The stock DowseFx removes gob rotation with goback("gobx"). */
    public static void effect(Sprite.Owner owner,byte[] data){
        double[] angles=angles(data);if(angles==null)return;
        Gob gob=owner instanceof Gob?(Gob)owner:owner.ocontext(Gob.class).orElse(null);
        if(gob==null||gob.glob==null||gob.rc==null)return;
        synchronized(pending){
            Pending cast=pending.get(gob.glob);
            if(cast==null||!cast.alive(System.currentTimeMillis())||
                (gob.id!=cast.player&&(!gob.virtual||gob.rc.dist(cast.at)>MCache.tilesz.x*2)))return;
            try{
                // Player overlays can initialize after walking/teleporting. Freeze their cast origin now,
                // while virtual ground effects have their own fixed server-provided position.
                long gridId=cast.grid;Coord2d local=cast.local;
                if(gob.id!=cast.player){
                    Coord2d tile=gob.rc.div(MCache.tilesz);
                    MCache.Grid grid=gob.glob.map.getgridt(tile.floor());
                    gridId=grid.id;local=tile.sub(new Coord2d(grid.ul));
                }
                Observation observation=new Observation(gridId,local.x,local.y,angles[0],angles[1],System.currentTimeMillis());
                if(observation.valid())cast.accept(observation);
            }catch(Loading unavailable){}
        }
    }
    /** Map coordinates have Y down; the resource negates and swaps these same bytes for render coordinates. */
    static double[] angles(byte[] data){
        if(data==null||data.length!=2)return null;
        double start=(data[0]&255)*Math.PI*2/256,end=(data[1]&255)*Math.PI*2/256;
        while(end<start)end+=Math.PI*2;
        return end>start&&end-start<=Math.PI?new double[]{start,end}:null;
    }
    /** A sector alone cannot identify Star Gaze: the matching successful server forecast is mandatory. */
    public static void notice(UI ui,String message,boolean forecast){
        if(ui==null||ui.sess==null||ui.sess.glob==null)return;
        synchronized(pending){
            Pending cast=pending.get(ui.sess.glob);if(cast==null)return;
            if("It is too bright to see any stars.".equals(message)||!cast.alive(System.currentTimeMillis()))pending.remove(ui.sess.glob);
            else if(forecast)cast.confirmed=true;
        }
    }
    public static void tick(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null)return;
        UI ui=gui.ui;Pending cast;
        synchronized(pending){
            cast=pending.get(ui.sess.glob);if(cast==null)return;
            if(!cast.alive(System.currentTimeMillis())||!Objects.equals(cast.world,key(ui))){pending.remove(ui.sess.glob);return;}
            if(!cast.ready(System.currentTimeMillis()))return;
            pending.remove(ui.sess.glob);
        }
        load();Survey survey=saved.computeIfAbsent(cast.world,key->new Survey());
        if(survey.ended){survey=new Survey();saved.put(cast.world,survey);}
        for(Observation old:survey.observations)if(same(old,cast.observation))return;
        if(survey.observations.size()>=MAX_MEASUREMENTS){ui.msg(L10n.tr("Meteorite triangulation: measurement limit reached. Clear old measurements on the map."));return;}
        survey.observations.add(cast.observation);save();
        ui.msg(L10n.format("Meteorite triangulation: measurement %d saved.",survey.observations.size()),new java.awt.Color(100,230,255),null);
    }

    public static final class Sector {
        public final Coord2d origin;
        public final double start,end;
        public Sector(Coord2d origin,double start,double end){this.origin=origin;this.start=start;this.end=end;}
    }
    /** Resolve every anchor again after map segments are merged; no saved segment/session offset becomes stale. */
    static List<Sector> resolve(MapFile file,long segment,List<Observation> observations){
        List<Sector> result=new ArrayList<>();
        if(!file.lock.readLock().tryLock())throw new Loading("Map file is busy");
        try{
            for(Observation observation:observations){
                MapFile.GridInfo info=file.gridinfo.get(observation.grid);
                if(info!=null&&info.seg==segment)result.add(new Sector(new Coord2d(info.sc.mul(MCache.cmaps)).add(observation.x,observation.y),observation.start,observation.end));
            }
        }finally{file.lock.readLock().unlock();}
        return result;
    }
    /** Sutherland-Hodgman clipping against both infinite rays, so no artificial range is invented. */
    static List<Coord2d> intersect(List<Coord2d> polygon,Sector sector){
        return halfplane(halfplane(polygon,sector.origin,Math.cos(sector.start),Math.sin(sector.start),1),
            sector.origin,Math.cos(sector.end),Math.sin(sector.end),-1);
    }
    private static List<Coord2d> halfplane(List<Coord2d> polygon,Coord2d origin,double dx,double dy,int sign){
        List<Coord2d> result=new ArrayList<>();if(polygon.isEmpty())return result;
        Coord2d a=polygon.get(polygon.size()-1);double da=sign*(dx*(a.y-origin.y)-dy*(a.x-origin.x));
        for(Coord2d b:polygon){
            double db=sign*(dx*(b.y-origin.y)-dy*(b.x-origin.x));
            if((da>=0)!=(db>=0)){
                double fraction=da/(da-db);result.add(a.add(b.sub(a).mul(fraction)));
            }
            if(db>=0)result.add(b);
            a=b;da=db;
        }
        return result;
    }
    static boolean distinctOrigins(List<Sector> sectors){
        if(sectors.size()<2)return false;
        for(int i=1;i<sectors.size();i++)if(sectors.get(i).origin.dist(sectors.get(0).origin)>.1)return true;
        return false;
    }
    private MeteorTriangulation(){}
}
