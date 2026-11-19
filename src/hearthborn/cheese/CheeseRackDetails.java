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

package hearthborn.cheese;
import haven.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;



import java.awt.Color;
import java.util.*;
import java.util.List;

/**
 * Details aligned with the trays in an open rack, away from the world labels.
 *
 * @author Jagy2014
 */
public class CheeseRackDetails extends Widget {
    private static final int WIDTH = UI.scale(230);
    private static final int PAD = UI.scale(8);
    private final Window window;
    private final Gob gob;
    private final Button location;
    private final Map<WItem, DetailsRow> rows = new LinkedHashMap<>();
    private double refresh;

    public CheeseRackDetails(Window window, Gob gob) {
        this.gob=gob;
	this.window = window;
	location = add(new Button(UI.scale(210), "", false, () -> {
	    CheeseAging.cycleLocation(gob);
	    refresh = 0;
	}));
	location.tooltip = L10n.tr("The location setting only affects the helper’s tooltips. It does not change which cheese is produced.");
    }

    @Override
    public void tick(double dt) {
	super.tick(dt);
	refresh -= dt;
	if(refresh > 0)
	    return;
	refresh = .5;
	List<WItem> items = new ArrayList<>(window.children(WItem.class));
	items.sort(Comparator.comparingInt((WItem w) -> w.parentpos(window).y)
	    .thenComparingInt(w -> w.parentpos(window).x));
	Set<WItem> empty = Collections.newSetFromMap(new IdentityHashMap<>());
        for(WItem item:items)try{if(CheeseAging.emptyTray(item.item))empty.add(item);}catch(Loading ignored){}
        boolean layoutChanged = false;
	for(WItem previous : new ArrayList<>(rows.keySet())) {
	    if(!items.contains(previous) || empty.contains(previous)) {
		rows.remove(previous).destroy();
		layoutChanged = true;
	    }
	}
	Coord offset = window.xlate(Coord.z, true);
	List<String> stages = gob == null ? Collections.emptyList() : CheeseAging.traysFromOverlay(gob);
	int right = 0, top = Integer.MAX_VALUE, bottom = 0;
	for(WItem item : items) {
            if(empty.contains(item))continue;
	    Inventory inventory = item.getparent(Inventory.class);
	    if(inventory == null)
		continue;
	    Coord pos = item.parentpos(window).sub(offset);
	    right = Math.max(right, inventory.parentpos(window).sub(offset).x + inventory.sz.x);
	    top = Math.min(top, pos.y);
	    bottom = Math.max(bottom, pos.y + item.sz.y);
	}
	if(top == Integer.MAX_VALUE) {
	    if(visible) {hide(); window.pack();}
	    return;
	}
        boolean allReady=true;
	for(int i = 0; i < items.size(); i++) {
	    WItem item = items.get(i);
            if(empty.contains(item))continue;
	    DetailsRow row = rows.get(item);
	    if(row == null) {
		row = add(new DetailsRow());
		rows.put(item, row);
		layoutChanged = true;
	    }
	    Coord pos = item.parentpos(window).sub(offset);
	    row.c = new Coord(PAD, pos.y - top + Math.max(0, (item.sz.y - row.sz.y) / 2));
	    try {
		String kind = CheeseAging.itemKind(item.item);
		String title = kind == null ? CheeseAging.concise("Cheese contents unknown","Unknown contents")
		    : CheeseAging.localizeName(CheeseAging.trayLabel(kind));
		CheeseAging.State state = CheeseAging.itemState(item.item, GameObjects.meter(item.item));
                if(state!=CheeseAging.State.READY)allReady=false;
		String stage = i < stages.size() && stages.get(i).matches("(?i)L[1-4]")
		    ? stages.get(i).toUpperCase(Locale.ROOT) : "";
		String next = state != CheeseAging.State.AGING || kind == null || gob == null ? null : CheeseAging.next(kind, CheeseAging.loc(gob));
		String location = gob == null || state==CheeseAging.State.READY ? "" : " · " + CheeseAging.locationPhrase(CheeseAging.loc(gob));
                Coord previousSize=row.sz;
		row.update(title, CheeseAging.productLabel(kind, stage) + " · " + CheeseAging.stateLabel(state) + location, state,
		    next == null ? null : CheeseAging.localizeName(next),kind);
                if(!previousSize.equals(row.sz))layoutChanged=true;
                row.future(kind,next,gob==null?null:CheeseAging.loc(gob),state==CheeseAging.State.AGING);
	    } catch(Loading loading) {
                allReady=false;
		row.update(CheeseAging.concise("Cheese contents unknown","Unknown contents"), CheeseAging.stateLabel(CheeseAging.State.UNKNOWN), CheeseAging.State.UNKNOWN, null,null);
                row.future(null,null,null,false);
	    }
	}
	Coord position = new Coord(right + UI.scale(8), top);
	CheeseAging.Loc selected = CheeseAging.selectedLocation(gob);
	String locationText = CheeseAging.concise("Cheese location selector","Location") + ": "
	    + (selected == null ? CheeseAging.concise("Cheese location auto","Auto") : CheeseAging.locLabel(selected));
	if(!locationText.equals(location.text.text))
	    location.change(locationText);
        if(location.visible==allReady)layoutChanged=true;
        location.show(!allReady);
	location.c = new Coord(PAD, bottom - top + PAD);
	int width = WIDTH;
	for(DetailsRow row : rows.values())
	    width = Math.max(width, row.sz.x + 2 * PAD);
	Coord size = new Coord(width, bottom - top + (allReady?0:location.sz.y+PAD) + PAD);
	if(!c.equals(position) || !sz.equals(size) || !visible)
	    layoutChanged = true;
	c = position;
	resize(size);
	show();
	if(layoutChanged)
	    window.pack();
    }

