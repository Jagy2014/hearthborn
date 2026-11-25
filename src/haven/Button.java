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

import java.awt.Graphics;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;

public class Button extends SIWidget {
    public static final BufferedImage bl = Resource.loadsimg("gfx/hud/buttons/tbtn/left");
    public static final BufferedImage br = Resource.loadsimg("gfx/hud/buttons/tbtn/right");
    public static final BufferedImage bt = Resource.loadsimg("gfx/hud/buttons/tbtn/top");
    public static final BufferedImage bb = Resource.loadsimg("gfx/hud/buttons/tbtn/bottom");
    public static final BufferedImage dt = Resource.loadsimg("gfx/hud/buttons/tbtn/dtex");
    public static final BufferedImage ut = Resource.loadsimg("gfx/hud/buttons/tbtn/utex");
    public static final BufferedImage bm = Resource.loadsimg("gfx/hud/buttons/tbtn/mid");
    public static final int hs = bl.getHeight(), hl = bm.getHeight();
    public static final Resource click = Loading.waitfor(Resource.local().load("sfx/hud/btn"));
    public static final Audio.Clip clbtdown = Loading.waitfor(Resource.local().load("sfx/hud/lbtn")).layer(Resource.audio, "down");
    public static final Audio.Clip clbtup = Loading.waitfor(Resource.local().load("sfx/hud/lbtn")).layer(Resource.audio, "up");
    public static final int margin = UI.scale(10);
    public boolean lg;
    public Text text;
    public BufferedImage cont;
    public Runnable action = null;
    static Text.Foundry tf = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(12f))).aa(true);
    private static final Text.Foundry settingsTf = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(14f))).aa(true);
    /** Settings windows show all their text one step larger; this button follows. */
    private boolean settings;
    /** The caption as given, drawn in the ink of the current theme; null when the caption came ready-made. */
    private String caption;
    private Color ink;
    private boolean inkLight;
    private Text shade;
    /** How the button is drawn: the default graphite pill, or one of the flat faces of the settings window. */
    public enum Look {PILL, MENU, OUTLINE, PRIMARY, DANGER, QUIET}
    private Look look = Look.PILL;
    private Color lookInk;
    private boolean a = false, dis = false, hover = false, selected = false;
    private UI.Grab d = null;
	
    @RName("btn")
    public static class $Btn implements Factory {
	public Widget create(UI ui, Object[] args) {
	    if(args.length > 2)
		return(new Button(UI.scale(Utils.iv(args[0])), (String)args[1], Utils.bv(args[2])));
	    else
		return(new Button(UI.scale(Utils.iv(args[0])), (String)args[1]));
	}
    }
    @RName("ltbtn")
    public static class $LTBtn implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(wrapped(UI.scale(Utils.iv(args[0])), (String)args[1]));
	}
    }
	
    public static Button wrapped(int w, String text) {
	Button ret = new Button(w, tf.renderwrap(hearthborn.L10n.text("button", text), w - margin));
	return(ret);
    }
        
    private static boolean largep(int w) {
	return(w >= (bl.getWidth() + bm.getWidth() + br.getWidth()));
    }

    private Button(int w, boolean lg) {
	super(new Coord(w, lg?hl:hs));
	this.lg = lg;
    }

    public Button(int w, String text, boolean lg, Runnable action) {
	this(w, lg);
	this.action = action;
	caption(hearthborn.L10n.text("button", text), null);
    }

    public Button(int w, String text, boolean lg) {
	this(w, text, lg, null);
	this.action = () -> wdgmsg("activate");
    }

    public Button(int w, String text, Runnable action) {
	this(w, text, largep(w), action);
    }

    public Button(int w, String text) {
	this(w, text, largep(w));
    }

    public Button(int w, Text text) {
	this(w, largep(w));
	this.text = text;
	this.cont = text.img;
    }
	
    public Button(int w, BufferedImage cont) {
	this(w, largep(w));
	this.cont = cont;
    }
	
    /** Renders the caption in the ink of the current theme, or in the colour the server asked for. */
    private void caption(String shown, Color custom) {
	if(text != null) text.dispose();
	if(shade != null) {shade.dispose(); shade = null;}
	caption = shown;
	lookInk = null;
	ink = custom;
	inkLight = hearthborn.ui.UiTheme.light();
	Text.Foundry foundry = settings ? settingsTf : tf;
	text = foundry.render(shown, custom != null ? custom : hearthborn.ui.PillSkin.ink(inkLight));
	cont = text.img;
	/* A caption that does not fit grows its button instead of running over the ends; a roomy button keeps its width. */
	int need = captionWidth(Utils.imgsz(cont).x);
	if(need > sz.x) resize(new Coord(need, sz.y));
	Color dark = custom != null ? null : hearthborn.ui.PillSkin.shade(inkLight);
	if(dark != null) shade = foundry.render(shown, dark);
    }

    private static int captionWidth(int textWidth) {
	return(textWidth + UI.scale(8) * 2);
    }

    static int minimumWidth(String text) {
	return(captionWidth(tf.strsize(hearthborn.L10n.text("button", text)).x));
    }

    public Button action(Runnable action) {
	this.action = action;
	return(this);
    }

    /** Draws the button in one of the flat looks; the default is the pill. */
    public Button look(Look look) {
	if(this.look != look) {
	    this.look = look;
	    lookInk = null;
	    redraw();
	}
	return(this);
    }

    public Look look() {return(look);}

    private void drawLook(BufferedImage img) {
	Color want = hearthborn.ui.ButtonLook.ink(look, hover || a, selected);
	if(caption != null && !want.equals(lookInk)) {
	    if(text != null) text.dispose();
	    if(shade != null) {shade.dispose(); shade = null;}
	    text = (settings ? settingsTf : tf).render(caption, want);
	    cont = text.img;
	    lookInk = want;
	}
	hearthborn.ui.ButtonLook.paint(img, look, hover, a, selected);
	Graphics g = img.getGraphics();
	Coord tc = sz.sub(Utils.imgsz(cont)).div(2);
	if(look == Look.MENU)
	    tc = new Coord(hearthborn.ui.ButtonLook.menuPad(), tc.y);
	if(a && look != Look.MENU)
	    tc = tc.add(UI.scale(1), UI.scale(1));
	g.drawImage(cont, tc.x, tc.y, null);
	g.dispose();
	if(dis)
	    PUtils.monochromize(img, Color.LIGHT_GRAY);
    }

    private int themeGeneration = hearthborn.ui.UiTheme.generation();

    /** A change of theme or accent draws the face again, without waiting for the pointer to touch it. */
    public void draw(GOut g) {
	if(themeGeneration != hearthborn.ui.UiTheme.generation()) {
	    themeGeneration = hearthborn.ui.UiTheme.generation();
	    redraw();
	}
	super.draw(g);
    }

    public void draw(BufferedImage img) {
	if(look != Look.PILL) {
	    drawLook(img);
	    return;
	}
	boolean light = hearthborn.ui.UiTheme.light();
	if(caption != null && ink == null && light != inkLight) caption(caption, null);
	int yo = lg?((hl - hs) / 2):0;
	hearthborn.ui.PillSkin.paint(img, yo, hs, a || selected, hover || a || selected, light);
	Graphics g = img.getGraphics();
	Coord tc = sz.sub(Utils.imgsz(cont)).div(2);
	if(a || selected)
	    tc = tc.add(UI.scale(1), UI.scale(1));
	if(shade != null)
	    g.drawImage(shade.img, tc.x + UI.scale(1), tc.y + UI.scale(1), null);
	g.drawImage(cont, tc.x, tc.y, null);
	g.dispose();

	if(dis)
	    PUtils.monochromize(img, Color.LIGHT_GRAY);
    }

    public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
	if(hovering != hover) {
	    hover = hovering;
	    redraw();
	}
	return(super.mousehover(ev, hovering));
    }

    /** Marks the button as the one whose section is open: it stays pressed in. */
    public void selected(boolean selected) {
	if(this.selected == selected) return;
	this.selected = selected;
	redraw();
    }

    public boolean selected() {return(selected);}

    public boolean disabled() {return(dis);}

    /** Settings buttons keep the same face, one step larger than the other buttons. */
    public void settingsFace() {
	if(settings || text == null) return;
	settings = true;
	if(caption != null) caption(caption, ink);
	redraw();
    }

    public void change(String text, Color col) {
	caption(hearthborn.L10n.text("button", text), col);
	redraw();
    }
    
    public void change(String text) {
	caption(hearthborn.L10n.text("button", text), null);
	redraw();
    }

    public void disable(boolean dis) {
	this.dis = dis;
	redraw();
    }

    public void click() {
	if(action != null)
	    action.run();
    }

    public boolean gkeytype(GlobKeyEvent ev) {
	click();
	return(true);
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "ch") {
	    if(args.length > 1)
		change((String)args[0], (Color)args[1]);
	    else
		change((String)args[0]);
	} else if(msg == "dis") {
	    disable(Utils.bv(args[1]));
	} else {
	    super.uimsg(msg, args);
	}
    }
    
    public void mousemove(MouseMoveEvent ev) {
	super.mousemove(ev);
	if(d != null) {
	    boolean a = ev.c.isect(Coord.z, sz);
	    if(a != this.a) {
		this.a = a;
		redraw();
	    }
	}
    }

    protected void depress() {
	ui.sfx(click);
    }

    protected void unpress() {
	ui.sfx(click);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if((ev.b != 1) || dis)
	    return(super.mousedown(ev));
	a = true;
	d = ui.grabmouse(this);
	depress();
	redraw();
	return(true);
    }
	
    public boolean mouseup(MouseUpEvent ev) {
	if((d != null) && ev.b == 1) {
	    d.remove();
	    d = null;
	    a = false;
	    redraw();
	    if(ev.c.isect(Coord.z, sz)) {
		unpress();
		click();
	    }
	    return(true);
	}
	return(super.mouseup(ev));
    }
}
