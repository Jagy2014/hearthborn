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

package hearthborn.scripts;

import haven.*;
import hearthborn.L10n;
import hearthborn.world.GobMarks;
import java.util.*;
import java.util.function.Predicate;

/**
 * A shore scout: the boat follows the shore on its own and, every few tiles, stops and takes a sample of the water and, in shallows, of the clay.
 * The samples are the ones the water and clay scripts take, one after another.
 *
 * @author Jagy2014
 */
public final class ScoutScripts {
    static final String WATER_PREF = "hearthborn/scout/water", CLAY_PREF = "hearthborn/scout/clay",
        MUSSELS_PREF = "hearthborn/scout/mussels", RIGHT_PREF = "hearthborn/scout/right", EVERY_PREF = "hearthborn/scout/every";
    static final int MIN_EVERY = 2, MAX_EVERY = 80, DEFAULT_EVERY = 10;
    /** How far away a shore is looked for when none is near, in tiles. */
    static final int NEAR = 10, FAR = 40;
    static final String MUSSELS = "gfx/terobjs/herbs/mussels";
    /**
     * How far the scout looks for mussels, in tiles. Full view would be much larger, but the server
     * only accepts a pick from a few tiles away - so we search this far and sail in when needed.
     */
    static final int MUSSEL_TILES = 20;
    /** Distance at which Shift+RMB is issued after an approach. */
    static final int MUSSEL_PICK_TILES = 6;
    static final double MUSSEL_REACH = MUSSEL_TILES * ShoreFollower.TILE;
    static final double MUSSEL_PICK = MUSSEL_PICK_TILES * ShoreFollower.TILE;

    static boolean supports(String id) {return "scout".equals(id);}

    /** True while the shore scout is the active script. */
    public static boolean active(GameUI gui) {
        return ScriptRunner.current(gui) instanceof Task;
    }

    /** Clears sample/best stats in the open scout window (map path clear). */
    public static void clearWindowStats(GameUI gui) {
        ScoutWindow.clearKept(gui);
    }

    static void run(GameUI gui) {
        if(gui == null) return;
        ScoutWindow existing = gui.getchild(ScoutWindow.class);
        // A window in its destruction animation cannot be revived by show().
        if(existing != null && !existing.visible()) {existing.destroy(); existing = null;}
        if(existing != null) {existing.show(); existing.raise(); return;}
        gui.add(new ScoutWindow(gui), UI.scale(250, 150));
    }

    static int every() {return Math.max(MIN_EVERY, Math.min(MAX_EVERY, Utils.getprefi(EVERY_PREF, DEFAULT_EVERY)));}

    /** Clay lies in shallow water: the plain and the coastal kind, not the deep ones. */
    static boolean shallow(String tile) {
        return "gfx/tiles/water".equals(tile) || "gfx/tiles/owater".equals(tile);
    }

    static String tileName(MCache map, Coord tile) {
        return map.tilesetr(map.gettile(tile)).name;
    }

    static void start(GameUI gui) {
        if(gui == null) return;
        ScriptRunner.Task previous = ScriptRunner.current(gui);
        if(previous instanceof Task) {ScriptRunner.cancel(gui); return;}
        Task task = new Task(gui);
        try {
            if(gui.map == null || gui.map.player() == null) throw new IllegalStateException("The map is not ready yet.");
            if(gui.fv != null && gui.fv.current != null) throw new IllegalStateException("Leave combat before scouting.");
            if(task.hand() != null) throw new IllegalStateException("Empty the cursor before starting the script.");
            task.check();
            if(previous != null) ScriptRunner.cancel(gui);
            ScriptRunner.start(task);
            task.start();
        } catch(IllegalStateException failure) {task.error(failure.getMessage());}
    }

