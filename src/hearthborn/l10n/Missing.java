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

package hearthborn.l10n;

import haven.Utils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Opt-in, debounced recording; never edits shipped translation catalogs.
 *
 * @author Jagy2014
 */
public final class Missing {
    private static final Map<String, SortedSet<String>> pending = new HashMap<>();
    private static final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "Hearthborn translation recorder"); thread.setDaemon(true); return thread;
    });
    private static boolean scheduled;
    /** Developer only: needs {@code missing-translations=true} in the developer file; there is no box for it in the settings. */
    public static boolean available() { return hearthborn.io.DeveloperMode.enabled("missing-translations"); }
    public static boolean enabled() { return available(); }

    public static synchronized void record(String context, String text) {
        if(!enabled() || (text != null && text.startsWith("/")) || text == null || text.length() > 16384 || !text.matches("(?s).*[A-Za-z]{2}.*") ||
           text.matches("(?s).*[\\p{IsCyrillic}].*") || keyChord(text) || resourcePath(text)) return;
        pending.computeIfAbsent(context, key -> new TreeSet<>()).add(text);
        if(!scheduled) { scheduled = true; writer.schedule(Missing::flush, 1, TimeUnit.SECONDS); }
    }

    /** A resource name (gfx/tiles/dirt, gfx/fx/seasonmap) is an identifier, not a phrase to translate. */
    public static boolean resourcePath(String text) {
        return text.matches("^[a-z0-9_.-]+(/[a-z0-9_.-]+)+$");
    }

    private static boolean keyChord(String text) {
        return text.matches("(?i)^(ctrl|shift|alt|meta|super)(\\+(ctrl|shift|alt|meta|super|[\\p{L}\\p{N}]|⇥|⏎|⌫))+$");
    }

    private static String escape(String value, boolean key) {
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if(c == '\n') out.append("\\n");
            else if(c == '\r') out.append("\\r");
            else if(c == '\t') out.append("\\t");
            else { if(c == '\\' || (key && " =:#!".indexOf(c) >= 0) || (i == 0 && c == ' ')) out.append('\\'); out.append(c); }
        }
        return out.toString();
    }

    public static synchronized void flush() {
        scheduled = false;
        if(pending.isEmpty()) return;
        Path root = hearthborn.io.Logs.directory();
        try {
            Files.createDirectories(root);
            Path file = root.resolve("missing-translations-" + hearthborn.L10n.language() + ".log");
            SortedSet<String> records = new TreeSet<>();
            if(Files.isRegularFile(file))
                for(String line : Files.readAllLines(file, StandardCharsets.UTF_8))
                    if(!line.isEmpty() && !line.startsWith("#")) records.add(line);
            for(Map.Entry<String, SortedSet<String>> batch : pending.entrySet())
                for(String text : batch.getValue()) records.add("[" + batch.getKey() + "]\t" + escape(text, false));
            Path temp = Files.createTempFile(root, "missing-translations-", ".tmp");
            try(Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                out.write("# Missing translations: [context] followed by the original phrase.\n");
                for(String record : records) out.write(record + "\n");
            }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            pending.clear();
        } catch(IOException e) { System.err.println("Could not save missing translations: " + e.getMessage()); }
    }
    private Missing() {}
}
