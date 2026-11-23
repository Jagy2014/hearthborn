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
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.*;
import haven.render.*;
import hearthborn.map.MarkerQuality;
import haven.iosys.tk.*;
import haven.MapFile.Marker;
import haven.MapFile.PMarker;
import haven.MapFile.SMarker;
import haven.MiniMap.*;
import haven.BuddyWnd.GroupSelector;
import haven.MiniMap.Location;
import static haven.MCache.tilesz;
import static haven.MCache.cmaps;
import static haven.Utils.eq;

public class MapWnd extends Window implements Console.Directory {
    public static final Resource markcurs = Resource.local().loadwait("gfx/hud/curs/flag");
    public final MapFile file;
    public final MiniMap view;
    public final MapView mv;
    public final Toolbox tool;
    public final Collection<String> overlays = new java.util.concurrent.CopyOnWriteArraySet<>();
    public MarkerConfig markcfg = MarkerConfig.showall, cmarkers = null;
    private final Locator player;
    private final Widget toolbar;
    private final Frame viewf;
    private final hearthborn.map.MapResize leftResize;
    private final MarkerObjs mvmarks = new MarkerObjs(this);
    private GroupSelector colsel;
    private ICheckBox compactToggle;
    private CheckBox onmapbtn;
    private Button mremove;
    private Predicate<Marker> mflt = pmarkers;
    private Comparator<ListMarker> mcmp = namecmp;
    private List<ListMarker> markers = Collections.emptyList();
    private int markerseq = -1;
    private Marker mrefocus = null;
    private int olalpha = 64;
    private long playerSegment = Long.MIN_VALUE;
    private Coord playerTile;
    /** A jump no walk can make between map updates. Same-segment teleports still need a recenter. */
    public static boolean farJump(Coord from, Coord to) {
	return from != null && to != null && from.dist(to) > 40;
    }
    public boolean frameBoosted;
    private final Collection<Runnable> deferred = new LinkedList<>();

    private final static Predicate<Marker> pmarkers = (m -> m instanceof PMarker);
    private final static Predicate<Marker> smarkers = (m -> m instanceof SMarker);
    private final static Comparator<ListMarker> namecmp = ((a, b) -> a.mark.nm.compareTo(b.mark.nm));
    private final static Comparator<ListMarker> typecmp = Comparator.comparing((ListMarker lm) -> lm.type).thenComparing(namecmp);

    public static final KeyBinding kb_home = KeyBinding.get("mapwnd/home", KeyMatch.forcode(KeyEvent.VK_HOME, 0));
    public static final KeyBinding kb_mark = KeyBinding.get("mapwnd/mark", KeyMatch.nil);
    public static final KeyBinding kb_hmark = KeyBinding.get("mapwnd/hmark", KeyMatch.forchar('M', KeyMatch.C));
    public static final KeyBinding kb_compact = KeyBinding.get("mapwnd/compact", KeyMatch.forchar('A', KeyMatch.M));
    public static final KeyBinding kb_prov = KeyBinding.get("mapwnd/prov", KeyMatch.nil);
    protected boolean escapeCloses() {return(false);}
    public MapWnd(MapFile file, MapView mv, Coord sz, String title) {
	super(sz, title, true);
	this.file = file;
	this.mv = mv;
	this.player = new MapLocator(mv);
	viewf = add(new ViewFrame());
	view = viewf.add(new View(file));
        leftResize=viewf.add(new hearthborn.map.MapResize(this));
        add(new hearthborn.ui.RightStretch(this){
            protected boolean enabled(){return !combatPresentation()&&MapWnd.this.c.x>=-10000;}
        });
	recenter();
	toolbar = add(new Widget(Coord.z));
	toolbar.add(new Img(Resource.loadtex("gfx/hud/mmap/fgwdg")) {
		public boolean mousedown(MouseDownEvent ev) {
		    if((ev.b == 1) && checkhit(ev.c)) {
			MapWnd.this.drag(parentpos(MapWnd.this, ev.c));
			return(true);
		    }
		    return(super.mousedown(ev));
		}
	    }, Coord.z);
	toolbar.add(new IButton("gfx/hud/mmap/home", "", "-d", "-h") {
		{settip("Follow"); setgkey(kb_home);}
		public void click() {
		    recenter();
		}
	    }, Coord.z);
	toolbar.add(new MarkButton(), Coord.z).setgkey(kb_mark);
	toolbar.add(new ICheckBox("gfx/hud/mmap/hmark", "", "-d", "-h", "-dh") {
		public void click() {markersShown(!markersShown());}
	    }).state(this::markersShown).setgkey(kb_hmark);
	toolbar.add(compactToggle = new ICheckBox("gfx/hud/mmap/wnd", "", "-d", "-h", "-dh"))
	    .state(this::compact).set(a -> {
		    compact(a);
		    Utils.setprefb("compact-map", a);
		})
	    .settip("Compact mode").setgkey(kb_compact);
	toolbar.add(new ICheckBox("gfx/hud/mmap/prov", "", "-d", "-h", "-dh") {
		public boolean mousewheel(MouseWheelEvent ev) {
		    if(!checkhit(ev.c) || !ui.modshift || !a)
			return(super.mousewheel(ev));
		    olalpha = Utils.clip(olalpha + (ev.a * -32), 32, 256);
		    return(true);
		}
	    })
	    .changed(a -> toggleol("realm", a))
	    .settip("Display provinces").setgkey(kb_prov);
	hearthborn.map.MapToolbar column = add(new hearthborn.map.MapToolbar(this));
	java.util.List<Widget> nativeButtons = new java.util.ArrayList<Widget>();
	for(Widget child : toolbar.children()) {
	    if(child instanceof Img)
		child.hide();
	    else
		nativeButtons.add(child);
	}
	for(Widget child : nativeButtons)
	    if(child.kb_gkey != kb_hmark && (!detachedWindow() || child.kb_gkey != kb_compact))
		column.adopt(child);
	toolbar.hide();
	toolbar.resize(Coord.z);
	tool = add(new Toolbox());
	compact(Utils.getprefb("compact-map", false));
	resize(sz);
    }

    protected void added() {
	super.added();
	if(worldMarks()) mv.basic.add(mvmarks);
    }

    public void remove() {
	super.remove();
	if(worldMarks()) mvmarks.remove();
    }

    protected boolean worldMarks() {return true;}

    public void toggleol(String tag, boolean a) {
	if(a)
	    overlays.add(tag);
	else
	    overlays.remove(tag);
    }

