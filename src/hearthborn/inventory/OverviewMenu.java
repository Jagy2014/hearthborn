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

package hearthborn.inventory;

import haven.*;
import hearthborn.L10n;
import hearthborn.ui.UiTheme;
import java.awt.Color;
import java.util.*;
import java.util.List;

/**
 * A flat modal context menu for an overview row. It sits just below the row (above it near the bottom edge) so the row stays visible.
 *
 * @author Jagy2014
 */
final class OverviewMenu extends Widget {
    private static final Text.Foundry SMALL=new Text.Foundry(Text.sans,10).aa(true);
    static final class Entry {
        final String label,hint;final Runnable action;final boolean danger,divider,icon;
        /** Ready quantity buttons; 0 stands for a custom amount. Null for a plain action. */
        final int[] chips;final boolean worst;
        Entry(String label,String hint,Runnable action,boolean danger){this(label,hint,action,danger,false,false,null,false);}
        private Entry(String label,String hint,Runnable action,boolean danger,boolean divider,boolean icon,int[] chips,boolean worst){
            this.label=label;this.hint=hint;this.action=action;this.danger=danger;this.divider=divider;this.icon=icon;this.chips=chips;this.worst=worst;
        }
        static Entry divider(){return new Entry("","",null,false,true,false,null,false);}
        static Entry backpack(String label,String hint,Runnable action){return new Entry(label,hint,action,false,false,true,null,false);}
        static Entry amounts(String label,String hint,boolean worst){return new Entry(label,hint,null,false,false,false,new int[]{1,OverviewSettings.quick(0),OverviewSettings.quick(1),0},worst);}
    }
    final ContainerOverview overview;final String key;final List<Entry> entries=new ArrayList<>();
    final Coord origin;final int width=UI.scale(OverviewTheme.MENU_WIDTH),row=UI.scale(OverviewTheme.ROW),gap=UI.scale(9),pad=UI.scale(4);
    private final int chipWidth=UI.scale(28),chipHeight=UI.scale(24),chipGap=UI.scale(4),chipStart=UI.scale(76);
    int hovered=-1,hoveredChip=-1;UI.Grab mouse,keys;
    static OverviewMenu open(ContainerOverview overview,ContainerOverview.Row selected,Coord at){return open(overview,selected,at,at.y,at.y);}
    /** {@code rowTop} and {@code rowBottom} are the clicked row's root coordinates. */
    static OverviewMenu open(ContainerOverview overview,ContainerOverview.Row selected,Coord at,int rowTop,int rowBottom){
        OverviewMenu menu=new OverviewMenu(overview,selected,at,rowTop,rowBottom);overview.ui.root.add(menu);return menu;
    }
    OverviewMenu(ContainerOverview overview,ContainerOverview.Row selected,Coord at,int rowTop,int rowBottom){
        super(overview.ui.root.sz);this.overview=overview;key=selected.key;
        if(selected.individual||selected.detail){
            // One stack or one quality: nothing to count.
            entries.add(Entry.backpack("To backpack","",()->overview.move(key,Integer.MAX_VALUE,false,false)));
            entries.add(Entry.divider());
            entries.add(new Entry("Drop this","",()->overview.dropGroup(key,true,at),true));
        }else{
            entries.add(Entry.backpack("To backpack","2×LMB",()->overview.move(key,Integer.MAX_VALUE,false,false)));
            entries.add(Entry.divider());
            entries.add(Entry.amounts("Best items","Shift+LMB",false));
            entries.add(Entry.amounts("Worst items","Shift+RMB",true));
            entries.add(Entry.divider());
            entries.add(new Entry("Drop below quality...","",()->overview.input(key,OverviewDialog.Mode.DROP_BELOW,false,at),false));
            entries.add(new Entry("Drop all...","",()->overview.dropGroup(key,false,at),true));
        }
        int height=height(),x=Math.max(0,Math.min(at.x,sz.x-width)),y=rowBottom;
        if(y+height>sz.y)y=rowTop-height;
        origin=Coord.of(x,Math.max(0,Math.min(y,sz.y-height)));
    }
    private int heightOf(Entry e){return e.divider?gap:row;}
    int height(){int h=pad*2;for(Entry e:entries)h+=heightOf(e);return h;}
    private int top(int index){int y=pad;for(int i=0;i<index;i++)y+=heightOf(entries.get(i));return y;}
    /** Menu-local top-left corner of a quantity button. */
    Coord chip(int entry,int index){return Coord.of(chipStart+index*(chipWidth+chipGap),top(entry)+(row-chipHeight)/2);}
    private boolean inside(Coord at){return at.isect(origin,Coord.of(width,height()));}
    private int entryAt(Coord at){
        if(!inside(at))return -1;
        int y=at.y-origin.y-pad;if(y<0)return -1;
        for(int i=0;i<entries.size();i++){int h=heightOf(entries.get(i));if(y<h)return entries.get(i).divider?-1:i;y-=h;}
        return -1;
    }
    private int chipAt(int entry,Coord at){
        if(entry<0||entries.get(entry).chips==null)return -1;
        Coord local=at.sub(origin);
        for(int i=0;i<entries.get(entry).chips.length;i++){Coord c=chip(entry,i);if(local.isect(c,Coord.of(chipWidth,chipHeight)))return i;}
        return -1;
    }
    protected void added(){mouse=ui.grabmouse(this);keys=ui.grabkeys(this);}
    public void mousemove(MouseMoveEvent e){hovered=entryAt(e.c);hoveredChip=chipAt(hovered,e.c);}
    private void choose(Entry entry,int chip){
        Runnable action;
        if(entry.chips!=null){
            if(chip<0)return;
            int amount=entry.chips[chip];boolean worst=entry.worst;
            action=amount==0?()->overview.input(key,OverviewDialog.Mode.TAKE,worst,ui.mc):()->overview.move(key,amount,worst,false);
        }else action=entry.action;
        close();action.run();
    }
    public boolean mousedown(MouseDownEvent e){
        if(!inside(e.c)){close();return true;}
        if(e.b!=1)return true;
        int index=entryAt(e.c);if(index<0)return true;
        choose(entries.get(index),chipAt(index,e.c));return true;
    }
    private static void backpack(GOut g,Coord at,Color color){
        g.chcolor(color);
        g.rect(at.add(UI.scale(2,5)),UI.scale(10,10));
        g.line(at.add(UI.scale(4,5)),at.add(UI.scale(4,2)),1);g.line(at.add(UI.scale(4,2)),at.add(UI.scale(10,2)),1);g.line(at.add(UI.scale(10,2)),at.add(UI.scale(10,5)),1);
        g.line(at.add(UI.scale(4,11)),at.add(UI.scale(10,11)),1);
        g.chcolor();
    }
    public void draw(GOut g){
        GOut main=g.reclip(origin,Coord.of(width,height()));OverviewTheme.panel(main,main.sz());
        for(int i=0;i<entries.size();i++){
            Entry e=entries.get(i);int y=top(i),h=heightOf(e);
            if(e.divider){main.chcolor(OverviewTheme.GOLD);main.line(Coord.of(UI.scale(5),y+h/2),Coord.of(width-UI.scale(5),y+h/2),1);main.chcolor();continue;}
            if(i==hovered&&e.chips==null){main.chcolor(OverviewTheme.ACCENT);main.frect(Coord.of(UI.scale(2),y),Coord.of(width-UI.scale(4),row));main.chcolor();}
            int left=UI.scale(10);
            if(e.icon){backpack(main,Coord.of(UI.scale(8),y+(row-UI.scale(18))/2),OverviewTheme.GOLD);left=UI.scale(34);}
            Color ink=e.danger?OverviewTheme.DANGER:OverviewTheme.ink();
            String hint=L10n.tr(e.hint);Text right=hint.isEmpty()?null:SMALL.render(hint,OverviewTheme.muted());
            int room=width-left-UI.scale(10)-(right==null?0:right.sz().x+UI.scale(8));
            if(e.chips!=null)room=chipStart-left-UI.scale(4);
            Text text=Text.render(OverviewTheme.fit(L10n.tr(e.label),Math.max(UI.scale(20),room),Text.std),ink);
            main.aimage(text.tex(),Coord.of(left,y+row/2),0,.5);text.dispose();
            if(right!=null){main.aimage(right.tex(),Coord.of(width-UI.scale(8),y+row/2),1,.5);right.dispose();}
            if(e.chips!=null)for(int c=0;c<e.chips.length;c++){
                Coord at=chip(i,c);boolean hot=i==hovered&&c==hoveredChip;
                main.chcolor(hot?OverviewTheme.ACCENT:UiTheme.light()?new Color(0,0,0,16):new Color(255,255,255,16));main.frect(at,UI.scale(28,24));
                main.chcolor(hot?OverviewTheme.GOLD:new Color(OverviewTheme.GOLD.getRed(),OverviewTheme.GOLD.getGreen(),OverviewTheme.GOLD.getBlue(),130));main.rect(at,UI.scale(28,24));main.chcolor();
                Text label=Text.render(e.chips[c]==0?"…":Integer.toString(e.chips[c]),OverviewTheme.ink());
                main.aimage(label.tex(),at.add(chipWidth/2,chipHeight/2),.5,.5);label.dispose();
            }
        }
    }
    public boolean keydown(KeyDownEvent e){if(e.code==java.awt.event.KeyEvent.VK_ESCAPE)close();return true;}
    void close(){if(mouse!=null){mouse.remove();mouse=null;}if(keys!=null){keys.remove();keys=null;}if(parent!=null)destroy();}
    public void dispose(){if(mouse!=null)mouse.remove();if(keys!=null)keys.remove();super.dispose();}
}
