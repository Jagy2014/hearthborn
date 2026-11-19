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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A colour swatch that opens the palette-and-sliders window; shared by container and quality colours.
 *
 * @author Jagy2014
 */
public final class ColorChooser {
    private static Window open;

    /** The small coloured square in a settings panel; clicking it opens the chooser. */
    public static class Swatch extends Widget {
        private final Supplier<Color> color;
        private final Consumer<Color> apply;
        private final String title;
        private final boolean opacity;
        boolean interactive = true;

        public Swatch(String title, boolean opacity, Supplier<Color> color, Consumer<Color> apply) {
            super(UI.scale(30, 22));
            this.title = title;
            this.opacity = opacity;
            this.color = color;
            this.apply = apply;
            settip("Click to change colour and opacity");
        }

        public void draw(GOut g) {
            Color c = color.get();
            // Show the selected RGB without checkerboard blending changing its appearance.
            g.chcolor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
            g.frect(Coord.z, sz);
            g.chcolor(Color.BLACK);
            g.rect(Coord.z, sz);
            g.chcolor();
            settip(opacity ? L10n.tr("Click to change colour and opacity") + "\n"
                + L10n.tr("Opacity") + ": " + Math.round(c.getAlpha() * 100f / 255) + "%"
                : L10n.tr("Click to change colour"));
        }

        public boolean mousedown(MouseDownEvent event) {
            if(interactive && event.b == 1 && ui != null) {
                if(open != null && open.parent != null) {
                    open.reqdestroy();
                    open = null;
                }
                Window picker = window(title, opacity, color, apply);
                open = picker;
                ui.root.add(picker, ui.root.sz.sub(picker.sz).div(2));
            }
            return true;
        }
    }

    /** The chooser window: a palette, red/green/blue sliders and, when asked, an opacity slider. Every change is applied at once. */
    public static Window window(String title, boolean opacity, Supplier<Color> current, Consumer<Color> apply) {
        Window window = new Window(Coord.z, L10n.tr(title));
        Color color = current.get();
        int[] channels = {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
        String[] names = {"Red", "Green", "Blue", "Opacity"};
        int count = opacity ? 4 : 3;
        HSlider[] sliders = new HSlider[4];
        ColorPalette palette = window.add(new ColorPalette(color, picked -> {
            channels[0] = picked.getRed();
            channels[1] = picked.getGreen();
            channels[2] = picked.getBlue();
            for(int channel = 0; channel < 3; channel++) {
                sliders[channel].val = channels[channel];
                sliders[channel].changed();
            }
        }), UI.scale(0, count * 34 + 4));
        for(int i = 0; i < count; i++) {
            final int index = i;
            int y = UI.scale(i * 34);
            window.add(new Label(names[i]), Coord.of(0, y));
            Label value = window.add(new Label(Integer.toString(channels[i])), Coord.of(UI.scale(290), y));
            sliders[i] = window.add(new HSlider(UI.scale(180), 0, 255, channels[i]) {
                public void changed() {
                    channels[index] = val;
                    value.settext(Integer.toString(val));
                    apply.accept(new Color(channels[0], channels[1], channels[2], opacity ? channels[3] : 255));
                    palette.setColor(new Color(channels[0], channels[1], channels[2]));
                }
            }, Coord.of(UI.scale(100), y));
        }
        Swatch preview = new Swatch(title, opacity, current, apply);
        preview.interactive = false;
        int bottom = palette.c.y + palette.sz.y + UI.scale(10);
        window.add(preview, Coord.of(0, bottom));
        window.add(new Button(UI.scale(180), "Close").action(window::reqdestroy), Coord.of(UI.scale(100), bottom));
        window.pack();
        window.reqclose(window::reqdestroy);
        return window;
    }

    private ColorChooser() {}
}
