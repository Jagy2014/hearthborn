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
import java.util.function.Consumer;

/**
 * Cached HSV plane, hue strip, and quick color swatches.
 *
 * @author Jagy2014
 */
public class ColorPalette extends Widget {
    private static final int W = UI.scale(300), H = UI.scale(150);
    private static final int GAP = UI.scale(10), STRIP = UI.scale(20), CELL = UI.scale(25);
    private static final Color[] PRESETS = {Color.BLACK, Color.GRAY, Color.WHITE,
	Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN,
	Color.BLUE, new Color(128, 0, 255), Color.MAGENTA, Color.PINK};
    private final Consumer<Color> changed;
    private float hue, saturation, brightness;
    private float drawnHue = -1;
    private Tex plane, strip;
    private UI.Grab grab;
    private boolean hueDrag;

    public ColorPalette(Color initial, Consumer<Color> changed) {
	super(new Coord(W + GAP + STRIP, H + GAP + CELL));
	this.changed = changed;
	setColor(initial);
    }

    public void setColor(Color color) {
	float[] hsv = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
	if(hsv[1] > 0)
	    hue = hsv[0];
	saturation = hsv[1];
	brightness = hsv[2];
    }

    private void textures() {
	if(plane == null || drawnHue != hue) {
	    if(plane != null)
		plane.dispose();
	    BufferedImage image = TexI.mkbuf(new Coord(W, H));
	    for(int y = 0; y < H; y++)
		for(int x = 0; x < W; x++)
		    image.setRGB(x, y, Color.HSBtoRGB(hue, x / (float)(W - 1), 1 - y / (float)(H - 1)));
	    plane = new TexI(image);
	    drawnHue = hue;
	}
	if(strip == null) {
	    BufferedImage image = TexI.mkbuf(new Coord(STRIP, H));
	    for(int y = 0; y < H; y++)
		for(int x = 0; x < STRIP; x++)
		    image.setRGB(x, y, Color.HSBtoRGB(y / (float)(H - 1), 1, 1));
	    strip = new TexI(image);
	}
    }

    @Override
    public void draw(GOut g) {
	textures();
	g.image(plane, Coord.z);
	g.image(strip, new Coord(W + GAP, 0));
	Coord point = new Coord(Math.round(saturation * (W - 1)), Math.round((1 - brightness) * (H - 1)));
	int radius = UI.scale(4);
	g.chcolor(Color.BLACK);
	g.rect(point.sub(radius, radius), new Coord(radius * 2 + 1, radius * 2 + 1));
	g.chcolor(Color.WHITE);
	g.rect(point.sub(radius - 1, radius - 1), new Coord(radius * 2 - 1, radius * 2 - 1));
	g.rect(new Coord(W + GAP, Math.round(hue * (H - 1)) - UI.scale(1)), new Coord(STRIP, UI.scale(3)));
	for(int i = 0; i < PRESETS.length; i++) {
	    g.chcolor(PRESETS[i]);
	    g.frect(new Coord(i * CELL, H + GAP), new Coord(CELL - UI.scale(2), CELL));
	}
	g.chcolor();
    }

    private void pick(Coord c) {
	if(hueDrag)
	    hue = Math.max(0, Math.min(1, c.y / (float)(H - 1)));
	else {
	    saturation = Math.max(0, Math.min(1, c.x / (float)(W - 1)));
	    brightness = 1 - Math.max(0, Math.min(1, c.y / (float)(H - 1)));
	}
	changed.accept(Color.getHSBColor(hue, saturation, brightness));
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
	if(ev.b != 1)
	    return super.mousedown(ev);
	if(ev.c.y >= H + GAP && ev.c.x >= 0 && ev.c.x < PRESETS.length * CELL) {
	    Color color = PRESETS[ev.c.x / CELL];
	    setColor(color);
	    changed.accept(color);
	    return true;
	}
	boolean planeHit = ev.c.isect(Coord.z, new Coord(W, H));
	boolean hueHit = ev.c.isect(new Coord(W + GAP, 0), new Coord(STRIP, H));
	if(!planeHit && !hueHit)
	    return super.mousedown(ev);
	hueDrag = hueHit;
	grab = ui.grabmouse(this);
	pick(ev.c);
	return true;
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
	if(grab != null)
	    pick(ev.c);
	else
	    super.mousemove(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
	if(ev.b == 1 && grab != null) {
	    grab.remove();
	    grab = null;
	    return true;
	}
	return super.mouseup(ev);
    }

    @Override
    public void dispose() {
	if(grab != null)
	    grab.remove();
	if(plane != null)
	    plane.dispose();
	if(strip != null)
	    strip.dispose();
	super.dispose();
    }
}
