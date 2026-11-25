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
import haven.ItemInfo.AttrCache;
import static haven.ItemInfo.find;
import static haven.Inventory.sqsz;

public class WItem extends Widget implements DTarget {
    public static final Resource missing = Resource.local().loadwait("gfx/invobjs/missing");
    public final GItem item;
    private final hearthborn.QualityOverlay qualityOverlay = new hearthborn.QualityOverlay();
    private final hearthborn.ui.ProcessItemOverlay processOverlay=new hearthborn.ui.ProcessItemOverlay();
    private final hearthborn.ItemCondition condition = new hearthborn.ItemCondition();
    private final hearthborn.ui.ClaimPresenceOverlay claimPresence = new hearthborn.ui.ClaimPresenceOverlay();
    private Resource cspr = null;
    private Message csdt = Message.nil;

    public WItem(GItem item) {
	super(sqsz);
	this.item = item;
    }

    public Coord parentpos(Widget in){
        if(parent instanceof Inventory&&in!=this)
            return parent.parentpos(in,hearthborn.inventory.InventoryScale.size(c));
        return super.parentpos(in);
    }

    private final hearthborn.ui.StudyOverlay studyOverlay = new hearthborn.ui.StudyOverlay();
    private final hearthborn.ui.MeterPercent progress = new hearthborn.ui.MeterPercent();
    private final hearthborn.cheese.CheeseCountdown cheeseTime=new hearthborn.cheese.CheeseCountdown();
    private long studyTooltipSecond = -1;
    public void drawmain(GOut g, GSprite spr) {
	if(hearthborn.ui.UiTheme.light()) {
	    GOut shadow = g.reclip(UI.scale(1, 1), g.sz());
	    shadow.chcolor(hearthborn.ui.UiTheme.EDGE);
	    spr.draw(shadow);
	}
	spr.draw(g);
    }

    public class ItemTip implements Indir<Tex>, ItemInfo.InfoTip {
	private final List<ItemInfo> info;
	private final TexI tex;

	public ItemTip(List<ItemInfo> info, BufferedImage img) {
	    this.info = info;
	    if(img == null)
		throw(new Loading());
	    tex = new TexI(img);
	}

	public GItem item() {return(item);}
	public List<ItemInfo> info() {return(info);}
	public Tex get() {return(tex);}
    }

    public class ShortTip extends ItemTip {
	public ShortTip(List<ItemInfo> info) {super(info, ItemInfo.shorttip(info));}
    }

    public class LongTip extends ItemTip {
	public LongTip(List<ItemInfo> info) {super(info, hearthborn.l10n.TooltipText.render(() -> ItemInfo.longtip(info)));}
    }

    private static final double FULL_TOOLTIP_DELAY = 1.0;
    private double hoverstart;
    private Coord pendingHandCoord;
    private UI.Grab handGrab;
    private boolean handDown;
    private void releaseHandGrab(){handDown=false;if(handGrab!=null){handGrab.remove();handGrab=null;}}
    private ItemTip shorttip = null, longtip = null;
    private List<ItemInfo> ttinfo = null;
    public Object tooltip(Coord c, Widget prev) {
	double now = Utils.rtime();
	if(prev != this)
	    hoverstart = now;
	/* Shift opens the stack/container preview - hide the tip so it does not cover the contents. */
	if(item.contents != null && ui != null && ui.modshift)
	    return(null);
	List<ItemInfo> info = item.info();
	if(info.size() < 1)
	    return(null);
	if(info != ttinfo) {
	    shorttip = longtip = null;
	    ttinfo = info;
	}
	if(!(parent instanceof Equipory) && now - hoverstart < FULL_TOOLTIP_DELAY) {
	    if(shorttip == null)
		shorttip = new ShortTip(info);
	    return shorttip;
	}
	if(hearthborn.ui.StudyProgress.isStudy(item)) {
	    long second = (long)now;
	    if(second != studyTooltipSecond) {
		longtip = null;
		studyTooltipSecond = second;
	    }
	}
        if(longtip == null)longtip=new LongTip(info);
        return longtip;
    }

