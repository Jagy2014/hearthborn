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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

/**
 * Switches for what only the developer needs: the translation log, the log of newly seen icons and the diagnostic traces.
 * They are on only where a file named {@value #FILE} sits in the working directory of the client and says so. The file is
 * written by hand and is never part of a build, so players do not have these switches at all. One line for each switch:
 * <pre>
 * missing-translations=true
 * new-icons=true
 * debug-logs=true
 * </pre>
 * {@code all=true} turns every log on. The file is read again when it changes.
 * The Game Journal preview requires its own {@code game-journal=true} and a client restart.
 *
 * @author Jagy2014
 */
public final class DeveloperMode {
    public static final String FILE = "developer.properties";
    private static final long RECHECK_MILLIS = 5000;
    private static Properties values = new Properties();
    private static long checked, modified = -2;
    private static Path read;

    /** The file: the one named by the property {@code hearthborn.developer.file}, otherwise the one in the working directory. */
    public static Path file() {
        return Paths.get(System.getProperty("hearthborn.developer.file", FILE));
    }

    private static synchronized Properties current() {
        long now = System.currentTimeMillis();
        Path path = file();
        if(path.equals(read) && now - checked < RECHECK_MILLIS) return values;
        checked = now;
        long stamp;
        try { stamp = Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : -1; }
        catch(IOException e) { stamp = -1; }
        if(path.equals(read) && stamp == modified) return values;
        read = path;
        modified = stamp;
        Properties next = new Properties();
        if(stamp >= 0) {
            try(InputStream in = Files.newInputStream(path)) { next.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)); }
            catch(IOException | IllegalArgumentException e) { next = new Properties(); }
        }
        values = next;
        return values;
    }

    /** Whether the switch is on in the developer file. A player has no such file, so for a player it is always off. */
    public static boolean enabled(String key) {
        Properties properties = current();
        return "true".equalsIgnoreCase(properties.getProperty(key, "").trim())
            || "true".equalsIgnoreCase(properties.getProperty("all", "").trim());
    }

    /** Keep the preview disabled unless explicitly requested for this client process. */
    public static boolean gameJournal() {
        return JournalPreview.ENABLED;
    }

    private static class JournalPreview {
        private static final boolean ENABLED = "true".equalsIgnoreCase(current().getProperty("game-journal", "").trim());
    }

    /** True when the file turns on anything at all. */
    public static boolean any() {
        for(String key : current().stringPropertyNames())
            if("true".equalsIgnoreCase(current().getProperty(key, "").trim())) return true;
        return false;
    }

    /** Forget what was read, so the next question reads the file again. */
    public static synchronized void reload() { read = null; }

    private DeveloperMode() {}
}
