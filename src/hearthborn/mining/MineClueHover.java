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
import hearthborn.L10n;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hover inspection only: highlights eight neighbors without classifying any tile as safe.
 *
 * @author Jagy2014
 */
final class MineClueHover {
    private static final MCache.OverlayInfo ID=new MCache.OverlayInfo(){
        final Material fill=new Material(new BaseColor(230,235,245,55),States.maskdepth);
        final Material edge=new Material(new BaseColor(130,205,245,155),States.maskdepth);
        public Collection<String> tags(){return Collections.singletonList("show");}
        public Material mat(){return fill;}public Material omat(){return edge;}
    };
    Coord pointer,tile;long grid,generation;double sampled;boolean pending;
    MCache map;MCache.LocalOverlay overlay;
    void clear(){generation++;pending=false;pointer=null;clearDisplay();}
    void clearDisplay(){if(overlay!=null&&map!=null)map.remove(overlay);overlay=null;tile=null;}
    static void tick(GameUI gui){
        Minesweeper mine=gui.minesweeper;if(mine.hover==null)mine.hover=new MineClueHover();MineClueHover hover=mine.hover;
        if(gui.map==null||!Minesweeper.enabled()){hover.clear();return;}
        Coord at=gui.map.rootxlate(gui.ui.mc);
        if(!at.isect(Coord.z,gui.map.sz)){hover.clear();return;}
        if(!at.equals(hover.pointer)){hover.pointer=at;hover.generation++;hover.clearDisplay();}
        double now=Utils.rtime();if(hover.pending||now-hover.sampled<.15)return;hover.sampled=now;hover.pending=true;long generation=hover.generation;
        gui.map.new Maptest(at){
            protected void hit(Coord pc,Coord2d world){
                hover.pending=false;if(generation!=hover.generation)return;
                try {hover.show(gui,world.floor(MCache.tilesz));}catch(Loading unavailable){hover.clearDisplay();}
            }
            protected void nohit(Coord pc){hover.pending=false;if(generation==hover.generation)hover.clearDisplay();}
        }.run();
    }
    void show(GameUI gui,Coord at){
        MCache current=gui.ui.sess.glob.map;Minesweeper mine=gui.minesweeper;Minesweeper.Clue clue=mine.clue(current,at);
        if(clue==null){clearDisplay();return;}
        if(!at.equals(tile)||grid!=clue.grid||map!=current){
            clearDisplay();tile=at;grid=clue.grid;map=current;
            Set<Coord> neighbors=new HashSet<>();Map<Coord,Long> ids=new HashMap<>();
            for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if(x!=0||y!=0){Coord cell=at.add(x,y);try{MCache.Grid g=current.getgridt(cell);if(g!=null){neighbors.add(cell);ids.put(g.gc,g.id);}}catch(Loading unavailable){}}
            overlay=new NeighborOverlay(current,neighbors,ids);current.add(overlay);
        }
    }
    static String describe(Minesweeper mine,MCache map,Coord at,Minesweeper.Clue clue){
        StringBuilder out=new StringBuilder(L10n.format("Dust clue: %d",clue.count));
        out.append(" — ").append(L10n.tr(clue.pinned?"Pinned observation":"Temporary observation"));
        if(clue.mark!=0)out.append("\n").append(L10n.tr(clue.mark==Minesweeper.FLAG_DANGER?"Your mark: cross":clue.mark==Minesweeper.FLAG_SAFE?"Your mark: check mark":"Your mark: question mark"));
        ClueObservation observed=clue.observation;
        if(observed==null)out.append("\n").append(L10n.tr("Observation time and terrain are unknown (older clue)."));
        else {
            String time=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(Instant.ofEpochMilli(observed.time).atZone(ZoneId.systemDefault()));
            out.append("\n").append(L10n.format("Observed: %s",time));
            out.append("\n").append(L10n.format("Then nearby: %d open, %d wall, %d unknown.",Integer.bitCount(observed.open),Integer.bitCount(observed.walls),8-Integer.bitCount(observed.open|observed.walls)));
        }
        Map<Coord,Integer> clues=new LinkedHashMap<>();
        for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)try {Minesweeper.Clue neighbor=mine.clue(map,at.add(x,y));if(neighbor!=null)clues.put(at.add(x,y),neighbor.count);}catch(Loading unavailable){}
        ClueConsistency.Result check=ClueConsistency.check(clues);
        out.append("\n\n").append(L10n.tr(check==ClueConsistency.Result.CONFLICT?"Nearby clues conflict with the eight-neighbor model.":check==ClueConsistency.Result.UNKNOWN?"Too little certainty to complete the consistency check.":"No conflict found in nearby clues; this does not prove safety."));
        out.append("\n").append(L10n.tr("Open tiles can still count. Manual marks are not server evidence."));
        return out.toString();
    }
    static final class NeighborOverlay implements MCache.LocalOverlay {
        final MCache map;final Set<Coord> cells;final Map<Coord,Long> ids;
        NeighborOverlay(MCache map,Set<Coord> cells,Map<Coord,Long> ids){this.map=map;this.cells=cells;this.ids=ids;}
        public MCache.OverlayInfo id(){return ID;}
        public boolean filter(Area area){for(Coord tile:cells)if(tile.isect(area.ul,area.sz()))return false;return true;}
        public void fill(Area area,boolean[] mask){for(Coord tile:cells)if(tile.isect(area.ul,area.sz()))try{MCache.Grid grid=map.getgridt(tile);if(grid!=null&&Objects.equals(ids.get(grid.gc),grid.id))mask[area.ri(tile)]=true;}catch(Loading unavailable){}}
    }
}
