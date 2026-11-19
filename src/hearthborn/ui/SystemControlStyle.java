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
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Quiet system sections and circular feedback, distinct from square action slots.
 *
 * @author Jagy2014
 */
public final class SystemControlStyle {
    private static final Map<Integer,TexI[]> effects=new HashMap<>();
    private SystemControlStyle() {}

    public static void feedback(GOut g,Iterable<Widget> controls){
        for(Widget control:controls){
            if(!control.visible()||!(control instanceof ICheckBox))continue;
            ICheckBox button=(ICheckBox)control;
            boolean active=button.state();
            if(!button.h&&!active)continue;
            int side=Math.max(button.sz.x,button.sz.y);
            TexI[] images=effects.computeIfAbsent(side,SystemControlStyle::effects);
            Coord at=button.c.add(button.sz.sub(side,side).div(2));
            if(button.h)g.image(images[0],at);
            if(active)g.image(images[1],at);
        }
    }
    private static TexI[] effects(int side){
        BufferedImage glow=TexI.mkbuf(Coord.of(side,side)),ring=TexI.mkbuf(Coord.of(side,side));
        Graphics2D graphics=glow.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setPaint(new RadialGradientPaint(side/2f,side/2f,side/2f,
            new float[]{0,.66f,.84f,1},new Color[]{new Color(231,191,105,0),new Color(231,191,105,0),new Color(231,191,105,48),new Color(231,191,105,0)}));
        graphics.fill(new Ellipse2D.Double(0,0,side,side));graphics.dispose();
        graphics=ring.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        float stroke=UI.scale(1f);double inset=Math.max(stroke,side*.035);
        graphics.setStroke(new BasicStroke(stroke));graphics.setColor(new Color(215,177,94,205));
        graphics.draw(new Ellipse2D.Double(inset,inset,side-2*inset,side-2*inset));graphics.dispose();
        return new TexI[]{new TexI(glow),new TexI(ring)};
    }
}
