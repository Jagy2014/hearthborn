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
import hearthborn.map.DetachedMap;
import java.awt.event.KeyEvent;

/**
 * Temporary HUD presentation; native window visibility and saved places stay intact.
 *
 * @author Jagy2014
 */
public final class CombatLayout {
    public static final KeyBinding KEY=KeyBinding.get("compact-ui",KeyMatch.forcode(KeyEvent.VK_SPACE,KeyMatch.M));
    private final GameUI gui;
    private boolean active,manual,battle,detached;
    private Boolean override;
    private MapPlace mapPlace;
    /** Windows the player opened from the equipment strip during this compact session. */
    private final java.util.Set<Widget> opened=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public CombatLayout(GameUI gui){this.gui=gui;}
    public boolean active(){return active;}
    private boolean fighting(){
        // A peace offer changes gst/current and may remove Fightsess, but the
        // relationship remains until the server actually removes the opponent.
        return gui.fv!=null&&!gui.fv.lsrel.isEmpty() || gui.findchild(Fightsess.class)!=null;
    }
    public void refresh(){
        boolean now=fighting();
        if(now!=battle){battle=now;override=null;}
        boolean next=battle?(override==null||override):manual;
        if(next!=active){
            active=next;
            if(active){
                detached=DetachedMap.showing();if(detached)DetachedMap.shown(false);
                if(gui.map!=null)gui.setfocus(gui.map);
            }
            else {restoreMap();opened.clear();if(detached)DetachedMap.shown(true);detached=false;}
        }
        if(active)layoutMap();
    }
    public void toggle(){
        refresh();
        if(battle)override=!active;else manual=!active;
        refresh();
        if(active&&gui.map!=null)gui.setfocus(gui.map);
    }
    /** A world container was opened; show the full interface instead of masking its window. */
    public void leave(){
        refresh();
        if(!active)return;
        if(battle)override=false;else manual=false;
        refresh();
    }
    /** Keep a window that was opened while compact mode is already on. */
    public void reveal(Widget widget){if(widget!=null&&active)opened.add(widget);}
    public static boolean visible(Widget widget){
        GameUI gui=widget.getparent(GameUI.class);
        return gui==null||gui.compactLayout==null||!gui.compactLayout.active||gui.compactLayout.keeps(widget);
    }
    private boolean keeps(Widget widget){
        if(widget==gui)return true;
        Widget branch=widget;
        while(branch.parent!=null&&branch.parent!=gui)branch=branch.parent;
        if(branch.parent!=gui)return true;
        if(branch==gui.map||branch instanceof Fightsess||branch instanceof EquipmentStrip||branch instanceof Partyview||branch instanceof ItemDrag)return true;
        if(widget instanceof Speedget)return true;
        // Hover popups stay up. A window opened from the strip during this
        // session stays up too; one that was already open before compact mode does not.
        if(opened.contains(branch))return true;
        if(branch instanceof Window&&(((Window)branch).deco instanceof GItem.HoverDeco||EquipmentInventoryPreview.showing((Window)branch)))return true;
        if(branch==gui.mapfile){
            // The combat map is a framed view, with room for the map itself.
            return widget==branch||widget==gui.mapfile.view||widget==gui.mapfile.view.parent;
        }
        if(gui.portrait!=null&&branch==gui.portrait.parent){
            if(widget==branch)return true;
            Widget part=widget;while(part.parent!=branch)part=part.parent;
            return part==gui.portrait||part instanceof IMeter||part instanceof StatusMeters&&StatusMeters.combatVisible(widget);
        }
        if(gui.fv!=null&&branch==gui.fv.parent)return widget==branch||widget.hasparent(gui.fv);
        return false;
    }
    private void layoutMap(){
        if(mapPlace!=null&&mapPlace.map!=gui.mapfile)restoreMap();
        MapWnd map=gui.mapfile;
        if(map==null||map.parent!=gui)return;
        if(mapPlace==null){
            mapPlace=new MapPlace(map);map.frameBoosted=true;
            if(map.deco!=null)map.compact(true);
        }
        Coord size=ActionMenuFrame.size(MenuGrid.bgsz.mul(4,4).add(1,1),ActionMenuFrame.GRID);
        int inset=gui.portrait==null||PortraitSide.onRight()?UI.scale(10):gui.portrait.c.x;
        Coord limit=Coord.of(Math.max(1,gui.sz.x-inset),Math.max(1,gui.sz.y));
        size=HudInterfaceScale.fromParent(map,size.min(limit));
        if(!map.csz().equals(size))map.resize(size);
        map.move(Coord.of(Math.min(inset,Math.max(0,gui.sz.x-HudInterfaceScale.size(map).x)),Math.max(0,gui.sz.y-HudInterfaceScale.size(map).y)));
        if(!map.visible())map.show();
    }
    private void restoreMap(){
        MapPlace place=mapPlace;mapPlace=null;
        if(place==null||place.map.parent==null)return;
        MapWnd map=place.map;
        if(!place.compact)map.compact(false);
        map.resize(place.size);
        Coord limit=gui.sz.sub(HudInterfaceScale.size(map)).max(Coord.z);
        map.move(Coord.of(Utils.clip(place.position.x,0,limit.x),Utils.clip(place.position.y,0,limit.y)));
        map.show(place.visible);map.frameBoosted=place.boosted;
    }
    private static final class MapPlace {
        final MapWnd map;final Coord position,size;final boolean visible,compact,boosted;
        MapPlace(MapWnd map){this.map=map;position=new Coord(map.c);size=map.csz();visible=map.visible();compact=map.deco==null;boosted=map.frameBoosted;}
    }
}
