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
import haven.MenuGrid.PagButton;
import haven.MenuGrid.Pagina;
import hearthborn.L10n;
import hearthborn.io.LocalStore;
import hearthborn.l10n.TooltipText;
import hearthborn.scripts.ShortcutRunner;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/**
 * The action palette: a narrow list with a search field, found by name in either language. Enter opens the
 * recipe in the small crafting window and closes the palette.
 *
 * @author Jagy2014
 */
public final class ActionBrowser extends MenuSearch {
    public enum Tab { ALL, CRAFT, BUILD, FAVORITES, HISTORY }
    private Tab tab=Tab.ALL;
    private final Set<String> favorites=new LinkedHashSet<>();
    private final List<String> history=new ArrayList<>();
    private long shortcutSequence;
    private int pagseq=-1,statusSeen=-1;
    private final TabBar tabBar;
    private final Label empty;
    private final Button ingredientButton;
    private final Widget chip;
    private MenuSearch ingredientResults;
    private boolean ingredientPending,chipShown;
    private double ingredientAsked;
    private final Details details;
    private Tex detailsTex;
    private Result detailsFor;
    private boolean detailsOpen=Utils.getprefb("hearthborn/palette/details",false);
    private boolean requestingPreview;
    private final Coord paneOrigin;
    private final Label hint;
    private Makewindow recipe;
    private Result selected;
    private double selectedAt;
    private String requestedRecipe;
    private final List<PendingRecipe> pendingRecipes=new ArrayList<>();
    private static class PendingRecipe {
        final String id,english,shown;
        PendingRecipe(PagButton button){id=button.res.name;english=normalized(button.name());shown=normalized(displayName(button));}
    }
    private static final Map<MenuGrid,ActionBrowser> browsers=new WeakHashMap<>();
    /** The list is LIST wide; the recipe of the chosen row is shown to the right of it, as wide as the crafting window. */
    private static final int LIST=UI.scale(400), GAP=UI.scale(8), HEIGHT=UI.scale(520);
    private static final int WIDTH=LIST;
    private static final int TOTAL=LIST+GAP+Makewindow.WIDTH;
    private static final Color ACCENT=new Color(230,185,70);

    public ActionBrowser(MenuGrid menu) {
        super("Action search",menu);
        load();
        browsers.put(menu,this);
        sbox.move(Coord.z);
        sbox.resize(Coord.of(WIDTH,sbox.sz.y));
        tabBar=add(new TabBar(),Coord.of(0,sbox.sz.y+UI.scale(6)));
        int top=tabBar.c.y+tabBar.sz.y+UI.scale(4);
        rls.move(Coord.of(0,top));rls.resize(Coord.of(WIDTH,HEIGHT-top-UI.scale(34)));
        empty=add(new Label("No matching actions"),Coord.of(UI.scale(8),top+UI.scale(12)));
        ingredientButton=add(new Button(WIDTH,"Search by ingredient",false,()->{
            if(ingredientResults!=null){clearIngredients();recons();}
            else {ingredientPending=true;menu.wdgmsg("act","itemcraft");}
        }),Coord.of(0,HEIGHT-UI.scale(28)));
        chip=add(new Chip(),Coord.z);chip.hide();
        paneOrigin=Coord.of(LIST+GAP,tabBar.c.y);
        hint=add(new Label(""),paneOrigin.add(UI.scale(4),UI.scale(6)));
        details=add(new Details(),paneOrigin);details.hide();
        resize(Coord.of(TOTAL,HEIGHT));
    }

