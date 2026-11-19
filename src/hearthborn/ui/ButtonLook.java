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
 * The flat faces of the settings window. A menu entry has no frame or fill; the open one gets an accent bar and a light
 * wash. An outline button has a neutral line, the main one an accent line, a dangerous one a red face. A quiet button is
 * only text. See {@link UiPalette} for the colours.
 *
 * @author Jagy2014
 */
public final class ButtonLook {
    private ButtonLook() {}

    /** Left padding of a menu entry's text, past the accent bar. */
    public static int menuPad() { return Math.max(8, UI.scale(14)); }

    /** The colour of the caption in the given state. */
    public static Color ink(haven.Button.Look look, boolean hover, boolean selected) {
        switch(look) {
        case MENU: return (hover || selected) ? UiPalette.text() : UiPalette.text2();
        case QUIET: return hover ? UiPalette.text2() : UiPalette.hint();
        case DANGER: return UiPalette.danger();
        case PRIMARY: {
            Color own = AccentTheme.current().primaryInk();
            return own != null ? own : UiPalette.text();
        }
        default: return UiPalette.text();
        }
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color((int)Math.round(a.getRed() * (1 - t) + b.getRed() * t), (int)Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
                         (int)Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    public static void paint(BufferedImage img, haven.Button.Look look, boolean hover, boolean pressed, boolean selected) {
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = img.getWidth(), h = img.getHeight();
            float line = Math.max(1, UI.scale(1)), r = PillSkin.radius(h);
            switch(look) {
            case MENU:
                if(selected) {
                    g.setColor(UiPalette.accentFill());
                    g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
                    g.setColor(AccentTheme.current().bar());
                    g.fill(new Rectangle2D.Float(0, 0, Math.max(2, UI.scale(3)), h));
                } else if(hover || pressed) {
                    g.setColor(UiPalette.hoverFill());
                    g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
                }
                break;
            case QUIET:
                if(hover || pressed) {
                    g.setColor(UiPalette.hoverFill());
                    g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
                }
                break;
            default:
                Color filled = look == haven.Button.Look.PRIMARY ? AccentTheme.current().primary(hover) : null;
                Color face = look == haven.Button.Look.DANGER ? UiPalette.dangerFill() : filled != null ? filled : UiPalette.field();
                Color edge = look == haven.Button.Look.PRIMARY ? (filled != null ? AccentTheme.current().primary(false) : UiPalette.accent())
                           : look == haven.Button.Look.DANGER ? (hover ? UiPalette.danger() : mix(UiPalette.danger(), face, 0.5))
                           : (hover ? UiPalette.borderLit() : UiPalette.border());
                g.setColor(edge);
                g.fill(new RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
                g.setColor(face);
                g.fill(new RoundRectangle2D.Float(line, line, w - line * 2, h - line * 2, Math.max(1, r * 2 - line), Math.max(1, r * 2 - line)));
                if(pressed || (hover && filled == null)) {
                    g.setColor(pressed ? new Color(0, 0, 0, 70) : UiPalette.hoverFill());
                    g.fill(new RoundRectangle2D.Float(line, line, w - line * 2, h - line * 2, Math.max(1, r * 2 - line), Math.max(1, r * 2 - line)));
                }
            }
        } finally {
            g.dispose();
        }
    }
}
