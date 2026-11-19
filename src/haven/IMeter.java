/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.awt.Color;
import java.util.*;

public class IMeter extends LayerMeter {
    private final hearthborn.ui.MeterReadout readout = new hearthborn.ui.MeterReadout();
    public void dispose() { readout.dispose(); super.dispose(); }
    public static final Coord off = UI.scale(22, 7);
    public static final Coord fsz = UI.scale(101, 24);
    public static final Coord msz = UI.scale(75, 10);
    public final Indir<Resource> bg;

    @RName("im")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Indir<Resource> bg = ui.sess.getresv(args[0]);
	    List<Meter> meters = decmeters(args, 1);
	    return(new IMeter(bg, meters));
	}
    }

    public IMeter(Indir<Resource> bg, List<Meter> meters) {
	super(hearthborn.ui.StatusScale.size(fsz));
	this.bg = bg;
	set(meters);
    }

    public void draw(GOut g) {
        g=hearthborn.ui.StatusScale.graphics(g,fsz);
	try {
	    Resource resource=this.bg.get();
            Tex bg=hearthborn.ui.MeterArtwork.replacement(resource.name);
            if(bg==null)bg=resource.flayer(Resource.imgc).tex();
	    g.chcolor(hearthborn.ui.UiTheme.meterTrack());
	    g.frect(off, msz);
	    g.chcolor();
	    for(Meter m : meters) {
		int w = msz.x;
		w = (int)Math.ceil(w * m.a);
		g.chcolor(m.c);
		g.frect(off, new Coord(w, msz.y));
	    }
	    g.chcolor();
	    g.image(bg, Coord.z);
            int constitution=ui!=null&&ui.sess!=null&&ui.sess.glob!=null?ui.sess.glob.getcattr("con").comp:0;
            String tip=hearthborn.ui.MeterReadout.tipText(rawinfo);
            if(hearthborn.ui.MeterReadout.labeledPercent(tip)==null)
                tip=hearthborn.ui.MeterReadout.widgetTip(tooltip);
            if("gfx/hud/meter/häst".equals(resource.name)&&rawinfo!=null&&hearthborn.ui.MeterReadout.labeledPercent(tip)==null){
                try{tip=hearthborn.ui.MeterReadout.infoText(info());}
                catch(Loading ignored){}
            }
            readout.draw(g, resource.name, meters, constitution, tip);
	} catch(Loading l) {
	}
    }
}
