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
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.inventory.InventoryActions;
import java.util.*;

/** Shared, server-confirmed exchanges for inventory double-clicks and equipment actions. */
public final class HandEquip {
    static final int BELT=5,LEFT=6,RIGHT=7;
    private static final double CLICK_INTERVAL=doubleClickInterval();
    private static final int HANDS=(1<<LEFT)|(1<<RIGHT);
    private static final Map<String,Integer> occupied=new HashMap<>();
    private static final Set<String> TWO_HANDED = new HashSet<>(Arrays.asList(
        "huntersbow", "rangersbow", "boarspear", "b12axe", "cutblade", "giantneedle",
        "pickaxe", "sledgehammer", "scythe", "shovel-m", "shovel-t", "shovel-w",
        "saw-m", "bonesaw", "smithshammer"));
    private static final Set<String> ONE_HANDED = new HashSet<>(Arrays.asList(
        "travellerssack", "wanderersbindle", "roundshield", "bronzesword", "fyrdsword",
        "hirdsword", "stoneaxe", "axe-m", "woodsmansaxe"));
    private static GItem clickItem;
    private static double clickAt;
    public static double doubleClickInterval() {
        try {Object value=java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
            if(value instanceof Number)return Math.max(.15,((Number)value).doubleValue()/1000.0);
        } catch(java.awt.HeadlessException ignored) {}
        return .35;
    }
    static WItem hand(GameUI gui,int slot) {
        Equipory gear=gui==null?null:gui.equipment();if(gear==null)return null;
        for(WItem view:gear.children(WItem.class))if(view.c.equals(Equipory.ecoords[slot].add(1,1)))return view;
        return null;
    }
    static String base(GItem item){return item.resource().basename();}
    public static String canon(GItem item){return item.resource().name.replace("gfx/invobjs/small/","gfx/invobjs/");}
    static int slots(GItem item) {
        Equipory.SlotInfo info=ItemInfo.find(Equipory.SlotInfo.class,item.info());
        if(info!=null)return info.slots()&HANDS;
        return knownSlots(item)!=0?HANDS:0;
    }
    public static boolean forHands(GItem item) {
        if(item==null)return false;
        try{return slots(item)!=0;}catch(Loading pending){return false;}
    }
    /** Actual occupied slots, unlike the tooltip's possible slots, tell us whether both hands are needed. */
    public static void observed(Equipory gear,GItem item,Object[] indexes) {
        GameUI gui=gear.getparent(GameUI.class);
        if(gui!=null&&gui.equipment()==gear)try {
            int mask=0;for(Object index:indexes){int slot=Utils.iv(index);if(slot==LEFT||slot==RIGHT)mask|=1<<slot;}
            if(mask!=0){String key=canon(item);occupied.put(key,mask==HANDS?HANDS:1);Utils.setprefi("hearthborn/hand-slots/"+key,mask==HANDS?HANDS:1);}
        }catch(Loading pending){}
        hearthborn.ui.HandMenu.invalidate();
    }
    static int knownSlots(GItem item){
        String key=canon(item);
        Integer seen=occupied.get(key);
        if(seen!=null)return seen;
        int saved=Utils.getprefi("hearthborn/hand-slots/"+key,0);
        if(saved!=0)return saved;
        String name=key.startsWith("gfx/invobjs/")?key.substring("gfx/invobjs/".length()):"";
        return TWO_HANDED.contains(name)?HANDS:ONE_HANDED.contains(name)?1:0;
    }
    static boolean twoHander(GItem item){return knownSlots(item)==HANDS;}
    public static boolean equipped(GameUI gui,String resource) {
        for(int slot:new int[]{LEFT,RIGHT}){WItem view=hand(gui,slot);if(view!=null)try{if(canon(view.item).equals(resource))return true;}catch(Loading pending){}}
        return false;
    }
    static boolean holding(GameUI gui,String name) {
        for(int slot:new int[]{LEFT,RIGHT}){WItem view=hand(gui,slot);if(view!=null&&base(view.item).equals(name))return true;}return false;
    }
    static Inventory belt(GameUI gui) {
        WItem view=hand(gui,BELT);
        if(view==null)return null;
        if(view.item.contents instanceof Inventory)return (Inventory)view.item.contents;
        GItem.ContentsWindow window=view.item.contentswnd;
        return window!=null&&window.inv instanceof Inventory?(Inventory)window.inv:null;
    }
    public static List<GItem> carried(GameUI gui) {
        List<GItem> result=new ArrayList<>();for(GItem item:ShortcutRunner.owned(gui))if(item.parent instanceof Inventory)result.add(item);return result;
    }
    static Coord cell(GItem item) {
        if(item.parent instanceof Inventory)for(WItem view:InventoryActions.items((Inventory)item.parent))if(view.item==item)return InventoryActions.cell(view);
        return null;
    }
    static Coord size(GItem item) {
        GSprite sprite=item.spr();if(sprite==null)throw new Loading();
        return sprite.sz().add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
    }
    static double quality(GItem item){return GameObjects.quality(item);}
    public static GItem best(GameUI gui,String resource) {
        GItem best=null;double q=-1;for(GItem item:carried(gui))try {
            if(canon(item).equals(resource)&&forHands(item)){double next=quality(item);if(best==null||next>q){best=item;q=next;}}
        }catch(Loading pending){}return best;
    }
    private static boolean personal(GameUI gui,Inventory inventory) {
        if(inventory==gui.maininv||inventory==belt(gui))return true;
        GItem.ContentsWindow window=inventory.getparent(GItem.ContentsWindow.class);
        return window!=null&&ShortcutRunner.owned(gui).stream().anyMatch(item->item.contents==inventory);
    }
    public static boolean click(GameUI gui,GItem item) {
        if(gui==null||item==null||!forHands(item)||!(item.parent instanceof Inventory)||!personal(gui,(Inventory)item.parent))return false;
        double now=Utils.rtime();
        if(item==clickItem&&now-clickAt<CLICK_INTERVAL){clickItem=null;swap(gui,item);return true;}
        if(clickItem!=null&&clickItem!=item&&clickItem.parent instanceof Inventory)clickItem.wdgmsg("take",Coord.z);
        clickItem=item;clickAt=now;return true;
    }
    public static boolean flushTake(GItem item){if(item!=clickItem||Utils.rtime()-clickAt<CLICK_INTERVAL)return false;clickItem=null;return true;}
    public static boolean pendingClick(GItem item){return item!=null&&item==clickItem;}
    public static void cancelClick(GItem item){if(item==clickItem)clickItem=null;}
    static int slotFor(GItem item,GameUI gui) {
        int mask=slots(item);if((mask&(1<<LEFT))==0)return RIGHT;if((mask&(1<<RIGHT))==0)return LEFT;
        if(twoHander(item))return hand(gui,LEFT)==null&&hand(gui,RIGHT)!=null?RIGHT:LEFT;
        if(hand(gui,LEFT)==null)return LEFT;if(hand(gui,RIGHT)==null)return RIGHT;return LEFT;
    }
    /** Reserve real rectangular space, respecting disabled cells and previously reserved returns. */
    static final class Space {
        final Inventory inventory;final boolean[] cells;final Coord bounds;final boolean belt;
        Space(Inventory inventory,GItem ignore){this(inventory,ignore,Coord.z);}
        Space(Inventory inventory,GItem ignore,Coord shrink){this(inventory,ignore,shrink,false);}
        Space(Inventory inventory,GItem ignore,Coord shrink,boolean belt){
            this.inventory=inventory;this.belt=belt;bounds=inventory.isz.sub(shrink);cells=new boolean[inventory.isz.x*inventory.isz.y];
            if(inventory.sqmask!=null)System.arraycopy(inventory.sqmask,0,cells,0,cells.length);
            for(WItem view:InventoryActions.items(inventory))if(view.item!=ignore)mark(InventoryActions.cell(view),footprint(view.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz)));
        }
        Coord footprint(Coord size){return belt?Coord.of(1,1):size;}
        void mark(Coord at,Coord size){for(int y=at.y;y<at.y+size.y;y++)for(int x=at.x;x<at.x+size.x;x++)if(x>=0&&y>=0&&x<inventory.isz.x&&y<inventory.isz.y)cells[x+y*inventory.isz.x]=true;}
        Coord reserve(Coord size,Coord preferred) {
            size=footprint(size);
            if(preferred!=null&&fits(preferred,size)){mark(preferred,size);return preferred;}
            for(int y=0;y+size.y<=bounds.y;y++)for(int x=0;x+size.x<=bounds.x;x++){Coord at=Coord.of(x,y);if(fits(at,size)){mark(at,size);return at;}}return null;
        }
        boolean fits(Coord at,Coord size){if(at.x<0||at.y<0||at.x+size.x>bounds.x||at.y+size.y>bounds.y)return false;
            for(int y=at.y;y<at.y+size.y;y++)for(int x=at.x;x<at.x+size.x;x++)if(cells[x+y*inventory.isz.x])return false;return true;}
    }
    static final class Place {final Inventory inventory;final Coord cell;Place(Inventory inventory,Coord cell){this.inventory=inventory;this.cell=cell;}}
    static Place place(List<Space> spaces,GItem item,Inventory source,Coord origin) {
        Coord size=size(item);for(Space space:spaces){Coord at=space.reserve(size,space.inventory==source?origin:null);if(at!=null)return new Place(space.inventory,at);}return null;
    }
    static List<Space> spaces(GameUI gui,GItem ignore,Inventory source) {
        return spaces(gui,ignore,source,Coord.z);
    }
    private static List<Space> spaces(GameUI gui,GItem ignore,Inventory source,Coord shrink) {
        List<Inventory> inventories=new ArrayList<>();
        Inventory belt=belt(gui);if(belt!=null)inventories.add(belt);
        if(source!=null&&!inventories.contains(source))inventories.add(source);
        if(gui.maininv!=null&&!inventories.contains(gui.maininv))inventories.add(gui.maininv);
        List<Space> result=new ArrayList<>();for(Inventory inventory:inventories)result.add(new Space(inventory,ignore,inventory==gui.maininv?shrink:Coord.z,inventory==belt));return result;
    }
    static final class Piece {
        final GItem original;final String resource;final Coord extent;GItem cursor;
        Piece(GItem item){original=item;resource=canon(item);extent=size(item);}
        boolean matches(GItem item){try{return canon(item).equals(resource);}catch(Loading pending){return false;}}
        boolean take(GameUI gui){if(gui.vhand==null||!matches(gui.vhand.item))return false;cursor=gui.vhand.item;return true;}
    }
    static void put(GameUI gui,Piece piece,Place target) {
        if(gui.vhand==null||gui.vhand.item!=piece.cursor)throw new IllegalStateException("Held item changed.");
        List<Space> destinations=spaces(gui,null,target.inventory);
        // Keep a pre-reserved slot for the second return free when putting away the first.
        destinations.sort(Comparator.comparing(space->space.inventory!=target.inventory));
        for(Space live:destinations){
            Coord at=live.reserve(piece.extent,live.inventory==target.inventory?target.cell:null);
            if(at!=null){live.inventory.wdgmsg("drop",at);return;}
        }
        ShortcutRunner.cancel(gui);
        gui.ui.msg(L10n.tr("No room to put the equipment away. Make space and place the held item in a bag."));
    }
    static Coord inventoryBonus(GItem item,int slot) {
        if(item==null)return Coord.z;
        switch(canon(item)){
        case "gfx/invobjs/travellerssack": return Coord.of(1,1);
        case "gfx/invobjs/wanderersbindle": return slot==LEFT?Coord.of(1,0):Coord.of(0,1);
        default: return Coord.z;
        }
    }
    static boolean queue(ShortcutRunner bot,GameUI gui,GItem item,int slot) {
        if(gui.vhand!=null||!(item.parent instanceof Inventory)||(slots(item)&(1<<slot))==0)return false;
        Inventory source=(Inventory)item.parent;Coord origin=cell(item);if(origin==null)return false;
        // Unknown handedness is conservative only when both hands contain distinct items.
        // The server's first equipment update teaches subsequent swaps the actual requirement.
        boolean both=twoHander(item)||knownSlots(item)==0&&slots(item)==HANDS;
        WItem target=hand(gui,slot),opposite=hand(gui,slot==LEFT?RIGHT:LEFT);
        GItem displaced=target==null?null:target.item,extra=both&&opposite!=null&&opposite.item!=displaced?opposite.item:null;
        final int destinationSlot=slot;
        int otherSlot=slot==LEFT?RIGHT:LEFT;
        Coord firstShrink=inventoryBonus(extra,otherSlot);
        Coord finalShrink=firstShrink.add(inventoryBonus(displaced,slot));
        // A first return must still fit after the second sack is removed.
        List<Space> before=spaces(gui,null,source,finalShrink);Place extraPlace=extra==null?null:place(before,extra,source,null);
        if(extra!=null&&extraPlace==null)return noRoom(gui);
        List<Space> after=spaces(gui,item,source,finalShrink);
        if(extraPlace!=null)for(Space space:after)if(space.inventory==extraPlace.inventory)space.mark(extraPlace.cell,space.footprint(size(extra)));
        Place mainPlace=displaced==null?null:place(after,displaced,source,origin);
        if(displaced!=null&&mainPlace==null)return noRoom(gui);
        Piece incoming=new Piece(item),old=displaced==null?null:new Piece(displaced);
        if(extra!=null){Piece first=new Piece(extra);Place destination=extraPlace;
            bot.add(()->{if(gui.vhand!=null)throw new IllegalStateException("Cursor changed.");first.original.wdgmsg("take",Coord.z);},()->first.take(gui),3);
            bot.add(()->put(gui,first,destination),()->gui.vhand==null,3);
        }
        bot.add(()->{if(gui.vhand!=null||item.parent!=source)throw new IllegalStateException("Equipment moved before the swap.");item.wdgmsg("take",Coord.z);},()->incoming.take(gui),3);
        bot.add(()->{if(gui.vhand==null||gui.vhand.item!=incoming.cursor)throw new IllegalStateException("Cursor changed.");gui.equipment().wdgmsg("drop",destinationSlot);},()->{
            WItem worn=hand(gui,destinationSlot);
            if(worn==null||worn.item==displaced||!incoming.matches(worn.item)
                ||gui.vhand!=null&&gui.vhand.item==incoming.cursor)return false;
            if(gui.vhand==null)return old==null;
            return old!=null&&old.take(gui);
        },5);
        if(old!=null){Place destination=mainPlace;bot.add(()->put(gui,old,destination),()->gui.vhand==null,3);}
        return true;
    }
    static boolean noRoom(GameUI gui){
        ShortcutRunner.cancel(gui);
        gui.ui.msg(L10n.tr("No room to swap equipment. Make space and try again."));
        return false;
    }
    /** A menu icon fills both hands when a matching pair is available. */
    public static boolean equipPair(GameUI gui,String resource) {
        if(!resource.equals("gfx/invobjs/travellerssack")&&!resource.equals("gfx/invobjs/wanderersbindle"))return false;
        if(gui.equipment()==null)return true;
        if(gui.vhand!=null){gui.ui.msg(L10n.tr("Empty the cursor before equipping."));return true;}
        int count=0;
        for(GItem item:carried(gui))try{if(canon(item).equals(resource)&&forHands(item))count++;}catch(Loading pending){}
        if(count==0)return true;
        if(count==1&&!equipped(gui,resource))return false;
        String[] names={resource.substring("gfx/invobjs/".length())};
        ShortcutRunner bot=new ShortcutRunner(gui);
        bot.afterPrevious(()->equip(bot,gui,names,LEFT));
        bot.afterPrevious(()->equip(bot,gui,names,RIGHT));
        ShortcutRunner.begin(bot);
        return true;
    }
    static void equip(ShortcutRunner bot,GameUI gui,String[] names,int slot) {
        if(gui.vhand!=null||gui.equipment()==null)return;
        WItem worn=hand(gui,slot);
        if(worn!=null&&Arrays.asList(names).contains(base(worn.item)))return;
        GItem best=null;double q=-1;for(GItem item:carried(gui))try{if(!bot.selected.contains(item)&&Arrays.asList(names).contains(base(item))&&forHands(item)&&quality(item)>q){best=item;q=quality(item);}}catch(Loading pending){}
        if(best!=null&&queue(bot,gui,best,slot))bot.selected.add(best);
    }
    public static void swap(GameUI gui,GItem item) {
        cancelClick(item);if(gui==null||gui.equipment()==null||!forHands(item))return;
        if(gui.vhand!=null){gui.ui.msg(L10n.tr("Empty the cursor before equipping."));return;}
        try{ShortcutRunner.cancel(gui);ShortcutRunner bot=new ShortcutRunner(gui);if(queue(bot,gui,item,slotFor(item,gui)))ShortcutRunner.begin(bot);}
        catch(Loading pending){gui.ui.msg(L10n.tr("Items are still loading. Try again."));}
    }
    private HandEquip(){}
}
