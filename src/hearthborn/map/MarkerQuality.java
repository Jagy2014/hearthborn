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
import java.util.*;

/**
 * The quality of a resource on the map, kept per map marker and written after its name: "Flint Wash (q44)". It comes from Inspect
 * when the resource tells it, and otherwise from what the resource gave: a piece with a quality that arrives in your inventory while you stand
 * at a resource you have just used (a clay pit gives Pit Clay of the quality of the pit).
 * The marker itself is not changed; the quality is a separate note that is kept between sessions.
 *
 * @author Jagy2014
 */
public final class MarkerQuality {
    private static final String STORE = "hearthborn/map/marker-quality";
    private static final int LIMIT = 4000;
    private static final Map<String, Double> notes = new LinkedHashMap<>();
    private static boolean loaded;
    private static volatile int captionRevision;

    public static int captionRevision() { return captionRevision; }
    private static final String NUMBER_STORE = "natural-resource-numbers";
    private static Map<String, Integer> numbers;

    public static MapFile.SMarker naturalMarker(Gob gob) {
        if(gob == null || gob.removed) return null;
        MiniMap.MarkerID id = gob.getattr(MiniMap.MarkerID.class);
        if(id == null || !(id.mark instanceof MapFile.SMarker)) return null;
        MapFile.SMarker marker = (MapFile.SMarker)id.mark;
        return hearthborn.ui.IconCategories.classify(marker.res.name) == hearthborn.ui.IconCategories.Category.RESOURCES
            ? marker : null;
    }

    public static synchronized String number(MapFile.SMarker marker) {
        String key = key(marker);
        loadNumbers();
        if(!numbers.containsKey(key)) {
            String prefix = marker.res.name + "|";
            int next = numbers.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .mapToInt(Map.Entry::getValue).max().orElse(0) + 1;
            numbers.put(key, next);
            captionRevision++;
            hearthborn.io.LocalStore.write(NUMBER_STORE, new HashMap<>(numbers));
        }
        return key;
    }

    private static void loadNumbers() {
        if(numbers != null) return;
        numbers = new HashMap<>();
        Object saved = hearthborn.io.LocalStore.read(NUMBER_STORE);
        if(saved instanceof Map<?, ?>) ((Map<?, ?>)saved).forEach((key, value) -> {
            if(key instanceof String && value instanceof Integer) numbers.put((String)key, (Integer)value);
        });
    }

    public static synchronized String numberedCaption(String key, String name) {
        return captionWithDetails(key, baseCaption(key, name));
    }

    private static synchronized String captionWithDetails(String key, String name) {
        if(key == null) return name;
        loadNumbers();
        Integer number = numbers.get(key);
        if(number != null) name += " " + number;
        load();
        Double quality = notes.get(key);
        return quality == null ? name : caption(name, quality);
    }

    public static String baseCaption(MapFile.Marker marker) {
        if(marker instanceof MapFile.SMarker && ((MapFile.SMarker)marker).customName)
            return marker.nm;
        return baseCaption(key(marker), marker.nm);
    }

