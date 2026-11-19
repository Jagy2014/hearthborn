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

package hearthborn.ui;

import haven.*;
import java.util.*;

/**
 * Persist client-only menu pages without asking the server to load virtual resources.
 *
 * @author Jagy2014
 */
public final class LocalActionBelt {
    private static final Map<GameUI,Map<Integer,String>> bindings=new WeakHashMap<>();
    private static String key(GameUI gui){return "hearthborn/local-belt/"+gui.chrid;}
    private static Map<Integer,String> bindings(GameUI gui){
        Map<Integer,String> saved=bindings.get(gui);
        if(saved==null){
            saved=new HashMap<>();boolean migrated=false;
            for(String entry:Utils.getprefsl(key(gui),new String[0]))try {
                int split=entry.indexOf(' '),slot=Integer.parseInt(entry.substring(0,split));String previous=entry.substring(split+1),path=XtensionsMenu.canonicalAction(previous);
                if(slot>=0&&slot<gui.belt.length&&(slot>=144||path.startsWith("hearthborn/"))){saved.put(slot,path);migrated|=!path.equals(previous);}
            }catch(RuntimeException ignored){}
            bindings.put(gui,saved);
            if(migrated)save(gui);
        }
        return saved;
    }
    private static void save(GameUI gui){
        List<String> entries=new ArrayList<>();for(Map.Entry<Integer,String> entry:bindings(gui).entrySet())entries.add(entry.getKey()+" "+entry.getValue());
        Collections.sort(entries);Utils.setprefsl(key(gui),entries);
    }
    public static boolean bind(GameUI gui,int slot,MenuGrid.Pagina page){
        String path=page.res().name;if(slot<144&&!path.startsWith("hearthborn/"))return false;
        bindings(gui).put(slot,path);save(gui);gui.belt[slot]=new GameUI.PagBeltSlot(slot,page);return true;
    }
    public static void forget(GameUI gui,int slot){
        if(bindings(gui).remove(slot)!=null){gui.belt[slot]=null;save(gui);}
    }
    private static final Map<GameUI,Double> scanned=new WeakHashMap<>();
    /** Matching every menu page against every saved slot is wasteful at frame rate; a quarter of a second is quick enough. */
    private static boolean due(GameUI gui){
        double now=Utils.rtime();Double last=scanned.get(gui);
        if(last!=null&&now-last<.25)return false;
        scanned.put(gui,now);return true;
    }
    public static void tick(GameUI gui){
        if(gui.menu==null)return;
        Map<Integer,String> saved=bindings(gui);if(saved.isEmpty()||!due(gui))return;
        synchronized(gui.menu.paginae){
            for(MenuGrid.Pagina page:gui.menu.paginae)try {
                String path=page.res().name;
                for(Map.Entry<Integer,String> entry:saved.entrySet())if(entry.getValue().equals(path)){
                    int slot=entry.getKey();GameUI.BeltSlot current=gui.belt[slot];
                    if(!(current instanceof GameUI.PagBeltSlot)||((GameUI.PagBeltSlot)current).pag!=page)gui.belt[slot]=new GameUI.PagBeltSlot(slot,page);
                }
            }catch(Loading ignored){}
        }
    }
    public static void dispose(GameUI gui){bindings.remove(gui);scanned.remove(gui);}
    private LocalActionBelt(){}
}
