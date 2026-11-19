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
import java.net.URI;

public class LoginScreen extends Widget {
    /** Set by the Steam launch file: the client may offer the Steam login next to the account name and password. */
    public static final String STEAM_LOGIN = "haven.steamlogin";
    /** Whether Steam answers; replaced in tests, which have none. */
    static java.util.function.BooleanSupplier steamAvailable = () -> Steam.get() != null;
    public static final Config.Variable<String> authmech = Config.Variable.prop("haven.authmech", "native");
    public static final Text.Foundry
	textf = new Text.Foundry(Text.sans, 16).aa(true),
	textfs = new Text.Foundry(Text.sans, 14).aa(true);
    public static final Position bgc = new Position(UI.scale(420, 300));
    public Widget login;
    public final String confname;
    private Text error, progress;
    private String lastError, lastProgress;
    private Widget cancel;
    private Button optbtn;
    private Button closeClient;
    private OptWnd opts;
    private hearthborn.ui.SavedLogins accounts;
    private Widget chooser;
    private Widget status;
    private Widget language;
    private Button back;
    private boolean formMode, accepting;


    private String getpref(String name, String def) {
	return(Utils.getpref(name + "@" + confname, def));
    }

    public LoginScreen(String confname) {
	super(UI.scale(800, 600));
	this.confname = confname;
        setfocustab(true);
        buildView();
    }

    private Button steam;

    private static boolean steamOffered() {
        String mode = System.getProperty(STEAM_LOGIN);
        return mode != null && !mode.equalsIgnoreCase("off") && !mode.equalsIgnoreCase("false") && steamAvailable.getAsBoolean();
    }

    private void steamEnter() {
        if(!accepting) return;
        try {
            error(null);
            wdgmsg("login", new SteamCreds(), false);
        } catch(java.io.IOException e) {
            error(e.getMessage());
        }
    }

    /** Under the account form (or the list of saved accounts), centered. */
    private void placeSteam() {
        if(steam == null || login == null) return;
        Widget last = chooser != null && chooser.visible ? chooser : login;
        int bottom = last.c.y + last.sz.y;
        if(back != null && back.visible) bottom = Math.max(bottom, back.c.y + back.sz.y);
        steam.move(Coord.of(Math.max(0, (sz.x - steam.sz.x) / 2), bottom + UI.scale(10)));
        steam.show(accepting && progress == null);
    }

    private void buildView() {
        steam = null;
        closeClient=add(new Button(UI.scale(200),"Close",false,()->hearthborn.ui.LoginControls.close(ui)));
        status = null;
        language = null;
	optbtn = adda(new Button(UI.scale(100), "Options"), pos("cbl").add(10, -10), 0, 1);
	optbtn.setgkey(GameUI.kb_opt);
	if(HttpStatus.mond.get() != null)
	    status = adda(new StatusLabel(HttpStatus.mond.get(), 1.0), sz.x - UI.scale(10), UI.scale(10), 1.0, 0.0);
	switch(authmech.get()) {
	case "native":
	    login = new Credbox();
	    break;
	case "steam":
	    login = new Steambox();
	    break;
	default:
	    throw(new RuntimeException("Unknown authmech: " + authmech.get()));
	}
	adda(login, bgc.adds(0, 10), 0.5, 0.0).hide();
        if(login instanceof Credbox) {
            Credbox credentials = (Credbox)login;
            chooser = add(new Widget(Coord.z), Coord.z);
            accounts = chooser.add(new hearthborn.ui.SavedLogins(confname,
                name -> {
                    if(!accepting) return;
                    credentials.init();
                    credentials.user.settext2(name);
                    error(null);
                    credentials.enter();
                },
                name -> {
                    if(!accepting) return;
                    Bootstrap.settoken(name, confname, null);
                    accounts.refresh();
                    credentials.checktoken();
                    if(accepting) showLogin();
                }), Coord.z);
            chooser.add(new Button(UI.scale(225), "Add account").action(() -> {
                if(!accepting) return;
                credentials.init();
                credentials.user.settext2("");
                credentials.pass.rsettext("");
                formMode = true;
                error(null);
                showLogin();
                credentials.setfocus(credentials.user);
            }), Coord.z);
            chooser.hide();
            back = add(new Button(UI.scale(200), "Saved accounts").action(() -> {
                if(!accepting) return;
                formMode = false;
                error(null);
                showLogin();
            }), Coord.z);
            back.hide();
        }
        language = adda(new hearthborn.ui.LoginLanguage(this::changeLanguage), optbtn.c.add(0, -UI.scale(5)), 0, 1);

	cancel = adda(new Button(UI.scale(100), "Cancel")
		      .action(() -> wdgmsg("cancel"))
		      .setgkey(key_esc),
		      bgc.adds(0, 75), 0.5, 0.0);
	cancel.hide();
        if(login instanceof Credbox && steamOffered()) {
            steam = add(new Button(UI.scale(200), "Log in with Steam", false, this::steamEnter), Coord.z);
            steam.hide();
        }
    }