    private static String baseCaption(String key, String name) {
        if(key == null) return name;
        // Saved automatic names may still be in English. Keep custom names intact.
        String icon;
        switch(name) {
            case "Abyssal Chasm":
            case "Абиссальная бездна": icon = "abyssalchasm"; break;
            case "Amber Wash": icon = "amberwash"; break;
            case "Ancient Windthrow": icon = "windthrow"; break;
            case "Anthill": icon = "anthill"; break;
            case "Badger Sett": icon = "badgerden"; break;
            case "Bat Cave": icon = "batcave"; break;
            case "Beaver Lodge": icon = "beaverdam"; break;
            case "Bee Dungeon": icon = "beehive"; break;
            case "Bird's Nest": icon = "birdsnest"; break;
            case "Cave Organ": icon = "caveorgan"; break;
            case "Coral Reef": icon = "coralreef"; break;
            case "Cave Clay": icon = "clay-cave"; break;
            case "Driftkelp": icon = "driftkelp"; break;
            case "Driftkelp Island": icon = "driftkelpisland"; break;
            case "Fallen Star":
            case "Meteorite": icon = "meteorite"; break;
            case "Foxhole": icon = "foxhole"; break;
            case "Geyser": icon = "geyser"; break;
            case "Guano Pile": icon = "guanopile"; break;
            case "Hidden Hollow": icon = "hiddenhollow"; break;
            case "Ice Spire": icon = "icespire"; break;
            case "Irminsul": icon = "irminsul"; break;
            case "Jotun Clam": icon = "jotunclam"; break;
            case "Jotun Mussel": icon = "jotunmussel"; break;
            case "Lilypad Lotus": icon = "lilypadlotus"; break;
            case "Loose Rock": icon = "looserock"; break;
            case "Monolith": icon = "monolith"; break;
            case "Rock Crystal":
            case "Горный хрусталь":
            case "Горный кристалл":
                if(key.startsWith("gfx/terobjs/mm/rockcrystal|"))
                    return hearthborn.L10n.resource("tooltip", "gfx/terobjs/mm/rockcrystal", "Rock Crystal");
                if(key.startsWith("gfx/terobjs/mm/crystalpatch|"))
                    return hearthborn.L10n.resource("tooltip", "gfx/terobjs/mm/crystalpatch", "Rock Crystal");
                return name;
            case "Seated Toad": icon = "gianttoad"; break;
            case "Stone Kist": icon = "stonekist"; break;
            case "Tidepool":
            case "Tide Pool": icon = "tidepool"; break;
            case "Wellspring": icon = "wellspring"; break;
            case "Flint Wash": icon = "flintwash"; break;
            case "Spawning Bed": icon = "spawningbed"; break;
            case "Burrow": icon = "burrow"; break;
            case "Clam Reef": icon = "clamreef"; break;
            case "Tarpit":
            case "Tar Pit": icon = "tarpit"; break;
            case "Heartwood Tree":
            case "Heartwood tree": icon = "woodheart"; break;
            case "Headwaters": icon = "headwaters"; break;
            case "Salt Basin": icon = "saltbasin"; break;
            case "Fairy Stone": icon = "fairystone"; break;
            case "Clay Pit": icon = "claypit"; break;
            case "Swirling Vortex": icon = "watervortex"; break;
            default: return name;
        }
        String resource = "gfx/terobjs/mm/" + icon;
        if(key.startsWith(resource + "|"))
            return hearthborn.L10n.resource("tooltip", resource, name);
        return name;
    }

    /** What identifies a marker: its resource and its place in the map segment. Null for markers that are not resources (flags). */
    static String key(MapFile.Marker marker) {
        if(!(marker instanceof MapFile.SMarker)) return null;
        return key(((MapFile.SMarker)marker).res.name, marker.seg, marker.tc);
    }

    static String key(String resource, long segment, Coord tile) {
        return resource + "|" + Long.toUnsignedString(segment, 16) + "|" + tile.x + "|" + tile.y;
    }

    /** The resource used last, and when. */
    private static java.lang.ref.WeakReference<Gob> touched;
    private static double touchedAt;
    private static TarSample tarSample;

    private static final class TarSample {
        final Gob barrel;
        final MapFile.SMarker pit;
        final double until = Utils.rtime() + WINDOW;

        TarSample(Gob barrel, MapFile.SMarker pit) {
            this.barrel = barrel;
            this.pit = pit;
        }
    }

    /** Capture the exact carried barrel at the attempt, before the server fills it. */
    public static synchronized void tarBarrel(GameUI gui, Gob pit) {
        MapFile.SMarker marker = naturalMarker(pit);
        if(gui == null || gui.map == null || marker == null || !letters(marker.res.name).equals("tarpit")) return;
        Gob player = gui.map.player();
        if(player == null || player.glob == null) return;
        for(Gob barrel : player.glob.oc.snapshot()) {
            Following follow = barrel.getattr(Following.class);
            if(follow == null || follow.tgt != player.id || barrel.removed) continue;
            if(tarSample != null && tarSample.until >= Utils.rtime() && tarSample.barrel == barrel
               && key(marker).equals(key(tarSample.pit))) return;
            if(hearthborn.storage.ContainerLook.emptyBarrel(barrel)) {
                beginTarSample(barrel, marker);
                return;
            }
            if(tarSample != null && tarSample.barrel == barrel) tarSample = null;
        }
    }

