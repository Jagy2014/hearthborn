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
import java.util.*;

public class Charlist extends Widget {
    public static final Coord bsz = UI.scale(289, 96);
    public static final Text.Furnace nf = new PUtils.BlurFurn(new PUtils.TexFurn(new Text.Foundry(Text.fraktur, 20).aa(true), Window.ctex), UI.scale(2), UI.scale(2), Color.BLACK);
    public static final Text.Furnace df = new PUtils.BlurFurn(new Text.Foundry(Text.sans, 12, Color.WHITE).aa(true), UI.scale(1), UI.scale(1), Color.BLACK);
    public static final int margin = UI.scale(6);
    public static final int btnw = UI.scale(100);
    public final int height;
    public final IButton sau, sad;
    public final List<Char> chars = new ArrayList<Char>();
    public final Boxlist list;
    public Avaview avalink;
    private Button exitClient, changelog;
    private LoginScreen.StatusLabel serverStatus;
    private boolean lifting;
    private boolean dirty;
    private boolean showdisc;

    @RName("charlist")
    public static class $_ implements Factory {
	public Widget create(UI ui, Object[] args) {
	    return(new Charlist(Utils.iv(args[0])));
	}
    }

    public Charlist(int height) {
	super(Coord.z);
	this.height = height;
	setcanfocus(true);
	sau = adda(new IButton("gfx/hud/buttons/csau", "u", "d", "o"), bsz.x / 2, 0, 0.5, 0)
	    .action(() -> scroll(-1));
	list = add(new Boxlist(height), 0, sau.c.y + sau.sz.y + margin);
	sad = adda(new IButton("gfx/hud/buttons/csad", "u", "d", "o"), bsz.x / 2, list.c.y + list.sz.y + margin, 0.5, 0)
	    .action(() -> scroll(1));
	sau.hide(); sad.hide();
	resize(new Coord(bsz.x, sad.c.y + sad.sz.y));
    }

    protected void added(){
        super.added();if(parent!=null)parent.setfocus(this);
        if(exitClient==null){
            exitClient=ui.root.add(new Button(UI.scale(200),"Log out of account",false,()->{
                if(ui.sess!=null)ui.sess.close();
            }));
            if(HttpStatus.mond.get()!=null)
                serverStatus=ui.root.add(new LoginScreen.StatusLabel(HttpStatus.mond.get(),1.0));
        }
        if(changelog==null){
            changelog=ui.root.add(new Button(UI.scale(200),"Changelog",false,()->hearthborn.ui.ChangelogWindow.open(ui)));
            hearthborn.ui.ChangelogWindow.openIfNew(ui);
        }
        if(!lifting)placeExit();
    }
    public Button logoutButton(){return exitClient;}
    public Button changelogButton(){return changelog;}
    public int firstCharacterTop(){return list.rootpos().y;}
    private void placeExit(){
        if(ui==null||ui.root==null)return;
        if(serverStatus!=null) {
            Area artwork=hearthborn.ui.LoginArt.characterBounds(ui.root.sz);
            serverStatus.move(Coord.of(artwork.br.x-serverStatus.sz.x-UI.scale(10),artwork.ul.y+UI.scale(10)));
            serverStatus.show(tvisible());
        }
        placeList();
        placeAvatar();
        if(exitClient!=null && exitClient.parent!=null){
            Button create=null;
            for(Button button:ui.root.children(Button.class))if(button!=exitClient && button.text!=null && button.text.text.equals(hearthborn.L10n.tr("New character"))){create=button;break;}
            if(create==null){exitClient.hide();return;}
            if(exitClient.sz.x!=create.sz.x){
                exitClient.resize(Coord.of(create.sz.x,exitClient.sz.y));
                exitClient.redraw();
            }
            Coord createPlaced=Coord.of(Math.max(0,Math.min(ui.root.sz.x-create.sz.x,list.rootpos().x)),
                hearthborn.ui.LoginControls.characterCreateY(ui.root.sz,create.sz));
            Coord exit=hearthborn.ui.LoginControls.characterLogout(ui.root.sz,exitClient.sz,createPlaced,create.sz);
            // The server's layout container can clip controls after a monitor change.
            if(create.parent!=ui.root) {
                create.unlink();
                ui.root.add(create);
            }
            create.move(createPlaced);
            exitClient.move(exit);
            exitClient.show(tvisible()&&create.tvisible());
            if(changelog!=null && changelog.parent!=null){
                if(changelog.sz.x!=create.sz.x){
                    changelog.resize(Coord.of(create.sz.x,changelog.sz.y));
                    changelog.redraw();
                }
                changelog.move(hearthborn.ui.LoginControls.characterLogout(ui.root.sz,changelog.sz,exit,exitClient.sz));
                changelog.show(tvisible()&&create.tvisible());
            }
        }
    }
    /** The server's layout box would clip a list moved toward the left edge. */
    private void placeList(){
        int x=hearthborn.ui.LoginControls.listLeft(ui.root.sz);
        if(parent!=ui.root){
            int y=rootpos().y;
            lifting=true;
            unlink();
            ui.root.add(this,Coord.of(x,y));
            lifting=false;
            return;
        }
        if(c.x!=x)move(Coord.of(x,c.y));
    }
    private void placeAvatar(){
        if(avalink==null)return;
        Widget frame=avalink.parent instanceof ProxyFrame?avalink.parent:avalink;
        if(frame.parent==null||frame.parent==ui.root)return;
        Coord at=frame.rootpos();
        frame.unlink();
        ui.root.add(frame,at);
    }
    public void dispose(){if(unverified!=null)unverified.destroy();if(unsubscribed!=null)unsubscribed.destroy();if(donation!=null)donation.destroy();if(serverStatus!=null && serverStatus.parent!=null)serverStatus.destroy();if(exitClient!=null && exitClient.parent!=null)exitClient.destroy();if(changelog!=null && changelog.parent!=null)changelog.destroy();super.dispose();}

