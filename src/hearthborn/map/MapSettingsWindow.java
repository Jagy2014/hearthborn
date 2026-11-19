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

package hearthborn.map;

import haven.*;
import haven.iosys.tk.FilePicker;
import hearthborn.L10n;
import hearthborn.ui.IconCategories;
import hearthborn.ui.IconCategoryPanel;
import java.awt.Color;
import java.util.*;

/**
 * Shared object/tile browser; native icon notifications and map rendering stay independent.
 *
 * @author Jagy2014
 */
public final class MapSettingsWindow extends Window {
    private final MapFilterModel model;
    private final TextEntry search;
    private final GroupList groups;
    private final EntryList list;
    private final Widget details;
    private final ACheckBox selectAll,discoveries,markers,markerNames;
    private final Label count;
    private final Map<String,String> categories=MapFilterModel.categories();
    private java.util.List<MapFilterModel.Entry> shown=Collections.emptyList();
    private String category="all";

    public MapSettingsWindow(GobIcon.Settings settings){
        super(Coord.z,L10n.text("window","Map display settings"));
        setfocustab(true);
        model=new MapFilterModel(settings);
        search=add(new TextEntry(UI.scale(420),""){
            protected void changed(){super.changed();if(list!=null)refill();}
        },Coord.z);
        search.settip("Search objects and tiles");
        selectAll=add(new CheckBox("").state(this::allShown).set(on->model.show(shown,on)),Coord.z);
        selectAll.settip("Select all rows in the open category. Search narrows those rows. Other categories stay as they are.");
        count=add(new Label(""),Coord.z);
        groups=add(new GroupList(),Coord.z);groups.sel="all";
        list=add(new EntryList(),Coord.z);
        details=add(new Widget(Coord.z),Coord.z);
        markers=add(new CheckBox("Show markers").state(()->{MapWnd map=map();return map==null||map.markersShown();}).set(on->{MapWnd map=map();if(map!=null)map.markersShown(on);}),Coord.z);
        markerNames=add(new CheckBox("Show marker names").state(()->MapDisplay.enabled("marker-names")).set(on->MapDisplay.enabled("marker-names",on)),Coord.z);
        discoveries=add(new CheckBox("Notification on newly seen icons").state(()->settings.notify).set(on->{settings.notify=on;settings.dsave();}),Coord.z);
        discoveries.settip(L10n.format("Reports previously unknown icon types in System chat and writes their discovery time, name and resource to:\n%s\nKnown icons appearing nearby are not logged. Use the Notify checkbox beside an icon for nearby alerts.",hearthborn.io.IconDiscoveryLog.path()),true);
        discoveries.show(hearthborn.io.IconDiscoveryLog.enabled());
        resize(UI.scale(660,520));refill();
    }