    private List<ItemInfo> info() {return(item.info());}
    public final AttrCache<Pipe.Op> rstate = new AttrCache<>(this::info, GItem.RStateInfo.combine);
    public final AttrCache<GItem.InfoOverlay<?>[]> itemols = new AttrCache<>(this::info, info -> {
	    ArrayList<GItem.InfoOverlay<?>> buf = new ArrayList<>();
	    for(ItemInfo inf : info) {
		if(inf instanceof GItem.OverlayInfo && !hearthborn.ItemCondition.replaces(inf,info) &&
		   !hearthborn.QualityOverlay.replaces(inf,info))
		    buf.add(GItem.InfoOverlay.create((GItem.OverlayInfo<?>)inf));
	    }
	    GItem.InfoOverlay<?>[] ret = buf.toArray(new GItem.InfoOverlay<?>[0]);
	    return(() -> ret);
	});
    public final AttrCache<Double> itemmeter = new AttrCache<>(this::info, AttrCache.map1(GItem.MeterInfo.class, minf -> minf::meter));

    private Widget contparent() {
	/* XXX: This is a bit weird, but I'm not sure what the alternative is... */
	Widget cont = getparent(GameUI.class);
	return((cont == null) ? cont = ui.root : cont);
    }

    private GSprite lspr = null;
    private Widget lcont = null;
    public void tick(double dt) {
	/* XXX: This is ugly and there should be a better way to
	 * ensure the resizing happens as it should, but I can't think
	 * of one yet. */
	GSprite spr = item.spr();
	if((spr != null) && (spr != lspr)) {
	    Coord sz = spr.sz();
	    resize(Coord.of(sqsz.x * ((sz.x + sqsz.x / 2) / sqsz.x),
			    sqsz.y * ((sz.y + sqsz.y / 2) / sqsz.y)));
	    lspr = spr;
	}
	/* First click on hand-gear waits for a possible double-click swap; then takes normally. */
	if(hearthborn.scripts.HandEquip.flushTake(item)) {
	    Coord at = pendingHandCoord == null ? Coord.z : pendingHandCoord;
	    pendingHandCoord = null;releaseHandGrab();
	    item.wdgmsg("take", at);
	}
    }

    public void draw(GOut g) {draw(g,true);}
    private double inventoryZoom(){return hearthborn.inventory.InventoryScale.zoomOf(this);}

    public void draw(GOut g,boolean showQuality) {
	GSprite spr = item.spr();
	if(spr != null) {
	    Coord sz = spr.sz();
	    g.defstate();
	    if(rstate.get() != null)
		g.usestate(rstate.get());
	    drawmain(g, spr);
	    g.defstate();
	    GItem.InfoOverlay<?>[] ols = itemols.get();
	    if(ols != null) {
		for(GItem.InfoOverlay<?> ol : ols)
		    if(!(ol.inf instanceof GItem.NumberInfo))
			ol.draw(g);
	    }
	    Double infoMeter = itemmeter.get();
	    hearthborn.ui.ItemMeter.info(item, infoMeter);
	    Double meter = hearthborn.ui.ItemMeter.shown(item, hearthborn.ui.ItemMeter.cooking(this));
            if(!hearthborn.ui.ProductionSettings.showProgress(this))meter=null;
            boolean process=processOverlay.draw(g,this,sz,meter==null?0:meter);
            if(process){
                qualityOverlay.drawCorner(g,item,hearthborn.ui.EquipmentStrip.SCALE/inventoryZoom());
            }else{
	    if(studyOverlay.draw(g, item, sz))
		progress.draw(g, sz.div(2), 0);
	    else
		progress.draw(g, sz.div(2), meter == null ? 0 : meter, !hearthborn.ui.MeterPercent.bare(getparent(Window.class)) && !hearthborn.ui.MeterPercent.egg(item));
            cheeseTime.draw(g,this,sz.div(2),meter==null?0:meter);
            if(showQuality)qualityOverlay.drawCorner(g,item,hearthborn.ui.EquipmentStrip.SCALE/inventoryZoom());
            }
	    if(ols != null) {
		for(GItem.InfoOverlay<?> ol : ols)
		    if(ol.inf instanceof GItem.NumberInfo && !GItem.NumberInfo.gildingClass(ol.inf.getClass().getName()))
			ol.draw(g);
	    }
	    condition.draw(g, item, sz, hearthborn.ui.EquipmentStrip.SCALE / inventoryZoom());
	    if(ols != null) {
		for(GItem.InfoOverlay<?> ol : ols)
		    if(GItem.NumberInfo.gildingClass(ol.inf.getClass().getName()))
			ol.draw(g);
	    }
            claimPresence.draw(g,item,sz);
	} else {
	    g.image(missing.layer(Resource.imgc).tex(), Coord.z, sz);
	}
        if(hearthborn.ui.GlobalCommands.ItemFilter.active()) {
            /* The items that do not match are dimmed, the ones that do get a gold frame, so the hits stand out in a full inventory. */
            if(hearthborn.ui.GlobalCommands.ItemFilter.dim(item)){g.chcolor(0,0,0,205);g.frect(Coord.z,sz);g.chcolor();}
            else {g.chcolor(255,205,70,255);g.rect(Coord.z,sz);g.rect(Coord.of(1,1),sz.sub(2,2));g.chcolor();}
        }
        hearthborn.inventory.ContainerOverview.highlight(g,this);
    }

