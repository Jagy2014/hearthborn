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
import hearthborn.L10n;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Tile catalog, saved selection and pulsing map mask; configured in MapSettingsWindow.
 *
 * @author Jagy2014
 */
public final class TerrainHighlight {
    private static final Set<String> selected = new TreeSet<>(Utils.getprefsl("hearthborn/map/terrain", new String[0]));
    private static final Properties names = hearthborn.l10n.Catalog.load("/hearthborn/l10n/resource-names.properties");
    private static final Map<String, String> PRODUCT = products();
    private static final Map<String, List<String>> catalog = new LinkedHashMap<>();
    public static long sequence;
    private static Set<String> questTiles = Collections.emptySet();
    private static Set<String> journalTiles = Collections.emptySet();

    public static Set<String> questTiles() { return questTiles; }
    public static void questTiles(Collection<String> tiles) {
        Set<String> next = Collections.unmodifiableSet(new HashSet<>(tiles));
        if (!next.equals(questTiles)) { questTiles = next; sequence++; }
    }

    public static void journalTiles(Collection<String> tiles) {
        Set<String> next = Set.copyOf(tiles);
        if(!next.equals(journalTiles)) {
            journalTiles = next;
            sequence++;
        }
    }

    static { load(); }

    public static boolean selected(String name) { return selected.contains(name); }
    public static boolean ore(String resource) { return product(resource) != null; }
    public static boolean empty() { return selected.isEmpty() && questTiles.isEmpty() && journalTiles.isEmpty(); }

    /** Existing tile catalog and preferences, shared with the map settings window. */
    public static Map<String,List<String>> catalog() {
        Map<String,List<String>> view=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> entry:catalog.entrySet())
            if(!entry.getKey().equals("All"))view.put(entry.getKey(),Collections.unmodifiableList(entry.getValue()));
        return Collections.unmodifiableMap(view);
    }

    public static String englishName(String resource) {
        return names.getProperty(resource,resource.substring(resource.lastIndexOf('/')+1));
    }

    public static String name(String resource) {
        String english = englishName(resource);
        String core = english.endsWith(" (paved)") ? english.substring(0, english.length() - " (paved)".length()) : english;
        String shown = L10n.text("biome", english);
        if(shown.equals(english)) shown = L10n.text("biome", core);
        if(resource.contains("/paving/")) {
            String mark = L10n.tr("paved");
            if(!shown.toLowerCase(Locale.ROOT).contains(mark.toLowerCase(Locale.ROOT)))
                shown = shown + " (" + mark + ")";
        }
        return annotate(shown, resource);
    }

    public static BufferedImage image(MapFile.DataGrid grid) {
        Coord maps = MCache.cmaps;
        BufferedImage image = TexI.mkbuf(maps);
        if(grid == null || grid.tilesets == null) return image;
        byte[] mark = new byte[maps.x * maps.y];
        for(int y = 0; y < maps.y; y++) {
            for(int x = 0; x < maps.x; x++) {
                int id = grid.gettile(Coord.of(x, y));
                if(id < 0 || id >= grid.tilesets.length) continue;
                MapFile.TileInfo info = grid.tilesets[id];
                if(info == null || info.res == null) continue;
                String name = info.res.name;
                byte color = (byte)((questTiles.contains(name) || journalTiles.contains(name)) ? 2 : selected(name) ? 1 : 0);
                if(color == 0) continue;
                for(int dy = -1; dy <= 1; dy++) {
                    for(int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if(nx >= 0 && ny >= 0 && nx < maps.x && ny < maps.y)
                            mark[ny * maps.x + nx] = (byte)Math.max(mark[ny * maps.x + nx], color);
                    }
                }
            }
        }
        for(int i = 0; i < mark.length; i++)
            if(mark[i] != 0) image.setRGB(i % maps.x, i / maps.x, mark[i] == 2 ? 0xb6ffd251 : 0xffff00ff);
        return image;
    }

    /** Same pulse as before: alpha breathes between 100 and 255 once a second. */
    public static int pulse() {
        double wave = Math.sin(Math.PI * ((System.currentTimeMillis() % 1000) / 1000.0));
        return (int)(100 + 155 * wave);
    }

    public static void choose(Collection<String> resources, boolean on) {
        boolean changed = on ? selected.addAll(resources) : selected.removeAll(resources);
        if(!changed) return;
        sequence++;
        Utils.setprefsl("hearthborn/map/terrain", selected);
    }

    public static String annotate(String tip, String resource) {
        String product = product(resource);
        if(product == null || tip == null || tip.isEmpty()) return tip;
        String label = L10n.tr(product);
        String suffix = " (" + label + ")";
        String lower = tip.toLowerCase(Locale.ROOT);
        if(tip.endsWith(suffix) || lower.contains("(" + label.toLowerCase(Locale.ROOT) + ")") || lower.contains("(" + product + ")"))
            return tip;
        return tip + suffix;
    }

    private static String product(String resource) {
        int slash = resource.lastIndexOf('/');
        String key = slash < 0 ? resource : resource.substring(slash + 1);
        String product = PRODUCT.get(key);
        if(product != null) return product;
        int dash = key.lastIndexOf('-');
        if(dash <= 0) return null;
        product = PRODUCT.get(key.substring(dash + 1));
        return product != null ? product : PRODUCT.get(key.substring(0, dash));
    }

    private static Map<String, String> products() {
        Map<String, String> map = new HashMap<>();
        String iron = "cast iron", copper = "copper", tin = "tin", lead = "lead";
        String silver = "silver", gold = "gold", coal = "coal", mercury = "mercury";
        put(map, iron, "hematite", "magnetite", "limonite", "ilmenite");
        put(map, copper, "malachite", "chalcopyrite", "cuprite", "peacockore");
        put(map, tin, "cassiterite");
        put(map, lead, "galena", "leadglance");
        put(map, silver, "argentite", "hornsilver");
        put(map, gold, "nagyagite", "petzite", "sylvanite");
        put(map, coal, "blackcoal");
        put(map, mercury, "cinnabar");
        return map;
    }

    private static void put(Map<String, String> map, String product, String... keys) {
        for(String key : keys) map.put(key, product);
    }

    private static void load() {
        List<String> all = new ArrayList<>();
        catalog.put("All", all);
        String json;
        try(InputStream in = TerrainHighlight.class.getResourceAsStream("tile-highlight.json")) {
            if(in == null) return;
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while((n = in.read(tmp)) >= 0) buf.write(tmp, 0, n);
            json = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch(java.io.IOException e) {
            return;
        }
        int at = 0;
        while(true) {
            int key = json.indexOf('"', at);
            if(key < 0) break;
            int keyEnd = json.indexOf('"', key + 1);
            int list = json.indexOf('[', keyEnd);
            int listEnd = json.indexOf(']', list);
            if(keyEnd < 0 || list < 0 || listEnd < 0) break;
            String category = json.substring(key + 1, keyEnd);
            List<String> items = new ArrayList<>();
            int quote = list;
            while(true) {
                int start = json.indexOf('"', quote + 1);
                if(start < 0 || start > listEnd) break;
                int end = json.indexOf('"', start + 1);
                if(end < 0 || end > listEnd) break;
                items.add(json.substring(start + 1, end));
                quote = end;
            }
            catalog.put(category, items);
            all.addAll(items);
            at = listEnd + 1;
        }
    }

    private TerrainHighlight() {}

}
