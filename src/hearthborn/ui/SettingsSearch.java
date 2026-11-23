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
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Supplier;

/**
 * Finds any setting by name or tooltip, wherever its panel is. The index is read from the panels themselves
 * when the first query arrives, so a new option is findable the moment it is added.
 *
 * @author Jagy2014
 */
public final class SettingsSearch {
    private static final int ROWS=8;
    private static final Color HOVER=new Color(255,255,255,40);
    private static final Color HINT=new Color(170,160,140);

    /** One findable thing: its label, the panel it lives in, and how to open that panel. */
    static final class Entry {
        final String section,label,hay;final Supplier<Widget> open;
        Entry(String section,String label,String tip,Supplier<Widget> open){
            this.section=section;this.label=label;this.open=open;
            this.hay=(label+" "+section+" "+(tip==null?"":tip)).toLowerCase(Locale.ROOT);
        }
        boolean named(String[] words){
            String text=(label+" "+section).toLowerCase(Locale.ROOT);
            for(String word:words)if(!text.contains(word))return false;
            return true;
        }
        boolean matches(String[] words){
            for(String word:words)if(!hay.contains(word))return false;
            return true;
        }
    }

    private final OptWnd options;
    private final List<Widget> grid=new ArrayList<>();
    private final Field field;
    private final IButton clear;
    private final Results results;
    private List<Entry> index;

    private SettingsSearch(OptWnd options,int width){
        this.options=options;
        field=new Field(width);
        clear=SmallWindowDeco.closeButton(()->{
            field.settext("");
            field.parent.setfocus(field);
        });
        clear.settip("Clear search");
        clear.hide();
        results=new Results(Coord.of(width,ROWS*rowHeight()));
        results.hide();
    }
    static int rowHeight(){return UI.scale(24);}

    /** Adds the search field at the top of the main panel. */
    public static SettingsSearch install(OptWnd options,int width){
        SettingsSearch search=new SettingsSearch(options,width);
        options.main.add(search.field,Coord.z);
        options.main.add(search.clear);
        search.resizeField(width);
        search.below=search.field.pos("bl").adds(0,8).y;
        options.main.add(search.results,Coord.of(0,search.below));
        return search;
    }
    private int below;
    /** The y at which the rest of the main panel starts. */
    public int bottom(){return below;}
    /**
     * Gives every button of the main panel the width of the widest one, which differs between languages, and keeps the
     * two columns the same gap apart. The search field and its results follow the new total width.
     */
    public void align(){
        List<Button> buttons=new ArrayList<>();
        int width=0;
        for(Widget w=options.main.child;w!=null;w=w.next)
            if(w instanceof Button){buttons.add((Button)w);width=Math.max(width,w.sz.x);}
        if(buttons.isEmpty())return;
        int gap=UI.scale(20),second=Integer.MAX_VALUE;
        for(Button b:buttons)if(b.c.x>0)second=Math.min(second,b.c.x);
        for(Button b:buttons){
            boolean right=b.c.x>0;
            b.resize(Coord.of(width,b.sz.y));
            b.move(Coord.of(right?width+gap:0,b.c.y));
        }
        int total=second==Integer.MAX_VALUE?width:width*2+gap;
        resizeField(total);
        results.resize(Coord.of(total,results.sz.y));
    }
    private Widget pane;
    /** Sidebar layout: the field spans the column, and the results take the place of the section pane while a query is typed. */
    public void sidebar(int columnWidth,Coord at,int width,Widget pane){
        this.pane=pane;
        resizeField(columnWidth);
        results.move(at);
        results.resize(Coord.of(width,results.sz.y));
    }
    /** Called once the rest of the main panel exists, so the controls hidden during a search are known. */
    public void finish(){
        grid.clear();
        if(pane==null)for(Widget w=options.main.child;w!=null;w=w.next)if(w!=field&&w!=clear&&w!=results)grid.add(w);
    }

