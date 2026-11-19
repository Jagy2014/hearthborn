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

import haven.*;
import haven.render.*;
import haven.render.sl.*;
import static haven.render.sl.Cons.*;

/**
 * Replace only the stone mesh's texture sample, retaining its UVs, shading and lighting.
 *
 * @author Jagy2014
 */
final class StoneSurface extends State {
    static final Slot<StoneSurface> slot=new Slot<>(Slot.Type.DRAW,StoneSurface.class);
    final Texture2D.Sampler2D texture;
    StoneSurface(Texture2D.Sampler2D texture){this.texture=texture;}
    static final Uniform image=new Uniform(Type.SAMPLER2D,"pile_stone_surface",p->p.get(slot).texture,slot);
    private static final ShaderMacro shader=program->{
        Tex2D texture=Tex2D.get(program);
        // Some pile passes have UVs but no ColorTex state. Value.root() is
        // evaluated before modifiers, so it still needs a valid sampler.
        if(texture.tex2d==null)texture.tex2d=image;
        // Keep the native alpha mask: stockpile meshes can contain hidden pieces.
        // Replacing alpha with an opaque material makes those pieces visible.
        texture.color().mod(original->vec4(pick(texture2D(image.ref(),texture.texcoord().ref()),"rgb"),pick(original,"a")),1000);
    };
    public ShaderMacro shader(){return shader;}
    public void apply(Pipe pipe){pipe.put(slot,this);}
}
