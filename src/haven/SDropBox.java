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

import java.util.*;
import java.util.function.*;
import java.awt.Color;

public abstract class SDropBox<I, W extends Widget> extends SListWidget<I, W> {
    public static final Tex dropimg = Resource.loadtex("gfx/hud/drop");
    public final int listh,itemh;
    protected final ACheckBox drop;
    private SDropList dl;
    private W curitem;

    public class SDropList extends SListBox<I, Widget> {
	private UI.Grab grab = null;

	protected SDropList() {
	    super(Coord.of(SDropBox.this.sz.x, Math.min(listh, SDropBox.this.items().size() * SDropBox.this.itemh)), SDropBox.this.itemh);
	    sel = SDropBox.this.sel;
	    display();
	}

        protected boolean autoscroll() { return SDropBox.this.listautoscroll(); }

	public class Item extends SListWidget.ItemWidget<I> {
	    private Item(I item, W child) {
		super(SDropList.this, child.sz, item);
		add(child, 0, 0);
	    }
	}

	protected List<? extends I> items() {return(SDropBox.this.items());}
	protected Widget makeitem(I item, int idx, Coord sz) {return(new Item(item, SDropBox.this.makeitem(item, idx, sz)));}

	public void add() {
	    SDropBox.this.ui.root.add(this, SDropBox.this.rootpos().add(0, SDropBox.this.sz.y));
	}

	protected void attached() {
	    super.attached();
	    grab = ui.grabmouse(this);
	}

	public void destroy() {
	    grab.remove();
	    super.destroy();
	    dl = null;
	}

	public void change(I item) {
	    SDropBox.this.change(item);
	    reqdestroy();
	}

	protected void drawbg(GOut g) {
	    ldrawbg(g, Area.sized(sz));
	    g.chcolor(hearthborn.ui.UiPalette.accent());
	    g.rect(Coord.z, sz);
	    g.chcolor();
	}

	protected void drawslot(GOut g, I item, int idx, Area area) {
	    ldrawslot(g, item, idx, area);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(!ev.c.isect(Coord.z, sz)) {
		reqdestroy();
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	protected boolean unselect(int btn) {
	    return(false);
	}
    }

    public SDropBox(int w, int listh, int itemh) {
	super(Coord.of(w, itemh));
	this.listh = listh;
	this.itemh = itemh;
	drop = adda(makedrop().state(() -> dl != null).set(this::drop), Coord.of(sz.x, sz.y / 2), 1.0, 0.5);
    }

    protected boolean listautoscroll() { return true; }

    /** The little arrow at the right end: a small chevron in the quiet text colour. */
    protected ACheckBox makedrop() {
	return(new ACheckBox(Coord.of(UI.scale(22), itemh)) {
		public void draw(GOut g) {
		    g.chcolor(hearthborn.ui.UiPalette.text2());
		    int w = Math.max(3, UI.scale(4)), cx = sz.x / 2, cy = sz.y / 2;
		    g.line(Coord.of(cx - w, cy - w / 2), Coord.of(cx, cy + w / 2), Math.max(1, UI.scale(1)));
		    g.line(Coord.of(cx, cy + w / 2), Coord.of(cx + w, cy - w / 2), Math.max(1, UI.scale(1)));
		    g.chcolor();
		}
		public boolean mousedown(MouseDownEvent ev) {
		    if(ev.b != 1)
			return(super.mousedown(ev));
		    click();
		    return(true);
		}
	    });
    }

    public void change(I item) {
	if(item != this.sel) {
	    if(curitem != null) {
		curitem.destroy();
		curitem = null;
	    }
	    curitem = add(makeitem(item, -1, Coord.of(drop.c.x, sz.y)), Coord.z);
	    this.sel = item;
	}
    }

    public void drop(boolean st) {
	if(st && (dl == null)) {
	    dl = new SDropList();
	    dl.add();
	} else if(!st && (dl != null)) {
	    dl.reqdestroy();
	}
    }

    /** A neutral frame on a dark field; the frame turns to the accent colour while the list is open. */
    protected void drawbg(GOut g) {
	g.chcolor(hearthborn.ui.UiPalette.field());
	g.frect(Coord.z, sz);
	g.chcolor(dl != null ? hearthborn.ui.UiPalette.accent() : hearthborn.ui.UiPalette.border());
	g.rect(Coord.z, sz);
	g.chcolor();
    }

    protected void ldrawbg(GOut g, Area area) {
	g.chcolor(hearthborn.ui.UiPalette.field());
	g.frect2(area.ul, area.br);
	g.chcolor();
    }

    protected void ldrawbg(GOut g, I item, int idx, Area area) {
    }

    protected void ldrawsel(GOut g, I item, int idx, Area area) {
	g.chcolor(hearthborn.ui.UiPalette.accentFill());
	g.frect2(area.ul, area.br);
	g.chcolor();
    }

    protected void ldrawslot(GOut g, I item, int idx, Area area) {
	ldrawbg(g, item, idx, area);
	if((sel != null) && (sel == item))
	    ldrawsel(g, item, idx, area);
    }

    public void draw(GOut g) {
	drawbg(g);
	super.draw(g);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if(ev.propagate(this))
	    return(true);
	if(ev.b == 1) {
	    drop.click();
	    return(true);
	}
	return(super.mousedown(ev));
    }

    public static <I> SDropBox<I, Widget> of(int w, int listh, int itemh, List<? extends I>  items, BiFunction<? super I, ? super Coord, ? extends Widget> render, Consumer<? super I> change) {
	return(new SDropBox<I, Widget>(w, listh, itemh) {
		{super.change(items.get(0));}
		protected List<? extends I> items() {return(items);}
		protected Widget makeitem(I item, int idx, Coord sz) {return(render.apply(item, sz));}
		public void change(I item) {
		    super.change(item);
		    change.accept(item);
		}
	    });
    }
}
