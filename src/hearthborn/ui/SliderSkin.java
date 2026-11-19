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
import java.util.HashMap;
import java.util.Map;

/**
 * The slider in the look of our buttons: a groove with a thin line, and a graphite pill for a handle.
 *
 * @author Jagy2014
 */
public final class SliderSkin {
    private static final Color GROOVE=new Color(0x0E,0x0D,0x0C),LIGHT_GROOVE=Parchment.SELECTED,LIGHT_RIM=Parchment.FRAME;
    private static final Map<String,Tex> cache=new HashMap<>();
    private SliderSkin(){}

    public static int handleWidth(){return Math.max(6,UI.scale(12));}
    public static int handleHeight(){return Math.max(8,UI.scale(18));}
    public static int trackHeight(){return Math.max(4,UI.scale(6));}
    /** The colour of the part of the groove up to the handle: the same line colour as the buttons, a little fainter. */
    public static Color filled(boolean light){
        Color ring=light?LIGHT_RIM:SystemButtonSkin.ring(false);
        return new Color(ring.getRed(),ring.getGreen(),ring.getBlue(),light?150:120);
    }

    private static Tex cached(String key,java.util.function.Supplier<BufferedImage> make){
        synchronized(cache){
            Tex t=cache.get(key);
            if(t==null)cache.put(key,t=new TexI(make.get()));
            return t;
        }
    }

    /** The groove, {@code w}x{@code h}: a dark rounded bar with a thin line round it. */
    public static Tex track(int w,int h,boolean light){
        return cached("t"+w+"x"+h+(light?"L":"D")+AccentTheme.current(),()->{
            BufferedImage img=TexI.mkbuf(Coord.of(Math.max(1,w),Math.max(1,h)));
            Graphics2D g=img.createGraphics();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                float line=Math.max(1,UI.scale(1));
                Color ring=light?LIGHT_RIM:SystemButtonSkin.ring(false);
                g.setColor(ring);
                g.fill(new RoundRectangle2D.Float(0,0,w,h,h,h));
                g.setColor(light?LIGHT_GROOVE:GROOVE);
                g.fill(new RoundRectangle2D.Float(line,line,w-line*2,h-line*2,Math.max(0,h-line*2),Math.max(0,h-line*2)));
            }finally{g.dispose();}
            return img;
        });
    }

    /** The handle: the face of a button, lit while the pointer is on it or it is being dragged. */
    public static Tex handle(int w,int h,boolean pressed,boolean hover,boolean light){
        return cached("h"+w+"x"+h+(pressed?"P":"")+(hover?"H":"")+(light?"L":"D")+AccentTheme.current(),()->{
            BufferedImage img=TexI.mkbuf(Coord.of(w,h));
            PillSkin.paint(img,0,h,pressed,hover,light);
            return img;
        });
    }

    /** The settings slider: a thin line and a plain rounded handle, tan when it is lit. */
    public static int settingsHandleWidth(){return Math.max(6,UI.scale(10));}
    public static int settingsHandleHeight(){return Math.max(8,UI.scale(18));}
    public static int settingsTrackHeight(){return Math.max(2,UI.scale(3));}
    /** The part of the settings slider up to the handle. */
    public static Color settingsFilled(){
        Color accent=AccentTheme.current().sliderFill();
        return accent!=null?accent:UiPalette.text2();
    }
    public static Tex settingsHandle(int w,int h,boolean pressed,boolean hover){
        Color accent=AccentTheme.current().sliderHandle(pressed||hover);
        Color fill=accent!=null?accent:pressed||hover?UiPalette.text():UiPalette.text2();
        return cached("s"+w+"x"+h+Integer.toHexString(fill.getRGB()),()->{
            BufferedImage img=TexI.mkbuf(Coord.of(w,h));
            Graphics2D g=img.createGraphics();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                float arc=Math.max(2,UI.scale(3));
                g.fill(new RoundRectangle2D.Float(0,0,w,h,arc,arc));
            }finally{g.dispose();}
            return img;
        });
    }
}
