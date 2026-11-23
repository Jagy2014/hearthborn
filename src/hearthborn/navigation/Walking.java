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

package hearthborn.navigation;
import haven.*;import java.util.*;import java.awt.geom.Area;import java.awt.geom.Rectangle2D;import java.awt.geom.Ellipse2D;import java.util.concurrent.*;
/**
 * Local navigation for ground clicks and approaching stationary objects.
 *
 * @author Jagy2014
 */
public final class Walking {
    private static final Map<MapView,Walking> active=new WeakHashMap<>();
    private static final Map<MapView,TreeMenu> treeMenus=new WeakHashMap<>();
    private static class TreeMenu {
        Gob target;Coord2d goal;Object[] args;double when=Utils.rtime();
        TreeMenu(Gob target,Coord2d goal,Object[] args){this.target=target;this.goal=goal;this.args=args.clone();}
    }
    private Runnable arrived,cancelled;
    private boolean scriptControlled;
    private static final ExecutorService worker=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"Hearthborn route search");t.setDaemon(true);return t;});
    private final MapView map;private final Coord2d goal;private final Object[] original;private final Gob interaction;private final boolean automatic;private final long moverId;
    private static class SearchResult {
        final Coord2d start;final List<Coord2d> points;final List<Area> obstacles;
        SearchResult(Coord2d start,List<Coord2d> points,List<Area> obstacles){this.start=start;this.points=points;this.obstacles=obstacles;}
    }
    private java.util.concurrent.Future<SearchResult> search;private List<Coord2d> route;private int next,retries;
    private Coord2d retryOrigin;private double progressed,sent,closest;private boolean issued;
    private int replans;
    private final List<Area> failed=new ArrayList<>();
    private Walking(MapView map,Coord2d goal,Object[] original,Gob interaction){this.map=map;this.goal=goal;this.original=original;this.interaction=interaction;this.automatic=enabled();Gob body=movementBody(map);this.moverId=body==null?-1:body.id;}
    /** A rider's rc stays at the mounting point; movement must follow the horse instead. */
    private static Gob movementBody(MapView map){
        Gob rider=map.player();if(rider==null)return null;
        // Boat drivers (notably coracles) need not have Following. Use the
        // server pose as well, including the idle state before the first stroke.
        try {
            Drawable drawable=rider.getattr(Drawable.class);
            if(drawable instanceof Composite)for(String pose:new String[]{"rowing","rowboat-d","dugoutidle","dugoutrowan","coracleidle","coraclerowan"})
                if(((Composite)drawable).hasPose(pose))return null;
        }catch(Loading waiting){return null;}
        Following riding=rider.getattr(Following.class);if(riding==null)return rider;
        Gob mount=riding.tgt();if(mount==null)return null;
        try {String resource=hearthborn.util.GameObjects.resourceName(mount);
            return resource!=null && resource.startsWith("gfx/kritter/horse/")?mount:null;
        }catch(Loading waiting){return null;}
    }
    /** Water uses native movement, including shore entry and not just deep water. */
    private static boolean water(MapView map,Coord2d at){
        try {
            MCache terrain=map.ui.sess.glob.map;
            return terrain.tiler(terrain.gettile(at.floor(MCache.tilesz))) instanceof haven.resutil.WaterTile;
        }catch(Loading waiting){return true;}
    }
    private static boolean nativeMovement(MapView map,Coord2d destination){
        Gob body=movementBody(map);
        return body==null||water(map,body.rc)||water(map,destination);
    }
    public static boolean enabled(){return Utils.getprefb("hearthborn/walking/avoid-obstacles",true);}
    /** Routes while riding a horse. Off leaves those clicks to the game's own movement. */
    public static boolean mountedRouting(){return Utils.getprefb("hearthborn/walking/mounted",true);}
    public static boolean requested(int modifiers){return modifiers==(enabled()?0:UI.MOD_CTRL);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/walking/avoid-obstacles",value);}
    public static void mountedRouting(boolean value){Utils.setprefb("hearthborn/walking/mounted",value);}
    private static boolean ridingHorse(MapView map){
        Gob rider=map.player();Gob body=movementBody(map);
        return rider!=null && body!=null && body!=rider;
    }
    public static boolean start(MapView map,Coord2d destination,Object[] args) {
        return start(map,destination,args,null);
    }
    /** The owning script ticks this route inside its command scope, including pause handling. */
    public static boolean startScript(MapView map,Coord2d destination,Object[] args){
        return startScript(map,destination,args,null);
    }
    public static boolean startScript(MapView map,Coord2d destination,Object[] args,Gob target){
        if(!start(map,destination,args,target))return false;
        Walking walk=active.get(map);if(walk==null)return false;
        walk.scriptControlled=true;
        // The script issues the interaction after arrival, inside its next queue step.
        if(target!=null)walk.arrived=()->{};
        return true;
    }
    public static boolean scriptRunning(MapView map){Walking walk=active.get(map);return walk!=null&&walk.scriptControlled;}
    public static void tickScript(MapView map){
        Walking walk=active.get(map);if(walk==null||!walk.scriptControlled)return;
        GameUI gui=map.getparent(GameUI.class);
        if(gui!=null&&gui.prog!=null){channel(map);return;}
        walk.update();
    }
    public static boolean start(MapView map,Coord2d destination,Object[] args,Gob target) {
        // A progress bar is the current action. A plain ground click must reach the server and stop it.
        GameUI gui=map.getparent(GameUI.class);
        if(gui!=null && gui.prog!=null){cancel(map);return false;}
        // Ctrl belongs to native dismount/vehicle handling while following a mount,
        // even when automatic routing is off and Ctrl normally requests a route.
        Gob rider=map.player();
        if((((Number)args[3]).intValue()&UI.MOD_CTRL)!=0 && rider!=null && rider.getattr(Following.class)!=null){cancel(map);return false;}
        if(!mountedRouting() && ridingHorse(map)){cancel(map);return false;}
        if(indoors(map)){cancel(map);return false;}
        if(nativeMovement(map,destination)){cancel(map);return false;}
        // Creature interactions, including carcass actions, use the server's reach.
        // Do this before remembering the menu, or its selection would route to the body center.
        if(target!=null && creature(target)){cancel(map);return false;}
        Gob player=movementBody(map);
        String resource=null;
        if(target!=null)try{resource=hearthborn.util.GameObjects.resourceName(target);}catch(Loading loading){return false;}
        // Fence pieces and gates use the original right-click, not a route to the solid mesh.
        if(((Number)args[2]).intValue()==3 && nativeInteraction(resource)){cancel(map);return false;}
        // Stairs/ladders still need the server's entrance handling rather than their object center.
        if(transition(resource)&&!resource.endsWith("door"))return false;
        // Object actions belong to the server's context menu. Route only after selection.
        // Architectural entrances can act immediately, without ever creating a menu.
        if(target!=null && !entrance(resource) && ((Number)args[2]).intValue()==3 && player!=null
                && !inCombat(map) && ordinaryCursor(map) && requested(((Number)args[3]).intValue())
                && player.rc.dist(destination)>MCache.tilesz.x && player.rc.dist(destination)<=440) {
            cancel(map);
            treeMenus.put(map,new TreeMenu(target,destination,args));
            return false;
        }
        if(target!=null) {
            try {
                if(resource==null||resource.startsWith("gfx/kritter/")||resource.startsWith("gfx/borka/")||target.getattr(Moving.class)!=null)return false;}
            catch(Loading loading){return false;}
        }
        if(inCombat(map)||!ordinaryCursor(map)||!requested(((Number)args[3]).intValue())||player==null||player.rc.dist(destination)>440)return false;
        if(target!=null && player.rc.dist(destination)<=MCache.tilesz.x)return false;
        cancel(map);Object[] click=args.clone();if(!enabled())click[3]=0;Walking walk=new Walking(map,destination,click,target);active.put(map,walk);walk.plan();return true;
    }
    private static boolean creature(Gob target) {
        try {
            String resource=hearthborn.util.GameObjects.resourceName(target);
            return resource==null || resource.startsWith("gfx/kritter/") || resource.startsWith("gfx/borka/");
        }catch(Loading waiting){return true;}
    }
    public static boolean gate(String resource){return resource!=null && resource.startsWith("gfx/terobjs/arch/") && resource.contains("gate");}
    private static boolean nativeInteraction(String resource) {
        if(gate(resource)||vehicle(resource)||"gfx/terobjs/thingwall".equals(resource))return true;
        String prefix="gfx/terobjs/arch/";
        if(resource==null||!resource.startsWith(prefix))return false;
        String piece=resource.substring(prefix.length());
        if(piece.endsWith("cp"))piece=piece.substring(0,piece.length()-2);
        else if(piece.endsWith("seg"))piece=piece.substring(0,piece.length()-3);
        else return false;
        return piece.equals("pole")||piece.equals("brickwall")||piece.equals("drystonewall")||piece.equals("palisade");
    }
    public static boolean vehicle(String resource){return resource!=null && resource.startsWith("gfx/terobjs/vehicle/");}
    private static boolean entrance(String resource){return resource!=null&&(resource.startsWith("gfx/terobjs/arch/")||transition(resource));}
    public static boolean transition(String resource) {
        return resource!=null && (resource.equals("gfx/terobjs/arch/cellardoor") || resource.endsWith("/ladder")
            || resource.endsWith("/stairs") || resource.endsWith("/cellarstairs")
            || resource.equals("gfx/terobjs/arch/upstairs") || resource.equals("gfx/terobjs/arch/downstairs"));
    }
    public static Coord2d interactionPoint(Gob target,ClickData click,Coord2d fallback) {
        try {if(target!=null && transition(hearthborn.util.GameObjects.resourceName(target)))return target.rc;}
        catch(Loading ignored){}
        return interactionPoint(click,fallback);
    }
    /** Ground picking ignores the height of the clicked door; use its rendered mesh transform instead. */
    public static Coord2d interactionPoint(ClickData click,Coord2d fallback) {
        if(click==null)return fallback;
        FastMesh.ResourceMesh mesh=null;
        for(Object node:click.array())if(node instanceof FastMesh.ResourceMesh)mesh=(FastMesh.ResourceMesh)node;
        if(mesh==null)return fallback;
        try {
            Volume3f bounds=mesh.bounds();
            Coord3f center=bounds.n.add(bounds.p).mul(.5f);
            Coord3f world=haven.render.Homo3D.locxf(click.slot.state()).mul4(center);
            return Float.isFinite(world.x)&&Float.isFinite(world.y)?Coord2d.of(world.x,-world.y):fallback;
        }catch(Loading loading){return fallback;}
    }
    public static boolean inCombat(MapView map) {
        GameUI gui=map.getparent(GameUI.class);return gui!=null && gui.fv!=null && !gui.fv.lsrel.isEmpty();
    }
    private static boolean indoors(MapView map){
        GameUI gui=map.getparent(GameUI.class);return gui!=null&&gui.indoors();
    }
    private static boolean ordinaryCursor(MapView map) {
        try {for(Widget w=map;w!=null;w=w.parent)if(w.cursor!=null)return w.cursor.get().name.equals("gfx/hud/curs/arw");}
        catch(Loading loading){return false;}
        return true;
    }
    public static void cancel(MapView map){
        if(map==null)return;
        map.cancelPendingClicks();
        treeMenus.remove(map);Walking walk=active.remove(map);
        if(walk!=null){if(walk.search!=null)walk.search.cancel(true);if(walk.cancelled!=null){Runnable callback=walk.cancelled;walk.cancelled=null;callback.run();}}
    }
    /** Drop a route without a ground click or a menu cancel. Either would abort a teleport channel. */
    public static void abandon(MapView map){
        if(map==null)return;
        map.cancelPendingClicks();
        treeMenus.remove(map);Walking walk=active.remove(map);
        if(walk==null)return;
        if(walk.search!=null)walk.search.cancel(true);
        walk.arrived=null;walk.cancelled=null;
    }
    /** Every frame of a progress bar: drop a route if there is one, but never a click the player has just made and that waits for its picking result. */
    private static void channel(MapView map){
        if(active.containsKey(map))abandon(map);else treeMenus.remove(map);
    }
    public static boolean hearthAction(String[] command){
        return command!=null && command.length>=2 && "travel".equals(command[0]) && "hearth".equals(command[1]);
    }
    private static boolean hearthTeleport(String option){
        if(option==null)return false;
        String name=option.toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("travel") && name.contains("hearth");
    }
    public static boolean routeTreeAction(MapView map,Gob target,String option,Runnable action,Runnable cancel){
        // Hearth travel is a server teleport. Walking to the fire would replace it with waypoint clicks.
        if(hearthTeleport(option)){abandon(map);return false;}
        // An inventory petal has no world object. Leave a pending tree or door menu for its own window.
        if(target==null)return false;
        if(!mountedRouting() && ridingHorse(map)){cancel(map);return false;}
        TreeMenu menu=treeMenus.remove(map);
        // Sleeping uses a server-defined position on the bed, not ordinary object reach.
        // Let FlowerMenu send the chosen action even when the bed's side is blocked.
        if("Sleep".equals(option)||indoors(map))return false;
        if(menu!=null && nativeMovement(map,menu.goal)){cancel(map);return false;}
        if(menu==null||menu.target!=target||target==null||target.removed||creature(target)||Utils.rtime()-menu.when>60||inCombat(map)||!requested(((Number)menu.args[3]).intValue()))return false;
        try {if(nativeInteraction(hearthborn.util.GameObjects.resourceName(target))){cancel(map);return false;}}
        catch(Loading waiting){return false;}
        Gob player=movementBody(map);if(player==null||player.rc.dist(menu.goal)<=MCache.tilesz.x||player.rc.dist(target.rc)<=MCache.tilesz.x)return false;
        cancel(map);Object[] args=menu.args.clone();if(!enabled())args[3]=0;
        Walking walk=new Walking(map,menu.goal,args,target);walk.arrived=action;walk.cancelled=cancel;active.put(map,walk);walk.plan();return true;
    }
    private void plan() {
        Gob player=movementBody(map);if(player==null){cancel(map);return;}
        Coord2d start=player.rc;progressed=Utils.rtime();issued=false;route=null;
        List<Gob> objects=new ArrayList<>();synchronized(map.ui.sess.glob.oc){for(Gob gob:map.ui.sess.glob.oc)if(gob!=map.player() && gob.rc.dist(start)<550)objects.add(gob);}
        List<Area> blocks=new ArrayList<>(failed);
        search=worker.submit(() -> {
            List<Area> geometry=hearthborn.world.ObjectGeometry.obstacles(objects,player);
            if(player!=map.player())for(Area obstacle:geometry)
                obstacle.add(new Area(new java.awt.BasicStroke(5,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND).createStrokedShape(obstacle)));
            blocks.addAll(geometry);
            terrain(map.ui.sess.glob.map,start,goal,blocks);
            return new SearchResult(start,LocalPathfinder.route(start,goal,blocks,interaction!=null),blocks);
        });
    }
    private static void terrain(MCache map,Coord2d from,Coord2d to,List<Area> blocks) {
        Coord first=Coord2d.of(Math.min(from.x,to.x)-88,Math.min(from.y,to.y)-88).floor(MCache.tilesz);
        Coord last=Coord2d.of(Math.max(from.x,to.x)+88,Math.max(from.y,to.y)+88).floor(MCache.tilesz);
        for(int y=first.y;y<=last.y;y++)for(int x=first.x;x<=last.x;x++) {
            if(Thread.currentThread().isInterrupted())return;
            boolean blocked;
            try {
                Resource tile=map.tilesetr(map.gettile(Coord.of(x,y)));
                String name=tile==null?null:tile.name;
                blocked=name==null||name.equals("gfx/tiles/deep")||name.equals("gfx/tiles/cave")||name.equals("gfx/tiles/nil")||name.startsWith("gfx/tiles/rocks/");
            }catch(Loading loading){blocked=true;}
            if(blocked)blocks.add(new Area(new Rectangle2D.Double(x*MCache.tilesz.x-2.5,y*MCache.tilesz.y-2.5,MCache.tilesz.x+5,MCache.tilesz.y+5)));
        }
    }
    public static void tick(MapView map) {
        Walking current=active.get(map);if(current!=null&&current.scriptControlled)return;
        // A progress bar is a server channel, including the hearth teleport. The next waypoint click would turn it into a walk.
        GameUI gui=map.getparent(GameUI.class);
        if(gui!=null && gui.prog!=null){channel(map);return;}
        Walking walk=active.get(map);if(walk!=null)walk.update();
    }
    private void update() {
        if(indoors(map)||nativeMovement(map,goal)){cancel(map);return;}
        Gob player=movementBody(map);
        if(!mountedRouting() && player!=null && map.player()!=null && player!=map.player()){cancel(map);return;}
        if(inCombat(map)||!ordinaryCursor(map)||automatic!=enabled()||player==null||player.id!=moverId){cancel(map);return;}
        if(interaction!=null && interaction.removed){cancel(map);return;}
        double now=Utils.rtime();
        if(route==null) {
            if(search==null||!search.isDone())return;
            SearchResult result=null;
            try {result=search.get();route=result.points;}
            catch(InterruptedException interrupted){Thread.currentThread().interrupt();cancel(map);return;}
            catch(CancellationException cancelled){cancel(map);return;}
            catch(ExecutionException failed){new Warning(failed.getCause(),"local route search failed").issue();route=null;}
            search=null;
            if(route==null) {
                // A click on a fence, its corner or any other solid object has no route to an exact point inside it. The click is the player's: send it as it is.
                if(interaction==null&&result!=null&&inside(result.obstacles,goal)){cancel(map);map.wdgmsg("click",original);return;}
                fail();
                return;
            }
            next=0;progressed=now;
            // The previous server movement continues during search. Reconnect
            // from the live position rather than walking back to an old corner.
            if(player.rc.dist(result.start)>1.5) {
                if(route.isEmpty()) {
                    if(player.rc.dist(goal)>MCache.tilesz.x){plan();return;}
                } else {
                    int reconnect=-1;
                    for(int i=route.size()-1;i>=0;i--)
                        if(LocalPathfinder.clear(player.rc,route.get(i),result.obstacles)){reconnect=i;break;}
                    if(reconnect<0){plan();return;}
                    next=reconnect;
                }
            }
        }
        if(retryOrigin!=null&&player.rc.dist(retryOrigin)>MCache.tilesz.x*2){retries=0;retryOrigin=null;}
        while(next<route.size()&&player.rc.dist(route.get(next))<1.5){next++;issued=false;}
        if(next>=route.size()) {
            if(interaction!=null)sendOriginal();
            cancel(map);return;
        }
        Coord2d point=route.get(next);
        double distance=player.rc.dist(point);
        // Sliding or jittering at an obstacle is not progress toward the waypoint.
        // Compare with the best distance, so alternating positions cannot postpone recovery forever.
        if(issued&&distance<closest-.5){closest=distance;progressed=now;}
        if(!issued) {
            if(interaction==null && next==route.size()-1)map.wdgmsg("click",original);
            else map.wdgmsg("click",Coord.z,point.floor(OCache.posres),1,0);
            issued=true;closest=distance;sent=progressed=now;
        } else if(now-progressed>1.8 && now-sent>1.8) {
            if(++retries>6||++replans>12){
                fail();
                return;
            }
            if(retryOrigin==null)retryOrigin=player.rc;
            Coord2d direction=route.get(next).sub(player.rc);double length=direction.abs();
            if(length>0) {
                // A tiny disk just ahead can send us back into the same unseen collision.
                // Increase the detour while stuck nearby, keeping the current position clear.
                double radius=Math.min(MCache.tilesz.x*2,MCache.tilesz.x/2*Math.pow(2,retries-1));
                Coord2d at=player.rc.add(direction.mul((radius+1.5)/length));
                failed.add(new Area(new Ellipse2D.Double(at.x-radius,at.y-radius,radius*2,radius*2)));
            }
            plan();
        }
    }
    private static boolean inside(List<Area> obstacles,Coord2d point){
        for(Area area:obstacles)if(area.contains(point.x,point.y))return true;
        return false;
    }
    private void fail(){
        // A chosen menu action must not be lost with the route: the server walks to the object on its own, as in the plain game.
        Runnable action=arrived;arrived=null;cancelled=null;
        cancel(map);
        if(action!=null){action.run();return;}
        Gob player=movementBody(map);
        if(player!=null)map.wdgmsg("click",Coord.z,player.rc.floor(OCache.posres),3,0);
        // A failed local route is routine during repeated map clicks. Its owner
        // can report a task failure; ordinary walking must not show error popups.
    }
    private void sendOriginal() {
        if(arrived!=null){Runnable action=arrived;arrived=null;cancelled=null;action.run();return;}
        if(interaction!=null) {
            if(interaction.removed)return;
            hearthborn.timers.ObjectTimers.clicked(interaction);
        }
        map.wdgmsg("click",original);
    }

}
