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

package hearthborn.world;
import haven.*;import hearthborn.L10n;import java.awt.Color;import java.util.regex.*;
/**
 * The same game-to-wall-clock annotation used by cooldown notices.
 *
 * @author Jagy2014
 */
public final class RealTime {
    private static final Pattern TIME=Pattern.compile("(?i)(?:in|another) (\\d+) (hours?|days?)");
    public static String annotate(String original,String translated) {
        if(original==null || original.contains(" RL"))return translated;
        Matcher match=TIME.matcher(original);if(!match.find())return translated;
        boolean days=match.group(2).toLowerCase(java.util.Locale.ROOT).startsWith("day");
        double minutes=Double.parseDouble(match.group(1))*(days?24:1)*60/3.29;
        if(!Double.isFinite(minutes)||minutes>Long.MAX_VALUE)return translated;
        long total=Math.max(1,Math.round(minutes));
        String real=L10n.format("%s h %s min real time",Long.toString(total/60),Long.toString(total%60));
        return translated+" ("+(days?L10n.tr("up to")+" ":"")+real+")";
    }
    public static UI.Notice wrap(UI.Notice notice) {return wrap(null,notice);}
    public static UI.Notice wrap(UI ui,UI.Notice notice) {
        if(notice==null)return null;
        String original=notice.message();if(original==null)return notice;
        String forecast=hearthborn.meteor.StarFall.realTime(ui,original);
        String translated=L10n.text("msg",original);
        String text=forecast==null?annotate(original,translated):translated+" ("+L10n.tr("up to")+" "+forecast+")";
        if(text.equals(original))return notice;
        return new UI.Notice() {
            public String message(){return text;}
            public Color color(){return notice.color();}
            public Audio.Clip sfx(){return notice.sfx();}
            public boolean handle(Widget w){return notice.handle(w);}
            public boolean handler(Widget w){return notice.handler(w);}
        };
    }
    private RealTime() {}
}
