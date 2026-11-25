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

package hearthborn.journal;

import haven.Button;
import haven.Coord;
import haven.GameUI;
import haven.GOut;
import haven.SListBox;
import haven.SListWidget;
import haven.Text;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;
import hearthborn.L10n;
import hearthborn.gamedata.Entity;
import hearthborn.gamedata.GameDataRepository;
import hearthborn.ui.CraftHistory;
import hearthborn.ui.HudInterfaceScale;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hearthborn.journal.JournalStyle.*;

final class JournalWindow extends Window {
    private static final String ALL = "@all";
    private static final String FAVORITES = "@favorites";
    private static final Coord PREFERRED = UI.scale(640, 475);
    private static final int GAP = UI.scale(8);
    private static final int ROW = Math.max(UI.scale(29), BODY.height() + DETAIL.height() + UI.scale(3));
    private static final int RESULT_ICON = UI.scale(23);
    private static final Color MATCH_BACKGROUND = new Color(107, 83, 37);
    private static final BufferedImage FAVORITE_EMPTY = CraftHistory.favoriteStar(UI.scale(16), false);
    private static final BufferedImage FAVORITE_FILLED = CraftHistory.favoriteStar(UI.scale(16), true);
    private static final int SEARCH_ICON = UI.scale(21);

    private CompletableFuture<GameDataRepository> loading;
    private final Supplier<GameDataRepository> latest;
    private CompletableFuture<GameJournal.CardImage> loadingImage;
    private GameDataRepository repository;
    private final List<String> categories = new ArrayList<>();
    private final Map<String, List<String>> categoryTree = new LinkedHashMap<>();
    private final Map<Entity, CompletableFuture<BufferedImage>> thumbnails =
        new LinkedHashMap<Entity, CompletableFuture<BufferedImage>>(32, .75f, true) {
            protected boolean removeEldestEntry(Map.Entry<Entity, CompletableFuture<BufferedImage>> entry) {
                return size() > 128;
            }
        };
    private final Set<String> expanded = new HashSet<>();
    private List<String> favorites = JournalFavorites.favorites();
    private List<Entity> matches = Collections.emptyList();
    private List<JournalSearch.Row> resultRows = Collections.emptyList();
    private Pattern highlight;
    private Text count;
    private int searchFieldWidth;
    private List<Entity> links = Collections.emptyList();
    private final Deque<String> trail = new ArrayDeque<>();
    private String requestedEntry;
    private Entity selected;
    private Set<String> habitatTiles = Set.of();
    private GameJournal.CardImage illustration;

    private final TextEntry search = add(new TextEntry(UI.scale(100), "") {
        private final Text hint = BODY.render(L10n.tr("Search the journal"), MUTED);

        protected Color textColor() { return INK; }
        protected void drawBackground(GOut g) {}
        protected void drawFrame(GOut g) {}
        public void draw(GOut g) {
            super.draw(g);
            if(text().isEmpty())
                g.image(hint.tex(), Coord.of(toffx + UI.scale(2), (sz.y - hint.sz().y) / 2));
        }
        public void dispose() { hint.dispose(); super.dispose(); }

        protected void changed() {
            super.changed();
            if(repository != null) {
                if(!text().trim().isEmpty() && !groups.favoritesView()) groups.sel = ALL;
                showResults();
            }
        }

        public void activate(String query) {
            if(!matches.isEmpty() && selected == null) visit(matches.get(0), false);
        }
    });
    private final Button clear = add(new Button(UI.scale(25), "×", false, () -> {
        search.settext("");
        setfocus(search);
    }).look(Button.Look.OUTLINE));
    private final Groups groups = add(new Groups());
    private final Entries results = add(new Entries());
    private final Button back = add(new Button(UI.scale(84), "Back", this::back).look(Button.Look.OUTLINE));
    private final Button favorite = add(new Button(UI.scale(30), FAVORITE_EMPTY).action(this::toggleFavorite));
    private final Button showOnMap = add(new Button(UI.scale(152), "Show on map", false, () -> {
        if(JournalMapSearch.show(getparent(GameUI.class), selected, habitatTiles)) destroy();
    }));
    private final JournalCard detail = add(new JournalCard(entry -> visit(entry, true), this::thumbnail));
    private String message;

