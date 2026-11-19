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

import java.awt.image.BufferedImage;

public class ICheckBox extends ACheckBox {
    public final Tex up, down, hoverup, hoverdown;
    private final BufferedImage img;
    public boolean h;
    /** Drawn at this fraction of the picture's size (1 = as is); the hit area follows. */
    private double shrink = 1;

    /** Draws the control {@code side} pixels across at most; never larger than its picture. */
    public void fit(int side) {
	Coord full = up.sz();
	double factor = Math.min(1, side / (double)Math.max(1, Math.max(full.x, full.y)));
	if(factor == shrink)
	    return;
	shrink = factor;
	resize(factor == 1 ? full : Coord.of(Math.max(1, (int)Math.round(full.x * factor)), Math.max(1, (int)Math.round(full.y * factor))));
    }

    @RName("ichk")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Tex up = Loading.waitfor(ui.sess.getresv(args[0])).flayer(Resource.imgc).tex();
	    Tex down = Loading.waitfor(ui.sess.getresv(args[1])).flayer(Resource.imgc).tex();
	    Tex hoverup = (args.length > 2) ? Loading.waitfor(ui.sess.getresv(args[1])).flayer(Resource.imgc).tex() : up;
	    Tex hoverdown = (args.length > 3) ? Loading.waitfor(ui.sess.getresv(args[1])).flayer(Resource.imgc).tex() : down;
	    ICheckBox ret = new ICheckBox(up, down, hoverup, hoverdown);
	    ret.canactivate = true;
	    return(ret);
	}
    }

    public ICheckBox(Tex up, Tex down, Tex hoverup, Tex hoverdown) {
	super(up.sz());
	this.up = up;
	this.down = down;
	this.hoverup = hoverup;
	this.hoverdown = hoverdown;
	if(up instanceof TexI)
	    this.img = ((TexI)up).back;
	else
	    this.img = null;
    }

    public ICheckBox(Tex up, Tex down, Tex hover) {
	this(up, down, hover, down);
    }

    public ICheckBox(Tex up, Tex down) {
	this(up, down, up);
    }

    public ICheckBox(String base, String up, String down, String hoverup, String hoverdown) {
	this(Resource.loadtex(base + up), Resource.loadtex(base + down), Resource.loadtex(base + hoverup), Resource.loadtex(base + hoverdown));
    }
    public ICheckBox(String base, String up, String down, String hover) {
	this(Resource.loadtex(base + up), Resource.loadtex(base + down), Resource.loadtex(base + hover));
    }
    public ICheckBox(String base, String up, String down) {
	this(Resource.loadtex(base + up), Resource.loadtex(base + down));
    }

    public void draw(GOut g) {
	Tex shown = !state() ? (h ? hoverup : up) : (h ? hoverdown : down);
	if(shrink == 1)
	    g.image(shown, Coord.z);
	else
	    g.image(shown, Coord.z, sz);
        super.draw(g);
    }

    public boolean checkhit(Coord c) {
	if(!c.isect(Coord.z, sz))
	    return(false);
	if((img == null) || img.getRaster().getNumBands() < 4)
	    return(true);
	int x = (shrink == 1) ? c.x : (int)(c.x / shrink), y = (shrink == 1) ? c.y : (int)(c.y / shrink);
	if(x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight())
	    return(false);
	return(img.getRaster().getSample(x, y, 3) >= 128);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if((ev.b == 1) && checkhit(ev.c)) {
	    click();
	    return(true);
	}
	return(super.mousedown(ev));
    }

    public void mousemove(MouseMoveEvent ev) {
	this.h = checkhit(ev.c);
    }
}
