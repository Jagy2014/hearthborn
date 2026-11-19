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
import java.util.function.*;
import java.awt.image.*;
import java.awt.Color;
import haven.MapFile.Segment;
import haven.MapFile.DataGrid;
import haven.MapFile.Grid;
import haven.MapFile.GridInfo;
import haven.MapFile.Marker;
import haven.MapFile.PMarker;
import haven.MapFile.SMarker;
import haven.MapFile.TileInfo;
import static haven.MCache.cmaps;
import static haven.MCache.tilesz;
import static haven.OCache.posres;

public class MiniMap extends Widget {
    public static final Tex bg = Resource.loadtex("gfx/hud/mmap/ptex");
    public static final Tex nomap = Resource.loadtex("gfx/hud/mmap/nomap");
    public final MapFile file;
    public Markers markers = new Markers(this);
    public Location curloc;
    /** Keep building the map even while the widget is hidden, for the detached window. */
    public boolean loadHidden;
    public Location sessloc;
    public GobIcon.Settings iconconf;
    public List<DisplayIcon> icons = Collections.emptyList();
    protected Locator setloc;
    protected boolean follow;
    protected int zoomlevel = 0, maglevel = 1 << Utils.clip((int)Math.round(Math.log(UI.scale(1.0)) / Math.log(2)), 0, 3);
    protected DisplayGrid[] display = {};
    protected Area dgext, dtext;
    protected Segment dseg;
    protected int dlvl, dmag;
    protected Location dloc;

    private final hearthborn.map.MapIconTrace iconTrace=new hearthborn.map.MapIconTrace();
    private final hearthborn.world.SiegePointers siegePins=new hearthborn.world.SiegePointers();
    private final hearthborn.planner.RoomPlan roomPlan=new hearthborn.planner.RoomPlan(this);
    private final hearthborn.map.MapDisplay extra = new hearthborn.map.MapDisplay(this);
    private final hearthborn.map.PlayerMarkers playerMarkers = new hearthborn.map.PlayerMarkers(this);
    public Location displayLocation() {return dloc;}
    public int displayZoom() {return zoomlevel;}
    public void dispose() {siegePins.dispose();extra.dispose();playerMarkers.dispose();super.dispose();}
    public MiniMap(Coord sz, MapFile file) {
	super(sz);
	this.file = file;
    }

    public MiniMap(MapFile file) {
	this(Coord.z, file);
    }

    protected void attached() {
	if(iconconf == null) {
	    GameUI gui = getparent(GameUI.class);
	    if(gui != null)
		iconconf = gui.iconconf;
	}
	super.attached();
    }

    public static class Location {
	public final Segment seg;
	public final Coord tc;

	public Location(Segment seg, Coord tc) {
	    Objects.requireNonNull(seg);
	    Objects.requireNonNull(tc);
	    this.seg = seg; this.tc = tc;
	}

	public String toString() {
	    return(String.format("(%d, %d) @ %s", tc.x, tc.y, Long.toUnsignedString(seg.id, 16)));
	}
    }

    public interface Locator {
	Location locate(MapFile file) throws Loading;
    }

    public static class SessionLocator implements Locator {
	public final Session sess;
	private MCache.Grid lastgrid = null;
	private Location lastloc;

	public SessionLocator(Session sess) {this.sess = sess;}

	public Location locate(MapFile file) {
	    MCache map = sess.glob.map;
	    if(lastgrid != null) {
		synchronized(map.grids) {
		    if(map.grids.get(lastgrid.gc) == lastgrid) {
			GridInfo info = file.gridinfo.get(lastgrid.id);
			if((info != null) && (info.seg == lastloc.seg.id))
			    return(lastloc);
		    }
		}
		lastgrid = null;
		lastloc = null;
	    }
	    Collection<MCache.Grid> grids = new ArrayList<>();
	    synchronized(map.grids) {
		grids.addAll(map.grids.values());
	    }
	    for(MCache.Grid grid : grids) {
		GridInfo info = file.gridinfo.get(grid.id);
		if(info == null)
		    continue;
		Segment seg = file.segments.get(info.seg);
		if(seg != null) {
		    Location ret = new Location(seg, info.sc.sub(grid.gc).mul(cmaps));
		    lastgrid = grid;
		    lastloc = ret;
		    return(ret);
		}
	    }
	    throw(new Loading("No mapped grids found."));
	}
    }

    /** Session transform must use the player's grid, not an arbitrary cached layer. */
    public static class PlayerSessionLocator implements Locator {
        private final MapView map;
        public PlayerSessionLocator(MapView map){this.map=map;}
        public Location locate(MapFile file){
            Gob player=map.player();if(player==null)throw new Loading("Waiting for player");
            MCache.Grid grid=map.ui.sess.glob.map.getgridt(player.rc.floor(tilesz));
            MapFile.GridInfo info=file.gridinfo.get(grid.id);if(info==null)throw new Loading("Waiting for player grid mapping");
            MapFile.Segment segment=file.segments.get(info.seg);if(segment==null)throw new Loading("Waiting for player map segment");
            return new Location(segment,info.sc.sub(grid.gc).mul(cmaps));
        }
    }

    public static class MapLocator implements Locator {
	public final MapView mv;

	public MapLocator(MapView mv) {this.mv = mv;}

	public Location locate(MapFile file) {
	    Coord mc = new Coord2d(mv.getcc()).floor(MCache.tilesz);
	    if(mc == null)
		throw(new Loading("Waiting for initial location"));
	    MCache.Grid plg = mv.ui.sess.glob.map.getgrid(mc.div(cmaps));
	    GridInfo info = file.gridinfo.get(plg.id);
	    if(info == null)
		throw(new Loading("No grid info, probably coming soon"));
	    Segment seg = file.segments.get(info.seg);
	    if(seg == null)
		throw(new Loading("No segment info, probably coming soon"));
	    return(new Location(seg, info.sc.mul(cmaps).add(mc.sub(plg.ul))));
	}
    }

    public static class SpecLocator implements Locator {
	public final long seg;
	public final Coord tc;

	public SpecLocator(long seg, Coord tc) {this.seg = seg; this.tc = tc;}

	public Location locate(MapFile file) {
	    Segment seg = file.segments.get(this.seg);
	    if(seg == null)
		return(null);
	    return(new Location(seg, tc));
	}
    }

