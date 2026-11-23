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
import haven.render.*;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Collection;

/**
 * While drawing a bow (or aiming a spear/sling), show max reach as a ring and the
 * fireable facing sector as a wedge that reaches the same radius.
 *
 * @author Jagy2014
 */
public final class ArcheryAim {
    /** Matches the server-side reach used by the common archery weapons. */
    public static final float SPEAR_OR_SLING = 160f, HUNTERS_BOW = 200f, RANGERS_BOW = 257f;
    private static final String PREF = "hearthborn/combat/archery-aim";
    private static volatile boolean enabled = Utils.getprefb(PREF, true);
    private static final double HALF = Math.toRadians(20);
    private static final int ARC = 24, RING = 96;
    private static final Color FILL = new Color(255, 0, 0, 150);
    private static final Color EDGE = new Color(255, 40, 40, 220);
    /** Same as GobMarks: clear Clickable on the mesh leaf so the aim overlay never eats clicks. */
    private static final Pipe.Op UNCLICK = pipe -> pipe.put(Clickable.slot, null);
    private static Gob.Overlay ring, wedge;
    private static float shown = -1;
    private static Gob host;
    private static GameUI gui;

    public static boolean enabled() { return enabled; }
    public static void enabled(boolean value) {
        enabled = value;
        Utils.setprefb(PREF, value);
        if(!value) clear();
    }

    /** Detected reach for the player's current aim pose, or {@code NaN} when not aiming. */
    public static float range(Gob body) {
        if(body == null) return Float.NaN;
        Drawable drawable = body.getattr(Drawable.class);
        if(!(drawable instanceof Composite)) return Float.NaN;
        Composite model = (Composite)drawable;
        Collection<ResData> poses = model.poseResources();
        if(poses.isEmpty()) return Float.NaN;
        java.util.ArrayList<String> poseNames = new java.util.ArrayList<>();
        for(ResData pose : poses) {
            try { poseNames.add(pose.res.get().name); }
            catch(Loading ignored) { return Float.NaN; }
        }
        java.util.ArrayList<String> gear = new java.util.ArrayList<>();
        for(Composited.ED piece : model.comp.cequ) {
            if(piece == null || piece.res == null || piece.res.res == null) continue;
            try { gear.add(leaf(piece.res.res.get().name)); }
            catch(Loading ignored) { return Float.NaN; }
        }
        return rangeFor(poseNames, gear);
    }

    /** Pose/equipment → reach; package-visible for tests. */
    static float rangeFor(Collection<String> poses, Collection<String> equipment) {
        boolean draw = false, spear = false, sling = false;
        for(String name : poses) {
            if(name.contains("drawbow")) draw = true;
            else if(name.contains("spear-ready")) spear = true;
            else if(name.contains("sling-aim")) sling = true;
        }
        if(spear || sling) return SPEAR_OR_SLING;
        if(!draw) return Float.NaN;
        if(equipment != null) for(String leaf : equipment) {
            if(leaf.equals("rangersbow")) return RANGERS_BOW;
            if(leaf.equals("huntersbow")) return HUNTERS_BOW;
        }
        return HUNTERS_BOW;
    }

    private static String leaf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    public static void tick(GameUI nextGui) {
        gui = nextGui;
        if(!enabled) { clear(); return; }
        Gob body = gui == null || gui.map == null ? null : gui.map.player();
        float next = range(body);
        if(Float.isNaN(next)) { clear(); return; }
        if(body != host || ring == null || wedge == null || Math.abs(shown - next) > .5f)
            attach(body, next);
    }

    /**
     * Where the facing wedge should point: mouse on the ground when possible, else a chosen
     * combat/bow target, else {@link Gob#a}. Avoids the boat nose / run-direction lag from
     * {@code Location.goback("gob")}.
     */
    static double aimAngle(Gob body) {
        if(body == null) return 0;
        GameUI game = gui;
        MapView map = game == null ? null : game.map;
        Coord2d mouse = mouseGround(map, body);
        if(mouse != null) {
            double dx = mouse.x - body.rc.x, dy = mouse.y - body.rc.y;
            if(dx * dx + dy * dy > 1) return Math.atan2(dy, dx);
        }
        Gob target = aimTarget(game);
        if(target != null && target.rc != null) {
            double dx = target.rc.x - body.rc.x, dy = target.rc.y - body.rc.y;
            if(dx * dx + dy * dy > 1) return Math.atan2(dy, dx);
        }
        return body.a;
    }

    private static Gob aimTarget(GameUI game) {
        if(game == null) return null;
        if(game.fv != null && game.fv.current != null && !game.fv.current.invalid) {
            Gob gob = game.ui.sess.glob.oc.getgob(game.fv.current.gobid);
            if(gob != null && !gob.removed) return gob;
        }
        return null;
    }