    public void wdgmsg(String message,Object... args){
        if(message.equals("play")&&args.length>0)synchronized(chars){
            hearthborn.ui.NearbySiegeCheck.startLogin(ui.sess);
            for(Char character:chars)if(character.name.equals(args[0])){hearthborn.ui.WorldIdentity.select(ui.sess,character.disc);break;}
            ui.root.enteringWorld();
        }
        super.wdgmsg(message,args);
    }

    public static class Char {
	public final String name;
	public String disc;
	public Composited.Desc avadesc;
	public Resource.Resolver avamap;
	public Collection<ResData> avaposes;

	public Char(String name) {
	    this.name = name;
	}

	public void ava(Composited.Desc desc, Resource.Resolver resmap, Collection<ResData> poses) {
	    this.avadesc = desc;
	    this.avamap = resmap;
	    this.avaposes = poses;
	}
    }

    /** The frame of an avatar; a flat theme draws one thin line round the picture, not the wide band of the moulding. */
    static Frame avatarFrame(Avaview ava) {
	Frame frame = new Frame(ava.sz, true) {
		public void drawframe(GOut g) {
		    if(!hearthborn.ui.FrameSkin.flat()) {super.drawframe(g); return;}
		    Coord edge = box.btloff();
		    int unit = Math.max(1, UI.scale(1));
		    hearthborn.ui.FrameSkin.thin(g, edge.sub(unit, unit), inner().add(unit * 2, unit * 2), 1);
		}
	    };
	frame.add(ava, 0, 0);
	return(frame);
    }

    public class Charbox extends Widget {
	public final Char chr;
	public final Avaview ava;
	public final ILabel name, disc;

	public Charbox(Char chr) {
	    super(bsz);
	    this.chr = chr;
	    Widget avaf = adda(avatarFrame(this.ava = new Avaview(Avaview.dasz, -1, "avacam")), Coord.of(sz.y / 2), 0.5, 0.5);
	    name = add(new ILabel(chr.name, nf), avaf.pos("ur").adds(5, 0));
	    disc = add(new ILabel("", df), name.pos("bl"));
	    adda(new Button(UI.scale(100), "Play"), pos("cbr").subs(10, 2), 1.0, 1.0).action(() -> Charlist.this.wdgmsg("play", chr.name));
	}

	public void tick(double dt) {
	    if(chr.avadesc != ava.avadesc)
		ava.pop(chr.avadesc, chr.avamap);
	    String sdisc = showdisc ? (chr.disc == null ? "" : chr.disc) : "";
	    if(!Utils.eq(sdisc, disc.text()))
		disc.settext(sdisc);
	}

	public void draw(GOut g) {
	    if(hearthborn.ui.FrameSkin.flat())
		hearthborn.ui.FrameSkin.thin(g, Coord.z, sz, list.sel == chr ? 2 : 1);
	    else {
		if(list.sel == chr)
		    g.chcolor(255, 255, 128, 255);
		ISBox.box.draw(g, Coord.z, sz);
		g.chcolor();
	    }
	    super.draw(g);
	}

	public boolean mousedown(MouseDownEvent ev) {
	    list.change(chr);
	    return(super.mousedown(ev));
	}
    }

    public class Boxlist extends SListBox<Char, Charbox> {
	public Boxlist(int h) {
	    super(Coord.of(bsz.x, ((bsz.y + margin) * h) - margin), bsz.y, margin);
	}

	protected List<Char> items() {return(chars);}
	protected Charbox makeitem(Char chr, int idx, Coord sz) {return(new Charbox(chr));}

	protected void drawslot(GOut g, Char item, int idx, Area area) {}
	public boolean mousewheel(MouseWheelEvent ev) {return(false);}
	protected boolean unselect(int button) {return(false);}
	protected boolean autoscroll() {return(false);}

	public void change(Char chr) {
	    super.change(chr);
	    display(chr);
	    if((avalink != null) && (chr.avadesc != null)) {
		avalink.pop(chr.avadesc.clone(), chr.avamap);
		avalink.chposes(chr.avaposes, false);
	    }
	}
    }


