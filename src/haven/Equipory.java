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
import static haven.Inventory.invsq;

public class Equipory extends Widget implements DTarget {
    private static final Resource.Image bgi = Resource.loadrimg("gfx/hud/equip/bg");
    private static final int yo = Inventory.sqsz.y, sh = 11;
    private static final Tex bg = new TexI(PUtils.uiscale(bgi.img, Coord.of((sh * yo * bgi.sz.x) / bgi.sz.y, sh * yo)));
    private static final int rx = invsq.sz().x + bg.sz().x;
    public static final Coord bgc = new Coord(invsq.sz().x, 0);
    public static final Coord ecoords[] = {
	new Coord( 0,  0 * yo),
	new Coord( 0,  1 * yo),
	new Coord( 0,  3 * yo),
	new Coord(rx,  3 * yo),
	new Coord( 0,  4 * yo),
	new Coord(rx,  4 * yo),
	new Coord( 0,  5 * yo),
	new Coord(rx,  5 * yo),
	new Coord( 0,  6 * yo),
	new Coord(rx,  6 * yo),
	new Coord( 0,  7 * yo),
	new Coord(rx,  7 * yo),
	new Coord( 0,  9 * yo),
	new Coord(rx,  9 * yo),
	new Coord( 0, 10 * yo),
	new Coord(rx, 10 * yo),
	new Coord(invsq.sz().x, 0 * yo),
	new Coord(rx,  1 * yo),
	new Coord(rx,  2 * yo),
	new Coord( 0,  8 * yo),
	new Coord(rx,  8 * yo),
	new Coord(rx,  0 * yo),
	new Coord( 0,  2 * yo),
    };
    public static final Tex[] ebgs = new Tex[ecoords.length];
    public static final Text[] etts = new Text[ecoords.length];
    /** Same step as a furnace grid: the paper doll grows, the marks stay the quality size. */
    public static final double SCALE = hearthborn.inventory.InventoryScale.OVEN;
    private Coord logical = Coord.z;
    static Coord isz;
    static {
	isz = new Coord();
	for(Coord ec : ecoords) {
	    if(ec.x + invsq.sz().x > isz.x)
		isz.x = ec.x + invsq.sz().x;
	    if(ec.y + invsq.sz().y > isz.y)
		isz.y = ec.y + invsq.sz().y;
	}
	for(int i = 0; i < ebgs.length; i++) {
	    Resource bgres = Resource.local().loadwait("gfx/hud/equip/ep" + i);
	    Resource.Image img = bgres.layer(Resource.imgc);
	    if(img != null) {
		ebgs[i] = img.tex();
		etts[i] = Text.render(bgres.flayer(Resource.tooltip).t);
	    }
	}
    }
    Map<GItem, Collection<WItem>> wmap = new HashMap<>();
    private final Avaview ava;

