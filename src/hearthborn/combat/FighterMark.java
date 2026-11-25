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
import haven.render.BaseColor;
import haven.render.Homo3D;
import haven.render.MixColor;
import haven.render.Pipe;
import haven.render.RenderTree;
import haven.render.States;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Initiative, openings, damage and combat highlights drawn on a fighter.
 *
 * @author Jagy2014
 */
public final class FighterMark extends GAttrib implements RenderTree.Node, PView.Render2D, Gob.SetupMod {
    private static final Map<Long, Fightview.Relation> live = new ConcurrentHashMap<>();
    private static final Map<Long, int[]> damage = new ConcurrentHashMap<>();
    private static final int SOFT = 61455, HARD = 64527, ARMOR = 36751;
    private static final Color SELF = new Color(2, 253, 177, 180);
    private static final Color LEADER = new Color(16, 64, 255, 180);
    private static final Color PARTY = new Color(16, 255, 16, 180);
    private static final Color ENEMY = new Color(246, 86, 153, 180);
    private static final Color TARGET = new Color(255, 0, 0, 200);
    private static final Color PREVIEW = new Color(255, 220, 75, 220);
    private static final Color[] OPEN = {
        new Color(210, 210, 64), new Color(81, 165, 56), new Color(39, 82, 191), new Color(192, 28, 28)
    };
    private static final String[] OPEN_NAME = {
        "paginae/atk/reeling", "paginae/atk/offbalance", "paginae/atk/dizzy", "paginae/atk/cornered"
    };
    private static final Text.Foundry numf = new Text.Foundry(Text.mono.deriveFont(java.awt.Font.BOLD), 14);
    private static volatile GameUI gui;
    /** Keep compact combat UI while a fled animal is being re-attacked. */
    private static double holdLayoutUntil;
    /** Ground band: inner radius 4, thickness 1.75. */
    private static final FastMesh RING = band(4f, 5.75f);
    private static final Pipe.Op RING_STATE = Pipe.Op.compose(
        new States.Facecull(States.Facecull.Mode.NONE), States.maskdepth, MapMesh.postmap, new IgnoreClicks());
    private MixColor tint;
    private Color tinted;
    private Color ringColor;
    private Gob.Overlay ring;
    private volatile boolean preview;
    private Gob.Overlay previewArrow;
    private int[] shown;
    private Tex damageTex;

    public FighterMark(Gob gob) { super(gob); }

    void preview(boolean marked) { preview = marked; }

    public static void bind(GameUI game) { gui = game; }

    public static void track(Fightview.Relation rel) { live.put(rel.gobid, rel); }

    public static void drop(long id) { live.remove(id); }

    public static void seen(Gob gob, Indir<Resource> res, byte[] sdt) {
        if((gob == null) || (res == null) || (sdt == null) || (sdt.length < 7))
            return;
        gob.defer(() -> read(gob, res, sdt, 0));
    }

    private static void read(Gob gob, Indir<Resource> res, byte[] sdt, int try_) {
        Resource loaded;
        try {
            loaded = res.get();
        } catch(Loading l) {
            if(try_ < 4)
                gob.defer(() -> read(gob, res, sdt, try_ + 1));
            return;
        }
        if(!"gfx/fx/floatimg".equals(loaded.name))
            return;
        MessageBuf msg = new MessageBuf(sdt);
        int value = msg.int32();
        msg.uint8();
        int color = msg.uint16();
        int[] row = damage.computeIfAbsent(gob.id, id -> new int[3]);
        if(color == SOFT)
            row[0] += value;
        else if(color == HARD)
            row[1] += value;
        else if(color == ARMOR)
            row[2] += value;
    }

    public static void clearPlayer() {
        if(gui != null)
            damage.remove(gui.plid);
    }

    public static void clearAll() { damage.clear(); }

    /** True briefly after an animal flees while re-aggro is on, so compact UI does not flicker off. */
    public static boolean holdingLayout() {
        return CombatPrefs.reaggro() && Utils.rtime() < holdLayoutUntil;
    }

