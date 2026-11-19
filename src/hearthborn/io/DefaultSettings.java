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

package hearthborn.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The values a setting has until a player changes it. The client ships a list ({@value #RESOURCE}, written by
 * {@code tools/export_default_settings.py}); {@code Utils.getpref*} answers from it whenever nothing is saved for a key,
 * before it falls back to the default written at the call site. Nothing is copied into the saved settings, so a "Reset"
 * button, which forgets the saved values, returns to these values and not to the older defaults in the code, and a
 * value saved by the player always wins.
 *
 * <p>{@code -Dhearthborn.default-settings=off} ignores the list; the regression checks use it so they see the defaults
 * written at the call sites.
 *
 * @author Jagy2014
 */
public final class DefaultSettings {
    public static final String RESOURCE = "default-settings.properties";
    private static volatile Properties values;

    private DefaultSettings() {}

    /** The shipped value of a setting, or null when the client ships none. */
    public static String value(String key) {
	if("off".equals(System.getProperty("hearthborn.default-settings")))
	    return null;
	return all().getProperty(key);
    }

    static Properties all() {
	Properties loaded = values;
	if(loaded == null) {
	    synchronized(DefaultSettings.class) {
		if((loaded = values) == null)
		    values = loaded = load();
	    }
	}
	return loaded;
    }

    private static Properties load() {
	Properties values = new Properties();
	try(InputStream in = DefaultSettings.class.getResourceAsStream(RESOURCE)) {
	    if(in != null)
		values.load(in);
	} catch(IOException | RuntimeException ignored) {
	}
	return values;
    }
}
