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
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * row of the twelve most recently opened recipes.
 *
 * @author Jagy2014
 */
public final class CraftHistory extends Widget {
    private static final Map<GameUI,List<MenuGrid.Pagina>> histories=new WeakHashMap<>();
    private final Toggle favorite;
    private final int historyWidth;
    public static void remember(GameUI gui,MenuGrid.Pagina page){
        List<MenuGrid.Pagina> history=histories.computeIfAbsent(gui,key->new ArrayList<>());
        history.removeIf(old->old.res().name.equals(page.res().name));history.add(0,page);
        if(history.size()>12)history.remove(history.size()-1);
    }
    public static void forget(GameUI gui){histories.remove(gui);}
    public static List<MenuGrid.Pagina> recent(GameUI gui){return new ArrayList<>(histories.getOrDefault(gui,Collections.emptyList()));}
    /** A five-pointed star drawn here, so that it does not depend on a font having the character. */
    static BufferedImage star(int size,Color fill,Color edge){
        BufferedImage image=TexI.mkbuf(Coord.of(size,size));
        java.awt.Graphics2D g=image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        Path2D.Double path=new Path2D.Double();
        double c=size/2.0,outer=size/2.0-1,inner=outer*0.4;
        for(int i=0;i<10;i++){
            double radius=i%2==0?outer:inner,angle=-Math.PI/2+i*Math.PI/5;
            double px=c+Math.cos(angle)*radius,py=c+Math.sin(angle)*radius;
            if(i==0)path.moveTo(px,py);else path.lineTo(px,py);
        }
        path.closePath();
        if(fill!=null){g.setColor(fill);g.fill(path);}
        g.setColor(edge);g.setStroke(new java.awt.BasicStroke(Math.max(1f,size/14f)));g.draw(path);
        g.dispose();
        return image;
    }
    private static final Color GOLD=new Color(240,195,70),GOLD_EDGE=new Color(255,235,150);
    public static BufferedImage favoriteStar(int size, boolean saved) {
        return saved ? star(size, GOLD, GOLD_EDGE) : star(size, null, new Color(170,150,100));
    }
    /** The star toggles the recipe that is open now. */
    private static final class Toggle extends Widget {
        private final MenuGrid menu;private final MenuGrid.Pagina page;private Tex filled,hollow;
        Toggle(MenuGrid menu,MenuGrid.Pagina page){super(UI.scale(32,32));this.menu=menu;this.page=page;}
        private boolean favorite(){try{return ActionBrowser.isFavorite(menu,page.button());}catch(Loading loading){return false;}}
        public void draw(GOut g){
            g.chcolor(28,18,8,255);g.frect(Coord.z,sz);g.chcolor(190,155,75,255);g.rect(Coord.z,sz);g.chcolor();
            Tex star;
            if(favorite()){if(filled==null)filled=new TexI(favoriteStar(UI.scale(22),true));star=filled;}
            else{if(hollow==null)hollow=new TexI(favoriteStar(UI.scale(22),false));star=hollow;}
            g.aimage(star,sz.div(2),.5,.5);
        }
        public Object tooltip(Coord at,Widget prev){return L10n.tr(favorite()?"Remove from Favorites":"Add to Favorites");}
        public boolean mousedown(MouseDownEvent event){
            if(event.b!=1)return false;
            try{if(ActionBrowser.canFavorite(menu,page.button()))ActionBrowser.toggleFavorite(menu,page.button());}catch(Loading loading){}
            return true;
        }
        public void dispose(){if(filled!=null)filled.dispose();if(hollow!=null)hollow.dispose();super.dispose();}
    }
    public CraftHistory(GameUI gui){
        super(Coord.z);int x=0;
        final MenuGrid menu=gui.menu;
        final List<MenuGrid.Pagina> recent=recent(gui);
        for(MenuGrid.Pagina page:recent){
            final boolean active=x==0;
            add(new Widget(UI.scale(32,32)){
                private Tex icon,mark;
                public void draw(GOut g){
                    g.chcolor(active?new java.awt.Color(115,85,27):new java.awt.Color(28,18,8));g.frect(Coord.z,sz);
                    g.chcolor(190,155,75,255);g.rect(Coord.z,sz);g.chcolor();
                    try{if(icon==null){Resource.Image image=page.res().layer(Resource.imgc);if(image!=null){Coord size=image.sz;double scale=(double)UI.scale(26)/Math.max(size.x,size.y);icon=new TexI(PUtils.uiscale(image.img,Coord.of(Math.max(1,(int)(size.x*scale)),Math.max(1,(int)(size.y*scale)))));}}
                        if(icon!=null)g.aimage(icon,sz.div(2),.5,.5);
                    }catch(Loading ignored){}
                    try{
                        if(menu!=null&&ActionBrowser.isFavorite(menu,page.button())){
                            if(mark==null)mark=new TexI(star(UI.scale(12),GOLD,GOLD_EDGE));
                            g.image(mark,Coord.of(sz.x-mark.sz().x-UI.scale(1),UI.scale(1)));
                        }
                    }catch(Loading ignored){}
                }
                public Object tooltip(Coord at,Widget prev){try{return hearthborn.L10n.resource("action",page.res().name,page.button().act().name);}catch(Loading loading){return null;}}
                public boolean mousedown(MouseDownEvent event){if(event.b==1){page.button().use(new MenuGrid.Interaction(1,0));return true;}return false;}
                public void dispose(){if(icon!=null)icon.dispose();if(mark!=null)mark.dispose();super.dispose();}
            },Coord.of(x,0));x+=UI.scale(34);
        }
        historyWidth=x;
        favorite=menu!=null&&!recent.isEmpty()?add(new Toggle(menu,recent.get(0))):null;
        align(Makewindow.WIDTH);
    }

    public void align(int contentWidth) {
        int minimum = favorite == null ? historyWidth : historyWidth + UI.scale(6) + favorite.sz.x;
        int width = Math.max(contentWidth, minimum);
        if(favorite != null) favorite.move(Coord.of(width - favorite.sz.x, 0));
        resize(Coord.of(width, historyWidth == 0 ? 0 : UI.scale(36)));
    }
}
