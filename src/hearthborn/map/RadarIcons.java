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

package hearthborn.map;

import haven.*;
import hearthborn.util.GameObjects;

/**
 * Local radar icons for objects for which the server supplies no GobIcon.
 *
 * @author Jagy2014
 */
public final class RadarIcons {
    private static final java.util.Map<String,String> pictures=new java.util.HashMap<String,String>();
    private static final java.util.Map<String,String> aliases=new java.util.HashMap<String,String>();
    private static final java.util.Map<String,Integer> versions=new java.util.HashMap<String,Integer>();
    private static final java.util.Map<String,byte[]> packed=new java.util.HashMap<String,byte[]>();
    private static byte[] blank;
    static {
        picture("radar/mine-ladder","icons/mapicons/mine-ladder.png",256);
        picture("radar/hearthfire","icons/mapicons/hearthfire.png",256);
        picture("radar/milestone-stone-m","icons/mapicons/milestone-stone-m.png",256);
        picture("radar/squirrel-cache","icons/mapicons/squirrel-cache.png",256);
        picture("radar/midgeswarm","icons/mapicons/midgeswarm.png",256);
        picture("radar/gem","icons/mapicons/gem.png",256);
        alias("radar/tame/horse","gfx/kritter/horse/icon-wildhorse");
        alias("radar/tame/cattle","gfx/kritter/cattle/icon-aurochs");
        alias("radar/tame/sheep","gfx/kritter/sheep/icon-mouflon");
        alias("radar/tame/goat","gfx/kritter/goat/icon");
        alias("radar/tame/pig","gfx/kritter/boar/icon");
        Resource.local().add(new PngSource());
    }
    private static void picture(String name,String file,int version){pictures.put(name,file);versions.put(name,version);}
    private static void alias(String name,String official){pictures.put(name,"");versions.put(name,256);aliases.put(name,official);}
    private static final class PngSource implements Resource.ResSource {
        public java.io.InputStream get(String name) throws java.io.IOException {
            byte[] data=pack(name);
            if(data==null)throw new java.io.FileNotFoundException(name);
            return new java.io.ByteArrayInputStream(data);
        }
        public String toString(){return "map icon png source";}
    }
    private static synchronized byte[] pack(String name){
        if(!pictures.containsKey(name))return null;
        byte[] ready=packed.get(name);
        if(ready!=null)return ready;
        try{
            byte[] png;
            String source=pictures.get(name);
            if(source.length()==0)png=placeholder();
            else try(java.io.InputStream in=hearthborn.assets.Assets.open(source)){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buf=new byte[4096];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);png=out.toByteArray();
            }
            MessageBuf payload=new MessageBuf();
            payload.adduint8(0).addint8((byte)0).addint16((short)0).adduint8(0).addint16((short)0).addint16((short)0).addint16((short)0).addbytes(png);
            byte[] layer=payload.fin();
            MessageBuf file=new MessageBuf();
            file.addbytes("Haven Resource 1".getBytes(Utils.ascii)).adduint16(versions.get(name)).addstring("image").addint32(layer.length).addbytes(layer);
            ready=file.fin();
            packed.put(name,ready);
            return ready;
        }catch(java.io.IOException error){
            absent(name);
            return null;
        }
    }
    private static byte[] placeholder() throws java.io.IOException {
        if(blank!=null)return blank;
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        if(!javax.imageio.ImageIO.write(image,"png",out))throw new java.io.IOException("png");
        blank=out.toByteArray();
        return blank;
    }
    private static final java.util.Properties configured=hearthborn.l10n.Catalog.load("/hearthborn/map/radar-icons.properties");
    private static final java.util.Map<String,String> learned=new java.util.HashMap<>();
    private static boolean loaded,dirty;
    private static double nextSave;
    private static synchronized void load(){if(loaded)return;loaded=true;Object data=hearthborn.io.LocalStore.read("radar-learned");if(data instanceof java.util.Map)for(java.util.Map.Entry<?,?> entry:((java.util.Map<?,?>)data).entrySet())if(entry.getKey() instanceof String&&entry.getValue() instanceof String)learned.put((String)entry.getKey(),(String)entry.getValue());}
    public static synchronized void flush(){if(dirty&&Utils.rtime()>=nextSave){hearthborn.io.LocalStore.write("radar-learned",new java.util.HashMap<>(learned));dirty=false;nextSave=Utils.rtime()+1;}}
    public static String identity(Gob gob){
        String base=GameObjects.resourceName(gob);if(base==null)return null;
        if("gfx/terobjs/vehicle/plow".equals(base)){String kind=plowKind(gob);if(kind!=null)return base+"["+kind+"]";}
        Drawable drawable=gob.getattr(Drawable.class);
        if(drawable instanceof Composite){Composite composite=(Composite)drawable;java.util.Set<String> mods=new java.util.TreeSet<>();java.util.List<Composited.MD> models=composite.nmod!=null?composite.nmod:composite.comp.cmod;
            for(Composited.MD model:models){String name=model.mod.get().name;if(!base.equals(name))mods.add(name);}
            if(!mods.isEmpty())return base+"["+String.join(",",mods)+"]";
        }else if(drawable instanceof ResDrawable&&(base.endsWith("/pow")||base.endsWith("/squirrelcache"))){MessageBuf state=((ResDrawable)drawable).stateData();int value=state.eom()?-1:state.uint8();if(base.endsWith("/pow")&&(value==17||value==33))return base+"[hearth]";if(base.endsWith("/squirrelcache"))return base+(value==5||value==6?"[full]":"[empty]");}
        return base;
    }
    /** A metal plow carries wrought iron. A plow with only wood is the wooden plow. */
    private static String plowKind(Gob gob){
        GAttrib materials=null;
        for(GAttrib attr:gob.attributes())if(attr!=null&&"haven.res.lib.vmat.AttrMats".equals(attr.getClass().getName())){materials=attr;break;}
        Object mats=GameObjects.field(materials,"mats",java.util.Map.class);
        if(!(mats instanceof java.util.Map))return null;
        boolean metal=false,any=false;
        for(Object material:((java.util.Map<?,?>)mats).values()){
            if(!(material instanceof Material.ResMaterial))continue;
            Resource skin=((Material.ResMaterial)material).res;if(skin==null||skin.name==null)continue;
            any=true;if(skin.name.contains("wroughtiron"))metal=true;
        }
        return any?metal?"metal":"wood":null;
    }
    public static synchronized void learn(Gob gob,GobIcon icon){load();String id=identity(gob);if(id==null)return;String path=icon.res.get().name;if(!path.equals(learned.put(id,path))){dirty=true;paths.clear();}}
    public static void configure(GobIcon.Settings.Loader loader){
        java.util.Map<String,Boolean> icons=new java.util.LinkedHashMap<>();
        java.util.Properties world=hearthborn.l10n.Catalog.load("/hearthborn/map/world-icons.properties");
        for(String path:world.stringPropertyNames())icons.put(path,Boolean.parseBoolean(world.getProperty(path)));
        java.util.Properties animals=hearthborn.l10n.Catalog.load("/hearthborn/map/animal-icons.properties");
        for(String path:animals.stringPropertyNames())icons.put(path,Boolean.parseBoolean(animals.getProperty(path)));
        for(String key:configured.stringPropertyNames()){String[] value=configured.getProperty(key).split("\\|",2);icons.put(value[0],value.length<2||Boolean.parseBoolean(value[1]));}
        for(String path:new String[]{"paginae/bld/bram","gfx/kritter/cattle/icon-aurochs","gfx/kritter/sheep/icon-mouflon","gfx/kritter/boar/icon","gfx/kritter/goat/icon"})icons.putIfAbsent(path,true);
        for(String path:aliases.keySet())icons.put(path,true);
        for(java.util.Map.Entry<String,Boolean> entry:icons.entrySet()){
            Resource.Saved resource=new Resource.Saved(Resource.remote(),entry.getKey(),-1);GobIcon.Settings.ResID id=new GobIcon.Settings.ResID(resource,new byte[0]);GobIcon.Setting setting=new GobIcon.Setting(resource,GobIcon.Icon.nilid);setting.show=setting.defshow=entry.getValue();loader.load.add(id);loader.defaults.put(id,setting);
        }
    }

    public static boolean transition(String resource){return resource!=null&&(resource.equals("gfx/terobjs/minehole")||resource.equals("gfx/terobjs/ladder")||resource.equals("gfx/terobjs/mineladder")||resource.contains("/cavein")||resource.contains("/caveout")||resource.equals("gfx/terobjs/cave"));}
    /** Tame livestock keeps the wild picture under its own icon name, the way mare and stallion used to. */
    private static final String NONE=new String("-");
    private static final java.util.concurrent.ConcurrentHashMap<String,String> domesticNames=new java.util.concurrent.ConcurrentHashMap<>(),paths=new java.util.concurrent.ConcurrentHashMap<>();
    /** Answers for one resource never change, but the map asks for every object on every frame. */
    public static String domestic(String resource){
        if(resource==null)return null;
        String known=domesticNames.get(resource);
        if(known==null){known=domesticRaw(resource);domesticNames.put(resource,known==null?NONE:known);}
        return known==NONE?null:known;
    }
    private static String domesticRaw(String resource){
        if(resource.startsWith("gfx/kritter/horse/mare")||resource.startsWith("gfx/kritter/horse/stallion")||resource.startsWith("gfx/kritter/horse/foal"))return "radar/tame/horse";
        if(resource.contains("gfx/kritter/cattle/cow")||resource.contains("gfx/kritter/cattle/bull")||resource.contains("gfx/kritter/cattle/calf"))return "radar/tame/cattle";
        if(resource.contains("gfx/kritter/sheep/ewe")||resource.contains("gfx/kritter/sheep/ram")||resource.contains("gfx/kritter/sheep/lamb"))return "radar/tame/sheep";
        if(resource.startsWith("gfx/kritter/goat/")&&!resource.contains("wildgoat"))return "radar/tame/goat";
        if(resource.startsWith("gfx/kritter/pig/"))return "radar/tame/pig";
        if(resource.contains("teimdeer"))return "gfx/kritter/reindeer/teimdeer-icon";
        return null;
    }
    private static final java.util.concurrent.ConcurrentHashMap<String,Boolean> livestockNames=new java.util.concurrent.ConcurrentHashMap<>();
    /** Most objects are not livestock; the prefix test is repeated for each of them on every frame, so it is remembered per resource. */
    private static boolean livestock(String base){
        Boolean known=livestockNames.get(base);
        if(known==null){known=base.startsWith("gfx/kritter/horse/")||base.startsWith("gfx/kritter/cattle/")||base.startsWith("gfx/kritter/sheep/")||base.startsWith("gfx/kritter/goat/")||base.startsWith("gfx/kritter/pig/")||base.startsWith("gfx/kritter/reindeer/");livestockNames.put(base,known);}
        return known;
    }
    public static String domestic(Gob gob){
        String base=GameObjects.resourceName(gob);
        if(base==null||!livestock(base))return null;
        return domestic(identity(gob));
    }
    /** Cached per resource; learning a new picture drops the cache. */
    public static String path(String resource){
        if(resource==null)return null;
        load();
        String known=paths.get(resource);
        if(known==null){known=pathRaw(resource);paths.put(resource,known==null?NONE:known);}
        return known==NONE?null:known;
    }
    private static String pathRaw(String resource){
        String tame=domestic(resource);if(tame!=null)return tame;
        if(transition(resource)){if(resource.contains("/cavein")||resource.contains("/caveout"))return "gfx/hud/mmap/cave";String value=configured.getProperty(resource);if(value!=null)return value.split("\\|",2)[0];}
        String remembered=learned.get(resource);if(remembered!=null)return remembered;
        String custom=configured.getProperty(resource);if(custom!=null)return custom.split("\\|",2)[0];
        switch(resource){
        case "gfx/terobjs/vehicle/cart":return "paginae/bld/cart";
        case "gfx/terobjs/vehicle/bram":return "paginae/bld/bram";
        case "gfx/terobjs/vehicle/wreckingball":return "paginae/bld/wball";
        case "gfx/terobjs/minehole":return "paginae/bld/minehole";
        case "gfx/terobjs/ladder":case "gfx/terobjs/mineladder":return "radar/mine-ladder";
        case "gfx/terobjs/cave":case "gfx/terobjs/cavein":case "gfx/terobjs/arch/cavein":
        case "gfx/terobjs/caveout":case "gfx/terobjs/arch/caveout":return "gfx/hud/mmap/cave";
        default:
            if(resource.startsWith("gfx/kritter/cattle/"))return "gfx/kritter/cattle/icon-aurochs";
            if(resource.startsWith("gfx/kritter/sheep/"))return "gfx/kritter/sheep/icon-mouflon";
            if(resource.startsWith("gfx/kritter/pig/"))return "gfx/kritter/boar/icon";
            if(resource.startsWith("gfx/kritter/goat/"))return "gfx/kritter/goat/icon";
            if(resource.startsWith("gfx/kritter/horse/"))return "gfx/kritter/horse/icon-wildhorse";
            if(resource.equals("gfx/kritter/chicken/chick"))return "gfx/kritter/chicken/icon-chick";
            return null;
        }
    }
    public static GobIcon icon(Gob gob,GobIcon previous){
        String path=path(identity(gob));if(path==null)path=path(GameObjects.resourceName(gob));if(path==null)return null;
        return pinned(gob,path,previous);
    }
    public static GobIcon pinned(Gob gob,String path,GobIcon previous){
        if(previous!=null&&previous.res.get().name.equals(path))return previous;
        return new GobIcon(gob,Resource.remote().load(path),new byte[0]);
    }
    public static GobIcon.Setting setting(GobIcon.Settings settings,GobIcon icon){
        GobIcon.Icon image=icon.icon();synchronized(settings){GobIcon.Setting setting=settings.get(image);
        if(setting==null){
            Resource.Saved resource=new Resource.Saved(Resource.remote(),image.res.name,image.res.ver);
            setting=new GobIcon.Setting(image,new GobIcon.Settings.ResID(resource,new byte[0]));
            setting.show=setting.defshow=true;settings.settings.put(setting.id,setting);settings.dsave();
        }
        return setting;}
    }
    /** Two-layer markers keep a pale plate behind the symbol. The map shows the symbol alone. */
    public static java.awt.image.BufferedImage image(Resource res) {
        String official=aliases.get(res.name);
        if(official!=null)return Resource.remote().load(official).get().flayer(Resource.imgc).img;
        Resource.Image symbol=res.layer(Resource.imgc,1);
        if(symbol!=null)return symbol.img;
        Resource.Image only=res.layer(Resource.imgc);
        if(only==null)only=res.flayer(Resource.imgc);
        return only.img;
    }
    private static final java.util.Set<String> reportedMissing=java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    /** Records each missing map icon once. The map keeps running without it. */
    public static void absent(String name){
        if(name==null||!reportedMissing.add(name))return;
        String line=java.time.Instant.now()+" missing map icon: "+name;
        System.err.println(line);
        if(!hearthborn.io.DeveloperMode.enabled("debug-logs"))return;
        try{hearthborn.io.Logs.append("map-icons.log",line+"\n");}catch(java.io.IOException ignored){}
    }
    /** Cave mouths are map tiles. 1 is an entrance, 2 is an exit. */
    public static int passage(String name){
        if(name==null||!name.startsWith("gfx/tiles/ridges/"))return 0;
        if(name.endsWith("/caveout"))return 2;
        if(name.endsWith("/cavein")||name.endsWith("/cavein2"))return 1;
        return 0;
    }
    private static GobIcon.Image cave;
    public static GobIcon.Image passageImage(boolean out){
        if(cave!=null)return cave;
        try{cave=GobIcon.Image.get(Resource.remote().load("gfx/hud/mmap/cave").get());return cave;}
        catch(Loading pending){return null;}
        catch(Resource.NoSuchResourceException missing){absent(missing.name);return null;}
    }
    public static String name(Resource res) {
        switch(res.name) {
        case "gfx/hud/mmap/cave":return hearthborn.L10n.tr("Cave Passage");
        case "radar/mine-hole":return hearthborn.L10n.tr("Mine descent");
        case "radar/mine-ladder":return hearthborn.L10n.tr("Mine ascent");
        case "radar/cave-in":return hearthborn.L10n.tr("Cave entrance");
        case "radar/cave-out":return hearthborn.L10n.tr("Cave exit");
        case "radar/tame/horse":return hearthborn.L10n.tr("Domestic horse");
        case "radar/tame/cattle":return hearthborn.L10n.tr("Domestic cattle");
        case "radar/tame/sheep":return hearthborn.L10n.tr("Domestic sheep");
        case "radar/tame/goat":return hearthborn.L10n.tr("Domestic goat");
        case "radar/tame/pig":return hearthborn.L10n.tr("Domestic pig");
        default:return res.name.substring(res.name.lastIndexOf('/')+1);
        }
    }
    private RadarIcons(){}
}
