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

/**
 * Persisted countdown state, independent of windows and world rendering.
 *
 * @author Jagy2014
 */
final class TimerEntry implements java.io.Serializable {
    private static final long serialVersionUID=1L;
    String name;
    String source;
    long duration;
    long finish;

    TimerEntry(String name, long duration, long finish) {
        this.name = name;
        this.duration = duration;
        this.finish = finish;
    }

    boolean running() { return finish > 0; }

    long remaining(long now) {
        return running() ? Math.max(0, finish - now) : duration;
    }

    void start(long now) {
        if(finish > 0)
            return;
        finish = now + Math.max(0, duration);
    }

    void stop(long now) {
        if(finish <= 0)
            return;
        duration = Math.max(0, finish - now);
        finish = 0;
    }
}
