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
 * Quest journal launcher in the buff row, immediately after the village plan.
 *
 * @author Jagy2014
 */
public final class QuestJournalButton extends Buff {
    private static final Map<GameUI,QuestJournalButton> shown=new WeakHashMap<>();
    private static Tex plate;
    private final GameUI gui;
    private QuestJournalButton(GameUI gui){super(QuestJournalButton::icon);this.gui=gui;resize(Buff.frame.sz());}
    private static Resource iconResource;
    /** The painted plate already has its frame, so the buff frame is not drawn again. */
    static synchronized Resource icon(){
        if(iconResource==null)iconResource=XtensionsMenu.resource("quest-help","Quest journal",'\0');
        return iconResource;
    }
    private static synchronized Tex plate(){
        if(plate==null)plate=new TexI(PUtils.uiscale(icon().flayer(Resource.imgc).img,Buff.frame.sz()));
        return plate;
    }
    public void draw(GOut g){g.chcolor(255,255,255,a);g.image(plate(),Coord.z);g.chcolor();}
    public static void tick(GameUI gui){
        if(gui.buffs==null||gui.questHelper==null)return;
        QuestJournalButton button=shown.get(gui);
        boolean closed=!QuestHelper.enabled();
        if(!closed){
            if(button!=null){shown.remove(gui);if(button.parent!=null)button.destroy();}
            return;
        }
        if(button==null||button.parent==null){button=new QuestJournalButton(gui);gui.buffs.addchild(button);shown.put(gui,button);}
    }
    public static void dispose(GameUI gui){QuestJournalButton button=shown.remove(gui);if(button!=null&&button.parent!=null)button.destroy();}
    public int layoutOrder(){return 0;}
    public boolean mousedown(MouseDownEvent event){
        if(event.b!=1)return false;
        gui.questHelper.toggleWindow();
        return true;
    }
    public Object tooltip(Coord at,Widget previous){return gui.questHelper.hoverSummary();}
}
