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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;

/**
 * One stage label on each tray sprite, above its tray.
 *
 * @author Jagy2014
 */
public final class CheeseRacks {
    private static final String PREF = "hearthborn/world/cheese-labels";
    private static final Color INK = new Color(252, 235, 255);
    private static final Map<String, Tex> cache = new HashMap<>();
    private static final Map<Class<?>, Field> inner = new HashMap<>();
    private static final Set<Sprite> labeled = Collections.newSetFromMap(new WeakHashMap<>());

    private CheeseRacks() {}

    private static volatile boolean shown=Utils.getprefb(PREF, true);
    private static double nextScan;
    public static boolean enabled() { return shown; }
    public static void enabled(boolean on) { shown=on; Utils.setprefb(PREF, on); if(!on){clearSprites();nextScan=0;} }

    public static void draw(MapView map, GOut g) {
        if(!enabled() || map.ui == null || map.ui.sess == null) {
            clearSprites();
            nextScan=0;
            return;
        }
        if(Utils.rtime()<nextScan)return;
        nextScan=Utils.rtime()+0.25;
        Set<Sprite> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Gob gob : copy(map.ui.sess.glob.oc)) {
            if(!rack(gob))
                continue;
            for(Gob.Overlay overlay : new ArrayList<>(gob.ols)) {
                Sprite tray = tray(overlay);
                if(tray == null || tray.res == null)
                    continue;
                String stage = stage(tray.res.name);
                tray.setTex2d(stage == null ? null : tex(stage));
                if(stage != null)
                    seen.add(tray);
            }
        }
        for(Sprite old : new ArrayList<>(labeled)) {
            if(!seen.contains(old))
                old.setTex2d(null);
        }
        labeled.clear();
        labeled.addAll(seen);
    }

    private static void clearSprites() {
        for(Sprite sprite : new ArrayList<>(labeled))
            sprite.setTex2d(null);
        labeled.clear();
    }

    private static boolean rack(Gob gob) {
        String name = resource(gob);
        return name != null && name.startsWith("gfx/terobjs/cheeserack");
    }

    private static String stage(String name) {
        int slash = name.lastIndexOf('-');
        String raw = slash < 0 ? null : name.substring(slash + 1);
        if(raw == null || raw.isEmpty() || raw.equals("empty") || !raw.matches("(?i)l[1-4]"))
            return null;
        String prefix = L10n.find("label", "Cheese stage prefix");
        if(prefix == null || prefix.isEmpty())
            prefix = "L";
        return prefix + raw.substring(1);
    }

    private static Sprite tray(Gob.Overlay overlay) {
        try {
            Sprite sprite = overlay.spr;
            if(overlay.sm instanceof OCache.OlSprite) {
                String name = ((OCache.OlSprite)overlay.sm).res.get().name;
                if(name.startsWith("gfx/terobjs/items/cheesetray"))
                    return sprite;
            }
            if(sprite == null)
                return null;
            if(sprite.res != null && sprite.res.name.startsWith("gfx/terobjs/items/cheesetray"))
                return sprite;
            Field field = inner(sprite.getClass());
            if(field == null)
                return null;
            Object child = field.get(sprite);
            if(child instanceof Sprite && ((Sprite)child).res != null
                && ((Sprite)child).res.name.startsWith("gfx/terobjs/items/cheesetray"))
                return (Sprite)child;
        } catch(Loading | ReflectiveOperationException ignored) {}
        return null;
    }

    private static Field inner(Class<?> type) {
        if(inner.containsKey(type))
            return inner.get(type);
        Field field = null;
        try {
            field = type.getDeclaredField("espr");
            field.setAccessible(true);
        } catch(NoSuchFieldException ignored) {}
        inner.put(type, field);
        return field;
    }

    private static String resource(Gob gob) {
        try {
            Drawable draw = gob.getattr(Drawable.class);
            Resource res = draw == null ? null : draw.getres();
            return res == null ? null : res.name;
        } catch(Loading e) {
            return null;
        }
    }

    private static Tex tex(String label) {
        Tex tex = cache.get(label);
        if(tex == null) {
            // Same stroke and halo as every other world caption; the sprite draws the picture 1:1.
            tex = new TexI(hearthborn.world.WorldLabels.captionImage(label, INK, 1));
            cache.put(label, tex);
        }
        return tex;
    }

    private static List<Gob> copy(OCache oc) {
        return oc.snapshot();
    }
}
