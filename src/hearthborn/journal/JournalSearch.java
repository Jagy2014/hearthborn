/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 */
package hearthborn.journal;

import hearthborn.gamedata.Entity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Presentation of repository matches; the repository remains responsible for finding records. */
final class JournalSearch {
    enum Group {
        NAME("Matches in names"), DESCRIPTION("Matches in descriptions"), OTHER("Other matches");
        final String title;
        Group(String title) { this.title = title; }
    }

    static final class Row {
        final Entity entity;
        final Group heading;
        final String excerpt;

        Row(Entity entity, Group heading, String excerpt) {
            this.entity = entity;
            this.heading = heading;
            this.excerpt = excerpt;
        }
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private final List<Pattern> terms = new ArrayList<>();
    final Pattern highlight;

    JournalSearch(String query) {
        Matcher tokens = WORD.matcher(query);
        List<String> words = new ArrayList<>();
        while(tokens.find()) {
            String word = tokens.group().toLowerCase(Locale.ROOT).replace('ё', 'е');
            if(!words.contains(word)) words.add(word);
        }
        words.sort(Comparator.comparingInt(String::length).reversed());
        List<String> patterns = new ArrayList<>();
        for(String word : words) {
            String expression = word.codePoints().mapToObj(c -> c == 'е' ? "[её]" : Pattern.quote(new String(Character.toChars(c))))
                .collect(Collectors.joining());
            terms.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            patterns.add(expression);
        }
        highlight = patterns.isEmpty() ? null : Pattern.compile(String.join("|", patterns),
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    List<Row> rows(List<Entity> matches, String language) {
        List<Row> rows = new ArrayList<>();
        if(highlight == null) {
            for(Entity entry : matches) rows.add(new Row(entry, null, ""));
            return rows;
        }
        EnumMap<Group, List<Row>> groups = new EnumMap<>(Group.class);
        for(Group group : Group.values()) groups.put(group, new ArrayList<>());
        for(Entity entity : matches) {
            Entity.Text shown = entity.text(language), alternate = entity.text(language.equals("ru") ? "en" : "ru");
            if(containsAll(shown.name)) {
                groups.get(Group.NAME).add(new Row(entity, null, ""));
                continue;
            }
            String name = matchingName(shown, alternate);
            if(name != null) {
                groups.get(Group.NAME).add(new Row(entity, null, excerpt(name)));
                continue;
            }
            String description = matchingDescription(shown, alternate);
            Group group = description == null ? Group.OTHER : Group.DESCRIPTION;
            groups.get(group).add(new Row(entity, null, description == null ? "" : excerpt(description)));
        }
        for(Group group : Group.values()) {
            List<Row> entries = groups.get(group);
            if(entries.isEmpty()) continue;
            rows.add(new Row(null, group, ""));
            rows.addAll(entries);
        }
        return rows;
    }

    private boolean containsAll(String text) {
        for(Pattern term : terms) if(!term.matcher(text).find()) return false;
        return true;
    }

    private String matchingName(Entity.Text shown, Entity.Text alternate) {
        for(String alias : shown.aliases) if(containsAll(alias)) return alias;
        if(containsAll(alternate.name)) return alternate.name;
        for(String alias : alternate.aliases) if(containsAll(alias)) return alias;
        return null;
    }

    private String matchingDescription(Entity.Text shown, Entity.Text alternate) {
        if(containsAll(shown.description)) return shown.description;
        if(containsAll(alternate.description)) return alternate.description;
        // The repository can also match a query split between the name and description.
        if(containsAll(shown.name + " " + shown.description) && highlight.matcher(shown.description).find())
            return shown.description;
        if(containsAll(alternate.name + " " + alternate.description) && highlight.matcher(alternate.description).find())
            return alternate.description;
        return null;
    }

    private String excerpt(String raw) {
        String text = raw.replaceAll("\\s+", " ").trim();
        Matcher first = highlight.matcher(text);
        if(!first.find()) return "";
        int start = Math.max(0, first.start() - 14);
        int end = Math.min(text.length(), first.end() + 53);
        if(start > 0) {
            int boundary = text.indexOf(' ', start);
            if(boundary >= 0 && boundary < first.start()) start = boundary + 1;
        }
        return (start > 0 ? "…" : "") + text.substring(start, end).trim() + (end < text.length() ? "…" : "");
    }
}
