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
 * Keeps the equipment, character sheet and Kith & Kin shortcuts working while their buttons are not in the system row:
 * the first two open from the portrait and the third is the heart on it. It draws nothing and takes no clicks.
 *
 * @author Jagy2014
 */
public final class SystemHotkeys extends Widget {
    private final KeyBinding[] keys;
    private final Runnable[] actions;

    public SystemHotkeys(KeyBinding[] keys, Runnable[] actions) {
        super(Coord.z);
        this.keys = keys;
        this.actions = actions;
    }

    public boolean globtype(GlobKeyEvent ev) {
        for(int i = 0; i < keys.length; i++) {
            KeyMatch match = keys[i].key();
            if(match != null && match.match(ev)) {
                actions[i].run();
                return true;
            }
        }
        return super.globtype(ev);
    }

    public boolean checkhit(Coord c) { return false; }
}