    JournalWindow(CompletableFuture<GameDataRepository> loading, Supplier<GameDataRepository> latest) {
        super(PREFERRED, "Game Journal");
        this.loading = loading;
        this.latest = latest;
        setfocustab(true);
        reqclose(this::destroy);
        clear.settip("Clear search");
        search.settip("Search in Russian or English");
        showOnMap.settip(L10n.tr("Highlighted terrain is a place to look, not a known plant location."));
        message = L10n.tr("Loading local game data...");
        layoutViews();
        detail.message(message);
    }

    void fit(boolean center) {
        if(parent == null) return;
        Coord room = HudInterfaceScale.fromParent(this, parent.sz).sub(sz.sub(csz()));
        Coord wanted = PREFERRED.min(room.sub(GAP, GAP)).max(UI.scale(200, 145));
        if(!wanted.equals(csz())) resize(wanted);
        Coord bounds = parent.sz.sub(HudInterfaceScale.size(this)).max(Coord.z);
        move(center ? bounds.div(2) : c.max(Coord.z).min(bounds));
    }

    public void presize() {
        super.presize();
        fit(false);
    }

    public void resize(Coord size) {
        super.resize(size);
        if(detail != null) {
            layoutViews();
            if(selected != null) renderCard();
            else if(message != null) detail.message(message);
        }
    }

    private void layoutViews() {
        Coord area = csz();
        search.move(Coord.of(SEARCH_ICON, 0));
        clear.move(Coord.of(area.x - clear.sz.x, (search.sz.y - clear.sz.y) / 2));
        searchFieldWidth = Math.max(1, clear.c.x - GAP / 2);
        int reserve = count == null || count.sz().x > searchFieldWidth / 3 ? 0 : count.sz().x + GAP;
        search.resize(Math.max(UI.scale(30), searchFieldWidth - SEARCH_ICON - reserve));

        int left = Math.min(UI.scale(150), area.x / 3);
        int x = left + GAP;
        int width = Math.max(1, area.x - x);
        int y = search.sz.y + GAP;
        int height = Math.max(1, area.y - y);
        groups.move(Coord.of(0, y));
        groups.resize(Coord.of(left, height));

        boolean card = selected != null;
        results.move(Coord.of(x, y));
        results.resize(Coord.of(width, height));
        back.move(Coord.of(x, y));
        favorite.move(Coord.of(x + width - favorite.sz.x, y));
        boolean canShowOnMap = card && !habitatTiles.isEmpty();
        int top = card ? y + back.sz.y + GAP / 2 : y;
        if(canShowOnMap) {
            showOnMap.resize(Coord.of(Math.min(width, UI.scale(152)), showOnMap.sz.y));
            showOnMap.move(Coord.of(x, top));
            top += showOnMap.sz.y + GAP / 2;
        }
        detail.move(Coord.of(x, top));
        detail.resize(Coord.of(width, Math.max(1, area.y - top)));

        groups.show(repository != null);
        results.show(repository != null && !card);
        back.show(card);
        favorite.show(card);
        showOnMap.show(canShowOnMap);
        detail.show(repository == null || card || matches.isEmpty());
    }

    public void tick(double dt) {
        super.tick(dt);
        if(loading != null && loading.isDone()) {
            try {
                useRepository(loading.join());
                setfocus(search);
            } catch(CompletionException error) {
                message = L10n.tr("Could not load game data.");
                detail.message(message);
                detail.tooltip = error.getCause().getMessage();
            } finally {
                loading = null;
            }
        }
        if(loading == null) {
            GameDataRepository next = latest.get();
            if(next != null && next != repository) useRepository(next);
            if(requestedEntry != null && repository != null) {
                String id = requestedEntry;
                requestedEntry = null;
                openEntry(id);
            }
        }
        if(loadingImage != null && loadingImage.isDone()) {
            GameJournal.CardImage next = loadingImage.getNow(null);
            loadingImage = null;
            if(selected != null && next != null) {
                illustration = next;
                renderCard();
            }
        }
    }

