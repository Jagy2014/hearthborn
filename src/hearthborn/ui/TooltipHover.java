/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.ui;

import haven.GobIcon;
import haven.MapWnd;
import haven.OptWnd;
import haven.Widget;
import hearthborn.map.MapSettingsWindow;

/** Wait for the pointer to settle on a settings control or marker row before showing its tip. */
public final class TooltipHover {
    static final double DELAY = 0.18;
    private Widget candidate;
    private double showAt;

    public Object visible(Widget owner, Object tip, double now) {
        if(tip == null || !delayed(owner)) {
            candidate = null;
            return tip;
        }
        if(candidate != owner) {
            candidate = owner;
            showAt = now + DELAY;
        }
        return now >= showAt ? tip : null;
    }

    private static boolean delayed(Widget owner) {
        for(Widget widget = owner; widget != null; widget = widget.parent) {
            if(widget instanceof OptWnd || widget instanceof MapSettingsWindow
               || widget instanceof GobIcon.IconSettings || widget instanceof MapWnd.MarkerList)
                return true;
        }
        return false;
    }
}