    private class ViewFrame extends Frame {
        ViewFrame() {super(Coord.z, true);}

        public void tick(double dt){super.tick(dt);fitMapView();}

        public void draw(GOut g){fitMapView();super.draw(g);}

        public void drawframe(GOut g){
            hearthborn.ui.JoinedWindowFrame.draw(g,(IBox.Images)box,Coord.z,sz,
                hearthborn.ui.MagneticWindows.joinedAbove(MapWnd.this),
                hearthborn.ui.MagneticWindows.joinedBelow(MapWnd.this));
        }

        public boolean mousedown(MouseDownEvent ev) {
            if(combatPresentation())return super.mousedown(ev);
            /* Shift-clicks that do not drag should be propagated to the map. */
            if(ev.b==1&&(checkhit(ev.c)||ui.modshift)) {
                MapWnd.this.drag(parentpos(MapWnd.this,ev.c));
                return true;
            }
            return super.mousedown(ev);
        }
    }

    private static final int btnw = UI.scale(95);
    private static final int MARKER_PANEL_MIN = UI.scale(200);
    private static final int MARKER_PANEL_MAX = UI.scale(260);
    private static final Text.Foundry MARKER_NAMES = new Text.Foundry(Text.sans, 13).aa(true);
    public class Toolbox extends Widget {
	public final MarkerList list;
	private final Frame listf;
	private final Button pmbtn, smbtn, nobtn, tobtn, mebtn, mibtn;
	private final TextEntry markerSearch;
	private final IButton clearMarkerSearch;
	private TextEntry namesel;

	private Toolbox() {
	    super(UI.scale(200, 200));
	    listf = add(new Frame(UI.scale(new Coord(200, 200)), false), 0, 0);
	    list = listf.add(new MarkerList(Coord.of(listf.inner().x, 0)), 0, 0);
	    markerSearch = add(new TextEntry(MARKER_PANEL_MIN, "") {
		private Text hint;
		public void draw(GOut g) {
		    super.draw(g);
		    if(!hasfocus && text().isEmpty()) {
			if(hint == null) hint = fnd.render(hearthborn.L10n.tr("Search markers…"), hearthborn.ui.UiPalette.text2());
			g.image(hint.tex(), Coord.of(toffx, (sz.y - hint.sz().y) / 2));
		    }
		}
		public void dispose() {
		    if(hint != null) hint.dispose();
		    super.dispose();
		}
		protected void changed() {
		    super.changed();
		    list.filterMarkers(text());
		    clearMarkerSearch.show(!text().isEmpty());
		}
		public void activate(String text) {
		    List<ListMarker> results = list.allitems();
		    if(!results.isEmpty()) list.change(results.get(0));
		}
	    });
	    clearMarkerSearch = add(hearthborn.ui.SmallWindowDeco.closeButton(() -> {
		markerSearch.settext("");
		setfocus(markerSearch);
	    }));
	    clearMarkerSearch.settip("Clear search");
	    clearMarkerSearch.hide();
	    pmbtn = add(new Button(btnw, "Placed", false) {
		    public void click() {
			mflt = pmarkers;
			markerseq = -1;
		    }
		});
	    smbtn = add(new Button(btnw, "Natural", false) {
		    public void click() {
			mflt = smarkers;
			markerseq = -1;
		    }
		});
	    nobtn = add(new Button(btnw, "By name", false) {
		    public void click() {
			mcmp = namecmp;
			markerseq = -1;
		    }
		});
	    tobtn = add(new Button(btnw, "By type", false) {
		    public void click() {
			mcmp = typecmp;
			markerseq = -1;
		    }
		});
	    mebtn = add(new Button(btnw, "Export...", false) {
		    public void click() {
			exportmap();
		    }
		});
	    mibtn = add(new Button(btnw, "Import...", false) {
		    public void click() {
			importmap();
		    }
		});
	}

	void resizePanel(int width, int height) {
	    super.resize(Coord.of(width, height));
	    mebtn.c = new Coord(0, sz.y - mebtn.sz.y);
	    mibtn.c = new Coord(sz.x - mibtn.sz.x, sz.y - mibtn.sz.y);
	    nobtn.c = new Coord(0, mebtn.c.y - UI.scale(5) - nobtn.sz.y);
	    tobtn.c = new Coord(sz.x - tobtn.sz.x, nobtn.c.y);
	    pmbtn.c = new Coord(0, nobtn.c.y - UI.scale(5) - pmbtn.sz.y);
	    smbtn.c = new Coord(sz.x - smbtn.sz.x, pmbtn.c.y);
	    markerSearch.resize(width - clearMarkerSearch.sz.x - UI.scale(4));
	    clearMarkerSearch.move(Coord.of(width - clearMarkerSearch.sz.x,
		Math.max(0, (markerSearch.sz.y - clearMarkerSearch.sz.y) / 2)));
	    markerSearch.move(Coord.z);
	    int listTop = Math.max(markerSearch.sz.y, clearMarkerSearch.sz.y) + UI.scale(5);
	    int bottom = pmbtn.c.y - UI.scale(6);
	    if(namesel != null) {
		int editor = namesel.sz.y + UI.scale(8);
		if(colsel != null)
		    editor += colsel.sz.y + onmapbtn.sz.y + UI.scale(12);
		if(mremove != null)
		    editor += mremove.sz.y + UI.scale(8);
		bottom -= editor;
	    }
	    listf.resize(sz.x, Math.max(UI.scale(48), bottom - listTop));
	    listf.move(Coord.of(0, listTop));
	    list.resize(listf.inner());
	    if(namesel != null) {
		namesel.c = listf.pos("bl").adds(0, 6);
		namesel.resize(sz.x);
		int y = namesel.c.y + namesel.sz.y + UI.scale(6);
		if(colsel != null) {
		    colsel.c = new Coord(0, y);
		    y += colsel.sz.y + UI.scale(4);
		    onmapbtn.c = new Coord(0, y);
		    y += onmapbtn.sz.y + UI.scale(6);
		}
		if(mremove != null)
		    mremove.c = new Coord(0, y);
	    }
	}
    }

    private class View extends MiniMap {
	View(MapFile file) {
	    super(file);
	}

