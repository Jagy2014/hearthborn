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

import haven.Coord;
import haven.MapWnd;
import haven.UI;
import haven.Utils;
import hearthborn.ui.HudInterfaceScale;
import hearthborn.ui.MagneticWindows;

/**
 * Separate remembered places for the compact minimap and the large map.
 *
 * @author Jagy2014
 */
public final class MapPlaces {
    private static final String MINI_C = "hearthborn/map/mini-c";
    private static final String MINI_SZ = "hearthborn/map/mini-sz";
    private static final String FULL_C = "hearthborn/map/full-c";
    private static final String FULL_SZ = "hearthborn/map/full-sz";

    private MapPlaces() {}

    /** Size used when the map window is created. Falls back to the last single place. */
    public static Coord size(boolean mini) {
        Coord saved = Utils.getprefc(mini ? MINI_SZ : FULL_SZ, null);
        if(saved != null) return saved;
        return Utils.getprefc("wndsz-map", UI.scale(new Coord(700, 500)));
    }

    /** Position used when the map window is created. Falls back to the last single place. */
    public static Coord position(boolean mini) {
        Coord saved = Utils.getprefc(mini ? MINI_C : FULL_C, null);
        if(saved != null) return saved;
        return Utils.getprefc("wndc-map", new Coord(50, 50));
    }

    /** Remember the place that is about to be covered by the other presentation. */
    public static void before(MapWnd map, boolean toCompact) {
        if(toCompact)
            save(FULL_C, FULL_SZ, map);
        else {
            save(MINI_C, MINI_SZ, map);
            MagneticWindows.hold(map);
        }
    }

    /** Apply the presentation that just became visible. */
    public static void after(MapWnd map, boolean toCompact) {
        if(toCompact)
            restore(map, MINI_C, MINI_SZ, true);
        else
            restore(map, FULL_C, FULL_SZ, false);
    }

    /** The close button hides the map. The compact control still switches to the minimap. */
    public static void close(MapWnd map) {
        Utils.setprefb("wndvis-map", false);
        map.hide();
    }

    /** Keep the current presentation's own place, in addition to the shared last-place keys. */
    public static void remember(MapWnd map) {
        save(map.deco == null ? MINI_C : FULL_C, map.deco == null ? MINI_SZ : FULL_SZ, map);
    }

    private static void save(String cKey, String szKey, MapWnd map) {
        Utils.setprefc(cKey, new Coord(map.c));
        Utils.setprefc(szKey, map.csz());
    }

    private static void restore(MapWnd map, String cKey, String szKey, boolean mini) {
        Coord size = Utils.getprefc(szKey, null);
        if(size == null && !mini) size = defaultFull(map);
        if(size != null) map.resize(size);
        Coord at = Utils.getprefc(cKey, null);
        if(at == null && !mini) at = centered(map);
        if(at != null) map.move(new Coord(at));
        if(mini) MagneticWindows.resume(map);
        map.fitOnScreen();
        MagneticWindows.uncover(map);
        map.storePlace();
    }

    private static Coord defaultFull(MapWnd map) {
        Coord chrome = map.sz.sub(map.csz());
        Coord room = map.parent.sz.sub(UI.scale(80, 80)).sub(chrome);
        Coord want = UI.scale(new Coord(700, 500));
        return Coord.of(
            Utils.clip(want.x, UI.scale(350), Math.max(UI.scale(350), room.x)),
            Utils.clip(want.y, UI.scale(255), Math.max(UI.scale(255), room.y)));
    }

    private static Coord centered(MapWnd map) {
        Coord outer = HudInterfaceScale.size(map);
        Coord parent = map.parent.sz;
        return Coord.of(Math.max(0, (parent.x - outer.x) / 2), Math.max(0, (parent.y - outer.y) / 2));
    }
}