    static class Task extends ScriptRunner.Task {
        enum Phase {SAIL, STOP, PROBE}
        /** How often the course is decided, how often the same target is sent again, the time without progress that counts as stuck, in seconds. */
        static final double DECIDE = 0.4, RESEND = 1.5, STUCK = 6, STILL = 0.7, STOP_LIMIT = 8;
        final MapView map;
        final boolean water = Utils.getprefb(WATER_PREF, true), clay = Utils.getprefb(CLAY_PREF, true),
            mussels = Utils.getprefb(MUSSELS_PREF, true), right = Utils.getprefb(RIGHT_PREF, true);
        final double spacing = every() * ShoreFollower.TILE;
        final MCache grid;
        int samples;
        Double bestWater, bestClay;
        Phase phase = Phase.SAIL;
        double heading, travelled, decided, sentAt, progressAt, stopAt, stillSince;
        double loadingAt = Double.NaN;
        Coord2d last, sent, progress, still;
        int stuck;
        ScriptRunner.Task probe;
        boolean started;
        final Deque<String> queue = new ArrayDeque<>();

        Task(GameUI gui) {
            super(gui);
            map = gui.map;
            grid = gui.ui.sess.glob.map;
            cleanup = this::leave;
            hearthborn.map.MapDisplay.Summary summary = hearthborn.map.MapDisplay.scoutSummary(gui);
            samples = summary.taken;
            bestWater = summary.water;
            bestClay = summary.clay;
        }

        @Override void check() {
            if(gui.map != map) throw new IllegalStateException("The map changed. Start the scout again.");
        }
        @Override void cancelled() {
            remember();
            String text = L10n.format("Shore scouting stopped: %d samples.", samples);
            gui.showPlainNotice(text, java.awt.Color.WHITE);
            if(gui.syslog != null) gui.syslog.append(text, java.awt.Color.WHITE);
        }

        void remember() {
            hearthborn.map.MapDisplay.scoutSummary(gui, samples, bestWater, bestClay);
        }

        Gob player() {return map.player();}
        boolean land(Coord tile) {return !WaterScripts.water(grid, tile);}
        Coord2d position() {return player().rc;}

        /** Checks that the scout can start: it needs a boat on the water and at least one thing to do. */
        void prepare() {
            if(!water && !clay && !mussels) throw new IllegalStateException("Choose what to do: water, clay, mussels or several of them.");
            if(!WaterScripts.water(grid, position().floor(MCache.tilesz))) throw new IllegalStateException("Start the scout on the water, in a boat.");
            // The probes check their own conditions, an empty waterskin and no clay in the inventory, before the first sail.
            if(water) new WaterScripts.Task(gui).prepare();
            if(clay) new DigScripts.Task(gui, DigScripts.Material.CLAY).prepare();
            if(ShoreFollower.nearestLand(this::land, position(), FAR) == null) throw new IllegalStateException("No shore within reach.");
        }

        void start() {
            add(this::prepare, () -> true, 15);
            add(() -> {
                started = true;
                gui.ui.msg(L10n.tr("Shore scouting started."));
                // First stop at the launch spot, then sail the usual spacing between further stops.
                beginProbe();
            });
            add(() -> {}, this::tick, 365 * 24 * 3600);
        }

        /** Queue whatever is enabled at this spot and start probing without sailing first. */
        void beginProbe() {
            queue.clear();
            // Samples first at the stop; mussels after, so an approach does not move the probe spot.
            if(water) queue.add("water");
            if(clay) queue.add("clay");
            if(mussels) {
                Gob mussel = nearestMussel(position());
                if(mussel != null && (musselsMayApproach() || mussel.rc.dist(position()) <= MUSSEL_PICK))
                    queue.add("mussels");
            }
            phase = Phase.PROBE;
            nextProbe();
        }

        /** Sailing toward mussels is fine when only mussels run; with water/clay keep the boat put. */
        boolean musselsMayApproach() {return mussels && !water && !clay;}

        /** Stops the probe that is under way and the boat. */
        void leave() {
            endProbe();
            if(started && gui.map == map && map.player() != null) map.wdgmsg("click", Coord.z, position().floor(OCache.posres), 1, 0);
        }

