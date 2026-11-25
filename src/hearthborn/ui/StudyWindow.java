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

/**
 * @author Jagy2014
 */
public final class StudyWindow extends GameUI.Hidewnd {
    private final Widget study;
    protected Deco makedeco(){return new SmallWindowDeco(large).translucentDark();}

    public StudyWindow(Widget study) {
        super(Coord.z, "Study");
        this.study = study;
        Widget proxy = add(new Proxy(study), Coord.z);
        SAttrWnd attributes=study.getparent(SAttrWnd.class);
        Label experience=StudyReport.big(new Label("Total experience points:")),learning=StudyReport.big(new Label("Total learning points:"));
        int width=Math.max(study.sz.x,Math.max(experience.sz.x,learning.sz.x)+StudyReport.big(new Label("999,999")).sz.x+UI.scale(12));
        int row=StudyReport.rowHeight();
        width=Math.max(width,UI.scale(185));
        Widget info=add(new SAttrWnd.StudyInfo(Coord.of(width, UI.scale(115)), study),
            Coord.of(0,proxy.sz.y+UI.scale(8)));
        int y=UI.scale(2)+row*4;
        CheckBox lock=info.getchild(CheckBox.class);
        lock.move(Coord.of(UI.scale(2),y+row*2+UI.scale(8)));
        info.resize(Coord.of(width,lock.c.y+lock.sz.y+UI.scale(3)));
        info.add(experience,Coord.of(UI.scale(2),y));
        info.add(learning,Coord.of(UI.scale(2),y+row));
        if(attributes!=null) {
            int right=width-UI.scale(4);
            info.adda(StudyReport.big(attributes.enclabel()),Coord.of(right,y),1,0);
            info.adda(StudyReport.big(attributes.explabel()),Coord.of(right,y+row),1,0);
        }
        pack();
    }
    public boolean shows(Widget study) { return this.study == study; }
    public static class Proxy extends Widget implements DTarget {
        public final Widget target;
        private WItem hovered;
        public Proxy(Widget target) {super(target.sz); this.target=target;}
        public void draw(GOut g) {target.draw(g);}
        public boolean mousedown(MouseDownEvent event) {return event.dispatch(target);}
        public boolean mouseup(MouseUpEvent event) {return event.dispatch(target);}
        public boolean mousewheel(MouseWheelEvent event) {return event.dispatch(target);}
        public void mousemove(MouseMoveEvent event) {event.dispatch(target);}
        public Object tooltip(Coord c,Widget previous) {
            for(WItem item : target.children(WItem.class)) {
                if(!c.isect(item.c, item.sz)) continue;
                boolean sameItem = previous == this && hovered == item;
                hovered = item;
                return item.tooltip(c.sub(item.c), sameItem ? item : null);
            }
            hovered = null;
            return null;
        }
        public boolean drop(Coord c,Coord ul) {return target instanceof DTarget && ((DTarget)target).drop(c,ul);}
        public boolean iteminteract(Coord c,Coord ul) {return target instanceof DTarget && ((DTarget)target).iteminteract(c,ul);}
    }
}
