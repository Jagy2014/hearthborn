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

package hearthborn.meteor;

import haven.*;
import haven.render.Model;
import hearthborn.L10n;
import java.awt.Color;
import java.util.*;

/**
 * Cached viewport geometry shared by both ordinary and detached large map views.
 *
 * @author Jagy2014
 */
public final class MeteorMapOverlay implements Disposable {
    private static final Text.Foundry NUMBER_FONT=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),16).aa(true);
    private final MiniMap map;
    private int version=-1;
    private long segment;
    private Coord center,size;
    private double scale,lastResolved;
    private String world;
    private java.util.List<MeteorTriangulation.Sector> resolved=Collections.emptyList(),sectors=Collections.emptyList();
    private java.util.List<java.util.List<Coord2d>> polygons=Collections.emptyList();
    private java.util.List<Coord2d> intersection=Collections.emptyList();
    private final Map<Integer,Tex> numbers=new HashMap<>();
    private Tex caption;
    private String captionText;
    private Area hit;
    private final java.util.List<Area> pointHits=new ArrayList<>();
    private boolean painted;
    public MeteorMapOverlay(MiniMap map){this.map=map;}
    private boolean visible(){return map.getparent(MapWnd.class)!=null&&map.ui!=null&&map.ui.sess!=null&&map.displayLocation()!=null;}
    private double viewScale(MiniMap.Location location){
        Coord zero=map.xlate(new MiniMap.Location(location.seg,location.tc));
        Coord unit=map.xlate(new MiniMap.Location(location.seg,location.tc.add(1024,0)));
        return (unit.x-zero.x)/1024d;
    }
    private boolean update(){
        if(!visible()){hit=null;return false;}
        MiniMap.Location location=map.displayLocation();
        double nextScale=viewScale(location);
        String nextWorld=hearthborn.ui.WorldIdentity.name(map.ui.sess);
        int nextVersion=MeteorTriangulation.generation();
        boolean resolve=version!=nextVersion||segment!=location.seg.id||!Objects.equals(world,nextWorld)||Utils.rtime()-lastResolved>1;
        if(resolve)try{
            resolved=MeteorTriangulation.resolve(map.file,location.seg.id,MeteorTriangulation.observations(map.ui));
        }catch(Loading pending){hit=null;return false;}
        boolean rebuild=resolve||!Objects.equals(center,location.tc)||!Objects.equals(size,map.sz)||scale!=nextScale;
        if(!rebuild)return true;
        if(resolve)lastResolved=Utils.rtime();
        version=MeteorTriangulation.generation();segment=location.seg.id;world=nextWorld;
        center=location.tc;size=map.sz;scale=nextScale;
        sectors=new ArrayList<>();polygons=new ArrayList<>();
        java.util.List<Coord2d> viewport=Arrays.asList(new Coord2d(0,0),new Coord2d(size.x,0),new Coord2d(size.x,size.y),new Coord2d(0,size.y));
        intersection=new ArrayList<>(viewport);
        for(MeteorTriangulation.Sector value:resolved){
            Coord2d origin=value.origin.sub(new Coord2d(center)).mul(scale).add(new Coord2d(size.div(2)));
            MeteorTriangulation.Sector sector=new MeteorTriangulation.Sector(origin,value.start,value.end);
            sectors.add(sector);polygons.add(MeteorTriangulation.intersect(viewport,sector));
            intersection=MeteorTriangulation.intersect(intersection,sector);
        }
        String text=resolved.isEmpty()?null:L10n.format("Meteorite triangulation: %d measurements",resolved.size());
        if(!Objects.equals(text,captionText)){
            if(caption!=null)caption.dispose();captionText=text;
            caption=text==null?null:outlined(text);
        }
        return true;
    }
    private static Tex outlined(String text){return new TexI(Utils.outline2(Text.render(text,Color.WHITE).img,Color.BLACK));}
    private static void fill(GOut g,java.util.List<Coord2d> polygon){
        if(polygon.size()<3)return;
        float[] vertices=new float[polygon.size()*2];int i=0;
        for(Coord2d point:polygon){vertices[i++]=(float)(point.x+g.tx.x);vertices[i++]=(float)(point.y+g.tx.y);}
        g.drawp(Model.Mode.TRIANGLE_FAN,vertices);
    }
    /** Filled ribbons retain their thickness on macOS, where OpenGL lines can stay one pixel wide. */
    private static void outline(GOut g,java.util.List<Coord2d> polygon,double width){
        if(polygon.size()<2)return;
        Coord2d a=polygon.get(polygon.size()-1);
        for(Coord2d b:polygon){
            double dx=b.x-a.x,dy=b.y-a.y,length=Math.hypot(dx,dy);
            if(length>.01){
                float px=(float)(-dy/length*width/2),py=(float)(dx/length*width/2);
                float ax=(float)(a.x+g.tx.x),ay=(float)(a.y+g.tx.y),bx=(float)(b.x+g.tx.x),by=(float)(b.y+g.tx.y);
                g.drawp(Model.Mode.TRIANGLE_STRIP,new float[]{ax+px,ay+py,ax-px,ay-py,bx+px,by+py,bx-px,by-py});
            }
            a=b;
        }
    }
    public void under(GOut graphics){
        painted=false;hit=null;pointHits.clear();
        if(!visible()||!update())return;
        painted=true;
        GOut g=graphics.reclip(Coord.z,map.sz);
        g.chcolor(255,55,55,24);
        for(java.util.List<Coord2d> polygon:polygons)fill(g,polygon);
        if(MeteorTriangulation.distinctOrigins(resolved)){
            g.chcolor(90,220,255,72);fill(g,intersection);
            g.chcolor(150,245,255,220);outline(g,intersection,UI.scale(1.0));
        }
        for(java.util.List<Coord2d> polygon:polygons){
            g.chcolor(0,0,0,180);outline(g,polygon,UI.scale(4.0));
            g.chcolor(255,55,55,245);outline(g,polygon,UI.scale(2.0));
        }
        g.chcolor();
    }
    public void over(GOut graphics){
        hit=null;pointHits.clear();if(!painted||!visible()||caption==null||sectors.isEmpty())return;
        GOut g=graphics.reclip(Coord.z,map.sz);int index=0;
        for(MeteorTriangulation.Sector sector:sectors){
            int number=++index;Coord at=sector.origin.round();if(!at.isect(Coord.z,map.sz))continue;
            Coord radius=UI.scale(8,8);
            pointHits.add(Area.sized(at.sub(radius),radius.mul(2).add(1,1)));
            g.chcolor(Color.BLACK);g.fellipse(at,UI.scale(6,6));
            g.chcolor(255,55,55,255);g.fellipse(at,UI.scale(4,4));g.chcolor();
            Tex label=numbers.computeIfAbsent(number,n->new TexI(Utils.outline2(NUMBER_FONT.render(Integer.toString(n),Color.WHITE).img,Color.BLACK)));
            int pad=UI.scale(2),x=at.x+UI.scale(10);
            if(x+label.sz().x+pad>map.sz.x)x=at.x-UI.scale(10)-label.sz().x;
            Coord position=Coord.of(Utils.clip(x,pad,Math.max(pad,map.sz.x-label.sz().x-pad)),
                Utils.clip(at.y-label.sz().y/2,pad,Math.max(pad,map.sz.y-label.sz().y-pad)));
            Area numberHit=Area.sized(position.sub(pad,pad),label.sz().add(pad*2,pad*2));
            pointHits.add(numberHit);
            g.chcolor(0,0,0,180);g.frect(numberHit.ul,numberHit.sz());g.chcolor();
            g.image(label,position);
        }
        Coord at=UI.scale(6,65);
        hit=Area.sized(at,caption.sz().add(UI.scale(6,4)));
        g.chcolor(0,0,0,150);g.frect(hit.ul,hit.sz());g.chcolor();
        g.image(caption,at.add(UI.scale(3,2)));
    }
    private boolean hit(Coord at){
        MiniMap.Location location=map.displayLocation();
        if(!painted||!visible()||location.seg.id!=segment||!location.tc.equals(center)||!map.sz.equals(size)||
            viewScale(location)!=scale||version!=MeteorTriangulation.generation()||!Objects.equals(world,hearthborn.ui.WorldIdentity.name(map.ui.sess)))return false;
        if(hit!=null&&hit.contains(at))return true;
        for(Area point:pointHits)if(point.contains(at))return true;
        return false;
    }
    public Object tooltip(Coord at){
        if(!hit(at))return null;
        return L10n.tr("Meteorite triangulation")+"\n"+L10n.tr("Use Star Gaze at night from different locations. Red sectors show directions; their shared area is highlighted in blue.")+"\n"+
            L10n.tr("The shaded area is an estimate, not a confirmed impact location.")+"\n"+
            (intersection.size()<3?L10n.tr("No shared area in this view. It may be offscreen, or the measurements may disagree.")+"\n":"")+
            L10n.tr("Right-click a measurement point, its number or this label to clear all measurements.");
    }
    public boolean click(Coord at,int button){
        if(!hit(at))return false;
        if(button==3){MeteorTriangulation.clear(map.ui);update();hit=null;pointHits.clear();return true;}
        return hit!=null&&hit.contains(at);
    }
    public void dispose(){if(caption!=null)caption.dispose();for(Tex number:numbers.values())number.dispose();}
}
