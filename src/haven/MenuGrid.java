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
import haven.render.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import haven.Resource.AButton;
import haven.ItemInfo.AttrCache;

public class MenuGrid extends Widget implements KeyBinding.Bindable {
    public final static Tex bg = Inventory.invsq;
    public final static Coord bgsz = Inventory.sqsz;
    public final static RichText.Foundry ttfnd = new RichText.Foundry(TextAttribute.FAMILY, hearthborn.UiFonts.family("SansSerif"), TextAttribute.SIZE, UI.scale(10f));
    private static Coord gsz = new Coord(4, 4);
    private Coord currentGrid=gsz;
    public Coord gridSize(){return currentGrid==null?gsz:currentGrid;}
    public Coord contentSize(){return bgsz.mul(gridSize()).add(1,1);}
    public final Set<Pagina> paginae = new HashSet<Pagina>();
    public Pagina cur;
    public int pagseq = 0;
    public final hearthborn.ui.ActionShortcuts shortcuts;
    private final Map<Object, Pagina> pmap = new CacheMap<>(CacheMap.RefType.WEAK);
    private Pagina dragging;
    private Collection<PagButton> curbtns = Collections.emptyList();
    private PagButton pressed, layout[][] = new PagButton[gsz.x][gsz.y];
    private UI.Grab grab;
    private int curoff = 0;
    private boolean recons = true, showkeys = false;
    private double fstart;
	
