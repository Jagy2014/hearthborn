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
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Painted movement poses share geometry and hit areas across walking and riding.
 *
 * @author Jagy2014
 */
public final class SpeedArtwork {
    public static Coord cellSize(){return UI.scale(23,24);}
    private static final Tex[][][] cache=new Tex[2][][];
    private static final Coord[][] cacheSizes=new Coord[2][];
    private static final String[][] NAMES={{"foot-sneak","foot-walk","foot-run","foot-sprint"},{"horse-slow","horse-walk","horse-trot","horse-gallop"}};
    public static synchronized Tex[][] images(boolean mounted,Coord[] cells,double scale){
        int mode=mounted?1:0;
        Coord[] sizes=new Coord[4];
        for(int i=0;i<4;i++)sizes[i]=Coord.of(Math.max(1,(int)Math.ceil(cells[i].x*scale)),Math.max(1,(int)Math.ceil(cells[i].y*scale)));
        if(!Arrays.equals(cacheSizes[mode],sizes)){
            Tex[][] images=new Tex[4][3];
            for(int i=0;i<4;i++){
                BufferedImage source=load(NAMES[mode][i]);
                for(int state=0;state<3;state++)images[i][state]=new TexI(render(source,state,sizes[i],Math.max(1,(int)Math.ceil(UI.scale(1)*scale))));
            }
            if(cache[mode]!=null)for(Tex[] states:cache[mode])for(Tex texture:states)texture.dispose();
            cache[mode]=images;
            cacheSizes[mode]=sizes;
        }
        return cache[mode];
    }
    private static BufferedImage load(String name){
        try(InputStream in=SpeedArtwork.class.getResourceAsStream("/hearthborn/assets/icons/speeds/"+name+".png")){
            if(in==null)throw new IOException("Missing speed icon: "+name);
            return ImageIO.read(in);
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    private static BufferedImage render(BufferedImage source,int state,Coord size,int padding){
        // Bake at the final on-screen resolution, including HUD magnification.
        // Reducing to a native-sized cell first loses detail before GPU enlargement.
        int factor=2;
        int w=size.x*factor,h=size.y*factor,pad=padding*factor;
        BufferedImage silhouette=PUtils.rasterimg(PUtils.imgraster(Coord.of(w,h)));
        Graphics2D g=silhouette.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)if((source.getRGB(x,y)>>>24)>24){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        if(right>=left){
            double scale=Math.min((w-pad*2)/(double)(right-left+1),(h-pad*2)/(double)(bottom-top+1));
            int sw=(int)Math.round((right-left+1)*scale),sh=(int)Math.round((bottom-top+1)*scale);
            g.drawImage(source,(w-sw)/2,h-pad-sh,(w+sw)/2,h-pad,left,top,right+1,bottom+1,null);
        }
        g.dispose();
        BufferedImage result=PUtils.rasterimg(PUtils.imgraster(Coord.of(w,h)));
        if(state==2){
            java.awt.image.WritableRaster alpha=PUtils.alpharaster(Coord.of(w,h));
            PUtils.copyband(alpha,0,Coord.z,silhouette.getRaster(),3);
            PUtils.imggrow(alpha,Math.max(1,pad/2));
            PUtils.alphadraw(result.getRaster(),alpha,Coord.z,new Color(57,216,255));
        }
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int pixel=silhouette.getRGB(x,y),a=pixel>>>24;
            if(a==0)continue;
            int r=(pixel>>16)&255,green=(pixel>>8)&255,b=pixel&255;
            if(state==0){int gray=(r+green+b)/3;r=green=b=gray;a=a*100/255;}
            else if(state==1){r=r*85/100;green=green*85/100;b=b*85/100;}
            else {r=Math.min(255,r+20);green=Math.min(255,green+15);}
            silhouette.setRGB(x,y,(a<<24)|(r<<16)|(green<<8)|b);
        }
        g=result.createGraphics();g.drawImage(silhouette,0,0,null);g.dispose();return PUtils.uiscale(result,size);
    }
    private SpeedArtwork(){}
}
