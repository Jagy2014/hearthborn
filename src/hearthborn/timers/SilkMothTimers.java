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
import hearthborn.L10n;
import haven.*;import hearthborn.timers.Timers;import java.util.*;
/**
 * One explicitly started breeding estimate per character, shared across cupboards.
 *
 * @author Jagy2014
 */
public final class SilkMothTimers extends Widget {
    private static final Map<Window,SilkMothTimers> panels=new WeakHashMap<>();
    private final Window window;private final String source;private final Button start;private final Label remaining;
    private SilkMothTimers(Window window,GameUI gui) {
        this.window=window;source="silkmoth-shared:"+gui.chrid;
        start=add(new Button(UI.scale(200),"Start egg timer",false,() -> {
            try {if(!hasPair(window))return;}catch(Loading ignored){return;}
            Timers.forget(source);
            Timers.track(source,L10n.tr("Silkworm eggs (no later than)"),System.currentTimeMillis()+(long)Math.ceil(86400000d/3.29));
            if(ui!=null)ui.msg(L10n.tr("Silkworm egg timer started"));update();
        }),Coord.z);
        start.settip("Start one shared egg timer for all cupboards with moth pairs.");
        remaining=add(new Label(""),Coord.z);remaining.hide();resize(start.sz);
    }
    public static boolean hasPair(Window window) {
        boolean male=false,female=false;
        for(WItem item:window.children(WItem.class))try {
            String name=item.item.resource().name;
            male|=name.equals("gfx/invobjs/silkmoth-m");female|=name.equals("gfx/invobjs/silkmoth-f");
            if(male&&female)return true;
        }catch(Loading loading){
            // An unrelated item may still be loading. Retry it on the next scan.
        }
        return male&&female;
    }
    public static void observe(Window window,GameUI gui) {
        panels.entrySet().removeIf(e -> e.getKey().parent==null);
        SilkMothTimers panel=panels.get(window);
        if(panel==null && hasPair(window)) {
            int bottom=0;for(Widget child:window.children())bottom=Math.max(bottom,child.c.y+child.sz.y);
            panel=window.add(new SilkMothTimers(window,gui),Coord.of(0,bottom+UI.scale(5)));panels.put(window,panel);window.pack();
        }
        if(panel!=null)panel.update();
    }
    private void update() {
        boolean pair=hasPair(window);long left=Timers.deadline(source)-System.currentTimeMillis();
        boolean running=left>0;
        start.show(pair&&!running);remaining.show(pair&&running);
        if(pair&&running) {
            long seconds=(left+999)/1000;
            remaining.settext(L10n.format("Eggs in: %s",String.format("%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60)));
        }
    }
}
