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
import java.util.*;

/**
 * The root action column. A branch slides out beside it; on the right that is a mirror to the left.
 *
 * @author Jagy2014
 */
public final class ActionStrip extends Widget {
    private static volatile Boolean menuOnRight,menuHorizontal;
    /** A row along the bottom: the root buttons lie side by side and a branch opens upward. */
    public static boolean horizontal(){if(menuHorizontal==null)menuHorizontal=Utils.getprefb("hearthborn/toolbar/actions-horizontal",false);return menuHorizontal;}
    public static void horizontal(boolean value){menuHorizontal=value;Utils.setprefb("hearthborn/toolbar/actions-horizontal",value);}
    /** Where a horizontal row sits along the bottom edge. */
    public enum Align {
        LEFT("left","Left"),CENTER("center","In the middle"),RIGHT("right","Right");
        private final String key;
        public final String label;
        Align(String key,String label){this.key=key;this.label=label;}
    }
    private static volatile Align rowAlign;
    public static Align align(){
        if(rowAlign==null){
            String saved=Utils.getpref("hearthborn/toolbar/actions-horizontal-align","left");
            rowAlign=Align.LEFT;
            for(Align value:Align.values())if(value.key.equals(saved))rowAlign=value;
        }
        return rowAlign;
    }
    public static void align(Align value){rowAlign=Objects.requireNonNull(value);Utils.setpref("hearthborn/toolbar/actions-horizontal-align",value.key);}
    public static boolean onRight(){if(menuOnRight==null)menuOnRight=Utils.getprefb("hearthborn/toolbar/actions-right",false);return menuOnRight;}
    public static void onRight(boolean value){menuOnRight=value;Utils.setprefb("hearthborn/toolbar/actions-right",value);}
    private static final String[] NAMES={
        "Adventure","Build","Craft","Emotions","Government","Help","Rite and Magic",
        "Xtensions","History","Favorites","Attack","Push!","Shoot","Search actions"
    };
    private static final String[][] PATHS={
        {"/act/adv","/act/adventure"},
        {"/act/bld","/act/build"},
        {"/act/craft"},
        {"/act/emotes","/act/emotions"},
        {"/act/gov","/act/government","/gov/gov"},
        {"/act/halp","/act/help"},
        {"/act/seid","/act/magic","/act/rite"},
        {"/xtensions"},
        {"/act/history"},
        {"/favorites","/act/favourites","/act/favorites"},
        {"/act/atk","/act/attack"},
        {"/act/push"},
        {"/act/shoot"},
        {"/action-search"}
    };
    /** Used only when the exact name is absent. Recent actions stands in for History. */
    private static final String[][] ALIASES={
        {},
        {},
        {},
        {"emotes"},
        {},
        {},
        {},
        {},
        {"recent actions"},
        {"favourites"},
        {},
        {},
        {},
        {}
    };
    private final List<MenuGrid.PagButton> roots=new ArrayList<>();
    private final List<MenuGrid.PagButton> row=new ArrayList<>();
    private final List<List<MenuGrid.PagButton>> below=new ArrayList<>();
    private final List<MenuGrid.Pagina> path=new ArrayList<>();
    private final List<Slot> slots=new ArrayList<>();
    private UI.Grab click,outside;
    private MenuGrid.PagButton pressed;
    private MenuGrid.Pagina carrying;
    private Coord logicalSize=Coord.z;
    private int anchor=-1;
    private double scale=ActionMenuFrame.grid();
    private boolean waiting;
    private int builtSeq=-1;
    private double discoveryStart;

    public ActionStrip(){super(Coord.z);layout();}

    public void tick(double dt){
        show(Toolbars.enabled("actions"));
        if(scale!=ActionMenuFrame.grid())layout();
        MenuGrid menu=menu();
        if(visible()&&menu!=null)for(MenuGrid.Pagina page:path)HandMenu.prepare(menu,page);
        if(visible()&&menu!=null&&(waiting||builtSeq!=menu.pagseq)){
            rebuild();
            if(!waiting)builtSeq=menu.pagseq;
        }
        if(visible())for(Slot slot:slots)try{slot.button.tick(dt);}catch(Loading ignored){}
        fit();
        GameUI gui=getparent(GameUI.class);
        if(gui!=null)gui.layoutChat();
        super.tick(dt);
    }
    public void presize(){layout();fit();}

