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

package hearthborn.cheese;

import haven.*;
import hearthborn.util.ObjectWindows;
import java.awt.Color;

/**
 * remaining aging time, drawn below the tray's percent.
 *
 * @author Jagy2014
 */
public final class CheeseCountdown implements Disposable {
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans,10).aa(true);
    private String shown;
    private Tex texture;
    public static int remaining(WItem view,double meter){
        if(!(meter>0)||CheeseAging.itemKind(view.item)==null)return -1;
        Window window=view.getparent(Window.class);GameUI gui=view.getparent(GameUI.class);
        if(window==null||gui==null)return -1;
        Gob rack=ObjectWindows.target(window,gui,CheeseAging::isRack);
        return rack==null?-1:CheeseAging.remainingSeconds(view.item,rack,meter);
    }
    /** Hours and minutes once an hour remains; shorter times stay minutes:seconds. */
    public static String format(int seconds){
        if(seconds<60)return String.format(java.util.Locale.ROOT,"%02d",seconds);
        int hours=seconds/3600;
        if(hours>0)return hours+"h "+(seconds/60%60)+"m";
        return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
    }
    public void draw(GOut g,WItem view,Coord center,double meter){
        int left=remaining(view,meter);if(left<0){dispose();return;}
        String text=format(left);
        if(!text.equals(shown)){dispose();shown=text;texture=new TexI(Utils.outline2(FONT.render(text,Color.WHITE).img,Color.BLACK));}
        g.aimage(texture,center.add(0,UI.scale(10)),.5,.5);
    }
    public void dispose(){if(texture!=null)texture.dispose();texture=null;shown=null;}
}
