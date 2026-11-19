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
import java.util.regex.*;
/**
 * Only server-provided preparation estimates are converted to real time.
 *
 * @author Jagy2014
 */
public final class SiegeTimers {
    private static final Pattern WAIT=Pattern.compile("^The (?:wrecking ball|battering ram) cannot be used until it has properly (?:settled|dried), in (\\d+) (hours?|days?)\\.?$",Pattern.CASE_INSENSITIVE);
    public static long remainingMillis(String message) {
        if(message==null)return 0;
        Matcher match=WAIT.matcher(message);if(!match.matches())return 0;
        try {
            double hours=Double.parseDouble(match.group(1))*(match.group(2).toLowerCase(java.util.Locale.ROOT).startsWith("day")?24:1);
            double millis=hours*3600000d/3.29;
            return millis>0&&millis<Long.MAX_VALUE?(long)Math.ceil(millis):0;
        }catch(NumberFormatException invalid){return 0;}
    }
    private SiegeTimers() {}
}
