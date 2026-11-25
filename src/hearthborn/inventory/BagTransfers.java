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

package hearthborn.inventory;

import haven.*;
import hearthborn.L10n;
import java.util.*;

/**
 * Moves from a bag or the main inventory into an open storage window, sent at once with no cursor.
 *
 * @author Jagy2014
 */
public final class BagTransfers {
    private static final String[] STORAGE={"Cupboard","Chest","Large Chest","Wooden Chest","Bone Chest","Exquisite Chest","Coffer","Crate","Linen Crate","Metal Cabinet","Stone Casket","Study Desk","Study Table"};
    private BagTransfers(){}
    static boolean studyDesk(String title){
        if(title==null)return false;
        if(title.equals("Study Desk")||title.equals("Study Table")||title.endsWith("'s study"))return true;
        String marker="__HB_OWNER__";
        String translated=L10n.text("window",marker+"'s study");
        int owner=translated.indexOf(marker);
        return owner>=0&&title.startsWith(translated.substring(0,owner))&&title.endsWith(translated.substring(owner+marker.length()));
    }
    static boolean herbalist(String title){return titled(title,"Herbalist Table");}
    static boolean gridiron(String title){
        return titled(title,"Gridiron")||titled(title,"Fireplace")||titled(title,"Brazier")
            ||titled(title,"Решетка-гриль");
    }
    private static boolean titled(String title,String name){
        if(title==null||name==null||name.isEmpty())return false;
        if(title.equalsIgnoreCase(name))return true;
        String translated=L10n.text("window",name);
        if(translated!=null&&title.equals(translated))return true;
        return follows(title,name)||follows(title,translated);
    }
    private static boolean follows(String title,String name){
        return name!=null&&!name.isEmpty()&&title.length()>name.length()&&title.regionMatches(true,0,name,0,name.length())&&title.charAt(name.length())==' ';
    }
    static Inventory storage(GameUI gui){return storage(gui,null);}
    static Inventory storage(GameUI gui,Inventory except){return storage(gui,except,false);}
    private static Inventory storage(GameUI gui,Inventory except,boolean includeGrill){
        if(includeGrill) {
            Inventory grill = cookingInventory(gui, except);
            if(grill != null) return grill;
        }
        for(Widget child=gui.lchild;child!=null;child=child.prev)if(child instanceof Window && child.visible() && child.tvisible() && !(child instanceof GItem.ContentsWindow)){
            Window window=(Window)child;boolean match=studyDesk(window.cap)||herbalist(window.cap);
            for(String name:STORAGE)if(name.equals(window.cap)||L10n.text("window",name).equals(window.cap)){match=true;break;}
            if(!match)continue;
            for(Inventory inventory:window.children(Inventory.class))
                if(inventory!=except&&inventory.tvisible()&&!hearthborn.ui.StudyProgress.isStudy(inventory))return inventory;
        }
        return null;
    }
    private static Inventory cookingInventory(GameUI gui, Inventory except) {
        for(Widget child = gui.lchild; child != null; child = child.prev) {
            if(!(child instanceof Window) || child instanceof GItem.ContentsWindow || !child.visible() || !child.tvisible()) continue;
            if(!gridiron(((Window)child).cap)) continue;
            for(Inventory inventory : child.children(Inventory.class)) {
                if(inventory != except && inventory.getparent(Window.class) == child
                    && inventory.tvisible() && inventory.wdgid() >= 0) return inventory;
            }
        }
        return null;
    }

    /**
     * Sends every item to the open storage in this very tick, naming the storage as the destination. The server leaves
     * what does not fit where it was. Returns false when this is not a move to a known storage, so that the ordinary
     * transfer takes over.
     */
    public static boolean transfer(WItem source,List<WItem> items){return transfer(source,items,false);}
    static boolean transfer(WItem source,List<WItem> items,boolean includeGrill){
        if(!(source.parent instanceof Inventory))return false;
        GameUI gui=source.getparent(GameUI.class);if(gui==null)return false;
        Inventory target=storage(gui,(Inventory)source.parent,includeGrill);if(target==null||target==source.parent||target.wdgid()<0)return false;
        boolean bag=source.getparent(GItem.ContentsWindow.class)!=null;
        Window destination=target.getparent(Window.class);
        boolean table=destination!=null&&herbalist(destination.cap);
        if(!bag&&!table&&(source.parent!=gui.maininv||destination==null||!(studyDesk(destination.cap)||gridiron(destination.cap))))return false;
        boolean grill = destination != null && gridiron(destination.cap);
        int targetId = target.wdgid();
        Set<GItem> sent = new HashSet<>();
        for(WItem item : items) {
            if(!(item.parent instanceof Inventory) || item.parent == target || !sent.add(item.item)) continue;
            List<GItem> contents = grill ? InventoryActions.stackContents(item.item) : Collections.emptyList();
            if(contents.isEmpty())
                item.item.wdgmsg("invxf2", 0, 1, targetId);
            else
                contents.get(0).wdgmsg("invxf2", 0, contents.size(), targetId);
        }
        return true;
    }
    public static Coord free(Inventory inventory,Coord size){
        for(int y=0;y+size.y<=inventory.isz.y;y++)for(int x=0;x+size.x<=inventory.isz.x;x++){
            boolean available=true;
            for(int yy=y;yy<y+size.y;yy++)for(int xx=x;xx<x+size.x;xx++)
                if(inventory.sqmask!=null&&inventory.sqmask[yy*inventory.isz.x+xx])available=false;
            for(WItem occupied:InventoryActions.items(inventory)){
                Coord at=InventoryActions.cell(occupied),extent=occupied.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
                if(x<at.x+extent.x&&x+size.x>at.x&&y<at.y+extent.y&&y+size.y>at.y)available=false;
            }
            if(available)return Coord.of(x,y);
        }
        return null;
    }
    public static boolean running(GameUI gui){return OverviewTransfer.running(gui);}
    public static void cancel(GameUI gui){OverviewTransfer.cancel(gui);}
    public static void tick(GameUI gui){OverviewTransfer.tick(gui);}
    public static void dispose(GameUI gui){OverviewTransfer.cancel(gui);}
}