    private MenuGrid menu(){GameUI gui=getparent(GameUI.class);return gui==null?null:gui.menu;}

    private void rebuild(){
        MenuGrid menu=menu();
        waiting=false;
        roots.clear();row.clear();below.clear();anchor=-1;
        if(menu!=null){menu.refreshNewPages();roots.addAll(branches(menu));}
        if(!path.isEmpty()){
            anchor=index(roots,path.get(0));
            if(anchor<0){if(!waiting)path.clear();}
            else{
                row.addAll(children(menu,path.get(0)));
                List<MenuGrid.PagButton> level=row;
                for(int depth=1;depth<path.size();depth++){
                    List<MenuGrid.PagButton> next=depth<FOLDER_LEVELS?children(menu,path.get(depth)):flatten(menu,path.get(depth));
                    if(index(level,path.get(depth))<0||next.isEmpty()){
                        if(!waiting)path.subList(depth,path.size()).clear();
                        break;
                    }
                    below.add(next);
                    level=next;
                }
            }
        }
        discoveryStart=Utils.rtime();
        layout();
        watchOutside();
    }

    private void collapse(){
        if(path.isEmpty())return;
        path.clear();
        rebuild();
    }

    /** While a branch is open, a click outside this panel folds it and still reaches whatever was clicked. */
    private void watchOutside(){
        if(ui==null)return;
        if(!path.isEmpty()){if(outside==null)outside=ui.grabmouse(this);}
        else if(outside!=null){outside.remove();outside=null;}
    }

    private List<MenuGrid.PagButton> branches(MenuGrid menu){
        List<MenuGrid.PagButton> roots=new ArrayList<>();
        discover(menu,roots);
        MenuGrid.PagButton[] chosen=new MenuGrid.PagButton[NAMES.length];
        take(chosen,roots,0);
        take(menu,chosen,0);
        take(chosen,roots,1);
        take(menu,chosen,1);
        List<MenuGrid.PagButton> result=new ArrayList<>();
        for(int i=0;i<chosen.length;i++)if(chosen[i]!=null)result.add(chosen[i]);
        return result;
    }

    /** The same walk as the root of the 4×4: a folder counts even when only its children were listed. */
    private void discover(MenuGrid menu,List<MenuGrid.PagButton> roots){
        Deque<MenuGrid.Pagina> open=new ArrayDeque<>();
        Set<MenuGrid.Pagina> seen=new HashSet<>();
        synchronized(menu.paginae){open.addAll(menu.paginae);}
        while(!open.isEmpty()){
            MenuGrid.Pagina page=open.removeFirst();
            if(!seen.add(page))continue;
            try{
                MenuGrid.PagButton button=page.button();
                MenuGrid.Pagina parent=button.parent();
                if(parent==null)roots.add(button);
                else if(!seen.contains(parent))open.addLast(parent);
            }catch(Loading ignored){waiting=true;}
        }
    }

    private void take(MenuGrid.PagButton[] chosen,List<MenuGrid.PagButton> buttons,int pass){
        for(MenuGrid.PagButton button:buttons)try{
            int slot=match(button,pass);
            if(slot>=0&&chosen[slot]==null)chosen[slot]=button;
        }catch(Loading ignored){waiting=true;}
    }
    private void take(MenuGrid menu,MenuGrid.PagButton[] chosen,int pass){
        List<MenuGrid.Pagina> pages;synchronized(menu.paginae){pages=new ArrayList<>(menu.paginae);}
        for(MenuGrid.Pagina page:pages)try{
            MenuGrid.PagButton button=page.button();
            int slot=match(button,pass);
            if(slot>=0&&chosen[slot]==null)chosen[slot]=button;
        }catch(Loading ignored){waiting=true;}
    }

