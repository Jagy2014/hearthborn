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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Remembers a cave floor per map segment and shows it on the map.
 *
 * @author Jagy2014
 */
public final class CaveLevels {
    private static final String SHOW = "hearthborn/map/cave-level";
    private static final String STORE = "hearthborn/map/cave-levels";
    private static final int MAX = 9;
    private static final Color[] PAL = {
        new Color(255, 214, 70), new Color(80, 200, 255), new Color(255, 128, 70),
        new Color(186, 120, 255), new Color(90, 220, 140), new Color(255, 110, 170)
    };
    private static final Map<Integer, Tex> labels = new HashMap<>();
    private static final Map<Integer, Tex> badges = new HashMap<>();
    private static final Map<Long, Boolean> caves = new HashMap<>();
    private static final Map<MiniMap, Area> hits = Collections.synchronizedMap(new WeakHashMap<MiniMap, Area>());
    private static final Map<String, Integer> stored = new HashMap<>();
    private static boolean loaded;
    private static int generation;
    private static long lastCave = Long.MIN_VALUE;
    private static int lastLevel;
    private static boolean fromSurface = true;

    private CaveLevels() {}

    public static boolean show() { return Utils.getprefb(SHOW, true); }
    public static void show(boolean on) { Utils.setprefb(SHOW, on); }
    public static int generation() { return generation; }

    public static void note(MiniMap map) {
        GameUI gui = map.getparent(GameUI.class);
        if(gui == null || gui.map == null || !map.file.lock.readLock().tryLock())
            return;
        MapFile.Grid grid;
        long seg;
        try {
            /* sessloc.tc is the world-to-segment offset, not where the player stands. */
            MiniMap.Location loc = new MiniMap.MapLocator(gui.map).locate(map.file);
            if(loc == null || loc.seg == null || loc.tc == null)
                return;
            seg = loc.seg.id;
            grid = loc.seg.grid(loc.tc.div(MCache.cmaps)).get();
        } catch(Loading e) {
            return;
        } finally {
            map.file.lock.readLock().unlock();
        }
        if(isCave(grid) || standing(gui)) {
            caves.put(seg, true);
            if(of(seg) == 0)
                set(seg, (!fromSurface && lastLevel > 0 && lastCave != seg) ? Math.min(MAX, lastLevel + 1) : 1);
            lastCave = seg;
            lastLevel = of(seg);
            fromSurface = false;
        } else if(isOverworld(grid) && !Boolean.TRUE.equals(caves.get(seg))) {
            caves.put(seg, false);
            fromSurface = true;
        }
    }

    public static void paint(MiniMap map, GOut g) {
        hits.remove(map);
        if(!show() || map.curloc == null || map.curloc.seg == null)
            return;
        long seg = map.curloc.seg.id;
        int level = of(seg);
        if(level <= 0 && !Boolean.TRUE.equals(caves.get(seg)))
            return;
        Tex tex = label(level);
        int covered = MapToolbar.below(map);
        int top = covered > 0 ? covered + UI.scale(2) : UI.scale(6);
        Coord at = Coord.of(UI.scale(6), top);
        g.chcolor(0, 0, 0, 140);
        g.frect(at.sub(UI.scale(3), UI.scale(1)), tex.sz().add(UI.scale(6), UI.scale(3)));
        g.chcolor();
        g.image(tex, at);
        hits.put(map, Area.sized(at.sub(UI.scale(3), UI.scale(1)), tex.sz().add(UI.scale(6), UI.scale(3))));
    }

    public static boolean click(MiniMap map, Coord c, int button) {
        Area hit = hits.get(map);
        if(!show() || hit == null || !hit.contains(c) || map.curloc == null || map.curloc.seg == null)
            return false;
        if(button != 1 && button != 3)
            return false;
        long seg = map.curloc.seg.id;
        int current = of(seg);
        int next = current <= 0 ? 1 : button == 3 ? (current <= 1 ? MAX : current - 1) : (current >= MAX ? 1 : current + 1);
        set(seg, next);
        caves.put(seg, true);
        lastCave = seg;
        lastLevel = next;
        fromSurface = false;
        labels.clear();
        return true;
    }

