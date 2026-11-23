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
import java.awt.image.BufferedImage;
import static haven.CharWnd.*;
import static haven.PUtils.*;
import static haven.PType.*;

public class QuestWnd extends Widget {
    private static final RichText.Foundry QUEST_TEXT=new RichText.Foundry(RichText.IMAGESRC,RichText.ImageSource.legacy,
        java.awt.font.TextAttribute.FAMILY,hearthborn.UiFonts.family("SansSerif"),java.awt.font.TextAttribute.SIZE,UI.scale(12)).aa(true);
    public final Widget questbox;
    public final QuestList cqst, dqst;
    public Quest.Info quest;

    @RName("quests")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new QuestWnd());
	}
    }

    public static class Quest {
	public static final int QST_PEND = 0, QST_DONE = 1, QST_FAIL = 2, QST_DISABLED = 3;
	public static final Color[] stcol = {
	    new Color(255, 255, 64), new Color(64, 255, 64), new Color(255, 64, 64),
	};
	public static final char[] stsym = {'\u2022', '\u2713', '\u2717'};
	public final int id;
	public Indir<Resource> res;
	public String title;
	public int done, ncond, ndcond;
	public int mtime;

	private Quest(int id) {
	    this.id = id;
	}

	public String title() {
	    if(title != null)
		return(hearthborn.L10n.text("label", title));
	    return(res.get().flayer(Resource.tooltip).t);
	}

	public static class Condition {
	    public final String desc;
	    public int done;
	    public String status;
	    public Object[] wdata = null;

	    public Condition(String desc, int done, String status) {
		this.desc = desc;
		this.done = done;
		this.status = status;
	    }
	}

	private static final Tex qcmp = catf.render(hearthborn.L10n.text("label", "Quest completed")).tex();
	private static final Tex qfail = failf.render(hearthborn.L10n.text("label", "Quest failed")).tex();
	public void done(GameUI parent) {
            if(done == QST_DONE) {
                GameUI game = parent;
                parent.add(new Widget() {
                    public void tick(double dt) {
                        try {
                            game.msg(new UI.SimpleMessage(hearthborn.L10n.format("Quest completed: %s", Quest.this.title()), stcol[QST_DONE], null));
                            destroy();
                        } catch(Loading waiting) {
                            // The quest resource may arrive after its completion update.
                        }
                    }
                });
                return;
            }
	    parent.add(new Widget() {
		    double a = 0.0;
		    Tex img, title, msg;

		    public void draw(GOut g) {
			if(img != null) {
			    if(a < 0.2)
				g.chcolor(255, 255, 255, (int)(255 * Utils.smoothstep(a / 0.2)));
			    else if(a > 0.8)
				g.chcolor(255, 255, 255, (int)(255 * Utils.smoothstep(1.0 - ((a - 0.8) / 0.2))));
			    /*
			    g.image(img, new Coord(0, (Math.max(img.sz().y, title.sz().y) - img.sz().y) / 2));
			    g.image(title, new Coord(img.sz().x + 25, (Math.max(img.sz().y, title.sz().y) - title.sz().y) / 2));
			    g.image(msg, new Coord((sz.x - msg.sz().x) / 2, Math.max(img.sz().y, title.sz().y) + 25));
			    */
			    int y = 0;
			    g.image(img, new Coord((sz.x - img.sz().x) / 2, y)); y += img.sz().y + 15;
			    g.image(title, new Coord((sz.x - title.sz().x) / 2, y)); y += title.sz().y + 15;
			    g.image(msg, new Coord((sz.x - msg.sz().x) / 2, y));
			}
		    }

		    public void tick(double dt) {
			if(img == null) {
			    try {
				title = (done == QST_DONE?catf:failf).render(title()).tex();
				img = res.get().flayer(Resource.imgc).tex();
				msg = (done == QST_DONE)?qcmp:qfail;
				/*
				resize(new Coord(Math.max(img.sz().x + 25 + title.sz().x, msg.sz().x),
						 Math.max(img.sz().y, title.sz().y) + 25 + msg.sz().y));
				*/
				resize(new Coord(Math.max(Math.max(img.sz().x, title.sz().x), msg.sz().x),
						 img.sz().y + 15 + title.sz().y + 15 + msg.sz().y));
				presize();
			    } catch(Loading l) {
				return;
			    }
			}
			if((a += (dt * 0.2)) > 1.0)
			    destroy();
		    }

		    public void presize() {
			c = parent.sz.sub(sz).div(2);
		    }

		    protected void added() {
			presize();
		    }
		});
	}

	public abstract static class CondWidget extends Widget {
	    public final Condition cond;

	    public CondWidget(Condition cond) {
		this.cond = cond;
	    }

	    public boolean update() {
		return(false);
	    }
	}

	public static class DefaultCond extends CondWidget {
	    public Text text;
	    private String picture;
	    private Tex icon;
	    private boolean missingIcon;
	    private int iconAt;

	    public DefaultCond(Condition cond) {super(cond);}

	    protected void added() {
		super.added();
		StringBuilder buf = new StringBuilder();
		Box box = getparent(Box.class);
		String desc = hearthborn.ui.CredoQuests.objective(box == null ? null : box.title, cond.desc);
		buf.append(String.format("%s{%c %s", RichText.Parser.col2a(stcol[cond.done]), stsym[cond.done], RichText.Parser.quote(hearthborn.L10n.text("label", desc))));
		if(cond.status != null) {
		    buf.append(' ');
		    buf.append(RichText.Parser.quote(hearthborn.L10n.text("label", cond.status)));
		}
		buf.append("}");
		picture = hearthborn.ui.QuestPictures.resource(cond.desc);
		int extra = picture != null ? UI.scale(26) : 0;
		text = QUEST_TEXT.render(buf.toString(), Math.max(UI.scale(40), parent.sz.x - UI.scale(20) - extra));
		int height = Math.max(text.sz().y, picture != null ? UI.scale(22) : 0);
		iconAt = UI.scale(15) + text.sz().x + UI.scale(4);
		resize(Coord.of(iconAt + Math.max(0, extra - UI.scale(4)), height + UI.scale(1)));
	    }

	    public void draw(GOut g) {
		g.image(text.tex(), Coord.of(UI.scale(15), Math.max(0, (sz.y - text.sz().y) / 2)));
		if(picture != null) {
		    Tex image = icon();
		    if(image != null)
			g.image(image, Coord.of(iconAt, Math.max(0, (sz.y - UI.scale(22)) / 2)), UI.scale(22, 22));
		}
	    }

            public boolean mousedown(MouseDownEvent event) {
                Box box = getparent(Box.class);
                return box != null && event.b == 1 && cond.done == QST_PEND && picture != null
                    && event.c.isect(Coord.of(iconAt, Math.max(0, (sz.y - UI.scale(22)) / 2)), UI.scale(22, 22))
                    && hearthborn.ui.QuestForage.open(getparent(GameUI.class), box.id, cond.desc, picture);
            }

	    private Tex icon() {
		if(icon != null || missingIcon || picture == null) return icon;
		try {
		    Resource.Image layer = Resource.remote().load(picture).get().layer(Resource.imgc);
		    if(layer == null || layer.img == null) { missingIcon = true; return null; }
		    return icon = new TexI(PUtils.uiscale(layer.img, UI.scale(22, 22)));
		} catch(Loading pending) { return null; }
		catch(RuntimeException error) { missingIcon = true; return null; }
	    }

	    public void dispose() {
		if(icon != null) icon.dispose();
		super.dispose();
	    }
	}

	public static class Box extends Widget implements Info, QView.QVInfo {
	    public final int id;
	    public final Indir<Resource> res;
	    public final String title;
	    public Condition[] cond = {};
	    private QView cqv;

	    public Box(int id, Indir<Resource> res, String title) {
		super(Coord.z);
		this.id = id;
		this.res = res;
		this.title = title;
	    }

	    protected void added() {
		resize(parent.sz);
	    }

	    public String title() {
		if(title != null)
		    return(hearthborn.L10n.text("label", title));
		return(res.get().flayer(Resource.tooltip).t);
	    }

	    public Condition[] conds() {
		return(cond);
	    }

	    private QuestWnd qw = null;
	    public int done() {
		if(qw == null)
		    qw = getparent(QuestWnd.class);
		if(qw == null)
		    return(Quest.QST_PEND);
		Quest qst;
		if((qst = qw.cqst.get(id)) != null)
		    return(qst.done);
		if((qst = qw.dqst.get(id)) != null)
		    return(qst.done);
		return(Quest.QST_PEND);
	    }

	    public void refresh() {
	    }

	    public String rendertext() {
		StringBuilder buf = new StringBuilder();
		Resource res = this.res.get();
		buf.append("$img[self]\n\n");
		buf.append("$b{$font[serif,16]{" + title() + "}}\n\n");
		Resource.Pagina pag = res.layer(Resource.pagina);
		if((pag != null) && !pag.text.equals("")) {
		    buf.append("\n");
		    buf.append(pag.text);
		    buf.append("\n");
		}
		return(buf.toString());
	    }

	    public Condition findcond(String desc) {
		for(Condition cond : this.cond) {
		    if(cond.desc.equals(desc))
			return(cond);
		}
		return(null);
	    }

	    public void uimsg(String msg, Object... args) {
		if(msg == "conds") {
		    int a = 0;
		    List<Condition> ncond = new ArrayList<Condition>(args.length);
		    while(a < args.length) {
			String desc = STR.of(args[a++]);
			int st = INT.of(args[a++]);
			String status = STR.of(args[a++]);
			Object[] wdata = null;
			if((a < args.length) && OBJS.is(args[a]))
			    wdata = OBJS.of(args[a++]);
			Condition cond = findcond(desc);
			if(cond != null) {
			    boolean ch = false;
			    if(st != cond.done) {cond.done = st; ch = true;}
			    if(!Utils.eq(status, cond.status)) {cond.status = status; ch = true;}
			    if(!Arrays.equals(wdata, cond.wdata)) {cond.wdata = wdata; ch = true;}
			    if(ch && (cqv != null))
				cqv.update(cond);
			} else {
			    cond = new Condition(desc, st, status);
			    cond.wdata = wdata;
			}
			ncond.add(cond);
		    }
		    this.cond = ncond.toArray(new Condition[0]);
            GameUI gui=getparent(GameUI.class);
            if(gui!=null) {
                hearthborn.ui.QuestForage.objectives(gui, id, cond);
                gui.questHelper.capture(this);
            }
		    refresh();
		    if(cqv != null)
			cqv.update();
		} else {
		    super.uimsg(msg, args);
		}
	    }

	    public void destroy() {
		super.destroy();
		if(cqv != null)
		    cqv.reqdestroy();
	    }

	    public int questid() {return(id);}

	    public Widget qview() {
		return(cqv = new QView(this));
	    }
	}

	public static class QView extends Widget {
	    public static final Text.Furnace qtfnd = new BlurFurn(new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD), 16).aa(true), 2, 1, Color.BLACK);
	    public static final Text.Foundry qcfnd = new Text.Foundry(Text.sans, 12).aa(true);
	    public final QVInfo info;
	    private Condition[] ccond;
	    private Tex[] rcond = {};
	    private Tex rtitle = null;
	    private Tex glow, glowon;
	    private double glowt = -1;

	    public interface QVInfo {
		public String title();
		public Condition[] conds();
		public int done();
	    }

	    public QView(QVInfo info) {
		this.info = info;
	    }

	    private void resize() {
		Coord sz = new Coord(0, 0);
		if(rtitle != null) {
		    sz.y += rtitle.sz().y + UI.scale(5);
		    sz.x = Math.max(sz.x, rtitle.sz().x);
		}
		for(Tex c : rcond) {
		    sz.y += c.sz().y;
		    sz.x = Math.max(sz.x, c.sz().x);
		}
		sz.x += UI.scale(3);
		resize(sz);
	    }

	    public void draw(GOut g) {
		int y = 0;
		if(rtitle != null) {
		    if(rootxlate(ui.mc).isect(Coord.z, rtitle.sz()))
			g.chcolor(192, 192, 255, 255);
		    else if(info.done() == QST_DISABLED)
			g.chcolor(255, 128, 0, 255);
		    g.image(rtitle, new Coord(3, y));
		    g.chcolor();
		    y += rtitle.sz().y + 5;
		}
		for(Tex c : rcond) {
		    g.image(c, new Coord(3, y));
		    if(c == glowon) {
			double a = (1.0 - Math.pow(Math.cos(glowt * 2 * Math.PI), 2));
			g.chcolor(255, 255, 255, (int)(128 * a));
			g.image(glow, new Coord(0, y - 3));
			g.chcolor();
		    }
		    y += c.sz().y;
		}
	    }

	    public boolean mousedown(MouseDownEvent ev) {
		if((rtitle != null) && ev.c.isect(Coord.z, rtitle.sz())) {
		    CharWnd cw = getparent(GameUI.class).chrwdg;
		    cw.show();
		    cw.raise();
		    cw.parent.setfocus(cw);
		    cw.questtab.showtab();
		    return(true);
		}
		return(super.mousedown(ev));
	    }

	    public void tick(double dt) {
		if(rtitle == null) {
		    try {
			rtitle = titleTex(info.title());
			resize();
		    } catch(Loading l) {
		    }
		}
		if(glowt >= 0) {
		    if((glowt += (dt * 0.5)) > 1.0) {
			glowt = -1;
			glow = glowon = null;
		    }
		}
	    }

	    private static final int QW = 280;

	    private Tex titleTex(String title) {
		int width = UI.scale(QW);
		Text.Foundry font = new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD), 16).aa(true);
		if(font.strsize(title).x <= width)
		    return(qtfnd.render(title).tex());
		Text text = font.renderwrap(title, width);
		return(new TexI(rasterimg(blurmask2(text.img.getRaster(), 2, 1, Color.BLACK))));
	    }

	    private Text ct(Condition c) {
		String prefix = " " + stsym[c.done] + " ";
		String title = info instanceof Box ? ((Box)info).title : null;
		String body = hearthborn.L10n.text("label", hearthborn.ui.CredoQuests.objective(title, c.desc));
		if(c.status != null)
		    body = body + " " + hearthborn.L10n.text("label", c.status);
		Color color = stcol[c.done];
		int width = UI.scale(QW);
		String line = prefix + body;
		if(qcfnd.strsize(line).x <= width)
		    return(qcfnd.render(line, color));
		int rest = Math.max(UI.scale(80), width - qcfnd.strsize(prefix).x);
		BufferedImage tail = qcfnd.renderwrap(body, color, rest).img;
		BufferedImage lead = qcfnd.render(prefix, color).img;
		BufferedImage img = TexI.mkbuf(new Coord(lead.getWidth() + tail.getWidth(), Math.max(lead.getHeight(), tail.getHeight())));
		java.awt.Graphics g = img.createGraphics();
		g.drawImage(lead, 0, 0, null);
		g.drawImage(tail, lead.getWidth(), 0, null);
		g.dispose();
		return(new Text(line, img));
	    }

	    void update() {
		Condition[] cond = info.conds();
		List<Condition> shown = new ArrayList<>();
		List<Tex> lines = new ArrayList<>();
		for(Condition c : cond) {
		    if(c.done == 1)
			continue;
		    BufferedImage text = ct(c).img;
		    shown.add(c);
		    lines.add(new TexI(rasterimg(blurmask2(text.getRaster(), 1, 1, Color.BLACK))));
		}
		if(glowon != null) {
		    Condition was = null;
		    for(int i = 0; i < this.rcond.length; i++) {
			if(this.rcond[i] == glowon) {
			    was = this.ccond[i];
			    break;
			}
		    }
		    glowon = null;
		    if(was != null) {
			for(int o = 0; o < shown.size(); o++) {
			    if(shown.get(o) == was) {
				glowon = lines.get(o);
				break;
			    }
			}
		    }
		}
		this.ccond = shown.toArray(new Condition[0]);
		this.rcond = lines.toArray(new Tex[0]);
		resize();
	    }

	    void update(Condition c) {
		glow = new TexI(rasterimg(blurmask2(ct(c).img.getRaster(), 3, 2, stcol[c.done])));
		for(int i = 0; i < ccond.length; i++) {
		    if(ccond[i] == c) {
			glowon = rcond[i];
			break;
		    }
		}
		glowt = 0.0;
	    }
	}

	public static class DefaultBox extends Box {
	    private Widget current;
	    private boolean refresh = true;
	    public List<Pair<String, String>> options = Collections.emptyList();
	    public CondWidget[] condw = {};

	    public DefaultBox(int id, Indir<Resource> res, String title) {
		super(id, res, title);
	    }

	    protected void layouth(Widget cont) {
		RichText text = QUEST_TEXT.render(resdoc(res.get(), rendertext()), cont.sz.x - UI.scale(20));
		cont.add(new Img(text.tex()), UI.scale(new Coord(10, 10)));
	    }

	    protected void layoutc(Widget cont) {
		int y = cont.contentsz().y + UI.scale(10);
		CondWidget[] nw = new CondWidget[cond.length];
		CondWidget[] pw = condw;
		cond: for(int i = 0; i < cond.length; i++) {
		    for(int o = 0; o < pw.length; o++) {
			if((pw[o] != null) && (pw[o].cond == cond[i])) {
			    if(pw[o].update()) {
				pw[o].unlink();
				nw[i] = cont.add(pw[o], new Coord(0, y));
				y += nw[i].sz.y;
				pw[o] = null;
				continue cond;
			    }
			}
		    }
		    if(cond[i].wdata != null) {
			Indir<Resource> wres = ui.sess.getresv(cond[i].wdata[0]);
			nw[i] = (CondWidget)wres.get().getcode(Widget.Factory.class, true).create(ui, new Object[] {cond[i]});
		    } else {
			nw[i] = new DefaultCond(cond[i]);
		    }
		    y += cont.add(nw[i], new Coord(0, y)).sz.y;
		}
		condw = nw;
	    }

	    protected void layouto(Widget cont) {
		int y = cont.contentsz().y + UI.scale(10);
		for(Pair<String, String> opt : options) {
		    y += cont.add(new Button(cont.sz.x - UI.scale(20), opt.b, false) {
			    public void click() {
				DefaultBox.this.wdgmsg("opt", opt.a);
			    }
			}, new Coord(UI.scale(10), y)).sz.y + UI.scale(5);
		}
	    }

	    protected void layout(Widget cont) {
		layouth(cont);
		layoutc(cont);
		layouto(cont);
	    }

	    public void draw(GOut g) {
		refresh: if(refresh) {
		    Scrollport newch = new Scrollport(sz);
		    try {
			layout(newch.cont);
		    } catch(Loading l) {
			break refresh;
		    }
		    if(current != null)
			current.destroy();
		    current = add(newch, Coord.z);
		    refresh = false;
		}
		super.draw(g);
	    }

	    public void refresh() {
		refresh = true;
	    }

	    public void uimsg(String msg, Object... args) {
		if(msg == "opts") {
		    List<Pair<String, String>> opts = new ArrayList<>();
		    for(int i = 0; i < args.length; i += 2)
			opts.add(new Pair<>(STR.of(args[i]), STR.of(args[i + 1])));
		    this.options = opts;
		    refresh();
		} else {
		    super.uimsg(msg, args);
		}
	    }
	}

	@RName("quest")
	public static class $quest implements Factory {
	    public Widget create(UI ui, Object[] args) {
		int id = INT.of(args[0]);
		Indir<Resource> res = ui.sess.getresv(args[1]);
		String title = (args.length > 2) ? STR.of(args[2]) : null;
		return(new DefaultBox(id, res, title));
	    }
	}
	public interface Info {
	    public int questid();
	    public Widget qview();
	}
    }

    public class QuestList extends SListBox<Quest, Widget> {
	public final List<Quest> quests = new ArrayList<Quest>();
	public final boolean showcond;
	private boolean loading = false;
	private final Comparator<Quest> comp = new Comparator<Quest>() {
	    public int compare(Quest a, Quest b) {
		return(b.mtime - a.mtime);
	    }
	};

	public QuestList(Coord sz, boolean showcond) {
	    super(sz, attrf.height() + UI.scale(2));
	    this.showcond = showcond;
	}

	protected List<Quest> items() {return(quests);}
	protected Widget makeitem(Quest q, int idx, Coord sz) {return(new Item(sz, q));}

	public void tick(double dt) {
	    if(loading) {
		loading = false;
		Collections.sort(quests, comp);
	    }
	    super.tick(dt);
	}

	public class Item extends Widget {
	    public final Quest q;
	    private final IconText nm;
	    private final TextItem conds;
	    private Object dres, dtit;

	    public Item(Coord sz, Quest q) {
		super(sz);
		this.q = q;
		if(showcond) {
		    this.conds = new TextItem(UI.scale(30)) {
			int sd = 01;

			public String text() {
			    return(String.format("%d/%d", sd = q.ndcond, q.ncond));
			}

			public boolean valid(String text) {
			    return(sd == q.ndcond);
			}
		    };
		    add(this.conds, sz.x - this.conds.sz.x, (sz.y - this.conds.sz.y) / 2);
		    sz = Coord.of(this.conds.c.x, sz.y);
		} else {
		    conds = null;
		}
		this.nm = new IconText(sz) {
			protected BufferedImage img() {return(q.res.get().flayer(Resource.imgc).img);}
			protected String text() {return(q.title());}

			protected void drawtext(GOut g) {
			    if(q.done == Quest.QST_DISABLED)
				g.chcolor(255, 128, 0, 255);
			    super.drawtext(g);
			    g.chcolor();
			}
		    };
		add(this.nm, Coord.z);
	    }

	    public void draw(GOut g) {
		if((q.res != dres) || (q.title != dtit)) {
		    nm.invalidate();
		    dres = q.res;
		    dtit = q.title;
		}
		super.draw(g);
	    }

	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.propagate(this) || super.mousedown(ev))
		    return(true);
		if(ev.b == 1) {
                    Integer selected=(QuestWnd.this.quest!=null && q.id==QuestWnd.this.quest.questid())?null:q.id;
                    getparent(GameUI.class).questHelper.manualSelection(selected);
                    QuestWnd.this.wdgmsg("qsel",selected);
		    return(true);
		}
		return(false);
	    }
	}

	protected void drawslot(GOut g, Quest q, int idx, Area area) {
	    super.drawslot(g, q, idx, area);
	    if((quest != null) && (quest.questid() == q.id))
		drawsel(g, q, idx, area);
	}

	protected boolean unselect(int button) {
            if(button==1){getparent(GameUI.class).questHelper.manualSelection((Integer)null);QuestWnd.this.wdgmsg("qsel",(Object)null);}
	    return(true);
	}

	public Quest get(int id) {
	    for(Quest q : quests) {
		if(q.id == id)
		    return(q);
	    }
	    return(null);
	}

	public void add(Quest q) {
	    quests.add(q);
	}

	public Quest remove(int id) {
	    for(Iterator<Quest> i = quests.iterator(); i.hasNext();) {
		Quest q = i.next();
		if(q.id == id) {
		    i.remove();
		    return(q);
		}
	    }
	    return(null);
	}

	public void remove(Quest q) {
	    quests.remove(q);
	}
    }

    public QuestWnd() {
	Widget prev;

	prev = add(CharWnd.settip(hearthborn.ui.UiTheme.heading(hearthborn.L10n.text("label", "Quest Log")), "gfx/hud/chr/tips/quests"), new Coord(0, 0));
	questbox = add(new Widget(new Coord(attrw + UI.scale(120), height + UI.scale(70))) {
		public void draw(GOut g) {
		    g.chcolor(0, 0, 0, 128);
		    g.frect(Coord.z, sz);
		    g.chcolor();
		    super.draw(g);
		}

		public void cdestroy(Widget w) {
		    if(w == quest)
			quest = null;
		}
	    }, prev.pos("bl").adds(5, 0).add(wbox.btloff()));
	Frame.around(this, Collections.singletonList(questbox));
	Tabs lists = new Tabs(prev.pos("bl").x(width + UI.scale(125)), Coord.z, this);
	int listw = attrw + UI.scale(60);
	Tabs.Tab cqst = lists.add();
	{
	    this.cqst = cqst.add(new QuestList(Coord.of(listw, height + UI.scale(70) - Button.hs - UI.scale(5)), true), wbox.btloff());
	    cframe = Frame.around(cqst, Collections.singletonList(this.cqst));
	}
	Tabs.Tab dqst = lists.add();
	{
	    this.dqst = dqst.add(new QuestList(Coord.of(listw, height + UI.scale(70) - Button.hs - UI.scale(5)), false), wbox.btloff());
	    dframe = Frame.around(dqst, Collections.singletonList(this.dqst));
	}
	lists.pack();
	this.lists = lists;
	addhlp(lists.c.add(0, lists.sz.y + UI.scale(5)), UI.scale(5), lists.sz.x,
		     current = lists.new TabButton(0, "Current",   cqst),
		     completed = lists.new TabButton(0, "Completed", dqst));
	pack();
    }

    private Tabs lists;
    private Frame cframe, dframe;
    private Button current, completed;

    /** Stretch the quest lists, their frames and tab buttons so the journal fills a wider tab area. */
    public void fitWidth(int width) {
	int extra = width - sz.x;
	if(extra <= 0)
	    return;
	Coord grow = Coord.of(extra, 0);
	cqst.resize(cqst.sz.add(grow));
	dqst.resize(dqst.sz.add(grow));
	cframe.resize(cframe.sz.add(grow));
	dframe.resize(dframe.sz.add(grow));
	lists.resize(lists.sz.add(grow));
	int pad = UI.scale(5), each = (lists.sz.x - pad) / 2;
	current.resizew(each);
	completed.resizew(each);
	completed.move(Coord.of(lists.c.x + lists.sz.x - completed.sz.x, completed.c.y));
	resize(sz.add(grow));
    }

    public void addchild(Widget child, Object... args) {
	String place = STR.opt(args[0]).map(String::intern).or(null);
	if(place == "quest") {
	    this.quest = (Quest.Info)child;
	    questbox.add(child, Coord.z);
	    getparent(GameUI.class).addchild(this.quest.qview(), "qq");
	} else {
	    super.addchild(child, args);
	}
    }

    public void uimsg(String nm, Object... args) {
	if(nm == "quests") {
	    for(int i = 0; i < args.length; i++) {
		Object[] qd = OBJS.of(args[i]);
		int a = 0;
		int id = INT.of(qd, a++);
		if(qd.length == a) {
		    cqst.remove(id);
		    dqst.remove(id);
                    hearthborn.ui.QuestForage.questEnded(getparent(GameUI.class), id);
		    continue;
		}

		QuestList cl = cqst;
		Quest q = cqst.get(id);
		if(q == null)
		    q = (cl = dqst).get(id);
		if(q == null) {
		    cl = null;
		    q = new Quest(id);
		}

		q.res = ui.sess.getresv(qd[a++]);
		q.done = INT.of(qd, a++);
		q.mtime = INT.of(qd, a++);
		q.title = STR.is(qd, a) ? STR.of(qd, a++) : null;
		q.ncond = INT.of(qd, a++);
		q.ndcond = INT.of(qd, a++);
		QuestList nl = ((q.done == Quest.QST_PEND) || (q.done == Quest.QST_DISABLED)) ? cqst : dqst;
		if(nl != cl) {
		    if(cl != null)
			cl.remove(q);
		    nl.add(q);
		}
                if(nl == dqst) hearthborn.ui.QuestForage.questEnded(getparent(GameUI.class), id);
		if((cl == cqst) && (nl == dqst))
		    q.done(getparent(GameUI.class));
		nl.loading = true;
	    }
	} else {
	    super.uimsg(nm, args);
	}
    }
}
