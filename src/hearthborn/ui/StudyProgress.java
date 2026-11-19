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

package hearthborn.ui;

import haven.*;
import haven.resutil.Curiosity;
import java.util.*;

/**
 * Curiosity durations are in game seconds; the display uses real time.
 *
 * @author Jagy2014
 */
public final class StudyProgress {
    public static final double GAME_SECONDS_PER_REAL_SECOND = 3.29;
    private static final Set<Widget> inventories = Collections.newSetFromMap(new WeakHashMap<Widget, Boolean>());
    private static final Map<GItem, Sample> samples = new WeakHashMap<>();
    private static class Sample { double fraction, observed; int info; }
    public static void register(Widget study) { inventories.add(study); }
    public static List<Widget> registered(GameUI gui) {
        List<Widget> result=new ArrayList<>();
        for(Widget widget:inventories)if(widget.parent!=null&&widget.getparent(GameUI.class)==gui)result.add(widget);
        return result;
    }
    public static boolean isStudy(Widget widget) {
        for(Widget current = widget; current != null; current = current.parent)
            if(inventories.contains(current)) return true;
        return false;
    }
    public static boolean locked(Widget widget) { return isStudy(widget) && Utils.getprefb("hearthborn/study/locked", false); }
    public static void locked(boolean locked) { Utils.setprefb("hearthborn/study/locked", locked); }
    public static double perHour(Curiosity curiosity) {
        return curiosity.time <= 0 ? 0 : curiosity.exp * 3600.0 * GAME_SECONDS_PER_REAL_SECOND / curiosity.time;
    }
    public static double realSeconds(int gameSeconds) { return gameSeconds / GAME_SECONDS_PER_REAL_SECOND; }
    public static double remaining(Curiosity curiosity) {
        if(!(curiosity.owner instanceof GItem)) return -1;
        GItem item = (GItem)curiosity.owner;
        if(!isStudy(item)) return -1;
        GItem.MeterInfo meter = ItemInfo.find(GItem.MeterInfo.class, item.info());
        double fraction = item.meter > 0 ? item.meter / 100.0 : meter == null ? 0 : meter.meter();
        double now = Utils.rtime();
        Sample sample = samples.get(item);
        if(sample == null || sample.fraction != fraction || sample.info != item.infoseq) {
            sample = new Sample(); sample.fraction = fraction; sample.observed = now; sample.info = item.infoseq;
            samples.put(item, sample);
        }
        return Math.max(0, realSeconds(curiosity.time) * (1 - fraction) - (now - sample.observed));
    }
    public static String shortTime(double seconds) {
        long minutes = Math.max(0, (long)Math.ceil(seconds / 60));
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60);
    }
    public static String longTime(double seconds) {
        long time = Math.max(0, (long)Math.ceil(seconds));
        return hearthborn.L10n.format("%dh %dm %ds", time / 3600, (time / 60) % 60, time % 60);
    }
}
