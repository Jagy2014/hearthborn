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
import hearthborn.io.LocalStore;
import hearthborn.util.GameObjects;
import hearthborn.world.ObjectHighlight;
import java.util.*;

/**
 * Local sightings only: the server's siegeptr response does not enumerate these objects.
 *
 * @author Jagy2014
 */
public final class NearbySiegeCheck {
    private static final String RAM = "gfx/terobjs/vehicle/bram";
    private static final String BALL = "gfx/terobjs/vehicle/wreckingball";
    private static final String RAM_HISTORY = "seen-battering-rams-v1";
    private static final Map<Session, LoginSightings> LOGIN_SIGHTINGS = new WeakHashMap<>();
    private Set<String> savedRams;
    private double next;
    public static boolean enabled(){return Utils.getprefb("hearthborn/siege/nearby",true);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/siege/nearby",value);}
    public static boolean rememberRams(){return Utils.getprefb("hearthborn/siege/remember-rams",false);}
    public static void rememberRams(boolean value){Utils.setprefb("hearthborn/siege/remember-rams",value);}
    public static String kind(String resource){
        if(RAM.equals(resource))return "Battering ram";
        if(BALL.equals(resource))return "Wrecking ball";
        if("gfx/terobjs/vehicle/catapult".equals(resource))return "Catapult";
        return null;
    }
    private static final class Sighting extends GAttrib {
        String resource;
        boolean announced,approached;
        Sighting(Gob gob){super(gob);}
        void resource(String value){
            if(!Objects.equals(resource,value)){resource=value;announced=false;approached=false;}
        }
        public void ctick(double dt){try{resource(GameObjects.resourceName(gob));}catch(Loading pending){}}
    }
    private static Sighting sighting(Gob gob,String resource){
        Sighting state=gob.getattr(Sighting.class);
        if(state==null){state=new Sighting(gob);gob.setattr(state);}
        state.resource(resource);return state;
    }
    public static void lostSight(Gob gob){
        Sighting state=gob.getattr(Sighting.class);
        if(state!=null){state.announced=false;state.approached=false;}
    }
    public static boolean ownsIconSound(String resource){
        return kind(resource)!=null||"gfx/terobjs/vehicle/wreckingball-fold".equals(resource);
    }
    public static boolean pointerVisible(Gob gob,Coord2d player){
        if(gob.removed||gob.rc==null)return false;
        String resource=GameObjects.resourceName(gob);
        if(kind(resource)==null){Sighting old=gob.getattr(Sighting.class);if(old!=null)old.resource(resource);return false;}
        Sighting state=sighting(gob,resource);
        if(player!=null&&player.dist(gob.rc)<3*MCache.tilesz.x)state.approached=true;
        return !state.approached;
    }
    private static java.util.function.Consumer<UI> sound(GameUI gui,Gob gob){
        if(gui.iconconf==null)return null;
        GobIcon icon=gob.getattr(GobIcon.class);
        if(icon==null)icon=hearthborn.map.RadarIcons.icon(gob,null);
        if(icon==null)return null;
        GobIcon.Setting setting=gui.iconconf.get(icon.icon());
        return setting!=null&&setting.notify?setting.notification():null;
    }
    private static final class RamPlace {
        final String resource;
        final long grid;
        final Coord offset;

        RamPlace(String resource, long grid, Coord offset) {
            this.resource = resource;
            this.grid = grid;
            this.offset = offset;
        }

        public boolean equals(Object other) {
            if(!(other instanceof RamPlace)) return false;
            RamPlace place = (RamPlace)other;
            return grid == place.grid && resource.equals(place.resource) && offset.equals(place.offset);
        }

        public int hashCode() {return Objects.hash(resource, grid, offset);}
    }

    private static final class LoginSightings {
        private final Set<String> ids = new HashSet<>();
        private final Set<RamPlace> places = new HashSet<>();

        synchronized boolean seen(String id, RamPlace place) {
            boolean newId = ids.add(id);
            boolean newPlace = place == null || places.add(place);
            return !newId || !newPlace;
        }
    }

    private static synchronized LoginSightings sightings(Session session) {
        return LOGIN_SIGHTINGS.computeIfAbsent(session, unused -> new LoginSightings());
    }

    /** A world handoff can replace both the UI and the server connection without a new login. */
    public static synchronized void transfer(Session from, Session to) {
        LoginSightings previous = LOGIN_SIGHTINGS.get(from);
        if(previous != null) LOGIN_SIGHTINGS.put(to, previous);
    }

    public static synchronized void startLogin(Session session) {
        LOGIN_SIGHTINGS.remove(session);
    }

    private static RamPlace place(Session session, Gob gob, String resource) {
        if(session == null || session.glob == null || session.glob.map == null) return null;
        try {
            Coord2d at = gob.rc;
            MCache.Grid grid = session.glob.map.getgridt(at.floor(MCache.tilesz));
            // Grid ID and a grid-relative coordinate survive rebased session coordinates and Gob IDs.
            Coord offset = at.sub(grid.ul.x * MCache.tilesz.x, grid.ul.y * MCache.tilesz.y).floor(OCache.posres);
            return new RamPlace(resource, grid.id, offset);
        } catch(Loading pending) {
            return null;
        }
    }

    private static List<String> ramKeys(Session session, RamPlace place, String id) {
        List<String> keys = new ArrayList<>(2);
        String world = WorldIdentity.name(session);
        NamedSocketAddress server = Bootstrap.authserv.get();
        String prefix = server.host.toLowerCase(Locale.ROOT) + ":" + server.port + "\n";
        if(world != null) keys.add(prefix + world + "\n" + id);
        // Map grid IDs remain the same when session coordinates change or the world label is missing.
        if(place != null) keys.add(prefix + "grid\n" + Long.toUnsignedString(place.grid) + "\n" + id);
        return keys;
    }

    private Set<String> savedRams() {
        if(savedRams == null) {
            savedRams = new HashSet<>();
            Object stored = LocalStore.read(RAM_HISTORY);
            if(stored instanceof Set<?>)
                for(Object key : (Set<?>)stored)
                    if(key instanceof String) savedRams.add((String)key);
        }
        return savedRams;
    }

    private void saveRam(List<String> keys) {
        if(!keys.isEmpty() && savedRams().addAll(keys))
            LocalStore.write(RAM_HISTORY, new HashSet<>(savedRams));
    }

    void report(GameUI gui,Gob gob,Gob player){
        if(gob.removed||gob.rc==null)return;
        String resource=GameObjects.resourceName(gob),kind=kind(resource);
        if(kind==null){Sighting old=gob.getattr(Sighting.class);if(old!=null)old.resource(resource);return;}
        boolean ram = RAM.equals(resource) || BALL.equals(resource);
        String id = (BALL.equals(resource) ? "ball:" : "") + Long.toUnsignedString(gob.id);
        List<String> keys = Collections.emptyList();
        if(ram) {
            Session session = gui.ui.sess;
            RamPlace place = place(session, gob, resource);
            // Returning through a door can load the engine before its map grid.
            if(place == null && session != null && session.glob != null && session.glob.map != null) return;
            keys = ramKeys(session, place, id);
            boolean remember = rememberRams();
            if(remember && keys.isEmpty()) return; // Wait for an identity that can be checked against the saved history.
            if(sightings(session).seen(id, place) || (remember && keys.stream().anyMatch(savedRams()::contains))) {
                saveRam(keys);
                return;
            }
        }
        Sighting state=sighting(gob,resource);if(!ram && state.announced)return;
        java.util.function.Consumer<UI> sound=sound(gui,gob);
        state.announced=true;
        if(ram) saveRam(keys);
        gob.setattr(new ObjectHighlight(gob));
        String message=L10n.format("Nearby: %s - %d tiles away (loaded objects only).",L10n.tr(kind),(int)Math.ceil(gob.rc.dist(player.rc)/MCache.tilesz.x));
        gui.ui.msg(message,java.awt.Color.WHITE,sound==null?UI.InfoMessage.sfx:null);
        if(sound!=null)sound.accept(gui.ui);
    }
    public void tick(GameUI gui){
        if(!enabled()||gui==null||gui.ui==null||gui.ui.sess==null||gui.map==null)return;
        Gob player=gui.map.player();if(player==null||player.rc==null||Utils.rtime()<next)return;next=Utils.rtime()+2;
        List<Gob> objects=new ArrayList<>();OCache cache=gui.ui.sess.glob.oc;
        synchronized(cache){for(Gob gob:cache)objects.add(gob);}
        for(Gob gob:objects)try{report(gui,gob,player);}catch(Loading pending){}catch(Resource.NoSuchResourceException missing){hearthborn.map.RadarIcons.absent(missing.name);}
    }
}
