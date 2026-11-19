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
import haven.render.*;
import java.awt.Color;
import java.awt.geom.*;
import java.util.*;
/**
 * Outlines drawn where a hidden object stands, so the gap is not mistaken for open ground.
 *
 * @author Jagy2014
 */
public final class HiddenBoxes {
 private static final Map<MapView,Cache> cache=new WeakHashMap<>();
 private static final VertexArray.Layout FORMAT=new VertexArray.Layout(new VertexArray.Layout.Input(Homo3D.vertex,new VectorFormat(3,NumberFormat.FLOAT32),0,0,12));
 private static class Cache {
  double next;RenderTree.Slot slot;Model model;
  void clear(){if(slot!=null){slot.remove();slot=null;}if(model!=null){model.dispose();model=null;}}
 }
 private static boolean enabled(){return hearthborn.world.ObjectHiding.on()&&hearthborn.world.ObjectHiding.boxes();}
 public static void dispose(MapView map){Cache c=cache.remove(map);if(c!=null)c.clear();}
 private static void vertex(MapView map,java.util.List<Float> data,double x,double y){data.add((float)x);data.add((float)-y);data.add((float)map.ui.sess.glob.map.getcz(x,y)+.5f);}
 private static void line(MapView map,java.util.List<Float> data,double x,double y,double xx,double yy){
  int count=Math.max(1,(int)Math.ceil(Math.hypot(xx-x,yy-y)/5));
  for(int i=0;i<count;i++){double a=i/(double)count,b=(i+1)/(double)count;vertex(map,data,x+(xx-x)*a,y+(yy-y)*a);vertex(map,data,x+(xx-x)*b,y+(yy-y)*b);}
 }
 public static void update(MapView map) {
  if(!enabled()||map.ui==null||map.ui.sess==null||map.player()==null){dispose(map);return;}
  Cache current=cache.computeIfAbsent(map,key->new Cache());
  if(Utils.rtime()<current.next)return;
  try {
   java.util.List<Gob> objects=new ArrayList<>();synchronized(map.ui.sess.glob.oc){for(Gob gob:map.ui.sess.glob.oc){Drawable drawable=gob.getattr(Drawable.class);Resource res=drawable==null?null:drawable.getres();if(res!=null&&hearthborn.world.ObjectHiding.outlined(res.name))objects.add(gob);}}
   java.util.List<Float> data=new ArrayList<>();
   for(java.awt.geom.Area polygon:hearthborn.world.ObjectGeometry.hitboxes(objects,map.player())){
    PathIterator path=polygon.getPathIterator(null,.5);double[] point=new double[6];double x=0,y=0,firstX=0,firstY=0;
    while(!path.isDone()){int kind=path.currentSegment(point);
     if(kind==PathIterator.SEG_MOVETO){firstX=x=point[0];firstY=y=point[1];}
     else if(kind==PathIterator.SEG_LINETO){line(map,data,x,y,point[0],point[1]);x=point[0];y=point[1];}
     else if(kind==PathIterator.SEG_CLOSE)line(map,data,x,y,firstX,firstY);
     path.next();
    }
   }
   float[] vertices=new float[data.size()];for(int i=0;i<vertices.length;i++)vertices[i]=data.get(i);
   current.clear();
   if(vertices.length>0){
    current.model=new Model(Model.Mode.LINES,new VertexArray(FORMAT,new VertexArray.Buffer(vertices.length*4,DataBuffer.Usage.STATIC,DataBuffer.Filler.of(vertices))),null);
    current.slot=map.drawadd(new RenderTree.Node(){public void added(RenderTree.Slot slot){
     slot.ostate(Pipe.Op.compose(Rendered.last,States.maskdepth,new States.Depthtest(States.Depthtest.Test.LE),new BaseColor(new Color(0,225,255,200)),new States.LineWidth(2),p->p.put(Clickable.slot,null)));
     slot.add(current.model);
    }});
   }
   current.next=Utils.rtime()+.5;
  }catch(Loading ignored){current.next=Utils.rtime()+.1;}
 }
}
