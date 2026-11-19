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
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import haven.render.Location;
import static haven.Inventory.invsq;
import static haven.PType.*;

public class GameUI extends ConsoleHost implements Console.Directory, UI.Notice.Handler {
    private static final int brpw = UI.scale(142);
    public final String chrid, genus;
    public final long plid;
    private final Hidepanel ulpanel, umpanel, urpanel, brpanel;
    public Widget portrait;
    public MenuGrid menu;
    public final hearthborn.meteor.StarFall starFall;
    private final hearthborn.ui.NearbySiegeCheck nearbySiegeCheck=new hearthborn.ui.NearbySiegeCheck();
    private final hearthborn.ui.LoginSiegeCheck loginSiegeCheck=new hearthborn.ui.LoginSiegeCheck();
    private final hearthborn.ui.IndoorCamera indoorCamera=new hearthborn.ui.IndoorCamera();
    public MapView map;
    public boolean indoors(){return indoorCamera!=null&&indoorCamera.indoors(this);}
    public boolean prepareCamera(MapView view){return view!=map||indoorCamera==null||indoorCamera.prepareFrame(this);}
    public GobIcon.Settings iconconf;
    public Fightview fv;
    public final hearthborn.ui.CombatLayout compactLayout=new hearthborn.ui.CombatLayout(this);
    public boolean compactUI(){return compactLayout!=null&&compactLayout.active();}
    private List<Widget> meters = new LinkedList<Widget>();
    private Text lastmsg;
    private String noticeText;
    private boolean noticeError;
    private double msgtime;
    private Window invwnd, equwnd, makewnd, srchwnd, iconwnd;
    private Coord makewndc = Utils.getprefc("makewndc", new Coord(400, 200));
    public Inventory maininv;
    public CharWnd chrwdg;
    public MapWnd mapfile;
    public hearthborn.mining.Minesweeper minesweeper;
    public hearthborn.cheese.CheeseChart cheeseChart;
    private boolean mapAnchored;
    private Widget qqview;
    public final hearthborn.ui.QuestHelper questHelper;
    public BuddyWnd buddies;
    private final Zergwnd zerg;
    public HelpWnd help;
    public OptWnd opts;
    public Collection<DraggedItem> hand = new LinkedList<DraggedItem>();
    public WItem vhand;
    public ChatUI chat;
    public ChatUI.Channel syslog;
    public Progress prog = null;
    private boolean afk = false;
    public BeltSlot[] belt = new BeltSlot[256];
    public Belt beltwdg;
    public final Map<Integer, String> polowners = new HashMap<Integer, String>();
    public Bufflist buffs;

    public static abstract class BeltSlot {
	public final int idx;

	public BeltSlot(int idx) {
	    this.idx = idx;
	}

	public abstract void draw(GOut g);
	public abstract void use(MenuGrid.Interaction iact);
    }

    private static final OwnerContext.ClassResolver<ResBeltSlot> beltctxr = new OwnerContext.ClassResolver<ResBeltSlot>()
	.add(GameUI.class, slot -> slot.wdg())
	.add(Glob.class, slot -> slot.wdg().ui.sess.glob)
	.add(Session.class, slot -> slot.wdg().ui.sess);
    public class ResBeltSlot extends BeltSlot implements GSprite.Owner, RandomSource {
	public final ResData rdt;

	public ResBeltSlot(int idx, ResData rdt) {
	    super(idx);
	    this.rdt = rdt;
	}

	private GSprite spr = null;
	public GSprite spr() {
	    GSprite ret = this.spr;
	    if(ret == null)
		ret = this.spr = GSprite.create(this, rdt.res.get(), new MessageBuf(rdt.sdt));
	    return(ret);
	}

	public void draw(GOut g) {
	    try {
		spr().draw(g);
	    } catch(Loading l) {}
	}

	public void use(MenuGrid.Interaction iact) {
	    Object[] args = {idx, iact.btn, iact.modflags};
	    if(iact.mc != null) {
		args = Utils.extend(args, iact.mc.floor(OCache.posres));
		if(iact.click != null)
		    args = Utils.extend(args, iact.click.clickargs());
	    }
	    GameUI.this.wdgmsg("belt", args);
	}

	public Resource getres() {return(rdt.res.get());}
	public Random mkrandoom() {return(new Random(System.identityHashCode(this)));}
	public <T> T context(Class<T> cl) {return(beltctxr.context(cl, this));}
	private GameUI wdg() {return(GameUI.this);}
    }

    public static class PagBeltSlot extends BeltSlot {
	public final MenuGrid.Pagina pag;

	public PagBeltSlot(int idx, MenuGrid.Pagina pag) {
	    super(idx);
	    this.pag = pag;
	}

	public void draw(GOut g) {
	    try {
		MenuGrid.PagButton btn = pag.button();
		btn.draw(g, btn.spr());
	    } catch(Loading l) {
	    }
	}

	public void use(MenuGrid.Interaction iact) {
	    try {
		pag.scm.use(pag.button(), iact, false);
	    } catch(Loading l) {
	    }
	}

	public static MenuGrid.Pagina resolve(MenuGrid scm, Indir<Resource> resid) {
	    Resource res = resid.get();
	    Resource.AButton act = res.layer(Resource.action);
	    /* XXX: This is quite a hack. Is there a better way? */
	    if((act != null) && (act.ad.length == 0))
		return(scm.paginafor(res.indir()));
	    return(scm.paginafor(resid));
	}
    }

    /* XXX: Remove me */
    public BeltSlot mkbeltslot(int idx, ResData rdt) {
	Resource res = rdt.res.get();
	Resource.AButton act = res.layer(Resource.action);
	if(act != null) {
	    if(act.ad.length == 0)
		return(new PagBeltSlot(idx, menu.paginafor(res.indir())));
	    return(new PagBeltSlot(idx, menu.paginafor(rdt.res)));
	}
	return(new ResBeltSlot(idx, rdt));
    }

    public abstract class Belt extends Widget implements DTarget, DropTarget {
	public Belt(Coord sz) {
	    super(sz);
	}

	public void act(int idx, MenuGrid.Interaction iact) {
	    if(belt[idx] != null)
		belt[idx].use(iact);
	}

	public void keyact(int slot) {
	    if(map != null) {
		BeltSlot si = belt[slot];
		Coord mvc = map.rootxlate(ui.mc);
		if(mvc.isect(Coord.z, map.sz)) {
		    map.new Hittest(mvc) {
			    protected void hit(Coord pc, Coord2d mc, ClickData inf) {
				act(slot, new MenuGrid.Interaction(1, ui.modflags(), mc, inf));
			    }
			    
			    protected void nohit(Coord pc) {
				act(slot, new MenuGrid.Interaction(1, ui.modflags()));
			    }
			}.run();
		}
	    }
	}

	public abstract int beltslot(Coord c);

	public boolean mousedown(MouseDownEvent ev) {
	    int slot = beltslot(ev.c);
	    if(slot != -1) {
		if(ev.b == 1)
		    act(slot, new MenuGrid.Interaction(1, ui.modflags()));
		if(ev.b == 3){
                    hearthborn.ui.LocalActionBelt.forget(GameUI.this,slot);
		    if(slot<144)GameUI.this.wdgmsg("setbelt", slot, null);
                }
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public boolean drop(Coord c, Coord ul) {
	    int slot = beltslot(c);
	    if(slot != -1 && slot<144) {
                hearthborn.ui.LocalActionBelt.forget(GameUI.this,slot);
		GameUI.this.wdgmsg("setbelt", slot, 0);
		return(true);
	    }
	    return(false);
	}

	public boolean iteminteract(Coord c, Coord ul) {return(false);}

	public boolean dropthing(Coord c, Object thing) {
	    int slot = beltslot(c);
	    if(slot != -1) {
		if(thing instanceof MenuGrid.Pagina) {
		    MenuGrid.Pagina pag = (MenuGrid.Pagina)thing;
		    try {
                        if(hearthborn.ui.LocalActionBelt.bind(GameUI.this,slot,pag))return true;
                        hearthborn.ui.LocalActionBelt.forget(GameUI.this,slot);
			if(pag.id instanceof Indir)
			    GameUI.this.wdgmsg("setbelt", slot, "res", pag.res().name);
			else
			    GameUI.this.wdgmsg("setbelt", slot, "pag", pag.id);
		    } catch(Loading l) {
		    }
		    return(true);
		}
	    }
	    return(false);
	}
    }
    
    @RName("gameui")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String chrid = (String)args[0];
	    long plid = Utils.uiv(args[1]);
	    String genus = "";
	    if(args.length > 2)
		genus = (String)args[2];
	    return(new GameUI(chrid, plid, genus));
	}
    }
    
    public Equipory equipment() { return equwnd == null ? null : equwnd.getchild(Equipory.class); }
    public void repeatCraft(MenuGrid.Pagina recipe) {
        if(makewnd!=null){makewnd.show(!makewnd.visible());if(makewnd.visible()){makewnd.raise();setfocus(makewnd);}}
        else if(recipe!=null)recipe.button().use(new MenuGrid.Interaction(1,0));
    }

