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
import hearthborn.QualityOverlay;
import hearthborn.L10n;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Carried ammunition, following item-owned contents rather than popup windows.
 *
 * @author Jagy2014
 */
public final class BowArrowCount implements Disposable {
    private double checked=Double.NEGATIVE_INFINITY;
    private String shown;
    private Tex label;
    private Snapshot snapshot;
    private WItem.ItemTip sourceTip,combinedTip;
    private String detailText;

    private enum Kind {
        ARROW("arrow","Arrow"),BONE("arrow-bone","Bone Arrow"),STONE("arrow-stone","Stone Arrow"),METAL("arrow-metal","Metal Arrow");
        final String resource,name;
        Kind(String resource,String name){this.resource="gfx/invobjs/"+resource;this.name=name;}
        static Kind of(String resource){for(Kind kind:values())if(kind.resource.equals(resource))return kind;return null;}
    }
    private static final class Group {
        long amount,known;
        double quality;
        void add(long amount,Double quality){
            this.amount+=amount;
            if(quality!=null){known+=amount;this.quality+=amount*quality;}
        }
        String average(){return known==amount&&known>0?QualityOverlay.format(quality/known):"?";}
    }
    private static final class Snapshot {
        long total;boolean loading;
        final EnumMap<Kind,Group> groups=new EnumMap<>(Kind.class);
        String total(){return loading?"?":Long.toString(total);}
        String text(){
            StringBuilder result=new StringBuilder(L10n.format("Arrows: %s",total()));
            groups.forEach((kind,group)->result.append('\n').append(L10n.format("%s: %d · Average quality: %s",
                L10n.resource("tooltip",kind.resource,kind.name),group.amount,group.average())));
            return result.toString();
        }
    }

    private static String resource(GItem item){
        String name=item.getres().name;
        return name.replace("gfx/invobjs/small/","gfx/invobjs/");
    }
    static boolean bow(GItem item){
        try{String name=resource(item);return name.equals("gfx/invobjs/huntersbow")||name.equals("gfx/invobjs/rangersbow");}
        catch(Loading pending){return false;}
    }
    private static boolean equipped(Equipory equipment){
        if(equipment==null)return false;
        for(Widget child=equipment.child;child!=null;child=child.next)if(child instanceof WItem)
            if((child.c.equals(Equipory.ecoords[6].add(1,1))||child.c.equals(Equipory.ecoords[7].add(1,1)))&&bow(((WItem)child).item))return true;
        return false;
    }
    private static void scan(Widget owner,Set<Widget> seen,Snapshot result){
        if(owner==null||!seen.add(owner))return;
        if(owner instanceof GItem){
            GItem item=(GItem)owner;
            if(item.contents!=null){scan(item.contents,seen,result);return;}
            try{
                Kind kind=Kind.of(resource(item));if(kind==null)return;
                List<ItemInfo> info=item.info();long amount=Math.max(1,item.num);
                for(ItemInfo tip:info)if(tip instanceof GItem.Amount){amount=Math.max(0,((GItem.Amount)tip).itemnum());break;}
                if(amount==0)return;
                result.total+=amount;
                result.groups.computeIfAbsent(kind,key->new Group()).add(amount,QualityOverlay.qualityOf(info));
            }catch(Loading pending){result.loading=true;}
            return;
        }
        for(Widget child=owner.child;child!=null;child=child.next)if(child instanceof GItem)scan(child,seen,result);
    }
    private static Snapshot snapshot(GameUI gui,Equipory equipment){
        Snapshot result=new Snapshot();
        Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        scan(gui.maininv,seen,result);scan(equipment,seen,result);return result;
    }
    /** Unknown resources or arrow amounts must not masquerade as an empty quiver. */
    static long count(GameUI gui,Equipory equipment){
        Snapshot result=snapshot(gui,equipment);return result.loading?-1:result.total;
    }
    public void update(GameUI gui,Equipory equipment,boolean visible){
        if(!visible||!equipped(equipment)){dispose();checked=Double.NEGATIVE_INFINITY;return;}
        double now=Utils.rtime();if(now-checked<.25)return;checked=now;
        snapshot=snapshot(gui,equipment);String next=snapshot.total();
        if(!next.equals(shown)){
            if(label!=null)label.dispose();label=new TexI(QualityOverlay.styled(next));shown=next;
        }
    }
    public void draw(GOut g,GItem item){
        if(label!=null&&bow(item))g.aimage(label,g.sz().sub(UI.scale(2,2)),1,1);
    }
    String details(){return snapshot==null?null:snapshot.text();}
    /** Preserve the native item/tooltip metadata and append ammunition below the bow's own stats. */
    public Object tooltip(WItem item,Object original){
        if(snapshot==null||!bow(item.item)||!(original instanceof WItem.ItemTip))return original;
        WItem.ItemTip source=(WItem.ItemTip)original;String text=details();
        if(source!=sourceTip||!text.equals(detailText)){
            clearTip();
            List<BufferedImage> lines=new ArrayList<>();
            for(String line:text.split("\n"))lines.add(Text.render(line).img);
            BufferedImage extra=ItemInfo.catimgs(UI.scale(2),lines.toArray(new BufferedImage[0]));
            combinedTip=item.new ItemTip(source.info(),ItemInfo.catimgs(UI.scale(6),((TexI)source.get()).back,extra));
            sourceTip=source;detailText=text;
        }
        return combinedTip;
    }
    private void clearTip(){if(combinedTip!=null)combinedTip.get().dispose();sourceTip=combinedTip=null;detailText=null;}
    public void dispose(){if(label!=null)label.dispose();label=null;shown=null;snapshot=null;clearTip();}
}
