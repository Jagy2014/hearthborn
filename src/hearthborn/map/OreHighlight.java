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

package hearthborn.map;

import haven.*;
import haven.render.BaseColor;
import haven.render.States;
import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.WeakHashMap;

/**
 * Paints ore veins on the ground, in caves and on mountains.
 *
 * @author Jagy2014
 */
public final class OreHighlight implements MCache.LocalOverlay {
    public static final String TAG = "ore";
    private static final Color COLOR = new Color(255, 168, 32, 96);
    private static final MCache.OverlayInfo ID = new MCache.OverlayInfo() {
        private final Material fill = paint(COLOR.getAlpha() / 256f);
        private final Material edge = paint(0.45f);
        public Collection<String> tags() { return Collections.singletonList(TAG); }
        public Material mat() { return fill; }
        public Material omat() { return edge; }
    };
    private static final WeakHashMap<Glob, OreHighlight> active = new WeakHashMap<>();
    private final Glob glob;
    private boolean added;

    private OreHighlight(Glob glob) { this.glob = glob; }

    public static void attach(MapView map, Glob glob) {
        OreHighlight highlight;
        synchronized(active) {
            highlight = active.get(glob);
            if(highlight == null) active.put(glob, highlight = new OreHighlight(glob));
        }
        synchronized(highlight) {
            if(!highlight.added) {
                glob.map.add(highlight);
                highlight.added = true;
            }
        }
        if(!map.visol(TAG)) map.enol(TAG);
    }

    public MCache.OverlayInfo id() { return ID; }

    public void fill(Area area, boolean[] mask) {
        MCache map = glob.map;
        for(Coord tile : area) {
            try {
                Resource resource = map.tilesetr(map.gettile(tile));
                if(resource != null && TerrainHighlight.ore(resource.name))
                    mask[area.ridx(tile)] = true;
            } catch(Loading ignored) {}
        }
    }

    private static Material paint(float alpha) {
        return new Material(new BaseColor(COLOR.getRed() / 255f, COLOR.getGreen() / 255f, COLOR.getBlue() / 255f, alpha), States.maskdepth);
    }
}
