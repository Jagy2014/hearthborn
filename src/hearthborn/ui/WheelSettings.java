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
 * Mouse wheel tuning kept separately for macOS, Windows and Linux. The platforms report the wheel
 * differently: a Mac sends many small fractional steps, a Windows wheel one whole step per notch.
 *
 * @author Jagy2014
 */
public final class WheelSettings {
    public enum Platform {
	MAC("mac", "macOS", 100, 100, 550),
	WINDOWS("windows", "Windows", 100, 100, 150),
	LINUX("linux", "Linux", 100, 100, 150);

	public final String id, title;
	public final int camera, rotate, mapDelay;
	Platform(String id, String title, int camera, int rotate, int mapDelay) {
	    this.id = id; this.title = title; this.camera = camera; this.rotate = rotate; this.mapDelay = mapDelay;
	}

	public static Platform of(String osName) {
	    String name = (osName == null) ? "" : osName.toLowerCase(java.util.Locale.ROOT);
	    if(name.startsWith("mac") || name.contains("darwin")) return(MAC);
	    if(name.startsWith("windows")) return(WINDOWS);
	    return(LINUX);
	}
    }

    public static final int MIN_PERCENT = 25, MAX_PERCENT = 400, MIN_DELAY = 0, MAX_DELAY = 1000;

    private static Platform platform = null;
    public static Platform platform() {
	if(platform == null)
	    platform = Platform.of(System.getProperty("hearthborn.platform", System.getProperty("os.name", "")));
	return(platform);
    }
    /** For tests: act as another platform. */
    public static void platform(Platform p) {platform = p;}

    private static String key(Platform p, String name) {return("hearthborn/wheel/" + p.id + "/" + name);}

    public static int cameraPercent(Platform p) {return(Utils.clip(Utils.getprefi(key(p, "camera"), p.camera), MIN_PERCENT, MAX_PERCENT));}
    public static int rotatePercent(Platform p) {return(Utils.clip(Utils.getprefi(key(p, "rotate"), p.rotate), MIN_PERCENT, MAX_PERCENT));}
    public static int mapDelayMs(Platform p) {return(Utils.clip(Utils.getprefi(key(p, "map-delay"), p.mapDelay), MIN_DELAY, MAX_DELAY));}
    public static void cameraPercent(Platform p, int v) {Utils.setprefi(key(p, "camera"), Utils.clip(v, MIN_PERCENT, MAX_PERCENT));}
    public static void rotatePercent(Platform p, int v) {Utils.setprefi(key(p, "rotate"), Utils.clip(v, MIN_PERCENT, MAX_PERCENT));}
    public static void mapDelayMs(Platform p, int v) {Utils.setprefi(key(p, "map-delay"), Utils.clip(v, MIN_DELAY, MAX_DELAY));}

    public static boolean inverted(Platform p) {return(Utils.getprefb(key(p, "invert"), false));}
    public static void inverted(Platform p, boolean v) {Utils.setprefb(key(p, "invert"), v);}
    /** +1 normally, -1 when the user inverted the wheel on this system. */
    public static int direction() {return(inverted(platform()) ? -1 : 1);}

    /** A camera zoom step from the wheel, in the units the camera expects. */
    public static double camera(double step) {return(step * cameraPercent(platform()) / 100.0);}
    /** A free object rotation step from the wheel. */
    public static double rotation(double step) {return(step * rotatePercent(platform()) / 100.0);}
    /** Minimum time between two map zoom levels, in seconds. */
    public static double mapDelay() {return(mapDelayMs(platform()) / 1000.0);}

    /** Adds the controls at {@code y}; returns the y below them. */
    public static int panel(Widget panel, int y) {
	Platform p = platform();
	Label head = panel.add(new Label("Mouse wheel on this system:"), Coord.of(0, y));
	panel.add(new Label(p.title), head.pos("ur").adds(6, 0));
	int column = 0;
	for(String t : new String[] {"Camera zoom speed", "Object rotation speed", "Map zoom delay"})
	    column = Math.max(column, new Label(t).sz.x);
	column += UI.scale(10);
	Widget invert = panel.add(new CheckBox("Invert mouse wheel") {{a = inverted(p);}}.changed(v -> inverted(p, v)),
				  head.pos("bl").adds(0, 6));
	invert.settip("Reverses the wheel direction everywhere: camera, minimap, lists and chat.");
	y = invert.pos("bl").adds(0, 6).y;
	y = slider(panel, "Camera zoom speed", "How far one wheel step moves the camera. Raise it if zooming feels slow.",
		   MIN_PERCENT, MAX_PERCENT, cameraPercent(p), y, column, v -> v + " %", v -> cameraPercent(p, v));
	y = slider(panel, "Object rotation speed", "Shift + wheel while placing an object.",
		   MIN_PERCENT, MAX_PERCENT, rotatePercent(p), y, column, v -> v + " %", v -> rotatePercent(p, v));
	return(slider(panel, "Map zoom delay", "Minimum pause between two minimap zoom levels. Lower it if the minimap zooms too slowly.",
	       MIN_DELAY, MAX_DELAY, mapDelayMs(p), y, column, v -> v + " ms", v -> mapDelayMs(p, v)));
    }

    private static int slider(Widget panel, String title, String tip, int min, int max, int value, int y, int column,
			      java.util.function.IntFunction<String> show, java.util.function.IntConsumer apply) {
	Label label = panel.add(new Label(title), Coord.of(0, y));
	Label readout = panel.add(new Label(show.apply(value)), Coord.of(0, y));
	HSlider slider = panel.add(new HSlider(UI.scale(150), min, max, value) {
		public void changed() {readout.settext(show.apply(val)); apply.accept(val);}
	    }, Coord.of(column, y));
	readout.move(Coord.of(slider.c.x + slider.sz.x + UI.scale(10), y));
	slider.settip(tip);
	return(y + Math.max(slider.sz.y, label.sz.y) + UI.scale(3));
    }

    private WheelSettings() {}
}
