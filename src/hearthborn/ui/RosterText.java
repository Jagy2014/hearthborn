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
import java.awt.Font;
/**
 * Compact livestock table text, independent of character-sheet headings.
 *
 * @author Jagy2014
 */
public final class RosterText {
    public static final Text.Foundry FONT=new Text.Foundry(Text.sans.deriveFont(Font.PLAIN,UI.scale(11f))).aa(true);
    private RosterText() {}
}
