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

package hearthborn;

import haven.Utils;
import java.awt.Color;

/**
 * How the quality mark on an item icon looks: shown or not, rounded or exact, and tinted by tier.
 *
 * @author Jagy2014
 */
public final class QualityStyle {
    /**
     * Four bands from the bottom: poor (red) under {@link #ORDINARY_FROM}, good (white) up to the wanted range, the wanted
     * range itself (green), and above it excellent (orange). Red, not grey: grey is the ordinary text colour in many menus
     * and would not stand out. Colours and the poor and good limits are fixed; only the wanted range is chosen. The range
     * includes both ends, so 100 to 200 is wanted and 201 is excellent.
     */
    public enum Tier {
        HIGH("Excellent",new Color(255,150,40)), GOOD("Wanted",new Color(90,230,90)),
        COMMON("Good",Color.WHITE), JUNK("Poor",new Color(215,75,75));
        public final String label;public final Color fallback;
        Tier(String label,Color color){this.label=label;this.fallback=color;}
        /** The chosen colour of the band, or the default one. */
        public Color color(){return new Color(Utils.getprefi(COLOR_KEY+name(),fallback.getRGB())&0xffffff);}
        public void color(Color value){Utils.setprefi(COLOR_KEY+name(),value.getRGB()&0xffffff);changed();}
        /** The lowest quality that belongs to the band. */
        public int threshold(){
            switch(this){
            case HIGH:return wantedTo()+1;
            case GOOD:return wantedFrom();
            case COMMON:return ORDINARY_FROM;
            default:return 0;
            }
        }
    }
    private static final String COLOR_KEY="hearthborn/quality/color-";
    /** Poor is everything under this quality. */
    public static final int ORDINARY_FROM=10;
    /** The wanted range can be set anywhere from the poor limit up to this quality. */
    public static final int RANGE_LIMIT=500;
    public static final int DEFAULT_FROM=100, DEFAULT_TO=200;
    private static final String FROM_KEY="hearthborn/quality/wanted-from", TO_KEY="hearthborn/quality/wanted-to";
    public static int wantedFrom(){return Math.max(ORDINARY_FROM,Math.min(RANGE_LIMIT-1,Utils.getprefi(FROM_KEY,DEFAULT_FROM)));}
    public static int wantedTo(){return Math.max(wantedFrom()+1,Math.min(RANGE_LIMIT,Utils.getprefi(TO_KEY,DEFAULT_TO)));}
    /** Sets the wanted range; it keeps at least one point of quality and stays inside the limits. */
    public static void wanted(int from,int to){
        from=Math.max(ORDINARY_FROM,Math.min(RANGE_LIMIT-1,from));
        to=Math.max(from+1,Math.min(RANGE_LIMIT,to));
        Utils.setprefi(FROM_KEY,from);Utils.setprefi(TO_KEY,to);changed();
    }
    private static volatile int version;
    /** Bumped whenever a setting changes, so cached marks redraw. */
    public static int version(){return version;}
    public static void changed(){version++;}

    public static boolean show(){return Utils.getprefb("hearthborn/quality/show",true);}
    public static void show(boolean value){Utils.setprefb("hearthborn/quality/show",value);changed();}
    public static boolean rounded(){return Utils.getprefb("hearthborn/quality/rounded",true);}
    public static void rounded(boolean value){Utils.setprefb("hearthborn/quality/rounded",value);changed();}
    /** Quality sits in the top-right of an icon and the count in the bottom-right; this puts each in the other's corner. */
    public static boolean swapped(){return Utils.getprefb("hearthborn/quality/swapped",false);}
    public static void swapped(boolean value){Utils.setprefb("hearthborn/quality/swapped",value);changed();}
    public static boolean tinted(){return Utils.getprefb("hearthborn/quality/tinted",false);}
    public static void tinted(boolean value){Utils.setprefb("hearthborn/quality/tinted",value);changed();}

    /** The tier a quality falls in: the first one, from the top, whose threshold it reaches. */
    public static Tier tier(double quality){
        for(Tier tier:Tier.values())if(quality>=tier.threshold())return tier;
        return Tier.JUNK;
    }
    /** The colour follows the number that is drawn: with rounding on, 49.6 is shown as 50 and takes the colour of 50. */
    public static Color ink(double quality){return tinted()?tier(rounded()?Math.round(quality):quality).color():Color.WHITE;}
    public static String text(double quality){return rounded()?Integer.toString((int)Math.round(quality)):QualityOverlay.format(quality);}
    public static void resetColors(){for(Tier tier:Tier.values())Utils.setpref(COLOR_KEY+tier.name(),null);changed();}
    public static void reset(){Utils.setpref(FROM_KEY,null);Utils.setpref(TO_KEY,null);resetColors();}
    private QualityStyle(){}
}
