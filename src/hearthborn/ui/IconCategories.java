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
import java.util.Arrays;

/**
 * Display filters remain independent of each icon's saved visibility and notifications.
 *
 * @author Jagy2014
 */
public final class IconCategories {
    public enum Category {
        ALL("All","all"),ANIMALS("Animals","kritters"),DOMESTIC("Domestic animals","domestic"),HERBS("Herbs","herbs"),
        ORES("Ores","ores"),ROCKS("Rocks","rocks"),TREES("Trees","trees"),
        BUSHES("Bushes","bushes"),FORAGEABLES("Forageables","forageables"),SIEGE("Siege machines","siege"),VEHICLES("Vehicles and movables","vehicles"),RESOURCES("Localized resources","resources"),OTHER("Other","other");
        public final String title,id;
        Category(String title,String id){this.title=title;this.id=id;}
        public boolean enabled(){return Utils.getprefb("hearthborn/icons/category/"+id,true);}
        public void enabled(boolean value){Utils.setprefb("hearthborn/icons/category/"+id,value);}
        public boolean matches(GobIcon.Setting setting){Category found=classify(setting.res.name);return this==ALL||this==found||this==FORAGEABLES&&found==HERBS;}
    }
private static final String[] ANIMAL_PATHS = {
	    "/kritter/",
	    "/invobjs/bunny",
	    "/invobjs/bogturtle",
	    "/invobjs/cavecentipede",
	    "/invobjs/cavemoth",
	    "/invobjs/dragonfly",
	    "/invobjs/forestlizard",
	    "/invobjs/forestsnail",
	    "/invobjs/frog",
	    "/invobjs/grasshopper",
	    "/invobjs/grub",
	    "/invobjs/crab",
	    "/invobjs/firefly",
	    "/invobjs/hen",
	    "/invobjs/jellyfish",
	    "/invobjs/ladybug",
	    "/invobjs/magpie",
	    "/invobjs/mallard",
	    "/invobjs/mole",
	    "/invobjs/monarchbutterfly",
	    "/invobjs/moonmoth",
	    "/invobjs/ptarmigan",
	    "/invobjs/quail",
	    "/invobjs/rabbit",
	    "/invobjs/rat",
	    "/invobjs/rockdove",
	    "/invobjs/rooster",
	    "/invobjs/sandflea",
	    "/invobjs/seagull",
	    "/invobjs/silkmoth",
	    "/invobjs/squirrel",
	    "/invobjs/stagbeetle",
	    "/invobjs/swan",
	    "/invobjs/toad",
	    "/invobjs/waterstrider",
	    "/invobjs/woodgrouse",
	    "/invobjs/bayshrimp",
	    "/invobjs/brimstonebutterfly",
	    "/invobjs/bullfinch",
	    "/invobjs/cranefly",
	    "/invobjs/dumbledore",
	    "/invobjs/earthworm",
	    "/invobjs/irrbloss",
	    "/invobjs/lobster",
	    "/invobjs/springbumblebee",
	    "/invobjs/tick",
	    "/invobjs/woodscorpion",
	    "/invobjs/woodworm",
	};
	
	private static final String[] HERB_PATHS = {
	    "/invobjs/herbs/",
	    "/invobjs/small/bladderwrack",
	    "/invobjs/small/snapdragon",
	    "/invobjs/small/thornythistle",
	    "/invobjs/small/tangledbramble",
	    "/invobjs/small/yulestar",
	    "/invobjs/small/yulelights",
	    "/invobjs/whirlingsnowflake",
	    "/invobjs/champignon-small",
	    "/invobjs/clay-gray",
	};
	
