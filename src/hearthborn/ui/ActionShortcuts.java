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
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Two root pages displaying live actions in the existing 4x4 menu.
 *
 * @author Jagy2014
 */
public final class ActionShortcuts {
    public static final int ACTION_SLOTS=15;
    private final MenuGrid menu;
    public final Pagina favorites,recent;
    private int pageSequence=-1;
    private long savedSequence=Long.MIN_VALUE;
    private boolean loading;

    public ActionShortcuts(MenuGrid menu){
        this.menu=menu;
        favorites=page("favorites","Favorites","Right-click an action to add or remove it from Favorites.");
        recent=page("recent-actions","Recent actions","The last 15 used actions. Recipe previews are not recorded.");
    }
    private Pagina page(String id,String name,String help){
        Resource.Virtual resource=(Resource.Virtual)XtensionsMenu.resource(id,name,'\0');
        resource.add(resource.new Pagina(new MessageBuf(L10n.tr(help).getBytes(StandardCharsets.UTF_8))));
        final String order=id.equals("favorites")?"0":"1";
        Pagina page=new Pagina(menu,resource.indir(),resource.indir()){
            private PagButton button;
            public PagButton button(){
                if(button==null)button=new PagButton(this){
                    public Pagina parent(){return null;}
                    public String sortkey(){return "\uffff"+order;}
                    public void use(MenuGrid.Interaction interaction){menu.change(pag);}
                };
                return button;
            }
        };
        menu.paginae.add(page);
        return page;
    }
    public boolean handles(Pagina page){return page!=null&&(page==favorites||page==recent);}
    public boolean changed(){
        return handles(menu.cur)&&(pageSequence!=menu.pagseq||savedSequence!=ActionBrowser.shortcutSequence(menu));
    }
    public boolean loading(){return loading;}
    public List<PagButton> buttons(Pagina page){
        loading=false;pageSequence=menu.pagseq;savedSequence=ActionBrowser.shortcutSequence(menu);
        List<Pagina> pages;synchronized(menu.paginae){pages=new ArrayList<>(menu.paginae);}
        Map<String,PagButton> available=new HashMap<>();
        for(Pagina candidate:pages)try{
            if(!handles(candidate))available.put(candidate.res().name,candidate.button());
        }catch(Loading waiting){loading=true;}
        List<PagButton> result=new ArrayList<>();
        for(String id:ActionBrowser.shortcuts(menu,page==favorites)){
            PagButton button=available.get(id);
            // Favorite positions stay stable while an action is loading or unavailable.
            if(button!=null||page==favorites)result.add(button);
            if(result.size()==ACTION_SLOTS)break;
        }
        return result;
    }
    public boolean favoriteMenu(PagButton button,Widget owner,Coord at){
        if(!ActionBrowser.canFavorite(menu,button)||owner.ui==null)return false;
        String label=L10n.tr(ActionBrowser.isFavorite(menu,button)?"Remove from Favorites":"Add to Favorites");
        SListMenu<SListMenu.Action,Widget> popup=SListMenu.of(UI.scale(245,28),Text.std,
            Collections.singletonList(SListMenu.Action.of(label,()->ActionBrowser.toggleFavorite(menu,button))));
        Coord wanted=owner.rootpos(at),size=popup.box.sz.add(SListMenu.obox.cisz()),screen=owner.ui.root.sz;
        owner.ui.root.add(popup,Coord.of(Math.max(0,Math.min(wanted.x,screen.x-size.x)),Math.max(0,Math.min(wanted.y,screen.y-size.y))));
        return true;
    }
}