    @RName("epry")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    long gobid;
	    if(args.length < 1)
		gobid = -2;
	    else if(args[0] == null)
		gobid = -1;
	    else
		gobid = Utils.uiv(args[0]);
	    return(new Equipory(gobid));
	}
    }

    protected void added() {
	if(ava.avagob == -2)
	    ava.avagob = getparent(GameUI.class).plid;
	super.added();
    }

    private final hearthborn.ui.Parasites parasites=new hearthborn.ui.Parasites();
    private final hearthborn.ui.EquipmentFeet feet=new hearthborn.ui.EquipmentFeet(this);
    private Widget parasiteToggle, bootToggle;
    public Equipory(long gobid) {
	super(isz);
	ava = add(new Avaview(bg.sz(), gobid, "equcam") {
		public boolean mousedown(MouseDownEvent ev) {
		    return(false);
		}

		public void draw(GOut g) {
		    g.image(bg, Coord.z);
		    super.draw(g);
		    feet.paint(g);
		}

		final FColor cc = new FColor(0, 0, 0, 0);
		protected FColor clearcolor() {return(cc);}
	    }, bgc);
        Widget bonuses=add(new hearthborn.ui.EquipmentBonuses(this,ava.sz.y),Coord.of(isz.x+UI.scale(14),0));
        parasiteToggle=add(new CheckBox("Auto drop parasites").state(hearthborn.ui.Parasites::enabled).set(hearthborn.ui.Parasites::enabled),
            Coord.of(0, Math.max(isz.y,bonuses.pos("bl").y)+UI.scale(4)));
        parasiteToggle.settip("Drop leeches and ticks as soon as they attach to you.");
        bootToggle=add(new CheckBox("Auto-Switch Bunny Slippers/Armor Boots").state(hearthborn.combat.BootSwitch::enabled).set(hearthborn.combat.BootSwitch::enabled),
            parasiteToggle.pos("ur").adds(12, 0));
        bootToggle.settip(hearthborn.L10n.tr("Right-click a rabbit to equip bunny slippers. A left click, a click on empty ground or the map, or a right click on anything else equips the best armor boots in the main inventory: plate boots, armored striders, ranger's boots, then leather boots."));
        pack();

    }
    public void pack() {
        super.pack();
        logical = sz;
        super.resize(hearthborn.inventory.InventoryScale.size(logical, SCALE));
    }
    public void draw(GOut g) {
        if(logical.x > 0 && logical.y > 0)
            g = hearthborn.inventory.InventoryScale.graphics(g, logical, SCALE);
        drawslots(g);
        super.draw(g);
    }
    public boolean handle(Event event) {
        if(event instanceof PointerEvent && SCALE != 1) {
            PointerEvent pointer = ((PointerEvent)event).derive(hearthborn.inventory.InventoryScale.logical(((PointerEvent)event).c, SCALE));
            event.stop();
            pointer.propagate = true;
            pointer.handling = this;
            return super.handle(pointer) || pointer.propagate(this);
        }
        return super.handle(event);
    }
    public void tick(double dt) {
        super.tick(dt);
        GameUI gui=getparent(GameUI.class);
        parasiteToggle.show(gui!=null && gui.equipment()==this);
        bootToggle.show(gui!=null && gui.equipment()==this);
        parasites.tick(this,dt);
        feet.tick(dt);
    }
    public void dispose(){feet.dispose();super.dispose();}

    public java.util.List<GItem> equippedItems() {
        synchronized(wmap){return new ArrayList<>(wmap.keySet());}
    }
    public static interface SlotInfo {
	public int slots();
    }

    public void addchild(Widget child, Object... args) {
	if(child instanceof GItem) {
	    add(child);
	    GItem g = (GItem)child;
	    ArrayList<WItem> v = new ArrayList<>();
	    for(int i = 0; i < args.length; i++) {
		int ep = Utils.iv(args[i]);
		if(ep < ecoords.length)
		    v.add(add(new WItem(g), ecoords[ep].add(1, 1)));
	    }
	    v.trimToSize();
	    synchronized(wmap) {
		wmap.put(g, v);
	    }
	    hearthborn.world.GobMarks.equipment(this);
            hearthborn.scripts.HandEquip.observed(this,g,args);
	} else {
	    super.addchild(child, args);
	}
    }

    public void cdestroy(Widget w) {
        hearthborn.ui.HandMenu.invalidate();
	super.cdestroy(w);
	if(w instanceof GItem) {
	    GItem i = (GItem)w;
	    Collection<WItem> views;
	    synchronized(wmap) {
		views = wmap.remove(i);
	    }
	    if(views != null)
		for(WItem v : views)
		    ui.destroy(v);
	    hearthborn.world.GobMarks.equipment(this);
	}
    }

    /** Live check, a loaded bat cape hides the radius. */
    public boolean hasBatCape() {
	GItem[] items;
	synchronized(wmap) {
	    items = wmap.keySet().toArray(new GItem[0]);
	}
	for(GItem item : items) {
	    try {
		Resource resource = item.resource();
		if(resource != null && resource.name.contains("/batcape"))
		    return true;
	    } catch(Loading ignored) {
	    }
	}
	return false;
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "pop") {
	    ava.avadesc = Composited.Desc.decode(ui.sess, args);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public int epat(Coord c) {
	for(int i = 0; i < ecoords.length; i++) {
	    if(c.isect(ecoords[i], invsq.sz()))
		return(i);
	}
	return(-1);
    }

    public boolean drop(Coord cc, Coord ul) {
	wdgmsg("drop", epat(cc));
	return(true);
    }

    public void drawslots(GOut g) {
	int slots = 0;
	GameUI gui = getparent(GameUI.class);
	if((gui != null) && (gui.vhand != null)) {
	    try {
		SlotInfo si = ItemInfo.find(SlotInfo.class, gui.vhand.item.info());
		if(si != null)
		    slots = si.slots();
	    } catch(Loading l) {
	    }
	}
	for(int i = 0; i < ecoords.length; i++) {
	    if((slots & (1 << i)) != 0) {
		g.chcolor(255, 255, 0, 64);
		g.frect(ecoords[i].add(1, 1), invsq.sz().sub(2, 2));
		g.chcolor();
	    }
	    if(hearthborn.ui.UiTheme.light()) {
		/* The same cell as an inventory on parchment, not the dark one with gold edges. */
		hearthborn.ui.UiTheme.panel(g, ecoords[i], invsq.sz());
		hearthborn.ui.UiTheme.slot(g, ecoords[i], invsq.sz());
	    } else
		g.image(invsq, ecoords[i]);
	    if(ebgs[i] != null)
		g.image(ebgs[i], ecoords[i]);
	}
    }

    public Object tooltip(Coord c, Widget prev) {
	int sl = epat(c);
	if(sl >= 0)
	    return(etts[sl]);
	return(null);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	return(false);
    }
}
