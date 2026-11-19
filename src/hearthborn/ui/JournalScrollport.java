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
import java.awt.Color;

/**
 * A quiet gold scroll thumb, shared by the journal HUD and its selection window.
 *
 * @author Jagy2014
 */
public final class JournalScrollport extends Scrollport {
    private boolean hideBar;
    public JournalScrollport(Coord size){this(size,false);}
    public JournalScrollport(Coord size,boolean hideBar){super(size);this.hideBar=hideBar;if(hideBar){bar.hide();cont.resize(size);}}
    public void resize(Coord size){super.resize(size);if(hideBar){bar.hide();cont.resize(size);}}

    protected Scrollbar makebar(int height){return new Grip(height){public void changed(){JournalScrollport.this.cont.sy=val;}};}
    public static class Grip extends Scrollbar {
        private UI.Grab grab;
        private int offset;
        public Grip(int height){super(height,0,0);resize(height);}
        public void resize(int height){super.resize(Coord.of(UI.scale(12),height));}
        public void move(Coord at){c=at.sub(sz.x,0);}
        private int thumb(){return Math.min(sz.y,Math.max(UI.scale(25),(int)((long)sz.y*sz.y/(Math.max(0,max-min)+sz.y))));}
        private int top(){return max<=min?0:(int)Math.round((double)(val-min)*(sz.y-thumb())/(max-min));}
        private static void pill(GOut g,int x,int y,int width,int height){
            int r=Math.max(1,width/2);g.frect(Coord.of(x,y+r),Coord.of(width,Math.max(0,height-2*r)));
            g.fellipse(Coord.of(x+r,y+r),Coord.of(r,r));g.fellipse(Coord.of(x+r,y+height-r),Coord.of(r,r));
        }
        private boolean hovered(){
            if(grab!=null)return true;
            Widget area=parent==null?null:parent.parent;
            if(ui==null||area==null||!ui.mc.sub(area.rootpos()).isect(Coord.z,area.sz))return false;
            for(Widget branch=area;branch.parent!=null;branch=branch.parent)
                for(Widget sibling=branch.next;sibling!=null;sibling=sibling.next)
                    if(sibling.visible()&&sibling instanceof Window&&ui.mc.sub(sibling.rootpos()).isect(Coord.z,sibling.sz))return false;
            return true;
        }
        public void draw(GOut g){
            if(!vis() || !hovered())return;
            int x=sz.x/2,bar=UI.scale(8),y=top(),h=thumb();
            g.chcolor(20,24,12,165);pill(g,x-UI.scale(2),0,UI.scale(4),sz.y);
            g.chcolor(35,26,12,240);pill(g,x-bar/2,y,bar,h);
            g.chcolor(grab==null?new Color(188,151,68):new Color(243,205,112));
            pill(g,x-bar/2+UI.scale(1),y+UI.scale(1),bar-UI.scale(2),Math.max(UI.scale(6),h-UI.scale(2)));g.chcolor();
        }
        private void update(int y){int travel=sz.y-thumb();if(travel<=0)return;int next=min+(int)Math.round(Utils.clip((double)(y-offset)/travel,0,1)*(max-min));ch(next-val);}
        public boolean mousedown(MouseDownEvent event){if(event.b!=1 || !vis() || !hovered())return false;int top=top();offset=event.c.y>=top && event.c.y<top+thumb()?event.c.y-top:thumb()/2;grab=ui.grabmouse(this);update(event.c.y);return true;}
        public void mousemove(MouseMoveEvent event){if(grab!=null)update(event.c.y);}
        public boolean mouseup(MouseUpEvent event){if(event.b!=1 || grab==null)return false;grab.remove();grab=null;return true;}
        public void dispose(){if(grab!=null)grab.remove();super.dispose();}
    }
}