        void endProbe() {
            ScriptRunner.Task running = probe;
            probe = null;
            if(running != null && running.cleanup != null) running.cleanup.run();
        }

        /** One look at the situation; never done by itself, only stopped or failed. */
        boolean tick() {
            double now = Utils.rtime();
            try {
                switch(phase) {
                case SAIL: sail(now); break;
                case STOP: stop(now); break;
                case PROBE: sample(); break;
                }
                loadingAt = Double.NaN;
            } catch(Loading pending) {
                if(Double.isNaN(loadingAt)) {loadingAt = now; if(phase == Phase.SAIL) {halt(); sent = null;}}
                if(now - loadingAt > 15) throw new IllegalStateException("The script timed out waiting for the server.");
            }
            return false;
        }

        void sail(double now) {
            Coord2d position = position();
            if(last != null) travelled += position.dist(last);
            last = position;
            if(travelled >= spacing) {
                phase = Phase.STOP;
                stopAt = now;
                stillSince = now;
                still = position;
                halt();
                return;
            }
            if(progress == null || position.dist(progress) > ShoreFollower.TILE) {progress = position; progressAt = now;}
            else if(now - progressAt > STUCK) {
                if(++stuck >= 3) throw new IllegalStateException("The boat is stuck.");
                // Away from the shore, then along it again.
                heading -= (right ? 1 : -1) * Math.PI / 2;
                progressAt = now;
                sent = null;
            }
            if(now - decided < DECIDE) return;
            decided = now;
            List<Coord2d> threats = threats(position);
            Predicate<Coord2d> hazard = hazard(threats);
            Coord2d menace = nearestThreat(position, threats, GobMarks.dangerRadius());
            if(menace != null) {
                Coord2d flee = flee(position, menace);
                if(flee == null) throw new IllegalStateException("The boat is boxed in by animals.");
                if(sent == null || now - sentAt > RESEND || sent.dist(flee) > ShoreFollower.TILE) {
                    map.wdgmsg("click", Coord.z, flee.floor(OCache.posres), 1, 0);
                    sent = flee;
                    sentAt = now;
                    heading = Math.atan2(flee.y - position.y, flee.x - position.x);
                }
                return;
            }
            if(ShoreFollower.nearestLand(this::land, position, NEAR) == null) {
                Coord2d far = ShoreFollower.nearestLand(this::land, position, FAR);
                if(far == null) throw new IllegalStateException("The shore is out of sight.");
                heading = ShoreFollower.startHeading(position, far, right);
            }
            ShoreFollower.Hop hop = ShoreFollower.next(this::land, hazard, position, heading, right);
            if(hop == null) {
                // After mussels (or a bad heading toward land) realign to the shore and try again.
                hop = realignHop(position, hazard);
            }
            if(hop == null) {
                // Land alone may still leave a way; animals closed the shore path - steer clear first.
                if(ShoreFollower.next(this::land, position, heading, right) == null)
                    throw new IllegalStateException("The boat is boxed in by the shore.");
                Coord2d nearest = nearestThreat(position, threats, GobMarks.dangerRadius() + ShoreFollower.TILE * 4);
                Coord2d flee = nearest == null ? null : flee(position, nearest);
                if(flee == null) throw new IllegalStateException("The boat is boxed in by animals.");
                hop = new ShoreFollower.Hop(Math.atan2(flee.y - position.y, flee.x - position.x), flee);
            }
            heading = hop.heading;
            if(sent == null || now - sentAt > RESEND || sent.dist(hop.target) > ShoreFollower.TILE) {
                map.wdgmsg("click", Coord.z, hop.target.floor(OCache.posres), 1, 0);
                sent = hop.target;
                sentAt = now;
            }
        }

