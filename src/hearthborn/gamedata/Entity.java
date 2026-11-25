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

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An immutable entry from a validated game data snapshot. */
public final class Entity {
    public enum Status { UNVERIFIED, VERIFIED, DISPUTED }

    public static final class Text {
        public final String name;
        public final String description;
        public final List<String> aliases;

        Text(String name, String description, List<String> aliases) {
            this.name = name;
            this.description = description;
            this.aliases = Collections.unmodifiableList(aliases);
        }
    }

    public static final class Source {
        public final String title;
        public final String url;
        public final String license;

        Source(String title, String url, String license) {
            this.title = title;
            this.url = url;
            this.license = license;
        }
    }

    public static final class Caption {
        private final String english, russian;

        Caption(String english, String russian) {
            this.english = english;
            this.russian = russian;
        }

        public String text(String language) {
            return "ru".equals(language) && russian != null ? russian : english;
        }
    }

    public static final class Section {
        public enum Kind { CHAIN, LINKS }

        public static final class Step {
            public final String entry;
            public final Caption label;

            Step(String entry, Caption label) {
                this.entry = entry;
                this.label = label;
            }
        }

        public final Kind kind;
        public final Caption title;
        public final List<Step> steps;

        Section(Kind kind, Caption title, List<Step> steps) {
            this.kind = kind;
            this.title = title;
            this.steps = Collections.unmodifiableList(steps);
        }
    }

    public final String id;
    public final String category;
    public final String subcategory;
    public final List<String> resourceIds;
    public final String icon;
    public final String image;
    public final Map<String, Object> attributes;
    public final Caption displayType;
    public final List<Section> sections;
    public final List<String> related;
    public final List<Source> sources;
    public final Status status;
    public final LocalDate updatedAt;
    private final Map<String, Text> translations;

    Entity(String id, String category, String subcategory, List<String> resourceIds, String icon, String image,
           Map<String, Object> attributes, Caption displayType, List<Section> sections,
           List<String> related, List<Source> sources, Status status,
           LocalDate updatedAt, Map<String, Text> translations) {
        this.id = id;
        this.category = category;
        this.subcategory = subcategory;
        this.resourceIds = Collections.unmodifiableList(resourceIds);
        this.icon = icon;
        this.image = image;
        this.attributes = Collections.unmodifiableMap(attributes);
        this.displayType = displayType;
        this.sections = Collections.unmodifiableList(sections);
        this.related = Collections.unmodifiableList(related);
        this.sources = Collections.unmodifiableList(sources);
        this.status = status;
        this.updatedAt = updatedAt;
        this.translations = Collections.unmodifiableMap(new LinkedHashMap<>(translations));
    }

    /** English is required by the loader; a missing locale uses the English entry. */
    public Text text(String language) {
        Text translated = translations.get(language);
        return translated == null ? translations.get("en") : translated;
    }

    Iterable<Text> translations() {
        return translations.values();
    }
}
