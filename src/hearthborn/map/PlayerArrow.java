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

package hearthborn.map;

import haven.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * One painted arrow, with recolored fill and unchanged light/dark outlines.
 *
 * @author Jagy2014
 */
final class PlayerArrow {
    private static final BufferedImage SOURCE=source();
    private static final Map<Integer,TexI> CACHE=new LinkedHashMap<Integer,TexI>(16,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Integer,TexI> entry){
            if(size()<=64)return false;
            entry.getValue().dispose();return true;
        }
    };

    private static BufferedImage source(){
        try(InputStream in=PlayerArrow.class.getResourceAsStream("/hearthborn/assets/icons/map/player.png")){
            if(in==null)throw new IOException("Missing player arrow");
            return ImageIO.read(in);
        }catch(IOException error){throw new IllegalStateException(error);}
    }

    static synchronized TexI texture(Color color){
        // Party members start black until their server color arrives. Keep them visible meanwhile.
        if(Math.max(color.getRed(),Math.max(color.getGreen(),color.getBlue()))<48)color=Color.WHITE;
        final Color target=color;
        return CACHE.computeIfAbsent(color.getRGB()|0xff000000,key->new TexI(recolor(target)));
    }

    private static BufferedImage recolor(Color target){
        float[] tint=Color.RGBtoHSB(target.getRed(),target.getGreen(),target.getBlue(),null);
        BufferedImage image=TexI.mkbuf(Coord.of(SOURCE.getWidth(),SOURCE.getHeight()));
        float[] original=new float[3];
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int pixel=SOURCE.getRGB(x,y),alpha=pixel>>>24;
            Color.RGBtoHSB((pixel>>16)&255,(pixel>>8)&255,pixel&255,original);
            // Restrict recoloring to the red paint, preserving the cream rim and brown shadow.
            if(alpha>0&&(original[0]<.06f||original[0]>.96f)&&original[1]>.6f&&original[2]>.3f){
                int fill=Color.HSBtoRGB(tint[0],original[1]*tint[1],original[2]*tint[2]);
                pixel=(alpha<<24)|(fill&0xffffff);
            }
            image.setRGB(x,y,pixel);
        }
        return PUtils.uiscale(image,UI.scale(20,20));
    }
}