    /** Map coordinate under the cursor on the player's ground plane, or null. */
    static Coord2d mouseGround(MapView map, Gob body) {
        if(map == null || map.ui == null || body == null || map.camera == null) return null;
        Coord sc = map.ui.mc.sub(map.rootpos());
        if(!sc.isect(Coord.z, map.sz)) return null;
        try {
            Coord3f at = body.getc();
            Pipe state = map.basic.state();
            haven.render.Camera cam = state.get(Homo3D.cam);
            Projection prj = state.get(Homo3D.prj);
            if(cam == null || prj == null) return null;
            Matrix4f view = cam.fin(Matrix4f.id);
            Matrix4f proj = prj.fin(Matrix4f.id);
            float nx = (sc.x + 0.5f) * 2f / map.sz.x - 1f;
            float ny = 1f - (sc.y + 0.5f) * 2f / map.sz.y;
            Matrix4f invView = view.invert();
            HomoCoord4f near = invView.mul4(HomoCoord4f.fromndc(proj, new Coord3f(nx, ny, -1f)));
            HomoCoord4f far = invView.mul4(HomoCoord4f.fromndc(proj, new Coord3f(nx, ny, 1f)));
            Coord3f a = near.pdiv(), b = far.pdiv();
            float dz = b.z - a.z;
            if(Math.abs(dz) < 1e-5f) return null;
            float t = (at.z - a.z) / dz;
            if(t < 0f || t > 1.5f) return null;
            float x = a.x + t * (b.x - a.x);
            float y = a.y + t * (b.y - a.y);
            // Render space uses (mapX, -mapY, z); convert back to map coordinates.
            return Coord2d.of(x, -y);
        } catch(Loading ignored) {
            return null;
        } catch(RuntimeException ignored) {
            return null;
        }
    }

    private static void attach(Gob body, float range) {
        clear();
        host = body; shown = range;
        synchronized(body) {
            ring = new Gob.Overlay(body, new Ring(body, range));
            wedge = new Gob.Overlay(body, new Wedge(body, range));
            body.addol(ring, false);
            body.addol(wedge, false);
        }
    }

    private static void clear() {
        Gob gob = host;
        Gob.Overlay a = ring, b = wedge;
        host = null; ring = wedge = null; shown = -1;
        if(gob == null) return;
        synchronized(gob) {
            if(a != null && gob.ols.contains(a)) a.remove(false);
            if(b != null && gob.ols.contains(b)) b.remove(false);
        }
    }

    /**
     * Ground pass + lifted Z so the overlay stays visible; {@link #UNCLICK} on the mesh leaf
     * so animals under the sector stay clickable. Aim is a Location rot around gobx.
     */
    private static Pipe.Op meshState(double angle, Color color) {
        return Pipe.Op.compose(new States.Facecull(States.Facecull.Mode.NONE), States.maskdepth,
            MapMesh.postmap, Location.goback("gobx"),
            new Location(Transform.makerot(new Matrix4f(), Coord3f.zu, -(float)angle)),
            UNCLICK, new BaseColor(color));
    }

    /** Thin ring at the weapon's max range. */
    private static final class Ring extends Sprite {
        private final FastMesh mesh;
        Ring(Owner owner, float range) {
            super(owner, null);
            float inner = Math.max(1f, range - 2f);
            FloatBuffer pos = Utils.mkfbuf(RING * 2 * 3);
            for(int ring = 0; ring < 2; ring++) {
                float radius = ring == 0 ? range : inner;
                for(int i = 0; i < RING; i++) {
                    double angle = i * Math.PI * 2.0 / RING;
                    pos.put((float)(Math.cos(angle) * radius));
                    pos.put((float)(Math.sin(angle) * radius));
                    pos.put(12f);
                }
            }
            pos.rewind();
            ShortBuffer ind = Utils.mksbuf(RING * 6);
            for(int i = 0; i < RING; i++) {
                int next = (i + 1) % RING;
                ind.put((short)i).put((short)next).put((short)(RING + next));
                ind.put((short)i).put((short)(RING + next)).put((short)(RING + i));
            }
            ind.rewind();
            mesh = new FastMesh(new VertexBuf(new VertexBuf.VertexData(pos)), ind);
        }
        public void added(RenderTree.Slot slot) {
            slot.add(mesh, meshState(0, EDGE));
        }
        public void dispose() { mesh.dispose(); }
    }

    /** Facing sector from the feet out to the same radius as {@link Ring}. */
    private static final class Wedge extends Sprite implements TickList.Ticking, TickList.TickNode {
        private final FastMesh mesh;
        private RenderTree.Slot meshSlot;
        private double drawn = Double.NaN;
        Wedge(Owner owner, float range) {
            super(owner, null);
            int points = ARC + 1;
            FloatBuffer pos = Utils.mkfbuf(points * 3);
            pos.put(0f).put(0f).put(12f);
            for(int i = 0; i < ARC; i++) {
                double angle = -HALF + (2 * HALF) * i / (ARC - 1);
                pos.put((float)(Math.cos(angle) * range));
                pos.put((float)(Math.sin(angle) * range));
                pos.put(12f);
            }
            pos.rewind();
            ShortBuffer ind = Utils.mksbuf((ARC - 1) * 3);
            for(int i = 1; i < ARC; i++)
                ind.put((short)0).put((short)i).put((short)(i + 1));
            ind.rewind();
            mesh = new FastMesh(new VertexBuf(new VertexBuf.VertexData(pos)), ind);
        }
        public void added(RenderTree.Slot slot) {
            double angle = aimAngle((Gob)owner);
            meshSlot = slot.add(mesh, meshState(angle, FILL));
            drawn = angle;
        }
        public void removed(RenderTree.Slot slot) { meshSlot = null; }
        public TickList.Ticking ticker() { return this; }
        public void autotick(double dt) {
            double angle = aimAngle((Gob)owner);
            if(meshSlot == null || (Double.isFinite(drawn) && Math.abs(Utils.cangle(angle - drawn)) < 0.01)) return;
            drawn = angle;
            meshSlot.ostate(meshState(angle, FILL));
        }
        public void dispose() { mesh.dispose(); }
    }

    private ArcheryAim() {}
}
