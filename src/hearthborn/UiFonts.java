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

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.text.AttributedCharacterIterator.Attribute;
import java.util.*;
import haven.Utils;
import hearthborn.assets.Assets;

/**
 * The client always draws text with the bundled Noto Sans; only its size is a setting.
 *
 * @author Jagy2014
 */
public final class UiFonts {
    public static final int MIN_PERCENT = 75, MAX_PERCENT = 150;
    private static final String SIZE_KEY = "hearthborn/fonts/size-percent";

    /** Saved size in percent; text widgets read it once, so a change applies after the next launch. */
    public static int percent() { return Utils.clip(Utils.getprefi(SIZE_KEY, 100), MIN_PERCENT, MAX_PERCENT); }
    public static void percent(int value) { Utils.setprefi(SIZE_KEY, Utils.clip(value, MIN_PERCENT, MAX_PERCENT)); }

    private static final String SMOOTH_KEY = "hearthborn/fonts/smooth";

    /** Saved text smoothing. Without a saved choice macOS keeps its hinted look; other systems smooth by default. */
    public static boolean smooth() { return Utils.getprefb(SMOOTH_KEY, !System.getProperty("os.name", "").startsWith("Mac")); }
    public static void smooth(boolean value) { Utils.setprefb(SMOOTH_KEY, value); }

    private static final float SCALE = percent() / 100f;
    private static final boolean SMOOTH = smooth();
    /** Whether text is drawn with antialiasing; read once at startup, so a change applies after the next launch. */
    public static boolean smoothing() { return SMOOTH; }
    public static final Font SANS = load("Regular");
    public static final Font SERIF = SANS;
    public static final Font MONO = SANS;
    public static final Font HEADING = SANS;
    static {
        load("Bold");
        load("Italic");
    }

    private static Font load(String style) {
        try(InputStream in = Assets.open("fonts/noto/NotoSans-" + style + ".ttf")) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(10f);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch(Exception error) {
            throw new IllegalStateException("Could not load bundled Noto Sans " + style, error);
        }
    }

    /** Text in tooltips is drawn this much larger than the same text elsewhere. */
    public static final float TIP_FACTOR = 1.25f;
    private static final ThreadLocal<int[]> TIPPING = ThreadLocal.withInitial(() -> new int[1]);
    /** Text rendered between {@link #beginTip} and {@link #endTip} on this thread is built for a tooltip. */
    public static void beginTip() { TIPPING.get()[0]++; }
    public static void endTip() { TIPPING.get()[0]--; }
    public static boolean tipping() { return TIPPING.get()[0] > 0; }
    private static float factor() { return tipping() ? SCALE * TIP_FACTOR : SCALE; }

    /** The font as the user's size setting draws it, larger while a tooltip is being built. */
    public static Font scaled(Font font) {
        float k = factor();
        return k == 1f ? font : font.deriveFont(font.getSize2D() * k);
    }
    /** The font at the user's size setting, never enlarged for tooltips. */
    public static Font normal(Font font) {
        return SCALE == 1f ? font : font.deriveFont(font.getSize2D() * SCALE);
    }
    /** The font as a tooltip draws it. */
    public static Font tip(Font font) {
        return font.deriveFont(font.getSize2D() * SCALE * TIP_FACTOR);
    }
    /** Rich-text attributes with the size, or the font carrying it, multiplied by the user's size setting. */
    public static Map<? extends Attribute, ?> scaled(Map<? extends Attribute, ?> attrs) {
        float k = factor();
        if(k == 1f) return attrs;
        Map<Attribute, Object> result = new HashMap<>();
        for(Map.Entry<? extends Attribute, ?> entry : attrs.entrySet()) {
            Object value = entry.getValue();
            if(entry.getKey() == TextAttribute.SIZE && value instanceof Number) value = ((Number)value).floatValue() * k;
            else if(entry.getKey() == TextAttribute.FONT && value instanceof Font) value = scaled((Font)value);
            result.put(entry.getKey(), value);
        }
        return result;
    }
    /** Rich-text font tags use the same family as labels and headings. */
    public static String family(String name) {
        if(name.equalsIgnoreCase("serif") || name.equalsIgnoreCase("monospaced") || name.equalsIgnoreCase("monospace")
            || name.equalsIgnoreCase("sans") || name.equalsIgnoreCase("sansserif") || name.equalsIgnoreCase("dialog"))
            return SANS.getFamily(Locale.ROOT);
        return name;
    }

    /** Families tried, in order, for a character the bundled Noto Sans does not have: check marks, arrows, stars, math signs. */
    private static final String[] SYMBOL_FAMILIES = {"Apple Symbols", "Segoe UI Symbol", "DejaVu Sans", "Arial Unicode MS", "Menlo", "Zapf Dingbats", "Dialog"};
    private static final Map<Integer, Font> FALLBACKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Font NONE = new Font(Font.DIALOG, Font.PLAIN, 1);
    private static final java.awt.Graphics2D SCRATCH = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();

    /** A system font that has this character, at one point; null when none of the families do. */
    public static Font fallback(int codePoint) {
        Font found = FALLBACKS.computeIfAbsent(codePoint, cp -> {
            for(String family : SYMBOL_FAMILIES) {
                Font font = new Font(family, Font.PLAIN, 1);
                if(font.canDisplay(cp)) return font;
            }
            return NONE;
        });
        return found == NONE ? null : found;
    }
    private static boolean shown(Font font, int codePoint) {
        return codePoint < 0x20 || Character.isWhitespace(codePoint) || Character.isISOControl(codePoint) || font.canDisplay(codePoint);
    }
    /** True when the font lacks a character of the text, so some of it needs a fallback. */
    public static boolean lacks(Font font, String text) {
        if(text == null || font.canDisplayUpTo(text) < 0) return false;
        for(int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if(!shown(font, cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }
    /** A stretch of text drawn in one font. */
    public static final class Run {
        public final String text; public final Font font;
        Run(String text, Font font) { this.text = text; this.font = font; }
    }
    /** The text cut into stretches: the font itself where it has the character, a fallback where it has not. */
    public static java.util.List<Run> runs(Font font, String text) {
        java.util.List<Run> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        Font using = font;
        for(int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Font wanted = font;
            if(!shown(font, cp)) {
                Font other = fallback(cp);
                if(other != null) wanted = other.deriveFont(font.getStyle() & ~Font.ITALIC, font.getSize2D());
            }
            if(wanted != using && current.length() > 0) {result.add(new Run(current.toString(), using)); current.setLength(0);}
            using = wanted;
            current.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        if(current.length() > 0) result.add(new Run(current.toString(), using));
        return result;
    }
    public static synchronized int width(Font font, String text) {
        return SCRATCH.getFontMetrics(font).stringWidth(text);
    }
    /** Gives each character the bundled font lacks a fallback font in the attributed text. */
    public static void patch(java.text.AttributedString string, String text, Font font) {
        if(font == null || !lacks(font, text)) return;
        int at = 0;
        for(Run run : runs(font, text)) {
            if(run.font != font) string.addAttribute(java.awt.font.TextAttribute.FONT, run.font, at, at + run.text.length());
            at += run.text.length();
        }
    }

    private UiFonts() {}
}
