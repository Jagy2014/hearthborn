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
 * A window without its frame: a dark panel with a thin strip on top that drags the window and holds the close cross.
 * The plain chat and the quest journal use it when their frame is switched off.
 *
 * @author Jagy2014
 */
public final class PlainDeco extends Window.DefaultDeco {
    private final java.util.function.DoubleSupplier fade;
    /** The only way to close such a window with the pointer: the frame and its own button are gone. */
    public final IButton close;
    private int header(){return close.sz.y+UI.scale(4);}
    public PlainDeco(java.util.function.DoubleSupplier fade){
        this.fade=fade;cbtn.hide();
        close=add(SmallWindowDeco.closeButton(()->((Window)parent).reqclose()));
    }
    public PlainDeco(){this(()->1);}
    public void iresize(Coord contents){
        aa=Area.sized(Coord.of(UI.scale(4),header()),contents);
        ca=Area.sized(Coord.of(0,header()),contents.add(UI.scale(18,18)));
        resize(ca.br);cptl=Coord.z;cpsz=Coord.of(sz.x,header());
        close.c=Coord.of(sz.x-close.sz.x-UI.scale(4),UI.scale(2));
    }
    protected void drawbg(GOut g){
        double level=fade.getAsDouble();
        if(level<1)g.chcolor(255,255,255,(int)Math.round(255*level));
        UiTheme.darkBackground(g,sz);
        if(level<1)g.chcolor();
    }
    protected void drawframe(GOut g){}
    public boolean checkhit(Coord at){return at.isect(Coord.z,sz);}
}
