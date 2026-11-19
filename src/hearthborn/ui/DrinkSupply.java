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
import java.util.*;
import java.util.regex.*;

/**
 * Totals only supplies owned by the player, including contents of equipped containers.
 *
 * @author Jagy2014
 */
public final class DrinkSupply {
    private static final Pattern CONTENT=Pattern.compile("([0-9]+(?:\\.[0-9]+)?) (?:l|liters?|litres?) of (Water|Tea)",Pattern.CASE_INSENSITIVE);
    public double water,tea,capacity;
    public boolean loading;
    public static double capacity(String resource) {
        if(resource.contains("/bucket"))return 10;
        if(resource.endsWith("/glassjug"))return 5;
        if(resource.endsWith("/waterskin"))return 3;
        if(resource.endsWith("/waterflask"))return 2;
        if(resource.contains("/kuksa"))return .8;
        return 0;
    }
    private static double[] contents(java.util.List<ItemInfo> info) {
        for(ItemInfo tip:info) {
            if(tip instanceof ItemInfo.Contents) {
                double[] found=contents(((ItemInfo.Contents)tip).sub);if(found!=null)return found;
            }
            if(tip instanceof ItemInfo.Name) {
                Matcher match=CONTENT.matcher(((ItemInfo.Name)tip).str.text);
                if(match.matches())return new double[]{match.group(2).equalsIgnoreCase("Water") ? 0 : 1,Double.parseDouble(match.group(1))};
            }
        }
        return null;
    }
    public void add(GItem item) {
        double max=capacity(item.res.get().name);if(max==0)return;
        java.util.List<ItemInfo> info=item.info();
        if(info.isEmpty()) {loading=true;return;}
        for(ItemInfo tip:info) {
            if(tip.getClass().getName().equals("haven.res.ui.tt.level.Level"))try {
                max=((Number)tip.getClass().getField("max").get(tip)).doubleValue()/100;
            } catch(ReflectiveOperationException ignored) {}
        }
        double[] content=contents(info);
        if(content!=null) {if(content[0]==0)water+=content[1];else tea+=content[1];}
        else if(ItemInfo.find(ItemInfo.Contents.class,info)!=null)return;
        capacity+=max;
    }
    private void scan(Widget widget,Set<GItem> visited) {
        if(widget==null)return;
        for(Widget child=widget.child;child!=null;child=child.next) {
            if(child instanceof GItem) {
                GItem item=(GItem)child;
                if(visited.add(item)) {
                    try {add(item);} catch(Loading e){loading=true;}
                    scan(item.contents,visited);
                }
            }
        }
    }
    public static DrinkSupply collect(GameUI gui) {
        DrinkSupply result=new DrinkSupply();
        Set<GItem> visited=Collections.newSetFromMap(new IdentityHashMap<GItem,Boolean>());
        result.scan(gui.maininv,visited);result.scan(gui.equipment(),visited);return result;
    }
}
