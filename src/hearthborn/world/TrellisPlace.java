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

package hearthborn.world;

import haven.*;

/**
 * Three trellis positions in one tile, spaced by the smallest place-grid step that clears the frame.
 *
 * @author Jagy2014
 */
public final class TrellisPlace {
    public static Coord2d slot(Gob gob, Coord2d mouse, Coord2d tile, double grid, double angle) {
        if(gob == null || mouse == null) return null;
        try {
            Drawable drawable = gob.getattr(Drawable.class);
            Resource resource = drawable == null ? null : drawable.getres();
            if(resource == null || !trellis(resource.name)) return null;
            java.util.List<Coord2d[]> polygons = new java.util.ArrayList<>();
            for(Resource.Obstacle obstacle : resource.layers(Resource.obst)) {
                if(obstacle.p == null) continue;
                for(Coord2d[] polygon : obstacle.p) polygons.add(polygon);
            }
            return slot(mouse, tile, grid, angle, polygons.toArray(new Coord2d[polygons.size()][]));
        } catch(Loading ignored) {
            return null;
        }
    }

    /** Center, or one cleared step to either side along the trellis's thin axis. Null when the shape is missing. */
    public static Coord2d slot(Coord2d mouse, Coord2d tile, double grid, double angle, Coord2d[][] obstacle) {
        if(mouse == null || tile == null || !(tile.x > 0) || !(tile.y > 0)) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;
        if(obstacle != null) {
            for(Coord2d[] polygon : obstacle) {
                if(polygon == null) continue;
                for(Coord2d point : polygon) {
                    if(point == null) continue;
                    any = true;
                    if(point.x < minX) minX = point.x;
                    if(point.y < minY) minY = point.y;
                    if(point.x > maxX) maxX = point.x;
                    if(point.y > maxY) maxY = point.y;
                }
            }
        }
        Coord2d center = Coord2d.of(mouse.floor(tile)).mul(tile).add(tile.div(2));
        if(!any) return null;
        double width = maxX - minX, depth = maxY - minY;
        if(!(width > 0) || !(depth > 0)) return null;
        boolean thinX = width < depth;
        double extent = thinX ? width : depth;
        double step = grid > 0 ? tile.x / grid : extent;
        if(!(step > 0)) return center;
        int steps = (int)Math.ceil(extent / step - 1e-9);
        if(steps < 1) steps = 1;
        int room = (int)Math.floor((tile.x / 2.0) / step - 1e-9);
        if(room < 1) return center;
        if(steps > room) steps = room;
        double offset = steps * step;
        double sine = Math.sin(angle), cosine = Math.cos(angle);
        double axisX = thinX ? cosine : -sine;
        double axisY = thinX ? sine : cosine;
        Coord2d best = center;
        double bestDistance = mouse.dist(center);
        for(int sign = -1; sign <= 1; sign += 2) {
            Coord2d candidate = center.add(axisX * sign * offset, axisY * sign * offset);
            double distance = mouse.dist(candidate);
            if(distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    static boolean trellis(String name) {
        return "gfx/terobjs/plants/trellis".equals(name);
    }

    private TrellisPlace() {}
}
