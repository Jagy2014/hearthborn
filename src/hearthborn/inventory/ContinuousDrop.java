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
import hearthborn.L10n;
import java.util.*;

/**
 * Drops every inventory item whose resource name is on the chosen list, while the backpack drop button is on.
 * Quality does not matter. The list is independent of {@link AutoDrop}.
 *
 * @author Jagy2014
 */
public final class ContinuousDrop {
    private static final String PREF = "hearthborn/drop-types";
    private static final Set<String> TYPES = new TreeSet<>(Utils.getprefsl(PREF, new String[0]));
    private static final WeakHashMap<GameUI, State> ACTIVE = new WeakHashMap<>();

    static final class State {
        boolean on;
        double elapsed;
        final Set<GItem> sent = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    public static Set<String> types() {return TYPES;}
    public static void save() {Utils.setprefsl(PREF, TYPES);}

    public static boolean on(GameUI gui) {
        State state = ACTIVE.get(gui);
        return state != null && state.on;
    }

    public static void stop(GameUI gui) {
        State state = ACTIVE.get(gui);
        if(state != null) {state.on = false; state.sent.clear();}
    }

    /** Turns continuous dropping on or off for the main backpack. With an empty list it opens the manager instead. */
    public static void toggle(GameUI gui) {
        if(gui == null || gui.maininv == null) return;
        State state = ACTIVE.computeIfAbsent(gui, ignored -> new State());
        if(state.on) {state.on = false; state.sent.clear(); return;}
        if(TYPES.isEmpty()) {
            AutoDropWindow.open(gui);
            gui.ui.msg(L10n.tr("Check types under “Drop while the inventory button is on”. Mining rules (Cat Gold and the rest) are separate."));
            return;
        }
        state.on = true;
        state.sent.clear();
        state.elapsed = 0;
    }

    /** Called from the backpack tools every frame; drops matching items a few times a second. */
    public static void tick(GameUI gui, double dt) {
        State state = ACTIVE.get(gui);
        if(state == null || !state.on || gui == null || gui.maininv == null || !gui.maininv.visible()) return;
        if(gui.vhand != null || TYPES.isEmpty()) return;
        state.elapsed += dt;
        if(state.elapsed < 0.4) return;
        state.elapsed = 0;
        List<WItem> items = InventoryActions.items(gui.maininv);
        Set<GItem> live = Collections.newSetFromMap(new IdentityHashMap<>());
        for(WItem item : items) live.add(item.item);
        state.sent.retainAll(live);
        for(WItem item : items) {
            try {
                if(TYPES.contains(item.item.resource().name) && !state.sent.contains(item.item)) {
                    item.item.info();
                    item.item.wdgmsg("drop", Coord.z);
                    state.sent.add(item.item);
                }
            } catch(Loading ignored) {}
        }
    }

    /** Checklist of the resources in the backpack, plus any that were already chosen. */
    public static final class TypeList extends Widget {
        private final GameUI gui;
        private List<String> resources = Collections.emptyList();
        private final Map<String, String> names = new HashMap<>();
        private double elapsed;

        public TypeList(GameUI gui) {
            super(UI.scale(260, 220));
            this.gui = gui;
            add(new SListBox<String, Widget>(sz, UI.scale(25)) {
                protected List<String> items() {return resources;}
                protected Widget makeitem(String resource, int index, Coord size) {
                    Widget row = new Widget(size);
                    row.add(new CheckBox(names.get(resource)).state(() -> TYPES.contains(resource)).set(on -> {
                        if(on) TYPES.add(resource); else TYPES.remove(resource);
                        save();
                    }), Coord.z);
                    return row;
                }
            });
            refresh();
        }

        private void refresh() {
            Map<String, String> next = new TreeMap<>();
            if(gui != null && gui.maininv != null)
                for(WItem item : InventoryActions.items(gui.maininv))
                    try {
                        String path = item.item.resource().name;
                        // Translate by resource id (gfx/invobjs/waterskin → Мех для воды), not the English basename (Waterskin → Бурдюк).
                        next.put(path, L10n.resource("tooltip", path, InventoryActions.baseName(item)));
                    } catch(Loading pending) {}
            for(String resource : TYPES)
                next.putIfAbsent(resource, L10n.resource("tooltip", resource,
                    resource.substring(resource.lastIndexOf('/') + 1).replace('-', ' ')));
            if(!next.equals(names)) {names.clear(); names.putAll(next); resources = new ArrayList<>(next.keySet());}
        }

        public void tick(double dt) {
            super.tick(dt);
            if((elapsed += dt) >= 0.3) {elapsed = 0; refresh();}
        }
    }

    private ContinuousDrop() {}
}
