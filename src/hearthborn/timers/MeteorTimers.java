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
import hearthborn.world.ObjectNames;
import hearthborn.ui.StudyProgress;
import haven.*;
import java.util.regex.*;
/**
 * A cool-down notice after inspecting or trying to chip a hot meteorite starts a
 * countdown, the same way claim and siege notices create timers.
 *
 * @author Jagy2014
 */
public final class MeteorTimers {
    private static final Pattern COOL=Pattern.compile("^The meteorite is too hot to touch, but will cool down in (\\d+) (hours?|days?)\\.?$",Pattern.CASE_INSENSITIVE);
    private static Gob clicked;
    private static double clickTime;

    public static boolean isMeteorite(String name) {
        return name!=null && name.toLowerCase(java.util.Locale.ROOT).contains("meteorite");
    }

    /** UI.msg calls observers with the original server text, before translation. */
    public static long remainingMillis(String message) {
        if(message==null)return 0;
        Matcher match=COOL.matcher(message.trim());if(!match.matches())return 0;
        try {
            double hours=Double.parseDouble(match.group(1))*(match.group(2).toLowerCase(java.util.Locale.ROOT).startsWith("day")?24:1);
            double millis=hours*3600000d/StudyProgress.GAME_SECONDS_PER_REAL_SECOND;
            return millis>0&&millis<Long.MAX_VALUE?(long)Math.ceil(millis):0;
        }catch(NumberFormatException invalid){return 0;}
    }

    /** Remember the last clicked object; the following cool-down notice owns the timer. */
    public static void clicked(Gob gob) {
        if(gob==null)return;
        clicked=gob;
        clickTime=Utils.rtime();
    }

    public static void notice(UI ui,String message) {
        if(!TimerSettings.automatic() || message==null || ui==null)return;
        long remaining=remainingMillis(message);if(remaining<=0)return;
        long deadline=System.currentTimeMillis()+remaining;
        Gob gob=usable(ui);
        String source="meteorite:cool-down";
        String title=L10n.tr("Meteorite cool-down");
        if(gob!=null){
            try {
                MCache.Grid grid=gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));
                Coord local=gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
                source="meteorite:"+grid.id+":"+local.x+":"+local.y;
                ObjectNames.bind(source,gob);
                title=ObjectNames.timerName(source,title);
            }catch(Loading ignored){}
        }
        long old=Timers.deadline(source);
        if(old>System.currentTimeMillis() && Math.abs(old-deadline)<5000)return;
        Timers.track(source,title,deadline);
        ui.msg(L10n.tr("Meteorite cool-down timer started."));
    }

    private static Gob usable(UI ui) {
        if(clicked==null || clicked.removed || ui.sess==null || clicked.glob!=ui.sess.glob || Utils.rtime()-clickTime>60)return null;
        try {
            String name=GameObjects.resourceName(clicked);
            if(name!=null && !isMeteorite(name))return null;
        }catch(Loading ignored){}
        return clicked;
    }

    private MeteorTimers() {}
}
