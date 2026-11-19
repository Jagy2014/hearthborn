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

/**
 * Small standalone settings window, so toolbar changes remain visible behind it.
 *
 * @author Jagy2014
 */
public final class UISettings extends Window {
    private OptWnd options;
    public static void open(GameUI gui){open(gui,null);}
    public static void open(GameUI gui,OptWnd options){
        UISettings window=gui.getchild(UISettings.class);
        if(window==null){window=new UISettings();gui.add(window,gui.sz.sub(window.sz).div(2).max(Coord.z));window.move(gui.sz.sub(HudInterfaceScale.size(window)).div(2).max(Coord.z));}
        if(options!=null)window.options=options;
        window.show();window.raise();gui.setfocus(window);
    }
    private void back(){
        if(options!=null){options.show();options.raise();}
        reqdestroy();
    }
    /** The same controls as a section of the options window. */
    public static OptWnd.Panel panel(OptWnd options){
        OptWnd.Panel panel=options.new Panel();
        new UISettingsControls(panel).build();
        panel.pack();
        return panel;
    }
    public UISettings(){
        super(Coord.z,"Interface display");reqclose(this::reqdestroy);
        UISettingsControls controls=new UISettingsControls(this);
        Widget help=controls.build();
        Button back=add(new Button(UI.scale(120),"Back",this::back),help.pos("bl").adds(0,8));
        pack();
        back.move(Coord.of(Math.max(0,(contentsz().x-back.sz.x)/2),back.c.y));
        // A line that comes or goes (the sidebar menu has more than the grid) moves the end of the controls, and the button with it.
        controls.relaid(()->{
            back.move(Coord.of(back.c.x,help.pos("bl").y+UI.scale(8)));
            pack();
            back.move(Coord.of(Math.max(0,(contentsz().x-back.sz.x)/2),back.c.y));
        });
    }
}
