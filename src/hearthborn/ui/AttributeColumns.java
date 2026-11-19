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
import java.util.*;

/**
 * Shared column widths keep every numeric value aligned without scaling text.
 *
 * @author Jagy2014
 */
public final class AttributeColumns {
    private int expansion;
    private int[] ends;
    /** Where the four number columns end in a row, or null before the first fit. */
    public int[] ends() { return ends; }
    public int fit(Widget panel, Collection<? extends Widget> rows, Frame frame,
                   List<AttributeValues> values, int nameWidth, int controls) {
        int[] widths = {UI.scale(24), UI.scale(28), UI.scale(28), UI.scale(30)};
        for(AttributeValues value : values) {
            int[] measured = value.widths();
            for(int i = 0; i < 4; i++) widths[i] = Math.max(widths[i], measured[i]);
        }
        int[] ends = new int[4];
        this.ends = ends;
        int x = nameWidth + UI.scale(20);
        for(int i = 0; i < 4; i++) { x += widths[i]; ends[i] = x; x += UI.scale(12); }
        int desired = Math.max(CharWnd.attrw, x + controls);
        int next = desired - CharWnd.attrw, delta = next - expansion;
        for(AttributeValues value : values) value.columns(ends);
        for(Widget row : rows) row.resize(Coord.of(desired, row.sz.y));
        if(delta != 0) {
            for(Widget child = panel.child; child != null; child = child.next)
                if(!rows.contains(child) && child != frame && child.c.x >= CharWnd.width + expansion)
                    child.move(child.c.add(delta, 0));
            frame.resize(frame.sz.add(delta, 0));
            expansion = next;
            panel.pack();
        }
        return delta;
    }
}
