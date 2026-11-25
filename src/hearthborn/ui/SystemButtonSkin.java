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
import hearthborn.assets.Assets;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

/**
 * Shared native system-control disks; the settings cog sits on the same wood-brown face and gold ring as the others.
 *
 * @author Jagy2014
 */
public final class SystemButtonSkin {
    public static final Color DISK=new Color(0x1c,0x1a,0x17);
    /** Warm wood-brown medallion face, close to the window moulding. Pressed or switched-on buttons glow gold. */
    public static final Color FACE=new Color(0x5c,0x3a,0x22);
    public static final Color FACE_LIT=new Color(0x8a,0x62,0x2e);
    private static final Color EDGE_SHADOW=new Color(0x1e,0x14,0x0c);
    /** The ring is the colour of the accent: gold, or the blue of the blue theme. */
    public static Color ring(boolean hover){return AccentTheme.current().ring(hover);}
    private static final List<String> NAMES=List.of("csearch","lbtn-claim","lbtn-vil","lbtn-rlm","rbtn-opt","rbtn-inv","rbtn-bud","rbtn-equ","rbtn-chr");
    private static final BufferedImage ATLAS=load("glyphs.png");
    private static final BufferedImage[] GLYPHS=glyphs();

    private static BufferedImage load(String name){
        try{return Assets.image("icons/system-menu/"+name);}
        catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
    private static BufferedImage[] glyphs(){
        BufferedImage[] result=new BufferedImage[9];
        for(int i=0;i<result.length;i++){
            int x=i%3,y=i/3;
            int left=x*ATLAS.getWidth()/3,top=y*ATLAS.getHeight()/3;
            int right=(x+1)*ATLAS.getWidth()/3,bottom=(y+1)*ATLAS.getHeight()/3;
            result[i]=trim(ATLAS.getSubimage(left,top,right-left,bottom-top),NAMES.get(i));
        }
        result[NAMES.indexOf("rbtn-opt")]=trim(load("settings.png"),"settings");
        return result;
    }
    /** The picture drawn inside a system medallion, for the same control elsewhere. */
    public static BufferedImage glyph(String name){
        int index=NAMES.indexOf(name);
        if(index<0)throw new IllegalArgumentException("Unknown system glyph: "+name);
        return GLYPHS[index];
    }
    private static BufferedImage trim(BufferedImage source,String name){
        int minX=source.getWidth(),minY=source.getHeight(),maxX=-1,maxY=-1;
        for(int row=0;row<source.getHeight();row++)for(int col=0;col<source.getWidth();col++)if((source.getRGB(col,row)>>>24)>=32){
            minX=Math.min(minX,col);minY=Math.min(minY,row);maxX=Math.max(maxX,col);maxY=Math.max(maxY,row);
        }
        if(maxX<minX||maxY<minY)throw new IllegalStateException("Empty system glyph: "+name);
        return source.getSubimage(minX,minY,maxX-minX+1,maxY-minY+1);
    }
    /** Native ICheckBox state order: up, down, hover-up, hover-down. */
    public static BufferedImage[] images(String name,int side){
        int index=NAMES.indexOf(name);
        if(index<0)throw new IllegalArgumentException("Unknown system glyph: "+name);
        BufferedImage[] states=new BufferedImage[4];
        for(int state=0;state<states.length;state++)states[state]=render(GLYPHS[index],side,(state&1)!=0,state>=2,"rbtn-opt".equals(name));
        return states;
    }
    /** The same states without a medallion of their own, for a button that sits in a frame: only the glyph on the frame's own brown. */
    public static BufferedImage[] bareImages(String name,int side){
        int index=NAMES.indexOf(name);
        if(index<0)throw new IllegalArgumentException("Unknown system glyph: "+name);
        BufferedImage[] states=new BufferedImage[4];
        for(int state=0;state<states.length;state++)states[state]=renderBare(GLYPHS[index],side,(state&1)!=0,state>=2);
        return states;
    }
    private static final Color FRAME_TOP=new Color(68,41,27),FRAME_BOTTOM=new Color(60,34,21);
    private static BufferedImage renderBare(BufferedImage glyph,int side,boolean active,boolean hover){
        BufferedImage result=TexI.mkbuf(Coord.of(side,side));
        Graphics2D g=result.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int lift=active?34:hover?16:0;
            g.setPaint(new java.awt.GradientPaint(0,0,lighter(FRAME_TOP,lift),0,side,lighter(FRAME_BOTTOM,lift)));
            g.fill(new Ellipse2D.Double(0,0,side,side));
            double scale=(side*.78)/Math.max(glyph.getWidth(),glyph.getHeight());
            int width=Math.max(1,(int)Math.round(glyph.getWidth()*scale)),height=Math.max(1,(int)Math.round(glyph.getHeight()*scale));
            g.drawImage(glyph,(side-width)/2,(side-height)/2,width,height,null);
        }finally{g.dispose();}
        return result;
    }
    private static BufferedImage render(BufferedImage glyph,int side,boolean active,boolean hover,boolean settings){
        BufferedImage result=TexI.mkbuf(Coord.of(side,side));
        Graphics2D g=result.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            paintDisk(g,side,active,hover);
            double fraction=settings?.7:.72;
            double scale=(side*fraction)/Math.max(glyph.getWidth(),glyph.getHeight());
            int width=Math.max(1,(int)Math.round(glyph.getWidth()*scale)),height=Math.max(1,(int)Math.round(glyph.getHeight()*scale));
            g.drawImage(glyph,(side-width)/2,(side-height)/2,width,height,null);
        }finally{g.dispose();}
        return result;
    }
    /** Draw the shared medallion without its glyph. */
    public static void paintDisk(Graphics2D g,int side,boolean active,boolean hover){
        float line=Math.max(1,UI.scale(2));
        Ellipse2D disk=new Ellipse2D.Double(line,line,side-line*2,side-line*2);
        g.setColor(EDGE_SHADOW);g.fill(new Ellipse2D.Double(0,0,side,side));
        Color face=active?FACE_LIT:FACE;
        g.setPaint(new java.awt.GradientPaint(0,line,lighter(face,26),0,side-line,darker(face,18)));g.fill(disk);
        g.setStroke(new BasicStroke(line));
        g.setColor(ring(hover));g.draw(disk);
    }
    private static Color lighter(Color c,int by){return new Color(Math.min(255,c.getRed()+by),Math.min(255,c.getGreen()+by),Math.min(255,c.getBlue()+by));}
    private static Color darker(Color c,int by){return new Color(Math.max(0,c.getRed()-by),Math.max(0,c.getGreen()-by),Math.max(0,c.getBlue()-by));}
    private SystemButtonSkin(){}
}
