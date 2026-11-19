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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The game's own switches: swimming, tracking and criminal acts. A ticked one is switched on after the character appears in
 * the world, through its button in the action menu, so a character that does not have the action yet (a skill not bought)
 * is skipped, and one that gets it later is switched on when the button first shows.
 *
 * <p>The button only toggles and the client cannot read the state, but the server says it in a notice ("Swimming is now
 * turned on."), at login for what it remembers and after every press. So a switch the server already reported on is left
 * alone, one that is not reported within a few seconds is pressed, and if the answer to the press is "off" it was on
 * before and is pressed once more. Nothing is pressed more than twice in one login.
 *
 * @author Jagy2014
 */
public final class LoginToggles {
    /** One switch: the setting name, the action resource, the text shown in the settings, the start of its notice and its default. */
    public enum Toggle {
	TRACKING("tracking", "paginae/act/tracking", "Tracking", "Tracking is now turned ", true),
	SWIMMING("swimming", "paginae/act/swim", "Swimming", "Swimming is now turned ", false),
	CRIME("crime", "paginae/act/crime", "Criminal acts", "Criminal acts are now turned ", false);
	public final String key, resource, label, notice;
	private final boolean fallback;
	Toggle(String key, String resource, String label, String notice, boolean fallback) {
	    this.key = key; this.resource = resource; this.label = label; this.notice = notice; this.fallback = fallback;
	}
	String pref() {return("hearthborn/login-toggles/" + key);}
	public boolean enabled() {return(Utils.getprefb(pref(), fallback));}
	public void enabled(boolean value) {Utils.setprefb(pref(), value);}
    }

    /** Seconds the character stands in the world before anything is pressed, so the menu has been filled. */
    static final double SETTLE = 2;
    /** Seconds to wait for the server's login report of a switch before assuming it is off. */
    static final double REPORT = 5;
    /** Seconds to wait for the answer to a press. */
    static final double ANSWER = 5;

    static final class Login {
	double since = -1;
	final Map<Toggle, Boolean> reported = new EnumMap<>(Toggle.class), answers = new EnumMap<>(Toggle.class);
	final Map<Toggle, Double> pressedAt = new EnumMap<>(Toggle.class);
	final Map<Toggle, Integer> presses = new EnumMap<>(Toggle.class);
	final Set<Toggle> done = EnumSet.noneOf(Toggle.class);
    }
    private static final WeakHashMap<GameUI, Login> logins = new WeakHashMap<>();
    private static Login login(GameUI gui) {return(logins.computeIfAbsent(gui, g -> new Login()));}

    /** Reads a server notice; never consumes it. */
    public static void notice(GameUI gui, String message) {
	Toggle toggle = toggle(message);
	if(toggle != null)
	    note(login(gui), toggle, message.endsWith("on."));
    }

    /** The switch a notice such as "Swimming is now turned on." is about; null for any other text. */
    static Toggle toggle(String message) {
	if(message == null || !(message.endsWith("on.") || message.endsWith("off.")))
	    return(null);
	for(Toggle toggle : Toggle.values()) {
	    if(message.startsWith(toggle.notice))
		return(toggle);
	}
	return(null);
    }

    static void note(Login login, Toggle toggle, boolean on) {
	if(login.pressedAt.containsKey(toggle))
	    login.answers.put(toggle, on);
	else
	    login.reported.put(toggle, on);
    }

    public static void tick(GameUI gui) {
	if(gui.menu == null || gui.map == null || gui.map.player() == null)
	    return;
	Login login = login(gui);
	double now = Utils.rtime();
	if(login.since < 0)
	    login.since = now;
	if(now - login.since < SETTLE)
	    return;
	for(Toggle toggle : Toggle.values()) {
	    if(toggle.enabled() && !login.done.contains(toggle))
		step(login, toggle, now, () -> pressButton(gui, toggle.resource));
	}
    }

    /** One look at one switch; {@code presser} presses its menu button and says false when it has none yet. */
    static void step(Login login, Toggle toggle, double now, java.util.function.BooleanSupplier presser) {
	Double at = login.pressedAt.get(toggle);
	if(at != null) {
	    Boolean answer = login.answers.remove(toggle);
	    if(answer != null && answer) {
		login.done.add(toggle);
	    } else if(answer != null) {
		/* It was on and the press turned it off: press again. */
		if(login.presses.get(toggle) >= 2 || !press(presser, login, toggle, now))
		    login.done.add(toggle);
	    } else if(now - at > ANSWER) {
		login.done.add(toggle);
	    }
	    return;
	}
	Boolean reported = login.reported.get(toggle);
	if(reported != null && reported) {
	    login.done.add(toggle);
	    return;
	}
	if(reported == null && now - login.since < SETTLE + REPORT)
	    return;
	press(presser, login, toggle, now);
    }

    private static boolean press(java.util.function.BooleanSupplier presser, Login login, Toggle toggle, double now) {
	if(!presser.getAsBoolean())
	    return(false);
	login.pressedAt.put(toggle, now);
	login.presses.merge(toggle, 1, Integer::sum);
	return(true);
    }

    private static boolean pressButton(GameUI gui, String resource) {
	MenuGrid.Pagina page = page(gui, resource);
	if(page == null)
	    return(false);
	try {
	    page.button().use(new MenuGrid.Interaction());
	    return(true);
	} catch(Loading ignored) {
	    return(false);
	}
    }

    private static MenuGrid.Pagina page(GameUI gui, String resource) {
	synchronized(gui.menu.paginae) {
	    for(MenuGrid.Pagina candidate : gui.menu.paginae) {
		try {
		    Resource res = candidate.res();
		    if(res != null && res.name.equals(resource))
			return(candidate);
		} catch(Loading ignored) {
		}
	    }
	}
	return(null);
    }

    private LoginToggles() {}
}
