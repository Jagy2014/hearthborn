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
 * The native discovery tint and opening pulse, shared by both action menus.
 *
 * @author Jagy2014
 */
public final class ActionDiscovery {
    public static void draw(GOut g,MenuGrid.PagButton button,GSprite sprite,double elapsed,int index){
        MenuGrid.Pagina page=button.pag;
        if(page.tnew!=0){
            page.anew=1;
            double alpha=.25;
            if(page.tnew==2){
                double phase=elapsed-((index*.15)%1.0);
                alpha=phase<1.25?(Math.cos(phase*Math.PI*2)*-.25)+.25:.25;
            }
            g.usestate(new ColorMask(new FColor(.125f,1f,.125f,(float)alpha)));
        }
        button.draw(g,sprite);
        g.defstate();
    }
    private ActionDiscovery(){}
}
