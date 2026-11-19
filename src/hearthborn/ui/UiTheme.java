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
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
/**
 * Shared background for windows and HUD panels. Dark is the default; light is the parchment theme, see {@link Parchment}.
 *
 * @author Jagy2014
 */
public final class UiTheme {
    public static final Color INK=Parchment.TEXT;
    public static final Color HEADER=Parchment.ACCENT_TEXT;
    public static final Color BONUS=Parchment.OK;
    public static final Color PENALTY=Parchment.DANGER;
    public static final Color METER=new Color(0x3A,0x2E,0x1F);
    /** Dark enough for the gold frame, warm enough that the end of the red stays visible. */
    private static final Color DARK_METER=new Color(72,36,28);
    public static final Color SYSTEM=new Color(0x0B,0x5C,0x73);
    public static final Color EDGE=Parchment.FRAME;
    public static final Color PANEL=Parchment.PANEL;
    private static final Color DARK_PANEL=new Color(0,0,0);
    private static final Color ENTRY_GOLD=new Color(174,143,76),ENTRY_FOCUS=new Color(232,192,105);
    private static final Color ENTRY_TEXT=new Color(236,232,218),ENTRY_DIRTY=new Color(255,205,109);
    private static final Color DARK_BACKGROUND=new Color(0,0,0,255);
    private static final Color DARK_SLOT=new Color(191,166,101,150),BLUE_SLOT=new Color(0x3C,0x7E,0xAE,150),BLUE_HATCH=new Color(0x3C,0x7E,0xAE,170);
    private static final Color DARK_CLOSED=new Color(6,7,6,220);
    private static final Color DARK_HATCH=new Color(191,166,101,170);
    private static final Color PARCHMENT=Parchment.BG;
    private static final Color LIGHT_SLOT=Parchment.LINE;
    private static final Color LIGHT_CLOSED=Parchment.SELECTED;
    private static final Color LIGHT_HATCH=new Color(Parchment.FRAME.getRed(),Parchment.FRAME.getGreen(),Parchment.FRAME.getBlue(),120);
    private static final Text.Foundry HEADING=new Text.Foundry(Text.fraktur,25).aa(true);
    private static final Text.Foundry GROUP=new Text.Foundry(Text.fraktur,18).aa(true);
    private static final String PREF="hearthborn/ui/theme";
    public static Color BACKGROUND=DARK_BACKGROUND;
    public static Color SLOT=DARK_SLOT;
    private static Color closedFill=DARK_CLOSED,hatch=DARK_HATCH;
    private static boolean light;
    private static int generation;
    private static Tex windowTex,closedTex;
    static {apply("light".equals(Utils.getpref(PREF,"dark")),false);}
    public static boolean light(){return light;}
    public static void light(boolean value){if(value!=light)apply(value,true);}
    /** The accent changed: colours are taken again and everything that caches what it draws draws it again. */
    public static void refresh(){apply(light,false);hearthborn.map.MapToolbar.clearArt();}
    public static int generation(){return generation;}
    /** White and pale colors become ink on a parchment panel. Chat and menus keep their own mapping. */
    public static Color textColor(Widget widget,Color color){
        if(color==null||!light||!onPanel(widget))return color;
        return readable(color);
    }
    /** A bright color that would disappear on parchment. Dark theme colors pass through. */
    public static Color readable(Color color){
        if(color==null||!light)return color;
        int r=color.getRed(),g=color.getGreen(),b=color.getBlue(),a=color.getAlpha();
        if(r>200&&g>200&&b>200)return new Color(INK.getRed(),INK.getGreen(),INK.getBlue(),a);
        double lum=(0.2126*r+0.7152*g+0.0722*b)/255.0;
        if(b>=160&&b>=r&&b+20>=g&&lum>0.35)return new Color(SYSTEM.getRed(),SYSTEM.getGreen(),SYSTEM.getBlue(),a);
        if(g>180&&g>r+20&&g>b&&lum>0.45)return new Color(BONUS.getRed(),BONUS.getGreen(),BONUS.getBlue(),a);
        if(r>180&&g<170&&b<170&&r>g+30&&r>b)return new Color(PENALTY.getRed(),PENALTY.getGreen(),PENALTY.getBlue(),a);
        if(lum<0.55)return color;
        double scale=0.42/lum;
        return new Color(clamp(r*scale),clamp(g*scale),clamp(b*scale),a);
    }
    private static int clamp(double value){return Math.max(0,Math.min(255,(int)Math.round(value)));}
    public static Text headingText(String text){return HEADING.render(text,HEADER);}
    public static Text groupText(String text){return GROUP.render(text,HEADER);}
    public static Widget heading(String text){return new Heading(text,true);}
    public static Widget groupHeading(String text){return new Heading(text,false);}
    /** Section title. Parchment uses dark gold; the dark theme keeps the gold texture. */
    public static final class Heading extends Widget {
        private final String text;
        private final boolean section;
        private Text rendered;
        private int seen=-2;
        private Heading(String text,boolean section){this.text=text;this.section=section;refresh();}
        private void refresh(){
            if(rendered!=null)rendered.dispose();
            rendered=light()?(section?headingText(text):groupText(text)):(section?haven.CharWnd.catf:haven.GridList.dcatf).render(text);
            resize(rendered.sz());
            seen=generation();
        }
        public void draw(GOut g){if(seen!=generation())refresh();g.image(rendered.tex(),Coord.z);}
        public void dispose(){if(rendered!=null)rendered.dispose();super.dispose();}
    }
    public static Color meterTrack(){return light?METER:DARK_METER;}
    public static Color panelColor(){return light?PANEL:DARK_PANEL;}
    public static Color entryText(boolean dirty){return light?(dirty?HEADER:INK):(dirty?ENTRY_DIRTY:ENTRY_TEXT);}
    public static void entryFrame(GOut g,Coord size,boolean focus){
        g.chcolor(focus?UiPalette.accent():UiPalette.border());
        g.rect(Coord.z,size);g.chcolor();
    }
    public static Color row(Color stripe){return light?PANEL:stripe;}
    public static Text.Slug line(Widget widget,GOut g,Text.Forge forge,String text){
        Color color=textColor(widget,g.getcolor());
        if(color!=g.getcolor()&&forge instanceof Text.Foundry)return((Text.Foundry)forge).render(text,color);
        return(forge.render(text));
    }
    public static boolean onPanel(Widget widget){
        for(Widget at=widget;at!=null;at=at.parent){
            if(at instanceof haven.ChatUI||at instanceof haven.FlowerMenu)return false;
            if(at instanceof Window)return true;
        }
        return false;
    }
    private static void apply(boolean next,boolean save){
        light=next;generation++;
        BACKGROUND=next?PARCHMENT:DARK_BACKGROUND;
        boolean blue=AccentTheme.blue();
        SLOT=next?LIGHT_SLOT:blue?BLUE_SLOT:DARK_SLOT;
        closedFill=next?LIGHT_CLOSED:DARK_CLOSED;
        hatch=next?LIGHT_HATCH:blue?BLUE_HATCH:DARK_HATCH;
        if(windowTex!=null){windowTex.dispose();windowTex=null;}
        if(closedTex!=null){closedTex.dispose();closedTex=null;}
        if(save){Utils.setpref(PREF,next?"light":"dark");hearthborn.map.MapToolbar.clearArt();}
    }
    public static void background(GOut g,Coord size){background(g,Coord.z,size);}
    public static void background(GOut g,Coord at,Coord size){g.chcolor(BACKGROUND);g.frect(at,size);g.chcolor();}
    public static void panel(GOut g,Coord size){panel(g,Coord.z,size);}
    public static void panel(GOut g,Coord at,Coord size){g.chcolor(panelColor());g.frect(at,size);g.chcolor();}
    /** Frames often surround siblings added earlier. Paint their backing before any content. */
    public static void panels(GOut g,Widget owner){
        for(Widget child=owner.child;child!=null;child=child.next){
            if(!child.visible||child instanceof Window||child instanceof Window.Deco)continue;
            if(!(child instanceof Frame)&&child.child==null)continue;
            Coord at=owner instanceof Window?child.c:owner.xlate(child.c,true);
            GOut local=g.reclip(at,child.sz);
            if(child instanceof Frame){
                Frame frame=(Frame)child;
                panel(local,frame.box.btloff(),frame.inner());
            }
            panels(local,child);
        }
    }
    /** Chat uses the same sheet as the other windows while the parchment theme is on. */
    public static void darkBackground(GOut g,Coord size){g.chcolor(light?PARCHMENT:DARK_BACKGROUND);g.frect(Coord.z,size);g.chcolor();}
    /** Gold cell outline used by the equipment strip and inventory grids. */
    public static void slot(GOut g,Coord at,Coord size){g.chcolor(SLOT);g.rect(at,size);g.chcolor();}
    public static void cells(GOut g,Coord grid,boolean[] mask){
        Coord c=new Coord();int i=0;Coord step=Inventory.sqsz,cell=Inventory.invsq.sz();
        for(c.y=0;c.y<grid.y;c.y++)for(c.x=0;c.x<grid.x;c.x++){
            Coord at=c.mul(step);
            if(mask!=null&&mask[i++])g.image(closed(cell),at);
            else {panel(g,at,cell);slot(g,at,cell);}
        }
    }
    /** Blocked inventory cells: a darker fill and a gold hatch, so they stay visible on the panel. */
    static BufferedImage closedImage(Coord size){
        BufferedImage image=new BufferedImage(size.x,size.y,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.setColor(closedFill);
        g.fillRect(0,0,size.x,size.y);
        g.setComposite(AlphaComposite.SrcOver);
        g.setStroke(new BasicStroke(1f));
        g.setColor(hatch);
        int step=Math.max(4,size.x/5);
        for(int x=-size.y;x<size.x;x+=step)g.drawLine(x,size.y,x+size.y,0);
        g.setColor(SLOT);
        g.drawRect(0,0,size.x-1,size.y-1);
        g.dispose();
        return image;
    }
    private static Tex closed(Coord size){
        if(closedTex!=null&&closedTex.sz().equals(size))return closedTex;
        if(closedTex!=null)closedTex.dispose();
        closedTex=new TexI(closedImage(size));
        return closedTex;
    }
    public static Tex windowBackground(){if(windowTex==null)windowTex=texture();return windowTex;}
    public static Tex texture(){
        BufferedImage image=PUtils.rasterimg(PUtils.imgraster(Coord.of(64,64)));
        java.awt.Graphics2D g=image.createGraphics();g.setComposite(java.awt.AlphaComposite.Src);g.setColor(BACKGROUND);g.fillRect(0,0,64,64);g.dispose();return new TexI(image);
    }
    private UiTheme(){}
}
