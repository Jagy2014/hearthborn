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
 * Apply preferences once on login and on mount changes, without overriding manual speed choices.
 *
 * @author Jagy2014
 */
public final class DefaultSpeed {
    private Boolean horse;
    public static int preferred(boolean horse) {return Utils.clip(Utils.getprefi("hearthborn/speed/"+(horse ? "horse" : "player"),-1),-1,3);}
    public static void preferred(boolean horse,int speed) {Utils.setprefi("hearthborn/speed/"+(horse ? "horse" : "player"),Utils.clip(speed,-1,3));}
    public boolean mounted(){return Boolean.TRUE.equals(horse);}
    public void tick(Speedget widget) {
        GameUI gui=widget.getparent(GameUI.class);
        if(gui==null || gui.map==null || gui.map.player()==null || widget.max<0)return;
        Gob player=gui.map.player();
        Following following=player.getattr(Following.class);
        boolean mounted=false;
        if(following!=null) {
            Gob mount=following.tgt();if(mount==null)return;
            Drawable drawable=mount.getattr(Drawable.class);if(drawable==null)return;
            try {Resource resource=drawable.getres();if(resource==null)return;mounted=resource.name.startsWith("gfx/kritter/horse/");}
            catch(Loading loading){return;}
        }
        update(widget,mounted);
    }
    public void update(Speedget widget,boolean mounted) {
        if(horse!=null && horse==mounted)return;
        horse=mounted;
        int speed=preferred(mounted);
        if(speed>=0)widget.set(Math.min(speed,widget.max));
    }
}
