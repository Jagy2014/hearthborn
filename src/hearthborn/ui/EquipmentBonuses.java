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
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

/**
 * combined equipment modifiers, including gilding and derived statistics.
 *
 * @author Jagy2014
 */
public final class EquipmentBonuses extends Widget {
    private final Equipory equipment;
    private final Scrollbar scroll;
    private List<Row> rows=Collections.emptyList();
    private final Map<String,Tex> icons=new HashMap<>();
    private double elapsed;
    private String signature="";
    private final Map<String,Tex> texts=new HashMap<>();
    private static final String[] ORDER={"str","agi","int","con","prc","csm","dex","wil","psy","unarmed","melee","ranged","explore","stealth","sewing","smithing","masonry","carpentry","cooking","farming","survive","lore"};
    public EquipmentBonuses(Equipory equipment,int height) {
        super(Coord.of(UI.scale(230),height));this.equipment=equipment;
        add(new Label("Equipment bonuses:"));
        scroll=add(new JournalScrollport.Grip(sz.y-UI.scale(24)),sz.x-UI.scale(12),UI.scale(24));
    }
    public static class Row {
        public final String path;public Resource resource;public Object attribute;public double value;
        Row(String path,Resource resource,Object attribute,double value){this.path=path;this.resource=resource;this.attribute=attribute;this.value=value;}
    }
    private static Object field(Object object,String name){return GameObjects.field(object,name,Object.class);}
    private static void merge(Map<String,Row> rows,Resource resource,Object attr,double value) {
        Row row=rows.get(resource.name);
        if(row==null)rows.put(resource.name,new Row(resource.name,resource,attr,value));else row.value+=value;
    }
    public static void collect(List<ItemInfo> info,Map<String,Row> rows) {
        for(ItemInfo tip:info) {
            Object entries=field(tip,"tab");
            if(entries instanceof Collection)for(Object entry:(Collection<?>)entries) {
                Object attr=field(entry,"attr"),res=field(attr,"res"),value=field(entry,"mod");
                if(!(value instanceof Number))value=field(entry,"f");
                if(res instanceof Resource && value instanceof Number)merge(rows,(Resource)res,attr,((Number)value).doubleValue());
            }
            Object sub=field(tip,"sub");if(sub instanceof List)collectTips((List<?>)sub,rows);
            Object slots=field(tip,"s");if(slots instanceof Collection)for(Object slot:(Collection<?>)slots) {
                Object nested=field(slot,"info");if(nested instanceof List)collectTips((List<?>)nested,rows);
            }
        }
    }
    private static void collectTips(List<?> info,Map<String,Row> rows){List<ItemInfo> tips=new ArrayList<>();for(Object tip:info)if(tip instanceof ItemInfo)tips.add((ItemInfo)tip);collect(tips,rows);}
    private static void custom(Map<String,Row> rows,String name,double value){String path="gfx/hud/chr/custom/"+name;rows.put(path,new Row(path,null,null,value));}
    private static int priority(Row row){for(int i=0;i<ORDER.length;i++)if(row.path.equals("gfx/hud/chr/"+ORDER[i]))return i;String[] custom={"ahard","asoft","sneak","detect","mine"};for(int i=0;i<custom.length;i++)if(row.path.endsWith("/custom/"+custom[i]))return ORDER.length+1+i;return ORDER.length;}
    public void tick(double dt) {
        super.tick(dt);if((elapsed+=dt)<.25 || ui==null || ui.sess==null)return;elapsed=0;
        try {
            Map<String,Row> combined=new HashMap<>();double mining=0;
            for(GItem item:equipment.equippedItems()) {
                List<ItemInfo> info=item.info();collect(info,combined);
                Object wear=null,armor=null;
                for(ItemInfo tip:info){if(tip.getClass().getSimpleName().equals("Wear"))wear=tip;if(tip.getClass().getSimpleName().equals("Armor"))armor=tip;}
                Number damage=GameObjects.field(wear,"d",Number.class),maximum=GameObjects.field(wear,"m",Number.class);
                if(armor!=null && damage!=null && maximum!=null && damage.doubleValue()<maximum.doubleValue()) {
                    for(String name:new String[]{"hard","soft"}) {
                        Number value=GameObjects.field(armor,name,Number.class);if(value!=null){String path="gfx/hud/chr/custom/a"+name;Row row=combined.get(path);custom(combined,"a"+name,(row==null?0:row.value)+value.doubleValue());}
                    }
                }
                if(isMe()) {
                    ItemInfo.Name name=ItemInfo.find(ItemInfo.Name.class,info);Double quality=hearthborn.QualityOverlay.qualityOf(info);
                    if(name!=null && quality!=null && Arrays.asList("Pickaxe","Stone Axe","Tinker's Throwing Axe","Metal Axe","Woodsman's Axe").contains(name.str.text))
                        mining=Math.max(mining,ui.sess.glob.getcattr("str").comp*quality);
                }
            }
            if(isMe()) {
                custom(combined,"detect",(double)ui.sess.glob.getcattr("prc").comp*ui.sess.glob.getcattr("explore").comp);
                custom(combined,"sneak",(double)ui.sess.glob.getcattr("int").comp*ui.sess.glob.getcattr("stealth").comp);
                Row multiplier=combined.get("gfx/hud/chr/mine-str");
                if(mining>0)custom(combined,"mine",Math.sqrt(mining*(1+(multiplier==null?0:multiplier.value))));
            }
            List<Row> next=new ArrayList<>(combined.values());next.sort(Comparator.comparingInt(EquipmentBonuses::priority).thenComparing(r->r.path));String current=next.stream().map(r->r.path+"="+r.value).collect(java.util.stream.Collectors.joining(";"));
            if(!current.equals(signature)){for(Tex text:texts.values())text.dispose();texts.clear();signature=current;}rows=next;
            scroll.max=Math.max(0,rows.size()*UI.scale(16)-(sz.y-UI.scale(24)));scroll.ch(0);
        }catch(Loading ignored){}
    }
    private boolean isMe(){return ui.root.getchild(GameUI.class)!=null && ui.root.getchild(GameUI.class).equipment()==equipment;}
    private Tex icon(Row row) {
        Tex result=icons.get(row.path);if(result!=null)return result;
        BufferedImage image=null;
        if(row.resource!=null){Resource.Image layer=row.resource.layer(Resource.imgc);if(layer!=null)image=layer.img;}
        else try(java.io.InputStream in=hearthborn.assets.Assets.open("icons/equipment/bonus-"+row.path.substring(row.path.lastIndexOf('/')+1)+".png")){if(in!=null)image=javax.imageio.ImageIO.read(in);}catch(java.io.IOException ignored){}
        if(image!=null){result=new TexI(PUtils.convolvedown(image,UI.scale(14,14),CharWnd.iconfilter));icons.put(row.path,result);}return result;
    }
    private static String customName(String path) {
        if(path.endsWith("/custom/ahard"))return "Armor deflection";
        if(path.endsWith("/custom/asoft"))return "Armor absorption";
        if(path.endsWith("/custom/sneak"))return "Concealment (Int*Ste)";
        if(path.endsWith("/custom/detect"))return "Detection (Prc*Exp)";
        if(path.endsWith("/custom/mine"))return "Mining power";
        return path;
    }
    /** Bright green and red of the dark theme become the ink colours of parchment. */
    static String themed(String markup) {
        if(!UiTheme.light())return markup;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\$col\\[(\\d+),(\\d+),(\\d+)(?:,(\\d+))?\\]").matcher(markup);
        StringBuffer out=new StringBuffer();
        while(m.find()){
            Color c=UiTheme.readable(new Color(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3))));
            m.appendReplacement(out,"\\$col["+c.getRed()+","+c.getGreen()+","+c.getBlue()+"]");
        }
        m.appendTail(out);
        return out.toString();
    }
    private static String format(Row row) {
        if(row.path.equals("gfx/hud/chr/mine-str"))return (row.value>=0?"$col[128,255,128]{+":"$col[255,128,128]{")+Utils.odformat2(row.value*100,1)+"%}";
        if(row.attribute!=null)try{return (String)row.attribute.getClass().getMethod("format",double.class).invoke(row.attribute,row.value);}catch(ReflectiveOperationException ignored){}
        return (row.value>=0?"$col[128,255,128]{+":"$col[255,128,128]{")+Utils.odformat2(row.value,1)+"}";
    }
    public void draw(GOut g) {
        UiTheme.panel(g,sz);
        GOut list=g.reclip(UI.scale(0,24),Coord.of(sz.x-scroll.sz.x,sz.y-UI.scale(24)));
        int y=-scroll.val;
        for(Row row:rows) {
            if(y+UI.scale(16)>=0 && y<list.sz().y)try {
                Tex icon=icon(row);if(icon!=null)list.image(icon,Coord.of(0,y));
                String english=row.resource==null?customName(row.path):row.resource.flayer(Resource.tooltip).original;
                String name=hearthborn.L10n.resource("tooltip",row.path,english);
                boolean light=UiTheme.light();
                Tex label=texts.computeIfAbsent("name:"+light+name,k->new TexI((light?Text.std.render(name,Parchment.TEXT2):Text.render(name)).img));list.image(label,Coord.of(UI.scale(17),y));
                String formatted=themed(format(row));Tex value=texts.computeIfAbsent("value:"+light+formatted,k->new TexI(RichText.render(formatted,0).img));list.aimage(value,Coord.of(list.sz().x,y),1,0);
            }catch(Loading ignored){}
            y+=UI.scale(16);
        }
        super.draw(g);
    }
    public boolean mousewheel(MouseWheelEvent ev){scroll.ch(UI.scale(16)*ev.step());return true;}
    public void dispose(){for(Tex icon:icons.values())icon.dispose();for(Tex text:texts.values())text.dispose();super.dispose();}
}
