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

package hearthborn.gamedata;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static hearthborn.gamedata.DataValidator.*;

final class DataLoader {
    private static final int MAX_ENTRIES = 20_000;
    private static final String KEY = "[a-z][a-z0-9-]*";
    private static final String QUALIFIED = KEY + "/" + KEY;
    private static final String RESOURCE = "[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)+";

    static GameDataRepository load(Path directory) throws IOException {
        Path root = directory.toRealPath();
        DataManifest manifest = read(root, root.resolve("manifest.json"), DataManifest::parse);

        Path data = root.resolve("data");
        Path categoryFile = data.resolve("categories.json");
        Set<String> categories = read(root, categoryFile, DataLoader::categories);
        Map<String, Entity.Text> english = translations(root, "en");
        Map<String, Entity.Text> russian = Files.notExists(root.resolve("translations/ru"), LinkOption.NOFOLLOW_LINKS)
            ? Collections.emptyMap() : translations(root, "ru");
        Map<String, Entity> entries = new LinkedHashMap<>();
        for(Path file : files(root, data)) {
            if(file.equals(categoryFile)) continue;
            Entity entity = read(root, file, raw -> entity(root, data.relativize(file), raw, categories, english, russian,
                manifest.schemaVersion));
            if(entries.putIfAbsent(entity.id, entity) != null) throw invalid(file.toString(), "duplicate entity ID: " + entity.id);
        }
        if(entries.isEmpty()) throw invalid("data", "no entities");
        for(Entity entity : entries.values()) {
            for(String related : entity.related) {
                if(!entries.containsKey(related)) throw invalid(entity.id, "unknown related entity: " + related);
            }
            for(Entity.Section section : entity.sections) {
                for(Entity.Section.Step step : section.steps) {
                    if(step.entry != null && !entries.containsKey(step.entry))
                        throw invalid(entity.id, "unknown section entry: " + step.entry);
                }
            }
        }
        validateTranslationIds(english, entries, "en");
        validateTranslationIds(russian, entries, "ru");
        return new GameDataRepository(root, manifest.schemaVersion, manifest.dataVersion, categories, entries);
    }

    private static Set<String> categories(Object raw) throws IOException {
        Set<String> result = new LinkedHashSet<>(strings(raw, "categories"));
        if(result.isEmpty()) throw invalid("categories", "no categories");
        for(String category : result) {
            matching(category, "category ID", KEY + "(?:/" + KEY + ")?");
            int slash = category.indexOf('/');
            if(slash >= 0 && !result.contains(category.substring(0, slash)))
                throw invalid(category, "unknown parent category");
        }
        return result;
    }

    private static List<Path> files(Path root, Path directory) throws IOException {
        Path real = directory.toRealPath();
        if(!real.startsWith(root) || !Files.isDirectory(real)) throw invalid(directory.toString(), "invalid directory");
        List<Path> result = new ArrayList<>();
        try(Stream<Path> paths = Files.walk(directory)) {
            Iterator<Path> iterator = paths.iterator();
            while(iterator.hasNext()) {
                Path file = iterator.next();
                if(file.getFileName().toString().endsWith(".json")) {
                    if(result.size() >= MAX_ENTRIES) throw invalid(directory.toString(), "too many files");
                    result.add(file);
                }
            }
        } catch(UncheckedIOException error) {
            throw error.getCause();
        }
        Collections.sort(result);
        return result;
    }

    private static Map<String, Entity.Text> translations(Path root, String language) throws IOException {
        Path directory = root.resolve("translations").resolve(language);
        Map<String, Entity.Text> entries = new LinkedHashMap<>();
        for(Path file : files(root, directory)) {
            String id = idFromPath(directory.relativize(file));
            Entity.Text text = read(root, file, raw -> {
                Map<String, Object> object = object(raw, "translation");
                keys(object, "name description", "aliases");
                List<String> aliases = object.containsKey("aliases") ? strings(object.get("aliases"), "aliases") : Collections.emptyList();
                return new Entity.Text(string(object.get("name"), "name"), string(object.get("description"), "description"), aliases);
            });
            entries.put(id, text);
        }
        return entries;
    }