    private static int match(MenuGrid.PagButton button,int pass){
        String name=plain(button.name());
        String path=button.res==null||button.res.name==null?"":button.res.name.toLowerCase(Locale.ROOT);
        for(int i=0;i<NAMES.length;i++){
            if(pass==0){
                if(name.equals(plain(NAMES[i])))return i;
                for(String suffix:PATHS[i])if(path.endsWith(suffix))return i;
            }else for(String alias:ALIASES[i])if(name.equals(alias))return i;
        }
        return -1;
    }
    private static String plain(String name){
        if(name==null)return "";
        StringBuilder out=new StringBuilder();
        boolean space=false;
        for(int i=0;i<name.length();i++){
            char c=Character.toLowerCase(name.charAt(i));
            if(c=='&'){
                if(out.length()>0)out.append(' ');
                out.append("and");
                space=true;
                continue;
            }
            if(c=='!'||c=='.'||c=='\u2026')continue;
            if(c==' '){space=true;continue;}
            if(space&&out.length()>0)out.append(' ');
            space=false;
            out.append(c);
        }
        return out.toString();
    }

    /** Same pages the 4×4 would show, including a folder that is only the parent of a listed action. */
    private List<MenuGrid.PagButton> children(MenuGrid menu,MenuGrid.Pagina parent){
        HandMenu.prepare(menu,parent);
        if(parent!=null&&menu.shortcuts!=null&&menu.shortcuts.handles(parent)){
            List<MenuGrid.PagButton> saved=new ArrayList<>();
            for(MenuGrid.PagButton button:menu.shortcuts.buttons(parent))if(button!=null)saved.add(button);
            return saved;
        }
        List<MenuGrid.PagButton> result=new ArrayList<>();
        Deque<MenuGrid.Pagina> open=new ArrayDeque<>();
        Set<MenuGrid.Pagina> seen=new HashSet<>();
        Set<String> listed=new HashSet<>();
        synchronized(menu.paginae){open.addAll(menu.paginae);}
        while(!open.isEmpty()){
            MenuGrid.Pagina page=open.removeFirst();
            if(!seen.add(page))continue;
            try{
                MenuGrid.Pagina above=page.parent();
                if(sameFolder(above,parent)){
                    if(listed.add(page.res().name))result.add(page.button());
                }else if(above!=null&&!seen.contains(above))open.addLast(above);
            }catch(Loading ignored){waiting=true;}
        }
        Collections.sort(result,(a,b)->{
            try{return a.sortkey().compareTo(b.sortkey());}
            catch(Loading ignored){waiting=true;return 0;}
        });
        return result;
    }

    /** Folder level two spills every action inside it. Deeper folders are not shown. */
    private List<MenuGrid.PagButton> flatten(MenuGrid menu,MenuGrid.Pagina parent){
        List<MenuGrid.PagButton> result=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(MenuGrid.PagButton button:children(menu,parent))spill(menu,button,result,seen);
        return result;
    }

    private void spill(MenuGrid menu,MenuGrid.PagButton button,List<MenuGrid.PagButton> result,Set<String> seen){
        String name;
        try{name=button.res.name;}
        catch(Loading ignored){waiting=true;return;}
        if(!seen.add(name))return;
        List<MenuGrid.PagButton> nested=children(menu,button.pag);
        if(nested.isEmpty()){result.add(button);return;}
        for(MenuGrid.PagButton child:nested)spill(menu,child,result,seen);
    }

    /** Two pagina objects can both be the same folder when one was listed and the other was only reached as a parent. */
    private static boolean sameFolder(MenuGrid.Pagina left,MenuGrid.Pagina right){
        if(left==right)return true;
        if(left==null||right==null)return false;
        return left.res().name.equals(right.res().name);
    }

    private static int index(List<MenuGrid.PagButton> buttons,MenuGrid.Pagina page){
        for(int i=0;i<buttons.size();i++)if(buttons.get(i).pag==page)return i;
        return -1;
    }

    /** The opened row, then one folder row inside a pack. The next open spills actions. */
    private static final int FOLDER_LEVELS=2;
    private static final int MAX_COLUMNS=9;
    /** Below this many icons the first row keeps one spare cell. A longer row is as wide as its icons, up to {@link #MAX_COLUMNS}. */
    private static final int SPARE_BELOW=7;
    /** In a horizontal row the buttons from this index on open their menu to the left. */
    private static final int LEFT_FROM=5;
    private Coord cell(){return Inventory.invsq.sz();}
    private int step(){return cell().x+ToolbarFrame.gap();}
    /** Fewer than seven icons on the first row leave one empty cell, so the next folder can start on that row. */
    private int columns(){
        int needed=Math.max(1,row.size());
        if(needed<SPARE_BELOW)needed++;
        return Math.min(MAX_COLUMNS,needed);
    }
    private int rowsOf(int count){return count==0?0:(count+columns()-1)/columns();}
    /** First free cell after the icons already open. A folder continues on a partial row instead of skipping to the next one. */
    private int cellOrigin(int band){
        int used=row.size();
        for(int i=0;i<band;i++)used+=below.get(i).size();
        return used;
    }
    private int openedCount(){
        int count=row.size();
        for(List<MenuGrid.PagButton> band:below)count+=band.size();
        return count;
    }