    @RName("scm")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new MenuGrid());
	}
    }

    public static class Pagina {
	public final MenuGrid scm;
	public final Object id;
	public Indir<Resource> res;
	public byte[] sdt = null;
	public int anew, tnew;
	public Object[] rawinfo = {};

	public Pagina(MenuGrid scm, Object id, Indir<Resource> res) {
	    this.scm = scm;
	    this.id = id;
	    this.res = res;
	}

	public Resource res() {
	    return(res.get());
	}

	public Message data() {
	    return((sdt == null) ? Message.nil : new MessageBuf(sdt));
	}

	private void invalidate() {
	    button = null;
	}

	private PagButton button = null;
	public PagButton button() {
	    if(button == null) {
		Resource res = res();
		PagButton.Factory f = res.getcode(PagButton.Factory.class, false);
		if(f == null)
		    button = new PagButton(this);
		else
		    button = f.make(this);
	    }
	    return(button);
	}

	public Pagina parent() {
	    return(button().parent());
	}
    }

    public static class Interaction {
	public final int btn, modflags;
	public final Coord2d mc;
	public final ClickData click;

	public Interaction(int btn, int modflags, Coord2d mc, ClickData click) {
	    this.btn = btn;
	    this.modflags = modflags;
	    this.mc = mc;
	    this.click = click;
	}

	public Interaction(int btn, int modflags) {
	    this(btn, modflags, null, null);
	}

	public Interaction() {
	    this(1, 0);
	}
    }

    public static class PagButton implements ItemInfo.Owner, GSprite.Owner, RandomSource {
	public final Pagina pag;
	public final Resource res;
	public final KeyBinding bind;
	private GSprite spr;
	private AButton act;

	public PagButton(Pagina pag) {
	    this.pag = pag;
	    this.res = pag.res();
	    this.bind = binding();
	}

	public AButton act() {
	    if(act == null)
		act = res.flayer(Resource.action);
	    return(act);
	}

	private Pagina parent;
	public Pagina parent() {
	    if(parent == null)
		parent = pag.scm.paginafor(act().parent);
	    return(parent);
	}

	public GSprite spr() {
	    if(spr == null)
		spr = GSprite.create(this, res, Message.nil);
	    return(spr);
	}
	public String name() {return(act().name);}
	public KeyMatch hotkey() {
	    char hk = act().hk;
	    if(hk == 0)
		return(KeyMatch.nil);
	    return(KeyMatch.forchar(Character.toUpperCase(hk), KeyMatch.MODS & ~KeyMatch.S, 0));
	}
	public KeyBinding binding() {
	    return(KeyBinding.get("scm/" + res.name, hotkey()));
	}
	public void use(Interaction iact) {
            hearthborn.ui.GlobalCommands.recipe(pag);
            if(hearthborn.navigation.Walking.hearthAction(act().ad) || (res != null && res.name.equals("paginae/act/travel-hearth"))) {
                GameUI gui = pag.scm.getparent(GameUI.class);
                if(gui != null) hearthborn.navigation.Walking.abandon(gui.map);
            }
	    Object[] eact = new Object[] {iact.modflags};
	    if(iact.mc != null) {
		eact = Utils.extend(eact, iact.mc.floor(OCache.posres));
		if(iact.click != null)
		    eact = Utils.extend(eact, iact.click.clickargs());
	    }
	    if(pag.id instanceof Indir)
		pag.scm.wdgmsg("act", Utils.extend(Utils.extend(new Object[0], act().ad), eact));
	    else
		pag.scm.wdgmsg("use", Utils.extend(new Object[] {pag.id}, eact));
            hearthborn.ui.ActionBrowser.used(pag.scm,this);
	}
	public void tick(double dt) {
	    if(spr != null)
		spr.tick(dt);
	}

	public BufferedImage img() {
	    GSprite spr = spr();
	    if(spr instanceof GSprite.ImageSprite)
		return(((GSprite.ImageSprite)spr).image());
	    return(null);
	}

	public final AttrCache<Pipe.Op> rstate = new AttrCache<>(this::info, info -> {
		ArrayList<GItem.RStateInfo> ols = new ArrayList<>();
		for(ItemInfo inf : info) {
		    if(inf instanceof GItem.RStateInfo)
			ols.add((GItem.RStateInfo)inf);
		}
		if(ols.size() == 0)
		    return(() -> null);
		if(ols.size() == 1) {
		    Pipe.Op op = ols.get(0).rstate();
		    return(() -> op);
		}
		Pipe.Op[] ops = new Pipe.Op[ols.size()];
		for(int i = 0; i < ops.length; i++)
		    ops[i] = ols.get(0).rstate();
		Pipe.Op cmp = Pipe.Op.compose(ops);
		return(() -> cmp);
	});
	public final AttrCache<GItem.InfoOverlay<?>[]> ols = new AttrCache<>(this::info, info -> {
		ArrayList<GItem.InfoOverlay<?>> buf = new ArrayList<>();
		for(ItemInfo inf : info) {
		    if(inf instanceof GItem.OverlayInfo)
			buf.add(GItem.InfoOverlay.create((GItem.OverlayInfo<?>)inf));
		}
		GItem.InfoOverlay<?>[] ret = buf.toArray(new GItem.InfoOverlay<?>[0]);
		return(() -> ret);
	});
	public final AttrCache<Double> meter = new AttrCache<>(this::info, AttrCache.map1(GItem.MeterInfo.class, minf -> minf::meter));
	private final hearthborn.ui.MeterPercent progress = new hearthborn.ui.MeterPercent();

	public void drawmain(GOut g, GSprite spr) {
	    spr.draw(g);
	}
	public void draw(GOut g, GSprite spr) {
	    if(rstate.get() != null)
		g.usestate(rstate.get());
	    drawmain(g, spr);
	    g.defstate();
	    GItem.InfoOverlay<?>[] ols = this.ols.get();
	    if(ols != null) {
		for(GItem.InfoOverlay<?> ol : ols)
		    ol.draw(g);
	    }
	    Double meter = this.meter.get();
	    progress.draw(g, spr.sz().div(2), meter == null ? 0 : meter);
	}

	public String sortkey() {
	    if((act().ad.length == 0) && (pag.id instanceof Indir))
		return("\0" + name());
	    return(name());
	}

	private char bindchr(KeyMatch key) {
	    if(key.modmatch != 0)
		return(0);
	    if(key instanceof KeyMatch.CharMatch)
		return(((KeyMatch.CharMatch)key).chr);
	    if(key.keyname.length() == 1)
		return(key.keyname.charAt(0));
	    return(0);
	}

	public static final Text.Foundry keyfnd = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 10);
	private Tex keyrend = null;
	private boolean haskeyrend = false;
	public Tex keyrend() {
	    if(!haskeyrend) {
		char vkey = bindchr(bind.key());
		if(vkey != 0)
		    keyrend = new TexI(Utils.outline2(keyfnd.render(Character.toString(vkey), Color.WHITE).img, Color.BLACK));
		else
		    keyrend = null;
		haskeyrend = true;
	    }
	    return(keyrend);
	}

	private List<ItemInfo> info = null;
	public List<ItemInfo> info() {
	    if(info == null) {
		info = hearthborn.l10n.TooltipText.render(() -> ItemInfo.buildinfo(this, pag.rawinfo));
		Resource.Pagina pg = res.layer(Resource.pagina);
		if(pg != null)
		    info.add(new ItemInfo.Pagina(this, pg.text));
	    }
	    return(info);
	}
	private static final OwnerContext.ClassResolver<PagButton> ctxr = new OwnerContext.ClassResolver<PagButton>()
	    .add(PagButton.class, p -> p)
	    .add(MenuGrid.class, p -> p.pag.scm)
	    .add(Glob.class, p -> p.pag.scm.ui.sess.glob)
	    .add(Session.class, p -> p.pag.scm.ui.sess);
	public <T> T context(Class<T> cl) {return(ctxr.context(cl, this));}
	public Random mkrandoom() {return(new Random());}
	public Resource getres() {return(res);}

	public BufferedImage rendertt(boolean withpg) {
	    String english = name();
	    String shown = hearthborn.L10n.resource("action", res.name, english);
	    String tt = shown;
	    KeyMatch key = bind.key();
	    int pos = -1;
	    char vkey = bindchr(key);
	    if((vkey != 0) && (key.modmatch == 0))
		pos = tt.toUpperCase().indexOf(Character.toUpperCase(vkey));
	    if(pos >= 0)
		tt = tt.substring(0, pos) + "$b{$col[255,128,0]{" + tt.charAt(pos) + "}}" + tt.substring(pos + 1);
	    else if(key != KeyMatch.nil)
		tt += " [$b{$col[255,128,0]{" + key.name() + "}}]";
	    BufferedImage ret = ttfnd.render(tt, UI.scale(300)).img;
	    if(!shown.equals(english))
		ret = ItemInfo.catimgs(0, ret, hearthborn.l10n.TooltipText.literal(()->Text.std.render(english, new Color(160, 160, 160))).img);
	    if(withpg) {
		List<ItemInfo> info = info();
		info.removeIf(el -> el instanceof ItemInfo.Name);
		if(!info.isEmpty())
		    ret = ItemInfo.catimgs(0, ret, hearthborn.l10n.TooltipText.render(() -> ItemInfo.longtip(info)));
	    }
	    return(ret);
	}

	public static class FactMaker extends Resource.PublishedCode.Instancer.Chain<Factory> {
	    public FactMaker() {
		super(Factory.class);
		add(new Direct<>(Factory.class));
		add(new StaticCall<>(Factory.class, "mkpagina", PagButton.class, new Class<?>[] {Pagina.class},
				     (make) -> (pagina) -> make.apply(new Object[] {pagina})));
		add(new Construct<>(Factory.class, PagButton.class, new Class<?>[] {Pagina.class},
				    (cons) -> (pagina) -> cons.apply(new Object[] {pagina})));
	    }
	}

	@Resource.PublishedCode(name = "pagina", instancer = FactMaker.class)
	public interface Factory {
	    public PagButton make(Pagina info);
	}
    }

    public final PagButton next = new PagButton(new Pagina(this, null, Resource.local().loadwait("gfx/hud/sc-next").indir())) {
	    {pag.button = this;}

	    public void use(Interaction iact) {
		Coord gsz=gridSize();
		int step = (gsz.x * gsz.y) - 2;
		if((curoff + step) >= curbtns.size())
		    curoff = 0;
		else
		    curoff += step;
		updlayout();
	    }

	    public String name() {return("More...");}

	    public KeyBinding binding() {return(kb_next);}
	};

    public final PagButton bk = new PagButton(new Pagina(this, null, Resource.local().loadwait("gfx/hud/sc-back").indir())) {
	    {pag.button = this;}

	    public void use(Interaction iact) {
		if(iact.btn == 3)
		    pag.scm.change(null);
		else if(pag.scm.cur != null)
		    pag.scm.change(pag.scm.cur.parent());
		curoff = 0;
	    }

	    public String name() {return("Back");}

	    public KeyBinding binding() {return(kb_back);}

	    public BufferedImage rendertt(boolean withpg) {
		return ttfnd.render(hearthborn.L10n.tr("Left click: Back\nRight click: Home"), UI.scale(300)).img;
	    }
	};

    public Pagina paginafor(Indir<Resource> res) {
	if(res == null)
	    return(null);
	synchronized(pmap) {
	    Pagina p = pmap.get(res);
	    if(p == null)
		pmap.put(res, p = new Pagina(this, res, res));
	    return(p);
	}
    }

    public Pagina paginafor(Object id, Indir<Resource> res) {
	synchronized(pmap) {
	    Pagina p = pmap.get(id);
	    if((p == null) && (res != null))
		pmap.put(id, p = new Pagina(this, id, res));
	    return(p);
	}
    }

    /** Refresh the native discovery marks without navigating either menu. */
    public void refreshNewPages() {
	synchronized(pmap) {
	    for(Pagina pag : pmap.values())
		pag.tnew = 0;
	}
	synchronized(paginae) {
	    for(Pagina pag : paginae) {
		if(pag.anew > 0) {
		    try {
			for(Pagina npag = pag; npag != null; npag = npag.parent())
			    npag.tnew = Math.max(npag.tnew, pag.anew);
		    } catch(Loading l) {
		    }
		}
	    }
	}
    }

    private boolean cons(Pagina p, Collection<PagButton> buf) {
        refreshNewPages();
	Collection<Pagina> open, close = new HashSet<Pagina>();
        synchronized(paginae) {open = new LinkedList<Pagina>(paginae);}
	boolean ret = true;
	while(!open.isEmpty()) {
	    Iterator<Pagina> iter = open.iterator();
	    Pagina pag = iter.next();
	    iter.remove();
	    try {
		Pagina parent = pag.parent();
		if(parent == p)
		    buf.add(pag.button());
		else if((parent != null) && !close.contains(parent) && !open.contains(parent))
		    open.add(parent);
		close.add(pag);
	    } catch(Loading e) {
		ret = false;
	    }
	}
	return(ret);
    }

    private void announce(Pagina pag) {
	ui.loader.defer(() -> ui.msg("New discovery: " + hearthborn.L10n.resource("action",pag.res().name,pag.button().name()), Color.WHITE, null), null);
    }

    public MenuGrid() {
	super(hearthborn.ui.ActionMenuFrame.scaledSize(bgsz.mul(gsz).add(1, 1)));
	hearthborn.ui.XtensionsMenu.install(this);
        shortcuts = new hearthborn.ui.ActionShortcuts(this);
    }

    private void updlayout() {
        Coord target=hearthborn.ui.XtensionsMenu.gridSize(cur);
        if(!target.equals(gridSize())){
            currentGrid=target;
            curoff=0;
            layout=new PagButton[target.x][target.y];
            hearthborn.ui.ActionMenuFrame frame=parent==null?null:parent.getchild(hearthborn.ui.ActionMenuFrame.class);
            if(frame!=null)frame.fit();
            else resize(hearthborn.ui.ActionMenuFrame.size(contentSize(),displayScale()));
        }
        Coord gsz=gridSize();
	if(shortcuts != null && shortcuts.handles(this.cur)) {
            List<PagButton> buttons = shortcuts.buttons(this.cur);
            curbtns = new ArrayList<>();
            for(int y = 0; y < gsz.y; y++) for(int x = 0; x < gsz.x; x++) {
                int index = x + y * gsz.x;
                PagButton button = index == gsz.x * gsz.y - 1 ? bk : index < buttons.size() ? buttons.get(index) : null;
                layout[x][y] = button;
                if(button != null) curbtns.add(button);
            }
            recons = shortcuts.loading();
            fstart = Utils.rtime();
            return;
        }
	synchronized(paginae) {
	    List<PagButton> cur = new ArrayList<>();
            if(hearthborn.ui.XtensionsMenu.expanded(this.cur)){
                cur.addAll(hearthborn.ui.XtensionsMenu.allCommands(this));recons=false;
            }else{
	        recons = !cons(this.cur, cur);
	        Collections.sort(cur, Comparator.comparing(PagButton::sortkey));
            }
	    this.curbtns = cur;
	    int i = curoff;
	    for(int y = 0; y < gsz.y; y++) {
		for(int x = 0; x < gsz.x; x++) {
		    PagButton btn = null;
		    if((this.cur != null) && (x == gsz.x - 1) && (y == gsz.y - 1)) {
			btn = bk;
		    } else if((cur.size() > ((gsz.x * gsz.y) - 1)) && (x == gsz.x - 2) && (y == gsz.y - 1)) {
			btn = next;
		    } else if(i < cur.size()) {
			btn = cur.get(i++);
		    }
		    layout[x][y] = btn;
		}
	    }
	    fstart = Utils.rtime();
	}
    }

    public void draw(GOut g) {
        Coord gsz=gridSize();
        g = hearthborn.ui.ActionMenuFrame.graphics(g,bgsz.mul(gsz).add(1,1),displayScale());
	double now = Utils.rtime();
	for(int y = 0; y < gsz.y; y++) {
	    for(int x = 0; x < gsz.x; x++) {
		Coord p = bgsz.mul(new Coord(x, y));
		hearthborn.ui.ActionMenuFrame.slot(g,p,bgsz.add(1,1));
		PagButton btn = layout[x][y];
		if(btn != null) {
		    GSprite spr;
		    try {
			spr = btn.spr();
		    } catch(Loading l) {
			continue;
		    }
		    GOut g2 = g.reclip(p.add(1, 1), spr.sz());
                    hearthborn.ui.ActionDiscovery.draw(g2,btn,spr,now-fstart,x+y*gsz.x);
		    if(showkeys) {
			Tex ki = btn.keyrend();
			if(ki != null)
			    g2.aimage(ki, Coord.of(bgsz.x - UI.scale(2), UI.scale(1)), 1.0, 0.0);
		    }
		    if(hearthborn.ui.ActionPress.same(pressed, btn)) {
			g.chcolor(new Color(0, 0, 0, 128));
			g.frect(p.add(1, 1), bgsz.sub(1, 1));
			g.chcolor();
		    }
		}
	    }
	}
	super.draw(g);
	if(dragging != null) {
	    GSprite ds = dragging.button().spr();
	    ui.drawafter(new UI.AfterDraw() {
		    public void draw(GOut g) {
			ds.draw(g.reclip(ui.mc.sub(ds.sz().div(2)), ds.sz()));
		    }
		});
	}
    }

    private static final double FULL_TOOLTIP_DELAY = 1.0;
    private PagButton hovered, tooltipButton;
    private boolean tooltipExpanded;
    private Tex tooltipTexture;
    private double hoverstart;

    public Object tooltip(Coord c, Widget prev) {
	PagButton button = bhit(c);
	double now = Utils.rtime();
	if(prev != this || button != hovered) {
	    hovered = button;
	    hoverstart = now;
	}
	if(button == null)
	    return null;
	boolean expanded = now - hoverstart >= FULL_TOOLTIP_DELAY;
	if(button != tooltipButton || expanded != tooltipExpanded) {
	    BufferedImage tip = button.rendertt(expanded);
	    if(tooltipTexture != null)
		tooltipTexture.dispose();
	    tooltipTexture = tip == null ? null : new TexI(tip);
	    tooltipButton = button;
	    tooltipExpanded = expanded;
	}
	return tooltipTexture;
    }

    private PagButton bhit(Coord c) {
        Coord gsz=gridSize();
        c = hearthborn.ui.ActionMenuFrame.logical(c,displayScale());
        if(c.x < 0 || c.y < 0)return null;
	Coord bc = c.div(bgsz);
	if((bc.x >= 0) && (bc.y >= 0) && (bc.x < gsz.x) && (bc.y < gsz.y))
	    return(layout[bc.x][bc.y]);
	else
	    return(null);
    }

    public boolean mousedown(MouseDownEvent ev) {
	PagButton h = bhit(ev.c);
        if(ev.b==3 && h!=null && h.res.name.equals("hearthborn/claim-plan")){
            boolean close=hearthborn.ui.XtensionsMenu.expanded(cur);
            h.use(new Interaction(3,ui.modflags()));if(close)change(null);return true;
        }
        if(ev.b==3 && h==bk){
            h.use(new Interaction(3,ui.modflags()));return true;
        }
        if(ev.b==3 && h!=null && shortcuts!=null && shortcuts.favoriteMenu(h,this,ev.c))return true;
        if(ev.b==3 && h==null && shortcuts!=null && shortcuts.handles(cur)){change(null);return true;}
	if((ev.b == 1) && (h != null)) {
	    pressed = h;
	    grab = ui.grabmouse(this);
	}
	return(true);
    }

    public void mousemove(MouseMoveEvent ev) {
	if((dragging == null) && (pressed != null)) {
	    PagButton h = bhit(ev.c);
	    if(!hearthborn.ui.ActionPress.same(pressed, h))
		dragging = pressed.pag;
	}
    }

    public void change(Pagina dst) {
	hearthborn.ui.HandMenu.prepare(this,dst);
	this.cur = dst;
	curoff = 0;
	if(dst == null)
	    showkeys = false;
	updlayout();
    }

    public void use(PagButton r, Interaction iact, boolean reset) {
        hearthborn.ui.HandMenu.prepare(this,r.pag);
	if(shortcuts != null && shortcuts.handles(r.pag)) {change(r.pag);return;}
	boolean close=hearthborn.ui.XtensionsMenu.expanded(cur)&&r!=next&&r!=bk;
	Collection<PagButton> sub = new ArrayList<>();
	cons(r.pag, sub);
	if(sub.size() > 0) {
	    change(r.pag);
	} else {
	    r.pag.anew = r.pag.tnew = 0;
	    r.use(iact);
            hearthborn.ui.ActionBrowser.used(this,r);
	    if(reset||close)
		change(null);
	}
    }

    public void tick(double dt) {
        int before=pagseq;hearthborn.ui.HandMenu.prepare(this,cur);if(before!=pagseq)updlayout();
	if(recons || shortcuts != null && shortcuts.changed() || !hearthborn.ui.XtensionsMenu.gridSize(cur).equals(gridSize()))
	    updlayout();
        Coord gsz=gridSize();
	for(int y = 0; y < gsz.y; y++) {
	    for(int x = 0; x < gsz.x; x++) {
		if(layout[x][y] != null)
		    layout[x][y].tick(dt);
	    }
	}
    }

    public boolean hasChildren(Pagina page) {
        Collection<Pagina> all;
        synchronized(paginae) {all = new ArrayList<>(paginae);}
        for(Pagina pag : all) {
            try {
                if(pag.parent() == page) {
                    pag.button();
                    return true;
                }
            } catch(Loading e) {
            }
        }
        return false;
    }

    /** Every page that has at least one action under it, found in one pass. */
    public Set<Pagina> parents() {
        Collection<Pagina> all;
        synchronized(paginae) {all = new ArrayList<>(paginae);}
        Set<Pagina> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Pagina pag : all) {
            try {
                Pagina parent = pag.parent();
                if(parent != null) {
                    pag.button();
                    result.add(parent);
                }
            } catch(Loading e) {
            }
        }
        return result;
    }

    public boolean mouseup(MouseUpEvent ev) {
	PagButton h = bhit(ev.c);
	if((ev.b == 1) && (grab != null)) {
	    if(dragging != null) {
		DropTarget.dropthing(ui.root, ui.mc, dragging);
		pressed = null;
		dragging = null;
	    } else if(pressed != null) {
		if(hearthborn.ui.ActionPress.same(pressed, h))
		    use(h, new Interaction(ev.b,ui.modflags()), false);
		pressed = null;
	    }
	    grab.remove();
	    grab = null;
	}
	return(true);
    }
    private double displayScale(){
	hearthborn.ui.ActionMenuFrame frame=parent==null?null:parent.getchild(hearthborn.ui.ActionMenuFrame.class);
	return frame==null?hearthborn.ui.ActionMenuFrame.SCALE:frame.factor();
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "goto") {
	    if(args[0] == null)
		change(null);
	    else
		change(paginafor(ui.sess.getresv(args[0])));
	} else if(msg == "fill") {
	    synchronized(paginae) {
		int a = 0;
		while(a < args.length) {
		    int fl = Utils.iv(args[a++]);
		    Pagina pag;
		    Object id;
		    if((fl & 2) != 0)
			pag = paginafor(id = args[a++], null);
		    else
			id = (pag = paginafor(ui.sess.getres(Utils.iv(args[a++]), -2))).res;
		    if((fl & 1) != 0) {
			if((fl & 2) != 0) {
			    Indir<Resource> res = ui.sess.getres(Utils.iv(args[a++]), -2);
			    if(pag == null) {
				pag = paginafor(id, res);
			    } else if(pag.res != res) {
				pag.res = res;
				pag.invalidate();
			    }
			}
			byte[] data = ((fl & 4) != 0) ? (byte[])args[a++] : null;
			if(!Arrays.equals(pag.sdt, data)) {
			    pag.sdt = data;
			    pag.invalidate();
			}
			if((fl & 8) != 0) {
			    pag.anew = 2;
			    announce(pag);
			}
			Object[] rawinfo = ((fl & 16) != 0) ? (Object[])args[a++] : new Object[0];
			if(!Arrays.deepEquals(pag.rawinfo, rawinfo)) {
			    pag.rawinfo = rawinfo;
			    pag.invalidate();
			}
			paginae.add(pag);
		    } else {
			paginae.remove(pag);
		    }
		}
		pagseq++;
		updlayout();
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final KeyBinding kb_root = KeyBinding.get("scm-root", KeyMatch.forcode(KeyEvent.VK_ESCAPE, 0));
    public static final KeyBinding kb_back = KeyBinding.get("scm-back", KeyMatch.forcode(KeyEvent.VK_BACK_SPACE, 0));
    public static final KeyBinding kb_next = KeyBinding.get("scm-next", KeyMatch.forchar('N', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S));
    public boolean globtype(GlobKeyEvent ev) {
        Coord gsz=gridSize();
	if(kb_root.key().match(ev) && (this.cur != null)) {
	    change(null);
	    return(true);
	} else if(kb_back.key().match(ev) && (this.cur != null)) {
	    use(bk, new Interaction(), false);
	    return(true);
	} else if(kb_next.key().match(ev) && (layout[gsz.x - 2][gsz.y - 1] == next)) {
	    use(next, new Interaction(), false);
	    return(true);
	}
	int cp = -1;
	PagButton pag = null;
	for(PagButton btn : curbtns) {
	    if(btn.bind.key().match(ev)) {
		int prio = btn.bind.set() ? 1 : 0;
		if((pag == null) || (prio > cp)) {
		    pag = btn;
		    cp = prio;
		}
	    }
	}
	if(pag != null) {
	    use(pag, new Interaction(1,ui.modflags()), (ev.mods & KeyMatch.S) == 0);
	    if(this.cur != null)
		showkeys = true;
	    return(true);
	}
	return(super.globtype(ev));
    }

    public KeyBinding getbinding(Coord cc) {
	PagButton h = bhit(cc);
	return((h == null) ? null : h.bind);
    }
}
