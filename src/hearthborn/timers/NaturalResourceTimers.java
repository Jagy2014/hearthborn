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

import haven.Gob;
import haven.MapFile;
import hearthborn.L10n;
import hearthborn.map.MarkerQuality;
import hearthborn.ui.StudyProgress;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Refills reported by the server when a natural resource is inspected. */
public final class NaturalResourceTimers {
    static final String PREFIX = "natural-resource:";
    private static final Pattern REFILL = Pattern.compile(
        "(?i)\\bwill\\s+refill\\s+in\\s+([0-9]+(?:[.,][0-9]+)?)\\s+(second|minute|hour|day|week)s?\\b");

    static boolean isRefill(String source) {
        return source != null && source.startsWith(PREFIX);
    }

    public static long remainingMillis(String message) {
        return remainingMillis(message, false);
    }

    private static long remainingMillis(String message, boolean allowRounding) {
        if(message == null) return 0;
        Matcher match = REFILL.matcher(message);
        if(!match.find()) return 0;
        String amount = match.group(1).replace(',', '.');
        double count = Double.parseDouble(amount);
        if(count <= 0) return 0;
        if(allowRounding) {
            // The server sends a displayed duration, not a timestamp. Allow one
            // step of its reported precision rather than treating it as exact.
            int dot = amount.indexOf('.');
            count += dot < 0 ? 1 : Math.pow(10, dot + 1 - amount.length());
        }
        int unit;
        switch(match.group(2).toLowerCase(Locale.ROOT)) {
            case "week": unit = 604800; break;
            case "day": unit = 86400; break;
            case "hour": unit = 3600; break;
            case "minute": unit = 60; break;
            default: unit = 1;
        }
        double millis = count * unit * 1000 / StudyProgress.GAME_SECONDS_PER_REAL_SECOND;
        return Double.isFinite(millis) && millis < Long.MAX_VALUE - System.currentTimeMillis()
            ? Math.round(millis) : 0;
    }

    public static boolean observe(Gob gob, String message) {
        long remaining = remainingMillis(message);
        if(remaining <= 0) return false;
        MapFile.SMarker marker = MarkerQuality.naturalMarker(gob);
        if(marker == null) return false;
        if(TimerSettings.automatic()) {
            long now = System.currentTimeMillis();
            long upperEstimate = remainingMillis(message, true);
            if(upperEstimate <= 0 || upperEstimate > Long.MAX_VALUE - now) return false;
            String source = PREFIX + MarkerQuality.number(marker);
            long deadline = Timers.deadline(source);
            // Repeating the same rounded duration must not postpone a still
            // plausible deadline. A more precise or later report can replace it.
            if(deadline < now + remaining || deadline > now + upperEstimate)
                Timers.track(source, marker.nm, now + upperEstimate);
        }
        return true;
    }

    static String name(String source, String fallback) {
        String key = source.substring(PREFIX.length());
        int end = key.indexOf('|');
        String base = end < 0 ? fallback : L10n.resource("tooltip", key.substring(0, end), fallback);
        return MarkerQuality.numberedCaption(key, base);
    }

    private NaturalResourceTimers() {}
}
