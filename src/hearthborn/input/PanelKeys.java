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

package hearthborn.input;

import haven.*;
import hearthborn.ui.Toolbars;

/**
 * A key of its own for every button of the two additional toolbars. A key belongs to the place on the panel, button 1 to button 10 or 12,
 * and presses whatever icon stands there. None is set until the player sets one.
 *
 * @author Jagy2014
 */
public final class PanelKeys {
    public static final String[] PANELS = {"extra", "extra2"};
    private static final KeyBinding[][] BINDINGS = new KeyBinding[PANELS.length][];

    static {
        for(int p = 0; p < PANELS.length; p++) {
            BINDINGS[p] = new KeyBinding[Toolbars.extraCapacity(PANELS[p])];
            for(int i = 0; i < BINDINGS[p].length; i++) {
                BINDINGS[p][i] = KeyBinding.get("hearthborn/toolbar/" + PANELS[p] + "/key/" + (i + 1), KeyMatch.nil);
                GlobalKeys.name(BINDINGS[p][i], name(PANELS[p], i));
            }
        }
    }

    /** Loads the class, so that the keys are known to the clash check. */
    static void ensure() {}

    public static int number(String panel) {return "extra2".equals(panel) ? 2 : 1;}
    public static int count(String panel) {return Toolbars.extraCapacity(panel);}
    public static String title(String panel) {return "Additional toolbar " + number(panel);}
    public static String name(String panel, int index) {return String.format("Additional toolbar %d, button %d", number(panel), index + 1);}

    public static KeyBinding binding(String panel, int index) {
        for(int p = 0; p < PANELS.length; p++)
            if(PANELS[p].equals(panel) && index >= 0 && index < BINDINGS[p].length) return BINDINGS[p][index];
        throw new IllegalArgumentException(panel + "/" + index);
    }

    /** A short caption for the key on a button, or null when the button has none. */
    public static String label(String panel, int index) {
        KeyMatch key = binding(panel, index).key();
        return key == KeyMatch.nil ? null : abbreviate(key.name());
    }

    static String abbreviate(String name) {
        return name.replace("Shift+", "S+").replace("Ctrl+", "C+").replace("Alt+", "A+");
    }

    public static boolean handle(GameUI gui, Widget.GlobKeyEvent event) {
        if(gui == null || gui.beltwdg == null) return false;
        for(String panel : PANELS)
            for(int i = 0; i < count(panel); i++) {
                KeyBinding binding = binding(panel, i);
                if(!GlobalKeys.matches(binding.key(), event.code, event.mods, event)) continue;
                int slot = Toolbars.panelSlot(gui, panel, i);
                if(slot < 0) continue;
                gui.beltwdg.keyact(slot);
                return true;
            }
        return false;
    }

    private PanelKeys() {}
}
