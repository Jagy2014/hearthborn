/*
 * This file is part of Hearthborn Client.
 *
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 *
 * Licensed under the GNU Lesser General Public License
 * version 3.0 (LGPL-3.0).
 *
 * See the COPYING file for details.
 */

package hearthborn.world;

import static haven.render.sl.Cons.*;

import haven.*;
import haven.render.*;
import haven.render.sl.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * The sky around the world: a big cube that follows the player. Either a picture on its faces (a starry lake with a far
 * forest) or the day and night drawn by the graphics card (see {@link SkyShader}).
 *
 * <p>A cross is four pictures in a row (the four horizons: left, front, right, back), with the zenith above
 * the front one and the nadir below it. The same table builds the cube and answers where a direction falls
 * on the picture, so the tests can check one against the other.
 *
 * @author Jagy2014
 */
public final class Sky implements RenderTree.Node {
    /** Half the side of the cube. It follows the eye, so its far corners (SIZE times 1.73) stay inside the far plane whatever the camera does. */
    public static final float SIZE = 3000f;
    /** The side of one picture of the cross, in pixels, as far as the model cares (it keeps clear of the edge by a pixel and a half). */
    public static final int TILE = 362;

    /** One face of the cube: where it sits in the cross and which corners of the cube it covers. */
    public static final class Face {
	public final String name;
	public final int col, row;
	/** Top-left, top-right, bottom-right and bottom-left corner of the picture, as directions. */
	public final float[][] corner;
	Face(String name, int col, int row, float[]... corner) {
	    this.name = name; this.col = col; this.row = row; this.corner = corner;
	}
    }

    /*
     * Looking from inside with the front picture ahead (+x) and up as +z, the right-hand side is -y.
     * The pictures follow each other clockwise seen from above: front, right, back, left.
     */
    public static final Face[] FACES = {
	new Face("front",  2, 1, new float[] {1, 1, 1},   new float[] {1, -1, 1},  new float[] {1, -1, -1},  new float[] {1, 1, -1}),
	new Face("right",  3, 1, new float[] {1, -1, 1},  new float[] {-1, -1, 1}, new float[] {-1, -1, -1}, new float[] {1, -1, -1}),
	new Face("back",   0, 1, new float[] {-1, -1, 1}, new float[] {-1, 1, 1},  new float[] {-1, 1, -1},  new float[] {-1, -1, -1}),
	new Face("left",   1, 1, new float[] {-1, 1, 1},  new float[] {1, 1, 1},   new float[] {1, 1, -1},   new float[] {-1, 1, -1}),
	new Face("zenith", 2, 0, new float[] {-1, 1, 1},  new float[] {-1, -1, 1}, new float[] {1, -1, 1},   new float[] {1, 1, 1}),
	new Face("nadir",  2, 2, new float[] {1, 1, -1},  new float[] {1, -1, -1}, new float[] {-1, -1, -1}, new float[] {-1, 1, -1}),
    };

    /** The face a direction looks at: the axis it points along most. */
    public static Face face(double x, double y, double z) {
	double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
	if(az >= ax && az >= ay) return(z > 0 ? FACES[4] : FACES[5]);
	if(ax >= ay) return(x > 0 ? FACES[0] : FACES[2]);
	return(y > 0 ? FACES[3] : FACES[1]);
    }

    /** Where a direction (before the turn to the light) falls on a cross: fractions of the whole picture. */
    public static double[] atlas(double x, double y, double z, int cols, int rows) {
	Face f = face(x, y, z);
	double m = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
	double[] p = {x / m, y / m, z / m};
	double[] tl = {f.corner[0][0], f.corner[0][1], f.corner[0][2]};
	double[] sx = {f.corner[1][0] - tl[0], f.corner[1][1] - tl[1], f.corner[1][2] - tl[2]};
	double[] sy = {f.corner[3][0] - tl[0], f.corner[3][1] - tl[1], f.corner[3][2] - tl[2]};
	double s = 0, t = 0, ls = 0, lt = 0;
	for(int i = 0; i < 3; i++) {
	    s += (p[i] - tl[i]) * sx[i]; ls += sx[i] * sx[i];
	    t += (p[i] - tl[i]) * sy[i]; lt += sy[i] * sy[i];
	}
	s /= ls; t /= lt;
	return(new double[] {(f.col + s) / cols, (f.row + t) / rows});
    }

