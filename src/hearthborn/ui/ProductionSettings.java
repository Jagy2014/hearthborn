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
import hearthborn.storage.ContainerLook;
import hearthborn.storage.ContainerSettings;
import hearthborn.timers.ObjectTimers;
import hearthborn.timers.TimerSettings;

/**
 * Controls for production indicators, independent of automatic process recording.
 *
 * @author Jagy2014
 */
public final class ProductionSettings {
    private static volatile boolean showProgress=Utils.getprefb("hearthborn/production/progress",true);
    public static boolean progress(){return showProgress;}
    public static void progress(boolean enabled){showProgress=enabled;Utils.setprefb("hearthborn/production/progress",enabled);}
    private static volatile boolean furnacePercent=Utils.getprefb("hearthborn/production/furnace-percent",true);
    /** The percentage in the middle of an item inside a furnace; the countdown at the bottom is not affected. */
    public static boolean furnacePercent(){return furnacePercent;}
    public static void furnacePercent(boolean enabled){furnacePercent=enabled;Utils.setprefb("hearthborn/production/furnace-percent",enabled);}
    public static boolean showProgress(WItem item){
        Window window=item.getparent(Window.class);
        return progress() || !(ObjectTimers.furnaceItem(item) || ObjectTimers.largeGrid(window) || MeterPercent.bare(window));
    }
    /** The preference groups the production half of the combined section puts back to their defaults. */
    static final String[] RESET={"hearthborn/production/","hearthborn/timers/automatic"};
    /** Puts the production controls on {@code panel} with the first one at {@code origin}; returns the lowest one. */
    static Widget fill(OptWnd options,OptWnd.Panel panel,Coord origin){
        Widget previous=panel.add(new CheckBox("Highlight finished objects").state(()->ContainerLook.enabled("finished")).set(value->{
            ContainerLook.enabled("finished",value);ContainerLook.changed();
        }),origin);
        previous.settip("Highlights drying racks, tanning tubs and curding tubs when their contents are ready.");
        previous=panel.add(new CheckBox("Show production progress on items").state(ProductionSettings::progress).set(ProductionSettings::progress),previous.pos("bl").adds(0,10));
        previous.settip("Shows server-reported percentages on items in production windows. Does not change study progress or buff indicators.",true);
        previous=panel.add(new CheckBox("Show progress percent in furnaces").state(ProductionSettings::furnacePercent).set(ProductionSettings::furnacePercent),previous.pos("bl").adds(0,10));
        previous.settip("Shows the percentage on items inside smelters, ovens, kilns and forges. The countdown at the bottom of each item stays.",true);
        previous=panel.add(new CheckBox("Show automatic timers").state(ObjectTimers::enabled).set(ObjectTimers::enabled),previous.pos("bl").adds(0,10));
        previous.settip("Countdown on smelters, kilns, forges, steel, herbalist tables, and silk.");
        previous=panel.add(new CheckBox("Automatically create process timers").state(TimerSettings::automatic).set(TimerSettings::automatic),previous.pos("bl").adds(0,10));
        previous.settip("Adds observed production and cheese-aging processes to Timers. Existing timers keep running when disabled.",true);
        return panel.add(options.new PButton(UI.scale(300),"Container colours",'c',()->ContainerSettings.panel(options,panel)),previous.pos("bl").adds(0,15));
    }
    private ProductionSettings(){}
}
