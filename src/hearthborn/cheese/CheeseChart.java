/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.cheese;
import hearthborn.L10n;
import hearthborn.ui.SmallWindowDeco;
import hearthborn.ui.UiTheme;

import haven.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * @author Jagy2014
 */
public class CheeseChart extends Window {
    private static final int GAP = UI.scale(4);
    private static final int ROW_GAP = UI.scale(18);
    private static final int ICO = UI.scale(22);
    private static final int LOC_ICO = UI.scale(30);
    private static final int FEV = UI.scale(12);
    private static final int HEAD_H = UI.scale(28);
    private static final int STAGE_H = UI.scale(22);
    private static final Coord MAX_SZ = UI.scale(960, 620);
    private static final int NAME_MAX = UI.scale(128);
    private static final Color PLUS2 = new Color(230, 190, 70);
    private static final Color LINE = Color.WHITE;
    private final GameUI gui;
    private CheeseAging.Milk milk = CheeseAging.Milk.COW;
    private final Pane pane;
    private final TreeBoard tree;
    private final Button btnCow, btnSheep, btnGoat;
    private final Foot hint;
    private final Map<String, Tex> icache = new HashMap<>();
    private final Set<String> imiss = new HashSet<>();
    private boolean laying;

    public static void toggle(GameUI gui) {
        if(gui.cheeseChart!=null && gui.cheeseChart.parent!=null) {gui.cheeseChart.reqdestroy();gui.cheeseChart=null;return;}
        gui.cheeseChart=gui.add(new CheeseChart(gui),UI.scale(80,80));
    }

    public CheeseChart(GameUI gui) {
	super(Coord.z, "Cheese");
	reqclose(this::close);
	this.gui = gui;
	btnCow = add(new Button(UI.scale(90), L10n.tr("Cow"), false), Coord.z);
	btnSheep = add(new Button(UI.scale(90), L10n.tr("Sheep"), false), UI.scale(95, 0));
	btnGoat = add(new Button(UI.scale(90), L10n.tr("Goat"), false), UI.scale(190, 0));
	pane = add(new Pane(UI.scale(420, 280)), new Coord(0, HEAD_H));
	tree = pane.view.add(new TreeBoard(), Coord.z);
	hint = add(new Foot(goudaHint()), pane.pos("bl").adds(0, 6));
	show(CheeseAging.Milk.COW);
    }

    @Override
    protected Deco makedeco() {
	return(new SmallWindowDeco(this.large).backing(UiTheme.DARK_TOOLTIP_BACKGROUND).dragsize(true));
    }

    protected Coord minContentSize() {
	return(UI.scale(300, 200));
    }

    @Override
    public void resize(Coord sz) {
	super.resize(sz);
	layoutInner();
    }

    @Override
    protected void added() {
        super.added();
        packToTree();
    }

    protected void setCfg() {}

    private void layoutInner() {
	if(laying || (pane == null) || (hint == null))
	    return;
	laying = true;
	try {
	    Coord inner = csz();
	    if((inner.x <= 0) || (inner.y <= 0))
		return;
	    int pw = Math.max(UI.scale(200), inner.x);
	    hint.wrap(pw);
	    int foot = hint.sz.y + UI.scale(8);
	    int ph = Math.max(UI.scale(120), inner.y - HEAD_H - foot);
	    pane.move(new Coord(0, HEAD_H));
	    pane.resize(new Coord(pw, ph));
	    pane.layoutBars();
	    pane.updateScroll();
	    hint.move(pane.pos("bl").adds(0, 6));
	} finally {
	    laying = false;
	}
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == btnCow) {
	    show(CheeseAging.Milk.COW);
	} else if(sender == btnSheep) {
	    show(CheeseAging.Milk.SHEEP);
	} else if(sender == btnGoat) {
	    show(CheeseAging.Milk.GOAT);
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }

    public void close() {
	if(gui != null)
	    gui.cheeseChart = null;
	reqdestroy();
    }

    private void show(CheeseAging.Milk milk) {
	this.milk = milk;
	tree.set(milk);
	pane.resetScroll();
	packToTree();
    }

    private Coord maxInner() {
	Coord cap = MAX_SZ;
	if((parent != null) && (parent.sz.x > 0) && (parent.sz.y > 0)) {
	    Coord chrome = sz.sub(csz());
	    if((chrome.x < 0) || (chrome.y < 0))
		chrome = Coord.z;
	    cap = parent.sz.sub(chrome).sub(UI.scale(24, 48));
	}
	return cap.max(minContentSize());
    }

