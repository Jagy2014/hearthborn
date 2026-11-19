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
import javax.imageio.ImageIO;

/**
 * A local, transparent crosshair and rectangle; its hotspot is the crosshair center.
 *
 * @author Jagy2014
 */
public final class AreaSelectionCursor {
    private static Resource cached;
    public static synchronized Resource resource(){
        if(cached!=null)return cached;
        BufferedImage icon=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=icon.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int pass=0;pass<2;pass++){
            g.setColor(pass==0?new Color(20,15,8):new Color(255,221,134));
            g.setStroke(new BasicStroke(pass==0?4:1.5f,BasicStroke.CAP_SQUARE,BasicStroke.JOIN_MITER));
            g.drawLine(2,7,12,7);g.drawLine(7,2,7,12);
            g.drawLine(15,14,21,14);g.drawLine(15,14,15,20);
            g.drawLine(28,14,24,14);g.drawLine(28,14,28,20);
            g.drawLine(15,28,15,24);g.drawLine(15,28,21,28);
            g.drawLine(28,28,24,28);g.drawLine(28,28,28,24);
        }
        g.dispose();
        try{
            Resource.Virtual result=new Resource.Virtual(Resource.local(),"hearthborn/area-selection-cursor",1);
            ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(icon,"png",png);
            MessageBuf image=new MessageBuf();image.addint16((short)0).addint16((short)-1).adduint8(0).addint16((short)-1).addint16((short)0).addint16((short)0).addbytes(png.toByteArray());
            result.add(result.new Image(new MessageBuf(image.fin())));
            MessageBuf neg=new MessageBuf();for(int value:new int[]{7,7,0,0,0,0,32,32})neg.addint16((short)value);neg.adduint8(0);
            result.add(result.new Neg(new MessageBuf(neg.fin())));cached=result;return result;
        }catch(IOException failure){throw new RuntimeException(failure);}
    }
    private AreaSelectionCursor(){}
}
