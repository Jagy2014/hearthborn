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

import haven.GameUI;
import hearthborn.L10n;
import hearthborn.meteor.StarFall;
import java.util.Set;

/**
 * Public integration API for countdowns, notifications and the optional window.
 *
 * @author Jagy2014
 */
public final class Timers {
    public static void toggle(GameUI gui) { TimerWindow.toggle(gui); }

    public static void tick(GameUI gui) {
        if(gui==null || gui.ui==null)return;
        long now=System.currentTimeMillis();
        for(TimerEntry item:TimerService.expire(now)) {
            String name=TimerWindow.displayName(item);
            String message=StarFall.isTimer(item.source)
                ? L10n.format("%s: forecast elapsed; impact has not been confirmed.",name)
                : item.source!=null && item.source.startsWith("cheese:")
                    ? L10n.format("%s: cheese is ready.",name)
                    : L10n.format("Timer %s finished.",name);
            gui.ui.msg(message);
        }
        TimerWindow.refresh(now);
    }

    public static void track(String source,String name,long deadline) { TimerService.track(source,name,deadline); }
    public static Set<String> sources(String prefix) { return TimerService.sources(prefix); }
    public static long deadline(String source) { return TimerService.deadline(source); }
    public static void pause(String source) { TimerService.pause(source); }
    public static void forget(String source) { TimerService.forget(source); }
    public static void namesChanged() { TimerService.namesChanged(); }

    private Timers() {}
}
