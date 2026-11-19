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

package hearthborn.planner;
import hearthborn.storage.ContainerContents;
import hearthborn.timers.ObjectTimers;
import hearthborn.world.ObjectHighlight;
import hearthborn.world.ObjectNames;

import haven.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.world.*;
import hearthborn.cheese.CheeseAging;
import java.awt.Color;
import java.util.*;

/**
 * A local floor plan of the currently loaded room; never infers hidden contents.
 *
 * @author Jagy2014
 */
public final class RoomPlan {
    private final Widget map;
    private Set<Coord> floor=Collections.emptySet();
    private final java.util.List<Marker> markers=new ArrayList<>();
    private Coord minimum,maximum;
    private Gob player;
    private double refreshed;
    private long playerId=Long.MIN_VALUE;
    private MCache.Grid sourceGrid;
    private long sourceGridId;
    private int sourceGridSeq;
    private boolean active,locationKnown;
    private boolean cellarPlan;
    private String roomIdentity;
    private Coord2d arrival;
    private boolean arrivalStairs;
    private String title;
    private double scale;
    private Coord origin;
    private int turns;
    private Coord rotatedSize;
    private static final class Marker {
        final Gob gob;final String kind;final boolean ready;Coord screen,box,span;
        Marker(Gob gob,String kind,boolean ready){this.gob=gob;this.kind=kind;this.ready=ready;}
    }
    /** Cupboards and the other furniture fill one floor square. Stairs up fill two. */
    static java.util.List<Coord> squares(String resource,Coord2d at,double angle,double[] bounds){
        if(resource!=null&&resource.contains("upstairs"))return upstairs(at,angle,bounds);
        java.util.List<Coord> covered=covered(at,bounds);
        if(covered.isEmpty())return Collections.singletonList(at.floor(MCache.tilesz));
        return Collections.singletonList(nearest(covered,center(at,bounds),1).get(0));
    }
    private static java.util.List<Coord> upstairs(Coord2d at,double angle,double[] bounds){
        java.util.List<Coord> covered=covered(at,bounds);Coord2d middle=center(at,bounds);
        if(covered.size()>=2){
            java.util.List<Coord> pair=nearest(covered,middle,2);
            if(Math.abs(pair.get(0).x-pair.get(1).x)+Math.abs(pair.get(0).y-pair.get(1).y)==1)return pair;
        }
        Coord base=covered.isEmpty()?at.floor(MCache.tilesz):nearest(covered,middle,1).get(0);
        return java.util.Arrays.asList(base,base.add(stairStep(angle)));
    }
    private static Coord stairStep(double angle){
        switch(Math.floorMod((int)Math.round(angle/(Math.PI/2)),4)){
        case 1:return Coord.of(-1,0);case 2:return Coord.of(0,-1);case 3:return Coord.of(1,0);default:return Coord.of(0,1);
        }
    }
    private static Coord2d center(Coord2d at,double[] bounds){
        if(bounds==null||bounds.length<4)return at;
        return at.add((bounds[0]+bounds[2])/2.0,(bounds[1]+bounds[3])/2.0);
    }
    private static java.util.List<Coord> covered(Coord2d at,double[] bounds){
        java.util.List<Coord> tiles=new ArrayList<>();if(at==null)return tiles;
        if(bounds==null||bounds.length<4){tiles.add(at.floor(MCache.tilesz));return tiles;}
        double eps=0.25;Coord lo=at.add(bounds[0],bounds[1]).add(eps,eps).floor(MCache.tilesz);Coord hi=at.add(bounds[2],bounds[3]).sub(eps,eps).floor(MCache.tilesz);
        if(hi.x<lo.x||hi.y<lo.y){tiles.add(at.floor(MCache.tilesz));return tiles;}
        for(int y=lo.y;y<=hi.y;y++)for(int x=lo.x;x<=hi.x;x++)tiles.add(Coord.of(x,y));
        return tiles;
    }
    private static java.util.List<Coord> nearest(java.util.List<Coord> tiles,Coord2d at,int count){
        java.util.List<Coord> ordered=new ArrayList<>(tiles);
        ordered.sort((left,right)->Double.compare(tileCenter(left).dist(at),tileCenter(right).dist(at)));
        return new ArrayList<>(ordered.subList(0,Math.min(count,ordered.size())));
    }
    private static Coord2d tileCenter(Coord tile){return new Coord2d((tile.x+.5)*MCache.tilesz.x,(tile.y+.5)*MCache.tilesz.y);}
    public RoomPlan(MiniMap map){this((Widget)map);}
    public RoomPlan(Widget map){this.map=map;}
    public static boolean enabled(){return Utils.getprefb("hearthborn/map/room-plan",true);}
    public static void enabled(boolean enabled){Utils.setprefb("hearthborn/map/room-plan",enabled);}
    static boolean empty(String tile){return tile==null||tile.equals("gfx/tiles/nil")||tile.equals("gfx/tiles/notile");}
    private String tile(MCache cache,Coord at){Resource res=cache.tilesetr(cache.gettile(at));return res==null?null:res.name;}
    /** Bounded flood fill rejects the overworld and large cave systems. */
    static Set<Coord> room(Coord start,java.util.function.Function<Coord,String> tiles){
        Set<Coord> seen=new HashSet<>(),result=new HashSet<>();ArrayDeque<Coord> queue=new ArrayDeque<>();queue.add(start);
        while(!queue.isEmpty()){
            Coord at=queue.remove();if(!seen.add(at))continue;
            String tile=tiles.apply(at);if(empty(tile))continue;
            if(tile.contains("/cave")||tile.startsWith("gfx/tiles/rocks/"))return Collections.emptySet();
            if(Math.abs(at.x-start.x)>40||Math.abs(at.y-start.y)>40||result.size()>=1024)return Collections.emptySet();
            result.add(at);queue.add(at.add(1,0));queue.add(at.add(-1,0));queue.add(at.add(0,1));queue.add(at.add(0,-1));
        }
        return result;
    }
    static boolean passage(String kind){return "Exit".equals(kind)||"Stairs".equals(kind)||"Basement".equals(kind)||"Cellar stairs".equals(kind);}
    static String kind(String resource){
        if(resource==null)return null;
        if(resource.startsWith("gfx/terobjs/arch/")&&(resource.contains("door")||resource.contains("stairs")||resource.contains("ladder"))){
            if(resource.contains("cellarstairs"))return "Cellar stairs";
            if(resource.contains("cellar"))return "Basement";
            String leaf=resource.substring(resource.lastIndexOf('/')+1);
            if(leaf.contains("stairs")||leaf.equals("ladder")||leaf.endsWith("ladder"))return "Stairs";
            return "Exit";
        }
        String name=resource.substring(resource.lastIndexOf('/')+1);
        switch(name){
        case "cupboard":return "Cupboard";case "cheeserack":return "Rack";
        case "oven":return "Oven";case "smelter":return "Ore Smelter";case "kiln":return "Kiln";
        case "htable":return "Herbalist Table";case "steelcrucible":return "Steel Crucible";case "crucible":return "Crucible";
        case "chest":case "lchest":case "crate":case "metalcabinet":return "Chest";
        default:return null;
        }
    }
    private void refresh(){
        GameUI gui=map.getparent(GameUI.class);if(gui==null||gui.map==null)return;
        Gob current=gui.map.player();if(current==null){active=false;return;}
        MCache cache=current.glob.map;MCache.Grid grid;
        try{grid=cache.getgridt(current.rc.floor(MCache.tilesz));}
        catch(Loading pending){active=false;locationKnown=false;return;}
        boolean changed=grid!=sourceGrid||grid.id!=sourceGridId||grid.seq!=sourceGridSeq;
        double now=Utils.rtime();if(locationKnown&&!changed&&now-refreshed<.5&&current.id==playerId)return;
        refreshed=now;playerId=current.id;player=current;active=false;locationKnown=false;markers.clear();
        sourceGrid=grid;sourceGridId=grid.id;sourceGridSeq=grid.seq;
        try{
            Set<Coord> next=room(current.rc.floor(MCache.tilesz),at->tile(cache,at));
            if(next.isEmpty()){locationKnown=true;roomIdentity=null;arrival=null;return;}
            floor=next;int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;
            boolean cellar=false;for(Coord at:floor){minX=Math.min(minX,at.x);minY=Math.min(minY,at.y);maxX=Math.max(maxX,at.x);maxY=Math.max(maxY,at.y);cellar|=tile(cache,at).contains("cellar");}
            minimum=Coord.of(minX,minY);maximum=Coord.of(maxX+1,maxY+1);cellarPlan=cellar;title=L10n.tr(cellar?"Basement":"Room plan");
            java.util.List<Gob> objects=new ArrayList<>();
            synchronized(current.glob.oc){for(Gob gob:current.glob.oc)objects.add(gob);}
            for(Gob gob:objects)try{
                if(gob.removed||gob.rc==null)continue;
                Coord at=gob.rc.floor(MCache.tilesz);if(at.x<minX-1||at.y<minY-1||at.x>maxX+1||at.y>maxY+1)continue;
                String kind=kind(GameObjects.resourceName(gob));if(kind!=null)markers.add(new Marker(gob,kind,(kind.equals("Rack")&&CheeseAging.ready(gob))||ObjectTimers.ready(gob)));
            }catch(Loading ignored){}
            for(Marker marker:markers)if(passage(marker.kind)){
                String exit=GameObjects.resourceName(marker.gob);
                if(exit!=null&&exit.contains("cellarstairs")){cellarPlan=true;title=L10n.tr("Basement");}
            }
            markers.sort(Comparator.comparingLong(marker->marker.gob.id));
            MCache.Grid roomGrid=cache.getgridt(minimum);
            String identity=roomGrid.id+":"+minimum.sub(roomGrid.ul)+":"+maximum.sub(roomGrid.ul);
            if(!identity.equals(roomIdentity)){roomIdentity=identity;arrival=null;}
            if(arrival==null){
                Marker nearest=null;double distance=Double.MAX_VALUE;
                for(Marker marker:markers)if(passage(marker.kind)&&marker.gob.rc.dist(current.rc)<distance){nearest=marker;distance=marker.gob.rc.dist(current.rc);}
                if(nearest!=null){arrival=nearest.gob.rc;String resource=GameObjects.resourceName(nearest.gob);arrivalStairs=resource!=null&&(resource.contains("stairs")||resource.contains("ladder"));}
            }
            active=true;locationKnown=true;
        }catch(Loading ignored){floor=Collections.emptySet();}
    }
    private Coord2d rotate(double x,double y){
        double width=maximum.x-minimum.x,height=maximum.y-minimum.y;
        switch(turns){case 1:return new Coord2d(height-y,x);case 2:return new Coord2d(width-x,height-y);case 3:return new Coord2d(y,width-x);default:return new Coord2d(x,y);}
    }
    private void orient(){
        turns=0;double farthest=-1;Marker entrance=null;
        for(Marker marker:markers)if(passage(marker.kind)){
            String resource=GameObjects.resourceName(marker.gob);
            if(resource==null||!(cellarPlan?(resource.contains("stairs")||resource.contains("ladder")||resource.contains("door")):resource.contains("door")))continue;
            double x=marker.gob.rc.x/MCache.tilesz.x-(minimum.x+maximum.x)/2.0;
            double y=marker.gob.rc.y/MCache.tilesz.y-(minimum.y+maximum.y)/2.0;
            double distance=x*x+y*y;if(distance>farthest){farthest=distance;entrance=marker;}
        }
        boolean stairCorner=cellarPlan;
        if(arrivalStairs && arrival!=null && (entrance==null || cellarPlan)){
            for(Marker marker:markers)if(passage(marker.kind)&&marker.gob.rc.dist(arrival)<1){entrance=marker;stairCorner=true;break;}
        }
        if(entrance==null){
            for(Marker marker:markers)if(passage(marker.kind)){
                String resource=GameObjects.resourceName(marker.gob);
                if(resource!=null&&(resource.contains("stairs")||resource.contains("ladder"))){entrance=marker;stairCorner=true;break;}
            }
        }
        if(entrance!=null){
            double x=entrance.gob.rc.x/MCache.tilesz.x-(minimum.x+maximum.x)/2.0;
            double y=entrance.gob.rc.y/MCache.tilesz.y-(minimum.y+maximum.y)/2.0;
            if(stairCorner){
                double best=-Double.MAX_VALUE;int selected=0;
                for(int rotation=0;rotation<4;rotation++){
                    turns=rotation;Coord2d at=rotate(x+(maximum.x-minimum.x)/2.0,y+(maximum.y-minimum.y)/2.0);
                    double width=rotation%2==0?maximum.x-minimum.x:maximum.y-minimum.y;
                    double height=rotation%2==0?maximum.y-minimum.y:maximum.x-minimum.x;
                    double score=at.x/width+at.y/height;if(score>best){best=score;selected=rotation;}
                }
                turns=selected;
            }else turns=Math.abs(x)>Math.abs(y)?(x>0?1:3):(y<0?2:0);
        }
        Coord size=maximum.sub(minimum);rotatedSize=turns%2==0?size:Coord.of(size.y,size.x);
    }
    private Coord point(Coord2d world){Coord2d at=rotate(world.x/MCache.tilesz.x-minimum.x,world.y/MCache.tilesz.y-minimum.y);return origin.add((int)Math.round(at.x*scale),(int)Math.round(at.y*scale));}
    private Coord cellOrigin(Coord tile){
        Coord2d a=rotate(tile.x-minimum.x,tile.y-minimum.y),b=rotate(tile.x+1-minimum.x,tile.y+1-minimum.y);
        return origin.add((int)(Math.min(a.x,b.x)*scale),(int)(Math.min(a.y,b.y)*scale));
    }
    private Coord cellSize(){return Coord.of((int)Math.ceil(scale),(int)Math.ceil(scale));}
    public boolean draw(GOut g){
        refresh();if(!enabled()||!active)return false;
        int top=UI.scale(44),bottom=UI.scale(24),margin=UI.scale(12);
        orient();Coord extent=rotatedSize;scale=Math.min((double)Math.max(1,map.sz.x-2*margin)/extent.x,(double)Math.max(1,map.sz.y-top-bottom)/extent.y);
        origin=Coord.of((map.sz.x-(int)(extent.x*scale))/2,top+(map.sz.y-top-bottom-(int)(extent.y*scale))/2);
        g.chcolor(0,0,0,255);g.frect(Coord.z,map.sz);g.chcolor();
        long ready=markers.stream().filter(marker->marker.ready).count();
        g.chcolor(new Color(238,218,159));g.atext(title,Coord.of(map.sz.x/2,UI.scale(32)),.5,0);g.chcolor();
        for(Coord tile:floor){
            Coord2d a=rotate(tile.x-minimum.x,tile.y-minimum.y),b=rotate(tile.x+1-minimum.x,tile.y+1-minimum.y);
            Coord at=origin.add((int)(Math.min(a.x,b.x)*scale),(int)(Math.min(a.y,b.y)*scale));
            Coord size=Coord.of((int)Math.ceil(scale),(int)Math.ceil(scale));g.chcolor(44,44,44,255);g.frect(at,size);g.chcolor(70,70,70,255);g.rect(at,size);
        }
        g.chcolor();
        for(Marker marker:markers){
            String resource=GameObjects.resourceName(marker.gob);double[] bounds=ClaimPlan.footprint(marker.gob);
            java.util.List<Coord> tiles=squares(resource,marker.gob.rc,marker.gob.a,bounds);
            Coord box=null,end=null;Coord cell=cellSize();int pad=cell.x>4&&cell.y>4?1:0;
            g.chcolor(passage(marker.kind)?new Color(108,190,246):marker.ready?new Color(135,228,83):new Color(213,178,96));
            for(Coord tile:tiles){
                Coord at=cellOrigin(tile);Coord br=at.add(cell);
                box=box==null?at:Coord.of(Math.min(box.x,at.x),Math.min(box.y,at.y));
                end=end==null?br:Coord.of(Math.max(end.x,br.x),Math.max(end.y,br.y));
                g.frect(at.add(pad,pad),cell.sub(pad*2,pad*2));
            }
            if(box==null)continue;
            marker.box=box;marker.span=end.sub(box);marker.screen=box.add(marker.span.div(2));g.chcolor();
            ContainerContents contents=marker.gob.getattr(ContainerContents.class);Tex icon=contents==null?null:contents.picture();
            int limit=Math.min(marker.span.x,marker.span.y)-UI.scale(4);if(limit<4)limit=Math.max(1,Math.min(marker.span.x,marker.span.y));
            if(icon!=null){Coord size=icon.sz();double ratio=(double)limit/Math.max(size.x,size.y);Coord drawn=Coord.of(Math.max(1,(int)(size.x*ratio)),Math.max(1,(int)(size.y*ratio)));g.image(icon,marker.screen.sub(drawn.div(2)),drawn);}
            else {
                String glyph="Exit".equals(marker.kind)?"↗":passage(marker.kind)?L10n.tr(marker.kind):L10n.text("window",marker.kind).substring(0,1);
                GOut clipped=g.reclip(box,marker.span);clipped.chcolor(Color.BLACK);clipped.atext(glyph,marker.span.div(2),.5,.5);clipped.chcolor();
            }
            if(marker.ready){g.chcolor(132,255,78,255);g.frect(box.add(marker.span.x-UI.scale(5),-UI.scale(2)),UI.scale(4,4));g.chcolor();}
        }
        Coord self=point(player.rc);g.chcolor(255,85,65,255);g.fellipse(self,UI.scale(3,3));g.chcolor();
        if(ready>0){g.chcolor(155,236,112,255);g.atext(L10n.format("Ready: %d",ready),Coord.of(map.sz.x/2,map.sz.y-UI.scale(20)),.5,0);g.chcolor();}
        return true;
    }
    private Marker hit(Coord at){Marker best=null;double distance=Double.MAX_VALUE;for(Marker marker:markers)if(marker.box!=null&&marker.span!=null&&at.isect(marker.box,marker.span)&&marker.screen.dist(at)<distance){distance=marker.screen.dist(at);best=marker;}return best;}
    public boolean active(){refresh();return enabled()&&active;}
    public boolean indoors(){refresh();return active;}
    public boolean locationKnown(){refresh();return locationKnown;}
    public String identity(){refresh();return active?roomIdentity:null;}
    public boolean click(Coord at,int button){refresh();if(!enabled()||!active)return false;if(button==1){Marker marker=hit(at);if(marker!=null)marker.gob.setattr(new ObjectHighlight(marker.gob));}return true;}
    public Object tooltip(Coord at){refresh();if(!enabled()||!active)return null;Marker marker=hit(at);if(marker==null)return title;
        String name=ObjectNames.name(marker.gob);int seconds=ObjectTimers.remainingSeconds(marker.gob);
        String status=marker.ready?L10n.tr("Ready"):seconds>0?L10n.format("Time left: %s",String.format("%d:%02d",seconds/60,seconds%60)):"";
        return (name==null?L10n.text("window",marker.kind):name)+(status.isEmpty()?"":" — "+status);
    }
}
