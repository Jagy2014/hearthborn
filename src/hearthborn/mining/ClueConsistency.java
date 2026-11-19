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
import haven.Coord;
import java.util.*;

/**
 * Bounded consistency check for the eight-neighbor model; opened tiles remain unknown variables.
 *
 * @author Jagy2014
 */
final class ClueConsistency {
    enum Result { CONSISTENT, CONFLICT, UNKNOWN }
    final long[] masks;final int[] targets;int budget=5000;
    ClueConsistency(Map<Coord,Integer> clues){
        Map<Coord,Integer> variables=new HashMap<>();masks=new long[clues.size()];targets=new int[clues.size()];int row=0;
        for(Map.Entry<Coord,Integer> clue:clues.entrySet()){
            for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)if(x!=0||y!=0){
                Coord cell=clue.getKey().add(x,y);Integer bit=variables.get(cell);
                if(bit==null){bit=variables.size();variables.put(cell,bit);}
                if(bit>=63)throw new IllegalArgumentException("Too many clue variables");
                masks[row]|=1L<<bit;
            }
            targets[row++]=clue.getValue();
        }
    }
    static Result check(Map<Coord,Integer> clues){
        try {int value=new ClueConsistency(clues).solve(0,0);return value>0?Result.CONSISTENT:value==0?Result.CONFLICT:Result.UNKNOWN;}
        catch(IllegalArgumentException tooLarge){return Result.UNKNOWN;}
    }
    int solve(long known,long ones){
        if(--budget<0)return -1;
        boolean changed;
        do {
            changed=false;
            for(int i=0;i<masks.length;i++){
                long remaining=masks[i]&~known;int need=targets[i]-Long.bitCount(masks[i]&ones),left=Long.bitCount(remaining);
                if(need<0||need>left)return 0;
                if(left>0&&(need==0||need==left)){known|=remaining;if(need==left)ones|=remaining;changed=true;}
            }
        }while(changed);
        long choice=0;int size=64;
        for(long mask:masks){long left=mask&~known;int n=Long.bitCount(left);if(n>0&&n<size){size=n;choice=Long.lowestOneBit(left);}}
        if(choice==0)return 1;
        int a=solve(known|choice,ones);if(a==1)return 1;
        int b=solve(known|choice,ones|choice);if(b==1)return 1;
        return a<0||b<0?-1:0;
    }
}
