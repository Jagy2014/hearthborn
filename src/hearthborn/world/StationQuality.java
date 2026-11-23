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
import hearthborn.QualityOverlay;
import hearthborn.quality.QualityRoster;
import hearthborn.quality.RosterStore;
import java.util.Objects;

/**
 * Build quality of a drying frame or tanning tub, drawn like an item's quality number.
 *
 * @author Jagy2014
 */
public final class StationQuality extends GAttrib implements RenderTree.Node, PView.Render2D {
    private String shown;
    private Tex mark;
    private double elapsed = 1;

    public StationQuality(Gob gob) { super(gob); }

    /** Whole number, or null when this object has no recorded quality. */
    public static String label(double quality) {
        if(!(quality > 0)) return null;
        return Integer.toString((int)Math.round(quality));
    }

    public static boolean station(String name) {
        return name != null && (name.endsWith("/dframe") || name.endsWith("/ttub"));
    }

    public void ctick(double dt) {
        if((elapsed += dt) < 0.5) return;
        elapsed = 0;
        String next = station(resource()) ? label(known(gob)) : null;
        if(Objects.equals(next, shown)) return;
        shown = next;
        if(mark != null) mark.dispose();
        mark = next == null ? null : new TexI(QualityOverlay.styled(next));
    }

    static double known(Gob gob) {
        double quality = QualityRoster.quality(gob);
        if(quality > 0) return quality;
        RosterStore.Rec saved = RosterStore.trackedGob(gob);
        return saved == null ? 0 : saved.q;
    }

    private String resource() {
        try {
            Drawable drawable = gob.getattr(Drawable.class);
            Resource resource = drawable == null ? null : drawable.getres();
            return resource == null ? null : resource.name;
        } catch(Loading pending) {
            return null;
        }
    }

    public void draw(GOut g, Pipe state) {
        if(mark == null) return;
        Coord at = WorldLabels.anchor(state, g.sz(), 12);
        if(at != null) g.aimage(mark, at, .5, .5);
    }

    public void dispose() {
        if(mark != null) mark.dispose();
        mark = null;
    }
}
