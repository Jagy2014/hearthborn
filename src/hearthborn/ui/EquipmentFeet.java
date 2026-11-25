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
import hearthborn.util.GameObjects;
import java.awt.Color;

/**
 * Detection, concealment and armor class drawn under the equipment portrait.
 *
 * @author Jagy2014
 */
public final class EquipmentFeet {
    private final Equipory equipment;
    private double elapsed;
    private String shown="";
    private Tex[] lines=new Tex[0];
    public EquipmentFeet(Equipory equipment){this.equipment=equipment;}
    public static int detection(int perception,int exploration){return perception*exploration;}
    public static int subtlety(int intelligence,int stealth){return intelligence*stealth;}
    public static String armorClass(int hard,int soft){return (hard+soft)+" ("+hard+" + "+soft+")";}
    public static int whole(double value){return (int)Math.round(value);}
    /** Broken armor, whose wear has reached its maximum, does not count. */
    public static boolean wornOut(Number damage,Number maximum){return damage!=null&&maximum!=null&&damage.doubleValue()>=maximum.doubleValue();}
    public static int[] armor(Iterable<GItem> items){
        double hard=0,soft=0;
        for(GItem item:items){
            haven.ItemInfo armor=null,wear=null;
            for(haven.ItemInfo tip:item.info()){
                String name=tip.getClass().getSimpleName();
                if(name.equals("Armor"))armor=tip;
                if(name.equals("Wear"))wear=tip;
            }
            if(armor==null)continue;
            Number damage=GameObjects.field(wear,"d",Number.class),maximum=GameObjects.field(wear,"m",Number.class);
            if(wornOut(damage,maximum))continue;
            Number hardValue=GameObjects.field(armor,"hard",Number.class),softValue=GameObjects.field(armor,"soft",Number.class);
            if(hardValue!=null)hard+=hardValue.doubleValue();
            if(softValue!=null)soft+=softValue.doubleValue();
        }
        return new int[]{whole(hard),whole(soft)};
    }
    public void tick(double dt){
        if((elapsed+=dt)<.25)return;elapsed=0;
        UI ui=equipment.ui;GameUI gui=ui==null?null:ui.root.getchild(GameUI.class);
        if(gui==null||gui.equipment()!=equipment||ui.sess==null){show("");return;}
        try{
            Glob glob=ui.sess.glob;
            int perception=glob.getcattr("prc").comp,exploration=glob.getcattr("explore").comp;
            int intelligence=glob.getcattr("int").comp,stealth=glob.getcattr("stealth").comp;
            int[] armor=armor(equipment.equippedItems());
            show(hearthborn.L10n.tr("Detection (Prc*Exp)")+": "+detection(perception,exploration)+"\n"
                +hearthborn.L10n.tr("Concealment (Int*Ste)")+": "+subtlety(intelligence,stealth)+"\n"
                +hearthborn.L10n.tr("Armor Class (Def*Abs)")+": "+(armor[0]+armor[1]));
        }catch(Loading ignored){}
    }
    private void show(String text){
        if(text.equals(shown))return;
        for(Tex line:lines)line.dispose();
        shown=text;
        if(text.isEmpty()){lines=new Tex[0];return;}
        String[] rows=text.split("\n");
        lines=new Tex[rows.length];
        for(int i=0;i<rows.length;i++)lines[i]=new TexI(hearthborn.QualityOverlay.styled(Text.std.render(rows[i],Color.WHITE).img));
    }
    public void paint(GOut g){
        if(lines.length==0)return;
        int height=0;for(Tex line:lines)height+=line.sz().y;
        int y=Math.max(0,g.sz().y-height-UI.scale(6));
        for(Tex line:lines){g.image(line,Coord.of(Math.max(0,(g.sz().x-line.sz().x)/2),y));y+=line.sz().y;}
    }
    public void dispose(){for(Tex line:lines)line.dispose();lines=new Tex[0];shown="";}
    public String shown(){return shown;}
    public static String format(int perception,int exploration,int intelligence,int stealth,int hard,int soft){
        return "Detection (Prc*Exp): "+detection(perception,exploration)
            +"\nConcealment (Int*Ste): "+subtlety(intelligence,stealth)
            +"\nArmor Class (Def*Abs): "+(hard+soft);
    }
}
