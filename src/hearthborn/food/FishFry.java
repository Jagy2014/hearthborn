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

import haven.*;
import haven.resutil.FoodInfo;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Roast-filet preview. FEPs scale with sqrt(q/10); W16/W16.2 hunger uses weighted FEPs and the fourth root.
 *
 * @author Jagy2014
 */
public final class FishFry {
    public static final int STR = 0, AGI = 1, INT = 2, CON = 3, PER = 4, CHA = 5, DEX = 6, WIL = 7, PSY = 8;
    private static final String[] KEY = {"str", "agi", "int", "con", "per", "cha", "dex", "wil", "psy"};
    private static final String[] FEV = {"str", "agi", "int", "con", "prc", "csm", "dex", "wil", "psy"};
    private static final String[] ATTR = {"gfx/hud/chr/str", "gfx/hud/chr/agi", "gfx/hud/chr/int", "gfx/hud/chr/con", "gfx/hud/chr/prc", "gfx/hud/chr/csm", "gfx/hud/chr/dex", "gfx/hud/chr/wil", "gfx/hud/chr/psy"};
    private static final String[] LABEL = {"Strength", "Agility", "Intelligence", "Constitution", "Perception", "Charisma", "Dexterity", "Will", "Psyche"};
    private static final Map<String, Yield> FISH = new LinkedHashMap<>();
    private static final String RAW_FILET = "gfx/invobjs/meat-filet";

    // FEP/hunger tiers cross-checked against Kitten Rider Cookbook observations, 2026-11-12.
    // Source and attribution: docs/features/interface.en.md. NaN means no verified tier.
    static {
        fish("abyssgazer", 2, 125, 5, "per:2,per2:2,psy:2");
        fish("asp", 2, 125, 5, "dex:4");
        fish("bass", 2, 125, 5, "per:1,wil:2");
        fish("bream", 1, 125, 5, "int:3,cha:2");
        fish("brill", 1, 125, 5, "str:2,dex:2");
        fish("burbot", 2, 125, 5, "con:2,cha:1");
        fish("carp", 2, 125, 5, "con:2,per:1,cha:2");
        fish("catfish", 3, 125, 5, "agi:1,int:2,psy:2");
        fish("cavelacanth", 2, 125, 5, "int:3,per2:3");
        fish("cavesculpin", 1, 125, 5, "con2:2,wil:3");
        fish("chub", 1, 60, 5, "int:1,cha:1");
        fish("cod", 2, 125, 5, "int:2,per:3");
        fish("eel", 2, 125, 6.25, "agi:4");
        fish("grayling", 2, 125, 5, "agi:1,per:2");
        fish("haddock", 2, 125, 5, "agi:2,per:3");
        fish("herring", 1, 125, 5, "int:1,dex:1");
        fish("ide", 1, 125, 5, "dex:2,psy:0.5");
        fish("lavaret", 2, 125, 5, "agi:1,con:3");
        fish("mackerel", 1, 125, 5, "int:3");
        fish("mullet", 1, 125, 5, "str:3,per:2");
        fish("paleghostfish", 1, 125, 5, "int:2,cha:2,psy2:1");
        fish("perch", 1, 125, 5, "int:4");
        fish("pike", 2, 125, 5, "str:2,int:2,con:1");
        fish("plaice", 1, 125, 5, "int:1,per:3");
        fish("pomfret", 1, 125, 5, "int:2,wil:1");
        fish("roach", 1, 60, 5, "int:1,dex:1");
        fish("rosefish", 2, 125, 5, "cha:2,wil:2");
        fish("ruffe", 1, 125, 5, "cha:3");
        fish("saithe", 2, 125, 5, "agi:2,int:2");
        fish("salmon", 2, 125, 6.25, "int:2,cha:3");
        fish("silverbream", 1, 60, 5, "int:2");
        fish("smelt", 1, 60, 5, "cha:1,dex:1");
        fish("sturgeon", 3, 125, 6.25, "str:3,psy:1");
        fish("tench", 1, 125, 5, "int:2,con:1,per:1");
        fish("trout", 2, 125, 6.25, "str:2,per:1.5,dex:1.5");
        fish("whiting", 1, 125, 5, "cha:1,wil:2");
        fish("zander", 2, 125, 5, "int:3");
        fish("zope", 1, 125, 5, "int:1,per:2");
    }

    public static final class Meal {
        public final int filets;
        public final double energy, hunger;
        public final double[] fep, bonus;
        public final boolean meat;

        Meal(int filets, double energy, double hunger, double[] fep, double[] bonus) {
            this(filets, energy, hunger, fep, bonus, false);
        }

        Meal(int filets, double energy, double hunger, double[] fep, double[] bonus, boolean meat) {
            this.filets = filets;
            this.energy = energy;
            this.hunger = hunger;
            this.fep = fep;
            this.bonus = bonus;
            this.meat = meat;
        }

        /** Effective FEP sum, matching the server's FoodInfo.sev and efficiency readout. */
        public double total() {
            double sum = 0;
            for(double value : fep) sum += value;
            for(double value : bonus) sum += 2 * value;
            return sum;
        }
    }

