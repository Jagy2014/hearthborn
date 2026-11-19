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
 * Temporary milking progress on animals: current, collected, or checked without milk.
 *
 * @author Jagy2014
 */
public final class MilkMark extends GAttrib implements Gob.SetupMod {
    public enum Status { CURRENT, DONE, EMPTY }
    public final String run;public final Status status;
    private double until=Double.POSITIVE_INFINITY;private boolean removalQueued;
    public MilkMark(Gob gob,String run,Status status){super(gob);this.run=run;this.status=status;}
    public void finish(){if(status==Status.CURRENT)until=0;else until=Utils.rtime()+120;}
    public void ctick(double dt){
        if(Utils.rtime()>=until&&!removalQueued){removalQueued=true;gob.defer(()->{if(gob.getattr(MilkMark.class)==this)gob.delattr(MilkMark.class);});}
    }
    public Pipe.Op gobstate(){
        if(Utils.rtime()>=until)return null;
        switch(status){
        case CURRENT:return new MixColor(255,210,55,100+(int)(100*Math.abs(Math.sin(Utils.rtime()*4))));
        case DONE:return new MixColor(70,255,110,130);
        default:return new MixColor(160,160,160,105);
        }
    }
}