    // Preserve the quest journal lower boundary without retaining corner-map widgets.
    private final int questBottomInset = Resource.loadtex("gfx/hud/blframe").sz().y
        + Resource.loadtex("gfx/hud/lbtn-bg").sz().y - UI.scale(33);
    private int questBottom(){
        int bottom=sz.y-questBottomInset;
        if(sidebarOnRight()&&brpanel!=null&&brpanel.visible())return Math.min(bottom,sz.y-brpanel.sz.y-UI.scale(8));
        if(!classicOnLeft()||brpanel==null||!brpanel.visible())return bottom;
        int span=brpanel.sz.y;
        hearthborn.ui.ActionMenuFrame frame=brpanel.getchild(hearthborn.ui.ActionMenuFrame.class);
        if(frame!=null&&!hearthborn.ui.Toolbars.enabled("actions"))span=frame.footprint().y;
        return Math.min(bottom,sz.y-span-UI.scale(8));
    }
    private final Coord menugridc;
    public GameUI(String chrid, long plid, String genus) {
	this.chrid = chrid;
	this.plid = plid;
	this.genus = genus;
        questHelper=new hearthborn.ui.QuestHelper(this);
	setcanfocus(true);
	setfocusctl(true);
	chat = add(new ChatUI());
	chat.show(Utils.getprefb("chatvis", true));
        chat.targetshow=chat.visible();
	beltwdg.raise();
        hearthborn.ui.Toolbars.install(this);
	brpanel = add(new Hidepanel("gui-br", null, new Coord(1, 1)));
	Utils.setprefb("gui-br-visible", true);
	if(!brpanel.tvis){brpanel.tvis=true;brpanel.show();brpanel.move(0);}
	ulpanel = add(new Hidepanel("gui-ul", null, new Coord(-1, -1)));
	umpanel = add(new Hidepanel("gui-um", null, new Coord( 0, -1)) {
            public Coord base() {
                Coord at=super.base();
                if(hearthborn.ui.PortraitSide.onRight())
                    at.x=Math.max(0,Math.min(at.x,ulpanel.c.x-sz.x-UI.scale(10)));
                else at.x=Math.min(Math.max(0,GameUI.this.sz.x-sz.x),Math.max(at.x,ulpanel.c.x+ulpanel.sz.x+UI.scale(10)));
                return at;
            }
        });
	urpanel = add(new Hidepanel("gui-ur", null, new Coord( 1, -1)));
        brpanel.add(new hearthborn.ui.ActionMenuFrame(MenuGrid.bgsz.mul(4,4).add(1,1)),Coord.z);
        menugridc = hearthborn.ui.ActionMenuFrame.inset();
	hearthborn.ui.HudPortrait avatar=ulpanel.add(new hearthborn.ui.HudPortrait(plid,()->togglewnd(equwnd),()->togglewnd(chrwdg)), UI.scale(10, 10));
	portrait=avatar;
	buffs = ulpanel.add(new hearthborn.ui.HudBuffs(), portrait.pos("bl").adds(0, 5));
	ulpanel.add(new hearthborn.ui.StatusMeters(this));
	umpanel.add(new Cal(), Coord.z);
        add(new hearthborn.ui.DewyTime(this), Coord.z);
        starFall=add(new hearthborn.meteor.StarFall(this), Coord.z);
        add(new hearthborn.ui.ServerStats(), Coord.z);
	syslog = chat.add(new ChatUI.Log("System"));
	chat.detach();
	opts = add(new OptWnd());
	opts.hide();
	zerg = add(new Zergwnd(), Utils.getprefc("wndc-zerg", UI.scale(new Coord(187, 50))));
	zerg.hide();
        buffs.move(portrait.pos("bl").adds(0,UI.scale(5)));
        // Kith & Kin is the heart over the portrait; its shortcut and those of equipment and the character sheet live in one relay.
        ((hearthborn.ui.HudPortrait)portrait).corner(new MenuCheckBox(hearthborn.ui.SystemButtonSkin.bareImages("rbtn-bud",hearthborn.ui.SystemCross.sourceSize()),null,"Kith & Kin")
            .state(() -> wndstate(zerg)).click(() -> togglewnd(zerg)));
        add(new hearthborn.ui.SystemHotkeys(new KeyBinding[]{kb_equ,kb_chr,kb_bud},
            new Runnable[]{() -> togglewnd(equwnd),() -> togglewnd(chrwdg),() -> togglewnd(zerg)}),Coord.z);
        int crossIcon = hearthborn.ui.SystemCross.sourceSize();
        add(new hearthborn.ui.SystemCross(
            crossControl(actionSearchButton(hearthborn.ui.SystemCross.sourceSize())),
            actionSystemCheckBox("lbtn-claim",crossIcon,kb_claim,"Display personal claims")
                .state(() -> map != null && map.visol("cplot")).set(a -> groundClaim("cplot",a)),
            actionSystemCheckBox("lbtn-vil",crossIcon,kb_vil,"Display village claims")
                .state(() -> map != null && map.visol("vlg")).set(a -> groundClaim("vlg",a)),
            actionSystemCheckBox("lbtn-rlm",crossIcon,kb_rlm,"Display provinces")
                .state(() -> map != null && map.visol("prov")).set(a -> groundClaim("prov",a)),
            actionSystemButton("rbtn-opt",crossIcon,kb_opt,"Options",() -> opts),
            actionSystemButton("rbtn-inv",crossIcon,kb_inv,"Inventory",() -> invwnd),
            actionSystemButton("rbtn-bud",crossIcon,null,"Kith & Kin",() -> zerg),
            actionSystemButton("rbtn-equ",crossIcon,null,"Equipment",() -> equwnd),
            actionSystemButton("rbtn-chr",crossIcon,null,"Character Sheet",() -> chrwdg)),
            Utils.getprefc("hearthborn/ui/system-cross", UI.scale(180, 90)));
    }

    protected void attached() {
	iconconf = loadiconconf();
	super.attached();
    }

    public static final KeyBinding kb_srch = KeyBinding.get("scm-srch", KeyMatch.forchar('S', KeyMatch.C));
    protected void added() {
	resize(parent.sz);
	hearthborn.world.GobMarks.attach(this);
	hearthborn.world.PlayerWarnings.attach(this);
	hearthborn.combat.FighterMark.bind(this);
	hearthborn.ui.FightBars.attach(this);
	ui.cons.out = new java.io.PrintWriter(new java.io.Writer() {
		StringBuilder buf = new StringBuilder();
		
		public void write(char[] src, int off, int len) {
		    List<String> lines = new ArrayList<String>();
		    synchronized(this) {
			buf.append(src, off, len);
			int p;
			while((p = buf.indexOf("\n")) >= 0) {
			    String ln = buf.substring(0, p).replace("\t", "        ");
			    lines.add(ln);
			    buf.delete(0, p + 1);
			}
		    }
		    for(String ln : lines) {
			syslog.append(ln, Color.WHITE);
		    }
		}
		
		public void close() {}
		public void flush() {}
	    });
	Debug.log = ui.cons.out;
	opts.c = sz.sub(opts.sz).div(2);
    }

    public void dispose() {
        hearthborn.combat.BowCombat.dispose(this);
        if(map!=null)hearthborn.navigation.Walking.cancel(map);
        hearthborn.scripts.ShortcutRunner.cancel(this);
        hearthborn.scripts.ScriptRunner.cancel(this);
        if(lastmsg!=null){lastmsg.dispose();lastmsg=null;}noticeText=null;noticeError=false;
        hearthborn.mining.MiningSafety.dispose(this);
        hearthborn.ui.GlobalCommands.dispose(this);
        hearthborn.ui.LocalActionBelt.dispose(this);
        hearthborn.inventory.BagTransfers.dispose(this);
        hearthborn.ui.StudyReminder.dispose(this);
        hearthborn.ui.ChatButton.dispose(this);
        hearthborn.ui.MapButton.dispose(this);
        hearthborn.planner.ClaimPlanStatus.dispose(this);
        hearthborn.ui.QuestJournalButton.dispose(this);
        hearthborn.map.DetachedMap.close(this);
        hearthborn.util.ObjectWindows.clear();
	inspect.dispose();
	dreamHarvester.cancel();
	savewndpos();
	Debug.log = new java.io.PrintWriter(System.err);
	ui.cons.clearout();
	super.dispose();
    }
    
    public class Hidepanel extends Widget {
	public final String id;
	public final Coord g;
	public final Indir<Coord> base;
	public boolean tvis;
	private double cur;

	public Hidepanel(String id, Indir<Coord> base, Coord g) {
	    this.id = id;
	    this.base = base;
	    this.g = g;
	    cur = show(tvis = Utils.getprefb(id + "-visible", true))?0:1;
	}

	public <T extends Widget> T add(T child) {
	    super.add(child);
	    pack();
	    if(parent != null)
		move();
	    return(child);
	}

	public Coord base() {
	    if(base != null) return(base.get());
	    return(new Coord((g.x > 0)?parent.sz.x:(g.x < 0)?0:((parent.sz.x - this.sz.x) / 2),
			     (g.y > 0)?parent.sz.y:(g.y < 0)?0:((parent.sz.y - this.sz.y) / 2)));
	}

	public void move(double a) {
	    cur = a;
            if(this==brpanel)g.x=sidebarOnRight()||classicOnLeft()?-1:1;
            if(this==ulpanel)g.x=hearthborn.ui.PortraitSide.onRight()?1:-1;
            if(this==urpanel)g.x=hearthborn.ui.PortraitSide.onRight()?-1:1;
	    Coord c = new Coord(base());
	    if(g.x < 0)
		c.x -= (int)(sz.x * a);
	    else if(g.x > 0)
		c.x -= (int)(sz.x * (1 - a));
	    if(g.y < 0)
		c.y -= (int)(sz.y * a);
	    else if(g.y > 0)
		c.y -= (int)(sz.y * (1 - a));
	    this.c = c;
	}

	public void move() {
	    move(cur);
	}

	public void presize() {
	    move();
	}

	public void cresize(Widget ch) {
	    if(this == brpanel && hearthborn.ui.Toolbars.enabled("actions")) {
		actionMenuSig = Integer.MIN_VALUE;
		syncActionMenu();
		return;
	    }
	    sz = contentsz();
	    if(this==brpanel&&parent!=null)move();
	}

	public boolean mshow(final boolean vis) {
	    clearanims(Anim.class);
	    if(vis)
		show();
	    new NormAnim(0.25) {
		final double st = cur, f = vis?0:1;

		public void ntick(double a) {
		    if((a == 1.0) && !vis)
			hide();
		    move(st + (Utils.smoothstep(a) * (f - st)));
		}
	    };
	    tvis = vis;
	    return(vis);
	}

	public boolean mshow() {
	    return(mshow(Utils.getprefb(id + "-visible", true)));
	}

	public boolean cshow(boolean vis) {
	    Utils.setprefb(id + "-visible", vis);
	    if(vis != tvis)
		mshow(vis);
	    return(vis);
	}

	public void cdestroy(Widget w) {
	    parent.cdestroy(w);
	}
    }

    boolean questLogOpen() {
        return hearthborn.ui.QuestHelper.enabled() && questHelper.visible();
    }

    void questLog(boolean open) {
        questHelper.combat(!open);
        if(qqview!=null && qqview.parent!=null)qqview.parent.hide();
    }

    public static class Hidewnd extends Window {
	public Hidewnd(Coord sz, String cap, boolean lg) {
	    super(sz, cap, lg);
	}

	public Hidewnd(Coord sz, String cap) {
	    super(sz, cap);
	}

	public void reqclose() {
	    hide();
	}
    }

    public static class Zergwnd extends Hidewnd {
	public final Tabs tabs = new Tabs(Coord.z, Coord.z, this);
	public final TButton kin;
	public final Collection<PTab<Category>> types = new ArrayList<>();

	public static class Category extends Widget {
	    public final String id;
	    public final List<Polity> pols = new ArrayList<>();
	    public final Widget cap;
	    public Widget sel = null;
	    private Coord polc = Coord.z;

	    public Category(String id, String name) {
		this.id = id;
		cap = add(hearthborn.ui.UiTheme.heading(name));
	    }

