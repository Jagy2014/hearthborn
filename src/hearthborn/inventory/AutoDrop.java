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
import hearthborn.QualityOverlay;
import java.util.*;

/**
 * Drops freshly arrived mining items of poor quality from the inventory, one rule per kind of item:
 * the item's resource name must be in the kind's set and its quality below the kind's limit.
 * An item is judged once, when its quality is first known, so stacks are never dropped whole.
 *
 * @author Jagy2014
 */
public final class AutoDrop {
    private static final String PREFIX = "hearthborn/autodrop/";
    public static final String ENABLED = PREFIX + "enabled", CONTAINERS = PREFIX + "containers", PICKAXE = PREFIX + "pickaxe";

    /** A kind of item with its resource base names and the quality limit it starts with. */
    public enum Kind {
        STONES("Stones", 30, "gneiss", "basalt", "cinnabar", "sunstone", "dolomite", "feldspar", "flint", "granite", "hornblende",
            "limestone", "marble", "porphyry", "quartz", "sandstone", "schist", "zincspar", "apatite", "sodalite", "fluorospar",
            "soapstone", "olivine", "gabbro", "alabaster", "microlite", "mica", "kyanite", "corund", "orthoclase", "breccia",
            "diabase", "arkose", "diorite", "slate", "jasper", "rhyolite", "pegmatite", "greenschist", "eclogite", "pumice",
            "serpentine", "chert", "graywacke", "halite"),
        COALS("Coals", 30, "blackcoal", "coal"),
        ORES("Ores", 30, "cassiterite", "chalcopyrite", "malachite", "ilmenite", "limonite", "hematite", "magnetite", "peacockore",
            "leadglance", "cuprite"),
        PRECIOUS("Precious Ores", 999, "galena", "argentite", "hornsilver", "petzite", "sylvanite", "nagyagite"),
        CAT_GOLD("Cat Gold", 30, "catgold"),
        PETRIFIED_SHELL("Petrified Seashell", 30, "petrifiedshell"),
        CURIOS("Strange Crystal", 30, "strangecrystal"),
        QUARRYARTZ("Quarryartz", 30, "quarryquartz");

        public final String label;
        final int limit;
        final Set<String> names;
        Kind(String label, int limit, String... names) {
            this.label = label;
            this.limit = limit;
            this.names = new HashSet<>(Arrays.asList(names));
        }
        public boolean on() {return Utils.getprefb(PREFIX + name().toLowerCase(Locale.ROOT), false);}
        public void on(boolean value) {Utils.setprefb(PREFIX + name().toLowerCase(Locale.ROOT), value);}
        /** Items of this kind with a quality below this number are dropped. */
        public int below() {
            try {return Integer.parseInt(Utils.getpref(PREFIX + name().toLowerCase(Locale.ROOT) + "-quality", Integer.toString(limit)).trim());}
            catch(NumberFormatException broken) {return 0;}
        }
        public String limitText() {return Utils.getpref(PREFIX + name().toLowerCase(Locale.ROOT) + "-quality", Integer.toString(limit));}
        public void below(String digits) {Utils.setpref(PREFIX + name().toLowerCase(Locale.ROOT) + "-quality", digits);}
    }

    public static boolean enabled() {return Utils.getprefb(ENABLED, false);}
    public static void enabled(boolean value) {Utils.setprefb(ENABLED, value);}
    /** Turns the auto-drop on or off and says so in the message line; the settings box and the shortcut share this. */
    public static void enabled(boolean value, UI ui) {
        enabled(value);
        if(ui != null) ui.msg(hearthborn.L10n.tr(value ? "Auto-drop from the inventory is on." : "Auto-drop from the inventory is off."));
    }
    public static void toggle(UI ui) {enabled(!enabled(), ui);}
    public static boolean containers() {return Utils.getprefb(CONTAINERS, false);}
    public static void containers(boolean value) {Utils.setprefb(CONTAINERS, value);}
    public static boolean pickaxeOnly() {return Utils.getprefb(PICKAXE, false);}
    public static void pickaxeOnly(boolean value) {Utils.setprefb(PICKAXE, value);}

    /** The kind whose rule drops this item, or null. */
    static Kind rule(String baseName, double quality) {
        for(Kind kind : Kind.values())
            if(kind.on() && kind.names.contains(baseName) && kind.below() > quality) return kind;
        return null;
    }

    /** Called every tick of an item; costs a flag test once the item was judged. */
    public static void check(GItem item) {
        if(item.autoDropChecked || !enabled() || item.ui == null || item.res == null) return;
        GameUI gui = item.getparent(GameUI.class);
        if(gui == null) return;
        if(pickaxeOnly() && !hearthborn.mining.MiningSafety.pickaxe(gui)) {item.autoDropChecked = true; return;}
        if(!droppable(item, gui)) {item.autoDropChecked = true; return;}
        String base;
        Double quality;
        try {
            base = item.res.get().basename();
            quality = QualityOverlay.qualityOf(item);
        } catch(Loading pending) {return;}
        if(quality == null || quality <= 0.1) return;
        item.autoDropChecked = true;
        if(rule(base, quality) != null) item.wdgmsg("drop", Coord.z);
    }

    /** Never the equipment, the study, the cursor, a whole stack, or other containers unless asked for. */
    private static boolean droppable(GItem item, GameUI gui) {
        if(item.contentswnd != null) return false;
        Widget parent = item.parent;
        if(parent == null || parent instanceof Equipory || parent instanceof GameUI) return false;
        if(item.getparent(CharWnd.class) != null || item.getparent(hearthborn.ui.StudyWindow.class) != null) return false;
        if(containers()) return true;
        if(parent.getClass().getName().equals("haven.res.ui.stackinv.ItemStack") && parent.getparent(GItem.ContentsWindow.class) != null)
            return parent.getparent(GItem.ContentsWindow.class).cont.parent == gui.maininv;
        return parent == gui.maininv;
    }
    private AutoDrop() {}
}
