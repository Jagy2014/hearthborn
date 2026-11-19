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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A small square button that shows a picture and keeps its words in the tooltip. {@link #action} names it in English.
 *
 * @author Jagy2014
 */
public final class FieldIcon extends Widget {
    public enum Glyph {PLAY,PAUSE,STOP,SWEEP,EDIT,ADD,CLOSE,SELECT,FILTER,PIN,ROUTE,TRASH,CHECK,REFRESH,SEARCH,LOCATE,RULER,DOWNLOAD,PANEL,HELP,DOWN,RIGHT,HOUSE,FLAME,BOX}
    public static final int SIDE=22;
    private static final Map<Glyph,Tex[]> cache=new EnumMap<>(Glyph.class);
    public final String action;
    private Glyph glyph;
    private final Supplier<String> tip;
    private final Runnable run;
    private final boolean framed;
    private boolean disabled,hover,down,on;
    private UI.Grab grab;
    public FieldIcon(Glyph glyph,String action,Supplier<String> tip,Runnable run){
        super(UI.scale(SIDE,SIDE));this.glyph=glyph;this.action=action;this.tip=tip;this.run=run;framed=true;
    }
    /** A bare picture without a frame, to decorate other controls. */
    public FieldIcon(Glyph glyph){
        super(UI.scale(SIDE,SIDE));this.glyph=glyph;action="";tip=()->null;run=null;framed=false;
    }
    public FieldIcon(Glyph glyph,String action,Runnable run){this(glyph,action,()->L10n.tr(action),run);}
    public boolean disabled(){return disabled;}
    /** Marks a toggle button as switched on. */
    public FieldIcon on(boolean value){on=value;return this;}
    public boolean on(){return on;}
    /** Swaps the picture, for a button that changes its job. */
    public void glyph(Glyph value){glyph=value;}
    public void disable(boolean value){disabled=value;if(value){down=false;hover=false;}}
    /** What a click does; nothing while disabled. */
    public void click(){if(!disabled&&run!=null)run.run();}
    private static Tex image(Glyph glyph,int state,boolean framed){
        Tex[] states=cache.computeIfAbsent(glyph,g->new Tex[10]);
        int slot=state+(framed?0:5);
        if(states[slot]!=null)return states[slot];
        int side=UI.scale(SIDE);
        BufferedImage image=TexI.mkbuf(Coord.of(side,side));
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.scale(side/22.0,side/22.0);
        // 0 plain, 1 pressed, 2 hovered, 3 disabled, 4 switched on
        int shade=state==1?-10:0;
        if(framed){
            g.setPaint(new GradientPaint(0,1,new Color(54+shade,47+shade,38+shade),0,21,new Color(36+shade,31+shade,25+shade)));
            g.fillRoundRect(1,1,20,20,4,4);
            g.setColor(state==2||state==4?new Color(240,200,105):state==3?new Color(78,64,40):new Color(122,98,52));
            g.setStroke(new BasicStroke(state==2||state==4?1.6f:1.1f));g.drawRoundRect(1,1,20,20,4,4);
            if(state==1)g.translate(0,.6);
        }
        Color ink=state==3?new Color(112,100,80):state==2||state==4?new Color(255,226,140):new Color(232,192,98);
        g.setColor(ink);g.setStroke(new BasicStroke(1.9f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.translate(3,3);g.scale(16/16.0,16/16.0);
        g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(glyph){
            case PLAY:g.drawPolygon(new int[]{4,4,13},new int[]{2,14,8},3);break;
            case PAUSE:g.drawRoundRect(2,2,4,12,1,1);g.drawRoundRect(10,2,4,12,1,1);break;
            case STOP:g.drawRoundRect(2,2,12,12,3,3);break;
            case SWEEP:
                // A harvest basket: rim, woven body and a handle.
                g.drawArc(4,0,8,9,0,180);
                g.drawRoundRect(1,6,14,3,2,2);
                g.drawPolygon(new int[]{2,14,12,4},new int[]{9,9,15,15},4);
                g.drawLine(8,9,8,15);
                break;
            case EDIT:
                g.drawRoundRect(1,3,11,11,2,2);
                g.setStroke(new BasicStroke(1.4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.drawLine(6,9,13,2);g.drawLine(5,10,6,9);g.drawLine(13,2,14,3);g.drawLine(14,3,7,10);g.drawLine(7,10,5,10);
                break;
            case ADD:g.drawLine(8,2,8,14);g.drawLine(2,8,14,8);break;
            case CLOSE:g.drawLine(3,3,13,13);g.drawLine(13,3,3,13);break;
            case SELECT:g.drawRoundRect(1,5,10,10,2,2);g.drawRoundRect(5,1,10,10,2,2);break;
            case PIN:{
                java.awt.geom.Path2D.Double pin=new java.awt.geom.Path2D.Double();
                pin.moveTo(8,15);pin.curveTo(2,9,3,1,8,1);pin.curveTo(13,1,14,9,8,15);g.draw(pin);
                g.drawOval(6,4,4,4);break;
            }
            case ROUTE:g.drawOval(1,1,4,4);g.drawOval(11,11,4,4);g.drawPolyline(new int[]{3,3,13,13},new int[]{5,8,8,11},4);break;
            case TRASH:
                g.drawLine(2,4,14,4);g.drawPolyline(new int[]{6,6,10,10},new int[]{4,2,2,4},4);
                g.drawPolyline(new int[]{4,5,11,12},new int[]{4,14,14,4},4);
                g.drawLine(7,7,7,11);g.drawLine(9,7,9,11);break;
            case CHECK:g.drawPolyline(new int[]{2,6,14},new int[]{8,12,3},3);break;
            case REFRESH:g.drawArc(2,2,12,12,40,280);g.drawLine(12,2,12,6);g.drawLine(12,6,8,6);break;
            case SEARCH:g.drawOval(2,2,9,9);g.drawLine(10,10,14,14);break;
            case LOCATE:
                g.drawOval(3,3,10,10);g.drawLine(8,0,8,4);g.drawLine(8,12,8,16);g.drawLine(0,8,4,8);g.drawLine(12,8,16,8);
                g.fillOval(7,7,2,2);break;
            case RULER:
                g.drawRoundRect(1,5,14,6,1,1);
                for(int x=4;x<=12;x+=3)g.drawLine(x,5,x,8);
                break;
            case DOWNLOAD:
                g.drawLine(8,1,8,9);g.drawPolyline(new int[]{4,8,12},new int[]{6,10,6},3);
                g.drawPolyline(new int[]{2,2,14,14},new int[]{10,14,14,10},4);break;
            case PANEL:g.drawRoundRect(1,2,14,12,2,2);g.drawLine(10,2,10,14);break;
            case HELP:
                g.drawOval(1,1,14,14);g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD,11f));
                g.setStroke(new BasicStroke(1f));g.drawString("?",5,12);break;
            case DOWN:g.drawPolyline(new int[]{3,8,13},new int[]{6,11,6},3);break;
            case RIGHT:g.drawPolyline(new int[]{6,11,6},new int[]{3,8,13},3);break;
            case HOUSE:
                g.drawPolyline(new int[]{1,8,15},new int[]{8,1,8},3);
                g.drawRect(3,8,10,7);g.drawRect(6,11,4,4);break;
            case FLAME:{
                java.awt.geom.Path2D.Double flame=new java.awt.geom.Path2D.Double();
                flame.moveTo(8,1);flame.curveTo(15,7,13,15,8,15);flame.curveTo(3,15,1,9,6,6);flame.curveTo(6,4,7,3,8,1);
                g.draw(flame);break;
            }
            case BOX:g.drawRoundRect(2,3,12,10,2,2);g.drawLine(2,7,14,7);break;
            case FILTER:g.drawPolygon(new int[]{1,15,10,10,6,6},new int[]{2,2,8,14,12,8},6);break;
        }
        g.dispose();
        return states[slot]=new TexI(image);
    }
    private int state(){return disabled?3:down?1:on?4:hover?2:0;}
    public void draw(GOut g){g.image(image(glyph,state(),framed),Coord.z);}
    public boolean checkhit(Coord c){return framed&&c.isect(Coord.z,sz);}
    public boolean mousedown(MouseDownEvent ev){
        if(ev.b!=1||disabled)return ev.b==1;
        down=true;grab=ui.grabmouse(this);return true;
    }
    public boolean mouseup(MouseUpEvent ev){
        if(ev.b!=1||!down)return false;
        down=false;if(grab!=null){grab.remove();grab=null;}
        if(ev.c.isect(Coord.z,sz))click();
        return true;
    }
    public boolean mousehover(MouseHoverEvent ev,boolean on){hover=on&&!disabled;return super.mousehover(ev,on);}
    public Object tooltip(Coord c,Widget prev){return tip.get();}
    public void dispose(){if(grab!=null)grab.remove();grab=null;super.dispose();}
}
