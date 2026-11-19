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

package hearthborn.ui;

import haven.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Saved account names only; credentials remain in the native token store.
 *
 * @author Jagy2014
 */
public final class SavedLogins extends SListBox<String, Widget> {
    private static final Text.Furnace CAPTION = new PUtils.BlurFurn(
        new PUtils.TexFurn(new Text.Foundry(Text.serif.deriveFont(java.awt.Font.BOLD, UI.scale(12f))).aa(true), Window.ctex),
        UI.rscale(0.75), UI.rscale(0.75), new java.awt.Color(80, 40, 0));
    private final String server;
    private final Consumer<String> select, forget;
    private List<String> names = Collections.emptyList();
    private double elapsed;
    private JournalScrollport.Grip scroll;

    public SavedLogins(String server, Consumer<String> select, Consumer<String> forget) {
        super(UI.scale(235, 280), UI.scale(30));
        this.server = server; this.select = select; this.forget = forget;
        scroll=add(new JournalScrollport.Grip(sz.y){
            public void changed(){SavedLogins.this.scrollval(val);}
        });
        scroll.move(Coord.of(sz.x,0));
        refresh();
    }
    protected boolean autoscroll(){return false;}
    public void resize(Coord size){
        super.resize(size);
        if(scroll!=null){scroll.resize(size.y);scroll.move(Coord.of(size.x,0));}
    }
    public void update(){
        super.update();
        if(scroll!=null){scroll.min=scrollmin();scroll.max=count()>5?Math.max(scroll.min,scrollmax()):scroll.min;if(count()<=5)scrollval(0);scroll.val=scrollval();scroll.show(count()>5&&scroll.max>scroll.min);}
    }

    public boolean mousewheel(MouseWheelEvent event){return count()>5&&super.mousewheel(event);}

    public void refresh() {
        List<String> saved = new ArrayList<>(new LinkedHashSet<>(Utils.getprefsl("saved-tokens@" + server, new String[0])));
        if(!saved.equals(names)) { names = saved; reset(); }
    }
    protected void drawbg(GOut g,String item,int index,Area area) {}
    protected void drawsel(GOut g,String item,int index,Area area) {}
    public int count() { return names.size(); }
    protected List<? extends String> items() { return names; }
    protected Widget makeitem(String name, int index, Coord size) {
        Widget row = new Widget(size);
        Button login = row.add(new Button(UI.scale(200), CAPTION.render(name)).action(() -> select.accept(name)), Coord.z);
        Button remove = new Button(UI.scale(20), CAPTION.render("X")).action(() -> { forget.accept(name); refresh(); });
        row.add(remove, new Coord(UI.scale(205), UI.scale(5)));
        return row;
    }
    public void tick(double dt) {
        super.tick(dt);
        if((elapsed += dt) >= 0.5) { elapsed = 0; refresh(); }
    }
}
