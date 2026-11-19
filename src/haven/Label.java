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

import java.awt.Color;

public class Label extends Widget {
    public final Text.Foundry f;
    private static final Text.Foundry SETTINGS = new Text.Foundry(Text.sans, 12).aa(true);
    private boolean settings;
    public Text text;
    public String texts;
    public Color col = Color.WHITE;

    @RName("lbl")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    if(args.length > 1)
		return(new Label(Utils.sv(args[0]), UI.scale(Utils.iv(args[1]))));
	    else
		return(new Label(Utils.sv(args[0])));
	}
    }

    public Label(String text, int w, Text.Foundry f) {
	super(Coord.z);
	this.f = f;
	this.wrap = w;
	this.text = f.renderwrap(hearthborn.ui.RuneTranslation.label(texts = text), this.col, w);
	resize(this.text.sz());
    }

    public Label(String text, Text.Foundry f) {
	super(Coord.z);
	this.f = f;
	this.text = renderLabel(texts = text,this.col);
	resize(this.text.sz());
    }

    public Label(String text, int w) {
	this(text, w, Text.std);
    }

    public Label(String text) {
	this(text, Text.std);
    }

    /** Body text in the settings window is one step larger than the usual label. */
    public void settingsFace() {
	if(settings || f != Text.std) return;
	settings = true;
	this.text.dispose();
	this.text = renderLabel(texts, col);
	resize(this.text.sz());
    }

    /** The width the text was asked to wrap at, kept so that a new face or new text wraps the same way. */
    private int wrap;

    /** Which text tier of the settings window this label is (0: none, the usual colours apply). */
    private int tier, tierGeneration = -1;

    /** Draws the label in a tier colour of {@link hearthborn.ui.UiPalette} while it keeps the default colour. */
    public Label tier(int tier) {
	if(this.tier == tier) return(this);
	this.tier = tier;
	this.text.dispose();
	this.text = renderLabel(texts, col);
	tierGeneration = hearthborn.ui.UiTheme.generation();
	resize(this.text.sz());
	return(this);
    }

    public int tier() {return(tier);}

    private Text.Foundry face() {
	return settings ? SETTINGS : f;
    }

    private Text renderLabel(String source,Color color){
        Text.Foundry face = face();
        if(tier != 0 && color == Color.WHITE) color = hearthborn.ui.UiPalette.tier(tier);
        String translated=hearthborn.ui.RuneTranslation.label(source);
        if(wrap > 0)return face.renderwrap(translated,color,wrap);
        return translated.contains("\n")?face.renderwrap(translated,color,Math.max(UI.scale(300),face.strsize(source).x)):face.render(translated,color);
    }

    private Text ink;
    private int inkGeneration = -1;
    public void draw(GOut g) {
	g.image(shown().tex(), Coord.z);
    }

    private Text shown() {
	if(tier != 0 && col == Color.WHITE) {
	    if(tierGeneration != hearthborn.ui.UiTheme.generation()) {
		text.dispose();
		text = renderLabel(texts, col);
		tierGeneration = hearthborn.ui.UiTheme.generation();
	    }
	    return(text);
	}
	if(col != Color.WHITE || !hearthborn.ui.UiTheme.light() || !hearthborn.ui.UiTheme.onPanel(this))
	    return(text);
	if(ink == null || inkGeneration != hearthborn.ui.UiTheme.generation()) {
	    if(ink != null)
		ink.dispose();
	    ink = renderLabel(texts, hearthborn.ui.UiTheme.INK);
	    inkGeneration = hearthborn.ui.UiTheme.generation();
	}
	return(ink);
    }

    public void settext(String text) {
	if(text.equals(this.text.text))
	    return;
	this.text.dispose();
	this.text = renderLabel(texts = text,col);
	resize(this.text.sz());
    }

    public void setcolor(Color color) {
	if(color.equals(col))
	    return;
	this.text.dispose();
	this.text = renderLabel(texts,col = color);
	resize(this.text.sz());
    }

    public void dispose() {
	super.dispose();
	this.text.dispose();
	if(ink != null)
	    ink.dispose();
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "set") {
	    settext(Utils.sv(args[0]));
	} else if(msg == "col") {
	    setcolor((Color)args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }
}
