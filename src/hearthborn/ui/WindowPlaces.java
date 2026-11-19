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

import haven.Coord;
import haven.GItem;
import haven.GameUI;
import haven.MapWnd;
import haven.UI;
import haven.Utils;
import haven.Widget;
import haven.Window;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Remembers where a window was last left, keyed by its caption.
 *
 * @author Jagy2014
 */
public final class WindowPlaces {
    private static final Map<Window, String> placed = Collections.synchronizedMap(new WeakHashMap<Window, String>());
    private static final Set<Window> moved = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<Window, Boolean>()));

    private WindowPlaces() {}
    /** Hover previews must not overwrite the owner's normal inventory position. */
    public static Coord positionForSave(Window window){return EquipmentInventoryPreview.savedPosition(window);}
    public static void flushPreferences(){
        try{Utils.prefs().flush();}catch(java.util.prefs.BackingStoreException error){new haven.Warning(error,"Could not save window positions").issue();}
    }


    public static void attached(Window wnd) {
        place(wnd);
    }

    public static void renamed(Window wnd) {
        place(wnd);
    }

    public static void moved(Window wnd) {
        if(wnd instanceof QuestHelper){((QuestHelper)wnd).magneticRemember();return;}
        if(boosted(wnd) || wnd.c.x < -10000)
            return;
        moved.add(wnd);
        if(wnd instanceof MapWnd) {
            ((MapWnd)wnd).storePlace();
            return;
        }
        if(wnd.cap != null) {
            placed.put(wnd, key(wnd.cap));
            Utils.setprefc(pref(wnd.cap), positionForSave(wnd));
        }
        remember(wnd);
        flushPreferences();
    }

    /** Last place of a window that is going away: its caption, and its own id when it has one. */
    public static void closed(Window wnd) {
        if(wnd instanceof QuestHelper){((QuestHelper)wnd).magneticRemember();return;}
        if(boosted(wnd) || wnd.c.x < -10000)
            return;
        if(wnd instanceof MapWnd) {
            ((MapWnd)wnd).storePlace();
            return;
        }
        if(wnd.cap != null && (moved.contains(wnd) || placed.containsKey(wnd)))
            Utils.setprefc(pref(wnd.cap), positionForSave(wnd));
        remember(wnd);
    }

    /** Puts windows back once the game view is large enough to hold the saved place. */
    public static void settle(Widget root) {
        if(root == null)
            return;
        for(Widget w = root.child; w != null; w = w.next) {
            if(w instanceof Window)
                place((Window)w);
            if(w.child != null)
                settle(w);
        }
    }

    public static boolean wasMoved(Window wnd) {
        return moved.contains(wnd);
    }

    private static void place(Window wnd) {
        // The map window has its own saved coordinate. Fitting it here
        // runs before the game window has its real size and pulls it away.
        if(wnd instanceof haven.OptWnd || wnd instanceof MapWnd || wnd instanceof QuestHelper || wnd instanceof GItem.ContentsWindow || wnd.getchild(haven.Makewindow.class)!=null)
            return;
        if(wnd.parent == null || wnd.cap == null || moved.contains(wnd) || wnd.c.x < -10000)
            return;
        if(wnd.parent.sz.x < UI.scale(200) || wnd.parent.sz.y < UI.scale(200))
            return;
        // A cupboard (and any other window the server names) keeps its own place.
        if(ownPlace(wnd))
            return;
        GameUI owner=wnd.getparent(GameUI.class);
        if(owner!=null&&owner.chat!=null&&owner.chat.detached()&&owner.chat.getparent(Window.class)==wnd)return;
        String key = key(wnd.cap);
        if(key == null || key.equals(placed.get(wnd)))
            return;
        Coord saved = Utils.getprefc(pref(wnd.cap), null);
        if(saved == null)
            return;
        wnd.move(fit(wnd, saved));
        placed.put(wnd, key);
    }

    private static boolean boosted(Window wnd) {
        return wnd instanceof MapWnd && ((MapWnd)wnd).frameBoosted;
    }

    /** Writes the per-window coordinate vanilla already reads as {@code wndc-misc}. */
    private static void remember(Window wnd) {
        GameUI gui = wnd.getparent(GameUI.class);
        if(gui == null)
            return;
        String id = gui.windowId(wnd);
        if(id != null)
            Utils.setprefc(String.format("wndc-misc/%s", id), positionForSave(wnd));
    }

    private static boolean ownPlace(Window wnd) {
        GameUI gui = wnd.getparent(GameUI.class);
        if(gui == null)
            return false;
        String id = gui.windowId(wnd);
        return id != null && Utils.getprefc(String.format("wndc-misc/%s", id), null) != null;
    }

    static String key(String caption) {
        if(caption == null || caption.isEmpty())
            return null;
        if(caption.matches("(.*)'s equipment"))
            return "Character equipment";
        if(caption.matches("(.*)'s inventory"))
            return "Character inventory";
        if(caption.matches("(.*)'s study"))
            return "Character study";
        return caption;
    }

    private static String pref(String caption) {
        String key = key(caption);
        StringBuilder safe = new StringBuilder("hearthborn/window/");
        for(int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            safe.append(Character.isLetterOrDigit(c) || c == ' ' || c == '.' || c == '-' || c == '\'' ? c : '_');
        }
        return safe.toString();
    }

    private static Coord fit(Window wnd, Coord c) {
        Coord parent = wnd.parent.sz;
        int marg = UI.scale(100);
        Coord ret = new Coord(c);
        ret.x = Math.max(ret.x, Math.min(0, marg - HudInterfaceScale.size(wnd).x));
        ret.y = Math.max(ret.y, Math.min(0, marg - HudInterfaceScale.size(wnd).y));
        ret.x = Math.min(ret.x, parent.x - Math.min(marg, HudInterfaceScale.size(wnd).x));
        ret.y = Math.min(ret.y, parent.y - Math.min(marg, HudInterfaceScale.size(wnd).y));
        return ret;
    }
}
