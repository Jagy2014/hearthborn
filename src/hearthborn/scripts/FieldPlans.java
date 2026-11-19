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

import haven.GameUI;
import haven.Utils;
import hearthborn.L10n;
import java.util.*;

/**
 * Read-only saved field geometry, anchored to stable grid IDs for claim plans.
 *
 * @author Jagy2014
 */
public final class FieldPlans {
    public static final class Field {
        public final String id,name,crop;
        public final int number,x,y,width,height;
        public final long grid;
        public final int qualityTiles;
        public final double minimumQuality,maximumQuality;
        private final String title;
        private Field(WheatFields.Field field){
            SavedArea area=field.land;
            this.id=field.id;this.name=field.title();this.number=field.number;title=field.title();grid=area.grid;
            x=area.x;y=area.y;width=area.width;height=area.height;
            CropProfile profile=CropProfile.get(field.cropId);crop=profile==null?null:profile.label();
            int count=0;double minimum=Double.POSITIVE_INFINITY,maximum=0;
            if(field.plantedMinQ!=null&&field.plantedMaxQ!=null&&field.plantedMinQ.length==field.tiles()&&field.plantedMaxQ.length==field.tiles())
                for(int index=field.planted.nextSetBit(0);index>=0&&index<field.tiles();index=field.planted.nextSetBit(index+1)){
                    double low=field.plantedMinQ[index],high=field.plantedMaxQ[index];
                    if(Double.isFinite(low)&&Double.isFinite(high)&&low>0&&high>=low){minimum=Math.min(minimum,low);maximum=Math.max(maximum,high);count++;}
                }
            qualityTiles=count;minimumQuality=count==0?0:minimum;maximumQuality=maximum;
        }
        public String heading(){return number+(crop==null?"":" · "+crop);}
        public String quality(){
            if(qualityTiles==0)return "";
            String low=Utils.odformat(Math.round(minimumQuality*10)/10.0,1),high=Utils.odformat(Math.round(maximumQuality*10)/10.0,1);
            return L10n.format("Sown Q %s",low.equals(high)?low:low+"–"+high);
        }
        public String label(){return heading()+(qualityTiles==0?"":" · "+quality());}
        public String tooltip(){
            return title+(crop==null?"":" · "+crop)+" · "+width+"×"+height+"\n"
                +(qualityTiles==0?L10n.tr("Planting quality has not been recorded yet.")
                    :quality()+"\n"+L10n.format("Planting quality is known for %d of %d tiles.",qualityTiles,width*height));
        }
    }
    public static List<Field> list(GameUI gui){
        if(SavedArea.key(gui,"wheat-fields")==null)return Collections.emptyList();
        try{
            WheatFields book=WheatFields.load(gui);List<Field> result=new ArrayList<>();
            for(int index=0;index<book.fields.size();index++){
                WheatFields.Field field=book.fields.get(index);
                if(field.land!=null)result.add(new Field(field));
            }
            return Collections.unmodifiableList(result);
        }catch(IllegalStateException unavailable){return Collections.emptyList();}
    }
    private FieldPlans(){}
}
