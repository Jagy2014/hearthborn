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

import static java.lang.Math.PI;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class Cal extends Widget {
    private final hearthborn.ui.CalendarInfo calendarInfo = new hearthborn.ui.CalendarInfo();
    private final hearthborn.meteor.MeteorShower meteors=new hearthborn.meteor.MeteorShower();
    public static final double hbr = UI.scale(20.0);
    static final Tex[] dlnd = new Tex[4];
    static final Tex[] nlnd = new Tex[4];
    static final Resource.Image dsky = Resource.loadrimg("gfx/hud/calendar/daysky");
    static final Resource.Image nsky = Resource.loadrimg("gfx/hud/calendar/nightsky");
    static final Coord sceneOffset = hearthborn.ui.CalendarArtwork.sceneOffset();
    static final TexI bg = hearthborn.ui.CalendarArtwork.frame(dsky.ssz);
    static final Resource.Anim sun = Resource.local().loadwait("gfx/hud/calendar/sun").layer(Resource.animc);
    static final Resource.Anim moon = Resource.local().loadwait("gfx/hud/calendar/moon").layer(Resource.animc);

    static {
	for(int i = 0; i < dlnd.length; i++) {
	    dlnd[i] = Resource.loadtex(String.format("gfx/hud/calendar/dayscape-%d", i));
	    nlnd[i] = Resource.loadtex(String.format("gfx/hud/calendar/nightscape-%d", i));
	}
    }

    public Cal() {
	super(bg.sz());
    }

    public void tick(double dt){
        super.tick(dt);
        GameUI gui=getparent(GameUI.class);
        meteors.tick(dt,!Fightsess.active&&gui!=null&&gui.starFall!=null&&gui.starFall.showerActive());
    }

    public void draw(GOut g) {
	if(Fightsess.active)
	    return;
	Astronomy a = ui.sess.glob.ast;
	long now = System.currentTimeMillis();
	g.image(a.night ? nsky : dsky, sceneOffset);
        if(a.night)meteors.draw(g,nsky.scaled(),UI.scale(nsky.o).add(sceneOffset));
	int mp = (int)Math.round(a.mp * (double)moon.f.length) % moon.f.length;
	Resource.Image moon = Cal.moon.f[mp][0];
	Resource.Image sun = Cal.sun.f[(int)((now / Cal.sun.d) % Cal.sun.f.length)][0];
	Coord mc = Coord.sc((a.dt + 0.25) * 2 * PI, hbr).add(dsky.ssz.div(2)).add(sceneOffset).sub(moon.ssz.div(2));
	Coord sc = Coord.sc((a.dt + 0.75) * 2 * PI, hbr).add(dsky.ssz.div(2)).add(sceneOffset).sub(sun.ssz.div(2));
	g.chcolor(a.mc);
	g.image(moon, mc);
	g.chcolor();
	g.image(sun, sc);
	g.image((a.night ? nlnd : dlnd)[a.is], sceneOffset);
	g.image(bg, Coord.z);
    }

    public boolean checkhit(Coord c) {
	return(Utils.checkhit(dsky.scaled(), c.sub(UI.scale(dsky.o)).sub(sceneOffset)) || Utils.checkhit(bg.back,c));
    }

    public void dispose() { meteors.dispose(); calendarInfo.dispose(); super.dispose(); }

    private static String ord(int i) {
	if(((i % 100) / 10) != 1) {
	    if((i % 10) == 1)
		return(i + "st");
	    else if((i % 10) == 2)
		return(i + "nd");
	    else if((i % 10) == 3)
		return(i + "rd");
	}
	return(i + "th");
    }

    public Object tooltip(Coord c, Widget prev) {
	if(Fightsess.active)
	    return(null);
        return calendarInfo.tooltip(ui, getparent(GameUI.class));
    }
}
