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
import hearthborn.planner.ClaimBorders;
import hearthborn.meteor.MeteorMapOverlay;
import hearthborn.io.LocalStore;
import hearthborn.ui.WorldIdentity;

import haven.*;
import hearthborn.L10n;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Locale;

/**
 * Optional overlays shared by the corner map, map window, and detached map.
 *
 * @author Jagy2014
 */
public final class MapDisplay implements Disposable {
    private static final Coord SERVER_GRID=Coord.of(100,100);
    private static final Map<String,Boolean> features=new HashMap<>();
    private static volatile Boolean hideTitle;
    public static boolean hideTerrainName(){if(hideTitle==null)hideTitle=Utils.getprefb("hearthborn/map/hide-terrain-name",true);return hideTitle;}
    public static void hideTerrainName(boolean hide){hideTitle=hide;Utils.setprefb("hearthborn/map/hide-terrain-name",hide);}
    public static boolean enabled(String feature){
        if(feature.equals("terrain-name"))return !hideTerrainName();
        Boolean value=features.get(feature);
        // Sample markers default on so scout results show without hunting for a toggle.
        if(value==null)features.put(feature,value=Utils.getprefb("hearthborn/map/"+feature,feature.equals("samples")));
        return value;
    }
    public static void enabled(String feature,boolean value){
        if(feature.equals("terrain-name"))hideTerrainName(!value);
        else{features.put(feature,value);Utils.setprefb("hearthborn/map/"+feature,value);}
    }
    private final MiniMap map;
    private final MeteorMapOverlay meteor;
    private Coord hover;
    private Resource terrain;
    private Tex caption;
    private double lastTerrain;
    private final Map<String,Tex> labels=new LinkedHashMap<>();
    private final Map<MapFile.DataGrid,Mask> masks=new LinkedHashMap<>();
    private static final String STORE="scout-map-marks-v1";
    /** Ordinary walking: kept in memory for this login only. */
    private static final Map<GameUI,Trail> sessionTrails=new WeakHashMap<>();
    /** Shore-scout path: saved with sample markers until right-click clear. */
    private static final Map<GameUI,Trail> scoutTrails=new WeakHashMap<>();
    private static final Map<GameUI,List<Sample>> samples=new WeakHashMap<>();
    private static final Map<GameUI,Summary> summaries=new WeakHashMap<>();
    private static final Map<GameUI,String> loadedFor=new WeakHashMap<>();
    private static final Map<GameUI,Boolean> scoutWas=new WeakHashMap<>();
    private static final Map<String,StoredWorld> saved=new HashMap<>();
    private static boolean storeLoaded;
    private static double lastTrailSave;
    /** Scout run totals kept with the scout path until right-click clear. */
    public static final class Summary {
        public int taken;
        public Double water, clay;
        public Summary() {}
        public Summary(int taken, Double water, Double clay){this.taken=taken;this.water=water;this.clay=clay;}
        Summary copy(){return new Summary(taken,water,clay);}
    }
    private static class Point {final long segment;final Coord tile;Point(long segment,Coord tile){this.segment=segment;this.tile=tile;}}
    private static class Trail {final ArrayDeque<Point> points=new ArrayDeque<>();double sampled;}
    /** Scout/probe marker; kept on disk for this world until right-clicked or cleared, like meteor measurements. */
    private static class Sample {
        final long segment;final Coord tile;final String kind,label;final Color color;
        Sample(long segment,Coord tile,String kind,String label,Color color){
            this.segment=segment;this.tile=tile;this.kind=kind;this.label=label;this.color=color;
        }
    }
    private static final class StoredPoint implements Serializable {
        private static final long serialVersionUID=1;
        final long segment;final int x,y;
        StoredPoint(long segment,int x,int y){this.segment=segment;this.x=x;this.y=y;}
    }
    private static final class StoredSample implements Serializable {
        private static final long serialVersionUID=1;
        final long segment;final int x,y,rgba;final String kind,label;
        StoredSample(long segment,int x,int y,String kind,String label,int rgba){
            this.segment=segment;this.x=x;this.y=y;this.kind=kind;this.label=label;this.rgba=rgba;
        }
    }
    private static final class StoredWorld implements Serializable {
        private static final long serialVersionUID=1;
        final ArrayList<StoredSample> samples=new ArrayList<>();
        final ArrayList<StoredPoint> trail=new ArrayList<>();
        int taken;
        Double bestWater, bestClay;
    }
    private static class Mask {final long sequence;final Tex tex;Mask(long sequence,Tex tex){this.sequence=sequence;this.tex=tex;}}
    private final List<Area> sampleHits=new ArrayList<>();
    public MapDisplay(MiniMap map){this.map=map;this.meteor=new MeteorMapOverlay(map);}
    public Object meteorTooltip(Coord at){return meteor.tooltip(at);}
    public boolean meteorClick(Coord at,int button){return meteor.click(at,button);}
    public Object sampleTooltip(Coord at){
        if(!sampleHit(at))return null;
        return L10n.tr("Sample markers")+"\n"+L10n.tr("Saved for this world until cleared.")+"\n"+L10n.tr("Right-click a marker to clear the scout path, the session path and all sample markers.");
    }
    public boolean sampleClick(Coord at,int button){
        if(!sampleHit(at))return false;
        if(button==3){clearTrail(gui());sampleHits.clear();return true;}
        return true;
    }
    private boolean sampleHit(Coord at){
        if(!enabled("samples"))return false;
        for(Area area:sampleHits)if(area.contains(at))return true;
        return false;
    }
    public void hover(Coord c){hover=c.isect(Coord.z,map.sz) ? c : null;}
    private GameUI gui(){return map.getparent(GameUI.class);}
    private static String storeKey(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null)return null;
        String world=WorldIdentity.name(gui.ui.sess);if(world==null)return null;
        NamedSocketAddress server=Bootstrap.authserv.get();
        return server.host.toLowerCase(Locale.ROOT)+":"+server.port+"\n"+world;
    }
    private static void loadStore(){
        if(storeLoaded)return;storeLoaded=true;
        Object raw=LocalStore.read(STORE);
        if(!(raw instanceof Map))return;
        for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet()){
            if(!(entry.getKey() instanceof String)||!(entry.getValue() instanceof StoredWorld))continue;
            StoredWorld world=(StoredWorld)entry.getValue();
            if(world.samples==null||world.trail==null||world.samples.size()>500||world.trail.size()>16000)continue;
            saved.put((String)entry.getKey(),world);
        }
    }
    private static void writeStore(){LocalStore.write(STORE,new HashMap<>(saved));}
    private static void ensureLoaded(GameUI gui){
        String key=storeKey(gui);if(key==null)return;
        if(key.equals(loadedFor.get(gui)))return;
        loadStore();
        StoredWorld world=saved.get(key);
        List<Sample> list=new ArrayList<>();
        Trail scout=new Trail();
        if(world!=null){
            for(StoredSample sample:world.samples){
                if(sample==null||sample.label==null||sample.kind==null)continue;
                list.add(new Sample(sample.segment,Coord.of(sample.x,sample.y),sample.kind,sample.label,new Color(sample.rgba,true)));
            }
            for(StoredPoint point:world.trail){
                if(point==null)continue;
                scout.points.add(new Point(point.segment,Coord.of(point.x,point.y)));
            }
        }
        samples.put(gui,list);
        scoutTrails.put(gui,scout);
        sessionTrails.put(gui,new Trail());
        Summary summary=new Summary();
        if(world!=null){
            summary.taken=Math.max(0,world.taken);
            summary.water=world.bestWater;
            summary.clay=world.bestClay;
        }
        summaries.put(gui,summary);
        loadedFor.put(gui,key);
    }
    private static void persist(GameUI gui){
        String key=storeKey(gui);if(key==null)return;
        loadStore();ensureLoaded(gui);
        StoredWorld world=new StoredWorld();
        List<Sample> list=samples.get(gui);
        if(list!=null)for(Sample sample:list)
            world.samples.add(new StoredSample(sample.segment,sample.tile.x,sample.tile.y,sample.kind,sample.label,sample.color.getRGB()));
        Trail scout=scoutTrails.get(gui);
        if(scout!=null)for(Point point:scout.points)
            world.trail.add(new StoredPoint(point.segment,point.tile.x,point.tile.y));
        Summary summary=summaries.get(gui);
        if(summary!=null){
            world.taken=Math.max(0,summary.taken);
            world.bestWater=summary.water;
            world.bestClay=summary.clay;
        }
        if(world.samples.isEmpty()&&world.trail.isEmpty()&&world.taken<=0&&world.bestWater==null&&world.bestClay==null)
            saved.remove(key);
        else saved.put(key,world);
        writeStore();
        lastTrailSave=Utils.rtime();
    }
    public static Summary scoutSummary(GameUI gui){
        if(gui==null)return new Summary();
        ensureLoaded(gui);
        Summary summary=summaries.get(gui);
        return summary==null?new Summary():summary.copy();
    }
    public static void scoutSummary(GameUI gui,int taken,Double water,Double clay){
        if(gui==null)return;
        ensureLoaded(gui);
        Summary summary=summaries.computeIfAbsent(gui,key->new Summary());
        summary.taken=Math.max(0,taken);
        summary.water=water;
        summary.clay=clay;
        persist(gui);
    }
    public static void clearTrail(GameUI gui){
        if(gui==null)return;
        ensureLoaded(gui);
        Trail scout=scoutTrails.get(gui);if(scout!=null)scout.points.clear();
        Trail session=sessionTrails.get(gui);if(session!=null)session.points.clear();
        List<Sample> list=samples.get(gui);if(list!=null)list.clear();
        Summary summary=summaries.get(gui);
        if(summary!=null){summary.taken=0;summary.water=null;summary.clay=null;}
        persist(gui);
        hearthborn.scripts.ScoutScripts.clearWindowStats(gui);
    }
    public static void clearSamples(GameUI gui){
        if(gui==null)return;
        ensureLoaded(gui);
        List<Sample> list=samples.get(gui);if(list!=null)list.clear();
        persist(gui);
    }
    /**
     * A quality marker at the player's map location
     * (water / clay / sand / soil probes and the shore scout). Saved for this world until cleared.
     */
    public static void sample(GameUI gui,String kind,Double quality){
        if(gui==null||gui.map==null||gui.mapfile==null||gui.mapfile.file==null)return;
        ensureLoaded(gui);
        MapFile file=gui.mapfile.file;
        String label=quality==null?"?":Utils.odformat(quality,1);
        Color color=sampleColor(kind);
        if(file.lock.readLock().tryLock())try{
            MiniMap.Location location=new MiniMap.MapLocator(gui.map).locate(file);
            if(location==null)return;
            List<Sample> list=samples.computeIfAbsent(gui,key->new ArrayList<>());
            list.add(new Sample(location.seg.id,location.tc,kind==null?"":kind,label,color));
            while(list.size()>500)list.remove(0);
            persist(gui);
        }catch(Loading ignored){}finally{file.lock.readLock().unlock();}
    }
    private static Color sampleColor(String kind){
        if("water".equals(kind))return new Color(70,150,230,255);
        if("clay".equals(kind))return new Color(196,110,48,255);
        if("sand".equals(kind))return new Color(210,180,90,255);
        if("soil".equals(kind))return new Color(110,150,70,255);
        return new Color(220,220,220,255);
    }
    private static int sampleOrder(String kind){
        if("water".equals(kind))return 0;
        if("clay".equals(kind))return 1;
        if("sand".equals(kind))return 2;
        if("soil".equals(kind))return 3;
        return 9;
    }
    public void tick() {
        GameUI gui=gui();if(gui==null||gui.map==null||map.ui==null||map.ui.sess==null)return;
        ensureLoaded(gui);
        boolean scouting=hearthborn.scripts.ScoutScripts.active(gui);
        /* Session path while walking normally; scout path only while the shore scout runs (saved until RMB). */
        Trail trail=(scouting?scoutTrails:sessionTrails).computeIfAbsent(gui,key -> new Trail());
        if(Utils.rtime()-trail.sampled>.25) {
            trail.sampled=Utils.rtime();
            if(map.file.lock.readLock().tryLock())try {
                MiniMap.Location location=new MiniMap.MapLocator(gui.map).locate(map.file);
                Point last=trail.points.peekLast();
                if(location!=null&&(last==null||last.segment!=location.seg.id||!last.tile.equals(location.tc))) {
                    trail.points.add(new Point(location.seg.id,location.tc));
                    while(trail.points.size()>16000)trail.points.removeFirst();
                    if(scouting&&Utils.rtime()-lastTrailSave>2)persist(gui);
                }
            } catch(Loading ignored){} finally{map.file.lock.readLock().unlock();}
        }
        if(!scouting&&Boolean.TRUE.equals(scoutWas.get(gui)))persist(gui);
        scoutWas.put(gui,scouting);
        CaveLevels.note(map);
        if(Utils.rtime()-lastTerrain<.2)return;lastTerrain=Utils.rtime();
        MiniMap.Location location=hover==null ? map.curloc : map.xlate(hover);
        Resource current=null;
        try {
            if(location!=null&&map.file.lock.readLock().tryLock())try {
                MapFile.Grid grid=location.seg.grid(location.tc.div(MCache.cmaps)).get();
                if(grid!=null){current=grid.tilesets[grid.gettile(location.tc.mod(MCache.cmaps))].res.get();}
            } finally{map.file.lock.readLock().unlock();}
            if(current==null&&gui.map.player()!=null) {
                Coord tile=gui.map.player().rc.floor(MCache.tilesz);current=map.ui.sess.glob.map.tilesetr(map.ui.sess.glob.map.gettile(tile));
            }
        }catch(Loading ignored){}
        if(current!=null&&current!=terrain) {
            terrain=current;if(caption!=null)caption.dispose();
            Resource.Tooltip tooltip=current.layer(Resource.tooltip);
            String name=L10n.objectName(current,tooltip==null?TerrainHighlight.name(current.name):tooltip.original);
            caption=outlined(TerrainHighlight.annotate(name,current.name));
        }
    }
    public void grid(GOut g,Coord ul,MiniMap.DisplayGrid display) {
        try {
            MapFile.DataGrid grid=display.gref.get();
            if(grid==null)return;
            if(TerrainHighlight.empty())return;
            Mask mask=masks.get(grid);
            if(mask==null && masks.size()>=128){Iterator<Mask> old=masks.values().iterator();old.next().tex.dispose();old.remove();}
            if(mask==null||mask.sequence!=TerrainHighlight.sequence) {
                if(mask!=null)mask.tex.dispose();
                mask=new Mask(TerrainHighlight.sequence,new TexI(TerrainHighlight.image(grid)));masks.put(grid,mask);
            }
            Coord end=map.xlate(new MiniMap.Location(display.seg,display.mapext.br));
            g.chcolor(255,255,255,TerrainHighlight.pulse());
            g.image(mask.tex,ul,end.sub(ul));
            g.chcolor();
        }catch(Loading ignored){}
    }
    public void under(GOut g) {
        ClaimBorders.paint(map,g);
        meteor.under(g);
        MiniMap.Location center=map.displayLocation();if(center==null)return;
        if(enabled("grid")) {
            MiniMap.Location a=map.xlate(Coord.z),b=map.xlate(map.sz);
            Coord sz=map.sz;
            if(a!=null&&b!=null&&sz.x>1&&sz.y>1) {
                // The grid is a guide, not a picture: about a third of full red lets the ground show through.
                g.chcolor(255,0,0,90);
                int step=MCache.cmaps.x;
                for(int x=Math.floorDiv(a.tc.x,step)*step;x<=b.tc.x&&x<a.tc.x+step*128;x+=step) {
                    Coord at=map.xlate(new MiniMap.Location(center.seg,Coord.of(x,a.tc.y)));
                    if(at==null||at.x<0||at.x>=sz.x)continue;
                    g.line(Coord.of(at.x,0),Coord.of(at.x,sz.y-1),1);
                }
                for(int y=Math.floorDiv(a.tc.y,step)*step;y<=b.tc.y&&y<a.tc.y+step*128;y+=step) {
                    Coord at=map.xlate(new MiniMap.Location(center.seg,Coord.of(a.tc.x,y)));
                    if(at==null||at.y<0||at.y>=sz.y)continue;
                    g.line(Coord.of(0,at.y),Coord.of(sz.x-1,at.y),1);
                }
                g.chcolor();
            }
        }
        GameUI gui=gui();
        if(gui!=null&&gui.map!=null&&map.sessloc!=null&&map.sessloc.seg==center.seg) {
            Gob player=gui.map.player();
            if(enabled("view")&&player!=null) {
                Coord2d start=player.rc.floor(new Coord2d(SERVER_GRID)).sub(4,4).mul(new Coord2d(SERVER_GRID));
                Coord a=map.p2c(start),b=map.p2c(start.add(new Coord2d(SERVER_GRID).mul(9)));
                int[] box=bounds(map.sz,a,b);
                if(box!=null) {
                    Coord ul=Coord.of(box[0],box[1]), size=Coord.of(box[2],box[3]);
                    g.chcolor(255,255,255,45);g.frect(ul,size);
                    g.chcolor(90,170,90,200);g.rect(ul,size);g.chcolor();
                }
            }
        }
        if(enabled("trail")&&gui!=null) {
            g.chcolor(220,36,36,235);
            double width=map.displayZoom()<=1 ? 3.2 : 4.2;
            paintTrail(g,center,sessionTrails.get(gui),width);
            paintTrail(g,center,scoutTrails.get(gui),width);
            g.chcolor();
        }
        sampleHits.clear();
        if(enabled("samples")&&gui!=null){
            List<Sample> list=samples.get(gui);
            if(list!=null){
                /* Same tile (scout water+clay): stack rows - coloured circle, quality to its right. */
                Map<Coord,List<Sample>> groups=new LinkedHashMap<>();
                for(Sample sample:list){
                    if(sample.segment!=center.seg.id)continue;
                    groups.computeIfAbsent(sample.tile,key->new ArrayList<>()).add(sample);
                }
                int r=Math.max(UI.scale(3), (int)Math.round(3.5/Math.max(0.5,map.displayZoom())));
                int row=Math.max(UI.scale(12), r*2+UI.scale(4));
                int textGap=r+UI.scale(4);
                int pad=UI.scale(2);
                for(Map.Entry<Coord,List<Sample>> entry:groups.entrySet()){
                    Coord base=map.xlate(new MiniMap.Location(center.seg,entry.getKey()));
                    if(base==null||!base.isect(Coord.z,map.sz))continue;
                    List<Sample> stack=entry.getValue();
                    stack.sort(Comparator.comparingInt(sample->sampleOrder(sample.kind)));
                    int n=stack.size();
                    for(int i=0;i<n;i++){
                        Sample sample=stack.get(i);
                        Coord at=base.add(0,(int)Math.round((i-(n-1)*.5)*row));
                        g.chcolor(0,0,0,200);g.fellipse(at,Coord.of(r+1,r+1));
                        g.chcolor(sample.color);g.fellipse(at,Coord.of(r,r));g.chcolor();
                        Tex name=label(sample.label);
                        g.aimage(name,at.add(textGap,0),0,.5);
                        int height=Math.max(r*2+2,name.sz().y);
                        Coord ul=Coord.of(at.x-(r+1),at.y-height/2).sub(pad,pad);
                        Coord size=Coord.of(r+1+textGap+name.sz().x,height).add(pad*2,pad*2);
                        sampleHits.add(Area.sized(ul,size));
                    }
                }
            }
        }
    }
    private void paintTrail(GOut g,MiniMap.Location center,Trail trail,double width){
        if(trail==null)return;
        Coord previous=null;Point last=null;
        for(Point point:trail.points){
            if(point.segment!=center.seg.id){previous=null;last=null;continue;}
            Coord next=map.xlate(new MiniMap.Location(center.seg,point.tile));
            if(next==null){previous=null;last=null;continue;}
            if(previous!=null&&last.tile.dist(point.tile)<=40){
                Coord[] seg=clip(map.sz,previous,next);
                if(seg!=null)stroke(g,seg[0],seg[1],width);
            }
            previous=next;last=point;
        }
    }
    /** Intersection of a rectangle with the map. Null when it misses. */
    private static int[] bounds(Coord sz, Coord a, Coord b) {
        int x0=Math.max(0,Math.min(a.x,b.x)), y0=Math.max(0,Math.min(a.y,b.y));
        int x1=Math.min(sz.x,Math.max(a.x,b.x)), y1=Math.min(sz.y,Math.max(a.y,b.y));
        if(x1<=x0||y1<=y0)return null;
        return new int[]{x0,y0,x1-x0,y1-y0};
    }

    /** The part of a segment that stays on the map. */
    private static Coord[] clip(Coord sz, Coord a, Coord b) {
        double x0=a.x, y0=a.y, x1=b.x, y1=b.y, dx=x1-x0, dy=y1-y0, t0=0, t1=1;
        double[] p={-dx,dx,-dy,dy};
        double[] q={x0, (sz.x-1)-x0, y0, (sz.y-1)-y0};
        for(int i=0;i<4;i++) {
            if(Math.abs(p[i])<1e-8) { if(q[i]<0)return null; }
            else {
                double t=q[i]/p[i];
                if(p[i]<0) { if(t>t1)return null; if(t>t0)t0=t; }
                else { if(t<t0)return null; if(t<t1)t1=t; }
            }
        }
        return new Coord[]{
            Coord.of((int)Math.round(x0+t0*dx),(int)Math.round(y0+t0*dy)),
            Coord.of((int)Math.round(x0+t1*dx),(int)Math.round(y0+t1*dy))
        };
    }

    /** A filled ribbon. OpenGL line width on macOS stays at one pixel, so the path would otherwise disappear. */
    private static void stroke(GOut g, Coord a, Coord b, double width) {
        double dx=b.x-a.x, dy=b.y-a.y, len=Math.hypot(dx,dy);
        if(len<0.5)return;
        float px=(float)(-dy/len*width/2.0), py=(float)(dx/len*width/2.0);
        float ax=a.x+g.tx.x, ay=a.y+g.tx.y, bx=b.x+g.tx.x, by=b.y+g.tx.y;
        g.drawp(haven.render.Model.Mode.TRIANGLE_STRIP,new float[]{ax+px,ay+py,ax-px,ay-py,bx+px,by+py,bx-px,by-py});
    }
    /** The same bold caption with a hard black stroke and a halo as the names of barrels and houses in the world, drawn 1:1. */
    private Tex outlined(String text){return new TexI(Utils.outline2(Text.render(text).img,Color.BLACK));}
    private Tex markerCaption(String text,double ratio){return new TexI(hearthborn.world.WorldLabels.captionImage(text,Color.WHITE,ratio));}
    private Tex label(String text) {
        double ratio=hearthborn.ui.LiveScale.ratio();
        String key=text+"\u0000"+ratio;
        Tex tex=labels.get(key);
        if(tex==null) {
            if(labels.size()>=256){Iterator<Tex> it=labels.values().iterator();it.next().dispose();it.remove();}
            labels.put(key,tex=markerCaption(text,ratio));
        }
        return tex;
    }
    private static class PointerHit {
        final Coord point;final Widget source;final Coord2d target;
        PointerHit(Coord point,Widget source,Coord2d target){this.point=point;this.source=source;this.target=target;}
    }
    private final java.util.List<PointerHit> pointerHits=new ArrayList<>();
    private String pointerTipText;
    private Tex pointerTip;
    public static double pointerAngle(Coord from,Coord to){
        return Math.atan2((double)from.x-to.x,(double)from.y-to.y);
    }
    private static boolean isPointer(Widget widget){
        for(Class<?> type=widget.getClass();type!=null;type=type.getSuperclass()){
            FromResource source=type.getAnnotation(FromResource.class);
            if((source!=null&&source.name().equals("ui/locptr"))||type.getName().contains(".locptr."))return true;
        }
        return false;
    }
    private Coord2d pointerTarget(Widget widget)throws ReflectiveOperationException{
        Object value=widget.getClass().getField("tc").get(widget);
        Coord2d target=value instanceof Coord2d?(Coord2d)value:null;
        try{
            long id=((Number)widget.getClass().getField("gobid").get(widget)).longValue();
            Gob gob=id==-1?null:map.ui.sess.glob.oc.getgob(id);
            if(gob!=null)target=gob.rc;
        }catch(NoSuchFieldException ignored){}
        return target;
    }
    public Object pointerTooltip(Coord at){
        if(map.getparent(MapWnd.class)==null||!enabled("pointers")||gui()==null||gui().map==null)return null;
        for(int i=pointerHits.size()-1;i>=0;i--){
            PointerHit hit=pointerHits.get(i);
            int radius=UI.scale(17);
            if(Math.abs(at.x-hit.point.x)>radius||Math.abs(at.y-hit.point.y)>radius)continue;
            Object original=hit.source.tooltip;
            String name=original instanceof Widget.KeyboundTip?((Widget.KeyboundTip)original).base:original instanceof String?(String)original:original instanceof Text?((Text)original).text:L10n.tr("Target");
            String text=name;
            try{
                Gob player=gui().map.player();Coord2d target=pointerTarget(hit.source);
                if(player!=null&&target!=null){
                    double distance=player.rc.dist(target)/MCache.tilesz.x;
                    if(Double.isFinite(distance))text+=String.format(Locale.ROOT," (%.1f %s)",distance,"ru".equals(L10n.language())?"м":"m");
                }
            }catch(ReflectiveOperationException|Loading ignored){}
            if(!text.equals(pointerTipText)){
                if(pointerTip!=null)pointerTip.dispose();
                boolean rich=original instanceof Widget.KeyboundTip&&((Widget.KeyboundTip)original).rich;
                pointerTip=new TexI(rich?RichText.render(text,UI.scale(350)).img:Text.render(text).img);pointerTipText=text;
            }
            return pointerTip;
        }
        return null;
    }
    public void over(GOut g) {
        pointerHits.clear();
        if(map.ui==null||map.ui.sess==null)return;
        if(map.getparent(MapWnd.class)!=null&&enabled("pointers")&&gui()!=null&&map.sessloc!=null)for(Widget widget:gui().children()) {
            if(!isPointer(widget))continue;
            try {
                Coord2d target=pointerTarget(widget);if(target==null)continue;
                Coord destination=map.p2c(target);if(destination==null)continue;
                Coord from=map.sz.div(2);
                Gob player=gui().map==null?null:gui().map.player();
                if(player!=null){Coord screen=map.p2c(player.rc);if(screen!=null)from=screen;}
                Tex pointer=MapToolbar.pointerMarker();
                int margin=UI.scale(20);
                int right=map.sz.x-margin;
                MapToolbar tools=map.getchild(MapToolbar.class);
                if(tools!=null&&tools.tvisible())right=Math.max(margin,tools.c.x-UI.scale(4));
                Coord point=Coord.of(Utils.clip(destination.x,margin,Math.max(margin,right)),Utils.clip(destination.y,margin,Math.max(margin,map.sz.y-margin)));
                g.chcolor();g.rotimage(pointer,point,pointer.sz().div(2),pointerAngle(from,destination));
                pointerHits.add(new PointerHit(point,widget,target));
            }catch(ReflectiveOperationException|Loading ignored){}
        }
        CaveLevels.paint(map, g);
        meteor.over(g);
        if(enabled("terrain-name")&&caption!=null) {
            int covered=MapToolbar.below(map);
            int top=covered>0?covered+UI.scale(2):UI.scale(2);
            Coord position=Coord.of(map.sz.x/2,top);g.chcolor(0,0,0,140);
            g.frect(position.sub(caption.sz().x/2+UI.scale(2),0),caption.sz().add(UI.scale(4),UI.scale(1)));g.chcolor();g.aimage(caption,position,.5,0);
        }
    }
    private final List<java.awt.Rectangle> named=new ArrayList<>();
    private final List<String> namedText=new ArrayList<>();
    /** A new round of marker names: what was drawn in the last one does not count. */
    public void beginMarkers() {named.clear();namedText.clear();}
    /** True when a name with this text is already drawn where the new one would overlap it. */
    static boolean crowded(List<java.awt.Rectangle> areas,List<String> texts,String text,java.awt.Rectangle area) {
        for(int i=0;i<areas.size();i++)if(texts.get(i).equals(text)&&areas.get(i).intersects(area))return true;
        return false;
    }
    public void marker(GOut g,MiniMap.DisplayMarker marker) {
        if(enabled("marker-names")&&marker.sc!=null) {
            String text=MarkerQuality.caption(marker.m);
            Tex name=label(text);Coord at=marker.sc.add(0,UI.scale(10));
            java.awt.Rectangle area=new java.awt.Rectangle(at.x-name.sz().x/2,at.y,name.sz().x,name.sz().y);
            /* Reefs and the like come in groups; one name is enough where the same name would be drawn over itself. */
            if(!crowded(named,namedText,text,area)) {
                named.add(area);namedText.add(text);
                g.aimage(name,at,.5,0);
            }
        }
        CaveLevels.badge(g, marker);
    }
    public void dispose(){meteor.dispose();if(pointerTip!=null)pointerTip.dispose();if(caption!=null)caption.dispose();for(Tex tex:labels.values())tex.dispose();for(Mask mask:masks.values())mask.tex.dispose();}
}
