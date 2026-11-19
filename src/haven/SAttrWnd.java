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
import haven.resutil.Curiosity;
import static haven.CharWnd.*;
import static haven.PUtils.*;

public class SAttrWnd extends Widget {
    private Frame attributeFrame;
    private final hearthborn.ui.AttributeColumns columns = new hearthborn.ui.AttributeColumns();
    public final Collection<SAttr> attrs;
    private Coord studyc;
    private CharWnd chr;
    private int scost;

    @RName("sattr")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new SAttrWnd(ui.sess.glob));
	}
    }

    public class SAttr extends CharWnd.AttrWdg {
	public final Text rnm;
	private Text inkName;
	private int inkGeneration = -1;
	public final Tex img;
	public final Color bg;
	public int tbv, cost;
	private final IButton add, sub;
        private final hearthborn.ui.AttributeValues summary = new hearthborn.ui.AttributeValues();
	private int cbv, ccv;

	private SAttr(Glob glob, String attr, Color bg) {
	    super(Coord.of(attrw, attrf.height() + UI.scale(2)), glob, attr);
	    Resource res = Loading.waitfor(this.attr.res());
	    this.img = new TexI(convolve(res.flayer(Resource.imgc).img, new Coord(this.sz.y, this.sz.y), iconfilter));
	    this.rnm = attrf.render(res.flayer(Resource.tooltip).t);
	    this.bg = bg;
	    add = adda(new IButton("gfx/hud/buttons/add", "u", "d", "h").action(() -> adj(1)),
		       sz.x - UI.scale(5), sz.y / 2, 1, 0.5);
	    sub = adda(new IButton("gfx/hud/buttons/sub", "u", "d", "h").action(() -> adj(-1)),
		       add.c.x - UI.scale(5), sz.y / 2, 1, 0.5);
	}

	public void tick(double dt) {
	    if(attr.base != cbv) {
		tbv = 0;
		ccv = 0;
		cbv = attr.base;
	    }
	    if(attr.comp != ccv) {
		ccv = attr.comp;
		Color c = Color.WHITE;
		if(ccv > cbv) {
		    c = buff;
		} else if(ccv < cbv) {
		    c = debuff;
		}
		if(tbv > 0)
		    c = tbuff;

		updcost();
	    }
            summary.update(this, tbv, dt);
	}

	public void draw(GOut g) {
	    g.chcolor(hearthborn.ui.UiTheme.row(bg));
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    super.draw(g);
	    Coord cn = new Coord(0, sz.y / 2);
	    g.aimage(img, cn.add(5, 0), 0, 0.5);
	    g.aimage(name().tex(), cn.add(img.sz().x + UI.scale(10), 1), 0, 0.5);
            summary.draw(g, cn.y + UI.scale(1));
	}

	private Text name() {
	    if(!hearthborn.ui.UiTheme.light())
		return(rnm);
	    if((inkName == null) || (inkGeneration != hearthborn.ui.UiTheme.generation())) {
		if(inkName != null)
		    inkName.dispose();
		inkName = attrf.render(rnm.text, hearthborn.ui.UiTheme.INK);
		inkGeneration = hearthborn.ui.UiTheme.generation();
	    }
	    return(inkName);
	}

        public void resize(Coord size) {
            super.resize(size);
            if(add != null) {
                add.move(Coord.of(size.x - UI.scale(5) - add.sz.x, (size.y - add.sz.y) / 2));
                sub.move(Coord.of(add.c.x - UI.scale(5) - sub.sz.x, (size.y - sub.sz.y) / 2));
            }
        }

        public Object tooltip(Coord c, Widget previous) {
            if(c.isect(add.c, add.sz) || c.isect(sub.c, sub.sz)) return "";
            return super.tooltip(c, previous);
        }

        public void dispose() {
	    if(inkName != null)
		inkName.dispose();
	    summary.dispose();
	    super.dispose();
	}

	private void updcost() {
	    int cv = attr.base, nv = cv + tbv;
	    int cost = 100 * ((nv + (nv * nv)) - (cv + (cv * cv))) / 2;
	    scost += cost - this.cost;
	    this.cost = cost;
	}

	public void adj(int a) {
	    if(tbv + a < 0) a = -tbv;
	    tbv += a;
	    ccv = 0;
	    updcost();
            summary.update(this, tbv, 0);
	}

	public void reset() {
	    tbv = 0;
	    ccv = 0;
	    updcost();
	}

	public boolean mousewheel(MouseWheelEvent ev) {
	    adj(-ev.a);
	    return(true);
	}
    }

    public RLabel<?> explabel() {
	return(new RLabel<Integer>(() -> chr.exp, Utils::thformat, new Color(192, 192, 255)));
    }

    public RLabel<?> enclabel() {
	return(new RLabel<Integer>(() -> chr.enc, Utils::thformat, new Color(255, 255, 192)));
    }

    protected void attached() {
	this.chr = getparent(CharWnd.class);
	super.attached();
    }

    public static class StudyInfo extends hearthborn.ui.StudyReport {
        public StudyInfo(Coord size, Widget study) {super(size, study);}
    }

    private void buy() {
	ArrayList<Object> args = new ArrayList<>();
	for (SAttr attr : attrs) {
	    if (attr.tbv > 0) {
		args.add(attr.attr.nm);
		args.add(attr.attr.base + attr.tbv);
	    }
	}
	wdgmsg("sattr", args.toArray(new Object[0]));
    }

    private void reset() {
	for (SAttr attr : attrs)
	    attr.reset();
    }

    public void tick(double dt) {
        super.tick(dt);
        java.util.List<hearthborn.ui.AttributeValues> values = new ArrayList<>();
        int nameWidth = 0;
        for(SAttr row : attrs) {
            values.add(row.summary);
            nameWidth = Math.max(nameWidth, row.img.sz().x + UI.scale(10) + row.rnm.sz().x);
        }
        int delta = columns.fit(this, attrs, attributeFrame, values, nameWidth, UI.scale(55));
        studyc = studyc.add(delta, 0);
    }

    /** The study report's words use the same larger face as the settings window. */
    private static <T extends Label> T big(T label) {
	label.settingsFace();
	return(label);
    }

    public SAttrWnd(Glob glob) {
	Widget prev;
	prev = add(CharWnd.settip(hearthborn.ui.UiTheme.heading(hearthborn.L10n.text("label", "Abilities")), "gfx/hud/chr/tips/sattr"), Coord.z);
	attrs = new ArrayList<>();
	SAttr aw;
	attrs.add(aw = add(new SAttr(glob, "unarmed", every), prev.pos("bl").adds(5, 0).add(wbox.btloff())));
	attrs.add(aw = add(new SAttr(glob, "melee", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "ranged", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "explore", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "stealth", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "sewing", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "smithing", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "masonry", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "carpentry", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "cooking", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "farming", every), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "survive", other), aw.pos("bl")));
	attrs.add(aw = add(new SAttr(glob, "lore", every), aw.pos("bl")));
	Widget lframe = attributeFrame = Frame.around(this, attrs);

	prev = add(CharWnd.settip(hearthborn.ui.UiTheme.heading(hearthborn.L10n.text("label", "Study Report")), "gfx/hud/chr/tips/study"), width, 0);
	studyc = prev.pos("bl").adds(5, 0);
	Widget bframe = adda(new Frame(new Coord(attrw, UI.scale(105)), true), prev.pos("bl").adds(5, 0).x, lframe.pos("br").y, 0.0, 1.0);
	int rx = bframe.pos("iur").subs(10, 0).x;
	prev = add(big(new Label("Experience points:")), bframe.pos("iul").adds(10, 5));
	adda(big(enclabel()), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = add(big(new Label("Learning points:")), prev.pos("bl").adds(0, 2));
	adda(big(explabel()), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = add(big(new Label("Learning cost:")), prev.pos("bl").adds(0, 2));
	adda(big(new RLabel<Integer>(() -> scost, Utils::thformat, n -> (n > chr.exp) ? debuff : Color.WHITE)), new Coord(rx, prev.pos("ul").y), 1.0, 0.0);
	prev = adda(new Button(UI.scale(75), "Buy").action(this::buy), bframe.pos("ibr").subs(5, 5), 1.0, 1.0);
	adda(new Button(UI.scale(75), "Reset").action(this::reset), prev.pos("bl").subs(5, 0), 1.0, 1.0);
	pack();
    }

    public void addchild(Widget child, Object... args) {
	String place = (args[0] instanceof String) ? (((String)args[0]).intern()) : null;
	if(place == "study") {
            hearthborn.ui.StudyProgress.register(child);
	    add(child, studyc.add(wbox.btloff()));
	    Widget f = Frame.around(this, Collections.singletonList(child));
	    Widget inf = add(new StudyInfo(new Coord(attrw - child.sz.x - wbox.bisz().x - UI.scale(5), child.sz.y), child), child.pos("ur").add(wbox.bisz().x + UI.scale(5), 0));
	    Frame.around(this, Collections.singletonList(inf));
	    pack();
	} else {
	    super.addchild(child, args);
	}
    }
}
