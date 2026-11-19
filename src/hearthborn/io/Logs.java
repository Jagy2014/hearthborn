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

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Application logs live under the current working directory.
 *
 * @author Jagy2014
 */
public final class Logs {
    /** The error log of one day stops growing here; what comes after goes to the console only. */
    public static final long ERROR_LIMIT = 5L << 20;
    private static boolean installed;
    public static Path directory() {
        return Paths.get(System.getProperty("hearthborn.logs.directory", "logs"));
    }
    public static Path file(String name) { return directory().resolve(name); }
    public static synchronized boolean installed() { return installed; }

    /**
     * Gives the file a fresh start when it belongs to an earlier day or has reached the limit: the old content moves to
     * {@code name.old}, replacing the previous {@code .old} file, so at most two days of errors stay on the disk.
     */
    public static boolean rotate(Path file, LocalDate today, long limit) throws IOException {
        if(!Files.isRegularFile(file)) return false;
        LocalDate written = Files.getLastModifiedTime(file).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if(!written.isBefore(today) && Files.size(file) < limit) return false;
        Files.move(file, file.resolveSibling(file.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /** What a diagnostic log may grow to before it starts afresh. */
    public static final long DEBUG_LIMIT = 1L << 20;

    /**
     * Appends to a diagnostic log. The log does not pile up: a new day or the size limit gives it a fresh start, and the
     * old content stays as {@code name.old} for one more round.
     */
    public static synchronized void append(String name, String text) throws IOException {
        Files.createDirectories(directory());
        Path file = file(name);
        rotate(file, LocalDate.now(), DEBUG_LIMIT);
        Files.write(file, text.getBytes(java.nio.charset.StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** The error log: a new file every day, and a cap on its size, so an error that repeats cannot fill the disk. */
    static final class DailyFile {
        private final Path path;
        private final long limit;
        private OutputStream out;
        private LocalDate day;
        private long size;
        private boolean capped;
        DailyFile(Path path, long limit) { this.path = path; this.limit = limit; }

        synchronized void write(byte[] bytes, int start, int length) {
            try {
                LocalDate today = LocalDate.now();
                if(out == null || !today.equals(day)) open(today);
                if(capped) return;
                if(size + length > limit) {
                    capped = true;
                    byte[] note = ("\n--- the error log of the day reached " + (limit >> 20) + " MB; more is only on the console ---\n").getBytes("UTF-8");
                    out.write(note); out.flush();
                    return;
                }
                out.write(bytes, start, length);
                size += length;
            } catch(IOException | RuntimeException e) { /* the console still has it */ }
        }
        synchronized void flush() {
            try { if(out != null) out.flush(); } catch(IOException e) { }
        }
        private void open(LocalDate today) throws IOException {
            if(out != null) out.close();
            Files.createDirectories(path.getParent() == null ? Paths.get(".") : path.getParent());
            rotate(path, today, limit);
            out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            day = today;
            size = Files.size(path);
            capped = size >= limit;
        }
    }

    public static synchronized void install() {
        if(installed) return;
        final PrintStream console = System.err;
        try {
            Files.createDirectories(directory());
            final DailyFile log = new DailyFile(file("error.log"), ERROR_LIMIT);
            log.write(new byte[0], 0, 0);
            System.setErr(new PrintStream(new OutputStream() {
                public synchronized void write(int value) throws IOException { console.write(value); log.write(new byte[] {(byte)value}, 0, 1); }
                public synchronized void write(byte[] bytes, int start, int length) throws IOException {
                    console.write(bytes, start, length); log.write(bytes, start, length);
                }
                public synchronized void flush() throws IOException { console.flush(); log.flush(); }
            }, true, "UTF-8"));
            installed = true;
        } catch(IOException e) { console.println("Could not open error log: " + e.getMessage()); }
    }
    private Logs() {}
}
