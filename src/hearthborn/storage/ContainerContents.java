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
import hearthborn.world.ObjectNames;
import hearthborn.world.WorldLabels;
import hearthborn.world.WorldIcon;

import haven.*;
import haven.render.*;
import hearthborn.io.LocalStore;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/**
 * Last observed cupboard/cabinet contents, keyed by map position rather than session IDs.
 *
 * @author Jagy2014
 */
public final class ContainerContents extends GAttrib implements RenderTree.Node {
    private static final Map<String,String> contents=new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean loaded;
    private static final Map<String,byte[]> images=new java.util.concurrent.ConcurrentHashMap<>();
    private static String identity(GItem item){
        ItemInfo.Name name=ItemInfo.find(ItemInfo.Name.class,item.info());
        return item.resource().name+"\n"+(name==null?"":name.str.text.replaceFirst(", stack of$",""))+"\n"+Base64.getEncoder().encodeToString(item.sdt.bytes());
    }
    private static final Set<String> pending=new HashSet<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> captured=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static void drain(){Runnable task;while((task=captured.poll())!=null)task.run();}
    private static void storeImage(String id,BufferedImage image){
        try{ByteArrayOutputStream data=new ByteArrayOutputStream();ImageIO.write(image,"png",data);images.put(id,data.toByteArray());
            LocalStore.write("cupboard-icon-images",new HashMap<>(images));
        }catch(IOException error){throw new IllegalStateException("Could not save cupboard icon",error);}
    }
    private static void snapshot(GItem item,String id){
        drain();if(images.containsKey(id)||pending.contains(id))return;
        GSprite sprite=item.spr();if(sprite==null)throw new Loading();
        List<GItem.InfoOverlay<?>> overlays=new ArrayList<>();
        for(ItemInfo info:item.info())if(info instanceof GItem.OverlayInfo && !(info instanceof GItem.NumberInfo))
            overlays.add(GItem.InfoOverlay.create((GItem.OverlayInfo<?>)info));
        if(item.ui!=null && item.ui.getenv()!=null && (!(sprite instanceof GSprite.ImageSprite)||!overlays.isEmpty())){
            haven.rs.DrawBuffer buffer=new haven.rs.DrawBuffer(item.ui.getenv(),sprite.sz());
            try{
                GOut graphics=buffer.graphics();
                graphics.out.clear(graphics.basicstate(),FragColor.fragcol,new FColor(0,0,0,0));
                sprite.draw(graphics);for(GItem.InfoOverlay<?> overlay:overlays)overlay.draw(graphics);
                GOut.getimage(graphics.out,buffer.color.image(0),true,image->captured.add(()->{
                    try{storeImage(id,image);}finally{pending.remove(id);buffer.dispose();}
                }));
                pending.add(id);buffer.env.submit(graphics.out);
            }catch(RuntimeException failure){pending.remove(id);buffer.dispose();throw failure;}
            return;
        }
        BufferedImage image=sprite instanceof GSprite.ImageSprite?((GSprite.ImageSprite)sprite).image():null;
        if(image==null){Resource.Image layer=item.resource().layer(Resource.imgc);if(layer!=null)image=layer.img;}
        if(image==null)throw new Loading();storeImage(id,image);
    }
    public static String itemIdentity(GItem item){return identity(item);}
    /** Capture the rendered inventory sprite, including material-dependent stone colors. */
    public static BufferedImage itemPicture(GItem item){
        String id=identity(item);snapshot(item,id);byte[] bytes=images.get(id);if(bytes==null)throw new Loading();
        try{return ImageIO.read(new ByteArrayInputStream(bytes));}catch(IOException invalid){return null;}
    }
    private static GItem leaf(GItem item){
        if(item.contents!=null && item.contents.getClass().getName().endsWith("ItemStack"))
            for(GItem child:item.contents.children(GItem.class))return leaf(child);
        return item;
    }
    private static GItem matching(Widget widget,String id){
        for(Widget child:widget.children())if(child instanceof GItem){
            GItem item=(GItem)child;
            if(item.contents!=null && item.contents.getClass().getName().endsWith("ItemStack")){
                GItem found=matching(item.contents,id);if(found!=null)return found;
            }else if(identity(item).equals(id))return item;
        }
        return null;
    }
    private static final Map<String,String> manual=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Glob,UI> interfaces=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Window,String> controls=new WeakHashMap<>();
    private static final Map<GameUI,Window> selecting=new WeakHashMap<>();
    public static void ui(GameUI gui){drain();interfaces.put(gui.ui.sess.glob,gui.ui);}
    private static volatile int iconMode=-1;
    /** Icons now appear only while Space is held. The earlier "always" choice is replaced once, and can be changed again in the settings. */
    private static synchronized int loadMode(){
        if(iconMode>=0)return iconMode;
        if(!Utils.getprefb("hearthborn/containers/icon-mode-space-default",false)){
            Utils.setprefi("hearthborn/containers/icon-mode",1);Utils.setprefb("hearthborn/containers/icon-mode-space-default",true);
        }
        return iconMode=Utils.getprefi("hearthborn/containers/icon-mode",1);
    }
    /** Read on every tick of every container, so it is cached: a preference lookup is a native call on macOS. */
    public static int mode(){int cached=iconMode;return cached>=0?cached:loadMode();}
    public static void mode(int mode){loadMode();iconMode=mode;Utils.setprefi("hearthborn/containers/icon-mode",mode);}
    public static String modeLabel(){return hearthborn.L10n.tr(new String[]{"Container icons: always","Container icons: hold Space","Container icons: hidden"}[Math.floorMod(mode(),3)]);}
    public static boolean allowed(Glob glob){return allowed(interfaces.get(glob));}
    public static boolean spaceHeld(Glob glob){UI ui=interfaces.get(glob);return ui!=null&&ui.spaceHeld;}
    public static boolean allowed(UI ui){int mode=mode();return mode==0 || mode==1 && ui!=null && ui.spaceHeld;}
    private static void saveManual(){LocalStore.write("cupboard-icons",new HashMap<>(manual));}
    public static boolean cancel(GameUI gui){return selecting.remove(gui)!=null;}
    public static boolean pick(WItem view){
        GameUI gui=view.getparent(GameUI.class);Window window=selecting.get(gui);
        if(window==null)return false;
        if(window.parent==null||!window.visible()){selecting.remove(gui);return false;}
        try{String key=controls.get(window);if(key==null)return false;
            GItem item=leaf(view.item);String id=identity(item);snapshot(item,id);
            manual.put(key,id);saveManual();selecting.remove(gui);
            gui.ui.msg(hearthborn.L10n.tr("Container icon saved."));
        }catch(Loading loading){}return true;
    }
    private static String windowResource(Window window){
        if(window==null||window.cap==null)return null;
        for(String[] type:new String[][]{{"Cupboard","gfx/terobjs/cupboard"},{"Metal Cabinet","gfx/terobjs/metalcabinet"}})
            for(String caption:new String[]{type[0],hearthborn.L10n.text("window",type[0]),hearthborn.L10n.tr(type[0])})
                if(window.cap.equals(caption)||window.cap.startsWith(caption+" "))return type[1];
        return null;
    }
    public static boolean supported(Window window){return windowResource(window)!=null;}
    public static void choose(Window window){
        GameUI gui=window.getparent(GameUI.class);if(gui==null)return;
        if(selecting.get(gui)==window){cancel(gui);return;}
        selecting.put(gui,window);gui.ui.msg(hearthborn.L10n.tr("Click an item to choose the container icon. Escape cancels."));
    }
    public static void automatic(Window window){
        String key=controls.get(window);if(key==null)return;
        manual.remove(key);saveManual();cancel(window.getparent(GameUI.class));
    }
    private String shown;
    private Tex icon;
    private byte[] shownImage;
    private double refresh;
    private final WorldIcon billboard=new WorldIcon(17.5f,.8,true,()->allowed(interfaces.get(gob.glob)));

