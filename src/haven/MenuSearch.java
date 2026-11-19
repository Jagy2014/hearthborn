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
import java.awt.image.BufferedImage;
import haven.MenuGrid.Pagina;
import haven.MenuGrid.PagButton;

public abstract class MenuSearch extends Window {
    public final MenuGrid menu;
    public final Results rls;
    public final TextEntry sbox;
    protected List<Result> cur = Collections.emptyList();
    protected List<Result> filtered = Collections.emptyList();
    private boolean recons = true;
    private double retryAt;

    public static String displayName(PagButton button) {
        return hearthborn.L10n.resource("action",button.res.name,button.name());
    }

    public class Result {
	public final PagButton btn;

	protected Result(PagButton btn) {
	    this.btn = btn;
	}
    }

    public static final Text.Foundry elf = CharWnd.attrf;
    public static final int elh = elf.height() + UI.scale(2);
    public class Results extends SListBox<Result, Widget> {
	private Results(Coord sz) {
	    super(sz, rowHeight());
	}

	protected List<Result> items() {return(filtered);}
	protected void drawsel(GOut g, Result item, int idx, Area area) {drawSelection(g, area);}
        public void change(Result item) {super.change(item);selectionChanged(item);}


	protected Widget makeitem(Result el, int idx, Coord sz) {
	    return(new ItemWidget<Result>(this, sz, el) {
		    {
			Widget content = rowContent(el, sz);
			if(content != null) {
			    add(content, Coord.z);
			} else {
			    add(new IconText(sz) {
				    protected BufferedImage img() {return(item.btn.img());}
				    protected String text() {return(displayName(el.btn));}
				    protected int margin() {return(0);}
				    protected Text.Foundry foundry() {return(elf);}
				}, Coord.z);
			}
		    }

                    private UI.Grab grab;
                    private Coord pressed;
                    private boolean dragging;
                    public void mousemove(MouseMoveEvent ev) {
                        if(pressed!=null && ev.c.dist(pressed)>UI.scale(5))dragging=true;
                    }
                    public boolean mouseup(MouseUpEvent ev) {
                        if(ev.b==1 && grab!=null) {
                            grab.remove();grab=null;pressed=null;
                            if(dragging)DropTarget.dropthing(ui.root,ui.mc,item.btn.pag);
                            dragging=false;return true;
                        }
                        return super.mouseup(ev);
                    }
                    public void draw(GOut g) {
                        super.draw(g);
                        if(dragging)ui.drawafter(out->{try {item.btn.spr().draw(out.reclip(ui.mc.sub(item.btn.spr().sz().div(2)),item.btn.spr().sz()));}catch(Loading ignored){}});
                    }
                    public void dispose(){if(grab!=null)grab.remove();super.dispose();}
		    private double lastcl = 0;
		    public boolean mousedown(MouseDownEvent ev) {
                        if(ev.b==3&&menu.shortcuts!=null&&menu.shortcuts.favoriteMenu(item.btn,this,ev.c))return true;
                        if(ev.b!=1)return super.mousedown(ev);
                        pressed=ev.c;grab=ui.grabmouse(this);dragging=false;
			boolean psel = sel == item;
			super.mousedown(ev);
			double now = Utils.rtime();
			if(psel) {
			    if(now - lastcl < 0.5)
				execute(item);
			}
			lastcl = now;
			return(true);
		    }
		});
	}
    }

    public MenuSearch(String title, MenuGrid menu) {
	super(Coord.z, title);
	this.menu = menu;
	rls = add(new Results(UI.scale(250, 500)), Coord.z);
	sbox = add(new TextEntry(UI.scale(250), "") {
		protected void changed() {
		    refilter();
		}

		public void activate(String text) {
                    activateSelected();
		}
	    }, 0, rls.sz.y);
	pack();
    }

    public MenuSearch(MenuGrid menu) {
	this("Action search", menu);
    }

    protected void refilter() {
	List<Result> found = new ArrayList<>();
	String needle = sbox.text().toLowerCase(Locale.ROOT);
	for(Result res : this.cur) {
	    if(matches(res,needle))
		found.add(res);
	}
	/* The sort keys are worked out once per result, not at every comparison. */
	Map<Result, Integer> rank = new IdentityHashMap<>();
	Map<Result, String> names = new IdentityHashMap<>();
	for(Result res : found) {
	    rank.put(res, priority(res, needle));
	    names.put(res, displayName(res.btn));
	}
        found.sort(Comparator.comparingInt((Result result)->rank.get(result))
            .thenComparing(result->names.get(result),String.CASE_INSENSITIVE_ORDER));
	this.filtered = found;
	int idx = filtered.indexOf(rls.sel);
	if(idx < 0) {
            if(filtered.isEmpty())rls.change(null);
	    if(filtered.size() > 0) {
		rls.change(filtered.get(0));
		rls.display(0);
	    }
	} else {
	    rls.display(idx);
	}
    }

