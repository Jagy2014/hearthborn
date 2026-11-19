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
import java.awt.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * Cached arithmetic summary, retaining the native detailed tooltip.
 *
 * @author Jagy2014
 */
public final class AttributeValues implements Disposable {
    private static final Text.Foundry SMALL = new Text.Foundry(Text.serif, 12).aa(true);
    private static final Color POSITIVE = new Color(128, 255, 128), NEGATIVE = new Color(255, 128, 128);
    private static final ClassValue<Optional<Method>> MOD = new ClassValue<Optional<Method>>() {
        protected Optional<Method> computeValue(Class<?> type) {
            for(Class<?> base = type; base != null; base = base.getSuperclass()) {
                if(base.getName().equals("haven.res.ui.tt.attr.mod.Modifier")) {
                    try { return Optional.of(base.getMethod("mod")); } catch(NoSuchMethodException ignored) { }
                }
            }
            return Optional.empty();
        }
    };
    private ItemInfo.Raw source;
    private long base, positive, negative, total;
    private int planned = Integer.MIN_VALUE, actualBase, actualComputed;
    private boolean ready;
    private double retry;

    public static long[] totals(int base, int computed, int planned, Iterable<ItemInfo> info) {
        long positive = 0, negative = 0;
        for(ItemInfo item : info) {
            Optional<Method> method = MOD.get(item.getClass());
            if(!method.isPresent()) continue;
            try {
                long value = ((Number)method.get().invoke(item)).longValue();
                String name = (String)method.get().getDeclaringClass().getMethod("name").invoke(item);
                if("Value ceiling".equals(name)) continue;
                if(value > 0) positive += value; else negative += value;
            } catch(InvocationTargetException e) {
                if(e.getCause() instanceof Loading) throw (Loading)e.getCause();
            } catch(ReflectiveOperationException ignored) { }
        }
        long residual = (long)computed - base - positive - negative;
        long projectedBase = (long)base + planned;
        long total = (long)computed + planned;
        if(planned != 0 && residual < 0 && computed == 2L * base) {
            total = Math.min(projectedBase + positive + negative, 2L * projectedBase);
            residual = total - projectedBase - positive - negative;
        }
        if(residual > 0) positive += residual; else negative += residual;
        return new long[] {projectedBase, positive, negative, total};
    }

    public void update(CharWnd.AttrWdg owner, int planned, double dt) {
        retry -= dt;
        if(ready && source == owner.attr.info && actualBase == owner.attr.base && actualComputed == owner.attr.comp && this.planned == planned) return;
        if(retry > 0 && this.planned == planned && actualBase == owner.attr.base && actualComputed == owner.attr.comp) return;
        retry = .2;
        java.util.List<ItemInfo> info;
        boolean loaded = true;
        long[] values;
        try {
            info = owner.info();
            values = totals(owner.attr.base, owner.attr.comp, planned, info);
        } catch(Loading e) {
            loaded = false;
            values = totals(owner.attr.base, owner.attr.comp, planned, Collections.emptyList());
        }
        if(base != values[0] || positive != values[1] || negative != values[2] || total != values[3] || cells == null) {
            base = values[0]; positive = values[1]; negative = values[2]; total = values[3]; dispose();
        }
        source = owner.attr.info; actualBase = owner.attr.base; actualComputed = owner.attr.comp; this.planned = planned; ready = loaded;
    }

    private Text[] cells;
    private int[] ends;
    private int painted = -1;
    public int[] widths() {
        prepare();
        int[] widths = new int[4];
        for(int i = 0; i < 4; i++) widths[i] = cells[i].sz().x;
        return widths;
    }
    public void columns(int[] ends) { this.ends = ends; }
    private void prepare() {
        if(cells != null && painted == UiTheme.generation()) return;
        dispose();
        boolean light = UiTheme.light();
        Color plain = light ? UiTheme.INK : Color.WHITE;
        Color plus = light ? UiTheme.BONUS : POSITIVE;
        Color minus = light ? UiTheme.PENALTY : NEGATIVE;
        cells = new Text[] {
            SMALL.render(Long.toString(base), plain),
            SMALL.render(positive == 0 ? "" : "+" + positive, plus),
            SMALL.render(negative == 0 ? "" : Long.toString(negative), minus),
            SMALL.render(Long.toString(total), plus)
        };
        painted = UiTheme.generation();
    }
    public void draw(GOut g, int centerY) {
        if(ends == null) return;
        prepare();
        for(int i = 0; i < 4; i++) g.aimage(cells[i].tex(), new Coord(ends[i], centerY), 1, .5);
    }
    public void dispose() {
        if(cells != null) { for(Text cell : cells) cell.dispose(); cells = null; }
    }
}
