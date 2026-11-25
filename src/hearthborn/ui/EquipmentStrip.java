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

/**
 * Interactive views of equipment slots; the inventory remains owned by Equipory.
 *
 * @author Jagy2014
 */
public final class EquipmentStrip extends Widget implements DTarget {
    public static final double SCALE=1.3;
    public static final int[] SLOTS={6,7,5,11,19,20,22,14};
    private int[] slots=SLOTS.clone();
    private static final boolean[] slotOn=new boolean[23];
    private static final boolean[] slotKnown=new boolean[23];
    public static boolean slotEnabled(int slot){
        if(slot==6||slot==7)return true;
        if(slot<0||slot>=slotKnown.length)return Utils.getprefb("hearthborn/toolbar/equipment/slot/"+slot,true);
        if(!slotKnown[slot]){slotOn[slot]=Utils.getprefb("hearthborn/toolbar/equipment/slot/"+slot,true);slotKnown[slot]=true;}
        return slotOn[slot];
    }
    public static void slotEnabled(int slot,boolean value){
        if(slot==6||slot==7)return;
        if(slot>=0&&slot<slotKnown.length){slotOn[slot]=value;slotKnown[slot]=true;}
        Utils.setprefb("hearthborn/toolbar/equipment/slot/"+slot,value);
    }
    private final hearthborn.QualityOverlay[] qualities=new hearthborn.QualityOverlay[SLOTS.length];
    private final EquipmentInventoryPreview inventoryPreview=new EquipmentInventoryPreview();
    private final BowArrowCount arrows=new BowArrowCount();
    private UI.Grab drag;
    private Widget hoverAnchor;
    private GItem previewItem;
    private Coord offset;
    private boolean vertical;
    private double scale=ToolbarScale.scale();
    private Coord logicalSize(){return cell(Math.max(0,slots.length-1)).add(Inventory.invsq.sz()).add(ToolbarFrame.inset());}
    public EquipmentStrip() {super(Coord.z);layout();for(int i=0;i<qualities.length;i++)qualities[i]=new hearthborn.QualityOverlay();show(Toolbars.enabled("equipment"));}
    private Coord cell(int i) {
        return ToolbarFrame.cell(i,vertical);
    }
    private Coord logical(Coord point){return ToolbarScale.logical(point,scale);}
    private void layout(){
        slots=java.util.Arrays.stream(SLOTS).filter(EquipmentStrip::slotEnabled).toArray();
        vertical=Toolbars.vertical("equipment");Coord size=logicalSize();
        double base=ToolbarScale.scale();scale=vertical&&parent!=null?Math.min(base,Math.max(1,parent.sz.y)/(double)size.y):base;
        Coord target=ToolbarScale.size(size,scale);if(!sz.equals(target))resize(target);
    }
    private int index(Coord c) {
        for(int i=0;i<slots.length;i++) if(c.isect(cell(i),Inventory.invsq.sz())) return i;
        return -1;
    }
    private Equipory equipment() {return getparent(GameUI.class).equipment();}
    private WItem item(Equipory equipment,int index) {
        if(equipment==null || index<0) return null;
        Coord position=Equipory.ecoords[slots[index]].add(1,1);
        for(Widget w=equipment.child;w!=null;w=w.next) if(w instanceof WItem && w.c.equals(position)) return (WItem)w;
        return null;
    }
    public void tick(double dt) {
        show(Toolbars.enabled("equipment"));layout();fit();arrows.update(getparent(GameUI.class),equipment(),visible());
        // mousehover does not re-run when only Shift changes, so the strip must open or close previews here.
        syncPreviews();
        inventoryPreview.tick(ui,visible()&&drag==null&&!ui.modmeta&&slotEnabled(11)&&(ContainerPreviews.allowed(ui)||inventoryPreview.pointerInside(ui)));
        super.tick(dt);
    }
    public void presize(){layout();fit();}
    private void fit(){
        if(parent==null||drag!=null)return;
        Coord limit=parent.sz.sub(sz).max(Coord.z);
        move(Coord.of(Utils.clip(c.x,0,limit.x),Utils.clip(c.y,0,limit.y)));
    }
    public void draw(GOut g) {
        g=ToolbarScale.graphics(g,logicalSize(),scale);
        Equipory equipment=equipment();
        for(int i=0;i<slots.length;i++) {
            Coord c=cell(i);
            ToolbarFrame.hotbarSlot(g,c,Inventory.invsq.sz());
            WItem item=item(equipment,i);
            if(item!=null){
                item.draw(g.reclip(c.add(1,1),Inventory.invsq.sz().sub(2,2)),false);
                qualities[i].drawCorner(g.reclip(c,Inventory.invsq.sz()),item.item);
                if(slots[i]==6||slots[i]==7)arrows.draw(g.reclip(c,Inventory.invsq.sz()),item.item);
            }
            else if(Equipory.ebgs[slots[i]]!=null) g.image(Equipory.ebgs[slots[i]],c);
        }
    }
    public boolean mousehover(MouseHoverEvent ev,boolean on) {
        if(drag!=null||ui.modmeta)return false;
        // Leaving the strip for the bag popup must not clear; ContentsWindow keeps the hover alive.
        if(!ContainerPreviews.allowed(ui)){releasePreviewSource();return super.mousehover(ev,on);}
        if(!on)return super.mousehover(ev,on);
        return showPreviewAt(logical(ev.c))||super.mousehover(ev,on);
    }
    /** Honour Shift-only without waiting for mouse movement; do not dismiss a bag the pointer has entered. */
    private void syncPreviews() {
        if(ui==null||!visible()||drag!=null||ui.modmeta){clearPreviews();return;}
        if(!ContainerPreviews.allowed(ui)){releasePreviewSource();return;}
        Coord local=rootxlate(ui.mc);
        if(local.isect(Coord.z,sz))showPreviewAt(logical(local));
    }
    private void releasePreviewSource() {
        if(previewItem!=null){
            previewItem.hovering(null);
            GItem.ContentsWindow popup=previewItem.contentswnd;
            if(popup!=null&&popup.pointerOnPreviewTree())return;
            if(popup!=null)popup.dismissHover();
            previewItem=null;
        }
    }
    private void clearPreviews() {
        if(previewItem!=null){
            previewItem.hovering(null);
            if(previewItem.contentswnd!=null)previewItem.contentswnd.dismissHover();
            previewItem=null;
        }
        inventoryPreview.close();
    }
    private boolean showPreviewAt(Coord logical) {
        int i=index(logical);WItem view=item(equipment(),i);
        if(i<0){clearPreviews();return false;}
        GItem next=view!=null&&view.item.contents!=null?view.item:null;
        if(previewItem!=next){
            if(previewItem!=null){
                previewItem.hovering(null);
                if(previewItem.contentswnd!=null)previewItem.contentswnd.dismissHover();
            }
            inventoryPreview.close();
            previewItem=next;
        }
        if(slots[i]==11&&(view==null||view.item.contents==null)){
            inventoryPreview.hover(this,ToolbarScale.size(cell(i),scale),ToolbarScale.size(Inventory.invsq.sz(),scale));
            return true;
        }
        if(view!=null&&view.item.contents!=null){
            if(hoverAnchor==null)hoverAnchor=add(new Widget(Coord.z));
            hoverAnchor.resize(ToolbarScale.size(Inventory.invsq.sz(),scale));
            hoverAnchor.move(ToolbarScale.size(cell(i),scale));view.item.hovering(hoverAnchor);return true;
        }
        return false;
    }
    /** Keep the whole source strip reachable, also when opening a bag inside this preview. */
    public Coord previewPosition(Window popup,Widget anchor){
        return previewPosition(popup,anchor.parentpos(this),anchor.sz);
    }
    Coord previewPosition(Window popup,Coord cell,Coord cellSize){
        Coord origin=parentpos(popup.parent),at=parentpos(popup.parent,cell);
        Coord size=HudInterfaceScale.size(popup);int gap=UI.scale(4);
        int x,y;
        if(vertical){
            x=origin.x+sz.x+gap;
            if(x+size.x>popup.parent.sz.x)x=origin.x-size.x-gap;
            y=at.y;
        }else{
            x=at.x+cellSize.x/2-size.x/2;
            y=origin.y+sz.y+gap;
            if(y+size.y>popup.parent.sz.y)y=origin.y-size.y-gap;
        }
        Coord limit=popup.parent.sz.sub(size).max(Coord.z);
        return Coord.of(Utils.clip(x,0,limit.x),Utils.clip(y,0,limit.y));
    }
    public boolean mousedown(MouseDownEvent ev) {
        Coord point=logical(ev.c);int i=index(point);
        if(ToolbarFrame.canDrag(ui,ev.b,point,logicalSize(),i>=0)) {
            inventoryPreview.close();
            offset=ev.c;MagneticWindows.begin(this);drag=ui.grabmouse(this);raise();
            return true;
        }
        WItem item=item(equipment(),i);
        if(i>=0&&slots[i]==11&&(item==null||item.item.contents==null)&&ui.modflags()==0&&(ev.b==1||ev.b==3)&&inventoryPreview.open(this))return true;
        return item!=null ? item.mousedown(new MouseDownEvent(ev,point.sub(cell(i)))) : true;
    }
    public void mousemove(MouseMoveEvent ev) {
        if(drag!=null){move(c.add(ev.c.sub(offset)));MagneticWindows.drag(this);}else super.mousemove(ev);
    }
    public boolean mouseup(MouseUpEvent ev) {
        if(drag!=null&&ev.b==1) {drag.remove();drag=null;MagneticWindows.end(this);rememberPosition();return true;}
        return super.mouseup(ev);
    }
    public Object tooltip(Coord c,Widget prev) {
        c=logical(c);
        int i=index(c);if(i<0)return null;if(ui.modmeta)return ToolbarFrame.moveTip();
        WItem item=item(equipment(),i);
        if(item==null)return Equipory.etts[slots[i]];
        Object tip=item.tooltip(c.sub(cell(i)),prev==this ? item : prev);
        return slots[i]==6||slots[i]==7?arrows.tooltip(item,tip):tip;
    }
    /** Vertical strips put tips beside the hovered row, rather than below the entire strip. */
    public Coord tooltipPosition(Coord pointer,Coord size,Coord screen){
        Coord origin=rootpos(),edge=UI.scale(16,16);int gap=UI.scale(8);
        Coord pos=pointer.sub(size);
        if(vertical){
            int right=origin.x+sz.x+gap,left=origin.x-size.x-gap;
            pos.x=right+size.x<=screen.x-edge.x?right:left;
            pos.y=pointer.y-size.y/2;
        }else{
            int below=origin.y+sz.y+gap,above=origin.y-size.y-gap;
            if(below+size.y<=screen.y-edge.y)pos.y=below;
            else if(above>=edge.y)pos.y=above;
            else pos.x=origin.x+sz.x+gap+size.x<=screen.x-edge.x?origin.x+sz.x+gap:origin.x-size.x-gap;
        }
        return Coord.of(Utils.clip(pos.x,edge.x,Math.max(edge.x,screen.x-edge.x-size.x)),
                        Utils.clip(pos.y,edge.y,Math.max(edge.y,screen.y-edge.y-size.y)));
    }
    public boolean drop(Coord c,Coord ul) {
        int i=index(logical(c));Equipory e=equipment();
        if(i<0 || e==null)return false;
        e.wdgmsg("drop",slots[i]);return true;
    }
    public boolean iteminteract(Coord c,Coord ul) {
        c=logical(c);ul=logical(ul);
        int i=index(c);WItem item=item(equipment(),i);
        return item!=null && item.iteminteract(c.sub(cell(i)),ul.sub(cell(i)));
    }
    void rememberPosition(){Utils.setprefc("hearthborn/toolbar/equipment/position",c);}
    public void dispose() {inventoryPreview.close();arrows.dispose();if(drag!=null)drag.remove();for(hearthborn.QualityOverlay quality:qualities)quality.dispose();super.dispose();}
}
