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
import hearthborn.world.ObjectHiding;

/**
 * Which kinds of objects disappear from the world when hiding is on; the controls sit in the world graphics section.
 *
 * @author Jagy2014
 */
public final class HidingSettings {
    /** Adds the choice of hidden kinds under {@code above}, inside the world graphics section. Returns the lowest widget. */
    public static Widget add(OptWnd.Panel panel, Widget above, int gap) {
        Widget previous = panel.add(new Label("Hide objects"), above.pos("bl").adds(0, gap));
        // Hiding itself is switched with a key; here one only marks the kinds it covers.
        previous = panel.add(new Label("The hotkey (Keybindings, \"Hide chosen objects\") hides or shows the kinds ticked here. Hidden objects can still be clicked through and block movement. Trees and bushes are set in Plants and growth.", UI.scale(380)), previous.pos("bl").adds(0, 5));
        ((Label)previous).tier(UiPalette.NOTE);
        for(ObjectHiding.Kind kind : ObjectHiding.Kind.values()) {
            previous = panel.add(new CheckBox(kind.label).state(kind::enabled).set(kind::enabled), previous.pos("bl").adds(0, kind == ObjectHiding.Kind.values()[0] ? 8 : 6));
        }
        previous = panel.add(new CheckBox("Show outlines of hidden objects").state(ObjectHiding::boxes).set(ObjectHiding::boxes), previous.pos("bl").adds(0, 14));
        previous.settip("Draws a blue outline where a hidden object stands, so the gap is not mistaken for free ground. Crops have no outline.");
        return previous;
    }
    private HidingSettings() {}
}