    void openEntry(String id) {
        if(repository == null) {
            requestedEntry = id;
            return;
        }
        Entity entry = repository.find(id);
        if(entry == null) {
            GameDataRepository current = latest.get();
            if(current == null || current == repository || current.find(id) == null) return;
            useRepository(current);
            entry = repository.find(id);
        }
        if(entry != selected) visit(entry, true);
    }

    private void useRepository(GameDataRepository next) {
        String category = groups.sel;
        String entry = selected == null ? null : selected.id;
        int scroll = results.scrollval();
        Deque<String> previous = new ArrayDeque<>(trail);
        repository = next;
        thumbnails.clear();
        buildCategoryTree();
        rebuildCategories();
        int index = categories.indexOf(category);
        groups.sel = index >= 0 ? categories.get(index) : ALL;
        detail.tooltip = null;
        showResults();
        if(entry == null) results.scrollval(scroll);
        else {
            Entity refreshed = repository.find(entry);
            if(refreshed == null) return;
            for(String id : previous) {
                if(repository.find(id) != null) trail.addLast(id);
            }
            showCard(refreshed);
        }
    }

    private void buildCategoryTree() {
        categoryTree.clear();
        Set<String> populated = new HashSet<>();
        for(Entity entry : repository.search("", null, L10n.language())) {
            populated.add(entry.category);
            if(entry.subcategory != null) populated.add(entry.subcategory);
        }
        for(String id : repository.categories()) {
            if(populated.contains(id) && !id.contains("/")) categoryTree.put(id, new ArrayList<>());
        }
        for(String id : repository.categories()) {
            int slash = id.indexOf('/');
            if(slash >= 0 && populated.contains(id)) categoryTree.get(id.substring(0, slash)).add(id);
        }
    }

    private void rebuildCategories() {
        categories.clear();
        categories.add(ALL);
        categories.add(FAVORITES);
        for(Map.Entry<String, List<String>> category : categoryTree.entrySet()) {
            categories.add(category.getKey());
            if(expanded.contains(category.getKey())) categories.addAll(category.getValue());
        }
        groups.reset();
    }

    private void showResults() {
        selected = null;
        illustration = null;
        loadingImage = null;
        trail.clear();
        links = Collections.emptyList();
        JournalSearch searchMode = new JournalSearch(search.text());
        resultRows = searchMode.rows(findResults(), L10n.language());
        highlight = searchMode.highlight;
        matches = new ArrayList<>();
        for(JournalSearch.Row row : resultRows) if(row.entity != null) matches.add(row.entity);
        if(count != null) count.dispose();
        count = highlight == null ? null : DETAIL.render(resultCount(matches.size()), MUTED);
        results.sel = null;
        results.scrollval(0);
        layoutViews();
        message = null;
        if(matches.isEmpty()) {
            String empty = groups.favoritesView() && search.text().trim().isEmpty()
                ? "No favorite entries yet." : "No journal entries found.";
            message = L10n.tr(empty);
            detail.message(message);
        }
    }

    private static String resultCount(int number) {
        int last = number % 10, pair = number % 100;
        if(!"ru".equals(L10n.language()))
            return L10n.format(number == 1 ? "%d journal result" : "%d journal results", number);
        String key = last == 1 && pair != 11 ? "%d journal result"
            : last >= 2 && last <= 4 && (pair < 12 || pair > 14) ? "Journal results: %d" : "%d journal results";
        return L10n.format(key, number);
    }

    @Override public void dispose() {
        if(count != null) count.dispose();
        super.dispose();
    }

    private List<Entity> findResults() {
        String group = groups.sel;
        String query = search.text();
        if(!groups.favoritesView()) return repository.search(query, ALL.equals(group) ? null : group, L10n.language());
        Set<Entity> found = query.trim().isEmpty() ? null : new HashSet<>(repository.search(query, null, L10n.language()));
        List<Entity> entries = new ArrayList<>();
        for(String id : favorites) {
            Entity entry = repository.find(id);
            if(entry != null && (found == null || found.contains(entry))) entries.add(entry);
        }
        return entries;
    }

