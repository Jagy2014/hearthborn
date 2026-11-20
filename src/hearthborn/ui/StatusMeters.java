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
import hearthborn.L10n;
import java.awt.Color;
import javax.imageio.ImageIO;

/**
 * Extra meters share the portrait grid with all server-supplied meters, including mounts.
 *
 * @author Jagy2014
 */
public final class StatusMeters extends Widget {
    private final GameUI gui;
    private final Meter[] extra;
    private DrinkSupply drinks=new DrinkSupply();
    private double elapsed=1;
    private Speedget speed;
    private Boolean mirrored;
    // Kept between ticks: the lists are filled on the UI thread only, so a tick does not allocate them again.
    private final java.util.List<IMeter> bars=new java.util.ArrayList<>(), mounts=new java.util.ArrayList<>();
    private final java.util.List<Coord> extraAt=new java.util.ArrayList<>();
    public static String hungerValue(double value){return String.format(java.util.Locale.ROOT,"%.1f\u2030",value*1000);}
    private static final java.util.concurrent.ConcurrentHashMap<String,Boolean> shown=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean enabled(String name) {Boolean value=shown.get(name);if(value==null)shown.put(name,value=Utils.getprefb("hearthborn/meters/"+name,true));return value;}
    public static void enabled(String name,boolean on) {shown.put(name,on);Utils.setprefb("hearthborn/meters/"+name,on);}
    static boolean combatVisible(Widget widget){return !(widget instanceof Meter)||!((Meter)widget).kind.equals("fep");}
    public StatusMeters(GameUI gui) {
        this.gui=gui;extra=new Meter[]{add(new Meter("fep")),add(new Meter("hunger")),add(new Meter("drinks"))};
    }
    public void applyScale(){
        for(Meter meter:extra)meter.resize(StatusScale.size(IMeter.fsz));
        tick(0);
    }
    private BAttrWnd attributes() {return gui.chrwdg==null ? null : gui.chrwdg.battr;}
    public void tick(double dt) {
        super.tick(dt);
        if((elapsed+=dt)>=.5) {elapsed=0;if(enabled("drinks"))drinks=DrinkSupply.collect(gui);}
        hearthborn.world.StructureWear.watch(gui);
        if(gui.portrait==null)return;
        boolean right=PortraitSide.onRight();
        if(mirrored==null||mirrored!=right){mirrored=right;if(gui.buffs instanceof Bufflist)((Bufflist)gui.buffs).relayout();}
        placePortrait();
        int count=0;
        bars.clear();
        mounts.clear();
        for(Widget child=parent.child;child!=null;child=child.next)
            if(child instanceof IMeter) {
                if(isMountMeter((IMeter)child))mounts.add((IMeter)child);
                else {child.move(origin(count++));bars.add((IMeter)child);}
            }
        Coord rowStart=origin(count-count%3);
        int offset=count;
        extraAt.clear();
        for(Meter meter:extra) {
            boolean visible=enabled(meter.kind) && (!gui.compactUI()||!meter.kind.equals("fep")) && (meter.kind.equals("drinks") || attributes()!=null);
            meter.show(visible);
            if(visible)extraAt.add(origin(offset++));
            else extraAt.add(null);
        }
        int anchorX=rowStart.x;
        for(Coord at:extraAt)if(at!=null)anchorX=Math.min(anchorX,at.x);
        move(Coord.of(anchorX,rowStart.y));
        for(int i=0;i<extra.length;i++)if(extraAt.get(i)!=null)extra[i].move(extraAt.get(i).sub(c));
        Coord meterSize=StatusScale.size(IMeter.fsz);
        int bottom=gui.portrait.c.y+((offset+2)/3)*(meterSize.y+UI.scale(2))+UI.scale(3);
        if(speed==null || speed.parent==null) speed=findSpeed(gui);
        int rowTop=bottom;
        int column=origin(0).x;
        int rowHeight=0;
        int step=meterSize.x+UI.scale(5);
        // The speed selector sits right under the portrait, on a black plate. The buffs take its old place beside the portrait.
        int speedX=Integer.MAX_VALUE;
        HudPortrait frame=gui.portrait instanceof HudPortrait?(HudPortrait)gui.portrait:null;
        if(speed!=null) {
            BoatSpeed.apply(speed);
            if(speed.visible) {
                if(frame!=null) {
                    // The selector is the bottom of the portrait: the frame grows to hold it and closes below it.
                    speed.fit(frame.reserveStrip(true));
                    Coord at=frame.c.add(frame.stripOffset());
                    speedX=at.x;
                    speed.move(parent.parentpos(gui).add(at).sub(speed.parent.parentpos(gui)));
                    if(speed.parent.lchild!=speed)speed.raise();
                } else {
                    int x=right?gui.portrait.c.x+gui.portrait.sz.x-speed.sz.x:gui.portrait.c.x;
                    speedX=x;
                    int y=gui.portrait.c.y+gui.portrait.sz.y+UI.scale(5);
                    speed.move(parent.parentpos(gui).add(x,y).sub(speed.parent.parentpos(gui)));
                }
            } else if(frame!=null) frame.reserveStrip(false);
        }
        boolean speedShown=speed!=null&&speed.visible;
        for(int i=0;i<mounts.size();i++){
            int x=right?column-i*step:column+i*step;
            mounts.get(i).move(Coord.of(x,rowTop));
        }
        if(!mounts.isEmpty())rowHeight=meterSize.y;
        bottom=rowTop+rowHeight+UI.scale(5);
        // Extra meters are already part of the grid above, so they stay above the buffs, which start below the grid and any mount meters.
        if(right)gui.buffs.move(Coord.of(column+meterSize.x-gui.buffs.sz.x,bottom));
        else gui.buffs.move(Coord.of(column,bottom));
        int minX=gui.portrait.c.x;
        minX=Math.min(minX,gui.buffs.c.x);
        minX=Math.min(minX,c.x);
        for(IMeter meter:bars)minX=Math.min(minX,meter.c.x);
        for(IMeter meter:mounts)minX=Math.min(minX,meter.c.x);
        if(speedShown)minX=Math.min(minX,speedX);
        int dx=Math.max(0,UI.scale(10)-minX);
        if(dx>0){
            gui.portrait.move(gui.portrait.c.add(dx,0));
            for(IMeter meter:bars)meter.move(meter.c.add(dx,0));
            for(IMeter meter:mounts)meter.move(meter.c.add(dx,0));
            move(c.add(dx,0));
            gui.buffs.move(gui.buffs.c.add(dx,0));
            if(speedShown)speed.move(speed.c.add(dx,0));
        }
        pack();
        // The horse meters and buffs are siblings moved after their parent's initial pack.
        parent.pack();
        gui.anchorHudSides();
    }
    /** Portrait on the left, or on the right with three meter columns reserved beside it. */
    private void placePortrait(){
        int margin=UI.scale(10);
        if(!PortraitSide.onRight()){gui.portrait.move(Coord.of(margin,margin));return;}
        Coord meter=StatusScale.size(IMeter.fsz);
        int step=meter.x+UI.scale(5);
        int block=meter.x+2*step;
        gui.portrait.move(Coord.of(margin+block+UI.scale(10),margin));
    }
    public static boolean isMountMeter(IMeter meter) {
        try {
            String name=meter.bg.get().name;
            return name.equals("gfx/hud/meter/h\u00e4st") || name.equals("gfx/hud/meter/mount") ||
                name.equals("gfx/hud/meter/boat");
        } catch(Loading loading) {return false;}
    }
    public static boolean isBoatMeter(IMeter meter) {
        try {return meter.bg.get().name.equals("gfx/hud/meter/boat");}
        catch(Loading loading) {return false;}
    }
    private static Speedget findSpeed(Widget widget) {
        for(Widget child=widget.child;child!=null;child=child.next) {
            if(child instanceof Speedget)return (Speedget)child;
            Speedget found=findSpeed(child);if(found!=null)return found;
        }
        return null;
    }
    private Coord origin(int index) {
        Coord meterSize=StatusScale.size(IMeter.fsz);
        int step=meterSize.x+UI.scale(5);
        int col=index%3,row=index/3;
        int y=gui.portrait.c.y+row*(meterSize.y+UI.scale(2));
        if(!PortraitSide.onRight())return Coord.of(gui.portrait.c.x+gui.portrait.sz.x+UI.scale(10)+col*step,y);
        return Coord.of(gui.portrait.c.x-UI.scale(10)-meterSize.x-col*step,y);
    }
    private final class Meter extends Widget {
        final String kind;
        final Tex frame;
        final MeterReadout readout=new MeterReadout();
        Tex tip;
        String lastTip;
        Meter(String kind) {
            super(StatusScale.size(IMeter.fsz));this.kind=kind;
            frame=MeterArtwork.frame(kind);
        }
        public void draw(GOut g) {
            g=StatusScale.graphics(g,IMeter.fsz);
            BAttrWnd attr=attributes();Coord off=IMeter.off,size=IMeter.msz;
            g.chcolor(30,35,25,255);g.frect(off,size);g.chcolor();
            String value="";
            if(kind.equals("fep")&&attr!=null) {
                double sum=0;
                for(BAttrWnd.FoodMeter.El el:attr.feps.els) {
                    double next=sum+el.a;
                    try {g.chcolor(el.ev().col);int left=attr.feps.cap<=0 ? 0 :(int)(sum/attr.feps.cap*size.x);
                        int right=attr.feps.cap<=0 ? 0 :(int)(next/attr.feps.cap*size.x);
                        g.frect(off.add(Math.min(left,size.x),0),Coord.of(Math.max(0,Math.min(right,size.x)-Math.min(left,size.x)),size.y));}
                    catch(Loading ignored){}sum=next;
                }
                value=Utils.odformat2(sum,1)+"/"+Utils.odformat2(attr.feps.cap,1);
            } else if(kind.equals("hunger")&&attr!=null) {
                g.chcolor(attr.glut.bg);g.frect(off,size);g.chcolor(attr.glut.fg);
                g.frect(off,Coord.of((int)(size.x*(attr.glut.glut-Math.floor(attr.glut.glut))),size.y));
                value=hungerValue(attr.glut.glut);
            } else if(kind.equals("drinks")) {
                if(drinks.capacity>0) {
                    g.chcolor(147,110,68,255);g.frect(off,Coord.of((int)(size.x*Math.min(1,(drinks.water+drinks.tea)/drinks.capacity)),size.y));
                    g.chcolor(82,116,188,255);g.frect(off,Coord.of((int)(size.x*Math.min(1,drinks.water/drinks.capacity)),size.y));
                }
                value=String.format(java.util.Locale.ROOT,"%.1f/%.1f",drinks.water+drinks.tea,drinks.capacity)+(drinks.loading ? "*" : "");
            }
            g.chcolor();g.image(frame,Coord.z);
            readout.drawLabel(g,value);
        }
        public Object tooltip(Coord c,Widget prev) {
            BAttrWnd attr=attributes();
            if(kind.equals("fep"))return attr==null ? null : attr.feps.tooltip(StatusScale.logical(c),prev);
            if(kind.equals("hunger"))return attr==null ? null : attr.glut.tooltip(StatusScale.logical(c),prev);
            String text=L10n.format("Water: %.2f l\nTea: %.2f l\nCapacity: %.2f l",drinks.water,drinks.tea,drinks.capacity);
            if(drinks.loading)text+="\n"+L10n.tr("Some supplies are still loading.");
            text+="\n"+AutoDrink.status();
            if(!text.equals(lastTip)){if(tip!=null)tip.dispose();lastTip=text;tip=Text.std.renderwrap(text,UI.scale(230)).tex();}
            return tip;
        }
        public boolean mousedown(MouseDownEvent event){
            if(kind.equals("drinks")&&event.b==3){AutoDrink.menu(gui,ui.mc);return true;}
            return false;
        }
        public void dispose(){frame.dispose();readout.dispose();if(tip!=null)tip.dispose();super.dispose();}
    }
}