    static synchronized void beginTarSample(Gob barrel, MapFile.SMarker pit) {
        number(pit);
        tarSample = new TarSample(barrel, pit);
    }

    public static synchronized void vessel(Gob barrel, String contents, double quality) {
        if(tarSample == null) return;
        if(tarSample.until < Utils.rtime() || tarSample.barrel.removed) {
            tarSample = null;
            return;
        }
        if(tarSample.barrel != barrel || !isTar(contents) || !Double.isFinite(quality) || quality <= 0) return;
        put(key(tarSample.pit), quality);
        tarSample = null;
    }

    static boolean isTar(String contents) {
        return contents != null && ("Tar".equalsIgnoreCase(contents.trim()) || "Смола".equalsIgnoreCase(contents.trim()));
    }
    /** How long after using a resource its yield is still taken for the resource's quality, in seconds, and how close you must stand, in map units. */
    static final double WINDOW = 90, REACH = 44;

    /** A resource with a marker was clicked. */
    public static synchronized void touched(Gob gob) {
        if(gob == null || gob.getattr(MiniMap.MarkerID.class) == null) return;
        MapFile.SMarker marker = naturalMarker(gob);
        if(marker != null) number(marker);
        touched = new java.lang.ref.WeakReference<>(gob);
        touchedAt = Utils.rtime();
    }

    /**
     * Looks at an item that is new in the inventory. Returns true when the item has been judged and need not be looked at again.
     * The item counts when you stand at the resource you used last, and the item is made of that resource.
     */
    public static synchronized boolean item(GItem item) {
        Gob gob = touched == null ? null : touched.get();
        if(gob == null || gob.removed) return true;
        double age = Utils.rtime() - touchedAt;
        if(age > WINDOW) {touched = null; return true;}
        GameUI gui = item.getparent(GameUI.class);
        if(gui == null || gui.map == null || gui.map.player() == null) return true;
        if(gui.map.player().rc.dist(gob.rc) > REACH) return true;
        try {
            String node = hearthborn.util.GameObjects.resourceName(gob);
            if(node == null || item.res == null) return true;
            Resource resource = item.res.get();
            boolean bucket = resource.basename().equals("bucket-tar");
            if(!own(item, gui) && !(bucket && gui.vhand != null && gui.vhand.item == item)) return true;
            if(!matches(node, resource.name)) return true;
            Double quality = hearthborn.QualityOverlay.qualityOf(item);
            if(quality == null) return false;
            remember(gob, quality);
            return true;
        } catch(Loading pending) {
            return false;
        }
    }

    /** A bucket may stay on the cursor while the server changes its resource or tooltip. */
    public static synchronized boolean bucketSample(Indir<Resource> resource) {
        Gob pit = touched == null ? null : touched.get();
        if(pit == null || Utils.rtime() - touchedAt > WINDOW || resource == null) return false;
        MapFile.SMarker marker = naturalMarker(pit);
        if(marker == null || !letters(marker.res.name).equals("tarpit")) return false;
        try {
            String name = resource.get().basename();
            return name.equals("bucket") || name.equals("bucket-tar");
        } catch(Loading pending) {
            return true;
        }
    }

    /** An item of the player's main inventory, also inside a stack there. */
    private static boolean own(GItem item, GameUI gui) {
        Widget parent = item.parent;
        if(parent == null) return false;
        if(parent == gui.maininv) return true;
        if(parent.getClass().getName().equals("haven.res.ui.stackinv.ItemStack")) {
            GItem.ContentsWindow window = parent.getparent(GItem.ContentsWindow.class);
            return window != null && window.cont.parent == gui.maininv;
        }
        return false;
    }

