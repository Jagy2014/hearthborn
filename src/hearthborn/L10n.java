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

package hearthborn;

import haven.Utils;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import haven.Resource;
import hearthborn.l10n.Catalog;

/**
 * English source text with optional UTF-8 translation bundles.
 *
 * @author Jagy2014
 */
public final class L10n {
    private static final Properties translations = new Properties();
    private static final Map<String, Catalog> catalogs = new HashMap<>();
    private static final Properties names = Catalog.load("/hearthborn/l10n/resource-names.properties");
    private static volatile String language = System.getProperty("hearthborn.language", Utils.getpref("hearthborn/language", "en"));
    public static String language() { return language; }
    public static void setLanguage(String language) {
        if(!language.equals("en") && !language.equals("ru")) throw new IllegalArgumentException("Unsupported language");
        Utils.setpref("hearthborn/language", language);
    }
    public static void switchLanguage(String selected) {
        setLanguage(selected);
        hearthborn.l10n.Missing.flush();
        synchronized(L10n.class) {
            language = selected;
            translations.clear();
            catalogs.clear();
            loadLanguage();
        }
        Resource.refreshTranslations();
    }
    static { loadLanguage(); }
    private static void loadLanguage() {
        if(language.matches("[a-z]{2}")) {
            try(InputStream in = L10n.class.getResourceAsStream("/hearthborn/l10n/" + language + ".properties")) {
                if(in != null) translations.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch(IOException e) {
                System.err.println("Could not load Hearthborn translations: " + e.getMessage());
            }
            if(!language.equals("en")) {
                for(String context : new String[] {"button", "pagina", "action", "tooltip", "ingredient", "window", "label", "flower", "biome", "msg", "note", "quest"}) {
                    boolean patterns = context.equals("quest") || context.equals("label") || context.equals("window") || context.equals("flower") || context.equals("msg");
                    catalogs.put(context, new Catalog(Catalog.load("/hearthborn/l10n/" + language + "/" + context + ".properties"), patterns));
                }
            }
        }
    }

    public static String tr(String english) {
        return text("label", english);
    }

    private static synchronized String lookup(String context, String english) {
        String value = context.equals("label") ? translations.getProperty(english) : null;
        if(value != null) return value;
        Catalog catalog = catalogs.get(context);
        value = catalog == null ? null : (context.equals("biome") || context.equals("ingredient"))
            ? catalog.lookupCompact(english) : catalog.lookup(english);
        if(value == null && !context.equals("label")) {
            value = translations.getProperty(english);
            Catalog labels = catalogs.get("label");
            if(value == null) value = labels == null ? null : labels.lookup(english);
        }
        return value;
    }

    public static String text(String context, String english) {
        if(english == null || english.isEmpty()) return english;
        if(!language.equals("en") && english.startsWith("New discovery: "))
            return tr("New discovery:")+" "+text("tooltip",english.substring("New discovery: ".length()));
        String value = resolve(context, english);
        if(value == null && !language.equals("en")) hearthborn.l10n.Missing.record(context, english);
        return value == null ? english : value;
    }

    public static String questObjective(String english) {
        String translated = find("label", english);
        if(translated == null) translated = find("quest", english);
        return translated != null ? translated : text("label", english);
    }

    /** The translation when there is one, otherwise the text itself; a miss is not reported. */
    public static String quiet(String context, String english) {
        String value = find(context, english);
        return value == null ? english : value;
    }

    /** Lookup without recording a missing translation. */
    public static String find(String context, String english) {
        if(english == null || english.isEmpty()) return null;
        return resolve(context, english);
    }

    private static String resolve(String context, String english) {
        String value = lookup(context, english);
        if(value == null && context.equals("tooltip")) value = lookup("ingredient", english);
        if(value == null && !context.equals("biome")) value = lookup("biome", english);
        if(value == null && english.endsWith(" (paved)")) {
            String core = english.substring(0, english.length() - " (paved)".length());
            String base = lookup("biome", core);
            if(base == null) base = lookup(context, core);
            if(base != null) {
                String mark = lookup("label", "paved");
                value = base + " (" + (mark == null ? "paved" : mark) + ")";
            }
        }
        if(value == null && context.equals("label") && english.endsWith(")")) {
            int suffix = english.lastIndexOf(" (");
            if(suffix > 0) {
                String base = resolve(context, english.substring(0, suffix));
                String hint = english.substring(suffix + 1);
                String translated = lookup(context, hint);
                if(base != null) value = base + " " + (translated == null ? hint : translated);
            }
        }
        if(value == null && context.equals("ingredient")) {
            java.util.regex.Matcher count = java.util.regex.Pattern.compile("^(.*?)( [\\(][×x][0-9]+[\\)])$").matcher(english);
            if(count.matches()) {
                String base = lookup(context, count.group(1));
                if(base != null) value = base + count.group(2);
            }
        }
        if(value == null && context.equals("ingredient")) {
            String withoutArticle = english.replaceFirst("(?i)^an?\\s+", "");
            if(!withoutArticle.equals(english)) value = lookup(context, withoutArticle);
        }
        if(value == null && context.equals("tooltip")) value = edged(english);
        return value;
    }

    /** "Wear: 12" uses the translation of "Wear" and keeps the numbers. */
    private static String edged(String english) {
        int start = 0, end = english.length();
        while(start < end && !Character.isLetter(english.charAt(start))) start++;
        while(end > start && !Character.isLetter(english.charAt(end - 1))) end--;
        if(start == end || (start == 0 && end == english.length())) return null;
        String core = english.substring(start, end);
        String translated = lookup("tooltip", core);
        if(translated == null) translated = lookup("ingredient", core);
        if(translated == null) translated = lookup("label", core);
        if(translated == null) return null;
        return english.substring(0, start) + translated + english.substring(end);
    }

    private static final String STACK_HELP =
        "$font[serif,16]{4. Stacks}\n" +
        "To stack items, $col[246,229,34]{Right-click} one item onto another, and...\n" +
        "• Hold $col[246,229,34]{Shift} to include all other available items in the stack.\n" +
        "• Hold $col[246,229,34]{Ctrl+Shift} to make stacks of all such available items.\n\n" +
        "On the stacks themselves...\n" +
        "• $col[246,229,34]{Shift+Right-click} to move one item out of the stack.\n" +
        "• $col[246,229,34]{Ctrl+Shift+Right-click} to move all items out of the stack.";

    /** Help pages receive the stack section in English. A Russian note replaces it when that catalog is loaded. */
    public static String note(String id) {
        String english = "paginae/halp/ui".equals(id) || "gfx/hud/halp/ui".equals(id) ? STACK_HELP : "";
        return resource("note", id, english);
    }

    public static synchronized String resource(String context, String id, String english) {
        Catalog catalog = catalogs.get(context);
        String value = catalog == null ? null : catalog.lookup(id);
        if(value == null && context.equals("tooltip")) {
            Catalog actions = catalogs.get("action");
            value = actions == null ? null : actions.lookup(id);
            if(value == null && id != null && id.startsWith("gfx/tiles/")) {
                value = lookup("biome", names.getProperty(id, english));
                if(value == null) value = lookup("biome", english);
            }
        }
        return value == null ? text(context, english) : value;
    }

    public static String objectName(Resource res, String fallback) {
        Resource.Tooltip tooltip = res.layer(Resource.tooltip);
        String english = names.getProperty(res.name, tooltip == null ? fallback : tooltip.original);
        String boulders = "gfx/terobjs/bumlings/";
        if(res.name.startsWith(boulders)) {
            String exact = lookup("tooltip", res.name);
            if(exact != null) return exact;
            String material = res.name.substring(boulders.length()).replaceFirst("[0-9]+$", "");
            String translated = lookup("tooltip", "gfx/invobjs/" + material);
            if(translated != null) return translated;
        }
        if(res.name.startsWith("gfx/tiles/")) {
            String biome = lookup("biome", english);
            if(biome != null) return biome;
        }
        return resource("tooltip", res.name, english);
    }

    public static String format(String english, Object... args) {
        return String.format(Locale.ROOT, tr(english), args);
    }

    private L10n() {}
}
