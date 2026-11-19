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
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;

public class Fightsess extends Widget {
    private static final Coord off = new Coord(UI.scale(32), UI.scale(32));
    public static final Tex cdframe = Resource.loadtex("gfx/hud/combat/cool");
    public static final Tex actframe = Buff.frame;
    public static final Coord actframeo = Buff.imgoff;
    public static final Tex indframe = Resource.loadtex("gfx/hud/combat/indframe");
    public static final Coord indframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex indbframe = Resource.loadtex("gfx/hud/combat/indbframe");
    public static final Coord indbframeo = (indframe.sz().sub(off)).div(2);
    public static final Tex useframe = Resource.loadtex("gfx/hud/combat/lastframe");
    public static final Coord useframeo = (useframe.sz().sub(off)).div(2);
    public static final int actpitch = UI.scale(50);
    public final Action[] actions;
    public int use = -1, useb = -1;
    public static boolean active;
    public Coord pcc;
    /** Cooldown circle and initiative, in place of the day and night dial. */
    public Coord deck = Coord.z;
    /** Attack buttons. Fixed above the hotbar, does not follow the fighter. */
    public Coord moves = Coord.z;
    public int pho;
    private Fightview fv;

    public static class Action {
	public final Indir<Resource> res;
	public double cs, ct;

	public Action(Indir<Resource> res) {
	    this.res = res;
	}
    }