    public static void reaggro(long gobid) {
        GameUI game = gui;
        if((game == null) || (game.map == null) || KnockoutState.unconscious(game))
            return;
        Gob target = game.ui.sess.glob.oc.getgob(gobid);
        Gob player = game.map.player();
        if((target == null) || (player == null) || (target.rc == null) || (player.rc == null))
            return;
        // Cover ranger-bow reach; animals often step out of melee before the del arrives.
        if(player.rc.dist(target.rc) > ArcheryAim.RANGERS_BOW + 20)
            return;
        GobHealth health = target.getattr(GobHealth.class);
        if((health != null) && (health.hp <= 0))
            return;
        Drawable draw = target.getattr(Drawable.class);
        try {
            Resource res = (draw == null) ? null : draw.getres();
            if((res == null) || !res.name.contains("gfx/kritter/") || res.name.startsWith("gfx/kritter/tick/"))
                return;
        } catch(Loading l) {
            return;
        }
        holdLayoutUntil = Utils.rtime() + 4;
        // Same sequence as the aggro hotkeys: raise attack, click the animal, clear the cursor.
        game.act("atk");
        Coord at = target.rc.floor(OCache.posres);
        game.map.wdgmsg("click", Coord.z, at, 1, 0, 0, (int)target.id, at, 0, -1);
        game.map.wdgmsg("click", Coord.z, player.rc.floor(OCache.posres), 3, 0);
    }

    private Color look(boolean ring) {
        if((gui == null) || !Fightsess.active)
            return(null);
        boolean me = gob.id == gui.plid;
        boolean member = gob.glob.party.memb.containsKey(gob.id);
        boolean leader = (gob.glob.party.leader != null) && (gob.glob.party.leader.gobid == gob.id);
        boolean current = (gui.fv != null) && (gui.fv.current != null) && (gui.fv.current.gobid == gob.id);
        if(me)
            return((ring ? CombatPrefs.ringSelf() : CombatPrefs.colorSelf()) ? SELF : null);
        if(member) {
            boolean on = ring ? CombatPrefs.ringParty() : CombatPrefs.colorParty();
            if(!on)
                return(null);
            return(leader ? LEADER : PARTY);
        }
        if(!(ring ? CombatPrefs.ringEnemy() : CombatPrefs.colorEnemy()))
            return(null);
        if(current)
            return(TARGET);
        return(live.containsKey(gob.id) ? ENEMY : null);
    }

    public void ctick(double dt) {
        Color combatRing = look(true);
        Color next = preview && !TARGET.equals(combatRing) ? PREVIEW : combatRing;
        if((next == null) || ((ringColor != null) && !ringColor.equals(next)))
            clearRing();
        if((next != null) && (ring == null)) {
            ringColor = next;
            ring = new Gob.Overlay(gob, new Band(gob, next));
            gob.addol(ring, false);
        }
        boolean combatArrow = preview && gui != null && Fightsess.active && gui.fv != null && gui.fv.current != null
            && gui.fv.current.gobid == gob.id && gui.fv.lsrel.size() > (CombatPrefs.mark() ? 0 : 1);
        if(preview && !combatArrow) {
            if(previewArrow == null) {
                try {
                    Gob.Overlay arrow = new Gob.Overlay(gob, new PreviewArrow(gob));
                    gob.addol(arrow, false);
                    previewArrow = arrow;
                } catch(Loading pending) {}
            }
        } else if(previewArrow != null) {
            Sprite sprite = previewArrow.spr;
            previewArrow.remove(false);
            previewArrow = null;
            gob.defer(sprite::dispose);
        }
    }

    private void clearRing() {
        Gob.Overlay overlay = ring;
        ring = null;
        ringColor = null;
        if(overlay == null)
            return;
        Sprite sprite = overlay.spr;
        overlay.remove(false);
        if(sprite != null)
            gob.defer(sprite::dispose);
    }

    public Pipe.Op gobstate() {
        Color color = look(false);
        if(color == null) {
            tint = null;
            tinted = null;
            return(null);
        }
        if((tint == null) || !color.equals(tinted)) {
            tinted = color;
            tint = new MixColor(color);
        }
        return(tint);
    }

    public void draw(GOut g, Pipe state) {
        boolean showDamage=CombatPrefs.damage()&&damage.containsKey(gob.id);
        Fightview.Relation rel=live.get(gob.id);
        boolean showInfo=CombatPrefs.info()&&rel!=null&&!rel.invalid;
        if(!showDamage&&!showInfo)return;
        Coord at=view(g,state,CombatPrefs.height());
        if(at==null)return;
        if(showDamage)drawDamage(g,at);
        if(showInfo)drawInfo(g,at,rel);
    }

    private void drawDamage(GOut g, Coord at) {
        int[] row = damage.get(gob.id);
        if((row == null) || ((row[0] == 0) && (row[1] == 0) && (row[2] == 0)))
            return;
        if((damageTex == null) || (shown == null) || (shown[0] != row[0]) || (shown[1] != row[1]) || (shown[2] != row[2])) {
            shown = new int[] {row[0], row[1], row[2]};
            java.awt.image.BufferedImage[] parts = new java.awt.image.BufferedImage[3];
            if(row[0] > 0)
                parts[0] = Text.render(Integer.toString(row[0]), Utils.col16(SOFT)).img;
            if(row[1] > 0)
                parts[1] = Text.render(Integer.toString(row[1]), Utils.col16(HARD)).img;
            if(row[2] > 0)
                parts[2] = Text.render(Integer.toString(row[2]), Utils.col16(ARMOR)).img;
            damageTex = new TexI(ItemInfo.catimgsh(UI.scale(3), parts));
        }
        g.aimage(damageTex, at.sub(0, UI.scale(18)), 0.5, 1);
    }

