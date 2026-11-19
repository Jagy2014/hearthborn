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

/**
 * The sky drawn by the graphics card, with no pictures: the colour of the air by the sun's height, the glow of
 * dawn and dusk, the sun with its halo, clouds that drift, stars and a faint Milky Way, and the moon with its
 * phase. Every pixel is worked out from the direction it looks in, so the cube's seams and corners do not show.
 *
 * <p>The function takes the direction (in the cube, which is not turned), the directions of the sun and the moon,
 * the moon's phase (an angle, 0 at the new moon, pi at the full one) and the time in seconds, and returns a colour.
 *
 * @author Jagy2014
 */
public final class SkyShader {
    private SkyShader() {}

    /** The body of the function, GLSL: $0 is the direction, $1 the sun, $2 the moon, $3 the phase, $4 the time, $5 the sun's real height (its sine; the sun is drawn at another height, see Sky.reach). */
    public static final String BODY = String.join("\n",
	"vec3 d = normalize($0);",
	"vec3 sn = normalize($1);",
	"vec3 mn = normalize($2);",
	"float h = $5;",
	"float z = d.z;",
	"float day = smoothstep(-0.16, 0.10, h);",
	"float dusk = exp(-pow(h / 0.17, 2.0));",
	"float up = clamp(z, 0.0, 1.0);",
	"float below = clamp(-z * 4.0, 0.0, 1.0);",
	"float nightAmt = 1.0 - day;",
	"",
	"vec3 dayCol = mix(vec3(0.74, 0.87, 1.00), vec3(0.09, 0.32, 0.80), pow(up, 0.5));",
	"dayCol = mix(dayCol, vec3(0.50, 0.66, 0.88), below);",
	"vec3 nightCol = mix(vec3(0.050, 0.075, 0.170), vec3(0.004, 0.010, 0.040), pow(up, 0.45));",
	"nightCol = mix(nightCol, vec3(0.020, 0.030, 0.070), below);",
	"vec3 col = mix(nightCol, dayCol, day);",
	"",
	"vec2 dh = normalize(d.xy + vec2(0.00001, 0.0));",
	"vec2 sh = normalize(sn.xy + vec2(0.00001, 0.0));",
	"float towards = max(dot(dh, sh), 0.0);",
	"float horizon = exp(-abs(z) * 5.5);",
	"float glow = dusk * horizon * (0.25 + 0.75 * towards * towards * towards);",
	"col = mix(col, mix(vec3(1.0, 0.42, 0.16), vec3(1.0, 0.72, 0.42), towards), clamp(glow * 1.2, 0.0, 0.9));",
	"",
	"float cs = dot(d, sn);",
	"float sunVis = smoothstep(-0.05, 0.04, h);",
	"float disc = smoothstep(0.99910, 0.99940, cs);",
	"float halo = pow(max(cs, 0.0), 500.0) * 0.55 + pow(max(cs, 0.0), 70.0) * 0.14 + pow(max(cs, 0.0), 12.0) * 0.06;",
	"col += vec3(1.0, 0.90, 0.70) * halo * sunVis * (1.0 - 0.3 * dusk);",
	"col = mix(col, vec3(1.0, 0.98, 0.90), disc * sunVis);",
	"",
	"float cv = smoothstep(0.02, 0.25, z);",
	"vec3 q = vec3(d.xy / (z + 0.30) * 1.4 + vec2($4 * 0.012, $4 * 0.004), $4 * 0.01);",
	"float dens = 0.0;",
	"float amp = 0.55;",
	"for(int i = 0; i < 5; i++) {",
	"    dens += amp * (0.5 + 0.5 * sin(q.x + 1.3 * sin(q.y * 1.4 + q.z)) * sin(q.y * 1.1 + 1.5 * sin(q.z * 1.2 + q.x * 0.7)));",
	"    q = q * 2.07 + vec3(3.1, 7.7, 1.9);",
	"    amp *= 0.5;",
	"}",
	"float cloud = smoothstep(0.60, 0.86, dens) * cv;",
	"vec3 cloudCol = mix(vec3(0.58, 0.64, 0.76), vec3(1.0), clamp(0.4 + 0.6 * dens, 0.0, 1.0));",
	"cloudCol = mix(cloudCol, vec3(1.0, 0.62, 0.38), dusk * (0.35 + 0.5 * towards));",
	"col = mix(col, cloudCol * (day * 0.95 + 0.05), cloud * 0.85);",
	"",
	"float fade = smoothstep(-0.05, 0.25, z);",
	"if(nightAmt > 0.01 && z > -0.06) {",
	"    float stars = 0.0;",
	"    for(int i = 0; i < 2; i++) {",
	"        float sc = (i == 0) ? 70.0 : 150.0;",
	"        vec3 sp = d * sc;",
	"        vec3 cell = floor(sp);",
	"        float rnd = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);",
	"        vec3 jit = vec3(fract(rnd * 17.13), fract(rnd * 71.77), fract(rnd * 53.31)) - 0.5;",
	"        float dist = length(fract(sp) - 0.5 - jit * 0.55);",
	"        float thr = (i == 0) ? 0.975 : 0.988;",
	"        stars += step(thr, rnd) * smoothstep(0.38, 0.03, dist) * (0.55 + 0.75 * fract(rnd * 311.7));",
	"    }",
	"    float band = exp(-pow(dot(d, normalize(vec3(0.35, -0.5, 0.8))) * 4.0, 2.0));",
	"    col += vec3(0.85, 0.92, 1.0) * stars * nightAmt * fade * (1.0 - 0.8 * cloud);",
	"    col += vec3(0.07, 0.09, 0.16) * band * (0.6 + 0.4 * sin(d.x * 23.0 + sin(d.y * 17.0) * sin(d.z * 13.0))) * nightAmt * fade;",
	"}",
	"",
	"vec3 mr = normalize(cross(mn, vec3(0.0, 0.0, 1.0)) + vec3(0.00001, 0.0, 0.0));",
	"vec3 mu = cross(mr, mn);",
	"float cm = dot(d, mn);",
	"float moonVis = smoothstep(-0.05, 0.04, -h);",
	"float ill = 0.5 - 0.5 * cos($3);",
	"col += vec3(0.55, 0.62, 0.85) * (pow(max(cm, 0.0), 900.0) * 0.55 + pow(max(cm, 0.0), 60.0) * 0.10) * moonVis * (0.15 + 0.85 * ill) * nightAmt;",
	"if(cm > 0.9) {",
	"    vec2 mp = vec2(dot(d, mr), dot(d, mu)) / 0.04;",
	"    float rr = dot(mp, mp);",
	"    if(rr < 1.0) {",
	"        vec3 nrm = vec3(mp, sqrt(1.0 - rr));",
	"        vec3 lt = vec3(sin($3), 0.0, -cos($3));",
	"        float lit = smoothstep(-0.04, 0.10, dot(nrm, lt));",
	"        float surf = 0.90 - 0.16 * smoothstep(0.55, 0.15, length(nrm.xy - vec2(-0.25, 0.2))) - 0.12 * smoothstep(0.45, 0.1, length(nrm.xy - vec2(0.3, -0.25))) - 0.10 * smoothstep(0.16, 0.05, length(nrm.xy - vec2(0.15, 0.5))) - 0.08 * smoothstep(0.12, 0.03, length(nrm.xy - vec2(-0.5, -0.3)));",
	"        vec3 moonCol = vec3(0.95, 0.94, 0.86) * surf * lit + vec3(0.035, 0.045, 0.08) * (1.0 - lit);",
	"        col = mix(col, moonCol, smoothstep(1.0, 0.93, sqrt(rr)) * moonVis);",
	"    }",
	"}",
	"return vec4(clamp(col, 0.0, 1.0), 1.0);");

