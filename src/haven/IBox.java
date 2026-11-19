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

public interface IBox {
    public Coord btloff();
    public Coord ctloff();
    public Coord bbroff();
    public Coord cbroff();
    public Coord bisz();
    public Coord cisz();
    public void draw(GOut g, Coord tl, Coord sz);

    public static abstract class Images implements IBox {
	public final Tex ctl, ctr, cbl, cbr;
	public final Tex bl, br, bt, bb;
	/** True for the window moulding, which a flat theme draws as a thin line instead of the sprites. */
	public boolean replaceable;

	public Images(Tex ctl, Tex ctr, Tex cbl, Tex cbr, Tex bl, Tex br, Tex bt, Tex bb) {
	    this.ctl = ctl;
	    this.ctr = ctr;
	    this.cbl = cbl;
	    this.cbr = cbr;
	    this.bl = bl;
	    this.br = br;
	    this.bt = bt;
	    this.bb = bb;
	}

	public Images(String base, String ctl, String ctr, String cbl, String cbr, String bl, String br, String bt, String bb) {
	    this(hearthborn.ui.FrameSkin.tex(base, ctl),
		 hearthborn.ui.FrameSkin.tex(base, ctr),
		 hearthborn.ui.FrameSkin.tex(base, cbl),
		 hearthborn.ui.FrameSkin.tex(base, cbr),
		 hearthborn.ui.FrameSkin.tex(base, bl),
		 hearthborn.ui.FrameSkin.tex(base, br),
		 hearthborn.ui.FrameSkin.tex(base, bt),
		 hearthborn.ui.FrameSkin.tex(base, bb));
	    this.replaceable = hearthborn.ui.FrameSkin.replaceable(base);
	}

	/** Draws the flat frame in place of the sprites, and says whether it did. */
	public boolean flat(GOut g, Coord tl, Coord sz, boolean top, boolean bottom) {
	    if(!replaceable || !hearthborn.ui.FrameSkin.flat())
		return(false);
	    hearthborn.ui.FrameSkin.flat(g, tl, sz,
					 Math.max(ctl.sz().x, Math.max(cbl.sz().x, bl.sz().x)),
					 Math.max(ctl.sz().y, Math.max(ctr.sz().y, bt.sz().y)),
					 Math.max(ctr.sz().x, Math.max(cbr.sz().x, br.sz().x)),
					 Math.max(cbl.sz().y, Math.max(cbr.sz().y, bb.sz().y)),
					 top, bottom);
	    return(true);
	}

	public Coord btloff() {
	    return(new Coord(bl.sz().x, bt.sz().y));
	}

	public Coord ctloff() {
	    return(ctl.sz());
	}

	public Coord bbroff() {
	    return(new Coord(br.sz().x, bb.sz().y));
	}

	public Coord cbroff() {
	    return(cbr.sz());
	}

	public Coord bisz() {
	    return(new Coord(bl.sz().x + br.sz().x, bt.sz().y + bb.sz().y));
	}

	public Coord cisz() {
	    return(ctl.sz().add(cbr.sz()));
	}
    }

    public static class Scaled extends Images {
	public Scaled(Tex ctl, Tex ctr, Tex cbl, Tex cbr, Tex bl, Tex br, Tex bt, Tex bb) {
	    super(ctl, ctr, cbl, cbr, bl, br, bt, bb);
	}

	public Scaled(String base, String ctl, String ctr, String cbl, String cbr, String bl, String br, String bt, String bb) {
	    super(base, ctl, ctr, cbl, cbr, bl, br, bt, bb);
	}

	public void draw(GOut g, Coord tl, Coord sz) {
	    if(flat(g, tl, sz, true, true))
		return;
	    g.image(bt, tl.add(new Coord(ctl.sz().x, 0)), new Coord(sz.x - ctr.sz().x - ctl.sz().x, bt.sz().y));
	    g.image(bb, tl.add(new Coord(cbl.sz().x, sz.y - bb.sz().y)), new Coord(sz.x - cbr.sz().x - cbl.sz().x, bb.sz().y));
	    g.image(bl, tl.add(new Coord(0, ctl.sz().y)), new Coord(bl.sz().x, sz.y - cbl.sz().y - ctl.sz().y));
	    g.image(br, tl.add(new Coord(sz.x - br.sz().x, ctr.sz().y)), new Coord(br.sz().x, sz.y - cbr.sz().y - ctr.sz().y));
	    g.image(ctl, tl);
	    g.image(ctr, tl.add(sz.x - ctr.sz().x, 0));
	    g.image(cbl, tl.add(0, sz.y - cbl.sz().y));
	    g.image(cbr, new Coord(sz.x - cbr.sz().x + tl.x, sz.y - cbr.sz().y + tl.y));
	}
    }
}
