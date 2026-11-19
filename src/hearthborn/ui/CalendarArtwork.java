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
import hearthborn.assets.Assets;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Authored clock frame; native astronomy layers keep their original scale.
 *
 * @author Jagy2014
 */
public final class CalendarArtwork {
    private CalendarArtwork() {}
    public static Coord sceneOffset(){return UI.scale(0,3);}

    public static TexI frame(Coord sceneSize){
        try {
            BufferedImage source=Assets.image("icons/calendar/gold-frame.png");
            BufferedImage image=TexI.mkbuf(sceneSize.add(0,UI.scale(11)));
            // The supplied ornament's circular opening is centered at (840,425), radius 350.
            // Native calendar art has a 123px sky radius on its 292px source canvas.
            double scale=(sceneSize.y*123.0/292.0)/350.0;
            double x=sceneSize.x/2.0-840*scale;
            double y=sceneOffset().y+sceneSize.y/2.0-425*scale;
            Graphics2D graphics=image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source,new AffineTransform(scale,0,0,scale,x,y),null);
            graphics.dispose();
            return new TexI(image);
        } catch(IOException error){throw new RuntimeException("Cannot load calendar frame",error);}
    }
}