    /** Pixels of a picture's edge that are pulled towards what the neighbouring picture shows there. */
    public static int blendWidth(int tile) {return(tile / 5);}
    /** Half the length, in pixels, over which an edge's colours are averaged before the two sides are matched. */
    public static final int SMOOTH = 12;
    private static float[][] smooth(float[][] line, int radius) {
	int n = line.length;
	float[][] out = new float[n][3];
	for(int k = 0; k < n; k++) {
	    int from = Math.max(0, k - radius), to = Math.min(n - 1, k + radius);
	    for(int j = from; j <= to; j++)
		for(int c = 0; c < 3; c++)
		    out[k][c] += line[j][c];
	    for(int c = 0; c < 3; c++)
		out[k][c] /= (to - from + 1);
	}
	return(out);
    }
    /** Rows on the border of every picture that are not trusted: they are painted over from the row inside. */
    public static final int BORDER = 2;

    private static boolean same(float[] a, float[] b) {
	return(a[0] == b[0] && a[1] == b[1] && a[2] == b[2]);
    }

    /** The pixel of a picture's edge: along it (k) and inwards (d), the edge number counted from the top clockwise. */
    public static int[] edgePixel(int edge, int k, int d, int s) {
	switch(edge) {
	case 0: return(new int[] {k, d});
	case 1: return(new int[] {s - 1 - d, k});
	case 2: return(new int[] {s - 1 - k, s - 1 - d});
	default: return(new int[] {d, s - 1 - k});
	}
    }

    /** The pairs of picture edges that meet on the cube: {face, edge, face, edge, reversed}. */
    public static java.util.List<int[]> seams() {
	java.util.List<int[]> out = new java.util.ArrayList<>();
	for(int a = 0; a < FACES.length; a++) {
	    for(int b = a + 1; b < FACES.length; b++) {
		for(int ea = 0; ea < 4; ea++) {
		    for(int eb = 0; eb < 4; eb++) {
			float[] a0 = FACES[a].corner[ea], a1 = FACES[a].corner[(ea + 1) % 4];
			float[] b0 = FACES[b].corner[eb], b1 = FACES[b].corner[(eb + 1) % 4];
			if(same(a0, b0) && same(a1, b1))
			    out.add(new int[] {a, ea, b, eb, 0});
			else if(same(a0, b1) && same(a1, b0))
			    out.add(new int[] {a, ea, b, eb, 1});
		    }
		}
	    }
	}
	return(out);
    }

    /**
     * A painted cross is never quite a cube: where two pictures meet, the colours differ and the edge shows. This pulls
     * both sides of every edge to their average on the edge itself and lets the change die away inwards, so that the
     * pictures run into each other. The middle of every picture stays as painted.
     */
    public static BufferedImage seamless(BufferedImage img, int cols, int rows) {
	int w = img.getWidth(), h = img.getHeight(), s = w / cols;
	int width = blendWidth(s);
	float[] px = new float[w * h * 3];
	int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
	for(int i = 0; i < argb.length; i++) {
	    px[i * 3] = (argb[i] >> 16) & 255; px[i * 3 + 1] = (argb[i] >> 8) & 255; px[i * 3 + 2] = argb[i] & 255;
	}
	/* Painted pictures often have a darker or lighter line right on their border: take the two outermost rows from the row inside. */
	for(Face f : FACES) {
	    for(int edge = 0; edge < 4; edge++) {
		for(int k = 0; k < s; k++) {
		    int[] in = edgePixel(edge, k, BORDER, s);
		    int from = ((f.row * s + in[1]) * w + f.col * s + in[0]) * 3;
		    for(int d = 0; d < BORDER; d++) {
			int[] at = edgePixel(edge, k, d, s);
			int to = ((f.row * s + at[1]) * w + f.col * s + at[0]) * 3;
			px[to] = px[from]; px[to + 1] = px[from + 1]; px[to + 2] = px[from + 2];
		    }
		}
	    }
	}
	float[] da = new float[3], db = new float[3];
	for(int pass = 0; pass < 2; pass++) {
	    for(int[] seam : seams()) {
		Face fa = FACES[seam[0]], fb = FACES[seam[2]];
		/* What each side looks like along the edge, smoothed: a star or a dark patch right on the border must not become a streak running inwards. */
		float[][] la = new float[s][3], lb = new float[s][3];
		for(int k = 0; k < s; k++) {
		    int kb = (seam[4] == 1) ? s - 1 - k : k;
		    int[] ea = edgePixel(seam[1], k, 0, s), eb = edgePixel(seam[3], kb, 0, s);
		    int ia = ((fa.row * s + ea[1]) * w + fa.col * s + ea[0]) * 3, ib = ((fb.row * s + eb[1]) * w + fb.col * s + eb[0]) * 3;
		    for(int c = 0; c < 3; c++) {
			la[k][c] = px[ia + c]; lb[k][c] = px[ib + c];
		    }
		}
		la = smooth(la, SMOOTH); lb = smooth(lb, SMOOTH);
		for(int k = 0; k < s; k++) {
		    int kb = (seam[4] == 1) ? s - 1 - k : k;
		    for(int c = 0; c < 3; c++) {
			float mid = (la[k][c] + lb[k][c]) / 2;
			da[c] = mid - la[k][c]; db[c] = mid - lb[k][c];
		    }
		    for(int d = 0; d < width; d++) {
			float weight = (1f - (float)d / width);
			weight *= weight;
			int[] qa = edgePixel(seam[1], k, d, s), qb = edgePixel(seam[3], kb, d, s);
			int ta = ((fa.row * s + qa[1]) * w + fa.col * s + qa[0]) * 3, tb = ((fb.row * s + qb[1]) * w + fb.col * s + qb[0]) * 3;
			for(int c = 0; c < 3; c++) {
			    px[ta + c] = Math.max(0, Math.min(255, px[ta + c] + da[c] * weight));
			    px[tb + c] = Math.max(0, Math.min(255, px[tb + c] + db[c] * weight));
			}
		    }
		}
	    }
	}
	for(int i = 0; i < argb.length; i++)
	    argb[i] = (Math.round(px[i * 3]) << 16) | (Math.round(px[i * 3 + 1]) << 8) | Math.round(px[i * 3 + 2]);
	BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
	out.setRGB(0, 0, w, h, argb, 0, w);
	return(out);
    }

