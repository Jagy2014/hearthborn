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

package hearthborn.food;

import haven.GameUI;
import haven.GItem;

/**
 * W16 food rules: Prelude: World 16, 2024-10-28. W16.2 changes decay, not this quality exponent.
 *
 * @author Jagy2014
 */
public final class FoodHunger {
    public enum World {
        WORLD16("c646473983afec09"), WORLD162("fd63ddee958da329"), UNKNOWN("");
        public final String id;
        World(String id) {this.id = id;}

        public static World fromId(String id) {
            for(World world : values())
                if(world != UNKNOWN && world.id.equalsIgnoreCase(id)) return world;
            return UNKNOWN;
        }

        public static World of(GItem item) {
            GameUI game = item.getparent(GameUI.class);
            if(game == null && item.ui != null) game = item.ui.root.getchild(GameUI.class);
            return fromId(game == null ? null : game.genus);
        }
    }

    public static double quality(Double quality) {
        return quality != null && Double.isFinite(quality) && quality > 0 ? quality : 10;
    }

    /** Promille per piece, before table bonuses. +2 points count twice, as in the server's FoodInfo.sev. */
    public static double roast(World world, double quality, double ratio, double[] fep, double[] bonus) {
        if(world == null || world == World.UNKNOWN || !Double.isFinite(ratio) || ratio <= 0)
            return Double.NaN;
        double weighted = 0;
        for(double value : fep) weighted += value;
        for(double value : bonus) weighted += 2 * value;
        return weighted / ratio * Math.sqrt(Math.sqrt(quality(quality) / 10));
    }

    private FoodHunger() {}
}
