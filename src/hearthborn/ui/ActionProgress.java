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
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

/**
 * Compact authored hourglass, a server-driven progress ring and an unbacked percentage.
 *
 * @author Jagy2014
 */
public class ActionProgress extends Widget {
    private static final int WIDTH=56,HEIGHT=72,RING=48,ICON=40,FONT_SIZE=12,SAMPLES=2;
    private static final double SIZE=1.5;
    private static final Color OUTLINE=new Color(30,22,12,240),TRACK=new Color(105,81,42,230);
    private static final Color GOLD=new Color(218,166,67),HIGHLIGHT=new Color(255,224,142),INK=new Color(255,247,220);
    private static final Color SAND_LIGHT=new Color(255,214,123),SAND_DARK=new Color(183,105,34);
    private static final BufferedImage ART=art();
    private static final Shape UPPER_BULB=upperBulb();
    public double prog;
    private TexI image;
    private int rendered=-1;
    private double density=-1;

    public ActionProgress(double progress){super(UI.scale((int)(WIDTH*SIZE),(int)(HEIGHT*SIZE)));set(progress);}
    private static BufferedImage art(){
        try{return Assets.image("icons/meters/action-hourglass-empty.png");}
        catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
    public void set(double progress){
        prog=Double.isFinite(progress)?Utils.clip(progress,0,1):0;
        tooltip=String.format(Locale.ROOT,"%d%%",percent());
    }
    private int percent(){return Utils.clip((int)Math.floor(prog*100),0,100);}
    /** Rasterize at the presentation resolution so HUD scaling does not enlarge a low-resolution label. */
    private void updateImage(){
        double scale=HudInterfaceScale.forUI(ui)*LiveScale.ratio();int amount=percent();
        if(image!=null&&amount==rendered&&scale==density)return;
        if(image!=null)image.dispose();
        image=new TexI(render(prog,scale));rendered=amount;density=scale;
    }
    public static BufferedImage render(double progress,double density){
        double value=Double.isFinite(progress)?Utils.clip(progress,0,1):0;
        double scale=UI.scale(1.0)*density*SIZE;
        Coord pixels=Coord.of((int)Math.ceil(WIDTH*scale),(int)Math.ceil(HEIGHT*scale));
        BufferedImage sampled=TexI.mkbuf(pixels.mul(SAMPLES));
        Graphics2D g=sampled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(scale*SAMPLES,scale*SAMPLES);
        double x=(WIDTH-RING)/2.0+2,y=4,diameter=RING-4;
        Ellipse2D track=new Ellipse2D.Double(x,y,diameter,diameter);
        g.setColor(OUTLINE);g.setStroke(new BasicStroke(4.5f));g.draw(track);
        g.setColor(TRACK);g.setStroke(new BasicStroke(2.5f));g.draw(track);
        if(value>0){
            g.setPaint(new GradientPaint(0,(float)y,HIGHLIGHT,0,(float)(y+diameter),GOLD));
            g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(x,y,diameter,diameter,90,-360*value,Arc2D.OPEN));
        }
        drawHourglass(g,value);
        int percent=Utils.clip((int)Math.floor(value*100),0,100);
        Font font=Text.sans.deriveFont(Font.BOLD,(float)FONT_SIZE);
        GlyphVector glyphs=font.createGlyphVector(g.getFontRenderContext(),percent+"%");
        Shape label=glyphs.getOutline();java.awt.geom.Rectangle2D bounds=label.getBounds2D();
        label=AffineTransform.getTranslateInstance(WIDTH/2.0-bounds.getCenterX(),52-bounds.getY()).createTransformedShape(label);
        g.setColor(OUTLINE);g.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(label);
        g.setColor(INK);g.fill(label);g.dispose();
        BufferedImage out=TexI.mkbuf(pixels);
        Graphics2D down=out.createGraphics();
        down.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        down.drawImage(sampled,0,0,pixels.x,pixels.y,null);down.dispose();
        return out;
    }
    /** Interior of the upper bulb in coordinates of the 128×128 artwork. */
    private static Shape upperBulb(){
        Path2D path=new Path2D.Double();
        path.moveTo(40,24);
        path.curveTo(33,42,48,53,62,61);
        path.quadTo(64,63,66,61);
        path.curveTo(80,53,95,42,88,24);
        path.closePath();
        return path;
    }

    private static void drawHourglass(Graphics2D target,double progress){
        Graphics2D glass=(Graphics2D)target.create();
        glass.translate((WIDTH-ICON)/2.0,6);
        glass.scale(ICON/128.0,ICON/128.0);
        glass.drawImage(ART,0,0,128,128,null);

        if(progress<1){
            double surface=28+35*progress;
            Area upper=new Area(UPPER_BULB);
            upper.intersect(new Area(new Rectangle2D.Double(0,surface,128,63-surface)));
            glass.setPaint(new GradientPaint(0,(float)surface,SAND_LIGHT,0,63,SAND_DARK));
            glass.fill(upper);
        }

        double amount=Math.sqrt(progress);
        double peak=105-28*amount;
        if(progress>0){
            double spread=23*amount;
            Path2D pile=new Path2D.Double();
            pile.moveTo(64-spread,105);
            pile.curveTo(64-spread*.75,105-7*amount,59,peak+2,64,peak);
            pile.curveTo(69,peak+2,64+spread*.75,105-7*amount,64+spread,105);
            pile.closePath();
            glass.setPaint(new GradientPaint(0,(float)peak,SAND_LIGHT,0,105,SAND_DARK));
            glass.fill(pile);
        }
        if(progress>0&&progress<1){
            glass.setColor(new Color(222,149,55,185));
            glass.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            glass.draw(new Line2D.Double(64,64,64,peak));
            double grain=64+(peak-64)*((progress*12)%1);
            glass.setColor(SAND_LIGHT);
            glass.fill(new Ellipse2D.Double(62.6,grain-1.4,2.8,2.8));
        }
        glass.dispose();
    }
    public void draw(GOut g){updateImage();g.image(image,Coord.z,sz);}
    public boolean checkhit(Coord at){
        if(!at.isect(Coord.z,sz))return false;updateImage();
        Coord pixel=Coord.of(at.x*image.back.getWidth()/sz.x,at.y*image.back.getHeight()/sz.y);
        return Utils.checkhit(image.back,pixel,10);
    }
    public void dispose(){if(image!=null){image.dispose();image=null;}super.dispose();}
}
