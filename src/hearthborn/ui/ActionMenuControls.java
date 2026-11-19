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

/**
 * System commands that must not become recipes, favorites or history entries.
 *
 * @author Jagy2014
 */
public final class ActionMenuControls {
    public static boolean isControl(String name){
        return name.equals("hearthborn/action-search")||name.equals("hearthborn/minimize-panel");
    }
    private ActionMenuControls(){}
}
