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

package hearthborn.combat;

import haven.Coord;
import haven.Utils;

/**
 * Switches from the combat settings page.
 *
 * @author Jagy2014
 */
public final class CombatPrefs {
    private CombatPrefs() {}

    private static final String P = "hearthborn/combat/";

    private static volatile boolean cached_alt=Utils.getprefb(P+"alt-ui",true);
    public static boolean alt() { return cached_alt; }
    public static void alt(boolean on) { cached_alt=on; Utils.setprefb(P+"alt-ui",on); }

    private static volatile boolean cached_drag=Utils.getprefb(P+"drag",false);
    public static boolean drag() { return cached_drag; }
    public static void drag(boolean on) { cached_drag=on; Utils.setprefb(P+"drag",on); }

    public static Coord shift() { return new Coord(Utils.getprefi(P + "shift-x", 0), Utils.getprefi(P + "shift-y", 0)); }
    public static void shift(Coord c) {
        Utils.setprefi(P + "shift-x", c.x);
        Utils.setprefi(P + "shift-y", c.y);
    }

    private static volatile boolean cached_peace=Utils.getprefb(P+"peace",false);
    public static boolean peace() { return cached_peace; }
    public static void peace(boolean on) { cached_peace=on; Utils.setprefb(P+"peace",on); }

    private static volatile boolean cached_reaggro=Utils.getprefb(P+"reaggro",true);
    public static boolean reaggro() { return cached_reaggro; }
    public static void reaggro(boolean on) { cached_reaggro=on; Utils.setprefb(P+"reaggro",on); }

    private static volatile boolean cached_mark=Utils.getprefb(P+"mark-target",false);
    public static boolean mark() { return cached_mark; }
    public static void mark(boolean on) { cached_mark=on; Utils.setprefb(P+"mark-target",on); }

    private static volatile boolean cached_colorParty=Utils.getprefb(P+"color-party",false);
    public static boolean colorParty() { return cached_colorParty; }
    public static void colorParty(boolean on) { cached_colorParty=on; Utils.setprefb(P+"color-party",on); }
    private static volatile boolean cached_ringParty=Utils.getprefb(P+"ring-party",false);
    public static boolean ringParty() { return cached_ringParty; }
    public static void ringParty(boolean on) { cached_ringParty=on; Utils.setprefb(P+"ring-party",on); }

    private static volatile boolean cached_colorSelf=Utils.getprefb(P+"color-self",false);
    public static boolean colorSelf() { return cached_colorSelf; }
    public static void colorSelf(boolean on) { cached_colorSelf=on; Utils.setprefb(P+"color-self",on); }
    private static volatile boolean cached_ringSelf=Utils.getprefb(P+"ring-self",false);
    public static boolean ringSelf() { return cached_ringSelf; }
    public static void ringSelf(boolean on) { cached_ringSelf=on; Utils.setprefb(P+"ring-self",on); }

    private static volatile boolean cached_colorEnemy=Utils.getprefb(P+"color-enemy",false);
    public static boolean colorEnemy() { return cached_colorEnemy; }
    public static void colorEnemy(boolean on) { cached_colorEnemy=on; Utils.setprefb(P+"color-enemy",on); }
    private static volatile boolean cached_ringEnemy=Utils.getprefb(P+"ring-enemy",false);
    public static boolean ringEnemy() { return cached_ringEnemy; }
    public static void ringEnemy(boolean on) { cached_ringEnemy=on; Utils.setprefb(P+"ring-enemy",on); }

    private static volatile boolean cached_info=Utils.getprefb(P+"info",false);
    public static boolean info() { return cached_info; }
    public static void info(boolean on) { cached_info=on; Utils.setprefb(P+"info",on); }

    private static volatile int cachedHeight=Utils.getprefi(P+"info-height",20);
    public static int height() { return cachedHeight; }
    public static void height(int value) { cachedHeight=value; Utils.setprefi(P + "info-height", value); }

    private static volatile boolean cached_simple=Utils.getprefb(P+"simple-openings",true);
    public static boolean simple() { return cached_simple; }
    public static void simple(boolean on) { cached_simple=on; Utils.setprefb(P+"simple-openings",on); }

    private static volatile boolean cached_keys=Utils.getprefb(P+"keys",true);
    public static boolean keys() { return cached_keys; }
    public static void keys(boolean on) { cached_keys=on; Utils.setprefb(P+"keys",on); }

    private static volatile boolean cached_damage=Utils.getprefb(P+"damage",true);
    public static boolean damage() { return cached_damage; }
    public static void damage(boolean on) { cached_damage=on; Utils.setprefb(P+"damage",on); }

    private static volatile boolean cached_clearPlayer=Utils.getprefb(P+"clear-player",true);
    public static boolean clearPlayer() { return cached_clearPlayer; }
    public static void clearPlayer(boolean on) { cached_clearPlayer=on; Utils.setprefb(P+"clear-player",on); }

    private static volatile boolean cached_clearAll=Utils.getprefb(P+"clear-all",false);
    public static boolean clearAll() { return cached_clearAll; }
    public static void clearAll(boolean on) { cached_clearAll=on; Utils.setprefb(P+"clear-all",on); }

    private static volatile boolean cached_bars=Utils.getprefb(P+"bars",false);
    public static boolean bars() { return cached_bars; }
    public static void bars(boolean on) { cached_bars=on; Utils.setprefb(P+"bars",on); }

    private static volatile boolean cached_barsCombat=Utils.getprefb(P+"bars-combat",true);
    public static boolean barsCombat() { return cached_barsCombat; }
    public static void barsCombat(boolean on) { cached_barsCombat=on; Utils.setprefb(P+"bars-combat",on); }

    private static volatile boolean cached_lockBars=Utils.getprefb(P+"bars-lock",false);
    public static boolean lockBars() { return cached_lockBars; }
    public static void lockBars(boolean on) { cached_lockBars=on; Utils.setprefb(P+"bars-lock",on); }
}
