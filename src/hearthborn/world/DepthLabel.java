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
import haven.*;import haven.render.*;import haven.render.sl.*;import java.util.*;
import static haven.render.sl.Cons.*;
/**
 * Pixel-sized billboard. It is depth tested, but pulled toward the camera by {@link #CLEARANCE}, so the plant's own leaves
 * and the neighbouring rows never hide the stage, while a building standing in front of the field does.
 *
 * @author Jagy2014
 */
public final class DepthLabel implements RenderTree.Node,Rendered,Disposable {
    private static final Attribute OFFSET=new Attribute(Type.VEC2,"growth_offset");
    private static final VertexArray.Layout QUAD=new VertexArray.Layout(
        new VertexArray.Layout.Input(Homo3D.vertex,new VectorFormat(3,NumberFormat.FLOAT32),0,0,28),
        new VertexArray.Layout.Input(Tex2D.texc,new VectorFormat(2,NumberFormat.FLOAT32),0,12,28),
        new VertexArray.Layout.Input(OFFSET,new VectorFormat(2,NumberFormat.FLOAT32),0,20,28));
    /** View-space distance (map units, a tile is 11) the label is moved toward the camera before the depth test. */
    static final float CLEARANCE=20;
    private static final Uniform PROJECTION=new Uniform(Type.MAT4,"growth_projection",Homo3D::prjxf,Homo3D.prj);
    private static final State.StandAlone BILLBOARD=new State.StandAlone(State.Slot.Type.DRAW) {
        private final ShaderMacro shader=prog -> {
            Homo3D.get(prog);
            prog.vctx.posv.mod(in -> add(in,vec4(mul(div(mul(OFFSET.ref(),l(2.0)),FrameConfig.u_screensize.ref()),pick(in,"w")),l(0.0),l(0.0))),100);
            /* Moving the point toward the viewer by CLEARANCE changes clip z by the projection's z column times it. */
            prog.vctx.posv.mod(in -> add(in,vec4(l(0.0),l(0.0),pick(mul(PROJECTION.ref(),vec4(l(0.0),l(0.0),l(CLEARANCE),l(0.0))),"z"),l(0.0))),110);
        };
        public ShaderMacro shader(){return shader;}
    };
    private static final Map<TexI,Map<Float,SharedModel>> models=new IdentityHashMap<>();
    private static final class SharedModel {
        final Model model;int users;
        SharedModel(Model model){this.model=model;}
    }
    private final List<RenderTree.Slot> slots=new ArrayList<>();
    private TexI image;private Model model;private SharedModel shared;private float height=1;
    private static synchronized SharedModel acquire(TexI image,float height){
        Map<Float,SharedModel> heights=models.computeIfAbsent(image,key->new HashMap<>());
        SharedModel shared=heights.get(height);
        if(shared==null){
            Coord size=image.sz(),texture=image.st().data.tex.sz();
            float x0=-size.x*.5f,x1=x0+size.x,y0=12,y1=y0+size.y;
            float tw=size.x/(float)texture.x,th=size.y/(float)texture.y;
            float[] data={0,0,height,0,th,x0,y0, 0,0,height,tw,th,x1,y0, 0,0,height,0,0,x0,y1,
                0,0,height,tw,th,x1,y0, 0,0,height,tw,0,x1,y1, 0,0,height,0,0,x0,y1};
            Model model=new Model(Model.Mode.TRIANGLES,new VertexArray(QUAD,new VertexArray.Buffer(data.length*4,DataBuffer.Usage.STATIC,DataBuffer.Filler.of(data))),null);
            heights.put(height,shared=new SharedModel(model));
        }
        shared.users++;return shared;
    }
    private void release(){
        synchronized(DepthLabel.class){
            if(shared!=null&&--shared.users==0){
                Map<Float,SharedModel> heights=models.get(image);heights.remove(height);
                if(heights.isEmpty())models.remove(image);
                shared.model.dispose();
            }
            shared=null;model=null;
        }
    }
    private Pipe.Op state() {
        if(image==null)return Pipe.Op.compose(Rendered.last,pipe -> pipe.put(Clickable.slot,null));
        return Pipe.Op.compose(Rendered.last,new States.Facecull(States.Facecull.Mode.NONE),
            pipe -> pipe.put(Clickable.slot,null),BILLBOARD,States.maskdepth,
            new States.Depthtest(States.Depthtest.Test.LE),image==null?null:image.st());
    }
    public synchronized void added(RenderTree.Slot slot){slot.ostate(state());slots.add(slot);}
    public synchronized void removed(RenderTree.Slot slot){slots.remove(slot);}
    public void set(TexI next) {set(next,1);}
    public synchronized void set(TexI next,float anchorHeight) {
        if(image==next && height==anchorHeight)return;
        release();
        height=anchorHeight;image=next;
        if(image!=null){shared=acquire(image,height);model=shared.model;}
        for(RenderTree.Slot slot:new ArrayList<>(slots)){slot.ostate(state());slot.update();}
    }
    public synchronized void draw(Pipe context,Render out){if(model!=null)out.draw(context,model);}
    public synchronized void dispose(){release();image=null;}
}
