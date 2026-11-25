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
import hearthborn.L10n;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * The controls of the UI settings, built into any host: the standalone window or a pane of the options window. They come
 * in headed blocks (look, panels, menu, indicators, other). The blocks are a column of lines, and a line that does not
 * apply, such as the position of a row that is not horizontal, is hidden and the lines under it move up.
 *
 * @author Jagy2014
 */
final class UISettingsControls {
    private final Widget host;
    private final List<Line> lines=new ArrayList<>();
    private Runnable relaid=()->{};
    UISettingsControls(Widget host){this.host=host;}

    /** The width of the controls, and the columns of the blocks. */
    private static int width(){return UI.scale(350);}
    private static int control(){return UI.scale(140);}
    private static int dropWidth(){return UI.scale(190);}
    private static int fieldHeight(){return UI.scale(24);}

    /** One line of the column: a few widgets that sit at the same height and are shown or hidden together. */
    private static final class Line{
        final List<Widget> parts=new ArrayList<>();
        final List<Integer> offsets=new ArrayList<>();
        final int before;
        final BooleanSupplier shown;
        int height;
        Line(int before,BooleanSupplier shown){this.before=before;this.shown=shown;}
    }
    /** Called when a line was shown or hidden and the ones under it moved. */
    void relaid(Runnable action){relaid=action==null?()->{}:action;}

    private <W extends Widget> W put(W widget,int x,int y){return host.add(widget,Coord.of(x,y));}
    private Line line(int before,BooleanSupplier shown,Widget... parts){
        Line line=new Line(before,shown);
        for(Widget part:parts){line.parts.add(part);line.offsets.add(part.c.y);line.height=Math.max(line.height,part.c.y+part.sz.y);}
        lines.add(line);
        return line;
    }
    private Line line(Widget... parts){return line(UI.scale(5),()->true,parts);}
    private static final BooleanSupplier ALWAYS=()->true;

    /** Puts every line in its place; what is not shown takes no room. Returns the bottom edge. */
    int layout(){
        int y=0;boolean first=true;
        for(Line line:lines){
            boolean show=line.shown.getAsBoolean();
            for(int i=0;i<line.parts.size();i++){
                Widget part=line.parts.get(i);
                part.show(show);
                if(show)part.move(Coord.of(part.c.x,y+(first?0:line.before)+line.offsets.get(i)));
            }
            if(show){y+=(first?0:line.before)+line.height;first=false;}
        }
        return y;
    }
    private void relayout(){layout();relaid.run();}

    /** A small grey heading with a thin line under it, as the groups of the section list have. */
    private void section(String title,int before){
        Label heading=new Label(title,SettingsSidebar.HEADING_FACE).tier(UiPalette.NOTE);
        int under=heading.sz.y+UI.scale(3);
        Widget rule=new Widget(Coord.of(width(),Math.max(1,UI.scale(1)))){
            public boolean checkhit(Coord c){return false;}
            public void draw(GOut g){g.chcolor(UiPalette.border());g.frect(Coord.z,sz);g.chcolor();}
        };
        put(heading,0,0);put(rule,0,under);
        // The line under the heading is part of it; what follows keeps a small gap from it.
        Line line=line(before,ALWAYS,heading,rule);
        line.height=under+rule.sz.y+UI.scale(4);
    }

    private GameUI gui(){return host.getparent(GameUI.class);}
    private void relayoutHud(){GameUI gui=gui();if(gui!=null)gui.resize(gui.sz);}
    private void strip(){
        GameUI gui=gui();
        if(gui!=null){ActionStrip strip=gui.getchild(ActionStrip.class);if(strip!=null)strip.applySide();gui.resize(gui.sz);}
    }
    private Label caption(String text){return put(new Label(text).tier(UiPalette.CAPTION),0,UI.scale(4));}

    private <I> SDropBox<I,Widget> drop(List<? extends I> items,int listHeight,java.util.function.Function<I,String> names,java.util.function.Consumer<I> changed){
        return put(new SDropBox<I,Widget>(dropWidth(),listHeight,fieldHeight()){
            protected List<? extends I> items(){return items;}
            protected Widget makeitem(I value,int index,Coord size){
                Widget row=new Widget(size);row.add(new Label(names.apply(value)),UI.scale(4,4));return row;
            }
            public void change(I value){super.change(value);if(value!=null)changed.accept(value);}
        },control(),0);
    }

