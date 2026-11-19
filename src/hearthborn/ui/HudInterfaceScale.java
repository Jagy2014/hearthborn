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
 * Game UI magnification independent of the existing monitor/interface scale.
 *
 * @author Jagy2014
 */
public final class HudInterfaceScale {
    public static final double MIN=.75,MAX=2;
    private static volatile double value=load();
    private static double load(){double saved=Utils.getprefd("hearthborn/ui/panel-scale",1);return Double.isFinite(saved)?Utils.clip(saved,MIN,MAX):1;}
    public static double current(){return value;}
    /** The whole interface is no longer magnified: only the button panels are (see {@link #presentation}). */
    public static double forUI(UI ui){return 1;}
    public static void apply(UI ui,double scale){
        if(!Double.isFinite(scale))return;
        value=Utils.clip(scale,MIN,MAX);Utils.setprefd("hearthborn/ui/panel-scale",value);
        if(ui!=null){
            GameUI gui=ui.root.getchild(GameUI.class);
            if(gui!=null){gui.relayoutPanels();gui.applyPortraitScale();}
            fit(ui);
        }
    }
    /** Also runs on display resize, including a saved HUD scale on login. */
    public static void fit(UI ui){
        GameUI gui=ui.root.getchild(GameUI.class);
        if(gui!=null)for(Widget child=gui.child;child!=null;child=child.next){
                if(!(child instanceof Window)||child.c.x< -10000)continue;
                Coord limit=gui.sz.sub(size(child)).max(Coord.z);
                child.move(Coord.of(Utils.clip(child.c.x,0,limit.x),Utils.clip(child.c.y,0,limit.y)));
        }
    }
    /** Nothing is magnified by the canvas any more: the button panels scale themselves (see {@link ToolbarScale}, {@link ActionMenuFrame#grid}). */
    public static double presentation(Widget widget){return 1;}
    public static Coord point(Coord point,double scale){
        return scale==1?point:Coord.of((int)Math.round(point.x*scale),(int)Math.round(point.y*scale));
    }
    public static Coord size(Widget widget){return ToolbarScale.size(widget.sz,presentation(widget));}
    public static Coord toParent(Widget widget,Coord point){return point(point,presentation(widget));}
    public static Coord fromParent(Widget widget,Coord point){return point(point,1/presentation(widget));}
    public static double toAncestor(Widget widget,Widget ancestor){
        double scale=1;
        for(Widget at=widget;at!=null&&at!=ancestor;at=at.parent)scale*=presentation(at);
        return scale;
    }
    private HudInterfaceScale(){}
}