    /** The matrix that moves the unit cube to its place around the player. */
    public static Matrix4f place(Coord3f at) {
	Matrix4f scale = Transform.makescale(new Matrix4f(), SIZE);
	return(Transform.makexlate(new Matrix4f(), at).mul(scale));
    }

    /**
     * How high, in half-sides of the cube, the lake picture's far shore (where the hills meet the water) stands above the cube's middle:
     * it is well below it, so the cube is lifted until that shore is level with the ground, and the trees begin where the land ends.
     */
    public static final float LAKE_HORIZON = -0.56f;

    /** Where the cube's middle is: on the eye, or, for the lake, lifted so that the picture's shore is at the height of the ground. */
    public static Coord3f center(String kind, Coord3f eye, Coord3f ground) {
	if(kind.equals("lake"))
	    return(Coord3f.of(eye.x, eye.y, ground.z - LAKE_HORIZON * SIZE));
	return(eye);
    }

    /** What the picture sky shows: its picture. */
    public static final class Still extends State {
	public static final Slot<Still> slot = new Slot<Still>(Slot.Type.DRAW, Still.class);
	public final Texture2D.Sampler2D picture;
	public Still(Texture2D.Sampler2D picture) {this.picture = picture;}
	static final Uniform spicture = new Uniform(Type.SAMPLER2D, "sky_still", p -> p.get(slot).picture, slot);
	private static final ShaderMacro shader = prog -> {
	    final ValBlock.Value tc = Tex2D.get(prog).texcoord();
	    tc.force();
	    /* Below the shore the picture is water seen from above, and it is busy: seen from far above, with the camera looking down, it fills the screen around the map. It is calmed into the water's own colour, deeper the lower it is. */
	    Expression below = smoothstep(l(LAKE_HORIZON - 0.04f), l(LAKE_HORIZON - 0.30f), pick(Homo3D.fragobjv.ref(), "z"));
	    Expression deep = mul(vec3(l(0.012f), l(0.19f), l(0.37f)), sub(l(1.0f), mul(l(0.4f), below)));
	    FragColor.fragcol(prog.fctx).mod(in -> vec4(mix(pick(texture2D(spicture.ref(), tc.ref()), "rgb"), deep, mul(l(0.92f), below)), l(1.0)), 0);
	};
	public ShaderMacro shader() {return(shader);}
	public static ShaderMacro macro() {return(shader);}
	public void apply(Pipe buf) {buf.put(slot, this);}
	public boolean equals(Object o) {return((o instanceof Still) && (((Still)o).picture == picture));}
	public int hashCode() {return(System.identityHashCode(picture));}
    }