    /** Depth 0 is the branch column. Opened icons stay within seven cells, wrap downward, and the next folder fills the empty cells. */
    Coord place(int depth,int index){
        Coord at=placed(depth,index);
        if(horizontal())return Coord.of(at.x,extent().y-at.y-cell().y);
        return onRight()?Coord.of(span()-at.x-cell().x,at.y):at;
    }
    private Coord placed(int depth,int index){
        int x=ToolbarFrame.inset().x;
        int y;
        if(horizontal()){
            if(depth==0)return Coord.of(x+index*step(),ToolbarFrame.inset().y);
            int cell=depth==1?index:cellOrigin(depth-2)+index;
            return Coord.of(openOrigin().x+x+(cell%columns())*step(),openOrigin().y+ToolbarFrame.inset().y+(cell/columns())*step());
        }
        if(depth==0)
            y=ToolbarFrame.inset().y+index*step();
        else{
            int cell=depth==1?index:cellOrigin(depth-2)+index;
            x+=(cell%columns()+1)*step();
            y=ToolbarFrame.inset().y+(Math.max(0,anchor)+cell/columns())*step();
        }
        return Coord.of(x,y);
    }

    private void layout(){
        scale=ActionMenuFrame.grid();
        slots.clear();
        for(int i=0;i<roots.size();i++)slots.add(new Slot(0,i,roots.get(i),place(0,i)));
        for(int i=0;i<row.size();i++)slots.add(new Slot(1,i,row.get(i),place(1,i)));
        for(int band=0;band<below.size();band++){
            List<MenuGrid.PagButton> icons=below.get(band);
            for(int i=0;i<icons.size();i++)slots.add(new Slot(band+2,i,icons.get(i),place(band+2,i)));
        }
        logicalSize=extent();
        Coord target=ToolbarScale.size(logicalSize,scale);
        int before=sz.x,beforeHeight=sz.y;
        if(!sz.equals(target))resize(target);
        if(horizontal()){if(beforeHeight!=sz.y)move(Coord.of(c.x,c.y+beforeHeight-sz.y));}
        else if(onRight()&&before!=sz.x)move(Coord.of(c.x+before-sz.x,c.y));
    }
    /** The panel with its opened branch, in logical units. */
    private Coord extent(){
        Coord column=columnBox();
        Coord far=anchor<0||row.isEmpty()?column:openOrigin().add(openBox());
        return Coord.of(Math.max(column.x,far.x),Math.max(column.y,far.y));
    }
    /** The menu is pinned: the left or right bottom corner for a column, the bottom edge for a row. */
    public void applySide(){
        if(parent==null)return;
        layout();
        pin();
    }
    private void pin(){
        if(parent==null)return;
        int x,y;
        if(horizontal()){
            switch(align()){
                case CENTER:x=(parent.sz.x-sz.x)/2;break;
                case RIGHT:x=parent.sz.x-sz.x;break;
                default:x=0;
            }
            y=parent.sz.y-sz.y;
        }else{
            x=onRight()?parent.sz.x-sz.x:0;
            y=parent.sz.y-sz.y;
        }
        Coord next=Coord.of(Math.max(0,x),Math.max(0,y));
        if(!next.equals(c))move(next);
    }
    /** Screen position of the root column. Opened rows sit beside it and are not part of this point. */
    public Coord columnAt(){
        if(horizontal())return Coord.of(c.x,c.y+sz.y-columnSize().y);
        return onRight()?Coord.of(c.x+sz.x-columnSize().x,c.y):c;
    }
    public void moveColumn(Coord at){
        if(horizontal())move(Coord.of(at.x,at.y-(sz.y-columnSize().y)));
        else if(onRight())move(Coord.of(at.x-(sz.x-columnSize().x),at.y));else move(at);
    }