    private void resizeField(int width){
        field.resize(width-clear.sz.x-UI.scale(4));
        clear.move(Coord.of(field.c.x+width-clear.sz.x,field.c.y+Math.max(0,(field.sz.y-clear.sz.y)/2)));
    }

    private final class Field extends TextEntry {
        private Text hint;
        Field(int width){super(width,"");}
        protected void changed(){super.changed();clear.show(!text().isEmpty());refresh(text());}
        public void draw(GOut g){
            super.draw(g);
            if(text().isEmpty()){
                if(hint==null)hint=Text.std.render(L10n.tr("Search settings"),HINT);
                g.image(hint.tex(),Coord.of(UI.scale(6),(sz.y-hint.sz().y)/2));
            }
        }
        public void activate(String text){
            if(!results.hits.isEmpty())results.open(results.hits.get(0));
        }
        public boolean keydown(KeyDownEvent ev){
            if(ev.code==KeyEvent.VK_ESCAPE&&!text().isEmpty()){settext("");refresh("");return true;}
            return super.keydown(ev);
        }
    }

    /** The matches, drawn over the button grid while a query is typed. */
    private final class Results extends Widget {
        final List<Entry> hits=new ArrayList<>();
        private final Map<Entry,Text> sections=new HashMap<>(),labels=new HashMap<>();
        private int hover=-1;
        Results(Coord size){super(size);}
        void show(List<Entry> found){
            hits.clear();hits.addAll(found);hover=-1;
        }
        public void draw(GOut g){
            if(hits.isEmpty()){
                Text none=Text.std.render(L10n.tr("Nothing found"),HINT);
                g.image(none.tex(),Coord.of(UI.scale(6),UI.scale(4)));
                return;
            }
            int h=rowHeight();
            for(int i=0;i<hits.size()&&i<ROWS;i++){
                Entry e=hits.get(i);
                if(i==hover){g.chcolor(HOVER);g.frect(Coord.of(0,i*h),Coord.of(sz.x,h));g.chcolor();}
                Text label=labels.computeIfAbsent(e,k->Text.std.render(k.label,Color.WHITE));
                Text section=sections.computeIfAbsent(e,k->Text.std.render(k.section,HINT));
                g.image(label.tex(),Coord.of(UI.scale(6),i*h+(h-label.sz().y)/2));
                g.image(section.tex(),Coord.of(sz.x-section.sz().x-UI.scale(6),i*h+(h-section.sz().y)/2));
            }
        }
        private int row(Coord c){int i=c.y/rowHeight();return c.y>=0&&i<hits.size()&&i<ROWS?i:-1;}
        public void mousemove(MouseMoveEvent ev){hover=row(ev.c);super.mousemove(ev);}
        public boolean mousedown(MouseDownEvent ev){
            int i=ev.b==1?row(ev.c):-1;
            if(i<0)return super.mousedown(ev);
            open(hits.get(i));return true;
        }
        void open(Entry entry){
            field.settext("");refresh("");
            Widget root=entry.open.get();
            if(root==null)return;
            Widget target=highlight(root,entry.label);
            if(target!=null&&pane instanceof Scrollport)reveal((Scrollport)pane,target);
        }
    }

    private void refresh(String query){
        String[] words=query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean active=!query.trim().isEmpty();
        for(Widget w:grid)w.visible=!active;
        if(pane!=null)pane.visible=!active;
        results.visible=active;
        if(!active)return;
        List<Entry> named=new ArrayList<>(),other=new ArrayList<>();
        for(Entry e:index()){
            if(e.named(words))named.add(e);
            else if(e.matches(words))other.add(e);
        }
        named.addAll(other);
        results.show(named.size()>ROWS?named.subList(0,ROWS):named);
    }

