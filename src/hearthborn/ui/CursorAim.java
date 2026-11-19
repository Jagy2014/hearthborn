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
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The magnifying glass is aimed with the middle of the lens, not the rim.
 *
 * @author Jagy2014
 */
public final class CursorAim {
    private static final Map<Resource, Coord> points = new WeakHashMap<>();

    private CursorAim() {}

    public static Coord point(Resource res) {
        Coord at = points.get(res);
        if(at != null) return at;
        at = res.flayer(Resource.negc).cc;
        String name = res.name;
        if(name.endsWith("/study") || name.endsWith("/inspect")) {
            Coord lens = lens(res.flayer(Resource.imgc).img);
            if(lens != null) at = lens;
        }
        points.put(res, at);
        return at;
    }

    /** Center of the round head. The handle hangs off the lower right. */
    private static Coord lens(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int limit = (h * 2) / 3;
        int minx = w, miny = h, maxx = -1, maxy = -1;
        for(int y = 0; y < limit; y++) {
            for(int x = 0; x < w; x++) {
                if(((img.getRGB(x, y) >>> 24) & 0xff) <= 40) continue;
                if(x < minx) minx = x;
                if(y < miny) miny = y;
                if(x > maxx) maxx = x;
                if(y > maxy) maxy = y;
            }
        }
        if(maxx < 0) return null;
        return new Coord((minx + maxx) / 2, (miny + maxy) / 2);
    }
}