    private void visit(Entity entity, boolean followLink) {
        if(followLink && selected != null) trail.push(selected.id);
        showCard(entity);
    }

    private void back() {
        if(trail.isEmpty()) showResults();
        else showCard(repository.find(trail.pop()));
    }

    private void toggleFavorite() {
        if(selected == null) return;
        favorites = JournalFavorites.saveFavorite(selected.id, !favorites.contains(selected.id));
        updateFavorite();
    }

    private void updateFavorite() {
        boolean saved = favorites.contains(selected.id);
        favorite.cont = saved ? FAVORITE_FILLED : FAVORITE_EMPTY;
        favorite.redraw();
        favorite.settip(saved ? "Remove from Favorites" : "Add to Favorites");
    }

    private void showCard(Entity entity) {
        selected = entity;
        habitatTiles = JournalMapSearch.tiles(entity);
        updateFavorite();
        illustration = null;
        links = new ArrayList<>();
        for(String id : entity.related) links.add(repository.find(id));
        detail.reset();
        layoutViews();
        renderCard();
        GameDataRepository snapshot = repository;
        loadingImage = CompletableFuture.supplyAsync(() -> GameJournal.image(snapshot, entity))
            .exceptionally(error -> null);
    }

    private void renderCard() {
        String category = groupName(selected.category);
        if(selected.subcategory != null) category += " / " + groupName(selected.subcategory);
        detail.render(repository, selected, category, illustration, links);
    }

    @Override public void cdraw(GOut g) {
        fill(g, Coord.z, csz(), PANEL);
        Coord field = Coord.of(searchFieldWidth, search.sz.y);
        fill(g, Coord.z, field, SURFACE);
        border(g, Coord.z, field, search.hasfocus ? GOLD : EDGE);
        if(count != null && count.sz().x <= searchFieldWidth / 3)
            g.image(count.tex(), Coord.of(field.x - count.sz().x - GAP / 2, (field.y - count.sz().y) / 2));
        Coord center = Coord.of(UI.scale(10), search.sz.y / 2 - UI.scale(1));
        Coord radius = Coord.of(UI.scale(3));
        g.chcolor(MUTED);
        g.fellipse(center, radius);
        g.chcolor(SURFACE);
        g.fellipse(center, radius.sub(1, 1).max(Coord.of(1)));
        g.chcolor(MUTED);
        g.line(center.add(UI.scale(2), UI.scale(2)), center.add(UI.scale(6), UI.scale(6)), 1);
        g.chcolor();
    }

    static int previewHeight(GameJournal.CardImage image, int availableWidth) {
        int width = image.pixels.getWidth(), height = image.pixels.getHeight();
        int longest = Math.max(width, height);
        int limit = UI.scale(image.icon ? Math.min(32, longest) : 100);
        double scale = Math.min((double)limit / longest, (double)Math.max(1, availableWidth) / width);
        return Math.max(1, (int)Math.round(height * scale));
    }

    static String attributeText(Map.Entry<String, Object> attribute) {
        Object value = attribute.getValue();
        if(value instanceof List<?>) {
            List<String> parts = new ArrayList<>();
            for(Object part : (List<?>)value) parts.add(attributeValue(part));
            return String.join(", ", parts);
        }
        return attributeValue(value);
    }

    private static String attributeValue(Object value) {
        if(value instanceof Boolean) return L10n.text("button", (Boolean)value ? "Yes" : "No");
        return value instanceof String ? L10n.quiet("label", (String)value) : String.valueOf(value);
    }

    static String attributeName(String id) {
        return L10n.quiet("label", humanize(id));
    }