    /** What the drawn sky shows: where the sun and the moon stand, the moon's phase (an angle) and the time, in seconds. */
    public static final class Procedural extends State {
	public static final Slot<Procedural> slot = new Slot<Procedural>(Slot.Type.DRAW, Procedural.class);
	public final Coord3f sun, moon;
	public final float phase, time, height;
	public Procedural(Coord3f sun, Coord3f moon, float phase, float time, float height) {
	    this.sun = sun; this.moon = moon; this.phase = phase; this.time = time; this.height = height;
	}
	static final Uniform usun = new Uniform(Type.VEC3, "sky_sun", p -> p.get(slot).sun, slot);
	static final Uniform umoon = new Uniform(Type.VEC3, "sky_moon", p -> p.get(slot).moon, slot);
	static final Uniform uphase = new Uniform(Type.FLOAT, "sky_phase", p -> p.get(slot).phase, slot);
	static final Uniform utime = new Uniform(Type.FLOAT, "sky_time", p -> p.get(slot).time, slot);
	static final Uniform uheight = new Uniform(Type.FLOAT, "sky_height", p -> p.get(slot).height, slot);
	private static final ShaderMacro shader = prog -> {
	    final Function.Def fn = SkyShader.function();
	    FragColor.fragcol(prog.fctx).mod(in -> fn.call(Homo3D.fragobjv.ref(), usun.ref(), umoon.ref(), uphase.ref(), utime.ref(), uheight.ref()), 0);
	};
	public ShaderMacro shader() {return(shader);}
	public static ShaderMacro macro() {return(shader);}
	public void apply(Pipe buf) {buf.put(slot, this);}
	public boolean equals(Object o) {
	    if(!(o instanceof Procedural))
		return(false);
	    Procedural that = (Procedural)o;
	    return(that.sun.equals(sun) && that.moon.equals(moon) && (that.phase == phase) && (that.time == time) && (that.height == height));
	}
	public int hashCode() {return(sun.hashCode() * 31 + Float.floatToIntBits(time));}
    }

    /** How far through its climb the sun is: -1 at midnight, 0 at dawn and dusk (a quarter and three quarters of the day), 1 at noon. */
    public static double sunPhase(double dayTime) {
	return(Math.sin(2 * Math.PI * (dayTime - 0.25)));
    }

    /** The height of the sun above the horizon, in radians, as the light has it: highest at noon. */
    public static final double NOON_HEIGHT = 1.2;
    public static double sunHeight(double dayTime) {
	return(NOON_HEIGHT * sunPhase(dayTime));
    }

    /**
     * How high the sun and the moon are drawn at their highest, in radians. The camera sees only a narrow strip of sky
     * (the perspective view is a long way back, so a few degrees fill the screen), and the real noon sun stands far above
     * it. The sun is therefore drawn in the strip that is on the screen: it climbs from the horizon to near the top edge.
     * {@code window} is {bottom, top} of what the screen looks at, as heights above the horizon.
     */
    public static float reach(float[] window) {
	float top = window[1], span = window[1] - window[0];
	return(Math.max(0.05f, Math.min((float)NOON_HEIGHT, top - 0.2f * span)));
    }

    /**
     * Where the sun is drawn, as a direction from the player. The game's light is the sun by day and the moon by night, and its
     * angle is where that body stands; the sun is across the sky from the moon, so at night the sun is on the other side.
     * Its height is the sun's climb scaled to {@code reach}.
     */
    public static Coord3f sunDirection(double dayTime, boolean night, double lightAngle, float reach) {
	double height = reach * sunPhase(dayTime), az = night ? lightAngle + Math.PI : lightAngle;
	return(Coord3f.o.sadd((float)height, (float)az, 1f));
    }
    public static Coord3f sunDirection(double dayTime, boolean night, double lightAngle) {
	return(sunDirection(dayTime, night, lightAngle, (float)NOON_HEIGHT));
    }

    public static BufferedImage read(String name) {
	try {
	    return(hearthborn.assets.Assets.image("icons/sky/" + name));
	} catch(java.io.IOException e) {
	    throw(new RuntimeException(e));
	}
    }

    private static Texture2D.Sampler2D sampler(BufferedImage img) {
	TexI tex = new TexI(img, false);
	Texture2D.Sampler2D ret = tex.st().data;
	ret.wrapmode(Texture.Wrapping.CLAMP);
	return(ret);
    }

