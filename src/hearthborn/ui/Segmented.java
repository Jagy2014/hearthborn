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
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A switch of a few mutually exclusive choices side by side, for settings where only one of them holds ("Left | Right").
 * The chosen segment is washed with the accent; the rest are quiet. The value is read each frame, so a setting that is
 * changed elsewhere shows at once.
 *
 * @author Jagy2014
 */
final class Segmented extends Widget {
    private static final Text.Foundry FACE = new Text.Foundry(Text.sans, 12).aa(true);
    final String[] names;
    private final IntSupplier value;
    private final IntConsumer set;
    private final int segment;
    private final Text[][] ink;
    private int generation = -1, over = -1;

    Segmented(int segmentWidth, int height, String[] names, IntSupplier value, IntConsumer set) {
        super(Coord.of(segmentWidth * names.length, height));
        this.names = names;
        this.value = value;
        this.set = set;
        this.segment = segmentWidth;
        this.ink = new Text[names.length][2];
    }

    int value() { return value.getAsInt(); }

    /** Chooses a segment as a click does. */
    void select(int index) {
        if(index < 0 || index >= names.length || index == value()) return;
        set.accept(index);
    }

    private Text text(int index, boolean chosen) {
        if(generation != UiTheme.generation()) {
            for(Text[] pair : ink) for(int i = 0; i < pair.length; i++) if(pair[i] != null) { pair[i].dispose(); pair[i] = null; }
            generation = UiTheme.generation();
        }
        int slot = chosen ? 1 : 0;
        if(ink[index][slot] == null)
            ink[index][slot] = FACE.render(hearthborn.L10n.text("label", names[index]), chosen ? UiPalette.accent() : UiPalette.text2());
        return ink[index][slot];
    }

    public void draw(GOut g) {
        int current = value();
        g.chcolor(UiPalette.field());
        g.frect(Coord.z, sz);
        for(int i = 0; i < names.length; i++) {
            Coord at = Coord.of(i * segment, 0);
            if(i == current) {
                g.chcolor(UiPalette.accentFill());
                g.frect(at, Coord.of(segment, sz.y));
            } else if(i == over) {
                g.chcolor(UiPalette.hoverFill());
                g.frect(at, Coord.of(segment, sz.y));
            }
            Text label = text(i, i == current);
            g.chcolor();
            g.image(label.tex(), at.add((segment - label.sz().x) / 2, (sz.y - label.sz().y) / 2));
        }
        int line = Math.max(1, UI.scale(1));
        g.chcolor(UiPalette.border());
        for(int i = 1; i < names.length; i++) g.frect(Coord.of(i * segment, 0), Coord.of(line, sz.y));
        g.rect(Coord.z, sz);
        if(current >= 0 && current < names.length) {
            g.chcolor(UiPalette.accent());
            g.rect(Coord.of(current * segment, 0), Coord.of(segment, sz.y));
        }
        g.chcolor();
    }

    private int at(Coord c) {
        return c.x < 0 || c.x >= sz.x || c.y < 0 || c.y >= sz.y ? -1 : Math.min(names.length - 1, c.x / segment);
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1) return super.mousedown(ev);
        select(at(ev.c));
        return true;
    }

    public void mousemove(MouseMoveEvent ev) {
        over = at(ev.c);
        super.mousemove(ev);
    }

    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
        if(!hovering) over = -1;
        return super.mousehover(ev, hovering);
    }
}
