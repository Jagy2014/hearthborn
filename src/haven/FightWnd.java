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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import static haven.CharWnd.*;
import static haven.Window.wbox;
import static haven.Inventory.invsq;

public class FightWnd extends Widget {
    public final int nsave;
    public int maxact;
    public final Actions actlist;
    public final Savelist savelist;
    public List<Action> acts = new ArrayList<Action>();
    public final Action[] order;
    public int usesave;
    private final Text[] saves;
    private boolean activeSchoolKnown;
    private int requestedSchool=-1;
    private final ImageInfoBox info;
    private final Label count;
    private final Map<Indir<Resource>, Object[]> actrawinfo = new HashMap<>();

    private static final OwnerContext.ClassResolver<FightWnd> actxr = new OwnerContext.ClassResolver<FightWnd>()
	.add(FightWnd.class, wdg -> wdg)
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    public static final Text.Foundry namef = new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD), 16).aa(true);
    public class Action implements ItemInfo.ResOwner {
	public final Indir<Resource> res;
	private final int id;
	public int a, u;
	private String name;

	public Action(Indir<Resource> res, int id, int a, int u) {this.res = res; this.id = id; this.a = a; this.u = u;}

	public String rendertext() {
	    StringBuilder buf = new StringBuilder();
	    Resource res = this.res.get();
	    buf.append("$img[" + res.name + "]\n\n");
	    buf.append("$b{$font[serif,16]{" + res.flayer(Resource.tooltip).t + "}}\n\n");
	    Resource.Pagina pag = res.layer(Resource.pagina);
	    if(pag != null)
		buf.append(pag.text);
	    return(buf.toString());
	}

	private void a(int a) {
	    this.a = a;
	}

	private void u(int u) {
	    if(this.u != u) {
		this.u = u;
		recount();
	    }
	}

	public Resource resource() {return(res.get());}

	private List<ItemInfo> info = null;
	public List<ItemInfo> info() {
	    if(info == null) {
		Object[] rawinfo = actrawinfo.get(this.res);
		if(rawinfo != null)
		    info = ItemInfo.buildinfo(this, rawinfo);
		else
		    info = Arrays.asList(new ItemInfo.Name(this, res.get().flayer(Resource.tooltip).t));
	    }
	    return(info);
	}
	public <T> T context(Class<T> cl) {return(actxr.context(cl, FightWnd.this));}

	public BufferedImage rendericon() {
	    return(IconInfo.render(res.get().flayer(Resource.imgc).scaled(), info()));
	}

	private Tex icon = null;
	public Tex icon() {
	    if(icon == null)
		icon = new TexI(rendericon());
	    return(icon);
	}

	public BufferedImage renderinfo(int width) {
	    ItemInfo.Layout l = new ItemInfo.Layout(this);
	    l.width = width;
	    List<ItemInfo> info = info();
	    l.cmp.add(rendericon(), Coord.z);
	    ItemInfo.Name nm = ItemInfo.find(ItemInfo.Name.class, info);
	    l.cmp.add(namef.render(hearthborn.L10n.resource("tooltip", res.get().name, nm.str.text)).img, new Coord(0, l.cmp.sz.y + UI.scale(10)));
	    l.cmp.sz = l.cmp.sz.add(0, UI.scale(10));
	    for(ItemInfo inf : info) {
		if((inf != nm) && (inf instanceof ItemInfo.Tip)) {
		    l.add((ItemInfo.Tip)inf);
		}
	    }
	    Resource.Pagina pag = res.get().layer(Resource.pagina);
	    if(pag != null)
		l.add(new ItemInfo.Pagina(this, resdoc(pag.getres(), pag.text)));
	    return(l.render());
	}
    }

    private void recount() {
	int u = 0;
	for(Action act : acts)
	    u += act.u;
	count.settext(String.format("Used: %d/%d", u, maxact));
	count.setcolor((u > maxact)?Color.RED:Color.WHITE);
    }

    public class Actions extends SListBox<Action, Widget> {
	private boolean loading = false;
	private Action drag = null;
	private UI.Grab grab;

	public Actions(Coord sz) {
	    super(sz, attrf.height() + UI.scale(2));
	}

        private List<Action> visible = new ArrayList<>();
        private hearthborn.ui.CombatCategories.Category category = hearthborn.ui.CombatCategories.Category.All;
        private boolean filterDirty = true;
        public void category(hearthborn.ui.CombatCategories.Category category) {
            this.category = category; filterDirty = true;
        }
        protected List<Action> items() { return visible; }
        private void filter() {
            List<Action> next = new ArrayList<>();
            try {
                for(Action action : acts) if(category.matches(action.res.get().name)) next.add(action);
            } catch(Loading loading) { return; }
            java.text.Collator names = java.text.Collator.getInstance(java.util.Locale.forLanguageTag(hearthborn.L10n.language()));
            next.sort((a, b) -> names.compare(a.name == null ? "" : a.name, b.name == null ? "" : b.name));
            visible = next; filterDirty = false;
            if(!visible.contains(sel)) change(visible.isEmpty() ? null : visible.get(0));
            reset(); scrollval(0);
        }

	protected Widget makeitem(Action act, int idx, Coord sz) {
	    return(new Item(sz, act));
	}

	public class Item extends Widget implements DTarget {
	    public final Action item;
	    private final Label use;
	    private int u = -1, a = -1;
	    private UI.Grab grab;
	    private Coord dp;

	    public Item(Coord sz, Action act) {
		super(sz);
		this.item = act;
		Widget prev;
		prev = adda(new IButton("gfx/hud/buttons/add", "u", "d", "h").action(() -> setu(item.u + 1)), sz.x - UI.scale(2), sz.y / 2, 1.0, 0.5);
		prev = adda(new IButton("gfx/hud/buttons/sub", "u", "d", "h").action(() -> setu(item.u - 1)), prev.c.x - UI.scale(2), sz.y / 2, 1.0, 0.5);
		prev = use = adda(new Label("0/0", attrf), prev.c.x - UI.scale(5), sz.y / 2, 1.0, 0.5);
		add(IconText.of(Coord.of(prev.c.x - UI.scale(2), sz.y), act::rendericon, () -> act.res.get().flayer(Resource.tooltip).t), Coord.z);
	    }

	    public void tick(double dt) {
		if((item.u != this.u) || (item.a != this.a))
                {
                    use.settext(String.format("%d/%d", this.u = item.u, this.a = item.a));
                    use.setcolor(hearthborn.ui.CombatCategories.usageColor(item.u, item.a));
                }
		super.tick(dt);
	    }

	    public boolean mousewheel(MouseWheelEvent ev) {
		if(ui.modshift) {
		    setu(item.u - ev.a);
		    return(true);
		}
		return(super.mousewheel(ev));
	    }

	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.propagate(this) || super.mousedown(ev))
		    return(true);
		if(ev.b == 1) {
		    change(item);
		    grab = ui.grabmouse(this);
		    dp = ev.c;
		}
		return(true);
	    }

	    public void mousemove(MouseMoveEvent ev) {
		super.mousemove(ev);
		if((grab != null) && (ev.c.dist(dp) > 5)) {
		    grab.remove();
		    grab = null;
		    drag(item);
		}
	    }

	    public boolean mouseup(MouseUpEvent ev) {
		if((grab != null) && (ev.b == 1)) {
		    grab.remove();
		    grab = null;
		    return(true);
		}
		return(super.mouseup(ev));
	    }

	    public boolean setu(int u) {
		u = Utils.clip(u, 0, item.a);
		int s;
		for(s = 0; s < order.length; s++) {
		    if(order[s] == item)
			break;
		}
		if(u > 0) {
		    if(s == order.length) {
			for(s = 0; s < order.length; s++) {
			    if(order[s] == null)
				break;
			}
			if(s == order.length)
			    return(false);
			order[s] = item;
		    }
		} else {
		    if(s < order.length)
			order[s] = null;
		}
		item.u(u);
		return(true);
	    }

	    public boolean drop(Coord cc, Coord ul) {
		return(false);
	    }

	    public boolean iteminteract(Coord cc, Coord ul) {
		FightWnd.this.wdgmsg("itemact", item.id, ui.modflags());
		return(true);
	    }
	}

	public void change(Action act) {
	    if(act != null)
		info.set(() -> new TexI(act.renderinfo(info.sz.x - UI.scale(20))));
	    else if(sel != null)
		info.set((Tex)null);
	    super.change(act);
	}

	public void tick(double dt) {
	    if(loading) {
		loading = false;
		for(Action act : acts) {
		    try {
			act.name = act.res.get().flayer(Resource.tooltip).t;
		    } catch(Loading l) {
			act.name = "...";
			loading = true;
		    }
		}
                Collections.sort(acts, Comparator.comparing(act -> act.name));
                filterDirty = true;
            }
            if(filterDirty) filter();
            super.tick(dt);
	}

	public void draw(GOut g) {
	    if(drag != null) {
		try {
		    Tex dt = drag.res.get().flayer(Resource.imgc).tex();
		    ui.drawafter(ag -> ag.image(dt, ui.mc.sub(dt.sz().div(2))));
		} catch(Loading l) {
		}
	    }
	    super.draw(g);
	}

	public void drag(Action act) {
	    if(grab == null)
		grab = ui.grabmouse(this);
	    drag = act;
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if((grab != null) && (ev.b == 1)) {
		grab.remove();
		grab = null;
		if(drag != null) {
		    DropTarget.dropthing(ui.root, ev.c.add(rootpos()), drag);
		    drag = null;
		}
		return(true);
	    }
	    return(super.mouseup(ev));
	}
    }

    public int findorder(Action a) {
	for(int i = 0; i < order.length; i++) {
	    if(order[i] == a)
		return(i);
	}
	return(-1);
    }

    public class BView extends Widget implements DropTarget {
	private UI.Grab grab;
	private Action drag;
	private Coord dp;
	private final Coord[] animoff = new Coord[order.length];
	private final double[] animpr = new double[order.length];
	private boolean anim = false;

	private BView() {
	    super(new Coord(((invsq.sz().x + UI.scale(2)) * (order.length - 1)) + (UI.scale(10) * ((order.length - 1) / 5)), 0).add(invsq.sz()).add(0, 2 * (Text.std.height() + UI.scale(2))));
	}

	private Coord itemc(int i) {
	    return(new Coord(((invsq.sz().x + UI.scale(2)) * i) + (UI.scale(10) * (i / 5)), Text.std.height() + UI.scale(2)));
	}

	private int citem(Coord c) {
	    for(int i = 0; i < order.length; i++) {
		if(c.isect(itemc(i), invsq.sz()))
		    return(i);
	    }
	    return(-1);
	}

        private final Tex[] keys = new Tex[order.length], quantities = new Tex[order.length];
        private final String[] keyNames = new String[order.length], quantityNames = new String[order.length];
        public void dispose() {
            for(Tex key : keys) if(key != null) key.dispose();
            for(Tex quantity : quantities) if(quantity != null) quantity.dispose();
            super.dispose();
        }
	public void draw(GOut g) {
	    int[] reo = null;
	    if(anim) {
		reo = new int[order.length];
		for(int i = 0, a = 0, b = order.length - 1; i < order.length; i++) {
		    if(animoff[i] == null)
			reo[a++] = i;
		    else
			reo[b--] = i;
		}
	    }
	    for(int io = 0; io < order.length; io++) {
		int i = (reo == null)?io:reo[io];
		Coord c = itemc(i);
		g.image(invsq, c);
		Action act = order[i];
		try {
		    if(act != null) {
			Coord ic = c.add(UI.scale(1), UI.scale(1));
			if(animoff[i] != null) {
			    ic = ic.add(animoff[i].mul(Math.pow(1.0 - animpr[i], 3)));
			}
			Tex tex = act.res.get().flayer(Resource.imgc).tex();
			g.image(tex, ic);
		    }
		} catch(Loading l) {}
                String keyName = i < Fightsess.kb_acts.length ? Fightsess.kb_acts[i].key().name() : "";
                if(!Objects.equals(keyNames[i], keyName)) {
                    if(keys[i] != null) keys[i].dispose();
                    keys[i] = Text.render(keyNames[i] = keyName).tex();
                }
                String quantity = act == null ? "" : act.u + "/" + act.a;
                if(!Objects.equals(quantityNames[i], quantity)) {
                    if(quantities[i] != null) quantities[i].dispose();
                    quantities[i] = Text.render(quantityNames[i] = quantity).tex();
                }
                int center = c.x + invsq.sz().x / 2;
                g.chcolor(0, 0, 0, 180);
                g.frect(Coord.of(c.x, 0), Coord.of(invsq.sz().x, Text.std.height() + UI.scale(2)));
                g.chcolor();
                g.aimage(keys[i], Coord.of(center, 0), .5, 0);
                if(act != null) {
                    g.chcolor(hearthborn.ui.CombatCategories.usageColor(act.u, act.a));
                    g.aimage(quantities[i], Coord.of(center, c.y + invsq.sz().y + UI.scale(2)), .5, 0);
                    g.chcolor();
                }
	    }
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 1) {
		int s = citem(ev.c);
		if(s >= 0) {
		    Action act = order[s];
		    actlist.change(act);
		    actlist.display();
		    if(act != null) {
			grab = ui.grabmouse(this);
			drag = act;
			dp = ev.c;
		    }
		    return(true);
		}
	    } else if(ev.b == 3) {
		int s = citem(ev.c);
		if(s >= 0) {
		    if(order[s] != null)
			order[s].u(0);
		    order[s] = null;
		    return(true);
		}
	    }
	    return(super.mousedown(ev));
	}

	public void mousemove(MouseMoveEvent ev) {
	    super.mousemove(ev);
	    if(dp != null) {
		if(ev.c.dist(dp) > 5) {
		    grab.remove();
		    actlist.drag(drag);
		    grab = null;
		    drag = null;
		    dp = null;
		}
	    }
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(grab != null) {
		grab.remove();
		grab = null;
		drag = null;
		dp = null;
		return(true);
	    }
	    return(super.mouseup(ev));
	}

	private void animate(int s, Coord off) {
	    animoff[s] = off;
	    animpr[s] = 0.0;
	    anim = true;
	}

	public boolean dropthing(Coord c, Object thing) {
	    if(thing instanceof Action) {
		Action act = (Action)thing;
		int s = citem(c);
		if(s < 0)
		    return(false);
		if(order[s] != act) {
		    int cp = findorder(act);
		    if(cp >= 0)
			order[cp] = order[s];
		    if(order[s] != null) {
			if(cp >= 0) {
			    animate(cp, itemc(s).sub(itemc(cp)));
			} else {
			    order[s].u(0);
			}
		    }
		    order[s] = act;
		    if(act.u < 1)
			act.u(1);
		}
		return(true);
	    }
	    return(false);
	}

	public void tick(double dt) {
	    if(anim) {
		boolean na = false;
		for(int i = 0; i < order.length; i++) {
		    if(animoff[i] != null) {
			if((animpr[i] += (dt * 3)) > 1.0)
			    animoff[i] = null;
			else
			    na = true;
		    }
		}
		anim = na;
	    }
	}
    }

    public class Savelist extends SListBox<Integer, Widget> {
	private final List<Integer> items = Utils.range(nsave);

	public Savelist(Coord sz) {
	    super(sz, attrf.height() + UI.scale(2));
	    sel = Integer.valueOf(0);
	}

	protected List<Integer> items() {return(items);}
	protected Widget makeitem(Integer n, int idx, Coord sz) {return(new Item(sz, n));}

	public class Item extends Widget implements ReadLine.Owner {
	    public final int n;
	    private Text.Line redit = null;
	    private ReadLine ed;
	    private double focusstart;

	    public Item(Coord sz, int n) {
		super(sz);
		this.n = n;
		setcanfocus(true);
	    }

	    public void draw(GOut g) {
		if(ed != null) {
		    if(redit == null)
			redit = attrf.render(ed.line());
		    g.aimage(redit.tex(), Coord.of(UI.scale(20), itemh / 2), 0.0, 0.5);
		    if(hasfocus && (((Utils.rtime() - focusstart) % 1.0) < 0.5)) {
			int cx = redit.advance(ed.point());
			g.chcolor(255, 255, 255, 255);
			Coord co = Coord.of(UI.scale(20) + cx + UI.scale(1), (sz.y - redit.sz().y) / 2);
			g.line(co, co.add(0, redit.sz().y), 1);
			g.chcolor();
		    }
		} else {
		    g.aimage(saves[n].tex(), Coord.of(UI.scale(20), itemh / 2), 0.0, 0.5);
		}
		if(n == usesave)
		    g.aimage(CheckBox.smark, Coord.of(itemh / 2), 0.5, 0.5);
	    }

	    private Coord lc = null;
	    private double lt = 0;
	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.propagate(this))
		    return(true);
		if(ev.b == 1) {
		    double now = Utils.rtime();
		    Savelist.this.change(n);
		    if(((now - lt) < 0.5) && (ev.c.dist(lc) < 10) && (saves[n] != unused)) {
			if(n == usesave) {
			    ed = ReadLine.make(this, saves[n].text);
			    redit = null;
			    parent.setfocus(this);
			    focusstart = now;
			} else {
			    load(n);
			    use(n);
			}
		    } else {
			lt = now;
			lc = ev.c;
		    }
		    return(true);
		}
		return(super.mousedown(ev));
	    }

	    public void done(ReadLine buf) {
		saves[n] = attrf.render(buf.line());
		ed = null;
	    }

	    public void changed(ReadLine buf) {
		redit = null;
	    }

	    public void tick(double dt) {
		super.tick(dt);
		if((ed != null) && (sel != n)) {
		    ed = null;
		}
	    }

	    public boolean keydown(KeyDownEvent ev) {
		if(ed != null) {
		    if(key_esc.match(ev)) {
			ed = null;
			return(true);
		    } else {
			return(ed.key(ev));
		    }
		}
		return(super.keydown(ev));
	    }
	}
    }

    @RName("fmg")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new FightWnd(Utils.iv(args[0]), Utils.iv(args[1]), Utils.iv(args[2])));
	}
    }

    public boolean activateSaved(int n) {
        if(n<0||n>=nsave||saves[n]==null||saves[n]==unused)return false;
        load(n);use(n);return true;
    }

    public void load(int n) {
	wdgmsg("load", n);
    }

    public void save(int n) {
	List<Object> args = new LinkedList<Object>();
	args.add(n);
	if(saves[n] != unused)
	    args.add(saves[n].text);
	for(int i = 0; i < order.length; i++) {
	    if(order[i] == null) {
		args.add(null);
	    } else {
		args.add(order[i].id);
		args.add(order[i].u);
	    }
	}
	wdgmsg("save", args.toArray(new Object[0]));
    }

    public void use(int n) {
        requestedSchool=n;
	wdgmsg("use", n);
    }

    private Text unused = new Text.Foundry(attrf.font.deriveFont(java.awt.Font.ITALIC)).aa(true).render(hearthborn.L10n.tr("Unused save"));
    public FightWnd(int nsave, int nact, int max) {
	super(Coord.z);
	this.nsave = nsave;
	this.maxact = max;
	this.order = new Action[nact];
	this.saves = new Text[nsave];
	for(int i = 0; i < nsave; i++)
	    saves[i] = unused;

	Widget p;
	Coord box = wbox.btloff();
	info = add(new ImageInfoBox(UI.scale(new Coord(380, 190))), UI.scale(new Coord(5, 35)).add(box));
	Frame.around(this, Collections.singletonList(info));

	add(CharWnd.settip(hearthborn.ui.UiTheme.heading(hearthborn.L10n.text("label", "Martial Arts & Combat Schools")), "gfx/hud/chr/tips/combat"), 0, 0);
	Coord listc = Coord.of(info.pos("ur").x + UI.scale(17), info.c.y);
	actlist = add(new Actions(UI.scale(250, 160)), listc.add(0, UI.scale(30)));
	add(new hearthborn.ui.CombatCategories(actlist::category), listc);
	actFrame = Frame.around(this, Collections.singletonList(actlist));

	p = add(new BView(), UI.scale(5, 238));
	count = add(new Label(""), p.pos("ur").adds(10, 0));

	savelist = add(new Savelist(UI.scale(370, 60)), p.pos("bl").adds(0, 10).add(wbox.btloff()));
	p = Frame.around(this, Collections.singletonList(savelist));
	p = add(new Button(UI.scale(110), "Load", false).action(() -> {
		    load(savelist.sel);
		    use(savelist.sel);
	}), p.pos("ur").adds(10, 0));
	p = add(new Button(UI.scale(110), "Save", false).action(() -> {
		    if(savelist.sel < 0) {
			getparent(GameUI.class).error("No save entry selected.");
		    } else {
			save(savelist.sel);
			use(savelist.sel);
		    }
	}), p.pos("bl").adds(0, 2));
	pack();
    }

    private Frame actFrame;

    /** Stretch the action list and its frame so the tab fills a wider character window. */
    public void fitWidth(int width) {
	int extra = width - sz.x;
	if(extra <= 0)
	    return;
	Coord grow = Coord.of(extra, 0);
	actlist.resize(actlist.sz.add(grow));
	actFrame.resize(actFrame.sz.add(grow));
	resize(sz.add(grow));
    }

    public Action findact(int resid) {
	for(Action act : acts) {
	    if(act.id == resid)
		return(act);
	}
	return(null);
    }

    private Text schoolName(String name) {
        if(name.matches("(?i)Saved school [0-9]+")) {
            String number = name.substring(name.lastIndexOf(' ') + 1);
            return new hearthborn.l10n.LocalizedText(name, attrf.render(hearthborn.L10n.tr("School") + " " + number));
        }
        return attrf.render(name);
    }

    public void uimsg(String nm, Object... args) {
	if(nm == "avail") {
	    List<Action> acts = new ArrayList<Action>();
	    int a = 0;
	    while(args[a] != null) {
		int resid = Utils.iv(args[a++]);
		if(resid < 0)
		    break;
		int av = Utils.iv(args[a++]);
		Action pact = findact(resid);
		if(pact == null) {
		    acts.add(new Action(ui.sess.getres(resid), resid, av, 0));
		} else {
		    acts.add(pact);
		    pact.a(av);
		}
	    }
	    this.acts = acts;
	    actlist.loading = true;
	} else if(nm == "tt") {
	    Indir<Resource> res = ui.sess.getresv(args[0]);
	    Object[] rawinfo = (Object[])args[1];
	    actrawinfo.put(res, rawinfo);
	} else if(nm == "used") {
	    int a = 0;
	    for(Action act : acts)
		act.u(0);
	    for(int i = 0; i < order.length; i++) {
		int resid = Utils.iv(args[a++]);
		if(resid < 0) {
		    order[i] = null;
		    continue;
		}
		int us = Utils.iv(args[a++]);
		(order[i] = findact(resid)).u(us);
	    }
	} else if(nm == "saved") {
	    int fl = Utils.iv(args[0]);
	    for(int i = 0; i < nsave; i++) {
		if((fl & (1 << i)) != 0) {
		    if(args[i + 1] instanceof String)
			saves[i] = schoolName((String)args[i + 1]);
		    else
			saves[i] = new hearthborn.l10n.LocalizedText(String.format("Saved school %d", i + 1),
                            attrf.render(hearthborn.L10n.format("Saved school %d", i + 1)));
		} else {
		    saves[i] = unused;
		}
	    }
	} else if(nm == "use") {
            int previous=usesave;
            usesave = Utils.iv(args[0]);
            savelist.change(Integer.valueOf(usesave));
            if((requestedSchool==usesave||activeSchoolKnown&&previous!=usesave)&&usesave>=0&&usesave<nsave&&saves[usesave]!=unused){
                GameUI gui=getparent(GameUI.class);
                if(gui!=null)gui.ui.msg(hearthborn.L10n.format("Activated combat school %d: %s",usesave+1,hearthborn.l10n.LocalizedText.shown(saves[usesave])));
            }
            requestedSchool=-1;activeSchoolKnown=true;
	} else if(nm == "max") {
	    maxact = Utils.iv(args[0]);
	    recount();
	} else {
	    super.uimsg(nm, args);
	}
    }
}
