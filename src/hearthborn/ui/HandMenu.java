/*
 * This file is part of Hearthborn Client.
 * Copyright (C) 2026 Jagy2014
 * GitHub: https://github.com/Jagy2014
 * Licensed under the GNU Lesser General Public License version 3.0 (LGPL-3.0).
 * See the COPYING file for details.
 */
package hearthborn.ui;

import haven.*;
import hearthborn.L10n;
import hearthborn.scripts.HandEquip;
import hearthborn.util.GameObjects;
import java.util.*;
import java.awt.image.BufferedImage;

/** Inventory-derived actions in the native menu; no inventory scan while the folder is closed. */
public final class HandMenu {
    private static long generation;
    private static final Map<MenuGrid,HandMenu> menus=new WeakHashMap<>();
    private final MenuGrid menu;private final MenuGrid.Pagina folder;
    private final Map<String,MenuGrid.Pagina> pages=new LinkedHashMap<>();
    private long built=-1;private boolean waiting;private double retry;
    private HandMenu(MenuGrid menu,MenuGrid.Pagina folder){this.menu=menu;this.folder=folder;}
    public static void invalidate(){generation++;}
    public static boolean handles(MenuGrid.Pagina page){try{return page!=null&&page.res().name.equals("hearthborn/hands");}catch(Loading pending){return false;}}
    public static void install(MenuGrid menu,MenuGrid.Pagina parent){
        MenuGrid.Pagina page=XtensionsMenu.page(menu,"hands","Equip in hands",'\0',parent,null);
        menus.put(menu,new HandMenu(menu,page));menu.paginae.add(page);
    }
    public static void restore(MenuGrid menu){HandMenu hands=menus.get(menu);if(hands!=null&&hands.built!=generation)hands.refresh(false);}
    public static void open(GameUI gui){if(gui==null||gui.menu==null)return;HandMenu hands=menus.get(gui.menu);if(hands!=null){hands.refresh(true);gui.menu.change(hands.folder);}}
    /** Called only for an open folder (native menu or sidebar). Pending resources retry at most ten times/second. */
    public static void prepare(MenuGrid menu,MenuGrid.Pagina page){if(handles(page)){HandMenu hands=menus.get(menu);if(hands!=null)hands.refresh(false);}}
    private void refresh(boolean force){
        if(!force&&built==generation&&(!waiting||Utils.rtime()<retry))return;
        built=generation;waiting=false;retry=Utils.rtime()+.1;
        GameUI gui=menu.getparent(GameUI.class);if(gui==null)return;
        Map<String,GItem> available=new TreeMap<>();
        for(GItem item:HandEquip.carried(gui))try {
            // Loading is retried only while this folder is visible.
            if(!HandEquip.forHands(item)) {
                item.info(); // Let the folder retry if metadata is still loading.
                continue;
            }
            String key=HandEquip.canon(item);GItem known=available.get(key);
            if(known==null||GameObjects.quality(item)>GameObjects.quality(known))available.put(key,item);
        }catch(Loading pending){waiting=true;}
        Set<String> current=new HashSet<>(available.keySet());
        // Already-equipped types remain visible and selected, even after their last inventory copy is taken.
        Equipory gear=gui.equipment();
        if(gear!=null)for(WItem view:gear.children(WItem.class))if(view.c.equals(Equipory.ecoords[6].add(1,1))||view.c.equals(Equipory.ecoords[7].add(1,1)))try{
            String key=HandEquip.canon(view.item);available.putIfAbsent(key,view.item);current.add(key);
        }catch(Loading pending){waiting=true;}
        boolean changed=false;
        synchronized(menu.paginae){
            for(Map.Entry<String,MenuGrid.Pagina> entry:pages.entrySet())if(!current.contains(entry.getKey()))changed|=menu.paginae.remove(entry.getValue());
            for(Map.Entry<String,GItem> entry:available.entrySet())try{
                MenuGrid.Pagina page=pages.get(entry.getKey());
                if(page==null){page=create(gui,entry.getKey(),entry.getValue());pages.put(entry.getKey(),page);}
                changed|=menu.paginae.add(page);
            }catch(Loading pending){waiting=true;}
        }
        if(changed)menu.pagseq++;
    }
    private MenuGrid.Pagina create(GameUI gui,String key,GItem item){
        String title=GameObjects.name(key);GSprite sprite=item.spr();
        BufferedImage image=sprite instanceof GSprite.ImageSprite?((GSprite.ImageSprite)sprite).image():item.resource().flayer(Resource.imgc).img;
        Resource resource=XtensionsMenu.resource("hand-item/"+(key.startsWith("gfx/invobjs/")?key.substring("gfx/invobjs/".length()):key),title,'\0',image);
        return new MenuGrid.Pagina(menu,resource.indir(),resource.indir()){
            private MenuGrid.PagButton button;
            public MenuGrid.PagButton button(){if(button==null)button=new MenuGrid.PagButton(this){
                public MenuGrid.Pagina parent(){return folder;}
                public void use(MenuGrid.Interaction interaction){
                    if(HandEquip.equipPair(gui,key))return;
                    GItem best=HandEquip.best(gui,key);
                    if(best!=null)HandEquip.swap(gui,best);
                    else if(!HandEquip.equipped(gui,key))gui.ui.msg(L10n.tr("No equipment for the hands in the inventory."));
                }
                public void drawmain(GOut g,GSprite sprite){super.drawmain(g,sprite);if(HandEquip.equipped(gui,key)){
                    g.chcolor(255,218,110,255);g.rect(Coord.of(1,1),g.sz().sub(2,2));g.rect(Coord.of(2,2),g.sz().sub(4,4));g.chcolor();
                }}
            };return button;}
        };
    }
    private HandMenu(){throw new AssertionError();}
}
