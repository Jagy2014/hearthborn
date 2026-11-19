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
import hearthborn.L10n;
import hearthborn.ui.NearbySiegeCheck;
import hearthborn.util.GameObjects;
import java.awt.*;
import java.util.*;

/**
 * Local directional pins for loaded siege engines.
 *
 * @author Jagy2014
 */
public final class SiegePointers implements Disposable {
    private final Map<Long,Label> labels=new HashMap<>();
    private static final class Label {String text;Tex tex;void dispose(){if(tex!=null)tex.dispose();}}
    private static void triangle(GOut g,Coord a,Coord b,Coord c){g.drawp(haven.render.Model.Mode.TRIANGLES,new float[]{a.x+g.tx.x,a.y+g.tx.y,b.x+g.tx.x,b.y+g.tx.y,c.x+g.tx.x,c.y+g.tx.y});}
    private static void stroke(GOut g,Coord a,Coord b,int width){
        double angle=Math.atan2(b.y-a.y,b.x-a.x);Coord offset=Coord.of((int)Math.round(-Math.sin(angle)*width/2.0),(int)Math.round(Math.cos(angle)*width/2.0));
        Coord p=a.add(offset),q=a.sub(offset),r=b.add(offset),s=b.sub(offset);triangle(g,p,q,r);triangle(g,q,r,s);
    }
    private static void arrow(GOut g,Coord at,double angle){
        Coord tip=at.add(Coord.sc(angle,UI.scale(16))),base=at.add(Coord.sc(angle,UI.scale(2))),tail=at.add(Coord.sc(angle,-UI.scale(14)));
        Coord wing=Coord.sc(angle+Math.PI/2,UI.scale(9));
        g.chcolor(0,0,0,220);stroke(g,tail,base,UI.scale(7));triangle(g,tip,base.add(wing),base.sub(wing));
        g.chcolor(255,45,45,255);stroke(g,tail,base,UI.scale(4));triangle(g,tip,base.add(wing.mul(.75)),base.sub(wing.mul(.75)));g.chcolor();
    }
    static Coord edge(Coord size,double angle){
        double dx=Math.cos(angle),dy=-Math.sin(angle),x=Math.max(1,size.x/2.0-UI.scale(65)),y=Math.max(1,size.y/2.0-UI.scale(80));
        double radius=Math.min(Math.abs(dx)<1e-9?Double.POSITIVE_INFINITY:x/Math.abs(dx),Math.abs(dy)<1e-9?Double.POSITIVE_INFINITY:y/Math.abs(dy));
        return size.div(2).add((int)Math.round(dx*radius),(int)Math.round(dy*radius));
    }
    static java.util.List<Gob> targets(OCache cache,Coord2d player){
        java.util.List<Gob> objects=new ArrayList<>();
        synchronized(cache){for(Gob gob:cache)objects.add(gob);}
        objects.removeIf(gob->{
            try{
                return !NearbySiegeCheck.pointerVisible(gob,player);
            }catch(Loading pending){return true;}
        });
        return objects;
    }
    private static OCache scanned;
    private static double scannedAt;
    private static java.util.List<Gob> found=java.util.Collections.emptyList();
    /** The full scan of every object runs a few times a second, not once per frame and per view; the pointers still follow their objects every frame. */
    static synchronized java.util.List<Gob> recentTargets(OCache cache,Coord2d player){
        double now=Utils.rtime();
        if(cache!=scanned||now-scannedAt>.25){scanned=cache;scannedAt=now;found=targets(cache,player);}
        java.util.List<Gob> live=new ArrayList<>(found.size());
        for(Gob gob:found)if(!gob.removed&&gob.rc!=null)live.add(gob);
        return live;
    }
    public void draw(MapView map,GOut g){
        if(!NearbySiegeCheck.enabled()||map.ui==null||map.ui.sess==null){disposeLabels();return;}
        Gob player=map.player();if(player==null||player.rc==null)return;
        Set<Long> visible=new HashSet<>();
        for(Gob gob:recentTargets(map.ui.sess.glob.oc,player.rc))try{
            String kind=NearbySiegeCheck.kind(GameObjects.resourceName(gob));
            double angle=map.screenangle(gob.rc,false);if(!Double.isFinite(angle))continue;
            Coord3f projected=map.screenxf(gob.getc().add(0,0,20));if(projected==null)continue;Coord at=projected.round2();
            boolean outside=!at.isect(UI.scale(65,80),map.sz.sub(UI.scale(130,160)));
            if(outside)at=edge(map.sz,angle);
            arrow(g,at,outside?angle:-Math.PI/2);
            Label label=labels.computeIfAbsent(gob.id,id->new Label());visible.add(gob.id);
            String text=L10n.tr(kind)+" · "+(int)Math.ceil(player.rc.dist(gob.rc)/MCache.tilesz.x)+" "+(L10n.language().equals("ru")?"тайл.":"tiles");
            if(!text.equals(label.text)||WorldLabels.stale(label.tex)){label.dispose();label.text=text;label.tex=WorldLabels.label(text,Color.WHITE,false);}
            WorldLabels.draw(g,label.tex,at.add(0,UI.scale(19)),.5,0);

        }catch(Loading ignored){}
        for(Iterator<Map.Entry<Long,Label>> it=labels.entrySet().iterator();it.hasNext();){Map.Entry<Long,Label> entry=it.next();if(!visible.contains(entry.getKey())){entry.getValue().dispose();it.remove();}}
    }
    public void drawMap(MiniMap map,GOut g){
        if(!NearbySiegeCheck.enabled()||map.ui==null||map.ui.sess==null||map.sessloc==null||map.displayLocation()==null||map.displayLocation().seg!=map.sessloc.seg)return;
        GameUI gui=map.getparent(GameUI.class);Gob player=gui==null||gui.map==null?null:gui.map.player();
        for(Gob gob:recentTargets(map.ui.sess.glob.oc,player==null?null:player.rc))try{
            Coord at=map.p2c(gob.rc);int radius=UI.scale(7);if(at.isect(Coord.z,map.sz)){
                GOut clipped=g.reclip(Coord.z,map.sz);Coord a=at.sub(radius,radius),b=at.add(radius,radius),c=at.add(-radius,radius),d=at.add(radius,-radius);
                clipped.chcolor(0,0,0,230);stroke(clipped,a,b,UI.scale(5));stroke(clipped,c,d,UI.scale(5));
                clipped.chcolor(255,35,35,255);stroke(clipped,a,b,UI.scale(3));stroke(clipped,c,d,UI.scale(3));clipped.chcolor();
            }
        }catch(Loading ignored){}
    }
    private void disposeLabels(){for(Label label:labels.values())label.dispose();labels.clear();}
    public void dispose(){disposeLabels();}
}
