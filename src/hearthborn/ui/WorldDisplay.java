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
import java.awt.Color;

/**
 * @author Jagy2014
 */
public final class WorldDisplay {
    private static volatile boolean day = Utils.getprefb("hearthborn/world/day", false);
    private static volatile boolean clear = Utils.getprefb("hearthborn/world/no-precipitation", false);
    public static boolean day() { return day; }
    public static void day(boolean value) { day=value; Utils.setprefb("hearthborn/world/day",value); }
    public static boolean clear() { return clear; }
    public static void clear(boolean value) { clear=value; Utils.setprefb("hearthborn/world/no-precipitation",value); }
    /** Share of white mixed into the scene light, so a dark night stays readable; 0 leaves the server light alone. */
    public static final int MAX_VISION = 65;
    private static volatile int vision = Utils.clip(Utils.getprefi("hearthborn/world/night-vision", 0), 0, MAX_VISION);
    public static int vision() { return vision; }
    public static void vision(int percent) { vision=Utils.clip(percent,0,MAX_VISION); Utils.setprefi("hearthborn/world/night-vision",vision); }
    /** The colour of the grid lines on the ground (Ctrl+G): dark and faint by default, so it shows on pale paving and does not tire on grass. */
    public static final String GRID_COLOR = "hearthborn/world/grid-color";
    public static final Color GRID_DEFAULT = new Color(0, 0, 0, 70);
    public static Color gridColor() { return new Color(Utils.getprefi(GRID_COLOR, GRID_DEFAULT.getRGB()), true); }
    public static void gridColor(Color color) { Utils.setprefi(GRID_COLOR, color.getRGB()); }
    public static Color light(Color original) {
        Color lit = day ? Utils.blendcol(original, Color.WHITE, .75) : original;
        return vision > 0 ? Utils.blendcol(lit, Color.WHITE, vision / 100.0) : lit;
    }

    /** Server weather and screen effects a player may switch off, by the resource that carries them. */
    public static final class Effect {
        public final String key, resource, label, tip;
        Effect(String key, String resource, String label, String tip) { this.key=key; this.resource=resource; this.label=label; this.tip=tip; }
        public boolean hidden() { return Utils.getprefb(PREFIX+key, false); }
        public void hidden(boolean value) { Utils.setprefb(PREFIX+key, value); }
    }
    private static final String PREFIX = "hearthborn/world/effect/";
    public static final Effect[] EFFECTS = {
        new Effect("seasons","gfx/fx/seasonmap","Hide seasonal ground colors","Keeps the ground the same colour all year round."),
        new Effect("clouds","gfx/fx/clouds","Hide cloud shadows on the ground","Removes the moving shadows of clouds. Helps the frame rate."),
        new Effect("wet","gfx/fx/wet","Hide wet ground after rain","Removes the dark wet look of the ground."),
        new Effect("valhalla","gfx/fx/desat","Hide Valhalla grey filter","Keeps colours when you are dead."),
        new Effect("quake","gfx/fx/quake","Hide screen shaking","Stops the screen shaking in earthquakes and similar events."),
        new Effect("hemp","gfx/fx/lucy","Hide hemp high","Removes the visual effect of smoking hemp."),
        new Effect("opium","gfx/fx/dragon","Hide opium high","Removes the visual effect of opium."),
        new Effect("shrooms","gfx/fx/shroomed","Hide liberty cap high","Removes the flashing effect of liberty caps."),
        new Effect("drunk","gfx/fx/bottle","Hide drunken distortion","Removes the wobble when you have been drinking.")
    };
    public static boolean hiddenEffect(String resource) {
        for(Effect effect : EFFECTS) if(effect.resource.equals(resource)) return effect.hidden();
        return false;
    }
    public static boolean hide(Glob.Weather weather) {
        String type = weather.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return clear && (type.contains("rain") || type.contains("snow") || type.contains("precip"));
    }
}