    protected void added(){
        super.added();
        resize(Coord.of(Math.min(UI.scale(660),Math.max(UI.scale(560),ui.root.sz.x-UI.scale(80))),
            Math.min(UI.scale(520),Math.max(UI.scale(380),ui.root.sz.y-UI.scale(120)))));
        setfocus(search);
    }
    public void resize(Coord size){
        super.resize(size.max(UI.scale(560,380)));
        if(list==null)return;
        Coord area=csz();int gap=UI.scale(8),left=UI.scale(180),x=left+gap,width=area.x-x;
        search.move(Coord.of(x,0));search.resize(Math.max(UI.scale(120),width-count.sz.x-gap));
        count.move(Coord.of(area.x-count.sz.x,(search.sz.y-count.sz.y)/2));
        int y=search.sz.y+gap;
        int footer=markers.sz.y+markerNames.sz.y+discoveries.sz.y+gap*3,detailHeight=UI.scale(104),header=selectAll.sz.y+UI.scale(2);
        int height=Math.max(UI.scale(80),area.y-y-header-detailHeight-footer-gap);
        list.move(Coord.of(x,y+header));list.resize(Coord.of(width,height));
        placeSelectAll();
        groups.move(Coord.z);groups.resize(Coord.of(left,area.y-footer));
        details.move(Coord.of(x,y+header+height+gap));details.resize(Coord.of(width,detailHeight));
        int foot=area.y-discoveries.sz.y;
        discoveries.move(Coord.of(0,foot));
        foot-=gap+markerNames.sz.y;markerNames.move(Coord.of(0,foot));
        foot-=gap+markers.sz.y;markers.move(Coord.of(0,foot));
        showDetails(list.sel);
    }
    private MapWnd map(){GameUI gui=getparent(GameUI.class);return gui==null?null:gui.mapfile;}
    private void playerSound(hearthborn.world.PlayerRelations.Kind kind){
        Label name=new Label(L10n.tr(kind.title));
        details.add(name,Coord.z);
        Label caption=new Label(L10n.tr("Player warning:"));
        details.add(caption,name.pos("bl").adds(0,5));
        Button play=new Button(UI.scale(50),"Play"){
            protected void depress(){}protected void unpress(){}
            public void click(){hearthborn.world.PlayerWarnings.preview(ui,kind);}
        };
        int width=Math.max(UI.scale(80),details.sz.x-play.sz.x-UI.scale(15));
        SDropBox<hearthborn.world.PlayerWarnings.Voice,Widget> voices=new SDropBox<hearthborn.world.PlayerWarnings.Voice,Widget>(width,UI.scale(70),UI.scale(20)){
            protected java.util.List<hearthborn.world.PlayerWarnings.Voice> items(){return java.util.Arrays.asList(hearthborn.world.PlayerWarnings.Voice.values());}
            protected Widget makeitem(hearthborn.world.PlayerWarnings.Voice item,int index,Coord size){
                return SListWidget.TextItem.of(size,Text.std,()->item==hearthborn.world.PlayerWarnings.Voice.FILE&&hearthborn.world.PlayerWarnings.customName(kind)!=null?hearthborn.world.PlayerWarnings.customName(kind):L10n.tr(item.title));
            }
            public void change(hearthborn.world.PlayerWarnings.Voice item){
                hearthborn.world.PlayerWarnings.Voice previous=sel;
                boolean replace=item==hearthborn.world.PlayerWarnings.Voice.FILE&&(hearthborn.world.PlayerWarnings.customFile(kind)==null||(ui!=null&&ui.modshift));
                super.change(item);
                if(replace){choosePlayerFile(kind,previous==null?hearthborn.world.PlayerWarnings.voice(kind):previous,this);return;}
                if(item!=null)hearthborn.world.PlayerWarnings.voice(kind,item);
            }
        };
        voices.change(hearthborn.world.PlayerWarnings.voice(kind));
        voices.settip(L10n.tr("Russian and English are the spoken warnings. Custom sound uses your own WAV file; Shift-click that choice to replace it."));
        details.addhl(caption.pos("bl").adds(0,2),details.sz.x,Frame.with(voices,false),play);
    }
    private void choosePlayerFile(hearthborn.world.PlayerRelations.Kind kind,hearthborn.world.PlayerWarnings.Voice revert,SDropBox<hearthborn.world.PlayerWarnings.Voice,Widget> voices){
        if(ui==null||ui.wnd==null){if(revert!=null)voices.change(revert);return;}
        FilePicker dialog=ui.wnd.toolkit().picker().make(FilePicker.Mode.OPEN,ui.wnd);
        dialog.filter("PCM wave file","wav");
        dialog.show().consume(path->{
            if(path==null){if(revert!=null)voices.change(revert);return;}
            hearthborn.world.PlayerWarnings.customFile(kind,path);
            hearthborn.world.PlayerWarnings.Voice bounce=voices.sel==hearthborn.world.PlayerWarnings.Voice.FILE?hearthborn.world.PlayerWarnings.Voice.RU:voices.sel;
            voices.change(bounce);
            voices.change(hearthborn.world.PlayerWarnings.Voice.FILE);
        }).report(ui);
    }
    /** Header box sits on the same column as each row's display checkbox. */
    private void placeSelectAll(){
        int itemw=list.sz.x-(shown.size()*list.itemh>list.sz.y?Scrollbar.width:0);
        int center=list.c.x+itemw-UI.scale(24)-UI.scale(28);
        selectAll.move(Coord.of(center-selectAll.sz.x/2,list.c.y-selectAll.sz.y-UI.scale(2)));
        count.move(Coord.of(csz().x-count.sz.x,search.c.y+(search.sz.y-count.sz.y)/2));
        search.resize(Math.max(UI.scale(120),list.sz.x-count.sz.x-UI.scale(8)));
    }
    private String categoryTitle(String group){
        String english=categories.get(group);
        return "all".equals(group)?L10n.text("window",english):L10n.tr(english);
    }
    private boolean allShown(){
        if(shown.isEmpty())return false;
        for(MapFilterModel.Entry entry:shown)if(!entry.shown())return false;
        return true;
    }
    private void refill(){refill(true);}
    private void refill(boolean resetScroll){
        shown=model.filter(category,MapFilterModel.Type.ALL,search.buf.line());
        count.settext(Integer.toString(shown.size()));
        if(list!=null)placeSelectAll();
        if(!shown.contains(list.sel))list.change(null);
        if(resetScroll)list.scrollval(0);
    }
    private void showDetails(MapFilterModel.Entry entry){
        for(Widget child:new ArrayList<>(details.children()))child.destroy();
        if(entry==null)return;
        if(entry.setting!=null)details.add(new GobIcon.IconSettings(model.settings,details.sz.x,entry.setting),Coord.z);
        else if(entry.playerKind!=null)playerSound(entry.playerKind);
        else {
            details.add(new Label(entry.name,details.sz.x),Coord.z);
            details.add(new Label("A checked tile is highlighted on the map.",details.sz.x),UI.scale(0,24));
        }
    }
    public void tick(double dt){
        if(model.refresh())refill(false);
        super.tick(dt);
    }

