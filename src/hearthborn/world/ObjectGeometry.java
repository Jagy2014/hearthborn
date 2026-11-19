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
import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.*;

/**
 * Loaded collision shapes shared by navigation, plans and world overlays.
 *
 * @author Jagy2014
 */
public final class ObjectGeometry {
    public static List<Area> obstacles(Iterable<Gob> objects,Gob player){return geometry(objects,player,true);}
    public static List<Area> hitboxes(Iterable<Gob> objects,Gob player){return geometry(objects,player,false);}
    private static List<Area> geometry(Iterable<Gob> objects, Gob player,boolean padding) {
        List<Area> result = new ArrayList<>();
        for(Gob gob : objects) {
            if(gob == player || gob.removed) continue;
            try {
                Drawable drawable = gob.getattr(Drawable.class);
                Resource res = drawable == null ? null : drawable.getres();
                if(res == null || res.name.equals("gfx/terobjs/arch/cellardoor")) continue;
                double stroke=padding?(res.name.startsWith("gfx/terobjs/stockpile-")?7:5):0;
                if(drawable instanceof ResDrawable) {
                    MessageBuf state=((ResDrawable)drawable).stateData();
                    if(res.name.startsWith("gfx/terobjs/arch/") && res.name.endsWith("gate") && !state.eom() && state.uint8()==1)continue;
                }
                List<Resource> sources=new ArrayList<>();sources.add(res);
                for(int index=0;index<sources.size() && index<16;index++) {
                    for(RenderLink.Res link:sources.get(index).layers(RenderLink.Res.class)) {
                        Resource linked=null;
                        if(link.l instanceof RenderLink.MeshMat)linked=((RenderLink.MeshMat)link.l).mesh.get();
                        else if(link.l instanceof RenderLink.ResSprite)linked=((RenderLink.ResSprite)link.l).res.get();
                        if(linked!=null && !sources.contains(linked))sources.add(linked);
                    }
                }
                for(Resource source:sources) {
                boolean explicit=false;
                for(Resource.Obstacle obstacle:source.layers(Resource.obst))if(!obstacle.id.equals("build")&&obstacle.p.length>0)explicit=true;
                if(!explicit)for(Resource.Neg neg : source.layers(Resource.negc)) {
                    if(neg.ac.x != neg.bc.x && neg.ac.y != neg.bc.y)
                        result.add(polygon(gob, new Coord2d[] {
                            new Coord2d(neg.ac.x, neg.ac.y), new Coord2d(neg.bc.x, neg.ac.y),
                            new Coord2d(neg.bc.x, neg.bc.y), new Coord2d(neg.ac.x, neg.bc.y)
                        },stroke));
                }
                for(Resource.Obstacle obstacle : source.layers(Resource.obst)) {
                    if(obstacle.id.equals("build")) continue;
                    for(Coord2d[] polygon : obstacle.p)
                        if(polygon.length >= 3) result.add(polygon(gob, polygon,stroke));
                }
                }
            } catch(Loading ignored) { }
        }
        return result;
    }

    private static Area polygon(Gob gob, Coord2d[] polygon,double stroke) {
        Path2D.Double path = new Path2D.Double();
        double cos = Math.cos(gob.a), sin = Math.sin(gob.a);
        for(int i = 0; i < polygon.length; i++) {
            Coord2d p = polygon[i];
            double x = gob.rc.x + p.x * cos - p.y * sin;
            double y = gob.rc.y + p.x * sin + p.y * cos;
            if(i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.closePath();
        Area area = new Area(path);
        if(stroke>0)area.add(new Area(new BasicStroke((float)stroke, BasicStroke.CAP_ROUND,
                                          BasicStroke.JOIN_ROUND).createStrokedShape(path)));
        return area;
    }

    private ObjectGeometry() {}
}
