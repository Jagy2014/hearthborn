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
import hearthborn.util.GameObjects;
import java.util.*;

/**
 * Crop rules for the shared harvest/reserve/sow/store engine, independent of saved class names.
 *
 * @author Jagy2014
 */
public final class CropProfile {
    static final String AUTO="auto";
    static final CropProfile WHEAT=new CropProfile("wheat","Wheat","straw",5);
    static final CropProfile CARROT=new CropProfile("carrot","Carrot","carrot",1,true);
    static final CropProfile TURNIP=new CropProfile("turnip","Turnip","turnip",1,true);
    static final CropProfile RED_ONION=new CropProfile("redonion","Red Onion","redonion",1,true,"onion");
    static final CropProfile YELLOW_ONION=new CropProfile("yellowonion","Yellow Onion","yellowonion",1,true,"onion");
    static final CropProfile GARLIC=new CropProfile("garlic","Garlic","garlic",1,true,"onion");
    static final CropProfile LEEK=new CropProfile("leek","Leek","leek",1,true,"leek",Coord.of(1,2));
    static final List<CropProfile> ALL=Collections.unmodifiableList(Arrays.asList(WHEAT,
        new CropProfile("barley","Barley","straw",5),
        new CropProfile("flax","Flax","flaxfibre",5),
        new CropProfile("pipeweed","Pipeweed","tobacco-fresh",5,false,"pipeleaves"),
        new CropProfile("hemp","Hemp (seeds)","hempfibre",5),CARROT,TURNIP,RED_ONION,YELLOW_ONION,GARLIC,LEEK,
        // Poppy drops its flowers on the ground, and pods with them; the flowers go to a flower pile.
        new CropProfile("poppy","Poppy","flower-poppy",5,false,"flower").also("poppypod")));
    final String id,name,crop,seed,product,ground,pile;
    final int seedsPerTile;
    final boolean rootCrop;
    final Coord seedSize;
    /** Inventory names of what else the harvest drops on the ground; they are picked up and kept in the seed storage, not piled. */
    List<String> extras=Collections.emptyList();
    private CropProfile also(String... names){extras=Collections.unmodifiableList(Arrays.asList(names));return this;}
    /** Whether a ground object is this crop's harvest or one of the other things it drops. */
    boolean dropsOnGround(String resource){
        if(resource==null)return false;
        if(ground.equals(resource))return true;
        for(String name:extras)if(resource.equals("gfx/terobjs/items/"+name))return true;
        return false;
    }
    /** The inventory item a ground object turns into when picked up. */
    String carried(String resource){
        if(resource==null||ground.equals(resource))return product;
        return resource.startsWith("gfx/terobjs/items/")?"gfx/invobjs/"+resource.substring("gfx/terobjs/items/".length()):product;
    }
    /** The inventory resources of the extra drops. */
    List<String> extraItems(){List<String> result=new ArrayList<>();for(String name:extras)result.add("gfx/invobjs/"+name);return result;}
    private CropProfile(String id,String name,String product,int seedsPerTile){
        this(id,name,product,seedsPerTile,false);
    }
    private CropProfile(String id,String name,String product,int seedsPerTile,boolean rootCrop){
        this(id,name,product,seedsPerTile,rootCrop,product);
    }
    private CropProfile(String id,String name,String product,int seedsPerTile,boolean rootCrop,String pile){
        this(id,name,product,seedsPerTile,rootCrop,pile,Coord.of(1,1));
    }
    private CropProfile(String id,String name,String product,int seedsPerTile,boolean rootCrop,String pile,Coord seedSize){
        this.seedSize=seedSize;
        this.id=id;this.name=name;this.rootCrop=rootCrop;this.crop="gfx/terobjs/plants/"+id;
        this.seed="gfx/invobjs/"+(rootCrop?product:"seed-"+id);
        this.product="gfx/invobjs/"+product;this.ground="gfx/terobjs/items/"+product;this.pile="gfx/terobjs/stockpile-"+pile;
        this.seedsPerTile=seedsPerTile;
    }
    String label(){return L10n.tr(name);}
    /** Canonical inventory basename, shared with the quality roster's classifier. */
    public static boolean isPlantingMaterial(String id){
        if(id==null)return false;
        for(CropProfile crop:ALL)if(crop.seed.substring(crop.seed.lastIndexOf('/')+1).equals(id))return true;
        return false;
    }
    static CropProfile get(String id){for(CropProfile profile:ALL)if(profile.id.equals(id))return profile;return null;}
    static CropProfile require(String id){CropProfile profile=get(id);if(profile==null)throw new IllegalStateException("Choose a crop for the empty field before starting.");return profile;}
    static CropProfile plant(String resource){for(CropProfile profile:ALL)if(profile.crop.equals(resource))return profile;return null;}
    static final class Detection {
        final CropProfile crop;
        final boolean mixed;
        Detection(CropProfile crop,boolean mixed){this.crop=crop;this.mixed=mixed;}
    }
    /** A resource-less or overlay-only object is not an unloaded plant.
     * Real resource lookup failures still propagate Loading to the caller. */
    static String plantResource(Gob gob){
        if(gob.removed||gob.virtual||gob.getattr(Following.class)!=null)return null;
        String path=GameObjects.resourceName(gob);
        return path!=null&&path.startsWith("gfx/terobjs/plants/")?path:null;
    }
    /** Mixed areas have no automatic choice; the player selects their planting crop. */
    static Detection inspect(GameUI gui,Area area){
        OCache cache=gui.ui.sess.glob.oc;List<Gob> objects=new ArrayList<>();
        synchronized(cache){for(Gob gob:cache)objects.add(gob);}
        Set<String> plants=new LinkedHashSet<>();
        for(Gob gob:objects)if(!gob.virtual&&FieldTask.inside(gob,area)){
            String path=plantResource(gob);if(path==null)continue;
            plants.add(path);
        }
        if(plants.size()!=1)return new Detection(null,plants.size()>1);
        String path=plants.iterator().next();CropProfile detected=plant(path);
        if(detected==null)throw new IllegalStateException(L10n.format("This crop is not supported by field processing yet: %s.",GameObjects.name(path)));
        return new Detection(detected,false);
    }
}