    public static int kinds() {return FISH.size();}

    /** Same +1/+2 labels the server prints on a roasted piece. */
    public static String statName(int stat, boolean bonus) {
        String path = "gfx/hud/chr/fev/" + FEV[stat] + (bonus ? "2" : "");
        return L10n.resource("tooltip", path, LABEL[stat] + (bonus ? " +2" : " +1"));
    }

    /** Null when the resource is not a known raw fish. Missing quality is treated as 10. */
    public static Meal meal(String resource, Double quality) {
        return meal(resource, quality, FoodHunger.World.WORLD16);
    }

    public static Meal meal(String resource, Double quality, FoodHunger.World world) {
        Yield yield = FISH.get(resource);
        if(yield == null) return null;
        double q = FoodHunger.quality(quality);
        double factor = Math.sqrt(q / 10.0);
        double hunger = FoodHunger.roast(world, q, yield.ratio, yield.fep, yield.bonus);
        return new Meal(yield.filets, yield.energy, hunger, scale(yield.fep, factor), scale(yield.bonus, factor));
    }

    /** One raw filet. Layers are the meat sprite's resources; roasted and dried bases are left to the server. */
    public static Meal filet(Iterable<String> layers, Double quality) {
        return filet(layers, quality, FoodHunger.World.WORLD16);
    }

    public static Meal filet(Iterable<String> layers, Double quality, FoodHunger.World world) {
        boolean raw = false;
        String species = null;
        if(layers != null) for(String name : layers) {
            if(RAW_FILET.equals(name)) raw = true;
            if(cooked(name)) return null;
            String fish = fishResource(name);
            if(fish != null) species = fish;
        }
        if(!raw || species == null) return null;
        Meal whole = meal(species, quality, world);
        return new Meal(1, whole.energy, whole.hunger, whole.fep, whole.bonus);
    }

    public static void add(GItem item, List<ItemInfo> info) {
        if(QualityOverlay.stacked(item)) {
            Double quality = QualityOverlay.stackQuality(item);
            if(item.contents != null) for(Widget child : item.contents.children()) {
                if(child instanceof GItem && preview((GItem)child, quality, info, item)) return;
            }
            return;
        }
        preview(item, QualityOverlay.qualityOf(info), info, item);
    }

    private static boolean preview(GItem source, Double quality, List<ItemInfo> info, GItem owner) {
        String resource = source.resource().name;
        FoodHunger.World world = FoodHunger.World.of(owner);
        Meal meal = meal(resource, quality, world);
        if(meal == null && "gfx/invobjs/meat".equals(resource)) {
            List<String> layers = layers(source);
            meal = filet(layers, quality, world);
            if(meal == null) meal = MeatRoast.preview(layers, quality, world);
        }
        if(meal == null) return false;
        info.add(new Tip(owner, meal));
        return true;
    }

    static boolean cooked(String name) {
        return "gfx/invobjs/meat-rfilet".equals(name) || "gfx/invobjs/meat-dfilet".equals(name)
            || "gfx/invobjs/meat-sfilet".equals(name) || "gfx/invobjs/meat-smoke".equals(name)
            || "gfx/invobjs/meat-roast".equals(name) || "gfx/invobjs/meat-smoked".equals(name)
            || "gfx/invobjs/meat-spitroast".equals(name);
    }

    private static String fishResource(String name) {
        String id = null;
        if(name.startsWith("gfx/invobjs/meat-")) id = name.substring("gfx/invobjs/meat-".length());
        else if(name.startsWith("gfx/invobjs/small/fish-")) id = name.substring("gfx/invobjs/small/fish-".length());
        else if(name.startsWith("gfx/invobjs/fish-")) return FISH.containsKey(name) ? name : null;
        if(id == null) return null;
        String full = "gfx/invobjs/fish-" + id;
        return FISH.containsKey(full) ? full : null;
    }

