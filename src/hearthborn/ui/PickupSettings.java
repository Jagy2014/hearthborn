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
 * @author Jagy2014
 */
public final class PickupSettings {
    public static int radius(){return Math.max(33,Math.min(352,(int)Math.round(Utils.getprefd("hearthborn/pickup-radius",55))));}
    public static void radius(int value){Utils.setprefd("hearthborn/pickup-radius",Math.max(33,Math.min(352,value)));}
    public static boolean visibleOnly(){return Utils.getprefb("hearthborn/pickup-visible-only",true);}
    public static void visibleOnly(boolean value){Utils.setprefb("hearthborn/pickup-visible-only",value);}
    /** The interface is the caller's: an object's own context does not reach up to it and raises when asked. */
    public static boolean eligible(GameUI gui,Gob gob){
        if(!visibleOnly())return true;
        GobIcon icon=gob.getattr(GobIcon.class);
        if(icon==null || gui==null || gui.iconconf==null)return true;
        try {GobIcon.Setting setting=gui.iconconf.get(icon.icon());return setting==null || setting.show;}
        catch(Loading loading){return true;}
    }
    public static Widget add(OptWnd.Panel panel,Widget previous){
        Label label=panel.add(new Label(""),previous.pos("bl").adds(0,12));
        HSlider slider=panel.add(new HSlider(UI.scale(260),33,352,radius()) {
            public void changed(){radius(val);label.settext(hearthborn.L10n.tr("Pickup radius:")+String.format(java.util.Locale.ROOT," %.2f",val/11.0));}
        },label.pos("bl").adds(0,3));
        slider.changed();
        Widget check=panel.add(new CheckBox("Pick up only objects visible on radar").state(PickupSettings::visibleOnly).set(PickupSettings::visibleOnly),slider.pos("bl").adds(0,5));
        check.settip("Skip objects whose map icons are hidden. Objects without a configured icon can still be picked up.");
        return check;
    }
    private PickupSettings(){}
}
