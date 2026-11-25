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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A complete immutable view of one local hafen-data snapshot, independent of UI and network. */
public final class GameDataRepository {
    public final int schemaVersion;
    public final int dataVersion;
    private final Path root;
    private final Set<String> categories;
    private final Map<String, Entity> byId;
    private final Map<String, List<Entity>> byResource = new LinkedHashMap<>();
    private final EntitySearch search;

    public static GameDataRepository load(Path directory) throws IOException {
        return DataLoader.load(directory);
    }

    GameDataRepository(Path root, int schemaVersion, int dataVersion, Set<String> categories, Map<String, Entity> entries) {
        this.root = root;
        this.schemaVersion = schemaVersion;
        this.dataVersion = dataVersion;
        this.categories = Collections.unmodifiableSet(categories);
        this.byId = Collections.unmodifiableMap(entries);
        for(Entity entity : entries.values()) {
            for(String resource : entity.resourceIds)
                byResource.computeIfAbsent(resource, key -> new ArrayList<>()).add(entity);
        }
        byResource.replaceAll((resource, matches) -> Collections.unmodifiableList(matches));
        this.search = new EntitySearch(entries.values());
    }

    public Set<String> categories() {
        return categories;
    }

    public Entity find(String id) {
        return byId.get(id);
    }

    /** Multiple matches are returned explicitly, never resolved by choosing one arbitrarily. */
    public List<Entity> findByResource(String resource) {
        return byResource.getOrDefault(resource, Collections.emptyList());
    }

    /** Search all categories with null, or restrict to a category or subcategory ID. */
    public List<Entity> search(String query, String category, String language) {
        return search.find(query, category, language);
    }

    public Path icon(Entity entity) {
        return entity.icon == null ? null : root.resolve(entity.icon);
    }

    public Path image(Entity entity) {
        return entity.image == null ? null : root.resolve(entity.image);
    }
}
