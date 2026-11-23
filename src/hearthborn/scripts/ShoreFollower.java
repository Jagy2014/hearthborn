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

package hearthborn.scripts;

import haven.*;
import java.util.function.Predicate;

/**
 * Keeps a vessel along a shore: the shore stays on one side and the vessel keeps a tile or two from it.
 * From the heading it looks toward the shore first and turns away from it until a ray ahead is free, so the vessel hugs the shore,
 * goes round a point that sticks out and follows a bay in. The land is whatever the predicate says is not water.
 * Angles are in map coordinates, where y grows downward: a larger angle turns the vessel to the right.
 *
 * @author Jagy2014
 */
final class ShoreFollower {
    static final double TILE = 11;
    /** The rays are this far apart. */
    static final double DELTA = Math.PI / 12;
    /** How far a ray must be free, and how far the target is from the vessel. */
    static final double REACH = 6 * TILE, HOP = 4 * TILE, SAMPLE = TILE / 2;
    /** Rays that are tried, counted from the one that points at the shore; the last one points back. */
    static final int RAYS = 19;

    /** Where to go next, and the heading that leads there. */
    static final class Hop {
        final double heading;
        final Coord2d target;
        Hop(double heading, Coord2d target) {this.heading = heading; this.target = target;}
    }

    private static int tile(double units) {return (int)Math.floor(units / TILE);}

    /** A point is blocked when it is on land or within a tile of land: a vessel is wider than a point. */
    static boolean blocked(Predicate<Coord> land, Coord2d point) {
        int x = tile(point.x), y = tile(point.y);
        for(int dy = -1; dy <= 1; dy++)
            for(int dx = -1; dx <= 1; dx++)
                if(land.test(Coord.of(x + dx, y + dy))) return true;
        return false;
    }

    static boolean blocked(Predicate<Coord> land, Predicate<Coord2d> hazard, Coord2d point) {
        if(blocked(land, point)) return true;
        return hazard != null && hazard.test(point);
    }

    static boolean clear(Predicate<Coord> land, Coord2d from, double angle) {
        return clear(land, null, from, angle);
    }

    static boolean clear(Predicate<Coord> land, Predicate<Coord2d> hazard, Coord2d from, double angle) {
        for(double d = SAMPLE; d <= REACH; d += SAMPLE)
            if(blocked(land, hazard, from.add(Math.cos(angle) * d, Math.sin(angle) * d))) return false;
        return true;
    }

    /** The next hop with the shore on the right or on the left, or null when every direction is blocked. */
    static Hop next(Predicate<Coord> land, Coord2d position, double heading, boolean shoreOnRight) {
        return next(land, null, position, heading, shoreOnRight);
    }

    /**
     * Same as {@link #next(Predicate, Coord2d, double, boolean)}, but rays that enter a hazard
     * (dangerous-animal radius, etc.) are skipped so the vessel steers around them.
     */
    static Hop next(Predicate<Coord> land, Predicate<Coord2d> hazard, Coord2d position, double heading, boolean shoreOnRight) {
        double side = shoreOnRight ? 1 : -1;
        for(int k = 0; k < RAYS; k++) {
            double angle = heading + side * (Math.PI / 2 - k * DELTA);
            if(clear(land, hazard, position, angle))
                return new Hop(angle, position.add(Math.cos(angle) * HOP, Math.sin(angle) * HOP));
        }
        return null;
    }

    /** The centre of the nearest land tile within the given number of tiles, or null. */
    static Coord2d nearestLand(Predicate<Coord> land, Coord2d position, int tiles) {
        Coord centre = Coord.of(tile(position.x), tile(position.y));
        Coord best = null;
        double distance = Double.MAX_VALUE;
        for(int dy = -tiles; dy <= tiles; dy++)
            for(int dx = -tiles; dx <= tiles; dx++) {
                double d = Math.hypot(dx, dy);
                if(d > tiles || d >= distance) continue;
                if(land.test(centre.add(dx, dy))) {best = centre.add(dx, dy); distance = d;}
            }
        return best == null ? null : Coord2d.of(best.x * TILE + TILE / 2, best.y * TILE + TILE / 2);
    }

    /** The heading that puts the given land on the chosen side. */
    static double startHeading(Coord2d position, Coord2d land, boolean shoreOnRight) {
        double toLand = Math.atan2(land.y - position.y, land.x - position.x);
        return toLand - (shoreOnRight ? 1 : -1) * Math.PI / 2;
    }

    private ShoreFollower() {}
}
