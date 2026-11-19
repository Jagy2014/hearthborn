package haven.res.ui.croster;

import haven.*;
import java.util.*;
import haven.MenuGrid.Pagina;

@haven.FromResource(name = "ui/croster", version = 78, override = true)
public abstract class CattleRoster<T extends Entry> extends Widget {
    public static final int WIDTH = UI.scale(900);
    public static final Comparator<Entry> namecmp = (a, b) -> a.name.compareTo(b.name);
    public static final int HEADH = UI.scale(40);
    public final Map<UID, T> entries = new HashMap<>();
    public final Scrollbar sb;
    public final Widget entrycont;
    public int entryseq = 0;
    public List<T> display = Collections.emptyList();
    public boolean dirty = true;
    public Comparator<? super T> order = namecmp;
    public Column<? super T> mousecol, ordercol;
    public boolean revorder;
    private final Button btnall, btnnone, btnrm;
    private final Set<UID> removalPending=new HashSet<>();

    public CattleRoster() {
	super(new Coord(WIDTH, UI.scale(280)));
	entrycont = add(new Widget(sz), 0, HEADH);
	sb = add(new Scrollbar(sz.y, 0, 0) {
		public void changed() {redisplay(display);}
	    }, sz.x, HEADH);
	btnall = add(new Button(UI.scale(100), "Select all", false).action(() -> {
		    for(Entry entry : this.entries.values())
			entry.mark.set(true);
		}), entrycont.pos("bl").adds(0, 5));
	btnnone = add(new Button(UI.scale(100), "Select none", false).action(() -> {
		    for(Entry entry : this.entries.values())
			entry.mark.set(false);
		}), btnall.pos("ur").adds(5, 0));
	btnrm = adda(new Button(UI.scale(150), "Remove selected", false).action(() -> {
	    Collection<Object> args = new ArrayList<>();
	    for(Entry entry : this.entries.values()) {
		if(entry.mark.a)
		    args.add(entry.id);
	    }
	    wdgmsg("rm", args.toArray(new Object[0]));
	}), entrycont.pos("br").adds(0, 5), 1, 0);
	layout();
    }

    private void layout() {
	int gap = UI.scale(5);
	int pad = UI.scale(6);
	btnall.c = new Coord(0, Math.max(HEADH, sz.y - btnall.sz.y - pad));
	btnnone.c = new Coord(btnall.sz.x + gap, btnall.c.y);
	btnrm.c = new Coord(0, Math.max(HEADH, btnall.c.y - gap - btnrm.sz.y));
	int listH = Math.max(0, btnrm.c.y - gap - HEADH);
	entrycont.resize(new Coord(sz.x, listH));
	entrycont.c = new Coord(0, HEADH);
	sb.resize(listH);
	sb.move(new Coord(sz.x, HEADH));
	dirty = true;
    }

    public void resize(Coord nsz) {
	super.resize(nsz);
	if(entrycont != null)
	    layout();
    }

    @SafeVarargs
    public static <E extends Entry> List<Column<? super E>> initcols(Column<? super E>... attrs) {
	for(int i = 0, x = CheckBox.sbox.sz().x + UI.scale(10); i < attrs.length; i++) {
	    Column<? super E> attr = attrs[i];
	    attr.x = x;
	    x += attr.w;
	    x += UI.scale(attr.r ? 5 : 1);
	}
	return(Arrays.asList(attrs));
    }

    public void redisplay(List<T> display) {
	Set<T> hide = new HashSet<>(entries.values());
	int h = 0, th = entrycont.sz.y;
	for(T entry : display)
	    h += entry.sz.y;
	sb.max = h - th;
	int y = -sb.val, idx = 0;
	for(T entry : display) {
	    entry.idx = idx++;
	    if((y + entry.sz.y > 0) && (y < th)) {
		entry.move(new Coord(0, y));
		entry.show();
	    } else {
		entry.hide();
	    }
	    hide.remove(entry);
	    y += entry.sz.y;
	}
	for(T entry : hide)
	    entry.hide();
	this.display = display;
    }

    public void tick(double dt) {
        if(hearthborn.ui.CattleCleanup.enabled()){
            List<Object> remove=new ArrayList<>();
            for(T entry:new ArrayList<>(entries.values())){
                if(hearthborn.ui.CattleCleanup.dead(entry)){
                    if(removalPending.add(entry.id))remove.add(entry.id);
                }else removalPending.remove(entry.id);
            }
            if(!remove.isEmpty())wdgmsg("rm",remove.toArray());
        }

	if(dirty) {
	    List<T> ndisp = new ArrayList<>(entries.values());
	    ndisp.sort(order);
	    redisplay(ndisp);
	    dirty = false;
	}
	super.tick(dt);
    }

