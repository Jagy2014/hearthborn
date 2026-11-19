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

package hearthborn.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Append only discoveries; ordinary appearances of known icons do not reach this log.
 *
 * @author Jagy2014
 */
public final class IconDiscoveryLog {
    private static boolean warned;
    public static String path() { return Logs.file("new-map-icons.log").toAbsolutePath().normalize().toString(); }

    /** Developer only: needs {@code new-icons=true} in the developer file. For a player nothing about new icons is noted or written. */
    public static boolean enabled() { return DeveloperMode.enabled("new-icons"); }

    public static synchronized void record(String name, String resource, Object[] variant) {
        if(!enabled()) return;
        String line = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            + "\t" + field(name) + "\t" + field(resource) + "\t" + field(Arrays.deepToString(variant)) + "\n";
        try {
            Files.createDirectories(Logs.directory());
            Files.write(Logs.file("new-map-icons.log"), line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch(IOException | SecurityException e) {
            if(!warned) {
                warned = true;
                System.err.println("Could not write new map icon log: " + e.getMessage());
            }
        }
    }

    private static String field(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }
    private IconDiscoveryLog() {}
}