    @RName("fsess")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    int nact = Utils.iv(args[0]);
	    return(new Fightsess(nact));
	}
    }

    @SuppressWarnings("unchecked")
    public Fightsess(int nact) {
	pho = -UI.scale(40);
	this.actions = new Action[nact];
    }

    protected void added() {
	active = true;
	hearthborn.ui.ServerStats.covered = true;
	fv = parent.getparent(GameUI.class).fv;
	presize();
    }

    public void presize() {
	resize(parent.sz);
	pcc = sz.div(2);
    }

    private void updatepos() {
	GameUI gui = getparent(GameUI.class);
	int half = (actpitch * 2) + (actpitch / 2);
	int x = sz.x / 2;
	int y = UI.scale(2);
	if(gui != null) {
	    Cal cal = gui.findchild(Cal.class);
	    if(cal != null) {
		Coord at = cal.rootpos().sub(rootpos());
		x = at.x + cal.sz.x / 2;
		y = at.y + cal.sz.y / 2;
	    }
	}
	x = Math.max(half, Math.min(sz.x - half, x));
	deck = new Coord(x, y).add(hearthborn.combat.CombatPrefs.alt() ? hearthborn.combat.CombatPrefs.shift() : Coord.z);
	int rows = Math.max(1, (actions.length + 4) / 5);
	int below = UI.scale(125) + ((rows - 1) * actpitch) + (actpitch / 2) + UI.scale(6) + UI.scale(36);
	int limit = sz.y - UI.scale(4);
	if(gui != null && gui.beltwdg != null && gui.beltwdg.visible())
	    limit = Math.min(limit, gui.beltwdg.c.y - UI.scale(6));
	if(gui != null && gui.chat != null && gui.chat.visible() && !gui.chat.detached())
	    limit = Math.min(limit, gui.chat.c.y - UI.scale(6));
	int my = limit - below;
	if(my < UI.scale(4))
	    my = UI.scale(4);
	moves = new Coord(Math.max(half, Math.min(sz.x - half, sz.x / 2)), my);
	MapView map;
	Gob pl;
	if((gui == null) || ((map = gui.map) == null) || ((pl = map.player()) == null))
	    return;
	Coord3f raw = pl.placed.getc();
	if(raw == null)
	    return;
	Coord3f at = map.screenxf(raw);
	if(at == null)
	    return;
	pcc = at.round2();
	if(!hearthborn.combat.CombatPrefs.alt()) {
	    deck = pcc.add(0, UI.scale(67));
	    moves = pcc;
	}
	Coord3f up = map.screenxf(raw.add(0, 0, UI.scale(20)));
	if(up != null)
	    pho = (int)(up.round2().sub(pcc).y) - UI.scale(20);
    }

    private static class Effect implements RenderTree.Node {
	Sprite spr;
	RenderTree.Slot slot;
	boolean used = true;

	Effect(Sprite spr) {this.spr = spr;}

	public void added(RenderTree.Slot slot) {
	    slot.add(spr);
	}
    }

    private static final Resource tgtfx = Resource.local().loadwait("gfx/hud/combat/trgtarw");
    private final Collection<Effect> curfx = new ArrayList<>();

    private Effect fxon(long gobid, Resource fx, Effect cur) {
	MapView map = getparent(GameUI.class).map;
	Gob gob = ui.sess.glob.oc.getgob(gobid);
	if((map == null) || (gob == null))
	    return(null);
	Pipe.Op place;
	try {
	    place = gob.placed.curplace();
	} catch(Loading l) {
	    return(null);
	}
	if((cur == null) || (cur.slot == null)) {
	    try {
		cur = new Effect(Sprite.create(new Sprite.UIOwner(this), fx, Message.nil));
		cur.slot = map.basic.add(cur.spr, place);
	    } catch(Loading l) {
		return(null);
	    }
	    curfx.add(cur);
	} else {
	    cur.slot.cstate(place);
	}
	cur.used = true;
	return(cur);
    }

    public void tick(double dt) {
	for(Iterator<Effect> i = curfx.iterator(); i.hasNext();) {
	    Effect fx = i.next();
	    if(!fx.used) {
		if(fx.slot != null) {
		    fx.slot.remove();
		    fx.slot = null;
		}
		i.remove();
	    } else {
		fx.used = false;
		fx.spr.tick(dt);
	    }
	}
	hearthborn.combat.AutoCombat.tick(this, fv);
    }

    public void destroy() {
	for(Effect fx : curfx) {
	    if(fx.slot != null)
		fx.slot.remove();
	}
	curfx.clear();
	active = false;
	hearthborn.ui.ServerStats.covered = false;
	hearthborn.combat.AutoCombat.ended();
        hearthborn.combat.BowCombat.ended(this);
	if(hearthborn.combat.CombatPrefs.clearAll())
	    hearthborn.combat.FighterMark.clearAll();
	else if(hearthborn.combat.CombatPrefs.clearPlayer())
	    hearthborn.combat.FighterMark.clearPlayer();
	super.destroy();
    }

    private static final Text.Furnace ipf = new PUtils.BlurFurn(new Text.Foundry(Text.serif, 18, new Color(128, 128, 255)).aa(true), 1, 1, new Color(48, 48, 96));
    private final Indir<Text> ip =  Utils.transform(() -> fv.current.ip , v -> ipf.render(Integer.toString(v)));
    private final Indir<Text> oip = Utils.transform(() -> fv.current.oip, v -> ipf.render(Integer.toString(v)));
    private static final String[] OPEN_NAME = {
	"paginae/atk/offbalance",
	"paginae/atk/reeling",
	"paginae/atk/dizzy",
	"paginae/atk/cornered",
    };
    private static final Color[] OPEN_COL = {
	new Color(81, 165, 56),
	new Color(210, 210, 64),
	new Color(39, 82, 191),
	new Color(192, 28, 28),
    };
    private static final Text.Foundry openf = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 12);
    private final Map<Buff, Coord> slot = new IdentityHashMap<>();
    private final Map<Buff, Tex> onum = new IdentityHashMap<>();
    private final Map<Buff, Integer> oval = new IdentityHashMap<>();
    private Coord lastc1, lastc2;
    private Coord lasts1, lasts2;

    private static final Coord usec1 = UI.scale(new Coord(-65, 67));
    private static final Coord usec2 = UI.scale(new Coord(65, 67));

    private static Coord actc(int i) {
	int rl = 5;
	return(new Coord((actpitch * (i % rl)) - (((rl - 1) * actpitch) / 2), UI.scale(125) + ((i / rl) * actpitch)));
    }

    private Coord coolc() {
	return(deck);
    }

    private static int openix(Buff buff) {
	Resource res;
	try {
	    res = buff.res.get();
	} catch(Loading l) {
	    return(-1);
	}
	String name = res.name;
	for(int i = 0; i < OPEN_NAME.length; i++) {
	    if(OPEN_NAME[i].equals(name))
		return(i);
	}
	return(-1);
    }

    private void split(Bufflist list, List<Buff> maneuvers, List<Buff> openings) {
	maneuvers.clear();
	openings.clear();
	if(list == null)
	    return;
	for(Buff buff : list.children(Buff.class)) {
	    if(openix(buff) >= 0)
		openings.add(buff);
	    else
		maneuvers.add(buff);
	}
    }

    /** Top of the icon row, directly under the cooldown circle. */
    private int rowTop() {
	return(coolc().y + (cdframe.sz().y / 2) + UI.scale(4));
    }

    /**
     * One row under the cooldown circle.
     * Mine, outward: last move, maneuver, openings.
     * Opponent, outward: maneuver, last move, openings.
     */
    private int placeBuffs(int edge, boolean left, List<Buff> buffs) {
	int gap = UI.scale(4);
	int y = rowTop();
	for(Buff buff : buffs) {
	    int x;
	    if(left) {
		x = edge - gap - buff.sz.x;
		edge = x;
	    } else {
		x = edge + gap;
		edge = x + buff.sz.x;
	    }
	    slot.put(buff, new Coord(x, y + (Buff.cframe.sz().y - buff.sz.y) / 2));
	}
	return(edge);
    }

    private int placeUse(int edge, boolean left, Indir<Resource> act, boolean mine) {
	if(act == null)
	    return(edge);
	Tex ut;
	try {
	    ut = act.get().flayer(Resource.imgc).tex();
	} catch(Loading l) {
	    return(edge);
	}
	int gap = UI.scale(4);
	int x = left ? edge - gap - ut.sz().x : edge + gap;
	int y = rowTop() + (Buff.cframe.sz().y - ut.sz().y) / 2;
	Coord c = new Coord(x, y);
	if(mine) {
	    lastc1 = c;
	    lasts1 = ut.sz();
	} else {
	    lastc2 = c;
	    lasts2 = ut.sz();
	}
	if(left)
	    return(x - Math.max(0, useframeo.x));
	return(x + Math.max(ut.sz().x, useframe.sz().x - useframeo.x));
    }

    /** Old combat UI: openings and maneuvers in columns beside the fighter, last move beside the circle. */
    private void placeClassic() {
	if(pcc == null)
	    return;
	for(Buff buff : fv.buffs.children(Buff.class))
	    slot.put(buff, pcc.add(-buff.c.x - Buff.cframe.sz().x - UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y));
	placeClassicUse(true, fv.lastact);
	if(fv.current == null)
	    return;
	for(Buff buff : fv.current.buffs.children(Buff.class))
	    slot.put(buff, pcc.add(buff.c.x + UI.scale(20), buff.c.y + pho - Buff.cframe.sz().y));
	placeClassicUse(false, fv.current.lastact);
    }

    private void placeClassicUse(boolean mine, Indir<Resource> act) {
	if(act == null)
	    return;
	Tex ut;
	try {
	    ut = act.get().flayer(Resource.imgc).tex();
	} catch(Loading l) {
	    return;
	}
	Coord c = pcc.add(mine ? usec1 : usec2).sub(ut.sz().div(2));
	if(mine) {
	    lastc1 = c;
	    lasts1 = ut.sz();
	} else {
	    lastc2 = c;
	    lasts2 = ut.sz();
	}
    }

    private void place() {
	slot.clear();
	lastc1 = lastc2 = null;
	lasts1 = lasts2 = null;
	if(!hearthborn.combat.CombatPrefs.alt()) {
	    placeClassic();
	    return;
	}
	Coord mid = coolc();
	List<Buff> maneuvers = new ArrayList<>();
	List<Buff> openings = new ArrayList<>();
	split(fv.buffs, maneuvers, openings);
	int edge = mid.x - UI.scale(6);
	edge = placeUse(edge, true, fv.lastact, true);
	edge = placeBuffs(edge, true, maneuvers);
	placeBuffs(edge, true, openings);
	if(fv.current != null) {
	    split(fv.current.buffs, maneuvers, openings);
	    edge = mid.x + UI.scale(6);
	    edge = placeBuffs(edge, false, maneuvers);
	    edge = placeUse(edge, false, fv.current.lastact, false);
	    placeBuffs(edge, false, openings);
	}
    }

    private void drawBuff(GOut g, Buff buff) {
	if(slot.get(buff) == null)
	    return;
	if((openix(buff) >= 0) && hearthborn.combat.CombatPrefs.simple())
	    drawOpening(g, buff);
	else
	    buff.draw(g.reclip(slot.get(buff), buff.sz));
    }

    private void drawOpening(GOut g, Buff buff) {
	Coord c = slot.get(buff);
	if(c == null)
	    return;
	int ix = openix(buff);
	if(ix < 0)
	    return;
	g.chcolor(OPEN_COL[ix]);
	g.frect(c.add(1, 1), buff.sz.sub(2, 2));
	g.chcolor(255, 255, 255, 210);
	g.rect(c, buff.sz);
	g.chcolor();
	Double meter = buff.ameter();
	if(meter == null)
	    return;
	int n = (int)Math.floor(meter * 100.0);
	Integer prev = oval.get(buff);
	Tex num = onum.get(buff);
	if((prev == null) || (prev != n) || (num == null)) {
	    num = new TexI(Utils.outline2(openf.render(Integer.toString(n), Color.WHITE).img, Color.BLACK));
	    oval.put(buff, n);
	    onum.put(buff, num);
	}
	g.aimage(num, c.add(buff.sz.div(2)), 0.5, 0.5);
    }

    private void drawuse(GOut g, Coord useul, Indir<Resource> act, double lastuse, double now) {
	if((useul == null) || (act == null))
	    return;
	try {
	    Tex ut = act.get().flayer(Resource.imgc).tex();
	    g.image(ut, useul);
	    g.image(useframe, useul.sub(useframeo));
	    double a = now - lastuse;
	    if(a < 1) {
		Coord off = new Coord((int)(a * ut.sz().x / 2), (int)(a * ut.sz().y / 2));
		g.chcolor(255, 255, 255, (int)(255 * (1 - a)));
		g.image(ut, useul.sub(off), ut.sz().add(off.mul(2)));
		g.chcolor();
	    }
	} catch(Loading l) {
	}
    }
    private final Tex[] keytex = new Tex[10];
    private final String[] keyname = new String[10];
    private UI.Grab drag;
    private Coord dragAt, dragOrg;
    private Indir<Resource> lastact1 = null, lastact2 = null;
    private Text lastacttip1 = null, lastacttip2 = null;
    private Effect curtgtfx;
    public void draw(GOut g) {
	updatepos();
	place();
	double now = Utils.rtime();

	for(Buff buff : fv.buffs.children(Buff.class))
	    drawBuff(g, buff);
	if(fv.current != null) {
	    for(Buff buff : fv.current.buffs.children(Buff.class))
		drawBuff(g, buff);
	    try {
		int lift = UI.scale(12);
		int side = cdframe.sz().x / 2 + UI.scale(4);
		g.aimage(ip.get().tex(), coolc().add(-side, -lift), 1, 0.5);
		g.aimage(oip.get().tex(), coolc().add(side, -lift), 0, 0.5);
	    } catch(Loading l) {
	    }
	    if(fv.lsrel.size() > (hearthborn.combat.CombatPrefs.mark() ? 0 : 1))
		curtgtfx = fxon(fv.current.gobid, tgtfx, curtgtfx);
	}

	{
	    Coord cdc = coolc();
	    if(now < fv.atkct) {
		double a = (now - fv.atkcs) / (fv.atkct - fv.atkcs);
		g.chcolor(255, 0, 128, 224);
		g.fellipse(cdc, UI.scale(new Coord(24, 24)), Math.PI / 2 - (Math.PI * 2 * Math.min(1.0 - a, 1.0)), Math.PI / 2);
		g.chcolor();
	    }
	    g.image(cdframe, cdc.sub(cdframe.sz().div(2)));
	}
	if(fv.lastact != this.lastact1) {
	    this.lastact1 = fv.lastact;
	    this.lastacttip1 = null;
	}
	drawuse(g, lastc1, fv.lastact, fv.lastuse, now);
	if(fv.current != null) {
	    if(fv.current.lastact != this.lastact2) {
		this.lastact2 = fv.current.lastact;
		this.lastacttip2 = null;
	    }
	    drawuse(g, lastc2, fv.current.lastact, fv.current.lastuse, now);
	}
	for(int i = 0; i < actions.length; i++) {
	    Coord ca = moves.add(actc(i));
	    Action act = actions[i];
	    try {
		if(act != null) {
		    Resource res = act.res.get();
		    Tex img = res.flayer(Resource.imgc).tex();
		    Coord ic = ca.sub(img.sz().div(2));
		    g.image(img, ic);
		    if(now < act.ct) {
			double a = (now - act.cs) / (act.ct - act.cs);
			g.chcolor(0, 0, 0, 128);
			g.prect(ca, ic.sub(ca), ic.add(img.sz()).sub(ca), (1.0 - a) * Math.PI * 2);
			g.chcolor();
		    }
		    if(hearthborn.combat.CombatPrefs.keys() && (i < kb_acts.length) && (i < keytex.length)) {
			String name = kb_acts[i].key().name();
			if(!name.equals(keyname[i])) {
			    keyname[i] = name;
			    keytex[i] = new TexI(Utils.outline2(openf.render(name, Color.WHITE).img, Color.BLACK));
			}
			if(keytex[i] != null)
			    g.aimage(keytex[i], ic.add(img.sz()), 1, 1);
		    }
		    if(i == use) {
			g.image(indframe, ic.sub(indframeo));
		    } else if(i == useb) {
			g.image(indbframe, ic.sub(indbframeo));
		    } else {
			g.image(actframe, ic.sub(actframeo));
		    }
		}
	    } catch(Loading l) {}
	}
	hearthborn.combat.AutoCombat.draw(g, this);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if(hearthborn.combat.AutoCombat.click(this, ev))
	    return(true);
	if(hearthborn.combat.CombatPrefs.drag() && hearthborn.combat.CombatPrefs.alt() && (ev.b == 1)
	    && (ev.c.dist(coolc()) <= (cdframe.sz().x / 2) + UI.scale(6))) {
	    drag = ui.grabmouse(this);
	    dragAt = ev.c;
	    dragOrg = hearthborn.combat.CombatPrefs.shift();
	    return(true);
	}
	return(false);
    }

    public void mousemove(MouseMoveEvent ev) {
	if(drag != null)
	    hearthborn.combat.CombatPrefs.shift(dragOrg.add(ev.c.sub(dragAt)));
    }

    public boolean mouseup(MouseUpEvent ev) {
	if((drag == null) || (ev.b != 1))
	    return(false);
	drag.remove();
	drag = null;
	return(true);
    }

    private Widget prevtt = null;
    private Text acttip = null;
    public static final String[] keytips = {"1", "2", "3", "4", "5", "Shift+1", "Shift+2", "Shift+3", "Shift+4", "Shift+5"};
    public Object tooltip(Coord c, Widget prev) {
	String auto = hearthborn.combat.AutoCombat.tip(c);
	if(auto != null)
	    return(auto);
	place();
	for(Buff buff : fv.buffs.children(Buff.class)) {
	    Coord dc = slot.get(buff);
	    if((dc != null) && c.isect(dc, buff.sz)) {
		Object ret = buff.tooltip(c.sub(dc), prevtt);
		if(ret != null) {
		    prevtt = buff;
		    return(ret);
		}
	    }
	}
	if(fv.current != null) {
	    for(Buff buff : fv.current.buffs.children(Buff.class)) {
		Coord dc = slot.get(buff);
		if((dc != null) && c.isect(dc, buff.sz)) {
		    Object ret = buff.tooltip(c.sub(dc), prevtt);
		    if(ret != null) {
			prevtt = buff;
			return(ret);
		    }
		}
	    }
	}
	final int rl = 5;
	for(int i = 0; i < actions.length; i++) {
	    Coord ca = moves.add(actc(i));
	    Indir<Resource> act = (actions[i] == null) ? null : actions[i].res;
	    if(act != null) {
		Tex img = act.get().flayer(Resource.imgc).tex();
		ca = ca.sub(img.sz().div(2));
		if(c.isect(ca, img.sz())) {
		    String tip = act.get().flayer(Resource.tooltip).t;
		    if(kb_acts[i].key() != KeyMatch.nil)
			tip += " ($b{$col[255,128,0]{" + kb_acts[i].key().name() + "}})";
		    if((acttip == null) || !acttip.text.equals(tip))
			acttip = RichText.render(tip, -1);
		    return(acttip);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact1;
	    if((lastact != null) && (lastc1 != null) && (lasts1 != null)) {
		if(c.isect(lastc1, lasts1)) {
		    if(lastacttip1 == null)
			lastacttip1 = Text.render(lastact.get().flayer(Resource.tooltip).t);
		    return(lastacttip1);
		}
	    }
	}
	{
	    Indir<Resource> lastact = this.lastact2;
	    if((lastact != null) && (lastc2 != null) && (lasts2 != null)) {
		if(c.isect(lastc2, lasts2)) {
		    if(lastacttip2 == null)
			lastacttip2 = Text.render(lastact.get().flayer(Resource.tooltip).t);
		    return(lastacttip2);
		}
	    }
	}
	return(null);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "act") {
	    int n = Utils.iv(args[0]);
	    if(args.length > 1) {
		Indir<Resource> res = ui.sess.getresv(args[1]);
		actions[n] = new Action(res);
	    } else {
		actions[n] = null;
	    }
	} else if(msg == "acool") {
	    int n = Utils.iv(args[0]);
	    double now = Utils.rtime();
	    actions[n].cs = now;
	    actions[n].ct = now + (Utils.dv(args[1]) * 0.06);
	} else if(msg == "use") {
	    this.use = Utils.iv(args[0]);
	    this.useb = (args.length > 1) ? Utils.iv(args[1]) : -1;
	} else if(msg == "used") {
	} else {
	    super.uimsg(msg, args);
	}
    }

    public static final KeyBinding[] kb_acts = {
	KeyBinding.get("fgt/0", KeyMatch.forcode(KeyEvent.VK_1, 0)),
	KeyBinding.get("fgt/1", KeyMatch.forcode(KeyEvent.VK_2, 0)),
	KeyBinding.get("fgt/2", KeyMatch.forcode(KeyEvent.VK_3, 0)),
	KeyBinding.get("fgt/3", KeyMatch.forcode(KeyEvent.VK_4, 0)),
	KeyBinding.get("fgt/4", KeyMatch.forcode(KeyEvent.VK_5, 0)),
	KeyBinding.get("fgt/5", KeyMatch.forcode(KeyEvent.VK_Q, 0)),
	KeyBinding.get("fgt/6", KeyMatch.forcode(KeyEvent.VK_W, 0)),
	KeyBinding.get("fgt/7", KeyMatch.forcode(KeyEvent.VK_E, 0)),
	KeyBinding.get("fgt/8", KeyMatch.forcode(KeyEvent.VK_R, 0)),
	KeyBinding.get("fgt/9", KeyMatch.forcode(KeyEvent.VK_T, 0)),
    };
    public static final KeyBinding kb_relcycle =  KeyBinding.get("fgt-cycle", KeyMatch.forcode(KeyEvent.VK_TAB, KeyMatch.C), KeyMatch.S);

    /* XXX: This is a bit ugly, but release message do need to be
     * properly sequenced with use messages in some way. */
    private class Release implements Runnable {
	final int n;

	Release(int n) {
	    this.n = n;
	    Environment env = ui.getenv();
	    Render out = env.render();
	    out.fence(this);
	    env.submit(out);
	}


	public void run() {
	    wdgmsg("rel", n);
	}
    }

    private UI.Grab holdgrab = null;
    private int held = -1;
    public boolean globtype(GlobKeyEvent ev) {
	// ev = new KeyEvent((java.awt.Component)ev.getSource(), ev.getID(), ev.getWhen(), ev.getModifiersEx(), ev.getKeyCode(), ev.getKeyChar(), ev.getKeyLocation());
	{
	    int n = -1;
	    for(int i = 0; i < kb_acts.length; i++) {
		if(kb_acts[i].key().match(ev)) {
		    n = i;
		    break;
		}
	    }
	    int fn = n;
	    if((n >= 0) && (n < actions.length)) {
		MapView map = getparent(GameUI.class).map;
		Coord mvc = map.rootxlate(ui.mc);
		if(held >= 0) {
		    new Release(held);
		    held = -1;
		}
		if(mvc.isect(Coord.z, map.sz)) {
		    map.new Maptest(mvc) {
			    protected void hit(Coord pc, Coord2d mc) {
				wdgmsg("use", fn, 1, ui.modflags(), mc.floor(OCache.posres));
			    }

			    protected void nohit(Coord pc) {
				wdgmsg("use", fn, 1, ui.modflags());
			    }
			}.run();
		}
		if(holdgrab == null)
		    holdgrab = ui.grabkeys(this);
		held = n;
		return(true);
	    }
	}
	if(kb_relcycle.key().match(ev, KeyMatch.S)) {
	    if((ev.mods & KeyMatch.S) == 0) {
		Fightview.Relation cur = fv.current;
		if(cur != null) {
		    fv.lsrel.remove(cur);
		    fv.lsrel.addLast(cur);
		}
	    } else {
		Fightview.Relation last = fv.lsrel.getLast();
		if(last != null) {
		    fv.lsrel.remove(last);
		    fv.lsrel.addFirst(last);
		}
	    }
	    fv.wdgmsg("bump", (int)fv.lsrel.get(0).gobid);
	    return(true);
	}
	return(super.globtype(ev));
    }

    public boolean keydown(KeyDownEvent ev) {
	return(false);
    }

    public boolean keyup(KeyUpEvent ev) {
	if(ev.grabbed && (kb_acts[held].key().match(ev, KeyMatch.MODS))) {
	    MapView map = getparent(GameUI.class).map;
	    new Release(held);
	    holdgrab.remove();
	    holdgrab = null;
	    held = -1;
	    return(true);
	}
	return(false);
    }
}
