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
import java.awt.image.WritableRaster;

public class Inventory extends Widget implements DTarget {
    public static final Coord sqsz = UI.scale(new Coord(32, 32)).add(1, 1);
    public static final Tex invsq, invsqmask;
    public boolean dropul = true;
    public Coord isz;
    private double viewScale = hearthborn.inventory.InventoryScale.SCALE;
    public double viewScale(){return viewScale;}
    private final hearthborn.inventory.ContainerTools hearthbornTools = new hearthborn.inventory.ContainerTools(this);
    public void collapseContainerTools(){hearthbornTools.collapse();}
    public boolean[] sqmask = null;
    Map<GItem, WItem> wmap = new HashMap<GItem, WItem>();

    static {
	Coord sz = sqsz.add(1, 1);
	WritableRaster buf = PUtils.imgraster(sz);
	for(int i = 1, y = sz.y - 1; i < sz.x - 1; i++) {
	    buf.setSample(i, 0, 0, 34); buf.setSample(i, 0, 1, 34); buf.setSample(i, 0, 2, 34); buf.setSample(i, 0, 3, 180);
	    buf.setSample(i, y, 0, 34); buf.setSample(i, y, 1, 34); buf.setSample(i, y, 2, 34); buf.setSample(i, y, 3, 180);
	}
	for(int i = 1, x = sz.x - 1; i < sz.y - 1; i++) {
	    buf.setSample(0, i, 0, 34); buf.setSample(0, i, 1, 34); buf.setSample(0, i, 2, 34); buf.setSample(0, i, 3, 180);
	    buf.setSample(x, i, 0, 34); buf.setSample(x, i, 1, 34); buf.setSample(x, i, 2, 34); buf.setSample(x, i, 3, 180);
	}
	for(int y = 1; y < sz.y - 1; y++) {
	    for(int x = 1; x < sz.x - 1; x++) {
		buf.setSample(x, y, 0, 0); buf.setSample(x, y, 1, 0); buf.setSample(x, y, 2, 0); buf.setSample(x, y, 3, 125);
	    }
	}
	invsq = new TexI(PUtils.rasterimg(buf));
	WritableRaster masked = PUtils.imgraster(sz);
	for(int i = 1, y = sz.y - 1; i < sz.x - 1; i++) {
	    masked.setSample(i, 0, 0, 96); masked.setSample(i, 0, 1, 90); masked.setSample(i, 0, 2, 74); masked.setSample(i, 0, 3, 230);
	    masked.setSample(i, y, 0, 96); masked.setSample(i, y, 1, 90); masked.setSample(i, y, 2, 74); masked.setSample(i, y, 3, 230);
	}
	for(int i = 1, x = sz.x - 1; i < sz.y - 1; i++) {
	    masked.setSample(0, i, 0, 96); masked.setSample(0, i, 1, 90); masked.setSample(0, i, 2, 74); masked.setSample(0, i, 3, 230);
	    masked.setSample(x, i, 0, 96); masked.setSample(x, i, 1, 90); masked.setSample(x, i, 2, 74); masked.setSample(x, i, 3, 230);
	}
	for(int y = 1; y < sz.y - 1; y++) {
	    for(int x = 1; x < sz.x - 1; x++) {
		masked.setSample(x, y, 0, 0); masked.setSample(x, y, 1, 0); masked.setSample(x, y, 2, 0); masked.setSample(x, y, 3, 80);
	    }
	}
	invsqmask = new TexI(PUtils.rasterimg(masked));
    }