    private static String humanize(String id) {
        String name = id.replace('-', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static String groupCaption(String id) {
        switch(id) {
        case ALL: return "All entries";
        case FAVORITES: return "Favorites";
        case "animals/wild": return "Wild animals";
        case "localized-resources": return "Natural resources";
        default: return humanize(id.substring(id.lastIndexOf('/') + 1));
        }
    }

    private static String groupName(String id) {
        return L10n.quiet("label", groupCaption(id));
    }

    private final class Groups extends SListBox<String, Widget> {
        private final int arrowWidth = UI.scale(16);

        Groups() { super(UI.scale(100, 100), ROW); }
        protected List<String> items() { return categories; }
        protected void drawbg(GOut g) {}
        protected void drawslot(GOut g, String id, int idx, haven.Area area) {}
        boolean favoritesView() { return FAVORITES.equals(sel); }

        protected Widget makeitem(String id, int index, Coord size) {
            boolean branch = hasChildren(id);
            int indent = branch || id.contains("/") ? arrowWidth : 0;
            return new JournalRow<String>(this, size, id, true) {
                {
                    if(branch) add(SListWidget.TextItem.of(Coord.of(arrowWidth, size.y), BODY,
                        () -> expanded.contains(id) ? "▾" : "▸"));
                    add(SListWidget.TextItem.of(Coord.of(Math.max(1, size.x - indent - UI.scale(4)), size.y), BODY,
                        () -> groupName(id)), Coord.of(indent + UI.scale(4), 0));
                }

                protected boolean clicked(Widget.MouseDownEvent ev) {
                    if(branch && ev.c.x < arrowWidth) {
                        if(!expanded.remove(id)) expanded.add(id);
                        else if(sel != null && sel.startsWith(id + "/")) select(id);
                        rebuildCategories();
                        return true;
                    }
                    return super.clicked(ev);
                }
            };
        }

        private boolean hasChildren(String id) {
            List<String> children = categoryTree.get(id);
            return children != null && !children.isEmpty();
        }

        public void change(String id) {
            if(id == null) return;
            if(hasChildren(id) && expanded.add(id)) rebuildCategories();
            select(id);
        }

        private void select(String id) {
            super.change(id);
            if(search.text().isEmpty()) showResults();
            else search.settext("");
        }
    }

    private CompletableFuture<BufferedImage> thumbnail(Entity entry) {
        GameDataRepository snapshot = repository;
        return thumbnails.computeIfAbsent(entry, key ->
            CompletableFuture.supplyAsync(() -> GameJournal.rowImage(snapshot, key, RESULT_ICON))
                .exceptionally(error -> null));
    }

    private static String entryType(Entity entry) {
        switch(entry.category) {
        case "animals": return "Animal";
        case "forageables":
            if("forageables/mushrooms".equals(entry.subcategory)) return "Mushroom";
            if("forageables/berries".equals(entry.subcategory)) return "Berry";
            return "Plant";
        case "localized-resources": return "Natural resource";
        case "trees": return "Tree";
        case "bushes": return "Bush";
        case "rocks": return "Rock";
        case "biomes": return "Biome";
        case "items": return "Item";
        case "buildings": return "Building";
        case "recipes": return "Recipe";
        case "dungeons": return "Dungeon";
        default: return groupCaption(entry.category);
        }
    }

    private static class JournalRow<T> extends SListWidget.ItemWidget<T> {
        private final boolean group;
        private boolean over;

        JournalRow(SListWidget<T, ?> list, Coord size, T item, boolean group) {
            super(list, size, item);
            this.group = group;
        }

        public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
            over = hovering;
            return super.mousehover(ev, hovering);
        }

        public void draw(GOut g) {
            boolean selected = item.equals(list.sel);
            if(selected || over) fill(g, Coord.z, sz, selected ? SELECTED : HOVER);
            if(group && selected) fill(g, Coord.z, Coord.of(UI.scale(2), sz.y), GOLD);
            if(!group) fill(g, Coord.of(0, sz.y - 1), Coord.of(sz.x, 1), EDGE);
            g.chcolor(selected ? GOLD : INK);
            super.draw(g);
            g.chcolor();
        }
    }

    private final class Entries extends SListBox<JournalSearch.Row, Widget> {
        Entries() { super(UI.scale(100, 100), ROW); }
        protected List<JournalSearch.Row> items() { return resultRows; }
        protected void drawbg(GOut g) { fill(g, Coord.z, sz, SURFACE); }
        protected void drawslot(GOut g, JournalSearch.Row entry, int idx, haven.Area area) {}

        protected Widget makeitem(JournalSearch.Row row, int index, Coord size) {
            if(row.heading != null) {
                return new Widget(size) {
                    {
                        haven.Label title = JournalStyle.label(L10n.tr(row.heading.title), size.x - 2 * GAP, DETAIL, MUTED);
                        add(title, Coord.of(GAP, (size.y - title.sz.y) / 2));
                    }
                    public void draw(GOut g) {
                        fill(g, Coord.z, sz, PANEL);
                        super.draw(g);
                    }
                };
            }
            Entity entry = row.entity;
            CompletableFuture<BufferedImage> preview = thumbnail(entry);
            return new JournalRow<JournalSearch.Row>(this, size, row, false) {
                {
                    int inset = UI.scale(8);
                    String english = entryType(entry);
                    String translated = L10n.find("label", "Journal type: " + english);
                    String type = entry.displayType != null ? entry.displayType.text(L10n.language())
                        : translated == null ? L10n.quiet("label", english) : translated;
                    haven.Label kind = new haven.Label(type, DETAIL);
                    kind.setcolor(MUTED);
                    int cap = Math.min(size.x / 3, kind.sz.x);
                    if(kind.sz.x > cap) {
                        kind.dispose();
                        Text clipped = DETAIL.ellipsize(type, cap);
                        kind = new haven.Label(clipped.text, DETAIL);
                        clipped.dispose();
                        kind.setcolor(MUTED);
                    }
                    int end = size.x - inset - kind.sz.x;
                    add(kind, Coord.of(end, (size.y - kind.sz.y) / 2));
                    int nameX = size.y + UI.scale(5);
                    int nameWidth = Math.max(1, end - inset - nameX);
                    String name = entry.text(L10n.language()).name;
                    boolean excerpt = !row.excerpt.isEmpty();
                    int height = BODY.height() + (excerpt ? DETAIL.height() : 0);
                    int y = Math.max(0, (size.y - height) / 2);
                    add(new SearchLine(name, nameWidth, BODY, INK, highlight), Coord.of(nameX, y));
                    if(excerpt)
                        add(new SearchLine(row.excerpt, nameWidth, DETAIL, MUTED, highlight), Coord.of(nameX, y + BODY.height()));
                    add(new SListWidget.IconText(Coord.of(size.y, size.y)) {
                        private boolean loaded;
                        protected BufferedImage img() { return preview.getNow(null); }
                        protected String text() { return ""; }
                        protected void drawtext(GOut g) {}
                        protected Text.Forge foundry() { return BODY; }
                        protected int margin() { return Math.max(0, (size.y - RESULT_ICON + 1) / 2); }

                        public void tick(double dt) {
                            if(!loaded && preview.isDone()) {
                                loaded = true;
                                invalidate();
                            }
                            super.tick(dt);
                        }
                    });
                }
            };
        }

        public void change(JournalSearch.Row row) {
            if(row == null || row.entity == null) return;
            super.change(row);
            visit(row.entity, false);
        }
    }

    private static final class SearchLine extends Widget {
        final Text.Line line;
        final Color color;
        final Pattern highlight;

        SearchLine(String text, int width, Text.Foundry font, Color color, Pattern highlight) {
            super(Coord.of(width, font.height()));
            line = font.ellipsize(text, width);
            this.color = color;
            this.highlight = highlight;
            if(!text.equals(line.text)) settip(text);
        }

        @Override public void draw(GOut g) {
            if(highlight != null) {
                Matcher match = highlight.matcher(line.text);
                while(match.find()) {
                    int start = line.advance(match.start()), end = line.advance(match.end());
                    fill(g, Coord.of(start, 0), Coord.of(Math.max(1, end - start), sz.y), MATCH_BACKGROUND);
                }
            }
            g.chcolor(color);
            g.image(line.tex(), Coord.z);
            g.chcolor();
        }

        @Override public void dispose() {
            line.dispose();
            super.dispose();
        }
    }
}
