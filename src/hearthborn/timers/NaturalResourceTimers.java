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

    public static long remainingMillis(String message) {
        if(message == null) return 0;
        Matcher match = REFILL.matcher(message);
        if(!match.find()) return 0;
        double count = Double.parseDouble(match.group(1).replace(',', '.'));
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
        if(TimerSettings.automatic())
            Timers.track(PREFIX + MarkerQuality.number(marker), marker.nm, System.currentTimeMillis() + remaining);
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
