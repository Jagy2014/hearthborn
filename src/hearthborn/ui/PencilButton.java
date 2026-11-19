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

/**
 * Small gold-and-brown header control drawn at the current interface resolution.
 *
 * @author Jagy2014
 */
public final class PencilButton extends IButton {
    private static BufferedImage image(int state){
        int size=UI.scale(SmallWindowDeco.HEADER_BUTTON);
        BufferedImage image=TexI.mkbuf(Coord.of(size,size));
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(size/16.0,size/16.0);
        g.setPaint(new GradientPaint(0,1,new Color(state==1?70:118,83,43),0,15,new Color(48,34,20)));
        g.fillRoundRect(1,1,14,14,3,3);
        g.setColor(state==2?new Color(255,224,133):new Color(203,171,102));
        g.setStroke(new BasicStroke(1));g.drawRoundRect(1,1,14,14,3,3);
        if(state==1)g.translate(0,.5);
        g.setColor(new Color(47,29,13));g.fillPolygon(new int[]{4,10,13,7},new int[]{10,3,6,13},4);
        g.setColor(new Color(238,190,82));g.fillPolygon(new int[]{5,10,12,7},new int[]{10,4,6,12},4);
        g.setColor(new Color(255,235,177));g.drawLine(6,10,10,5);
        g.setColor(new Color(229,209,165));g.fillPolygon(new int[]{4,5,7},new int[]{13,10,12},3);
        g.setColor(new Color(36,27,20));g.drawLine(4,13,5,12);
        g.setColor(new Color(191,113,83));g.drawLine(11,4,12,5);
        g.dispose();return image;
    }
    public PencilButton(Runnable action){super(image(0),image(1),image(2),action);settip("Rename object");}
}
