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
 * The server's own shutdown window says why and counts down, in whatever words the administrators chose. Under that the
 * client adds one fixed line of its own, in the player's language, saying that the server is going down for maintenance.
 *
 * @author Jagy2014
 */
public final class ShutdownNotice {
    /** The caption the server gives the window. */
    public static final String CAPTION = "Shutdown";
    public static final String TEXT = "The server will be shut down for maintenance.";
    private ShutdownNotice() {}

    private static final class Note extends Label {
        Note() {
            super(TEXT);
            setcolor(new Color(235, 195, 95));
        }
    }

    public static boolean applies(Window window) {
        return CAPTION.equals(window.cap) && window.getchild(Note.class) == null;
    }

    /** Adds the line once the server has put its own widgets in the window, below the lowest of them. */
    public static void tick(Window window) {
        if(!applies(window) || window.getchild(Button.class) == null) return;
        int bottom = 0;
        for(Widget child : window.children())
            if(!(child instanceof Window.Deco)) bottom = Math.max(bottom, child.c.y + child.sz.y);
        Coord content = window.csz();
        Note note = new Note();
        window.add(note, Coord.of(Math.max(0, (content.x - note.sz.x) / 2), bottom + UI.scale(12)));
        window.resize(Coord.of(Math.max(content.x, note.c.x + note.sz.x), note.c.y + note.sz.y + UI.scale(8)));
    }
}