    private static List<String> layers(GItem item) {
        List<String> names = new ArrayList<>();
        collect(item.spr(), names, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()), 0);
        return names;
    }

    private static void collect(Object value, List<String> names, Set<Object> seen, int depth) {
        if(value == null || depth > 6 || !seen.add(value)) return;
        if(value instanceof Resource) {
            names.add(((Resource)value).name);
            return;
        }
        if(value instanceof Indir) {
            collect(((Indir<?>)value).get(), names, seen, depth + 1);
            return;
        }
        if(value instanceof Iterable) {
            for(Object item : (Iterable<?>)value) collect(item, names, seen, depth + 1);
            return;
        }
        if(value instanceof Object[]) {
            for(Object item : (Object[])value) collect(item, names, seen, depth + 1);
            return;
        }
        if(value instanceof Widget || value instanceof String || value instanceof Number || value instanceof Message || value.getClass().getName().startsWith("java."))
            return;
        for(Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for(Field field : type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    collect(field.get(value), names, seen, depth + 1);
                } catch(Loading loading) {
                    throw loading;
                } catch(IllegalAccessException | RuntimeException ignored) {
                    // A changed sprite layout should omit the preview, not break the item.
                }
            }
        }
    }

    private static double[] scale(double[] base, double factor) {
        double[] scaled = new double[base.length];
        for(int i = 0; i < base.length; i++) scaled[i] = base[i] * factor;
        return scaled;
    }

    private static void fish(String id, int filets, int energy, double ratio, String stats) {
        double[] one = new double[KEY.length], two = new double[KEY.length];
        for(String part : stats.split(",")) {
            int colon = part.indexOf(':');
            String name = part.substring(0, colon);
            double value = Double.parseDouble(part.substring(colon + 1));
            boolean bonus = name.endsWith("2");
            if(bonus) name = name.substring(0, name.length() - 1);
            int index = -1;
            for(int i = 0; i < KEY.length; i++) if(KEY[i].equals(name)) index = i;
            if(index < 0) throw new IllegalArgumentException(id + " " + part);
            (bonus ? two : one)[index] = value;
        }
        FISH.put("gfx/invobjs/fish-" + id, new Yield(filets, energy, ratio, one, two));
    }

    private static final class Yield {
        final int filets;
        final double energy, ratio;
        final double[] fep, bonus;
        Yield(int filets, double energy, double ratio, double[] fep, double[] bonus) {
            this.filets = filets; this.energy = energy; this.ratio = ratio; this.fep = fep; this.bonus = bonus;
        }
    }

    private static final class Tip extends ItemInfo.Tip {
        final Meal meal;
        Tip(Owner owner, Meal meal) {super(owner); this.meal = meal;}
        public int order() {return 120;}

        public void layout(Layout layout) {
            String title = meal.meat ? L10n.text("tooltip", "Roast meat")
                : meal.filets == 1 ? L10n.text("tooltip", "Roast filet")
                : String.format(Locale.ROOT, L10n.text("tooltip", "%d roast filets, each"), meal.filets);
            layout.cmp.add(RichText.render("$b{" + title + "}", 0).img, Coord.of(0, layout.cmp.sz.y));
            String head = String.format("%s: $col[128,128,255]{%s%%}, %s: $col[255,192,128]{%s\u2030}",
                L10n.text("tooltip", "Energy"), Utils.odformat2(meal.energy, 2),
                L10n.text("tooltip", "Hunger"), Double.isFinite(meal.hunger) ? Utils.odformat2(meal.hunger, 2) : "—");
            layout.cmp.add(RichText.render(head, 0).img, Coord.of(0, layout.cmp.sz.y));
            points(layout, meal.fep, false);
            points(layout, meal.bonus, true);
            if(meal.total() > 0) {
                String total = String.format("%s: $col[128,192,255]{%s}",
                    L10n.text("tooltip", "Total"), Utils.odformat2(meal.total(), 2));
                if(meal.hunger > 0)
                    total += String.format(" ($col[128,192,255]{%s}/\u2030 %s)",
                        Utils.odformat2(meal.total() / meal.hunger, 2), L10n.text("tooltip", "hunger"));
                layout.cmp.add(RichText.render(total, 0).img, Coord.of(UI.scale(5), layout.cmp.sz.y));
            }
            note(layout, "Fresh off the fire increases food points and hunger by 25% for about half an hour.");
            note(layout, "Roast quality is limited by Perception and Survival.");
            if(!Double.isFinite(meal.hunger)) note(layout, "No verified hunger estimate for this world or food.");
            note(layout, "Data: cookbook.kittenrider.com (CC BY 4.0).");
        }

        private void points(Layout layout, double[] values, boolean bonus) {
            for(int i = 0; i < values.length; i++) {
                if(values[i] == 0) continue;
                String name = statName(i, bonus);
                BufferedImage icon = icon("gfx/hud/chr/fev/" + FEV[i] + (bonus ? "2" : ""));
                Color color = color("gfx/hud/chr/fev/" + FEV[i] + (bonus ? "2" : ""));
                BufferedImage line = RichText.render(String.format("%s: %s{%s}", name, RichText.Parser.col2a(color), Utils.odformat2(values[i], 2)), 0).img;
                layout.cmp.add(icon == null ? line : ItemInfo.catimgsh(5, icon, line), Coord.of(UI.scale(5), layout.cmp.sz.y));
            }
        }

        private void note(Layout layout, String english) {
            layout.cmp.add(RichText.render("$col[190,190,170]{" + L10n.text("tooltip", english) + "}", 0).img, Coord.of(0, layout.cmp.sz.y));
        }

        private Resource event(String path) {
            return Resource.remote().load(path).get();
        }

        private BufferedImage icon(String path) {
            Resource res = event(path);
            Resource.Image image = res.layer(Resource.imgc);
            if(image == null) return null;
            return PUtils.convolve(image.img, FoodInfo.Event.imgsz, CharWnd.iconfilter);
        }

        private Color color(String path) {
            BAttrWnd.FoodMeter.Event event = event(path).layer(BAttrWnd.FoodMeter.Event.class);
            return event == null ? Color.WHITE : Utils.blendcol(event.col, Color.WHITE, 0.5);
        }
    }

    private FishFry() {}
}
