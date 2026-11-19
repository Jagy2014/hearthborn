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

package hearthborn.mining;

import haven.*;
import haven.render.BaseColor;
import haven.render.States;
import java.util.*;

/**
 * Immutable support coverage tile mask, bound to the map grid identities observed during its construction.
 *
 * @author Jagy2014
 */
final class MiningCoverage implements MCache.LocalOverlay {
    private static final MCache.OverlayInfo ID=new MCache.OverlayInfo(){
        final Material material=new Material(new BaseColor(75,165,210,55),States.maskdepth);
        public Collection<String> tags(){return Collections.singletonList("show");}
        public Material mat(){return material;}
    };
    final MCache map;final Set<Coord> cells;final Map<Coord,Long> grids;
    MiningCoverage(MCache map,Set<Coord> cells,Map<Coord,Long> grids){this.map=map;this.cells=Collections.unmodifiableSet(new HashSet<>(cells));this.grids=Collections.unmodifiableMap(new HashMap<>(grids));}
    static MiningCoverage build(GameUI gui,MiningSafety.Survey survey){
        MCache map=gui.ui.sess.glob.map;Coord player=gui.map.player().rc.floor(MCache.tilesz);
        Set<Coord> candidates=new HashSet<>(),cells=new HashSet<>();Map<Coord,Long> grids=new HashMap<>();
        for(MiningSafety.Support support:survey.supports){
            Coord center=support.at.floor(MCache.tilesz);int radius=(int)Math.ceil(support.radius/MCache.tilesz.x)+1;
            int left=Math.max(center.x-radius,player.x-45),right=Math.min(center.x+radius,player.x+45);
            int top=Math.max(center.y-radius,player.y-45),bottom=Math.min(center.y+radius,player.y+45);
            for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++)candidates.add(Coord.of(x,y));
        }
        for(Coord tile:candidates)if(covered(tile,survey.supports)){
            try {
                MCache.Grid grid=map.getgridt(tile);if(grid==null)continue;
                cells.add(tile);grids.put(grid.gc,grid.id);
            }catch(Loading unavailable){}
        }
        return new MiningCoverage(map,cells,grids);
    }
    static boolean covered(Coord tile,List<MiningSafety.Support> supports){
        Coord2d point=MiningSafety.center(tile);
        for(MiningSafety.Support support:supports)if(point.dist(support.at)<=support.radius)return true;
        return false;
    }
    boolean same(MiningCoverage other){return cells.equals(other.cells)&&grids.equals(other.grids);}
    public MCache.OverlayInfo id(){return ID;}
    public boolean filter(Area area){for(Coord tile:cells)if(tile.isect(area.ul,area.sz()))return false;return true;}
    public void fill(Area area,boolean[] mask){
        for(Coord tile:area)if(cells.contains(tile))try {
            MCache.Grid grid=map.getgridt(tile);
            if(grid!=null&&Objects.equals(grids.get(grid.gc),grid.id))mask[area.ri(tile)]=true;
        }catch(Loading unavailable){}
    }
}
