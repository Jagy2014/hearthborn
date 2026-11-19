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
import haven.render.sl.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static haven.render.sl.Cons.*;

/**
 * A screen-facing contents icon that keeps the scene's depth test.
 *
 * @author Jagy2014
 */
public final class WorldIcon implements RenderTree.Node,Rendered,Disposable {
    private static final Map<Resource,Volume3f> SURFACES=new WeakHashMap<>();
    private static final Attribute OFFSET=new Attribute(Type.VEC2,"contents_offset");
    private static final VertexArray.Layout QUAD=new VertexArray.Layout(
        new VertexArray.Layout.Input(Homo3D.vertex,new VectorFormat(3,NumberFormat.FLOAT32),0,0,28),
        new VertexArray.Layout.Input(Tex2D.texc,new VectorFormat(2,NumberFormat.FLOAT32),0,12,28),
        new VertexArray.Layout.Input(OFFSET,new VectorFormat(2,NumberFormat.FLOAT32),0,20,28));
    private static final class Style extends State {
        static final Slot<Style> slot=new Slot<>(Slot.Type.DRAW,Style.class);
        final float height;final double coverage;final Volume3f surface;
        Style(float height,double coverage,Volume3f surface){this.height=height;this.coverage=coverage;this.surface=surface;}
        public void apply(Pipe pipe){pipe.put(slot,this);}
        public ShaderMacro shader(){return BILLBOARD;}
    }
    private static final Uniform EXTENT=new Uniform(Type.FLOAT,"contents_extent",pipe->{
        Style style=pipe.get(Style.slot);
        return (float)WorldLabels.iconExtent(transform(pipe),pipe.get(FrameConfig.slot).sz,style.height,style.coverage);
    },Style.slot,FrameConfig.slot,Homo3D.prj,Homo3D.cam,Homo3D.loc);
    private static final Uniform DEPTH=new Uniform(Type.FLOAT,"contents_depth",WorldIcon::surfaceDepth,
        Style.slot,Homo3D.prj,Homo3D.cam,Homo3D.loc);
    private static final ShaderMacro BILLBOARD=prog->{
        Homo3D.get(prog);
        prog.vctx.posv.mod(in->add(in,vec4(mul(div(mul(OFFSET.ref(),EXTENT.ref(),l(2.0)),FrameConfig.u_screensize.ref()),pick(in,"w")),l(0.0),l(0.0))),100);
        prog.vctx.posv.mod(in->vec4(pick(in,"xy"),mul(DEPTH.ref(),pick(in,"w")),pick(in,"w")),110);
    };
    private final List<RenderTree.Slot> slots=new ArrayList<>();
    private Style style;
    private final boolean centered;
    private final BooleanSupplier visible;
    private TexI image;
    private Model model;
    public WorldIcon(float height,double coverage,boolean centered,BooleanSupplier visible){
        style=new Style(height,coverage,null);this.centered=centered;this.visible=visible;
    }
    /** Preserve the screen anchor, but place the icon just in front of its own model. */
    public synchronized void surface(Resource resource){
        Volume3f surface=modelBounds(resource);
        if(surface==null||Objects.equals(surface,style.surface))return;
        style=new Style(style.height,style.coverage,surface);
        for(RenderTree.Slot slot:new ArrayList<>(slots)){slot.ostate(state());slot.update();}
    }
    /** Both uniforms are recomputed back to back for every icon, so the last object-to-clip matrix is shared. */
    private static final class Transform {
        final Object loc,cam,prj;final Matrix4f matrix;
        Transform(Object loc,Object cam,Object prj,Matrix4f matrix){this.loc=loc;this.cam=cam;this.prj=prj;this.matrix=matrix;}
    }
    private static volatile Transform lastTransform;
    /** Object space to clip space in one matrix: the projection, camera and location chain are walked once per icon. */
    static Matrix4f transform(Pipe pipe){
        Object loc=pipe.get(Homo3D.loc),cam=pipe.get(Homo3D.cam),prj=pipe.get(Homo3D.prj);
        Transform last=lastTransform;
        if(last!=null&&last.loc==loc&&last.cam==cam&&last.prj==prj)return last.matrix;
        Matrix4f matrix=Homo3D.prjxf(pipe).mul(Homo3D.camxf(pipe)).mul(Homo3D.locxf(pipe));
        lastTransform=new Transform(loc,cam,prj,matrix);
        return matrix;
    }
    static float surfaceDepth(Pipe pipe){
        Style style=pipe.get(Style.slot);
        Matrix4f transform=transform(pipe);
        HomoCoord4f anchor=transform.mul4(HomoCoord4f.of(new Coord3f(0,0,style.height)));
        float depth=anchor.w>0?anchor.z/anchor.w:1;
        if(style.surface!=null){
            Volume3f box=style.surface.margin(.05f);
            for(int corner=0;corner<8;corner++){
                HomoCoord4f point=transform.mul4(HomoCoord4f.of((corner&1)==0?box.n.x:box.p.x,
                    (corner&2)==0?box.n.y:box.p.y,(corner&4)==0?box.n.z:box.p.z));
                if(point.w>0)depth=Math.min(depth,point.z/point.w);
            }
        }
        return depth;
    }
    private static Volume3f include(Volume3f bounds,FastMesh.MeshRes mesh){
        if(mesh.m==null)throw new Loading();
        return bounds==null?mesh.m.bounds():bounds.include(mesh.m.bounds());
    }
    /** Model geometry is immutable once loaded; retry loading links before caching. */
    static synchronized Volume3f modelBounds(Resource resource){
        Volume3f known=SURFACES.get(resource);if(known!=null)return known;
        Volume3f bounds=null;List<Resource> sources=new ArrayList<>();sources.add(resource);
        for(int index=0;index<sources.size();index++){
            Resource source=sources.get(index);
            for(FastMesh.MeshRes mesh:source.layers(FastMesh.MeshRes.class))bounds=include(bounds,mesh);
            for(RenderLink.Res layer:source.layers(RenderLink.Res.class)){
                if(layer.l instanceof RenderLink.MeshMat){
                    RenderLink.MeshMat link=(RenderLink.MeshMat)layer.l;
                    for(FastMesh.MeshRes mesh:link.mesh.get().layers(FastMesh.MeshRes.class))
                        if(link.meshid<0||mesh.id==link.meshid){bounds=include(bounds,mesh);break;}
                }else if(layer.l instanceof RenderLink.ResSprite){
                    Resource linked=((RenderLink.ResSprite)layer.l).res.get();
                    if(!sources.contains(linked))sources.add(linked);
                }else if(layer.l instanceof RenderLink.Collect){
                    RenderLink.Collect link=(RenderLink.Collect)layer.l;
                    for(FastMesh.MeshRes mesh:link.from.get().layers(FastMesh.MeshRes.class))
                        if(!((link.meshid>=0&&mesh.id<0)||((mesh.id&link.meshmask)!=link.meshid)))bounds=include(bounds,mesh);
                }
            }
        }
        if(bounds!=null)SURFACES.put(resource,bounds);
        return bounds;
    }
    private Pipe.Op state(){
        return Pipe.Op.compose(Rendered.last,new States.Facecull(States.Facecull.Mode.NONE),
            pipe->{pipe.put(Clickable.slot,null);pipe.put(MixColor.slot,null);},
            States.maskdepth,new States.Depthtest(States.Depthtest.Test.LE),style,image==null?null:image.st());
    }
    public synchronized void set(TexI next){
        if(image==next)return;
        if(model!=null){model.dispose();model=null;}
        image=next;
        if(image!=null){
            Coord size=image.sz(),texture=image.st().data.tex.sz();
            float extent=Math.max(size.x,size.y),width=size.x/extent,height=size.y/extent;
            float x0=-width/2,x1=width/2,y0=centered?-height/2:0,y1=y0+height,z=style.height;
            float tw=size.x/(float)texture.x,th=size.y/(float)texture.y;
            float[] data={0,0,z,0,th,x0,y0, 0,0,z,tw,th,x1,y0, 0,0,z,0,0,x0,y1,
                0,0,z,tw,th,x1,y0, 0,0,z,tw,0,x1,y1, 0,0,z,0,0,x0,y1};
            model=new Model(Model.Mode.TRIANGLES,new VertexArray(QUAD,new VertexArray.Buffer(data.length*4,DataBuffer.Usage.STATIC,DataBuffer.Filler.of(data))),null);
        }
        for(RenderTree.Slot slot:new ArrayList<>(slots)){slot.ostate(state());slot.update();}
        sync();
    }
    /** Parent slot to the slot this icon holds under it, or null while hidden. */
    private final Map<RenderTree.Slot,RenderTree.Slot> hosts=new LinkedHashMap<>();
    /** Take a place under each of the object's slots only while the icon is shown: a hidden icon costs nothing per frame. */
    public synchronized void host(RenderTree.Slot parent){hosts.putIfAbsent(parent,null);sync();}
    /** Attach or detach the icon after its visibility changed; cheap enough to call every tick. */
    public synchronized void sync(){
        /* Without a picture there is nothing to draw, and every attached icon costs per-instance uniform work whenever the camera moves. */
        boolean want=!hosts.isEmpty()&&model!=null&&visible.getAsBoolean();
        for(Iterator<Map.Entry<RenderTree.Slot,RenderTree.Slot>> it=hosts.entrySet().iterator();it.hasNext();){
            Map.Entry<RenderTree.Slot,RenderTree.Slot> entry=it.next();
            RenderTree.Slot own=entry.getValue();
            if(want&&own==null){
                try{entry.setValue(entry.getKey().add(this));}
                catch(RenderTree.SlotRemoved gone){it.remove();}   // the object left the scene between the callbacks
            }else if(!want&&own!=null){
                entry.setValue(null);
                try{own.remove();}catch(RuntimeException gone){}
            }
        }
    }
    /** The object's slot is going away: forget it and its icon slot, which the tree removes with the parent. */
    public synchronized void unhost(RenderTree.Slot parent){
        RenderTree.Slot own=hosts.remove(parent);
        if(own!=null)try{own.remove();}catch(RuntimeException gone){}
    }
    public synchronized void unhost(){
        for(RenderTree.Slot own:hosts.values())if(own!=null)try{own.remove();}catch(RuntimeException gone){}
        hosts.clear();
    }
    public synchronized void added(RenderTree.Slot slot){slot.ostate(state());slots.add(slot);}
    public synchronized void removed(RenderTree.Slot slot){slots.remove(slot);}
    public synchronized void draw(Pipe context,Render out){if(model!=null&&visible.getAsBoolean())out.draw(context,model);}
    public synchronized void dispose(){unhost();set(null);}
}
