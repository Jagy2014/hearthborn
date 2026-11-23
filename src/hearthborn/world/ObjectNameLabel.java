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
import hearthborn.quality.RosterStore;
import hearthborn.timers.ObjectTimers;
import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.*;

/**
 * Personal object names follow the object's render transform and sit above timers. House names sit above the door.
 *
 * @author Jagy2014
 */
public final class ObjectNameLabel extends GAttrib implements RenderTree.Node,PView.Render2D {
    private static final Map<String,Float> doorTops=new HashMap<>();
    private static final Map<String,float[]> doorSlabs=new HashMap<>();
    private String shown, shownQuality;
    private Tex label;
    private double refresh;
    private boolean rack,atDoor;
    /** Highest point of the model, or -1 while it is unknown. */
    private float top=-1;
    private float doorX,doorY,doorZ;

    public ObjectNameLabel(Gob gob){super(gob);refresh=(gob.id&15)*(0.5/16.0);}

    public void ctick(double dt){
        refresh-=dt;
        if(refresh>0)return;
        refresh=.5;
        String name=ObjectNames.hasNames()?ObjectNames.label(gob):null;
        String quality=null;
        rack=false;
        atDoor=false;
        try{
            Drawable drawable=gob.getattr(Drawable.class);Resource resource=drawable==null?null:drawable.getres();
            if(resource!=null&&ObjectTimers.furnace(resource.name)){
                RosterStore.Rec record=RosterStore.trackedGob(gob);
                if(record!=null&&record.q>0){
                    quality="q"+RosterStore.fmtQ(record.q).replaceFirst("\\.0$", "");
                    if(name==null)name=RosterStore.buildingDisplayName(record);
                }
            }
            if(name!=null&&resource!=null){
                rack=resource.name.equals("gfx/terobjs/cheeserack");
                locateDoor(resource);
                top=modelTop(resource);
            }else top=-1;
        }catch(Loading pending){}
        if(Objects.equals(name,shown)&&Objects.equals(quality,shownQuality))return;
        shown=name;
        shownQuality=quality;
        if(label!=null)label.dispose();
        label=caption();
    }

    private Tex caption(){
        if(shown==null)return null;
        return shownQuality==null ? WorldLabels.caption(shown,Color.WHITE,false)
            : WorldLabels.captionLines(shown,shownQuality,Color.WHITE);
    }

    private void locateDoor(Resource resource){
        if(resource==null||!resource.name.startsWith("gfx/terobjs/arch/")||resource.name.endsWith("-door")||gob.glob==null||gob.rc==null)return;
        float[] door=modeledDoor(resource);
        if(door==null){
            List<Gob> nearby=new ArrayList<>();
            synchronized(gob.glob.oc){for(Gob other:gob.glob.oc)nearby.add(other);}
            door=doorPoint(gob,nearby);
        }
        if(door==null)return;
        doorX=door[0];doorY=door[1];doorZ=door[2];atDoor=true;
    }

    /** Local point above this house's door, or null when no door is part of the model or loaded beside it. */
    static float[] doorPoint(Gob house,Iterable<Gob> nearby){
        Resource resource=resource(house);
        if(resource==null||!resource.name.startsWith("gfx/terobjs/arch/")||resource.name.endsWith("-door"))return null;
        float[] modeled=modeledDoor(resource);
        if(modeled!=null)return modeled;
        String doorName=resource.name+"-door";
        float reach=reach(resource);
        Gob door=null;double best=reach;
        for(Gob other:nearby){
            if(other==house||other.rc==null||house.rc==null)continue;
            double distance=other.rc.dist(house.rc);
            if(distance>best)continue;
            Resource otherResource=resource(other);
            if(otherResource!=null&&otherResource.name.equals(doorName)){door=other;best=distance;}
        }
        if(door==null)return null;
        double cos=Math.cos(house.a),sin=Math.sin(house.a);
        double dx=door.rc.x-house.rc.x,dy=door.rc.y-house.rc.y;
        return new float[]{(float)(dx*cos+dy*sin),(float)(-dx*sin+dy*cos),doorTop(resource(door))+1f};
    }