	    public class Selector extends SDropBox<Polity, Widget> {
		public Selector() {
		    super(BuddyWnd.width, UI.scale(75), Polity.nmf.height());
		    for(Widget ch : Category.this.children()) {
			if((ch instanceof Polity) && ch.visible()) {
			    super.change((Polity)ch);
			    break;
			}
		    }
		}

		protected List<Polity> items() {return(pols);}
		protected Widget makeitem(Polity pol, int idx, Coord sz) {
		    return(TextItem.of(sz, Polity.nmf, () -> pol.name));
		}

		public void change(Polity pol) {
		    super.change(pol);
		    select(pol);
		}
	    }

	    private void updsel() {
		if(sel != null)
		    sel.destroy();
		if(pols.isEmpty()) {
		    sel = null;
		} else if(pols.size() == 1) {
		    sel = new Label(pols.get(0).name, Polity.nmf);
		} else {
		    sel = new Selector();
		}
		Coord c = cap.pos("bl").adds(0, 2);
		if(sel != null)
		    c = add(sel, c).pos("bl").adds(0, 5);
		if(!Utils.eq(c, polc)) {
		    polc = c;
		    for(Polity pol : pols)
			pol.move(polc);
		    pack();
		}
	    }

	    public void select(Polity sel) {
		for(Polity pol : pols)
		    pol.show(pol == sel);
		pack();
	    }

	    public void cresize(Widget ch) {
		pack();
	    }

	    public void addpol(Polity p) {
		pols.add(add(p));
		if(sel != null)
		    p.move(polc);
		select(p);
		updsel();
	    }

	    public void cdestroy(Widget w) {
		if(pols.contains(w)) {
		    pols.remove(w);
		    updsel();
		    if(pols.isEmpty()) {
			destroy();
		    } else {
			if(w.visible) {
			    if(pols.size() > 1)
				((Selector)sel).change(pols.get(0));
			    else
				pols.get(0).show(true);
			}
		    }
		}
	    }
	}

	class PTab<W extends Widget> extends Tabs.Tab {
	    public final W main;
	    public final TButton tb;

	    public PTab(W main, TButton tb) {
		tabs.super();
		this.main = main;
		this.tb = tb;
	    }

	    public void cdestroy(Widget w) {
		if(w == main) {
		    destroy();
		    tb.destroy();
		    Zergwnd.this.types.remove(this);
		    repack();
		    if(tabs.curtab == this) {
			tabs.showtab(kin.tab);
			repack();
		    }
		}
	    }

	    public void cresize(Widget ch) {
		repack();
	    }
	}

	class TButton extends IButton {
	    public final Resource.Image upimg;
	    public PTab tab = null;

	    TButton(String nm) {
		super("gfx/hud/buttons/" + nm, "u", "d", null);
		upimg = Resource.loadrimg("gfx/hud/buttons/" + nm + "u");
		Resource.Tooltip tt = upimg.getres().layer(Resource.tooltip);
		if(tt != null)
		    settip(tt.t);
	    }

	    public void click() {
		if(tab != null) {
		    tabs.showtab(tab);
		    repack();
		}
	    }

	    protected void depress() {
		ui.sfx(Button.clbtdown.stream());
	    }

	    protected void unpress() {
		ui.sfx(Button.clbtup.stream());
	    }
	}

	Zergwnd() {
	    super(Coord.z, "Kith & Kin", true);
	    kin = add(new TButton("kin"));
	    kin.tooltip = Text.render("Kin");
	}

	private void repack() {
	    tabs.indpack();
	    kin.move(Coord.of(0, tabs.curtab.contentsz().y + UI.scale(20)));
	    List<TButton> pbtns = new ArrayList<>();
	    for(Widget ch : children()) {
		if((ch instanceof TButton) && (ch != kin))
		    pbtns.add((TButton)ch);
	    }
	    pbtns.sort((a, b) -> a.upimg.z - b.upimg.z);
	    Widget lf = kin, prev = lf;
	    int x = 1;
	    for(TButton pbtn : pbtns) {
		if(x < 3) {
		    pbtn.move(prev.pos("ur").adds(10, 0));
		    prev = pbtn;
		    x++;
		} else {
		    pbtn.move(lf.pos("bl").adds(0, 10));
		    lf = prev = pbtn;
		    x = 0;
		}
	    }
	    this.pack();
	}

	public <W extends Widget> PTab<W> ntab(W ch, TButton tb) {
	    PTab<W> tab = add(new PTab<>(ch, tb), tabs.c);
	    tab.add(ch, Coord.z);
	    tb.tab = tab;
	    repack();
	    return(tab);
	}

	private PTab<Category> getptab(String name) {
	    PTab<Category> tab = null;
	    for(PTab<Category> cur : types) {
		if(Utils.eq(cur.main.id, name)) {
		    tab = cur;
		    break;
		}
	    }
	    if(tab == null) {
		TButton tb = add(new TButton(name));
		tab = ntab(new Category(name, tb.upimg.getres().flayer(Resource.tooltip).t), tb);
		types.add(tab);
	    }
	    return(tab);
	}

	public void addpol(Polity p) {
	    getptab(p.type()).main.addpol(p);
	}
    }

    static class DraggedItem {
	final GItem item;
	final Coord dc;

	DraggedItem(GItem item, Coord dc) {
	    this.item = item; this.dc = dc;
	}
    }

    private void updhand() {
	if((hand.isEmpty() && (vhand != null)) || ((vhand != null) && !hand.contains(vhand.item))) {
	    ui.destroy(vhand);
	    vhand = null;
	}
	if(!hand.isEmpty() && (vhand == null)) {
	    DraggedItem fi = hand.iterator().next();
	    vhand = add(new ItemDrag(fi.dc, fi.item));
	}
    }

    private String mapfilename() {
	StringBuilder buf = new StringBuilder();
	buf.append(genus);
	String chrid = Utils.getpref("mapfile/" + this.chrid, "");
	if(!chrid.equals("")) {
	    if(buf.length() > 0) buf.append('/');
	    buf.append(chrid);
	}
	return(buf.toString());
    }

    public Coord optplacement(Widget child, Coord org) {
	Coord childSize=hearthborn.ui.HudInterfaceScale.size(child);
	Set<Window> closed = new HashSet<>();
	Set<Coord> open = new HashSet<>();
	open.add(org);
	Coord opt = null;
	double optscore = Double.NEGATIVE_INFINITY;
	Coord plc = null;
	{
	    Gob pl = map.player();
	    if(pl != null) {
		Coord3f raw = pl.placed.getc();
		if(raw != null)
		    plc = map.screenxf(raw).round2();
	    }
	}
	Area parea = Area.sized(Coord.z, sz);
	while(!open.isEmpty()) {
	    Coord cur = Utils.take(open);
	    double score = 0;
	Area tarea = Area.sized(cur, childSize);
	    if(parea.isects(tarea)) {
		double outside = 1.0 - (((double)parea.overlap(tarea).area()) / ((double)tarea.area()));
		if((outside > 0.75) && !cur.equals(org))
		    continue;
		score -= Math.pow(outside, 2) * 100;
	    } else {
		if(!cur.equals(org))
		    continue;
		score -= 100;
	    }
	    {
		boolean any = false;
		for(Widget wdg = this.child; wdg != null; wdg = wdg.next) {
		    if(!(wdg instanceof Window))
			continue;
		    Window wnd = (Window)wdg;
		    if(!wnd.visible())
			continue;
		    Area warea = wnd.parentarea(this);
		    if(warea.isects(tarea)) {
			any = true;
			score -= ((double)warea.overlap(tarea).area()) / ((double)tarea.area());
			if(!closed.contains(wnd)) {
		    open.add(new Coord(wnd.c.x - childSize.x, cur.y));
		    open.add(new Coord(cur.x, wnd.c.y - childSize.y));
			    open.add(new Coord(wnd.c.x + wnd.sz.x, cur.y));
			    open.add(new Coord(cur.x, wnd.c.y + wnd.sz.y));
			    closed.add(wnd);
			}
		    }
		}
		if(!any)
		    score += 10;
	    }
	    if(plc != null) {
		if(tarea.contains(plc))
		    score -= 100;
		else
		    score -= (1 - Math.pow(tarea.closest(plc).dist(plc) / sz.dist(Coord.z), 0.5)) * 1.5;
	    }
	    score -= (cur.dist(org) / sz.dist(Coord.z)) * 0.75;
	    if(score > optscore) {
		optscore = score;
		opt = cur;
	    }
	}
	return(opt);
    }

    private void savewndpos() {
	if(invwnd != null)
	    Utils.setprefc("wndc-inv", hearthborn.ui.WindowPlaces.positionForSave(invwnd));
	if(equwnd != null)
	    Utils.setprefc("wndc-equ", equwnd.c);
	if(chrwdg != null)
	    Utils.setprefc("wndc-chr", chrwdg.c);
	if(zerg != null)
	    Utils.setprefc("wndc-zerg", zerg.c);
	if(mapfile != null)
	    mapfile.storePlace();
    }

    private final BMap<String, Window> wndids = new HashBMap<String, Window>();

    public IMeter meter(String part) {
	for(Widget w : meters) {
	    if(!(w instanceof IMeter))
		continue;
	    IMeter meter = (IMeter)w;
	    try {
		if(meter.bg.get().name.contains(part))
		    return(meter);
	    } catch(Loading l) {
	    }
	}
	return(null);
    }