    @Override
    public void draw(GOut g) {
	g.chcolor(new Color(0, 0, 0, 235));
	g.frect(Coord.z, sz);
	g.chcolor(new Color(100, 90, 60, 160));
	g.rect(Coord.z, sz);
	g.chcolor();
	super.draw(g);
    }

    private static class DetailsRow extends Widget {
        private CheeseFutureTip future;
        private String futureKey;
        void future(String kind,String next,CheeseAging.Loc loc,boolean aging){
            String key=kind+":"+next+":"+loc+":"+aging+":"+L10n.language();
            if(!key.equals(futureKey)){if(future!=null)future.dispose();future=kind==null?null:new CheeseFutureTip(kind,next,loc,aging);futureKey=key;}
            if(future!=null&&!future.empty()){title.tooltip=future;stats.tooltip=future;}
        }
        public void dispose(){if(future!=null)future.dispose();super.dispose();}
	private final Label title = add(new Label(""), new Coord(0, UI.scale(18)));
	private final StatusLine status = add(new StatusLine());
        private final Stats stats=add(new Stats());

	DetailsRow() {
	    super(new Coord(WIDTH - 2 * PAD, UI.scale(36)));
	}

	void update(String name, String state, CheeseAging.State value, String next,String kind) {
	    String caption = name + (next == null ? "" : " → " + next);
	    title.settext(caption);
	    title.tooltip = name + (next == null ? "" : " → " + next);
	    status.update(state,value==CheeseAging.State.READY);
	    status.tooltip = state;
            stats.update(value==CheeseAging.State.READY?kind:null);
            stats.move(title.c.add(title.sz.x+UI.scale(8),0));
	    resize(new Coord(Math.max(WIDTH - 2 * PAD, Math.max(title.sz.x+(stats.sz.x>0?UI.scale(8)+stats.sz.x:0), status.sz.x)), UI.scale(36)));
	    title.setcolor(value == CheeseAging.State.READY ? new Color(150, 255, 130)
		: value == CheeseAging.State.AGING ? new Color(255, 230, 150) : new Color(190, 190, 190));
	}
    }
    private static final class StatusLine extends Widget{
        private Tex texture;private String shown;
        void update(String value,boolean ready){
            String text=RichText.Parser.quote(value);
            if(ready){String word=RichText.Parser.quote(CheeseAging.stateLabel(CheeseAging.State.READY));text=text.replace(word,"$col[150,255,130]{"+word+"}");}
            if(Objects.equals(shown,text))return;
            if(texture!=null)texture.dispose();shown=text;texture=RichText.render(text,0).tex();resize(texture.sz());
        }
        public void draw(GOut g){if(texture!=null)g.image(texture,Coord.z);}
        public void dispose(){if(texture!=null)texture.dispose();super.dispose();}
    }
    static String statText(String kind){
        StringBuilder text=new StringBuilder();
        for(CheeseAging.Fep stat:CheeseAging.feps(kind)){
            if(text.length()>0)text.append("  ");
            String value=stat.base().toUpperCase(Locale.ROOT)+(stat.plus2()?"+2":"")+" – "+stat.a;
            text.append("$img[").append(stat.statRes()[0]).append(",c,h=12] ");
            if(stat.plus2())text.append("$col[255,215,0]{").append(value).append("}");else text.append(value);
        }
        return text.toString();
    }
    private static final class Stats extends Widget{
        private Tex texture;private String shown;
        void update(String kind){
            String next=kind==null?"":statText(kind);
            if(Objects.equals(next,shown))return;
            if(texture!=null){texture.dispose();texture=null;}resize(Coord.z);
            if(next.isEmpty()){shown=next;return;}
            try{
                texture=RichText.render(next,0,RichText.IMAGESRC,RichText.ImageSource.res(Resource.remote())).tex();
                shown=next;resize(texture.sz());
            }catch(Loading pending){shown=null;}
        }
        public void draw(GOut g){if(texture!=null)g.image(texture,Coord.z);}
        public void dispose(){if(texture!=null)texture.dispose();super.dispose();}
    }

}
