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
import hearthborn.QualityStyle;

/**
 * The number on item icons: whether it shows, how it is written and which colour each tier gets.
 *
 * @author Jagy2014
 */
public final class QualitySettings {
    public static OptWnd.Panel panel(OptWnd options) {
        OptWnd.Panel panel = options.new Panel().resettable("hearthborn/quality/");
        Widget previous = panel.add(new CheckBox("Show quality on items").state(QualityStyle::show).set(QualityStyle::show), Coord.z);
        previous = panel.add(new CheckBox("Round quality to a whole number").state(QualityStyle::rounded).set(QualityStyle::rounded), previous.pos("bl").adds(0, 6));
        previous.settip("Off shows one decimal, for example 12.4. Item tooltips always show the exact value.");
        previous = panel.add(new CheckBox("Swap quality and count").state(QualityStyle::swapped).set(QualityStyle::swapped), previous.pos("bl").adds(0, 6));
        previous.settip("Quality moves to the bottom-right corner of the icon, and the count and the gilding to the top-right. Off keeps quality on top and the count and the gilding at the bottom.");
        previous = panel.add(new CheckBox("Colour quality by tier").state(QualityStyle::tinted).set(QualityStyle::tinted), previous.pos("bl").adds(0, 6));
        previous.settip("The number on the icon takes the colour of its tier. White when off.");
        // One range is chosen: the wanted one. Red under 10, white up to it and orange above it are fixed.
        previous = panel.add(new Label("Wanted quality"), previous.pos("bl").adds(0, 16));
        previous.settip("The quality you are after, with both ends included. Under 10 is poor, from 10 up to it is good, above it is excellent. Each band has its own colour below; the colours show when \"Colour quality by tier\" is on.");
        Label[] legend = new Label[QualityStyle.Tier.values().length];
        RangeSlider range = panel.add(new RangeSlider(UI.scale(260), QualityStyle.ORDINARY_FROM, QualityStyle.RANGE_LIMIT,
            QualityStyle.wantedFrom(), QualityStyle.wantedTo(), QualityStyle.Tier.GOOD::color, (from, to) -> {
                QualityStyle.wanted(from, to);
                legend(legend);
            }), previous.pos("bl").adds(0, 8));
        range.settip("Drag the handles to set where the wanted quality starts and ends.");
        previous = range;
        QualityStyle.Tier[] tiers = QualityStyle.Tier.values();
        previous = panel.add(new Label("Colours"), previous.pos("bl").adds(0, 14));
        previous.settip("Click a square to choose the colour of that band. The numbers on item icons use it when \"Colour quality by tier\" is on.");
        for(int i = tiers.length - 1; i >= 0; i--) {
            QualityStyle.Tier tier = tiers[i];
            int y = previous.c.y + previous.sz.y + UI.scale(6);
            Widget swatch = panel.add(new ColorChooser.Swatch(tier.label, false, tier::color, color -> {
                tier.color(color);
                legend(legend);
            }), Coord.of(0, y));
            legend[i] = panel.add(new Label(""), Coord.of(UI.scale(40), y + UI.scale(3)));
            previous = swatch;
        }
        previous = panel.add(new Button(UI.scale(200), "Reset quality colours").action(() -> {
            QualityStyle.resetColors();
            legend(legend);
        }), previous.pos("bl").adds(0, 12));
        legend(legend);
        panel.add(options.new PButton(UI.scale(200), "Back", 27, options.main), previous.pos("bl").adds(0, 20));
        panel.pack();
        return panel;
    }
    /** One line for each band with the qualities it holds, so what the range does is in front of the eyes. */
    static void legend(Label[] lines) {
        QualityStyle.Tier[] tiers = QualityStyle.Tier.values();
        for(int i = 0; i < tiers.length; i++) {
            QualityStyle.Tier tier = tiers[i];
            int from = tier.threshold(), next = i == 0 ? Integer.MAX_VALUE : tiers[i - 1].threshold();
            String span = i == 0 ? hearthborn.L10n.format("Over %d", from - 1)
                : next <= from ? hearthborn.L10n.tr("None")
                : from == next - 1 ? Integer.toString(from) : from + "\u2013" + (next - 1);
            lines[i].settext(hearthborn.L10n.tr(tier.label) + ": " + span);
            lines[i].setcolor(tier.color());
        }
    }
    private QualitySettings() {}
}
