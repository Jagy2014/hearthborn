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

/**
 * Keep the avatar a real enlarged 3D viewport; only the gold moulding uses a 2D projection.
 *
 * @author Jagy2014
 */
public final class HudPortrait extends Frame {
    private final Runnable equipment,characterSheet;
    /** Height in pixels of the movement strip inside the frame under the avatar, with the divider bar above it; 0 when there is none. The frame is as thick as the map's, whatever the portrait scale. */
    private int strip;
    public HudPortrait(long player,Runnable equipment,Runnable characterSheet){
        super(StatusScale.size(Avaview.dasz).add(Window.wbox.bisz()),false);
        this.equipment=equipment;this.characterSheet=characterSheet;
        add(new Avaview(inner(),player,"avacam"),Coord.z);
    }
    private Coord avatar(){return StatusScale.size(Avaview.dasz);}
    private Coord outer(){return avatar().add(box.bisz()).add(0,strip);}
    /** Pixels the movement strip takes below the avatar. */
    public int stripHeight(){return strip;}
    /** Makes room for the movement selector inside the frame and returns the size it should take, or null when there is none. */
    public Coord reserveStrip(boolean wanted){
        int want=0;
        if(wanted){
            int width=avatar().x;
            int height=Math.max(1,Math.round(width*(float)Speedget.tsz.y/Speedget.tsz.x));
            want=height+bar();
        }
        if(want!=strip){strip=want;applyScale();}
        return strip==0?null:Coord.of(avatar().x,stripHeight()-barHeight());
    }
    /** Where the selector goes, in the same coordinates as {@link #c}. */
    public Coord stripOffset(){
        Coord margin=box.btloff();
        return Coord.of(margin.x,margin.y+inner().y+barHeight());
    }
    private Widget corner,ornament;
    /** The gilded scroll frame around the heart: its picture, and where the round hole sits in it. */
    private static final String ORNAMENT="icons/common/portrait-heart-frame.png";
    private static final Coord ORNAMENT_HOLE_CENTER=Coord.of(149,233);
    private static final int ORNAMENT_HOLE=229;
    private static java.awt.image.BufferedImage ornamentArt;
    private static boolean ornamentMissing;
    private static synchronized java.awt.image.BufferedImage ornamentArt(){
        if(ornamentArt==null&&!ornamentMissing)try{ornamentArt=hearthborn.assets.Assets.image(ORNAMENT);}
        catch(java.io.IOException failure){ornamentMissing=true;System.err.println("Hearthborn: missing portrait frame: "+failure.getMessage());}
        return ornamentArt;
    }
    /** The frame is only a picture behind the button: it never takes a click. */
    private static final class Ornament extends Widget {
        private final java.awt.image.BufferedImage art;
        private Tex tex;
        Ornament(java.awt.image.BufferedImage art){super(Coord.z);this.art=art;}
        void fit(Coord size){if(!size.equals(sz)){resize(size);tex=null;}}
        public boolean checkhit(Coord c){return false;}
        public void draw(GOut g){
            if(tex==null&&sz.x>0&&sz.y>0)tex=new TexI(PUtils.uiscale(art,sz));
            if(tex!=null)g.image(tex,Coord.z);
        }
    }
    /** A small round button in a gilded frame, over the avatar in its bottom-left corner and above the movement strip. */
    public void corner(Widget button){
        java.awt.image.BufferedImage art=ornamentArt();
        if(art!=null)ornament=add(new Ornament(art),Coord.z);
        corner=add(button,Coord.z);
        placeCorner();
    }
    private void placeCorner(){
        if(corner==null)return;
        int side=Math.max(1,StatusScale.size(Coord.of(UI.scale(ornamentArt()==null?26:19),0)).x);
        if(corner instanceof ICheckBox)((ICheckBox)corner).fit(side);
        if(ornament==null){corner.move(Coord.of(0,Math.max(0,inner().y-corner.sz.y)));return;}
        // The frame's hole is as wide as the button; its lower left corner is the corner of the avatar.
        java.awt.image.BufferedImage art=ornamentArt();
        double factor=Math.max(corner.sz.x,corner.sz.y)/(double)ORNAMENT_HOLE;
        Coord size=Coord.of(Math.max(1,(int)Math.round(art.getWidth()*factor)),Math.max(1,(int)Math.round(art.getHeight()*factor)));
        ((Ornament)ornament).fit(size);
        Coord at=Coord.of(0,Math.max(0,inner().y-size.y));
        ornament.move(at);
        Coord hole=Coord.of((int)Math.round(ORNAMENT_HOLE_CENTER.x*factor),(int)Math.round(ORNAMENT_HOLE_CENTER.y*factor));
        corner.move(at.add(hole).sub(corner.sz.div(2)));
    }
    public void applyScale(){
        resize(outer());
        Avaview view=getchild(Avaview.class);
        if(view!=null)view.resize(inner());
        placeCorner();
    }
    /** The heart lies over the portrait, so a click on it must reach the button instead of opening equipment. */
    private boolean overCorner(Coord point){
        if(corner==null||!corner.visible())return false;
        Coord at=xlate(point,false).sub(corner.c);
        return at.x>=0&&at.y>=0&&at.x<corner.sz.x&&at.y<corner.sz.y&&corner.checkhit(at);
    }
    public boolean mousedown(MouseDownEvent event){
        if(overCorner(event.c)){event.propagate(this);return true;}
        if(event.b==1){equipment.run();return true;}
        if(event.b==3){characterSheet.run();return true;}
        // Retain native avatar actions, but never send an unhandled portrait click to the world.
        event.propagate(this);
        return true;
    }
    /** The two clicks are the way to equipment and the character sheet, so the portrait says so. The heart has its own tip. */
    public Object tooltip(Coord at,Widget previous){
        return hearthborn.L10n.tr("Left click: equipment\nRight click: character sheet");
    }
    /** The frame lets its inside through to the world, so the tip is given here: over the avatar, after the heart and the strip had their say. */
    public boolean tooltip(TooltipQuery ev){
        if(ev.propagate(this))return true;
        if(xlate(ev.c,false).y>=inner().y)return false;
        ev.set(tooltip(ev.c,ev.last),this);
        return true;
    }
    public Coord inner(){return sz.sub(box.bisz()).sub(0,stripHeight());}
    public Coord xlate(Coord point,boolean in){return in?point.add(box.btloff()):point.sub(box.btloff());}
    /** Behind the movement strip, so no sliver of the world shows between the divider bar and the selector's own plate. */
    public void draw(GOut g){
        if(strip>0){
            // From the top of the divider bar, so no sliver of the world shows between the bar and the strip whatever the rounding.
            Coord from=xlate(Coord.of(0,inner().y),true),to=sz.sub(box.bbroff());
            if(to.y>from.y){g.chcolor(0,0,0,235);g.frect(Coord.of(from.x,from.y),Coord.of(Math.max(0,to.x-from.x),to.y-from.y));g.chcolor();}
        }
        super.draw(g);
    }
    public void drawframe(GOut g){
        if(FrameSkin.flat()){flatFrame(g,sz);return;}
        box.draw(g,Coord.z,sz);
        if(strip>0&&box instanceof IBox.Images)divider(g,(IBox.Images)box,sz);
    }
    /**
     * The flat look has no moulding: one thin line hugs the avatar, and a second one marks the movement strip. The band the
     * sprites would take stays empty, so the world shows through it and the frame is not wider than a line.
     */
    private void flatFrame(GOut g,Coord whole){
        Coord edge=box.btloff(),room=box.bisz();
        if(UiTheme.light()){lightFrame(g,whole,edge,room);return;}
        int unit=Math.max(1,UI.scale(1));
        Coord from=edge.sub(unit,unit),size=whole.sub(room).sub(0,strip).add(unit*2,unit*2);
        g.chcolor(AccentTheme.current().frame());
        g.frect(from,Coord.of(size.x,unit));
        g.frect(from.add(0,size.y-unit),Coord.of(size.x,unit));
        g.frect(from,Coord.of(unit,size.y));
        g.frect(from.add(size.x-unit,0),Coord.of(unit,size.y));
        if(strip>0){
            int bottom=edge.y+whole.y-room.y;
            g.frect(Coord.of(from.x,bottom-strip-unit),Coord.of(size.x,unit));
            g.frect(Coord.of(from.x,bottom),Coord.of(size.x,unit));
            g.frect(Coord.of(from.x,bottom-strip-unit),Coord.of(unit,strip+unit*2));
            g.frect(Coord.of(from.x+size.x-unit,bottom-strip-unit),Coord.of(unit,strip+unit*2));
        }
        g.chcolor();
    }
    /** On the light sheet the portrait has the same filled band and line as the windows, not a bare hairline. */
    private void lightFrame(GOut g,Coord whole,Coord edge,Coord room){
        Coord end=room.sub(edge);
        FrameSkin.flat(g,Coord.z,whole,edge.x,edge.y,end.x,end.y,true,true);
        if(strip>0){
            int top=whole.y-strip-end.y,thick=Math.min(Math.max(1,bar()),strip),unit=Math.max(1,UI.scale(1));
            g.chcolor(UiTheme.BACKGROUND);g.frect(Coord.of(edge.x,top),Coord.of(Math.max(0,whole.x-room.x),thick));
            g.chcolor(AccentTheme.current().frame());
            g.frect(Coord.of(edge.x,top),Coord.of(Math.max(0,whole.x-room.x),unit));
            g.frect(Coord.of(edge.x,top+thick-unit),Coord.of(Math.max(0,whole.x-room.x),unit));
            g.chcolor();
        }
    }
    /** Height of the moulding bar between the avatar and the movement strip, in pixels. */
    private int bar(){return box instanceof IBox.Images?((IBox.Images)box).bt.sz().y:0;}
    private int barHeight(){return strip==0?0:bar();}
    /** A bar of the same moulding under the avatar, in space of its own, so the movement strip sits in its own compartment. */
    private void divider(GOut g,IBox.Images moulding,Coord whole){
        Coord edge=box.btloff(),room=box.bisz();
        g.image(moulding.bt,Coord.of(edge.x,whole.y-strip-room.y+edge.y),Coord.of(Math.max(0,whole.x-room.x),moulding.bt.sz().y));
    }
}
