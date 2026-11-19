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
import hearthborn.world.WorldLabels;
import hearthborn.world.WorldIcon;

import haven.*;
import haven.render.*;
import hearthborn.L10n;
import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Server container states and live vessel contents, kept separate from inventory sorting.
 *
 * @author Jagy2014
 */
public final class ContainerLook extends GAttrib implements Gob.SetupMod, RenderTree.Node, PView.Render2D {
    public enum State { EMPTY, GLASS, PARTIAL, FULL, READY }
    private static final String PREF = "hearthborn/containers/";
    private static final Map<String, int[][]> STATES = new HashMap<>();
    private static final Color[] DEFAULTS = {
        new Color(0,120,0,180), new Color(230,110,0,200), new Color(194,155,2,140),
        new Color(170,0,0,170), new Color(16,255,16,128)
    };
    private static final Set<String> STRUCTURAL = new HashSet<>(Arrays.asList(
        "acreclay","ballclay","boneclay","caveclay","coadeclay","coadestone","grayclay","pottersclay","seaclay","soapclay",
        "alderbough","elmbough","firbough","yewbough","bronze","castiron","copper","lead","steel","wroughtiron","liquimetal",
        "hempcloth","leatherfabric","mohaircloth","primcloth","silkcloth","woolcloth","blueglass","redbrick","sand","straw",
        "standinggrass","reeds","tarsticks","glimmermoss","fire"));
    static {
        states("stonecasket",17); states("chest",29); states("largechest",17); states("bonechest",17);
        states("cupboard",29); states("metalcabinet",65); states("coffer",65);
        STATES.put("gfx/terobjs/crate",new int[][]{{0},{16}});
        STATES.put("gfx/terobjs/map/squirrelcache[full]",new int[][]{{1,2},{5,6}});
        STATES.put("gfx/terobjs/map/squirrelcache[empty]",new int[][]{{1,2},{5,6}});
    }
    private static void states(String name,int full) { STATES.put("gfx/terobjs/"+name,new int[][]{{1,2},{full,full+1}}); }
    private static final Map<String,Boolean> flags=new HashMap<>();
    public static boolean enabled(String key) {
        Boolean value=flags.get(key);
        if(value==null)flags.put(key,value=Utils.getprefb(PREF+key, key.equals("fullness") || key.equals("labels")));
        return value;
    }
    public static void enabled(String key,boolean on) { flags.put(key,on); Utils.setprefb(PREF+key,on); }
    public static Color color(State state) { return new Color(Utils.getprefi(PREF+"color-"+state.name(),DEFAULTS[state.ordinal()].getRGB()),true); }
    public static void color(State state,Color value) { Utils.setprefi(PREF+"color-"+state.name(),value.getRGB()); }
    public static boolean defaultMaterials(String resource) {
        return (resource.equals("gfx/terobjs/cupboard") || resource.equals("gfx/terobjs/metalcabinet"))
            && enabled("default-materials");
    }
    public static boolean skipMaterial(haven.ModSprite.Mod mod,String resource) {
        if(!defaultMaterials(resource))return false;
        for(Class<?> type=mod.getClass();type!=null;type=type.getSuperclass()) {
            if(type.getSimpleName().equals("VarMats"))return true;
            FromResource source=type.getAnnotation(FromResource.class);
            if(source!=null && source.name().equals("lib/vmat")) {
                try { type.getMethod("varmat",int.class);return true; }
                catch(NoSuchMethodException ignored) {}
            }
        }
        return false;
    }
    public static void resetColors() { for(State state:State.values())Utils.setpref(PREF+"color-"+state.name(),null); }
    public ContainerLook(Gob gob) { super(gob); elapsed=-((gob.id&15)*(0.25/16.0)); }

