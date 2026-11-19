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
import java.awt.Color;
import java.awt.Font;
import java.util.*;

/**
 * Tints deteriorating buildings and shows their remaining health.
 *
 * @author Jagy2014
 */
public final class StructureWear extends GAttrib implements RenderTree.Node, PView.Render2D, Gob.SetupMod {
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans.deriveFont(Font.BOLD),16).aa(true);
    private static final Map<String,Tex> labels=new HashMap<>();
    private static volatile Boolean shown;
    public static boolean enabled(){if(shown==null)shown=Utils.getprefb("hearthborn/world/structure-wear",true);return shown;}
    public static void enabled(boolean on){shown=on;Utils.setprefb("hearthborn/world/structure-wear",on);}

    /** The server supplies the fraction of health remaining. */
    public static int healthPercent(float health){
        return Math.max(0,Math.min(100,Math.round(health*100f)));
    }
    /** Damage controls the tint and whether a label is needed, not the displayed number. */
    public static int damagePercent(float health){
        if(health>=1f)return 0;
        return Math.max(1,Math.min(100,Math.round((1f-health)*100f)));
    }
    public static boolean structure(String name){
        return name!=null&&name.startsWith("gfx/terobjs/")
            &&!name.startsWith("gfx/terobjs/plants/")
            &&!name.startsWith("gfx/terobjs/trees/")
            &&!name.startsWith("gfx/terobjs/bushes/")
            &&!name.startsWith("gfx/terobjs/items/");
    }
    /** Pale ink with a black stroke. Yellow disappears on wood, straw and sand. */
    static Color ink(int percent){
        if(percent>=75)return new Color(255,186,176);
        if(percent>=50)return new Color(255,220,190);
        return new Color(255,244,220);
    }

    private String text;
    private Pipe.Op tint;
    private int percent;
    private double elapsed;
    public StructureWear(Gob gob){super(gob);}

    public void ctick(double dt){
        if((elapsed+=dt)<0.25)return;
        elapsed=0;
        GobHealth health=gob.getattr(GobHealth.class);
        boolean show=enabled()&&health!=null&&health.hp<1f&&structure(resource());
        int next=show?damagePercent(health.hp):0;
        if(next==percent)return;
        percent=next;
        text=next==0?null:healthPercent(health.hp)+"%";
        tint=next==0?null:new MixColor(255,36,0,Math.min(170,70+next));
    }
    private String resource(){
        try{Drawable drawable=gob.getattr(Drawable.class);Resource resource=drawable==null?null:drawable.getres();return resource==null?null:resource.name;}
        catch(Loading pending){return null;}
    }
    public Pipe.Op gobstate(){return enabled()&&!hearthborn.storage.ContainerLook.ready(gob)?tint:null;}
    /** Half a tile at most, and never larger than the glyphs themselves. */
    public static int drawnHeight(int texture,int tilePixels){
        if(texture<=0||tilePixels<=0)return 0;
        int fitted=Math.max(1,(int)Math.round(tilePixels*0.5));
        return Math.min(texture,fitted);
    }
    public static Coord fitted(Coord texture,int tilePixels){
        int height=drawnHeight(texture.y,tilePixels);
        if(height<=0)return Coord.z;
        int width=Math.max(1,(int)Math.round(texture.x*(height/(double)texture.y)));
        return Coord.of(width,height);
    }
    public void draw(GOut g,Pipe state){
        if(!enabled()||text==null)return;
        Coord at=WorldLabels.anchor(state,g.sz(),8);
        if(at==null)return;
        Tex mark=texture(text,percent);
        int tile=WorldLabels.iconExtent(state,g.sz(),8,1);
        Coord drawn=fitted(mark.sz(),tile);
        if(drawn.x<=0||drawn.y<=0)return;
        g.aimage(mark,at,.5,.5,drawn);
    }
    private static Tex texture(String text,int percent){
        synchronized(labels){
            Tex known=labels.get(text);
            if(known==null)labels.put(text,known=new TexI(Utils.outline2(FONT.render(text,ink(percent)).img,Color.BLACK,true)));
            return known;
        }
    }
}