    public void addchild(Widget child, Object... args) {
	String place = ((String)args[0]).intern();
	if(place == "mapview") {
	    child.resize(sz);
	    map = add((MapView)child, Coord.z);
	    map.lower();
	    if(mapfile != null) {
		ui.destroy(mapfile);
		mapfile = null;
	    }
	    ResCache mapstore = ResCache.global;
	    if(MapFile.mapbase.get() != null)
		mapstore = HashDirCache.get(MapFile.mapbase.get());
	    if(mapstore != null) {
		MapFile file;
		try {
		    file = MapFile.load(mapstore, mapfilename());
		} catch(java.io.IOException e) {
		    /* XXX: Not quite sure what to do here. It's
		     * certainly not obvious that overwriting the
		     * existing mapfile with a new one is better. */
		    throw(new RuntimeException("failed to load mapfile", e));
		}
		minesweeper = new hearthborn.mining.Minesweeper(file);
                lastsavegrid = null; lastsaveseq = -1; lastsaveid = -1;
		boolean mini=Utils.getprefb("compact-map", false);
		mapfile = new MapWnd(file, map, hearthborn.map.MapPlaces.size(mini), "Map");
		mapfile.reqclose(() -> hearthborn.map.MapPlaces.close(mapfile));
		mapfile.show(Utils.getprefb("wndvis-map", false));
		add(mapfile, hearthborn.map.MapPlaces.position(mini));
                mapAnchored=false;mapfile.fitOnScreen();
	    }
	} else if(place == "menu") {
	    menu = (MenuGrid)brpanel.add(child, menugridc);
	    actionMenuSig = Integer.MIN_VALUE;
	    syncActionMenu();
	    fitwdg(srchwnd = GameUI.this.add(new hearthborn.ui.ActionBrowser(menu), Utils.getprefc("wndc-srch", UI.scale(200, 200))));
	    srchwnd.reqclose(srchwnd::hide).hide();
	} else if(place == "fight") {
	    fv = urpanel.add((Fightview)child, 0, 0);
	} else if(place == "fsess") {
	    add(child, Coord.z);
	} else if(place == "inv") {
	    invwnd = new Hidewnd(Coord.z, "Inventory") {
		    public void cresize(Widget ch) {
			pack();
		    }
		};
	    invwnd.add(maininv = (Inventory)child, Coord.z);
	    invwnd.pack();
	    invwnd.hide();
	    add(invwnd, Utils.getprefc("wndc-inv", new Coord(100, 100)));
	} else if(place == "equ") {
	    equwnd = new Hidewnd(Coord.z, "Equipment");
	    equwnd.add(child, Coord.z);
	    equwnd.pack();
	    equwnd.hide();
	    add(equwnd, Utils.getprefc("wndc-equ", new Coord(400, 10)));
	} else if(place == "hand") {
	    GItem g = add((GItem)child);
	    Coord lc = (Coord)args[1];
	    hand.add(new DraggedItem(g, lc));
	    hearthborn.ui.StockpileCursor.taken(this, g);
	    updhand();
	} else if(place == "chr") {
	    chrwdg = add((CharWnd)child, Utils.getprefc("wndc-chr", new Coord(300, 50)));
	    chrwdg.reqclose(chrwdg::hide).hide();
	} else if(place == "craft") {
            if(child instanceof Makewindow && srchwnd instanceof hearthborn.ui.ActionBrowser
                    && ((hearthborn.ui.ActionBrowser)srchwnd).embed((Makewindow)child)) {
                return;
            }
            if(makewnd!=null && makewnd.parent==this){makewndc=new Coord(makewnd.c);makewnd.hide();}
	    String cap = "";
	    Widget mkwdg = child;
	    if(mkwdg instanceof Makewindow)
		cap = ((Makewindow)mkwdg).rcpnm;
	    if(cap.equals(""))
		cap = "Crafting";
	    makewnd = new Window(Coord.z, cap, true) {
		    public void wdgmsg(Widget sender, String msg, Object... args) {
			if((sender == this) && msg.equals("close")) {
			    mkwdg.wdgmsg("close");
			    return;
			}
			super.wdgmsg(sender, msg, args);
		    }
		    public void cdestroy(Widget w) {
			if(w == mkwdg) {
			    ui.destroy(this);
			    if(makewnd==this)makewnd = null;
			}
		    }
		    public void destroy() {
			Utils.setprefc("makewndc", makewndc = this.c);
			super.destroy();
		    }
		};
	    hearthborn.ui.CraftHistory history=makewnd.add(new hearthborn.ui.CraftHistory(this),Coord.z);
            makewnd.add(mkwdg,Coord.of(0,history.sz.y));
	    makewnd.pack();
	    fitwdg(add(makewnd, makewndc));
	} else if(place == "buddy") {
	    zerg.ntab(buddies = (BuddyWnd)child, zerg.kin);
	} else if(place == "pol") {
	    zerg.addpol((Polity)child);
	} else if(place == "chat") {
	    chat.addchild(child);
	} else if(place == "party") {
	    add(child);
            hearthborn.ui.PartyLayout.place(child);
	} else if(place == "meter") {
	    Coord meterSize=hearthborn.ui.StatusScale.size(IMeter.fsz);
	    int x = (meters.size() % 3) * (meterSize.x + UI.scale(5));
	    int y = (meters.size() / 3) * (meterSize.y + UI.scale(2));
	    ulpanel.add(child, portrait.c.x + portrait.sz.x + UI.scale(10) + x, portrait.c.y + y);
	    meters.add(child);
	} else if(place == "buff") {
	    buffs.addchild(child);
	} else if(place == "qq") {
	    if(qqview != null)
		qqview.reqdestroy();
	    final Widget cref = qqview = child;
	    add(new AlignPanel() {
		    {add(cref);}

		    protected Coord getc() {
			return(new Coord(10, questBottom() - this.sz.y - 10));
		    }

		    public void cdestroy(Widget ch) {
			qqview = null;
			destroy();
		    }
		}).hide();
	} else if(place == "misc") {
            if(child instanceof MenuSearch && !(child instanceof hearthborn.ui.ActionBrowser)
                    && srchwnd instanceof hearthborn.ui.ActionBrowser
                    && ((hearthborn.ui.ActionBrowser)srchwnd).acceptIngredients((MenuSearch)child))return;
	    Coord c;
	    int a = 1;
	    if(args[a] instanceof Coord) {
		c = (Coord)args[a++];
	    } else if(args[a] instanceof Coord2d) {
		c = ((Coord2d)args[a++]).mul(new Coord2d(this.sz.sub(child.sz))).round();
		c = optplacement(child, c);
	    } else if(args[a] instanceof String) {
		c = relpos((String)args[a++], child, (args.length > a) ? ((Object[])args[a++]) : new Object[] {}, 0);
	    } else {
		throw(new UI.UIException("Illegal gameui child", place, args));
	    }
	    while(a < args.length) {
		Object opt = args[a++];
		if(opt instanceof Object[]) {
		    Object[] opta = (Object[])opt;
		    switch((String)opta[0]) {
		    case "id":
			String wndid = (String)opta[1];
			if(child instanceof Window) {
			    c = Utils.getprefc(String.format("wndc-misc/%s", (String)opta[1]), c);
			    if(!wndids.containsKey(wndid)) {
				c = fitwdg(child, c);
				wndids.put(wndid, (Window)child);
			    } else {
				c = optplacement(child, c);
			    }
			}
			break;
		    case "obj":
			if(child instanceof Window) {
			    ((Window)child).settrans(new GobTrans(map, Utils.uiv(opta[1])));
			}
			break;
		    }
		}
	    }
	    add(child, c);
	} else if(place == "abt") {
	    add(child, Coord.z);
	} else {
	    throw(new UI.UIException("Illegal gameui child", place, args));
	}
    }

    public static class GobTrans implements Window.Transition<GobTrans.Anim, GobTrans.Anim> {
	public static final double time = 0.1;
	public final MapView map;
	public final long gobid;

	public GobTrans(MapView map, long gobid) {
	    this.map = map;
	    this.gobid = gobid;
	}

	private Coord oc() {
	    Gob gob = map.ui.sess.glob.oc.getgob(gobid);
	    if(gob == null)
		return(null);
	    Location.Chain loc = Utils.el(gob.getloc());
	    if(loc == null)
		return(null);
	    return(map.screenxf(loc.fin(Matrix4f.id).mul4(Coord3f.o).invy()).round2());
	}

	public class Anim extends Window.NormAnim {
	    public final Window wnd;
	    private Coord oc;

	    public Anim(Window wnd, boolean hide, Anim from) {
		super(time, from, hide);
		this.wnd = wnd;
		this.oc = wnd.c.add(wnd.sz.div(2));
	    }

	    public void draw(GOut g, Tex tex) {
		GOut pg = g.reclipl(wnd.c.inv(), wnd.parent.sz);
		Coord cur = oc();
		if(cur != null)
		    this.oc = cur;
		Coord sz = tex.sz();
		double na = Utils.smoothstep(this.na);
		pg.chcolor(255, 255, 255, (int)(na * 255));
		double fac = 1.0 - na;
		Coord c = this.oc.sub(sz.div(2)).mul(1.0 - na).add(wnd.c.mul(na));
		pg.image(tex, c.add((int)(sz.x * fac * 0.5), (int)(sz.y * fac * 0.5)),
			 Coord.of((int)(sz.x * (1.0 - fac)), (int)(sz.y * (1.0 - fac))));
	    }
	}

	public Anim show(Window wnd, Anim hide) {return(new Anim(wnd, false, hide));}
	public Anim hide(Window wnd, Anim show) {return(new Anim(wnd, true,  show));}
    }

    public String windowId(Window wnd) {
	return(wndids.reverse().get(wnd));
    }

    public void cdestroy(Widget w) {
	if(w instanceof Window) {
	    hearthborn.ui.WindowPlaces.closed((Window)w);
	    String wndid = wndids.reverse().get((Window)w);
	    if(wndid != null) {
		wndids.remove(wndid);
		Utils.setprefc(String.format("wndc-misc/%s", wndid), w.c);
	    }
	}
	if(w instanceof GItem) {
	    for(Iterator<DraggedItem> i = hand.iterator(); i.hasNext();) {
		DraggedItem di = i.next();
		if(di.item == w) {
		    i.remove();
		    updhand();
		}
	    }
	} else if(w == chrwdg) {
	    chrwdg = null;
	}
	meters.remove(w);
    }

    public static class Progress extends hearthborn.ui.ActionProgress {
	public Progress(double prog){super(prog);}
    }

    public void draw(GOut g) {
        layoutActionBelt();
        compactLayout.refresh();
	super.draw(g);
	int by = sz.y;
        int bx=chatDockBase().x;
	if(chat.visible() && !chat.detached())
	    by = Math.min(by, chat.c.y);
	if(beltwdg.visible())
	    by = Math.min(by, beltwdg.c.y);
	if(cmdline != null) {
	    drawcmd(g, new Coord(bx+UI.scale(10), by -= UI.scale(20)));
	} else if(lastmsg != null) {
	    if((Utils.rtime() - msgtime) > hearthborn.ui.ChatSounds.seconds()) {
		lastmsg.dispose();lastmsg = null;noticeText=null;noticeError=false;
	    } else {
		int pad=UI.scale(2);
		Coord box=lastmsg.sz();
		Cal clock=umpanel==null?null:umpanel.getchild(Cal.class);
		Coord under=hearthborn.ui.NoticePlace.belowClock()?hearthborn.ui.NoticePlace.underClock(sz,box,clock==null?null:clock.parentarea(this),Fightsess.active):null;
		int x,y;
		if(under==null){
		    x=bx+UI.scale(10);
		    y=by-UI.scale(6)-box.y;
		    y=hearthborn.ui.NoticePlace.aboveStrips(this,x,y,box);
		    by=y-pad;
		}else{x=under.x;y=under.y;}
		g.chcolor(0, 0, 0, 192);
		g.frect(Coord.of(x-pad, y-pad), box.add(pad*2, pad*2));
		g.chcolor();
		g.image(lastmsg.tex(), Coord.of(x, y));
	    }
	}
	if(!compactUI()&&chat.closed()) {
	    chat.drawsmall(g, new Coord(bx+UI.scale(10), by), UI.scale(100));
	}
    }
    
