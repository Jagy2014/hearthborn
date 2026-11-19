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

package hearthborn.inventory;

import haven.*;

/**
 * Gesture preferences are shared by all container overviews.
 *
 * @author Jagy2014
 */
public final class OverviewSettings {
    private static final String KEY="hearthborn/overview/";
    public static boolean hints(){return Utils.getprefb(KEY+"hints",true)&&uses()<20;}
    static int uses(){return Utils.getprefi(KEY+"gesture-uses",0);}
    static void success(){Utils.setprefi(KEY+"gesture-uses",Math.min(20,uses()+1));}
    public static void hints(boolean on){Utils.setprefb(KEY+"hints",on);if(on)Utils.setprefi(KEY+"gesture-uses",0);}
    public static boolean confirm(){return Utils.getprefb(KEY+"confirm-group",true);}
    public static void confirm(boolean value){Utils.setprefb(KEY+"confirm-group",value);}
    static int quick(int index){return Math.max(1,Utils.getprefi(KEY+"quick-"+index,index==0?5:10));}
    static String last(boolean quality){return Utils.getpref(KEY+(quality?"last-quality":"last-count"),quality?"10":"1");}
    static void last(boolean quality,String text){Utils.setpref(KEY+(quality?"last-quality":"last-count"),text);}
    public static OptWnd.Panel panel(OptWnd options,OptWnd.Panel back){
        OptWnd.Panel panel=options.new Panel();
        Widget previous=panel.add(new CheckBox("Show overview gesture hints").state(OverviewSettings::hints).set(OverviewSettings::hints));
        previous=panel.add(new CheckBox("Confirm dropping an entire group").state(OverviewSettings::confirm).set(OverviewSettings::confirm),previous.pos("bl").adds(0,10));
        java.util.List<Widget> entries=new java.util.ArrayList<>();int labelWidth=0;
        for(int i=0;i<2;i++){
            final int index=i;Label label=panel.add(new Label(i==0?"First quick take amount:":"Second quick take amount:"),Coord.of(0,previous.pos("bl").y+UI.scale(14)));
            TextEntry input=panel.add(new OverviewTheme.Entry(100,Integer.toString(quick(i))){
                protected void changed(){super.changed();try{String s=text().trim();if(s.matches("[1-9][0-9]{0,6}"))Utils.setprefi(KEY+"quick-"+index,Integer.parseInt(s));}catch(NumberFormatException ignored){}}
            },Coord.of(UI.scale(275),label.c.y));previous=input;entries.add(input);labelWidth=Math.max(labelWidth,label.sz.x);
        }
        // Right after the longest label rather than at a fixed place, so the panel fits a narrow settings pane in either language.
        for(Widget input:entries)input.move(Coord.of(Math.min(UI.scale(275),labelWidth+UI.scale(12)),input.c.y));
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),previous.pos("bl").adds(0,18));panel.pack();return panel;
    }
    private OverviewSettings(){}
}
