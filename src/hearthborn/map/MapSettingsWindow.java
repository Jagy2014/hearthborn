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
import hearthborn.journal.GameJournal;
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
    private static final Text.Foundry LIST_FONT = new Text.Foundry(Text.sans, 13).aa(true);
    private static final int GROUP_WIDTH = UI.scale(196);
    private static final int ROW_HEIGHT = UI.scale(24);
    private final MapFilterModel model;
    private final TextEntry search;
    private final IButton clearSearch;
    private final GroupList groups;
    private final EntryList list;
    private final Widget details;
    private final Button journalButton;
    private GameJournal.Lookup journal;
    private final ACheckBox selectAll,discoveries,markers,markerNames,hideNamesZoomedOut;
    private final Label count;
    private final Map<String,String> categories=MapFilterModel.categories();
    private java.util.List<MapFilterModel.Entry> shown=Collections.emptyList();
    private String category="all";

    public MapSettingsWindow(GobIcon.Settings settings){
        super(Coord.z,L10n.text("window","Map display settings"));
        setfocustab(true);
        model=new MapFilterModel(settings);
        search=add(new TextEntry(UI.scale(420),""){
            protected void changed(){super.changed();clearSearch.show(!text().isEmpty());if(list!=null)refill();}
        },Coord.z);
        clearSearch=add(hearthborn.ui.SmallWindowDeco.closeButton(()->{
            search.settext("");
            setfocus(search);
        }));
        clearSearch.settip("Clear search");
        clearSearch.hide();
        search.settip("Search objects and tiles");
        selectAll=add(new CheckBox("").state(this::allShown).set(on->model.show(shown,on)),Coord.z);
        selectAll.settip("Select all rows in the open category. Search narrows those rows. Other categories stay as they are.");
        count=add(new Label(""),Coord.z);
        groups=add(new GroupList(),Coord.z);groups.sel="all";
        list=add(new EntryList(),Coord.z);
        details=add(new Widget(Coord.z),Coord.z);
        journalButton=add(new Button(UI.scale(162),"Open in Game Journal",this::openJournal));
        journalButton.hide();
        markers=add(new CheckBox("Show markers").state(()->{MapWnd map=map();return map==null||map.markersShown();}).set(on->{MapWnd map=map();if(map!=null)map.markersShown(on);}),Coord.z);
        markerNames=add(new CheckBox("Show marker names").state(()->MapDisplay.enabled("marker-names")).set(on->MapDisplay.enabled("marker-names",on)),Coord.z);
        hideNamesZoomedOut=add(new CheckBox("Hide names when zoomed out").state(()->MapDisplay.enabled(MapDisplay.HIDE_NAMES_ZOOMED_OUT))
            .set(on->MapDisplay.enabled(MapDisplay.HIDE_NAMES_ZOOMED_OUT,on)),Coord.z);
        hideNamesZoomedOut.settip("When the map is zoomed far out, hide marker names but keep their icons visible.");
        discoveries=add(new CheckBox("Notification on newly seen icons").state(()->settings.notify).set(on->{settings.notify=on;settings.dsave();}),Coord.z);
        discoveries.settip(L10n.format("Reports previously unknown icon types in System chat and writes their discovery time, name and resource to:\n%s\nKnown icons appearing nearby are not logged. Use the Notify checkbox beside an icon for nearby alerts.",hearthborn.io.IconDiscoveryLog.path()),true);
        discoveries.show(hearthborn.io.IconDiscoveryLog.enabled());
        resize(UI.scale(660,520));refill();
    }

    protected void added(){
        super.added();
        int wantedHeight=Math.max(UI.scale(520),groups.totalh()+footerHeight());
        int room=ui.root.sz.y-(sz.y-csz().y)-UI.scale(40);
        resize(Coord.of(Math.min(UI.scale(660),Math.max(UI.scale(560),ui.root.sz.x-UI.scale(80))),
            Math.min(wantedHeight,room)));
        setfocus(search);
    }
    private int footerHeight(){
        int gap=UI.scale(8);
        return markers.sz.y+markerNames.sz.y+hideNamesZoomedOut.sz.y+gap*3+(discoveries.visible?discoveries.sz.y+gap:0);
    }
    public void resize(Coord size){
        super.resize(size.max(UI.scale(560,380)));
        if(list==null)return;
        Coord area=csz();
        int gap=UI.scale(8);
        groups.resize(Coord.of(GROUP_WIDTH,area.y-footerHeight()));
        int foot=area.y-discoveries.sz.y;
        discoveries.move(Coord.of(0,foot));
        if(!discoveries.visible)foot=area.y+gap;
        foot-=gap+hideNamesZoomedOut.sz.y;hideNamesZoomedOut.move(Coord.of(0,foot));
        foot-=gap+markerNames.sz.y;markerNames.move(Coord.of(0,foot));
        foot-=gap+markers.sz.y;markers.move(Coord.of(0,foot));
        details.resize(Coord.of(area.x-GROUP_WIDTH-gap,0));
        showDetails(list.sel);
    }
    private void layoutResults(){
        int gap=UI.scale(8);
        int x=GROUP_WIDTH+gap,width=details.sz.x;
        search.move(Coord.of(x,0));
        int y=search.sz.y+gap+selectAll.sz.y+UI.scale(2);
        int detailHeight=details.contentsz().y;
        int foot=csz().y;
        if(journalButton.visible()) {
            journalButton.move(Coord.of(csz().x-journalButton.sz.x,foot-journalButton.sz.y));
            foot=journalButton.c.y-gap;
        }
        int detailTop=foot-detailHeight;
        int bottom=detailHeight>0?detailTop-gap:detailTop;
        list.move(Coord.of(x,y));list.resize(Coord.of(width,Math.max(1,bottom-y)));
        details.move(Coord.of(x,detailTop));details.resize(Coord.of(width,detailHeight));
        placeSelectAll();
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
        int gap=UI.scale(4);
        int clearX=count.c.x-gap-clearSearch.sz.x;
        clearSearch.move(Coord.of(clearX,search.c.y+Math.max(0,(search.sz.y-clearSearch.sz.y)/2)));
        search.resize(clearX-search.c.x-gap);
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
        if(entry!=null && entry.setting!=null)details.add(new GobIcon.IconSettings(model.settings,details.sz.x,entry.setting),Coord.z);
        else if(entry!=null && entry.playerKind!=null)playerSound(entry.playerKind);
        else if(entry!=null) {
            details.add(new Label(entry.name,details.sz.x),Coord.z);
            details.add(new Label("A checked tile is highlighted on the map.",details.sz.x),UI.scale(0,24));
        }
        refreshJournalButton();
        layoutResults();
    }
    private boolean refreshJournalButton(){
        MapFilterModel.Entry entry=list.sel;
        boolean available=false;
        if(hearthborn.io.DeveloperMode.gameJournal() && entry!=null && entry.playerKind==null && getparent(GameUI.class)!=null) {
            if(journal==null)journal=new GameJournal.Lookup();
            available=journal.entryId(entry.resource)!=null;
        }
        if(journalButton.visible()==available) return false;
        journalButton.show(available);
        return true;
    }
    private void openJournal(){
        if(journal!=null && list.sel!=null)
            journal.openResource(getparent(GameUI.class),list.sel.resource);
    }
    public void tick(double dt){
        if(model.refresh())refill(false);
        if(refreshJournalButton())layoutResults();
        super.tick(dt);
    }

    private final class GroupList extends SListBox<String,Widget> {
        GroupList(){super(Coord.of(GROUP_WIDTH,UI.scale(360)),ROW_HEIGHT);}
        protected java.util.List<String> items(){return new ArrayList<>(categories.keySet());}
        protected Widget makeitem(String group,int index,Coord size){
            if(MapFilterModel.section(group))return new SListWidget.ItemWidget<String>(this,size,group){
                { Label title=new Label(categoryTitle(group),LIST_FONT);title.setcolor(new Color(220,190,110));
                  add(title,Coord.of(UI.scale(4),(size.y-title.sz.y)/2)); }
            };
            return new SListWidget.ItemWidget<String>(this,size,group){
                { IconCategories.Category icon=null;
                  for(IconCategories.Category value:IconCategories.Category.values())if(value.id.equals(group))icon=value;
                  if(icon!=null){
                      Tex image=IconCategoryPanel.image(icon);
                      add(new Img(image),Coord.of(UI.scale(2),(size.y-image.sz().y)/2));
                  }
                  add(SListWidget.TextItem.of(size.sub(UI.scale(27),0),LIST_FONT,()->categoryTitle(group)),UI.scale(27,0));
                  settip(categoryTitle(group));
                }
            };
        }
        public void change(String value){if(value==null||MapFilterModel.section(value))return;super.change(value);category=value;refill();}
    }
    private final class EntryList extends SListBox<MapFilterModel.Entry,Widget> {
        EntryList(){super(UI.scale(420,250),ROW_HEIGHT);setcanfocus(true);}
        protected java.util.List<MapFilterModel.Entry> items(){return shown;}
        protected Widget makeitem(MapFilterModel.Entry entry,int index,Coord size){return new EntryRow(this,entry,size);}
        public void change(MapFilterModel.Entry entry){super.change(entry);showDetails(entry);}
        public boolean mousedown(MouseDownEvent event){parent.setfocus(this);return super.mousedown(event);}
        public boolean keydown(KeyDownEvent event){
            if(event.code==java.awt.event.KeyEvent.VK_SPACE){if(sel!=null&&!sel.questSearch())sel.shown(!sel.shown());return true;}
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
            adda(new CheckBox("").state(entry::displayChecked).set(on->{if(!entry.questSearch())entry.shown(on);}).settip("Display"),show,size.y/2,.5,.5);
            if(entry.playerKind!=null)adda(new CheckBox(""){
                public Object tooltip(Coord c,Widget prev){return L10n.tr(hearthborn.world.PlayerWarnings.choiceName(entry.playerKind));}
            }.state(()->hearthborn.world.PlayerWarnings.heard(entry.playerKind)).set(on->hearthborn.world.PlayerWarnings.heard(entry.playerKind,on)),right,size.y/2,.5,.5);
            else if(entry.soundEditable())adda(new CheckBox("").state(entry::soundChecked).set(on->{if(!entry.questSearch()){entry.setting.notify=on;model.settings.dsave();}}).settip("Play sound when this icon appears"),right,size.y/2,.5,.5);
            else if(warning)adda(new CheckBox("").state(()->hearthborn.world.GobMarks.warned(entry.resource)).set(on->hearthborn.world.GobMarks.warned(entry.resource,on)).settip("Play a warning when this animal appears"),right,size.y/2,.5,.5);
            else if(entry.setting!=null)adda(new Label("–").settip(L10n.tr("Uses the animal warning sound.")),right,size.y/2,.5,.5);
            Label kind=new Label(entry.playerKind!=null?"Player":entry.setting==null?"Tile":"Object");kind.setcolor(new Color(190,179,145));
            adda(kind,right-UI.scale(50),size.y/2,1,.5);
            Coord textSize=Coord.of(Math.max(UI.scale(40),right-UI.scale(110)),size.y);
            add(new SListWidget.IconText(textSize) {
                protected java.awt.image.BufferedImage img(){return entry.image();}
                protected String text(){return entry.name;}
                protected Text.Forge foundry(){return LIST_FONT;}
            },Coord.z);
            tooltip=entry.englishName;
        }
    }
}