    public void changeLanguage(String language) {
        String username = login instanceof Credbox ? ((Credbox)login).user.text() : "";
        String password = login instanceof Credbox ? ((Credbox)login).pass.text() : "";
        boolean remember = login instanceof Credbox && ((Credbox)login).savetoken.state();
        boolean ready = accepting;
        String errorMessage = lastError, progressMessage = lastProgress;
        hearthborn.L10n.switchLanguage(language);
        for(Widget child = this.child, next; child != null; child = next) {
            next = child.next;
            child.destroy();
        }
        accounts = null; chooser = null; back = null; opts = null;
        buildView();
        if(login instanceof Credbox) {
            Credbox credentials = (Credbox)login;
            credentials.init();
            credentials.user.settext2(username);
            credentials.pass.rsettext(password);
            credentials.savetoken.set(remember);
        }
        if(ready) mklogin();
        else if(progressMessage != null) showLogin();
        error(errorMessage);
        progress(progressMessage);
        presize();
    }

    public static final KeyBinding kb_savtoken = KeyBinding.get("login/savtoken", KeyMatch.forchar('R', KeyMatch.M));
    public static final KeyBinding kb_deltoken = KeyBinding.get("login/deltoken", KeyMatch.forchar('F', KeyMatch.M));
    public class Credbox extends Widget {
	public final UserEntry user;
	private final TextEntry pass;
	private final CheckBox savetoken;
	private final Button fbtn;
	private final IButton exec;
	private final Widget pwbox, tkbox;
        private final Label notice, passwordLabel;
	private byte[] token = null;
	private boolean inited = false;

	public class UserEntry extends TextEntry {
	    private final List<String> history = new ArrayList<>();
	    private int hpos = -1;
	    private String hcurrent;

	    private UserEntry(int w) {
		super(w, "");
                background=new java.awt.Color(38,29,24);
		history.addAll(Utils.getprefsl("saved-tokens@" + confname, new String[] {}));
	    }

	    protected void changed() {
		checktoken();
		savetoken.set(token != null);
	    }

	    public void settext2(String text) {
		rsettext(text);
		changed();
	    }

	    public boolean keydown(KeyDownEvent ev) {
		if(ConsoleHost.kb_histprev.key().match(ev)) {
		    if(hpos < history.size() - 1) {
			if(hpos < 0)
			    hcurrent = text();
			settext2(history.get(++hpos));
		    }
		} else if(ConsoleHost.kb_histnext.key().match(ev)) {
		    if(hpos >= 0) {
			if(--hpos < 0)
			    settext2(hcurrent);
			else
			    settext2(history.get(hpos));
		    }
		} else {
		    return(super.keydown(ev));
		}
		return(true);
	    }

	    public void init(String name) {
		history.remove(name);
		settext2(name);
	    }
	}