	public void drawgrid(GOut g, Coord ul, DisplayGrid disp) {
	    super.drawgrid(g, ul, disp);
	    for(String tag : overlays) {
		try {
		    Tex img = disp.olimg(tag);
		    if(img != null) {
			g.chcolor(255, 255, 255, olalpha);
			g.image(img, ul, img.sz().mul(dmag));
		    }
		} catch(Loading l) {
		}
	    }
	    g.chcolor();
	}

	public boolean filter(DisplayMarker mark) {
	    return(markcfg.filter(mark.m));
	}

	public boolean clickmarker(DisplayMarker mark, Location loc, int button, boolean press) {
	    if(button == 1) {
		if(!compact() && !press) {
		    focus(mark.m);
		    return(true);
		}
	    } else if(mark.m instanceof SMarker) {
		Gob gob = MarkerID.find(ui.sess.glob.oc, mark.m);
		if(gob != null)
		    mvclick(mv, null, loc, gob, button);
	    }
	    return(false);
	}

	public boolean clickicon(DisplayIcon icon, Location loc, int button, boolean press) {
	    if(!press) {
		mvclick(mv, null, loc, icon.gob, button);
		return(true);
	    }
	    return(false);
	}

	public boolean clickloc(Location loc, int button, boolean press) {
	    if(!press && (sessloc != null) && (loc.seg == sessloc.seg)) {
		mvclick(mv, null, loc, null, button);
		return(true);
	    }
	    return(false);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    super.mousedown(ev);
	    return(true);
	}

	public void draw(GOut g) {
	    g.chcolor(0, 0, 0, 128);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    super.draw(g);
	}
    }

    public class MarkButton extends ICheckBox implements CursorQuery.Handler {
	private UI.Grab grab = null;

	private MarkButton() {
	    super("gfx/hud/mmap/mark", "", "-d", "-h", "-dh");
	}

	public boolean state() {
	    return(grab != null);
	}

	public void tick(double dt) {
	    if(indoors()) ungrab();
	    super.tick(dt);
	}

	public void click() {
	    if(indoors()) {
		ungrab();
		return;
	    }
	    if(grab == null)
		grab = ui.grabmouse(this);
	}

	public Object tooltip(Coord c, Widget prev) {
	    settip(indoors() ? "Cannot place a marker indoors" : "New marker");
	    return super.tooltip(c, prev);
	}

	public void mark(Location loc, boolean onmap) {
	    if(indoors()) return;
	    if(compact()) {
		GameUI gui = getparent(GameUI.class);
		if(gui != null) {
		    hearthborn.map.MarkerAsk.open(gui, c.add(UI.scale(12, 12)), (title, color) -> file.add(new PMarker(file, loc.seg.id, loc.tc, title, color, onmap)));
		    return;
		}
	    }
	    Marker nm = new PMarker(file, loc.seg.id, loc.tc, "New marker", BuddyWnd.gc[new Random().nextInt(BuddyWnd.gc.length)], onmap);
	    file.add(nm);
	    focus(nm);
	}

	private boolean ungrab() {
	    if(grab != null) {
		grab.remove();
		grab = null;
	    }
	    return(true);
	}

	public class FindMark extends MapView.Maptest {
	    private FindMark(MapView mv, Coord c) {mv.super(c);}

	    protected void hit(Coord pc, Coord2d mc) {
		Location sloc = view.sessloc;
		if(sloc != null) {
		    Location loc = new Location(sloc.seg, sloc.tc.add(mc.floor(tilesz)));
		    mark(loc, true);
		}
		ungrab();
	    }
	}

	public class PlaceMarker extends Widget.PointerEvent {
	    public PlaceMarker(Coord c) {super(c);}
	    public PlaceMarker(PlaceMarker from, Coord c) {super(from, c);}
	    public PlaceMarker derive(Coord c) {return(new PlaceMarker(this, c));}

	    protected boolean shandle(Widget w) {
		if((w == MarkButton.this) && checkhit(c)) {
		    return(ungrab());
		} else if(w instanceof MiniMap) {
		    mark(((MiniMap)w).xlate(c), false);
		    return(ungrab());
		} else if(w instanceof MapView) {
		    new FindMark(mv, c).run();
		    return(true);
		}
		return(super.shandle(w));
	    }
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(!ev.grabbed)
		return(super.mousedown(ev));
	    if(ev.b == 1) {
                Widget domain=hearthborn.map.DetachedMap.domain(this);
                if(domain==null)ui.dispatch(ui.root,new PlaceMarker(ev.c.add(rootpos())));
                else ui.dispatchWithin(domain,new PlaceMarker(ev.c.add(parentpos(domain))));
		return(true);
	    } else if(ev.b == 3) {
		return(ungrab());
	    }
	    return(false);
	}

	public boolean getcurs(CursorQuery ev) {
	    return(ev.grabbed ? ev.set(markcurs) : false);
	}
    }

    public void copyMarkerFiltersFrom(MapWnd source) {
        mflt=source.mflt;
        mcmp=source.mcmp;
        markerseq=-1;
    }

    public void tick(double dt) {
	super.tick(dt);
	synchronized(deferred) {
	    for(Iterator<Runnable> i = deferred.iterator(); i.hasNext();) {
		Runnable task = i.next();
		try {
		    task.run();
		} catch(Loading l) {
		    continue;
		}
		i.remove();
	    }
	}
	view.markobjs();
	try {
	    MiniMap.Location place = view.resolve(player);
	    if(place != null) {
		long segment = place.seg.id;
		if((playerSegment != Long.MIN_VALUE) && ((playerSegment != segment) || farJump(playerTile, place.tc)))
		    recenter();
		playerSegment = segment;
		playerTile = new Coord(place.tc);
	    }
	} catch(Loading l) {
	}
	if(visible || view.loadHidden) {
	    if(mrefocus != null) {
		for(Predicate<Marker> filter : Arrays.asList(pmarkers, smarkers)) {
		    if(filter.test(mrefocus)) {
			if(filter != mflt) {
			    mflt = filter;
			    markerseq = -1;
			}
			break;
		    }
		}
	    }
	    if(markerseq != view.file.markerseq) {
		int markerseq = view.file.markerseq;
		if(view.file.lock.readLock().tryLock()) {
		    try {
			Map<Marker, ListMarker> prev = new HashMap<>();
			for(ListMarker pm : this.markers)
			    prev.put(pm.mark, pm);
			List<ListMarker> markers = new ArrayList<>();
			for(Marker mark : view.file.markers) {
			    if(!mflt.test(mark))
				continue;
			    ListMarker lm = prev.get(mark);
			    if(lm == null)
				lm = new ListMarker(mark);
			    else
				lm.type = MarkerType.of(lm.mark);
			    markers.add(lm);
			}
			markers.sort(mcmp);
			this.markers = markers;
			this.markerseq = markerseq;
		    } finally {
			view.file.lock.readLock().unlock();
		    }
		}
	    }
	    if(mrefocus != null) {
		for(ListMarker lm : markers) {
		    if(lm.mark == mrefocus) {
			tool.list.change2(lm);
			tool.list.display(lm);
			mrefocus = null;
			break;
		    }
		}
	    }
	}
    }