    /** Adds every control to the host and returns the last one, below which a caller may add more. */
    Widget build(){
        section("LANGUAGE",0);
        languageRow();

        // The look: one choice, four cards. It replaced a drop-down and a box that said the same thing twice.
        section("THEME",UI.scale(14));
        ThemeCards cards=put(new ThemeCards(UI.scale(80),UI.scale(10)),0,0);
        cards.settip("The look of the interface: the colour of the active entry, the focus, the main button, the tick, the slider and the window frame, on a dark or a parchment background. Applies at once.");
        line(UI.scale(8),ALWAYS,cards);
        Widget frames=put(new CheckBox("Hide gold HUD frames").state(HudFrames::hidden).set(HudFrames::hidden),0,0);
        frames.settip("Hides the decorative borders around the portrait, movement speed, minimap, chat and quest tracker without moving or undocking them.");
        line(frames);

        // The panels: a table, a row for each panel.
        section("PANELS",UI.scale(14));
        int showColumn=UI.scale(180),verticalColumn=UI.scale(270),slotsColumn=UI.scale(320);
        Label showHeader=put(new Label("Show",SettingsSidebar.HEADING_FACE).tier(UiPalette.NOTE),showColumn,0);
        Label verticalHeader=put(new Label("Vertical",SettingsSidebar.HEADING_FACE).tier(UiPalette.NOTE),verticalColumn,0);
        line(UI.scale(6),ALWAYS,showHeader,verticalHeader);
        panel("numeric","Numeric toolbar","Shows this row of the button strip.",showColumn,verticalColumn,slotsColumn);
        panel("function","F-key toolbar","Shows this row of the button strip.",showColumn,verticalColumn,slotsColumn);
        panel("extra","Additional toolbar 1","A separate toolbar with 10 slots.",showColumn,verticalColumn,slotsColumn);
        panel("extra2","Additional toolbar 2","A separate toolbar with 12 slots.",showColumn,verticalColumn,slotsColumn);
        Label equipment=caption("Equipment strip");
        Widget equipmentShow=put(new CheckBox("").state(()->Toolbars.enabled("equipment")).set(value->Toolbars.enabled("equipment",value)),showColumn,0);
        equipmentShow.settip("Shows the equipment strip.");
        Widget equipmentVertical=put(new CheckBox("").state(()->Toolbars.vertical("equipment")).set(value->Toolbars.vertical("equipment",value)),verticalColumn,0);
        equipmentVertical.settip("Turns the equipment strip upright.");
        Button equipmentSlots=put(new Button(UI.scale(22),"\u2026",()->EquipmentStripSettings.open(host.getparent(GameUI.class))),slotsColumn,0);
        equipmentSlots.settip("Equipment strip slots");
        line(equipment,equipmentShow,equipmentVertical,equipmentSlots);
        Label moving=put(new Label("To move a panel, drag its background, or hold Alt/Option and drag any icon. The equipment strip moves on its own.",UI.scale(340)).tier(UiPalette.NOTE),0,0);
        line(UI.scale(6),ALWAYS,moving);

        // The menu: its kind, its side, and what only some kinds have.
        section("MENU",UI.scale(14));
        Label kindName=caption("Menu type");
        Segmented kind=put(new Segmented(dropWidth()/2,fieldHeight(),new String[]{"Grid","Strip"},()->Toolbars.enabled("actions")?1:0,i->chooseMenu(i==1)),control(),0);
        kind.settip("The classic grid with the system buttons around it, or the strip of the action menu along a screen edge.");
        line(UI.scale(6),ALWAYS,kindName,kind);
        // The grid stands on the left or the right; the strip also has the bottom edge, where it lies as a row.
        Label sideName=caption("Side");
        Segmented side=put(new Segmented(dropWidth()/2,fieldHeight(),new String[]{"Left","Right"},
            ()->ActionMenuFrame.onLeft()?0:1,i->{ActionMenuFrame.onLeft(i==0);relayoutHud();}),control(),0);
        side.settip("Which edge of the screen the classic menu stands on.");
        line(UI.scale(6),()->!Toolbars.enabled("actions"),sideName,side);
        Label placeName=caption("Position");
        Segmented place=put(new Segmented(dropWidth()/3,fieldHeight(),new String[]{"Left","Bottom","Right"},
            ()->ActionStrip.horizontal()?1:ActionStrip.onRight()?2:0,i->{
                ActionStrip.horizontal(i==1);
                if(i!=1)ActionStrip.onRight(i==2);
                strip();relayout();
            }),control(),0);
        place.settip("Which edge of the screen the strip stands on. On the left and right it is a column that opens its menus away from the edge; at the bottom it is a row that opens them upward.");
        line(UI.scale(6),()->Toolbars.enabled("actions"),placeName,place);
        Label alignName=caption("Row position");
        SDropBox<ActionStrip.Align,Widget> rowAlign=drop(Arrays.asList(ActionStrip.Align.values()),UI.scale(72),value->value.label,value->{ActionStrip.align(value);strip();});
        rowAlign.change(ActionStrip.align());
        rowAlign.settip("Where the row sits along the bottom edge: left, in the middle or right.");
        line(UI.scale(6),()->Toolbars.enabled("actions")&&ActionStrip.horizontal(),alignName,rowAlign);
        // The Xtensions layout is a property of the classic grid; the strip has its own menus.
        Label extensionsName=caption("Xtensions menu");
        SDropBox<XtensionsMenu.ClassicLayout,Widget> layout=drop(Arrays.asList(XtensionsMenu.ClassicLayout.values()),UI.scale(90),value->value.label,value->{
            XtensionsMenu.classicLayout(value);
            GameUI gui=gui();if(gui!=null&&gui.menu!=null)gui.menu.change(gui.menu.cur);
        });
        layout.change(XtensionsMenu.classicLayout());
        layout.settip("Classic Xtensions: normal categories in 4×4, all commands upward, or all commands sideways toward the screen center.");
        line(UI.scale(6),()->!Toolbars.enabled("actions"),extensionsName,layout);
        Label systemName=caption("System menu");
        SDropBox<SystemCross.Layout,Widget> systemLayout=drop(SystemCross.sidebarLayouts(),UI.scale(100),value->value.label,value->{
            SystemCross.arrangement(value);
            GameUI gui=gui();
            if(gui!=null){SystemCross cross=gui.getchild(SystemCross.class);if(cross!=null)cross.relayout();gui.resize(gui.sz);}
        });
        if(SystemCross.sidebarLayouts().contains(SystemCross.arrangement()))systemLayout.change(SystemCross.arrangement());
        systemLayout.settip("System buttons of the strip: one strip, horizontal or vertical. The classic menu always has them around its 4×4 grid.");
        systemMenu=systemLayout;
        line(UI.scale(6),()->Toolbars.enabled("actions"),systemName,systemLayout);

        section("INDICATORS",UI.scale(14));
        Widget previous=null;
        for(String[] entry:new String[][]{{"fep","Show food event meter"},{"hunger","Show hunger meter"},{"drinks","Show water and tea supplies"},{"numbers","Show numeric meter values"}}){
            String id=entry[0];
            previous=put(new CheckBox(entry[1]).state(()->StatusMeters.enabled(id)).set(v->StatusMeters.enabled(id,v)),0,0);
            line(UI.scale(5),ALWAYS,previous);
        }
        previous=put(new CheckBox("Show FPS").state(ServerStats::fps).set(ServerStats::fps),0,0);
        previous.settip(hearthborn.L10n.tr("Shows the frame rate beside the clock."));line(previous);
        previous=put(new CheckBox("Show ping").state(ServerStats::ping).set(ServerStats::ping),0,0);
        previous.settip(hearthborn.L10n.tr("Shows the connection round-trip time under the FPS counter."));line(previous);

        section("OTHER",UI.scale(14));
        previous=put(new CheckBox("Portrait on the right").state(PortraitSide::onRight).set(value->{
            PortraitSide.onRight(value);GameUI gui=gui();if(gui!=null)gui.layoutHudSides();
        }),0,0);
        previous.settip("Puts the portrait, status bars and effect widgets on the right. Combat moves to the top-left, and the icons open toward the left.");
        line(UI.scale(5),ALWAYS,previous);
        previous=put(new CheckBox("Study slot reminder").state(StudyReminder::enabled).set(StudyReminder::enabled),0,0);line(previous);
        previous=put(new CheckBox("Always show the study widget").state(StudyReminder::always).set(StudyReminder::always),0,0);line(previous);
        previous=put(new CheckBox("Window magnets").state(MagneticWindows::enabled).set(MagneticWindows::enabled),0,0);
        previous.settip("Windows stick to each other and to the screen edges. Turn this off to move and resize them freely. Turning it on again sticks them back.");line(previous);
        previous=put(new CheckBox("Show system notices under the clock").state(NoticePlace::belowClock).set(NoticePlace::belowClock),0,0);
        previous.settip("System notices sit above the hotbar. Turn this on to place that line under the clock. In a fight it stays above the hotbar.");line(previous);
        sync();
        return previous;
    }