        /** Aggressive animals within sight that the boat must keep outside the red danger circle. */
        List<Coord2d> threats(Coord2d position) {
            List<Coord2d> found = new ArrayList<>();
            double reach = GobMarks.dangerRadius() + ShoreFollower.TILE * 8;
            for(Gob gob : objects()) {
                if(!GobMarks.livingThreat(gob)) continue;
                if(position.dist(gob.rc) > reach) continue;
                found.add(gob.rc);
            }
            return found;
        }

        /** Never inspect a Gob while holding the object-cache monitor. */
        List<Gob> objects() {
            OCache cache = gui.ui.sess.glob.oc;
            List<Gob> copy = new ArrayList<>();
            synchronized(cache) {for(Gob gob : cache) copy.add(gob);}
            return copy;
        }

        Predicate<Coord2d> hazard(List<Coord2d> threats) {
            if(threats.isEmpty()) return null;
            // Keep a tile of margin outside the drawn aggro circle so a moving animal does not catch the boat.
            double keep = GobMarks.dangerRadius() + ShoreFollower.TILE;
            return point -> {
                for(Coord2d threat : threats)
                    if(point.dist(threat) < keep) return true;
                return false;
            };
        }

        Coord2d nearestThreat(Coord2d position, List<Coord2d> threats, double within) {
            Coord2d best = null;
            double nearest = within;
            for(Coord2d threat : threats) {
                double d = position.dist(threat);
                if(d < nearest) {nearest = d; best = threat;}
            }
            return best;
        }

        /** A water hop that increases distance from the animal, trying angles around the flee direction. */
        Coord2d flee(Coord2d position, Coord2d threat) {
            Coord2d away = position.sub(threat);
            double base = away.abs() < 1e-3 ? heading : Math.atan2(away.y, away.x);
            double here = position.dist(threat);
            List<Coord2d> nearby = threats(position);
            for(int k = 0; k < 12; k++) {
                for(int side : new int[] {0, 1, -1}) {
                    if(k == 0 && side != 0) continue;
                    double angle = base + side * k * Math.PI / 12;
                    Coord2d target = position.add(Math.cos(angle) * ShoreFollower.HOP, Math.sin(angle) * ShoreFollower.HOP);
                    if(!ShoreFollower.clear(this::land, position, angle)) continue;
                    if(target.dist(threat) <= here) continue;
                    boolean safe = true;
                    for(Coord2d other : nearby) {
                        for(double d = ShoreFollower.SAMPLE; d <= ShoreFollower.HOP; d += ShoreFollower.SAMPLE) {
                            Coord2d point = position.add(Math.cos(angle) * d, Math.sin(angle) * d);
                            if(point.dist(other) < Math.min(position.dist(other), GobMarks.dangerRadius() + ShoreFollower.TILE) - .01) {safe = false; break;}
                        }
                        if(!safe) break;
                    }
                    if(safe) return target;
                }
            }
            return null;
        }

        /** A click on the boat's own spot stops it. */
        void halt() {map.wdgmsg("click", Coord.z, position().floor(OCache.posres), 1, 0);}

        void stop(double now) {
            Coord2d position = position();
            if(position.dist(still) > 0.3) {still = position; stillSince = now;}
            if(now - stillSince >= STILL || now - stopAt > STOP_LIMIT)
                beginProbe();
        }

        Gob nearestMussel(Coord2d from) {
            Gob best = null;
            double nearest = MUSSEL_REACH;
            for(Gob gob : objects()) {
                    if(gob.removed || gob.rc == null) continue;
                    double d = gob.rc.dist(from);
                    if(d > nearest) continue;
                    try {
                        String name = hearthborn.util.GameObjects.resourceName(gob);
                        if(!MUSSELS.equals(name)) continue;
                    } catch(Loading pending) {continue;}
                    nearest = d;
                    best = gob;
            }
            return best;
        }

