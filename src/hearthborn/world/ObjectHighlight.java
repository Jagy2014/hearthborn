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
/**
 * Short-lived local indication of the object selected in the timer list.
 *
 * @author Jagy2014
 */
public final class ObjectHighlight extends GAttrib implements Gob.SetupMod {
    private final double until=Utils.rtime()+8;
    public ObjectHighlight(Gob gob){super(gob);}
    public Pipe.Op gobstate(){if(Utils.rtime()>=until)return null;return new MixColor(255,210,48,100+(int)(130*Math.abs(Math.sin(Utils.rtime()*4))));}
}
