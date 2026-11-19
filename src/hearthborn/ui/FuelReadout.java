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
import java.awt.Color;
import java.util.*;
import java.util.regex.*;
/**
 * Oven fuel is a fraction of a full load. The bar alone hides a small remainder.
 *
 * @author Jagy2014
 */
public final class FuelReadout {
    public static final int CAPACITY=30;
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans,9).aa(true);
    private static final Pattern COUNT=Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Map<String,Tex> labels=new HashMap<String,Tex>();
    private FuelReadout(){}
    public static int units(double amount){
        if(!(amount>0))return 0;
        if(amount>1)amount=1;
        return (int)Math.round(amount*CAPACITY);
    }
    public static String text(double amount){return units(amount)+"/"+CAPACITY;}
    public static int width(int bar){return bar+UI.scale(4)+mark("30/30").sz().x;}
    public static void draw(GOut g,VMeter meter,int barWidth){
        Window window=meter.getparent(Window.class);
        if(!hearthborn.timers.ObjectTimers.oven(window))return;
        double amount=0;
        for(LayerMeter.Meter layer:meter.meters())amount=Math.max(amount,layer.a);
        String shown=reading(meter);
        if(shown==null)shown=text(amount);
        Tex label=mark(shown);
        int y=Math.max(0,(meter.sz.y-label.sz().y)/2);
        g.image(label,Coord.of(barWidth+UI.scale(2),y));
    }
    private static String reading(VMeter meter){
        try{
            for(ItemInfo info:meter.info()){
                String line=line(info);
                if(line==null)continue;
                Matcher match=COUNT.matcher(line);
                if(match.find())return match.group(1)+"/"+match.group(2);
            }
        }catch(Loading ignored){}
        return null;
    }
    private static String line(ItemInfo info){
        if(info instanceof ItemInfo.AdHoc)return ((ItemInfo.AdHoc)info).str.text;
        if(info instanceof ItemInfo.Name)return ((ItemInfo.Name)info).str.text;
        return null;
    }
    private static Tex mark(String text){
        if(labels.size()>12){for(Tex old:labels.values())old.dispose();labels.clear();}
        Tex found=labels.get(text);
        if(found==null)labels.put(text,found=new TexI(Utils.outline2(FONT.render(text,Color.WHITE).img,Color.BLACK,true)));
        return found;
    }
}