        void nextProbe() {
            probe = null;
            while(!queue.isEmpty() && probe == null) {
                String kind = queue.peek();
                if(kind.equals("mussels")) {
                    MusselTask pick = new MusselTask(gui, this);
                    pick.start();
                    if(!pick.steps.isEmpty()) probe = pick;
                } else if(kind.equals("clay")) {
                    if(!shallow(tileName(grid, position().floor(MCache.tilesz)))) {queue.poll(); continue;}
                    DigScripts.Task dig = new DigScripts.Task(gui, DigScripts.Material.CLAY);
                    dig.start();
                    probe = dig;
                } else {
                    WaterScripts.Task fill = new WaterScripts.Task(gui);
                    fill.start();
                    probe = fill;
                }
                queue.poll();
            }
            if(probe == null) {
                // Resolve the shore before committing this stop, so Loading can retry safely.
                realignHeading(position());
                samples++;
                remember();
                travelled = 0;
                phase = Phase.SAIL;

                sent = null;
                progress = null;
                last = null;
                decided = 0;
                stuck = 0;
            }
        }

        void realignHeading(Coord2d position) {
            Coord2d shore = ShoreFollower.nearestLand(this::land, position, FAR);
            if(shore != null) heading = ShoreFollower.startHeading(position, shore, right);
        }

        ShoreFollower.Hop realignHop(Coord2d position, Predicate<Coord2d> hazard) {
            realignHeading(position);
            return ShoreFollower.next(this::land, hazard, position, heading, right);
        }

        void sample() {
            if(probe == null) {nextProbe(); return;}
            boolean done;
            try {done = ScriptRunner.advance(probe);}
            catch(Loading pending) {throw pending;}
            catch(RuntimeException failure) {endProbe(); throw failure;}
            if(done) {noteBest(probe); endProbe(); nextProbe();}
        }

        void noteBest(ScriptRunner.Task finished) {
            boolean changed = false;
            if(finished instanceof WaterScripts.Task) {
                Double q = ((WaterScripts.Task)finished).quality;
                if(q != null && (bestWater == null || q > bestWater)) {bestWater = q; changed = true;}
            } else if(finished instanceof DigScripts.Task) {
                DigScripts.Task dig = (DigScripts.Task)finished;
                if(dig.material != DigScripts.Material.CLAY) return;
                Double q = dig.quality;
                if(q != null && (bestClay == null || q > bestClay)) {bestClay = q; changed = true;}
            }
            if(changed) remember();
        }
    }

    /** Find mussels within {@link #MUSSEL_TILES}, sail in if needed, then Shift+RMB the cluster. */
    static final class MusselTask extends ScriptRunner.Task {
        final Task scout;
        MusselTask(GameUI gui, Task scout) {
            super(gui);
            this.scout = scout;
        }
        @Override void check() {
            if(gui.map != scout.map) throw new IllegalStateException("The map changed. Start the scout again.");
        }
        void start() {pickNext();}
        void pickNext() {
            Gob target = scout.nearestMussel(scout.position());
            if(target == null) return;
            final Gob mussel = target;
            if(mussel.rc.dist(scout.position()) > MUSSEL_PICK) {
                if(!scout.musselsMayApproach()) {
                    // Water/clay keep the boat on the sample spot - skip mussels that are out of pick range.
                    return;
                }
                add(() -> {
                    if(mussel.removed || mussel.rc == null) return;
                    gui.map.wdgmsg("click", Coord.z, mussel.rc.floor(OCache.posres), 1, 0);
                }, () -> mussel.removed || mussel.rc == null || mussel.rc.dist(scout.position()) <= MUSSEL_PICK, 25);
                add(this::pickNext);
                return;
            }
            add(() -> {
                Coord at = mussel.rc.floor(OCache.posres);
                gui.map.wdgmsg("click", Coord.z, at, 3, UI.MOD_SHIFT, 0, (int)mussel.id, at, 0, -1);
            }, () -> mussel.removed || scout.nearestMussel(scout.position()) == null
                || scout.nearestMussel(scout.position()).id != mussel.id, 30);
            add(this::pickNext);
        }
    }

    private ScoutScripts() {}
}
