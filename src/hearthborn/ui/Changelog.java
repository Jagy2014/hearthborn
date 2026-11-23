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

import haven.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/**
 * The changelog of the client: the same Markdown files as the documentation (docs/changelog*.md are packed
 * into the jar by the build), turned into the rich text of the game.
 *
 * @author Jagy2014
 */
public final class Changelog {
    /** The newest entry the player has been shown. */
    public static final String SEEN = "hearthborn/changelog-seen";
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");

    private Changelog() {}

    /** The text for a language; English when there is none for it. */
    public static String source(String language) {
        String text = read("/hearthborn/changelog/" + language + ".md");
        return text != null ? text : read("/hearthborn/changelog/en.md");
    }

    private static String read(String path) {
        try(InputStream in = Changelog.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException e) {
            return null;
        }
    }

    /** The heading of the newest entry, such as "v1.0.0 - Initial Release"; null when there is none. */
    public static String latest(String markdown) {
        if(markdown == null) return null;
        for(String line : markdown.split("\r?\n"))
            if(line.startsWith("## ")) return line.substring(3).trim();
        return null;
    }

    /** A new entry came since the player last looked: the first run of a version shows the window by itself. */
    public static boolean unseen(String markdown) {
        String latest = latest(markdown);
        if(latest == null) return false;
        String version = version(latest);
        return !version.equals(Utils.getpref(SEEN, ""));
    }

    public static void seen(String markdown) {
        String latest = latest(markdown);
        if(latest != null) Utils.setpref(SEEN, version(latest));
    }

    /** "v1.0.0" of "v1.0.0 - Initial Release": the name may be reworded, the version is what counts. */
    public static String version(String heading) {
        int space = heading.indexOf(' ');
        return space < 0 ? heading : heading.substring(0, space);
    }

    /** The entries as the game's rich text; what comes before the first version heading is left out. */
    public static String markup(String markdown) {
        StringBuilder out = new StringBuilder();
        boolean started = false, blank = false;
        for(String raw : markdown.split("\r?\n")) {
            String line = raw.stripTrailing();
            if(line.startsWith("## ")) {
                started = true;
                if(out.length() > 0) out.append("\n\n");
                out.append("$size[18]{$col[255,232,168]{$b{").append(RichText.Parser.quote(plain(line.substring(3)))).append("}}}\n");
                blank = true;
                continue;
            }
            if(!started) continue;
            if(line.startsWith("### ")) {
                out.append('\n').append("$size[15]{$col[222,170,70]{$b{").append(RichText.Parser.quote(plain(line.substring(4)))).append("}}}\n");
                blank = true;
            } else if(line.startsWith("- ")) {
                out.append("  \u2022 ").append(inline(line.substring(2))).append('\n');
                blank = false;
            } else if(line.isBlank() || line.equals("---")) {
                if(!blank) out.append('\n');
                blank = true;
            } else {
                out.append(inline(line)).append('\n');
                blank = false;
            }
        }
        return out.toString().stripTrailing();
    }

    /** Links and code marks give way to their text; bold stays. */
    static String plain(String text) {
        return LINK.matcher(text).replaceAll("$1").replace("`", "").replace("**", "");
    }

    static String inline(String text) {
        String clean = LINK.matcher(text).replaceAll("$1").replace("`", "");
        String quoted = RichText.Parser.quote(clean);
        return BOLD.matcher(quoted).replaceAll(m -> "\\$b{" + Matcher.quoteReplacement(m.group(1)) + "}");
    }
}