	private Credbox() {
	    super(UI.scale(200, 150));
	    setfocustab(true);
	    Widget prev = add(new Label("User name", textf), 0, 0);
	    add(user = new UserEntry(this.sz.x), prev.pos("bl").adds(0, 1));
	    setfocus(user);

	    /* The message is a child of the box itself, not of the password box: with a saved login the password box is hidden, and the
	     * reason the login failed (for example, an unreachable server) has to show there as well. */
	    notice = add(new Label("", textfs), user.pos("bl").adds(0, 4));
            notice.setcolor(UI.ErrorMessage.color);
            notice.hide();
	    add(pwbox = new Widget(Coord.z), user.pos("bl").adds(0, 10));
            pwbox.add(prev = passwordLabel = new Label("Password", textf), Coord.z);
	    pwbox.add(pass = new TextEntry(this.sz.x, ""), prev.pos("bl").adds(0, 1)).pw = true;
            pass.background=new java.awt.Color(38,29,24);
	    pwbox.add(savetoken = new CheckBox("Remember me", true), pass.pos("bl").adds(0, 10));
	    savetoken.setgkey(kb_savtoken);
	    savetoken.settip("Saving your login does not save your password, but rather " +
			     "a randomly generated token that will be used to log in. " +
			     "Manage saved logins on the website: Account security > Saved client logins.",
			     true);
	    pwbox.pack();
	    pwbox.hide();

	    add(tkbox = new Widget(new Coord(this.sz.x, 0)), user.pos("bl").adds(0, 10));
	    tkbox.add(prev = new Label("Login saved", textfs), UI.scale(0, 25));
            tkbox.add(fbtn = new Button(this.sz.x, "Forget me"), prev.pos("bl").adds(0, 10)).action(this::forget);
	    fbtn.setgkey(kb_deltoken);
	    tkbox.pack();
	    tkbox.hide();

	    adda(exec = new IButton("gfx/hud/buttons/login", "u", "d", "o") {
		    protected void depress() {ui.sfx(Button.clbtdown.stream());}
		    protected void unpress() {ui.sfx(Button.clbtup.stream());}
		    public void click() {enter();}
		},
		pos("cmid").y(Math.max(pwbox.pos("bl").y, tkbox.pos("bl").y)).adds(0, 35), 0.5, 0.0);
	    pack();
	}

        private void notice(String message) {
            if(message == null && !notice.visible) return;
            notice.text.dispose();
            notice.texts = message == null ? "" : message;
            notice.text = textfs.renderwrap(notice.texts, notice.col, UI.scale(200));
            notice.resize(notice.text.sz());
            notice.show(message != null);
            arrange();
        }

        /** The password or the saved-login box and the button go under the message, when there is one. */
        private void arrange() {
            int top = user.pos("bl").y + UI.scale(10);
            if(notice.visible) {
                notice.move(Coord.of(0, user.pos("bl").y + UI.scale(4)));
                top = notice.c.y + notice.sz.y + UI.scale(8);
            }
            pwbox.move(Coord.of(0, top));
            tkbox.move(Coord.of(0, top));
            exec.move(Coord.of((UI.scale(200) - exec.sz.x) / 2,
                Math.max(pwbox.pos("bl").y, tkbox.pos("bl").y) + UI.scale(35)));
            pack();
            LoginScreen.this.layoutForm();
        }

	private void init() {
	    if(inited)
		return;
	    inited = true;
	    user.init(getpref("loginname", ""));
	}

	private void checktoken() {
	    if(this.token != null) {
		Arrays.fill(this.token, (byte)0);
		this.token = null;
	    }
	    byte[] token = Bootstrap.gettoken(user.text(), confname);
	    if(token == null) {
		tkbox.hide();
		pwbox.show();
	    } else {
		tkbox.show();
		pwbox.hide();
		this.token = token;
	    }
	}

	private void forget() {
	    String nm = user.text();
	    Bootstrap.settoken(nm, confname, null);
	    savetoken.set(false);
	    checktoken();
	}

	private void enter() {
            if(!accepting) return;
	    if(user.text().equals("")) {
		setfocus(user);
	    } else if(pwbox.visible && pass.text().equals("")) {
		setfocus(pass);
	    } else {
		AuthClient.Credentials credentials = creds();
                boolean save = pwbox.visible && savetoken.state();
                LoginScreen.this.error(null);
                LoginScreen.this.wdgmsg("login", credentials, save);
	    }
	}

	private AuthClient.Credentials creds() {
	    byte[] token = this.token;
	    AuthClient.Credentials ret;
	    if(token != null) {
		ret = new AuthClient.TokenCred(user.text(), Arrays.copyOf(token, token.length));
	    } else {
		String pw = pass.text();
		ret = null;
		parse: if(pw.length() == 64) {
		    byte[] ptok;
		    try {
			ptok = Utils.hex.dec(pw);
		    } catch(IllegalArgumentException e) {
			break parse;
		    }
		    ret = new AuthClient.TokenCred(user.text(), ptok);
		}
		if(ret == null)
		    ret = new AuthClient.NativeCred(user.text(), pw);
		pass.rsettext("");
	    }
	    return(ret);
	}

	public boolean keydown(KeyDownEvent ev) {
	    if(key_act.match(ev)) {
		enter();
		return(true);
	    }
	    return(super.keydown(ev));
	}