    /** The cube, with its pictures: positions in the unit cube and where each corner falls on the cross. */
    public static Model model(int cols, int rows, int tile) {
	float inset = 1.0f / tile;
	FloatBuffer pos = Utils.wfbuf(FACES.length * 4 * 3), tex = Utils.wfbuf(FACES.length * 4 * 2);
	int[][] at = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
	for(Face f : FACES) {
	    for(int i = 0; i < 4; i++) {
		pos.put(f.corner[i][0]).put(f.corner[i][1]).put(f.corner[i][2]);
		float s = inset + at[i][0] * (1 - 2 * inset), t = inset + at[i][1] * (1 - 2 * inset);
		tex.put((f.col + s) / cols).put((f.row + t) / rows);
	    }
	}
	pos.rewind(); tex.rewind();
	VertexBuf buf = new VertexBuf(new VertexBuf.VertexData(pos), new VertexBuf.TexelData(tex));
	return(new Model(Model.Mode.TRIANGLES, buf.data(),
			 new Model.Indices(FACES.length * 6, NumberFormat.UINT16, DataBuffer.Usage.STATIC, (ind, env) -> {
			     FillBuffer fb = env.fillbuf(ind);
			     ShortBuffer sb = fb.push().asShortBuffer();
			     for(int f = 0; f < FACES.length; f++) {
				 int b = f * 4;
				 sb.put((short)b).put((short)(b + 1)).put((short)(b + 2));
				 sb.put((short)b).put((short)(b + 2)).put((short)(b + 3));
			     }
			     return(fb);
			 })));
    }

    private final MapView view;
    private final Glob glob;
    private Model model;
    private final java.util.Map<String, Texture2D.Sampler2D> stills = new java.util.HashMap<>();
    private String shownKind;
    private RenderTree.Slot slot;
    private Coord3f placed;
    private boolean failed;
    private Coord3f shownSun;
    private float shownPhase = Float.NaN, shownTime = Float.NaN, shownHeight = Float.NaN, reach = Float.NaN;
    private final double born = Utils.rtime();

    public Sky(MapView view, Glob glob) {
	this.view = view;
	this.glob = glob;
    }

    /** What the sky shows: "shader" (day and night drawn by the graphics card), "lake" (a starry lake with a far forest, always) or "off". */
    public static final String PREF = "hearthborn/camera/sky", KIND = "hearthborn/camera/sky-kind";
    public static final java.util.List<String> KINDS = java.util.Arrays.asList("shader", "lake", "off");
    /** The skies that are one picture and stand still, and the files they are. */
    public static final java.util.Map<String, String> STILLS = new java.util.LinkedHashMap<String, String>() {{
	put("lake", "sky-lake.png");
    }};
    public static String kind() {
	String kind = Utils.getpref(KIND, null);
	if(kind == null)
	    return(Utils.getprefb(PREF, true) ? "shader" : "off");
	/* The painted day, night and galaxy skies are gone: whoever had them gets the drawn one. */
	return(KINDS.contains(kind) ? kind : "shader");
    }
    public static void kind(String kind) {
	if(!KINDS.contains(kind))
	    throw(new IllegalArgumentException("Unknown sky: " + kind));
	Utils.setpref(KIND, kind);
    }
    public static boolean enabled() {return(!kind().equals("off"));}

    /** Cameras that can look up at the sky: every one that is not orthographic. */
    public static boolean wanted(MapView view) {
	return(enabled() && view.camera != null && !view.camera.getClass().getName().contains("OrthoCam"));
    }

    /** Whether the player is somewhere the sky does not belong: in a house, a cellar or a mine. */
    public static boolean covered(MapView view, Glob glob) {
	try {
	    GameUI gui = view.getparent(GameUI.class);
	    if((gui != null) && gui.indoors())
		return(true);
	    Coord3f cc = view.getcc();
	    Coord tile = new Coord2d(cc.x, cc.y).floor(MCache.tilesz);
	    Resource res = glob.map.tilesetr(glob.map.gettile(tile));
	    return(res.name.contains("/cave") || res.name.contains("cellar"));
	} catch(RuntimeException e) {
	    return(false);
	}
    }

