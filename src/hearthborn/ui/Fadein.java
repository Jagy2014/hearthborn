/* Preprocessed source code */
package hearthborn.ui;
import haven.*;
import haven.render.*;
import java.awt.image.WritableRaster;

/* >wdg: Fadein */
public class Fadein extends Widget {
    public static final Coord hsz = new Coord(128, 128);
    public static final double ir = 40, or = 60;
    public static final Tex peephole;
    public final double len;

    static {
	Coord cc = hsz.div(2);
	int B = 4;
	WritableRaster buf = PUtils.imgraster(hsz);
	Coord c = new Coord();
	for(c.y = 0; c.y < hsz.y; c.y++) {
	    for(c.x = 0; c.x < hsz.x; c.x++) {
		buf.setSample(c.x, c.y, 0, 0);
		buf.setSample(c.x, c.y, 1, 0);
		buf.setSample(c.x, c.y, 2, 0);
		double d = c.dist(cc);
		if(d < ir)
		    buf.setSample(c.x, c.y, 3, 0);
		else if(d > or)
		    buf.setSample(c.x, c.y, 3, 255);
		else
		    buf.setSample(c.x, c.y, 3, Utils.clip((int)(((d - ir) / (or - ir)) * 255), 0, 255));
	    }
	}
	TexI tex = new TexI(PUtils.rasterimg(buf));;
	tex.magfilter(Texture.Filter.LINEAR);
	tex.wrapmode(Texture.Wrapping.CLAMP);
	peephole = tex;
    }

    public Fadein(double len) {
	super(Coord.z);
	this.len = len;
    }

    public static Fadein mkwidget(UI ui, Object... args) {
	double len = ((Number)args[0]).doubleValue();
	return(new Fadein(len));
    }

    private double start = 0;
    private void drawfx(GOut g) {
	GameUI gui = getparent(GameUI.class);
	MapView mv = (gui == null) ? null : gui.map;
	Coord sz = g.sz();
	Loading loading = (mv == null) ? new Loading("Loading...") : mv.terrain.loading();
	if(loading != null) {
	    g.chcolor(0, 0, 0, 255);
	    g.frect(Coord.z, sz);
	    g.chcolor();
            Tex title=LoadingCaption.texture();
	    g.aimage(title, sz.div(2), 0.5, 0.5);
	    String reason = loading.getMessage();
	    if(reason != null)
		g.atext(reason, sz.div(2).add(0, title.sz().y / 2 + 15), 0.5, 0.5);
	    return;
	}
	double now = Utils.rtime();
	if(start == 0)
	    start = now;
	double a = (now - start) / len;
	if(a > 1) {
	    ui.destroy(Fadein.this);
	    return;
	}
	double ra = (a * 0.25) + (Math.pow(a, 3) * 0.25) + (Math.pow(a, 6) * 0.5);
	double mr = Coord.z.dist(sz) / 2;
	double f = Math.max(ra * mr, 1) / ir;
	Coord ul = new Coord((int)((hsz.x - (sz.x / f)) / 2),
			     (int)((hsz.y - (sz.y / f)) / 2));
	Coord br = new Coord((int)((hsz.x + (sz.x / f)) / 2),
			     (int)((hsz.y + (sz.y / f)) / 2));
	peephole.render(g, g.ul, g.ul.add(sz), ul, br);
	g.chcolor(Utils.clipcol(0, 0, 0, 255 - (int)(255 * a)));
	g.frect(Coord.z, sz);
	g.chcolor();
    }

    public void draw(GOut g) {
	ui.drawafter(this::drawfx);
    }
}
