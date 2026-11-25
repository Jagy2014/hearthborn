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

public class Speedget extends Widget {
    private final hearthborn.ui.DefaultSpeed defaultSpeed = new hearthborn.ui.DefaultSpeed();
    public void tick(double dt) { super.tick(dt); hearthborn.ui.BoatSpeed.apply(this); if(visible) defaultSpeed.tick(this); }
    public static final Tex imgs[][];
    public static final String tips[];
    public static final Coord tsz;
    private static final Coord[] cells;
    public int cur, max;
    /** Set while the selector sits inside the portrait frame: its own pixel size, split into four equal cells. */
    private Coord fitted;
    private Coord[] fittedCells;

    static {
        String[] names = {"crawl", "walk", "run", "sprint"};
        String[] vars = {"dis", "off", "on"};
        imgs = new Tex[names.length][vars.length];
        cells = new Coord[names.length];
        int w = 0;
        for(int i = 0; i < names.length; i++) {
            for(int o = 0; o < vars.length; o++)
                imgs[i][o] = Resource.loadtex("gfx/hud/meter/rmeter/" + names[i] + "-" + vars[o]);
            cells[i] = hearthborn.ui.SpeedArtwork.cellSize();
            w += cells[i].x;
        }
        tsz = new Coord(w, cells[0].y);
        tips = new String[names.length];
        for(int i = 0; i < names.length; i++)
            tips[i] = Resource.local().loadwait("gfx/hud/meter/rmeter/" + names[i] + "-on").flayer(Resource.tooltip).t;
    }

    @RName("speedget")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    int cur = Utils.iv(args[0]);
	    int max = Utils.iv(args[1]);
	    return(new Speedget(cur, max));
	}
    }

    public Speedget(int cur, int max) {
	super(hearthborn.ui.HudScale.size(tsz));
	this.cur = cur;
	this.max = max;
    }

    /** Fills {@code size} pixels exactly, with four equal cells, instead of the fixed magnified size. */
    public void fit(Coord size) {
	if(size.equals(fitted) && size.equals(sz))
	    return;
	int w = Math.max(1, size.x / 4);
	fitted = size;
	fittedCells = new Coord[] {Coord.of(w, size.y), Coord.of(w, size.y), Coord.of(w, size.y), Coord.of(Math.max(1, size.x - 3 * w), size.y)};
	resize(size);
    }

    public void unfit() {
	if(fitted == null)
	    return;
	fitted = null;
	fittedCells = null;
	resize(hearthborn.ui.HudScale.size(tsz));
    }

    public boolean fitted() {return(fitted != null);}

    public void draw(GOut g) {
	boolean fit = fitted != null;
	Coord[] cs = fit ? fittedCells : cells;
	Coord total = fit ? fitted : tsz;
        Tex[][] artwork=hearthborn.ui.SpeedArtwork.images(defaultSpeed.mounted(),cs,
            (fit ? 1 : hearthborn.ui.ActionMenuFrame.SCALE)*hearthborn.ui.HudInterfaceScale.forUI(ui));
        if(!fit)
	    g=hearthborn.ui.HudScale.graphics(g,tsz);
	if(!hearthborn.ui.HudFrames.hidden()) {
	    g.chcolor(0, 0, 0, 235);
	    g.frect(Coord.z, total);
	    g.chcolor();
	}
	int x = 0;
	for(int i = 0; i < 4; i++) {
	    Tex t;
	    if(i == cur)
		t = artwork[i][2];
	    else if(i > max)
		t = artwork[i][0];
	    else
		t = artwork[i][1];
	    g.image(t, new Coord(x, 0),cs[i]);
	    x += cs[i].x;
	}
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "cur")
	    cur = Utils.iv(args[0]);
	else if(msg == "max")
	    max = Utils.iv(args[0]);
    }

    public void set(int s) {
	wdgmsg("set", s);
    }

    public boolean mousedown(MouseDownEvent ev) {
	Coord point = fitted != null ? ev.c : hearthborn.ui.HudScale.logical(ev.c);
	Coord[] cs = fitted != null ? fittedCells : cells;
	int x = 0;
	for(int i = 0; i < 4; i++) {
	    x += cs[i].x;
	    if(point.x < x) {
		set(i);
		break;
	    }
	}
	return(true);
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	if(max >= 0)
	    set(Utils.clip(cur + ev.a, 0, max));
	return(true);
    }

    public Object tooltip(Coord c, Widget prev) {
	if((cur >= 0) && (cur < tips.length))
	    return(hearthborn.L10n.format("Selected speed: %s", tips[cur]));
	return(null);
    }

    public static final KeyBinding kb_speedup = KeyBinding.get("speed-up", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.C));
    public static final KeyBinding kb_speeddn = KeyBinding.get("speed-down", KeyMatch.forchar('R', KeyMatch.S | KeyMatch.C | KeyMatch.M, KeyMatch.S | KeyMatch.C));
    public static final KeyBinding[] kb_speeds = {
	KeyBinding.get("speed-set/0", KeyMatch.nil),
	KeyBinding.get("speed-set/1", KeyMatch.nil),
	KeyBinding.get("speed-set/2", KeyMatch.nil),
	KeyBinding.get("speed-set/3", KeyMatch.nil),
    };
    public boolean globtype(GlobKeyEvent ev) {
	if(!visible)
	    return(super.globtype(ev));
	int dir = 0;
	if(kb_speedup.key().match(ev))
	    dir = 1;
	else if(kb_speeddn.key().match(ev))
	    dir = -1;
	if(dir != 0) {
	    if(max >= 0) {
		set(Utils.clip(cur + dir, 0, max));
	    }
	    return(true);
	}
	for(int i = 0; i < kb_speeds.length; i++) {
	    if(kb_speeds[i].key().match(ev)) {
		set(i);
		return(true);
	    }
	}
	return(super.globtype(ev));
    }
}