    private SDropBox<SystemCross.Layout,Widget> systemMenu;

    private void languageRow(){
        int selectorWidth=UI.scale(110);
        int selectorX=width()-selectorWidth;
        Label caption=put(new Label("Choose language:",selectorX-UI.scale(8)).tier(UiPalette.CAPTION),0,UI.scale(4));
        SDropBox<String,Widget> languages=new SDropBox<String,Widget>(selectorWidth,fieldHeight()*2,fieldHeight()){
            {super.change(Utils.getpref("hearthborn/language",L10n.language()));}
            protected boolean listautoscroll(){return false;}
            protected List<? extends String> items(){return List.of("en","ru");}
            protected Widget makeitem(String language,int index,Coord size){
                Widget row=new Widget(size);
                Label label=new Label(language.equals("ru")?"Russian":"English");
                row.add(label,Coord.of(UI.scale(4),(size.y-label.sz.y)/2));
                return row;
            }
            public void change(String language){
                super.change(language);
                L10n.setLanguage(language);
                if(!language.equals(L10n.language())&&ui!=null)
                    ui.msg(L10n.tr("The language applies after you close the client window completely and start it again (logging out to the character screen is not enough)."));
            }
        };
        put(languages,selectorX,0);
        line(caption,languages);
    }

    private void panel(String id,String title,String tip,int showColumn,int verticalColumn,int slotsColumn){
        Label name=caption(title);
        Widget show=put(new CheckBox("").state(()->Toolbars.enabled(id)).set(value->{
            Toolbars.enabled(id,value);relayoutHud();
        }),showColumn,0);
        show.settip(tip);
        Widget vertical=put(new CheckBox("").state(()->Toolbars.vertical(id)).set(value->{
            Toolbars.vertical(id,value);relayoutHud();
        }),verticalColumn,0);
        vertical.settip("Turns this button strip upright.");
        if(!Toolbars.extra(id)){
            line(name,show,vertical);
            return;
        }
        Button keys=put(new Button(UI.scale(22),"\u2026",()->{
            if(host instanceof UISettings)
                ((UISettings)host).openToolbarKeys(id);
            else
                host.getparent(OptWnd.class).openToolbarKeys(id);
        }),slotsColumn,0);
        keys.settip("Open toolbar keybindings");
        line(name,show,vertical,keys);
    }

    private void chooseMenu(boolean sidebar){
        Toolbars.enabled("actions",sidebar);
        SystemCross.menuChosen(sidebar);
        sync();
        GameUI gui=gui();
        if(gui!=null){SystemCross cross=gui.getchild(SystemCross.class);if(cross!=null)cross.relayout();gui.resize(gui.sz);}
    }

    /** The classic menu always has its buttons around the grid, so only the sidebar menu offers a choice. */
    private void sync(){
        if(systemMenu!=null&&Toolbars.enabled("actions")&&systemMenu.sel!=SystemCross.arrangement()&&SystemCross.sidebarLayouts().contains(SystemCross.arrangement()))
            systemMenu.change(SystemCross.arrangement());
        relayout();
    }
}
