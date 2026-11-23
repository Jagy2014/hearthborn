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

package hearthborn.map;

import haven.*;
import haven.render.*;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * Collision outline glued to a gob (including the placement ghost), so it moves and turns with it.
 *
 * @author Jagy2014
 */
public final class HitboxSprite extends Sprite {
    private static final int OL_ID = -782341;
    private static final VertexArray.Layout FORMAT = new VertexArray.Layout(
        new VertexArray.Layout.Input(Homo3D.vertex, new VectorFormat(3, NumberFormat.FLOAT32), 0, 0, 12));
    private static final Pipe.Op STATE = Pipe.Op.compose(
        Rendered.last, States.Depthtest.none, new States.LineWidth(8),
        new BaseColor(new Color(255, 90, 90, 230)),
        pipe -> pipe.put(Clickable.slot, null));
    private final Model model;

    private HitboxSprite(Owner owner, Model model) {
        super(owner, null);
        this.model = model;
    }

    public void added(RenderTree.Slot slot) {
        slot.ostate(STATE);
        slot.add(model);
    }

    public void dispose() {
        if(model != null) model.dispose();
    }

    /** Attach the outline once; local geometry turns with the gob placement. */
    public static void sync(Gob gob, boolean on) {
        if(gob == null) return;
        Gob.Overlay previous = gob.findol(OL_ID);
        if(!on) {
            if(previous != null) previous.remove(false);
            return;
        }
        if(previous != null) return;
        try {
            Model built = mesh(gob);
            if(built == null) return;
            gob.addol(new Gob.Overlay(gob, OL_ID, (Sprite.Owner owner) -> new HitboxSprite(owner, built)), false);
        } catch(Loading ignored) { }
    }

    private static Model mesh(Gob gob) {
        List<Coord2d[]> shapes = hearthborn.world.ObjectGeometry.local(gob, true);
        if(shapes.isEmpty()) return null;
        List<Float> data = new ArrayList<>();
        for(Coord2d[] polygon : shapes) {
            if(polygon.length < 2) continue;
            for(int i = 0; i < polygon.length; i++) {
                Coord2d a = polygon[i], b = polygon[(i + 1) % polygon.length];
                data.add((float)a.x); data.add((float)-a.y); data.add(0.25f);
                data.add((float)b.x); data.add((float)-b.y); data.add(0.25f);
            }
        }
        if(data.isEmpty()) return null;
        float[] vertices = new float[data.size()];
        for(int i = 0; i < vertices.length; i++) vertices[i] = data.get(i);
        return new Model(Model.Mode.LINES,
            new VertexArray(FORMAT, new VertexArray.Buffer(vertices.length * 4, DataBuffer.Usage.STATIC, DataBuffer.Filler.of(vertices))),
            null);
    }
}
