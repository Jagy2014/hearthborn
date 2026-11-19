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

public class CheckBox extends ACheckBox {
    public static final Tex lbox = blackened("gfx/hud/chkbox");
    public static final Tex lmark = Resource.loadtex("gfx/hud/chkmark");
    public static final Tex sbox = blackened("gfx/hud/chkboxs");
    public static final Tex smark = Resource.loadtex("gfx/hud/chkmarks");
    /** The native box has an olive-green face; it is drawn black, keeping the gold frame and the shading. */
    public static Tex blackened(String name) {
	java.awt.image.BufferedImage src = Resource.loadsimg(name);
	java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
	for(int y = 0; y < src.getHeight(); y++) {
	    for(int x = 0; x < src.getWidth(); x++) {
		int argb = src.getRGB(x, y), r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		if(g > r && g > b + 8) {
		    int dark = (int)Math.round((r + g + b) / 3.0 * 0.12);
		    argb = (argb & 0xff000000) | (dark << 16) | (dark << 8) | dark;
		}
		out.setRGB(x, y, argb);
	    }
	}
	return(new TexI(out));
    }

    public final Tex box, mark;
    public final Coord loff;
    public java.awt.Color background;
    private static final Text.Foundry SETTINGS = new Text.Foundry(Text.sans, 12).aa(true);
    private boolean settings;
    Text lbl;
    public final String originalLabel;
    /** Text put in brackets at the end of the caption, read again each frame (for example the key a command is on). */
    private java.util.function.Supplier<String> suffix;
    private String suffixShown;

    public CheckBox suffix(java.util.function.Supplier<String> value) {
	suffix = value;
	suffixShown = null;
	return(this);
    }

    private String labelText() {
	String text = hearthborn.L10n.text("label", originalLabel);
	if(suffix == null)
	    return(text);
	suffixShown = suffix.get();
	return(text + " (" + suffixShown + ")");
    }

    /** The caption as it stands, with the bracketed ending when there is one; the ending is read again before each frame. */
    public String shownLabel() {
	if(suffix != null && !suffix.get().equals(suffixShown))
	    relabel();
	return(lbl == null ? null : lbl.text);
    }

    private void relabel() {
	if(lbl == null)
	    return;
	lbl.dispose();
	if(ink != null) {
	    ink.dispose();
	    ink = null;
	}
	lbl = settings ? SETTINGS.render(labelText(), hearthborn.ui.UiPalette.tier(hearthborn.ui.UiPalette.CAPTION))
	    : Text.std.render(labelText(), java.awt.Color.WHITE);
	sz = Coord.of(box.sz().x + UI.scale(5) + lbl.sz().x, Math.max(box.sz().y, lbl.sz().y));
    }

    @RName("chk")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    CheckBox ret = new CheckBox((String)args[0]);
	    ret.canactivate = true;
	    return(ret);
	}
    }

    public CheckBox(String lbl, boolean lg) {
        this.originalLabel=lbl;
	this.lbl = (lbl.length() > 0) ? Text.std.render(hearthborn.L10n.text("label", lbl), java.awt.Color.WHITE) : null;
	if(lg) {
	    box = lbox; mark = lmark;
	    loff = UI.scale(0, 6);
	} else {
	    box = sbox; mark = smark;
	    loff = UI.scale(5, 0);
	}
	if(this.lbl != null)
	    sz = Coord.of(box.sz().x + UI.scale(5) + this.lbl.sz().x, Math.max(box.sz().y, this.lbl.sz().y));
	else
	    sz = box.sz();
    }

    public CheckBox(String lbl) {
	this(lbl, false);
    }

    /** Checkbox captions in the settings window are one step larger. */
    public void settingsFace() {
	if(settings) return;
	settings = true;
	if(this.lbl == null) return;
	this.lbl.dispose();
	this.lbl = SETTINGS.render(labelText(), hearthborn.ui.UiPalette.tier(hearthborn.ui.UiPalette.CAPTION));
	sz = Coord.of(box.sz().x + UI.scale(5) + this.lbl.sz().x, Math.max(box.sz().y, this.lbl.sz().y));
    }

    private Text ink;
    private int inkGeneration = -1;
    public void draw(GOut g) {
	if(suffix != null && !suffix.get().equals(suffixShown))
	    relabel();
	Text shown = caption();
	if(shown != null)
	    g.image(shown.tex(), loff.add(box.sz().x, (sz.y - shown.sz().y) / 2));
        Tex face = settings ? hearthborn.ui.CheckSkin.box(box.sz().x, box.sz().y) : box;
        g.image(face, Coord.z.add(0, (sz.y - box.sz().y) / 2));
        if(background!=null){
            int inset=UI.scale(3);
            g.chcolor(background);
            g.frect(Coord.of(inset,(sz.y-box.sz().y)/2+inset),box.sz().sub(inset*2,inset*2));
            g.chcolor();
        }
        if(state()) {
            Tex tick = settings ? hearthborn.ui.CheckSkin.mark(box.sz().x, box.sz().y) : mark;
            g.image(tick, Coord.z.add(0, (sz.y - tick.sz().y) / 2));
        }
    }

    private Text caption() {
	if(lbl == null || (!settings && (!hearthborn.ui.UiTheme.light() || !hearthborn.ui.UiTheme.onPanel(this))))
	    return(lbl);
	if(ink == null || inkGeneration != hearthborn.ui.UiTheme.generation()) {
	    if(ink != null)
		ink.dispose();
	    ink = (settings ? SETTINGS : Text.std).render(labelText(),
							  settings ? hearthborn.ui.UiPalette.tier(hearthborn.ui.UiPalette.CAPTION) : hearthborn.ui.UiTheme.INK);
	    inkGeneration = hearthborn.ui.UiTheme.generation();
	}
	return(ink);
    }
    public boolean mousedown(MouseDownEvent ev) {
	if(ev.b == 1) {
	    click();
	    return(true);
	}
	return(super.mousedown(ev));
    }
}
