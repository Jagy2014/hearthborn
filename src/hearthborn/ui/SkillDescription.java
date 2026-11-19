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
 * Give skill descriptions more room while keeping long texts within the screen.
 *
 * @author Jagy2014
 */
public final class SkillDescription extends RichTextBox {
    private final int minimumHeight;
    private Frame frame;
    public SkillDescription(Coord size, RichText.Foundry font) {
        super(size, font, null);
        minimumHeight = size.y;
    }
    public void frame(Frame frame) { this.frame = frame; }
    protected void rendered(RichText text) {
        int maximum = ui == null ? UI.scale(500) : Math.max(minimumHeight, ui.root.sz.y - UI.scale(180));
        int needed = text == null ? minimumHeight : text.sz().y + UI.scale(20);
        int height = Math.max(minimumHeight, Math.min(maximum, needed));
        if(height == sz.y) return;
        resize(Coord.of(sz.x, height));
        if(frame != null) frame.resize(sz.add(frame.box.bisz()));
        if(parent != null) parent.pack();
    }
}
