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
 * Study, journal and the other buffs, smaller than the 1.5× action grid, so the row stays compact. The widget row grows with portrait scale and with panel scale.
 *
 * @author Jagy2014
 */
public final class HudBuffs extends Bufflist {
    public static final double SCALE = 0.85;
    private Coord logicalSize;
    public HudBuffs(){resize(Buff.cframe.sz());}
    protected int columns(){return 16;}
    protected boolean growLeft(){return PortraitSide.onRight();}
    private static double factor(){return SCALE*PortraitScale.current()*HudInterfaceScale.current();}
    public static Coord size(Coord size){double scale=factor();return Coord.of((int)Math.ceil(size.x*scale),(int)Math.ceil(size.y*scale));}
    public static Coord logical(Coord point){double scale=factor();return Coord.of((int)Math.floor(point.x/scale),(int)Math.floor(point.y/scale));}
    public void applyScale(){if(logicalSize!=null)resize(logicalSize);}
    public void resize(Coord size){logicalSize=size;super.resize(size(size));}
    public void draw(GOut g){super.draw(graphics(g,logicalSize));}
    public boolean handle(Event event){
        if(event instanceof PointerEvent){
            PointerEvent pointer=((PointerEvent)event).derive(logical(((PointerEvent)event).c));
            event.stop();pointer.propagate=true;pointer.handling=this;
            return super.handle(pointer)||pointer.propagate(this);
        }
        return super.handle(event);
    }
    private static GOut graphics(GOut original,Coord nativeSize){
        double scale=factor();
        Pipe pipe=original.basicstate();Ortho2D ortho=(Ortho2D)pipe.get(States.vxf);
        pipe.prep(new Ortho2D((float)((ortho.l-original.tx.x)/scale),(float)((ortho.u-original.tx.y)/scale),
            (float)((ortho.r-original.tx.x)/scale),(float)((ortho.b-original.tx.y)/scale)));
        return new GOut(original.out,pipe,nativeSize);
    }
}