    public static class MarkerIcon implements ItemInfo.Owner, ItemInfo.Name.Dynamic {
	public final Markers o;
	public final Marker m;
	private final Loader loader;
	private Loader.Future<GobIcon.Icon> load;
	private GobIcon.Icon icon;
	private int lseq, iseq;

	public MarkerIcon(Markers o, Marker m) {
	    this.o = o;
	    this.m = m;
	    this.loader = o.mm.ui.loader;
	}

	private static final OwnerContext.ClassResolver<MarkerIcon> ctxr = new OwnerContext.ClassResolver<MarkerIcon>()
	    .add(Marker.class, i -> i.m)
	    .add(MiniMap.class, i -> i.o.mm)
	    .add(UI.class, i -> i.o.mm.ui)
	    .add(Glob.class, i -> i.o.mm.ui.sess.glob)
	    .add(Session.class, i -> i.o.mm.ui.sess);
	public <T> T context(Class<T> cl) {
	    return(ctxr.context(cl, this));
	}

	private GobIcon.Icon create() {
	    if(m instanceof PMarker) {
		return(new Flag(this, ((PMarker)m).color, m.nm));
	    } else {
		SMarker sm = (SMarker)m;
		Resource res = sm.res.get();
		return(GobIcon.getfac(res).create(this, res, new MessageBuf(sm.data)));
	    }
	}

	private void ckload() {
	    /* XXX: Arguably, the loader task should do this part itself. */
	    if(load.done()) {
		icon = load.get();
		iseq = lseq;
		load = null;
		info = null;
		o.seq++;
	    }
	}

	private void update() {
	    int nseq = m.seq;
	    boolean reload = false;
	    if(load == null) {
		reload = (nseq != this.iseq);
	    } else {
		if(nseq != this.lseq)
		    reload = true;
		else
		    ckload();
	    }
	    if(reload) {
		if(load != null)
		    load.cancel();
		load = loader.defer(this::create);
		lseq = nseq;
	    }
	}

	public GobIcon.Icon icon() {
	    synchronized(o) {
		if((load == null) && (icon == null)) {
		    load = loader.defer(this::create);
		    lseq = o.mseq;
		    o.loading = true;
		}
		if(load != null)
		    ckload();
		if(icon == null)
		    throw(new Loading());
		return(icon);
	    }
	}

	public String name() {
	    return(m.nm);
	}

	private List<ItemInfo> info = null;
	public List<ItemInfo> info() {
	    if(info == null) {
		Object[] raw = icon().info(this);
		info = ItemInfo.buildinfo(this, raw);
	    }
	    return(info);
	}
    }

    public static class Markers {
	public final MiniMap mm;
	public int seq;
	private final Map<Marker, MarkerIcon> icons = new HashMap<>();
	private volatile int mseq = -1;
	private volatile Future<?> updater = null;
	private boolean loading;

	private Markers(MiniMap mm) {
	    this.mm = mm;
	}

	private void update0() {
	    boolean loading = false;
	    try(Locked lk = new Locked(mm.file.lock.readLock())) {
		int nseq = mm.file.markerseq;
		Set<Marker> current = new HashSet<>(mm.file.markers);
		synchronized(this) {
		    for(Iterator<Map.Entry<Marker, MarkerIcon>> i = icons.entrySet().iterator(); i.hasNext();) {
			Map.Entry<Marker, MarkerIcon> ent = i.next();
			Marker m = ent.getKey();
			MarkerIcon st = ent.getValue();
			if(current.contains(m)) {
			    current.remove(m);
			    st.update();
			    if(st.load != null)
				loading = true;
			} else {
			    i.remove();
			}
		    }
		    boolean ch = false;
		    for(Marker m : current) {
			MarkerIcon st = new MarkerIcon(this, m);
			icons.put(m, st);
			st.update();
			if(st.load != null)
			    loading = true;
			ch = true;
		    }
		    mseq = nseq;
		    if(ch)
			seq++;
		}
	    } finally {
		this.loading = loading;
		updater = null;
	    }
	}

	private void update() {
	    if((mseq != mm.file.markerseq) || loading) {
		if(updater == null)
		    updater = Defer.later(this::update0, null);
	    }
	}

	public MarkerIcon get(Marker m) {
	    synchronized(this) {
		update();
		return(icons.computeIfAbsent(m, k -> new MarkerIcon(this, k)));
	    }
	}

	public Collection<? extends MarkerIcon> known() {
	    return(icons.values());
	}
    }

    public void center(Location loc) {
	curloc = loc;
    }

    public Location resolve(Locator loc) {
	if(!file.lock.readLock().tryLock())
	    throw(new Loading("Map file is busy"));
	try {
	    return(loc.locate(file));
	} finally {
	    file.lock.readLock().unlock();
	}
    }

    public Coord xlate(Location loc) {
	Location dloc = this.dloc;
	if((dloc == null) || (dloc.seg != loc.seg))
	    return(null);
	return(l2dscale(loc.tc.sub(dloc.tc)).add(sz.div(2)));
    }

    public Location xlate(Coord sc) {
	Location dloc = this.dloc;
	if(dloc == null)
	    return(null);
	Coord tc = d2lscale(sc.sub(sz.div(2))).add(dloc.tc);
	return(new Location(dloc.seg, tc));
    }

