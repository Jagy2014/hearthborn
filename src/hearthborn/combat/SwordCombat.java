/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 * See the COPYING file for details.
 */

package hearthborn.combat;

import haven.Fightsess;
import haven.Fightview;
import haven.GameUI;
import haven.Gob;
import haven.Loading;
import haven.Utils;
import hearthborn.L10n;
import hearthborn.scripts.ShortcutRunner;
import java.util.EnumSet;

/** Opt-in sword rotation for an already selected animal. */
final class SwordCombat {
    private static final String AIM = "paginae/atk/takeaim", LOW_BLOW = "paginae/atk/lowblow", CHOP = "paginae/atk/chop";
    private static final int CHOP_COST = 1;
    private static final double ACK_TIMEOUT = 5.0;
    private static final int MAX_CONSECUTIVE_TIMEOUTS = 3;
    private final EnumSet<Problem> warned = EnumSet.noneOf(Problem.class);
    private Fightview.Relation target;
    private Pending pending;
    private boolean blocked;
    private int consecutiveTimeouts;

    private enum Problem {
        SCHOOL("Sword auto-combat: add Chop and either Take Aim or Low Blow to the active combat school."),
        CHOP("Sword auto-combat: add Chop to the active combat school."),
        BUILDER("Sword auto-combat: add Take Aim or Low Blow to the active combat school."),
        TIMEOUT("Sword auto-combat paused: the server did not confirm %d moves in a row.");

        final String message;
        Problem(String message) { this.message = message; }
    }

    private static final class Pending {
        final boolean gaining;
        final int initiative;
        final double issuedAt, cooldownStarted;
        boolean timedOut;
        Pending(boolean gaining, int initiative, double issuedAt, double cooldownStarted) {
            this.gaining = gaining;
            this.initiative = initiative;
            this.issuedAt = issuedAt;
            this.cooldownStarted = cooldownStarted;
        }
        boolean confirmed(int current, double cooldown) {
            return cooldown > cooldownStarted && (gaining ? current > initiative : current < initiative);
        }
    }

    SwordCombat() {}

    void reset() {
        pause();
        warned.clear();
    }

    void pause() {
        target = null;
        pending = null;
        blocked = false;
        consecutiveTimeouts = 0;
        warned.remove(Problem.TIMEOUT);
    }

    void resume(Fightview.Relation relation) {
        if(target == relation) pause();
    }

    private void warn(GameUI gui, Problem problem, Object... args) {
        if(warned.add(problem)) gui.ui.msg(L10n.format(problem.message,args));
    }

    /** True when the sword rotation owns this tick; the ordinary picker is left alone otherwise. */
    boolean handle(Fightsess fight, Fightview view) {
        GameUI gui = fight.getparent(GameUI.class);
        Fightview.Relation current = view == null ? null : view.current;
        if(gui == null || current == null) {
            pause();
            return false;
        }
        if(ShortcutRunner.switchingWeapon(gui)) return true;

        try {
            if(!ShortcutRunner.swordInHands(gui)) {
                pause();
                return false;
            }
            if(current != target) {
                pause();
                target = current;
            }
            if(gui.vhand != null) return true;
            Gob animal = BowCombat.currentAnimal(gui);
            if(current.gst != 0 || animal == null) {
                pause();
                return true;
            }

            double now = Utils.rtime();
            int builder = -1, chop = -1;
            for(int i = 0; i < fight.actions.length; i++) {
                Fightsess.Action action = fight.actions[i];
                if(action == null) continue;
                String resource = action.res.get().name;
                if(AIM.equals(resource) || LOW_BLOW.equals(resource)) {
                    if(builder < 0 || (fight.actions[builder].ct > now && action.ct <= now)) builder = i;
                }
                if(chop < 0 && CHOP.equals(resource)) chop = i;
            }
            if(chop < 0 || builder < 0) {
                pending = null;
                blocked = false;
                consecutiveTimeouts = 0;
                warned.remove(Problem.TIMEOUT);
                warn(gui, chop < 0 && builder < 0 ? Problem.SCHOOL : chop < 0 ? Problem.CHOP : Problem.BUILDER);
                return true;
            }

            if(pending != null) {
                if(pending.confirmed(current.ip, view.atkcs)) {
                    pending = null;
                    consecutiveTimeouts = 0;
                    return true;
                }
                if(!pending.timedOut) {
                    if(now - pending.issuedAt >= ACK_TIMEOUT) {
                        pending.timedOut = true;
                        if(++consecutiveTimeouts >= MAX_CONSECUTIVE_TIMEOUTS) {
                            blocked = true;
                            pending = null;
                            warn(gui, Problem.TIMEOUT, MAX_CONSECUTIVE_TIMEOUTS);
                        }
                    }
                    return true;
                }
                // Keep looking for a late acknowledgement until the cooldown and in-use guards allow a retry.
            }
            boolean gaining = current.ip < CHOP_COST;
            int slot = gaining ? builder : chop;
            if(blocked || fight.use >= 0 || fight.useb >= 0 || now < view.atkct || now < fight.actions[slot].ct)
                return true;
            pending = new Pending(gaining, current.ip, now, view.atkcs);
            AutoCombat.tap(fight, slot, animal);
        } catch(Loading pendingResources) {
            // The deck or equipment is not ready to validate yet.
        }
        return true;
    }
}