    public static abstract class MarkerType implements Comparable<MarkerType> {
	public static final int iconsz = UI.scale(20);
	private static final HashedSet<MarkerType> types = new HashedSet<>(Hash.eq);
	public abstract Tex icon();

	public static MarkerType of(Marker mark) {
	    if(mark instanceof PMarker) {
		return(types.intern(new PMarkerType(((PMarker)mark).color)));
	    } else if(mark instanceof SMarker) {
		return(types.intern(new SMarkerType(((SMarker)mark).res)));
	    } else {
		return(null);
	    }
	}

	public int compareTo(MarkerType that) {
	    return(this.getClass().getName().compareTo(that.getClass().getName()));
	}
    }

    public static class PMarkerType extends MarkerType {
	public final Color col;
	private Tex icon = null;

	public PMarkerType(Color col) {
	    this.col = col;
	}

	public Tex icon() {
	    if(icon == null) {
		Resource.Image fg = MiniMap.Flag.fg, bg = MiniMap.Flag.bg;
		Coord tsz = Coord.of(Math.max(fg.tsz.x, bg.tsz.x), Math.max(fg.tsz.y, bg.tsz.y));
		Coord bsz = Coord.of(Math.max(tsz.x, tsz.y));
		Coord o = bsz.sub(tsz);
		WritableRaster buf = PUtils.imgraster(bsz);
		PUtils.blit(buf, PUtils.coercergba(fg.img).getRaster(), fg.o.add(o));
		PUtils.colmul(buf, col);
		PUtils.alphablit(buf, PUtils.coercergba(bg.img).getRaster(), bg.o.add(o));
		icon = new TexI(PUtils.uiscale(PUtils.rasterimg(buf), new Coord(iconsz, iconsz)));
	    }
	    return(icon);
	}

	public boolean equals(PMarkerType that) {
	    return(Utils.eq(this.col, that.col));
	}
	public boolean equals(Object that) {
	    return((that instanceof PMarkerType) && equals((PMarkerType)that));
	}

	public int hashCode() {
	    return(col.hashCode());
	}

	public int compareTo(PMarkerType that) {
	    int a = Utils.index(BuddyWnd.gc, this.col), b = Utils.index(BuddyWnd.gc, that.col);
	    if((a >= 0) && (b >= 0))
		return(a - b);
	    if((a < 0) && (b >= 0))
		return(1);
	    if((a >= 0) && (b < 0))
		return(-1);
	    return(Utils.idcmp.compare(this.col, that.col));
	}
	public int compareTo(MarkerType that) {
	    if(that instanceof PMarkerType)
		return(compareTo((PMarkerType)that));
	    return(super.compareTo(that));
	}
    }

    public static class SMarkerType extends MarkerType {
	private Resource.Saved spec;
	private Tex icon = null;

	public SMarkerType(Resource.Saved spec) {
	    this.spec = spec;
	}

	public Tex icon() {
	    if(icon == null) {
		BufferedImage img = spec.get().flayer(Resource.imgc).img;
		icon = new TexI(PUtils.uiscale(img, new Coord((iconsz * img.getWidth())/ img.getHeight(), iconsz)));
	    }
	    return(icon);
	}

	public boolean equals(SMarkerType that) {
	    if(Utils.eq(this.spec.name, that.spec.name)) {
		if(that.spec.ver > this.spec.ver) {
		    this.spec = that.spec;
		    this.icon = null;
		}
		return(true);
	    }
	    return(false);
	}
	public boolean equals(Object that) {
	    return((that instanceof SMarkerType) && equals((SMarkerType)that));
	}

	public int hashCode() {
	    return(spec.name.hashCode());
	}

	public int compareTo(SMarkerType that) {
	    return(this.spec.name.compareTo(that.spec.name));
	}
	public int compareTo(MarkerType that) {
	    if(that instanceof SMarkerType)
		return(compareTo((SMarkerType)that));
	    return(super.compareTo(that));
	}
    }

    public static class MarkerConfig {
	public static final MarkerConfig showall = new MarkerConfig();
	public static final MarkerConfig hideall = new MarkerConfig().showsel(true);
	public Set<MarkerType> sel = Collections.emptySet();
	public boolean showsel = false;

	public MarkerConfig() {
	}

	public MarkerConfig(MarkerConfig from) {
	    this.sel = from.sel;
	    this.showsel = from.showsel;
	}

	public MarkerConfig showsel(boolean showsel) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.showsel = showsel;
	    return(ret);
	}

