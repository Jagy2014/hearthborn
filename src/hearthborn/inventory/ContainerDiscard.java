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
 * Identity-checked discard plans shared by all overview dialogs.
 *
 * @author Jagy2014
 */
final class ContainerDiscard {
    static final class Plan {
        final List<ContainerOverview.Portion> members=new ArrayList<>();
        final List<GItem> targets=new ArrayList<>();
        int count,stacks;
        static Plan make(ContainerOverview.Group group,Double limit) {
            Plan plan=new Plan();Set<GItem> eligible=Collections.newSetFromMap(new IdentityHashMap<>());
            Map<WItem,Double> qualities=new IdentityHashMap<>();
            if(limit!=null)for(WItem owner:group.owners())qualities.put(owner,QualityOverlay.qualityOf(owner.item));
            for(ContainerOverview.Portion portion:group.portions) {
                Double quality=qualities.get(portion.owner);
                if(limit==null||(quality!=null&&quality<limit)) {
                    plan.members.add(portion);plan.count+=portion.count;eligible.add(portion.item);
                }
            }
            for(WItem owner:group.owners()) {
                if(eligible.contains(owner.item)) {
                    plan.targets.add(owner.item);if(QualityOverlay.stacked(owner.item))plan.stacks++;continue;
                }
                Collection<GItem> contents=owner.item.contents==null?Collections.emptyList():owner.item.contents.children(GItem.class);
                if(!contents.isEmpty()&&eligible.containsAll(contents)) {
                    plan.targets.add(owner.item);plan.stacks++;
                } else for(ContainerOverview.Portion portion:plan.members)if(portion.owner==owner)plan.targets.add(portion.item);
            }
            return plan;
        }
        String summary(){return L10n.format("%d items",count)+(stacks>0?" · "+L10n.format("%d stacks",stacks):"");}
        boolean same(Plan other) {
            if(other==null||count!=other.count||stacks!=other.stacks||members.size()!=other.members.size()||targets.size()!=other.targets.size())return false;
            Map<GItem,ContainerOverview.Portion> previous=new IdentityHashMap<>();
            for(ContainerOverview.Portion portion:members)previous.put(portion.item,portion);
            for(ContainerOverview.Portion portion:other.members) {
                ContainerOverview.Portion before=previous.get(portion.item);
                if(before==null||before.owner!=portion.owner||before.count!=portion.count||Double.compare(before.quality,portion.quality)!=0)return false;
            }
            Set<GItem> before=Collections.newSetFromMap(new IdentityHashMap<>());before.addAll(targets);
            return before.containsAll(other.targets);
        }
    }

    private ContainerDiscard(){}
}
