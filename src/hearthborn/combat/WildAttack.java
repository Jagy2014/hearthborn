/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * Licensed under the GNU Lesser General Public License, version 3.0.
 */

package hearthborn.combat;

import haven.*;
import hearthborn.L10n;
import hearthborn.scripts.ScriptRunner;
import hearthborn.scripts.ShortcutRunner;
import java.util.Map;
import java.util.WeakHashMap;

/** Activate the native attack tool, then apply it after the server arms its cursor. */
public final class WildAttack {
    private static final String ACTION = "paginae/act/atk";
    private static final String CURSOR = "gfx/hud/curs/atk";
    private static final double CURSOR_TIMEOUT = 4;
    private static final double ENGAGEMENT_TIMEOUT = 15;
    private static final double CLEAR_TIMEOUT = 2;
    private static final Map<GameUI, WildAttack> pending = new WeakHashMap<>();
    private final MapView map;
    private final Gob target;
    private double deadline = Utils.rtime() + CURSOR_TIMEOUT;
    private boolean applied;
    private boolean cancelled;
    private boolean clearing;

    private WildAttack(MapView map, Gob target) {
        this.map = map;
        this.target = target;
    }

    private static boolean valid(GameUI gui, Gob target) {
        return gui.map != null && gui.map.player() != null && target != gui.map.player()
            && gui.ui.sess.glob.oc.getgob(target.id) == target && ShortcutRunner.wildTarget(gui,target)
            && gui.vhand == null && !ShortcutRunner.switchingWeapon(gui) && !KnockoutState.unconscious(gui);
    }

    private static boolean armed(MapView map) {
        for(Widget widget = map; widget != null; widget = widget.parent) {
            if(widget.cursor == null) continue;
            try { return CURSOR.equals(widget.cursor.get().name); }
            catch(Loading ignored) { return false; }
        }
        return false;
    }

    public static boolean request(GameUI gui, Gob target) {
        if(pending.containsKey(gui) || !valid(gui,target)) return false;
        MenuGrid.PagButton action = null;
        if(gui.menu != null) {
            synchronized(gui.menu.paginae) {
                for(MenuGrid.Pagina page : gui.menu.paginae) {
                    try {
                        if(ACTION.equals(page.res().name)) {
                            action = page.button();
                            break;
                        }
                    } catch(Loading ignored) {}
                }
            }
        }
        if(action == null) {
            gui.ui.msg(L10n.tr("The Attack action is not available in the action menu."));
            return false;
        }
        ScriptRunner.cancel(gui);
        WildAttack attack = new WildAttack(gui.map,target);
        hearthborn.navigation.Walking.abandon(attack.map);
        if(!armed(attack.map)) action.use(new MenuGrid.Interaction(1,0));
        pending.put(gui,attack);
        return true;
    }

    public static void tick(GameUI gui) {
        WildAttack attack = pending.get(gui);
        if(attack == null) return;
        if(gui.map != attack.map) {
            pending.remove(gui);
            return;
        }
        boolean active = armed(attack.map);
        double now = Utils.rtime();
        boolean expired = now >= attack.deadline;
        if(attack.applied) {
            if(attack.clearing) {
                if(!active || expired) pending.remove(gui);
                return;
            }
            Fightview.Relation confirmed = attack.engagement(gui.fv);
            if(confirmed != null) AutoCombat.engagementConfirmed(confirmed);
            if(confirmed != null || expired || !valid(gui,attack.target)) {
                if(active) attack.finishCursor(now);
                else pending.remove(gui);
            }
            return;
        }
        if(!valid(gui,attack.target)) attack.cancelled = true;
        if(expired || (attack.cancelled && active)) {
            pending.remove(gui);
            if(active) attack.clearCursor();
            else if(!attack.cancelled) gui.ui.msg(L10n.tr("Shortcut action timed out."));
            return;
        }
        if(!active) return;
        attack.applied = true;
        attack.deadline = now + ENGAGEMENT_TIMEOUT;
        Gob target = attack.target;
        ClickData hit = new ClickData(new Gob.GobClick(target), null);
        attack.map.cancelPendingClicks();
        attack.map.wdgmsg("click", Utils.extend(new Object[]{Coord.z,target.rc.floor(OCache.posres),1,0},hit.clickargs()));
    }

    public static boolean cancel(GameUI gui) {
        WildAttack attack = pending.get(gui);
        if(attack == null || attack.clearing) return false;
        if(attack.applied) {
            pending.remove(gui);
            if(gui.map==attack.map && armed(attack.map)) attack.clearCursor();
            return true;
        }
        attack.cancelled = true;
        if(gui.map != attack.map || armed(attack.map)) {
            pending.remove(gui);
            if(gui.map == attack.map) attack.clearCursor();
        }
        return true;
    }

    private void clearCursor() {
        Gob player = map.player();
        if(player != null && player.rc != null)
            map.wdgmsg("click", Coord.z, player.rc.floor(OCache.posres), 3, 0);
    }

    private void finishCursor(double now) {
        clearing = true;
        deadline = now + CLEAR_TIMEOUT;
        clearCursor();
    }

    private Fightview.Relation engagement(Fightview fight) {
        if(fight!=null) for(Fightview.Relation relation : fight.lsrel)
            if(relation.gobid==target.id && !relation.invalid) return relation;
        return null;
    }

    static boolean engaging(GameUI gui) { return pending.containsKey(gui); }

    public static void manual(GameUI gui) { pending.remove(gui); }
}
