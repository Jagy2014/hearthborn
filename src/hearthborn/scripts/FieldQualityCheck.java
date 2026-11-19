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

package hearthborn.scripts;

import haven.*;
import hearthborn.L10n;
import hearthborn.quality.RosterStore;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * Compare a field's actual planting reserve with historical records, without fetching remote stock.
 *
 * @author Jagy2014
 */
final class FieldQualityCheck {
    private static final String PREF="hearthborn/fields/confirm-better-quality";
    private static boolean confirm=Utils.getprefb(PREF,false);
    static boolean confirmation(){return confirm;}
    static void confirmation(boolean value){confirm=value;Utils.setprefb(PREF,value);}
    private double bestSeen=-1,loggedFloor=Double.POSITIVE_INFINITY,loggedRecord=-1;
    private double approvedFloor=Double.POSITIVE_INFINITY,approvedRecord=-1;

    static double recorded(CropProfile crop){
        String id=RosterStore.seedIdFromRes(crop.seed);double best=-1;
        for(RosterStore.Rec record:RosterStore.seeds())
            if(id.equals(record.name)||crop.id.equals(record.name))
                if(Double.isFinite(record.q))best=Math.max(best,record.q);
        return best;
    }
    /** Record only a usable batch of at least five equal-quality planting units. */
    static void recordPlanting(CropProfile crop,Collection<WheatFields.Lot> stock){
        Map<Double,Integer> counts=new TreeMap<>(Comparator.reverseOrder());
        for(WheatFields.Lot lot:stock)if(lot.count>=crop.seedsPerTile&&lot.quality>0)
            counts.merge(lot.quality,lot.count,Integer::sum);
        for(Map.Entry<Double,Integer> entry:counts.entrySet())if(entry.getValue()>=5){
            double quality=RosterStore.tenths(entry.getKey());
            if(quality<=recorded(crop))return;
            String id=RosterStore.seedIdFromRes(crop.seed);
            // Preserve an existing historical bare crop key rather than adding a duplicate.
            if(RosterStore.get(RosterStore.seedKey(id))==null&&RosterStore.get(RosterStore.seedKey(crop.id))!=null)id=crop.id;
            RosterStore.noteBestSeed(id,quality,entry.getValue());return;
        }
    }
    double betterRecord(CropProfile crop,double maximum){
        // Exhausting the best batch does not mean that a matching historical record
        // suddenly represents better stock elsewhere: that batch was already selected here.
        bestSeen=Math.max(bestSeen,maximum);double record=recorded(crop);
        return RosterStore.tenths(record)>RosterStore.tenths(bestSeen)?record:-1;
    }
    void report(GameUI gui,String field,CropProfile crop,double minimum,double maximum,double record){
        if(minimum>=loggedFloor&&record<=loggedRecord)return;
        String message=L10n.format("%s · %s: planting Q%.1f–%.1f, but the quality roster records Q%.1f.",field,crop.label(),minimum,maximum,record);
        if(gui.syslog!=null)gui.syslog.append(message,Color.WHITE);
        loggedFloor=Math.min(loggedFloor,minimum);loggedRecord=Math.max(loggedRecord,record);
    }
    boolean approved(double minimum,double record){return minimum>=approvedFloor&&record<=approvedRecord;}
    void approve(double minimum,double record){approvedFloor=Math.min(approvedFloor,minimum);approvedRecord=Math.max(approvedRecord,record);}

    static final class Prompt extends Window {
        private final java.util.function.Consumer<Boolean> answer;
        private boolean closed;
        Prompt(String field,CropProfile crop,double minimum,double maximum,double record,java.util.function.Consumer<Boolean> answer){
            super(Coord.z,"Better planting quality recorded");this.answer=answer;
            add(new Label(Text.std.ellipsize(field+" · "+crop.label(),UI.scale(400)).text));
            add(new Label(L10n.format("Planting reserve: Q%.1f–%.1f",minimum,maximum)),UI.scale(0,26));
            add(new Label(L10n.format("Quality roster record: Q%.1f",record)),UI.scale(0,50));
            add(new Label("Continue with this planting reserve?"),UI.scale(0,82));
            add(new Button(UI.scale(196),"Continue anyway",false,()->decide(true)),UI.scale(0,110));
            add(new Button(UI.scale(196),"Cancel",false,()->decide(false)),UI.scale(204,110));pack();
        }
        private void decide(boolean proceed){if(closed)return;close();answer.accept(proceed);}
        void close(){if(!closed){closed=true;hide();reqdestroy();}}
        public void reqclose(){decide(false);}
        public boolean keydown(KeyDownEvent event){if(event.code==KeyEvent.VK_ESCAPE){decide(false);return true;}return super.keydown(event);}
    }
}
