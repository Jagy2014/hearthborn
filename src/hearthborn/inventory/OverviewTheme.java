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
import hearthborn.L10n;
import hearthborn.ui.UiTheme;
import java.awt.Color;

/**
 * Flat overview controls; all dimensions are logical UI pixels.
 *
 * @author Jagy2014
 */
final class OverviewTheme {
    static final int WIDTH=460,ROW=30,ACTION=28,PADDING=8,FOOTER=28,MENU_WIDTH=270;
    static final Color GOLD=new Color(174,143,76),ACCENT=new Color(137,124,40,150),DANGER=new Color(224,96,82);
    static Color background(){return UiTheme.light()?UiTheme.BACKGROUND:UiTheme.panelColor();}
    static Color ink(){return UiTheme.light()?UiTheme.INK:new Color(236,232,218);}
    static Color muted(){return UiTheme.light()?new Color(105,95,75):new Color(157,154,142);}
    static void panel(GOut g,Coord size){g.chcolor(background());g.frect(Coord.z,size);g.chcolor(GOLD);g.rect(Coord.z,size);g.chcolor();}
    static String fit(String text,int width,Text.Foundry font){
        if(font.strsize(text).x<=width)return text;
        int n=text.length();while(n>0&&font.strsize(text.substring(0,n)+"…").x>width)n=text.offsetByCodePoints(n,-1);
        return n==0?"":text.substring(0,n)+"…";
    }
    static class FlatButton extends Widget {
        final String title;final Runnable action;final boolean danger;boolean disabled,hover;
        FlatButton(int width,String title,boolean danger,Runnable action){super(UI.scale(width,30));this.title=title;this.danger=danger;this.action=action;}
        void disable(boolean value){disabled=value;}
        void click(){if(!disabled)action.run();}
        public void mousemove(MouseMoveEvent e){hover=e.c.isect(Coord.z,sz);}
        public void draw(GOut g){
            panel(g,sz);if(hover&&!disabled){g.chcolor(ACCENT);g.frect(UI.scale(1,1),sz.sub(UI.scale(2,2)));g.chcolor();}
            g.chcolor(disabled?muted():danger?DANGER:ink());g.atext(L10n.tr(title),sz.div(2),.5,.5);g.chcolor();
        }
        public boolean mousedown(MouseDownEvent e){if(e.b==1){click();return true;}return true;}
    }
    static class Entry extends TextEntry {
        Entry(int width,String text){super(UI.scale(width),text);}
    }
    private OverviewTheme(){}
}