    private String iconconfname() {
	StringBuilder buf = new StringBuilder();
	buf.append("data/mm-icons-2");
	if(genus != null)
	    buf.append("/" + genus);
	if(ui.sess != null)
	    buf.append("/" + ui.sess.user.prsname());
	return(buf.toString());
    }

    private GobIcon.Settings loadiconconf() {
	String nm = iconconfname();
	try {
	    return(GobIcon.Settings.load(ui, nm));
	} catch(Exception e) {
	    new Warning(e, "could not load icon-conf").issue();
	}
	return(new GobIcon.Settings(ui, nm));
    }

    private Coord lastsavegrid = null;
    private int lastsaveseq = -1;
    private long lastsaveid = -1;
    private void mapfiletick() {
	MapView map = this.map;
	MapFile file = mapfile == null ? null : mapfile.file;
	if((map == null) || (file == null))
	    return;
	Gob pl = ui.sess.glob.oc.getgob(map.plgob);
	Coord gc;
	if(pl == null)
	    gc = map.cc.floor(MCache.tilesz).div(MCache.cmaps);
	else
	    gc = pl.rc.floor(MCache.tilesz).div(MCache.cmaps);
	try {
	    MCache.Grid grid = ui.sess.glob.map.getgrid(gc);
	    if(grid == null)
		return;
	    if(Utils.eq(gc, lastsavegrid) && (lastsaveseq == grid.seq) && (lastsaveid == grid.id))
		return;
	    if(!file.update(ui.sess.glob.map, gc))
		return;
	    lastsavegrid = gc;
	    lastsaveseq = grid.seq;
	    lastsaveid = grid.id;
	} catch(Loading l) {
	}
    }

    private double lastwndsave = 0;
    public final hearthborn.ui.Inspect inspect = new hearthborn.ui.Inspect(this);
    public final hearthborn.scripts.DreamHarvester dreamHarvester = new hearthborn.scripts.DreamHarvester(this);

    public boolean tooltip(TooltipQuery ev) {
        if(checkhit(ev.c)) {
            Object tip = inspect.tooltip();
            if(tip != null) return ev.set(tip, this);
        }
        return super.tooltip(ev);
    }

