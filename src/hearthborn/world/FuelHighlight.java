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
 * pulsing green indication of the automation target.
 *
 * @author Jagy2014
 */
public final class FuelHighlight extends GAttrib implements Gob.SetupMod {
    private final long start=System.currentTimeMillis();
    public FuelHighlight(Gob gob){super(gob);}
    public Pipe.Op gobstate(){
        double pulse=Math.abs(Math.sin(Math.PI*(System.currentTimeMillis()-start)/1200.0));
        return new MixColor(64,255,64,(int)(255*pulse));
    }
}
