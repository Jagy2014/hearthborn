/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.journal;

import haven.Coord;
import haven.GOut;
import haven.Label;
import haven.Scrollport;
import haven.Tex;
import haven.TexI;
import haven.Text;
import haven.UI;
import haven.Widget;
import hearthborn.L10n;
import hearthborn.gamedata.Entity;
import hearthborn.gamedata.GameDataRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static hearthborn.journal.JournalStyle.*;

final class JournalCard extends Scrollport {
    private static final int PAD = UI.scale(10), GAP = UI.scale(5), SECTION = UI.scale(10);
    private static final int HABITAT_LIMIT = 6;
    private static final List<String> SEASONS = List.of("Spring", "Summer", "Autumn", "Winter");
    private static final List<String> ANIMAL_STATS = List.of(
        "hitpoints", "armor", "damage-before-fleeing", "base-quality", "is-deadly", "domestic");
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)\\s*([×x]\\s*[0-9]+(?:-[0-9]+)?)$");
    private final Consumer<Entity> follow;
    private final Function<Entity, CompletableFuture<BufferedImage>> thumbnail;
    private Widget page;
    private boolean expanded;
    private final Set<Entity.Section> expandedSections = new HashSet<>();

    JournalCard(Consumer<Entity> follow, Function<Entity, CompletableFuture<BufferedImage>> thumbnail) {
        super(UI.scale(100, 100));
        this.follow = follow;
        this.thumbnail = thumbnail;
    }

    @Override public void draw(GOut g) {
        fill(g, Coord.z, sz, SURFACE);
        super.draw(g);
        border(g, Coord.z, sz, EDGE);
    }

    void reset() {
        expanded = false;
        expandedSections.clear();
        bar.val = cont.sy = 0;
    }

    void message(String text) {
        int width = Math.max(1, cont.sz.x - 2 * PAD);
        Widget next = new Widget();
        next.add(label(text, width, BODY, MUTED));
        next.pack();
        replace(next);
    }

    void render(GameDataRepository repo, Entity entry, String category, GameJournal.CardImage image, List<Entity> related) {
        CardLayout layout = new CardLayout(Math.max(1, cont.sz.x - 2 * PAD));
        Entity.Text text = entry.text(L10n.language());
        layout.header(text.name, category, image);
        if(!text.description.trim().isEmpty())
            layout.line(text.description, BODY, INK, SECTION);

        Map<String, Object> stats = new LinkedHashMap<>(entry.attributes);
        Object products = stats.remove("creature-products");
        Object seasons = stats.remove("seasonality");
        Object habitats = stats.remove("habitats");
        layout.stats(stats);

        List<Chip> chips = new ArrayList<>();
        for(Object value : values(products)) {
            String raw = String.valueOf(value);
            Matcher amount = AMOUNT.matcher(raw);
            boolean hasAmount = amount.matches();
            String name = hasAmount ? amount.group(1).trim() : raw;
            String quantity = hasAmount ? amount.group(2).replace('x', '×') : "";
            Entity target = findLink(repo, name, "items");
            String caption = target == null ? L10n.quiet("tooltip", name) : target.text(L10n.language()).name;
            chips.add(new Chip(layout.width, caption, quantity, target, null, false, false, null));
        }
        layout.chips(L10n.tr("Creature products"), chips);

        List<?> seasonValues = values(seasons);
        if(!seasonValues.isEmpty()) {
            chips = new ArrayList<>();
            boolean canonical = SEASONS.containsAll(seasonValues);
            List<?> labels = canonical ? SEASONS : seasonValues;
            for(Object value : labels) {
                boolean active = seasonValues.contains(value);
                chips.add(new Chip(layout.width, L10n.quiet("label", value.toString()), "", null, null, active, !active, null));
            }
            layout.chips(L10n.tr("Seasonality"), chips);
        }

        List<?> terrain = values(habitats);
        chips = new ArrayList<>();
        int limit = expanded ? terrain.size() : Math.min(HABITAT_LIMIT, terrain.size());
        for(int i = 0; i < limit; i++) {
            String name = terrain.get(i).toString();
            Entity target = findLink(repo, name, "biomes");
            String caption = target == null ? L10n.quiet("biome", name) : target.text(L10n.language()).name;
            chips.add(new Chip(layout.width, caption, "", target, null, false, false, null));
        }
        if(terrain.size() > HABITAT_LIMIT) {
            String toggle = expanded ? L10n.tr("Show fewer habitats") : L10n.format("%d more habitats", terrain.size() - limit);
            chips.add(new Chip(layout.width, toggle, "", null, () -> {
                expanded = !expanded;
                render(repo, entry, category, image, related);
            }, false, false, null));
        }
        String habitatHeading = List.of("forageables", "trees", "bushes").contains(entry.category)
            ? "Where it grows" : "Habitats";
        layout.chips(L10n.tr(habitatHeading), chips);
        Set<String> listed = new HashSet<>();
        for(Entity.Section section : entry.sections) {
            boolean chain = section.kind == Entity.Section.Kind.CHAIN;
            int maximum = chain || expandedSections.contains(section) ? section.steps.size() : Math.min(4, section.steps.size());
            chips = new ArrayList<>();
            int chipWidth = chain ? Math.max(1, layout.width - UI.scale(20)) : layout.width;
            for(int i = 0; i < section.steps.size(); i++) {
                Entity.Section.Step step = section.steps.get(i);
                if(step.entry != null) listed.add(step.entry);
                if(i >= maximum) continue;
                Entity target = step.entry == null ? null : repo.find(step.entry);
                String caption = target == null ? step.label.text(L10n.language()) : target.text(L10n.language()).name;
                chips.add(new Chip(chipWidth, caption, "", target == entry ? null : target, null, target == entry,
                    false, target == null ? null : thumbnail.apply(target)));
            }
            if(!chain && section.steps.size() > 4) {
                String toggle = expandedSections.contains(section) ? L10n.tr("Show fewer links")
                    : L10n.format("%d more habitats", section.steps.size() - maximum);
                chips.add(new Chip(layout.width, toggle, "", null, () -> {
                    if(!expandedSections.remove(section)) expandedSections.add(section);
                    render(repo, entry, category, image, related);
                }, false, false, null));
            }
            String heading = section.title.text(L10n.language());
            if(chain) layout.chain(heading, chips);
            else layout.chips(heading, chips);
        }
        boolean productsOnly = List.of("localized-resources", "trees", "bushes").contains(entry.category) &&
            related.stream().allMatch(link -> link.category.equals("items"));
        List<Entity> remaining = new ArrayList<>();
        for(Entity link : related) if(!listed.contains(link.id)) remaining.add(link);
        layout.related(L10n.tr(productsOnly ? "What it yields" : "Related entries"), remaining);
        layout.body.resize(Coord.of(layout.width, layout.y + PAD));
        replace(layout.body);
    }

    private void replace(Widget next) {
        if(page != null) page.destroy();
        page = cont.add(next, Coord.of(PAD, PAD));
        cont.update();
        bar.val = cont.sy = Math.min(cont.sy, bar.max);
    }

    private static List<?> values(Object value) {
        return value instanceof List<?> ? (List<?>)value : value == null ? List.of() : List.of(value);
    }

    private static Entity findLink(GameDataRepository repo, String raw, String category) {
        Entity match = null;
        String wanted = key(raw);
        for(Entity candidate : repo.search("", category, "en")) {
            if(!matches(candidate.text("en"), wanted) && !matches(candidate.text(L10n.language()), wanted)) continue;
            if(match != null) return null; // Do not guess between identically named entries.
            match = candidate;
        }
        return match;
    }

    private static boolean matches(Entity.Text text, String wanted) {
        return key(text.name).equals(wanted) || text.aliases.stream().anyMatch(alias -> key(alias).equals(wanted));
    }

    private static String key(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").replace('ё', 'е');
    }

    private final class CardLayout {
        final int width;
        final Widget body = new Widget();
        int y;

        CardLayout(int width) { this.width = width; }

        void header(String name, String category, GameJournal.CardImage image) {
            int x = 0, height = 0;
            if(image != null && image.icon) {
                Picture picture = body.add(new Picture(image, width), Coord.z);
                x = picture.sz.x + PAD;
                height = picture.sz.y;
            }
            Label title = body.add(label(name, width - x, TITLE, GOLD), Coord.of(x, y));
            Label subtitle = body.add(label(category, width - x, DETAIL, MUTED), title.pos("bl").adds(0, 2));
            y = Math.max(height, subtitle.c.y + subtitle.sz.y) + SECTION;
            if(image != null && !image.icon) {
                Picture picture = body.add(new Picture(image, width), Coord.of(0, y));
                y += picture.sz.y + SECTION;
            }
        }

        void line(String text, Text.Foundry font, Color ink, int gap) {
            Label label = body.add(label(text, width, font, ink), Coord.of(0, y));
            y += label.sz.y + gap;
        }

        void stats(Map<String, Object> stats) {
            if(stats.isEmpty()) return;
            Map<String, Object> ordered = new LinkedHashMap<>();
            for(String key : ANIMAL_STATS) if(stats.containsKey(key)) ordered.put(key, stats.remove(key));
            ordered.putAll(stats);
            int columns = Math.max(1, Math.min(3, (width + GAP) / UI.scale(118)));
            int tileWidth = (width - (columns - 1) * GAP) / columns;
            int col = 0, rowHeight = 0;
            for(Map.Entry<String, Object> attr : ordered.entrySet()) {
                Widget tile = new Widget() {
                    public void draw(GOut g) {
                        fill(g, Coord.z, sz, TILE);
                        border(g, Coord.z, sz, EDGE);
                        super.draw(g);
                    }
                };
                Label name = tile.add(label(JournalWindow.attributeName(attr.getKey()), tileWidth - 2 * GAP, DETAIL, MUTED), Coord.of(GAP, GAP));
                Label value = tile.add(label(JournalWindow.attributeText(attr), tileWidth - 2 * GAP, BODY, INK), name.pos("bl"));
                tile.resize(Coord.of(tileWidth, value.c.y + value.sz.y + GAP));
                body.add(tile, Coord.of(col * (tileWidth + GAP), y));
                rowHeight = Math.max(rowHeight, tile.sz.y);
                if(++col == columns) {
                    y += rowHeight + GAP;
                    col = rowHeight = 0;
                }
            }
            if(col > 0) y += rowHeight + GAP;
            y += SECTION - GAP;
        }

        void chips(String heading, List<Chip> chips) {
            if(chips.isEmpty()) return;
            line(heading, DETAIL, MUTED, GAP);
            int x = 0, row = 0;
            for(Chip chip : chips) {
                if(x > 0 && x + chip.sz.x > width) {
                    x = 0;
                    y += row + GAP;
                    row = 0;
                }
                body.add(chip, Coord.of(x, y));
                x += chip.sz.x + GAP;
                row = Math.max(row, chip.sz.y);
            }
            y += row + SECTION;
        }

        void related(String heading, List<Entity> entries) {
            if(entries.isEmpty()) return;
            line(heading, DETAIL, MUTED, GAP);
            int cols = width >= UI.scale(290) ? 2 : 1;
            int tileWidth = (width - (cols - 1) * GAP) / cols;
            int index = 0, row = 0;
            for(Entity entry : entries) {
                Chip chip = new Chip(tileWidth, entry.text(L10n.language()).name, "", entry, null, false, false, thumbnail.apply(entry));
                chip.resizew(tileWidth);
                int col = index++ % cols;
                body.add(chip, Coord.of(col * (tileWidth + GAP), y));
                row = Math.max(row, chip.sz.y);
                if(col == cols - 1) {
                    y += row + GAP;
                    row = 0;
                }
            }
            y += row;
        }

        void chain(String heading, List<Chip> chips) {
            line(heading, DETAIL, MUTED, GAP);
            int x = 0, rowHeight = 0, arrowWidth = UI.scale(20);
            for(int i = 0; i < chips.size(); i++) {
                Chip chip = chips.get(i);
                int stepWidth = chip.sz.x + (i == 0 ? 0 : arrowWidth);
                if(x > 0 && x + stepWidth > width) {
                    y += rowHeight + GAP;
                    x = rowHeight = 0;
                }
                if(i > 0) {
                    Label arrow = label("→", arrowWidth, BODY, GOLD);
                    body.add(arrow, Coord.of(x + UI.scale(3), y + Math.max(0, (chip.sz.y - arrow.sz.y) / 2)));
                    x += arrowWidth;
                }
                body.add(chip, Coord.of(x, y));
                rowHeight = Math.max(rowHeight, chip.sz.y);
                x += chip.sz.x;
            }
            y += rowHeight + SECTION;
        }
    }

    private static final class Picture extends Widget {
        final Tex image;
        Picture(GameJournal.CardImage card, int width) {
            int height = JournalWindow.previewHeight(card, width);
            super.resize(Coord.of(Math.max(1, Math.min(width, (int)Math.round((double)height * card.pixels.getWidth() / card.pixels.getHeight()))), height));
            image = new TexI(card.pixels);
        }
        public void draw(GOut g) { g.image(image, Coord.z, sz); }
        public void dispose() { image.dispose(); super.dispose(); }
    }

    final class Chip extends Widget {
        final Entity target;
        private final Runnable action;
        private final boolean active, inactive;
        private boolean over;
        private final CompletableFuture<BufferedImage> thumbnail;
        private Tex icon;
        private boolean imageLoaded;

        Chip(int width, String name, String quantity, Entity target, Runnable toggle, boolean active,
             boolean inactive, CompletableFuture<BufferedImage> thumbnail) {
            this.active = active;
            this.inactive = inactive;
            this.target = target;
            this.thumbnail = thumbnail;
            action = target == null ? toggle : () -> follow.accept(target);
            int inset = thumbnail == null ? GAP : UI.scale(26);
            int quantityWidth = quantity.isEmpty() ? 0 : BODY.strsize(quantity).x + GAP;
            int nameWidth = Math.min(Math.max(1, width - inset - quantityWidth - GAP), BODY.strsize(name).x + 1);
            Label label = add(label(name, nameWidth, BODY, active || toggle != null ? GOLD : inactive ? FAINT : INK), Coord.of(inset, GAP));
            if(quantityWidth > 0) add(label(quantity, quantityWidth, BODY, GOLD), label.pos("ur").adds(3, 0));
            resize(Coord.of(Math.min(width, contentsz().x + GAP), contentsz().y + GAP));
            if(action != null) settip(name);
        }

        public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
            over = hovering;
            return super.mousehover(ev, hovering);
        }

        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b != 1 || action == null) return false;
            action.run();
            return true;
        }

        public void draw(GOut g) {
            fill(g, Coord.z, sz, active ? SELECTED : over && action != null ? HOVER : inactive ? SURFACE : TILE);
            if(inactive) {
                for(int x = 1; x < sz.x; x += UI.scale(5)) {
                    Coord dash = Coord.of(Math.min(UI.scale(2), sz.x - x), 1);
                    fill(g, Coord.of(x, 0), dash, EDGE);
                    fill(g, Coord.of(x, sz.y - 1), dash, EDGE);
                }
                for(int y = 1; y < sz.y; y += UI.scale(5)) {
                    Coord dash = Coord.of(1, Math.min(UI.scale(2), sz.y - y));
                    fill(g, Coord.of(0, y), dash, EDGE);
                    fill(g, Coord.of(sz.x - 1, y), dash, EDGE);
                }
            } else border(g, Coord.z, sz, active || (over && action != null) ? GOLD : EDGE);
            if(thumbnail != null && !imageLoaded && thumbnail.isDone()) {
                BufferedImage image = thumbnail.getNow(null);
                if(image != null) icon = new TexI(image);
                imageLoaded = true;
            }
            if(icon != null) {
                double scale = Math.min(1, UI.scale(17.0) / Math.max(icon.sz().x, icon.sz().y));
                Coord size = icon.sz().mul(scale).max(Coord.of(1));
                g.image(icon, Coord.of(GAP, (sz.y - size.y) / 2), size);
            }
            super.draw(g);
        }

        public void dispose() {
            if(icon != null) icon.dispose();
            super.dispose();
        }
    }
}