    private void packToTree() {
	if((tree == null) || (hint == null) || laying)
	    return;
	Coord cap = maxInner();
	Coord tsz = tree.sz.max(UI.scale(280, 160));
	hint.wrap(Math.min(tsz.x, cap.x));
	int foot = hint.sz.y + UI.scale(8);
	Coord inner = new Coord(
	    Math.min(Math.max(tsz.x, UI.scale(280)), cap.x),
	    Math.min(tsz.y + HEAD_H + foot, cap.y));
	inner = inner.max(minContentSize());
	if(!Utils.eq(csz(), inner))
	    resize(inner);
	else
	    layoutInner();
        if(parent != null) move(Coord.of(Math.max(0, Math.min(c.x, parent.sz.x - sz.x)), Math.max(0, Math.min(c.y, parent.sz.y - sz.y))));
    }

    private static String curdKey(CheeseAging.Milk milk) {
	if(milk == CheeseAging.Milk.SHEEP)
	    return "Sheep's Curd";
	if(milk == CheeseAging.Milk.GOAT)
	    return "Goat's Curd";
	return "Curd";
    }

    private static String curdTitle(CheeseAging.Milk milk) {
	if(milk == CheeseAging.Milk.SHEEP)
	    return L10n.tr("Sheep's Curd");
	if(milk == CheeseAging.Milk.GOAT)
	    return L10n.tr("Goat's Curd");
	return L10n.tr("Cow's Curd");
    }

    private static String stripCheeseWord(String name) {
	if((name != null) && name.endsWith(" Cheese"))
	    return name.substring(0, name.length() - 7);
	return name;
    }

    private Tex txt(String s) {
	if(s == null)
	    s = "";
	String key = "t:" + s;
	Tex t = icache.get(key);
	if(t == null) {
	    t = Text.std.render(s).tex();
	    icache.put(key, t);
	}
	return t;
    }

    private Tex wrap(String s, int w) {
	if(s == null)
	    s = "";
	String key = "w:" + w + ":" + s;
	Tex t = icache.get(key);
	if(t == null) {
	    t = Text.std.renderwrap(s, w).tex();
	    icache.put(key, t);
	}
	return t;
    }

    private Tex icon(String[] paths, Coord sz) {
	if(paths == null)
	    return null;
	for(String path : paths) {
	    Tex t = icon(path, sz);
	    if(t != null)
		return t;
	}
	return null;
    }

    private Tex icon(String res, Coord sz) {
	if((res == null) || imiss.contains(res))
	    return null;
	String key = res + "@" + sz.x;
	Tex t = icache.get(key);
	if(t != null)
	    return t;
	try {
	    Resource r = null;
	    try {
		r = Resource.local().load(res).get();
	    } catch(Loading e) {
		return null;
	    } catch(Exception ignored) {}
	    if(r == null) {
		try {
		    r = Resource.remote().load(res).get();
		} catch(Loading e) {
		    return null;
		}
	    }
	    Resource.Image im = r.layer(Resource.imgc);
	    if(im == null) {
		Resource.Anim an = r.layer(Resource.animc);
		if((an != null) && (an.f.length > 0) && (an.f[0].length > 0))
		    im = an.f[0][0];
	    }
	    if(im == null) {
		imiss.add(res);
		return null;
	    }
	    BufferedImage img = PUtils.convolvedown(im.img, sz, CharWnd.iconfilter);
	    t = new TexI(img);
	    icache.put(key, t);
	    return t;
	} catch(Loading e) {
	    return null;
	} catch(Exception e) {
	    imiss.add(res);
	    return null;
	}
    }

    private Tex locTex(CheeseAging.Loc loc) {
	return iconFit(CheeseAging.locIcons(loc), Coord.of(LOC_ICO, LOC_ICO));
    }

    private Tex iconFit(String[] paths, Coord sz) {
	if(paths == null)
	    return null;
	for(String path : paths) {
	    Tex t = iconFit(path, sz);
	    if(t != null)
		return t;
	}
	return null;
    }

