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
import haven.render.*;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class GItem extends AWidget implements ItemInfo.SpriteOwner, GSprite.Owner, RandomSource {
    public Indir<Resource> res;
    public MessageBuf sdt;
    public int meter = 0;
    public long meterUpdated;
    public int num = -1;
    public Widget contents = null;
    public String contentsnm = null;
    public Object contentsid = null;
    public ContentsWindow contentswnd = null;
    /** Set once the automatic drop rules have judged this item. */
    public boolean autoDropChecked = false;
    public boolean markerChecked = false;
    public int infoseq;
    private Widget hovering;
    private boolean hoverset;
    private GSprite spr;
    private ItemInfo.Raw rawinfo;
    private List<ItemInfo> info = Collections.emptyList();

    @RName("item")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Indir<Resource> res = ui.sess.getresv(args[0]);
	    Message sdt = (args.length > 1) ? new MessageBuf((byte[])args[1]) : Message.nil;
	    return(new GItem(res, sdt));
	}
    }

    public interface RStateInfo {
	public Pipe.Op rstate();

	public static final Function<List<ItemInfo>, Supplier<Pipe.Op>> combine = info -> {
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
	};
    }

    public interface ColorInfo extends RStateInfo {
	public Color olcol();

	public default Pipe.Op rstate() {
	    return(buf -> {
		    Color col = olcol();
		    if(col != null)
			new ColorMask(col).apply(buf);
		});
	}
    }

    public interface OverlayInfo<T> {
	public T overlay();
	public void drawoverlay(GOut g, T data);
    }

    public static class InfoOverlay<T> {
	public final OverlayInfo<T> inf;
	public final T data;

	public InfoOverlay(OverlayInfo<T> inf) {
	    this.inf = inf;
	    this.data = inf.overlay();
	}

	public void draw(GOut g) {
	    inf.drawoverlay(g, data);
	}

	public static <S> InfoOverlay<S> create(OverlayInfo<S> inf) {
	    return(new InfoOverlay<S>(inf));
	}
    }

    public interface NumberInfo extends OverlayInfo<Tex> {
	public int itemnum();
	public default Color numcolor() {
	    return(Color.WHITE);
	}

	public default Tex overlay() {
	    Color col = numcolor();
	    return(new TexI(hearthborn.QualityOverlay.styled(Integer.toString(itemnum()), col)));
	}

	/** Item counts, remaining uses and gilding sit in the bottom-right. Quality has its own overlay. */
	public static boolean gildingClass(String name) {
	    return(name != null && name.endsWith("tt.slots.ISlots"));
	}

	public static boolean stackCount(String name) {
	    return(name != null && name.equals("haven.GItem$Amount"));
	}

	public default void drawoverlay(GOut g, Tex tex) {
	    /* With quality and count swapped the count and the gilding go to the top-right, where quality was. */
	    if(hearthborn.QualityStyle.swapped())
		g.aimage(tex, Coord.of(g.sz().x, 0), 1, 0);
	    else
		g.aimage(tex, g.sz(), 1, 1);
	}

	public static BufferedImage numrender(int num, Color col) {
	    return(Utils.outline2(Text.render(Integer.toString(num), col).img, Utils.contrast(col)));
	}
    }

    public interface MeterInfo {
	public double meter();
    }

    public static class Amount extends ItemInfo implements NumberInfo {
	private final int num;

	public Amount(Owner owner, int num) {
	    super(owner);
	    this.num = num;
	}

	public int itemnum() {
	    return(num);
	}
    }

    public GItem(Indir<Resource> res, Message sdt) {
	this.res = res;
	this.sdt = new MessageBuf(sdt);
    }

    public GItem(Indir<Resource> res) {
	this(res, Message.nil);
    }

    private Random rnd = null;
    public Random mkrandoom() {
	if(rnd == null)
	    rnd = new Random();
	return(rnd);
    }
    public Resource getres() {return(res.get());}
    public void wdgmsg(String msg, Object... args) {
	if("iact".equals(msg)) hearthborn.ui.FlowerChoices.itemMenu(ui);
	super.wdgmsg(msg, args);
    }
    private static final OwnerContext.ClassResolver<GItem> ctxr = new OwnerContext.ClassResolver<GItem>()
	.add(GItem.class, wdg -> wdg)
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    public <T> T context(Class<T> cl) {return(ctxr.context(cl, this));}

    public GSprite spr() {
	GSprite spr = this.spr;
	if(spr == null) {
	    try {
		spr = this.spr = GSprite.create(this, res.get(), sdt.clone());
	    } catch(Loading l) {
	    }
	}
	return(spr);
    }

    public void tick(double dt) {
	super.tick(dt);
	GSprite spr = spr();
	if(spr != null)
	    spr.tick(dt);
	updcontinfo();
	hearthborn.inventory.AutoDrop.check(this);
	if(!markerChecked)
	    markerChecked = hearthborn.map.MarkerQuality.item(this);
	if(!hoverset)
	    hovering = null;
	hoverset = false;
    }

    public List<ItemInfo> info() {
	if(this.info == null) {
	    List<ItemInfo> info = ItemInfo.buildinfo(this, rawinfo);
	    addcontinfo(info);
	    Resource resource = res.get();
	    Resource.Pagina pg = resource.layer(Resource.pagina);
	    boolean stack = "gfx/invobjs/stack".equals(resource.name) || hearthborn.QualityOverlay.stacked(this);
	    if(pg != null && !stack)
		info.add(new ItemInfo.Pagina(this, pg.text));
	    hearthborn.food.FishFry.add(this, info);
	    if(stack) {
		hearthborn.QualityOverlay.presentAverage(this, info);
		hearthborn.ui.StackTips.present(this, info);
	    }
	    stripStackGuide(info);
	    this.info = info;
	}
	return(this.info);
    }

    /** The server attaches the stack instructions through its own tooltip page, not only the item resource. */
    private static void stripStackGuide(List<ItemInfo> info) {
	for(Iterator<ItemInfo> tips = info.iterator(); tips.hasNext();) {
	    ItemInfo tip = tips.next();
	    if(!(tip instanceof ItemInfo.Pagina)) continue;
	    String text = ((ItemInfo.Pagina)tip).doc.text;
	    if(text != null && (text.contains("To stack items") || text.contains("On the stacks themselves")
		|| text.contains("Чтобы сложить предметы в стак") || text.contains("На самих стаках")))
		tips.remove();
	}
    }

    public Resource resource() {
	return(res.get());
    }

    public GSprite sprite() {
	if(spr == null)
	    throw(new Loading("Still waiting for sprite to be constructed"));
	return(spr);
    }

    public void uimsg(String name, Object... args) {
        if(name=="tt"||name=="chres"||name=="num")hearthborn.ui.HandMenu.invalidate();
	if(name == "num") {
	    num = Utils.iv(args[0]);
	} else if(name == "chres") {
            if(markerChecked && hearthborn.map.MarkerQuality.bucketSample(res)) markerChecked = false;
	    synchronized(this) {
		res = ui.sess.getresv(args[0]);
		sdt = (args.length > 1) ? new MessageBuf((byte[])args[1]) : MessageBuf.nil;
		spr = null;
	    }
	} else if(name == "tt") {
            if(markerChecked && hearthborn.map.MarkerQuality.bucketSample(res))
                markerChecked = false;
            meterUpdated=System.currentTimeMillis();
	    info = null;
	    rawinfo = new ItemInfo.Raw(args);
	    infoseq++;
	} else if(name == "meter") {
            meterUpdated=System.currentTimeMillis();
	    meter = Utils.iv(args[0]);
	    hearthborn.ui.ItemMeter.widget(this, meter);
	} else if(name == "contopen") {
	    if(contentswnd != null) {
		boolean nst;
		if(args[0] == null)
		    nst = (contentswnd.st != "wnd");
		else
		    nst = Utils.bv(args[0]);
		contentswnd.wndshow(nst);
	    }
	} else {
	    super.uimsg(name, args);
	}
    }

    public void addchild(Widget child, Object... args) {
	/* XXX: Update this to use a checkable args[0] once a
	 * reasonable majority of clients can be expected to not crash
	 * on that. */
	if(true || ((String)args[0]).equals("contents")) {
	    contents = child;
	    contentsnm = (String)args[1];
	    contentsid = null;
	    if(args.length > 2)
		contentsid = args[2];
	    contentswnd = contparent().add(new ContentsWindow(this, contents));
	}
    }

    public static interface ContentsInfo {
	public void propagate(List<ItemInfo> buf, ItemInfo.Owner outer);
    }

    private int lastcontseq;
    private List<Pair<GItem, Integer>> lastcontinfo = null;
    private void updcontinfo() {
	if(info == null)
	    return;
	Widget contents = this.contents;
	if(contents != null) {
	    boolean upd = false;
	    if((lastcontinfo == null) || (lastcontseq != contents.childseq)) {
		lastcontinfo = new ArrayList<>();
		for(Widget ch : contents.children()) {
		    if(ch instanceof GItem) {
			GItem item = (GItem)ch;
			lastcontinfo.add(new Pair<>(item, item.infoseq));
		    }
		}
		lastcontseq = contents.childseq;
		upd = true;
	    } else {
		for(ListIterator<Pair<GItem, Integer>> i = lastcontinfo.listIterator(); i.hasNext();) {
		    Pair<GItem, Integer> ch = i.next();
		    if(ch.b != ch.a.infoseq) {
			i.set(new Pair<>(ch.a, ch.a.infoseq));
			upd = true;
		    }
		}
	    }
	    if(upd) {
		info = null;
		infoseq++;
	    }
	} else {
	    lastcontinfo = null;
	}
    }

    private void addcontinfo(List<ItemInfo> buf) {
	Widget contents = this.contents;
	if(contents != null) {
	    for(Widget ch : contents.children()) {
		if(ch instanceof GItem) {
		    for(ItemInfo inf : ((GItem)ch).info()) {
			if(inf instanceof ContentsInfo)
			    ((ContentsInfo)inf).propagate(buf, this);
		    }
		}
	    }
	}
    }

    private Widget contparent() {
	/* XXX: This is a bit weird, but I'm not sure what the alternative is... */
	Widget cont = getparent(GameUI.class);
	return((cont == null) ? cont = ui.root : cont);
    }

    public void destroy() {
	if(contents != null) {
	    contents.reqdestroy();
	    contents = null;
	}
	if(contentswnd != null) {
	    contentswnd.reqdestroy();
	    contentswnd = null;
	}
	super.destroy();
    }

    public void hovering(Widget hovering) {
	this.hovering = hovering;
	this.hoverset = true;
    }

    public static class HoverDeco extends Window.Deco {
	public static final Coord hovermarg = UI.scale(12, 12);
	/** A one-pixel line close around the items instead of the full window moulding. */
	public static final Coord inset = UI.scale(3, 3);
	public Area ca;
	private UI.Grab dm = null;
	private Coord doff;

	public void iresize(Coord isz) {
	    ca = Area.sized(hovermarg.add(inset), isz);
	    resize(ca.br.add(inset));
	}

	public Area contarea() {
	    return(ca);
	}

	public void draw(GOut g) {
	    Coord bgc = new Coord();
	    Coord ctl = hovermarg;
	    Coord cbr = sz;
	    for(bgc.y = ctl.y; bgc.y < cbr.y; bgc.y += Window.bg().sz().y) {
		for(bgc.x = ctl.x; bgc.x < cbr.x; bgc.x += Window.bg().sz().x)
		    g.image(Window.bg(), bgc, ctl, cbr);
	    }
	    g.chcolor(hearthborn.ui.AccentTheme.blue() ? new java.awt.Color(0x4D, 0xA6, 0xD3, 220) : new java.awt.Color(0xDA, 0xB9, 0x70, 220));
	    g.rect2(hovermarg, sz.sub(1, 1));
	    g.chcolor();
	    super.draw(g);
	}

	public boolean checkhit(Coord c) {
	    return((c.x >= hovermarg.x) && (c.y >= hovermarg.y));
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this))
		return(true);
	    if(checkhit(ev.c) && (ev.b == 1)) {
		dm = ui.grabmouse(this);
		doff = ev.c;
		return(true);
	    }
	    return(super.mousedown(ev));
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if((dm != null) && (ev.b == 1)) {
		dm.remove();
		dm = null;
		return(true);
	    }
	    return(super.mouseup(ev));
	}

	public void mousemove(MouseMoveEvent ev) {
	    super.mousemove(ev);
	    if(dm != null) {
		if(ev.c.dist(doff) > 10) {
		    dm.remove();
		    dm = null;
		    ContentsWindow wnd = (ContentsWindow)parent;
		    wnd.drag(doff);
		    wnd.chstate("wnd");
		}
	    }
	}
    }

    public static class ContentsWindow extends Window {
	public static final Coord overlap = UI.scale(2, 2);
	public final GItem cont;
	public final Widget inv;
	/** What the window really holds: the contents themselves, or a magnifier around a bare stack row. */
	private final Widget view;
	private final Object id;
	private Coord psz = null;
	private String st;
	private boolean hovering;
        private Widget hoverSource;

	public ContentsWindow(GItem cont, Widget inv) {
	    super(Coord.z, cont.contentsnm);
	    this.cont = cont;
	    this.inv = inv;
	    this.view = add(inv instanceof Inventory ? inv : new hearthborn.inventory.ScaledContents(inv, () -> sourceScale(cont), this::cdestroy), Coord.z);
	    this.id = cont.contentsid;
	    this.tick(0);
	    Coord c = null;
	    if(Utils.getprefb(String.format("cont-wndvis/%s", id), false))
		c = Utils.getprefc(String.format("cont-wndc/%s", id), null);
	    if(c != null) {
		chstate("wnd");
		this.c = c;
	    } else {
		chstate("hide");
	    }
	}

	/** A stack opens as large as the cells it sits in. */
	private static double sourceScale(GItem item) {
	    if(item.parent instanceof Inventory)
		return(((Inventory)item.parent).viewScale());
	    if(item.parent instanceof Equipory)
		return(Equipory.SCALE);
	    return(hearthborn.inventory.InventoryScale.SCALE);
	}

	private void chstate(String nst) {
	    if(nst == st)
		return;
	    if(st == "wnd") {
		if(id != null)
		    Utils.setprefb(String.format("cont-wndvis/%s", id), false);
	    }
	    st = nst;
	    if(nst == "hide") {
		hide();
	    } else if(nst == "hover") {
		chdeco(new HoverDeco());
		show();
		z(90);
		if(parent != null)
		    raise();
	    } else if(nst == "wnd") {
		chdeco(makedeco());
		show();
		z(0);
		if(parent != null)
		    raise();
		GameUI opened=getparent(GameUI.class);
		if(opened!=null&&opened.compactUI())opened.compactLayout.reveal(this);
		if(id != null)
		    Utils.setprefb(String.format("cont-wndvis/%s", id), true);
	    }
	}

	private void ckhover() {
	    Widget hover = cont.hovering;
	    if(hover != null) {
		ckparent: for(Widget prev : parent.children()) {
		    if((prev instanceof ContentsWindow) && (((ContentsWindow)prev).st == "hover")) {
			for(Widget p = hover; p != null; p = p.parent) {
			    if(p == prev)
				break ckparent;
			    if(p instanceof ContentsWindow)
				break;
			}
			/* Another stack is under the pointer: the earlier one gives way at once, unless the pointer is really inside it. */
			ContentsWindow other = (ContentsWindow)prev;
			if(other.pointerOnPopup())
			    return;
			other.dismissHover();
		    }
		}
                hoverSource=hover;
                for(Widget owner=hover;owner!=null;owner=owner.parent)if(owner instanceof ContentsWindow){
                    Widget source=((ContentsWindow)owner).hoverSource;
                    if(source!=null)hoverSource=source;
                    break;
                }
		chstate("hover");
		move(belowPointer());
	    }
	}

	/** The stack opens just under the pointer, so the click still lands on the stack icon. */
	public static final Coord pointerGap = UI.scale(0, 8);
	public static Coord belowPointer(Coord cursor, Coord popup, Coord bounds) {
	    return(belowPointer(cursor, popup, bounds, pointerGap));
	}
	private static Coord belowPointer(Coord cursor, Coord popup, Coord bounds, Coord gap) {
	    int width = popup == null ? 0 : popup.x;
	    int height = popup == null ? 0 : popup.y;
	    Coord at = Coord.of(cursor.x - width / 2, cursor.y + gap.y);
	    if(bounds != null && popup != null) {
		if(at.y + height > bounds.y)
		    at = Coord.of(at.x, Math.max(0, cursor.y - height - gap.y));
		if(at.x + width > bounds.x)
		    at = Coord.of(Math.max(0, bounds.x - width), at.y);
		if(at.x < 0)
		    at = Coord.of(0, at.y);
	    }
	    return(at);
	}

	private Coord belowPointer() {
            if(hoverSource!=null&&hoverSource.parent instanceof hearthborn.ui.EquipmentStrip)
                return ((hearthborn.ui.EquipmentStrip)hoverSource.parent).previewPosition(this,hoverSource);
	    Coord cursor = parent == null || ui == null ? Coord.z : parent.rootxlate(ui.mc);
	    return(belowPointer(cursor, hearthborn.ui.HudInterfaceScale.size(this), parent == null ? null : parent.sz,
			hearthborn.ui.HudInterfaceScale.toParent(this, pointerGap)));
	}

	/** Gap and the frame margin are not clickable, but the pointer still crosses them into the belt. */
	public static boolean reaches(Coord pointer, Coord popupAt, Coord popupSize, Coord pad) {
	    return(pointer.isect(popupAt.sub(pad), popupSize.add(pad.mul(2))));
	}

	/** The clickable part of the popup, without the gap and the frame margin that only keep it open. */
	public boolean pointerOnPopup() {
	    if(ui == null || parent == null) return(false);
	    Coord marg = hearthborn.ui.HudInterfaceScale.toParent(this, HoverDeco.hovermarg);
	    return(reaches(parent.rootxlate(ui.mc), c.add(marg), hearthborn.ui.HudInterfaceScale.size(this).sub(marg), Coord.z));
	}

        /** An open child preview also keeps its source preview alive. */
        public boolean pointerOnPreviewTree() {
            if(st != "hover")return false;
            if(pointerOnPopup())return true;
            for(GItem item:children(GItem.class))
                if(item.contentswnd!=null&&item.contentswnd.pointerOnPreviewTree())return true;
            return false;
        }

	private boolean pointerInPopup() {
	    if(ui == null || parent == null) return(false);
	    Coord pad = hearthborn.ui.HudInterfaceScale.toParent(this, Coord.of(UI.scale(16), pointerGap.y + HoverDeco.hovermarg.y));
	    return(reaches(parent.rootxlate(ui.mc), c, hearthborn.ui.HudInterfaceScale.size(this), pad));
	}

	private void ckunhover() {
	    if((cont.hovering == null) && !hovering && !pointerInPopup()) {
		boolean hasmore = false;
		for(GItem item : children(GItem.class)) {
		    if((item.contentswnd != null) && (item.contentswnd.st == "hover")) {
			hasmore = true;
			break;
		    }
		}
		if(!hasmore)
		    chstate("hide");
	    }
	}

	private Coord lc = null;
	public void tick(double dt) {
	    super.tick(dt);
	    if(st == "hide") {
		ckhover();
	    } else if(st == "hover") {
		ckunhover();
	    }
	    if(!Utils.eq(view.sz, psz))
		resize(view.c.add(psz = view.sz));
	    if(st == "wnd") {
		if(!Utils.eq(lc, this.c) && (id != null))
		    Utils.setprefc(String.format("cont-wndc/%s", id), lc = this.c);
	    }
	}

	public void reqclose() {
	    chstate("hide");
	}

        /** Replacing a toolbar hover closes only transient previews, including nested bags. */
        public void dismissHover() {
            if(st != "hover")return;
            cont.hovering=null;cont.hoverset=false;hovering=false;
            for(GItem item:children(GItem.class))
                if(item.contentswnd!=null)item.contentswnd.dismissHover();
            chstate("hide");
        }

	public void cdestroy(Widget w) {
	    super.cdestroy(w);
	    if(w == inv) {
		cont.contents = null;
		cont.contentsnm = null;
		cont.contentsid = null;
		cont.contentswnd = null;
		this.destroy();
	    }
	}

	public boolean mousehover(MouseHoverEvent ev, boolean on) {
	    hovering = on;
	    return(true);
	}

	public void wndshow(boolean show) {
	    if(show && (st != "wnd")) {
		Coord wc = null;
		if(id != null)
		    wc = Utils.getprefc(String.format("cont-wndc/%s", id), null);
		if(st == "hide") {
		    if(wc == null)
			wc = cont.rootxlate(ui.mc).add(overlap);
		}
		chstate("wnd");
		if(wc != null)
		    move(wc);
	    } else if(!show && (st == "wnd")) {
		chstate("hide");
	    }
	}
    }
}
