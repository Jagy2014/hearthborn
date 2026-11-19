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

package hearthborn.inventory;

import haven.*;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import java.util.*;

/**
 * Reconcile native combine receipts before choosing the next pair of outer items.
 *
 * @author Jagy2014
 */
public final class ItemStacking extends Widget {
    private final Inventory inventory;
    private final GameUI gui;
    private final Set<String> groups = new LinkedHashSet<>();
    private final Map<String,Integer> limits = new HashMap<>();
    private Map<GItem,Integer> before, observed;
    private GItem target, source;
    private WItem donorView;
    private String key;
    private int targetCount;
    private long beforePacking;
    private double elapsed, quiet;

    private ItemStacking(Inventory inventory, GameUI gui, ContainerOverview.Snapshot snapshot) {
        this.inventory=inventory;this.gui=gui;
        for(ContainerOverview.Group group:snapshot.groups)
            if(group.owners().size()>1)groups.add(group.key);
    }

    static boolean running(GameUI gui) {return !gui.children(ItemStacking.class).isEmpty();}

    static void start(Inventory inventory) {
        GameUI gui=inventory.getparent(GameUI.class);
        if(gui==null||BagTransfers.running(gui))return;
        ContainerOverview.Snapshot snapshot=ContainerOverview.snapshot(inventory);
        if(snapshot.loading) {
            inventory.ui.error(L10n.tr("Items are still loading. Try again."));return;
        }
        inventory.add(new ItemStacking(inventory,gui,snapshot));
    }

    private static Map<GItem,Integer> counts(ContainerOverview.Snapshot snapshot) {
        Map<GItem,Integer> result=new HashMap<>();
        for(ContainerOverview.Group group:snapshot.groups)
            for(ContainerOverview.Portion portion:group.portions)
                result.merge(portion.owner.item,portion.count,Integer::sum);
        return result;
    }
    private static long packing(ContainerOverview.Snapshot snapshot,String key){
        Map<GItem,Integer> counts=new IdentityHashMap<>();
        for(ContainerOverview.Group group:snapshot.groups)if(group.key.equals(key))
            for(ContainerOverview.Portion portion:group.portions)counts.merge(portion.owner.item,portion.count,Integer::sum);
        long score=0;for(int count:counts.values())score+=(long)count*count;
        return score;
    }

    private void stop() {destroy();}

    public void tick(double dt) {
        Window window=inventory.getparent(Window.class);
        if(inventory.parent==null||window==null||!window.visible()||!inventory.tvisible()
                ||BagTransfers.running(gui)) {stop();return;}
        ContainerOverview.Snapshot snapshot=ContainerOverview.snapshot(inventory);
        if(before!=null) {
            elapsed+=dt;
            if(elapsed>5) {
                stop();inventory.ui.error(L10n.tr("Stacking items timed out. Stacking stopped."));return;
            }
            if(snapshot.loading||gui.vhand!=null) {quiet=0;return;}
            Map<GItem,Integer> current=counts(snapshot);
            if(!current.equals(observed)) {observed=current;quiet=0;return;}
            quiet+=dt;
            if(quiet<0.2)return;
            boolean changed=!current.equals(before);
            // A quiet snapshot alone is not an acknowledgement: the take may still be
            // in transit. Require conserved inventory quantity and the old donor view
            // to have left the grid (take/drop recreates it even after a no-op combine).
            if(current.values().stream().mapToInt(Integer::intValue).sum()
                    !=before.values().stream().mapToInt(Integer::intValue).sum())return;
            if(!changed&&donorView.parent==inventory)return;
            int after=current.getOrDefault(target,0);
            // An unchanged target rejected another identical item. Its actual count is the
            // capacity for this run; no item-specific maximum (butter, seeds, etc.) is assumed.
            // Reparenting can replace every shell ID even for a rejected merge.
            // Progress is denser packing, not different widget identities or cell order.
            if(packing(snapshot,key)<=beforePacking)limits.merge(key,targetCount,Math::min);
            else if(after>targetCount&&current.containsKey(source))limits.merge(key,after,Math::min);
            before=null;
        }
        if(snapshot.loading)return;
        if(gui.vhand!=null) {stop();return;}
        Map<GItem,Integer> current=counts(snapshot);
        for(ContainerOverview.Group group:snapshot.groups) {
            if(!groups.contains(group.key))continue;
            List<WItem> owners=new ArrayList<>();
            int limit=limits.getOrDefault(group.key,Integer.MAX_VALUE);
            for(WItem owner:group.owners()) {
                int count=0;
                for(ContainerOverview.Portion portion:group.portions)if(portion.owner==owner)count+=portion.count;
                // Do not combine a shell containing another material/name as well.
                if(count==current.get(owner.item)&&count<limit)owners.add(owner);
            }
            if(owners.size()<2)continue;
            owners.sort(Comparator.<WItem>comparingInt(w->QualityOverlay.stacked(w.item)?0:1)
                .thenComparingInt(w->current.get(w.item)));
            WItem destination=owners.remove(0);
            owners.sort(Comparator.comparingInt(w->current.get(w.item)));
            WItem donor=owners.get(0);
            key=group.key;target=destination.item;source=donor.item;donorView=donor;
            targetCount=current.get(target);before=current;observed=null;elapsed=quiet=0;
            beforePacking=packing(snapshot,key);
            // Keep this ordered batch together; drop returns any uncombined remainder
            // to its original inventory cell, never to the ground.
            source.wdgmsg("take",Coord.z);
            target.wdgmsg("itemact",InventoryActions.COMBINE);
            inventory.wdgmsg("drop",InventoryActions.cell(donor));
            return;
        }
        stop();
    }
}
