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

import java.awt.*;
import haven.render.*;

public class Speaking extends GAttrib implements RenderTree.Node, PView.Render2D {
    public static final IBox.Images sb = new IBox.Scaled("gfx/hud/emote", "tl", "tr", "bl", "br", "el", "er", "et", "eb");
    public static final Tex svans = Resource.loadtex("gfx/hud/emote/svans");
    public static final int sx = UI.scale(5);
    public float zo;
    /** Speech bubbles stay antialiased whatever the text smoothing setting is. */
    public static final Text.Foundry fnd = new Text.Foundry(Text.sans, 10).aa(true);
    public Text text;
	
    public Speaking(Gob gob, float zo, String text) {
	super(gob);
	this.zo = zo;
	this.text = fnd.render(text, Color.BLACK);
    }
	
    public void update(String text) {
	this.text = fnd.render(text, Color.BLACK);
    }
	
    public void draw(GOut g, Coord c) {
	Coord sz = text.sz();
	sz.x = Math.max(sz.x, UI.scale(15));
	Coord tl = c.sub(sx, sb.cisz().y + sz.y + svans.sz().y - sb.bb.sz().y);
	Coord ftl = tl.add(sb.btloff());
	Coord body = sz.add(sb.cisz());
	int tail = Math.max(UI.scale(4), c.y - (tl.y + body.y));
	/* No dark outline: a plain white balloon with a barely visible soft shadow to lift it off pale ground. */
	g.chcolor(0, 0, 0, 36);
	balloon(g, tl.add(0, 1), body, c.add(0, 1), tail);
	g.chcolor(Color.WHITE);
	balloon(g, tl, body, c, tail);
	g.chcolor(Color.BLACK);
	g.image(text.tex(), ftl);
	g.chcolor(Color.WHITE);
    }

    /** White rounded rectangle with a small pointer whose tip sits at {@code tip}. */
    static void balloon(GOut g, Coord tl, Coord sz, Coord tip, int tail) {
	int r = Math.min(UI.scale(3), Math.min(sz.x, sz.y) / 2);
	/* One strip per row, so translucent colours never overlap and darken. */
	for(int y = 0; y < sz.y; y++) {
	    int edge = Math.min(y, sz.y - 1 - y);
	    int inset = (edge < r) ? r - edge : 0;
	    g.frect(tl.add(inset, y), Coord.of(sz.x - 2 * inset, 1));
	}
	int base = UI.scale(9);
	for(int row = 0; row < tail; row++) {
	    int w = Math.max(1, base * (tail - row) / tail);
	    g.frect(Coord.of(tip.x, tip.y - tail + row), Coord.of(w, 1));
	}
    }

    public void draw(GOut g, Pipe state) {
	Coord sc = Homo3D.obj2view(new Coord3f(0, 0, zo), state, Area.sized(g.sz())).round2();
	draw(g, sc.add(sx, 0));
    }

    @OCache.DeltaType(OCache.OD_SPEECH)
    public static class $speak implements OCache.Delta {
	public void apply(Gob g, OCache.AttrDelta msg) {
	    float zo = msg.int16() / 100.0f;
	    String text = msg.string();
	    if(text.length() < 1) {
		g.delattr(Speaking.class);
	    } else {
		Speaking m = g.getattr(Speaking.class);
		if(m == null) {
		    g.setattr(new Speaking(g, zo, text));
		} else {
		    m.zo = zo;
		    m.update(text);
		}
	    }
	}
    }
}
