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

import java.util.*;

/**
 * Roast preview for raw animal meat. q10 values are the roast-meat table; food points scale with sqrt(quality / 10).
 *
 * @author Jagy2014
 */
public final class MeatRoast {
    private static final Map<String, Yield> MEAT = new HashMap<>();
    private static final String[] RAW = {
        "gfx/invobjs/meat-raw", "gfx/invobjs/meat-crust", "gfx/invobjs/meat-poultry", "gfx/invobjs/meat-weird"
    };

    // FEP/hunger tiers cross-checked against Kitten Rider Cookbook observations, 2026-11-12.
    // Source and attribution: docs/features/interface.en.md. NaN means no verified tier.
    static {
        meat("wil2:1", 400, 5, "adder");
        meat("agi:2,wil:2", 400, 6.25, "ant");
        meat("agi:1,con:1", 400, 5, "badger");
        meat("int:1,per:1", 400, 5, "bat");
        meat("str:1,str2:1", 400, 5, "bear");
        meat("agi:1,wil:1", 400, 5, "beaver");
        meat("agi:2,dex:2", 400, 6.25, "bee");
        meat("con:2", 400, 5, "beef", "cow");
        meat("int:2,con:1", 400, 5, "bogturtle");
        meat("per:1.5,dex:3", 400, 5, "boreworm");
        meat("per:2", 400, 5, "bullfinch");
        meat("str2:4,agi2:2", 400, 10, "cachalot");
        meat("con2:1,wil:2", 400, 5, "cavelouse");
        meat("wil:1", 400, 5, "caverat");
        meat("wil2:2,con2:2,per:2", 400, 10, "chasmconch");
        meat("dex2:1.5", 400, 5, "chevon", "goat");
        meat("dex:1", 400, 5, "chicken");
        meat("str:0.5,dex:1", 400, 5, "crab");
        meat("agi:1,cha:0.75,cha2:0.25", 400, 5, "crane");
        meat("agi:1,con:1", 400, 5, "duck");
        meat("int2:2,per:2", 400, Double.NaN, "eagleowl");
        meat("int:1", 400, 5, "fox");
        meat("con:1,con2:0.5", 400, 5, "garefowl");
        meat("per2:2,wil:3", 400, 5, "goldeneagle");
        meat("str2:3,per2:1.5,psy2:3", 400, Double.NaN, "goshawk");
        meat("agi2:1,dex:1", 400, 5, "greyseal");
        meat("con:0.75,con2:0.25", 400, 5, "hedgehog");
        meat("agi:2", 400, 5, "horse");
        meat("str:4,str2:2", 400, Double.NaN, "icebear");
        meat("int:2,cha:2", 400, 6.25, "jotunclam");
        meat("dex:1.5,dex2:1,wil:1.5", 400, 7.5, "lobster");
        meat("str:1,agi:1,int:2", 400, 5, "lynx");
        meat("dex:1", 400, 5, "magpie");
        meat("con:1,dex:1", 400, 6.25, "mallard");
        meat("str2:2,con:2", 400, 10, "mammoth");
        meat("per:1", 400, 5, "mole");
        meat("str:2,int:1,cha:1", 400, 5, "moose");
        meat("cha:1", 400, 5, "mutton", "sheep");
        meat("agi:1,agi2:1,con:1,con2:1", 400, 7.5, "narwhal");
        meat("agi2:4,int2:2", 400, 7.5, "orca");
        meat("dex2:1", 400, 5, "otter");
        meat("con:1.5,cha:0.75", 400, 5, "pelican");
        meat("str2:3,con2:3", 400, 5, "pork", "pig");
        meat("per2:2", 400, 7.5, "ptarmigan");
        meat("per2:2", 400, 7.5, "quail");
        meat("agi:1", 400, 5, "rabbit");
        meat("per2:1,dex:1", 400, 5, "reindeer");
        meat("agi:1", 400, 6.25, "rockdove");
        meat("agi:1,per:1", 400, 5, "roedeer");
        meat("dex:1", 400, 5, "seagull");
        meat("int:1", 400, 5, "squirrel");
        meat("agi2:1", 400, 5, "stoat");
        meat("per:1,per2:0.5", 400, 5, "swan");
        meat("per2:2,dex:1", 400, Double.NaN, "teimdeer");
        meat("str2:6,psy2:4", 800, 10, "troll");
        meat("per:2", 400, 5, "reddeer");
        meat("agi2:1,con:1", 400, 5, "walrus");
        meat("cha:1", 400, 5, "aurochs");
        meat("cha:1", 400, 5, "mouflon");
        meat("str:2", 400, 5, "boar");
        meat("agi:1,wil:1", 400, 5, "wildgoat");
        meat("agi:2", 400, 5, "wildhorse");
        meat("str2:1,agi:1", 400, 5, "wolf");
        meat("agi:1,dex:1", 400, 5, "wolverine");
        meat("con2:0.5,dex:1", 400, Double.NaN, "woodgrouse");
    }

    public static int kinds() {return MEAT.size();}

    /** One raw piece. Roasted, smoked and spitted meat keep the server tooltip. */
    public static FishFry.Meal preview(Iterable<String> layers, Double quality) {
        return preview(layers, quality, FoodHunger.World.WORLD16);
    }

    public static FishFry.Meal preview(Iterable<String> layers, Double quality, FoodHunger.World world) {
        boolean raw = false;
        Yield yield = null;
        if(layers != null) for(String name : layers) {
            if(FishFry.cooked(name)) return null;
            for(String base : RAW) if(base.equals(name)) raw = true;
            Yield species = MEAT.get(name);
            if(species != null) yield = species;
        }
        if(!raw || yield == null) return null;
        double q = FoodHunger.quality(quality);
        double factor = Math.sqrt(q / 10.0);
        double hunger = FoodHunger.roast(world, q, yield.ratio, yield.fep, yield.bonus);
        return new FishFry.Meal(1, yield.energy, hunger, scale(yield.fep, factor), scale(yield.bonus, factor), true);
    }

    private static double[] scale(double[] base, double factor) {
        double[] scaled = new double[base.length];
        for(int i = 0; i < base.length; i++) scaled[i] = base[i] * factor;
        return scaled;
    }

    private static void meat(String stats, int energy, double ratio, String... ids) {
        String[] key = {"str", "agi", "int", "con", "per", "cha", "dex", "wil", "psy"};
        double[] one = new double[key.length], two = new double[key.length];
        for(String part : stats.split(",")) {
            int colon = part.indexOf(':');
            String name = part.substring(0, colon);
            double value = Double.parseDouble(part.substring(colon + 1));
            boolean bonus = name.endsWith("2");
            if(bonus) name = name.substring(0, name.length() - 1);
            int index = -1;
            for(int i = 0; i < key.length; i++) if(key[i].equals(name)) index = i;
            if(index < 0) throw new IllegalArgumentException(part);
            (bonus ? two : one)[index] = value;
        }
        Yield yield = new Yield(energy, ratio, one, two);
        for(String id : ids) MEAT.put("gfx/invobjs/meat-" + id, yield);
    }

    private static final class Yield {
        final double energy, ratio;
        final double[] fep, bonus;
        Yield(double energy, double ratio, double[] fep, double[] bonus) {
            this.energy = energy; this.ratio = ratio; this.fep = fep; this.bonus = bonus;
        }
    }

    private MeatRoast() {}
}
