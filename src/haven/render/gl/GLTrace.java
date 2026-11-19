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

package haven.render.gl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;

/**
 * Finds out what makes a frame hitch on the graphics thread. While it is on, every submission to the driver is timed,
 * and one that takes longer than the limit is written to {@code logs/gltrace.log} with the calls that took the time
 * (shader builds, texture and buffer uploads, buffer swap). Off by default; the {@code gltrace} console command and
 * the {@code hearthborn.gltrace} property turn it on. Everything here runs on the graphics thread only.
 *
 * @author Jagy2014
 */
public final class GLTrace {
    private static final int KEEP = 256;
    private static final long MIN_CALL = 1_000_000L;

    private static volatile boolean on = Boolean.getBoolean("hearthborn.gltrace");
    private static volatile long limit = Long.getLong("hearthborn.gltrace.limit", 40L) * 1_000_000L;

    private static long inner;
    private static long start;
    private static int n;
    private static final String[] names = new String[KEEP];
    private static final long[] times = new long[KEEP];
    private static final Object[] calls = new Object[KEEP];
    private static long submissions, slow;

    private GLTrace() {}

    public interface Body {void run();}

    public static boolean enabled() {return(on);}

    public static void enable(boolean value, long limitms) {
	limit = Math.max(1, limitms) * 1_000_000L;
	on = value;
    }

    public static boolean active() {return(on);}

    public static void begin() {
	n = 0;
	inner = 0;
	start = System.nanoTime();
    }

    /** Runs one command, noting the time it spent itself, apart from the commands nested in it. */
    public static void time(Object cmd, Body body) {
	long saved = inner;
	inner = 0;
	long t0 = System.nanoTime();
	try {
	    body.run();
	} finally {
	    long dt = System.nanoTime() - t0;
	    long self = dt - inner;
	    inner = saved + dt;
	    if((self >= MIN_CALL) && (n < KEEP)) {
		calls[n] = cmd;
		times[n] = self;
		n++;
	    }
	}
    }

    public static void end() {
	long total = System.nanoTime() - start;
	submissions++;
	if(total < limit)
	    return;
	slow++;
	Integer[] order = new Integer[n];
	for(int i = 0; i < n; i++)
	    order[i] = i;
	Arrays.sort(order, (a, b) -> Long.compare(times[b], times[a]));
	StringBuilder buf = new StringBuilder();
	buf.append(String.format("%s  %.1f ms in %d calls over 1 ms (submission %d, slow %d)%n", LocalTime.now(), total / 1e6, n, submissions, slow));
	Map<String, Long> kinds = new TreeMap<>();
	for(int i = 0; i < n; i++)
	    kinds.merge(kind(calls[i]), times[i], Long::sum);
	buf.append("    by kind: ");
	kinds.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
	    .forEach(e -> buf.append(String.format("%s %.1f ms; ", e.getKey(), e.getValue() / 1e6)));
	buf.append('\n');
	for(int i = 0; i < Math.min(n, 5); i++) {
	    int k = order[i];
	    String d = String.valueOf(calls[k]).replace('\n', ' ');
	    if(d.length() > 160)
		d = d.substring(0, 160) + "...";
	    buf.append(String.format("    %.1f ms  %s%n", times[k] / 1e6, d));
	}
	write(buf.toString());
	n = 0;
    }

    private static String kind(Object cmd) {
	String s = String.valueOf(cmd);
	int p = s.indexOf('(');
	return((p > 0) ? s.substring(0, p) : s);
    }

    private static void write(String text) {
	try {
	    Path dir = hearthborn.io.Logs.directory();
	    Files.createDirectories(dir);
	    Files.write(dir.resolve("gltrace.log"), text.getBytes(StandardCharsets.UTF_8),
			StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	} catch(IOException | RuntimeException e) {
	    on = false;
	}
    }
}
