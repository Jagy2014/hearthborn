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

package hearthborn.world;

import haven.*;
import haven.render.*;
import java.util.*;

/**
 * Hides whole kinds of objects by swapping their drawn model for nothing. The object itself, its state and its
 * hitbox stay, so it can still be clicked through and walked around; its outline can be drawn in its place.
 *
 * @author Jagy2014
 */
public final class ObjectHiding implements RenderTree.Node {
    /** One hideable kind of object and the resources that belong to it. */
    public enum Kind {
        BOULDERS("Boulders"), LOGS("Tree logs"), WALLS("Palisades and brick walls"), HOUSES("Houses"),
        STOCKPILES("Stockpiles"), CROPS("Crops"), TRELLISES("Trellises");
        public final String label;
        Kind(String label){this.label=label;}
        public boolean enabled(){return Utils.getprefb(PREFIX+name().toLowerCase(Locale.ROOT),false);}
        public void enabled(boolean value){Utils.setprefb(PREFIX+name().toLowerCase(Locale.ROOT),value);}
    }
    private static final String PREFIX="hearthborn/hide/";
    private static final String[] HOUSES={"logcabin","timberhouse","stonestead","stonemansion","greathall","stonetower","windmill","primitivetent","greenhouse"};
    private static volatile boolean master=Utils.getprefb(PREFIX+"on",false);
    private static volatile boolean boxes=Utils.getprefb(PREFIX+"boxes",true);

    public static boolean on(){return master;}
    public static void on(boolean value){master=value;Utils.setprefb(PREFIX+"on",value);}
    public static void toggle(){on(!master);}
    public static boolean boxes(){return boxes;}
    public static void boxes(boolean value){boxes=value;Utils.setprefb(PREFIX+"boxes",value);}

    /** The kind an object belongs to, or null when it is not hideable at all. */
    public static Kind kind(String res){
        if(res.startsWith("gfx/terobjs/bumlings"))return Kind.BOULDERS;
        if(res.startsWith("gfx/terobjs/trees/")&&(res.endsWith("log")||res.endsWith("oldtrunk")))return Kind.LOGS;
        if((res.startsWith("gfx/terobjs/arch/palisade")||res.startsWith("gfx/terobjs/arch/brickwall"))&&!res.endsWith("gate"))return Kind.WALLS;
        if(res.startsWith("gfx/terobjs/arch/"))for(String house:HOUSES)if(res.equals("gfx/terobjs/arch/"+house))return Kind.HOUSES;
        if(res.startsWith("gfx/terobjs/stockpile"))return Kind.STOCKPILES;
        if(res.endsWith("trellis")&&res.startsWith("gfx/terobjs/plants"))return Kind.TRELLISES;
        if(res.startsWith("gfx/terobjs/plants")&&!res.endsWith("giantturnip"))return Kind.CROPS;
        return null;
    }
    /** Whether an object of this resource is hidden right now. */
    public static boolean hidden(String res){
        if(!master)return false;
        Kind kind=kind(res);
        return kind!=null&&kind.enabled();
    }
    /** Hidden objects whose outline is drawn instead; crops have no hitbox to draw. */
    public static boolean outlined(String res){
        return boxes&&hidden(res)&&kind(res)!=Kind.CROPS;
    }

    private final ResDrawable drawable;
    private final Map<RenderTree.Slot,RenderTree.Slot> slots=new LinkedHashMap<>();
    private RenderTree.Node current;
    private double elapsed;
    private ObjectHiding(ResDrawable drawable){
        this.drawable=drawable;this.current=hidden(drawable.rres.name)?null:drawable.spr;
        this.elapsed=-((System.identityHashCode(drawable)&15)*(0.1/16.0));
    }
    public static RenderTree.Node visual(ResDrawable drawable){
        return kind(drawable.rres.name)!=null?new ObjectHiding(drawable):drawable.spr;
    }
    public void added(RenderTree.Slot slot){if(current!=null)slots.put(slot,slot.add(current));else slots.put(slot,null);}
    public void removed(RenderTree.Slot slot){slots.remove(slot);}
    public static void tick(RenderTree.Node node,double dt){
        if(node instanceof ObjectHiding)((ObjectHiding)node).tick(dt);
    }
    private void tick(double dt){
        if((elapsed+=dt)<.1)return;
        elapsed=0;
        RenderTree.Node next=hidden(drawable.rres.name)?null:drawable.spr;
        if(next==current)return;
        try{
            List<RenderTree.Slot> parents=new ArrayList<>(slots.keySet());
            List<RenderTree.Slot> old=new ArrayList<>();
            for(RenderTree.Slot child:slots.values())if(child!=null)old.add(child);
            Iterator<RenderTree.Slot> children=null;
            if(next!=null)children=RUtils.multiadd(parents,next).iterator();
            RUtils.multirem(old);
            slots.clear();
            for(RenderTree.Slot parent:parents)slots.put(parent,children==null?null:children.next());
            current=next;
        }catch(Loading ignored){}
    }
    public static void dispose(RenderTree.Node node){}
}
