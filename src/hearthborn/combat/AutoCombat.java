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

package hearthborn.combat;

import haven.*;
import haven.render.Environment;
import haven.render.Render;
import hearthborn.L10n;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Picks a deck attack that matches an animal's largest opening. Never swings at players.
 *
 * @author Jagy2014
 */
public final class AutoCombat {
    private static final String PREF = "hearthborn/combat/auto-attack";
    private static final double MIN_GAP = 0.2;
    private static final String GREEN = "paginae/atk/offbalance";
    private static final String YELLOW = "paginae/atk/reeling";
    private static final String BLUE = "paginae/atk/dizzy";
    private static final String RED = "paginae/atk/cornered";
    private static final Set<String> ATTACKS = new HashSet<>();
    private static final Map<String, String> OPENING = new HashMap<>();
    private static final Map<String, Integer> COST = new HashMap<>();

    private static final Text.Foundry LABEL = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 16);

    private static Fightsess sess;
    private static long target = -1;
    private static int swing;
    private static int held = -1;
    private static int dropped = -1;
    private static Fightsess bowSession;
    private static double lastUse;
    private static String shown;
    private static Tex mark;
    private static Coord boxc, boxsz;

    static {
        put("pow", YELLOW, 0);
        put("chop", YELLOW, 1);
        put("sting", YELLOW, 2);
        put("sideswipe", YELLOW, 0);
        put("fullcircle", YELLOW, 0);
        put("barrage", YELLOW, 0);
        put("stealthunder", YELLOW, 0);
        put("lefthook", GREEN, 0);
        put("knockteeth", GREEN, 1);
        put("takedown", GREEN, 3);
        put("kick", GREEN, 1);
        put("oppknock", GREEN, 4);
        put("punchboth", GREEN, 0);
        put("uppercut", BLUE, 0);
        put("gojug", BLUE, 2);
        put("ravenbite", BLUE, 4);
        put("haymaker", RED, 0);
        put("lowblow", RED, 0);
        put("ripapart", RED, 6);
        put("cleave", RED, 4);
        put("sos", RED, 2);
    }

    private AutoCombat() {}

    public static boolean enabled() {
        return Utils.getprefb(PREF, false);
    }

    public static void enabled(boolean on) {
        Utils.setprefb(PREF, on);
        if(!on)
            release();
    }

    public static void ended() {
        bowSession = null;
        held = -1;
        dropped = -1;
        target = -1;
        sess = null;
    }

    public static void tick(Fightsess fight, Fightview fv) {
        sess = fight;
        if(BowCombat.handles(fight)){
            if(bowSession!=fight){
                bowSession=fight;
                ++swing; // Invalidate an outstanding automatic attack's render-fence callback.
                int previous=held;
                release();
                if(previous>=0&&fight.use>=0&&previous!=fight.use)rel(fight,previous);
                if(fight.useb>=0&&fight.useb!=fight.use&&fight.useb!=previous)rel(fight,fight.useb);
            }
            return;
        }
        bowSession=null;
        if(KnockoutState.unconscious(fight.getparent(GameUI.class))){release();return;}
        target = (fv != null && fv.current != null) ? fv.current.gobid : -1;
        if(!enabled()) {
            int use = fight.use;
            if(use >= 0 && use != dropped) {
                dropped = use;
                rel(fight, use);
            }
            return;
        }
        dropped = -1;
        if(fv == null || fv.current == null || fv.current.gst != 0)
            return;
        double now = Utils.rtime();
        if(fight.use >= 0) {
            if((now - lastUse) > 0.8)
                rel(fight, fight.use);
            return;
        }
        if(now < fv.atkct || (now - lastUse) < MIN_GAP)
            return;
        Gob animal = animal(fight, fv.current.gobid);
        if(animal == null)
            return;
        int idx = pick(fight, opening(fv.current), now, fv.current.ip);
        if(idx < 0)
            return;
        lastUse = now;
        tap(fight, idx, animal);
    }

    public static void draw(GOut g, Fightsess fight) {
        sess = fight;
        if(BowCombat.draw(g,fight)){boxc=null;return;}
        if(target >= 0 && animal(fight, target) == null) {
            boxc = null;
            return;
        }
        boolean on = enabled();
        String label = on ? "Auto: on" : "Auto: off";
        if(!label.equals(shown) || mark == null) {
            if(mark != null)
                mark.dispose();
            shown = label;
            BufferedImage img = Utils.outline2(LABEL.render(L10n.tr(label)).img, Color.BLACK);
            mark = new TexI(Utils.outline2(img, Color.BLACK));
        }
        Coord pad = UI.scale(new Coord(14, 6));
        boxsz = mark.sz().add(pad.mul(2));
        int rows = Math.max(1, (fight.actions.length + 4) / 5);
        int below = UI.scale(125) + ((rows - 1) * Fightsess.actpitch) + (Fightsess.actpitch / 2) + UI.scale(6);
        int swapWidth=BowCombat.swapWidth(fight);
        boxc = fight.moves.add(-(boxsz.x+swapWidth) / 2, below);
        g.chcolor(on ? new Color(16, 52, 18, 235) : new Color(36, 24, 12, 235));
        g.frect(boxc, boxsz);
        g.chcolor(on ? new Color(150, 235, 90) : new Color(235, 190, 80));
        g.rect(boxc, boxsz);
        g.rect(boxc.add(1, 1), boxsz.sub(2, 2));
        g.chcolor();
        g.image(mark, boxc.add(pad));
        if(swapWidth>0)BowCombat.drawSwap(g,fight,boxc.add(boxsz.x+UI.scale(6),0));
    }

    public static boolean click(Fightsess fight, Widget.MouseDownEvent ev) {
        if(BowCombat.clickSwap(fight,ev))return true;
        if(BowCombat.handles(fight))return BowCombat.click(fight,ev);
        if(ev.b != 1 || boxc == null || !ev.c.isect(boxc, boxsz))
            return false;
        sess = fight;
        boolean on = !enabled();
        enabled(on);
        fight.ui.msg(on ? "Animal combat assistance is on. Click Auto to turn it off." : "Animal combat assistance is off.");
        return true;
    }

    public static String tip(Coord c) {
        String swap=BowCombat.swapTip(sess,c);if(swap!=null)return swap;
        if(BowCombat.handles(sess))return BowCombat.tip(sess,c);
        if(boxc == null || !c.isect(boxc, boxsz))
            return null;
        return "Click to turn combat assistance with the current animal on or off.";
    }

    private static void tap(Fightsess fight, int idx, Gob gob) {
        int id = ++swing;
        held = idx;
        fight.wdgmsg("use", idx, 1, 0, gob.rc.floor(OCache.posres));
        try {
            Environment env = fight.ui.getenv();
            Render out = env.render();
            out.fence(() -> { if(id == swing) rel(fight, idx); });
            env.submit(out);
        } catch(RuntimeException e) {
            if(id == swing)
                rel(fight, idx);
        }
    }

    private static void release() {
        Fightsess fight = sess;
        int idx = held;
        if(fight != null && fight.use >= 0)
            idx = fight.use;
        held = -1;
        if(fight != null && idx >= 0)
            rel(fight, idx);
    }

    private static void rel(Fightsess fight, int idx) {
        if(fight == null || fight.parent == null || idx < 0)
            return;
        try {
            fight.wdgmsg("rel", idx);
        } catch(RuntimeException ignored) {}
        if(held == idx)
            held = -1;
    }

    private static Gob animal(Fightsess fight, long gobid) {
        GameUI gui = fight.getparent(GameUI.class);
        if(gui != null && gobid == gui.plid)
            return null;
        Gob gob = fight.ui.sess.glob.oc.getgob(gobid);
        if(gob == null || gob.rc == null)
            return null;
        String name = name(gob);
        if(name == null || name.startsWith("gfx/kritter/tick/") || name.startsWith("gfx/borka/") || !name.startsWith("gfx/kritter/"))
            return null;
        return gob;
    }

    private static String name(Gob gob) {
        try {
            Drawable draw = gob.getattr(Drawable.class);
            Resource res = draw == null ? null : draw.getres();
            return res == null ? null : res.name;
        } catch(Loading e) {
            return null;
        }
    }

    private static String opening(Fightview.Relation rel) {
        String best = null;
        int bestv = 0;
        for(Buff buff : rel.buffs.children(Buff.class)) {
            try {
                String name = buff.res.get().name;
                if(!GREEN.equals(name) && !YELLOW.equals(name) && !BLUE.equals(name) && !RED.equals(name))
                    continue;
                int value = meter(buff);
                if(value > bestv) {
                    bestv = value;
                    best = name;
                }
            } catch(Loading ignored) {}
        }
        return best;
    }

    private static int meter(Buff buff) {
        try {
            for(ItemInfo info : buff.info()) {
                if(info instanceof Buff.AMeterInfo)
                    return (int)Math.floor(((Buff.AMeterInfo)info).ameter() * 100);
            }
        } catch(Loading ignored) {}
        return -1;
    }

    private static int pick(Fightsess fight, String opening, double now, int ip) {
        int fallback = -1, matched = -1;
        for(int i = 0; i < fight.actions.length; i++) {
            Fightsess.Action act = fight.actions[i];
            if(act == null)
                continue;
            try {
                if(now < act.ct)
                    continue;
                Resource res = act.res.get();
                if(!ATTACKS.contains(res.name))
                    continue;
                String id = suffix(res.name);
                if(COST.getOrDefault(id, 0) > ip)
                    continue;
                if(fallback < 0)
                    fallback = i;
                if(matched < 0 && opening != null && opening.equals(OPENING.get(id)))
                    matched = i;
            } catch(Loading ignored) {}
        }
        return matched >= 0 ? matched : fallback;
    }

    private static void put(String attack, String opening, int cost) {
        OPENING.put(attack, opening);
        COST.put(attack, cost);
        ATTACKS.add("paginae/atk/" + attack);
    }

    private static String suffix(String name) {
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
}