    private Tex iconFit(String res, Coord sz) {
	if((res == null) || imiss.contains(res))
	    return null;
	String key = "fit:" + res + "@" + sz.x;
	Tex t = icache.get(key);
	if(t != null)
	    return t;
	try {
	    Resource r = null;
	    try {
		r = Resource.local().load(res).get();
	    } catch(Loading e) {
		return null;
	    } catch(Exception ignored) {}
	    if(r == null) {
		try {
		    r = Resource.remote().load(res).get();
		} catch(Loading e) {
		    return null;
		}
	    }
	    Resource.Image im = r.layer(Resource.imgc);
	    if(im == null) {
		Resource.Anim an = r.layer(Resource.animc);
		if((an != null) && (an.f.length > 0) && (an.f[0].length > 0))
		    im = an.f[0][0];
	    }
	    if(im == null) {
		imiss.add(res);
		return null;
	    }
	    t = new TexI(locationImage(im.img,sz));
	    icache.put(key, t);
	    return t;
	} catch(Loading e) {
	    return null;
	} catch(Exception e) {
	    imiss.add(res);
	    return null;
	}
    }

    static BufferedImage locationImage(BufferedImage source,Coord size){
        BufferedImage image=cropOpaque(source);int width=image.getWidth(),height=image.getHeight();
        if(width>height*5/4||height>width*5/4){int side=Math.min(width,height);image=image.getSubimage((width-side)/2,(height-side)/2,side,side);}
        return PUtils.convolvedown(image,size,CharWnd.iconfilter);
    }

