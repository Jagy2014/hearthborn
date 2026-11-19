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

package hearthborn.mining;
import haven.*;
import haven.resutil.CaveTile;

/**
 * What was actually visible when a dust effect was created; openness is not a safety label.
 *
 * @author Jagy2014
 */
final class ClueObservation {
    final long time;final int strength,open,walls;
    ClueObservation(long time,int strength,int open,int walls){this.time=time;this.strength=strength;this.open=open;this.walls=walls;}
    static ClueObservation capture(MCache map,Coord tile,int strength){
        int open=0,walls=0,bit=0;
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if(x!=0||y!=0){
            try {Tiler tiler=map.tiler(map.gettile(tile.add(x,y)));if(tiler instanceof CaveTile)walls|=1<<bit;else if(tiler!=null)open|=1<<bit;}catch(Loading unavailable){}
            bit++;
        }
        return new ClueObservation(System.currentTimeMillis(),strength,open,walls);
    }
}