    private static void validateTranslationIds(Map<String, Entity.Text> translations, Map<String, Entity> entities,
                                               String language) throws IOException {
        for(String id : translations.keySet()) {
            if(!entities.containsKey(id)) throw invalid("translations/" + language, "unknown entity: " + id);
        }
    }

    private static String idFromPath(Path file) throws IOException {
        String name = file.getFileName().toString();
        if(file.getNameCount() != 2 || !name.endsWith(".json")) throw invalid(file.toString(), "expected category/slug.json");
        return matching(file.getName(0) + "/" + name.substring(0, name.length() - 5), "entity path", QUALIFIED);
    }

    private static Entity entity(Path root, Path file, Object raw, Set<String> categories,
                                 Map<String, Entity.Text> english, Map<String, Entity.Text> russian,
                                 int schema) throws IOException {
        Map<String, Object> object = object(raw, "entity");
        keys(object, "id category resourceIds attributes related sources status updatedAt",
            schema >= 2 ? "subcategory icon image displayType sections" : "subcategory icon image");
        String id = matching(object.get("id"), "id", QUALIFIED);
        String category = matching(object.get("category"), "category", KEY);
        if(!id.equals(idFromPath(file)) || !id.startsWith(category + "/"))
            throw invalid("id", "must match the file path and category");
        if(!categories.contains(category)) throw invalid("category", "unknown category " + category);

        String subcategory = null;
        if(object.containsKey("subcategory")) {
            subcategory = matching(object.get("subcategory"), "subcategory", QUALIFIED);
            if(!subcategory.startsWith(category + "/") || !categories.contains(subcategory))
                throw invalid("subcategory", "unknown subcategory " + subcategory);
        }
        List<String> resources = strings(object.get("resourceIds"), "resourceIds");
        for(String resource : resources) {
            matching(resource, "resource ID", RESOURCE);
            for(String segment : resource.split("/")) {
                if(segment.equals(".") || segment.equals("..")) throw invalid("resource ID", "invalid path segment");
            }
        }
        List<String> related = strings(object.get("related"), "related");
        for(String link : related) {
            matching(link, "related ID", QUALIFIED);
            if(link.equals(id)) throw invalid("related", "entry cannot link to itself");
        }

        Map<String, Entity.Text> texts = new LinkedHashMap<>();
        Entity.Text base = english.get(id);
        if(base == null) throw invalid("translations/en", "missing translation for " + id);
        texts.put("en", base);
        if(russian.containsKey(id)) texts.put("ru", russian.get(id));

        return new Entity(id, category, subcategory, resources, asset(root, object, "icon", "icons"),
            asset(root, object, "image", "images"), attributes(object.get("attributes")),
            object.containsKey("displayType") ? caption(object.get("displayType"), "displayType") : null,
            object.containsKey("sections") ? sections(id, object.get("sections")) : List.of(), related,
            sources(object.get("sources")), status(object.get("status")), date(object.get("updatedAt")), texts);
    }

    private static Entity.Caption caption(Object raw, String field) throws IOException {
        Map<String, Object> values = object(raw, field);
        keys(values, "en", "ru");
        return new Entity.Caption(string(values.get("en"), field + "/en"),
            values.containsKey("ru") ? string(values.get("ru"), field + "/ru") : null);
    }

