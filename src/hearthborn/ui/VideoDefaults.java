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

import haven.GSettings.LightMode;
import hearthborn.ui.WheelSettings.Platform;

/**
 * The video settings a player has until they change them, chosen per operating system. A Mac runs shadows and zoned
 * lighting well; on Windows and Linux (often integrated graphics) the same picture costs far too much, so they start
 * without shadows and with simple ("Global") lighting. A value the player saved always wins, and "Reset" returns to these.
 *
 * @author Jagy2014
 */
public final class VideoDefaults {
    public static boolean shadows(Platform p) {return(p == Platform.MAC);}
    public static LightMode lighting(Platform p) {return(p == Platform.MAC ? LightMode.ZONED : LightMode.SIMPLE);}
    public static boolean vsync(Platform p) {return(true);}
    /** Frames per second of the active window; infinity means no limit. */
    public static float limit(Platform p) {return(Float.POSITIVE_INFINITY);}
    /** Frames per second of a window in the background. */
    public static float background(Platform p) {return(5f);}

    public static boolean shadows() {return(shadows(WheelSettings.platform()));}
    public static LightMode lighting() {return(lighting(WheelSettings.platform()));}
    public static boolean vsync() {return(vsync(WheelSettings.platform()));}
    public static float limit() {return(limit(WheelSettings.platform()));}
    public static float background() {return(background(WheelSettings.platform()));}

    private VideoDefaults() {}
}
