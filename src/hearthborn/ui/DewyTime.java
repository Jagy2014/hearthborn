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
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A dawn status icon; astronomy remains authoritative even with forced daytime lighting.
 *
 * @author Jagy2014
 */
public final class DewyTime extends Widget {
    public static final int START = 4 * 60 + 45, END = 7 * 60 + 15;
    public static boolean enabled() {return Utils.getprefb("hearthborn/dewy-notification",true);}
    public static void enabled(boolean enabled) {Utils.setprefb("hearthborn/dewy-notification",enabled);}
    public static boolean active(double dayFraction) {
        double minutes=dayFraction*24*60;
        return minutes>=START && minutes<END;
    }
    public static double remaining(double dayFraction) {
        return Math.max(0,(END-dayFraction*24*60)*60/StudyProgress.GAME_SECONDS_PER_REAL_SECOND);
    }
    public enum Notice { NONE, START }
    public static final class Transition {
        private String startedDay;
        public Notice update(boolean active,String day,boolean enabled) {
            if(active && enabled && !day.equals(startedDay)) {
                startedDay=day;
                return Notice.START;
            }
            return Notice.NONE;
        }
    }
    static final String ICON="gfx/invobjs/herbs/ladysmantledew";
    private final GameUI gui;
    private final Transition transition=new Transition();
    private Indicator indicator;
    private double left;
    public DewyTime(GameUI gui) {this.gui=gui;hide();}
    public void tick(double dt) {
        if(ui==null || ui.sess==null || ui.sess.glob.ast==null) {clear();return;}
        Astronomy astronomy=ui.sess.glob.ast;
        boolean active=active(astronomy.dt);
        String day=(long)Math.floor(astronomy.years)+":"+(long)Math.floor(astronomy.ym)+":"+(long)Math.floor(astronomy.md);
        Notice notice=transition.update(active,day,enabled());
        if(notice==Notice.START) {
            ui.sfx(Resource.local().loadwait("sfx/hud/mmap/bell1"));
            ui.msg(L10n.tr("Dewy Lady's Mantle time has begun."),new Color(100,230,255),null);
        }
        if(!enabled() || !active || gui.buffs==null) {clear();return;}
        left=remaining(astronomy.dt);
        if(indicator==null || indicator.parent!=gui.buffs) {
            clear();indicator=new Indicator(this);gui.buffs.addchild(indicator);
        }
        indicator.update();
    }
    /** Keep the original game artwork and compose the existing blue HUD style behind it. */
    static BufferedImage icon(BufferedImage plant){
        BufferedImage image=TexI.mkbuf(Coord.of(128,128));
        Graphics2D g=image.createGraphics();
        g.setPaint(new RadialGradientPaint(64,64,90,new float[]{0,1},new Color[]{new Color(51,139,193),new Color(12,44,79)}));
        g.fillRect(0,0,128,128);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double scale=116.0/Math.max(plant.getWidth(),plant.getHeight());
        int width=(int)Math.round(plant.getWidth()*scale),height=(int)Math.round(plant.getHeight()*scale);
        g.drawImage(plant,(128-width)/2,(128-height)/2,width,height,null);g.dispose();
        return image;
    }
    static final class Indicator extends Buff {
        private final DewyTime owner;
        private Tex icon,timer;
        private String lastTimer;
        Indicator(DewyTime owner){super(Resource.remote().load(ICON));this.owner=owner;}
        public int layoutOrder(){return -2;}
        void update(){
            String text=StudyProgress.shortTime(owner.left);
            if(text.equals(lastTimer))return;
            lastTimer=text;if(timer!=null)timer.dispose();
            timer=new TexI(Utils.outline2(Buff.nfnd.render(text,Color.WHITE).img,Color.BLACK));
        }
        public void draw(GOut g){
            g.chcolor(255,255,255,a);g.image(Buff.frame,Coord.z);
            if(icon==null)try{
                icon=new TexI(PUtils.uiscale(DewyTime.icon(res.get().flayer(Resource.imgc).img),UI.scale(32,32)));
            }catch(Loading pending){}
            if(icon!=null)g.image(icon,Buff.imgoff);
            if(timer!=null)g.aimage(timer,Coord.of(Buff.imgoff.x+UI.scale(16),Buff.imgoff.y+UI.scale(32)),.5,1);
            g.chcolor();
        }
        public Object tooltip(Coord at,Widget previous){return L10n.format("Dewy Lady's Mantle: %s left",StudyProgress.longTime(owner.left));}
        public boolean mousedown(MouseDownEvent event){return true;}
        public void dispose(){if(icon!=null)icon.dispose();if(timer!=null)timer.dispose();super.dispose();}
    }
    private void clear(){if(indicator!=null){indicator.destroy();indicator=null;}}
    public void dispose(){clear();super.dispose();}
}