    public static void badge(GOut g, MiniMap.DisplayMarker marker) {
        if(!showFor(marker.m.seg))
            return;
        int level = of(marker.m.seg);
        Coord at = marker.sc.add(UI.scale(7), UI.scale(-7));
        int radius = UI.scale(7);
        g.chcolor(0, 0, 0, 200);
        g.fellipse(at, Coord.of(radius + 1, radius + 1));
        g.chcolor(color(level));
        g.fellipse(at, Coord.of(radius, radius));
        g.chcolor();
        g.aimage(badge(level), at, 0.5, 0.5);
    }

    public static String markerSuffix(long seg) {
        return showFor(seg) ? "  [" + of(seg) + "]" : "";
    }

    public static BufferedImage tip(long seg) {
        if(!showFor(seg))
            return null;
        int level = of(seg);
        return Utils.outline2(Text.render(caption(level), color(level)).img, Color.BLACK);
    }

    private static boolean showFor(long seg) {
        if(!show() || Boolean.FALSE.equals(caves.get(seg)))
            return false;
        return of(seg) > 0;
    }

    public static int of(long seg) {
        load();
        Integer value = stored.get(Long.toUnsignedString(seg, 16));
        if(value == null || value <= 0)
            return 0;
        return Math.min(MAX, value);
    }

    private static void set(long seg, int level) {
        load();
        String key = Long.toUnsignedString(seg, 16);
        if(level <= 0)
            stored.remove(key);
        else
            stored.put(key, level);
        generation++;
        StringBuilder raw = new StringBuilder();
        for(Map.Entry<String, Integer> entry : stored.entrySet()) {
            if(raw.length() > 0)
                raw.append(';');
            raw.append(entry.getKey()).append('=').append(entry.getValue());
        }
        Utils.setpref(STORE, raw.toString());
    }

    private static void load() {
        if(loaded)
            return;
        loaded = true;
        String raw = Utils.getpref(STORE, "");
        for(String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if(eq <= 0)
                continue;
            try {
                stored.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1)));
            } catch(NumberFormatException ignored) {}
        }
    }

    /** The floor under the player, when the saved map grid is not ready yet. */
    private static boolean standing(GameUI gui) {
        try {
            Gob player = gui.map.player();
            if(player == null || gui.ui == null || gui.ui.sess == null)
                return false;
            Coord tile = player.rc.floor(MCache.tilesz);
            Resource res = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(tile));
            return res != null && caveName(res.name);
        } catch(Loading e) {
            return false;
        }
    }

    public static boolean isCave(MapFile.DataGrid grid) {
        if(grid == null || grid.tilesets == null)
            return false;
        for(MapFile.TileInfo tile : grid.tilesets) {
            if(tile.res != null && caveName(tile.res.name))
                return true;
        }
        return false;
    }

    private static boolean caveName(String name) {
        if(name == null || name.startsWith("gfx/tiles/rocks/") || name.startsWith("gfx/tiles/paving/") || name.startsWith("gfx/tiles/ridges/"))
            return false;
        int slash = name.lastIndexOf('/');
        String base = slash < 0 ? name : name.substring(slash + 1);
        return base.equals("mine") || base.equals("cellar") || base.equals("gloomdark") || base.equals("shadehollow") || base.equals("warmdepth")
            || base.endsWith("cave") || base.endsWith("cavern") || base.contains("grotto");
    }

    private static boolean isOverworld(MapFile.DataGrid grid) {
        if(grid == null || grid.tilesets == null || isCave(grid))
            return false;
        for(MapFile.TileInfo tile : grid.tilesets) {
            String name = tile.res == null ? null : tile.res.name;
            if(name == null || name.isEmpty() || name.equals("gfx/tiles/notile") || name.equals("gfx/tiles/nil"))
                continue;
            if(name.startsWith("gfx/tiles/rocks/") || name.startsWith("gfx/tiles/paving/"))
                continue;
            return true;
        }
        return false;
    }

    private static String caption(int level) {
        return level > 0 ? L10n.format("Lv. %d", level) : L10n.tr("Lv. ?");
    }

    private static Color color(int level) {
        return level <= 0 ? Color.WHITE : PAL[(level - 1) % PAL.length];
    }

    private static Tex label(int level) {
        return labels.computeIfAbsent(level, value -> text(caption(value), color(value > 0 ? value : 1)));
    }

    private static Tex badge(int level) {
        return badges.computeIfAbsent(level, value -> text(Integer.toString(value), Color.WHITE));
    }

    private static Tex text(String value, Color color) {
        return new TexI(Utils.outline2(Text.render(value, color).img, Color.BLACK));
    }
}