    private static List<Entity.Section> sections(String id, Object raw) throws IOException {
        List<?> input = array(raw, "sections");
        if(input.size() > 32) throw invalid("sections", "too many sections");
        List<Entity.Section> result = new ArrayList<>();
        for(Object element : input) {
            Map<String, Object> section = object(element, "section");
            keys(section, "kind title steps", "");
            Entity.Section.Kind kind;
            switch(string(section.get("kind"), "section kind")) {
            case "chain": kind = Entity.Section.Kind.CHAIN; break;
            case "links": kind = Entity.Section.Kind.LINKS; break;
            default: throw invalid("section kind", "unknown kind");
            }
            List<?> steps = array(section.get("steps"), "section steps");
            if(steps.isEmpty() || steps.size() > 128) throw invalid("section steps", "expected 1-128 steps");
            List<Entity.Section.Step> items = new ArrayList<>();
            for(Object rawStep : steps) {
                Map<String, Object> step = object(rawStep, "section step");
                boolean link = step.containsKey("entry");
                keys(step, link ? "entry" : "label", "");
                String target = link ? matching(step.get("entry"), "section entry", QUALIFIED) : null;
                if(id.equals(target) && kind != Entity.Section.Kind.CHAIN)
                    throw invalid("section entry", "only a chain can include its own record");
                items.add(new Entity.Section.Step(target, link ? null : caption(step.get("label"), "step label")));
            }
            result.add(new Entity.Section(kind, caption(section.get("title"), "section title"), items));
        }
        return result;
    }

    private static Map<String, Object> attributes(Object raw) throws IOException {
        Map<String, Object> result = object(raw, "attributes");
        for(Map.Entry<String, Object> entry : result.entrySet()) {
            matching(entry.getKey(), "attribute key", KEY);
            Object value = entry.getValue();
            if(value instanceof List) {
                List<?> list = (List<?>)value;
                for(Object element : list) scalar(element, entry.getKey());
                entry.setValue(Collections.unmodifiableList(list));
            } else {
                scalar(value, entry.getKey());
            }
        }
        return result;
    }

    private static void scalar(Object value, String key) throws IOException {
        if(value instanceof Boolean || value instanceof BigDecimal) return;
        string(value, "attributes/" + key);
    }

    private static List<Entity.Source> sources(Object raw) throws IOException {
        List<Entity.Source> result = new ArrayList<>();
        for(Object entry : array(raw, "sources")) {
            Map<String, Object> source = object(entry, "source");
            keys(source, "title", "url license");
            String url = source.containsKey("url") ? string(source.get("url"), "source/url") : null;
            if(url != null) {
                try {
                    URI address = new URI(url);
                    if(!"https".equals(address.getScheme()) || address.getHost() == null || address.getUserInfo() != null)
                        throw invalid("source/url", "expected an HTTPS source URL");
                } catch(URISyntaxException e) {
                    throw invalid("source/url", "invalid URL");
                }
            }
            String license = source.containsKey("license") ? string(source.get("license"), "source/license") : null;
            result.add(new Entity.Source(string(source.get("title"), "source/title"), url, license));
        }
        if(result.isEmpty()) throw invalid("sources", "at least one source is required");
        return result;
    }

    private static Entity.Status status(Object raw) throws IOException {
        String value = matching(raw, "status", "verified|unverified|disputed");
        return Entity.Status.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static LocalDate date(Object raw) throws IOException {
        String value = matching(raw, "updatedAt", "[0-9]{4}-[0-9]{2}-[0-9]{2}");
        try {
            return LocalDate.parse(value);
        } catch(DateTimeParseException e) {
            throw invalid("updatedAt", "invalid calendar date");
        }
    }

    private static String asset(Path root, Map<String, Object> object, String field, String folder) throws IOException {
        if(!object.containsKey(field)) return null;
        String prefix = "assets/" + folder;
        String path = matching(object.get(field), field, prefix + "/(?:" + KEY + "/)*" + KEY + "\\.png");
        Path file = root.resolve(path).toRealPath();
        if(!file.startsWith(root.resolve(prefix)) || !Files.isRegularFile(file))
            throw invalid(field, "asset must stay under " + prefix);
        return path;
    }

    @FunctionalInterface
    private interface Decode<T> {
        T apply(Object raw) throws IOException;
    }

    private static <T> T read(Path root, Path path, Decode<T> decode) throws IOException {
        try {
            Path real = path.toRealPath();
            if(!real.startsWith(root) || !Files.isRegularFile(real)) throw invalid(path.toString(), "invalid file");
            try(Reader reader = Files.newBufferedReader(real, StandardCharsets.UTF_8)) {
                return decode.apply(JsonReader.read(reader));
            }
        } catch(IOException error) {
            throw new IOException(root.relativize(path) + ": " + error.getMessage(), error);
        }
    }
}
