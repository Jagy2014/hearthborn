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
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Attribute;
import haven.res.ui.tt.attrmod.Entry;
import haven.res.ui.tt.attrmod.Mod;
import haven.resutil.Curiosity;
import haven.resutil.FoodInfo;
import java.util.*;

/**
 * A stack tooltip shows the same lines as one item inside it. Numbers are the amount-weighted mean.
 *
 * @author Jagy2014
 */
public final class StackTips {
    private StackTips() {}

    public static void present(GItem stack, List<ItemInfo> info) {
    if(stack == null || stack.contents == null || !hearthborn.QualityOverlay.stacked(stack)) return;
    Set<Class<?>> present = new HashSet<>();
    for(ItemInfo tip : info) present.add(tip.getClass());
    List<ItemInfo> added = new ArrayList<>();
    Study study = null;
    Food food = null;
    Bonuses bonuses = null;
    int studyAt = -1, foodAt = -1, bonusAt = -1;
    for(Widget child : stack.contents.children()) {
        if(!(child instanceof GItem)) continue;
        GItem member = (GItem)child;
        int amount = amount(member);
        for(ItemInfo tip : member.info()) {
        if(skip(tip) || present.contains(tip.getClass())) continue;
        if(tip instanceof Curiosity) {
            if(study == null) {study = new Study(); studyAt = added.size(); added.add(null);}
            study.add((Curiosity)tip, amount);
        } else if(tip instanceof FoodInfo) {
            if(food == null) {food = new Food(); foodAt = added.size(); added.add(null);}
            food.add((FoodInfo)tip, amount);
        } else if(tip instanceof AttrMod) {
            if(bonuses == null) {bonuses = new Bonuses(); bonusAt = added.size(); added.add(null);}
            bonuses.add((AttrMod)tip, amount);
        } else if(present.add(tip.getClass())) {
            added.add(tip);
        }
        }
    }
    if(study != null) added.set(studyAt, study.tip(stack));
    if(food != null) added.set(foodAt, food.tip(stack));
    if(bonuses != null) added.set(bonusAt, bonuses.tip(stack));
    for(ItemInfo tip : added) if(tip != null) info.add(tip);
    }

    private static boolean skip(ItemInfo tip) {
    if(tip instanceof ItemInfo.Name || tip instanceof GItem.Amount) return true;
    String name = tip.getClass().getName();
    return name.endsWith(".QBuff") || name.endsWith("QualityOverlay$Average");
    }

    private static int amount(GItem item) {
    for(ItemInfo tip : item.info()) {
        if(tip instanceof GItem.Amount) {
        int num = ((GItem.Amount)tip).itemnum();
        return num > 0 ? num : 1;
        }
    }
    return 1;
    }

    private static int round(double value) {return (int)Math.round(value);}

    private static final class Study {
    private double exp, mw, enc, time;
    private int count;

    void add(Curiosity curiosity, int amount) {
        exp += curiosity.exp * (double)amount;
        mw += curiosity.mw * (double)amount;
        enc += curiosity.enc * (double)amount;
        time += curiosity.time * (double)amount;
        count += amount;
    }

    ItemInfo tip(GItem owner) {
        if(count <= 0) return null;
        int seconds = round(time / count);
        if(seconds <= 0 && time > 0) seconds = 1;
        return new Curiosity(owner, round(exp / count), round(mw / count), round(enc / count), seconds);
    }
    }

    private static final class Food {
    private FoodInfo first;
    private double end, glut, cons, sev;
    private int count;
    private boolean effectsMatch = true;
    private double[] effect;
    private final Map<String, EventTotal> events = new LinkedHashMap<>();

    private static final class EventTotal {
        Resource resource;
        double sum;
        int count;
    }

    void add(FoodInfo info, int amount) {
        if(first == null) {
        first = info;
        effect = new double[info.efs == null ? 0 : info.efs.length];
        } else if(info.efs == null || info.efs.length != effect.length) {
        effectsMatch = false;
        }
        end += info.end * amount;
        glut += info.glut * amount;
        cons += info.cons * amount;
        sev += info.sev * amount;
        count += amount;
        if(effectsMatch) {
        for(int i = 0; i < effect.length; i++) effect[i] += info.efs[i].p * amount;
        }
        if(info.evs == null) return;
        for(FoodInfo.Event event : info.evs) {
        String key = event.ev.getres().name;
        EventTotal total = events.get(key);
        if(total == null) {
            total = new EventTotal();
            total.resource = event.ev.getres();
            events.put(key, total);
        }
        total.sum += event.a * amount;
        total.count += amount;
        }
    }

    ItemInfo tip(GItem owner) {
        if(first == null || count <= 0) return null;
        try {
        FoodInfo.Event[] evs = new FoodInfo.Event[events.size()];
        int i = 0;
        for(EventTotal total : events.values())
            evs[i++] = new FoodInfo.Event(total.resource, total.sum / total.count);
        FoodInfo.Effect[] efs = first.efs;
        if(effectsMatch && effect.length > 0) {
            efs = new FoodInfo.Effect[effect.length];
            for(int n = 0; n < effect.length; n++)
            efs[n] = new FoodInfo.Effect(first.efs[n].info, effect[n] / count);
        }
        int[] types = first.types == null ? new int[0] : first.types;
        return new FoodInfo(owner, end / count, glut / count, cons / count, sev / count, evs, efs, types);
        } catch(RuntimeException failed) {
        return first;
        }
    }
    }

    private static final class Bonuses {
    private final Map<Attribute, Total> rows = new LinkedHashMap<>();

    private static final class Total {double sum; int count;}

    void add(AttrMod tip, int amount) {
        for(Entry entry : tip.tab) {
        if(!(entry instanceof Mod)) continue;
        Mod mod = (Mod)entry;
        Total total = rows.get(mod.attr);
        if(total == null) rows.put(mod.attr, total = new Total());
        total.sum += mod.mod * amount;
        total.count += amount;
        }
    }

    ItemInfo tip(GItem owner) {
        if(rows.isEmpty()) return null;
        List<Entry> averaged = new ArrayList<>();
        for(Map.Entry<Attribute, Total> row : rows.entrySet()) {
        Total total = row.getValue();
        if(total.count > 0) averaged.add(new Mod(row.getKey(), total.sum / total.count));
        }
        return averaged.isEmpty() ? null : new AttrMod(owner, averaged);
    }
    }
}
