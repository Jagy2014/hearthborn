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

import java.util.List;

/**
 * What the crafting window says about the ingredients. The server counts how many of each ingredient it found
 * (the "use" message), so the window only compares that with what the recipe needs.
 *
 * @author Jagy2014
 */
public final class RecipeStock {
    private RecipeStock() {}

    /** How many pieces of an ingredient are lacking. Optional ingredients and ingredients without a count never lack. */
    public static int missing(int need, int have, boolean optional) {
	return(optional || need <= 0 ? 0 : Math.max(0, need - Math.max(0, have)));
    }

    /** "have/need" as it is written under an ingredient; what is found beyond the need is not shown. */
    public static String progress(int need, int have) {
	return(Math.min(Math.max(0, have), need) + "/" + need);
    }

    /** The line under the ingredients; empty when nothing lacks. */
    public static String summary(String prefix, List<String> names, List<Integer> counts) {
	StringBuilder out = new StringBuilder();
	for(int i = 0; i < names.size(); i++) {
	    if(counts.get(i) <= 0)
		continue;
	    out.append(out.length() == 0 ? prefix + " " : ", ").append(names.get(i)).append(" ×").append(counts.get(i));
	}
	return(out.toString());
    }

    /** The amount the stepper holds: at least one. */
    public static int count(int typed) {
	return(Math.max(1, typed));
    }
}
