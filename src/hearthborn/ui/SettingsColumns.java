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
 * Two-column settings panels: the second column goes under the first when the settings pane is too narrow for both.
 *
 * @author Jagy2014
 */
public final class SettingsColumns {
    private SettingsColumns() {}

    /** Makes the panel lay its columns out for the pane it is shown in. */
    public static void stack(OptWnd.Panel panel,int split){panel.fitting(stacked(panel,split));}

    /**
     * The panel has a second column from {@code split} on. A narrow settings pane (a small game window) cuts that column
     * off, so it goes under the first one instead; the pane only scrolls up and down. The returned function takes the
     * width of the pane and lays the columns out for it, so it can run each time the panel is shown.
     */
    public static java.util.function.IntConsumer stacked(Widget panel,int split){
        Widget back=null;
        for(Widget child=panel.child;child!=null;child=child.next)
            if(child instanceof OptWnd.PButton&&((OptWnd.PButton)child).title().equals("Back"))back=child;
        // The content is everything above the Back button (which the reset button hangs under).
        List<Widget> first=new ArrayList<>(),second=new ArrayList<>();
        int need=0,top=Integer.MAX_VALUE,firstBottom=0;
        for(Widget child=panel.child;child!=null;child=child.next){
            if(back!=null&&child.c.y>=back.c.y)continue;
            if(child.c.x>=split){second.add(child);need=Math.max(need,child.c.x+child.sz.x);top=Math.min(top,child.c.y);}
            else{first.add(child);firstBottom=Math.max(firstBottom,child.c.y+child.sz.y);}
        }
        if(back==null||second.isEmpty())return width->{};
        Widget button=back;
        int wide=need,secondTop=top,aboveBottom=firstBottom,secondBottom=0;
        for(Widget child:second)secondBottom=Math.max(secondBottom,child.c.y+child.sz.y);
        int beside=Math.max(aboveBottom,secondBottom),stackedBottom=aboveBottom+UI.scale(15)+secondBottom-secondTop;
        int backGap=back.c.y-beside;
        boolean[] stacked={false};
        return width->{
            boolean want=wide>width;
            if(want==stacked[0])return;
            int dy=aboveBottom+UI.scale(15)-secondTop;
            for(Widget child:second)child.move(want?child.c.add(-split,dy):child.c.add(split,-dy));
            button.move(Coord.of(button.c.x,(want?stackedBottom:beside)+backGap));
            stacked[0]=want;
            panel.pack();
        };
    }
}
