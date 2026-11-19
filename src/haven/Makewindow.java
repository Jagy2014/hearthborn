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

import haven.render.*;
import java.util.*;
import java.awt.Font;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static haven.PType.*;
import static haven.Inventory.invsq;

public class Makewindow extends Widget {
    private static final Text.Foundry labelFont=new Text.Foundry(Text.sans,12).aa(true);
    /** Quality, input and result counts use the row label size instead of the default 10. */
    public static BufferedImage number(String text, Color color) {
        return Utils.outline2(labelFont.render(text, color).img, Color.BLACK);
    }
    public static final Text qmodl = labelFont.render(hearthborn.L10n.tr("Quality:"));
    public static final Text tooll = labelFont.render(hearthborn.L10n.tr("Tools:"));
    public static final Coord boff = UI.scale(new Coord(7, 9));
    public String rcpnm;
    private Label recipeTitle;
    public void localizeTitle(String title){recipeTitle.settext(title);}
    public List<Input> inputs = Collections.emptyList();
    public List<SpecWidget> outputs = Collections.emptyList();
    public List<Indir<Resource>> qmod = Collections.emptyList();
    public List<Indir<Resource>> tools = new ArrayList<>();;
    private final int inputy=UI.scale(40);
    /** The counts under the ingredients take a line, so everything below them stands lower; the line about what lacks and the buttons come last. */
    private int xoff = UI.scale(45), qmy = UI.scale(92), outy = UI.scale(119);
    private final int missy = UI.scale(158), acty = UI.scale(178);
    public static final int WIDTH = UI.scale(470);

