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

/**
 * A slightly larger face for the settings window only.
 *
 * @author Jagy2014
 */
public final class SettingsText {
    private SettingsText() {}

    /** A label that has no tier yet is a name, or an explanation when it is an arrow line or runs over several lines. */
    private static void tier(Label label) {
        if(label.tier() != 0 || label.col != java.awt.Color.WHITE || label.texts == null || label.texts.isEmpty()) return;
        boolean arrow = label.texts.startsWith("\u2191") || label.texts.startsWith("\u2193");
        boolean long_ = label.sz.y > label.f.height() * 8 / 5 + UI.scale(4);
        label.tier(arrow || long_ ? UiPalette.NOTE : UiPalette.TITLE);
    }

    public static void enlarge(Widget widget) {
        if(widget instanceof Button) ((Button)widget).settingsFace();
        else if(widget instanceof Label) { Label label = (Label)widget; label.settingsFace(); tier(label); }
        else if(widget instanceof CheckBox) ((CheckBox)widget).settingsFace();
        else if(widget instanceof HSlider) ((HSlider)widget).settingsFace();
        for(Widget child : widget.children()) enlarge(child);
    }
}