    static State status(String resource,int data) {
        int[][] values=STATES.get(resource);
        if(values==null)return null;
        for(int value:values[0])if(value==data)return State.EMPTY;
        for(int value:values[1])if(value==data)return State.FULL;
        return State.PARTIAL;
    }
    static boolean vessel(String name) {
        return name.equals("gfx/terobjs/barrel") || name.startsWith("gfx/terobjs/barrel-")
            || name.equals("gfx/terobjs/demijohn") || name.startsWith("gfx/terobjs/demijohn-")
            || name.equals("gfx/terobjs/cistern") || name.startsWith("gfx/terobjs/cistern-");
    }
    static boolean liquid(String name) {
        if(name==null)return false;
        for(String type:new String[]{"barrel","demijohn","cistern"}) {
            String prefix="gfx/terobjs/"+type+"-";
            if(name.startsWith(prefix))return !name.substring(prefix.length()).isEmpty() && !name.equals(prefix+"opt");
        }
        if(!name.startsWith("gfx/terobjs/subst/"))return false;
        String raw=name.substring("gfx/terobjs/subst/".length());
        return !raw.startsWith("bough-") && !STRUCTURAL.contains(raw);
    }
    private static Object field(Object value,String name) {
        try { Field field=value.getClass().getField(name);return field.get(value); }
        catch(ReflectiveOperationException e) { return null; }
    }
    private static String label(Resource resource) {
        Resource.Tooltip tip=resource.layer(Resource.tooltip);
        String raw=resource.basename();
        for(String prefix:new String[]{"barrel-","demijohn-","cistern-"})if(raw.startsWith(prefix))raw=raw.substring(prefix.length());
        if(raw.equals("mercury"))return L10n.resource("tooltip","gfx/invobjs/mercury","Quicksilver");
        if(raw.equals("flaxoil"))return L10n.resource("tooltip","gfx/invobjs/flaxoil","Linseed Oil");
        String fallback=raw.isEmpty()?raw:Character.toUpperCase(raw.charAt(0))+raw.substring(1);
        if(enabled("short-labels")) {
            String text=tip==null?fallback:tip.original;
            return L10n.text("tooltip",text.replaceAll(" Hide|Dried |Bar of | Leaf| Leaves",""));
        }
        return L10n.objectName(resource,tip==null?L10n.text("ingredient",fallback):tip.original);
    }
    private String fromState(MessageBuf data) {
        if(data.rem()<2 || gob.glob==null || gob.glob.sess==null)return null;
        int id=data.uint16();if(id==0 || id==65535)return null;
        boolean marked=(id&0x8000)!=0;
        Resource resource=gob.glob.sess.getres(id&0x7fff).get();
        return (marked || liquid(resource.name)) && !resource.name.equals("gfx/terobjs/barrel")
            && !resource.name.equals("gfx/terobjs/demijohn") && !resource.name.equals("gfx/terobjs/cistern") ? label(resource):null;
    }
    private String observedContents;
    private String observedSignature;
    private static String signature(Gob gob,ResDrawable drawable) {
        StringBuilder key=new StringBuilder(drawable.rres.name).append(':').append(Arrays.hashCode(drawable.stateData().bytes()));
        for(Gob.Overlay overlay:new ArrayList<>(gob.ols)) {
            key.append(':').append(overlay.id);
            if(overlay.sm instanceof OCache.OlSprite) {
                OCache.OlSprite source=(OCache.OlSprite)overlay.sm;
                key.append(source.res.get().name).append(Arrays.hashCode(source.sdt));
            }
        }
        return key.toString();
    }
    public static void observed(Gob gob,String contents) {
        ContainerLook look=gob.getattr(ContainerLook.class);
        Drawable drawable=gob.getattr(Drawable.class);
        if(look==null || !(drawable instanceof ResDrawable))return;
        look.observedContents=contents;
        look.observedSignature=signature(gob,(ResDrawable)drawable);
        look.elapsed=1;
    }
    private String contents(ResDrawable drawable,int state) {
        String name=drawable.rres.name;
        if(name.equals("gfx/terobjs/demijohn") && (state&1)==0) {observedSignature=null;observedContents=null;return null;}
        if(observedSignature!=null) {
            if(observedSignature.equals(signature(gob,drawable)))return observedContents;
            observedSignature=null;observedContents=null;
        }
        if(liquid(name))return label(drawable.rres);
        for(Gob.Overlay overlay:new ArrayList<>(gob.ols)) {
            Resource resource=null;byte[] data=null;
            if(overlay.sm instanceof OCache.OlSprite) {
                OCache.OlSprite source=(OCache.OlSprite)overlay.sm;resource=source.res.get();data=source.sdt;
            } else if(overlay.sm instanceof Sprite.Mill.FromRes) {
                Sprite.Mill.FromRes source=(Sprite.Mill.FromRes)overlay.sm;resource=source.res.get();data=source.sdt;
            } else if(overlay.spr!=null)resource=overlay.spr.res;
            if(resource!=null && liquid(resource.name))return label(resource);
            if(overlay.spr!=null) {
                Object nested=field(overlay.spr,"espr");
                if(nested instanceof Sprite && ((Sprite)nested).res!=null && liquid(((Sprite)nested).res.name))return label(((Sprite)nested).res);
            }
            if(data!=null) { String text=fromState(new MessageBuf(data));if(text!=null)return text; }
        }
        for(GAttrib attribute:gob.attributes())if(attribute.getClass().getSimpleName().equals("AttrMats")) {
            Object resources=field(attribute,"res");
            Iterable<?> values=resources instanceof Map?((Map<?,?>)resources).values():resources instanceof Iterable?(Iterable<?>)resources:null;
            if(values!=null)for(Object value:values)if(value instanceof Resource && liquid(((Resource)value).name))return label((Resource)value);
        }
        return name.equals("gfx/terobjs/demijohn") ? L10n.tr("Liquid") : fromState(drawable.stateData());
    }