    private void checkdisc() {
        showdisc = true;
    }


    private hearthborn.ui.AccountStatusFlag unverified,unsubscribed,donation;
    public void accountBadges(){
        if(ui==null||parent==null||avalink==null)return;
        if(unverified==null){unverified=ui.root.add(new hearthborn.ui.AccountStatusFlag(false,false));unsubscribed=ui.root.add(new hearthborn.ui.AccountStatusFlag(true,false));donation=ui.root.add(hearthborn.ui.AccountStatusFlag.donation());}
        unverified.show(tvisible()&&!hearthborn.ui.AccountStatusFlag.hasStatus(ui,false));
        unsubscribed.show(tvisible()&&!hearthborn.ui.AccountStatusFlag.hasStatus(ui,true));
        donation.show(tvisible());
    }
    private int scrolltgt = -1;
    private double scrollval = -1;
    public void tick(double dt) {
        accountBadges();placeExit();
	if(scrolltgt >= 0) {
	    if(scrollval < 0)
		scrollval = list.scrollval();
	    double d = scrollval - scrolltgt;
	    double nv = scrolltgt + (d * Math.pow(0.5, dt * 50));
	    if(Math.abs(nv - scrolltgt) < 1) {
		nv = scrolltgt;
		scrolltgt = -1;
		scrollval = -1;
	    }
	    list.scrollval((int)Math.round(scrollval = nv));
	}
	if(dirty) {
	    checkdisc();
	    dirty = false;
	}
	super.tick(dt);
    }

    public void scroll(int amount) {
	scrolltgt = Utils.clip(((scrolltgt < 0) ? list.scrollval() : scrolltgt) + ((bsz.y + margin) * amount), list.scrollmin(), list.scrollmax());
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	scroll(ev.a);
	return(true);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "add") {
	    Char c = new Char((String)args[0]);
	    if(args.length > 1) {
		Object[] rawdesc = (Object[])args[1];
		Collection<ResData> poses = new ArrayList<>();
		Composited.Desc desc = Composited.Desc.decode(ui.sess, rawdesc);
		Resource.Resolver map = new Resource.Resolver.ResourceMap(ui.sess, (Object[])args[2]);
		if(rawdesc.length > 3) {
		    Object[] rawposes = (Object[])rawdesc[3];
		    for(int i = 0; i < rawposes.length; i += 2)
			poses.add(new ResData(ui.sess.getresv(rawposes[i]), new MessageBuf((byte[])rawposes[i + 1])));
		}
		c.ava(desc, map, poses);
	    }
	    synchronized(chars) {
		chars.add(c);
		if(chars.size() > height) {
		    sau.show();
		    sad.show();
		}
		if(list.sel == null)
		    list.change(c);
	    }
	    dirty = true;
	} else if(msg == "ava") {
	    String cnm = (String)args[0];
	    Object[] rawdesc = (Object[])args[1];
	    Collection<ResData> poses = new ArrayList<>();
	    Composited.Desc ava = Composited.Desc.decode(ui.sess, rawdesc);
	    Resource.Resolver map = new Resource.Resolver.ResourceMap(ui.sess, (Object[])args[2]);
	    if(rawdesc.length > 3) {
		Object[] rawposes = (Object[])rawdesc[3];
		for(int i = 0; i < rawposes.length; i += 2)
		    poses.add(new ResData(ui.sess.getresv(rawposes[i]), new MessageBuf((byte[])rawposes[i + 1])));
	    }
	    synchronized(chars) {
		for(Char c : chars) {
		    if(c.name.equals(cnm)) {
			c.ava(ava, map, poses);
			dirty = true;
			break;
		    }
		}
	    }
	} else if(msg == "srv") {
	    String cnm = (String)args[0];
	    String disc = (String)args[1];
	    synchronized(chars) {
		for(Char c : chars) {
		    if(c.name.equals(cnm)) {
			c.disc = disc;
			dirty = true;
			break;
		    }
		}
	    }
	} else if(msg == "biggu") {
	    int id = Utils.iv(args[0]);
	    if(id < 0) {
		avalink = null;
	    } else {
		Widget tgt = ui.getwidget(id);
		if(tgt instanceof ProxyFrame)
		    avalink = (Avaview)((ProxyFrame)tgt).ch;
		else if(tgt instanceof Avaview)
		    avalink = (Avaview)tgt;
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }

    public boolean keydown(KeyDownEvent ev) {
	if(ev.code == ev.awt.VK_UP) {
	    if(!chars.isEmpty())
		list.change(chars.get(Math.max(chars.indexOf(list.sel) - 1, 0)));
	    return(true);
	} else if(ev.code == ev.awt.VK_DOWN) {
	    if(!chars.isEmpty())
		list.change(chars.get(Math.min(chars.indexOf(list.sel) + 1, chars.size() - 1)));
	    return(true);
	} else if(ev.code == ev.awt.VK_ENTER) {
	    if(list.sel != null)
		wdgmsg("play", list.sel.name);
	    return(true);
	}
	return(super.keydown(ev));
    }
}
