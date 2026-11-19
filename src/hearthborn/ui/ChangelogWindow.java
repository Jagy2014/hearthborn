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

/**
 * The changelog on the character screen: a window of its own over the whole screen, opened by a button and,
 * once for every new version, by itself.
 *
 * @author Jagy2014
 */
public final class ChangelogWindow extends Window {
    private ChangelogWindow(String markdown, Coord screen) {
        super(Coord.z, "Changelog", true);
        Coord size = size(screen);
        RichTextBox text = new RichTextBox(size, Changelog.markup(markdown), new Object[0]);
        text.bg = new Color(14, 16, 22, 235);
        add(text, Coord.of(0, UI.scale(4)));
        Button close = new Button(UI.scale(110), "Close", false).action(this::reqdestroy);
        add(close, Coord.of(size.x - close.sz.x, text.c.y + size.y + UI.scale(10)));
        pack();
        reqclose(this::reqdestroy);
    }

    /** Wide enough to read, never taller than the screen leaves room for. */
    static Coord size(Coord screen) {
        int width = Math.max(UI.scale(320), Math.min(UI.scale(620), screen.x - UI.scale(80)));
        int height = Math.max(UI.scale(160), Math.min(UI.scale(620), screen.y * 3 / 4 - UI.scale(60)));
        return Coord.of(width, height);
    }

    /** Centered, with room for its frame. */
    static Coord place(Coord screen, Coord size) {
        return Coord.of(Math.max(0, (screen.x - size.x) / 2), Math.max(0, (screen.y - size.y) / 2));
    }

    /** Opens the window, or brings the open one forward. */
    public static ChangelogWindow open(UI ui) {
        if(ui == null || ui.root == null) return null;
        ChangelogWindow open = ui.root.getchild(ChangelogWindow.class);
        if(open != null) {
            open.raise();
            return open;
        }
        String markdown = Changelog.source(L10n.language());
        if(markdown == null) return null;
        ChangelogWindow window = new ChangelogWindow(markdown, ui.root.sz);
        ui.root.add(window, place(ui.root.sz, window.sz));
        window.raise();
        Changelog.seen(markdown);
        return window;
    }

    /** The first time a version is seen on the character screen. */
    public static void openIfNew(UI ui) {
        String markdown = Changelog.source(L10n.language());
        if(markdown != null && Changelog.unseen(markdown)) open(ui);
    }
}
