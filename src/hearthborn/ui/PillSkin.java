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
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The face of a text button: a graphite pill with a gold line, or parchment in the light theme.
 *
 * @author Jagy2014
 */
public final class PillSkin {
    /** Buttons and window caption plates share one graphite face. */
    private static final Color GRAY=new Color(0x4C,0x4A,0x47),GRAPHITE=new Color(0x23,0x22,0x20),GRAY_LIT=new Color(0x68,0x65,0x61),GRAY_RIM=new Color(0x0E,0x0D,0x0C);
    private static final Color LIGHT_RIM=Parchment.LINE;
    private static final Color LIGHT_TOP=Parchment.PANEL,LIGHT_BOTTOM=Parchment.BG;
    private static final Color LIGHT_LIT_TOP=Parchment.PANEL,LIGHT_LIT_BOTTOM=Parchment.PANEL;
    private static final Color DARK_INK=new Color(0xF5,0xE2,0xA0),LIGHT_INK=Parchment.TEXT;

    public static float radius(float h){return Math.min(h/2,Math.max(2,UI.scale(3)));}

    /** Text colour that reads on the face of the current theme. */
    public static Color ink(boolean light){return light?LIGHT_INK:DARK_INK;}
    public static Color shade(boolean light){return light?null:new Color(0,0,0,170);}

    /** Draws the pill into the band {@code y .. y+h} of the image. Pressed flips the gradient, hover lights the face and the gold line. */
    public static void paint(BufferedImage img,int y,int h,boolean pressed,boolean hover,boolean light){
        Graphics2D g=img.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=img.getWidth();
            float line=Math.max(1,UI.scale(1));
            g.setColor(light?(hover?AccentTheme.current().ring(true):LIGHT_RIM):GRAY_RIM);
            float r=radius(h);
            g.fill(new RoundRectangle2D.Float(0,y,w,h,r*2,r*2));
            Color top,bottom;
            if(light){top=hover?LIGHT_LIT_TOP:LIGHT_TOP;bottom=hover?LIGHT_LIT_BOTTOM:LIGHT_BOTTOM;}
            else{
                top=hover||pressed?GRAY_LIT:GRAY;bottom=hover||pressed?GRAY:GRAPHITE;
            }
            if(pressed){Color swap=top;top=bottom;bottom=swap;}
            float in=line;
            g.setPaint(new GradientPaint(0,y+in,top,0,y+h-in,bottom));
            float ri=Math.max(0,r-in);
            g.fill(new RoundRectangle2D.Float(in,y+in,w-in*2,h-in*2,ri*2,ri*2));
            if(!light){
                g.setStroke(new BasicStroke(line));
                g.setColor(SystemButtonSkin.ring(hover));
                float gap=in+line/2;
                float rg=Math.max(0,r-gap);
                g.draw(new RoundRectangle2D.Float(gap,y+gap,w-gap*2,h-gap*2,rg*2,rg*2));
            }
        }finally{g.dispose();}
    }
    private static final java.util.Map<String,Tex> plates=new java.util.HashMap<>();
    /** A caption plate {@code w}x{@code h} like a button; always dark so the gold title reads in both themes. */
    public static Tex plate(int w,int h,boolean lit){
        String key=w+"x"+h+(lit?"L":"")+AccentTheme.current();
        synchronized(plates){
            Tex t=plates.get(key);
            if(t==null){
                BufferedImage img=TexI.mkbuf(Coord.of(w,h));
                paint(img,0,h,false,lit,false);
                plates.put(key,t=new TexI(img));
            }
            return t;
        }
    }
    private PillSkin(){}
}