    /** The door is the ground-level slab on the wall, not the roof or a fitting above the sill. */
    static float[] doorSlab(float[][] boxes){
        float[] chosen=null;float score=0;
        if(boxes==null)return null;
        for(float[] box:boxes){
            if(box==null||box.length<6)continue;
            float dx=box[3]-box[0],dy=box[4]-box[1],dz=box[5]-box[2],thin=Math.min(dx,dy),wide=Math.max(dx,dy);
            if(box[2]>2||dz<8||dz>24||thin>12||wide<6||wide>40)continue;
            float next=wide*dz;
            if(next<=score)continue;
            score=next;chosen=new float[]{(box[0]+box[3])/2f,(box[1]+box[4])/2f,box[5]+1f};
        }
        return chosen;
    }
    private static float[] modeledDoor(Resource resource){
        if(doorSlabs.containsKey(resource.name))return doorSlabs.get(resource.name);
        ArrayList<float[]> boxes=new ArrayList<>();boolean pending=false;
        for(FastMesh.MeshRes mesh:resource.layers(FastMesh.MeshRes.class)){
            if(mesh.m==null){pending=true;continue;}
            Volume3f box=mesh.m.bounds();
            boxes.add(new float[]{box.n.x,box.n.y,box.n.z,box.p.x,box.p.y,box.p.z});
        }
        if(pending&&boxes.isEmpty())return null;
        float[] at=doorSlab(boxes.toArray(new float[boxes.size()][]));
        if(at!=null||!pending)doorSlabs.put(resource.name,at);
        return at;
    }
    private static Resource resource(Gob gob){
        try{Drawable drawable=gob.getattr(Drawable.class);return drawable==null?null:drawable.getres();}
        catch(Loading pending){return null;}
    }

    private static float reach(Resource resource){
        float reach=56;
        for(Resource.Obstacle obstacle:resource.layers(Resource.obst)){
            if(obstacle.id.equals("build"))continue;
            for(Coord2d[] polygon:obstacle.p)for(Coord2d point:polygon)reach=Math.max(reach,(float)Math.hypot(point.x,point.y)+8);
        }
        return reach;
    }

    private static final Map<String,Float> modelTops=new HashMap<>();
    /** Highest vertex of the object's meshes, cached per resource; -1 when it has none (animated or still loading). */
    static float modelTop(Resource resource){
        Float known=modelTops.get(resource.name);
        if(known!=null)return known;
        float top=-1;
        for(VertexBuf.VertexRes layer:resource.layers(VertexBuf.VertexRes.class))for(VertexBuf.AttribData data:layer.b.bufs)
            if(data instanceof VertexBuf.VertexData){
                FloatBuffer positions=((VertexBuf.VertexData)data).data;
                for(int i=2;i<positions.capacity();i+=3)top=Math.max(top,positions.get(i));
            }
        modelTops.put(resource.name,top);return top;
    }
    /** Low objects such as benches get their name just above them instead of at the fixed height made for ovens and smelters. */
    static final float LOW=12;
    static boolean low(float top){return top>0&&top<LOW;}

    private static float doorTop(Resource resource){
        if(resource==null)return 12;
        Float known=doorTops.get(resource.name);
        if(known!=null)return known;
        float top=12;
        for(VertexBuf.VertexRes layer:resource.layers(VertexBuf.VertexRes.class))for(VertexBuf.AttribData data:layer.b.bufs)
            if(data instanceof VertexBuf.VertexData){
                FloatBuffer positions=((VertexBuf.VertexData)data).data;
                for(int i=2;i<positions.capacity();i+=3)top=Math.max(top,positions.get(i));
            }
        doorTops.put(resource.name,top);return top;
    }

    public void draw(GOut g,Pipe state){
        if(label==null)return;
        if(WorldLabels.stale(label)){label.dispose();label=caption();}
        Coord at=atDoor?WorldLabels.anchor(state,g.sz(),doorX,doorY,doorZ):anchor(state,g.sz(),rack,top);
        if(at!=null)WorldLabels.draw(g,label,at,.5,1);
    }

    static Coord anchor(Pipe state,Coord viewport,boolean rack,float top){
        if(!rack&&low(top)){
            Coord near=WorldLabels.anchor(state,viewport,top+2);
            return near==null?null:near.add(0,-UI.scale(4));
        }
        return anchor(state,viewport,rack);
    }

    static Coord anchor(Pipe state,Coord viewport,boolean rack){
        // The rack model is 18 world units high. Keep the label just above its top at every zoom.
        Coord at=WorldLabels.anchor(state,viewport,rack?20:15);
        return at==null||rack?at:at.add(0,-UI.scale(20));
    }

    public void dispose(){if(label!=null)label.dispose();label=null;}
}
