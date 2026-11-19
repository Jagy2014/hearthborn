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
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.Color;
import java.awt.image.BufferedImage;
import haven.render.*;

/**
 * @author Jagy2014
 */
public final class MeterReadout implements Disposable {
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),10).aa(true);
    private static final Text.Foundry HEALTH=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),9).aa(true);
    private static final Pattern PERCENT=Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*%");
    private String previous;
    private Coord previousRoom;
    private double previousScale;
    private Tex label;
    public void draw(GOut g,String resource,List<LayerMeter.Meter> meters,int constitution) {
        draw(g,resource,meters,constitution,null);
    }
    public void draw(GOut g,String resource,List<LayerMeter.Meter> meters,int constitution,String tip) {
        if(!StatusMeters.enabled("numbers")||meters.isEmpty())return;
        drawLabel(g,text(resource,meters,constitution,tip));
    }
    /** Rasterize once at screen resolution; bar projection must never enlarge the glyph bitmap. */
    public void drawLabel(GOut g,String value) {
        Pixels pixels=new Pixels(g);
        if(!value.equals(previous)||!pixels.room.equals(previousRoom)||pixels.scale!=previousScale) {
            dispose();previous=value;previousRoom=pixels.room;previousScale=pixels.scale;
            if(!value.isEmpty())label=new TexI(labelImage(value,value.indexOf('/')>=0?HEALTH:FONT,pixels.scale,pixels.room));
        }
        if(label!=null)pixels.draw(label);
    }
    /** Soft, hard and maximum hit points. Maximum is 100 × √(constitution / 10). */
    public static String text(String resource,List<LayerMeter.Meter> meters,int constitution){
        return text(resource,meters,constitution,null);
    }
    public static String text(String resource,List<LayerMeter.Meter> meters,int constitution,String tip){
        if(meters==null||meters.isEmpty())return "";
        if("gfx/hud/meter/häst".equals(resource)){
            Integer labeled=labeledPercent(tip);
            if(labeled!=null)return labeled+"%";
        }
        if("gfx/hud/meter/hp".equals(resource)&&constitution>0){
            int max=maxHitPoints(constitution);
            int hard=points(meters.get(0).a,max);
            int soft=meters.size()>1?points(meters.get(1).a,max):hard;
            return soft+"/"+hard+"/"+max;
        }
        return String.format(Locale.ROOT,"%.0f%%",meters.get(0).a*100);
    }
    /** Percent written by the server in the meter tooltip, such as "Pony Power: 11%". */
    public static Integer labeledPercent(String tip){
        if(tip==null)return null;
        Matcher match=PERCENT.matcher(tip);
        if(!match.find())return null;
        return (int)Math.round(Double.parseDouble(match.group(1).replace(',','.')));
    }
    /** Plain meter tooltip, sent as a string rather than item info. */
    public static String widgetTip(Object tooltip){
        if(tooltip instanceof String)return (String)tooltip;
        if(tooltip instanceof Widget.KeyboundTip)return ((Widget.KeyboundTip)tooltip).base;
        return null;
    }
    public static String tipText(ItemInfo.Raw raw){
        if(raw==null||raw.data==null)return null;
        StringBuilder buf=new StringBuilder();
        appendValues(buf,raw.data);
        return buf.toString();
    }
    public static String infoText(List<ItemInfo> info){
        if(info==null)return null;
        StringBuilder buf=new StringBuilder();
        for(ItemInfo item:info){
            String line=null;
            if(item instanceof ItemInfo.Name)line=((ItemInfo.Name)item).str.text;
            else if(item instanceof ItemInfo.AdHoc)line=((ItemInfo.AdHoc)item).str.text;
            else if(item instanceof ItemInfo.Pagina)line=((ItemInfo.Pagina)item).doc.text;
            if(line==null||line.isEmpty())continue;
            if(buf.length()>0)buf.append('\n');
            buf.append(line);
        }
        return buf.toString();
    }
    private static void appendValues(StringBuilder buf,Object value){
        if(value instanceof Object[]){
            for(Object item:(Object[])value)appendValues(buf,item);
        }else if(value instanceof String){
            if(buf.length()>0)buf.append('\n');
            buf.append((String)value);
        }
    }
    public static int maxHitPoints(int constitution){return (int)Math.round(100*Math.sqrt(constitution/10.0));}
    private static int points(double fraction,int max){return (int)Math.round(Utils.clip(fraction,0,1)*max);}
    public static Tex renderLabel(String value){return renderLabel(value,FONT);}
    public static Tex renderLabel(String value,Text.Foundry font){
        return new TexI(labelImage(value,font,1,IMeter.msz.sub(UI.scale(2),0)));
    }
    static BufferedImage labelImage(String value,Text.Foundry font,double scale,Coord room) {
        float size=(float)(font.font.getSize2D()*scale);
        BufferedImage image=null;
        for(int attempt=0;attempt<12;attempt++) {
            Text.Foundry fitted=new Text.Foundry(font.font.deriveFont(Math.max(1f,size))).aa(true);
            image=outlined(trim(fitted.render(value,Color.WHITE).img));
            if(image.getWidth()<=room.x&&image.getHeight()<=room.y)break;
            double fit=Math.min(room.x/(double)image.getWidth(),room.y/(double)image.getHeight());
            size=Math.max(1f,Math.min(size-.5f,(float)(size*fit*.98)));
        }
        return image;
    }
    /** One screen pixel around the glyph alpha, including its antialiased edges; no rectangular plate or glow. */
    private static BufferedImage outlined(BufferedImage text) {
        BufferedImage image=TexI.mkbuf(Coord.of(text.getWidth()+2,text.getHeight()+2));
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int alpha=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                if(Math.abs(dx)+Math.abs(dy)>1)continue;
                int sx=x-1+dx,sy=y-1+dy;
                if(sx>=0&&sy>=0&&sx<text.getWidth()&&sy<text.getHeight())
                    alpha=Math.max(alpha,text.getRGB(sx,sy)>>>24);
            }
            image.setRGB(x,y,alpha<<24);
        }
        java.awt.Graphics2D graphics=image.createGraphics();
        graphics.drawImage(text,1,1,null);graphics.dispose();return image;
    }
    private static BufferedImage trim(BufferedImage image) {
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)>>>24)!=0) {
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        }
        return right<left?TexI.mkbuf(Coord.of(1,1)):image.getSubimage(left,top,right-left+1,bottom-top+1);
    }
    /** Convert the current bar projection to integer framebuffer coordinates, preserving its clip. */
    static final class Pixels {
        final GOut canvas;
        final Coord center,room,ul,br;
        final double scale;
        Pixels(GOut g) {
            Pipe pipe=g.basicstate();
            States.Viewport viewport=pipe.get(States.viewport);
            State transform=pipe.get(States.vxf);
            if(viewport!=null&&transform instanceof Ortho2D) {
                Ortho2D ortho=(Ortho2D)transform;
                Coord size=viewport.area.sz();
                double sx=size.x/(double)(ortho.r-ortho.l),sy=size.y/(double)(ortho.b-ortho.u);
                scale=sy;
                center=point(g.tx.add(IMeter.off).add(IMeter.msz.div(2)),ortho,sx,sy);
                room=Coord.of(Math.max(1,(int)Math.floor(IMeter.msz.x*sx)-2),Math.max(1,(int)Math.floor(IMeter.msz.y*sy)));
                ul=point(g.ul,ortho,sx,sy);br=point(g.br,ortho,sx,sy);
                pipe.prep(new Ortho2D(0,0,size.x,size.y));
                canvas=new GOut(g.out,pipe,size);
            } else {
                scale=1;canvas=g;center=IMeter.off.add(IMeter.msz.div(2));
                room=IMeter.msz.sub(UI.scale(2),0);ul=g.ul.sub(g.tx);br=g.br.sub(g.tx);
            }
        }
        private static Coord point(Coord value,Ortho2D ortho,double sx,double sy) {
            return Coord.of((int)Math.round((value.x-ortho.l)*sx),(int)Math.round((value.y-ortho.u)*sy));
        }
        void draw(Tex label) {canvas.image(label,center.sub(label.sz().div(2)),ul,br);}
    }
    public void dispose(){if(label!=null)label.dispose();label=null;previous=null;previousRoom=null;previousScale=0;}
}