    /** A piece of text for a function's body, with $0, $1... standing for the function's parameters. */
    private static final class Raw extends Statement {
	private final String text;
	private final Function.Parameter[] pars;
	Raw(String text, Function.Parameter... pars) {this.text = text; this.pars = pars;}
	public void walk(Walker w) {}
	public void output(Output out) {
	    int i = 0;
	    while(i < text.length()) {
		char c = text.charAt(i);
		if((c == '$') && (i + 1 < text.length()) && Character.isDigit(text.charAt(i + 1))) {
		    out.write(pars[text.charAt(i + 1) - '0'].name);
		    i += 2;
		} else {
		    out.write(c);
		    i++;
		}
	    }
	}
    }

    /** The function: vec4 sky(vec3 direction, vec3 sun, vec3 moon, float phase, float time, float height). */
    public static Function.Def function() {
	Function.Def fn = new Function.Def(Type.VEC4, "sky") {{
	    Function.Parameter[] p = {param(Function.PDir.IN, Type.VEC3), param(Function.PDir.IN, Type.VEC3), param(Function.PDir.IN, Type.VEC3),
				      param(Function.PDir.IN, Type.FLOAT), param(Function.PDir.IN, Type.FLOAT), param(Function.PDir.IN, Type.FLOAT)};
	    code.add(new Raw(BODY, p));
	}};
	return(fn);
    }
}
