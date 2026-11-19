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
 * Add a numeric fill level only where the server has not supplied its own label.
 *
 * @author Jagy2014
 */
public final class ClaimPresence {
    public static void attach(Progress meter) {
        Window window = meter.getparent(Window.class);
        if(window == null) return;
        boolean claim = "Claim Stake".equals(window.cap) || "Land Management".equals(window.cap);
        if(!claim) for(Label label : window.children(Label.class))
            if(label.texts != null && label.texts.startsWith("Presence")) {claim=true;break;}
        if(claim) meter.percent();
    }
}
