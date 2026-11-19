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

/**
 * One click that lowers the graphics load, for laptops with integrated graphics whose fans run flat out.
 *
 * @author Jagy2014
 */
public final class EconomyMode {
    public static final float FPS = 30f;

    public static GSettings apply(GSettings prefs) {
	return(prefs.update(null, prefs.lshadow, false)
	       .update(null, prefs.hz, FPS)
	       .update(null, prefs.lightmode, GSettings.LightMode.SIMPLE)
	       .update(null, prefs.maxlights, 0));
    }

    public static boolean active(GSettings prefs) {
	return(!prefs.lshadow.val && prefs.hz.val <= FPS);
    }

    /** The values economy mode changes, back at their defaults. Other video settings, the render scale among them, are left alone. */
    public static GSettings standard(GSettings prefs) {
	GSettings def = GSettings.defaults();
	return(prefs.update(null, prefs.lshadow, def.lshadow.val)
	       .update(null, prefs.hz, def.hz.val)
	       .update(null, prefs.lightmode, def.lightmode.val)
	       .update(null, prefs.maxlights, def.maxlights.val));
    }

    private static boolean isStandard(GSettings prefs) {
	GSettings def = GSettings.defaults();
	return(prefs.lshadow.val == def.lshadow.val && prefs.hz.val.equals(def.hz.val)
	       && prefs.lightmode.val == def.lightmode.val && prefs.maxlights.val.equals(def.maxlights.val));
    }

    /** The saved video profiles offered in the video settings. */
    public enum Profile {
	STANDARD("Standard"), ECONOMY("Economy mode"), CUSTOM("Custom settings");
	public final String label;
	Profile(String label) {this.label = label;}

	/** Which profile the settings amount to. */
	public static Profile of(GSettings prefs) {
	    return(active(prefs) ? ECONOMY : isStandard(prefs) ? STANDARD : CUSTOM);
	}

	/** The settings after choosing this profile; "custom" is only a description and changes nothing. */
	public GSettings apply(GSettings prefs) {
	    return(this == ECONOMY ? EconomyMode.apply(prefs) : this == STANDARD ? standard(prefs) : prefs);
	}
    }

    private EconomyMode() {}
}
