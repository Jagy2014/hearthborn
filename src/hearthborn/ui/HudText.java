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
 * Crisp white HUD lettering with a one-pixel black stroke.
 *
 * @author Jagy2014
 */
public final class HudText {
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans,10).aa(false);
    public static BufferedImage image(String value){
        String[] lines=value.split("\\n",-1);
        int width=1,height=FONT.height();
        for(String line:lines)width=Math.max(width,FONT.strsize(line).x);
        BufferedImage image=TexI.mkbuf(Coord.of(width+2,height*lines.length));
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(FONT.font);
        int baseline=g.getFontMetrics().getAscent();
        for(String line:lines){
            g.setColor(Color.BLACK);
            g.drawString(line,0,baseline);g.drawString(line,2,baseline);
            g.drawString(line,1,baseline-1);g.drawString(line,1,baseline+1);
            g.setColor(Color.WHITE);g.drawString(line,1,baseline);
            baseline+=height;
        }
        g.dispose();return image;
    }
    private HudText(){}
}
