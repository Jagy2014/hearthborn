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

package hearthborn.mining;
import hearthborn.ui.Inspect;

import haven.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.navigation.Walking;
import java.util.*;

/**
 * UI-thread mining guard. Dust clues are historical observations, never a safety verdict.
 *
 * @author Jagy2014
 */
public final class MiningSafety {
    private static final Map<GameUI,State> states=new WeakHashMap<>();
    static class State {
        Area selected;final Map<Coord,Long> grids=new HashMap<>();
        String status="Choose a mining area to check it.";double checked,painted;boolean stopped;
        MiningCoverage overlay;MCache overlayMap;boolean destroying;
    }
    static State state(GameUI gui){return states.computeIfAbsent(gui,key->new State());}
    public static void dispose(GameUI gui){Minesweeper.clearHover(gui);State state=states.remove(gui);if(state!=null)clearCoverage(state);}
    public static boolean enabled(){return Utils.getprefb("hearthborn/mining/guard",true);}
    public static boolean coverage(){return Utils.getprefb("hearthborn/mining/coverage",true);}
    public static double threshold(){return Utils.getprefb("hearthborn/mining/stop-at-50",true)?.5:.25;}
    public static double radius(String resource){
        switch(resource){
        case "gfx/terobjs/ladder":case "gfx/terobjs/minesupport":return 100;
        case "gfx/terobjs/column":return 125;
        case "gfx/terobjs/minebeam":return 150;
        default:return 0;
        }
    }
    static final class Support {
        final Coord2d at;final double radius,health;
        Support(Coord2d at,double radius,double health){this.at=at;this.radius=radius;this.health=health;}
    }
    static String assess(Coord2d at,List<Support> supports,List<Coord2d> rocks,double threshold){
        for(Coord2d rock:rocks)if(at.dist(rock)<=125)return "Loose rock is too close to the mining tile.";
        boolean covered=false;
        for(Support support:supports)if(at.dist(support.at)<=support.radius){
            covered=true;
            if(support.health<=threshold)return "A support covering the mining tile is damaged.";
        }
        return covered?null:"The mining tile is outside visible support coverage.";
    }
    static final class Survey {
        final List<Support> supports=new ArrayList<>();final List<Coord2d> rocks=new ArrayList<>();
        Survey(GameUI gui){
            OCache cache=gui.ui.sess.glob.oc;List<Gob> objects=new ArrayList<>();
            // Network updates mutate OCache. Copy under its lock, then inspect gobs outside it.
            synchronized(cache){for(Gob gob:cache)objects.add(gob);}
            for(Gob gob:objects){
                if(gob.removed||gob.rc==null)continue;
                String resource=GameObjects.resourceName(gob);if(resource==null)continue;
                double range=radius(resource);
                if(range>0){GobHealth health=gob.getattr(GobHealth.class);supports.add(new Support(gob.rc,range,health==null?1:health.hp));}
                if(resource.equals("gfx/terobjs/looserock"))rocks.add(gob.rc);
            }
        }
        String assess(Coord tile){return MiningSafety.assess(center(tile),supports,rocks,threshold());}
    }
    static Coord2d center(Coord tile){return tile.mul(MCache.tilesz).add(MCache.tilesz.div(2));}
    /** Destroy and Mine share gfx/hud/curs/mine; remember the actual outgoing action. */
    public static void action(Widget sender,String message,Object[] args){
        if(!message.equals("act")&&!message.equals("use"))return;
        GameUI gui=sender instanceof GameUI?(GameUI)sender:sender.getparent(GameUI.class);if(gui==null)return;
        State state=state(gui);state.destroying=false;
        if(message.equals("act"))state.destroying=args.length>0&&"destroy".equals(args[0]);
        else if(sender instanceof MenuGrid&&args.length>0)try{
            MenuGrid.Pagina page=((MenuGrid)sender).paginafor(args[0],null);
            Resource.AButton action=page==null?null:page.res().layer(Resource.action);
            if(action!=null)state.destroying=action.ad.length>0?"destroy".equals(action.ad[0]):"Destroy".equals(action.name);
        }catch(Loading pending){} // Unknown actions retain the guard rather than disabling it.
        if(state.destroying){state.selected=null;state.grids.clear();state.stopped=false;}
    }
    /** True while the pickaxe is the active cursor and no destroy action is pending. */
    public static boolean pickaxe(GameUI gui){return gui!=null&&gui.map!=null&&cursor(gui.map);}
    private static boolean cursor(MapView map){
        GameUI gui=map.getparent(GameUI.class);
        if(gui!=null&&state(gui).destroying)return false;
        for(Widget widget=map;widget!=null;widget=widget.parent)if(widget.cursor!=null)
            return widget.cursor.get().name.equals("gfx/hud/curs/mine");
        return false;
    }
    /** Inspect outgoing native commands, including selection made by other local helpers. */
    public static boolean command(MapView map,String message,Object[] args){
        if(message.equals("click")&&args.length>=3&&args[2] instanceof Number&&((Number)args[2]).intValue()==3){
            GameUI gui=map.getparent(GameUI.class);
            if(gui!=null){State state=state(gui);state.selected=null;state.grids.clear();state.stopped=false;}
            return true;
        }
        if(!enabled())return true;
        boolean selection=message.equals("sel")&&args.length>=2&&args[0] instanceof Coord&&args[1] instanceof Coord;
        boolean click=message.equals("click")&&args.length==4&&args[2] instanceof Number&&((Number)args[2]).intValue()==1;
        if(!selection&&!click)return true;
        GameUI gui=map.getparent(GameUI.class);if(gui==null)return true;
        boolean miningCommand=false;
        try {
            if(!cursor(map))return true;
            miningCommand=true;
            if(selection)
                return select(gui,(Coord)args[0],(Coord)args[1]);
            if(click){
                Coord tile=new Coord2d((Coord)args[1]).mul(OCache.posres).floor(MCache.tilesz);
                return select(gui,tile,tile);
            }
        }catch(Loading waiting){
            if(miningCommand||state(gui).selected!=null){block(gui,"Mining data is still loading. Select the area again.");return false;}
            // An ordinary cursor can be loading too. This is not evidence of mining.
            return true;
        }
        return true;
    }
    static boolean select(GameUI gui,Coord one,Coord two){
        Coord ul=Coord.of(Math.min(one.x,two.x),Math.min(one.y,two.y));
        Coord br=Coord.of(Math.max(one.x,two.x)+1,Math.max(one.y,two.y)+1);
        if((long)(br.x-ul.x)*(br.y-ul.y)>10000){block(gui,"Select a smaller mining area.");return false;}
        State state=state(gui);Survey survey=new Survey(gui);Map<Coord,Long> grids=new HashMap<>();
        for(int y=ul.y;y<br.y;y++)for(int x=ul.x;x<br.x;x++){
            Coord tile=Coord.of(x,y);String reason=survey.assess(tile);
            if(reason!=null){block(gui,reason);return false;}
            MCache.Grid grid=gui.ui.sess.glob.map.getgridt(tile);
            if(grid==null)throw new Loading();grids.put(grid.gc,grid.id);
        }
        state.selected=new Area(ul,br);state.grids.clear();state.grids.putAll(grids);state.stopped=false;
        state.status="Selected area is covered; no configured hazard detected.";return true;
    }
    static void block(GameUI gui,String reason){
        State state=state(gui);state.status=reason;state.selected=null;state.grids.clear();
        Walking.cancel(gui.map);
        if(!state.stopped){
            // Escape leaves the action cursor, but does not reliably cancel server-side mining.
            // A ground right-click cancels the active action, including queued area mining.
            Gob player=gui.map.player();
            gui.map.wdgmsg("click",Coord.z,player==null?Coord.z:player.rc.floor(OCache.posres),3,0);
            gui.ui.root.wdgmsg("gk",27);
            gui.ui.error(L10n.format("Mining stopped: %s",L10n.tr(reason)));
        }
        state.stopped=true;
    }
    public static void tick(GameUI gui){
        if(gui.map==null||gui.map.player()==null)return;
        State state=state(gui);double now=Utils.rtime();
        if(now-state.painted>=.5){state.painted=now;updateCoverage(gui,state);}
        if(!enabled()||state.selected==null||now-state.checked<.2)return;state.checked=now;
        try {
            Gob player=gui.map.player();Drawable draw=player.getattr(Drawable.class);
            boolean mineCursor=cursor(gui.map);
            boolean mining=draw instanceof Composite&&(((Composite)draw).hasPose("pickan")
                ||mineCursor&&((Composite)draw).hasPose("choppan"));
            if(!mining)state.stopped=false;
            if(state.stopped)return;
            if(!mineCursor&&!mining){state.selected=null;state.grids.clear();return;}
            // Validate the complete queued command, including pauses and alternate tool animations.
            Survey survey=new Survey(gui);
            for(Coord tile:state.selected){
                MCache.Grid grid=gui.ui.sess.glob.map.getgridt(tile);
                if(grid==null||!Objects.equals(state.grids.get(grid.gc),grid.id)){block(gui,"The mine level changed. Select the area again.");return;}
                String reason=survey.assess(tile);if(reason!=null){block(gui,reason);return;}
            }
        }catch(Loading waiting){block(gui,"Mining data is still loading. Select the area again.");}
    }
    private static void clearCoverage(State state){
        if(state.overlay!=null&&state.overlayMap!=null)state.overlayMap.remove(state.overlay);
        state.overlay=null;state.overlayMap=null;
    }
    private static void updateCoverage(GameUI gui,State state){
        // The shading is shown with the support radii, and only when its box is ticked.
        if(!coverage()||!hearthborn.world.GobMarks.supportRadiiShown()){clearCoverage(state);return;}
        try {
            MiningCoverage next=MiningCoverage.build(gui,new Survey(gui));
            if(state.overlay!=null&&state.overlayMap==gui.ui.sess.glob.map&&state.overlay.same(next))return;
            clearCoverage(state);
            if(!next.cells.isEmpty()){state.overlay=next;state.overlayMap=gui.ui.sess.glob.map;state.overlayMap.add(next);}
        }catch(Loading unavailable){clearCoverage(state);}
    }
    public static OptWnd.Panel settings(OptWnd options,OptWnd.Panel back){
        OptWnd.Panel panel=options.new Panel().resettable("hearthborn/mining/","hearthborn/minesweeper/");
        Panel controls=panel.add(new Panel(options.getparent(GameUI.class)),Coord.z);
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),controls.pos("bl").adds(10,10));
        panel.pack();return panel;
    }
    static class Panel extends Widget {
        final GameUI gui;final Label status;String previous;
        Panel(GameUI gui){
            super(UI.scale(430,240));this.gui=gui;
            add(new CheckBox("Stop mining when a hazard is detected"){{a=enabled();}public void set(boolean value){a=value;Utils.setprefb("hearthborn/mining/guard",value);}},UI.scale(10,10));
            add(new CheckBox("Stop at support health of 50% (otherwise 25%)"){{a=threshold()==.5;}public void set(boolean value){a=value;Utils.setprefb("hearthborn/mining/stop-at-50",value);}},UI.scale(10,40));
            Widget shade=add(new CheckBox("Shade tiles within support coverage"){{a=Utils.getprefb("hearthborn/mining/coverage",true);}public void set(boolean value){a=value;Utils.setprefb("hearthborn/mining/coverage",value);}},UI.scale(10,70));
            shade.settip("The tiles under supports are shaded while the radii of supports are shown, and not otherwise.");
            add(new CheckBox("Show temporary dust clues (~1 to ~8)"){{a=Minesweeper.clues();}public void set(boolean value){a=value;Minesweeper.clues(value);}},UI.scale(10,100));
            add(new Label("Dust clues expire after 30 minutes; they do not prove safety.",UI.scale(405)),UI.scale(10,132));
            add(new Label("Blue tiles are within support range. Ctrl+R controls object radius.",UI.scale(405)),UI.scale(10,170));
            status=add(new Label("",UI.scale(405)){
                public void settext(String value){
                    if(value.equals(texts))return;
                    text.dispose();texts=value;text=f.renderwrap(value,col,UI.scale(405));resize(text.sz());
                }
            },UI.scale(10,205));
        }
        public void tick(double dt){super.tick(dt);String text=L10n.tr(gui==null?"Choose a mining area to check it.":state(gui).status);if(!Objects.equals(text,previous)){previous=text;status.settext(text);}}
    }
    private MiningSafety(){}
}
