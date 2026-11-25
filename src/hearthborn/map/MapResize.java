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

package hearthborn.map;

import haven.*;
import hearthborn.ui.HudInterfaceScale;
import hearthborn.ui.MagneticWindows;
import hearthborn.ui.ResizeMarks;

/**
 * Bottom-left grip. Uses the detached-chat stripes, mirrored so they sit in this corner.
 *
 * @author Jagy2014
 */
public final class MapResize extends Widget {
    private final MapWnd map;
    private UI.Grab grab;
    private MagneticWindows.StackResize stack;
    private Coord from,size,origin;
    private int right;
    public MapResize(MapWnd map){
        super(Window.sizer.sz());this.map=map;z(1);
        settip("Drag to resize the map: left to widen, down to make taller. Double-click restores the previous size.");
    }
    public void presize(){move(Coord.of(0,Math.max(0,((Frame)parent).inner().y-sz.y)));}
    public void draw(GOut g){
        if(map.c.x<-10000)return;
        ResizeMarks.draw(g, Coord.of(0, sz.y), true);
    }
    public boolean checkhit(Coord c){return map.c.x>=-10000&&c.isect(Coord.z,sz);}
    public boolean mousedown(MouseDownEvent event){
        if(!checkhit(event.c))return false;
        if(event.b!=1)return true;
        stack=MagneticWindows.resizeStack(map,true);
        if(stack!=null){
            from=parentpos(map.parent,event.c);
            grab=ui.grabmouse(this);map.raise();return true;
        }
        if(map.cornerClick())return true;
        from=parentpos(map.parent,event.c);size=map.csz();origin=new Coord(map.c);right=map.c.x+HudInterfaceScale.size(map).x;
        MagneticWindows.release(map);grab=ui.grabmouse(this);map.raise();return true;
    }
    public void mousemove(MouseMoveEvent event){
        if(grab==null)return;
        Coord travel=parentpos(map.parent,event.c).sub(from);
        if(stack!=null){stack.resize(travel);return;}
        Coord delta=HudInterfaceScale.fromParent(map,travel);
        if(Math.abs(delta.x)>UI.scale(3)||Math.abs(delta.y)>UI.scale(3))map.cornerDragged();
        map.resize(size.add(-delta.x,delta.y));map.move(Coord.of(right-HudInterfaceScale.size(map).x,origin.y));
    }
    public boolean mouseup(MouseUpEvent event){
        if(grab==null||event.b!=1)return false;
        if(stack!=null){stack.finish();stack=null;release();return true;}
        release();map.cornerReleased(size,origin);map.storePlace();return true;
    }
    private void release(){if(grab!=null){grab.remove();grab=null;}}
    public void dispose(){release();super.dispose();}
}