	public void show() {
	    if(!inited)
		init();
	    super.show();
	    checktoken();
	    if(pwbox.visible && !user.text().equals(""))
		setfocus(pass);
	}
    }

    private static boolean steam_autologin = true;
    public class Steambox extends Widget {
	private Steambox() {
	    super(UI.scale(200, 150));
	    Widget prev = adda(new Label("Logging in with Steam", textf), sz.x / 2, 0, 0.5, 0);
	    adda(new IButton("gfx/hud/buttons/login", "u", "d", "o") {
		    protected void depress() {ui.sfx(Button.clbtdown.stream());}
		    protected void unpress() {ui.sfx(Button.clbtup.stream());}
		    public void click() {enter();}
		},
		prev.pos("bl").adds(0, 10).x(sz.x / 2), 0.5, 0.0)
		.setgkey(key_act);
	}

	private AuthClient.Credentials creds() throws java.io.IOException {
	    return(new SteamCreds());
	}

	private void enter() {
            if(!accepting) return;
	    try {
		LoginScreen.this.wdgmsg("login", creds(), false);
	    } catch(java.io.IOException e) {
		error(e.getMessage());
	    }
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(steam_autologin) {
		enter();
		steam_autologin = false;
	    }
	}
    }

    public static class StatusLabel extends Widget {
	public final HttpStatus stat;
	public final double ax;

	public StatusLabel(URI svc, double ax) {
	    super(new Coord(UI.scale(240), Text.std.height() * 2));
	    this.stat = new HttpStatus(svc);
	    this.ax = ax;
	}

	private Text[] lines = new Text[2];
	public void draw(GOut g) {
	    String[] buf = {null, null};
	    synchronized(stat) {
		if(!stat.syn || (stat.status == "")) {
		} else if("up".equals(stat.status)) {
		    buf[0] = hearthborn.L10n.tr("Server status: Up");
		    buf[1] = hearthborn.L10n.format("Hearthlings playing: %,d", stat.users);
		} else if("shutdown".equals(stat.status)) {
		    buf[0] = hearthborn.L10n.tr("Server status: Down");
		} else if("terminating".equals(stat.status)) {
		    buf[0] = hearthborn.L10n.tr("Server status: Shutting down");
		} else if("crashed".equals(stat.status)) {
		    buf[0] = hearthborn.L10n.tr("Server status: Crashed");
		}
	    }
	    for(int i = 0, y = 0; i < 2; i++) {
		if((lines[i] != null) && !Utils.eq(buf[i], lines[i].text)) {
		    lines[i].dispose();
		    lines[i] = null;
		}
		if((lines[i] == null) && (buf[i] != null))
		    lines[i] = Text.render(buf[i]);
		if(lines[i] != null) {
		    g.image(lines[i].tex(), Coord.of((int)((sz.x - lines[i].sz().x) * ax), y));
		    y += lines[i].sz().y;
		}
	    }
	}

	protected void added() {
	    stat.start();
	}

	public void dispose() {
	    stat.quit();
	}
    }

    /** Align with the login artwork rather than a game-scaled absolute Y coordinate. */
    private int loginAnchorY() {return (int)Math.round(sz.y*.60);}
    private void showLogin() {
        if(accounts == null) { login.show(); return; }
        accounts.refresh();
        boolean saved = accounts.count() > 0;
        boolean showAccounts = saved && !formMode;
        login.show(!showAccounts);
        chooser.show(showAccounts);
        back.show(!showAccounts && saved);
        int rows = Math.max(1, Math.min(accounts.count(), 5));
        accounts.resize(Coord.of(UI.scale(235), rows * accounts.itemh));
        chooser.lchild.move(Coord.of(0, accounts.sz.y + UI.scale(10)));
        chooser.pack();
        int chooserY = Math.max(UI.scale(10), Math.min(loginAnchorY(), sz.y - UI.scale(80) - chooser.sz.y));
        chooser.move(Coord.of((sz.x - chooser.sz.x) / 2, chooserY));
        layoutForm();
    }

    private void layoutForm() {
        int footer = back != null && back.visible ? back.sz.y + UI.scale(8) : 0;
        int y = Math.min(loginAnchorY(), sz.y - UI.scale(80) - login.sz.y - footer);
        login.move(Coord.of((sz.x - login.sz.x) / 2, Math.max(UI.scale(10), y)));
        if(back != null) back.move(Coord.of(login.c.x, login.c.y + login.sz.y + UI.scale(8)));
        placeSteam();
    }

