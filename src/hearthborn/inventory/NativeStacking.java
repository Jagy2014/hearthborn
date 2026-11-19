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

package hearthborn.inventory;

import haven.*;
import java.util.*;

/**
 * The game stacks picked-up items itself when its "Stacking" toggle is on ({@code act itemcomb}). Instead of imitating that in
 * the client, this only sets the toggle to the state the player chose: the server reports "Stacking is now on/off." at login
 * and whenever the toggle changes, and a differing report is answered with one more toggle.
 *
 * @author Jagy2014
 */
public final class NativeStacking {
    private static final String KEY = "hearthborn/inventory/game-stacking";
    private static final String ACTION = "itemcomb";
    /** What the server last said about each session; absent until it has said anything. */
    private static final Map<GameUI, Boolean> reported = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<GameUI> settled = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    /** True when the player has chosen a state, so the toggle is enforced; otherwise the game's own state stays. */
    public static boolean chosen() {return Utils.getprefi(KEY, -1) >= 0;}
    public static boolean wanted() {return Utils.getprefi(KEY, -1) == 1;}

    /** What the checkbox shows: the chosen state, or what the server reported when nothing was chosen. */
    public static boolean shown(GameUI gui) {
        if(chosen()) return wanted();
        Boolean actual = gui == null ? null : reported.get(gui);
        return actual != null && actual;
    }

    /** Stores the choice and applies it at once when the server's state is known and differs. */
    public static void choose(boolean value, GameUI gui) {
        Utils.setprefi(KEY, value ? 1 : 0);
        Boolean actual = gui == null ? null : reported.get(gui);
        if(gui != null && actual != null && actual != value) gui.act(ACTION);
    }

    /** Reads a server notice; true when it is the login report that was answered with a toggle and should stay unseen. */
    public static boolean notice(GameUI gui, String message) {
        if(message == null || !message.startsWith("Stacking is now")) return false;
        boolean on = message.endsWith("on.");
        reported.put(gui, on);
        if(settled.contains(gui)) return false;
        settled.add(gui);
        if(chosen() && wanted() != on) {
            gui.act(ACTION);
            return true;
        }
        return false;
    }
    private NativeStacking() {}
}