    /** Where the docked chat may start so it clears this vertical column. Zero when the column is not on the left. */
    public int leftClearance(){
        if(!visible()||horizontal()||parent==null||parent.sz.x<=0||columnAt().x>parent.sz.x/3)return 0;
        return columnAt().x+columnSize().x+UI.scale(12);
    }
    /** Width kept clear at the right edge. Zero when the column is not on the right. */
    public int rightClearance(){
        if(!visible()||horizontal()||parent==null||parent.sz.x<=0||columnAt().x<parent.sz.x*2/3)return 0;
        return parent.sz.x-columnAt().x+UI.scale(12);
    }

    /** The root column in screen pixels. Opened rows are a popup and do not move snapped panels. */
    public Coord columnSize(){return ToolbarScale.size(columnBox(),scale);}
    /** The root column, from the top of the panel through its last button. */
    private Coord columnBox(){
        if(horizontal())return Coord.of(ToolbarFrame.inset().x+Math.max(1,roots.size())*step()-ToolbarFrame.gap()+ToolbarFrame.inset().x,
            ToolbarFrame.inset().y+cell().y+ToolbarFrame.inset().y);
        int rows=Math.max(1,roots.size());
        return Coord.of(ToolbarFrame.inset().x+cell().x+ToolbarFrame.inset().x,
            ToolbarFrame.inset().y+rows*step()-ToolbarFrame.gap()+ToolbarFrame.inset().y);
    }
    private int span(){return extent().x;}
    private Coord columnFrame(){
        if(horizontal())return Coord.of(0,extent().y-columnBox().y);
        return onRight()?Coord.of(span()-columnBox().x,0):Coord.z;
    }
    /** Opened icons begin on the clicked button's row, or above its column in a horizontal row. The frame does not cover the other buttons. */
    private Coord openOrigin(){
        // A column sits in the bottom corner, so a branch stays within its height and never runs off the screen.
        if(!horizontal())return Coord.of(step(),Utils.clip(Math.max(0,anchor)*step(),0,Math.max(0,columnBox().y-openBox().y)));
        // The first buttons open to the right, the rest to the left. Either way the menu stays within the row, so opening it never widens the panel.
        int index=Math.max(0,anchor),width=openBox().x;
        int from=index>=LEFT_FROM?ToolbarFrame.inset().x*2+index*step()+cell().x-width:index*step();
        return Coord.of(Utils.clip(from,0,Math.max(0,columnBox().x-width)),step());
    }
    private Coord openFrame(){
        Coord origin=openOrigin(),size=openBox();
        if(horizontal())return Coord.of(origin.x,extent().y-origin.y-size.y);
        return onRight()?Coord.of(span()-origin.x-size.x,origin.y):origin;
    }
    private Coord openBox(){
        int rows=Math.max(1,rowsOf(openedCount()));
        return Coord.of(ToolbarFrame.inset().x+columns()*step()-ToolbarFrame.gap()+ToolbarFrame.inset().x,
            ToolbarFrame.inset().y+rows*step()-ToolbarFrame.gap()+ToolbarFrame.inset().y);
    }
    private boolean inside(Coord point){
        if(point.isect(columnFrame(),columnBox()))return true;
        return anchor>=0&&!row.isEmpty()&&point.isect(openFrame(),openBox());
    }

    private Coord logical(Coord point){return ToolbarScale.logical(point,scale);}
    private Slot hit(Coord point){
        Coord cell=cell();
        for(int i=slots.size()-1;i>=0;i--)if(point.isect(slots.get(i).origin,cell))return slots.get(i);
        return null;
    }
    Coord pointer(int depth,int index){
        Coord mid=place(depth,index).add(cell().div(2));
        return Coord.of((int)Math.floor(mid.x*scale),(int)Math.floor(mid.y*scale));
    }
    List<MenuGrid.PagButton> column(){return Collections.unmodifiableList(roots);}
    List<MenuGrid.PagButton> band(int depth){
        if(depth==0)return Collections.unmodifiableList(row);
        int band=depth-1;
        if(band>=0&&band<below.size())return Collections.unmodifiableList(below.get(band));
        return Collections.<MenuGrid.PagButton>emptyList();
    }

    private void fit(){pin();}

    public boolean checkhit(Coord c){return inside(logical(c));}

