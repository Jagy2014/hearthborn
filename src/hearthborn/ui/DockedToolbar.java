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
 * The ten number buttons. The F row and the extra icons are their own panels.
 *
 * @author Jagy2014
 */
public final class DockedToolbar implements Disposable {
    private static final int NUMBER=0,PAGE=1,FUNCTION=2,EXTRA=3;
    private final GameUI gui;
    private boolean placed;
    private UI.Grab drag;
    private Coord offset;
    private double scale=ToolbarScale.scale();
    private Coord logicalSize=Coord.z;
    private Spot[] spots=new Spot[0];
    private final Tex[] functionLabels=new Tex[12];
    private final Tex[] numbers=new Tex[10];
    private final Tex[] pageMarks=new Tex[10];
    private int hoverSlot=-1;
    private GameUI.BeltSlot hoverEntry;
    private MenuGrid.PagButton hoverButton;
    private Tex hoverTip;
    private boolean hoverLong;
    private double hoverStart;
    public DockedToolbar(GameUI gui){this.gui=gui;}
    /** Old callers asked which bar was glued to the chat. The strip is never glued. */
    public static boolean docked(){return false;}
    public static void docked(boolean value){}
    public static int pageSlot(int page,int digit){return page*12+digit;}
    public boolean active(){return spots.length>0;}
    private static final class Spot {
        final int kind,index,slot;final Coord at;
        Spot(int kind,int index,int slot,Coord at){this.kind=kind;this.index=index;this.slot=slot;this.at=at;}
    }
    public void layout(GameUI.NKeyBelt belt,Coord base){
        scale=ToolbarScale.scale();
        boolean upright=Toolbars.vertical("numeric");
        boolean numbersOn=Toolbars.enabled("numeric");
        int count=numbersOn?10:0;
        Spot[] next=new Spot[count];
        Coord slot=Inventory.invsq.sz();
        Coord pad=ToolbarFrame.inset();
        int x=pad.x,y=pad.y,n=0;
        if(numbersOn){
            for(int i=0;i<10;i++){next[n++]=new Spot(NUMBER,i,0,Coord.of(x,y));if(upright)y+=slot.y+ToolbarFrame.gap();else x+=slot.x+ToolbarFrame.gap();}
        }
        spots=next;
        int right=pad.x,bottom=pad.y;
        for(Spot spot:spots){right=Math.max(right,spot.at.x+slot.x);bottom=Math.max(bottom,spot.at.y+slot.y);}
        logicalSize=spots.length==0?Coord.of(1,1):Coord.of(right+pad.x,bottom+pad.y);
        Coord target=ToolbarScale.size(logicalSize,scale);
        if(belt!=null&&!belt.sz.equals(target))belt.resize(target);
        if(belt!=null)belt.show(spots.length>0);
    }
    public Coord numericCell(int digit){return cell(NUMBER,digit);}
    public Coord functionCell(int index){return cell(FUNCTION,index);}
    public Coord extraCell(int index){return cell(EXTRA,index);}
    public Coord pageSwitcher(){return cell(PAGE,0);}
    public int extraCount(){int n=0;for(Spot spot:spots)if(spot.kind==EXTRA)n++;return n;}
    private Coord cell(int kind,int index){for(Spot spot:spots)if(spot.kind==kind&&spot.index==index)return spot.at;return Coord.z;}
    /** Floating coordinates stay where the player dropped the strip. Otherwise it sits along the lower HUD. */
    public void place(GameUI.NKeyBelt belt){
        if(belt==null||gui==null||gui.sz==null||drag!=null)return;
        if(Toolbars.stripMoved()){
            if(!placed){belt.move(Utils.getprefc("hearthborn/toolbar/strip/position",bottom(belt)));placed=true;}
        }else belt.move(bottom(belt));
        if(drag==null)fit(belt);
    }
    private Coord bottom(GameUI.NKeyBelt belt){
        int x=gui.chat==null?UI.scale(8):Toolbars.chatLeft(gui);
        x=Utils.clip(x,0,Math.max(0,gui.sz.x-belt.sz.x));
        Coord at=Coord.of(x,Math.max(0,gui.sz.y-belt.sz.y));
        if(gui.menu!=null&&gui.menu.tvisible())at=aboveCorner(at,belt.sz,gui.menu.parent);
        SystemCross radial=gui.getchild(SystemCross.class);
        if(radial!=null&&radial.tvisible())at=aboveCorner(at,belt.sz,radial);
        // A horizontal sidebar row lies on the very bottom edge, so the belt sits above it. Only the row counts, not a menu opened from it.
        ActionStrip row=gui.getchild(ActionStrip.class);
        if(row!=null&&row.visible()&&ActionStrip.horizontal()){
            Coord origin=row.columnAt(),extent=row.columnSize();
            if(at.x<origin.x+extent.x&&at.x+belt.sz.x>origin.x&&at.y+belt.sz.y>origin.y)at=Coord.of(at.x,Math.max(0,origin.y-belt.sz.y));
        }
        return at;
    }
    /** A fixed-width row may need to sit above a corner menu on a narrow screen. */
    private Coord aboveCorner(Coord at,Coord size,Widget corner){
        if(corner==null)return at;
        Coord origin=corner.parentpos(gui),extent=HudInterfaceScale.size(corner);
        if(at.x<origin.x+extent.x&&at.x+size.x>origin.x&&at.y<origin.y+extent.y&&at.y+size.y>origin.y)
            return Coord.of(at.x,Math.max(0,origin.y-size.y));
        return at;
    }
    private void fit(GameUI.NKeyBelt belt){
        Coord limit=gui.sz.sub(belt.sz).max(Coord.z);
        belt.move(Coord.of(Utils.clip(belt.c.x,0,limit.x),Utils.clip(belt.c.y,0,limit.y)));
    }
    public boolean handle(GameUI.NKeyBelt belt,Widget.MouseDownEvent event){
        boolean overIcon=false;
        Coord size=Inventory.invsq.sz();
        for(Spot spot:spots)if(logical(event.c).isect(spot.at,size)){overIcon=true;break;}
        if(belt.ui!=null&&ToolbarFrame.canDrag(belt.ui,event.b,logical(event.c),logicalSize,overIcon)){
            offset=event.c;MagneticWindows.begin(belt);drag=belt.ui.grabmouse(belt);belt.raise();return true;
        }
        return false;
    }
    public boolean mousemove(GameUI.NKeyBelt belt,Widget.MouseMoveEvent event){
        if(drag==null)return false;
        belt.move(belt.c.add(event.c.sub(offset)));MagneticWindows.drag(belt);return true;
    }
    public boolean mouseup(GameUI.NKeyBelt belt,Widget.MouseUpEvent event){
        if(drag==null||event.b!=1)return false;
        releaseDrag();MagneticWindows.end(belt);Toolbars.stripMoved(true);fit(belt);rememberPosition(belt);return true;
    }
    public void rememberPosition(GameUI.NKeyBelt belt){Utils.setprefc("hearthborn/toolbar/strip/position",belt.c);}
    private void releaseDrag(){if(drag!=null){drag.remove();drag=null;}}
    public Coord logical(Coord point){return scale==1?point:Coord.of((int)Math.floor(point.x/scale),(int)Math.floor(point.y/scale));}
    public Coord logicalSize(Coord fallback){return logicalSize==null?fallback:logicalSize;}
    public GOut graphics(GOut original){
        if(scale==1)return original;
        haven.render.Pipe pipe=original.basicstate();
        haven.render.Ortho2D ortho=(haven.render.Ortho2D)pipe.get(haven.render.States.vxf);
        pipe.prep(new haven.render.Ortho2D((float)((ortho.l-original.tx.x)/scale),(float)((ortho.u-original.tx.y)/scale),
            (float)((ortho.r-original.tx.x)/scale),(float)((ortho.b-original.tx.y)/scale)));
        return new GOut(original.out,pipe,logicalSize);
    }
    public int slot(Coord c){
        Coord size=Inventory.invsq.sz();
        for(Spot spot:spots)if(spot.kind!=PAGE&&c.isect(spot.at,size))return spot.kind==NUMBER?-2:spot.slot;
        return -1;
    }
    /** Number-row hits need the belt's current page. */
    public int slot(Coord c,int page){
        int found=slot(c);
        if(found==-2){
            for(Spot spot:spots)if(spot.kind==NUMBER&&c.isect(spot.at,Inventory.invsq.sz()))return pageSlot(page,spot.index);
        }
        return found;
    }
    public boolean pageHit(Coord c){
        Coord size=Inventory.invsq.sz();
        for(Spot spot:spots)if(spot.kind==PAGE&&c.isect(spot.at,size))return true;
        return false;
    }
    private Tex number(int digit){
        if(numbers[digit]==null)numbers[digit]=ToolbarFrame.keyLabel(Integer.toString((digit+1)%10));
        return numbers[digit];
    }
    private Tex pageMark(int page){
        if(pageMarks[page]==null)pageMarks[page]=new TexI(hearthborn.QualityOverlay.styled(Integer.toString((page+1)%10),new java.awt.Color(255,220,120)));
        return pageMarks[page];
    }
    private void clearTip(){if(hoverTip!=null)hoverTip.dispose();hoverTip=null;hoverButton=null;}
    public Object tooltip(int slot,boolean entered){
        if(gui.belt==null||slot<0||slot>=gui.belt.length||gui.belt[slot]==null){clearTip();hoverEntry=null;hoverSlot=-1;return null;}
        GameUI.BeltSlot entry=gui.belt[slot];
        if(entered||hoverSlot!=slot||hoverEntry!=entry){clearTip();hoverStart=Utils.rtime();hoverSlot=slot;hoverEntry=entry;}
        try{
            MenuGrid.PagButton button=null;Resource resource=null;
            if(entry instanceof GameUI.PagBeltSlot)button=((GameUI.PagBeltSlot)entry).pag.button();
            else if(entry instanceof GameUI.ResBeltSlot){
                GameUI.ResBeltSlot saved=(GameUI.ResBeltSlot)entry;resource=saved.getres();
                if(gui.menu!=null&&resource.layer(Resource.action)!=null)button=gui.menu.paginafor(saved.rdt.res).button();
            }
            boolean full=button!=null&&(XtensionsMenu.isScript(button.res.name)||Utils.rtime()-hoverStart>.5);
            if(hoverTip==null||hoverButton!=button||hoverLong!=full){
                clearTip();
                if(button!=null){java.awt.image.BufferedImage image=button.rendertt(full);if(image!=null)hoverTip=new TexI(image);}
                else if(resource!=null){Resource.Tooltip title=resource.layer(Resource.tooltip);if(title!=null)hoverTip=Text.render(hearthborn.L10n.resource("tooltip",resource.name,title.t)).tex();}
                hoverButton=button;hoverLong=full;
            }
            return hoverTip;
        }catch(Loading waiting){return null;}
    }
    /** The frame is the buttons themselves. */
    public Coord buttonFrame(){return logicalSize==null?Coord.z:logicalSize;}
    public void draw(GOut g,int selectedPage){
        Coord slot=Inventory.invsq.sz();
        for(Spot spot:spots){
            ToolbarFrame.hotbarSlot(g,spot.at,slot);
            if(spot.kind==PAGE){g.aimage(pageMark(selectedPage),spot.at.add(slot.div(2)),.5,.5);continue;}
            int index=spot.kind==NUMBER?pageSlot(selectedPage,spot.index):spot.slot;
            if(gui.belt!=null&&index>=0&&index<gui.belt.length){
                GameUI.BeltSlot entry=gui.belt[index];
                try{if(entry!=null)entry.draw(g.reclip(spot.at.add(UI.scale(1,1)),slot.sub(UI.scale(2,2))));}catch(Loading ignored){}
            }
            if(spot.kind==NUMBER)g.aimage(number(spot.index),ToolbarFrame.keyAnchor(spot.at,slot),1,1);
            if(spot.kind==FUNCTION){
                if(functionLabels[spot.index]==null)functionLabels[spot.index]=ToolbarFrame.keyLabel("F"+(spot.index+1));
                g.aimage(functionLabels[spot.index],ToolbarFrame.keyAnchor(spot.at,slot),1,1);
            }
        }
    }
    public void dispose(){
        releaseDrag();clearTip();
        for(Tex label:functionLabels)if(label!=null)label.dispose();
        for(Tex image:numbers)if(image!=null)image.dispose();
        for(Tex image:pageMarks)if(image!=null)image.dispose();
    }
}
