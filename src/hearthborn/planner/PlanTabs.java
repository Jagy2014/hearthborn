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

import haven.*;
import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Two or more cells joined into one strip; exactly one is lit.
 *
 * @author Jagy2014
 */
final class PlanTabs extends Widget {
    private final String[] ids;
    private final Tex[] lit,dim;
    private final int[] left;
    private final Supplier<String> current;
    private final Consumer<String> choose;
    PlanTabs(String[] ids,String[] titles,Supplier<String> current,Consumer<String> choose){
        super(Coord.z);
        this.ids=ids;this.current=current;this.choose=choose;
        lit=new Tex[ids.length];dim=new Tex[ids.length];left=new int[ids.length+1];
        int width=0;
        for(int i=0;i<ids.length;i++){
            lit[i]=Text.render(titles[i],new Color(255,226,140)).tex();dim[i]=Text.render(titles[i],new Color(190,176,140)).tex();
            left[i]=width;width+=lit[i].sz().x+UI.scale(26);
        }
        left[ids.length]=width;
        resize(Coord.of(width,UI.scale(24)));
    }
    String[] ids(){return ids;}
    void choose(String id){choose.accept(id);}
    public void draw(GOut g){
        g.chcolor(26,24,20,255);g.frect(Coord.z,sz);
        String now=current.get();
        for(int i=0;i<ids.length;i++){
            boolean active=ids[i].equals(now);
            Coord at=Coord.of(left[i],0),cell=Coord.of(left[i+1]-left[i],sz.y);
            if(active){g.chcolor(66,54,32,255);g.frect(at.add(1,1),cell.sub(2,2));}
            g.chcolor();g.aimage(active?lit[i]:dim[i],at.add(cell.x/2,cell.y/2),.5,.5);
        }
        g.chcolor(176,141,70,255);g.rect(Coord.z,sz);
        g.chcolor(122,98,52,255);
        for(int i=1;i<ids.length;i++)g.line(Coord.of(left[i],1),Coord.of(left[i],sz.y-1),1);
        g.chcolor();
    }
    public boolean mousedown(MouseDownEvent ev){
        if(ev.b!=1)return false;
        for(int i=0;i<ids.length;i++)if(ev.c.x>=left[i]&&ev.c.x<left[i+1]){choose.accept(ids[i]);return true;}
        return false;
    }
    public void dispose(){for(Tex tex:lit)tex.dispose();for(Tex tex:dim)tex.dispose();super.dispose();}
}
