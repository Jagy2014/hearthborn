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

package hearthborn.map;

import haven.MapFile;
import haven.Message;
import haven.StreamMessage;

import java.io.IOException;
import java.io.InputStream;

import static haven.MapFile.*;

/**
 * @author Jagy2014
 */
public class MapFileUtils {
    public interface ILoader {
	boolean Load(StreamMessage data);
    }
    
    public static boolean load(MapFile file, ILoader loader, String name, Object... args) {
	InputStream fp;
	try {
	    fp = file.sfetch(name, args);
	} catch (IOException e) {
	    return false;
	}
	String fname = String.format(name, args);
	try (StreamMessage data = new StreamMessage(fp)) {
	    return loader.Load(data);
	} catch (Message.BinError e) {
	    warn(e, "error when loading '%s': %s", fname, e);
	    return false;
	}
    }
}
