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
import hearthborn.io.LocalStore;
import java.io.Serializable;
import java.util.*;

/**
 * Historical serialized identity retained for saved fields; rules now come from CropProfile.
 *
 * @author Jagy2014
 */
final class WheatFields implements Serializable {
    private static final long serialVersionUID=1L;
    enum Phase {READY,HARVEST,PLANT,COLLECT,STORE,DONE}
    static final int SEEDS_PER_TILE=5,MAX_TILES=256;
    static final class Field implements Serializable {
        private static final long serialVersionUID=1L;
        final String id=UUID.randomUUID().toString();
        String name;
        int number;
        String cropId=CropProfile.AUTO;
        boolean cropPinned;
        SavedArea land,seeds,straw;
        Phase phase=Phase.READY;
        boolean harvestOnly;
        long lastHarvest;
        final BitSet harvested=new BitSet(),planted=new BitSet();
        double[] plantedMinQ,plantedMaxQ;
        int pending=-1;
        Field(String name){this.name=name;}
        int tiles(){return land==null?0:Math.multiplyExact(land.width,land.height);}
        int needed(){return (tiles()-planted.cardinality())*CropProfile.require(cropId).seedsPerTile;}
        String title(){return Integer.toString(number);}
        String summary(){CropProfile crop=CropProfile.get(cropId);return title()+" · "+(crop==null?hearthborn.L10n.tr("Choose crop"):crop.label())+(land==null?"":" · "+land.width+"×"+land.height);}
        void reset(){phase=Phase.READY;harvestOnly=false;harvested.clear();planted.clear();plantedMinQ=plantedMaxQ=null;pending=-1;}
        void notePlantingQuality(BitSet cells,double minimum,double maximum){
            if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<=0||maximum<minimum)return;
            if(plantedMinQ==null||plantedMaxQ==null||plantedMinQ.length!=tiles()||plantedMaxQ.length!=tiles()){
                plantedMinQ=new double[tiles()];plantedMaxQ=new double[tiles()];
            }
            for(int index=cells.nextSetBit(0);index>=0&&index<tiles();index=cells.nextSetBit(index+1)){
                plantedMinQ[index]=minimum;plantedMaxQ[index]=maximum;
            }
        }
        Coord tile(Area area,int index){return area.ul.add(index%land.width,index/land.width);}
    }
    final List<Field> fields=new ArrayList<>();
    String selected;
    long revision;
    static WheatFields load(GameUI gui){
        String key=SavedArea.key(gui,"wheat-fields");
        if(key==null)throw new IllegalStateException("Character and world are not ready yet.");
        Object raw=LocalStore.read(key);
        if(raw==null)return new WheatFields();
        if(!(raw instanceof WheatFields))throw new IllegalStateException("Could not read the saved wheat fields.");
        WheatFields book=(WheatFields)raw;
        if(book.fields==null||book.fields.size()>100)throw new IllegalStateException("Could not read the saved wheat fields.");
        for(Field field:book.fields)if(field==null||field.name==null||field.phase==null||field.harvested==null||field.planted==null
            ||field.land!=null&&(field.land.width<=0||field.land.height<=0||(long)field.land.width*field.land.height>MAX_TILES
                ||field.harvested.length()>field.tiles()||field.planted.length()>field.tiles()))
            throw new IllegalStateException("Could not read the saved wheat fields.");
        // Missing in old serialized records: those fields were exclusively wheat.
        for(Field field:book.fields){
            if(field.cropId==null)field.cropId=CropProfile.WHEAT.id;
            if(!CropProfile.AUTO.equals(field.cropId)&&CropProfile.get(field.cropId)==null)
                throw new IllegalStateException("The saved field uses an unknown crop profile.");
        }
        book.normalizeNumbers();return book;
    }
    void save(GameUI gui){
        String key=SavedArea.key(gui,"wheat-fields");
        if(key==null)throw new IllegalStateException("Character and world are not ready yet.");
        normalizeNumbers();revision++;LocalStore.write(key,this);
        Object written=LocalStore.read(key);
        if(!(written instanceof WheatFields)||((WheatFields)written).revision!=revision)
            throw new IllegalStateException("Could not save the area. Check the client data directory.");
    }
    /** Old saves have no number. Reserve existing valid numbers before filling gaps. */
    private void normalizeNumbers(){
        Set<Integer> used=new HashSet<>();
        for(Field field:fields)if(field.number<=0||!used.add(field.number))field.number=0;
        int next=1;
        for(Field field:fields)if(field.number==0){while(used.contains(next))next++;field.number=next;used.add(next);}
    }
    int nextNumber(){normalizeNumbers();Set<Integer> used=new HashSet<>();for(Field field:fields)used.add(field.number);int next=1;while(used.contains(next))next++;return next;}
    void renumber(Field field,int number){
        if(number<=0)throw new IllegalStateException("Enter a positive whole field number.");
        for(Field other:fields)if(other!=field&&other.number==number)
            throw new IllegalStateException(hearthborn.L10n.format("Field number %d is already in use.",number));
        field.number=number;
    }
    List<Field> ordered(){List<Field> result=new ArrayList<>(fields);result.sort(Comparator.comparingInt(field->field.number));return result;}
    Field selected(){for(Field field:fields)if(field.id.equals(selected))return field;return fields.isEmpty()?null:fields.get(0);}
    /** Match the player's field by stable grid identity, even at a partially loaded edge. */
    Field here(GameUI gui){
        if(gui.map==null||gui.map.player()==null||gui.ui==null||gui.ui.sess==null)return null;
        Coord tile=gui.map.player().rc.floor(MCache.tilesz);Field found=null;
        for(Field field:fields){
            Area bounds=field.land==null?null:field.land.bounds(gui.ui.sess.glob.map);
            if(bounds!=null&&bounds.contains(tile)){
                if(found!=null)throw new IllegalStateException("Saved fields overlap here. Choose a field from the list.");
                found=field;
            }
        }
        return found;
    }
    static SavedArea capture(GameUI gui,Area area,boolean field){
        if(!area.positive()||(long)area.sz().x*area.sz().y>(field?MAX_TILES:1024))
            throw new IllegalStateException("The selected area is empty or too large.");
        SavedArea saved=SavedArea.capture(gui.ui.sess.glob.map,area);
        // Request every covered grid; resolve alone only inspects the loaded snapshot.
        Coord first=area.ul.div(MCache.cmaps),last=area.br.sub(1,1).div(MCache.cmaps);
        boolean waiting=false;
        for(int y=first.y;y<=last.y;y++)for(int x=first.x;x<=last.x;x++)try{
            gui.ui.sess.glob.map.getgridt(Coord.of(x,y).mul(MCache.cmaps));
        }catch(Loading pending){waiting=true;}
        if(waiting)throw new Loading();
        if(saved.resolve(gui.ui.sess.glob.map)==null)throw new Loading();
        return saved;
    }
    /** A planting batch cannot use fewer than five seeds from a native packet. */
    static final class Lot {
        final double quality;final int count;
        Lot(double quality,int count){
            if(!Double.isFinite(quality)||quality<0||count<=0)throw new IllegalArgumentException("Invalid wheat seed lot");
            this.quality=quality;this.count=count;
        }
    }
    static List<Lot> reserve(List<Lot> available,int tiles){
        return reserve(available,tiles,SEEDS_PER_TILE);
    }
    static List<Lot> reserve(List<Lot> available,int tiles,int seedsPerTile){
        return reserve(available,tiles,seedsPerTile,false);
    }
    static List<Lot> reserveAvailable(List<Lot> available,int tiles,int seedsPerTile){
        return reserve(available,tiles,seedsPerTile,true);
    }
    private static List<Lot> reserve(List<Lot> available,int tiles,int seedsPerTile,boolean partial){
        if(tiles<0||tiles>MAX_TILES)throw new IllegalArgumentException("Invalid wheat field size");
        if(seedsPerTile<=0)throw new IllegalArgumentException("Invalid planting quantity");
        List<Lot> ordered=new ArrayList<>(available),result=new ArrayList<>();
        ordered.sort(Comparator.comparingDouble((Lot lot)->lot.quality).reversed());
        int remaining=tiles;
        for(Lot lot:ordered){
            int plants=Math.min(remaining,lot.count/seedsPerTile);
            if(plants>0){result.add(new Lot(lot.quality,plants*seedsPerTile));remaining-=plants;}
        }
        if(remaining>0&&!partial)throw new IllegalStateException(hearthborn.L10n.format("Not enough plantable seeds for the whole field. Each packet needs at least %d seeds.",seedsPerTile));
        return result;
    }
}
