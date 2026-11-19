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

import haven.Tex;

/**
 * Hover text drawn inside the window frame rather than the plain tooltip box.
 *
 * @author Jagy2014
 */
public final class FramedTip {
    public final Tex text;
    public FramedTip(Tex text) {this.text = text;}
}