    public void tick(double dt) {
        compactLayout.refresh();
        hearthborn.map.RadarIcons.flush();
        if(iconconf!=null){iconconf.installRadar();iconconf.restoreSavedSelection();iconconf.recoverMissingSelection();}
        hearthborn.mining.Minesweeper.observe(this);
        hearthborn.mining.MiningSafety.tick(this);
        loginSiegeCheck.tick(this);
        nearbySiegeCheck.tick(this);
        indoorCamera.tick(this);
        hearthborn.inventory.BagTransfers.tick(this);
        if(questHelper.parent==null)add(questHelper);
        questHelper.layout(questBottom());
        if(qqview!=null && qqview.parent!=null)qqview.parent.hide();
	inspect.tick(dt);
        hearthborn.scripts.ShortcutRunner.tick(this);
        hearthborn.scripts.ScriptRunner.tick(this);
        hearthborn.combat.BowCombat.tick(this);
        hearthborn.ui.AutoDrink.tick(this);
        hearthborn.ui.LoginToggles.tick(this);
        hearthborn.ui.LocalActionBelt.tick(this);
        hearthborn.ui.StudyReminder.tick(this);
        hearthborn.ui.ChatButton.tick(this);
        hearthborn.ui.MapButton.tick(this);
        hearthborn.planner.ClaimPlanStatus.tick(this);
        hearthborn.ui.QuestJournalButton.tick(this);
	dreamHarvester.tick(dt);
        if(map!=null)hearthborn.navigation.Walking.tick(map);
        hearthborn.timers.SiegeTimerCleanup.tick(this);
	hearthborn.timers.Timers.tick(this);
        hearthborn.ClientObservations.tick(this);
	hearthborn.world.GobMarks.poll();
	super.tick(dt);
        hearthborn.ui.MagneticWindows.tick(this);
	double now = Utils.rtime();
	if(now - lastwndsave > 60) {
	    savewndpos();
	    lastwndsave = now;
	}
	double idle = now - ui.lastevent;
	if(!afk && (idle > 300)) {
	    afk = true;
	    wdgmsg("afk");
	} else if(afk && (idle <= 300)) {
	    afk = false;
	}
	mapfiletick();
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "err") {
	    String err = (String)args[0];
	    ui.error(err);
	} else if(msg == "msg") {
	    String text = (String)args[0];
	    ui.msg(text);
	} else if(msg == "prog") {
            hearthborn.combat.BowCombat.progress(this,args);
	    if(args.length > 0) {
		double p = Utils.dv(args[0]) / 100.0;
		if(prog == null)
		    prog = adda(new Progress(p), 0.5, 0.35);
		else
		    prog.set(p);
	    } else {
		if(prog != null) {
		    prog.reqdestroy();
		    prog = null;
		}
	    }
	} else if(msg == "setbelt") {
	    int slot = Utils.iv(args[0]);
	    if(args.length < 2) {
		belt[slot] = null;
	    } else {
		Indir<Resource> res = ui.sess.getresv(args[1]);
		Message sdt = Message.nil;
		if(args.length > 2)
		    sdt = new MessageBuf((byte[])args[2]);
		ResData rdt = new ResData(res, sdt);
		ui.sess.glob.loader.defer(() -> {
			belt[slot] = mkbeltslot(slot, rdt);
		    }, null);
	    }
	} else if(msg == "setbelt2") {
	    int slot = Utils.iv(args[0]);
	    if(args.length < 2) {
		belt[slot] = null;
	    } else {
		switch((String)args[1]) {
		case "p": {
		    Object id = args[2];
		    belt[slot] = new PagBeltSlot(slot, menu.paginafor(id, null));
		    break;
		}
		case "r": {
		    Indir<Resource> res = ui.sess.getresv(args[2]);
		    ui.sess.glob.loader.defer(() -> {
			    belt[slot] = new PagBeltSlot(slot, PagBeltSlot.resolve(menu, res));
			}, null);
		    break;
		}
		case "d": {
		    Indir<Resource> res = ui.sess.getresv(args[2]);
		    Message sdt = Message.nil;
		    if(args.length > 2)
			sdt = new MessageBuf((byte[])args[3]);
		    belt[slot] = new ResBeltSlot(slot, new ResData(res, sdt));
		    break;
		}
		}
	    }
	} else if(msg == "polowner") {
	    int id = Utils.iv(args[0]);
	    String o = (String)args[1];
	    boolean n = Utils.bv(args[2]);
	    if(o != null)
		o = o.intern();
	    String cur = polowners.get(id);
	    if(map != null) {
		if((o != null) && (cur == null)) {
		    if(n)
			map.setpoltext(id, "Entering " + o);
		} else if((o == null) && (cur != null)) {
		    map.setpoltext(id, "Leaving " + cur);
		}
	    }
	    polowners.put(id, o);
	} else if(msg == "showhelp") {
	    Indir<Resource> res = ui.sess.getresv(args[0]);
	    if(help == null) {
		(help = adda(new HelpWnd(res), 0.5, 0.25)).reqclose(() -> {
		    if(help != null)
		        help.reqdestroy();
		    help = null;
		});
	    } else {
		help.set(res);
	    }
	} else if(msg == "map-mark") {
	    long gobid = UINT.of(args[0]);
	    UID oid = UNIQID.of(args[1]);
	    Indir<Resource> res = ui.sess.getresv(args[2]);
	    String nm = STR.of(args[3]);
	    byte[] data = BYTES.opt(args, 4).or(new byte[0]);
	    if(mapfile != null)
		mapfile.markobj(gobid, oid, res, data, nm);
	} else if(msg == "map-icons") {
	    GobIcon.Settings conf = this.iconconf;
	    int tag = Utils.iv(args[0]);
	    if(args.length < 2) {
		if(conf.tag != tag)
		    wdgmsg("map-icons", conf.tag);
	    } else {
		conf.receive(args);
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    private static final int fitmarg = UI.scale(100);
    private Coord fitwdg(Widget wdg, Coord c) {
	Coord ret = new Coord(c);
	ret.x = Math.max(ret.x, Math.min(0, fitmarg - wdg.sz.x));
	ret.y = Math.max(ret.y, Math.min(0, fitmarg - wdg.sz.y));
	ret.x = Math.min(ret.x, sz.x - Math.min(fitmarg, wdg.sz.x));
	ret.y = Math.min(ret.y, sz.y - Math.min(fitmarg, wdg.sz.y));
	return(ret);
    }

    private void fitwdg(Widget wdg) {
	wdg.c = fitwdg(wdg, wdg.c);
    }

    private boolean wndstate(Window wnd) {
	if(wnd == null)
	    return(false);
	return(wnd.visible()&&!hearthborn.ui.EquipmentInventoryPreview.showing(wnd));
    }

    public void toggleMap() {
	if(mapfile == null) return;
	togglewnd(mapfile);
	Utils.setprefb("wndvis-map", mapfile.visible());
    }

    private void togglewnd(Window wnd) {
	if(wnd != null) {
            if(hearthborn.ui.EquipmentInventoryPreview.open(wnd))return;
	    if(wnd.show(!wnd.visible())) {
		wnd.raise();
		fitwdg(wnd);
		setfocus(wnd);
		if(wnd==mapfile)hearthborn.ui.MagneticWindows.uncover(wnd);
	    }
	}
    }

    public static class MenuButton extends IButton {
	MenuButton(String base, KeyBinding gkey, String tooltip) {
            this(hearthborn.ui.SystemControls.buttonImages("gfx/hud/"+base,"gfx/hud/"+base+"-d","gfx/hud/"+base+"-h"),gkey,tooltip);
	}
        private MenuButton(java.awt.image.BufferedImage[] images,KeyBinding gkey,String tooltip){
            super(images[0],images[1],images[2]);
	    setgkey(gkey);
	    settip(tooltip);
	}
    }

    public static class MenuCheckBox extends ICheckBox {
	MenuCheckBox(String base, KeyBinding gkey, String tooltip) {
            this(hearthborn.ui.SystemControls.buttonImages("gfx/hud/"+base,"gfx/hud/"+base+"-d","gfx/hud/"+base+"-h","gfx/hud/"+base+"-dh"),gkey,tooltip);
	}
        private MenuCheckBox(java.awt.image.BufferedImage[] images,KeyBinding gkey,String tooltip){
            super(new TexI(images[0]),new TexI(images[1]),new TexI(images[2]),new TexI(images[3]));
	    setgkey(gkey);
	    settip(tooltip);
	}
        public void dispose(){up.dispose();down.dispose();hoverup.dispose();hoverdown.dispose();super.dispose();}
    }

    public static final KeyBinding kb_inv = KeyBinding.get("inv", KeyMatch.forcode(KeyEvent.VK_TAB, 0));
    public static final KeyBinding kb_equ = KeyBinding.get("equ", KeyMatch.forchar('E', KeyMatch.C));
    public static final KeyBinding kb_chr = KeyBinding.get("chr", KeyMatch.forchar('T', KeyMatch.C));
    public static final KeyBinding kb_bud = KeyBinding.get("bud", KeyMatch.forchar('B', KeyMatch.C));
    public static final KeyBinding kb_opt = KeyBinding.get("opt", KeyMatch.forchar('O', KeyMatch.C));
    public static final KeyBinding kb_map = KeyBinding.get("map", KeyMatch.forchar('A', KeyMatch.C));
    public static final KeyBinding kb_claim = KeyBinding.get("ol-claim", KeyMatch.nil);
    public static final KeyBinding kb_vil = KeyBinding.get("ol-vil", KeyMatch.nil);
    public static final KeyBinding kb_rlm = KeyBinding.get("ol-rlm", KeyMatch.nil);
    public static final KeyBinding kb_ico = KeyBinding.get("map-icons", KeyMatch.nil);
    public void toggleIconSettings() {
		    if(iconconf == null)
			return;
		    if(iconwnd == null) {
			iconwnd = new hearthborn.map.MapSettingsWindow(iconconf).reqclose(() -> {
			    if(iconwnd != null)
				iconwnd.reqdestroy();
			    iconwnd = null;
			});
			fitwdg(GameUI.this.add(iconwnd, Utils.getprefc("wndc-icon", new Coord(200, 200))));
		    } else {
			iconwnd.reqclose();
		    }
    }

    /** Shows or hides one claim overlay ("cplot", "vlg" or "prov"), as the system buttons do. */
    public void toggleClaim(String tag) {
	if(map != null)
	    groundClaim(tag, !map.visol(tag));
    }

    private void groundClaim(String tag, boolean enabled) {
        if(map != null) {
            boolean was = map.visol(tag);
            if(enabled) map.enol(tag); else map.disol(tag);
            if(was != enabled) claimNotice(tag, enabled);
        }
    }

    /** Every way of switching a claim overlay, from the buttons, the action menu or a hotkey, answers with a click and a notice. */
    private void claimNotice(String tag, boolean shown) {
        String subject = tag.equals("vlg") ? "Village claims" : tag.equals("prov") ? "Provinces" : "Personal claims";
        ui.msg(hearthborn.L10n.tr(subject + (shown ? " shown" : " hidden")), new Color(235, 225, 190),
            shown ? Button.clbtdown : Button.clbtup);
    }

    private MenuCheckBox actionSystemCheckBox(String base,int size,KeyBinding key,String tip) {
        return new MenuCheckBox(hearthborn.ui.SystemButtonSkin.images(base,size),key,tip);
    }

    private Widget actionSystemButton(String base,int size,KeyBinding key,String tip,java.util.function.Supplier<Window> window) {
        return new MenuCheckBox(hearthborn.ui.SystemButtonSkin.images(base,size),key,tip).state(() -> wndstate(window.get())).click(() -> togglewnd(window.get()));
    }

    /** Search is also opened from the action menu, so this copy does not take the hotkey. */
    private Widget crossControl(Widget button) { button.kb_gkey = null; return button; }

    private Widget actionSearchButton(int size) {
        MenuCheckBox box=new MenuCheckBox(hearthborn.ui.SystemButtonSkin.images("csearch",size),kb_srch,"Search actions...");
        box.state(() -> srchwnd!=null&&srchwnd.visible()).click(this::toggleActionSearch);
        return box;
    }

    public static Widget mapIconButton(java.util.function.Supplier<GameUI> owner) {
        return new MenuCheckBox("lbtn-ico", kb_ico, "Map display settings").state(() -> {
            GameUI gui = owner.get();return gui != null && gui.wndstate(gui.iconwnd);
        }).click(() -> {GameUI gui = owner.get();if(gui != null)gui.toggleIconSettings();});
    }

    public static final KeyBinding kb_shoot = KeyBinding.get("screenshot", KeyMatch.forchar('S', KeyMatch.M));
    public static final KeyBinding kb_chat = KeyBinding.get("chat-toggle", KeyMatch.forchar('C', KeyMatch.C));
    public static final KeyBinding kb_hide = KeyBinding.get("ui-toggle", KeyMatch.nil);
    public static final KeyBinding kb_study = KeyBinding.get("hearthborn/study", KeyMatch.forcode(java.awt.event.KeyEvent.VK_S, KeyMatch.M));
    private hearthborn.ui.StudyWindow studyWindow;
    public void toggleStudy() {
        SAttrWnd attributes = chrwdg == null ? null : chrwdg.getchild(SAttrWnd.class);
        if(attributes == null) return;
        Inventory study = attributes.getchild(Inventory.class);
        if(study == null) return;
        if(studyWindow == null || !studyWindow.shows(study)) {
            if(studyWindow != null) studyWindow.destroy();
            studyWindow = add(new hearthborn.ui.StudyWindow(study), UI.scale(100,100));
        } else studyWindow.show(!studyWindow.visible);
        if(studyWindow.visible) {studyWindow.raise();setfocus(studyWindow);}
    }

    public static final KeyBinding kb_logout = KeyBinding.get("logout", KeyMatch.nil);
    public static final KeyBinding kb_switchchr = KeyBinding.get("logout-cs", KeyMatch.nil);
    public static boolean matchesActionSearch(KbdEvent event){
        KeyMatch key=kb_srch.key();
        if(key.match(event))return true;
        if(key instanceof KeyMatch.CharMatch) {
            KeyMatch.CharMatch match=(KeyMatch.CharMatch)key;
            int code=event.code==java.awt.event.KeyEvent.VK_UNDEFINED?hearthborn.input.LayoutKeys.keyCode(event.c):event.code;
            return code==java.awt.event.KeyEvent.getExtendedKeyCodeForChar(match.chr)
                && (event.mods&match.modmask)==match.modmatch;
        }
        return false;
    }
    public hearthborn.ui.ActionBrowser actionBrowser(){return srchwnd instanceof hearthborn.ui.ActionBrowser?(hearthborn.ui.ActionBrowser)srchwnd:null;}
    public void toggleActionSearch(){if(srchwnd!=null){if(srchwnd.visible()&&!srchwnd.hasfocus){setfocus(srchwnd);srchwnd.setfocus(((MenuSearch)srchwnd).sbox);}else togglewnd(srchwnd);}}
    /** A cupboard, kiln, stockpile or other container keeps keyboard focus and would otherwise swallow Tab. */
    private boolean containerOpen() {
        for(Widget child = lchild; child != null; child = child.prev) {
            if(!(child instanceof Window) || child == invwnd || !child.visible()) continue;
            Window window = (Window)child;
            if(!window.children(ISBox.class).isEmpty()) return true;
            for(Inventory inventory : window.children(Inventory.class))
                if(inventory != maininv && inventory.visible() && !hearthborn.ui.StudyProgress.isStudy(inventory)) return true;
        }
        return false;
    }
    public boolean keydown(KeyDownEvent ev) {
        if(kb_inv.key().match(ev) && containerOpen()) {togglewnd(invwnd);return true;}
        if(matchesActionSearch(ev)){toggleActionSearch();return true;}
        if(ev.code==java.awt.event.KeyEvent.VK_ESCAPE&&hearthborn.inventory.BagTransfers.running(this)){hearthborn.inventory.BagTransfers.cancel(this);return true;}
        if(ev.code==java.awt.event.KeyEvent.VK_ESCAPE&&hearthborn.scripts.ScriptRunner.running(this)){hearthborn.scripts.ScriptRunner.cancel(this);return true;}
        return super.keydown(ev);
    }

    public boolean globtype(GlobKeyEvent ev) {
        if(matchesActionSearch(ev)){toggleActionSearch();return true;}
        if(ev.code==java.awt.event.KeyEvent.VK_ESCAPE && hearthborn.scripts.ScriptRunner.running(this)){hearthborn.scripts.ScriptRunner.cancel(this);return true;}
	if(ev.c == ':') {
	    entercmd();
	    return(true);
	} else if(kb_shoot.key().match(ev) && (Screenshooter.screenurl.get() != null)) {
	    Screenshooter.take(this, Screenshooter.screenurl.get());
	    return(true);
	} else if(kb_hide.key().match(ev)) {
	    toggleui();
	    return(true);
	} else if(kb_logout.key().match(ev)) {
	    act("lo");
	    return(true);
	} else if(kb_switchchr.key().match(ev)) {
	    act("lo", "cs");
	    return(true);
	} else if(kb_chat.key().match(ev)) {
	    /* A closed chat opens, an open one that has no focus takes it, and one that has it closes. */
	    if(chat.closed())
		chat.present();
	    else if(!chat.hasfocus)
		chat.focusChat();
	    else
		chat.sshow(false);
	    return(true);
	} else if(kb_map.key().match(ev)) {
	    toggleMap();
	    return(true);
	} else if((ev.c == 27) && (map != null) && !map.hasfocus) {
	    setfocus(map);
	    return(true);
	}
	return(super.globtype(ev));
    }

    private int uimode = 1;
    public void toggleui(int mode) {
	Hidepanel[] panels = {brpanel, ulpanel, umpanel, urpanel};
	switch(uimode = mode) {
	case 0:
	    for(Hidepanel p : panels)
		p.mshow(true);
	    break;
	case 1:
	    for(Hidepanel p : panels)
		p.mshow();
	    break;
	case 2:
	    for(Hidepanel p : panels)
		p.mshow(false);
	    break;
	}
    }

    public void resetui() {
	Hidepanel[] panels = {brpanel, ulpanel, umpanel, urpanel};
	for(Hidepanel p : panels)
	    p.cshow(p.tvis);
	uimode = 1;
    }

    public void toggleui() {
	toggleui((uimode + 1) % 3);
    }

    private int chatCornerReserve(){
        int span=brpanel==null?0:brpanel.sz.x;
        if(brpanel!=null&&!hearthborn.ui.Toolbars.enabled("actions")){
            hearthborn.ui.ActionMenuFrame frame=brpanel.getchild(hearthborn.ui.ActionMenuFrame.class);
            if(frame!=null)return frame.footprint().x;
        }
        return Math.max(brpw,span);
    }
    /** The vertical action column pushes the bottom button strip off the left edge. */
    private int actionColumnClearance(){
        if(!hearthborn.ui.Toolbars.enabled("actions")||chat==null)return 0;
        hearthborn.ui.ActionStrip strip=getchild(hearthborn.ui.ActionStrip.class);
        return strip==null?0:strip.leftClearance();
    }
    private boolean sidebarOnRight(){
        return hearthborn.ui.Toolbars.enabled("actions")&&hearthborn.ui.ActionStrip.onRight()&&!hearthborn.ui.ActionStrip.horizontal();
    }
    private int sidebarRightClearance(){
        if(!sidebarOnRight())return 0;
        hearthborn.ui.ActionStrip strip=getchild(hearthborn.ui.ActionStrip.class);
        return strip==null?0:strip.rightClearance();
    }
    /** Classic mode parks the system buttons in the bottom corner opposite the 4×4. */
    private int systemReserve(){
        if(hearthborn.ui.Toolbars.enabled("actions")||hearthborn.ui.SystemCross.gridEdges())return 0;
        hearthborn.ui.SystemCross cross=getchild(hearthborn.ui.SystemCross.class);
        return cross==null||!cross.visible()||cross.moved()?0:cross.sz.x;
    }
    private int chatDockLeft(){
        int strip=actionColumnClearance();
        if(classicOnLeft())return Math.max(chatCornerReserve(),strip);
        if(sidebarOnRight())return Math.max(brpanel==null?0:brpanel.sz.x,strip);
        return Math.max(strip,systemReserve());
    }
    public int chatDockWidth(){
        int right=classicOnLeft()?systemReserve():sidebarOnRight()?sidebarRightClearance():chatCornerReserve();
        return Math.max(1,sz.x-chatDockLeft()-right);
    }
    public Coord chatDockBase(){return new Coord(chatDockLeft(),sz.y);}
    /** Height of the button strip pinned under the docked chat. Zero when that strip is floating or hidden. */
    public int dockedStripHeight(){
        if(chat==null||chat.detached())return 0;
        hearthborn.ui.Toolbars.ChatMode mode=hearthborn.ui.Toolbars.chatMode();
        if(mode==hearthborn.ui.Toolbars.ChatMode.ADDITIONAL){
            Widget bar=hearthborn.ui.Toolbars.attached(this);
            return bar!=null&&bar.visible()?bar.sz.y:0;
        }
        if(beltwdg!=null&&beltwdg.visible()&&(mode==hearthborn.ui.Toolbars.ChatMode.NUMERIC||mode==hearthborn.ui.Toolbars.ChatMode.NUMERIC_FUNCTION||!(beltwdg instanceof NKeyBelt)))
            return beltwdg.sz.y;
        return 0;
    }
    private int laidChatWidth=-1;
    public void layoutChat(){
        if(chat.detached())return;
        int width=chatDockWidth();
        if(width!=laidChatWidth){laidChatWidth=width;chat.resize(width);}
        chat.anchor(chatDockBase().sub(0,dockedStripHeight()));
    }
    private void layoutActionBelt(){
        hearthborn.ui.Toolbars.layout(this);
        if(beltwdg instanceof NKeyBelt){
            NKeyBelt numeric=(NKeyBelt)beltwdg;
            numeric.extension.layout(numeric,nkeybg.sz());numeric.extension.place(numeric);
        }else beltwdg.move(new Coord(chatDockBase().x,Math.max(0,sz.y-beltwdg.sz.y)));
        layoutChat();
    }
    private int actionMenuSig = Integer.MIN_VALUE;
    private int actionMenuSignature(boolean fold, hearthborn.ui.SystemCross cross) {
        int sig = (fold ? 1 : 0) + (hearthborn.ui.ActionMenuFrame.onLeft() ? 2 : 0) + (brpanel.tvis ? 4 : 0) + uimode * 8 + (hearthborn.ui.ActionStrip.onRight() ? 32 : 0) + (hearthborn.ui.ActionStrip.horizontal() ? 64 : 0);
        sig = sig * 31 + hearthborn.ui.SystemCross.arrangement().ordinal();
        sig = sig * 31 + (hearthborn.ui.Toolbars.clean()?1:0);
        sig = sig * 31 + sz.x;
        sig = sig * 31 + sz.y;
        sig = sig * 31 + brpanel.sz.x;
        sig = sig * 31 + brpanel.sz.y;
        // Attached system buttons must follow Hidepanel's show/hide animation too.
        sig = sig * 31 + brpanel.c.x;
        sig = sig * 31 + brpanel.c.y;
        sig = sig * 31 + ((menu != null && menu.visible) ? 1 : 0);
        if(cross != null) {
            sig = sig * 31 + cross.sz.x;
            sig = sig * 31 + cross.sz.y;
            sig = sig * 31 + cross.c.x;
            sig = sig * 31 + cross.c.y;
            sig = sig * 31 + (cross.visible() ? 1 : 0);
            sig = sig * 31 + (cross.moved() ? 1 : 0);
        }
        return sig;
    }
    /** Left swaps the classic grid and the system buttons. The sidebar menu keeps them on the right. */
    private boolean classicOnLeft(){
        return hearthborn.ui.ActionMenuFrame.onLeft()&&!hearthborn.ui.Toolbars.enabled("actions");
    }
    /** Sidebar menu replaces the 4×4. Classic mode keeps the grid and puts the system buttons in the opposite bottom corner. */
    public void syncActionMenu() {
        if(brpanel == null) return;
        hearthborn.ui.SystemCross cross = getchild(hearthborn.ui.SystemCross.class);
        if(cross != null && cross.dragging()) return;
        boolean fold = hearthborn.ui.Toolbars.enabled("actions") && cross != null;
        if(!brpanel.tvis) {
            if(actionMenuSignature(fold, cross) != actionMenuSig) {
                if(cross != null) {
                    if(hearthborn.ui.SystemCross.gridEdges())cross.hide();
                    else if(uimode == 1 && !fold) placeSystem(cross, classicSystemCorner(cross));
                    else if(cross.moved()) cross.show();
                    else cross.hide();
                }
                actionMenuSig = actionMenuSignature(fold, cross);
                layoutChat();
            }
            return;
        }
        if(actionMenuSignature(fold, cross) == actionMenuSig) return;
        if(menu != null) menu.show(!fold);
        for(hearthborn.ui.ActionMenuFrame frame : brpanel.children(hearthborn.ui.ActionMenuFrame.class)) frame.show(!fold);
        if(fold) {
            if(cross.moved()) {
                brpanel.resize(Coord.z);
                brpanel.move();
                cross.show();
            } else {
                brpanel.resize(cross.sz);
                brpanel.move();
                cross.dock(brpanel.c);
            }
        } else {
            for(hearthborn.ui.ActionMenuFrame frame : brpanel.children(hearthborn.ui.ActionMenuFrame.class)) frame.fit();
            brpanel.pack();
            brpanel.move();
            if(cross != null){
                if(hearthborn.ui.SystemCross.gridEdges()){
                    hearthborn.ui.ActionMenuFrame frame=brpanel.getchild(hearthborn.ui.ActionMenuFrame.class);
                    if(frame!=null)cross.attachEdges(frame);
                }else placeSystem(cross, classicSystemCorner(cross));
            }
        }
        actionMenuSig = actionMenuSignature(fold, cross);
        layoutChat();
    }
    /** A menu the player has dragged stays where they left it. */
    private void placeSystem(hearthborn.ui.SystemCross cross, Coord automatic) {
        if(cross.dragging()) return;
        if(cross.moved()) { cross.show(); return; }
        cross.dock(automatic);
    }
    private Coord classicSystemCorner(hearthborn.ui.SystemCross cross) {
        boolean menuOnLeft = hearthborn.ui.ActionMenuFrame.onLeft();
        return Coord.of(menuOnLeft ? sz.x - cross.sz.x : 0, Math.max(0, sz.y - cross.sz.y));
    }

    /** Keep the portrait cluster, the clock and the combat panel on the sides chosen in UI settings. */
    public void anchorHudSides(){
        if(ulpanel!=null)ulpanel.move();
        if(urpanel!=null)urpanel.move();
        if(umpanel!=null)umpanel.move();
    }
    public void layoutHudSides(){
        if(buffs instanceof Bufflist)((Bufflist)buffs).relayout();
        hearthborn.ui.StatusMeters meters=ulpanel==null?null:ulpanel.getchild(hearthborn.ui.StatusMeters.class);
        if(meters!=null)meters.tick(0);
        else anchorHudSides();
    }
    /** Resize the portrait cluster after its own scale, or after panel scale, so panel scale does not magnify it. */
    /** Re-place the button panels after panel scale changed; windows and the map are not touched. */
    public void relayoutPanels(){
        actionMenuSig=Integer.MIN_VALUE;
        if(brpanel!=null)brpanel.move();
        syncActionMenu();
        layoutActionBelt();
        if(compactLayout!=null)compactLayout.refresh();
        anchorHudSides();
    }
    public void applyPortraitScale(){
        if(portrait instanceof hearthborn.ui.HudPortrait)((hearthborn.ui.HudPortrait)portrait).applyScale();
        if(ulpanel!=null)for(Widget child=ulpanel.child;child!=null;child=child.next)
            if(child instanceof IMeter)child.resize(hearthborn.ui.StatusScale.size(IMeter.fsz));
        if(buffs instanceof hearthborn.ui.HudBuffs)((hearthborn.ui.HudBuffs)buffs).applyScale();
        hearthborn.ui.StatusMeters meters=ulpanel==null?null:ulpanel.getchild(hearthborn.ui.StatusMeters.class);
        if(meters!=null)meters.applyScale();
        else if(ulpanel!=null)anchorHudSides();
    }

    public void resize(Coord sz) {
        super.resize(sz);
        if(brpanel!=null)brpanel.move();
        syncActionMenu();
        layoutChat();
        if(questHelper!=null)questHelper.layout(questBottom());
	if(map != null)
	    map.resize(sz);
	if(!mapAnchored && mapfile != null && sz.x > UI.scale(200) && sz.y > UI.scale(200)
	    && !mapfile.frameBoosted && !hearthborn.ui.WindowPlaces.wasMoved(mapfile)) {
	    mapAnchored = true;
	    Coord saved = hearthborn.map.MapPlaces.position(mapfile.deco==null);
	if(saved != null)
		mapfile.move(saved);
	}
	hearthborn.ui.WindowPlaces.settle(this);
        hearthborn.ui.MagneticWindows.tick(this);
        if(mapfile!=null&&!mapfile.frameBoosted&&!compactUI())mapfile.fitOnScreen();
	if(chat != null) chat.fitWindow();
	if(prog != null)
	    prog.move(sz.sub(prog.sz).mul(0.5, 0.35));
        layoutActionBelt();
        if(compactLayout!=null)compactLayout.refresh();
    }
    
    public void presize() {
	resize(parent.sz);
    }
    
    public static interface LogMessage extends UI.Notice {
	public ChatUI.Channel.Message logmessage();
    }

    /** Native notification line without appending to System chat or selecting it. */
    public void showNotice(String message,Color color) {
        double now=Utils.rtime();
        String shown=hearthborn.ui.Inspect.withRefill(hearthborn.L10n.text("msg",message),message);
        boolean error=color!=null&&color.getRed()>140&&color.getGreen()<90&&color.getBlue()<90;
        if(!error&&!noticeError&&noticeText!=null&&now-msgtime<1.0)shown=hearthborn.ui.Inspect.status(noticeText,shown);
        msgtime=now;noticeText=shown;noticeError=error;
        if(lastmsg!=null)lastmsg.dispose();
        lastmsg=hearthborn.ui.Inspect.plate(shown,color);
    }

    /** Keep helper errors in System history without changing the selected chat. */
    public void showErrorNotice(String message) {
        showNotice(message, UI.ErrorMessage.color);
        if(syslog != null)
            syslog.append(hearthborn.L10n.text("msg", message), UI.ErrorMessage.color);
    }

    /** A chat line on the notice plate, without translating it or mixing it into an inspect result. */
    public void showPlainNotice(String message, Color color) {
        if(message == null || message.isEmpty()) return;
        msgtime = Utils.rtime();
        noticeText = message;
        noticeError = false;
        if(lastmsg != null) lastmsg.dispose();
        lastmsg = hearthborn.ui.Inspect.plate(message, color == null ? Color.WHITE : color);
    }

    private static boolean quietStudy(String message){
        if(message==null)return false;
        String english="You are already studying that.";
        return english.equals(message)||message.equals(hearthborn.L10n.text("msg",english));
    }

    public boolean msg(UI.Notice msg) {
        msg=inspect.namedNotice(msg);
        if(hearthborn.ui.LoginSiegeCheck.clear(msg.message()))return true;
        hearthborn.ui.LoginToggles.notice(this,msg.message());
        if(hearthborn.inventory.NativeStacking.notice(this,msg.message()))return true;
	if(msg.handler(this))
	    return(true);
        Color noticeColor=msg.color();
        if(noticeColor!=null&&noticeColor.getRed()>140&&noticeColor.getGreen()<90&&noticeColor.getBlue()<90)noticeColor=UI.ErrorMessage.color;
	ChatUI.Channel.Message logged;
	if(msg instanceof LogMessage)
	    logged = ((LogMessage)msg).logmessage();
	else
	    logged = new ChatUI.Channel.SimpleMessage(hearthborn.ui.Inspect.withRefill(hearthborn.L10n.text("msg", msg.message()), msg.message()), noticeColor);
        showNotice(msg.message(),noticeColor);
	if(!quietStudy(msg.message())){
	    syslog.append(logged);
	    if(chat.sel != syslog)
		chat.select(syslog, false);
	}
	ui.sfxrl(msg.sfx());
	return(true);
    }

    public void error(String msg) {
	ui.error(msg);
    }
    
    public void act(String... args) {
        if(hearthborn.navigation.Walking.hearthAction(args))
            hearthborn.navigation.Walking.abandon(map);
	wdgmsg("act", (Object[])args);
    }

    public void act(int mods, Coord mc, Gob gob, String... args) {
	int n = args.length;
	Object[] al = new Object[n];
	System.arraycopy(args, 0, al, 0, n);
	if(mc != null) {
	    al = Utils.extend(al, al.length + 2);
	    al[n++] = mods;
	    al[n++] = mc;
	    if(gob != null) {
		al = Utils.extend(al, al.length + 2);
		al[n++] = (int)gob.id;
		al[n++] = gob.rc;
	    }
	}
	wdgmsg("act", al);
    }

    public class FKeyBelt extends Belt implements DTarget, DropTarget {
	public final int beltkeys[] = {KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4,
				       KeyEvent.VK_F5, KeyEvent.VK_F6, KeyEvent.VK_F7, KeyEvent.VK_F8,
				       KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12};
	public int curbelt = 0;

	public FKeyBelt() {
	    super(UI.scale(new Coord(450, 34)));
	}

	private Coord beltc(int i) {
	    return(new Coord((((invsq.sz().x + UI.scale(2)) * i) + (10 * (i / 4))), 0));
	}
    
	public int beltslot(Coord c) {
	    for(int i = 0; i < 12; i++) {
		if(c.isect(beltc(i), invsq.sz()))
		    return(i + (curbelt * 12));
	    }
	    return(-1);
	}
    
	public void draw(GOut g) {
	    for(int i = 0; i < 12; i++) {
		int slot = i + (curbelt * 12);
		Coord c = beltc(i);
		hearthborn.ui.ToolbarFrame.slot(g,c,invsq.sz());
		try {
		    if(belt[slot] != null)
			belt[slot].draw(g.reclip(c.add(UI.scale(1), UI.scale(1)), invsq.sz().sub(UI.scale(2), UI.scale(2))));
		} catch(Loading e) {}
		g.chcolor(156, 180, 158, 255);
		FastText.aprintf(g, c.add(invsq.sz().sub(UI.scale(2), 0)), 1, 1, "F%d", i + 1);
		g.chcolor();
	    }
	}
	
	public boolean globtype(GlobKeyEvent ev) {
	    boolean M = (ev.mods & KeyMatch.M) != 0;
	    for(int i = 0; i < beltkeys.length; i++) {
		if(ev.code == beltkeys[i]) {
		    if(M) {
			curbelt = i;
			return(true);
		    } else {
			keyact(i + (curbelt * 12));
			return(true);
		    }
		}
	    }
	    return(super.globtype(ev));
	}
    }
    
    private static final Tex nkeybg = Resource.loadtex("gfx/hud/hb-main");
    public class NKeyBelt extends Belt {
        private final hearthborn.ui.DockedToolbar extension = new hearthborn.ui.DockedToolbar(GameUI.this);
	public int curbelt = 0;

	public NKeyBelt() {
	    super(nkeybg.sz());
	}
	
        public void rememberPosition(){extension.rememberPosition(this);}
        public int beltslot(Coord c) {return extension.slot(extension.logical(c),curbelt);}

        public boolean mousedown(MouseDownEvent event){
            if(extension.handle(this,event)){extension.layout(this,nkeybg.sz());extension.place(this);return true;}
            if(extension.pageHit(extension.logical(event.c))){
                if(event.b==1)curbelt=(curbelt+1)%10;
                else if(event.b==3)curbelt=(curbelt+9)%10;
                return true;
            }
            return super.mousedown(event);
        }
        public void mousemove(MouseMoveEvent event){if(!extension.mousemove(this,event))super.mousemove(event);}
        public boolean mouseup(MouseUpEvent event){return extension.mouseup(this,event)||super.mouseup(event);}
        public Object tooltip(Coord at,Widget previous){
            if(beltslot(at)<0||ui.modmeta){
                if(extension.pageHit(extension.logical(at)))return hearthborn.L10n.tr("Next page. Right-click for the previous page.");
                return hearthborn.ui.ToolbarFrame.moveTip();
            }
            return extension.tooltip(beltslot(at),previous!=this);
        }

	public void draw(GOut g) {
            GOut original=g;g=extension.graphics(g);
	    extension.background(g, extension.logicalSize(sz), nkeybg);
            extension.draw(g,curbelt);
	    super.draw(original);
	}
	
        public void tick(double dt) { hearthborn.ui.Toolbars.layout(GameUI.this);extension.layout(this,nkeybg.sz());extension.place(this);super.tick(dt); }
        public void dispose() { extension.dispose(); super.dispose(); }

	public boolean globtype(GlobKeyEvent ev) {
            if(hearthborn.ui.Toolbars.functionKey(GameUI.this,ev))return true;
	    if((ev.code < KeyEvent.VK_0) || (ev.code > KeyEvent.VK_9))
		return(super.globtype(ev));
	    int i = Utils.floormod(ev.code - KeyEvent.VK_0 - 1, 10);
	    boolean M = (ev.mods & KeyMatch.M) != 0;
	    if(M) {
		curbelt = i;
	    } else {
		keyact(i + (curbelt * 12));
	    }
	    return(true);
	}
    }
    
    {
	String val = Utils.getpref("belttype", "n");
	if(val.equals("n")) {
	    beltwdg = add(new NKeyBelt());
	} else if(val.equals("f")) {
	    beltwdg = add(new FKeyBelt());
	} else {
	    beltwdg = add(new NKeyBelt());
	}
    }
    
    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
        cmdmap.put("rmseg", (cons, args) -> {
            if(map == null || mapfile == null) return;
            try {
                MiniMap.Location location = mapfile.view.resolve(new MiniMap.MapLocator(map));
                if(location != null) try(Locked lock = new Locked(mapfile.file.lock.writeLock())) {
                    mapfile.file.segments.remove(location.seg.id);
                }
            } catch(Loading waiting) {}
        });
	cmdmap.put("afk", (cons, args) -> {
	    afk = true;
	    wdgmsg("afk");
	});
	cmdmap.put("act", (cons, args) -> {
		Object[] ad = new Object[args.length - 1];
		System.arraycopy(args, 1, ad, 0, ad.length);
		wdgmsg("act", ad);
	});
	cmdmap.put("belt", (cons, args) -> {
	    if(args[1].equals("f")) {
		beltwdg.destroy();
		beltwdg = add(new FKeyBelt());
		Utils.setpref("belttype", "f");
		resize(sz);
	    } else if(args[1].equals("n")) {
		beltwdg.destroy();
		beltwdg = add(new NKeyBelt());
		Utils.setpref("belttype", "n");
		resize(sz);
	    }
	});
	cmdmap.put("chrmap", (cons, args) -> {
	    Utils.setpref("mapfile/" + GameUI.this.chrid, args[1]);
	});
	cmdmap.put("tool", (cons, args) -> {
	    try {
		Object[] wargs = new Object[args.length - 2];
		for(int i = 0; i < wargs.length; i++)
		    wargs[i] = args[i + 2];
		add(gettype(args[1]).create(ui, wargs), 200, 200);
	    } catch(RuntimeException e) {
		e.printStackTrace(Debug.log);
	    }
	});
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
