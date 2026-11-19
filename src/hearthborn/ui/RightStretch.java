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
 * Foreground grip at the visible outer corner, independent of the content/decorator.
 *
 * @author Jagy2014
 */
public class RightStretch extends Widget {
    private final Window window;
    private UI.Grab grab;
    private Coord from, size;

    public RightStretch(Window window) {
        super(UI.scale(20,20));this.window=window;z(2);
        settip(hearthborn.L10n.tr(window instanceof MapWnd
            ? "Drag to resize the minimap. Double-click restores the previous size."
            : "Drag to resize: right to widen, down to make taller."));
    }
    protected boolean enabled(){return true;}
    private void place(){
        if(parent!=null)move(window.xlate(window.sz.sub(sz).max(Coord.z),false));
    }
    protected void added(){place();}
    public void presize(){place();}
    public boolean checkhit(Coord at){return enabled()&&at.isect(Coord.z,sz);}
    public void draw(GOut g){if(enabled())ResizeMarks.draw(g,sz,false);}
    public boolean mousedown(MouseDownEvent event){
        if(event.b!=1||!checkhit(event.c))return false;
        if(window instanceof MapWnd){
            MapWnd map=(MapWnd)window;
            if(map.cornerClick())return true;
            map.cornerPressed();
        }
        from=parentpos(window.parent,event.c);size=window.csz();
        if(window instanceof QuestHelper)((QuestHelper)window).magneticMove(new Coord(window.c));
        MagneticWindows.forget(window);grab=ui.grabmouse(this);window.raise();return true;
    }
    public void mousemove(MouseMoveEvent event){
        if(grab==null)return;
        Coord delta=HudInterfaceScale.fromParent(window,parentpos(window.parent,event.c).sub(from));
        if(window instanceof MapWnd&&(Math.abs(delta.x)>UI.scale(3)||Math.abs(delta.y)>UI.scale(3)))
            ((MapWnd)window).cornerDragged();
        window.resize(size.add(delta));
    }
    public boolean mouseup(MouseUpEvent event){
        if(grab==null||event.b!=1)return false;
        release();
        if(window instanceof MapWnd){MapWnd map=(MapWnd)window;map.cornerReleased(size);map.storePlace();}
        if(window instanceof QuestHelper)((QuestHelper)window).magneticRemember();
        ChatUI chat=window.getchild(ChatUI.class);if(chat!=null)chat.rememberPlace();
        return true;
    }
    private void release(){if(grab!=null){grab.remove();grab=null;}}
    public void dispose(){release();super.dispose();}
}
