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
import hearthborn.meteor.StarFall;
import java.util.*;

/**
 * Party controls occupy free HUD space without moving the owner's saved journal or map.
 *
 * @author Jagy2014
 */
public final class PartyLayout {
    public static void place(Widget party){
        GameUI gui=party.getparent(GameUI.class);
        if(gui==null||gui.portrait==null||gui.sz.x<=0||gui.sz.y<=0)return;
        Widget upper=gui.portrait.parent;
        Coord origin=upper.parentpos(gui);
        Coord preferred=Coord.of(gui.portrait.parentpos(gui).x,origin.y+upper.sz.y+UI.scale(8));
        List<Area> occupied=new ArrayList<>();
        for(Widget child:gui.children())if(child!=party&&child.visible()&&child.sz.x>0&&child.sz.y>0&&
            (child instanceof GameUI.Hidepanel||child instanceof QuestHelper||
             child instanceof StarFall||child instanceof ServerStats||child instanceof MapWnd||child instanceof ChatUI))
            occupied.add(Area.sized(child.c,child.sz));
        party.move(position(gui.sz,party.sz,preferred,occupied));
    }
    static Coord position(Coord screen,Coord size,Coord preferred,List<Area> occupied){
        int gap=UI.scale(8),maxX=Math.max(0,screen.x-size.x),maxY=Math.max(0,screen.y-size.y);
        Set<Integer> xs=new LinkedHashSet<>(),ys=new LinkedHashSet<>();
        xs.add(Utils.clip(preferred.x,0,maxX));ys.add(Utils.clip(preferred.y,0,maxY));
        for(Area block:occupied){
            xs.add(Utils.clip(block.br.x+gap,0,maxX));xs.add(Utils.clip(block.ul.x-size.x-gap,0,maxX));
            ys.add(Utils.clip(block.br.y+gap,0,maxY));ys.add(Utils.clip(block.ul.y-size.y-gap,0,maxY));
        }
        Coord best=Coord.of(Utils.clip(preferred.x,0,maxX),Utils.clip(preferred.y,0,maxY));
        long score=Long.MAX_VALUE;
        for(int x:xs)for(int y:ys){
            Area candidate=Area.sized(Coord.of(x,y),size);boolean clear=true;
            for(Area block:occupied)if(candidate.ul.x<block.br.x&&block.ul.x<candidate.br.x&&candidate.ul.y<block.br.y&&block.ul.y<candidate.br.y){clear=false;break;}
            long distance=2L*Math.abs(x-preferred.x)+Math.abs(y-preferred.y);
            if(clear&&distance<score){best=Coord.of(x,y);score=distance;}
        }
        return best;
    }
    private PartyLayout(){}
}
