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

import haven.Utils;
import haven.render.*;
import haven.render.sl.*;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Hiding visuals preserves flavor audio nodes; hiding everything detaches the whole cut.
 *
 * @author Jagy2014
 */
public final class FlavorObjects {
    private static volatile int mode=Utils.clip(Utils.getprefi("hearthborn/world/flavor-mode",0),0,2);
    private static final RenderTree.Node EMPTY=new RenderTree.Node(){};
    private static final Map<RenderTree.Node,WeakReference<RenderTree.Node>> visual=new WeakHashMap<>();
    private static final ShaderMacro DISCARD=program->program.fctx.mainmod(block->block.add(new Discard()),-10000);
    private static final State.StandAlone INVISIBLE=new State.StandAlone(State.Slot.Type.DRAW){
        public ShaderMacro shader(){return DISCARD;}
    };
    public static int mode(){return mode;}
    public static void mode(int value){mode=Utils.clip(value,0,2);Utils.setprefi("hearthborn/world/flavor-mode",mode);}
    public static boolean silent(){return mode==1;}
    public static RenderTree.Node empty(){return EMPTY;}
    public static synchronized RenderTree.Node node(RenderTree.Node source){
        if(mode==1)return EMPTY;
        if(mode==0)return source;
        WeakReference<RenderTree.Node> reference=visual.get(source);
        RenderTree.Node wrapper=reference==null?null:reference.get();
        if(wrapper==null){
            wrapper=new RenderTree.Node(){public void added(RenderTree.Slot slot){slot.add(source,INVISIBLE);}};
            visual.put(source,new WeakReference<>(wrapper));
        }
        return wrapper;
    }
    private FlavorObjects(){}
}
