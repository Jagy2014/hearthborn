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

package hearthborn.planner;

import haven.*;
import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A small pill with a colored square that turns one kind of object on the plan on or off.
 *
 * @author Jagy2014
 */
final class PlanChip extends Widget {
    final String title;
    private final Color color;
    private final BooleanSupplier on;
    private final Consumer<Boolean> set;
    private final Tex lit,dim;
    private boolean hover;
    PlanChip(String title,Color color,BooleanSupplier on,Consumer<Boolean> set){
        super(Coord.z);
        this.title=title;this.color=color;this.on=on;this.set=set;
        lit=Text.render(title,new Color(238,220,175)).tex();dim=Text.render(title,new Color(130,124,108)).tex();
        resize(Coord.of(lit.sz().x+UI.scale(30),UI.scale(22)));
    }
    boolean on(){return on.getAsBoolean();}
    void toggle(){set.accept(!on());}
    private void dashed(GOut g,Coord from,Coord to){
        int step=UI.scale(3);
        if(from.y==to.y)for(int x=from.x;x<to.x;x+=step*2)g.frect(Coord.of(x,from.y),Coord.of(Math.min(step,to.x-x),1));
        else for(int y=from.y;y<to.y;y+=step*2)g.frect(Coord.of(from.x,y),Coord.of(1,Math.min(step,to.y-y)));
    }
    public void draw(GOut g){
        boolean shown=on();
        g.chcolor(shown?(hover?new Color(54,47,36):new Color(40,35,28)):new Color(26,25,22));g.frect(Coord.z,sz);
        if(shown){
            g.chcolor(hover?new Color(240,200,105):new Color(176,141,70));
            g.rect(Coord.z,sz);
        }else{
            g.chcolor(hover?new Color(150,142,120):new Color(94,90,80));
            dashed(g,Coord.z,Coord.of(sz.x,0));dashed(g,Coord.of(0,sz.y-1),Coord.of(sz.x,sz.y-1));
            dashed(g,Coord.z,Coord.of(0,sz.y));dashed(g,Coord.of(sz.x-1,0),Coord.of(sz.x-1,sz.y));
        }
        int box=UI.scale(9);Coord at=Coord.of(UI.scale(8),(sz.y-box)/2);
        if(shown){g.chcolor(color);g.frect(at,Coord.of(box,box));}
        else{g.chcolor(new Color(100,96,86));g.rect(at,Coord.of(box,box));}
        g.chcolor();
        g.aimage(shown?lit:dim,Coord.of(UI.scale(22),sz.y/2),0,.5);
    }
    public boolean mousedown(MouseDownEvent ev){
        if(ev.b!=1)return false;
        toggle();return true;
    }
    public boolean mousehover(MouseHoverEvent ev,boolean over){hover=over;return super.mousehover(ev,over);}
    public Object tooltip(Coord c,Widget prev){return hearthborn.L10n.tr("Show or hide on the map");}
    public void dispose(){lit.dispose();dim.dispose();super.dispose();}
}
