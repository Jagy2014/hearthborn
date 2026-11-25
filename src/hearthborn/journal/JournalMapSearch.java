/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.journal;

import haven.Button;
import haven.Coord;
import haven.GameUI;
import haven.MapWnd;
import haven.Text;
import haven.UI;
import haven.Widget;
import hearthborn.L10n;
import hearthborn.gamedata.Entity;
import hearthborn.map.TerrainHighlight;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A temporary habitat preview, independent of quest search and saved map settings. */
final class JournalMapSearch extends Widget {
    private final Button reopen;
    private final Button stop;
    private final int nameWidth;

    static Set<String> tiles(Entity entity) {
        if(entity == null || !("forageables/herbs".equals(entity.subcategory) ||
                                "forageables/mushrooms".equals(entity.subcategory))) return Set.of();
        Object habitats = entity.attributes.get("habitats");
        if(!(habitats instanceof List<?>)) return Set.of();
        Set<String> names = new HashSet<>();
        for(Object habitat : (List<?>)habitats)
            if(habitat instanceof String) names.add(key((String)habitat));
        Set<String> result = new HashSet<>();
        for(String tile : TerrainHighlight.catalog().getOrDefault("Natural", List.of())) {
            if(names.contains(key(tile)) || names.contains(key(TerrainHighlight.englishName(tile))))
                result.add(tile);
        }
        return result;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    static boolean show(GameUI gui, Entity entity, Set<String> tiles) {
        if(gui == null || gui.mapfile == null || tiles.isEmpty()) return false;
        MapWnd map = gui.mapfile;
        JournalMapSearch previous = map.getchild(JournalMapSearch.class);
        if(previous != null) previous.destroy();
        JournalMapSearch chip = map.add(new JournalMapSearch(gui, entity));
        chip.z(100);
        chip.presize();
        TerrainHighlight.journalTiles(tiles);
        map.show();
        map.raise();
        return true;
    }

    private JournalMapSearch(GameUI gui, Entity entity) {
        String name = entity.text(L10n.language()).name;
        nameWidth = Math.max(UI.scale(95), Text.std.strsize(name).x + UI.scale(24));
        reopen = add(new Button(nameWidth, name, false, () -> GameJournal.openEntry(gui, entity.id)));
        reopen.settip(name);
        stop = add(new Button(UI.scale(26), "×", false, this::destroy));
        stop.settip(L10n.tr("Stop searching"));
    }

    @Override public void presize() {
        // Leave the bottom row available for the quest-search chip.
        MapWnd map = (MapWnd)parent;
        Coord area = map.csz();
        int margin = UI.scale(8);
        int gap = UI.scale(2);
        reopen.resizew(Math.max(1, Math.min(nameWidth, area.x - 2 * margin - stop.sz.x - gap)));
        stop.move(reopen.pos("ur").adds(gap, 0));
        pack();
        move(Coord.of(margin, Math.max(0, area.y - 2 * sz.y - UI.scale(10))));
    }

    @Override public void dispose() {
        TerrainHighlight.journalTiles(Set.of());
        super.dispose();
    }
}
