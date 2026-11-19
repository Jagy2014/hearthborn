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
import hearthborn.io.LocalStore;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A tile rectangle anchored to a stable map grid, scoped to one character/world.
 *
 * @author Jagy2014
 */
final class SavedArea implements Serializable {
    private static final long serialVersionUID=1L;
    final long grid;
    final int x,y,width,height;
    SavedArea(long grid,int x,int y,int width,int height){this.grid=grid;this.x=x;this.y=y;this.width=width;this.height=height;}
    static String key(GameUI gui,String kind){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.chrid==null||gui.chrid.isBlank()||gui.genus==null||gui.genus.isBlank())return null;
        String user=gui.ui.sess.user==null?"":gui.ui.sess.user.name;
        String identity=user+"\n"+gui.genus+"\n"+gui.chrid;
        return "script-area-v1-"+kind+"-"+UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
    static SavedArea load(GameUI gui,String kind){
        String key=key(gui,kind);if(key==null)return null;
        Object value=LocalStore.read(key);
        if(!(value instanceof SavedArea))return null;
        SavedArea area=(SavedArea)value;
        return area.width>0&&area.height>0&&area.x>=0&&area.y>=0&&area.x<MCache.cmaps.x&&area.y<MCache.cmaps.y?area:null;
    }
    static SavedArea capture(MCache map,Area area){
        if(!area.positive())throw new IllegalStateException("Select a non-empty area.");
        MCache.Grid anchor=map.getgridt(area.ul);
        return new SavedArea(anchor.id,area.ul.x-anchor.ul.x,area.ul.y-anchor.ul.y,area.sz().x,area.sz().y);
    }
    static void save(GameUI gui,String kind,Area area){
        String key=key(gui,kind);
        if(key==null)throw new IllegalStateException("Character and world are not ready yet.");
        SavedArea captured=capture(gui.ui.sess.glob.map,area);
        if(captured.resolve(gui.ui.sess.glob.map)==null)throw new Loading();
        LocalStore.write(key,captured);
        SavedArea written=load(gui,kind);
        if(written==null||written.grid!=captured.grid||written.x!=captured.x||written.y!=captured.y||written.width!=captured.width||written.height!=captured.height)
            throw new IllegalStateException("Could not save the area. Check the client data directory.");
    }
    /** Locate the rectangle without assuming that all its covered grids are loaded. */
    Area bounds(MCache map){
        for(MCache.Grid anchor:map.loadedGrids())if(anchor.id==grid)return Area.sized(anchor.ul.add(x,y),Coord.of(width,height));
        return null;
    }
    Area resolve(MCache map){
        Collection<MCache.Grid> loaded=map.loadedGrids();
        MCache.Grid anchor=null;Set<Coord> grids=new HashSet<>();
        for(MCache.Grid current:loaded){grids.add(current.gc);if(current.id==grid)anchor=current;}
        if(anchor==null)return null;
        Area result=Area.sized(anchor.ul.add(x,y),Coord.of(width,height));
        if(!result.positive())return null;
        Coord first=result.ul.div(MCache.cmaps),last=result.br.sub(1,1).div(MCache.cmaps);
        if(((long)last.x-first.x+1)*((long)last.y-first.y+1)>grids.size())return null;
        for(int gy=first.y;gy<=last.y;gy++)for(int gx=first.x;gx<=last.x;gx++)
            if(!grids.contains(Coord.of(gx,gy)))return null;
        return result;
    }
}
