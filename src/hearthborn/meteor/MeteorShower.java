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

package hearthborn.meteor;

import haven.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * A small decorative shower: one reusable texture and prechecked paths inside the calendar sky.
 *
 * @author Jagy2014
 */
public final class MeteorShower implements Disposable {
    static final double PERIOD=3.0;
    static final double[] START={.35,1.05,1.7},DURATION={.9,1,.85};
    static final int FRAMES=48;
    private static final double[][] PATHS={{.60,.25,.25,.62},{.40,.22,.08,.55},{.72,.36,.40,.74}};
    private BufferedImage mask,image;
    private Tex texture;
    private final Coord[][] positions=new Coord[3][FRAMES];
    private double time;
    private boolean active;

    public void tick(double dt,boolean enabled){
        active=enabled;
        if(!enabled){time=0;return;}
        if(Double.isFinite(dt)&&dt>0)time=(time+Math.min(dt,.25))%PERIOD;
    }
    static BufferedImage artwork(){
        // Draw at four source pixels per UI pixel for smooth curves on Retina displays.
        BufferedImage source=new BufferedImage(56,64,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=source.createGraphics();g.scale(4,4);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(255,147,40,32));g.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(12,3,3.5,13));
        Path2D tail=new Path2D.Double();tail.moveTo(13.5,1);tail.lineTo(2.3,12.2);tail.lineTo(4.7,14.1);tail.closePath();
        g.setPaint(new GradientPaint(13.5f,1,new Color(255,144,38,0),3.5f,13,new Color(255,222,124,235)));g.fill(tail);
        g.setPaint(new GradientPaint(10,6,new Color(255,208,110,0),3.5f,13,new Color(255,248,218,255)));
        g.setStroke(new BasicStroke(.9f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(new Line2D.Double(10,6,3.5,13));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(3.5f,13),3,
            new float[]{0,.4f,1},new Color[]{new Color(255,237,171,210),new Color(255,181,60,95),new Color(255,133,25,0)}));
        g.fill(new Ellipse2D.Double(.5,10,6,6));
        g.setColor(new Color(255,251,227));g.fill(new Ellipse2D.Double(2.4,11.9,2.2,2.2));g.dispose();
        return PUtils.uiscale(PUtils.coercergba(source),UI.scale(14,16));
    }
    private void prepare(BufferedImage sky){
        if(mask==sky&&texture!=null)return;
        if(texture!=null)texture.dispose();mask=sky;image=artwork();texture=new TexI(image);
        int left=sky.getWidth(),top=sky.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<sky.getHeight();y++)for(int x=0;x<sky.getWidth();x++)if((sky.getRGB(x,y)>>>24)>=240){
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        }
        for(int track=0;track<positions.length;track++)for(int frame=0;frame<FRAMES;frame++){
            positions[track][frame]=null;if(right<left||bottom<top)continue;
            double progress=frame/(double)(FRAMES-1);double[] path=PATHS[track];
            int x=(int)Math.round(left+(path[0]+(path[2]-path[0])*progress)*(right-left)-UI.scale(3.5));
            int y=(int)Math.round(top+(path[1]+(path[3]-path[1])*progress)*(bottom-top)-UI.scale(13.0));
            Coord point=Coord.of(x,y);
            if(fits(sky,image,point))positions[track][frame]=point;
        }
    }
    static boolean fits(BufferedImage sky,BufferedImage sprite,Coord at){
        for(int y=0;y<sprite.getHeight();y++)for(int x=0;x<sprite.getWidth();x++)if((sprite.getRGB(x,y)>>>24)>0){
            int sx=at.x+x,sy=at.y+y;
            if(sx<0||sy<0||sx>=sky.getWidth()||sy>=sky.getHeight()||(sky.getRGB(sx,sy)>>>24)<240)return false;
        }
        return true;
    }
    private static double progress(double time,int track){return (time-START[track])/DURATION[track];}
    static int opacity(double progress){return progress<=0||progress>=1?0:(int)Math.round(235*Math.pow(Math.sin(Math.PI*progress),.7));}
    public void draw(GOut g,BufferedImage sky,Coord offset){
        if(!active)return;prepare(sky);
        for(int track=0;track<positions.length;track++){
            double progress=progress(time,track);int alpha=opacity(progress);if(alpha==0)continue;
            Coord point=positions[track][Math.min(FRAMES-1,(int)(progress*(FRAMES-1)))];if(point==null)continue;
            g.chcolor(255,255,255,alpha);g.image(texture,point.add(offset));
        }
        g.chcolor();
    }
    public void dispose(){if(texture!=null)texture.dispose();texture=null;mask=null;image=null;}
}
