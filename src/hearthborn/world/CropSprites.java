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
import haven.resutil.CSprite;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Keeps the resource's stage model, but draws a single plant at the tile centre.
 *
 * @author Jagy2014
 */
public final class CropSprites {
    private static final Set<Resource> crops = Collections.newSetFromMap(new WeakHashMap<>());

    private static volatile boolean simplified=Utils.getprefb("hearthborn/plants/simple",false);
    public static boolean simple() { return simplified; }
    public static void simple(boolean on) { simplified=on; Utils.setprefb("hearthborn/plants/simple", on); }
    public static synchronized boolean crop(Resource resource) { return crops.contains(resource); }

    private static boolean plant(Class<?> type) {
        for(Class<?> current = type; current != null; current = current.getSuperclass())
            if(current.getSimpleName().equals("GrowingPlant") || current.getSimpleName().equals("TrellisPlant"))
                return true;
        return false;
    }

    public static Sprite create(Sprite.Factory factory, Sprite.Owner owner, Resource resource, Message data) {
        if(!plant(factory.getClass())) return factory.create(owner, resource, data);
        synchronized(CropSprites.class) { crops.add(resource); }
        return new LiveCrop(factory,owner,resource,new MessageBuf(data));
    }

    /** Existing crops respond to the setting without waiting for a new growth stage. */
    private static final class LiveCrop extends Sprite {
        final Sprite.Factory factory;final MessageBuf data;
        final Map<RenderTree.Slot,RenderTree.Slot> slots=new LinkedHashMap<>();
        Sprite current;boolean simplified;
        LiveCrop(Sprite.Factory factory,Owner owner,Resource resource,MessageBuf data){
            super(owner,resource);this.factory=factory;this.data=data;
            simplified=simple();current=make(simplified);
        }
        private Sprite make(boolean simplified){
            if(simplified){
                RenderTree.Node part=centralPart(factory,data.clone());
                if(part!=null){
                    // Broad crops remain large even as a single plant. Keep their transform
                    // unlocked, like CSprite placements: it depends on the movable gob location.
                    if(compact(res.name))part=Location.scale(.5f).apply(part,false);
                    CSprite sprite=new CSprite(owner,res);sprite.addpart(0,0,Pipe.Op.nil,part);return sprite;
                }
            }
            return factory.create(owner,res,data.clone());
        }
        public void added(RenderTree.Slot slot){slots.put(slot,slot.add(current));}
        public void removed(RenderTree.Slot slot){slots.remove(slot);}
        public boolean tick(double dt){
            boolean nextMode=simple();
            if(nextMode!=simplified){
                Sprite next=null;
                try{
                    next=make(nextMode);next.age();
                    List<RenderTree.Slot> parents=new ArrayList<>(slots.keySet());
                    Collection<RenderTree.Slot> children=RUtils.multiadd(parents,next);
                    RUtils.multirem(new ArrayList<>(slots.values()));
                    Iterator<RenderTree.Slot> child=children.iterator();
                    for(RenderTree.Slot parent:parents)slots.put(parent,child.next());
                    Sprite previous=current;current=next;simplified=nextMode;previous.dispose();
                }catch(Loading waiting){if(next!=null)next.dispose();}
            }
            return current.tick(dt);
        }
        public void age(){current.age();}
        public void gtick(Render out){current.gtick(out);}
        public void dispose(){current.dispose();}
    }

    static boolean compact(String name){
        return name.equals("gfx/terobjs/plants/hemp")||name.equals("gfx/terobjs/plants/tobacco")
            ||name.equals("gfx/terobjs/plants/pipeweed")||name.equals("gfx/terobjs/plants/pumpkin");
    }

    static RenderTree.Node centralPart(Sprite.Factory factory, MessageBuf data) {
        if(data.eom()) return null;
        try {
            Field field = factory.getClass().getField("var");
            Object value = field.get(factory);
            if(!(value instanceof List)) return null;
            List<?> stages = (List<?>)value;
            int stage = data.uint8();
            if(stage >= stages.size() || !(stages.get(stage) instanceof List)) return null;
            List<?> variants = (List<?>)stages.get(stage);
            return !variants.isEmpty() && variants.get(0) instanceof RenderTree.Node ? (RenderTree.Node)variants.get(0) : null;
        } catch(ReflectiveOperationException e) {
            return null;
        }
    }

    private CropSprites() {}
}
