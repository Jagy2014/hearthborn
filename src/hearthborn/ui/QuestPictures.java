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

package hearthborn.ui;

import hearthborn.l10n.Catalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The inventory picture for a thing named by a quest objective.
 *
 * @author Jagy2014
 */
public final class QuestPictures {
    private static final Map<String, String> byName = names();

    private QuestPictures() {}

    /** The living plant, when the objective names a herb rather than a picked bundle. */
    public static String growing(String inventory) {
        if(inventory == null) return null;
        String marker = "/herbs/";
        int at = inventory.indexOf(marker);
        if(at < 0 || !inventory.startsWith("gfx/invobjs/")) return null;
        return "gfx/terobjs/herbs/" + inventory.substring(at + marker.length());
    }

    /** For example, {@code Pick chives} resolves to the chives inventory sprite. */
    public static String resource(String objective) {
        if(objective == null || objective.isEmpty()) return null;
        String filet = filet(objective);
        if(filet != null) return filet;
        List<String> words = words(objective);
        for(int length = Math.min(4, words.size()); length >= 1; length--) {
            for(int start = 0; start + length <= words.size(); start++) {
                StringBuilder key = new StringBuilder();
                for(int i = start; i < start + length; i++) key.append(words.get(i));
                if(key.length() < 4) continue;
                String found = resolve(key.toString());
                if(found != null) return found;
            }
        }
        return null;
    }

    /** Keep the longest name. Otherwise "strawberries" collapses to the straw bundle. */
    private static String resolve(String key) {
        String best = null;
        int length = -1;
        String[] forms = new String[] {
            key,
            key.endsWith("ies") && key.length() > 4 ? key.substring(0, key.length() - 3) + "y" : null,
            key.endsWith("es") && key.length() > 4 ? key.substring(0, key.length() - 2) : null,
            key.endsWith("s") && key.length() > 4 ? key.substring(0, key.length() - 1) : null,
            key.endsWith("berries") && key.length() > 12 ? key.substring(0, key.length() - 7) : null
        };
        for(String form : forms) {
            if(form == null || form.length() <= length) continue;
            String found = byName.get(form);
            if(found != null) { best = found; length = form.length(); }
        }
        return best;
    }

    /** "fish" would otherwise win over "roasted fish filet". */
    private static String filet(String objective) {
        String lower = objective.toLowerCase(Locale.ROOT);
        if(!lower.contains("filet") && !lower.contains("fillet")) return null;
        if(lower.contains("roast")) return "gfx/invobjs/meat-rfilet";
        if(lower.contains("dried") || lower.contains("dry ")) return "gfx/invobjs/meat-dfilet";
        if(lower.contains("sizzl") || lower.contains("spit")) return "gfx/invobjs/meat-sfilet";
        return "gfx/invobjs/meat-filet";
    }

    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for(int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if(c == '\'' || c == '\u2019') continue;
            if(c >= 'a' && c <= 'z') word.append(c);
            else if(word.length() > 0) { words.add(word.toString()); word.setLength(0); }
        }
        if(word.length() > 0) words.add(word.toString());
        return words;
    }

    private static Map<String, String> names() {
        Map<String, String> names = new HashMap<>();
        Properties tips = Catalog.load("/hearthborn/l10n/ru/tooltip.properties");
        for(String path : tips.stringPropertyNames()) {
            if(!path.startsWith("gfx/invobjs/")) continue;
            String slug = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if(slug.length() < 4) continue;
            String current = names.get(slug);
            if(current == null || rank(path) < rank(current)) names.put(slug, path);
        }
        return names;
    }

    private static int rank(String path) {
        if(path.contains("/herbs/")) return 0;
        if(path.contains("/small/")) return 1;
        return 2;
    }
}
