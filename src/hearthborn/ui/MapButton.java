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
import hearthborn.L10n;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * A buff-row button that brings the map back after it was closed.
 *
 * @author Jagy2014
 */
public final class MapButton extends Buff {
    private static final Map<GameUI,MapButton> shown = new WeakHashMap<>();
    private static Tex picture;
    private final GameUI gui;
    private MapButton(GameUI gui) {
        super(Resource.local().load("gfx/hud/lbtn-map"));
        this.gui = gui;
    }
    /** The map button art sits in the corner of a large empty plate. Drawn whole, the buff stays black. */
    static BufferedImage iconImage() {
        int side = UI.scale(32);
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setPaint(new RadialGradientPaint(side / 2f, side / 2f, side * 0.7071f,
            new float[] {0f, 0.42f, 0.71f, 1f},
            new Color[] {new Color(62, 170, 214), new Color(50, 158, 206), new Color(11, 87, 145), new Color(5, 43, 84)}));
        g.fillRect(0, 0, side, side);
        BufferedImage mark = mark();
        int inset = Math.max(1, side / 12);
        int box = side - inset * 2;
        double fit = Math.min(box / (double)mark.getWidth(), box / (double)mark.getHeight());
        int w = Math.max(1, (int)Math.round(mark.getWidth() * fit));
        int h = Math.max(1, (int)Math.round(mark.getHeight() * fit));
        g.drawImage(mark, (side - w) / 2, (side - h) / 2, w, h, null);
        g.dispose();
        return image;
    }
    private static BufferedImage mark() {
        BufferedImage image = Resource.local().loadwait("gfx/hud/lbtn-map").layer(Resource.imgc).img;
        int x0 = image.getWidth(), y0 = image.getHeight(), x1 = -1, y1 = -1;
        boolean alpha = image.getRaster().getNumBands() >= 4;
        for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) {
            int sample = alpha ? image.getRaster().getSample(x, y, 3) : 255;
            if(sample < 16) continue;
            if(x < x0) x0 = x;
            if(y < y0) y0 = y;
            if(x > x1) x1 = x;
            if(y > y1) y1 = y;
        }
        if(x1 < x0) return image;
        return image.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }
    private static Tex picture() {
        if(picture == null) picture = new TexI(iconImage());
        return picture;
    }
    public void draw(GOut g) {
        g.chcolor(255, 255, 255, a);
        g.image(Buff.frame, Coord.z);
        g.image(picture(), Buff.imgoff);
        g.chcolor();
    }
    public static void tick(GameUI gui) {
        if(gui.buffs == null) return;
        MapButton button = shown.get(gui);
        boolean closed = gui.mapfile != null && !gui.mapfile.visible();
        if(!closed) {
            if(button != null) {shown.remove(gui); if(button.parent != null) button.destroy();}
            return;
        }
        if(button == null || button.parent == null) {
            button = new MapButton(gui);
            gui.buffs.addchild(button);
            shown.put(gui, button);
        }
    }
    public static void dispose(GameUI gui) {
        MapButton button = shown.remove(gui);
        if(button != null && button.parent != null) button.destroy();
    }
    /** Sits just left of the closed-chat button. */
    public int layoutOrder() {return -4;}
    public boolean mousedown(MouseDownEvent event) {
        if(event.b != 1 || gui.mapfile == null) return false;
        gui.mapfile.show();
        gui.mapfile.raise();
        gui.setfocus(gui.mapfile);
        Utils.setprefb("wndvis-map", true);
        return true;
    }
    public Object tooltip(Coord at, Widget previous) {return L10n.tr("Open map");}
}
