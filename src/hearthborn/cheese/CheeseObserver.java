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

package hearthborn.cheese;
import haven.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import hearthborn.timers.Timers;
import java.util.*;

/**
 * Observations are attached to the opened object, not to another nearby rack.
 *
 * @author Jagy2014
 */
public final class CheeseObserver {
    private static final Map<Window,CheeseRackDetails> details=new WeakHashMap<>();
    private static boolean rack(String caption) {
        return caption!=null && (caption.equals("Rack")||caption.equals("Cheese Rack")||caption.equals("C. Rack")
            ||caption.equals(L10n.tr("Cheese Rack"))||caption.equals(L10n.tr("Rack")));
    }
    public static void beginScan() {
        details.entrySet().removeIf(entry -> entry.getKey().parent==null);
    }
    public static void observe(Window window,GameUI gui) {
        if(!rack(window.cap))return;
        Gob gob=ObjectWindows.target(window,gui,CheeseAging::isRack);
        if(gob==null)return;
        if(!details.containsKey(window)) {
            CheeseRackDetails panel=window.add(new CheeseRackDetails(window,gob),Coord.z);
            details.put(window,panel);
        }
        snapshot(window,gob);
    }
    static void snapshot(Window window,Gob gob) {
        List<WItem> items=new ArrayList<>(window.children(WItem.class));
        items.sort(Comparator.comparingInt((WItem w)->w.parentpos(window).y).thenComparingInt(w->w.parentpos(window).x));
        List<String> overlay=CheeseAging.traysFromOverlay(gob);
        List<CheeseAging.Tray> trays=new ArrayList<>();
        long now=System.currentTimeMillis();
        for(WItem item:items) {
            if(CheeseAging.emptyTray(item.item))continue;
            String kind=CheeseAging.itemKind(item.item);
            if(kind==null || item.item.meterUpdated<=0)return;
            double meter=GameObjects.meter(item.item);
            int left=CheeseAging.remainingSeconds(item.item,gob,meter);
            CheeseAging.Tray tray=new CheeseAging.Tray(kind,meter,item.item.meterUpdated,left<0?0:now+left*1000L);
            tray.state=CheeseAging.itemState(item.item,meter);
            tray.location=CheeseAging.loc(gob);
            tray.overlay=trays.size()<overlay.size()?overlay.get(trays.size()):null;
            trays.add(tray);
        }
        if(overlay.size()!=trays.size())return;
        CheeseAging.noteRack(gob.id,trays,overlay.size());
        int left=CheeseAging.leftSeconds(gob);
        String key="cheese:"+gob.id;
        hearthborn.world.ObjectNames.bind(key,gob);
        if(!trays.isEmpty())hearthborn.world.ObjectNames.place(gob,CheeseAging.loc(gob));
        if(hearthborn.timers.TimerSettings.automatic() && left>0)Timers.track(key,L10n.tr("Cheese rack")+" #"+Long.toHexString(gob.id),now+left*1000L);
        if(trays.isEmpty())Timers.forget(key);
    }
    private CheeseObserver() {}
}