    protected abstract List<Column<? super T>> cols();

    public void drawcols(GOut g) {
	int body = HEADH + ((entrycont != null) ? entrycont.sz.y : 0);
	Column prev = null;
	for(Column col : cols()) {
	    if((prev != null) && !prev.r) {
		g.chcolor(255, 255, 0, 64);
		int x = (prev.x + prev.w + col.x) / 2;
		g.line(new Coord(x, 0), new Coord(x, body), 1);
		g.chcolor();
	    }
	    if((col == mousecol) && (col.order != null)) {
		g.chcolor(255, 255, 0, 16);
		g.frect2(new Coord(col.x, 0), new Coord(col.x + col.w, body));
		g.chcolor();
	    }
	    if(col == ordercol) {
		g.chcolor(255, 255, 0, 16);
		g.frect2(new Coord(col.x, 0), new Coord(col.x + col.w, body));
		g.chcolor();
	    }
	    Tex head = col.head();
	    g.aimage(head, new Coord(col.x + (col.w / 2), HEADH / 2), 0.5, 0.5);
	    prev = col;
	}
    }

    public void draw(GOut g) {
	drawcols(g);
	super.draw(g);
    }

    public Column<? super T> onhead(Coord c) {
	if((c.y < 0) || (c.y >= HEADH))
	    return(null);
	for(Column<? super T> col : cols()) {
	    if((c.x >= col.x) && (c.x < col.x + col.w))
		return(col);
	}
	return(null);
    }

    public void mousemove(MouseMoveEvent ev) {
	super.mousemove(ev);
	mousecol = onhead(ev.c);
    }

    public boolean mousedown(MouseDownEvent ev) {
	Column<? super T> col = onhead(ev.c);
	if(ev.b == 1) {
	    if((col != null) && (col.order != null)) {
		revorder = (col == ordercol) ? !revorder : false;
		this.order = col.order;
		if(revorder)
		    this.order = this.order.reversed();
		ordercol = col;
		dirty = true;
		return(true);
	    }
	}
	return(super.mousedown(ev));
    }

    public boolean mousewheel(MouseWheelEvent ev) {
	sb.ch(ev.step() * UI.scale(15));
	return(true);
    }

    public Object tooltip(Coord c, Widget prev) {
	if(mousecol != null) {
	    if(mousecol.tip != null)
		return(hearthborn.L10n.text("tooltip", mousecol.tip));
	    return(mousecol.tip);
	}
	return(super.tooltip(c, prev));
    }

    public void addentry(T entry) {
	entries.put(entry.id, entry);
	entrycont.add(entry, Coord.z);
	dirty = true;
	entryseq++;
    }

    public void delentry(UID id) {
	T entry = entries.remove(id);
	entry.destroy();
	dirty = true;
	entryseq++;
    }

    public void delentry(T entry) {
	delentry(entry.id);
    }

    public abstract T parse(Object... args);

    public void uimsg(String msg, Object... args) {
	if(msg == "add") {
	    addentry(parse(args));
	} else if(msg == "upd") {
	    T entry = parse(args);
	    delentry(entry.id);
	    addentry(entry);
	} else if(msg == "rm") {
            removalPending.remove((UID)args[0]);
	    delentry((UID)args[0]);
	} else if(msg == "addto") {
	    GameUI gui = (GameUI)ui.getwidget(Utils.iv(args[0]));
	    Pagina pag = gui.menu.paginafor(ui.sess.getresv(args[1]));
	    RosterButton btn = (RosterButton)Loading.waitfor(pag::button);
	    btn.add(this);
	} else {
	    super.uimsg(msg, args);
	}
    }

    public abstract TypeButton button();

    public static TypeButton typebtn(Indir<Resource> up, Indir<Resource> dn) {
	Resource ur = Loading.waitfor(up);
	Resource.Image ui = ur.layer(Resource.imgc);
	Resource.Image di = Loading.waitfor(dn).layer(Resource.imgc);
	TypeButton ret = new TypeButton(ui.scaled(), di.scaled(), ui.z);
	Resource.Tooltip tip = ur.layer(Resource.tooltip);
	if(tip != null)
	    ret.settip(tip.t);
	return(ret);
    }
}
