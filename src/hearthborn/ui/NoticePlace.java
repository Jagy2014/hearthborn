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
/**
 * The notice line sits above the hotbar unless the player asks for it under the clock.
 *
 * @author Jagy2014
 */
public final class NoticePlace {
    private static final String UNDER = "hearthborn/ui/notices-under-clock";
    private NoticePlace() {}
    public static boolean belowClock() {return Utils.getprefb(UNDER, false);}
    public static void belowClock(boolean on) {Utils.setprefb(UNDER, on);}
    public static Coord underClock(Coord screen, Coord box, Area clock, boolean combat) {
        if(combat || screen == null || box == null || clock == null || clock.ul.y < 0 || clock.br.y <= 0 || clock.sz().x <= 0)
            return null;
        int pad = UI.scale(2);
        int x = clock.ul.x + (clock.sz().x - box.x) / 2;
        x = Utils.clip(x, pad, Math.max(pad, screen.x - box.x - pad));
        int y = clock.br.y + UI.scale(6);
        if(y < 0 || y + box.y > screen.y - pad) return null;
        return Coord.of(x, y);
    }
    /** Lifts a plate that covers a button strip. A strip that does not reach the plate leaves it where it is. */
    public static int aboveStrips(GameUI gui, int x, int y, Coord box) {
        if(gui == null || box == null) return y;
        int pad = UI.scale(6);
        boolean lifted = true;
        while(lifted) {
            lifted = false;
            int bottom = y + box.y;
            for(Widget child = gui.child; child != null; child = child.next) {
                if(!(child instanceof GameUI.Belt) || !child.visible || !CombatLayout.visible(child)) continue;
                if(child.c.x >= x + box.x || x >= child.c.x + child.sz.x) continue;
                if(child.c.y >= bottom || child.c.y + child.sz.y <= y) continue;
                int above = child.c.y - pad - box.y;
                if(above < y) { y = above; lifted = true; }
            }
        }
        return y;
    }
}
