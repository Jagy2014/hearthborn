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

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the action list knows about the ingredients of a recipe: the last time the recipe was open, its crafting
 * window counted how many of the ingredients were at hand. A recipe never opened has no status.
 *
 * @author Jagy2014
 */
public final class RecipeStatus {
    private RecipeStatus() {}

    public static final class State {
	/** Ingredients that are at hand in full, and all the ingredients the recipe needs. */
	public final int have, total;
	State(int have, int total) {this.have = have; this.total = total;}
	public boolean ready() {return(have >= total);}
    }

    private static final Map<String, State> known = new ConcurrentHashMap<>();

    private static String key(String name) {
	return(ActionBrowser.normalized(name));
    }

    private static volatile int version = 0;

    /** Changes whenever a status does, so that what was worked out from the statuses is only redone then. */
    public static int version() {
	return(version);
    }

    public static void record(String recipe, int have, int total) {
	if(recipe == null || recipe.isEmpty() || total <= 0)
	    return;
	State state = new State(Math.max(0, Math.min(have, total)), total);
	State old = known.put(key(recipe), state);
	if(old == null || old.have != state.have || old.total != state.total)
	    version++;
    }

    /** By either name of the action: the server's and the one shown. */
    public static State of(String english, String shown) {
	State state = known.get(key(english));
	return((state != null || shown == null) ? state : known.get(key(shown)));
    }

    public static void forget() {
	known.clear();
	version++;
    }

    /** Ready recipes first, then the unknown ones, then those that lack something. */
    public static int order(State state) {
	if(state == null)
	    return(1);
	if(state.ready())
	    return(0);
	return(state.have > 0 ? 2 : 3);
    }

    public static String text(State state) {
	if(state == null)
	    return("");
	return(state.ready() ? "\u2713" : state.have + "/" + state.total);
    }

    public static Color color(State state) {
	if(state == null || state.have == 0 && !state.ready())
	    return(new Color(150, 145, 130));
	return(state.ready() ? new Color(110, 215, 110) : new Color(235, 105, 95));
    }
}