    protected int rowHeight(){return elh;}
    /** What a row shows instead of the plain icon and name; null keeps the plain row. */
    protected Widget rowContent(Result result, Coord size) {return(null);}
    protected void drawSelection(GOut g, Area area) {
	g.chcolor(255, 255, 0, 128);
	g.frect2(area.ul, area.br);
	g.chcolor();
    }
    protected void selectionChanged(Result result){}
    protected boolean matches(Result result,String needle) {
        return result.btn.name().toLowerCase(Locale.ROOT).contains(needle)
            || displayName(result.btn).toLowerCase(Locale.ROOT).contains(needle);
    }
    protected int priority(Result result,String needle){return 0;}
    protected void execute(Result result){menu.use(result.btn,new MenuGrid.Interaction(1,ui.modflags()),false);}
    protected void activateSelected(){
        if(rls.sel!=null)execute(rls.sel);
        if(!ui.modctrl){reqclose();sbox.settext("");refilter();}
    }

    public boolean collectButtons(List<PagButton> buttons){return generate(buttons);}
    protected abstract boolean generate(List<PagButton> buf);

    private int stamp = Integer.MIN_VALUE;
    /** Changes when something besides the list and the query changes how the list is ordered. */
    protected int refilterStamp() {return(0);}

    protected void updlist() {
	recons = false;
	List<PagButton> buf = new ArrayList<>();
	if(generate(buf)) {
	    /* Something is still loading: look again soon, not at every frame. */
	    recons = true;
	    retryAt = Utils.rtime() + 0.5;
	}
	Map<PagButton, Result> prev = new HashMap<>();
	for(Result pr : this.cur)
	    prev.put(pr.btn, pr);
	List<Result> results = new ArrayList<>();
	for(PagButton btn : buf) {
	    Result pr = prev.get(btn);
	    if(pr != null)
		results.add(pr);
	    else
		results.add(new Result(btn));
	}
	boolean same = results.size() == this.cur.size() && stamp == refilterStamp();
	for(int i = 0; same && i < results.size(); i++)
	    same = results.get(i) == this.cur.get(i);
	this.cur = results;
	stamp = refilterStamp();
	if(!same)
	    refilter();
    }

    protected void recons() {
	recons = true;
	retryAt = 0;
    }

    public void tick(TickEvent ev) {
	if(ev.visible && recons && Utils.rtime() >= retryAt) {
	    updlist();
	}
	super.tick(ev);
    }

    public boolean keydown(KeyDownEvent ev) {
	if(ev.code == ev.awt.VK_DOWN) {
	    int idx = filtered.indexOf(rls.sel);
	    if((idx >= 0) && (idx < filtered.size() - 1)) {
		idx++;
		rls.change(filtered.get(idx));
		rls.display(idx);
	    }
	    return(true);
	} else if(ev.code == ev.awt.VK_UP) {
	    int idx = filtered.indexOf(rls.sel);
	    if(idx > 0) {
		idx--;
		rls.change(filtered.get(idx));
		rls.display(idx);
	    }
	    return(true);
	} else {
	    return(super.keydown(ev));
	}
    }

    public static class Main extends MenuSearch {
	private Pagina root;
	private int pagseq;

	public static final KeyBinding kb_itemcraft = KeyBinding.get("scm-itemcraft", KeyMatch.nil);
	public Main(MenuGrid menu) {
	    super(menu);
	    setroot(null);
	    add(new Button(sbox.sz.x, "Search by ingredient", false).action(() -> menu.wdgmsg("act", "itemcraft")),
		sbox.pos("bl").adds(0, 5)).setgkey(kb_itemcraft);
	    pagseq = menu.pagseq;
	    pack();
	}

	protected boolean generate(List<PagButton> buf) {
	    boolean recons = false;
	    Pagina root = this.root;
	    Collection<Pagina> leaves = new ArrayList<>();
	    synchronized(menu.paginae) {
		leaves.addAll(menu.paginae);
	    }
	    for(Pagina pag : leaves) {
		try {
		    if(root == null) {
			buf.add(pag.button());
		    } else {
			for(Pagina parent = pag; parent != null; parent = parent.parent()) {
			    if(parent == root) {
				buf.add(pag.button());
				break;
			    }
			}
		    }
		} catch(Loading l) {
		    recons = true;
		}
	    }
	    Collections.sort(buf, Comparator.comparing(MenuSearch::displayName));
	    return(recons);
	}

	public void setroot(Pagina nr) {
	    root = nr;
	    recons();
	    rls.sb.val = 0;
	}

	public void tick(TickEvent ev) {
	    if(ev.visible) {
		if(menu.cur != root)
		    setroot(menu.cur);
		if(pagseq != menu.pagseq) {
		    recons();
		    pagseq = menu.pagseq;
		}
	    }
	    super.tick(ev);
	}
    }
}
