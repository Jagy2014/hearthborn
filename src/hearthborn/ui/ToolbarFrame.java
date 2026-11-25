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
 * Shared strip geometry and movement gestures, without a separate handle.
 *
 * @author Jagy2014
 */
public final class ToolbarFrame {
    /** A clean cell is a faint tint and a thin olive line, so the world stays visible through it. */
    private static final java.awt.Color GOLD_CLEAN=new java.awt.Color(150,138,88,120),BLUE_CLEAN=new java.awt.Color(104,152,205,120);
    private static final int CLEAN_ALPHA=48;
    public static int gap(){return UI.scale(2);}
    /** Strips have no moulding of their own, so nothing is left between the cells and the edge. */
    public static Coord inset(){return Coord.z;}
    public static Coord cell(int index,boolean vertical){
        Coord slot=Inventory.invsq.sz();
        return vertical?Coord.of(0,index*(slot.y+gap())):Coord.of(index*(slot.x+gap()),0);
    }
    /** Hotbar cells a gap apart, with only the panel's outer frame allowance. */
    public static Coord packedSize(int count,boolean vertical){
        Coord slot=Inventory.invsq.sz();
        int between=gap()*Math.max(0,count-1);
        return (vertical?Coord.of(slot.x,slot.y*count+between):Coord.of(slot.x*count+between,slot.y)).add(inset().mul(2));
    }
    public static boolean canDrag(UI ui,int button,Coord point,Coord size,boolean overIcon){
        return button==1&&point.isect(Coord.z,size)&&(ui.modmeta||!overIcon);
    }
    public static String moveTip(){return hearthborn.L10n.tr("Drag the background, or hold Alt/Option and drag any icon to move the toolbar.");}
    /** Shared empty/action cell appearance for the menu and all hotkey strips. */
    public static void slot(GOut g,Coord at,Coord size){
        java.awt.Color fill=UiTheme.light()?UiTheme.PANEL:SystemButtonSkin.DISK;
        fill=new java.awt.Color(fill.getRed(),fill.getGreen(),fill.getBlue(),CLEAN_ALPHA);
        g.chcolor(fill);g.frect(at,size);
        g.chcolor(UiTheme.light()?UiTheme.EDGE:AccentTheme.blue()?BLUE_CLEAN:GOLD_CLEAN);g.rect(at,size);g.chcolor();
    }
    private static volatile Tex tile;
    private static Tex tile(){
        Tex t=tile;Coord size=Inventory.invsq.sz();
        if(t==null||!t.sz().equals(size)){
            java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(size.x,size.y,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            int fill=new java.awt.Color(40,39,37,200).getRGB(),edge=new java.awt.Color(128,121,106,240).getRGB();
            for(int y=0;y<size.y;y++)for(int x=0;x<size.x;x++){
                boolean border=x==0||y==0||x==size.x-1||y==size.y-1;
                boolean corner=(x==0||x==size.x-1)&&(y==0||y==size.y-1);
                img.setRGB(x,y,corner?0:border?edge:fill);
            }
            tile=t=new TexI(img);
        }
        return t;
    }
    public static void hotbarSlot(GOut g,Coord at,Coord size){
        if(!UiTheme.light()){g.image(tile(),at);return;}
        slot(g,at,size);
    }
    public static Tex keyLabel(String text){
        java.awt.image.BufferedImage img=KEY_FONT.render(text).img;
        return new TexI(Utils.outline2(img,java.awt.Color.BLACK,true));
    }
    private static final Text.Foundry KEY_FONT=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),12);
    /** Where a caption's bottom-right corner goes inside a cell. */
    public static Coord keyAnchor(Coord at,Coord size){return at.add(size.x-UI.scale(2),size.y);}
    /** The chat moulding, in the same pixels as the window. A scaled strip must not enlarge it. */
    public static void frame(GOut g,Coord size){frame(g,Coord.z,size);}
    public static void frame(GOut g,Coord at,Coord size){Window.wbox.draw(g,at,size);}
    public static void frame(GOut physical,Coord logicalAt,Coord logicalBox,double scale){
        Coord at=ToolbarScale.size(logicalAt,scale);
        Coord end=ToolbarScale.size(logicalAt.add(logicalBox),scale);
        frame(physical,at,end.sub(at).max(Coord.of(1,1)));
    }
    private ToolbarFrame(){}
}