    private void drawInfo(GOut g, Coord at, Fightview.Relation rel) {
        Tex mine = outlined(Integer.toString(rel.ip), new Color(100, 220, 220));
        Tex theirs = outlined(Integer.toString(rel.oip), new Color(255, 150, 80));
        int gap = UI.scale(16);
        g.aimage(mine, at.add(-gap, 0), 1, 1);
        g.aimage(theirs, at.add(gap, 0), 0, 1);
        int[] open = openings(rel);
        int width = 0;
        Tex[] tex = new Tex[open.length];
        for(int i = 0; i < open.length; i++) {
            if(open[i] <= 0)
                continue;
            tex[i] = outlined(Integer.toString(open[i]), OPEN[i]);
            width += tex[i].sz().x + UI.scale(4);
        }
        int x = at.x - width / 2;
        int y = at.y + UI.scale(2);
        for(int i = 0; i < tex.length; i++) {
            if(tex[i] == null)
                continue;
            g.image(tex[i], new Coord(x, y));
            x += tex[i].sz().x + UI.scale(4);
        }
    }

    private static int[] openings(Fightview.Relation rel) {
        int[] got = new int[OPEN_NAME.length];
        for(Buff buff : rel.buffs.children(Buff.class)) {
            Double meter = buff.ameter();
            if((meter == null) || (meter <= 0))
                continue;
            try {
                String name = buff.res.get().name;
                for(int i = 0; i < OPEN_NAME.length; i++) {
                    if(OPEN_NAME[i].equals(name))
                        got[i] = (int)Math.floor(meter * 100);
                }
            } catch(Loading l) {
            }
        }
        return(got);
    }

    private final Map<String, Tex> cache = new ConcurrentHashMap<>();

    private static FastMesh band(float inner, float outer) {
        int segments = 32;
        FloatBuffer pos = Utils.mkfbuf(segments * 2 * 3);
        for(int side = 0; side < 2; side++) {
            float radius = side == 0 ? outer : inner;
            for(int i = 0; i < segments; i++) {
                double angle = i * Math.PI * 2.0 / segments;
                pos.put((float)(Math.cos(angle) * radius));
                pos.put((float)(Math.sin(angle) * radius));
                pos.put(0.5f);
            }
        }
        pos.rewind();
        ShortBuffer ind = Utils.mksbuf(segments * 6);
        for(int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            ind.put((short)i);
            ind.put((short)next);
            ind.put((short)(segments + next));
            ind.put((short)i);
            ind.put((short)(segments + next));
            ind.put((short)(segments + i));
        }
        ind.rewind();
        return new FastMesh(new VertexBuf(new VertexBuf.VertexData(pos)), ind);
    }

    private static final class IgnoreClicks implements Pipe.Op {
        public void apply(Pipe pipe) { pipe.put(Clickable.slot, null); }
    }

    private static final class Band extends Sprite {
        Band(Owner owner, Color color) {
            super(owner, null);
            this.color = new BaseColor(color);
        }

        private final BaseColor color;

        public void added(RenderTree.Slot slot) {
            slot.add(RING, Pipe.Op.compose(RING_STATE, color));
        }
    }

    private static final class PreviewArrow extends Sprite {
        private final Sprite arrow;

        PreviewArrow(Gob gob) {
            super(gob, null);
            arrow = Fightsess.targetArrow(gob);
        }

        public void added(RenderTree.Slot slot) { slot.add(arrow, new IgnoreClicks()); }
        public boolean tick(double dt) { arrow.tick(dt); return false; }
        public void gtick(haven.render.Render render) { arrow.gtick(render); }
        public void dispose() { arrow.dispose(); }
    }

    private Tex outlined(String text, Color color) {
        return(cache.computeIfAbsent(color.getRGB() + ":" + text,
            key -> new TexI(Utils.outline2(numf.render(text, color).img, Color.BLACK))));
    }

    private static Coord view(GOut g, Pipe state, float z) {
        try {
            return(Homo3D.obj2view(new Coord3f(0, 0, z), state, Area.sized(g.sz())).round2());
        } catch(RuntimeException e) {
            return(null);
        }
    }
}
