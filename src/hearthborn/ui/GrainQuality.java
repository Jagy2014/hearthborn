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
import hearthborn.QualityOverlay;
import hearthborn.quality.RosterStore;
import hearthborn.util.GameObjects;

/**
 * Adds the server-provided seed quality to the granary row label.
 *
 * @author Jagy2014
 */
public final class GrainQuality extends Widget {
    private final Widget row;
    private String base,last;
    private double elapsed;
    private GrainQuality(Widget row){super(Coord.z);this.row=row;}
    public static Widget attach(Widget row){row.add(new GrainQuality(row));return row;}
    /** The note that the quality journal knows better seeds of this crop; empty when it does not, or does not know the crop. */
    private static String better(Widget row,double quality){
        Indir<Resource> icon=GameObjects.field(row,"icon",Indir.class);
        if(icon==null)return "";
        try{
            String id=RosterStore.seedIdFromRes(icon.get().name);
            return hint(id==null?null:RosterStore.get(RosterStore.seedKey(id)),quality);
        }catch(Loading pending){return "";}
    }
    /** " ↑Q520" when the journal's record is better than this row by at least a tenth. */
    static String hint(RosterStore.Rec record,double quality){
        if(record==null||record.q<=0||RosterStore.tenths(record.q)<=RosterStore.tenths(quality)+0.05)return "";
        return " ↑Q"+QualityOverlay.format(RosterStore.tenths(record.q));
    }
    public void tick(double dt){
        super.tick(dt);if((elapsed+=dt)<.2)return;elapsed=0;
        if(!(row instanceof ItemInfo.Owner))return;
        Label label=GameObjects.field(row,"lbl",Label.class);if(label==null)return;
        if(!java.util.Objects.equals(label.texts,last))base=label.texts;
        if(base==null)return;
        try{
            Double quality=GameObjects.field(row,"rawinfo",ItemInfo.Raw.class)==null?null:QualityOverlay.qualityOf(((ItemInfo.Owner)row).info());
            String text=base+(quality==null?"":" - Q "+QualityOverlay.format(quality)+better(row,quality));
            if(!text.equals(label.texts))label.settext(text);
            last=text;
        }catch(Loading pending){}
    }
}
