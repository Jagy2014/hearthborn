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

package hearthborn.timers;

import haven.Utils;

/**
 * Automatic process discovery is independent of countdown label visibility.
 *
 * @author Jagy2014
 */
public final class TimerSettings {
    private static final String AUTOMATIC = "hearthborn/timers/automatic";

    public static boolean automatic() {
        return Utils.getprefb(AUTOMATIC, true);
    }

    public static void automatic(boolean enabled) {
        Utils.setprefb(AUTOMATIC, enabled);
    }

    private TimerSettings() {}
}
