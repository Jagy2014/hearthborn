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

package hearthborn.timers;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.util.ObjectWindows;
import haven.*;import hearthborn.timers.Timers;import java.util.*;import java.util.regex.*;
/**
 * A placement intent must be followed by an actual claim pole before starting its activation timer.
 *
 * @author Jagy2014
 */
public final class ClaimTimers {
    public static final long WAIT=8*3600000L;
    static final String EXPANSION_PREFIX = "claim-expansion:";
    static final String EXPANSION_TITLE = "Personal claim expansion";
    private static final Map<GameUI,List<Pending>> pending=new WeakHashMap<>();
    private static final Pattern EXPANSION_REMAIN = Pattern.compile(
        "^Your claim to this land is too recent to be expanded\\. It needs to remain for another ([0-9]+) (minutes?|hours?|days?)\\.?$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern IDOL_REMAIN = Pattern.compile(
        "^The idol is not ready until in ([0-9]+) (minutes?|hours?|days?)\\.?$", Pattern.CASE_INSENSITIVE);
    private static Gob clicked;
    private static double clickTime;
    private static class Pending {
        final Coord2d at;final long time=System.currentTimeMillis();final Set<Long> existing=new HashSet<>();
        Pending(Coord2d at){this.at=at;}
    }
    public static boolean isClaim(String name) {return "gfx/terobjs/pclaim".equals(name);}
    public static void clicked(Gob gob) {clicked=gob;clickTime=Utils.rtime();}
    public static void placing(MapView map,Gob ghost,int button) {
        if(button!=1 || !hearthborn.timers.TimerSettings.automatic())return;
        GameUI gui=map.getparent(GameUI.class);if(gui==null)return;
        try {
            if(!isClaim(GameObjects.resourceName(ghost)))return;
            Pending entry=new Pending(ghost.rc);
            synchronized(map.ui.sess.glob.oc) {for(Gob gob:map.ui.sess.glob.oc)if(gob.rc.dist(entry.at)<3)try {if(isClaim(GameObjects.resourceName(gob)))entry.existing.add(gob.id);}catch(Loading ignored){}}
            pending.computeIfAbsent(gui,g -> new ArrayList<>()).add(entry);
        }catch(Loading ignored){}
    }
    public static void observe(GameUI gui,List<Gob> objects) {
        List<Pending> entries=pending.get(gui);if(entries==null)return;
        for(Iterator<Pending> it=entries.iterator();it.hasNext();) {
            Pending entry=it.next();
            if(System.currentTimeMillis()-entry.time>1800000){it.remove();continue;}
            for(Gob gob:objects)try {
                if(!entry.existing.contains(gob.id)&&gob.rc.dist(entry.at)<3&&isClaim(GameObjects.resourceName(gob))) {
                    start(gob,System.currentTimeMillis()+WAIT);
                    gui.ui.msg(L10n.tr("Personal claim activation timer started (8 real hours)."));it.remove();break;
                }
            }catch(Loading ignored){}
        }
        if(entries.isEmpty())pending.remove(gui);
    }
    private static void start(Gob gob,long deadline) {
        String key=objectKey("claim:",gob);
        Timers.track(key,L10n.format("Personal claim #%s",Integer.toHexString(key.hashCode())),deadline);
    }
    private static String objectKey(String prefix,Gob gob) {
        MCache.Grid grid=gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));
        Coord local=gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
        return prefix+grid.id+":"+local.x+":"+local.y;
    }
    public static long idolRemainingMillis(String message) {
        return remainingMillis(IDOL_REMAIN, message);
    }
    public static long expansionRemainingMillis(String message) {
        return remainingMillis(EXPANSION_REMAIN, message);
    }
    private static long remainingMillis(Pattern pattern,String message) {
        Matcher match=pattern.matcher(message);
        if(!match.matches()) return 0;
        double minutes=Double.parseDouble(match.group(1));
        String unit=match.group(2).toLowerCase(Locale.ROOT);
        if(unit.startsWith("day")) minutes*=24*60;
        else if(unit.startsWith("hour")) minutes*=60;
        double duration=minutes*60000/hearthborn.ui.StudyProgress.GAME_SECONDS_PER_REAL_SECOND;
        return duration>0 && duration<Long.MAX_VALUE ? (long)Math.ceil(duration) : 0;
    }
    static void observeIdol(Gob gob,String message) {
        if(!TimerSettings.automatic() || gob.removed || gob.rc==null ||
           !"gfx/terobjs/villageidol".equals(GameObjects.resourceName(gob))) return;
        refresh("village-idol:", "Village idol readiness", gob, idolRemainingMillis(message));
    }
    private static void refresh(String prefix,String name,Gob gob,long duration) {
        long now=System.currentTimeMillis();
        if(duration<=0 || duration>=Long.MAX_VALUE-now) return;
        long deadline=now+duration;
        String key=objectKey(prefix,gob);
        long previous=Timers.deadline(key);
        // The server rounds its estimate; repeated clicks should not push the deadline back.
        if(previous<=now || deadline<previous)
            Timers.track(key,L10n.tr(name),deadline);
    }

    public static Gob claimWindow(Window window,GameUI gui) {
        if(!"Claim Stake".equals(window.cap) && !"Land Management".equals(window.cap)) return null;
        return ObjectWindows.target(window,gui,gob -> isClaim(GameObjects.resourceName(gob)));
    }

    public static void notice(UI ui,String message) {
        if(message==null || ui==null || ui.sess==null || !TimerSettings.automatic()) return;
        Gob recent=clicked!=null && clicked.glob==ui.sess.glob && Utils.rtime()-clickTime<=60 ? clicked : null;
        if(recent!=null) {
            try {observeIdol(recent,message);} catch(Loading ignored) {}
        }
        long duration=expansionRemainingMillis(message);
        if(duration<=0) return;
        try {
            Gob claim=null;
            GameUI gui=ui.root==null ? null : ui.root.getchild(GameUI.class);
            if(gui!=null) for(Window window:gui.children(Window.class)) {
                if(!window.visible()) continue;
                Gob target=claimWindow(window,gui);
                if(target==null || target.removed || target.glob!=ui.sess.glob) continue;
                if(claim!=null && claim!=target) return;
                claim=target;
            }
            if(claim==null) claim=recent;
            if(claim!=null && !claim.removed && claim.rc!=null && isClaim(GameObjects.resourceName(claim)))
                refresh(EXPANSION_PREFIX, EXPANSION_TITLE, claim, duration);
        } catch(Loading ignored) {
        }
    }
    private ClaimTimers() {}
}
