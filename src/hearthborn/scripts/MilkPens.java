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
import java.util.*;

/**
 * Persistent pen rectangles. A launch chooses by the player's position, never by proximity.
 *
 * @author Jagy2014
 */
final class MilkPens implements Serializable {
    private static final long serialVersionUID=1L;
    final List<SavedArea> areas=new ArrayList<>();
    // Absent in older records. Preserve the serialized rectangle list and class identity.
    private Map<String,String> names=new HashMap<>();
    static MilkPens load(GameUI gui){
        String key=SavedArea.key(gui,"milk-pens");
        if(key==null)throw new IllegalStateException("Character and world are not ready yet.");
        Object raw=LocalStore.read(key);
        if(raw!=null){
            if(!(raw instanceof MilkPens))throw new IllegalStateException("Could not read the saved collection areas.");
            MilkPens pens=(MilkPens)raw;
            if(pens.areas==null||pens.areas.size()>64)throw new IllegalStateException("Could not read the saved collection areas.");
            for(SavedArea area:pens.areas)if(area==null||area.width<=0||area.height<=0||area.x<0||area.y<0||area.x>=MCache.cmaps.x||area.y>=MCache.cmaps.y)
                throw new IllegalStateException("Could not read the saved collection areas.");
            if(pens.names==null)pens.names=new HashMap<>();
            return pens;
        }
        // The old single-pen record remains untouched, including on a failed new save.
        MilkPens pens=new MilkPens();SavedArea legacy=SavedArea.load(gui,"milk");
        if(legacy!=null)pens.areas.add(legacy);return pens;
    }
    SavedArea current(GameUI gui){
        if(gui.map==null||gui.map.player()==null)return null;
        Coord tile=gui.map.player().rc.floor(MCache.tilesz);SavedArea found=null;
        for(SavedArea area:areas){
            Area bounds=area.bounds(gui.ui.sess.glob.map);
            if(bounds!=null&&bounds.contains(tile)){
                if(found!=null)throw new IllegalStateException("Saved collection areas overlap. Check their boundaries.");
                found=area;
            }
        }
        return found;
    }
    static boolean same(SavedArea a,SavedArea b){return a!=null&&b!=null&&a.grid==b.grid&&a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    private static String identity(SavedArea area){return area.grid+":"+area.x+":"+area.y+":"+area.width+":"+area.height;}
    SavedArea find(SavedArea area){for(SavedArea saved:areas)if(same(saved,area))return saved;return null;}
    String name(SavedArea area){
        String saved=names.get(identity(area));
        return saved==null?hearthborn.L10n.format("Area %d",areas.indexOf(find(area))+1):saved;
    }
    static void rename(GameUI gui,SavedArea area,String name){
        String text=name.trim();
        if(text.isEmpty()||text.length()>40||text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalStateException("Enter an area name between 1 and 40 characters.");
        MilkPens pens=load(gui);
        if(pens.find(area)==null)throw new IllegalStateException("The saved area changed. Reopen milk settings.");
        pens.names.put(identity(area),text);pens.write(gui);
    }
    static void delete(GameUI gui,SavedArea area){
        MilkPens pens=load(gui);SavedArea found=pens.find(area);
        if(found==null)throw new IllegalStateException("The saved area changed. Reopen milk settings.");
        pens.areas.remove(found);pens.names.remove(identity(found));pens.write(gui);
    }
    private static boolean overlap(Area a,Area b){return a.ul.x<b.br.x&&a.br.x>b.ul.x&&a.ul.y<b.br.y&&a.br.y>b.ul.y;}
    static void save(GameUI gui,Area selected,SavedArea replace){
        MilkPens pens=load(gui);MCache map=gui.ui.sess.glob.map;
        SavedArea saved=SavedArea.capture(map,selected);
        if(saved.resolve(map)==null)throw new Loading();
        if(gui.map==null||gui.map.player()==null||!selected.contains(gui.map.player().rc.floor(MCache.tilesz)))
            throw new IllegalStateException("Stand inside the collection area you are selecting.");
        int index=-1;
        for(int i=0;i<pens.areas.size();i++){
            SavedArea old=pens.areas.get(i);
            if(replace!=null&&same(old,replace)){index=i;continue;}
            Area bounds=old.bounds(map);
            if(bounds!=null&&overlap(bounds,selected))throw new IllegalStateException("The selected area overlaps another saved collection area.");
        }
        if(replace!=null&&index<0)throw new IllegalStateException("The saved area changed. Reopen milk settings.");
        if(index<0){if(pens.areas.size()>=64)throw new IllegalStateException("Too many collection areas saved.");pens.areas.add(saved);}
        else{
            String name=pens.names.remove(identity(pens.areas.get(index)));
            pens.areas.set(index,saved);
            if(name!=null)pens.names.put(identity(saved),name);
        }
        pens.write(gui);
    }
    private void write(GameUI gui){
        LocalStore.write(SavedArea.key(gui,"milk-pens"),this);
        MilkPens written=load(gui);
        if(written.areas.size()!=areas.size()||!written.names.equals(names))throw new IllegalStateException("Could not save the area. Check the client data directory.");
        for(int i=0;i<areas.size();i++)if(!same(written.areas.get(i),areas.get(i)))
            throw new IllegalStateException("Could not save the area. Check the client data directory.");
    }
}
