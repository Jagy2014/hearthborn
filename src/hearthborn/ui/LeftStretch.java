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
 * Bottom-left grip. Drag left to widen and down to grow; the right and top edges stay.
 *
 * @author Jagy2014
 */
public final class LeftStretch extends Widget {
    private final Window window;
    private final String pref;
    private UI.Grab grab;
    private Coord from,size,origin;
    private int right;
    private Coord restC,restSz;
    private double cornerAt=Double.NEGATIVE_INFINITY;
    private boolean cornerClickable;
    public LeftStretch(Window window,String pref){
        super(UI.scale(20,20));this.window=window;this.pref=pref;z(2);
        settip(hearthborn.L10n.tr("Drag to resize: left to widen, down to make taller. Double-click restores the previous size."));
    }
    private void place(){
        if(parent==null)return;
        // Children use content coordinates; the visible frame corner includes its trim.
        move(window.xlate(Coord.of(0,Math.max(0,window.sz.y-sz.y)),false));
    }
    protected void added(){place();}
    public void presize(){place();}
    public void draw(GOut g){
        ResizeMarks.draw(g,Coord.of(0,sz.y),true);
    }
    public boolean mousedown(MouseDownEvent event){
        if(event.b!=1||!checkhit(event.c))return false;
        if(restore())return true;
        from=parentpos(window.parent,event.c);size=window.csz();origin=new Coord(window.c);
        right=window.c.x+HudInterfaceScale.size(window).x;
        pressed();
        MagneticWindows.forget(window);grab=ui.grabmouse(this);window.raise();return true;
    }
    public void mousemove(MouseMoveEvent event){
        if(grab==null)return;
        Coord delta=HudInterfaceScale.fromParent(window,parentpos(window.parent,event.c).sub(from));
        if(Math.abs(delta.x)>UI.scale(3)||Math.abs(delta.y)>UI.scale(3))cornerClickable=false;
        window.resize(size.add(-delta.x,delta.y));
        settle(Coord.of(right-HudInterfaceScale.size(window).x,origin.y));
    }
    public boolean mouseup(MouseUpEvent event){
        if(grab==null||event.b!=1)return false;
        Coord before=size;
        release();released(before);remember();return true;
    }
    private void settle(Coord at){
        if(window instanceof QuestHelper)((QuestHelper)window).magneticMove(at);
        else window.move(at);
    }
    private void remember(){
        if(window instanceof QuestHelper)((QuestHelper)window).magneticRemember();
        ChatUI chat=window.getchild(ChatUI.class);
        if(chat!=null)chat.rememberPlace();
    }
    private boolean restore(){
        double now=Utils.rtime();
        boolean twice=cornerClickable&&now-cornerAt<=0.5;
        cornerAt=now;cornerClickable=true;
        if(!twice)return false;
        cornerClickable=false;
        Coord sz=restSz!=null?restSz:Utils.getprefc(pref+"-sz",null);
        Coord at=restC!=null?restC:Utils.getprefc(pref+"-c",null);
        if(sz==null||at==null)return true;
        window.resize(sz);settle(new Coord(at));clear();remember();return true;
    }
    private void pressed(){
        if(restSz!=null)return;
        Coord saved=Utils.getprefc(pref+"-sz",null),savedAt=Utils.getprefc(pref+"-c",null);
        if(saved!=null&&savedAt!=null){restSz=saved;restC=savedAt;return;}
        restSz=window.csz();restC=new Coord(window.c);
        Utils.setprefc(pref+"-sz",restSz);Utils.setprefc(pref+"-c",restC);
    }
    private void released(Coord before){
        Coord now=window.csz();
        boolean grew=now.x>before.x+1||now.y>before.y+1;
        if(!grew&&restSz!=null&&now.x<=restSz.x+1&&now.y<=restSz.y+1)clear();
    }
    private void clear(){
        restC=null;restSz=null;
        Utils.setprefc(pref+"-c",null);Utils.setprefc(pref+"-sz",null);
    }
    private void release(){if(grab!=null){grab.remove();grab=null;}}
    public void dispose(){release();super.dispose();}
}