    /** What resources give when the item's name does not begin like the resource's: a geyser gives brimstone. Names without separators. */
    private static final Map<String, String[]> GIVES = new HashMap<>();
    static {
        GIVES.put("geyser", new String[] {"brimstone"});
        GIVES.put("saltbasin", new String[] {"salt", "saltcrystal"});
        GIVES.put("abyssalchasm", new String[] {"foulsmoke"});
        GIVES.put("fairystone", new String[] {"fairyshroom"});
        GIVES.put("windthrow", new String[] {"ancientroot"});
        GIVES.put("guanopile", new String[] {"guano"});
        GIVES.put("tarpit", new String[] {"tar", "buckettar"});
        GIVES.put("crystalpatch", new String[] {"rockcrystal"});
        GIVES.put("amberwash", new String[] {"amber"});
        GIVES.put("flintwash", new String[] {"flint"});
    }

    /** Whether an item is made of a resource: "claypit" gives "clay-pit", "flintwash" gives "flint", "geyser" gives "brimstone". */
    static boolean matches(String node, String item) {
        String n = letters(node), i = letters(item);
        if(n.length() < 3 || i.length() < 3) return false;
        String[] gives = GIVES.get(n);
        if(gives != null) for(String name : gives) if(name.equals(i)) return true;
        return n.equals(i) || n.startsWith(i) || i.startsWith(n);
    }

    private static String letters(String resource) {
        if(resource == null) return "";
        String name = resource.substring(resource.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
        return name.replaceAll("[^a-z]", "");
    }

    /** Called when Inspect has told the quality of an object: the marker of that object, if it has one, gets the note. */
    public static void remember(Gob gob, double quality) {
        if(gob == null || !Double.isFinite(quality) || quality < 0) return;
        MiniMap.MarkerID id = gob.getattr(MiniMap.MarkerID.class);
        String key = id == null ? null : key(id.mark);
        if(key != null) put(key, quality);
    }

    static synchronized void put(String key, double quality) {
        load();
        Double known = notes.get(key);
        if(known != null && known == quality) return;
        notes.remove(key);
        notes.put(key, quality);
        captionRevision++;
        while(notes.size() > LIMIT) {
            Iterator<String> oldest = notes.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        Utils.setpref(STORE, encode(notes));
        if(numbers != null && numbers.containsKey(key)) hearthborn.timers.Timers.namesChanged();
    }

    /** The noted quality of the marker, or null. */
    public static synchronized Double of(MapFile.Marker marker) {
        String key = key(marker);
        if(key == null) return null;
        load();
        return notes.get(key);
    }

    /** The text drawn under the marker. */
    public static String caption(MapFile.Marker marker) {
        return captionWithDetails(key(marker), baseCaption(marker));
    }

    static String caption(String name, double quality) {
        return name + " (q" + format(quality) + ")";
    }

    /** 44 for 44.0, 44.5 for 44.5. */
    static String format(double quality) {
        double tenths = Math.round(quality * 10) / 10.0;
        return tenths == Math.rint(tenths) ? String.valueOf((long)tenths) : String.valueOf(tenths);
    }

    static String encode(Map<String, Double> notes) {
        StringBuilder raw = new StringBuilder();
        for(Map.Entry<String, Double> note : notes.entrySet()) {
            if(raw.length() > 0) raw.append(';');
            raw.append(note.getKey()).append('=').append(note.getValue());
        }
        return raw.toString();
    }

    static Map<String, Double> decode(String raw) {
        Map<String, Double> notes = new LinkedHashMap<>();
        if(raw == null) return notes;
        for(String part : raw.split(";")) {
            int eq = part.lastIndexOf('=');
            if(eq <= 0) continue;
            try {
                notes.put(part.substring(0, eq), Double.parseDouble(part.substring(eq + 1)));
            } catch(NumberFormatException ignored) {}
        }
        return notes;
    }

    private static void load() {
        if(loaded) return;
        loaded = true;
        notes.putAll(decode(Utils.getpref(STORE, "")));
    }

    private MarkerQuality() {}
}
