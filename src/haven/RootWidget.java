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

import haven.iosys.tk.*;
import java.util.*;
import java.awt.Color;

public class RootWidget extends ConsoleHost implements UI.Notice.Handler, Widget.CursorQuery.Handler, Console.Directory {
    public static final Text.Foundry msgfoundry = new Text.Foundry(Text.dfont, 14);
    public boolean modtip = false;
    Profile guprof, grprof, ggprof;
    private Text lastmsg;
    private double msgtime;
    private boolean characterBackdrop;
    private boolean characterSelected;

    public void beginCharacterSession() {
        characterBackdrop=true;
        characterSelected=false;
    }

    public void enteringWorld() {
        characterBackdrop=true;
        characterSelected=true;
    }
	
    public RootWidget(UI ui, Coord sz) {
	super(ui, new Coord(0, 0), sz);
	setfocusctl(true);
	hasfocus = true;
    }

    public boolean getcurs(CursorQuery ev) {
	if(cursor != null) {
	    try {
		ev.set(cursor.get());
	    } catch(Loading l) {}
	}
	return(false);
    }

    public static final KeyMatch key_prof = KeyMatch.forloc(Key.Loc.Std.TLDE, "`", 0);
    public boolean globtype(GlobKeyEvent ev) {
	if(ev.propagate(this))
	    return(true);
	if(key_prof.match(ev)) {
	    if(UILoop.profile.get()) {
		add(new Profwnd(guprof, "UI profile"), UI.scale(100, 100));
		add(new Profwnd(grprof, "GL profile"), UI.scale(500, 100));
		/* XXXRENDER
		   GameUI gi = findchild(GameUI.class);
		   if((gi != null) && (gi.map != null))
		   add(new Profwnd(gi.map.prof, "Map profile"), UI.scale(100, 250));
		*/
		add(new Profwnd(ggprof, "GPU profile"), UI.scale(500, 250));
	    }
	    return(true);
	} else if(ev.c == ':') {
	    entercmd();
	    return(true);
	} else if(ev.c != 0) {
	    wdgmsg("gk", (int)ev.c, ev.mods);
	    return(true);
	}
	return(super.globtype(ev));
    }

    public void draw(GOut g) {
        boolean signingIn=findchild(LoginScreen.class)!=null;
        GameUI game=findchild(GameUI.class);
        Charlist characters=findchild(Charlist.class);
        if(signingIn || (game!=null && game.map!=null)) {
            characterBackdrop=false;
            characterSelected=false;
        } else if(characters!=null)characterBackdrop=true;
        // RemoteUI starts the backdrop before server widgets arrive and keeps it
        // until the map widget arrives, including transfers to a fresh UI.
        if(characterBackdrop && !signingIn) {
            if(characterSelected || characters==null) {
                // Server widgets continue receiving normal draws during the transition.
                super.draw(g);
                hearthborn.ui.LoginArt.drawCharacters(g,sz);
                hearthborn.ui.LoginArt.drawLoading(g,sz);
            } else {
                Avaview preview=null;
                for(Avaview candidate:children(Avaview.class))
                    if(candidate.tvisible() && (preview==null || (long)candidate.sz.x*candidate.sz.y>(long)preview.sz.x*preview.sz.y))preview=candidate;
                // Server account badges may be inside a fixed-width layout container.
                // Position and draw them in root coordinates, outside that container's clip.
                java.util.List<hearthborn.ui.AccountStatusFlag> badges=new java.util.ArrayList<>();
                for(hearthborn.ui.AccountStatusFlag badge:children(hearthborn.ui.AccountStatusFlag.class))badges.add(badge);
                for(hearthborn.ui.AccountStatusFlag badge:badges) {
                    if(badge.parent!=this) {
                        Coord at=badge.rootpos();
                        badge.unlink();
                        add(badge,at);
                    }
                }
                if(preview!=null && preview.sz.y>UI.scale(200) && characters!=null)preview.fitCharacterPreview(characters.logoutButton());
                for(hearthborn.ui.AccountStatusFlag badge:badges)badge.tick(0);
                characterChildren(g,true);
                hearthborn.ui.LoginArt.drawCharacters(g,sz);
                characterChildren(g,false);
                if(preview!=null && preview.sz.y>UI.scale(200) && characters!=null)preview.drawCharacterPreview(g);
                /* The changelog is read on its own: above everything else, the big character picture included. */
                for(Widget w=child;w!=null;w=w.next)
                    if(w.visible && w instanceof hearthborn.ui.ChangelogWindow)w.draw(g.reclip(xlate(w.c,true),w.sz));
            }
        } else super.draw(g);
	if(cmdline != null) {
	    drawcmd(g, new Coord(UI.scale(20), sz.y - UI.scale(20)));
	} else if(lastmsg != null) {
	    if((Utils.rtime() - msgtime) > 3.0) {
		lastmsg = null;
	    } else {
		Coord msgc = pos("cbl").adds(20, -20).sub(0, lastmsg.sz().y);
		g.chcolor(0, 0, 0, 192);
		g.frect(msgc.sub(UI.scale(2, 2)), lastmsg.sz().add(UI.scale(4, 4)));
		g.chcolor();
		g.image(lastmsg.tex(), msgc);
	    }
	}
    }

