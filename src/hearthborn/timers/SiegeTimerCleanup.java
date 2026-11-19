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

package hearthborn.timers;

import haven.*;
import hearthborn.timers.Timers;
import hearthborn.util.GameObjects;
import java.util.*;

/**
 * Reconciles preparation timers with nearby loaded ground; a cache removal alone proves nothing.
 *
 * @author Jagy2014
 */
public final class SiegeTimerCleanup {
    private static final double INTERVAL=.5,GRACE=5;
    private static final double NEAR=12*MCache.tilesz.x;
    private static final String BALL="gfx/terobjs/vehicle/wreckingball",RAM="gfx/terobjs/vehicle/bram",FOLDED=BALL+"-fold";
    private static final Map<GameUI,State> states=new WeakHashMap<>();
    static final class Anchor {
        final long grid;final Coord2d local;
        Anchor(long grid,Coord2d local){this.grid=grid;this.local=local;}
    }
    static Anchor parse(String source){
        if(source==null||!source.startsWith("siege:"))return null;
        String[] parts=source.split(":");if(parts.length!=4)return null;
        try{
            long grid=Long.parseLong(parts[1]);int x=Integer.parseInt(parts[2]),y=Integer.parseInt(parts[3]);
            Coord2d local=Coord.of(x,y).mul(OCache.posres);
            if(local.x<0||local.y<0||local.x>=MCache.cmaps.x*MCache.tilesz.x||local.y>=MCache.cmaps.y*MCache.tilesz.y)return null;
            return new Anchor(grid,local);
        }catch(NumberFormatException invalid){return null;}
    }
    static final class State {
        Glob glob;MCache.Grid grid;long player;Coord2d at;
        double next,stableSince;
        final Map<String,Double> missing=new HashMap<>();
    }
    public static void tick(GameUI gui){tick(gui,Utils.rtime());}
    static void tick(GameUI gui,double now){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;
        State state=states.computeIfAbsent(gui,key->new State());
        if(now<state.next)return;state.next=now+INTERVAL;
        Glob glob=gui.ui.sess.glob;Gob player=gui.map.player();
        if(glob==null||player==null||player.rc==null){state.missing.clear();state.grid=null;return;}
        MCache.Grid playerGrid;
        try{playerGrid=glob.map.getgridt(player.rc.floor(MCache.tilesz));}
        catch(Loading pending){state.missing.clear();state.grid=null;return;}
        boolean transition=state.glob!=glob||state.grid!=playerGrid||state.player!=player.id||state.at==null||
            state.at.dist(player.rc)>5*MCache.tilesz.x;
        if(transition){state.missing.clear();state.stableSince=now;}
        state.glob=glob;state.grid=playerGrid;state.player=player.id;state.at=new Coord2d(player.rc.x,player.rc.y);
        Set<String> sources=Timers.sources("siege:");state.missing.keySet().retainAll(sources);
        if(sources.isEmpty())return;
        List<Gob> objects=new ArrayList<>();synchronized(glob.oc){for(Gob gob:glob.oc)objects.add(gob);}
        Map<Long,MCache.Grid> grids=new HashMap<>();for(MCache.Grid grid:glob.map.loadedGrids())grids.put(grid.id,grid);
        for(String source:sources){
            Anchor anchor=parse(source);MCache.Grid grid=anchor==null?null:grids.get(anchor.grid);
            if(grid==null){state.missing.remove(source);continue;}
            Coord2d target=new Coord2d(grid.ul).mul(MCache.tilesz).add(anchor.local);
            if(player.rc.dist(target)>NEAR){state.missing.remove(source);continue;}
            boolean assembled=false,folded=false,unresolved=false;
            for(Gob gob:objects){
                if(gob.removed||gob.virtual||gob.rc==null||gob.rc.dist(target)>MCache.tilesz.x)continue;
                try{
                    String path=GameObjects.resourceName(gob);
                    // Unresolved objects near the expected position must not count as empty ground.
                    if(path==null||path.isEmpty()){unresolved=true;continue;}
                    if(gob.rc.dist(target)>1)continue;
                    assembled|=BALL.equals(path)||RAM.equals(path);
                    folded|=FOLDED.equals(path);
                }catch(Loading pending){unresolved=true;}
            }
            // An assembled replacement at the same location can still own the existing process.
            if(assembled||unresolved){state.missing.remove(source);continue;}
            if(folded){Timers.forget(source);state.missing.remove(source);continue;}
            if(now-state.stableSince<GRACE){state.missing.remove(source);continue;}
            double since=state.missing.computeIfAbsent(source,key->now);
            if(now-since>=GRACE){Timers.forget(source);state.missing.remove(source);}
        }
    }
    private SiegeTimerCleanup(){}
}
