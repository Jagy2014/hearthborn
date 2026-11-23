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

package hearthborn.world;

import haven.*;
import haven.render.*;
import java.util.*;

/**
 * Only replaces the drawn model; the original sprite, state and interaction remain intact.
 *
 * @author Jagy2014
 */
public final class PlantVisibility implements RenderTree.Node {
    private final ResDrawable drawable;
    private final boolean bush;
    private final Map<RenderTree.Slot, RenderTree.Slot> slots = new LinkedHashMap<>();
    private RenderTree.Node current;
    private Sprite stump;
    private boolean missing, generic;
    private double elapsed;

    private PlantVisibility(ResDrawable drawable, boolean bush) {
        this.drawable = drawable;
        this.bush = bush;
        // A newly streamed tree must not show its full model while its stump loads.
        this.current = hidden(bush) ? null : drawable.spr;
        this.elapsed=-((System.identityHashCode(drawable)&15)*(0.1/16.0));
    }

    private static volatile boolean hideTrees=Utils.getprefb("hearthborn/plants/hide-trees",false);
    private static volatile boolean hideBushes=Utils.getprefb("hearthborn/plants/hide-bushes",false);
    public static boolean includeBushes(){return hideBushes;}
    public static void includeBushes(boolean value){hideBushes=value;Utils.setprefb("hearthborn/plants/hide-bushes",value);}
    public static boolean hidden(boolean bush) { return hideTrees && (!bush || hideBushes); }
    public static void hidden(boolean bush, boolean on) {
        if(bush)hideBushes=on;else hideTrees=on;
        Utils.setprefb("hearthborn/plants/hide-" + (bush ? "bushes" : "trees"), on);
    }
    public static RenderTree.Node visual(ResDrawable drawable) {
        String name = drawable.rres.name;
        boolean bush = name.startsWith("gfx/terobjs/bushes/");
        // Use the world object's resource category, regardless of the sprite implementation.
        if(!name.endsWith("stump") && !name.endsWith("log") && !name.endsWith("oldtrunk")
            && (bush || name.startsWith("gfx/terobjs/trees/")))
            return new PlantVisibility(drawable, bush);
        return ObjectHiding.visual(drawable);
    }

    public void added(RenderTree.Slot slot) {
        // A previously detached drawable may be attached again after the setting changes.
        if(slots.isEmpty()) current = hidden(bush) ? stump : drawable.spr;
        slots.put(slot, current == null ? null : slot.add(current));
    }
    public void removed(RenderTree.Slot slot) { slots.remove(slot); }

    public static void tick(RenderTree.Node node, double dt) {
        if(node instanceof PlantVisibility) ((PlantVisibility)node).tick(dt);
        else ObjectHiding.tick(node, dt);
    }

    private void tick(double dt) {
        if((elapsed += dt) < .1) return;
        elapsed = 0;
        RenderTree.Node next = drawable.spr;
        if(hidden(bush)) {
            if(stump == null && !missing) try {
                String resource = bush ? "gfx/terobjs/trees/mulberrystump" : generic ? "gfx/terobjs/trees/oakstump" : drawable.rres.name + "stump";
                Resource loaded = Resource.remote().load(resource).get();
                stump = Sprite.create(drawable, loaded, MessageBuf.nil);
            } catch(Resource.LoadFailedException | Resource.LoadException | Sprite.ResourceException e) {
                if(bush || generic) missing = true;
                else generic = true;
            } catch(Loading ignored) {}
            next = stump;
        }
        if(next != current) try {
            List<RenderTree.Slot> parents = new ArrayList<>(slots.keySet());
            Collection<RenderTree.Slot> children = next == null ? Collections.emptyList() : RUtils.multiadd(parents, next);
            List<RenderTree.Slot> previous = new ArrayList<>(slots.values());
            previous.removeIf(Objects::isNull);
            RUtils.multirem(previous);
            slots.clear();
            Iterator<RenderTree.Slot> child = children.iterator();
            for(RenderTree.Slot parent : parents) slots.put(parent, next == null ? null : child.next());
            current = next;
        } catch(Loading ignored) {}
        if(current == stump && stump != null) stump.tick(dt);
    }

    public static void dispose(RenderTree.Node node) {
        if(node instanceof PlantVisibility) {
            Sprite stump = ((PlantVisibility)node).stump;
            if(stump != null) stump.dispose();
        }
    }
}
