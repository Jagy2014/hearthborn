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
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Native control images and compact viewports shared by the map and HUD.
 *
 * @author Jagy2014
 */
public final class SystemControls {
    private SystemControls() {}

    /** Clip transparent placement padding; keep the original widget and event handlers. */
    public static Widget compact(Widget control,Coord cell){return new CompactControl(control,cell,bounds(control));}

    /** One crop/scale for every state keeps native hit tests and pressed images aligned. */
    public static BufferedImage[] buttonImages(String... resources){
        return buttonImages(UI.scale(26),false,resources);
    }
    /** Fit all states to the action icon size, including enlargement of small originals. */
    public static BufferedImage[] buttonImages(int maximum,String... resources){
        return buttonImages(maximum,true,resources);
    }
    private static BufferedImage[] buttonImages(int maximum,boolean enlarge,String... resources){
        List<BufferedImage> images=new ArrayList<>();
        for(String resource:resources)images.add(Resource.loadsimg(resource));
        Area bounds=visibleBounds(images);
        double scale=maximum/(double)Math.max(bounds.sz().x,bounds.sz().y);
        if(!enlarge)scale=Math.min(1,scale);
        Coord size=Coord.of(Math.max(1,(int)Math.round(bounds.sz().x*scale)),Math.max(1,(int)Math.round(bounds.sz().y*scale)));
        BufferedImage[] result=new BufferedImage[images.size()];
        for(int i=0;i<result.length;i++){
            BufferedImage crop=TexI.mkbuf(bounds.sz());java.awt.Graphics2D g=crop.createGraphics();
            g.drawImage(images.get(i),-bounds.ul.x,-bounds.ul.y,null);g.dispose();
            result[i]=size.equals(bounds.sz())?crop:PUtils.uiscale(crop,size);
        }
        return result;
    }

    /** Same square for every state, with the picture's weight in the middle. */
    public static BufferedImage[] square(BufferedImage[] images, int side) {
        BufferedImage[] out = new BufferedImage[images.length];
        for(int i = 0; i < images.length; i++) out[i] = square(images[i], side);
        return out;
    }
    private static BufferedImage square(BufferedImage src, int side) {
        long weightX = 0, weightY = 0, weight = 0;
        for(int y = 0; y < src.getHeight(); y++) for(int x = 0; x < src.getWidth(); x++) {
            if((src.getRGB(x, y) >>> 24) < 16) continue;
            weightX += x; weightY += y; weight++;
        }
        int dx = weight == 0 ? (side - src.getWidth()) / 2 : side / 2 - (int)Math.round(weightX / (double)weight);
        int dy = weight == 0 ? (side - src.getHeight()) / 2 : side / 2 - (int)Math.round(weightY / (double)weight);
        int minX = Math.min(0, side - src.getWidth()), maxX = Math.max(0, side - src.getWidth());
        int minY = Math.min(0, side - src.getHeight()), maxY = Math.max(0, side - src.getHeight());
        dx = Math.max(minX, Math.min(maxX, dx));
        dy = Math.max(minY, Math.min(maxY, dy));
        BufferedImage dst = TexI.mkbuf(Coord.of(side, side));
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, dx, dy, null);
        g.dispose();
        return dst;
    }

    private static Area bounds(Widget control){
        List<BufferedImage> images=new ArrayList<>();
        if(control instanceof ICheckBox){
            ICheckBox box=(ICheckBox)control;
            for(Tex texture:new Tex[]{box.up,box.down,box.hoverup,box.hoverdown}){
                if(!(texture instanceof TexI))throw new IllegalArgumentException("System control needs an image texture");
                images.add(((TexI)texture).back);
            }
        }else if(control instanceof IButton){
            IButton button=(IButton)control;
            images.add(button.up);images.add(button.down);images.add(button.hover);
        }else throw new IllegalArgumentException("Unsupported system control: "+control.getClass());
        return visibleBounds(images);
    }
    private static Area visibleBounds(List<BufferedImage> images){
        int left=Integer.MAX_VALUE,top=Integer.MAX_VALUE,right=-1,bottom=-1;
        for(BufferedImage image:images)for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)
            if((image.getRGB(x,y)>>>24)!=0){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        return right<0?Area.sized(Coord.z,Coord.of(1,1)):new Area(Coord.of(left,top),Coord.of(right+1,bottom+1));
    }
    private static final class CompactControl extends Widget {
        private final Widget control;
        CompactControl(Widget control,Coord cell,Area bounds){
            super(cell);this.control=control;
            add(control,cell.sub(bounds.sz()).div(2).sub(bounds.ul));
        }
        public void draw(GOut g){
            boolean active=control instanceof ACheckBox&&((ACheckBox)control).state();
            g.chcolor(active?new java.awt.Color(39,70,93,235):UiTheme.BACKGROUND);g.frect(Coord.z,sz);
            g.chcolor(active?new java.awt.Color(255,222,125):new java.awt.Color(126,111,76));g.rect(Coord.z,sz);g.chcolor();
            super.draw(g);
        }
    }
}
