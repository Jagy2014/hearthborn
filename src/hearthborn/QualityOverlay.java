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

package hearthborn;

import haven.*;
import hearthborn.L10n;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * A cached quality label drawn without changing the item's click area.
 *
 * @author Jagy2014
 */
public final class QualityOverlay implements Disposable {
    private List<ItemInfo> previous;
    private Tex label;
    private int style=-1;

    /** Tableware uses our quality mark; the server's extra overlay would paint over it. */
    public static boolean replaces(ItemInfo overlay,List<ItemInfo> info) {
        return overlay.getClass().getName().equals("haven.res.ui.tt.q.quality.Quality") &&
            ItemCondition.tableware(info) && qualityOf(java.util.Collections.singletonList(overlay)) != null &&
            displayQuality(info,true) != null;
    }

    /** QBuff is server-loaded code; read its public value without copying it. */
    public static Double qualityOf(List<ItemInfo> info) {
        for(ItemInfo tip : info) {
            for(Class<?> type = tip.getClass(); type != null; type = type.getSuperclass()) {
                if(!type.getName().equals("haven.res.ui.tt.q.qbuff.QBuff"))
                    continue;
                try {
                    double value = ((Number)type.getField("q").get(tip)).doubleValue();
                    if(Double.isFinite(value) && value >= 0)
                        return value;
                } catch(ReflectiveOperationException | ClassCastException ignored) {
                    // A changed server format should hide the label, not break the UI.
                }
            }
        }
        return null;
    }

    private static final java.util.regex.Pattern LIQUID_AMOUNT=java.util.regex.Pattern.compile("(?i)[0-9]+(?:[.,][0-9]+)?\\s*(?:l|litres?|liters?|л|литр[а-я]*)\\b");
    private static boolean liquidName(List<ItemInfo> info){
        for(ItemInfo tip:info){
            if(tip instanceof ItemInfo.Name&&LIQUID_AMOUNT.matcher(((ItemInfo.Name)tip).str.text).find())return true;
            if(tip instanceof ItemInfo.Contents&&liquidName(((ItemInfo.Contents)tip).sub))return true;
        }
        return false;
    }
    public static Double displayQuality(List<ItemInfo> info,boolean liquidVessel){
        for(ItemInfo tip:info)if(tip instanceof ItemInfo.Contents){
            List<ItemInfo> contents=((ItemInfo.Contents)tip).sub;
            if(liquidVessel||liquidName(contents))return nestedQuality(contents);
        }
        return qualityOf(info);
    }
    private static Double nestedQuality(List<ItemInfo> info){
        for(ItemInfo tip:info)if(tip instanceof ItemInfo.Contents){Double value=nestedQuality(((ItemInfo.Contents)tip).sub);if(value!=null)return value;}
        return qualityOf(info);
    }
    private static boolean liquidVessel(String resource){
        String name=resource.substring(resource.lastIndexOf('/')+1);
        for(String kind:new String[]{"waterflask","waterskin","wineskin","bucket","jug","bottle","kuksa","mug","tankard","wineglass","amphora","demijohn"})if(name.contains(kind))return true;
        return false;
    }

    public static Double qualityOf(GItem item) {
        List<ItemInfo> info = item.info();
        if(stacked(item)) return stackQuality(item);
        return displayQuality(info,liquidVessel(item.res.get().name));
    }

    /** Mean quality of a stack, weighted by each member's amount. Null for a single item, an empty stack, or a member that has no quality yet. Does not read the stack item's own tooltip. */
    public static Double stackQuality(GItem item) {
        if(item == null || item.contents == null || !stacked(item)) return null;
        double total = 0;
        int count = 0;
        for(Widget child : item.contents.children()) {
            if(!(child instanceof GItem)) continue;
            GItem member = (GItem)child;
            Double quality = qualityOf(member.info());
            if(quality == null) return null;
            int amount = amount(member);
            total += quality * amount;
            count += amount;
        }
        return count == 0 ? null : total / count;
    }

    public static boolean stacked(GItem item) {
        if(item == null) return false;
        if(item.contents != null && item.contents.getClass().getName().endsWith("ItemStack")) return true;
        try {return item.res != null && "gfx/invobjs/stack".equals(item.resource().name);}
        catch(Loading pending) {return false;}
    }

    private static int amount(GItem item) {
        for(ItemInfo tip : item.info()) {
            if(tip instanceof GItem.Amount) {
                int num = ((GItem.Amount)tip).itemnum();
                return num > 0 ? num : 1;
            }
        }
        return 1;
    }

    /** One quality line for the whole stack, using the amount-weighted mean. */
    public static void presentAverage(GItem item, List<ItemInfo> info) {
        if(!stacked(item)) return;
        java.util.Iterator<ItemInfo> tips = info.iterator();
        while(tips.hasNext()) if(tips.next().getClass().getName().endsWith(".QBuff")) tips.remove();
        Double average = stackQuality(item);
        if(average != null) info.add(new Average(item, average));
    }

    private static final class Average extends ItemInfo.Tip {
        final double quality;
        Average(ItemInfo.Owner owner, double quality) {super(owner); this.quality = quality;}
        public int order() {return 10;}
        public java.awt.image.BufferedImage tipimg() {
            return RichText.render(String.format("$col[192,192,255]{%s} %s", L10n.tr("Quality:"), format(quality)), 0).img;
        }
    }

    public static String format(double quality) {
        String value = String.format(Locale.ROOT, "%.1f", quality);
        return value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
    }

    public void draw(GOut g, GItem item, Coord size) {
        drawCorner(g, item, 1);
    }
    /** Whole number in the icon's top-right, with a thick dark outline. */
    public void drawCorner(GOut g,GItem item){drawCorner(g,item,1);}
    /** {@code fit} shrinks the same corner mark so a zoomed inventory does not enlarge it. */
    public void drawCorner(GOut g,GItem item,double fit){
        try{
            if(!QualityStyle.show())return;
            List<ItemInfo> info=item.info();
            if(info!=previous||style!=QualityStyle.version()){
                style=QualityStyle.version();
                Double quality=qualityOf(item);
                dispose();
                if(quality!=null)label=new TexI(corner(quality));
                previous=info;
            }
            if(label==null)return;
            // Top-right, or bottom-right where the count is when the two are swapped.
            boolean low=QualityStyle.swapped();
            Coord corner=Coord.of(g.sz().x,low?g.sz().y:0);
            double along=low?0.8:0.2;
            if(!(fit>0)||fit==1)g.aimage(label,corner,0.95,along);
            else g.aimage(label,corner,0.95,along,label.sz().mul(fit));
        }catch(Loading loading){}
    }
    private static BufferedImage corner(double quality){
        return styled(QualityStyle.text(quality),QualityStyle.ink(quality));
    }
    /** White cut type, a tight black outline, then a one-pixel black glow. */
    public static BufferedImage styled(String text){return styled(text,Color.WHITE);}
    /** The same cut type and glow, in the given ink. Gilding keeps its own color. */
    public static BufferedImage styled(String text,Color ink){
        return styled(Text.render(text,ink==null?Color.WHITE:ink).img);
    }
    /** Thick black outline and a one-pixel glow around an already drawn line. */
    public static BufferedImage styled(BufferedImage text){
        BufferedImage drawn=Utils.outline2(text,Color.BLACK,true);
        return PUtils.rasterimg(PUtils.blurmask2(drawn.getRaster(),UI.scale(1),UI.scale(1),Color.BLACK));
    }

    public void dispose() {
        if(label != null) {
            label.dispose();
            label = null;
        }
        previous = null;
    }
}