    private MixColor tint;
    private Pipe.Op stoneSurface;
    private Color tinted;
    private boolean readyTint;
    public static boolean ready(Gob gob){
        ContainerLook look=gob.getattr(ContainerLook.class);
        return look!=null && look.readyTint && enabled("finished");
    }
    private Tex caption;
    private boolean stoneCaption;
    private Tex oreIcon;
    private final WorldIcon billboard=new WorldIcon(5,.65,false,()->ContainerContents.allowed(gob.glob));
    private String oreIdentity;
    private String shown;
    private boolean onBody;
    private float labelHeight = 6;
    private double elapsed;
    private long revision=-1;
    private static long settings;
    public static void changed() { settings++; }

    public void ctick(double dt) {
        billboard.sync();
        if((elapsed+=dt)<.25 && revision==settings)return;
        elapsed=0;revision=settings;
        Drawable base=gob.getattr(Drawable.class);
        if(!(base instanceof ResDrawable)) { update(null,null);return; }
        ResDrawable drawable=(ResDrawable)base;
        String resource=drawable.rres.name;
        labelHeight=WorldLabels.height(resource);
        stoneCaption=false;onBody=false;
        if(StonePileColors.supported(resource)){
            String identity=StonePileColors.remembered(gob);
            String remembered=StonePileColors.enabled()?identity:null;
            boolean ore=StonePileColors.iconPile(resource);
            boolean slag=!StonePileColors.contentsPile(resource)&&identity!=null&&identity.split("\n",2)[0].equals("gfx/invobjs/slag");
            if(slag){
                stoneCaption=false;labelHeight=8;oreIcon(null);
                update(null,L10n.resource("tooltip","gfx/invobjs/slag","Slag"));stoneSurface=StonePileColors.surface(gob);return;
            }
            stoneCaption=resource.equals("gfx/terobjs/stockpile-stone");
            String stoneName=null;
            if(stoneCaption&&remembered!=null){
                String path=remembered.split("\n",2)[0];
                stoneName=L10n.find("tooltip",path);
                if(stoneName==null)stoneName=path.substring(path.lastIndexOf('/')+1);
                labelHeight=8;
            }
            if(ore){
                update(null,stoneName);stoneSurface=ore?null:StonePileColors.surface(gob);
                try{
                    if(remembered!=null)billboard.surface(drawable.getres());
                    oreIcon(remembered);
                }catch(Loading waiting){}
                return;
            }
            oreIcon(null);
            update(null,stoneName);stoneSurface=StonePileColors.surface(gob);return;
        }
        oreIcon(null);
        stoneSurface=null;
        if(!vessel(resource) && !STATES.containsKey(resource) && !resource.endsWith("/dframe") && !resource.endsWith("/ttub") && !resource.endsWith("/curdingtub")) { update(null,null);return; }
        try {
            MessageBuf data=drawable.stateData();
            int state=data.eom()?0xffff000:Sprite.decnum(data);
            String content=vessel(resource)?contents(drawable,state):null;
            State mark=status(resource,state);
            if(resource.equals("gfx/terobjs/demijohn") && (state&1)==0 && gob.getattr(Following.class)==null)mark=State.GLASS;
            else if(resource.equals("gfx/terobjs/barrel") && content==null && gob.getattr(Following.class)==null)mark=State.EMPTY;
            if(resource.endsWith("/ttub"))mark=(state&12)==0?State.EMPTY:(state&8)!=0?State.READY:null;
            if(resource.endsWith("/curdingtub"))mark=(state&2)!=0?State.READY:null;
            if(resource.endsWith("/dframe")) {
                boolean empty=true,ready=true;
                for(Gob.Overlay overlay:new ArrayList<>(gob.ols))if(overlay.sm instanceof OCache.OlSprite) {
                    empty=false;
                    String item=((OCache.OlSprite)overlay.sm).res.get().name;
                    if(item.endsWith("-blood") || item.endsWith("-windweed") || item.endsWith("-fishraw"))ready=false;
                }
                mark=empty?State.EMPTY:ready?State.READY:null;
            }
            boolean paint=mark!=null && (mark==State.READY?enabled("finished"):enabled("fullness"));
            if(mark==State.PARTIAL && !enabled("partial"))paint=false;
            onBody=true;
            String text=enabled("labels")?content:null;
            if(text!=null && enabled("short-labels"))text=text.replaceAll(" Hide|Dried |Bar of | Leaf| Leaves","");
            update(paint?color(mark):null,text,paint && mark==State.READY);
        } catch(Loading ignored) {}
    }
    private void oreIcon(String identity){
        if(Objects.equals(identity,oreIdentity))return;
        Tex replacement=null;
        if(identity!=null){
            java.awt.image.BufferedImage image=ContainerContents.savedPicture(identity);
            if(image==null){
                Resource.Image layer=Resource.remote().load(identity.split("\n",2)[0]).get().layer(Resource.imgc);
                if(layer==null)return;
                image=layer.img;
            }
            replacement=new TexI(image).filter(haven.render.Texture.Filter.LINEAR);
        }
        billboard.set((TexI)replacement);
        if(oreIcon!=null)oreIcon.dispose();
        oreIcon=replacement;oreIdentity=identity;
    }
    private WorldLabels.Label caption(String text){return onBody?WorldLabels.vessel(text):WorldLabels.caption(text,WorldLabels.CONTENTS,true);}
    private void update(Color color,String text) {
        update(color,text,false);
    }
    private void update(Color color,String text,boolean ready) {
        readyTint=ready;
        if(!Objects.equals(color,tinted)) { tinted=color;tint=color==null?null:new MixColor(color); }
        if(!Objects.equals(text,shown)) {
            shown=text;if(caption!=null)caption.dispose();caption=null;
            if(text!=null && !text.isEmpty())caption=caption(text);
        }
    }
    public Pipe.Op gobstate() { return stoneSurface!=null?stoneSurface:tint; }
    static int oreIconExtent(Pipe state,Coord viewport){return WorldLabels.iconExtent(state,viewport,5,.65);}
    public void draw(GOut g,Pipe state) {
        if(oreIcon!=null)return;
        if(caption!=null&&WorldLabels.stale(caption)){caption.dispose();caption=caption(shown);}
        Tex text=caption;if(text==null||stoneCaption&&!ContainerContents.spaceHeld(gob.glob))return;
        Coord at=WorldLabels.anchor(state,g.sz(),labelHeight);
        if(at!=null)WorldLabels.draw(g,text,onBody?at.add(0,WorldLabels.vesselDrop()):at,.5,1);
    }
    public void added(RenderTree.Slot slot){billboard.host(slot);super.added(slot);}
    public void removed(RenderTree.Slot slot){billboard.unhost(slot);super.removed(slot);}
    public void dispose() { billboard.unhost();if(caption!=null)caption.dispose();caption=null;oreIcon(null); }
}