    public void draw(GOut g){
        GOut content=ToolbarScale.graphics(g,logicalSize,scale);
        Coord columnAt=columnFrame(),column=columnBox();
        ActionMenuFrame.background(content.reclip(columnAt,column),column);
        boolean opened=anchor>=0&&!row.isEmpty();
        Coord origin=Coord.z,open=Coord.z;
        if(opened){
            origin=openFrame();open=openBox();
            ActionMenuFrame.background(content.reclip(origin,open),open);
        }
        Coord cell=cell();
        double elapsed=Utils.rtime()-discoveryStart;
        for(Slot slot:slots){
            ActionMenuFrame.slot(content,slot.origin,cell);
            try{
                GSprite sprite=slot.button.spr();
                GOut clip=content.reclip(slot.origin.add(1,1),sprite.sz());
                ActionDiscovery.draw(clip,slot.button,sprite,elapsed,slot.depth==0?slot.index:
                    slot.depth==1?slot.index:cellOrigin(slot.depth-2)+slot.index);
            }catch(Loading ignored){}
            if(ActionPress.same(pressed,slot.button)){
                content.chcolor(0,0,0,128);
                content.frect(slot.origin.add(1,1),cell.sub(1,1));
                content.chcolor();
            }
        }
        if(carrying!=null&&ui!=null)try{
            GSprite sprite=carrying.button().spr();
            ui.drawafter(out->sprite.draw(out.reclip(ui.mc.sub(sprite.sz().div(2)),sprite.sz())));
        }catch(Loading ignored){}
    }

    private void activate(Slot slot){
        MenuGrid menu=menu();
        if(menu==null)return;
        boolean folder=slot.depth<=FOLDER_LEVELS&&(menu.shortcuts!=null&&menu.shortcuts.handles(slot.button.pag)||!children(menu,slot.button.pag).isEmpty());
        if(folder){
            boolean open=!(path.size()>slot.depth&&path.get(slot.depth)==slot.button.pag);
            if(path.size()>slot.depth)path.subList(slot.depth,path.size()).clear();
            if(open){path.add(slot.button.pag);raise();}
            rebuild();
            return;
        }
        slot.button.pag.anew=slot.button.pag.tnew=0;
        slot.button.use(new MenuGrid.Interaction(1,ui==null?0:ui.modflags()));
        ActionBrowser.used(menu,slot.button);
        collapse();
    }

    public boolean mousedown(MouseDownEvent ev){
        Coord point=logical(ev.c);
        if(!inside(point)){collapse();return false;}
        Slot slot=hit(point);
        MenuGrid menu=menu();
        if(ev.b==3&&slot!=null&&menu!=null&&menu.shortcuts!=null&&menu.shortcuts.favoriteMenu(slot.button,this,ev.c))return true;
        if(ev.b==1&&slot!=null){pressed=slot.button;click=ui.grabmouse(this);return true;}
        return true;
    }
    public void mousemove(MouseMoveEvent ev){
        if(click!=null&&carrying==null&&pressed!=null){
            Slot slot=hit(logical(ev.c));
            if(slot==null||!ActionPress.same(pressed,slot.button))carrying=pressed.pag;
        }
    }
    public boolean mouseup(MouseUpEvent ev){
        if(click!=null&&ev.b==1){
            Slot slot=hit(logical(ev.c));
            MenuGrid.PagButton chosen=pressed;
            MenuGrid.Pagina page=carrying;
            pressed=null;carrying=null;click.remove();click=null;
            if(page!=null){DropTarget.dropthing(ui.root,ui.mc,page);return true;}
            if(slot!=null&&ActionPress.same(chosen,slot.button))activate(slot);
            return true;
        }
        return true;
    }
    public Object tooltip(Coord c,Widget prev){
        Slot slot=hit(logical(c));
        if(slot==null)return null;
        try{
            String english=slot.button.name();
            return hearthborn.L10n.resource("action",slot.button.res.name,english);
        }catch(Loading ignored){return null;}
    }

    private static final class Slot {
        final int depth,index;
        final MenuGrid.PagButton button;
        final Coord origin;
        Slot(int depth,int index,MenuGrid.PagButton button,Coord origin){
            this.depth=depth;this.index=index;this.button=button;this.origin=origin;
        }
    }
}