    @RName("inv")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Inventory((Coord)args[0]));
	}
    }

    public void draw(GOut g) {
	g=hearthborn.inventory.InventoryScale.graphics(g,sqsz.mul(isz).add(1,1),viewScale);
	hearthborn.ui.UiTheme.cells(g, isz, sqmask, !hearthborn.ui.StudyProgress.isStudy(this));
	super.draw(g);
    }
	
    public Inventory(Coord sz) {
	super(hearthborn.inventory.InventoryScale.size(sqsz.mul(sz).add(1, 1)));
	isz = sz;
    }

    protected void attached() {
	super.attached();
	GameUI gui = getparent(GameUI.class);
	Window window = getparent(Window.class);
	// The inventory is often built before its window joins the interface, so this
	// runs once the cupboard is actually open. The main inventory and item
	// contents, such as a belt, stay in compact mode.
	if(gui != null && gui.compactUI() && window != null && (gui.maininv == null || window != gui.maininv.parent) && !(window instanceof GItem.ContentsWindow))
	    gui.compactLayout.leave();
	boolean pocket = getparent(GItem.class) != null || (gui != null && gui.maininv == this);
	if(hearthborn.timers.ObjectTimers.largeGrid(window) || (!pocket && hearthborn.inventory.ContainerTools.container(window)))
	    enlargeGrid(window);
    }

    /** Grow the grid and slide a fuel bar out from under it. The window frame follows. */
    private void enlargeGrid(Window window) {
	Coord before = sz;
	viewScale = hearthborn.inventory.InventoryScale.CONTAINER; // 1.35: a little above a bag, not the old 1.6 grid
	Coord grid = sqsz.mul(isz).add(1, 1);
	super.resize(hearthborn.inventory.InventoryScale.size(grid, viewScale));
	Coord extra = sz.sub(before);
	int right = c.x + before.x;
	boolean oven = hearthborn.timers.ObjectTimers.oven(window);
	for(Widget child = window.child; child != null; child = child.next) {
	    if(child == this || child == window.deco)
		continue;
	    if(child.c.x >= right - UI.scale(8)) {
		child.move(child.c.add(extra.x, 0));
		if(child instanceof VMeter && hearthborn.timers.ObjectTimers.stretchFuelMeter(window))
		    child.resize(oven?hearthborn.ui.FuelReadout.width(VMeter.bg.sz().x):VMeter.bg.sz().x, sz.y);
	    }
	}
	window.pack();
    }

    public void resize(Coord size){super.resize(hearthborn.inventory.InventoryScale.size(size,viewScale));}

    public boolean handle(Event event){
        if(event instanceof PointerEvent){
            PointerEvent pointer=((PointerEvent)event).derive(hearthborn.inventory.InventoryScale.logical(((PointerEvent)event).c,viewScale));
            event.stop();pointer.propagate=true;pointer.handling=this;
            return super.handle(pointer)||pointer.propagate(this);
        }
        return super.handle(event);
    }
    
    @Override
    public void tick(double dt) {
	super.tick(dt);
	hearthbornTools.tick(dt);
    }

    @Override
    public void dispose() {
	hearthbornTools.dispose();
	super.dispose();
    }

    public boolean mousewheel(MouseWheelEvent ev) {
        if(hearthborn.ui.StudyProgress.locked(this)) return true;
	if(ui.modshift) {
	    Inventory minv = getparent(GameUI.class).maininv;
	    if(minv != this) {
		if(ev.a < 0)
		    wdgmsg("invxf", minv.wdgid(), 1);
		else if(ev.a > 0)
		    minv.wdgmsg("invxf", this.wdgid(), 1);
	    }
	}
	return(true);
    }
    
    public void addchild(Widget child, Object... args) {
        hearthborn.ui.HandMenu.invalidate();
	add(child);
	Coord c = (Coord)args[0];
	if(child instanceof GItem) {
	    GItem i = (GItem)child;
	    wmap.put(i, add(new WItem(i), c.mul(sqsz).add(1, 1)));
	}
    }
    
    public void cdestroy(Widget w) {
        hearthborn.ui.HandMenu.invalidate();
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    ui.destroy(wmap.remove(i));
	}
    }
    
    public boolean drop(Coord cc, Coord ul) {
        if(hearthborn.ui.StudyProgress.locked(this)) return true;
	Coord dc;
	if(dropul)
	    dc = ul.add(sqsz.div(2)).div(sqsz);
	else
	    dc = cc.div(sqsz);
	wdgmsg("drop", dc);
	return(true);
    }
	
    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }
	
    public void uimsg(String msg, Object... args) {
	if(msg == "sz") {
	    isz = (Coord)args[0];
	    resize(sqsz.mul(isz).add(1,1));
	    sqmask = null;
	} else if(msg == "mask") {
	    boolean[] nmask;
	    if(args[0] == null) {
		nmask = null;
	    } else {
		nmask = new boolean[isz.x * isz.y];
		byte[] raw = (byte[])args[0];
		for(int i = 0; i < isz.x * isz.y; i++)
		    nmask[i] = (raw[i >> 3] & (1 << (i & 7))) != 0;
	    }
	    this.sqmask = nmask;
	} else if(msg == "mode") {
	    dropul = !Utils.bv(args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }
}
