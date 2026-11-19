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

package hearthborn.l10n;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Context-specific, whole-phrase translation with deterministic rule ordering.
 *
 * @author Jagy2014
 */
public final class Catalog {
    private static final Pattern SLOT = Pattern.compile("(?<!\\\\)([$@])(\\d+)");
    private final Map<String, String> exact = new HashMap<>();
    private final Map<String, String> folded = new HashMap<>();
    private final Map<String, String> compact = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();
    private final Map<String, String> cache = Collections.synchronizedMap(new LinkedHashMap<String, String>(128, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, String> entry) { return size() > 2048; }
    });
    private static final class Rule {
        final Pattern pattern;
        final String value, source;
        Rule(String source, String value) {
            this.source = source; this.value = value;
            pattern = Pattern.compile("(?:" + source + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
    }

    public static Properties load(String path) {
        Properties result = new Properties();
        try(InputStream in = Catalog.class.getResourceAsStream(path)) {
            if(in != null) result.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch(IOException e) { System.err.println("Could not load translations " + path + ": " + e.getMessage()); }
        String relative = path.startsWith("/hearthborn/l10n/") ? path.substring("/hearthborn/".length()) : null;
        if(relative != null) {
            java.nio.file.Path override = java.nio.file.Paths.get(relative);
            if(java.nio.file.Files.isRegularFile(override)) {
                try(Reader in = java.nio.file.Files.newBufferedReader(override, StandardCharsets.UTF_8)) { result.load(in); }
                catch(IOException e) { System.err.println("Could not load translation overrides: " + e.getMessage()); }
            }
        }
        return result;
    }

    public Catalog(Properties values, boolean patterns) {
        List<String> keys = new ArrayList<>(values.stringPropertyNames());
        Collections.sort(keys);
        for(String key : keys) {
            String value = values.getProperty(key);
            exact.put(key, value);
            if(!patterns) {
                folded.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
                compact.putIfAbsent(key.replaceAll("[\\s_-]+", "").toLowerCase(Locale.ROOT), value);
            }
            if(patterns) {
                try {
                    Rule rule = new Rule(key, value);
                    if(rule.pattern.matcher("").groupCount() == 0 && !key.contains(".*") && !key.contains(".+") && !key.contains("[") && !key.contains("\\d") && !key.contains("\\w") && !key.contains("\\s") && !key.contains("(?:")) {
                        String literal = key.replaceAll("\\\\(.)", "$1");
                        exact.put(literal, value);
                        folded.putIfAbsent(literal.toLowerCase(Locale.ROOT), value);
                    } else rules.add(rule);
                } catch(PatternSyntaxException e) {
                    System.err.println("Invalid translation rule: " + key);
                }
            }
        }
        rules.sort(Comparator.comparingInt((Rule r) -> r.pattern.matcher("").groupCount())
                             .thenComparing(Comparator.comparingInt((Rule r) -> r.source.length()).reversed())
                             .thenComparing(r -> r.source));
    }

    public String lookupCompact(String source) {
        String value = lookup(source);
        return value != null || source == null ? value : compact.get(source.replaceAll("[\\s_-]+", "").toLowerCase(Locale.ROOT));
    }

    public String lookup(String source) {
        if(source == null || source.isEmpty()) return null;
        if(!source.matches("(?s).*[A-Za-z].*")) return null;
        String value = exact.get(source);
        if(value != null) return value;
        value = folded.get(source.toLowerCase(Locale.ROOT));
        if(value != null) return value;
        value = cache.get(source);
        if(value != null) return value.equals(source) ? null : value;
        for(Rule rule : rules) {
            Matcher match = rule.pattern.matcher(source);
            if(!match.matches()) continue;
            Matcher slots = SLOT.matcher(rule.value);
            StringBuffer result = new StringBuffer();
            while(slots.find()) {
                int group = Integer.parseInt(slots.group(2));
                String replacement = group <= match.groupCount() ? match.group(group) : slots.group();
                if(replacement == null) replacement = "";
                if(slots.group(1).equals("$") && group <= match.groupCount())
                    replacement = hearthborn.L10n.text("ingredient", replacement);
                slots.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
            slots.appendTail(result);
            value = result.toString(); cache.put(source, value); return value;
        }
        cache.put(source, source); return null;
    }
}