	public MarkerConfig add(MarkerType type) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.sel = new HashSet<>(ret.sel);
	    ret.sel.add(type);
	    return(ret);
	}

	public MarkerConfig remove(MarkerType type) {
	    MarkerConfig ret = new MarkerConfig(this);
	    ret.sel = new HashSet<>(ret.sel);
	    ret.sel.remove(type);
	    return(ret);
	}

	public MarkerConfig toggle(MarkerType type) {
	    if(sel.contains(type))
		return(remove(type));
	    else
		return(add(type));
	}

	public boolean filter(MarkerType type) {
	    return(sel.contains(type) != showsel);
	}

	public boolean filter(Marker mark) {
	    return(sel.isEmpty() ? showsel : filter(MarkerType.of(mark)));
	}

	public boolean equals(MarkerConfig that) {
	    return(Utils.eq(this.sel, that.sel) && (this.showsel == that.showsel));
	}
	public boolean equals(Object that) {
	    return((that instanceof MarkerConfig) && equals((MarkerConfig)that));
	}
    }

    public static class ListMarker {
	public final Marker mark;
	public MarkerType type;

	public ListMarker(Marker mark) {
	    this.mark = mark;
	    type = MarkerType.of(mark);
	}
    }

    public class MarkerList extends SSearchBox<ListMarker, Widget> {
	public MarkerList(Coord sz) {
	    super(sz, MarkerType.iconsz);
	}

	private String query = "";
	private List<ListMarker> filteredSource, filteredMarkers;
	private int filteredRevision;

	public List<ListMarker> allitems() {
	    if(query.isEmpty()) return markers;
	    int revision = MarkerQuality.captionRevision();
	    if(filteredSource != markers || filteredRevision != revision) {
		filteredMarkers = new ArrayList<>();
		for(ListMarker marker : markers)
		    if(searchmatch(marker, query)) filteredMarkers.add(marker);
		filteredSource = markers;
		filteredRevision = revision;
	    }
	    return filteredMarkers;
	}

	public boolean searchmatch(ListMarker marker, String text) {
	    return hearthborn.map.MapFilterModel.searchText(MarkerQuality.caption(marker.mark))
		.contains(hearthborn.map.MapFilterModel.searchText(text));
	}

	private void filterMarkers(String text) {
	    query = hearthborn.map.MapFilterModel.searchText(text).trim();
	    filteredSource = null;
	    stopsearch();
	    if(sel != null && !allitems().contains(sel)) change2(null);
	    scrollval(0);
	}

	public class Item extends IconText {
	    public final ListMarker lm;

	    public Item(Coord sz, ListMarker lm) {
		super(sz);
		this.lm = lm;
	    }

	    protected BufferedImage img() {throw(new RuntimeException());}
	    protected String text() {return(MarkerQuality.caption(lm.mark) + hearthborn.map.CaveLevels.markerSuffix(lm.mark.seg));}
	    protected Text.Forge foundry() {return MARKER_NAMES;}
	    public Object tooltip(Coord c, Widget prev) {return text();}
	    protected boolean valid(String text) {return(Utils.eq(text, text()));}

	    protected void drawicon(GOut g) {
		try {
		    Tex icon = lm.type.icon();
		    if(markcfg.filter(lm.type))
			g.chcolor(255, 255, 255, 128);
		    g.aimage(icon, Coord.of(sz.y / 2), 0.5, 0.5);
		    g.chcolor();
		} catch(Loading l) {
		}
	    }

	    public boolean mousedown(MouseDownEvent ev) {
		if(ev.c.x < sz.y) {
		    toggletype(lm.type);
		    return(true);
		}
		return(super.mousedown(ev));
	    }
	}

	public Widget makeitem(ListMarker lm, int idx, Coord sz) {
	    Widget ret = new ItemWidget<ListMarker>(this, sz, lm);
	    ret.add(new Item(sz, lm), Coord.z);
	    return(ret);
	}

	private void toggletype(MarkerType type) {
	    MarkerConfig nc = markcfg.toggle(type);
	    markcfg = nc;
	    cmarkers = nc.sel.isEmpty() ? null : nc;
	}

	public void change(ListMarker lm) {
	    change2(lm);
	    if(lm != null)
		view.center(new SpecLocator(lm.mark.seg, lm.mark.tc));
	}

	public void change2(ListMarker lm) {
	    this.sel = lm;

	    if(tool.namesel != null) {
		ui.destroy(tool.namesel);
		tool.namesel = null;
		ui.destroy(mremove);
		mremove = null;
		if(colsel != null) {
		    ui.destroy(colsel);
		    ui.destroy(onmapbtn);
		    colsel = null;
		}
	    }

	    if(lm != null) {
		Marker mark = lm.mark;
		if(tool.namesel == null) {
		    tool.namesel = tool.add(new TextEntry(UI.scale(200), "") {
			    {dshow = true;}
			    public void activate(String text) {
				mark.nm = text;
				view.file.update(mark);
				commit();
				change2(null);
			    }
			});
		}
		tool.namesel.settext(mark.nm);
		tool.namesel.buf.point(mark.nm.length());
		tool.namesel.commit();
		if(mark instanceof PMarker) {
		    PMarker pm = (PMarker)mark;
		    colsel = tool.add(new GroupSelector(Math.max(0, Utils.index(BuddyWnd.gc, pm.color))) {
			    public void changed(int group) {
				pm.color = BuddyWnd.gc[group];
				view.file.update(mark);
			    }
			});
		    onmapbtn = (CheckBox)tool.add(new CheckBox("Display in world").state(() -> pm.onmap));
		    onmapbtn.set(v -> {
			pm.onmap = v;
			view.file.update(mark);
		    });
		}
		mremove = tool.add(new Button(UI.scale(200), "Delete icon", false) {
			public void click() {
			    view.file.remove(mark);
			    change2(null);
			}
		    });
	    }
	    MapWnd.this.resize(csz());
	}
    }

    /** Use the map's former bottom-border space when a lower window owns the shared edge. */
    private void fitMapView(){
        Coord size=viewf.inner();
        if(compact()&&hearthborn.ui.MagneticWindows.joinedBelow(this))size=size.add(0,viewf.box.bbroff().y);
        if(!view.sz.equals(size))view.resize(size);
    }

    public void resize(Coord sz) {
        boolean combat=combatPresentation();
	sz = sz.max(combat?Coord.of(1,1):compact() ? UI.scale(150, 150) : UI.scale(350, 255));
        int mapWidth=viewf.box.bisz().x+leftResize.sz.x+toolbar.sz.x+UI.scale(4);
        if(!combat)sz.x=Math.max(sz.x,mapWidth+(compact()?0:MARKER_PANEL_MIN+UI.scale(10)));
	if(boundedFullMap())sz=sz.min(fullMapLimit());
	super.resize(sz);
	int markerWidth = compact() ? MARKER_PANEL_MIN : Utils.clip(sz.x / 3, MARKER_PANEL_MIN, MARKER_PANEL_MAX);
	tool.resizePanel(markerWidth, sz.y);
	hearthborn.map.MapToolbar column = getchild(hearthborn.map.MapToolbar.class);
	int frameW = sz.x;
	if(!compact()) {
	    tool.c = new Coord(sz.x - tool.sz.x, 0);
	    frameW = Math.max(1, tool.c.x - UI.scale(10));
	}
	viewf.move(Coord.z);
	if(!compact())
	    viewf.resize(Coord.of(frameW, Math.max(1, sz.y)));
	else {
	    viewf.resize(Coord.of(sz.x, Math.max(1, sz.y)));
	    tool.c = viewf.pos("ur").adds(10, 0);
	}
	fitMapView();
	leftResize.presize();
	if(!combat && column != null) {
	    column.place(Math.max(1, viewf.inner().x));
	    Coord at = viewf.c.add(viewf.xlate(Coord.z, true));
	    if(!compact() && deco instanceof hearthborn.ui.SmallWindowDeco)
		at = at.add(0, ((hearthborn.ui.SmallWindowDeco)deco).buttonRowGap());
	    column.move(at);
	    column.raise();
	}
	toolbar.c = viewf.c.add(viewf.xlate(leftResize.c,true).x+leftResize.sz.x+UI.scale(4), viewf.sz.y-toolbar.sz.y-UI.scale(2));
    }

    private boolean compact() {
	return(deco == null);
    }

    /** Saved map markers. The choice lives in icon settings and applies to every open map. */
    public boolean markersShown() {return !Utils.eq(markcfg, MarkerConfig.hideall);}

    public void markersShown(boolean show) {
	MarkerConfig next = show ? MarkerConfig.showall : MarkerConfig.hideall;
	GameUI gui = getparent(GameUI.class);
	if(gui == null && mv != null) gui = mv.getparent(GameUI.class);
	if(gui == null) {markcfg = next; return;}
	for(Widget child = gui.child; child != null; child = child.next)
	    if(child instanceof MapWnd) ((MapWnd)child).markcfg = next;
    }

    public boolean detachedWindow() { return false; }

    /** A house floor, including a cellar. Caves stay outside. */
    public boolean indoors() {
	GameUI gui = getparent(GameUI.class);
	if(gui == null && mv != null) gui = mv.getparent(GameUI.class);
	return gui != null && gui.indoors();
    }

    private Coord restC, restSz;
    private double cornerAt = Double.NEGATIVE_INFINITY;
    private boolean cornerClickable;

    /** The second press on a resize corner puts the map back to its size before the stretch. */
    public boolean cornerClick() {
	if(combatPresentation()) return false;
	double now = Utils.rtime();
	boolean twice = cornerClickable && now - cornerAt <= 0.5;
	cornerAt = now;
	cornerClickable = true;
	if(!twice) return false;
	cornerClickable = false;
	Coord sz = restSz != null ? restSz : Utils.getprefc("hearthborn/map/corner-sz", null);
	Coord at = restC != null ? restC : Utils.getprefc("hearthborn/map/corner-c", null);
	if(sz == null || at == null) return true;
	resize(sz);
	move(new Coord(at));
	fitOnScreen();
	clearCorner();
	storePlace();
	return true;
    }

    /** Remember the place a stretch started from. Later stretches keep that same place. */
    public void cornerPressed() {
	if(restSz != null) return;
	Coord saved = Utils.getprefc("hearthborn/map/corner-sz", null);
	Coord savedAt = Utils.getprefc("hearthborn/map/corner-c", null);
	if(saved != null && savedAt != null) {restSz = saved; restC = savedAt; return;}
	restSz = csz();
	restC = new Coord(c);
	Utils.setprefc("hearthborn/map/corner-sz", restSz);
	Utils.setprefc("hearthborn/map/corner-c", restC);
    }

    public void cornerDragged() {cornerClickable = false;}

    public void cornerReleased(Coord before) {
	Coord now = csz();
	boolean grew = now.x > before.x + 1 || now.y > before.y + 1;
	if(!grew && restSz != null && now.x <= restSz.x + 1 && now.y <= restSz.y + 1)
	    clearCorner();
    }

    private void clearCorner() {
	restC = null;
	restSz = null;
	Utils.setprefc("hearthborn/map/corner-c", null);
	Utils.setprefc("hearthborn/map/corner-sz", null);
    }

    private boolean combatPresentation(){GameUI gui=getparent(GameUI.class);return gui!=null&&gui.compactUI()&&gui.mapfile==this;}

    public void show(){
	boolean was=visible();
	super.show();
	// The map button calls show(), not show(boolean). Both paths come through here.
	if(!was&&parent!=null&&!frameBoosted){fitOnScreen();hearthborn.ui.MagneticWindows.uncover(this);}
    }

    public void compact(boolean a) {
	if(compactToggle != null)
	    compactToggle.settip(a ? "Show full map" : "Compact mode");
	boolean presenting=!frameBoosted&&parent!=null&&c.x>=-10000&&a!=compact();
	if(presenting)clearCorner();
	if(presenting)hearthborn.map.MapPlaces.before(this,a);
	tool.show(!a);
	if(a)
	    delfocusable(tool);
	else
	    newfocusable(tool);
	chdeco(a ? null : makedeco());
	pack();
	if(presenting)hearthborn.map.MapPlaces.after(this,a);
	else if(!a)fitOnScreen();
    }

    private boolean boundedFullMap() {
        return parent != null && deco != null && !frameBoosted && !combatPresentation()
            && parent.sz.x > 0 && parent.sz.y > 0;
    }

    private Coord fullMapLimit() {
        Coord available = hearthborn.ui.HudInterfaceScale.fromParent(this, parent.sz);
        return available.sub(sz.sub(csz())).max(Coord.of(1, 1));
    }

    /** Shrink an oversized full map before fitting its position into the current window. */
    public void fitOnScreen(){
	if(parent==null||frameBoosted||parent.sz.x<=0||parent.sz.y<=0)return;
	if(boundedFullMap()){
	    Coord fitted = csz().min(fullMapLimit());
	    if(!fitted.equals(csz()))resize(fitted);
	}
	Coord outer=hearthborn.ui.HudInterfaceScale.size(this);
	int x=c.x,y=c.y;
	if(x+outer.x>parent.sz.x)x=parent.sz.x-outer.x;
	if(y+outer.y>parent.sz.y)y=parent.sz.y-outer.y;
	x=Math.max(0,x);y=Math.max(0,y);
	if(x!=c.x||y!=c.y)move(Coord.of(x,y));
    }

    public void recenter() {
	view.follow(player);
    }

    /** Writes the window's current place. The off-screen copy used by the separate map window is ignored. */
    public void storePlace() {
	if(frameBoosted || parent == null || c.x < -10000)
	    return;
	if(boundedFullMap())fitOnScreen();
	Utils.setprefc("wndc-map", new Coord(c));
	Utils.setprefc("wndsz-map", csz());
	hearthborn.map.MapPlaces.remember(this);
        hearthborn.ui.WindowPlaces.flushPreferences();
    }

    public void focus(Marker m) {
	mrefocus = m;
    }

    protected Deco makedeco() {
	hearthborn.ui.SmallWindowDeco deco=new hearthborn.ui.SmallWindowDeco(true);
	deco.untitled();
	return deco;
    }

    public static class MarkerObjs extends RenderTree.Node.Track1 implements TickList.TickNode, TickList.Ticking {
	public static final Indir<Resource> flag = Resource.local().load("gfx/hud/mmap/markobj");
	public final MapWnd mm;
	private final Map<PMarker, Pair<Gob, RenderTree.Slot>> dcurrent = new HashMap<>();
	private Collection<PMarker> acurrent = Collections.emptyList();
	private Location curloc = null;
	private boolean loading = true;

	public MarkerObjs(MapWnd mm) {
	    this.mm = mm;
	}

	private Area area = null;
	private int markerseq = -1;
	private void updatepos() {
	    try {
		Location loc = mm.view.sessloc;
		if(loc == null)
		    return;
		Coord cc = Coord2d.of(mm.mv.getcc()).floor(tilesz).div(MCache.cutsz);
		Area area = Area.corni(cc.sub(2, 2), cc.add(2, 2)).mul(MCache.cutsz).xl(loc.tc);
		int markerseq = mm.file.markerseq;
		if(Utils.eq(area, this.area) && (markerseq == this.markerseq))
		    return;
		Collection<PMarker> next = new ArrayList<>();
		if(!mm.file.lock.readLock().tryLock())
		    return;
		try {
		    for(Marker m : mm.file.markers) {
			if(!(m instanceof PMarker))
			    continue;
			PMarker pm = (PMarker)m;
			if(pm.onmap && (pm.seg == loc.seg.id) && area.contains(pm.tc))
			    next.add(pm);
		    }
		} finally {
		    mm.file.lock.readLock().unlock();
		}
		this.acurrent = next.isEmpty() ? Collections.emptyList() : next;
		this.area = area;
		this.curloc = loc;
		this.markerseq = markerseq;
		this.loading = true;
	    } catch(Loading l) {
	    }
	}

	private void updateobjs() {
	    if(!loading)
		return;
	    try {
		Collection<PMarker> old = new HashSet<>(dcurrent.keySet());
		for(PMarker m : acurrent) {
		    if(!dcurrent.containsKey(m)) {
			Gob mob = new Gob(mm.ui.sess.glob, Coord2d.of(m.tc.sub(curloc.tc)).add(0.5, 0.5).mul(tilesz));
			MessageBuf sdt = new MessageBuf();
			sdt.addcolor(m.color);
			mob.setattr(new ResDrawable(mob, flag, new MessageBuf(sdt.fin())));
			dcurrent.put(m, Pair.of(mob, slot.add(mob.placed)));
		    }
		    old.remove(m);
		}
		for(PMarker m : old) {
		    Pair<Gob, RenderTree.Slot> r = dcurrent.remove(m);
		    synchronized(r.a) {
			r.b.remove();
		    }
		}
		loading = false;
	    } catch(Loading l) {}
	}

	public TickList.Ticking ticker() {return(this);}
	public void autotick(double dt) {
	    updatepos();
	    updateobjs();
	    for(Pair<Gob, RenderTree.Slot> c : dcurrent.values())
		c.a.ctick(dt);
	}

	public void autogtick(Render out) {
	    for(Pair<Gob, RenderTree.Slot> c : dcurrent.values())
		c.a.gtick(out);
	}

	void remove() {
	    if(slot != null)
		slot.remove();
	}
    }

    public void markobj(long gobid, UID oid, Indir<Resource> resid, byte[] data, String nm) {
	synchronized(deferred) {
	    deferred.add(new Runnable() {
		    double f = 0;
		    public void run() {
			Resource res = resid.get();
			String rnm = nm;
			if(rnm == null) {
			    GobIcon.Icon micon = GobIcon.getfac(res).create(wdgctx.curry(MapWnd.this), res, new MessageBuf(data));
			    rnm = micon.name();
			}
			double now = Utils.rtime();
			if(f == 0)
			    f = now;
			Gob gob = ui.sess.glob.oc.getgob(gobid);
			if(gob == null) {
			    if(now - f < 1.0)
				throw(new Loading());
			    return;
			}
			Coord tc = gob.rc.floor(tilesz);
			MCache.Grid obg = ui.sess.glob.map.getgrid(tc.div(cmaps));
			SMarker mark;
			if(!view.file.lock.writeLock().tryLock())
			    throw(new Loading());
			try {
			    MapFile.GridInfo info = view.file.gridinfo.get(obg.id);
			    if(info == null)
				throw(new Loading());
			    Coord sc = tc.add(info.sc.sub(obg.gc).mul(cmaps));
			    SMarker prev = view.file.smarker(res.name, info.seg, sc);
			    if(prev == null) {
				mark = new SMarker(file, info.seg, sc, rnm, oid, new Resource.Saved(Resource.remote(), res.name, res.ver), data);
				view.file.add(mark);
			    } else {
				mark = prev;
				if(!Arrays.equals(prev.data, data) || (prev.seg != info.seg) || !eq(prev.tc, sc) || !eq(prev.nm, rnm)) {
				    prev.data = data;
				    prev.seg = info.seg;
				    prev.tc = sc;
				    prev.nm = rnm;
				    view.file.update(prev);
				}
			    }
			} finally {
			    view.file.lock.writeLock().unlock();
			}
			synchronized(gob) {
			    gob.setattr(new MarkerID(gob, mark));
			}
		    }
		});
	}
    }

    public static class ExportWindow extends Window implements MapFile.ExportStatus {
	private Thread th;
	private volatile String prog = "Exporting map...";

	public ExportWindow() {
	    super(UI.scale(new Coord(300, 65)), "Exporting map...", true);
	    adda(new Button(UI.scale(100), "Cancel", false, this::cancel), csz().x / 2, UI.scale(40), 0.5, 0.0);
	}

	public void run(Thread th) {
	    (this.th = th).start();
	}

	public void cdraw(GOut g) {
	    g.text(prog, UI.scale(new Coord(10, 10)));
	}

	public void cancel() {
	    th.interrupt();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(!th.isAlive())
		destroy();
	}

	public void grid(int cs, int ns, int cg, int ng) {
	    this.prog = String.format("Exporting map cut %,d/%,d in segment %,d/%,d", cg, ng, cs, ns);
	}

	public void mark(int cm, int nm) {
	    this.prog = String.format("Exporting marker", cm, nm);
	}
    }

    public static class ImportWindow extends Window {
	private Thread th;
	private volatile String prog = "Initializing";
	private double sprog = -1;

	public ImportWindow() {
	    super(UI.scale(new Coord(300, 65)), "Importing map...", true);
	    adda(new Button(UI.scale(100), "Cancel", false, this::cancel), csz().x / 2, UI.scale(40), 0.5, 0.0);
	}

	public void run(Thread th) {
	    (this.th = th).start();
	}

	public void cdraw(GOut g) {
	    String prog = this.prog;
	    if(sprog >= 0)
		prog = String.format("%s: %d%%", prog, (int)Math.floor(sprog * 100));
	    else
		prog = prog + "...";
	    g.text(prog, UI.scale(new Coord(10, 10)));
	}

	public void cancel() {
	    th.interrupt();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(!th.isAlive())
		destroy();
	}

	public void prog(String prog) {
	    this.prog = prog;
	    this.sprog = -1;
	}

	public void sprog(double sprog) {
	    this.sprog = sprog;
	}
    }

    public void exportmap(Path path) {
	GameUI gui = getparent(GameUI.class);
	ExportWindow prog = new ExportWindow();
	Thread th = new HackThread(() -> {
		boolean complete = false;
		try {
		    try {
			try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
			    file.export(out, MapFile.ExportFilter.all, prog);
			}
			complete = true;
		    } finally {
			if(!complete)
			    Files.deleteIfExists(path);
		    }
		} catch(IOException e) {
		    e.printStackTrace(Debug.log);
		    gui.error("Unexpected error occurred when exporting map.");
		} catch(InterruptedException e) {
		}
	}, "Mapfile exporter");
	prog.run(th);
	gui.adda(prog, gui.sz.div(2), 0.5, 1.0);
    }

    public void importmap(Path path) {
	GameUI gui = getparent(GameUI.class);
	ImportWindow prog = new ImportWindow();
	Thread th = new HackThread(() -> {
		try {
		    try(SeekableByteChannel fp = Files.newByteChannel(path)) {
			long size = fp.size();
			class Updater extends CountingInputStream {
			    Updater(InputStream bk) {super(bk);}

			    protected void update(long val) {
				super.update(val);
				prog.sprog((double)pos / (double)size);
			    }
			}
			prog.prog("Validating map data");
			file.reimport(new Updater(new BufferedInputStream(Channels.newInputStream(fp))), MapFile.ImportFilter.readonly);
			prog.prog("Importing map data");
			fp.position(0);
			file.reimport(new Updater(new BufferedInputStream(Channels.newInputStream(fp))), MapFile.ImportFilter.all);
		    }
		} catch(InterruptedException e) {
		} catch(Exception e) {
		    e.printStackTrace(Debug.log);
		    gui.error("Could not import map: " + e.getMessage());
		}
	}, "Mapfile importer");
	prog.run(th);
	gui.adda(prog, gui.sz.div(2), 0.5, 1.0);
    }

    public void exportmap() {
        Window choices=new Window(UI.scale(340,155),hearthborn.L10n.tr("Export map"),true);
        choices.reqclose(choices::destroy);
        choices.add(new Button(UI.scale(320),"Map data (.hmap)",false,()->{choices.destroy();exportdata();}),UI.scale(10,5));
        choices.add(new Button(UI.scale(320),"Current segment as PNG",false,()->{choices.destroy();exportimage(false);}),UI.scale(10,40));
        choices.add(new Button(UI.scale(320),"All saved tiles as PNG (.zip)",false,()->{choices.destroy();exportimage(true);}),UI.scale(10,75));
        choices.add(new Label(hearthborn.L10n.tr("PNG contains terrain, without markers.")),UI.scale(10,115));
        GameUI gui=getparent(GameUI.class);
        gui.adda(choices,gui.sz.div(2),.5,.5);
    }

    private void exportimage(boolean tiles) {
        MiniMap.Location loc=view.displayLocation();
        if(!tiles&&loc==null){ui.error(hearthborn.L10n.tr("No map segment is selected."));return;}
        final long segment=loc==null?0:loc.seg.id;
        String extension=tiles?"zip":"png";
        FilePicker dialog=ui.wnd.toolkit().picker().make(FilePicker.Mode.SAVE,ui.wnd);
        dialog.filter(tiles?"PNG map tiles":"Map image",extension);
        dialog.show().consume(Promise.cnonnull(chosen->{
            Path target=chosen.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("."+extension)?chosen:chosen.resolveSibling(chosen.getFileName()+"."+extension);
            GameUI gui=getparent(GameUI.class);
            ExportWindow progress=new ExportWindow();progress.reqclose(progress::cancel);
            Thread worker=new HackThread(()->{
                try{
                    java.util.function.Consumer<String> status=text->progress.prog=hearthborn.L10n.tr("Exporting map images")+": "+text;
                    if(tiles)hearthborn.map.MapImageExport.tiles(file,target,status);
                    else hearthborn.map.MapImageExport.png(file,segment,target,status);
                    gui.ui.success(hearthborn.L10n.format("Map exported: %s",target.toString()));
                }catch(InterruptedException cancelled){
                }catch(Exception failure){
                    new Warning(failure,"Map image export failed").issue();
                    gui.error(hearthborn.L10n.tr("Could not export map image:")+" "+hearthborn.L10n.tr(failure.getMessage()==null?failure.toString():failure.getMessage()));
                }
            },"Map image exporter");
            gui.adda(progress,gui.sz.div(2),.5,.5);progress.run(worker);
        })).report(ui);
    }

    private void exportdata() {
	FilePicker dialog = ui.wnd.toolkit().picker().make(FilePicker.Mode.SAVE, ui.wnd);
	dialog.filter("Exported Haven map data", "hmap");
	dialog.show().consume(Promise.cnonnull(path -> {
	    if(path.getFileName().toString().indexOf('.') < 0)
		path = path.resolveSibling(path.getFileName() + ".hmap");
	    exportmap(path);
	})).report(ui);
    }

    public void importmap() {
	FilePicker dialog = ui.wnd.toolkit().picker().make(FilePicker.Mode.OPEN, ui.wnd);
	dialog.filter("Exported Haven map data", "hmap");
	dialog.show().consume(Promise.cnonnull(this::importmap)).report(ui);
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("exportmap", (cons, args) -> {
	    if(args.length > 1)
		exportmap(Utils.path(args[1]));
	    else
		exportmap();
	});
	cmdmap.put("importmap", (cons, args) -> {
	    if(args.length > 1)
		importmap(Utils.path(args[1]));
	    else
		importmap();
	});
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