    private List<Entry> index(){
        if(index!=null)return index;
        List<Entry> found=new ArrayList<>();
        for(Widget w=options.main.child;w!=null;w=w.next){
            if(!(w instanceof OptWnd.PButton))continue;
            OptWnd.PButton button=(OptWnd.PButton)w;
            String title=button.title();
            if(title.equals("Back"))continue;
            // The UI settings need a running game; before login their button is disabled and has nothing to index.
            if(title.equals("Interface display")&&!options.ingame)continue;
            Supplier<Widget> open=()->{button.click();return button.panel();};
            found.add(new Entry(L10n.tr("Sections"),L10n.text("button",title),null,open));
            try{scan(button.panel(),L10n.text("button",title),open,found);}
            catch(RuntimeException e){System.err.println("settings search skipped "+title+": "+e);}
        }
        boolean listed=false;
        for(Widget w=options.main.child;w!=null;w=w.next)if(w instanceof OptWnd.PButton&&((OptWnd.PButton)w).title().equals("Interface display"))listed=true;
        if(options.ingame&&!listed)try{
            Supplier<Widget> open=()->{
                GameUI gui=options.getparent(GameUI.class);
                if(gui==null)return null;
                UISettings.open(gui,options);options.hide();
                return gui.getchild(UISettings.class);
            };
            String title=L10n.text("button","Interface display");
            found.add(new Entry(L10n.tr("Sections"),title,null,open));
            scan(new UISettings(),title,open,found);
        }catch(RuntimeException e){System.err.println("settings search skipped UI settings: "+e);}
        return index=found;
    }

    static String label(Widget w){
        String text=null;
        if(w instanceof CheckBox)text=L10n.text("label",((CheckBox)w).originalLabel);
        else if(w instanceof Button&&!(w instanceof OptWnd.PButton)&&((Button)w).text!=null)text=((Button)w).text.text;
        else if(w instanceof Label)text=((Label)w).texts;
        if(text==null)return null;
        text=text.trim();
        if(text.endsWith(":"))text=text.substring(0,text.length()-1).trim();
        return text.length()<3||text.equals(L10n.text("button","Back"))||text.matches("[\\d.,%\\s]+")?null:text;
    }
    private static void scan(Widget parent,String section,Supplier<Widget> open,List<Entry> out){
        for(Widget w=parent.child;w!=null;w=w.next){
            String label=label(w);
            if(label!=null){
                String tip=w.tooltip instanceof Widget.KeyboundTip?((Widget.KeyboundTip)w.tooltip).base:null;
                out.add(new Entry(section,label,tip,open));
            }
            scan(w,section,open,out);
        }
    }
    private static Widget find(Widget parent,String label){
        for(Widget w=parent.child;w!=null;w=w.next){
            if(w.visible&&label.equals(label(w)))return w;
            Widget inner=find(w,label);
            if(inner!=null)return inner;
        }
        return null;
    }
    private static Widget highlight(Widget root,String label){
        Widget target=find(root,label);
        if(target==null||target.parent==null)return null;
        target.parent.add(new Flash(target.sz.add(UI.scale(6),UI.scale(4))),target.c.sub(UI.scale(3),UI.scale(2)));
        return target;
    }
    /** Scrolls the pane so a found setting is in view, a little below the top edge. */
    private static void reveal(Scrollport pane,Widget target){
        int y=0;
        for(Widget w=target;w!=null&&w!=pane.cont;w=w.parent)y+=w.c.y;
        int visibleBottom=pane.cont.sy+pane.cont.sz.y;
        if(y<pane.cont.sy||y+target.sz.y>visibleBottom)pane.bar.ch(Math.max(0,y-UI.scale(30))-pane.bar.val);
    }

    /** A pulsing frame that shows where a found setting is, and never takes a click. */
    private static final class Flash extends Widget {
        private double age;
        Flash(Coord size){super(size);}
        public void tick(double dt){age+=dt;if(age>4)reqdestroy();super.tick(dt);}
        public void draw(GOut g){
            int alpha=(int)(150+100*Math.sin(age*8));
            g.chcolor(255,214,90,Math.max(40,Math.min(255,alpha)));
            g.rect(Coord.z,sz);g.rect(Coord.of(1,1),sz.sub(2,2));g.chcolor();
        }
        public boolean checkhit(Coord c){return false;}
        public boolean mousedown(MouseDownEvent ev){return false;}
    }
}