    /** Corner map keeps the last external view while a detached map shows the room. */
    protected boolean preserveOutsideMap(){return false;}
    private boolean retainedOutside;
    private Location outsideLocation;
    private boolean outsideLoaded;
    private String outsideKey(){return "hearthborn/map/corner-outside-"+Integer.toHexString(file.filename.hashCode());}
    private void retainOutside(){
        if(file==null)return;
        if(!outsideLoaded){
            try{
                String saved=Utils.getpref(outsideKey(),"");
                if(!saved.isEmpty()){String[] parts=saved.split(",");if(parts.length!=3){outsideLoaded=true;return;}outsideLocation=resolve(new SpecLocator(Long.parseLong(parts[0]),Coord.of(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]))));if(outsideLocation==null)return;}
                outsideLoaded=true;
            }catch(Loading wait){return;}catch(IllegalArgumentException invalid){outsideLoaded=true;}
        }
        if(outsideLocation!=null)center(outsideLocation);
    }
    private void rememberOutside(){
        if(file==null||curloc==null)return;
        if(outsideLocation==null||outsideLocation.seg.id!=curloc.seg.id||!outsideLocation.tc.equals(curloc.tc)){
            outsideLocation=curloc;Utils.setpref(outsideKey(),curloc.seg.id+","+curloc.tc.x+","+curloc.tc.y);
        }
        outsideLoaded=true;
    }
    private Locator sesslocator;
    public void tick(double dt) {
        GameUI owner=getparent(GameUI.class);if(owner!=null&&owner.iconconf!=null)iconconf=owner.iconconf;
        boolean indoors=preserveOutsideMap()&&roomPlan.indoors();
        boolean hold=preserveOutsideMap()&&indoors;
        if(hold)retainOutside();
        // A new installation/session may have no external map yet. Never freeze an empty view.
        retainedOutside=hold&&outsideLocation!=null;
	if(setloc != null&&!retainedOutside) {
	    try {
		Location loc = resolve(setloc);
		center(loc);
		if(!follow)
		    setloc = null;
	    } catch(Loading l) {
	    }
	}
	if((sesslocator == null) && (ui != null) && (ui.sess != null))
	    sesslocator = new SessionLocator(ui.sess);
	if(sesslocator != null) {
	    try {
                sessloc=resolve(owner!=null&&owner.map!=null&&owner.map.player()!=null?new PlayerSessionLocator(owner.map):sesslocator);
	    } catch(Loading l) {
	    }
	}
        if(preserveOutsideMap()&&!indoors&&roomPlan.locationKnown())rememberOutside();
	/* Looking at every object in view is the slowest thing the map does; a new or vanished object shows up a fifth of a second late, positions follow every tick. */
	double scanned = Utils.rtime();
	if(scanned >= nextIconScan) {
	    icons = findicons(icons);
	    nextIconScan = scanned + ICON_SCAN;
	} else {
	    icons = movedicons(icons);
	}
        if(!retainedOutside)extra.tick();
	if(tvisible() || loadHidden) {
	    Location loc = this.curloc;
	    if(loc != null)
		redisplay(loc);
            iconTrace.sample(this);
	}
    }

    public void center(Locator loc) {
	setloc = loc;
	follow = false;
    }

    public void follow(Locator loc) {
	setloc = loc;
	follow = true;
    }

    public static class Scale2D implements Pipe.Op {
	public final Coord cc;
	public final float f;

	public Scale2D(Coord cc, float f) {
	    this.cc = cc;
	    this.f = f;
	}

	public void apply(Pipe buf) {
	    Ortho2D st = (Ortho2D)buf.get(States.vxf);
	    float w = st.r - st.l, h = st.b - st.u;
	    buf.prep(new Ortho2D(cc.x + ((st.l - cc.x) / f), cc.y + ((st.u - cc.y) / f),
				 cc.x + ((st.r - cc.x) / f), cc.y + ((st.b - cc.y) / f)));
	}
    }

    public static final Color notifcol = new Color(255, 128, 0, 255);
    public class DisplayIcon {
	public final GobIcon attr;
	public final Gob gob;
	public final GobIcon.Icon icon;
	public final GobIcon.Setting conf;
	public Coord2d rc = null;
	public Coord sc = null;
	public double ang = 0.0;
	public int z;
	public double stime, ntime;
	public boolean notify;
	private Consumer<UI> snotify;
	private boolean markchecked;

	public DisplayIcon(GobIcon attr, GobIcon.Setting conf) {
	    this.attr = attr;
	    this.gob = attr.gob;
	    this.icon = attr.icon();
	    this.z = icon.z();
	    this.stime = ui.lasttick;
	    this.conf = conf;
	    if(this.notify = conf.notify && (hearthborn.world.GobMarks.corpse(icon.id()) || !hearthborn.world.GobMarks.hasDedicatedIconSound(conf.res.name))
                && !hearthborn.world.PlayerWarnings.ownsIconSound(gob)
                && !hearthborn.ui.NearbySiegeCheck.ownsIconSound(hearthborn.util.GameObjects.resourceName(gob)))
		this.snotify = conf.notification();
	}

	public void update(Coord2d rc, double ang) {
	    this.rc = rc;
	    this.ang = ang;
	    if(notify) {
		if((ntime = (ui.lasttick - stime) * 0.5) > 1.0) {
		    notify = false;
		    snotify = null;
		}
	    }
	}

	public void dispupdate() {
	    if((this.rc == null) || (sessloc == null) || (dloc == null) || (dloc.seg != sessloc.seg))
		this.sc = null;
	    else
		this.sc = p2c(this.rc);
	}

	public void draw(GOut g) {
	    icon.draw(g, sc);
	    if(notify) {
		double f = 1.0 + (Math.pow(Math.sin(ntime * Math.PI * 1.5), 2) * 1.0);
		double a = (ntime < 0.5) ? 0.5 : (0.5 - (ntime - 0.5));
		g.usestate(new ColorMask(notifcol));
		g.usestate(new Scale2D(sc.add(g.tx), (float)f));
		g.chcolor(255, 255, 255, (int)Math.round(255 * a));
		icon.draw(g, sc);
		g.defstate();
	    }
	    if(snotify != null) {
		snotify.accept(ui);
		snotify = null;
	    }
	}

	public boolean force() {
	    if(notify)
		return(true);
	    return(false);
	}
    }

    public static class MarkerID extends GAttrib {
	public final Marker mark;

	public MarkerID(Gob gob, Marker mark) {
	    super(gob);
	    this.mark = mark;
	}

	public static Gob find(OCache oc, Marker mark) {
	    synchronized(oc) {
		for(Gob gob : oc) {
		    MarkerID iattr = gob.getattr(MarkerID.class);
		    if((iattr != null) && (iattr.mark == mark))
			return(gob);
		}
	    }
	    return(null);
	}
    }

    public static class Flag extends GobIcon.Icon {
	public static final Resource res = Resource.local().loadwait("gfx/hud/mmap/flag");
	public static final Resource.Image fg = res.flayer(Resource.imgc, 0);
	public static final Resource.Image bg = res.flayer(Resource.imgc, 1);
	public static final Coord cc = UI.scale(res.flayer(Resource.negc).cc);
	public final Color col;
	public final String name;

	public Flag(OwnerContext owner, Color col, String name) {
	    super(owner, res);
	    this.col = col;
	    this.name = name;
	}

	public String name() {
	    return(name);
	}

	public BufferedImage image() {
	    WritableRaster buf = PUtils.imgraster(bg.sz);
	    PUtils.colmul(PUtils.blit(buf, fg.img.getRaster(), fg.o), col);
	    PUtils.alphablit(buf, bg.img.getRaster(), bg.o);
	    return(PUtils.rasterimg(buf));
	}

	public void draw(GOut g, Coord c) {
	    Coord ul = c.sub(cc);
	    g.chcolor(col);
	    g.image(fg, ul);
	    g.chcolor();
	    g.image(bg, ul);
	}

	public boolean checkhit(Coord c) {
	    return(c.isect(cc.inv(), bg.ssz));
	}

	public Object[] id() {
	    return(new Object[] {col});
	}
    }

    public static class DisplayMarker {
	public final MiniMap mm;
	public final Marker m;
	public Coord sc = null;

	public DisplayMarker(MiniMap mm, Marker marker) {
	    this.mm = mm;
	    this.m = marker;
	}

	public GobIcon.Icon icon() {
	    return(mm.markers.get(m).icon());
	}

	public void dispupdate() {
	    if(mm.dloc == null)
		this.sc = null;
	    else
		this.sc = mm.l2dscale(m.tc).sub(mm.l2dscale(mm.dloc.tc)).add(mm.sz.div(2));
	}

	public void draw(GOut g, Coord c) {
	    try {
		icon().draw(g, c);
	    } catch(Loading l) {}
	}

	private int tseq = -1, caveSeq = -1;
	private BufferedImage tooltip = null;
	public BufferedImage tooltip() {
	    MarkerIcon minf = mm.markers.get(m);
	    int caves = hearthborn.map.CaveLevels.generation();
	    if((tooltip == null) || (minf.iseq != tseq) || (caves != caveSeq)) {
		tooltip = ItemInfo.longtip(minf.info());
		java.awt.image.BufferedImage level = hearthborn.map.CaveLevels.tip(m.seg);
		if(level != null)
		    tooltip = (tooltip == null) ? level : ItemInfo.catimgs(3, tooltip, level);
		tseq = minf.iseq;
		caveSeq = caves;
	    }
	    return(tooltip);
	}
    }

    public static class DisplayGrid {
	public final MiniMap mm;
	public final MapFile file;
	public final Segment seg;
	public final Coord sc;
	public final Area mapext;
	public final Indir<? extends DataGrid> gref;
	public Coord dc;
	private Tex img = null;
	private Defer.Future<Tex> nextimg = null;

	public DisplayGrid(MiniMap mm, Segment seg, Coord sc, int lvl, Indir<? extends DataGrid> gref) {
	    this.mm = mm;
	    this.file = seg.file();
	    this.seg = seg;
	    this.sc = sc;
	    this.gref = gref;
	    mapext = Area.sized(sc.mul(cmaps.mul(1 << lvl)), cmaps.mul(1 << lvl));
	}

	class CachedImage {
	    final Function<DataGrid, Defer.Future<Tex>> src;
	    DataGrid cgrid;
	    Defer.Future<Tex> next;
	    Tex img;

	    CachedImage(Function<DataGrid, Defer.Future<Tex>> src) {
		this.src = src;
	    }

	    public Tex get() {
		DataGrid grid = gref.get();
		if(grid != cgrid) {
		    if(next != null)
			next.cancel();
		    next = src.apply(grid);
		    cgrid = grid;
		}
		if(next != null) {
		    try {
			img = next.get();
		    } catch(Loading l) {}
		}
		return(img);
	    }
	}

	private CachedImage img_c;
	public Tex img() {
	    if(img_c == null) {
		img_c = new CachedImage(grid -> {
			if(grid instanceof MapFile.ZoomGrid) {
			    return(Defer.later(() -> new TexI(grid.render(sc.mul(cmaps))).filter(Texture.Filter.NEAREST)));
			} else {
			    return(Defer.later(new Defer.Callable<Tex>() {
				    MapFile.View view = new MapFile.View(seg);

				    public TexI call() {
					try(Locked lk = new Locked(file.lock.readLock())) {
					    for(int y = -1; y <= 1; y++) {
						for(int x = -1; x <= 1; x++) {
						    view.addgrid(sc.add(x, y));
						}
					    }
					    view.fin();
					    return(new TexI(MapSource.drawmap(view, Area.sized(sc.mul(cmaps), cmaps))).filter(Texture.Filter.NEAREST));
					}
				    }
				}));
			}
		});
	    }
	    return(img_c.get());
	}

	private Map<String, CachedImage> olimg_c = new HashMap<>();
	public Tex olimg(String tag) {
	    CachedImage ret;
	    synchronized(olimg_c) {
		if((ret = olimg_c.get(tag)) == null)
		    olimg_c.put(tag, ret = new CachedImage(grid -> Defer.later(() -> new TexI(grid.olrender(sc.mul(cmaps), tag)))));
	    }
	    return(ret.get());
	}

	private Collection<DisplayMarker> markers = Collections.emptyList();
	private int markerseq = -1;
	public Collection<DisplayMarker> markers(boolean remark) {
	    if(remark && (markerseq != file.markerseq)) {
		if(file.lock.readLock().tryLock()) {
		    try {
			ArrayList<DisplayMarker> marks = new ArrayList<>();
			for(Marker mark : file.markers) {
			    if((mark.seg == this.seg.id) && mapext.contains(mark.tc))
				marks.add(new DisplayMarker(mm, mark));
			}
			marks.trimToSize();
			markers = (marks.size() == 0) ? Collections.emptyList() : marks;
			markerseq = file.markerseq;
		    } finally {
			file.lock.readLock().unlock();
		    }
		}
	    }
	    return(markers);
	}
    }

    private Coord l2dscale(Coord c) {
	return(c.mul(dmag).div(1 << dlvl));
    }

    private Coord d2lscale(Coord c) {
	return(c.mul(1 << dlvl).div(dmag));
    }

    public Coord st2c(Coord tc) {
	return(l2dscale(tc.add(sessloc.tc).sub(dloc.tc)).add(sz.div(2)));
    }

    /** Map-file tile to the map view. Unlike {@link #st2c}, this does not use the session offset. */
    public Coord tc2c(Coord tc) {
	if(dloc == null)
	    return(null);
	return(l2dscale(tc.sub(dloc.tc)).add(sz.div(2)));
    }

    public Coord p2c(Coord2d pc) {
	return(st2c(pc.floor(tilesz)));
    }

    private void redisplay(Location loc) {
	Coord hsz = sz.div(2);
	Coord zmaps = cmaps.mul(1 << zoomlevel);
	Area next = Area.sized(loc.tc.sub(hsz.mul(1 << zoomlevel).div(maglevel)).div(zmaps),
			       sz.div(maglevel).div(cmaps).add(2, 2));
	if((display == null) || (loc.seg != dseg) || (zoomlevel != dlvl) || (maglevel != dmag) || !next.equals(dgext)) {
	    DisplayGrid[] nd = new DisplayGrid[next.rsz()];
	    if((display != null) && (loc.seg == dseg) && (zoomlevel == dlvl)) {
		for(Coord c : dgext) {
		    if(next.contains(c))
			nd[next.ri(c)] = display[dgext.ri(c)];
		}
	    }
	    display = nd;
	    dseg = loc.seg;
	    dlvl = zoomlevel;
	    dmag = maglevel;
	    dgext = next;
	    dtext = Area.sized(next.ul.mul(zmaps), next.sz().mul(zmaps));
	}
	dloc = loc;
	if(file.lock.readLock().tryLock()) {
	    try {
		for(Coord c : dgext) {
		    if(display[dgext.ri(c)] == null)
			display[dgext.ri(c)] = new DisplayGrid(this, dloc.seg, c, dlvl, dloc.seg.grid(dlvl, c.mul(1 << dlvl)));
		}
	    } finally {
		file.lock.readLock().unlock();
	    }
	}
	for(Coord c : dgext) {
	    DisplayGrid dgrid = display[dgext.ri(c)];
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(true))
		mark.dispupdate();
	}
	for(DisplayIcon icon : icons)
	    icon.dispupdate();
    }

    public void drawgrid(GOut g, Coord ul, DisplayGrid disp) {
	try {
	    disp.dc = ul;
	    Tex img = disp.img();
	    if(img != null)
		g.image(img, ul, img.sz().mul(dmag));
            extra.grid(g,ul,disp);
	} catch(Loading l) {
	}
    }

    public void drawmap(GOut g) {
	Coord hsz = sz.div(2);
	for(Coord c : dgext) {
	    Coord ul = c.mul(cmaps).mul(dmag).sub(l2dscale(dloc.tc)).add(hsz);
	    DisplayGrid disp = display[dgext.ri(c)];
	    if(disp == null)
		continue;
	    drawgrid(g, ul, disp);
	}
    }

    public void drawmarkers(GOut g) {
	Coord hsz = sz.div(2);
	for(Coord c : dgext) {
	    DisplayGrid dgrid = display[dgext.ri(c)];
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(true)) {
		if((mark.sc == null) || filter(mark))
		    continue;
		mark.draw(g, mark.sc);
                extra.marker(g,mark);
	    }
	}
    }

    private static final double ICON_SCAN = 0.2;
    private double nextIconScan = 0;
    /** The icons of the last scan, without those whose object has gone, at the objects' present places. */
    private List<DisplayIcon> movedicons(List<DisplayIcon> prev) {
	if(prev.isEmpty())
	    return(prev);
	List<DisplayIcon> ret = new ArrayList<>(prev.size());
	for(DisplayIcon disp : prev) {
	    if(disp.gob.removed)
		continue;
	    disp.update(disp.gob.rc, disp.gob.a);
	    ret.add(disp);
	}
	return(ret);
    }
    private final Map<Long,GobIcon> radarIcons=new HashMap<>();
    public List<DisplayIcon> findicons(Collection<? extends DisplayIcon> prev) {
	if((ui.sess == null) || (iconconf == null))
	    return(Collections.emptyList());
	Map<GobIcon, DisplayIcon> pmap = Collections.emptyMap();
	if(prev != null) {
	    pmap = new HashMap<>();
	    for(DisplayIcon disp : prev)
		pmap.put(disp.attr, disp);
	}
	List<DisplayIcon> ret = new ArrayList<>();
	OCache oc = ui.sess.glob.oc;
        Set<Long> localSeen=new HashSet<>();
        List<Gob> objects=oc.snapshot();
	    for(Gob gob : objects) {
		try {
                    if(hearthborn.world.PlayerRelations.player(gob))continue;
		    String tame=hearthborn.map.RadarIcons.domestic(gob);
		    GobIcon icon = gob.getattr(GobIcon.class);
                    String have=icon==null?null:icon.res.get().name;
                    boolean replace=tame!=null&&!tame.equals(have);
                    boolean local=icon==null||replace;
                    if(icon!=null&&!replace)hearthborn.map.RadarIcons.learn(gob,icon);
                    if(local){localSeen.add(gob.id);icon=tame!=null?hearthborn.map.RadarIcons.pinned(gob,tame,radarIcons.get(gob.id)):hearthborn.map.RadarIcons.icon(gob,radarIcons.get(gob.id));if(icon!=null){radarIcons.put(gob.id,icon);localSeen.add(gob.id);}}
		    if(icon != null) {
			GobIcon.Setting conf = local?hearthborn.map.RadarIcons.setting(iconconf,icon):iconconf.get(icon.icon());
			if((conf != null) && conf.show) {
			    DisplayIcon disp = pmap.remove(icon);
			    if(disp == null)
				disp = new DisplayIcon(icon, conf);
			    ret.add(disp);
			}
		    }
		} catch(Loading l) {
		} catch(Resource.NoSuchResourceException missing) {
		    hearthborn.map.RadarIcons.absent(missing.name);
		}
	    }
        radarIcons.keySet().retainAll(localSeen);
	for(DisplayIcon disp : pmap.values()) {
	    try{if(disp.force()&&!hearthborn.world.PlayerRelations.player(disp.gob))ret.add(disp);}catch(Loading pending){}
	}
	for(DisplayIcon disp : ret)
	    disp.update(disp.gob.rc, disp.gob.a);
	Collections.sort(ret, (a, b) -> a.z - b.z);
	if(ret.size() == 0)
	    return(Collections.emptyList());
	return(ret);
    }

    /** Cave mouths stay on the saved map. They are ridge tiles, not nearby objects. */
    private void drawPassages(GOut g) {
	if(dloc == null || iconconf == null || dgext == null)
	    return;
	if(!shown("gfx/hud/mmap/cave"))
	    return;
	GobIcon.Image mark = hearthborn.map.RadarIcons.passageImage(false);
	if(mark == null)
	    return;
	for(Coord gc : dgext) {
	    DisplayGrid dgrid = display[dgext.ri(gc)];
	    if(dgrid == null || dgrid.mapext == null)
		continue;
	    DataGrid grid;
	    try {
		grid = dgrid.gref.get();
	    } catch(Loading pending) {
		continue;
	    }
	    if(grid == null || grid.tiles == null || grid.tilesets == null)
		continue;
	    int side = (int)Math.sqrt(grid.tiles.length);
	    int span = dgrid.mapext.sz().x;
	    if(side < 1 || side * side != grid.tiles.length || span % side != 0)
		continue;
	    int step = span / side;
	    int[] kind = new int[grid.tilesets.length];
	    boolean any = false;
	    for(int i = 0; i < grid.tilesets.length; i++) {
		kind[i] = hearthborn.map.RadarIcons.passage(grid.tilesets[i].res.name);
		if(kind[i] != 0)
		    any = true;
	    }
	    if(!any)
		continue;
	    Coord origin = dgrid.mapext.ul;
	    for(int y = 0; y < side; y++) {
		for(int x = 0; x < side; x++) {
		    int tile = grid.gettile(new Coord(x, y));
		    if(tile < 0 || tile >= kind.length)
			continue;
		    int which = kind[tile];
		    if(which == 0)
			continue;
		    if(x > 0 && kind[grid.gettile(new Coord(x - 1, y))] == which)
			continue;
		    if(y > 0 && kind[grid.gettile(new Coord(x, y - 1))] == which)
			continue;
		    GobIcon.Image image = mark;
		    Coord tc = origin.add(x * step + step / 2, y * step + step / 2);
		    Coord sc = l2dscale(tc.sub(dloc.tc)).add(sz.div(2));
		    g.image(image.tex, sc.sub(image.cc));
		}
	    }
	}
    }

    private boolean shown(String name) {
	GobIcon.Setting setting = iconconf.settings.get(new GobIcon.Setting.ID(name, GobIcon.Icon.nilid));
	return setting == null || setting.show;
    }

    public void drawicons(GOut g) {
	if((sessloc == null) || (dloc.seg != sessloc.seg))
	    return;
	for(DisplayIcon disp : icons) {
	    if((disp.sc == null) || filter(disp))
		continue;
	    disp.draw(g);
	}
	g.chcolor();
    }

    public void remparty() {
	Map<Long, Party.Member> memb = ui.sess.glob.party.memb;
	if(memb.isEmpty())return;
	for(Iterator<DisplayIcon> it = icons.iterator(); it.hasNext();) {
	    DisplayIcon icon = it.next();
	    if(memb.containsKey(icon.gob.id))
		it.remove();
	}
    }

    public void drawparty(GOut g) {
	if(sessloc==null||displayLocation()==null||displayLocation().seg!=sessloc.seg)return;
	for(Party.Member m : ui.sess.glob.party.memb.values()) {
	    try {
		Coord2d ppc = m.getc();
		if(ppc == null)
		    continue;
                Coord at=p2c(ppc);
                GameUI gui=getparent(GameUI.class);
                boolean own=gui!=null&&m.gobid==gui.plid;
                Tex marker=own?hearthborn.map.MapToolbar.playerMarker():hearthborn.map.MapToolbar.playerMarker(m.col);
		g.chcolor();
                g.rotimage(marker,at,marker.sz().div(2),-m.geta()-(Math.PI/2));
		g.chcolor();
	    } catch(Loading l) {}
	}
    }

    public void drawparts(GOut g){
        if((!preserveOutsideMap()||outsideLocation==null)&&roomPlan.draw(g))return;
        if(retainedOutside){drawmap(g);extra.under(g);drawmarkers(g);return;}
	drawmap(g);
        extra.under(g);
	drawmarkers(g);
        drawicons(g);
        drawPassages(g);
	drawparty(g);
        playerMarkers.draw(g);
        siegePins.drawMap(this,g);
        extra.over(g);
    }

    public void draw(GOut g) {
        if(dloc != null) {
            remparty();
            drawparts(g);
        }
        super.draw(g);
    }

    private static boolean hascomplete(DisplayGrid[] disp, Area dext, Coord c) {
	DisplayGrid dg = disp[dext.ri(c)];
	if(dg == null)
	    return(false);
	return(dg.gref.get() != null);
    }

    protected boolean allowzoomout() {
	DisplayGrid[] disp = this.display;
	Area dext = this.dgext;
	if(dext == null)
	    return(false);
	try {
	    for(int x = dext.ul.x; x < dext.br.x; x++) {
		if(hascomplete(disp, dext, new Coord(x, dext.ul.y)) ||
		   hascomplete(disp, dext, new Coord(x, dext.br.y - 1)))
		    return(true);
	    }
	    for(int y = dext.ul.y; y < dext.br.y; y++) {
		if(hascomplete(disp, dext, new Coord(dext.ul.x, y)) ||
		   hascomplete(disp, dext, new Coord(dext.br.x - 1, y)))
		    return(true);
	    }
	} catch(Loading l) {
	    return(false);
	}
	return(false);
    }

    public DisplayIcon iconat(Coord c) {
	for(ListIterator<DisplayIcon> it = icons.listIterator(icons.size()); it.hasPrevious();) {
	    DisplayIcon disp = it.previous();
	    if((disp.sc != null) && disp.icon.checkhit(c.sub(disp.sc)) && !filter(disp))
		return(disp);
	}
	return(null);
    }

    public DisplayGrid gridat(Coord sc) {
	if((dloc == null) || (dgext == null))
	    return(null);
	Coord hsz = sz.div(2);
	Coord gc = dloc.tc.add(d2lscale(sc.sub(hsz))).div(cmaps.mul(1 << dlvl));
	if(!dgext.contains(gc))
	    return(null);
	return(display[dgext.ri(gc)]);
    }

    public DisplayMarker findmarker(Marker rm) {
	for(DisplayGrid dgrid : display) {
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(false)) {
		if(mark.m == rm)
		    return(mark);
	    }
	}
	return(null);
    }

    public DisplayMarker markerat(Coord tc) {
	for(DisplayGrid dgrid : display) {
	    if(dgrid == null)
		continue;
	    for(DisplayMarker mark : dgrid.markers(false)) {
		try {
		    if(mark.icon().checkhit(l2dscale(tc).sub(l2dscale(mark.m.tc))) && !filter(mark))
			return(mark);
		} catch(Loading l) {}
	    }
	}
	return(null);
    }

    public void markobjs() {
	for(DisplayIcon icon : icons) {
	    try {
		if(icon.markchecked)
		    continue;
		GobIcon aicon = icon.attr;
		Resource res = aicon.res.get();
		GobIcon.Icon micon = icon.icon;
		if(!icon.conf.getmarkablep()) {
		    icon.markchecked = true;
		    continue;
		}
		Coord tc = icon.gob.rc.floor(tilesz);
		MCache.Grid obg = ui.sess.glob.map.getgrid(tc.div(cmaps));
		if(!file.lock.writeLock().tryLock())
		    continue;
		SMarker mid = null;
		try {
		    MapFile.GridInfo info = file.gridinfo.get(obg.id);
		    if(info == null)
			continue;
		    Coord sc = tc.add(info.sc.sub(obg.gc).mul(cmaps));
		    SMarker prev = file.smarker(res.name, info.seg, sc);
		    if(prev == null) {
			if(icon.conf.getmarkp()) {
			    mid = new SMarker(file, info.seg, sc, micon.name(), UID.nil, new Resource.Saved(Resource.remote(), res.name, res.ver), aicon.sdt);
			    file.add(mid);
			} else {
			    mid = null;
			}
		    } else {
			if(!Arrays.equals(prev.data, aicon.sdt)) {
			    prev.data = aicon.sdt;
			    file.update(prev);
			}
			mid = prev;
		    }
		} finally {
		    file.lock.writeLock().unlock();
		}
		if(mid != null) {
		    synchronized(icon.gob) {
			icon.gob.setattr(new MarkerID(icon.gob, mid));
		    }
		}
		icon.markchecked = true;
	    } catch(Loading l) {
		continue;
	    }
	}
    }

    public boolean filter(DisplayIcon icon) {
	MarkerID iattr = icon.gob.getattr(MarkerID.class);
        DisplayMarker marker=iattr==null?null:findmarker(iattr.mark);
        if(marker!=null&&marker.sc!=null&&!filter(marker)){
            // A saved marker must actually be drawable before replacing a live icon.
            try{
                GobIcon.Icon saved=marker.icon();
                if(hearthborn.map.RadarIcons.transition(hearthborn.util.GameObjects.resourceName(icon.gob))&&!saved.res.name.equals(icon.icon.res.name))return false;
                return true;
            }catch(Loading pending){return false;}
            catch(Resource.BadResourceException failed){return false;}
        }
	return(false);
    }

    public boolean filter(DisplayMarker marker) {
	return(false);
    }

    public boolean clickloc(Location loc, int button, boolean press) {
	return(false);
    }

    public boolean clickicon(DisplayIcon icon, Location loc, int button, boolean press) {
	return(false);
    }

    public boolean clickmarker(DisplayMarker mark, Location loc, int button, boolean press) {
	return(false);
    }

    private UI.Grab drag;
    private boolean dragging;
    private Coord dsc, dmc;
    public boolean dragp(int button) {
	return(button == 1);
    }

    private Location dsloc;
    private DisplayIcon dsicon;
    private DisplayMarker dsmark;
    private Gob dsplayer;
    public boolean mousedown(MouseDownEvent ev) {
        dsplayer=null;
        if(ev.propagate(this))
            return(true);
	if(retainedOutside)return true;
	if((!preserveOutsideMap()||outsideLocation==null)&&roomPlan.click(ev.c,ev.b)){dsloc=null;dsicon=null;dsmark=null;return true;}
        if(extra.meteorClick(ev.c,ev.b)){dsloc=null;dsicon=null;dsmark=null;return true;}
	if(hearthborn.map.CaveLevels.click(this, ev.c, ev.b))
	    return(true);
	dsloc = xlate(ev.c);
	if(dsloc != null) {
	    dsplayer = playerMarkers.hit(ev.c);
	    dsicon = iconat(ev.c);
	    dsmark = markerat(dsloc.tc);
	    if((dsicon != null) && clickicon(dsicon, dsloc, ev.b, true))
		return(true);
	    if((dsmark != null) && clickmarker(dsmark, dsloc, ev.b, true))
		return(true);
	    if(clickloc(dsloc, ev.b, true))
		return(true);
	} else {
	    dsloc = null;
	    dsicon = null;
	    dsmark = null;
	}
	if(dragp(ev.b)) {
	    Location loc = curloc;
	    if((drag == null) && (loc != null)) {
		drag = ui.grabmouse(this);
		dsc = ev.c;
		dmc = loc.tc;
		dragging = false;
	    }
	    return(true);
	}
	return(super.mousedown(ev));
    }

    public void mousemove(MouseMoveEvent ev) {
        extra.hover(ev.c);
	if(drag != null) {
	    if(dragging) {
		setloc = null;
		follow = false;
		curloc = new Location(curloc.seg, dmc.add(d2lscale(dsc.sub(ev.c))));
	    } else if(ev.c.dist(dsc) > 5) {
		dragging = true;
	    }
	}
	super.mousemove(ev);
    }

    public boolean mouseup(MouseUpEvent ev) {
	if((drag != null) && (ev.b == 1)) {
	    drag.remove();
	    drag = null;
	}
	release: if(!dragging && (dsloc != null)) {
            GameUI gui=getparent(GameUI.class);
            if(dsplayer!=null&&!dsplayer.removed&&gui!=null&&gui.map!=null){mvclick(gui.map,null,dsloc,dsplayer,ev.b);break release;}
	    if((dsicon != null) && clickicon(dsicon, dsloc, ev.b, false))
		break release;
	    if((dsmark != null) && clickmarker(dsmark, dsloc, ev.b, false))
		break release;
	    if(clickloc(dsloc, ev.b, false))
		break release;
	}
	dsloc = null;
	dsicon = null;
	dsmark = null;
	dsplayer = null;
	dragging = false;
	return(super.mouseup(ev));
    }

    private double zoomacc = 0;
    private double zoomt = 0;

    public boolean mousewheel(MouseWheelEvent ev) {
        if((!preserveOutsideMap()||outsideLocation==null)&&roomPlan.active())return true;
	double step = ev.step();
	if(step == 0)
	    return(true);
	double now = Utils.rtime();
	if((now - zoomt) < hearthborn.ui.WheelSettings.mapDelay())
	    return(true);
	if(Math.abs(step) < 1) {
	    zoomacc += step;
	    if(Math.abs(zoomacc) < 1)
		return(true);
	    step = zoomacc;
	    zoomacc = 0;
	} else {
	    zoomacc = 0;
	}
	zoomt = now;
	if(step > 0) {
	    if(maglevel > 1) {
		maglevel >>= 1;
	    } else {
		if(allowzoomout())
		    zoomlevel = Math.min(zoomlevel + 1, dlvl + 1);
	    }
	} else {
	    if(zoomlevel > 0) {
		zoomlevel--;
	    } else {
		maglevel = Math.min(maglevel << 1, 8);
	    }
	}
	return(true);
    }

    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
	boolean ret = false;
	if(hovering) {
	    for(ListIterator<DisplayIcon> it = icons.listIterator(icons.size()); it.hasPrevious();) {
		DisplayIcon disp = it.previous();
		if(disp.sc == null)
		    continue;
		Coord ic = ev.c.sub(disp.sc);
		if(disp.icon.hover(ic, hovering && disp.icon.checkhit(ic) && !filter(disp))) {
		    hovering = false;
		    ret = true;
		}
	    }
	    for(DisplayGrid dgrid : display) {
		if(dgrid == null)
		    continue;
		for(DisplayMarker mark : dgrid.markers(false)) {
		    if(mark.sc == null)
			continue;
		    try {
			GobIcon.Icon icon = mark.icon();
			Coord ic = ev.c.sub(mark.sc);
			if(icon.hover(ic, hovering && icon.checkhit(ic) && !filter(mark))) {
			    hovering = false;
			    ret = true;
			}
		    } catch(Loading l) {}
		}
	    }
	}
	return(ret);
    }

    private String lasttname = null;
    private Object lastobjid = null;
    private Tex lasttip = null;
    public Object tooltip(Coord c, Widget prev) {
        Object meteorTip=extra.meteorTooltip(c);if(meteorTip!=null)return meteorTip;
        Object pointerTip=extra.pointerTooltip(c);if(pointerTip!=null)return pointerTip;
        Object roomTip=preserveOutsideMap()&&outsideLocation!=null?null:roomPlan.tooltip(c);if(roomTip!=null)return roomTip;
        Object playerTip=playerMarkers.tooltip(c);if(playerTip!=null)return playerTip;
	DisplayGrid grid = gridat(c);
	String tname = null;
	Object objid = null;
	Supplier<BufferedImage> objtip = null;
	if(Utils.getprefb("hearthborn/map/biome-tooltip",true)) {
	try {
	    if((grid != null) && (grid.dc != null)) {
		DataGrid dgrid = grid.gref.get();
		if(dgrid != null) {
		    Coord gc = c.sub(grid.dc).div(dmag);
		    gc = Area.sized(cmaps).closest(gc); /* XXX: This should not be necessary. */
		    TileInfo tile = dgrid.tilesets[dgrid.gettile(gc)];
		    if(tile != null) {
			Resource tres = tile.res.get();
			Resource.Tooltip tt = tres.layer(Resource.tooltip);
			if(tt != null)
			    tname = hearthborn.map.TerrainHighlight.annotate(tt.t,tres.name);
		    }
		}
	    }
	} catch(Loading l) {
	    tname = "...";
	}
	}
	Location mloc = xlate(c);
	if(mloc != null) {
	    DisplayIcon icon = iconat(c);
	    DisplayMarker mark = markerat(mloc.tc);
	    if(icon != null) {
		if(icon.icon != null) {
		    objid = icon.icon;
		    objtip = () -> Text.render(hearthborn.map.TerrainHighlight.annotate(hearthborn.L10n.text("tooltip",icon.icon.name()),icon.icon.res.name)).img;
		}
	    } else if(mark != null) {
		objid = mark;
		objtip = mark::tooltip;
	    }
	}
	if((tname != null) || (objid != null)) {
	    if((tname != lasttname) || (objid != lastobjid)) {
		BufferedImage tip = ItemInfo.catimgs(0,
		    (objid == null) ? null : objtip.get(),
		    (tname == null) ? null : RichText.render(hearthborn.L10n.tr("Terrain:")+" $col[255,255,128]{" + RichText.Parser.quote(tname) + "}", 0).img);
		lasttip = new TexI(tip);
		lasttname = tname; lastobjid = objid;
	    }
	} else {
	    lasttip = null;
	}
	if(lasttip != null)
	    return(lasttip);
	return(super.tooltip(c, prev));
    }

    public void mvclick(MapView mv, Coord mc, Location loc, Gob gob, int button) {
	if(mc == null) mc = ui.mc;
	if((sessloc != null) && (sessloc.seg == loc.seg)) {
	    hearthborn.navigation.Walking.cancel(mv);
	    hearthborn.combat.BootSwitch.map(mv.getparent(GameUI.class), gob, button);
	    if(gob == null)
		mv.wdgmsg("click", mc,
			  loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2)).floor(posres),
			  button, ui.modflags());
	    else
		mv.wdgmsg("click", mc,
			  loc.tc.sub(sessloc.tc).mul(tilesz).add(tilesz.div(2)).floor(posres),
			  button, ui.modflags(), 0,
			  (int)gob.id,
			  gob.rc.floor(posres),
			  0, -1);
	}
    }
}