    @RName("make")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Makewindow((String)args[0]));
	}
    }

    private static final OwnerContext.ClassResolver<Makewindow> ctxr = new OwnerContext.ClassResolver<Makewindow>()
	.add(Makewindow.class, wdg -> wdg)
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    public class Spec implements GSprite.Owner, ItemInfo.SpriteOwner, ItemInfo.Name.Dynamic, RandomSource {
	public ResData item, constraint;
	public int num;
	private GSprite spr;
	private Object[] rawinfo;
	private List<ItemInfo> info;
	private String itemName;
	private boolean itemNamed;

	public String name() {
	    String shown = dynamicName(sprite());
	    Resource.Tooltip tooltip = resource().layer(Resource.tooltip);
	    String fallback = tooltip == null ? null : tooltip.original;
	    if(shown != null && (fallback == null || !shown.equals(fallback)))
		return(shown);
	    return(outputName(shown, specificName(), fallback));
	}

	private String specificName() {
	    if(itemNamed)
		return(itemName);
	    if(constraint == null || item.sdt.eom()) {
		itemNamed = true;
		return(null);
	    }
	    itemName = dynamicName(GSprite.create(this, item.res.get(), item.sdt.clone()));
	    itemNamed = true;
	    return(itemName);
	}

	private String dynamicName(GSprite sprite) {
	    return(sprite instanceof ItemInfo.Name.Dynamic ? ((ItemInfo.Name.Dynamic)sprite).name() : null);
	}

	public Spec(ResData item, int num, Object[] info) {
	    this.item = item;
	    this.num = num;
	    this.rawinfo = (info.length > 0) ? info : new Object[][] {{new ItemInfo.Name.Default()}};
	}

	private ResData display() {
	    if(constraint != null)
		return(constraint);
	    return(item);
	}

	public GSprite sprite() {
	    if(spr == null)
		spr = GSprite.create(this, display().res.get(), display().sdt.clone());;
	    return(spr);
	}

	public void draw(GOut g) {
	    try {
		sprite().draw(g);
	    } catch(Loading e) {}
	}

	private int opt = 0;
	public boolean opt() {
	    if(opt == 0)
		opt = (ItemInfo.find(Optional.class, info()) != null) ? 1 : 2;
	    return(opt == 1);
	}

	public class SpecTip implements Indir<Tex>, ItemInfo.InfoTip {
	    private final List<ItemInfo> info;
	    private final TexI tex;

	    public SpecTip(List<ItemInfo> info, BufferedImage img) {
		this.info = info;
		if(img == null)
		    throw(new Loading());
		tex = new TexI(img);
	    }

	    public List<ItemInfo> info() {return(info);}
	    public Tex get() {return(tex);}
	}

	public SpecTip shorttip() {
	    List<ItemInfo> info = info();
	    return(new SpecTip(info, ItemInfo.shorttip(info())));
	}
	public SpecTip longtip() {
	    List<ItemInfo> info = info();
	    BufferedImage img = ItemInfo.longtip(info);
	    Resource.Pagina pg = item.res.get().layer(Resource.pagina);
	    if(pg != null)
		img = ItemInfo.catimgs(0, img, RichText.render("\n" + pg.text, 200).img);
	    return(new SpecTip(info, img));
	}

	private Random rnd = null;
	public Random mkrandoom() {
	    if(rnd == null)
		rnd = new Random();
	    return(rnd);
	}
	public Resource getres() {return(display().res.get());}
	public <T> T context(Class<T> cl) {return(ctxr.context(cl, Makewindow.this));}

	public List<ItemInfo> info() {
	    if(info == null)
		info = ItemInfo.buildinfo(this, rawinfo);
	    return(info);
	}
	public Resource resource() {return(item.res.get());}
    }

    /** A plain resource tooltip loses the cut. The item data carries the specific name. */
    public static String outputName(String shown, String item, String fallback) {
	if(item != null && (shown == null || (fallback != null && shown.equals(fallback))) && (fallback == null || !item.equals(fallback)))
	    return(item);
	if(shown != null)
	    return(shown);
	return(fallback);
    }

    public static final KeyBinding kb_make = KeyBinding.get("make/one", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, 0));
    public static final KeyBinding kb_makeall = KeyBinding.get("make/all", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, KeyMatch.C));
    private hearthborn.ui.ValueEntry amount;
    private int remaining;
    private boolean waiting, crafting;
    private double craftDeadline;
    private Button craft, craftAll;
    private Text missingText;
    private String missingValue = "";
    private final Map<String, Text> countTexts = new HashMap<>();
    private Text softcapText;
    private String softcapValue;
    private final Map<String, Tex> qualityIcons = new HashMap<>();
    public Makewindow(String rcpnm) {
        add(new Widget(UI.scale(35,35)) {
            public void draw(GOut g) {
                if(!outputs.isEmpty())try {outputs.get(0).spec.draw(g);}catch(Loading ignored){}
            }
        },Coord.z);
        recipeTitle=add(new Label(hearthborn.L10n.text("action",rcpnm),labelFont),UI.scale(45,8));
        int inputW=add(new Label("Input:",labelFont),Coord.of(0,inputy+UI.scale(8))).sz.x;
        int resultW=add(new Label("Result:",labelFont),Coord.of(0,outy+UI.scale(8))).sz.x;
        xoff=Math.max(inputW,resultW)+UI.scale(10);
        Button less=new Button(UI.scale(24),"−",false);
        add(less,Coord.of(0,acty)).action(()->step(-1));
        amount=add(new hearthborn.ui.ValueEntry(UI.scale(36),this::craftMultiple){
            protected String transform(int value){return Integer.toString(hearthborn.ui.RecipeStock.count(value));}
        },less.pos("ur").adds(2,1));
        amount.value(Utils.getprefi("hearthborn/craft/count",1));amount.clearOnFocus=true;
        amount.update=()->Utils.setprefi("hearthborn/craft/count",hearthborn.ui.RecipeStock.count(amount.value()));
        amount.settip(hearthborn.L10n.tr("How many times to craft."));
        Button more=new Button(UI.scale(24),"+",false);
        add(more,amount.pos("ur").adds(2,-1)).action(()->step(1));
        craft=new Button(UI.scale(85),"Craft",false) {
            /* Enter belongs to the craft only while the window is in sight and the craft is possible; otherwise it is the quick chat's. */
            public boolean gkeytype(GlobKeyEvent ev) {return(tvisible() && !disabled() && super.gkeytype(ev));}
        };
        craft.resize(Coord.of(Math.max(craft.sz.x,craft.text.sz().x+UI.scale(24)),craft.sz.y));
        /* The window keeps the width it always had: the quality line with the soft cap and the names of the ingredients need the room. */
        add(new Widget(Coord.of(WIDTH,1)),Coord.z);
        add(craft,more.pos("ur").adds(8,0)).action(this::craftMultiple).setgkey(kb_make);
        craftAll=new Button(UI.scale(60),"Everything",false) {
            public boolean gkeytype(GlobKeyEvent ev) {return(tvisible() && !disabled() && super.gkeytype(ev));}
        };
        craftAll.resize(Coord.of(Math.max(craftAll.sz.x,craftAll.text.sz().x+UI.scale(24)),craftAll.sz.y));
        add(craftAll,Coord.of(WIDTH-craftAll.sz.x,acty)).action(this::craftEverything).setgkey(kb_makeall);
        craft.move(Coord.of(craftAll.c.x-UI.scale(5)-craft.sz.x,acty));
        craftAll.settip(hearthborn.L10n.tr("Craft as many as the ingredients allow."));
	pack();
	this.rcpnm = rcpnm;
    }

    private Spec parsespec(Object[] desc) {
	int a = 0;
	Indir<Resource> res = ui.sess.getresv(desc[a++]);
	Message sdt = BYTES.is(desc, a) ? new MessageBuf(BYTES.of(desc, a++)) : MessageBuf.nil;
	int num = INT.of(desc, a++);
	Object[] info = OBJS.is(desc, a) ? OBJS.of(desc, a++) : new Object[0];
	Spec ret = new Spec(new ResData(res, sdt), num, info);
	while(a < desc.length) {
	    Object[] arg = OBJS.of(desc[a++]);
	    switch(STR.of(arg[0])) {
	    case "constraint":
		ret.constraint = new ResData(ui.sess.getresv(arg[1]), Message.nil);
		if(BYTES.is(arg, 2))
		    ret.constraint.sdt = new MessageBuf(BYTES.of(arg, 2));
		break;
	    }
	}
	return(ret);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "inpop") {
	    List<Spec> inputs;
	    if(INT.is(args, 0)) {
		inputs = Arrays.asList(this.inputs.stream().map(w -> w.spec).toArray(Spec[]::new));
		for(int i = 0; i < args.length; i += 2)
		    inputs.set(INT.of(args, i), parsespec(OBJS.of(args, i + 1)));
	    } else {
		inputs = new ArrayList<>();
		for(int i = 0; i < args.length; i++)
		    inputs.add(parsespec(OBJS.of(args[i])));
	    }
	    List<Input> wdgs = new ArrayList<>();
	    int idx = 0;
	    for(Spec spec : inputs)
		wdgs.add(new Input(spec, idx++));
	    synchronized(ui) {
		for(Widget w : this.inputs)
		    w.destroy();
		Position pos = new Position(xoff, inputy);
		SpecWidget prev = null;
		for(Input wdg : wdgs) {
		    if((prev != null) && (wdg.opt != false))
			pos = pos.adds(10, 0);
		    add(wdg, pos);
		    pos = pos.add(Inventory.sqsz.x, 0);
		    prev = wdg;
		}
		this.inputs = wdgs;
	    }
	} else if(msg == "opop") {
	    List<Spec> outputs;
	    if(INT.is(args, 0)) {
		outputs = Arrays.asList(this.outputs.stream().map(w -> w.spec).toArray(Spec[]::new));
		for(int i = 0; i < args.length; i += 2)
		    outputs.set(INT.of(args, i), parsespec(OBJS.of(args, i + 1)));
	    } else {
		outputs = new ArrayList<>();
		for(int i = 0; i < args.length; i++)
		    outputs.add(parsespec(OBJS.of(args[i])));
	    }
	    List<SpecWidget> wdgs = new ArrayList<>();
	    for(Spec spec : outputs)
		wdgs.add(new SpecWidget(spec));
	    synchronized(ui) {
		for(Widget w : this.outputs)
		    w.destroy();
		Position pos = new Position(xoff, outy);
		SpecWidget prev = null;
		for(SpecWidget wdg : wdgs) {
		    if((prev != null) && (wdg.opt != prev.opt))
			pos = pos.adds(10, 0);
		    add(wdg, pos);
		    pos = pos.add(Inventory.sqsz.x, 0);
		    prev = wdg;
		}
		this.outputs = wdgs;
	    }
	} else if(msg == "qmod") {
	    List<Indir<Resource>> qmod = new ArrayList<Indir<Resource>>();
	    for(Object arg : args)
		qmod.add(ui.sess.getresv(arg));
	    this.qmod = qmod;
	} else if(msg == "tool") {
	    tools.add(ui.sess.getresv(args[0]));
	} else if(msg == "use") {
	    inputs.get(INT.of(args[0])).using(INT.of(args[1]));
	    stockSeen = true;
	} else if(msg == "inprcps") {
	    int idx = INT.of(args[0]);
	    List<MenuGrid.Pagina> rcps = new ArrayList<>();
	    GameUI gui = getparent(GameUI.class);
	    if((gui != null) && (gui.menu != null)) {
		for(int a = 1; a < args.length; a++)
		    rcps.add(gui.menu.paginafor(ui.sess.getresv(args[a])));
	    }
	    inputs.get(idx).recipes(rcps);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final Coord qmodsz = UI.scale(20, 20);
    private Tex qmicon(Indir<Resource> qm) {
        Resource resource=qm.get();
        Glob.CAttr attr=ui.sess.glob.getcattr(resource.basename());
        boolean quality=qmod.contains(qm);
        String key=resource.name+":"+(quality?attr.comp+":"+attr.base:"tool");
        Tex result=qualityIcons.get(key);
        if(result==null) {
            BufferedImage image=PUtils.convolve(resource.flayer(Resource.imgc).img,qmodsz,CharWnd.iconfilter);
            if(quality) image=ItemInfo.catimgsh(UI.scale(1),image,
                number(Integer.toString(attr.comp),attr.comp>attr.base?CharWnd.buff:attr.comp<attr.base?CharWnd.debuff:Color.WHITE));
            for(Iterator<Map.Entry<String,Tex>> it=qualityIcons.entrySet().iterator();it.hasNext();) {
                Map.Entry<String,Tex> old=it.next();if(old.getKey().startsWith(resource.name+":")){old.getValue().dispose();it.remove();}
            }
            result=new TexI(image);qualityIcons.put(key,result);
        }
        return result;
    }

    public static class SpecWidget extends Widget {
	public final Spec spec;
	public final boolean opt;
	public Tex num;

	public SpecWidget(Spec spec) {
	    super(invsq.sz());
	    this.spec = spec;
	    opt = spec.opt();
	    if(spec.num >= 0)
		this.num = new TexI(number(Integer.toString(spec.num), Color.WHITE));
	    else
		this.num = null;
	}

	public List<ItemInfo> info() {return(spec.info());}

	public void drawbg(GOut g) {
	    if(opt) {
		g.chcolor(0, 255, 0, 255);
		g.image(invsq, Coord.z);
		g.chcolor();
	    } else {
		g.image(invsq, Coord.z);
	    }
	}

	public final ItemInfo.AttrCache<Pipe.Op> rstate = new ItemInfo.AttrCache<>(this::info, GItem.RStateInfo.combine);
	public void drawicon(GOut g) {
	    if(rstate.get() != null)
		g.usestate(rstate.get());
	    spec.draw(g);
	    g.defstate();
	    if(num != null)
		g.aimage(num, Inventory.sqsz, 1.0, 1.0);
	}

	public void draw(GOut g) {
	    drawbg(g);
	    drawicon(g);
	}

	private double hoverstart;
	Object stip, ltip;
	public Object tooltip(Coord c, Widget prev) {
	    double now = Utils.rtime();
	    if(prev == this) {
	    } else if(prev instanceof SpecWidget) {
		double ps = ((SpecWidget)prev).hoverstart;
		hoverstart = (now - ps < 1.0) ? now : ps;
	    } else {
		hoverstart = now;
	    }
	    if(now - hoverstart < 1.0) {
		if(stip == null)
		    stip = spec.shorttip();
		return(stip);
	    } else {
		if(ltip == null)
		    ltip = spec.longtip();
		return(ltip);
	    }
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(spec.spr != null)
		spec.spr.tick(dt);
	}
    }

    public class Input extends SpecWidget implements DTarget {
	public final int idx;
	public int using = 0;
	private List<MenuGrid.Pagina> rpag = null;
	private Coord cc = null;

	public Input(Spec spec, int idx) {
	    super(spec);
	    this.idx = idx;
	    /* The count is written under the icon, "have/need", not in its corner. */
	    this.num = null;
	}

	public void drawbg(GOut g) {
	    super.drawbg(g);
	    if(!opt && (using < spec.num)) {
		g.chcolor(255, 0, 0, 64);
		g.frect2(Coord.of(0, (sz.y * using) / spec.num), sz);
		g.chcolor();
	    }
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b == 1 && ui.modflags() == UI.MOD_META) {
		/* Alt+click: the recipes that use this ingredient, in the action palette. */
		try {
		    if(hearthborn.ui.ActionBrowser.searchByResource(getparent(GameUI.class), spec.resource()))
			return(true);
		} catch(Loading l) {
		    return(true);
		}
	    }
	    if(ev.b == 1) {
		Makewindow.this.wdgmsg("choose", idx, ui.modflags());
		return(true);
	    } else if(ev.b == 3) {
		if(rpag == null)
		    Makewindow.this.wdgmsg("findrcps", idx);
		this.cc = ev.c;
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if((cc != null) && (rpag != null)) {
		if(!rpag.isEmpty()) {
		    SListMenu.of(UI.scale(250, 120), rpag,
				 pag -> pag.button().name(), pag -> pag.button().img(),
				 pag -> pag.button().use(new MenuGrid.Interaction(1, ui.modflags())))
			.addat(this, cc.add(UI.scale(5, 5))).tick(dt);
		}
		cc = null;
	    }
	}

	public boolean drop(Coord cc, Coord ul) {
	    Makewindow.this.wdgmsg("itemact", idx, ui.modflags());
	    return(true);
	}

	public boolean iteminteract(Coord cc, Coord ul) {
	    Makewindow.this.wdgmsg("itemact", idx, ui.modflags());
	    return(true);
	}

	public void recipes(List<MenuGrid.Pagina> pag) {
	    rpag = pag;
	}

	public void using(int a) {
	    using = a;
	}
    }

    public void draw(GOut g) {
	int x = 0;
	if(!qmod.isEmpty()) {
	    g.aimage(qmodl.tex(), new Coord(x, qmy + (qmodsz.y / 2)), 0, 0.5);
	    x += qmodl.sz().x + UI.scale(5);
	    x = Math.max(x, xoff);
	    qmx = x;
	    int count=0;double product=1;
	    for(Indir<Resource> qm : qmod) {
		try {
		    Tex t = qmicon(qm);
		    g.image(t, new Coord(x, qmy));
		    x += t.sz().x + UI.scale(1);
                    product*=ui.sess.glob.getcattr(qm.get().basename()).comp;count++;
		} catch(Loading l) {
		}
	    }
            if(count>0) {
                String value=hearthborn.L10n.tr("Softcap:")+" "+String.format(java.util.Locale.ROOT,"%.1f",Math.pow(product,1.0/count));
                if(!value.equals(softcapValue)) {
                    if(softcapText!=null)softcapText.tex().dispose();
                    softcapValue=value;softcapText=labelFont.render(value);
                }
                g.aimage(softcapText.tex(),Coord.of(x+UI.scale(5),qmy+qmodsz.y/2),0,.5);x+=softcapText.sz().x+UI.scale(6);
            }
	    x += UI.scale(25);
	}
	if(!tools.isEmpty()) {
	    g.aimage(tooll.tex(), new Coord(x, qmy + (qmodsz.y / 2)), 0, 0.5);
	    x += tooll.sz().x + UI.scale(5);
	    x = Math.max(x, xoff);
	    toolx = x;
	    for(Indir<Resource> tool : tools) {
		try {
		    Tex t = qmicon(tool);
		    g.image(t, new Coord(x, qmy));
		    x += t.sz().x + UI.scale(1);
		} catch(Loading l) {
		}
	    }
	    x += UI.scale(25);
	}
	super.draw(g);
	for(Input in : inputs) {
	    if(in.opt || in.spec.num <= 0)
		continue;
	    boolean enough = hearthborn.ui.RecipeStock.missing(in.spec.num, in.using, false) == 0;
	    Text t = countText(hearthborn.ui.RecipeStock.progress(in.spec.num, in.using), enough ? COUNT_ENOUGH : COUNT_SHORT);
	    g.aimage(t.tex(), Coord.of(in.c.x + in.sz.x / 2, in.c.y + in.sz.y + UI.scale(1)), 0.5, 0);
	}
	if(!missingValue.isEmpty()) {
	    if(missingText == null) {
		missingText = labelFont.render(clip(missingValue, sz.x), COUNT_SHORT);
	    }
	    g.image(missingText.tex(), Coord.of(0, missy));
	}
    }

    private static final Color COUNT_ENOUGH = new Color(120, 220, 120), COUNT_SHORT = new Color(235, 95, 85);
    private Text countText(String value, Color color) {
	String key = color.getRGB() + value;
	Text t = countTexts.get(key);
	if(t == null) {
	    if(countTexts.size() > 64) {
		for(Text old : countTexts.values())
		    old.tex().dispose();
		countTexts.clear();
	    }
	    countTexts.put(key, t = labelFont.render(value, color));
	}
	return(t);
    }

    /** A line that does not fit ends in an ellipsis instead of widening the window. */
    private static String clip(String text, int width) {
	if(labelFont.strsize(text).x <= width)
	    return(text);
	while(text.length() > 1 && labelFont.strsize(text + "…").x > width)
	    text = text.substring(0, text.length() - 1);
	return(text + "…");
    }

    /** What the ingredients lack, from what the server counted. Null while the ingredients are not known yet. */
    private boolean stockSeen = false;
    private void updateStock() {
	List<String> names = new ArrayList<>();
	List<Integer> counts = new ArrayList<>();
	boolean lacking = false;
	int needed = 0, ready = 0;
	for(Input in : inputs) {
	    int lack = hearthborn.ui.RecipeStock.missing(in.spec.num, in.using, in.opt);
	    if(!in.opt) {
		needed++;
		if(lack == 0)
		    ready++;
	    }
	    if(lack > 0) {
		lacking = true;
		String name;
		try {name = in.spec.name();} catch(Loading l) {name = "?";}
		names.add(name);
		counts.add(lack);
	    }
	}
	if(stockSeen)
	    hearthborn.ui.RecipeStatus.record(rcpnm, ready, needed);
	String line = hearthborn.ui.RecipeStock.summary(hearthborn.L10n.tr("Missing:"), names, counts);
	if(!line.equals(missingValue)) {
	    missingValue = line;
	    if(missingText != null) {missingText.tex().dispose(); missingText = null;}
	}
	if(craft != null && craft.disabled() != lacking) {
	    craft.disable(lacking);
	    craftAll.disable(lacking);
	}
    }

    private void step(int by) {
	amount.value(Math.max(1, hearthborn.ui.RecipeStock.count(amount.value()) + by));
	amount.update.run();
    }

    private int qmx, toolx;
    public boolean keydown(KeyDownEvent ev) {
        if(ev.code==java.awt.event.KeyEvent.VK_ESCAPE){waiting=false;remaining=0;}
        return super.keydown(ev);
    }
    private void craftMultiple() {
        if(craft != null && craft.disabled())return;
        int count=hearthborn.ui.RecipeStock.count(amount.value());remaining=count-1;
        waiting=count>1;crafting=false;craftDeadline=Utils.rtime()+1;
        wdgmsg("make",0);
    }
    private void craftEverything() {
        if(craftAll != null && craftAll.disabled())return;
        waiting=false;remaining=0;
        wdgmsg("make",1);
    }
    public void tick(double dt) {
        super.tick(dt);
        updateStock();
        if(!waiting)return;
        GameUI gui=getparent(GameUI.class);
        if(gui==null || Utils.rtime()>craftDeadline){waiting=false;remaining=0;return;}
        if(gui.prog!=null){crafting=true;craftDeadline=Utils.rtime()+60;}
        else if(crafting) {
            crafting=false;
            if(remaining-- > 0){wdgmsg("make",0);craftDeadline=Utils.rtime()+1;}
            else waiting=false;
        }
    }
    public void dispose() {
        waiting=false;if(softcapText!=null)softcapText.tex().dispose();if(missingText!=null)missingText.tex().dispose();for(Text t:countTexts.values())t.tex().dispose();countTexts.clear();for(Tex tex:qualityIcons.values())tex.dispose();qualityIcons.clear();super.dispose();
    }
    public Object tooltip(Coord mc, Widget prev) {
	Spec tspec = null;
	Coord c;
	if(!qmod.isEmpty()) {
	    c = new Coord(qmx, qmy);
	    for(Indir<Resource> qm : qmod) {
		Coord tsz = qmicon(qm).sz();
		if(mc.isect(c, tsz))
		    return(qm.get().flayer(Resource.tooltip).t);
		c = c.add(tsz.x + UI.scale(1), 0);
	    }
	}
	if(!tools.isEmpty()) {
	    c = new Coord(toolx, qmy);
	    for(Indir<Resource> tool : tools) {
		Coord tsz = qmicon(tool).sz();
		if(mc.isect(c, tsz))
		    return(tool.get().flayer(Resource.tooltip).t);
		c = c.add(tsz.x + UI.scale(1), 0);
	    }
	}
	return(super.tooltip(mc, prev));
    }

    public static class Optional extends ItemInfo.Tip {
	public static final Text text = RichText.render("$i{Optional}", 0);
	public Optional(Owner owner) {
	    super(owner);
	}

	public BufferedImage tipimg() {
	    return(text.img);
	}

	public Tip shortvar() {return(this);}
    }
}