    private String noted;
    /**
     * logs/sky.log gets a line each time the sky shows or hides, but only in developer mode (debug-logs), like the other
     * diagnostic logs. A failure of the sky is always written, with its stack.
     */
    private void note(String state, Throwable error) {
	if(error == null && !hearthborn.io.DeveloperMode.enabled("debug-logs"))
	    return;
	if(state.equals(noted))
	    return;
	noted = state;
	try {
	    StringBuilder line = new StringBuilder(new java.util.Date() + " " + state + "\n");
	    if(error != null) {
		java.io.StringWriter text = new java.io.StringWriter();
		error.printStackTrace(new java.io.PrintWriter(text));
		line.append(text);
	    }
	    hearthborn.io.Logs.append("sky.log", line.toString());
	} catch(java.io.IOException | RuntimeException ignored) {
	}
    }

    public void tick(double dt) {
	if(failed)
	    return;
	try {
	    boolean wanted = wanted(view), covered = wanted && covered(view, glob);
	    boolean show = wanted && !covered;
	    note(show ? "shown: " + kind() + ", camera " + view.camera.getClass().getSimpleName()
		 : "hidden: " + (!wanted ? "sky " + kind() + ", camera " + (view.camera == null ? "none" : view.camera.getClass().getSimpleName()) : "covered (indoors, cave or cellar)"), null);
	    Astronomy ast = glob.ast;
	    if(!show) {
		if(slot != null) {slot.remove(); slot = null; placed = null;}
		return;
	    }
	    String kind = kind();
	    boolean drawn = kind.equals("shader");
	    if(slot == null) {
		if(model == null)
		    model = model(4, 3, TILE);
		slot = view.basic.add(this);
		placed = null; shownKind = null; shownSun = null;
	    }
	    /* The cube sits on the eye of the camera (the sky is at no distance, however far the camera is pulled back); the lake's is lifted to the ground. */
	    Coord3f cc = center(kind, view.camera.eye(), view.getcc());
	    Coord3f sun = null;
	    float phase = 0f, time = 0f, height = 0f;
	    if(drawn) {
		double dayTime = (ast == null) ? 0.5 : ast.dt;
		float want = reach(view.camera.skyWindow());
		reach = Float.isNaN(reach) ? want : reach + (want - reach) * (float)Math.min(1, dt * 8);
		sun = sunDirection(dayTime, (ast != null) && ast.night, glob.lightang, reach);
		height = (float)Math.sin(sunHeight(dayTime));
		phase = (float)(2 * Math.PI * ((ast == null) ? 0.5 : ast.mp));
		time = (float)(Utils.rtime() - born);
	    }
	    boolean moved = (placed == null) || (placed.dist(cc) > 0.5f) || !kind.equals(shownKind);
	    if(drawn && !moved)
		moved = (shownSun == null) || (shownSun.dist(sun) > 0.0005f) || (phase != shownPhase) || (height != shownHeight) || (Math.abs(time - shownTime) > 0.1f);
	    if(moved) {
		Pipe.Op look;
		if(drawn) {
		    look = new Procedural(sun, sun.neg(), phase, time, height);
		    shownSun = sun; shownPhase = phase; shownTime = time; shownHeight = height;
		} else {
		    Texture2D.Sampler2D picture = stills.get(kind);
		    if(picture == null) stills.put(kind, picture = sampler(seamless(read(STILLS.get(kind)), 4, 3)));
		    look = new Still(picture);
		}
		slot.ostate(Pipe.Op.compose(new Location(place(cc)), look));
		placed = cc; shownKind = kind;
	    }
	} catch(Loading e) {
	} catch(RuntimeException | Error e) {
	    failed = true;
	    note("failed", e);
	    new Warning(e, "the sky could not be drawn").issue();
	}
    }

    /**
     * Drawn before the world, and the world's depth still hides it: the sky writes no depth but is tested against the
     * depth of what is already there, so it can not come out over the ground whichever way the draws are ordered. Both
     * sides of every face show (the world's pipe culls back faces, and we see the cube from the inside).
     * The order is a plain early number: {@link Rendered#first} is Integer.MIN_VALUE, and the order comparison subtracts.
     */
    public static final Rendered.Order EARLY = new Rendered.Order.Default(-100000);
    public static Pipe.Op states() {
	return(Pipe.Op.compose(EARLY, States.maskdepth, new States.Depthtest(States.Depthtest.Test.LE), new States.Facecull(States.Facecull.Mode.NONE),
			       p -> p.put(Clickable.slot, null)));
    }

    public void added(RenderTree.Slot slot) {
	slot.add(model, states());
    }
}
