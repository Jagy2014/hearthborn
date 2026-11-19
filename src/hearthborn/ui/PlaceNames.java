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

import haven.UI;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Province and realm names carried by the server location widget.
 *
 * @author Jagy2014
 */
public final class PlaceNames {
    private static final Map<UI, String[]> names = Collections.synchronizedMap(new WeakHashMap<UI, String[]>());

    public static void notice(UI ui, String type, Object[] args) {
        String[] read = read(type, args);
        if(ui != null && read != null) names.put(ui, read);
    }

    public static String province(UI ui) {
        String[] read = ui == null ? null : names.get(ui);
        return read == null ? null : read[0];
    }

    public static String realm(UI ui) {
        String[] read = ui == null ? null : names.get(ui);
        return read == null ? null : read[1];
    }

    static String[] read(String type, Object[] args) {
        if(type == null || !type.startsWith("ui/province") || args == null || args.length == 0 || !(args[0] instanceof String))
            return null;
        String realm = args.length >= 4 && args[3] instanceof String ? (String)args[3] : null;
        return new String[]{(String)args[0], realm};
    }

    private PlaceNames() {}
}
