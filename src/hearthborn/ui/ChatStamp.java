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

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Arrival time, and a speaker when the message actually has one.
 *
 * @author Jagy2014
 */
public final class ChatStamp {
    private static volatile boolean enabled=haven.Utils.getprefb("hearthborn/chat/show-time",true);
    public static boolean enabled(){return enabled;}
    public static void enabled(boolean value){enabled=value;haven.Utils.setprefb("hearthborn/chat/show-time",value);}
    private ChatStamp() {}

    public static String now() {
        return new SimpleDateFormat("HH:mm").format(new Date());
    }

    /** The plain line, with the time in front of it. Used where one string is all there is, such as logs and tests. */
    public static String line(String at, String who, String text) {
        String body = (who == null || who.isEmpty()) ? text : who + ": " + text;
        return enabled?"[" + at + "] " + body:body;
    }

    /** The light blue of the player's own name. */
    public static final java.awt.Color OWN = new java.awt.Color(150, 195, 255);
    /** The cream of the text of a message. */
    public static final java.awt.Color TEXT = new java.awt.Color(236, 228, 208);

    /** One colour for one name, always the same: the hue comes from the name itself, away from the blue kept for oneself. */
    public static java.awt.Color nameColor(String who) {
        if(who == null) return UiTheme.light() ? Parchment.TEXT : TEXT;
        if(UiTheme.light()) return LIGHT_NAMES[Math.floorMod(who.toLowerCase(java.util.Locale.ROOT).hashCode(), LIGHT_NAMES.length)];
        int hash = who.toLowerCase(java.util.Locale.ROOT).hashCode();
        float hue = (hash * 0.61803399f) % 1f;
        if(hue < 0) hue += 1f;
        // 0.52..0.68 is the blue of one's own name.
        if(hue >= 0.52f && hue < 0.68f) hue += 0.16f;
        return new java.awt.Color(java.awt.Color.HSBtoRGB(hue % 1f, 0.5f, 0.96f));
    }

    /** The names on the light sheet: amber, green, violet and teal, dark enough to read; the blue is kept for oneself. */
    private static final java.awt.Color[] LIGHT_NAMES = {Parchment.ACCENT_TEXT, Parchment.OK, Parchment.Q_PURPLE, UiTheme.SYSTEM};
    /** One's own name on the light sheet. */
    public static final java.awt.Color LIGHT_OWN = new java.awt.Color(0x2F6A9E);

    /** The colour a speaker's name is written in: blue for oneself, otherwise the one of the name. */
    public static java.awt.Color speakerColor(String who, boolean own) {
        return own ? (UiTheme.light() ? LIGHT_OWN : OWN) : nameColor(who);
    }

    /** The text of a message for the rich-text renderer: the name in its colour, then the text. The time is a column of its own. */
    public static String markup(String who, String text, java.awt.Color whoColor) {
        String quoted = haven.RichText.Parser.quote(text);
        if(who == null || who.isEmpty()) return quoted;
        java.awt.Color c = UiTheme.readable(whoColor);
        return "$col[" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "]{" + haven.RichText.Parser.quote(who + ":") + "} " + quoted;
    }

    private static final haven.Text.Foundry STAMP_FACE = new haven.Text.Foundry(haven.Text.sans, 11).aa(true);
    private static final java.util.Map<String, haven.Text> STAMPS = new java.util.HashMap<>();
    private static int stampGeneration = -1;
    /** The time as a small dim text, kept while the colours of the interface stay. */
    public static haven.Tex stamp(String at) {
        synchronized(STAMPS) {
            if(stampGeneration != UiTheme.generation()) {
                for(haven.Text text : STAMPS.values()) text.dispose();
                STAMPS.clear();
                stampGeneration = UiTheme.generation();
            }
            return STAMPS.computeIfAbsent(at, key -> STAMP_FACE.render(key, UiPalette.text2())).tex();
        }
    }
    /** The width of the time column: the widest time, and a gap. */
    public static int column() {
        return STAMP_FACE.strsize("00:00").x + haven.UI.scale(8);
    }
    /** The height of a time, to put it level with the first line of the text. */
    public static int stampHeight() { return STAMP_FACE.height(); }

    /** Realm chat is loaded from the server and carries the speaker in {@code cn}. */
    public static boolean realm(Object message) {
        String type = message.getClass().getName();
        return type.contains("rchan") && (has(message, "cn") || has(message,"from")) && has(message, "text");
    }

    public static Object value(Object object, String name) {
        if(object==null)return null;
        try {
            Field field=find(object.getClass(),name);
            if(field==null)return null;
            field.setAccessible(true);
            return field.get(object);
        } catch(ReflectiveOperationException e) {return null;}
    }
    public static String read(Object object, String name) {
        Object value=value(object,name);
        return value instanceof String?(String)value:null;
    }
    public static boolean realmChannel(Object channel) {
        for(Class<?> type=channel.getClass();type!=null;type=type.getSuperclass()) {
            haven.FromResource source=type.getAnnotation(haven.FromResource.class);
            if(type.getName().contains(".rchan.") || (source!=null && source.name().equals("ui/rchan")))return true;
        }
        return false;
    }

    private static boolean has(Object message, String name) {
        return find(message.getClass(), name) != null;
    }

    private static Field find(Class<?> type, String name) {
        for(Class<?> at = type; at != null; at = at.getSuperclass()) {
            try { return at.getDeclaredField(name); }
            catch(NoSuchFieldException ignored) {}
        }
        return null;
    }
}
