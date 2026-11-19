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

/**
 * The question "leave the game?", shown when the system asks the client to quit (Cmd+Q on macOS).
 * Nothing happens until a button is clicked; Escape and the window's cross keep the game.
 *
 * @author Jagy2014
 */
public final class QuitConfirm {
    private static Window open;

    /** Asks once; a second request while the question is up only brings it to the front. */
    public static Window ask(UI ui, Runnable quit) {
        if(open != null && open.parent != null) {
            open.raise();
            return open;
        }
        Window window = new Window(Coord.z, L10n.tr("Quit"));
        Label question = window.add(new Label(L10n.tr("Leave the game?")), Coord.z);
        int gap = UI.scale(10), width = UI.scale(110);
        int y = question.sz.y + UI.scale(14);
        Runnable dismiss = () -> {
            if(open == window)
                open = null;
            window.reqdestroy();
        };
        window.add(new Button(width, "Quit").action(() -> {
            dismiss.run();
            quit.run();
        }), Coord.of(0, y));
        window.add(new Button(width, "Stay").action(dismiss), Coord.of(width + gap, y));
        window.pack();
        window.reqclose(dismiss);
        ui.root.add(window, ui.root.sz.sub(window.sz).div(2).max(Coord.z));
        window.raise();
        open = window;
        return window;
    }

    private QuitConfirm() {}
}
