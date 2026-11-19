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
import haven.*;
import hearthborn.L10n;
import hearthborn.timers.Timers;
import hearthborn.util.GameObjects;
import java.util.*;

/**
 * Seeds are tracked by table and cell; silk batches retain their server observation time.
 *
 * @author Jagy2014
 */
public final class ItemProcessTimers {
    private static final Map<Window,Set<String>> tracked=new WeakHashMap<>();
    static void observe(Window window,String location,boolean table) {
        if(!TimerSettings.automatic())return;
        Set<String> present=new HashSet<>();
        long now=System.currentTimeMillis();
        try {
            for(WItem item:window.children(WItem.class)) {
                String resource=item.item.resource().name;
                boolean pot=table && (resource.equals("gfx/invobjs/treepot-s")||resource.equals("gfx/invobjs/treepot-t"));
                boolean egg=table && resource.equals("gfx/invobjs/silkegg");
                boolean cocoon=resource.equals("gfx/invobjs/silkcocoon");
                if(!pot&&!egg&&!cocoon)continue;
                Coord cell=item.c.sub(1,1).div(Inventory.sqsz);
                String source=location+":"+(pot?"seedling":egg?"silkegg":"cocoon")+":"+cell.x+":"+cell.y;
                present.add(source);
                if(resource.equals("gfx/invobjs/treepot-t"))continue;
                boolean progress=item.item.meter>0 || item.item.info().stream().anyMatch(info -> info instanceof GItem.MeterInfo);
                if(!progress)continue;
                double fraction=GameObjects.meter(item.item);
                if(!Double.isFinite(fraction) || fraction<0 || fraction>=1)continue;
                double total=pot?4*3600000d:egg?86400000d:2*86400000d;
                long left=(long)Math.ceil(total/3.29*(1-fraction))-(item.item.meterUpdated>0?Math.max(0,now-item.item.meterUpdated):0);
                if(left<=0)continue;
                String title=pot?L10n.tr("Seedling"):L10n.tr(egg?"Silkworm eggs: hatching":"Silkworm cocoons: moths");
                if(pot) {
                    for(ItemInfo info:item.item.info())if(info instanceof ItemInfo.Contents) {
                        ItemInfo.Name name=ItemInfo.find(ItemInfo.Name.class,((ItemInfo.Contents)info).sub);
                        if(name!=null)title+=": "+L10n.text("tooltip",name.str.text);
                    }
                }
                title+=" #"+Integer.toHexString(location.hashCode())+" "+(cell.x+1)+","+(cell.y+1);
                Timers.track(source,title,now+left);
            }
        } catch(Loading e){return;}
        for(String source:tracked.getOrDefault(window,Collections.emptySet()))if(!present.contains(source))Timers.pause(source);
        tracked.put(window,present);
    }
    private ItemProcessTimers() {}
}
