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

/**
 * The choice of the look of the interface: a row of small cards, each with a miniature window in the colours of the
 * theme. The card of the look in use has a frame in the accent. Choosing one applies it at once. A look is an accent
 * ({@link AccentTheme}) on a dark or a parchment background, so the four cards are every combination of the two.
 *
 * @author Jagy2014
 */
final class ThemeCards extends Widget {
    /** The looks, in the order of the cards. */
    enum Choice {
        GOLD("Gold", AccentTheme.GOLD, false),
        PARCHMENT("Parchment", AccentTheme.GOLD, true),
        NIGHT("Night", AccentTheme.BLUE, false),
        MOON("Moon", AccentTheme.BLUE, true);

        final String label;
        final AccentTheme accent;
        final boolean light;
        Choice(String label, AccentTheme accent, boolean light) { this.label = label; this.accent = accent; this.light = light; }

        static Choice current() {
            for(Choice choice : values())
                if(choice.accent == AccentTheme.current() && choice.light == UiTheme.light()) return choice;
            return GOLD;
        }
    }

    /** Switches the interface to the look now and remembers it. */
    static void apply(Choice choice) {
        AccentTheme.set(choice.accent);
        UiTheme.light(choice.light);
    }

    private static final Text.Foundry FACE = new Text.Foundry(Text.sans, 12).aa(true);
    private final int cardWidth, previewHeight, gap;
    private int over = -1, generation = -1;
    private final Text[][] names = new Text[Choice.values().length][2];

    ThemeCards(int cardWidth, int gap) {
        super(Coord.z);
        this.cardWidth = cardWidth;
        this.gap = gap;
        this.previewHeight = UI.scale(46);
        resize(Coord.of(cardWidth * Choice.values().length + gap * (Choice.values().length - 1), previewHeight + UI.scale(24)));
    }

    /** Where a card starts. */
    Coord card(int index) { return Coord.of(index * (cardWidth + gap), 0); }

    private int at(Coord c) {
        for(int i = 0; i < Choice.values().length; i++) {
            Coord from = card(i);
            if(c.x >= from.x && c.x < from.x + cardWidth && c.y >= 0 && c.y < sz.y) return i;
        }
        return -1;
    }

    private Text name(int index, boolean chosen) {
        if(generation != UiTheme.generation()) {
            for(Text[] pair : names) for(int i = 0; i < pair.length; i++) if(pair[i] != null) { pair[i].dispose(); pair[i] = null; }
            generation = UiTheme.generation();
        }
        int slot = chosen ? 1 : 0;
        if(names[index][slot] == null)
            names[index][slot] = FACE.render(hearthborn.L10n.text("label", Choice.values()[index].label), chosen ? UiPalette.accent() : UiPalette.text2());
        return names[index][slot];
    }

    private static void fill(GOut g, Color color, int x, int y, int w, int h) {
        g.chcolor(color);
        g.frect(Coord.of(x, y), Coord.of(w, h));
    }

    /** A miniature window: frame, caption tab, three lines of text and the main button, all in the colours of the look. */
    private void preview(GOut g, Choice choice, boolean chosen, boolean hover) {
        boolean light = choice.light;
        int w = cardWidth, h = previewHeight, pad = UI.scale(6);
        fill(g, light ? Parchment.BG : new Color(0x0B, 0x0B, 0x0B), 0, 0, w, h);
        Color frame = choice.accent.frameOn(light);
        fill(g, choice.accent.tabOn(light), pad, 0, w * 2 / 5, UI.scale(9));
        g.chcolor(frame);
        g.rect(Coord.of(pad, 0), Coord.of(w * 2 / 5, UI.scale(9)));
        Color ink = light ? Parchment.TEXT2 : UiPalette.TEXT2;
        int[] lengths = {w * 3 / 5, w * 2 / 5, w / 2};
        for(int i = 0; i < lengths.length; i++)
            fill(g, UiPalette.withAlpha(ink, 170), pad, UI.scale(15) + i * UI.scale(7), lengths[i], Math.max(2, UI.scale(3)));
        int bw = w / 3, bh = UI.scale(9), bx = w - pad - bw, by = h - pad - bh + UI.scale(2);
        Color primary = choice.accent.primaryOn(light);
        if(primary != null) fill(g, primary, bx, by, bw, bh);
        else {
            g.chcolor(choice.accent.accentOn(light));
            g.rect(Coord.of(bx, by), Coord.of(bw, bh));
        }
        g.chcolor(frame);
        g.rect(Coord.z, Coord.of(w, h));
        // The look in use is framed twice in the accent of the interface, the card under the pointer in a quiet line.
        if(chosen) {
            g.chcolor(UiPalette.accent());
            g.rect(Coord.z, Coord.of(w, h));
            g.rect(Coord.of(1, 1), Coord.of(w - 2, h - 2));
        } else if(hover) {
            g.chcolor(UiPalette.borderLit());
            g.rect(Coord.z, Coord.of(w, h));
        }
        g.chcolor();
    }

    public void draw(GOut g) {
        Choice current = Choice.current();
        for(int i = 0; i < Choice.values().length; i++) {
            Choice choice = Choice.values()[i];
            Coord from = card(i);
            preview(g.reclip(from, Coord.of(cardWidth, previewHeight)), choice, choice == current, i == over);
            Text label = name(i, choice == current);
            g.image(label.tex(), from.add((cardWidth - label.sz().x) / 2, previewHeight + UI.scale(5)));
        }
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b != 1) return super.mousedown(ev);
        int index = at(ev.c);
        if(index < 0) return false;
        select(Choice.values()[index]);
        return true;
    }

    /** Chooses a look as a click on its card does. */
    void select(Choice choice) {
        if(choice != Choice.current()) apply(choice);
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