    private void mklogin() {
        accepting = true;
        showLogin();
        progress(null);
        placeSteam();
    }

    /** Whether the reason of a failed login is on the screen: in the form, with a saved login as well as with a password box. */
    public boolean errorShown() {
        if(login instanceof Credbox) {
            Credbox credentials = (Credbox)login;
            if(credentials.notice.texts.length() == 0) return false;
            for(Widget w = credentials.notice; w != null && w != this; w = w.parent)
                if(!w.visible) return false;
            return true;
        }
        return error != null;
    }

    private void error(String message) {
        lastError = message;
        if(this.error != null) { this.error.dispose(); this.error = null; }
        if(login instanceof Credbox) {
            Credbox credentials = (Credbox)login;
            if(message != null) { formMode = true; credentials.init(); credentials.checktoken(); }
            credentials.notice(message == null ? null : hearthborn.L10n.text("msg", message));
        } else if(message != null) {
            this.error = textfs.renderwrap(hearthborn.L10n.text("msg", message), java.awt.Color.RED, UI.scale(320));
        }
    }

    private void progress(String p) {
        lastProgress = p;
	if(progress != null) {
            progress.dispose();
	    progress = null;
	}
        cancel.hide();
        if(steam != null) steam.hide();
	if(p != null) {
	    progress = textf.render(hearthborn.L10n.text("msg", p), java.awt.Color.WHITE);
            Coord position = progressPosition();
            cancel.move(position.add(-cancel.sz.x / 2, progress.sz().y + UI.scale(8)));
	    cancel.show();
	}
    }

    private Coord progressPosition() {
        Widget controls = chooser != null && chooser.visible ? chooser : login;
        int bottom = controls.c.y + controls.sz.y;
        if(back != null && back.visible) bottom = Math.max(bottom, back.c.y + back.sz.y);
        return Coord.of(sz.x / 2, bottom + UI.scale(12));
    }

    private void clear() {
        accepting = false;
        if(!login.visible && (chooser == null || !chooser.visible)) showLogin();
	progress(null);
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender == optbtn) {
	    if(opts == null) {
		opts = adda(new OptWnd(false) {
			public void hide() {
			    /* XXX */
			    reqdestroy();
			}
		    }, sz.div(2), 0.5, 0.5);
	    } else {
		opts.reqdestroy();
		opts = null;
	    }
	    return;
	} else if(sender == opts) {
	    opts.reqdestroy();
	    opts = null;
	}
	super.wdgmsg(sender, msg, args);
    }

    public void cdestroy(Widget ch) {
	if(ch == opts) {
	    opts = null;
	}
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "login") {
	    mklogin();
	} else if(msg == "error") {
	    error((String)args[0]);
	} else if(msg == "prg") {
	    error(null);
	    clear();
	    progress((String)args[0]);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public void presize() {
	c = Coord.z;
	if(parent != null)
	    resize(parent.sz);
	placeChrome();
	if(login != null && (login.visible || (chooser != null && chooser.visible)))
	    showLogin();
	if(progress != null)
	    progress(lastProgress);
    }

    private void placeChrome() {
        Area artwork=hearthborn.ui.LoginArt.bounds(sz);
        if(closeClient!=null)closeClient.move(hearthborn.ui.LoginControls.position(sz,closeClient.sz));
	if(optbtn != null)
	    optbtn.move(Coord.of(artwork.ul.x + UI.scale(10), artwork.br.y - optbtn.sz.y - UI.scale(10)));
	if(language != null && optbtn != null)
	    language.move(Coord.of(optbtn.c.x, optbtn.c.y - language.sz.y - UI.scale(5)));
	if(status != null)
	    status.move(Coord.of(artwork.br.x - status.sz.x - UI.scale(10), artwork.ul.y + UI.scale(10)));
    }

    protected void added() {
	presize();
	parent.setfocus(this);
    }

    public void draw(GOut g) {
	hearthborn.ui.LoginArt.draw(g, sz);
	super.draw(g);
	if(error != null)
	    g.aimage(error.tex(), login.c.add(login.sz.x / 2, login.sz.y + UI.scale(5)), 0.5, 0.0);
	if(progress != null)
	    g.aimage(progress.tex(), progressPosition(), 0.5, 0.0);
    }
}
