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
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * The tick box of the settings window: a neutral frame on a dark field, and a green tick when it is on. Yellow is not
 * used here, it is kept for the open section and the main button. The images follow the theme and are drawn again when
 * it changes.
 *
 * @author Jagy2014
 */
public final class CheckSkin {
    private CheckSkin() {}

    private static Tex box, mark;
    private static int generation = -1, boxW = -1, boxH = -1;

    private static synchronized void refresh(int w, int h) {
        if(generation == UiTheme.generation() && boxW == w && boxH == h && box != null) return;
        if(box != null) box.dispose();
        if(mark != null) mark.dispose();
        box = new TexI(drawBox(w, h));
        mark = new TexI(drawMark(w, h));
        generation = UiTheme.generation();
        boxW = w;
        boxH = h;
    }

    public static Tex box(int w, int h) { refresh(w, h); return box; }
    public static Tex mark(int w, int h) { refresh(w, h); return mark; }

    private static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static BufferedImage drawBox(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        try {
            float line = Math.max(1, UI.scale(1)), r = Math.max(2, UI.scale(3));
            g.setColor(UiPalette.checkLine());
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
            g.setColor(UiPalette.field());
            g.fill(new RoundRectangle2D.Float(line, line, w - line * 2, h - line * 2, r * 2 - line, r * 2 - line));
        } finally {
            g.dispose();
        }
        return img;
    }

    static BufferedImage drawMark(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img);
        try {
            g.setColor(UiPalette.check());
            g.setStroke(new BasicStroke(Math.max(2f, w / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D tick = new Path2D.Float();
            tick.moveTo(w * 0.24, h * 0.52);
            tick.lineTo(w * 0.43, h * 0.71);
            tick.lineTo(w * 0.77, h * 0.30);
            g.draw(tick);
        } finally {
            g.dispose();
        }
        return img;
    }
}