    private final class GroupList extends SListBox<String,Widget> {
        GroupList(){super(UI.scale(180,360),UI.scale(28));}
        protected java.util.List<String> items(){return new ArrayList<>(categories.keySet());}
        protected Widget makeitem(String group,int index,Coord size){
            if(MapFilterModel.section(group))return new SListWidget.ItemWidget<String>(this,size,group){
                { Label title=new Label(categoryTitle(group),CharWnd.attrf);title.setcolor(new Color(220,190,110));
                  add(title,Coord.of(UI.scale(4),(size.y-title.sz.y)/2)); }
            };
            return new SListWidget.ItemWidget<String>(this,size,group){
                { IconCategories.Category icon=null;
                  for(IconCategories.Category value:IconCategories.Category.values())if(value.id.equals(group))icon=value;
                  if(icon!=null)add(new Img(IconCategoryPanel.image(icon)),UI.scale(2,4));
                  add(SListWidget.TextItem.of(size.sub(UI.scale(27),0),CharWnd.attrf,()->categoryTitle(group)),UI.scale(27,0));
                }
            };
        }
        public void change(String value){if(value==null||MapFilterModel.section(value))return;super.change(value);category=value;refill();}
    }
    private final class EntryList extends SListBox<MapFilterModel.Entry,Widget> {
        EntryList(){super(UI.scale(420,250),UI.scale(28));setcanfocus(true);}
        protected java.util.List<MapFilterModel.Entry> items(){return shown;}
        protected Widget makeitem(MapFilterModel.Entry entry,int index,Coord size){return new EntryRow(this,entry,size);}
        public void change(MapFilterModel.Entry entry){super.change(entry);showDetails(entry);}
        public boolean mousedown(MouseDownEvent event){parent.setfocus(this);return super.mousedown(event);}
        public boolean keydown(KeyDownEvent event){
            if(event.code==java.awt.event.KeyEvent.VK_SPACE){if(sel!=null)sel.shown(!sel.shown());return true;}
            if(event.code==java.awt.event.KeyEvent.VK_UP||event.code==java.awt.event.KeyEvent.VK_DOWN){
                if(shown.isEmpty())return true;
                int index=shown.indexOf(sel),direction=event.code==java.awt.event.KeyEvent.VK_UP?-1:1;
                index=index<0?0:Math.max(0,Math.min(shown.size()-1,index+direction));change(shown.get(index));display(index);return true;
            }
            return super.keydown(event);
        }
    }
    private final class EntryRow extends SListWidget.ItemWidget<MapFilterModel.Entry> {
        EntryRow(EntryList list,MapFilterModel.Entry entry,Coord size){
            super(list,size,entry);
            int right=size.x-UI.scale(24);
            int show=right-UI.scale(28);
            boolean warning=entry.setting!=null&&!entry.corpse()&&hearthborn.world.GobMarks.choosesWarning(entry.resource);
            adda(new CheckBox("").state(entry::shown).set(entry::shown).settip("Display"),show,size.y/2,.5,.5);
            if(entry.playerKind!=null)adda(new CheckBox(""){
                public Object tooltip(Coord c,Widget prev){settip(hearthborn.world.PlayerWarnings.choiceName(entry.playerKind));return super.tooltip(c,prev);}
            }.state(()->hearthborn.world.PlayerWarnings.heard(entry.playerKind)).set(on->hearthborn.world.PlayerWarnings.heard(entry.playerKind,on)),right,size.y/2,.5,.5);
            else if(entry.soundEditable())adda(new CheckBox("").state(()->entry.setting.notify).set(on->{entry.setting.notify=on;model.settings.dsave();}).settip("Play sound when this icon appears"),right,size.y/2,.5,.5);
            else if(warning)adda(new CheckBox("").state(()->hearthborn.world.GobMarks.warned(entry.resource)).set(on->hearthborn.world.GobMarks.warned(entry.resource,on)).settip("Play a warning when this animal appears"),right,size.y/2,.5,.5);
            else if(entry.setting!=null)adda(new Label("–").settip(L10n.tr("Uses the animal warning sound.")),right,size.y/2,.5,.5);
            Label kind=new Label(entry.playerKind!=null?"Player":entry.setting==null?"Tile":"Object");kind.setcolor(new Color(190,179,145));
            adda(kind,right-UI.scale(50),size.y/2,1,.5);
            add(SListWidget.IconText.of(Coord.of(Math.max(UI.scale(40),right-UI.scale(110)),size.y),entry::image,()->entry.name),Coord.z);
            settip(entry.name);
        }
    }
}
