/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.ui;

import haven.*;
import hearthborn.L10n;
import hearthborn.map.TerrainHighlight;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Local forage habitats keyed by inventory resource, independently of the UI language. */
public final class QuestForage extends Window {
    private static QuestForage active;
    private final GameUI gui;
    private final int questId;
    private final String objective;
    private Widget chip;
    private static String searchedItem;

    public static boolean searchesFor(String iconResource, String worldResource) {
        return searchedItem != null && (searchedItem.equals(iconResource)
            || Objects.equals(QuestPictures.growing(searchedItem), worldResource));
    }

    public static void stopSearch() {
        QuestForage previous = active;
        clearSearch();
        if(previous != null) {
            if(previous.chip != null && previous.chip.parent != null) previous.chip.destroy();
            previous.chip = null;
            if(previous.parent != null) previous.destroy();
        }
    }

    public static void sessionEnded(GameUI gui) {
        if(active == null || active.gui == gui) clearSearch();
    }

    private static void clearSearch() {
        active = null;
        searchedItem = null;
        TerrainHighlight.questTiles(Collections.emptySet());
    }

    static void startSearch(String resource, Habitat habitat) {
        searchedItem = resource;
        TerrainHighlight.questTiles(habitat.tiles);
    }
    private static final String PREFIX = "gfx/invobjs/herbs/";
    private static final Pattern GATHER = Pattern.compile("(?i)^(?:pick|gather|forage|collect|harvest)\\b.*");
    private static final Map<String, Habitat> HABITATS = load();

    static final class Habitat {
        final String name;
        final Set<String> tiles;
        Habitat(String name, Set<String> tiles) {
            this.name = name;
            this.tiles = Collections.unmodifiableSet(tiles);
        }
    }

    private static Map<String, Habitat> load() {
        Map<String, Habitat> result = new HashMap<>();
        try (InputStream source = QuestForage.class.getResourceAsStream("quest-forage.txt")) {
            if (source == null) return result;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.trim().isEmpty()) continue;
                    String[] fields = line.split("\\|", 3);
                    if (fields.length != 3) continue;
                    Set<String> tiles = new LinkedHashSet<>();
                    for (String tile : fields[2].split(" ")) tiles.add("gfx/tiles/" + tile);
                    result.put(PREFIX + fields[0], new Habitat(fields[1], tiles));
                }
            }
        } catch (IOException error) {
            new Warning(error, "Could not load quest habitats").issue();
        }
        return result;
    }

    static Habitat habitat(String objective, String resource) {
        return objective != null && GATHER.matcher(objective.trim()).matches() ? HABITATS.get(resource) : null;
    }

    public static boolean open(GameUI gui, int questId, String objective, String resource) {
        Habitat habitat = habitat(objective, resource);
        if (gui == null || habitat == null) return false;
        for(QuestForage card : new ArrayList<>(gui.children(QuestForage.class))) {
            if(card == active && card.questId == questId && card.objective.equals(objective)) {
                card.reopen();
                return true;
            }
            if(card == active) card.hide();
            else card.destroy();
        }
        QuestForage card = new QuestForage(gui, questId, objective, resource, habitat);
        Coord pointer = gui.rootxlate(gui.ui.mc).add(UI.scale(12, 12));
        Coord max = gui.sz.sub(HudInterfaceScale.size(card));
        gui.add(card, Coord.of(Math.max(0, Math.min(pointer.x, max.x)),
                               Math.max(0, Math.min(pointer.y, max.y))));
        return true;
    }

    private void reopen() {
        for(QuestForage card : new ArrayList<>(gui.children(QuestForage.class)))
            if(card != this && card != active) card.destroy();
        show();
        raise();
    }

    public static void objectives(GameUI gui, int questId, QuestWnd.Quest.Condition[] conditions) {
        if(active == null || active.gui != gui || active.questId != questId) return;
        for(QuestWnd.Quest.Condition condition : conditions) {
            if(condition.done == QuestWnd.Quest.QST_DONE && condition.desc.equals(active.objective)) {
                stopSearch();
                return;
            }
        }
    }

    public static void questEnded(GameUI gui, int questId) {
        if(active != null && active.gui == gui && active.questId == questId) stopSearch();
    }

    public static void placeChip(GameUI gui) {
        if(active == null || active.gui != gui || gui.mapfile == null) return;
        active.placeChip(gui.mapfile);
    }

    private void placeChip(MapWnd map) {
        if(chip == null || chip.parent != map) {
            String name = cap;
            int width = Math.max(UI.scale(95), Text.std.strsize(name).x + UI.scale(24));
            Widget strip = new Widget();
            Button reopen = strip.add(new Button(width, name, false, this::reopen));
            Button stop = strip.add(new Button(UI.scale(26), "×", false, QuestForage::stopSearch),
                reopen.pos("ur").adds(2, 0));
            stop.settip(L10n.tr("Stop searching"));
            strip.pack();
            chip = map.add(strip);
            chip.z(100);
        }
        chip.move(Coord.of(UI.scale(8), Math.max(0, map.csz().y - chip.sz.y - UI.scale(8))));
    }

    private QuestForage(GameUI gui, int questId, String objective, String resource, Habitat habitat) {
        super(Coord.z, L10n.resource("tooltip", resource, habitat.name));
        this.gui = gui;
        this.questId = questId;
        this.objective = objective;
        reqclose(() -> {
            if (active == this) hide();
            else reqdestroy();
        });
        int width = UI.scale(320);
        Indir<Resource> item = Resource.remote().load(resource);
        add(new Widget(UI.scale(58, 58)) {
            public void draw(GOut g) {
                try {
                    Resource.Image image = item.get().layer(Resource.imgc);
                    if (image != null) {
                        Coord size = image.ssz;
                        double factor = (double) sz.x / Math.max(size.x, size.y);
                        Coord fitted = size.mul(factor);
                        g.image(image.tex(), sz.sub(fitted).div(2), fitted);
                    }
                } catch (Loading pending) {}
            }
        }, Coord.z);
        String summary = resource.equals(PREFIX + "waybroad")
            ? "A wild herb used to treat deep cuts."
            : "Wild forageable";
        add(new Label(L10n.tr(summary), width - UI.scale(70)), UI.scale(70, 5));
        Set<String> names = new LinkedHashSet<>();
        for (String tile : habitat.tiles) names.add(TerrainHighlight.name(tile));
        Label places = add(new Label(String.join(", ", names), width), UI.scale(0, 66));
        Label description = add(new Label(L10n.tr("Highlighted terrain is a place to look, not a known plant location."), width),
            places.pos("bl").add(UI.scale(0, 8)));
        Button map = add(new Button(UI.scale(152), "Show on map", false) {
            public void click() {
                if(active == QuestForage.this) {
                    stopSearch();
                    return;
                }
                stopSearch();
                active = QuestForage.this;
                startSearch(resource, habitat);
                change("Stop searching");
                QuestForage.this.hide();
                if(gui.mapfile != null) {
                    gui.mapfile.show();
                    gui.mapfile.raise();
                    placeChip(gui);
                }
            }
        }, description.pos("bl").add(UI.scale(0, 10)));
        add(new Button(UI.scale(125), "Open wiki", false, () -> {
            try {
                String path = habitat.name.replace(' ', '_');
                gui.ui.wnd.toolkit().browse(new URI("https", "ringofbrodgar.com", "/wiki/" + path, null));
            } catch (Exception error) {
                gui.error(L10n.tr("Could not open the wiki."));
            }
        }), map.c.add(map.sz.x + UI.scale(6), 0));
        pack();
    }

}
