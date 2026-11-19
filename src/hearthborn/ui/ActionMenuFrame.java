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

package hearthborn.ui;

import haven.*;
import haven.render.*;
import java.awt.Color;

/**
 * Straight frame and a local scale for the existing native action grid.
 *
 * @author Jagy2014
 */
public final class ActionMenuFrame extends Widget {
    private static final Color DARK_BACKGROUND=new Color(0,0,0,255);
    /** A translucent warm-black backing; cells add only fine lines, not another fill. */
    public static void background(GOut g,Coord size){
        g.chcolor(UiTheme.light()?UiTheme.BACKGROUND:DARK_BACKGROUND);g.frect(Coord.z,size);g.chcolor();
    }
    public static void slot(GOut g,Coord at,Coord size){
        ToolbarFrame.slot(g,at,size);
    }
    public static final double SCALE=1.5;
    /** The classic 4×4 stays this size, with no hover grow. */
    public static final double GRID=1.0;
    /** The grid's magnification now: its base size times the panel scale. */
    public static double grid(){return GRID*HudInterfaceScale.current();}
    private final Coord nativeSize;
    private final Coord extensions;
    private double shown;
    private Integer normalPanelZ;
    private static volatile Boolean menuOnLeft;
    public static boolean onLeft(){if(menuOnLeft==null)menuOnLeft=Utils.getprefb("hearthborn/ui/action-menu-left",false);return menuOnLeft;}
    public static void onLeft(boolean value){menuOnLeft=value;Utils.setprefb("hearthborn/ui/action-menu-left",value);}
    public static Coord inset(){return UI.scale(6,6);}
    public static Coord scaledSize(Coord nativeSize){return size(nativeSize,SCALE);}
    public static Coord size(Coord nativeSize,double factor){
        return Coord.of((int)Math.ceil(nativeSize.x*factor),(int)Math.ceil(nativeSize.y*factor));
    }
    public static Coord pad(double factor){
        Coord full=inset();
        return Coord.of((int)Math.round(full.x*factor/SCALE),(int)Math.round(full.y*factor/SCALE));
    }
    public static Coord frameSize(Coord nativeSize){return frameSize(nativeSize,SCALE);}
    public static Coord frameSize(Coord nativeSize,double factor){return size(nativeSize,factor).add(pad(factor).mul(2));}
    /** Fixed corner size of the classic grid. */
    public static Coord footprint(Coord nativeSize){return frameSize(nativeSize,grid()).add(SystemCross.edgeInset());}
    public Coord footprint(){return footprint(nativeSize);}
    public double factor(){return shown;}
    public static Coord logical(Coord point){return logical(point,SCALE);}
    public static Coord logical(Coord point,double factor){return Coord.of((int)Math.floor(point.x/factor),(int)Math.floor(point.y/factor));}
    public static Coord physical(Coord point){return physical(point,SCALE);}
    public static Coord physical(Coord point,double factor){return Coord.of((int)Math.ceil(point.x*factor),(int)Math.ceil(point.y*factor));}
    public static GOut graphics(GOut original,Coord nativeSize){return graphics(original,nativeSize,SCALE);}
    public static GOut graphics(GOut original,Coord nativeSize,double factor){
        Pipe pipe=original.basicstate();Ortho2D ortho=(Ortho2D)pipe.get(States.vxf);
        pipe.prep(new Ortho2D((float)((ortho.l-original.tx.x)/factor),(float)((ortho.u-original.tx.y)/factor),
            (float)((ortho.r-original.tx.x)/factor),(float)((ortho.b-original.tx.y)/factor)));
        return new GOut(original.out,pipe,nativeSize);
    }
    public ActionMenuFrame(Coord nativeSize){this(nativeSize,Coord.z);}
    public ActionMenuFrame(Coord nativeSize,Coord extensions){
        super(Coord.z);
        this.nativeSize=nativeSize;
        this.extensions=extensions;
        shown=grid();
        resize(frameSize(nativeSize,shown).add(extensions));
    }
    public void fit(){
        MenuGrid menu=parent==null?null:parent.getchild(MenuGrid.class);
        Coord content=menu==null?nativeSize:menu.contentSize();
        Coord edge=SystemCross.edgeInset();
        shown=grid();
        boolean expanded=menu!=null&&(menu.gridSize().x>4||menu.gridSize().y>4);
        if(expanded && parent.parent!=null){
            Coord available=parent.parent.sz.sub(UI.scale(12,12)).sub(edge).max(Coord.of(1,1));
            Coord padded=content.add(inset().mul(2));
            shown=Math.min(grid(),Math.max(.25,Math.min(available.x/(double)padded.x,available.y/(double)padded.y)));
        }
        edge=SystemCross.edgeInset(shown);
        Coord grid=size(content,shown),margin=pad(shown);
        Coord body=SystemCross.gridEdges()?grid.max(SystemCross.edgeMinimum()):grid;
        resize(body.add(margin.mul(2)).add(extensions).add(edge));
        if(parent==null)return;
        if(expanded){
            if(normalPanelZ==null)normalPanelZ=parent.z;
            // Above windows and hover contents (90), below the carried item (100).
            parent.z(Math.max(normalPanelZ,95));
        }else if(normalPanelZ!=null){
            parent.z(normalPanelZ);normalPanelZ=null;
        }
        if(menu!=null){menu.resize(grid);menu.move(Coord.of(SystemCross.gridEdges()&&!onLeft()?sz.x-margin.x-grid.x:margin.x,sz.y-margin.y-grid.y));}
        if(parent instanceof GameUI.Hidepanel&&parent.parent!=null)((GameUI.Hidepanel)parent).move();
        if(SystemCross.gridEdges()&&parent.parent instanceof GameUI){
            SystemCross cross=parent.parent.getchild(SystemCross.class);if(cross!=null)cross.attachEdges(this);
        }
    }
    /** Where the unified moulding lies when the system buttons sit on its top and side edges. */
    Area edgeFrame(){
        Coord margin=pad(factor()),edge=SystemCross.edgeInset(factor());
        IBox.Images box=(IBox.Images)Window.wbox;
        Coord thick=Coord.of(box.bl.sz().x,box.bt.sz().y);
        int rail=margin.x+edge.x,top=(margin.y+edge.y)/2-thick.y/2;
        int left=edge.x==0?0:onLeft()?0:rail/2-thick.x/2,right=edge.x==0?sz.x:onLeft()?sz.x-rail/2+thick.x/2:sz.x;
        return Area.corn(Coord.of(Math.max(0,left),Math.max(0,top)),Coord.of(Math.min(sz.x,right),sz.y));
    }
    public void draw(GOut g){
        if(SystemCross.gridEdges()){
            // One frame around the grid; the medallions are centred on its top and side lines.
            Area box=edgeFrame();
            g.chcolor(UiTheme.light()?UiTheme.BACKGROUND:SystemButtonSkin.DISK);g.frect(box.ul,box.sz());g.chcolor();
            ToolbarFrame.frame(g,box.ul,box.sz());
            return;
        }
        background(g,sz);
    }
}