    public ContainerContents(Gob gob){super(gob);refresh=(gob.id&15)*(0.5/16.0);}
    private static synchronized void load(){
        if(loaded)return;loaded=true;
        Object pictures=LocalStore.read("cupboard-icon-images");
        if(pictures instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)pictures).entrySet())
            if(entry.getKey() instanceof String && entry.getValue() instanceof byte[])images.put((String)entry.getKey(),(byte[])entry.getValue());
        Object custom=LocalStore.read("cupboard-icons");
        if(custom instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)custom).entrySet())
            if(entry.getKey() instanceof String && entry.getValue() instanceof String)manual.put((String)entry.getKey(),(String)entry.getValue());
        Object saved=LocalStore.read("cupboard-contents");
        if(saved instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)saved).entrySet())
            if(entry.getKey() instanceof String && entry.getValue() instanceof String)
                contents.put((String)entry.getKey(),(String)entry.getValue());
    }
    private static boolean iconContainer(Gob gob){
        Drawable draw=gob.getattr(Drawable.class);Resource res=draw==null?null:draw.getres();
        return res!=null && (res.name.equals("gfx/terobjs/cupboard")||res.name.equals("gfx/terobjs/metalcabinet"));
    }
    private static void count(GItem item,Map<String,Long> totals){
        if(item.contents!=null && item.contents.getClass().getName().endsWith("ItemStack")){
            for(GItem child:item.contents.children(GItem.class))count(child,totals);
        }else totals.merge(identity(item),(long)GameObjects.count(item),Long::sum);
    }
    static String dominant(Inventory inventory,String previous){
        Map<String,Long> totals=new TreeMap<>();
        for(Widget child:inventory.children())if(child instanceof GItem)count((GItem)child,totals);
        String best=null;long maximum=0;
        for(Map.Entry<String,Long> entry:totals.entrySet())if(entry.getValue()>maximum){best=entry.getKey();maximum=entry.getValue();}
        return maximum>0 && previous!=null && totals.getOrDefault(previous,0L)==maximum?previous:best;
    }
    public static void observe(Window window,GameUI gui){
        String resource=windowResource(window);if(resource==null)return;
        Gob gob=ObjectWindows.target(window,gui,target->resource.equals(GameObjects.resourceName(target)));
        Inventory inventory=window.findchild(Inventory.class);
        if(gob==null||inventory==null)return;
        String key=ObjectNames.key(gob);if(key==null)return;load();
        controls.put(window,key);
        String next=dominant(inventory,contents.get(key));
        if(next!=null){GItem item=matching(inventory,next);if(item!=null)snapshot(item,next);}
        if(Objects.equals(next,contents.get(key)))return;
        if(next==null)contents.remove(key);else contents.put(key,next);
        LocalStore.write("cupboard-contents",new HashMap<>(contents));
    }
    public void ctick(double dt){
        billboard.sync();
        refresh-=dt;if(refresh>0)return;refresh=.5;
        try{
            if(!iconContainer(gob))return;
            billboard.surface(gob.getattr(Drawable.class).getres());
            String key=ObjectNames.key(gob);if(key==null)return;load();String next=manual.getOrDefault(key,contents.get(key));
            if(Objects.equals(next,shown)&&shownImage==(next==null?null:images.get(next)))return;
            Tex replacement=null;
            if(next!=null){
                BufferedImage image=null;byte[] bytes=images.get(next);
                if(bytes!=null)try{image=ImageIO.read(new ByteArrayInputStream(bytes));}catch(IOException ignored){}
                if(image==null){String resource=next.split("\n",2)[0];Resource.Image layer=Resource.remote().load(resource).get().layer(Resource.imgc);if(layer!=null)image=layer.img;}
                if(image==null)return;replacement=new TexI(image).filter(haven.render.Texture.Filter.LINEAR);
            }
            billboard.set((TexI)replacement);
            if(icon!=null)icon.dispose();icon=replacement;shown=next;shownImage=next==null?null:images.get(next);
        }catch(Loading ignored){}
    }
    /** Match ore icons: grow up to 40 UI pixels, then shrink with the projected footprint. */
    public static int iconExtent(Pipe state,Coord viewport){return WorldLabels.iconExtent(state,viewport,17.5f,.8);}
    public void added(RenderTree.Slot slot){billboard.host(slot);super.added(slot);}
    public void removed(RenderTree.Slot slot){billboard.unhost(slot);super.removed(slot);}
    public Tex picture(){return icon;}
    public static String rememberedIcon(Gob gob){String key=ObjectNames.key(gob);if(key==null)return null;load();return manual.getOrDefault(key,contents.get(key));}
    public static BufferedImage savedPicture(String id){
        if(id==null)return null;load();byte[] bytes=images.get(id);if(bytes==null)return null;
        try{return ImageIO.read(new ByteArrayInputStream(bytes));}catch(IOException ignored){return null;}
    }
    public void dispose(){billboard.unhost();billboard.dispose();if(icon!=null)icon.dispose();icon=null;}
}
