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
import hearthborn.scripts.FieldPlans;
import java.awt.Color;
import java.util.*;

/**
 * Project saved farming rectangles into this survey's coordinates, clipping to its claim.
 *
 * @author Jagy2014
 */
final class PlanFields {
    static final Color FILL=new Color(119,163,90,48),BORDER=new Color(220,192,112,230);
    static final class Field {
        final FieldPlans.Field source;
        final Set<Coord> tiles;
        final List<Area> patches;
        final Area label;
        Field(FieldPlans.Field source,Set<Coord> tiles,List<Area> patches){
            this.source=source;this.tiles=tiles;this.patches=patches;
            label=Collections.max(patches,Comparator.comparingInt(a->a.sz().x*a.sz().y));
        }
    }
    static List<Field> project(List<FieldPlans.Field> fields,Collection<ClaimSurvey.GridRecord> survey,Set<Coord> territory){
        Map<Long,ClaimSurvey.GridRecord> grids=new HashMap<>();
        for(ClaimSurvey.GridRecord grid:survey)grids.put(grid.id,grid);
        List<Field> result=new ArrayList<>();
        for(FieldPlans.Field field:fields){
            ClaimSurvey.GridRecord anchor=grids.get(field.grid);if(anchor==null)continue;
            Area bounds=Area.sized(Coord.of(anchor.x+field.x,anchor.y+field.y),Coord.of(field.width,field.height));
            Set<Coord> tiles=new HashSet<>();List<Area> patches=new ArrayList<>();
            for(int y=bounds.ul.y;y<bounds.br.y;y++){
                int x=bounds.ul.x;
                while(x<bounds.br.x){
                    if(!territory.contains(Coord.of(x,y))){x++;continue;}
                    int start=x;
                    do{tiles.add(Coord.of(x++,y));}while(x<bounds.br.x&&territory.contains(Coord.of(x,y)));
                    Area row=new Area(Coord.of(start,y),Coord.of(x,y+1));boolean merged=false;
                    for(int index=patches.size()-1;index>=0;index--){
                        Area previous=patches.get(index);
                        if(previous.br.y==y&&previous.ul.x==start&&previous.br.x==x){
                            patches.set(index,new Area(previous.ul,row.br));merged=true;break;
                        }
                    }
                    if(!merged)patches.add(row);
                }
            }
            if(!tiles.isEmpty())result.add(new Field(field,tiles,patches));
        }
        return result;
    }
    private PlanFields(){}
}