    /** Tabs written as words with the chosen one underlined, so it can be told from the rest at a glance. */
    private final class TabBar extends Widget {
        private final String[] names={"All actions","Crafting","Construction","Favorites","History"};
        private final int[] starts=new int[names.length],widths=new int[names.length];
        private final Text[] shown=new Text[names.length],dim=new Text[names.length];
        TabBar() {
            super(Coord.z);
            int x=0,height=0;
            for(int i=0;i<names.length;i++) {
                String label=L10n.tr(names[i]);
                shown[i]=Text.std.render(label,new Color(255,245,180));
                dim[i]=Text.std.render(label,new Color(170,160,130));
                starts[i]=x;widths[i]=shown[i].sz().x;x+=widths[i]+UI.scale(16);height=Math.max(height,shown[i].sz().y);
            }
            resize(Coord.of(WIDTH,height+UI.scale(6)));
        }
        public void draw(GOut g) {
            Tab[] values=Tab.values();
            for(int i=0;i<values.length;i++) {
                boolean active=values[i]==tab;
                g.image((active?shown:dim)[i].tex(),Coord.of(starts[i],0));
                if(active) {
                    g.chcolor(ACCENT);
                    g.frect(Coord.of(starts[i],sz.y-UI.scale(2)),Coord.of(widths[i],UI.scale(2)));
                    g.chcolor();
                }
            }
        }
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b!=1)return false;
            for(int i=0;i<names.length;i++)
                if(ev.c.x>=starts[i]-UI.scale(4)&&ev.c.x<starts[i]+widths[i]+UI.scale(4)){selectTab(Tab.values()[i]);return true;}
            return false;
        }
        public void dispose(){for(Text t:shown)t.tex().dispose();for(Text t:dim)t.tex().dispose();super.dispose();}
    }

    /** "Details" under the recipe card: the rest of what the recipe says, folded away until it is asked for. */
    private final class Details extends Widget {
        private final Text label=Text.std.render(L10n.tr("Details"),new Color(255,245,180));
        private final int row=label.sz().y+UI.scale(4);
        Details() {super(Coord.z);resize(Coord.of(TOTAL-LIST-GAP,row));}
        void refit(int room) {
            int body=detailsOpen&&detailsTex!=null?Math.min(detailsTex.sz().y+UI.scale(4),Math.max(0,room-row)):0;
            resize(Coord.of(TOTAL-LIST-GAP,row+body));
        }
        public void draw(GOut g) {
            g.image(label.tex(),Coord.of(0,UI.scale(2)));
            int s=UI.scale(1),x=label.sz().x+UI.scale(8),y=UI.scale(2)+(label.sz().y-5*s)/2;
            g.chcolor(ACCENT);
            for(int i=0;i<5;i++)g.frect(Coord.of(x+i*s,y+(detailsOpen?5-1-i:i)*s),Coord.of((9-2*i)*s,s));
            g.chcolor();
            if(detailsOpen&&detailsTex!=null&&sz.y>row)g.reclip(Coord.of(0,row),Coord.of(sz.x,sz.y-row)).image(detailsTex,Coord.of(0,UI.scale(4)));
        }
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b!=1||ev.c.y>=row)return false;
            detailsOpen=!detailsOpen;Utils.setprefb("hearthborn/palette/details",detailsOpen);
            return true;
        }
        public void dispose(){label.tex().dispose();super.dispose();}
    }
    private void updateDetails(){
        boolean wanted=recipe!=null&&selected!=null;
        if(!wanted){details.hide();return;}
        if(detailsFor!=selected){
            try {
                java.awt.image.BufferedImage image=extraInfo(selected.btn);
                if(detailsTex!=null){detailsTex.dispose();detailsTex=null;}
                detailsFor=selected;
                if(image!=null)detailsTex=new TexI(enlarge(image));
            }catch(Loading loading){details.hide();return;}
        }
        if(detailsTex==null){details.hide();return;}
        int top=paneOrigin.y+recipe.sz.y+UI.scale(8);
        details.move(Coord.of(paneOrigin.x,top));
        details.refit(HEIGHT-top-UI.scale(4));
        details.show();
    }
    /** "Ingredient filter x" in the search line: one click removes the filter. */
    private final class Chip extends Widget {
        private final Text text=Text.std.render(L10n.tr("Ingredient")+" \u2715",new Color(255,245,180));
        Chip() {super(Coord.z);resize(text.sz().add(UI.scale(14),UI.scale(4)));settip(L10n.tr("Remove the ingredient filter"));}
        public void draw(GOut g) {
            g.chcolor(115,85,27,255);g.frect(Coord.z,sz);
            g.chcolor(ACCENT);g.rect(Coord.z,sz);g.chcolor();
            g.image(text.tex(),Coord.of(UI.scale(7),UI.scale(2)));
        }
        public boolean mousedown(MouseDownEvent ev){if(ev.b!=1)return false;clearIngredients();recons();setfocus(sbox);return true;}
        public void dispose(){text.tex().dispose();super.dispose();}
    }

    protected int rowHeight(){return UI.scale(44);}
    public static String fitCategory(String trail,int width){
        if(Text.std.render(trail).sz().x<=width)return trail;
        List<String> parts=new ArrayList<>(Arrays.asList(trail.split(" → ")));
        while(parts.size()>1){parts.remove(0);String text="… → "+String.join(" → ",parts);if(Text.std.render(text).sz().x<=width)return text;}
        return parts.get(0); // Keep the last category intact; the widget clips if exceptionally long.
    }
    static final String ARROW="gfx/hud/curs/arw";
    /** Item search is a targeting cursor. Dropping the hand does not end it; a right-click does. */
    static boolean targeting(String cursorName){return cursorName!=null && !cursorName.equals(ARROW);}
    static Object[] cancelClick(Coord mc){return new Object[]{Coord.z,mc==null?Coord.z:mc,3,0};}
    static void releaseCursor(GameUI gui){
        if(gui==null||gui.map==null)return;
        for(Widget widget=gui.map;widget!=null;widget=widget.parent){
            if(widget.cursor==null)continue;
            try{if(!targeting(widget.cursor.get().name))continue;}catch(Loading ignored){}
            widget.cursor=null;
        }
        Gob player=gui.map.player();
        gui.map.wdgmsg("click",cancelClick(player!=null&&player.rc!=null?player.rc.floor(OCache.posres):Coord.z));
    }
    /** The same two messages as the "Search by ingredient" button followed by a click on the item. */
    public static boolean searchByItem(GameUI gui,GItem item,Coord at){
        ActionBrowser browser=gui==null?null:gui.actionBrowser();
        if(browser==null||item==null||item.parent==null||gui.vhand!=null||browser.ingredientPending)return false;
        browser.ingredientPending=true;browser.ingredientAsked=Utils.rtime();
        browser.menu.wdgmsg("act","itemcraft");
        item.wdgmsg("take",at);
        return true;
    }
    private static String plain(String resource){return resource.replace("gfx/invobjs/small/","gfx/invobjs/");}
    /** An ingredient of a recipe: the search needs an item of that kind to point at, so one is taken from the inventory. */
    public static boolean searchByResource(GameUI gui,Resource resource){
        if(gui==null||gui.actionBrowser()==null)return false;
        for(GItem item:ShortcutRunner.owned(gui))try {
            if(item.parent instanceof Inventory&&plain(item.getres().name).equals(plain(resource.name)))return searchByItem(gui,item,Coord.z);
        }catch(Loading loading){}
        gui.ui.msg(L10n.tr("Put this ingredient in your inventory to search recipes by it."));
        return true;
    }
    private void clearIngredients(){
        ingredientPending=false;
        if(ingredientResults!=null){MenuSearch previous=ingredientResults;ingredientResults=null;previous.reqclose();recons();}
        if(ingredientButton!=null)ingredientButton.change("Search by ingredient");
    }
    public boolean acceptIngredients(MenuSearch results){
        if(ingredientResults!=null){add(results,Coord.z);results.hide();results.wdgmsg("close");releaseCursor(getparent(GameUI.class));return true;}
        if(!ingredientPending)return false;
        ingredientResults=add(results,Coord.z);results.hide();results.visible=false;ingredientPending=false;
        tab=Tab.ALL;sbox.settext("");
        ingredientButton.change("Clear ingredient filter");
        recons();show();raise();setfocus(sbox);
        releaseCursor(getparent(GameUI.class));
        return true;
    }
    private void load(){
        Object data=LocalStore.read("action-search");
        if(data instanceof Map) {
            Object saved=((Map<?,?>)data).get("favorites");
            if(saved instanceof List)for(Object value:(List<?>)saved)if(value instanceof String)favorites.add(XtensionsMenu.canonicalAction((String)value));
            saved=((Map<?,?>)data).get("history");
            if(saved instanceof List)for(Object value:(List<?>)saved)if(value instanceof String){
                String id=XtensionsMenu.canonicalAction((String)value);
                if(!history.contains(id))history.add(id);
            }
        }
    }
    private void save(){
        Map<String,List<String>> data=new LinkedHashMap<>();
        data.put("favorites",new ArrayList<>(favorites));data.put("history",new ArrayList<>(history));
        LocalStore.write("action-search",(Serializable)data);
    }
    private void remember(PagButton button){
        String id=button.res.name;
        if(!history.isEmpty()&&history.get(0).equals(id))return;
        history.remove(id);history.add(0,id);shortcutSequence++;
        while(history.size()>60)history.remove(history.size()-1);
        save();
        if(tab==Tab.HISTORY)recons();
    }
    private void selectTab(Tab value){clearIngredients();tab=value;recons();setfocus(sbox);}
    private void toggleFavorite(PagButton button){
        String id=button.res.name;
        if(!favorites.remove(id)){
            if(favorites.size()>=ActionShortcuts.ACTION_SLOTS){if(ui!=null)ui.error(L10n.tr("Favorites are full. Remove an action first (15 slots)."));return;}
            if(folder(button))return;
            favorites.add(id);
        }
        save();shortcutSequence++;
        if(tab==Tab.FAVORITES)recons();
    }
    static List<String> shortcuts(MenuGrid menu,boolean favorite){
        ActionBrowser browser=browsers.get(menu);
        return browser==null?Collections.emptyList():new ArrayList<>(favorite?browser.favorites:browser.history);
    }
    static long shortcutSequence(MenuGrid menu){ActionBrowser browser=browsers.get(menu);return browser==null?-1:browser.shortcutSequence;}
    static boolean isFavorite(MenuGrid menu,PagButton button){ActionBrowser browser=browsers.get(menu);return browser!=null&&browser.favorites.contains(button.res.name);}
    static void toggleFavorite(MenuGrid menu,PagButton button){ActionBrowser browser=browsers.get(menu);if(browser!=null)browser.toggleFavorite(button);}
    public static String normalized(String text){return text.toLowerCase(Locale.ROOT).replace('ё','е').trim();}
    public static int matchRank(String english,String shown,String query){
        String a=normalized(english),b=normalized(shown),q=normalized(query);
        if(a.equals(q)||b.equals(q))return 0;
        if(a.startsWith(q)||b.startsWith(q))return 1;
        return 2;
    }
    public static boolean matchesQuery(String english,String shown,String path,String query){
        String haystack=normalized(english+" "+shown+" "+path);
        for(String word:normalized(query).split("\\s+"))if(!haystack.contains(word))return false;
        return true;
    }
    public static String category(PagButton button){
        List<String> names=new ArrayList<>();Set<Pagina> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        try {for(Pagina parent=button.parent();parent!=null&&seen.add(parent);parent=parent.parent())names.add(MenuSearch.displayName(parent.button()));}
        catch(Loading ignored){}
        Collections.reverse(names);return String.join(" → ",names);
    }
    private static boolean under(PagButton button,String resource){
        Set<Pagina> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Pagina page=button.pag;page!=null&&seen.add(page);page=page.parent())if(page.res().name.equals(resource))return true;
        return false;
    }
    private static boolean craft(PagButton button){return button.res.name.startsWith("paginae/craft/");}
    private static boolean folder(PagButton button){return folder(button,null);}
    /** With the set of pages that have children from {@link MenuGrid#parents}, no search through all the actions is needed. */
    private static boolean folder(PagButton button,Set<Pagina> parents){
        Resource.AButton action=button.res.layer(Resource.action);
        return action!=null && (action.ad.length==0 && !button.res.name.startsWith("hearthborn/")
            || button.pag.scm.shortcuts!=null && button.pag.scm.shortcuts.handles(button.pag)
            || ActionMenuControls.isControl(button.res.name)
            || (parents==null?button.pag.scm.hasChildren(button.pag):parents.contains(button.pag)));
    }
    static boolean canFavorite(MenuGrid menu,PagButton button){
        return browsers.containsKey(menu)&&button!=menu.bk&&button!=menu.next&&button.res.layer(Resource.action)!=null&&!folder(button);
    }
    protected boolean generate(List<PagButton> result){
        boolean pending=false;
        if(ingredientResults!=null)return ingredientResults.collectButtons(result);
        List<Pagina> pages; synchronized(menu.paginae){pages=new ArrayList<>(menu.paginae);}
        Set<Pagina> parents=menu.parents();
        for(Pagina page:pages)try {
            if(menu.shortcuts!=null&&menu.shortcuts.handles(page)||ActionMenuControls.isControl(page.res().name))continue;
            PagButton button=page.button();String id=button.res.name;
            // The palette lists what can be done; categories are only a way to find it.
            if(folder(button,parents))continue;
            if(tab==Tab.CRAFT && !under(button,"paginae/act/craft"))continue;
            if(tab==Tab.BUILD && !under(button,"paginae/act/bld"))continue;
            if(tab==Tab.FAVORITES && !favorites.contains(id))continue;
            if(tab==Tab.HISTORY && !history.contains(id))continue;
            result.add(button);
        }catch(Loading loading){pending=true;}
        return pending;
    }
    /** The names of an action and its path, found once: looking them up costs too much to repeat for every row at every frame. */
    private static final class Names {
        final String english,shown,path;
        Names(PagButton button){english=button.name();shown=displayName(button);path=category(button);}
    }
    private final Map<Result,Names> names=new WeakHashMap<>();
    private Names names(Result result){
        Names found=names.get(result);
        if(found==null){found=new Names(result.btn);names.put(result,found);}
        return found;
    }
    protected int refilterStamp(){return RecipeStatus.version();}
    protected boolean matches(Result result,String query){Names n=names(result);return matchesQuery(n.english,n.shown,n.path,query);}
    protected int priority(Result result,String query){
        if(query.isEmpty() && tab==Tab.HISTORY)return history.indexOf(result.btn.res.name);
        // The better match first; among equals the recipes that can be made now, as far as is known.
        Names n=names(result);
        return matchRank(n.english,n.shown,query)*10+RecipeStatus.order(status(result.btn,n));
    }
    private static RecipeStatus.State status(PagButton button,Names n){
        return craft(button)?RecipeStatus.of(n.english,n.shown):null;
    }
    /** Enter on a recipe asks the server for its crafting window, which opens as the small one; the palette closes. */
    protected void execute(Result result){
        if(folder(result.btn))return;
        // The preview is closed and its late answer is not taken for it: the real one opens as the small window.
        clearRecipe();pendingRecipes.clear();
        menu.use(result.btn,new MenuGrid.Interaction(1,craft(result.btn)?0:ui.modflags()),false);
        if(!ui.modctrl){reqclose();}
    }

    protected Widget rowContent(Result result,Coord size){return new Row(result,size);}
    protected void drawSelection(GOut g,Area area){
        g.chcolor(255,205,90,38);g.frect2(area.ul,area.br);
        g.chcolor(ACCENT);g.frect2(area.ul,Coord.of(area.ul.x+UI.scale(3),area.br.y));g.chcolor();
    }

    private static final Color PATH_COLOR=new Color(160,155,135);
    private static Text.Foundry pathFont,stateFont;
    /** A row of the list: the icon, the name, the path in small grey letters and what is known about the ingredients. */
    private final class Row extends Widget {
        private final Result result;
        private Tex icon;private Text name,path,state,star;private String stateText="";
        private int stateVersion=-1;private RecipeStatus.State known;
        Row(Result result,Coord size){
            super(size);this.result=result;
            if(pathFont==null){pathFont=new Text.Foundry(Text.sans,10,PATH_COLOR);stateFont=new Text.Foundry(Text.sans,13);}
        }
        public void draw(GOut g){
            int pad=UI.scale(8),iconSize=UI.scale(32),right=sz.x-pad;
            try {
                if(icon==null){
                    BufferedImage image=result.btn.img();
                    if(image!=null){
                        int w=image.getWidth(),h=image.getHeight();
                        Coord fit=w>h?Coord.of(iconSize,Math.max(1,iconSize*h/w)):Coord.of(Math.max(1,iconSize*w/h),iconSize);
                        icon=new TexI(PUtils.convolve(image,fit,GobIcon.filter));
                    }else icon=Tex.nil;
                }
                g.image(icon,Coord.of(pad+(iconSize-icon.sz().x)/2,(sz.y-icon.sz().y)/2));
            }catch(Loading loading){}
            Names n;
            try{n=names(result);}catch(Loading loading){return;}
            if(stateVersion!=RecipeStatus.version()){
                stateVersion=RecipeStatus.version();
                known=status(result.btn,n);
                String wanted=RecipeStatus.text(known);
                if(!wanted.equals(stateText)){
                    if(state!=null){state.tex().dispose();state=null;}
                    if(!wanted.isEmpty())state=stateFont.render(wanted,RecipeStatus.color(known));
                    stateText=wanted;
                }
            }
            if(state!=null){g.image(state.tex(),Coord.of(right-state.sz().x,(sz.y-state.sz().y)/2));right-=state.sz().x+UI.scale(8);}
            if(favorites.contains(result.btn.res.name)){
                if(star==null)star=stateFont.render("\u2605",ACCENT);
                g.image(star.tex(),Coord.of(right-star.sz().x,(sz.y-star.sz().y)/2));right-=star.sz().x+UI.scale(6);
            }
            int tx=pad+iconSize+UI.scale(8),room=Math.max(UI.scale(40),right-tx);
            if(name==null)name=fit(elf,n.shown,Color.WHITE,room);
            if(path==null)path=fit(pathFont,n.path,PATH_COLOR,room);
            int y=(sz.y-name.sz().y-path.sz().y)/2;
            g.image(name.tex(),Coord.of(tx,y));
            g.image(path.tex(),Coord.of(tx,y+name.sz().y));
        }
        private Text fit(Text.Foundry font,String text,Color color,int room){
            Text line=font.render(text,color);
            if(line.sz().x<=room)return line;
            int len=((Text.Line)line).charat(room-font.strsize("...").x);
            line.tex().dispose();
            return font.render(text.substring(0,Math.max(0,len))+"...",color);
        }
        public void dispose(){
            if(icon!=null&&icon!=Tex.nil)icon.dispose();
            if(name!=null)name.tex().dispose();if(path!=null)path.tex().dispose();if(state!=null)state.tex().dispose();if(star!=null)star.tex().dispose();
            super.dispose();
        }
    }

    private void clearRecipe(){
        requestedRecipe=null;
        if(recipe!=null){recipe.hide();recipe.wdgmsg("close");recipe=null;}
    }
    /** Opening a recipe only asks the server for its widget; crafting stays explicit. */
    private void requestRecipe(PagButton button){
        requestedRecipe=button.res.name;
        PendingRecipe pending=new PendingRecipe(button);pendingRecipes.add(pending);
        requestingPreview=true;
        try {menu.use(button,new MenuGrid.Interaction(1,0),false);}
        catch(Loading loading){requestedRecipe=null;pendingRecipes.remove(pending);}
        finally {requestingPreview=false;}
    }
    /** True while the palette itself asks for a recipe to show it, which is not opening the recipe. */
    static boolean previewing(MenuGrid menu){ActionBrowser browser=browsers.get(menu);return browser!=null&&browser.requestingPreview;}
    public boolean embed(Makewindow widget){
        PendingRecipe pending=null;String caption=normalized(widget.rcpnm);
        for(PendingRecipe candidate:pendingRecipes)
            if(candidate.english.equals(caption)||candidate.shown.equals(caption)){pending=candidate;break;}
        if(pending==null && pendingRecipes.size()==1)pending=pendingRecipes.get(0);
        if(pending==null)return false;
        pendingRecipes.remove(pending);
        if(!visible() || !pending.id.equals(requestedRecipe)) {
            add(widget,Coord.z);widget.hide();widget.wdgmsg("close");return true;
        }
        if(recipe!=null){recipe.hide();recipe.wdgmsg("close");}
        if(selected!=null)widget.localizeTitle(displayName(selected.btn));
        recipe=add(widget,paneOrigin);
        hint.settext("");
        return true;
    }
    private void updateHint(){
        if(recipe!=null||selected==null){hint.settext("");return;}
        hint.settext(L10n.tr(craft(selected.btn)?"Loading the recipe...":"Press Enter to use the action"));
    }
    public void cresize(Widget child){if(child!=recipe)super.cresize(child);}
    public void wdgmsg(Widget sender,String message,Object... args){
        if(sender==recipe && message.equals("make") && selected!=null)remember(selected.btn);
        super.wdgmsg(sender,message,args);
    }
    public void hide(){super.hide();if(hint!=null)clearRecipe();}
    public static void used(MenuGrid menu,PagButton button){
        // Back/More are local navigation controls with no action layer.
        if(button==menu.bk || button==menu.next || button.res.layer(Resource.action)==null)return;
        ActionBrowser browser=browsers.get(menu);
        if(browser!=null && !browser.requestingPreview && !folder(button))browser.remember(button);
    }
    public void cdestroy(Widget child){if(child==ingredientResults){ingredientResults=null;ingredientButton.change("Search by ingredient");recons();}if(child==recipe){recipe=null;requestedRecipe=null;}super.cdestroy(child);}
    public boolean keydown(KeyDownEvent ev){
        if(ev.code==ev.awt.VK_TAB){
            Tab[] values=Tab.values();
            int next=(tab.ordinal()+((ev.mods&KeyMatch.S)!=0?values.length-1:1))%values.length;
            selectTab(values[next]);return true;
        }
        return super.keydown(ev);
    }
    public void show(){
        super.show();
        if(sbox!=null){
            setfocus(sbox);
            if(parent!=null)move(Coord.of(Math.max(0,(parent.sz.x-HudInterfaceScale.size(this).x)/2),Math.max(UI.scale(40),parent.sz.y/8)));
        }
    }
    /** The recipe card already shows the name and the ingredient slots, so those lines are not repeated. */
    static boolean ingredientTip(String className){
        String name=className.toLowerCase(java.util.Locale.ROOT);
        return name.contains(".inputs.")||name.endsWith(".inputs")||name.contains(".ingr.")||name.endsWith(".ingr");
    }
    static BufferedImage extraInfo(PagButton button){
        List<ItemInfo> info=new ArrayList<>();
        for(ItemInfo item:button.info())
            if(!(item instanceof ItemInfo.Name)&&!ingredientTip(item.getClass().getName()))info.add(item);
        return info.isEmpty()?null:ItemInfo.longtip(info);
    }
    /** The detail block stays a little larger than a tooltip, without covering the card. */
    static BufferedImage enlarge(BufferedImage image){
        if(image==null)return null;
        int width=Math.max(1,(int)Math.round(image.getWidth()*1.2));
        int height=Math.max(1,(int)Math.round(image.getHeight()*1.2));
        BufferedImage scaled=TexI.mkbuf(Coord.of(width,height));
        Graphics2D draw=scaled.createGraphics();
        draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        draw.drawImage(image,0,0,width,height,null);
        draw.dispose();
        return scaled;
    }
    public void tick(TickEvent event){
        if(event.visible && pagseq!=menu.pagseq){pagseq=menu.pagseq;recons();}
        if(event.visible && statusSeen!=RecipeStatus.version()){statusSeen=RecipeStatus.version();recons();}
        super.tick(event);
        if(!event.visible)return;
        empty.show(filtered.isEmpty());
        if(ingredientPending&&Utils.rtime()-ingredientAsked>6)ingredientPending=false;
        if(selected!=rls.sel){selected=rls.sel;selectedAt=Utils.rtime();clearRecipe();updateHint();}
        if(selected!=null && recipe==null && requestedRecipe==null && Utils.rtime()-selectedAt>.25
                && craft(selected.btn) && !folder(selected.btn))requestRecipe(selected.btn);
        updateDetails();
        boolean wantChip=ingredientResults!=null;
        if(wantChip!=chipShown){
            chipShown=wantChip;
            chip.show(wantChip);
            sbox.resize(Coord.of(wantChip?WIDTH-chip.sz.x-UI.scale(4):WIDTH,sbox.sz.y));
            chip.move(Coord.of(WIDTH-chip.sz.x,(sbox.sz.y-chip.sz.y)/2));
        }
    }
    public void dispose(){browsers.remove(menu,this);if(detailsTex!=null)detailsTex.dispose();super.dispose();}
}
