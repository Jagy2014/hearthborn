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
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Shared meter geometry keeps the painted symbols larger than the fill strip.
 *
 * @author Jagy2014
 */
public final class MeterArtwork {
    private static final Map<String,Tex> frames=new HashMap<>();
    public static String replacementKind(String resource){
        if("gfx/hud/meter/hp".equals(resource))return "hp";
        if("gfx/hud/meter/stam".equals(resource))return "stam";
        if("gfx/hud/meter/nrj".equals(resource))return "nrj";
        if("gfx/hud/meter/häst".equals(resource))return "horse-stamina";
        if("gfx/hud/meter/mount".equals(resource))return "horse-food";
        if("gfx/hud/meter/boat".equals(resource))return "boat";
        return null;
    }
    public static Tex replacement(String resource){String kind=replacementKind(resource);return kind==null?null:frame(kind);}
    public static synchronized Tex frame(String kind){
        Tex frame=frames.get(kind);
        if(frame==null){frame=new TexI(PUtils.uiscale(image(kind),IMeter.fsz));frames.put(kind,frame);}
        return frame;
    }
    public static BufferedImage image(String kind){
        try{
            boolean horse=kind.equals("horse-stamina")||kind.equals("horse-food");
            BufferedImage symbol=load(horse?"horse":kind);
            BufferedImage image=PUtils.rasterimg(PUtils.imgraster(Coord.of(404,96)));
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.scale(4,4);
            g.setColor(new Color(55,30,15));g.fillRoundRect(18,3,83,18,11,11);
            g.setPaint(new GradientPaint(0,4,new Color(255,239,159),0,20,new Color(153,88,28)));
            g.fillRoundRect(19,4,81,16,10,10);
            g.setColor(new Color(255,218,117));g.setStroke(new BasicStroke(.6f));g.drawRoundRect(20,5,79,14,9,9);
            g.setColor(new Color(62,39,22));g.fillRoundRect(21,6,77,12,7,7);
            g.setComposite(AlphaComposite.Clear);g.fillRect(22,7,75,10);
            g.setComposite(AlphaComposite.SrcOver);
            if(horse){
                fitIcon(g,symbol,4,0,20,23);
                fitIcon(g,load(kind.equals("horse-stamina")?"nrj":"carrot"),0,3,15,21);
            }else g.drawImage(symbol,0,0,24,24,null);
            g.dispose();return image;
        }catch(IOException error){throw new IllegalStateException("Could not load meter artwork",error);}
    }
    private static void fitIcon(Graphics2D g,BufferedImage icon,int x,int y,int width,int height){
        int left=icon.getWidth(),top=icon.getHeight(),right=-1,bottom=-1;
        for(int sy=0;sy<icon.getHeight();sy++)for(int sx=0;sx<icon.getWidth();sx++)if((icon.getRGB(sx,sy)>>>24)>24){
            left=Math.min(left,sx);top=Math.min(top,sy);right=Math.max(right,sx);bottom=Math.max(bottom,sy);
        }
        if(right<left)return;
        double scale=Math.min(width/(double)(right-left+1),height/(double)(bottom-top+1));
        int w=(int)Math.round((right-left+1)*scale),h=(int)Math.round((bottom-top+1)*scale);
        int dx=x+(width-w)/2,dy=y+(height-h)/2;
        g.drawImage(icon,dx,dy,dx+w,dy+h,left,top,right+1,bottom+1,null);
    }
    private static BufferedImage load(String kind)throws IOException{
        try(InputStream in=MeterArtwork.class.getResourceAsStream("/hearthborn/assets/icons/meters/"+kind+".png")){
            if(in==null)throw new IOException("Missing meter symbol: "+kind);
            return ImageIO.read(in);
        }
    }
    private MeterArtwork(){}
}
