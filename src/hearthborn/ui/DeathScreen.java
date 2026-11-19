/* Preprocessed source code */
package hearthborn.ui;
import hearthborn.L10n;

import haven.*;
import haven.render.*;
import haven.render.sl.*;
import java.nio.*;
import java.awt.Color;
import java.awt.image.WritableRaster;
import static haven.PUtils.*;
import static haven.render.sl.Cons.*;
import static haven.render.sl.Type.*;

/* >wdg: DeathScreen */
public class DeathScreen extends Widget {
    public static final Text.Furnace capf = new BlurFurn(new TexFurn(new Text.Foundry(Text.fraktur, 50).aa(true), Window.ctex), UI.scale(5), UI.scale(4), new Color(96, 48, 0));
    public static final Tex cap = capf.render(L10n.tr("You are dead")).tex();
    public static final Tex text = deathText();
    private static Tex deathText() {
        String language = "ru".equals(L10n.language()) ? "ru" : "en";
        try(java.io.InputStream in = hearthborn.assets.Assets.open("icons/screens/death-" + language + ".png")) {
            return new TexI(PUtils.uiscale(javax.imageio.ImageIO.read(in), UI.scale(new Coord(250, 300))));
        } catch(java.io.IOException e) {
            throw new RuntimeException("Cannot load death screen", e);
        }
    }
    public static final int mrgn = UI.scale(25);
    public static final Coord fsz = new Coord(Math.max(cap.sz().x, text.sz().x), cap.sz().y + mrgn + text.sz().y + mrgn + Button.hl);
    public static final Texture2D.Sampler2D cloud;
    public final MapView mv;
    private double tm;
    private Button done;
    private Window inheritance;
    private boolean continued;

    private void next() {
        if(inheritance != null || continued) return;
        hide();
        inheritance = InheritanceHelp.open(ui, () -> {
            if(continued) return;
            continued = true;
            inheritance = null;
            DeathScreen.this.wdgmsg("done");
        }, () -> {
            inheritance = null;
            show();
        });
    }

    static {
	final Coord sz = new Coord(256, 256);
	WritableRaster buf = PUtils.alpharaster(sz);
	SNoise3 rnd = new SNoise3();
	for(int y = 0; y < sz.y; y++) {
	    for(int x = 0; x < sz.x; x++) {
		double a =
		    (rnd.getr(0, 1, 64, x, y, 4219) * 0.5714286) +
		    (rnd.getr(0, 1, 16, x, y, 4219) * 0.2857143) +
		    (rnd.getr(0 ,1,  4, x, y, 4219) * 0.1428571);
		buf.setSample(x, y, 0, (int)(255 * Utils.clip(a, 0, 1)));
	    }
	}
	final byte[] pixels = ((java.awt.image.DataBufferByte)buf.getDataBuffer()).getData();
	Texture2D tex = new Texture2D(sz, DataBuffer.Usage.STATIC, new VectorFormat(1, NumberFormat.UNORM8),
				      (img, env) -> {
					  if(img.level != 0)
					      return(null);
					  FillBuffer fbuf = env.fillbuf(img);
					  fbuf.pull(ByteBuffer.wrap(pixels));
					  return(fbuf);
				      });
	cloud = new Texture2D.Sampler2D(tex);
    }

    public DeathScreen(GameUI gui) {
	super(fsz);
	this.mv = gui.map;
    }

    public static DeathScreen mkwidget(UI ui, Object... args) {
	GameUI gui = (GameUI)ui.getwidget(Utils.iv(args[0]));
	return(new DeathScreen(gui));
    }

    protected void attached() {
	super.attached();
	this.mv.add(proc);
    }

    public void destroy() {
        if(inheritance != null) { inheritance.destroy(); inheritance = null; }
	this.mv.remove(proc);
	proc.dispose();
	super.destroy();
    }

    protected void added() {
	presize();
	super.added();
    }

    public void presize() {
	c = parent.sz.div(2).sub(sz.div(2));
    }

    public void draw(GOut g) {
	super.draw(g);
	Coord sz = g.sz();
	g.chcolor(255, 255, 255, (int)(255 * Utils.clip(tm * 0.5, 0.0, 1.0)));
	g.aimage(cap, new Coord(sz.x / 2, 0), 0.5, 0.0);
	g.chcolor(255, 255, 255, (int)(255 * Utils.clip((tm - 5.0) * 0.5, 0.0, 1.0)));
	g.aimage(text, new Coord(sz.x / 2, cap.sz().y + mrgn), 0.5, 0.0);
	g.chcolor();
    }

    class Draw extends RUtils.AdHoc {
	final float mdeg;
	Draw(float mdeg) {super(code); this.mdeg = mdeg;}
    }
    public static final Uniform mdeg = new Uniform(FLOAT, p -> ((Draw)p.get(RUtils.adhoc)).mdeg, RUtils.adhoc);
    public static final Uniform ctex = new Uniform(SAMPLER2D, p -> cloud);
    public static final ShaderMacro code = new ShaderMacro() {
	    Function mono = new Function.Def(VEC4) {{
		Expression in = param(PDir.IN, VEC4).ref();
		code.add(new Return(vec4(mix(pick(in, "rgb"),
					     vec3(dot(pick(in, "rgb"), vec3(l(1.0 / 3.0)))),
					     mul(clamp(Cons.add(mdeg.ref(), mul(pick(texture2D(ctex.ref(), Tex2D.rtexcoord.ref()), "r"), l(2.0))),
						       l(0.0), l(1.0)),
						 l(0.8))),
					 pick(in, "a"))));
	    }};

	    public void modify(ProgramContext prog) {
		FragColor.fragcol(prog.fctx).mod(in -> mono.call(in), 10);
	    }
	};

    public final RenderContext.PostProcessor proc = new RenderContext.PostProcessor() {
	    public void run(GOut g, Texture2D.Sampler2D in) {
		g.usestate(new Draw((float)Math.min(tm - 2.0, 1.0)));
		g.image(new TexRaw(in, true), Coord.z);
	    }
	};

    public void tick(double dt) {
	tm += dt;
	if((tm > 9) && (done == null)) {
	    done = adda(new Button(UI.scale(200), "Move on", true).action(this::next),
			sz.x / 2, sz.y, 0.5, 1.0);
	}
	super.tick(dt);
    }
}
