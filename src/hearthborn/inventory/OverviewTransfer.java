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
import java.util.*;

/**
 * Direct inventory transfers, either an entire batch of outer items or ordered individual members.
 *
 * @author Jagy2014
 */
final class OverviewTransfer {
    private static final Map<GameUI,OverviewTransfer> active=new WeakHashMap<>();
    final ContainerOverview overview;final Inventory target;final String key;
    final Deque<ContainerOverview.Portion> pending;
    final int requested;final boolean gesture;
    int moved,beforeSource,beforeTarget;double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,until;
    ContainerOverview.Portion current;
    private ContainerOverview.Group wholeGroup;
    private List<ContainerOverview.Portion> whole;
    private List<WItem> owners;
    private boolean issued;
    private OverviewTransfer(ContainerOverview overview,ContainerOverview.Row row,List<ContainerOverview.Portion> items,int requested,boolean gesture){
        this.overview=overview;target=overview.destination();key=ContainerOverview.baseKey(row.key);pending=new ArrayDeque<>(items);this.requested=requested;this.gesture=gesture;until=Utils.rtime()+5;
    }
    static List<ContainerOverview.Portion> ordered(ContainerOverview.Group group,int amount,boolean worst){
        List<ContainerOverview.Portion> sorted=new ArrayList<>(group.portions),result=new ArrayList<>();
        sorted.removeIf(p->p.quality<0);sorted.sort(Comparator.comparingDouble((ContainerOverview.Portion p)->p.quality));
        if(!worst)Collections.reverse(sorted);
        for(ContainerOverview.Portion p:sorted)if(p.count<=amount){result.add(p);amount-=p.count;if(amount==0)break;}
        return result;
    }
    static boolean start(ContainerOverview overview,ContainerOverview.Row row,int amount,boolean worst,boolean gesture){
        GameUI gui=overview.source().getparent(GameUI.class);if(gui==null||BagTransfers.running(gui)||overview.destination()==null)return false;
        List<ContainerOverview.Portion> items=ordered(row.group,amount,worst);if(items.isEmpty())return false;
        active.put(gui,new OverviewTransfer(overview,row,items,Math.min(amount,row.group.count),gesture));return true;
    }
    static boolean startWhole(ContainerOverview overview,ContainerOverview.Row row,boolean gesture){
        GameUI gui=overview.source().getparent(GameUI.class);
        if(gui==null||BagTransfers.running(gui)||overview.destination()==null||overview.destination().wdgid()<0)return false;
        OverviewTransfer task=new OverviewTransfer(overview,row,Collections.emptyList(),row.group.count,gesture);
        task.wholeGroup=row.group;task.whole=new ArrayList<>(row.group.portions);task.owners=new ArrayList<>(row.group.owners());
        active.put(gui,task);return true;
    }
    static int count(Inventory inventory,String key){
        ContainerOverview.Snapshot snapshot=ContainerOverview.snapshot(inventory);if(snapshot.loading)throw new Loading();
        for(ContainerOverview.Group group:snapshot.groups)if(group.key.equals(key))return group.count;return 0;
    }
    static boolean running(GameUI gui){return active.containsKey(gui);}
    static void cancel(GameUI gui){OverviewTransfer task=active.remove(gui);if(task!=null)task.finish(false);}
    void finish(boolean full){overview.transferred(moved,requested,min,max,full,gesture);}
    static void tick(GameUI gui){OverviewTransfer task=active.get(gui);if(task==null)return;try{task.update(gui);}catch(Loading waiting){if(task.until!=0&&Utils.rtime()>task.until){active.remove(gui);task.finish(false);}}}
    private void update(GameUI gui){
        if(!overview.usable()||target.parent==null||gui.vhand!=null){active.remove(gui);finish(false);return;}
        if(target!=gui.maininv&&!target.tvisible()){active.remove(gui);finish(false);return;}
        if(whole!=null){updateWhole(gui);return;}
        if(current==null){
            if(pending.isEmpty()){active.remove(gui);finish(moved<requested);return;}
            ContainerOverview.Portion next=pending.peek();if(!next.live(overview.source())){active.remove(gui);finish(false);return;}
            if(target.wdgid()<0){active.remove(gui);finish(false);return;}
            beforeSource=count(overview.source(),key);beforeTarget=count(target,key);
            // A Loading above must leave this command unissued, not waiting for a receipt.
            current=next;current.item.wdgmsg("invxf2",0,1,target.wdgid());until=Utils.rtime()+5;return;
        }
        int from=count(overview.source(),key),to=count(target,key);
        if(beforeSource-from>=current.count&&to-beforeTarget>=current.count){
            moved+=current.count;min=Math.min(min,current.quality);max=Math.max(max,current.quality);pending.remove();current=null;until=Utils.rtime()+5;
        }else if(Utils.rtime()>until){active.remove(gui);finish(true);}
    }
    private void updateWhole(GameUI gui){
        if(!issued){
            if(!wholeGroup.wholeStacks()){active.remove(gui);finish(false);return;}
            for(ContainerOverview.Portion p:whole)if(!p.live(overview.source())){active.remove(gui);finish(false);return;}
            beforeSource=count(overview.source(),key);beforeTarget=count(target,key);
            // Address each outer item exactly once. Sending member invxf2 commands would unpack stacks.
            issued=true;until=Utils.rtime()+5;
            for(WItem owner:owners)owner.item.wdgmsg("invxf2",0,1,target.wdgid());
            return;
        }
        int removed=beforeSource-count(overview.source(),key),received=count(target,key)-beforeTarget;
        int gone=0;double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
        for(ContainerOverview.Portion p:whole)if(!p.live(overview.source())){
            gone+=p.count;if(p.quality>=0){low=Math.min(low,p.quality);high=Math.max(high,p.quality);}
        }
        if(gone<=removed&&gone<=received){moved=gone;min=low;max=high;}
        if(moved==requested||Utils.rtime()>until){active.remove(gui);finish(moved<requested);}
    }
}
