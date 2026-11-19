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

package hearthborn.planner;
import hearthborn.ui.XtensionsMenu;

import haven.*;
import hearthborn.L10n;
import hearthborn.planner.ClaimPlan;
import hearthborn.planner.ClaimSurvey;
import java.util.*;

/**
 * Persistent claim scanner status beside the study reminder.
 *
 * @author Jagy2014
 */
public final class ClaimPlanStatus extends Buff {
    private static final Map<GameUI,ClaimPlanStatus> shown=new WeakHashMap<>();
    private static final Tex pauseLabel=new TexI(Utils.outline2(
        new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),7).aa(false).render("PAUSED",java.awt.Color.WHITE).img,
        java.awt.Color.BLACK));
    private static final Tex statusIcon=new TexI(PUtils.uiscale(
        XtensionsMenu.claimPlanIcon().flayer(Resource.imgc).img,Buff.frame.sz()));
    private final GameUI gui;
    private ClaimPlanStatus(GameUI gui){super(XtensionsMenu::claimPlanIcon);this.gui=gui;resize(Buff.frame.sz());}
    public int layoutOrder(){return -1;}
    public static void tick(GameUI gui){
        if(gui.buffs==null)return;
        ClaimPlanStatus status=shown.get(gui);
        if(status==null||status.parent==null){
            status=new ClaimPlanStatus(gui);gui.buffs.addchild(status);shown.put(gui,status);
        }
    }
    public static void dispose(GameUI gui){
        ClaimPlanStatus status=shown.remove(gui);
        if(status!=null&&status.parent!=null)status.destroy();
    }
    public static boolean activate(GameUI gui,int button){
        if(button==3){ClaimSurvey.enabled(!ClaimSurvey.enabled());return true;}
        if(button==1){ClaimPlan.toggle(gui);return true;}
        return false;
    }
    private static void drawState(GOut g,Coord size,GameUI gui){
        if(ClaimSurvey.scanning(gui))return;
        int height=pauseLabel.sz().y+UI.scale(2),y=(size.y-height)/2;
        g.chcolor(45,8,6,235);g.frect(Coord.of(UI.scale(1),y),Coord.of(size.x-UI.scale(2),height));
        g.chcolor(255,105,90,255);g.line(Coord.of(UI.scale(1),y),Coord.of(size.x-UI.scale(1),y),1);g.chcolor();
        g.aimage(pauseLabel,size.div(2),.5,.5);
    }
    public void draw(GOut g){
        g.chcolor(255,255,255,a);g.image(statusIcon,Coord.z);g.chcolor();
        drawState(g,sz,gui);
    }
    public boolean mousedown(MouseDownEvent event){return activate(gui,event.b);}
    public Object tooltip(Coord at,Widget previous){
        String state=!ClaimSurvey.enabled()?"Claim scanning disabled":ClaimSurvey.scanning(gui)?"Claim scanning active":"Claim scanning paused outside your claims";
        return L10n.tr("Claim plan")+"\n"+L10n.tr(state)+"\n"+L10n.tr("Left click opens the claim plan. Right click toggles scanning. Scanning pauses outside your claims and resumes on return if enabled.");
    }
}