    private static BufferedImage cropOpaque(BufferedImage img) {
	int w = img.getWidth(), h = img.getHeight();
	int x0 = w, y0 = h, x1 = 0, y1 = 0;
	boolean any = false;
	for(int y = 0; y < h; y++) {
	    for(int x = 0; x < w; x++) {
		if(((img.getRGB(x, y) >>> 24) & 0xff) > 16) {
		    any = true;
		    if(x < x0) x0 = x;
		    if(y < y0) y0 = y;
		    if(x > x1) x1 = x;
		    if(y > y1) y1 = y;
		}
	    }
	}
	if(!any)
	    return img;
	return img.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    private String goudaHint() {
	StringBuilder b = new StringBuilder();
	b.append(L10n.tr("Wrong place or sequence becomes Generic Gouda"));
	for(CheeseAging.Fep f : CheeseAging.feps(CheeseAging.GOUDA))
	    b.append("  ").append(fepLine(f));
	b.append(". ").append(L10n.tr("Cheese stays until you restack the tray."));
	return b.toString();
    }

    private String hoursPhrase(int h) {
        if(!"ru".equals(L10n.language()))return h+(h==1?" hour":" hours");
        int n=h%100,d=n%10;
        return L10n.format(n>=11&&n<=14?"%d hours":d==1?"%d hour":d>=2&&d<=4?"%d hours (few)":"%d hours",h);
    }

    private String locNames(Out o) {
	StringBuilder b = new StringBuilder();
	for(int i = 0; i < o.locs.size(); i++) {
	    if(i > 0)
		b.append(" ").append(L10n.tr("or")).append(" ");
	    b.append(CheeseAging.locLabel(o.locs.get(i)));
	}
	return b.toString();
    }

    private String fepLine(CheeseAging.Fep f) {
	String a = f.base().toUpperCase(Locale.ROOT);
	if(f.plus2())
	    a += "+2";
	return a + " - " + f.a;
    }

    private static class Out {
	final List<CheeseAging.Loc> locs;
	final int hours;
	final String name;

	Out(List<CheeseAging.Loc> locs, int hours, String name) {
	    this.locs = locs;
	    this.hours = hours;
	    this.name = name;
	}
    }

    private class Node {
	final Out out;
	final List<Node> kids = new ArrayList<>();

	Node(Out out) {
	    this.out = out;
	    for(Out c : outcomes(out.name))
		kids.add(new Node(c));
	}
    }

    private List<Node> trees() {
	return Collections.singletonList(new Node(new Out(Collections.emptyList(), 0, curdKey(milk))));
    }

    private List<Out> outcomes(String from) {
	LinkedHashMap<String, Out> by = new LinkedHashMap<>();
	for(CheeseAging.Step s : CheeseAging.steps(milk)) {
	    if(!s.from.equals(from))
		continue;
	    Out old = by.get(s.to);
	    if(old == null) {
		by.put(s.to, new Out(new ArrayList<>(Collections.singletonList(s.loc)), s.hours, s.to));
	    } else if(!old.locs.contains(s.loc)) {
		old.locs.add(s.loc);
	    }
	}
	return new ArrayList<>(by.values());
    }

    private class View extends Widget {
	int sx, sy;

	View(Coord sz) {
	    super(sz);
	}

	public Coord xlate(Coord c, boolean in) {
	    if(in)
		return(c.add(-sx, -sy));
	    else
		return(c.add(sx, sy));
	}

	public void draw(GOut g) {
	    Widget next;
	    for(Widget wdg = child; wdg != null; wdg = next) {
		next = wdg.next;
		if(!wdg.visible)
		    continue;
		Coord cc = xlate(wdg.c, true);
		if((cc.x + wdg.sz.x < 0) || (cc.y + wdg.sz.y < 0) || (cc.x > sz.x) || (cc.y > sz.y))
		    continue;
		wdg.draw(g.reclip(cc, wdg.sz));
	    }
	}
    }

    private class HBar extends Widget {
	int min = 0, max = 0, val = 0;
	private UI.Grab drag;

	HBar(int w) {
	    super(new Coord(w, UI.scale(12)));
	}

	boolean vis() {
	    return(max > min);
	}

	public void resizew(int w) {
	    resize(new Coord(Math.max(1, w), sz.y));
	}

	void ch(int a) {
	    int n = Utils.clip(val + a, min, max);
	    if(n != val) {
		val = n;
		changed();
	    }
	}

	void changed() {
	    pane.view.sx = val;
	}

	public void draw(GOut g) {
	    if(!vis())
		return;
	    g.chcolor(new Color(40, 40, 40));
	    g.frect(Coord.z, sz);
	    g.chcolor(new Color(90, 90, 90));
	    g.rect(Coord.z, sz);
	    int range = Math.max(1, max - min);
	    int tw = Math.max(UI.scale(24), (int)((long)sz.x * sz.x / (sz.x + range)));
	    int tx = (int)((long)(val - min) * (sz.x - tw) / range);
	    g.chcolor(new Color(210, 210, 210));
	    g.frect(new Coord(tx, UI.scale(2)), new Coord(tw, sz.y - UI.scale(4)));
	    g.chcolor();
	}

	private void update(Coord c) {
	    if(!vis())
		return;
	    int range = Math.max(1, max - min);
	    int tw = Math.max(UI.scale(24), (int)((long)sz.x * sz.x / (sz.x + range)));
	    double a = (double)(c.x - (tw / 2)) / (double)Math.max(1, sz.x - tw);
	    int n = min + (int)Math.round(Utils.clip(a, 0, 1) * range);
	    if(n != val) {
		val = n;
		changed();
	    }
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if((ev.b != 1) || !vis())
		return(super.mousedown(ev));
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
	    if((ev.b != 1) || (drag == null))
		return(super.mouseup(ev));
	    drag.remove();
	    drag = null;
	    return(true);
	}
    }

    private class Foot extends Widget {
	final String src;
	Text tex;

	Foot(String src) {
	    super(Coord.z);
	    this.src = src;
	    wrap(UI.scale(400));
	}

	void wrap(int w) {
	    w = Math.max(UI.scale(120), w);
	    if(tex != null)
		tex.dispose();
	    tex = Text.std.renderwrap(src, w);
	    resize(tex.sz());
	}

	public void draw(GOut g) {
	    if(tex != null)
		g.image(tex.tex(), Coord.z);
	}
    }

    private class Pane extends Widget {
	final View view;
	final Scrollbar vsb;
	final HBar hsb;
	private UI.Grab pan;
	private Coord pan0, panS;

	Pane(Coord sz) {
	    super(sz);
	    vsb = add(new Scrollbar(Math.max(1, sz.y - UI.scale(12)), 0, 0) {
		public void changed() {
		    view.sy = val;
		}
	    });
	    hsb = add(new HBar(Math.max(1, sz.x - Scrollbar.width)));
	    view = add(new View(sz.sub(Scrollbar.width, hsb.sz.y)), Coord.z);
	    layoutBars();
	}

	void layoutBars() {
	    Coord need = (tree == null) ? Coord.z : tree.sz;
	    boolean needv = need.y > sz.y;
	    boolean needh = need.x > sz.x;
	    if(needv && (need.x > sz.x - Scrollbar.width))
		needh = true;
	    if(needh && (need.y > sz.y - hsb.sz.y))
		needv = true;
	    int vb = needv ? Scrollbar.width : 0;
	    int hb = needh ? hsb.sz.y : 0;
	    vsb.show(needv);
	    hsb.show(needh);
	    vsb.move(new Coord(sz.x, 0));
	    vsb.resize(Math.max(1, sz.y - hb));
	    hsb.c = new Coord(0, sz.y - Math.max(1, hb));
	    hsb.resizew(Math.max(1, sz.x - vb));
	    view.resize(new Coord(Math.max(1, sz.x - vb), Math.max(1, sz.y - hb)));
	}

	void resetScroll() {
	    vsb.val = 0;
	    hsb.val = 0;
	    view.sx = 0;
	    view.sy = 0;
	    updateScroll();
	}

	void updateScroll() {
	    Coord need = (tree == null) ? Coord.z : tree.sz;
	    Coord vis = view.sz;
	    vsb.max = Math.max(0, need.y - vis.y);
	    hsb.max = Math.max(0, need.x - vis.x);
	    vsb.val = Utils.clip(vsb.val, vsb.min, vsb.max);
	    hsb.val = Utils.clip(hsb.val, hsb.min, hsb.max);
	    view.sy = vsb.val;
	    view.sx = hsb.val;
	}

	public boolean mousewheel(MouseWheelEvent ev) {
	    if(ui.modshift)
		hsb.ch((int)Math.round(ev.s * UI.scale(40)));
	    else
		vsb.ch(ev.s * UI.scale(15));
	    return(true);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 2) {
		pan = ui.grabmouse(this);
		pan0 = ev.c;
		panS = Coord.of(view.sx, view.sy);
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public void mousemove(MouseMoveEvent ev) {
	    super.mousemove(ev);
	    if(pan != null) {
		Coord d = ev.c.sub(pan0);
		vsb.val = Utils.clip(panS.y - d.y, vsb.min, vsb.max);
		hsb.val = Utils.clip(panS.x - d.x, hsb.min, hsb.max);
		view.sy = vsb.val;
		view.sx = hsb.val;
	    }
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if((ev.b == 2) && (pan != null)) {
		pan.remove();
		pan = null;
		return(true);
	    }
	    return(super.mouseup(ev));
	}
    }

    private class TreeBoard extends Widget {
	private List<Node> nodes = Collections.emptyList();
	private int maxDepth;
	private int[] colW = new int[1];
	private int[] locColW = new int[1];
	private int[] colX = new int[]{UI.scale(8)};

	TreeBoard() {
	    super(Coord.z);
	}

	void set(CheeseAging.Milk milk) {
	    nodes = trees();
	    layoutCols();
	    resize(layoutSize());
	    pane.updateScroll();
	}

	private void layoutCols() {
	    maxDepth = 0;
	    if(nodes.isEmpty()) {
		colW = new int[1];
		locColW = new int[1];
		colX = new int[]{UI.scale(8)};
		return;
	    }
	    walkDepth(nodes.get(0), 0);
	    locColW = new int[maxDepth + 1];
	    int[] cardColW = new int[maxDepth + 1];
	    walkWidth(nodes.get(0), 0, locColW, cardColW);
	    colW = new int[maxDepth + 1];
	    for(int d = 0; d <= maxDepth; d++)
		colW[d] = locColW[d] + cardColW[d];
	    colX = new int[maxDepth + 1];
	    colX[0] = UI.scale(8);
	    for(int d = 0; d < maxDepth; d++)
		colX[d + 1] = colX[d] + colW[d] + GAP;
	}

	private void walkDepth(Node n, int d) {
	    maxDepth = Math.max(maxDepth, d);
	    for(Node k : n.kids)
		walkDepth(k, d + 1);
	}

	private void walkWidth(Node n, int d, int[] locColW, int[] cardColW) {
	    locColW[d] = Math.max(locColW[d], locPad(n.out));
	    cardColW[d] = Math.max(cardColW[d], cardW(n.out));
	    for(Node k : n.kids)
		walkWidth(k, d + 1, locColW, cardColW);
	}

	private int blockH(Out o) {
	    return titleTex(o).sz().y + CheeseAging.feps(o.name).length * UI.scale(16) + UI.scale(6);
	}

	private int locH(Out o) {
	    if(o.locs.size() >= 2)
		return Math.max(UI.scale(40), LOC_ICO * 2 + UI.scale(2));
	    return Math.max(UI.scale(40), LOC_ICO + UI.scale(18));
	}

	private String cheeseTitle(Out o) {
	    String nm = CheeseAging.isCurd(o.name) ? curdTitle(milk) : L10n.tr(stripCheeseWord(o.name));
	    int pc = CheeseAging.pieces(o.name);
	    if(pc > 0)
		nm = nm + " ×" + pc;
	    return nm;
	}

	private Tex titleTex(Out o) {
	    return wrap(cheeseTitle(o), NAME_MAX);
	}

	private int cardW(Out o) {
	    int w = ICO + UI.scale(4) + titleTex(o).sz().x;
	    for(CheeseAging.Fep f : CheeseAging.feps(o.name))
		w = Math.max(w, UI.scale(4) + FEV + UI.scale(4) + txt(fepLine(f)).sz().x);
	    return w;
	}

	private int locPad(Out o) {
	    return o.locs.isEmpty() ? 0 : locW(o) + UI.scale(4);
	}

	private int locW(Out o) {
	    Tex hours = txt("(" + hoursPhrase(o.hours) + ")");
	    Tex names = txt(locNames(o));
	    return LOC_ICO + UI.scale(8) + Math.max(names.sz().x, hours.sz().x);
	}

	private int chainH(Node n) {
	    int h = Math.max(blockH(n.out), oHasLoc(n) ? locH(n.out) : 0);
	    if(n.kids.isEmpty())
		return h;
	    int gap = CheeseAging.isCurd(n.out.name) ? ROW_GAP : UI.scale(10);
	    if(n.kids.size() == 1)
		return Math.max(h, chainH(n.kids.get(0)));
	    int kh = 0;
	    for(Node k : n.kids)
		kh += chainH(k) + gap;
	    kh -= gap;
	    return Math.max(h, kh);
	}

	private boolean oHasLoc(Node n) {
	    return !n.out.locs.isEmpty();
	}

	private Coord layoutSize() {
	    if(nodes.isEmpty())
		return new Coord(UI.scale(16), UI.scale(16));
	    int w = colX[maxDepth] + colW[maxDepth] + UI.scale(8);
	    int h = STAGE_H + chainH(nodes.get(0)) + UI.scale(16);
	    return new Coord(w, h);
	}

	public void draw(GOut g) {
	    if(nodes.isEmpty())
		return;
	    drawStageHeads(g);
	    drawColLines(g);
	    drawChain(g, nodes.get(0), 0, STAGE_H + UI.scale(4));
	}

	private void drawStageHeads(GOut g) {
	    for(int d = 1; d <= maxDepth; d++) {
		Tex t = txt(L10n.tr("Stage") + " " + d);
		g.aimage(t, new Coord(colX[d] + colW[d] / 2, STAGE_H / 2), 0.5, 0.5);
	    }
	}

	private void drawColLines(GOut g) {
	    if(maxDepth < 1)
		return;
	    int top = 0;
	    int h = sz.y;
	    g.chcolor(LINE);
	    for(int d = 2; d <= maxDepth; d++) {
		int x = colX[d] - Math.max(UI.scale(1), GAP / 2);
		g.frect(new Coord(x, top), new Coord(UI.scale(1), h));
	    }
	    g.chcolor();
	}

	private int drawChain(GOut g, Node n, int d, int y) {
	    int bh = blockH(n.out);
	    int h = Math.max(bh, oHasLoc(n) ? locH(n.out) : 0);
	    int cw = cardW(n.out);
	    boolean fork = n.kids.size() > 1;
	    int gap = CheeseAging.isCurd(n.out.name) ? ROW_GAP : UI.scale(10);
	    int[] hs = null;
	    if(fork) {
		hs = new int[n.kids.size()];
		int kh = 0;
		for(int i = 0; i < n.kids.size(); i++) {
		    hs[i] = chainH(n.kids.get(i));
		    kh += hs[i] + gap;
		}
		kh -= gap;
		h = Math.max(h, kh);
	    } else if(n.kids.size() == 1) {
		h = Math.max(h, chainH(n.kids.get(0)));
	    }
	    int x = colX[d];
	    int lpCol = (d < locColW.length) ? locColW[d] : locPad(n.out);
	    int py = y + Math.max(0, (h - bh) / 2);
	    if(oHasLoc(n))
		drawLoc(g, n.out, x, y, h);
	    drawCol(g, n.out, x + lpCol, py, cw);
	    if(n.kids.size() == 1) {
		drawChain(g, n.kids.get(0), d + 1, y);
		return h;
	    }
	    if(fork) {
		int cy = y;
		int lineX = CheeseAging.isCurd(n.out.name) ? colX[d + 1] : x;
		for(int i = 0; i < n.kids.size(); i++) {
		    drawChain(g, n.kids.get(i), d + 1, cy);
		    if(CheeseAging.isCurd(n.out.name) && (i + 1 < n.kids.size())) {
			int ly = cy + hs[i] + gap / 2;
			int right = colX[maxDepth] + colW[maxDepth];
			g.chcolor(LINE);
			g.frect(new Coord(lineX, ly), new Coord(Math.max(UI.scale(8), right - lineX), UI.scale(1)));
			g.chcolor();
		    }
		    cy += hs[i] + gap;
		}
	    }
	    return h;
	}

	private void drawLoc(GOut g, Out o, int x, int y, int rowH) {
	    if(o.locs.isEmpty())
		return;
	    int h = locH(o);
	    int ly = y + Math.max(0, (rowH - h) / 2);
	    Tex hours = txt("(" + hoursPhrase(o.hours) + ")");
	    if(o.locs.size() >= 2) {
		int stackH = LOC_ICO * 2 + UI.scale(2);
		Tex ic1 = locTex(o.locs.get(0));
		if(ic1 != null)
		    g.aimage(ic1, new Coord(x + LOC_ICO / 2, ly + LOC_ICO / 2), 0.5, 0.5);
		Tex ic2 = locTex(o.locs.get(1));
		if(ic2 != null)
		    g.aimage(ic2, new Coord(x + LOC_ICO / 2, ly + LOC_ICO + UI.scale(2) + LOC_ICO / 2), 0.5, 0.5);
		Tex names = txt(locNames(o));
		int tx = x + LOC_ICO + UI.scale(6);
		int textH = names.sz().y + UI.scale(16);
		int ty = ly + Math.max(0, (stackH - textH) / 2);
		g.image(names, new Coord(tx, ty));
		g.image(hours, new Coord(tx, ty + UI.scale(16)));
		return;
	    }
	    int mid = ly + LOC_ICO / 2;
	    Tex ic = locTex(o.locs.get(0));
	    if(ic != null)
		g.aimage(ic, new Coord(x, mid), 0, 0.5);
	    int tx = x + LOC_ICO + UI.scale(6);
	    g.image(txt(locNames(o)), new Coord(tx, ly));
	    g.image(hours, new Coord(tx, ly + UI.scale(16)));
	}

	private int drawCol(GOut g, Out o, int x, int y, int w) {
	    Coord isz = Coord.of(ICO, ICO);
	    int tx = x;
	    int mid = y + UI.scale(10);
	    Tex ic = icon(CheeseAging.icons(o.name, milk), isz);
	    if(ic != null) {
		g.aimage(ic, new Coord(tx, mid), 0, 0.5, isz);
		tx += ICO + UI.scale(4);
	    }
	    Tex title = titleTex(o);
	    g.image(title, new Coord(tx, y + Math.max(0, (UI.scale(20) - title.sz().y) / 2)));
	    y += Math.max(UI.scale(22), title.sz().y + UI.scale(4));
	    for(CheeseAging.Fep f : CheeseAging.feps(o.name)) {
		int fx = x + UI.scale(4);
		Tex fs = icon(f.statRes(), Coord.of(FEV, FEV));
		if(fs != null) {
		    g.aimage(fs, new Coord(fx, y + UI.scale(7)), 0, 0.5);
		    fx += FEV + UI.scale(4);
		}
		String line = fepLine(f);
		if(f.plus2())
		    g.chcolor(PLUS2);
		g.image(txt(line), new Coord(fx, y));
		g.chcolor();
		y += UI.scale(16);
	    }
	    return y;
	}
    }

    public void dispose() {
        for(Tex texture:icache.values())texture.dispose();
        if(gui!=null && gui.cheeseChart==this)gui.cheeseChart=null;
        super.dispose();
    }
}
