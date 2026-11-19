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

import haven.*;
import java.awt.Color;
import java.util.*;

/**
 * Claim outlines share the surveyed tiles and sit on the map file's own grid placement.
 *
 * @author Jagy2014
 */
public final class ClaimBorders {
    private static final Map<MiniMap,Cache> caches=new WeakHashMap<>();
    public static boolean enabled(String tag){return Utils.getprefb("hearthborn/map/claim-border-"+tag,true);}
    public static void enabled(String tag,boolean enabled){Utils.setprefb("hearthborn/map/claim-border-"+tag,enabled);}
    static java.util.List<Coord[]> edges(Set<Coord> tiles){
        Map<Integer,Set<Integer>> rows=new HashMap<>();
        for(Coord tile:tiles)rows.computeIfAbsent(tile.y,key->new HashSet<>()).add(tile.x);
        Map<Integer,List<Integer>> horizontal=new HashMap<>(),vertical=new HashMap<>();
        for(Coord tile:tiles){
            if(!contains(rows,tile.x,tile.y-1))horizontal.computeIfAbsent(tile.y,key->new ArrayList<>()).add(tile.x);
            if(!contains(rows,tile.x,tile.y+1))horizontal.computeIfAbsent(tile.y+1,key->new ArrayList<>()).add(tile.x);
            if(!contains(rows,tile.x-1,tile.y))vertical.computeIfAbsent(tile.x,key->new ArrayList<>()).add(tile.y);
            if(!contains(rows,tile.x+1,tile.y))vertical.computeIfAbsent(tile.x+1,key->new ArrayList<>()).add(tile.y);
        }
        List<Coord[]> result=new ArrayList<>();merge(horizontal,true,result);merge(vertical,false,result);return result;
    }
    private static boolean contains(Map<Integer,Set<Integer>> rows,int x,int y){
        Set<Integer> row=rows.get(y);return row!=null&&row.contains(x);
    }
    /** Long straight borders need one draw, rather than one draw per tile. */
    private static void merge(Map<Integer,List<Integer>> lines,boolean horizontal,List<Coord[]> result){
        for(Map.Entry<Integer,List<Integer>> line:lines.entrySet()){
            List<Integer> cells=line.getValue();Collections.sort(cells);
            int start=cells.get(0),end=start+1;
            for(int i=1;i<=cells.size();i++){
                if(i<cells.size()&&cells.get(i)==end){end++;continue;}
                int fixed=line.getKey();
                result.add(horizontal?new Coord[]{Coord.of(start,fixed),Coord.of(end,fixed)}
                    :new Coord[]{Coord.of(fixed,start),Coord.of(fixed,end)});
                if(i<cells.size()){start=cells.get(i);end=start+1;}
            }
        }
    }
    static Set<Coord> placed(Collection<ClaimSurvey.GridRecord> grids,Map<Long,Coord> origins,String tag){
        Set<Coord> tiles=new HashSet<>();
        for(ClaimSurvey.GridRecord grid:grids){
            Coord origin=origins.get(grid.id);if(origin==null)continue;
            BitSet mask=tag.equals("cplot")?grid.personal:grid.village;
            for(int cell=mask.nextSetBit(0);cell>=0;cell=mask.nextSetBit(cell+1))tiles.add(origin.add(cell%100,cell/100));
            if(tag.equals("cplot")&&grid.foreign!=null)for(int cell=grid.foreign.nextSetBit(0);cell>=0;cell=grid.foreign.nextSetBit(cell+1))tiles.add(origin.add(cell%100,cell/100));
        }return tiles;
    }
    private static final class Mask {
        final Coord origin;
        final BitSet personal,village;
        Mask(ClaimSurvey.GridRecord grid,Coord origin){
            this.origin=Coord.of(origin.x,origin.y);personal=(BitSet)grid.personal.clone();village=(BitSet)grid.village.clone();
            if(grid.foreign!=null)personal.or(grid.foreign);
        }
        public boolean equals(Object other){
            if(!(other instanceof Mask))return false;Mask mask=(Mask)other;
            return origin.equals(mask.origin)&&personal.equals(mask.personal)&&village.equals(mask.village);
        }
        public int hashCode(){return Objects.hash(origin,personal,village);}
    }
    static final class Cache {
        Object scope;long segment;int revision=Integer.MIN_VALUE;double next;
        private Map<Long,Mask> masks=Collections.emptyMap();
        private List<Coord[]> personal=Collections.emptyList(),village=Collections.emptyList();
        boolean update(Object scope,long segment,Collection<ClaimSurvey.GridRecord> grids,Map<Long,Coord> origins){
            Map<Long,Mask> current=new HashMap<>();
            for(ClaimSurvey.GridRecord grid:grids){
                Coord origin=origins.get(grid.id);if(origin!=null)current.put(grid.id,new Mask(grid,origin));
            }
            if(this.scope==scope&&this.segment==segment&&current.equals(masks))return false;
            this.scope=scope;this.segment=segment;masks=current;
            personal=outline(grids,origins,"cplot");village=outline(grids,origins,"vlg");return true;
        }
        List<Coord[]> lines(String tag){return tag.equals("cplot")?personal:village;}
    }
    private static List<Coord[]> outline(Collection<ClaimSurvey.GridRecord> grids,Map<Long,Coord> origins,String tag){
        return edges(placed(grids,origins,tag));
    }
    public static void paint(MiniMap map,GOut graphics){
        if(!enabled("cplot")&&!enabled("vlg"))return;
        GameUI gui=map.getparent(GameUI.class);MiniMap.Location location=map.displayLocation();
        if(gui==null||location==null)return;
        ClaimSurvey.prepare(gui);int revision=ClaimSurvey.generation();double now=Utils.rtime();
        Cache cache=caches.computeIfAbsent(map,key->new Cache());
        Object scope=gui.ui.sess;
        if(cache.scope!=scope||cache.segment!=location.seg.id||cache.revision!=revision||now>=cache.next){
            Map<Long,Coord> origins=ClaimSurvey.mapOrigins(gui,location.seg.id);
            if(origins==null){
                if(cache.scope!=scope||cache.segment!=location.seg.id)return;
            }else{
                cache.update(scope,location.seg.id,ClaimSurvey.allRecords(),origins);
                cache.revision=revision;cache.next=now+.5;
            }
        }
        GOut g=graphics.reclip(Coord.z,map.sz);
        for(String tag:new String[]{"vlg","cplot"})if(enabled(tag)){
            g.chcolor(tag.equals("cplot")?new Color(247,112,112,220):new Color(107,191,255,220));
            for(Coord[] edge:cache.lines(tag)){
                Coord a=map.tc2c(edge[0]),b=map.tc2c(edge[1]);if(a==null||b==null)continue;
                if(Math.max(a.x,b.x)<0||Math.min(a.x,b.x)>map.sz.x||Math.max(a.y,b.y)<0||Math.min(a.y,b.y)>map.sz.y)continue;
                Coord at=Coord.of(Math.min(a.x,b.x),Math.min(a.y,b.y));g.frect(at,Coord.of(Math.abs(b.x-a.x)+UI.scale(1),Math.abs(b.y-a.y)+UI.scale(1)));
            }g.chcolor();
        }
    }
    private ClaimBorders(){}
}