	private static final String[] ORE_PATHS = {
	    "gfx/invobjs/argentite",
	    "gfx/invobjs/blackcoal",
	    "gfx/invobjs/cassiterite",
	    "gfx/invobjs/chalcopyrite",
	    "gfx/invobjs/cinnabar",
	    "gfx/invobjs/coal",
	    "gfx/invobjs/cuprite",
	    "gfx/invobjs/galena",
	    "gfx/invobjs/hematite",
	    "gfx/invobjs/hornsilver",
	    "gfx/invobjs/ilmenite",
	    "gfx/invobjs/leadglance",
	    "gfx/invobjs/limonite",
	    "gfx/invobjs/magnetite",
	    "gfx/invobjs/malachite",
	    "gfx/invobjs/nagyagite",
	    "gfx/invobjs/peacockore",
	    "gfx/invobjs/petzite",
	    "gfx/invobjs/sylvanite",
	};
	private static final String[] ROCK_PATHS = {
	    "gfx/invobjs/alabaster",
	    "gfx/invobjs/apatite",
	    "gfx/invobjs/arkose",
	    "gfx/invobjs/basalt",
	    "gfx/invobjs/breccia",
	    "gfx/invobjs/chert",
	    "gfx/invobjs/corund",
	    "gfx/invobjs/diabase",
	    "gfx/invobjs/diorite",
	    "gfx/invobjs/dolomite",
	    "gfx/invobjs/eclogite",
	    "gfx/invobjs/feldspar",
	    "gfx/invobjs/flint",
	    "gfx/invobjs/fluorospar",
	    "gfx/invobjs/gabbro",
	    "gfx/invobjs/gneiss",
	    "gfx/invobjs/granite",
	    "gfx/invobjs/graywacke",
	    "gfx/invobjs/greenschist",
	    "gfx/invobjs/halite",
	    "gfx/invobjs/hornblende",
	    "gfx/invobjs/jasper",
	    "gfx/invobjs/jasper",
	    "gfx/invobjs/kyanite",
	    "gfx/invobjs/limestone",
	    "gfx/invobjs/marble",
	    "gfx/invobjs/mica",
	    "gfx/invobjs/microlite",
	    "gfx/invobjs/olivine",
	    "gfx/invobjs/orthoclase",
	    "gfx/invobjs/pegmatite",
	    "gfx/invobjs/porphyry",
	    "gfx/invobjs/pumice",
	    "gfx/invobjs/quartz",
	    "gfx/invobjs/rhyolite",
	    "gfx/invobjs/sandstone",
	    "gfx/invobjs/schist",
	    "gfx/invobjs/serpentine",
	    "gfx/invobjs/slate",
	    "gfx/invobjs/soapstone",
	    "gfx/invobjs/sodalite",
	    "gfx/invobjs/sunstone",
	    "gfx/invobjs/zincspar",
	    "gfx/invobjs/lavarock",
	};
	
    private static boolean leaf(String name,String... names){
        String base=name.substring(name.lastIndexOf('/')+1);
        for(String candidate:names)if(base.equals(candidate))return true;
        return false;
    }
    public static Category classify(String name) {
        if(leaf(name,"catapult","bram","batteringram","wball","wreckingball","wreckingball-fold","archerytower","warflag"))return Category.SIEGE;
        if(name.startsWith("gfx/terobjs/vehicle/")||leaf(name,"rowboat","dugout","coracle","knarr","snekkja","wagon","cart","wheelbarrow","raft","raft-seg","spark","primsled","finesled","sleigh","sled","wskisitem","skis-wilderness","skis"))return Category.VEHICLES;
        if(leaf(name,"tarpit","wellspring","claypit","clay-cave","saltbasin","geyser","headwaters","amberwash","flintwash","meteorite","guanopile","clamreef","coralreef","driftkelp","driftkelpisland","spawningbed","tidepool","looserock","abyssalchasm","windthrow","fairystone","caveorgan","woodheart","icespire","jotunmussel","jotunclam","lilypadlotus","rockcrystal","crystalpatch"))return Category.RESOURCES;
        if(name.contains("/herbs/")){
            String base=name.substring(name.lastIndexOf('/')+1);
            if(base.contains("berry")||base.contains("bolete")||base.contains("mushroom")||base.contains("puffball")||base.contains("trumpet")||base.contains("blewit")||base.contains("morel")||base.contains("oyster")||leaf(name,"chantrelle","chanterelle","ambergris","flotsam","cavebulb","cattailroots","frogspawn","commonstarfish","giantpuffball","ghostapple","spindlytaproot"))return Category.FORAGEABLES;
        }

        if(name.contains("mm/trees/"))return Category.TREES;
        if(name.startsWith("radar/tame/")||name.contains("teimdeer"))return Category.DOMESTIC;
        if(Arrays.stream(ANIMAL_PATHS).anyMatch(name::contains))return Category.ANIMALS;
        if(Arrays.stream(ROCK_PATHS).anyMatch(name::contains))return Category.ROCKS;
        if(Arrays.stream(ORE_PATHS).anyMatch(name::contains))return Category.ORES;
        if(Arrays.stream(HERB_PATHS).anyMatch(name::contains))return Category.HERBS;
        if(name.contains("mm/bushes/"))return Category.BUSHES;
        return Category.OTHER;
    }
    public static boolean visible(GobIcon.Setting setting){return setting.show;}
    public static boolean shown(GobIcon.Settings settings,Category category){
        boolean any=false;for(GobIcon.Setting setting:settings.settings.values())if(category.matches(setting)){any=true;if(!setting.show)return false;}return any;
    }
    public static void shown(GobIcon.Settings settings,Category category,boolean show){
        for(GobIcon.Setting setting:settings.settings.values())if(category.matches(setting))setting.show=show;
        settings.dsave();
    }
    private IconCategories() {}
}
