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
 * Light, flat looks for the pieces of the old interface that were drawn as gold ornaments: bars and picture buttons.
 *
 * @author Jagy2014
 */
public final class LightSkin {
    private LightSkin() {}

    /** A thin line round a bar, the colour of the window frame. */
    public static void barFrame(GOut g, Coord at, Coord size) {
        g.chcolor(Parchment.FRAME);
        g.rect(at.sub(1, 1), size.add(2, 2));
        g.chcolor();
    }

    /** The empty part of a bar on parchment. */
    public static void barTrack(GOut g, Coord at, Coord size) {
        g.chcolor(Parchment.SCROLL_TRACK);
        g.frect(at, size);
        g.chcolor();
    }

    /**
     * A picture button without the gold ornament: a panel face with a thin line, the picture inside it. The rim of the
     * picture (its own gold frame, {@code rim} pixels at 81 across) is cut off. Hovering darkens the line.
     */
    public static void pictureButton(BufferedImage buf, BufferedImage art, boolean hover, boolean pressed) {
        int w = buf.getWidth(), h = buf.getHeight();
        java.awt.Graphics2D g = buf.createGraphics();
        try {
            g.setColor(hover || pressed ? Parchment.FRAME : Parchment.LINE);
            g.fillRect(0, 0, w, h);
            g.setColor(Parchment.PANEL);
            g.fillRect(1, 1, w - 2, h - 2);
            int cut = Math.max(2, Math.round(art.getWidth() * 5f / 81f));
            int shift = pressed ? 1 : 0;
            int aw = art.getWidth() - cut * 2, ah = art.getHeight() - cut * 2;
            if(aw > 0 && ah > 0)
                g.drawImage(art.getSubimage(cut, cut, aw, ah), Math.max(2, (w - aw) / 2) + shift, Math.max(2, (h - ah) / 2) + shift, null);
        } finally {
            g.dispose();
        }
    }
}
