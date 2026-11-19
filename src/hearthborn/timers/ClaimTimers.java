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
import haven.*;import hearthborn.timers.Timers;import java.util.*;import java.util.regex.*;
/**
 * A placement intent must be followed by an actual claim pole before starting its activation timer.
 *
 * @author Jagy2014
 */
public final class ClaimTimers {
    public static final long WAIT=8*3600000L;
    private static final Map<GameUI,List<Pending>> pending=new WeakHashMap<>();
    private static final Pattern REMAIN=Pattern.compile("Your claim to this land is too recent.*another (\\d+) hours?\\.",Pattern.CASE_INSENSITIVE);
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
        MCache.Grid grid=gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));
        Coord local=gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
        String key="claim:"+grid.id+":"+local.x+":"+local.y;
        Timers.track(key,L10n.format("Personal claim #%s",Integer.toHexString(key.hashCode())),deadline);
    }
    public static void notice(UI ui,String message) {
        if(message==null||clicked==null||Utils.rtime()-clickTime>60||!hearthborn.timers.TimerSettings.automatic())return;
        Matcher match=REMAIN.matcher(message);if(!match.matches())return;
        try {if(isClaim(GameObjects.resourceName(clicked)))start(clicked,System.currentTimeMillis()+(long)Math.ceil(Double.parseDouble(match.group(1))*3600000d/3.29));}catch(Loading ignored){}
    }
    private ClaimTimers() {}
}