    private void characterChildren(GOut g,boolean background) {
        for(Widget w=child,next;w!=null;w=next) {
            next=w.next;
            if(!w.visible || w instanceof hearthborn.ui.AccountStatusFlag || (!background && (w instanceof hearthborn.ui.NewsPopup || w instanceof hearthborn.ui.ChangelogWindow)))continue;
            boolean painting=!(w instanceof Charlist) && !(w instanceof hearthborn.ui.ChangelogWindow) && w.findchild(Charlist.class)==null
                && !(w instanceof Avaview) && !(w instanceof Button) && !(w instanceof IButton)
                && w.sz.x>=UI.scale(500) && w.sz.y>=UI.scale(280);
            if(painting==background)w.draw(g.reclip(xlate(w.c,true),w.sz));
        }
        if(!background)for(Widget w=child;w!=null;w=w.next) {
            if(w.visible && w instanceof hearthborn.ui.AccountStatusFlag)w.draw(g.reclip(xlate(w.c,true),w.sz));
        }
        /* The news come last: above the account badges, which are drawn over the rest. */
        if(!background)for(Widget w=child;w!=null;w=w.next) {
            if(w.visible && w instanceof hearthborn.ui.NewsPopup)w.draw(g.reclip(xlate(w.c,true),w.sz));
        }
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "err") {
	    ui.error((String)args[0]);
	} else if(msg == "msg") {
	    if(args.length == 1) {
		ui.msg((String)args[0]);
	    } else {
		int a = 0;
		UI.SimpleMessage info = new UI.InfoMessage((String)args[a++]);
		if(args[a] instanceof Color)
		    info.color = (Color)args[a++];
		if(args.length > a) {
		    Indir<Resource> res = ui.sess.getresv(args[a++]);
		    info.sfx = (res == null) ? null : Audio.resclip(res.get());
		}
		ui.msg(info);
	    }
	} else if(msg == "msg2") {
	    Resource res = ui.sess.getresv(args[0]).get();
	    UI.Notice.Factory fac = res.getcode(UI.Notice.Factory.class, true);
	    ui.msg(fac.format(new OwnerContext() {
		    public <T> T context(Class<T> cl) {
			return(wdgctx.context(cl, RootWidget.this));
		    }
		}, Utils.splice(args, 1)));
	} else if(msg == "sfx") {
	    int a = 0;
	    Indir<Resource> resid = ui.sess.getresv(args[a++]);
	    double vol = (args.length > a) ? Utils.dv(args[a++]) : 1.0;
	    double spd = (args.length > a) ? Utils.dv(args[a++]) : 1.0;
	    ui.sess.glob.loader.defer(() -> {
		    Audio.CS clip = Audio.fromres(resid.get());
		    if(spd != 1.0)
			clip = new Audio.Resampler(clip).sp(spd);
		    if(vol != 1.0)
			clip = new Audio.VolAdjust(clip, vol);
		    ui.sfx(clip);
		}, null);
	} else if(msg == "bgm") {
	    int a = 0;
	    Indir<Resource> resid = (args.length > a) ? ui.sess.getresv(args[a++]) : null;
	    boolean loop = (args.length > a) ? Utils.bv(args[a++]) : false;
	    if(Music.enabled) {
		if(resid == null)
		    Music.play(null, false);
		else
		    Music.play(resid, loop);
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void msg(String msg, Color color) {
	lastmsg = msgfoundry.render(msg, color);
	msgtime = Utils.rtime();
    }

    public boolean msg(UI.Notice msg) {
	if(msg.handler(this))
	    return(true);
	msg(msg.message(), msg.color());
	ui.sfxrl(msg.sfx());
	return(true);
    }

    public void error(String msg) {
	ui.error(msg);
    }

    public Object tooltip(Coord c, Widget prev) {
	if(modtip && (ui.modflags() != 0))
	    return(KeyMatch.modname(ui.modflags()));
	return(super.tooltip(c, prev));
    }

    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("wdgtree", (cons, args) -> {
	    for(Widget w = RootWidget.this; w != null; w = w.rnext()) {
		for(Widget p = w.parent; p != null; p = p.parent)
		    cons.out.write('\t');
		cons.out.write(w.visible ? 'S' : 'H');
		cons.out.write(' ');
		cons.out.write(w.hasfocus ? "F" : "f");
		cons.out.write(w.focusctl ? "C" : "c");
		cons.out.write(w.focustab ? "T" : "t");
		cons.out.write(w.canfocus ? "A" : "a");
		cons.out.write(w.autofocus ? "T" : "t");
		cons.out.write(((w.parent != null) && (w.parent.focused == w)) ? "P" : "p");
		cons.out.write(' ');
		cons.out.write(w.toString());
		cons.out.write('\n');
	    }
	});
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
