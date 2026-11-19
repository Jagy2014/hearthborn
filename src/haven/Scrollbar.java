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

public class Scrollbar extends Widget {
    public static final Tex schain = Resource.loadtex("gfx/hud/schain");
    public static final Tex sflarp = Resource.loadtex("gfx/hud/sflarp");
    public static final int chcut = UI.scale(7);
    public static final int width = sflarp.sz().x;
    public Scrollable ctl;
    public int val, min, max;
    private UI.Grab drag = null;

    public Scrollbar(int h, int min, int max) {
	super(new Coord(width, h));
	this.min = min;
	this.max = max;
	this.val = min;
	this.ctl = null;
    }

    public Scrollbar(int h, Scrollable ctl) {
	this(h, 0, 0);
	this.ctl = ctl;
	this.min = ctl.scrollmin();
	this.max = ctl.scrollmax();
	this.val = ctl.scrollval();
    }

    public boolean vis() {
	return(max > min);
    }

    public void draw(GOut g) {
	if(ctl != null) {
	    min = ctl.scrollmin();
	    max = ctl.scrollmax();
	    val = ctl.scrollval();
	}
	if(vis()) {
	    int tw = Math.max(2, UI.scale(5)), x = (sz.x - tw) / 2, th = thumb();
	    g.chcolor(hearthborn.ui.UiPalette.scrollTrack());
	    g.frect(Coord.of(x, 0), Coord.of(tw, sz.y));
	    double a = (double)val / (double)(max - min);
	    int fy = (int)((sz.y - th) * a);
	    g.chcolor(hearthborn.ui.UiPalette.scrollThumb(over || drag != null));
	    g.frect(Coord.of(x, fy), Coord.of(tw, th));
	    g.chcolor();
	}
    }

    private boolean over;

    /** The length of the thumb: a fixed stretch, never more than half the track. */
    private int thumb() {
	return(Math.max(Math.min(UI.scale(36), sz.y / 2), 1));
    }

    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
	over = hovering;
	return(super.mousehover(ev, hovering));
    }

    private void update(Coord c) {
	double a = (double)(c.y - (thumb() / 2)) / (double)Math.max(1, sz.y - thumb());
	if(a < 0)
	    a = 0;
	if(a > 1)
	    a = 1;
	int val = (int)Math.round(a * (max - min)) + min;
	if(val != this.val) {
	    this.val = val;
	    changed();
	}
    }

    public boolean mousedown(MouseDownEvent ev) {
	if(ev.b != 1)
	    return(super.mousedown(ev));
	if(!vis())
	    return(false);
	drag = ui.grabmouse(this);
	update(ev.c);
	return(true);
    }

    public void mousemove(MouseMoveEvent ev) {
	super.mousemove(ev);
	if(drag != null)
	    update(ev.c);
    }

    public boolean mouseup(MouseUpEvent ev) {
	if(ev.b != 1)
	    return(super.mouseup(ev));
	if(drag == null)
	    return(false);
	drag.remove();
	drag = null;
	return(true);
    }

    public void changed() {
	if(ctl != null)
	    ctl.scrollval(val);
    }

    public void ch(int a) {
	int val = this.val + a;
	if(val > max)
	    val = max;
	if(val < min)
	    val = min;
	if(this.val != val) {
	    this.val = val;
	    changed();
	}
    }

    private double acc = 0.5;
    public void ch(double a) {
	acc += a;
	int i = (int)Math.floor(acc);
	if(i != 0) {
	    ch(i);
	    acc -= i;
	}
    }

    public void resize(int h) {
	super.resize(new Coord(sflarp.sz().x, h));
    }

    public void move(Coord c) {
	this.c = c.add(-sflarp.sz().x, 0);
    }
}
