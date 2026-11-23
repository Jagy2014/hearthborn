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

package hearthborn.quality;
import haven.*;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import hearthborn.util.GameObjects;
import java.util.*;
import java.util.regex.*;

/**
 * Reads canonical server item info and original label text, including quantity and quality.
 *
 * @author Jagy2014
 */
public final class VesselInfo {
    String contents="", amount="";
    double quality;
    boolean empty,loading;
    private static final Pattern CONTENT=Pattern.compile("^(?:Contains:\\s*)?([0-9]+(?:[.,][0-9]+)?)\\s*(l|L|kg|seeds?|liters?|litres?|pieces?)\\s+(?:of\\s+)?(.+?)[.]?$");
    private static final Pattern LITERS=Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s+(?:l|liters?|litres?)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY=Pattern.compile("^(?:Quality|"+Pattern.quote(L10n.tr("Quality"))+"):\\s*([0-9]+(?:[.,][0-9]+)?)");

    void text(String text) {
        if(text==null)return;
        text=text.trim();
        if(text.equalsIgnoreCase("Empty") || text.equals("Contains: Empty.") || text.equals(L10n.tr("Empty"))) {empty=true;return;}
        Matcher q=QUALITY.matcher(text);
        if(q.find()){quality=Double.parseDouble(q.group(1).replace(',','.'));return;}
        Matcher content=CONTENT.matcher(text);
        if(content.matches()) {contents=content.group(3);amount=content.group(1)+" "+content.group(2);}
    }
    @SuppressWarnings("unchecked")
    void info(List<ItemInfo> info) {
        Double q=QualityOverlay.qualityOf(info);if(q!=null&&q>0)quality=q;
        for(ItemInfo tip:info) {
            if(tip instanceof ItemInfo.Name) {
                String name=((ItemInfo.Name)tip).str.text;
                if(!name.equals("Barrel")&&!name.equals("Cistern")&&!name.equals("Demijohn")&&!name.equals("Contents")&&!name.equals("Contents:"))contents=name;
                text(name);
            }
            if(tip instanceof ItemInfo.AdHoc)text(((ItemInfo.AdHoc)tip).str.text);
            Object nested=GameObjects.field(tip,"sub",Object.class);
            if(nested instanceof List)info((List<ItemInfo>)nested);
            Object name=GameObjects.field(tip,"name",Object.class);
            if(name instanceof String) {
                if(tip.getClass().getSimpleName().equals("NamedContents"))contents=(String)name;
                text((String)name);
            }
        }
    }
    public static VesselInfo read(Window window) {
        VesselInfo stats=new VesselInfo();
        for(Widget widget:window.children(Widget.class))if(widget instanceof ItemInfo.Owner)try {
            stats.info(((ItemInfo.Owner)widget).info());
        } catch(Loading e){stats.loading=true;}
        List<Label> labels=new ArrayList<>(window.children(Label.class));
        labels.sort(Comparator.comparingInt((Label l)->l.parentpos(window).y).thenComparingInt(l->l.parentpos(window).x));
        for(int i=0;i<labels.size();i++) {
            String text=labels.get(i).texts;
            stats.text(text);
            if((text.equals("Quality:")||text.equals("Quality")||text.equals(L10n.tr("Quality")+":")||text.equals(L10n.tr("Quality"))) && i+1<labels.size())try {
                stats.quality=Double.parseDouble(labels.get(i+1).texts.trim().replace(',','.'));
            }catch(NumberFormatException ignored){}
        }
        return stats;
    }
    public boolean ready(){return !loading&&(empty||!contents.isEmpty());}
    public boolean empty(){return empty;}
    public String contents(){return contents;}
    public double quality(){return quality;}
    public double liters(){
        Matcher match=LITERS.matcher(amount);
        return match.matches()?Double.parseDouble(match.group(1).replace(',','.')):-1;
    }
    public static void observe(Window window,Gob gob) {
        VesselInfo stats=read(window);
        if(stats.loading)return;
        if(!stats.empty)hearthborn.map.MarkerQuality.vessel(gob,stats.contents,stats.quality);
        if(stats.empty || !stats.contents.isEmpty())
            hearthborn.storage.ContainerLook.observed(gob,stats.empty?null:L10n.text("ingredient",stats.contents));
        if(!RosterStore.shouldNoteVessel(gob))return;
        if(stats.empty)RosterStore.noteBarrel(gob,"Empty",0,"");
        else if(!stats.contents.isEmpty()) {
            RosterStore.noteBarrel(gob,L10n.text("ingredient",stats.contents),stats.quality,stats.amount);
            RosterStore.observeBarrelSeeds(stats.amount.contains("seed")?"Seeds of "+stats.contents:stats.contents,stats.quality,stats.amount);
        }
    }
    private VesselInfo() {}
}
