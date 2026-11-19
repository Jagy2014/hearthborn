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

import haven.MenuGrid.PagButton;

/**
 * Metadata updates replace a button without changing the action under a held pointer.
 *
 * @author Jagy2014
 */
public final class ActionPress {
    public static boolean same(PagButton pressed,PagButton current){
        return pressed!=null&&current!=null&&pressed.pag==current.pag&&pressed.res==current.res;
    }
    private ActionPress(){}
}
