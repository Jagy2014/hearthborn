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
import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * The window moulding follows the accent theme. In the gold theme the sprites are drawn as they are. In a flat theme (blue)
 * the sprites are not drawn at all: the band they occupy is filled like the window and a one-pixel line of the theme's
 * frame colour runs along the outer edge, so the layout of every window stays exactly the same.
 *
 * @author Jagy2014
 */
public final class FrameSkin {
    /** The piece of moulding {@code base/name} as it is on disk. */
    public static Tex tex(String base,String name){return Resource.loadtex(base+"/"+name);}
    /** The moulding that a flat theme replaces: windows, and the boxes of item stacks. */
    public static boolean replaceable(String base){return base.equals("gfx/hud/wnd")||base.equals("gfx/hud/bosq");}
    public static boolean flat(){return AccentTheme.flatFrames();}
    /** A colour for panel lines: the first in the gold theme, the second in the blue one. */
    public static Color line(Color gold,Color blue){return AccentTheme.blue()?blue:gold;}

    /**
     * Draws the flat frame into the box {@code at}/{@code size}. The four numbers are the thickness of the band the sprites
     * would occupy. Without {@code top} or {@code bottom} that edge is left open, for windows stacked on each other.
     */
    public static void flat(GOut g,Coord at,Coord size,int left,int top,int right,int bottom,boolean topEdge,boolean bottomEdge){
        g.chcolor(UiTheme.BACKGROUND);
        if(topEdge)g.frect(at,Coord.of(size.x,top));
        if(bottomEdge)g.frect(at.add(0,size.y-bottom),Coord.of(size.x,bottom));
        int from=topEdge?top:0,to=bottomEdge?size.y-bottom:size.y;
        g.frect(at.add(0,from),Coord.of(left,Math.max(0,to-from)));
        g.frect(at.add(size.x-right,from),Coord.of(right,Math.max(0,to-from)));
        g.chcolor(AccentTheme.current().frame());
        int unit=Math.max(1,UI.scale(1));
        if(topEdge)g.frect(at,Coord.of(size.x,unit));
        if(bottomEdge)g.frect(at.add(0,size.y-unit),Coord.of(size.x,unit));
        g.frect(at,Coord.of(unit,size.y));
        g.frect(at.add(size.x-unit,0),Coord.of(unit,size.y));
        g.chcolor();
    }

    /** A line of the theme's frame colour, {@code width} units thick, drawn just inside the box; nothing is filled. */
    public static void thin(GOut g,Coord at,Coord size,int width){
        int unit=Math.max(1,UI.scale(1))*Math.max(1,width);
        g.chcolor(AccentTheme.current().frame());
        g.frect(at,Coord.of(size.x,unit));
        g.frect(at.add(0,size.y-unit),Coord.of(size.x,unit));
        g.frect(at,Coord.of(unit,size.y));
        g.frect(at.add(size.x-unit,0),Coord.of(unit,size.y));
        g.chcolor();
    }

    /** The picture with its colours moved to the theme's hue; grey and white stay as they are. */
    public static BufferedImage recolor(BufferedImage source,AccentTheme theme){
        Float hue=theme.hue();
        if(hue==null)return source;
        BufferedImage out=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        float[] hsb=new float[3];
        for(int y=0;y<out.getHeight();y++)for(int x=0;x<out.getWidth();x++){
            int argb=source.getRGB(x,y),a=argb>>>24;
            if(a==0)continue;
            Color.RGBtoHSB(argb>>16&255,argb>>8&255,argb&255,hsb);
            if(hsb[1]>0.12f)hsb[1]=Math.min(1f,hsb[1]*1.15f);
            int rgb=hsb[1]>0.12f?Color.HSBtoRGB(hue,hsb[1],hsb[2]):argb&0xFFFFFF;
            out.setRGB(x,y,a<<24|rgb&0xFFFFFF);
        }
        return out;
    }
    private FrameSkin(){}
}
