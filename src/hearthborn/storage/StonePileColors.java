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

package hearthborn.storage;

import haven.*;
import hearthborn.io.LocalStore;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import hearthborn.scripts.ShortcutRunner;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.*;
import haven.render.Pipe;

/**
 * Pile materials and item pictures learned after confirmed transfers.
 *
 * @author Jagy2014
 */
public final class StonePileColors {
    static final String METEORITE="gfx/invobjs/meteorite";
    private static final String CACHE="stone-pile-colors-v2";
    private static final Map<String,Sample> samples=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<GameUI,Transfer> transfers=new WeakHashMap<>();
    private static final Map<GameUI,Double> blocked=new WeakHashMap<>();
    private static final class Transfer {
        final Gob gob;final ISBox box;final int remaining,direction;final String resource;
        final Map<GItem,Integer> before=new IdentityHashMap<>();final GItem held;
        double until=Utils.rtime()+8;
        Transfer(GameUI gui,Gob gob,ISBox box,int direction){
            this.gob=gob;this.box=box;this.direction=direction;remaining=box.remaining();resource=box.resource().name;held=gui.vhand==null?null:gui.vhand.item;
            for(GItem item:items(gui))try{before.put(item,GameObjects.count(item));}catch(Loading waiting){before.put(item,Integer.MAX_VALUE);}
        }
        boolean matches(GItem item){return contentsPile(GameObjects.resourceName(gob))?item.resource().name.startsWith("gfx/invobjs/"):StonePileColors.matches(resource,item.resource().name);}
    }
    private static boolean loaded;
    private static final Set<String> stones=new HashSet<>();
    private static final Set<String> metals=new HashSet<>(Arrays.asList("castiron","wroughtiron","steel","copper","tin","bronze","lead","silver","gold","rosegold"));
    static boolean metal(String path){return path.startsWith("gfx/invobjs/bar-")&&metals.contains(path.substring("gfx/invobjs/bar-".length()));}
    private static final Set<String> clayTypes=new HashSet<>(Arrays.asList("acre","ball","bone","cave","gray","pit","potters","sea","riverbed","soap","coade"));
    static boolean ceramic(String path,String kind){
        String prefix="gfx/invobjs/"+kind+"-";
        return path.startsWith(prefix)&&clayTypes.contains(path.substring(prefix.length()));
    }
    private static boolean identifiable(String path){return stones.contains(path)||metal(path)||ceramic(path,"clay")||ceramic(path,"brick");}
    static {
        // Meteorite goes into ore and stone piles but has no mineable terrain tile.
        stones.add(METEORITE);
        Properties catalog=hearthborn.l10n.Catalog.load("/hearthborn/l10n/resource-names.properties");
        for(String path:catalog.stringPropertyNames())if(path.startsWith("gfx/tiles/rocks/")||path.startsWith("gfx/tiles/paving/"))stones.add("gfx/invobjs/"+path.substring(path.lastIndexOf('/')+1));
    }
    static boolean matches(String generic,String actual){
        return generic.equals(actual)||generic.equals("gfx/invobjs/clay")&&ceramic(actual,"clay")||generic.equals("gfx/invobjs/brick")&&ceramic(actual,"brick")||(generic.equals("gfx/invobjs/bar-metal")&&metal(actual))||(generic.equals("gfx/invobjs/stone")||generic.equals("gfx/invobjs/ore")||generic.startsWith("gfx/invobjs/ore-"))&&stones.contains(actual);
    }
    private static class Sample implements Serializable {
        private static final long serialVersionUID=1;
        final long gob;final String resource;final int color;final boolean actual;
        private transient Glob session;
        private transient long liveGob;
        private transient java.lang.ref.WeakReference<Gob> liveObject;
        private String pileType;
        synchronized boolean accepts(Gob target){
            String type=GameObjects.resourceName(target);
            if(target.removed||!supported(type))return false;
            if(pileType!=null&&!pileType.equals(type))return false;
            Gob previous=liveObject==null?null:liveObject.get();
            if(session!=null&&session==target.glob&&liveGob!=target.id&&previous!=null&&!previous.removed)return false;
            // The server may recreate a streamed-out pile with a new ID without reconnecting.
            session=target.glob;liveGob=target.id;pileType=type;
            if(previous!=target)liveObject=new java.lang.ref.WeakReference<>(target);
            return true;
        }
        Sample(long gob,String resource,Color color){this.gob=gob;this.resource=resource;this.color=color.getRGB();this.actual=true;}
    }
    private static volatile Boolean shown;
    public static boolean enabled(){if(shown==null)shown=Utils.getprefb("hearthborn/containers/stone-colors",true);return shown;}
    public static void enabled(boolean value){shown=value;Utils.setprefb("hearthborn/containers/stone-colors",value);ContainerLook.changed();}
    static boolean contentsPile(String resource){return "gfx/terobjs/stockpile-trash".equals(resource)||"gfx/terobjs/stockpile-flower".equals(resource)||"gfx/terobjs/stockpile-wool".equals(resource);}
    static boolean iconPile(String resource){return "gfx/terobjs/stockpile-ore".equals(resource)||contentsPile(resource);}
    static boolean supported(String resource){return "gfx/terobjs/stockpile-stone".equals(resource)||iconPile(resource)||"gfx/terobjs/stockpile-metal".equals(resource)||"gfx/terobjs/stockpile-clay".equals(resource)||"gfx/terobjs/stockpile-brick".equals(resource);}
    private static synchronized void load(){
        if(loaded)return;loaded=true;Object data=LocalStore.read(CACHE);
        if(data instanceof Map)for(Map.Entry<?,?> item:((Map<?,?>)data).entrySet())if(item.getKey() instanceof String&&item.getValue() instanceof Sample&&((Sample)item.getValue()).actual)samples.put((String)item.getKey(),(Sample)item.getValue());
    }
    private static void save(){LocalStore.write(CACHE,new HashMap<>(samples));}
    private static String key(Gob gob){
        if(gob.glob==null||gob.rc==null)return null;
        MCache.Grid grid=gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));Coord local=gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
        return grid.id+":"+local.x+":"+local.y;
    }
    static Color average(BufferedImage image){
        long red=0,green=0,blue=0,weight=0;
        long darkRed=0,darkGreen=0,darkBlue=0,darkWeight=0;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            int pixel=image.getRGB(x,y),a=pixel>>>24,r=(pixel>>>16)&255,g=(pixel>>>8)&255,b=pixel&255;
            if(a<128)continue;
            if(Math.max(r,Math.max(g,b))<25){darkRed+=(long)r*a;darkGreen+=(long)g*a;darkBlue+=(long)b*a;darkWeight+=a;continue;}
            red+=(long)r*a;green+=(long)g*a;blue+=(long)b*a;weight+=a;
        }
        if(weight==0){red=darkRed;green=darkGreen;blue=darkBlue;weight=darkWeight;}
        return weight==0?null:new Color((int)(red/weight),(int)(green/weight),(int)(blue/weight),185);
    }
    private static final Map<String,StoneSurface> surfaces=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> missingSurfaces=java.util.concurrent.ConcurrentHashMap.newKeySet();
    static String material(String identity){
        String path=identity.split("\n",2)[0];
        if(metal(path))return "gfx/terobjs/subst/"+path.substring("gfx/invobjs/bar-".length());
        if(!path.startsWith("gfx/invobjs/")||path.equals("gfx/invobjs/stone")||path.equals("gfx/invobjs/ore")||path.startsWith("gfx/invobjs/ore-"))return null;
        return "gfx/terobjs/items/"+path.substring(path.lastIndexOf('/')+1);
    }
    /** Resolve the material actually used by the loose item, including linked boulder materials. */
    static haven.render.Texture2D.Sampler2D looseTexture(Resource resource,int materialId,Set<String> visited){
        if(!visited.add(resource.name+":"+materialId))return null;
        for(Material.Res material:resource.layers(Material.Res.class))if(materialId<0||material.id==materialId){
            haven.render.BufPipe pipe=new haven.render.BufPipe();
            material.get().apply(pipe);
            haven.render.ColorTex color=pipe.get(haven.render.ColorTex.slot);
            if(color!=null)return color.data;
        }
        for(RenderLink.Res layer:resource.layers(RenderLink.Res.class)){
            haven.render.Texture2D.Sampler2D found=null;
            if(layer.l instanceof RenderLink.MeshMat){
                RenderLink.MeshMat link=(RenderLink.MeshMat)layer.l;
                found=looseTexture(link.mat.get(),link.matid,visited);
            }else if(layer.l instanceof RenderLink.ResSprite)
                found=looseTexture(((RenderLink.ResSprite)layer.l).res.get(),-1,visited);
            if(found!=null)return found;
        }
        TexR texture=resource.layer(TexR.class);
        return texture==null?null:texture.tex().img;
    }

    public static Pipe.Op surface(Gob gob){
        if(!enabled()||iconPile(GameObjects.resourceName(gob)))return null;load();
        try{
            String key=key(gob);Sample sample=key==null?null:samples.get(key);if(sample==null||!sample.accepts(gob))return null;
            String path=material(sample.resource);if(path==null||missingSurfaces.contains(path))return null;
            StoneSurface surface=surfaces.get(path);
            if(surface==null){Resource resource=Resource.remote().load(path).get();haven.render.Texture2D.Sampler2D texture=looseTexture(resource,-1,new HashSet<>());if(texture==null){missingSurfaces.add(path);return null;}surface=new StoneSurface(texture);surfaces.put(path,surface);}
            return surface;
        }catch(Loading waiting){return null;}catch(Resource.LoadException|Resource.LoadFailedException absent){return null;}
    }
    static String remembered(Gob gob){load();try{String key=key(gob);Sample sample=key==null?null:samples.get(key);return sample==null||!sample.accepts(gob)?null:sample.resource;}catch(Loading waiting){return null;}}
    private static void remember(Gob gob,GItem item){
        String type=GameObjects.resourceName(gob);
        String identity=item.resource().name;if(!contentsPile(type)&&(material(identity)==null||!identifiable(identity)))return;
        if(!identity.equals(METEORITE)&&(iconPile(type)||identity.equals("gfx/invobjs/slag"))){
            // Reuse the cupboard's actual rendered inventory image, not the generic ISBox icon.
            String icon=ContainerContents.itemIdentity(item);
            ContainerContents.savedPicture(icon); // Load persisted images before capturing a new one.
            if(ContainerContents.itemPicture(item)==null)throw new Loading();
            identity=icon;
        }
        rememberIdentity(gob,identity);
    }
    private static void rememberIdentity(Gob gob,String identity){
        String key=key(gob);if(key==null)return;load();Sample sample=new Sample(gob.id,identity,new Color(0,0,0,0));sample.accepts(gob);samples.put(key,sample);save();ContainerLook.changed();
    }
    public static void observe(Window window,GameUI gui){
        if(!"Stockpile".equals(window.cap)&&!hearthborn.L10n.text("window","Stockpile").equals(window.cap))return;
        Gob gob=ObjectWindows.target(window,gui,target->supported(GameObjects.resourceName(target)));if(gob==null)return;
        String key=key(gob);if(key==null)return;load();
        for(ISBox box:window.children(ISBox.class)){
            if(box.remaining()<=0){if(samples.remove(key)!=null){save();ContainerLook.changed();}return;}
            // Only a material-specific icon is evidence; a generic ore icon is not.
            if(METEORITE.equals(box.resource().name)&&!METEORITE.equals(remembered(gob)))rememberIdentity(gob,METEORITE);
            return;
        }
    }
    private static List<GItem> items(GameUI gui){
        List<GItem> result=new ArrayList<>(ShortcutRunner.owned(gui));
        if(gui.vhand!=null&&!result.contains(gui.vhand.item))result.add(gui.vhand.item);
        return result;
    }
    public static void transfer(Widget sender,String message,Object... args){
        if(!(sender instanceof ISBox)||!enabled())return;
        int direction=(message.equals("click")||message.equals("xfer")||message.equals("xfer2")&&args.length>0&&Utils.iv(args[0])<0)?-1:
            (message.equals("drop")||message.equals("iact")||message.equals("xfer2")&&args.length>0&&Utils.iv(args[0])>0)?1:0;
        if(direction==0)return;
        GameUI gui=sender.getparent(GameUI.class);Window window=sender.getparent(Window.class);
        if(gui==null||window==null||(!"Stockpile".equals(window.cap)&&!hearthborn.L10n.text("window","Stockpile").equals(window.cap))||Utils.rtime()<blocked.getOrDefault(gui,0.0))return;
        try{
            Gob gob=ObjectWindows.target(window,gui,target->supported(GameObjects.resourceName(target)));if(gob==null)return;
            tick(gui);
            Transfer old=transfers.get(gui);
            if(old!=null&&Utils.rtime()<old.until){
                if(old.gob==gob&&old.direction==direction){old.until=Utils.rtime()+8;return;}
                transfers.remove(gui);blocked.put(gui,Utils.rtime()+8);return; // Ambiguous simultaneous transfers: don't guess.
            }
            Transfer next=new Transfer(gui,gob,(ISBox)sender,direction);
            transfers.put(gui,next);

        }catch(Loading waiting){}catch(RuntimeException failure){transfers.remove(gui);new Warning(failure,"stone pile color observation failed").issue();}
    }
    public static void tick(GameUI gui){
        worldTick(gui);
        Transfer next=transfers.get(gui);if(next==null)return;
        if(next.gob.removed||Utils.rtime()>next.until){transfers.remove(gui);return;}
        int difference=next.box.remaining()-next.remaining;
        if(next.direction<0?difference>=0:difference<=0)return; // Wait for server confirmation.
        try{
            GItem sample=next.direction>0&&next.held!=null?ContainerContents.leaf(next.held):null;String identity=null;
            if(next.direction<0)for(GItem item:items(gui)){
                if(item.contents!=null&&item.contents.getClass().getName().endsWith("ItemStack"))continue;
                if(!next.matches(item)||GameObjects.count(item)<=next.before.getOrDefault(item,-1))continue;
                String found=ContainerContents.itemIdentity(item);
                if(identity!=null&&!identity.equals(found)){transfers.remove(gui);return;}
                sample=item;identity=found;
            }
            if(next.direction>0&&sample==null){
                Set<GItem> current=Collections.newSetFromMap(new IdentityHashMap<>());current.addAll(items(gui));
                for(Map.Entry<GItem,Integer> entry:next.before.entrySet()){
                    GItem item=entry.getKey();int before=entry.getValue();
                    if(before==Integer.MAX_VALUE||item.contents!=null&&item.contents.getClass().getName().endsWith("ItemStack"))continue;
                    if(!next.matches(item)||current.contains(item)&&GameObjects.count(item)>=before)continue;
                    String found=ContainerContents.itemIdentity(item);
                    if(identity!=null&&!identity.equals(found)){transfers.remove(gui);return;}
                    sample=item;identity=found;
                }
            }
            if(sample==null)return;
            remember(next.gob,sample);transfers.remove(gui);
        }catch(Loading waiting){}catch(RuntimeException failure){transfers.remove(gui);new Warning(failure,"stone pile color observation failed").issue();}
    }
    private static final Map<GameUI,GItem> selected=new WeakHashMap<>();
    private static final Map<GameUI,WorldTransfer> worlds=new WeakHashMap<>();
    private static class WorldTransfer {
        final GItem item,sample;final Gob target;final Coord2d at;final String pileType;final Set<Long> before=new HashSet<>();final int count;final double until=Utils.rtime()+15;
        WorldTransfer(GameUI gui,GItem item,Gob target,Coord2d at,String pileType){this.item=item;this.sample=ContainerContents.leaf(item);this.target=target;this.at=at;this.pileType=pileType;count=GameObjects.count(item);
            synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)if(gob.rc!=null&&gob.rc.dist(at)<11)before.add(gob.id);}
        }
    }
    public static void selected(Widget sender,String message){
        if(!(sender instanceof GItem)||!message.equals("iact")&&!message.equals("take"))return;
        GameUI gui=sender.getparent(GameUI.class);if(gui==null)return;
        try{if(((GItem)sender).resource().name.startsWith("gfx/invobjs/"))selected.put(gui,(GItem)sender);}catch(Loading waiting){}
    }
    private static void worldStart(MapView map,Gob target,Coord2d at,String pileType){
        if(!enabled())return;GameUI gui=map.getparent(GameUI.class);if(gui==null)return;
        try{GItem item=gui.vhand==null?selected.get(gui):gui.vhand.item;
            if(item==null)return;
            selected.put(gui,item);
            String resource=ContainerContents.leaf(item).resource().name;
            if(!contentsPile(pileType)&&(!identifiable(resource)||material(resource)==null))return;
            worlds.put(gui,new WorldTransfer(gui,item,target,at,pileType));
        }catch(Loading waiting){}
    }
    public static void placing(MapView map,Gob ghost,int button){
        try{String type=GameObjects.resourceName(ghost);if(button==1&&supported(type))worldStart(map,null,ghost.rc,type);}catch(Loading waiting){}
    }
    public static void itemact(MapView map,Gob target,Coord2d at){
        try{String type=GameObjects.resourceName(target);if(target==null||supported(type))worldStart(map,target,at,type);}catch(Loading waiting){}
    }
    private static void worldTick(GameUI gui){
        WorldTransfer transfer=worlds.get(gui);if(transfer==null)return;
        if(Utils.rtime()>transfer.until){worlds.remove(gui);return;}
        try{
            Gob target=transfer.target;
            if(target!=null){
                if(target.removed){worlds.remove(gui);return;}
                // A direct deposit must consume the selected item before changing the material.
                if(transfer.item.parent!=null&&GameObjects.count(transfer.item)>=transfer.count)return;
            }else{
                List<Gob> objects=new ArrayList<>();
                synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)objects.add(gob);}
                for(Gob gob:objects)if(!gob.removed&&gob.rc!=null&&!transfer.before.contains(gob.id)&&gob.rc.dist(transfer.at)<6){
                    String type=GameObjects.resourceName(gob);
                    if(supported(type)&&(transfer.pileType==null||transfer.pileType.equals(type))){if(target!=null)return;target=gob;}
                }
                if(target==null)return;
            }
            remember(target,transfer.sample);worlds.remove(gui);selected.remove(gui);
        }catch(Loading waiting){}catch(RuntimeException failure){worlds.remove(gui);new Warning(failure,"stone pile material observation failed").issue();}
    }
    private StonePileColors(){}
}
