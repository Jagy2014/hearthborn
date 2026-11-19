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
import hearthborn.L10n;
import java.util.*;

/**
 * Right-click Drink on the water meter, and optional sips when stamina falls.
 *
 * @author Jagy2014
 */
public final class AutoDrink {
    private static final String PREF="hearthborn/drink/below";
    private static final Map<GameUI,Double> next=new WeakHashMap<>();
    public static int below(){int value=Utils.getprefi(PREF,0);return value==25||value==50||value==75?value:0;}
    public static void below(int value){Utils.setprefi(PREF,value==25||value==50||value==75?value:0);}
    public static String status(){
        int level=below();
        return level==0?L10n.tr("Auto-drink: off"):L10n.format("Auto-drink: on, below %d%% stamina",level);
    }
    public static void menu(GameUI gui,Coord at){
        if(gui==null||gui.ui==null||gui.ui.root.getchild(FlowerMenu.class)!=null)return;
        gui.ui.root.add(new FlowerMenu("Drink"){
            public void choose(Petal option){
                if(option!=null){drink(gui);uimsg("act",option.num);}
                else uimsg("cancel");
            }
        },at);
    }
    public static void drink(GameUI gui){
        MenuGrid.Pagina page=page(gui);
        if(page!=null&&page.scm.ui!=null)try{page.button().use(new MenuGrid.Interaction());return;}catch(Loading ignored){}
        gui.act("drink");
    }
    public static void tick(GameUI gui){
        // The field runner schedules drinks between acknowledged actions, with an empty cursor.
        if(hearthborn.scripts.ScriptRunner.managesDrinking(gui))return;
        int level=below();if(level==0||gui.prog!=null||hearthborn.combat.BowCombat.busy(gui))return;
        double now=Utils.rtime();if(now<next.getOrDefault(gui,0.0))return;
        IMeter stamina=gui.meter("stam");
        if(stamina==null||stamina.meters().isEmpty()||stamina.meters().get(0).a>level/100.0)return;
        DrinkSupply supply=DrinkSupply.collect(gui);
        if(supply.water+supply.tea<=0)return;
        drink(gui);next.put(gui,now+3);
    }
    private static MenuGrid.Pagina page(GameUI gui){
        if(gui.menu==null)return null;
        synchronized(gui.menu.paginae){
            for(MenuGrid.Pagina candidate:gui.menu.paginae)try{
                Resource resource=candidate.res();
                if(resource!=null&&resource.name.equals("paginae/act/drink"))return candidate;
            }catch(Loading ignored){}
        }
        return null;
    }
    private AutoDrink(){}
}
