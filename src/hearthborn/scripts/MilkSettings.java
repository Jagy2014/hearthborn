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

package hearthborn.scripts;

import haven.*;
import hearthborn.L10n;
import hearthborn.ui.UiTheme;
import java.awt.Color;
import java.awt.event.KeyEvent;

/**
 * Named collection areas in one window: a row per area with icon buttons. Selecting a row edits metadata only; collection follows the player.
 *
 * @author Jagy2014
 */
public final class MilkSettings extends Window {
    private static final int WIDTH=440,ROW=34,MAX_ROWS=6;
    private final GameUI gui;
    private final SavedAreaHighlight highlight;
    private final Label status,empty,where;
    private final SListBox<SavedArea,Widget> list;
    private final FieldIcon add,pin;
    private final Button collect;
    private final Widget selectionHint;
    private final Label selectionTitle;
    private MilkPens pens;
    private SavedArea current,selected,renaming;
    private double elapsed;
    private Runnable cancelSelection;
    private int theme=-1;
    private String lastControls="";
    public static void open(GameUI gui){
        if(gui==null)return;
        MilkSettings existing=gui.getchild(MilkSettings.class);
        // A window in its destruction animation cannot be revived by show().
        if(existing!=null&&!existing.visible()){existing.destroy();existing=null;}
        if(existing!=null){existing.refresh();existing.show();existing.raise();return;}
        try{gui.add(new MilkSettings(gui),UI.scale(250,150));}
        catch(IllegalStateException failure){gui.error(L10n.tr(failure.getMessage()));}
    }
    private MilkSettings(GameUI gui){
        super(Coord.z,"Milk collection");this.gui=gui;highlight=new SavedAreaHighlight(gui,this);
        status=add(new Label(""));
        add=add(new FieldIcon(FieldIcon.Glyph.ADD,"Add area",()->L10n.tr(cancelSelection!=null?"Cancel area selection":"Add area"),()->select(false)));
        selectionHint=add(new Widget(UI.scale(WIDTH,FieldIcon.SIDE)));selectionHint.hide();
        selectionHint.settip("Stand inside the collection area, then drag its boundaries on the map.");
        selectionTitle=selectionHint.add(new Label(""),UI.scale(4,3));
        selectionHint.add(new FieldIcon(FieldIcon.Glyph.CLOSE,"Cancel area selection",()->L10n.tr("Cancel area selection"),this::cancelSelection),Coord.of(UI.scale(WIDTH-FieldIcon.SIDE),0));
        list=add(new SListBox<SavedArea,Widget>(UI.scale(WIDTH,ROW*2),UI.scale(ROW)){
            protected java.util.List<SavedArea> items(){return pens.areas;}
            public void change(SavedArea area){if(cancelSelection==null){super.change(area);choose(area);}}
            protected void drawbg(GOut g,SavedArea area,int index,Area at){
                g.chcolor(UiTheme.light()?UiTheme.PANEL:index%2==0?new Color(30,27,22):new Color(38,34,27));g.frect2(at.ul,at.br);g.chcolor();
            }
            protected void drawsel(GOut g,SavedArea area,int index,Area at){
                g.chcolor(UiTheme.light()?new Color(116,91,43,35):new Color(53,46,32));g.frect2(at.ul,at.br);
                g.chcolor(174,143,76,190);g.frect(at.ul,Coord.of(UI.scale(2),at.sz().y));g.chcolor();
            }
            protected Widget makeitem(SavedArea area,int index,Coord size){return new AreaRow(this,size,area);}
        });
        empty=add(new Label("No collection areas saved yet."),UI.scale(8,8));
        pin=add(new FieldIcon(FieldIcon.Glyph.PIN));
        where=add(new Label(""));
        collect=add(new Button(UI.scale(130),"Collect here",false,this::collectHere));
        refresh();
    }
    private static String fit(String text,int width){
        if(Text.std.strsize(text).x<=width)return text;
        int end=text.length();
        while(end>0&&Text.std.strsize(text.substring(0,end)+"…").x>width)end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+"…";
    }
    private static Color gray(){return UiTheme.light()?UiTheme.HEADER:new Color(160,153,131);}
    private final class AreaRow extends SListWidget.ItemWidget<SavedArea> {
        private final Label title,dimensions;
        private final TextEntry name;
        private final FieldIcon marker,collectHere,edit,bounds,delete;
        private final int textLeft,iconsLeft;
        private String controls="";
        private boolean editing;
        private int rowTheme=-1;
        AreaRow(SListWidget<SavedArea,?> owner,Coord size,SavedArea area){
            super(owner,size,area);
            int side=UI.scale(FieldIcon.SIDE),gap=UI.scale(5),y=(size.y-side)/2,right=size.x-UI.scale(8);
            delete=add(new FieldIcon(FieldIcon.Glyph.TRASH,"Delete area",()->L10n.tr("Delete area"),()->deleteArea(area)),Coord.of(right-side,y));
            bounds=add(new FieldIcon(FieldIcon.Glyph.ROUTE,"Change boundaries",()->{
                String tip=L10n.tr("Change boundaries");
                return MilkPens.same(area,current)?tip:tip+"\n"+L10n.tr("Stand inside the collection area, then drag its boundaries on the map.");
            },()->{if(cancelSelection==null&&MilkPens.same(area,current)){list.change(area);select(true);}}),Coord.of(delete.c.x-gap-side,y));
            edit=add(new FieldIcon(FieldIcon.Glyph.EDIT,"Edit",()->L10n.tr(renaming==area?"Save name":"Change the area name"),()->{
                if(cancelSelection!=null)return;
                if(renaming==area)saveName(area,AreaRow.this.name.text());else startRename(area);
            }),Coord.of(bounds.c.x-gap-side,y));
            collectHere=add(new FieldIcon(FieldIcon.Glyph.PLAY,"Collect milk here",()->{
                String tip=L10n.tr("Collect milk here");
                return MilkPens.same(area,current)?tip:tip+"\n"+L10n.tr("Collection uses the area containing your character.");
            },()->{if(MilkPens.same(area,current))collectHere();}),Coord.of(edit.c.x-gap-side,y));
            iconsLeft=collectHere.c.x-UI.scale(8);textLeft=UI.scale(34);
            marker=add(new FieldIcon(FieldIcon.Glyph.PIN),Coord.of(UI.scale(8),y));
            title=add(new Label(""),Coord.of(textLeft,0));
            dimensions=add(new Label(item.width+"×"+item.height),Coord.z);
            name=add(new TextEntry(UI.scale(150),""){
                public void activate(String text){saveName(area,text);}
                public boolean keydown(KeyDownEvent event){if(event.code==KeyEvent.VK_ESCAPE){cancelRename();return true;}return super.keydown(event);}
            },Coord.of(textLeft,(size.y-UI.scale(24))/2));
            name.canactivate=true;name.hide();
            update();
        }
        private void update(){
            boolean here=MilkPens.same(item,current),renamingThis=renaming==item;
            if(renamingThis!=editing){
                editing=renamingThis;
                name.show(editing);title.show(!editing);edit.glyph(editing?FieldIcon.Glyph.CHECK:FieldIcon.Glyph.EDIT);
                if(editing){name.settext(pens.name(item));name.setcanfocus(true);if(parent!=null)MilkSettings.this.setfocus(name);}
                else name.setcanfocus(false);
            }
            marker.show(here);
            int dimsWidth=dimensions.sz.x;
            String full=pens.name(item),shortened=fit(full,Math.max(UI.scale(40),iconsLeft-textLeft-dimsWidth-UI.scale(16)));
            if(!shortened.equals(title.texts))title.settext(shortened);
            title.settip(full);title.move(Coord.of(textLeft,(sz.y-title.sz.y)/2));
            int after=editing?name.c.x+name.sz.x:title.c.x+title.sz.x;
            dimensions.move(Coord.of(Math.min(after+UI.scale(8),iconsLeft-dimsWidth),(sz.y-dimensions.sz.y)/2));
            if(rowTheme!=UiTheme.generation()){
                rowTheme=UiTheme.generation();
                dimensions.setcolor(gray());title.setcolor(UiTheme.light()?UiTheme.INK:new Color(232,220,190));
            }
            String next=here+"|"+ScriptRunner.running(gui)+"|"+(cancelSelection!=null)+"|"+editing;
            if(next.equals(controls))return;controls=next;
            boolean blocked=cancelSelection!=null||ScriptRunner.running(gui);
            collectHere.disable(!here||blocked);bounds.disable(!here||blocked);
            edit.disable(cancelSelection!=null);delete.disable(blocked);
        }
        public void tick(double dt){super.tick(dt);update();}
    }
    MilkPens pens(){return pens;}
    private void startRename(SavedArea area){
        if(cancelSelection!=null)return;
        list.change(area);renaming=area;lastControls="";updateControls();
    }
    private void cancelRename(){if(renaming!=null){renaming=null;lastControls="";updateControls();}}
    private void saveName(SavedArea area,String name){
        if(cancelSelection!=null)return;
        try{MilkPens.rename(gui,area,name);renaming=null;refresh();}
        catch(IllegalStateException error){gui.error(L10n.tr(error.getMessage()));}
    }
    private void deleteArea(SavedArea area){
        if(cancelSelection!=null||ScriptRunner.running(gui))return;
        try{
            MilkPens.delete(gui,area);
            if(renaming!=null&&MilkPens.same(renaming,area))renaming=null;
            if(MilkPens.same(selected,area))selected=null;
            refresh();
        }catch(IllegalStateException error){gui.error(L10n.tr(error.getMessage()));}
    }
    private void collectHere(){
        if(cancelSelection!=null)return;
        if(ScriptRunner.running(gui)){gui.error(L10n.tr("Stop the current script first."));return;}
        reqclose();ScriptRunner.run(gui,"milk");
    }
    private void choose(SavedArea area){
        if(renaming!=null&&!MilkPens.same(renaming,area))renaming=null;
        selected=area;list.sel=area;highlight.show(area);lastControls="";updateControls();
    }
    private void refresh(){
        pens=MilkPens.load(gui);current=pens.current(gui);
        SavedArea keep=pens.find(selected);
        choose(keep!=null?keep:current!=null?current:pens.areas.isEmpty()?null:pens.areas.get(0));
        if(renaming!=null)renaming=pens.find(renaming);
        list.update();
        updateCurrent();
    }
    private void updateCurrent(){
        if(cancelSelection!=null)return;
        current=pens.current(gui);
        updateControls();
    }
    private void updateControls(){
        if(list==null||collect==null)return;
        boolean selecting=cancelSelection!=null,busy=ScriptRunner.running(gui);
        String key=selecting+"|"+busy+"|"+pens.areas.size()+"|"+UiTheme.generation()+"|"+(current==null?"":pens.name(current))+"|"+(renaming!=null);
        if(key.equals(lastControls))return;
        lastControls=key;
        String text=L10n.format("Areas: %d · collection runs where you stand",pens.areas.size());
        status.settext(fit(text,UI.scale(WIDTH)-add.sz.x-UI.scale(10)));status.setcolor(gray());status.settip(text);
        add.disable(busy&&!selecting);
        selectionHint.show(selecting);
        if(selecting)selectionTitle.settext(L10n.tr("Waiting for the collection area"));
        selectionTitle.setcolor(UiTheme.entryText(false));
        String here=current==null?L10n.tr("You are outside saved areas"):L10n.format("You are in the area “%s”",pens.name(current));
        int room=UI.scale(WIDTH)-pin.sz.x-collect.sz.x-UI.scale(24);
        where.settext(fit(here,room));where.setcolor(gray());where.settip(here);
        pin.show(current!=null);
        collect.disable(selecting||busy);
        layoutControls();
    }
    private void layoutControls(){
        int gap=UI.scale(6);
        int top=Math.max(status.sz.y,add.sz.y);
        status.move(Coord.of(0,(top-status.sz.y)/2));add.move(Coord.of(UI.scale(WIDTH)-add.sz.x,(top-add.sz.y)/2));
        int y=top+gap;
        if(selectionHint.visible()){selectionHint.move(Coord.of(0,y));y+=selectionHint.sz.y+gap;}
        int rows=Math.max(1,Math.min(MAX_ROWS,pens.areas.size()));
        Coord listSize=Coord.of(UI.scale(WIDTH),UI.scale(rows*ROW));if(!list.sz.equals(listSize))list.resize(listSize);
        list.move(Coord.of(0,y));empty.move(Coord.of(UI.scale(8),y+UI.scale(8)));empty.show(pens.areas.isEmpty());
        y+=listSize.y+gap;
        int foot=Math.max(collect.sz.y,pin.sz.y);
        collect.move(Coord.of(UI.scale(WIDTH)-collect.sz.x,y+(foot-collect.sz.y)/2));
        pin.move(Coord.of(0,y+(foot-pin.sz.y)/2));
        where.move(Coord.of(pin.visible()?pin.sz.x+UI.scale(4):0,y+(foot-where.sz.y)/2));
        if(!contentsz().equals(csz())){
            pack();
            if(parent!=null)move(c.min(parent.sz.sub(hearthborn.ui.HudInterfaceScale.size(this)).max(Coord.z)).max(Coord.z));
        }
    }
    protected void added(){super.added();layoutControls();}
    private void select(boolean edit){
        if(cancelSelection!=null){if(!edit)cancelSelection.run();return;}
        if(gui.map==null)return;
        if(ScriptRunner.running(gui)){gui.error(L10n.tr("Stop the current script first."));return;}
        try{
            updateCurrent();
            if(edit&&!MilkPens.same(selected,current))return;
            SavedArea replace=edit?selected:null;
            renaming=null;
            cancelSelection=gui.map.selectLocalArea(area->{
                cancelSelection=null;
                try{MilkPens.save(gui,area,replace);selected=null;refresh();}
                catch(Loading pending){gui.error(L10n.tr("The selected area is still loading. Select it again when the map is ready."));}
                catch(IllegalStateException error){gui.error(L10n.tr(error.getMessage()));}
                selectionFinished();
            },()->{cancelSelection=null;selectionFinished();});
            gui.showNotice(L10n.tr("Stand inside the collection area, then drag its boundaries on the map."),java.awt.Color.WHITE);
            highlight.suspend(true);
            lastControls="";updateControls();
        }catch(IllegalStateException error){gui.error(L10n.tr(error.getMessage()));}
    }
    private void selectionFinished(){highlight.suspend(false);lastControls="";updateCurrent();}
    private void cancelSelection(){if(cancelSelection!=null){cancelSelection.run();cancelSelection=null;}}
    public void tick(double dt){super.tick(dt);highlight.tick();elapsed+=dt;if(elapsed>=.5){elapsed=0;try{updateCurrent();}catch(IllegalStateException failure){gui.error(L10n.tr(failure.getMessage()));}}}
    public void reqclose(){cancelSelection();highlight.clear();ui.destroy(this);}
    public void dispose(){cancelSelection();highlight.clear();super.dispose();}
}
