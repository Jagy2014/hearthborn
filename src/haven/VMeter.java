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

public class VMeter extends LayerMeter {
    public static final Tex bg = Resource.loadtex("gfx/hud/vm-frame");
    public static final Tex fg = Resource.loadtex("gfx/hud/vm-tex");

    @RName("vm")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new VMeter(decmeters(args, 0)));
	}
    }

    public VMeter(List<Meter> meters) {
	super(bg.sz());
	set(meters);
    }

    public void draw(GOut g) {
	boolean grown=!sz.equals(bg.sz());
	int barW=bg.sz().x;
	Coord bar=grown?Coord.of(Math.min(barW,sz.x),sz.y):sz;
	if(grown)g.image(bg,Coord.z,bar);
	else g.image(bg,Coord.z);
	int h=(bar.y-UI.scale(6));
	for(Meter m : meters) {
	    g.chcolor(m.c);
	    int mh=(int)Math.round(h*m.a);
	    if(grown)g.image(fg,Coord.z,new Coord(0,bar.y-UI.scale(3)-mh),bar,bar);
	    else g.image(fg,new Coord(0,0),new Coord(0,sz.y-UI.scale(3)-mh),sz.add(0,mh));
	}
	g.chcolor();
	if(grown)hearthborn.ui.FuelReadout.draw(g,this,bar.x);
    }

    public void attached() {
	super.attached();
	Window window=getparent(Window.class);
	if(!hearthborn.timers.ObjectTimers.largeGrid(window))return;
	Inventory grid=null;
	for(Widget child=window.child;child!=null;child=child.next)if(child instanceof Inventory){grid=(Inventory)child;break;}
	if(grid==null)return;
	int width=hearthborn.timers.ObjectTimers.oven(window)?hearthborn.ui.FuelReadout.width(bg.sz().x):bg.sz().x;
	if(hearthborn.timers.ObjectTimers.stretchFuelMeter(window))resize(width,grid.sz.y);
	int edge=grid.c.x+grid.sz.x;
	if(c.x<edge)move(Coord.of(edge,c.y));
	window.pack();
    }
}
