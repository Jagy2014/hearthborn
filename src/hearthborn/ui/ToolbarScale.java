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

import haven.*;
import haven.render.*;

/**
 * Local UI projection for floating strips, with optional per-component scale.
 *
 * @author Jagy2014
 */
public final class ToolbarScale {
    /** Toolbar pinned under the chat, against the bottom edge. */
    public static final double DOCK=1.40;
    /** The compact strips use the panel scale without additional magnification. */
    public static double scale(){return HudInterfaceScale.current();}
    public static Coord size(Coord size){return size(size,scale());}
    public static Coord size(Coord size,double scale){return Coord.of((int)Math.ceil(size.x*scale),(int)Math.ceil(size.y*scale));}
    public static Coord logical(Coord point){return logical(point,scale());}
    public static Coord logical(Coord point,double scale){return Coord.of((int)Math.floor(point.x/scale),(int)Math.floor(point.y/scale));}
    public static GOut graphics(GOut original,Coord size){return graphics(original,size,scale());}
    public static GOut graphics(GOut original,Coord size,double scale){
        Pipe pipe=original.basicstate();Ortho2D ortho=(Ortho2D)pipe.get(States.vxf);
        pipe.prep(new Ortho2D((float)((ortho.l-original.tx.x)/scale),(float)((ortho.u-original.tx.y)/scale),
            (float)((ortho.r-original.tx.x)/scale),(float)((ortho.b-original.tx.y)/scale)));
        return new GOut(original.out,pipe,size);
    }
    private ToolbarScale(){}
}
