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
import java.util.function.BiConsumer;

/**
 * A slider with two handles that picks a range: everything between them is the chosen part, drawn in a colour of its own.
 * The handles never cross; they keep at least one step apart. A click takes the nearer handle, a drag moves it.
 *
 * @author Jagy2014
 */
public final class RangeSlider extends Widget {
    private final int min, max;
    private int low, high, held = -1, over = -1;
    private final java.util.function.Supplier<Color> fill;
    private final BiConsumer<Integer, Integer> changed;
    private UI.Grab grab;

    public RangeSlider(int width, int min, int max, int low, int high, java.util.function.Supplier<Color> fill, BiConsumer<Integer, Integer> changed) {
        super(Coord.of(width, SliderSkin.settingsHandleHeight() + UI.scale(4)));
        this.min = min;
        this.max = max;
        this.low = Math.max(min, Math.min(max - 1, low));
        this.high = Math.max(this.low + 1, Math.min(max, high));
        this.fill = fill;
        this.changed = changed;
    }

    public int low() { return low; }
    public int high() { return high; }

    /** Moves the handles from outside, for a setting that was changed elsewhere. Does not call back. */
    public void set(int low, int high) {
        this.low = Math.max(min, Math.min(max - 1, low));
        this.high = Math.max(this.low + 1, Math.min(max, high));
    }

    private int span() { return sz.x - SliderSkin.settingsHandleWidth(); }
    /** The left edge of a handle for a value. */
    int handleX(int value) { return (span() * (value - min)) / (max - min); }
    private int valueAt(int x) {
        double a = (double)(x - SliderSkin.settingsHandleWidth() / 2) / span();
        return (int)Math.round(Math.max(0, Math.min(1, a)) * (max - min)) + min;
    }

    public void draw(GOut g) {
        int hw = SliderSkin.settingsHandleWidth(), hh = Math.min(sz.y, SliderSkin.settingsHandleHeight());
        int th = SliderSkin.settingsTrackHeight(), ty = (sz.y - th) / 2;
        g.chcolor(UiPalette.borderLit());
        g.frect(Coord.of(hw / 2, ty), Coord.of(span(), th));
        g.chcolor(fill.get());
        g.frect(Coord.of(hw / 2 + handleX(low), ty), Coord.of(handleX(high) - handleX(low), th));
        g.chcolor();
        for(int i = 0; i < 2; i++)
            g.image(SliderSkin.settingsHandle(hw, hh, held == i, over == i || held == i), Coord.of(handleX(i == 0 ? low : high), (sz.y - hh) / 2));
    }

    /** Which handle a point is nearer to; the upper one when they sit together and the point is on their right. */
    private int nearest(Coord c) {
        int centre = SliderSkin.settingsHandleWidth() / 2;
        int toLow = Math.abs(c.x - (handleX(low) + centre)), toHigh = Math.abs(c.x - (handleX(high) + centre));
        if(toLow == toHigh) return c.x > handleX(high) + centre ? 1 : 0;
        return toLow < toHigh ? 0 : 1;
    }

    private void drag(Coord c) {
        int value = valueAt(c.x);
        int newLow = low, newHigh = high;
        if(held == 0) newLow = Math.max(min, Math.min(high - 1, value));
        else newHigh = Math.min(max, Math.max(low + 1, value));
        if(newLow != low || newHigh != high) {
            low = newLow;
            high = newHigh;
            changed.accept(low, high);
        }
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1) return super.mousedown(ev);
        held = nearest(ev.c);
        grab = ui.grabmouse(this);
        drag(ev.c);
        return true;
    }

    public void mousemove(MouseMoveEvent ev) {
        super.mousemove(ev);
        if(held >= 0) drag(ev.c);
        else over = ev.c.y >= 0 && ev.c.y < sz.y && ev.c.x >= 0 && ev.c.x < sz.x ? nearest(ev.c) : -1;
    }

    public boolean mouseup(MouseUpEvent ev) {
        if(ev.b != 1 || held < 0) return super.mouseup(ev);
        grab.remove();
        grab = null;
        held = -1;
        return true;
    }

    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
        if(!hovering) over = -1;
        return super.mousehover(ev, hovering);
    }
}
