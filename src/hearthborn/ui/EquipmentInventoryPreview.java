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
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Use the native inventory with bag-style hover decoration, then restore its full window.
 *
 * @author Jagy2014
 */
public final class EquipmentInventoryPreview {
    private static final java.util.Map<Window,Coord> homes=new java.util.WeakHashMap<>();
    private static final java.util.Map<Window,WeakReference<EquipmentInventoryPreview>> previews=new java.util.WeakHashMap<>();
    static Coord savedPosition(Window window){Coord position=homes.get(window);return position==null?window.c:position;}
    public static boolean showing(Window window){return homes.containsKey(window);}
    /** Inventory's existing button/hotkey must promote a preview rather than close it. */
    public static boolean open(Window window){
        WeakReference<EquipmentInventoryPreview> ref=previews.get(window);
        EquipmentInventoryPreview preview=ref==null?null:ref.get();
        if(preview==null)return false;
        preview.release(false);focus(window);return true;
    }
    private Window window;
    private Coord saved,fullSize;
    private int savedZ;
    private final List<Widget> hidden=new ArrayList<>();
    private double lastHover;
    private static Window inventory(EquipmentStrip strip){
        GameUI gui=strip.getparent(GameUI.class);
        return gui==null||gui.maininv==null?null:gui.maininv.getparent(Window.class);
    }
    boolean open(EquipmentStrip strip){
        Window inventory=inventory(strip);if(inventory==null)return false;
        if(!open(inventory))focus(inventory);return true;
    }
    private static void focus(Window window){
        window.show();window.raise();
        if(window.parent!=null)window.parent.setfocus(window);
        GameUI gui=window.getparent(GameUI.class);
        if(gui!=null&&gui.compactUI())gui.compactLayout.reveal(window);
    }
    void hover(EquipmentStrip strip,Coord cell,Coord cellSize){
        GameUI gui=strip.getparent(GameUI.class);
        Window inventory=inventory(strip);
        if(inventory==null)return;
        lastHover=Utils.rtime();
        if(window==inventory)return;
        if(inventory.visible()||(gui.compactUI()&&inventory.visible))return; // A masked full window is still open.
        close();window=inventory;saved=new Coord(inventory.c);fullSize=new Coord(inventory.csz());savedZ=inventory.z;
        homes.put(inventory,saved);
        previews.put(inventory,new WeakReference<>(this));
        for(Widget child=inventory.child;child!=null;child=child.next)
            if(child!=inventory.deco&&child!=gui.maininv&&child.visible()){hidden.add(child);child.hide();}
        inventory.chdeco(new PreviewDeco());inventory.pack();inventory.z(90);
        inventory.move(strip.previewPosition(inventory,cell,cellSize));
        inventory.show();inventory.raise();
    }
    boolean pointerInside(UI ui){
        return window!=null&&ui.mc.isect(window.rootpos(),HudInterfaceScale.size(window));
    }
    void tick(UI ui,boolean enabled){
        if(window==null)return;
        if(!enabled||!window.visible()||window.parent==null){release(true);return;}
        if(pointerInside(ui))lastHover=Utils.rtime();
        if(Utils.rtime()-lastHover>.3)release(true);
    }
    void close(){release(true);}
    private void release(boolean hide){
        if(window==null)return;
        Window previous=window;window=null;
        if(previous.parent!=null){
            if(hide)previous.hideImmediately();
            previous.chdeco(new SmallWindowDeco(previous.large));
            for(Widget child:hidden)if(child.parent==previous)child.show();
            previous.resize(fullSize.max(previous.contentsz()));previous.z(savedZ);previous.move(saved);
            if(!hide)previous.show();
        }
        previews.remove(previous);homes.remove(previous);hidden.clear();saved=null;fullSize=null;
    }
    /** The native HoverDeco's drag casts its parent to ContentsWindow; this is a main inventory. */
    private final class PreviewDeco extends GItem.HoverDeco {
        public boolean mousedown(MouseDownEvent event){
            if(event.b==1||event.b==3){Window previous=window;release(false);focus(previous);return true;}
            return false;
        }
    }
}