    @Override
    public void dispose() {
        hearthborn.scripts.HandEquip.cancelClick(item);releaseHandGrab();
	qualityOverlay.dispose();
        processOverlay.dispose();
        claimPresence.dispose();
	condition.dispose();
        studyOverlay.dispose();
	progress.dispose();
        cheeseTime.dispose();
	super.dispose();
    }

    public boolean mousedown(MouseDownEvent ev) {
        if(ev.b==1 && hearthborn.storage.ContainerContents.pick(this))return true;
        if(ev.b==1 && hearthborn.quality.QualityRoster.pick(this))return true;
        if(hearthborn.ui.StudyProgress.locked(this)) return true;
        if(hearthborn.inventory.InventoryActions.transferMatches(ui.modflags()) && (ev.b==1 || ev.b==3) &&
            hearthborn.inventory.InventoryActions.transferSame(this,ev.b==3)) return true;
	if(ev.b == 1) {
	    /* Alt+click: the actions that use this item, in the action palette. */
	    if(ui.modflags() == UI.MOD_META && hearthborn.ui.ActionBrowser.searchByItem(getparent(GameUI.class), item, ev.c)) return(true);
	    if(ui.modshift) {
		int n = ui.modctrl ? -1 : 1;
                if(!hearthborn.inventory.BagTransfers.transfer(this,java.util.Collections.singletonList(this)))
                    hearthborn.inventory.InventoryActions.transfer(this,ev.c,n);
	    } else if(ui.modctrl) {
		int n = ui.modmeta ? -1 : 1;
		item.wdgmsg("drop", ev.c, n);
	    } else {
                if(hearthborn.scripts.HandEquip.forHands(item)){
                    pendingHandCoord=ev.c;
                    if(hearthborn.scripts.HandEquip.click(getparent(GameUI.class),item)){
                        releaseHandGrab();
                        if(hearthborn.scripts.HandEquip.pendingClick(item)){handDown=true;handGrab=ui.grabmouse(this);}
                        return(true);
                    }
                }
		item.wdgmsg("take", ev.c);
	    }
	    return(true);
	} else if(ev.b == 3) {
	    item.wdgmsg("iact", ev.c, ui.modflags());
	    return(true);
	}
	return(super.mousedown(ev));
    }

    public boolean mouseup(MouseUpEvent ev){if(ev.b==1&&handDown){releaseHandGrab();return true;}return super.mouseup(ev);}
    public void mousemove(MouseMoveEvent ev){
        if(handDown&&pendingHandCoord!=null&&ev.c.dist(pendingHandCoord)>UI.scale(4)){
            Coord at=pendingHandCoord;pendingHandCoord=null;releaseHandGrab();
            hearthborn.scripts.HandEquip.cancelClick(item);item.wdgmsg("take",at);
        }
        super.mousemove(ev);
    }

    public boolean drop(Coord cc, Coord ul) {
	return(false);
    }

    public boolean iteminteract(Coord cc, Coord ul) {
	item.wdgmsg("itemact", ui.modflags());
	return(true);
    }

    public boolean mousehover(MouseHoverEvent ev, boolean on) {
	if(on && (item.contents != null) && hearthborn.ui.ContainerPreviews.allowed(ui)) {
	    item.hovering(this);
	    return(true);
	}
	return(super.mousehover(ev, on));
    }
}
