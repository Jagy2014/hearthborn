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

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class EntitySearch {
    private final List<Entry> english;
    private final List<Entry> russian;

    EntitySearch(Collection<Entity> entities) {
        english = new ArrayList<>(entities.size());
        for(Entity entity : entities) english.add(new Entry(entity));
        russian = new ArrayList<>(english);
        sort(english, "en", Locale.ENGLISH);
        sort(russian, "ru", Locale.forLanguageTag("ru"));
    }

    private static void sort(List<Entry> entries, String language, Locale locale) {
        Collator names = Collator.getInstance(locale);
        entries.sort(Comparator.comparing((Entry entry) -> entry.entity.text(language).name, names)
            .thenComparing(entry -> entry.entity.id));
    }

    List<Entity> find(String query, String category, String language) {
        String normalized = normalize(query == null ? "" : query);
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split(" ");
        List<Entity> exact = new ArrayList<>();
        List<Entity> prefixes = new ArrayList<>();
        List<Entity> other = new ArrayList<>();
        for(Entry entry : "ru".equals(language) ? russian : english) {
            Entity entity = entry.entity;
            if(category != null && !category.equals(entity.category) && !category.equals(entity.subcategory)) continue;
            switch(entry.score(normalized, words)) {
            case 2: exact.add(entity); break;
            case 1: prefixes.add(entity); break;
            case 0: other.add(entity); break;
            }
        }
        exact.addAll(prefixes);
        exact.addAll(other);
        return Collections.unmodifiableList(exact);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё', 'е')
            .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static final class Entry {
        final Entity entity;
        final List<String> names = new ArrayList<>();
        final String searchable;

        Entry(Entity entity) {
            this.entity = entity;
            StringBuilder text = new StringBuilder(entity.id).append(' ').append(String.join(" ", entity.resourceIds));
            for(Entity.Text translation : entity.translations()) {
                names.add(normalize(translation.name));
                for(String alias : translation.aliases) names.add(normalize(alias));
                text.append(' ').append(translation.description);
            }
            searchable = String.join(" ", names) + ' ' + normalize(text.toString());
        }

        int score(String query, String[] words) {
            if(words.length == 0) return 0;
            for(String word : words) {
                if(!searchable.contains(word)) return -1;
            }
            if(names.contains(query)) return 2;
            for(String name : names) {
                if(name.startsWith(query)) return 1;
            }
            return 0;
        }
    }

}
