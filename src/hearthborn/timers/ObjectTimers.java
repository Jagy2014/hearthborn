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
import hearthborn.world.ObjectNames;
import hearthborn.world.WorldLabels;

import haven.*;
import hearthborn.L10n;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.util.*;

/**
 * Starts a countdown from the open window of a smelter, kiln, oven, forge, steel crucible,
 * herbalist table, or silk cupboard, then keeps it on the object.
 *
 * @author Jagy2014
 */
public final class ObjectTimers {
    private static final String PREF = "hearthborn/world/auto-timers";
    private static final String STORE = "hearthborn/world/timers";
    private static final double RATIO = 3.29;
    private static final int STEEL = 12 * 60 * 60;
    private static final Map<String, Long> until = new HashMap<>();
    private static final Map<String, Integer> steelMeters = new HashMap<>();
    private static final Map<String, Integer> waiting = new HashMap<>();
    private static final Map<String, Tex> cache = new HashMap<>();
    private static final BufferedImage TIMER_ICON = timerIcon();
    private static final Set<String> told = new HashSet<>();
    private static boolean loaded;
    private static Gob clicked;
    private static final Map<Gob,Boolean> readyObjects=new WeakHashMap<>();
    public static boolean ready(Gob gob){
        Kind kind=Kind.ofGob(resource(gob));
        if(kind!=null && requiresFire(kind) && lit(gob,kind))return false;
        return Boolean.TRUE.equals(readyObjects.get(gob));
    }
    public static int remainingSeconds(Gob gob){
        load();String key=key(gob);if(key==null)return -1;
        Long deadline=until.get(key);return deadline==null?-1:(int)Math.max(0,(deadline-System.currentTimeMillis())/1000);
    }
    private static void observeReady(Window window,Gob gob){
        boolean ready=false;
        for(WItem view:window.children(WItem.class)){
            GItem item=view.item;
            ready|=item.resource().name.equals("gfx/invobjs/treepot-t");
            if(item.info().stream().anyMatch(info->info instanceof GItem.MeterInfo)||item.meter>0)ready|=meter(item)>=1;
        }
        readyObjects.put(gob,ready);
    }
    private static double clickedAt;
    private static double scanned;

    private ObjectTimers() {}

    private static volatile boolean shownTimers=Utils.getprefb(PREF, true);
    private static final List<Gob> watched=new ArrayList<>();
    private static double watchAt;
    public static boolean enabled() { return shownTimers; }
    public static void enabled(boolean on) { shownTimers=on; Utils.setprefb(PREF, on); if(!on)watched.clear(); }

    public static void clicked(Gob gob) {
        hearthborn.util.ObjectWindows.clicked(gob);
        ClaimTimers.clicked(gob);
        clicked = gob;
        clickedAt = Utils.rtime();
    }

    public static void notice(UI ui,String message) {
        if(!hearthborn.timers.TimerSettings.automatic() || message==null || clicked==null || clicked.removed || ui==null || ui.sess==null || clicked.glob!=ui.sess.glob || Utils.rtime()-clickedAt>60)return;
        String res=resource(clicked);
        if(!"gfx/terobjs/vehicle/wreckingball".equals(res) && !"gfx/terobjs/vehicle/bram".equals(res))return;
        long remaining=SiegeTimers.remainingMillis(message);if(remaining<=0)return;
        try {
            MCache.Grid grid=clicked.glob.map.getgridt(clicked.rc.floor(MCache.tilesz));
            Coord local=clicked.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
            String source="siege:"+grid.id+":"+local.x+":"+local.y;
            ObjectNames.bind(source,clicked);
            long deadline=System.currentTimeMillis()+remaining,old=hearthborn.timers.Timers.deadline(source);
            if(old>System.currentTimeMillis() && old<=deadline)return;
            hearthborn.timers.Timers.track(source,ObjectNames.timerName(source,L10n.tr(res.endsWith("/bram")?"Battering ram":"Wrecking ball")),deadline);
            ui.msg(L10n.tr("Siege engine timer updated."));
        }catch(Loading ignored){}
    }
    public static void draw(MapView map, GOut g) {
        if(!enabled() || map.ui == null || map.ui.sess == null)
            return;
        load();
        double time=Utils.rtime();
        if(time>=watchAt){
            watchAt=time+0.25;
            List<Gob> copy=map.ui.sess.glob.oc.snapshot();
            List<Gob> found=new ArrayList<>();
            for(Gob gob:copy) if(key(gob)!=null) found.add(gob);
            watched.clear();watched.addAll(found);
        }
        long now = System.currentTimeMillis();
        for(Gob gob : watched) {
            String key = key(gob);
            if(key == null)
                continue;
            Long deadline = until.get(key);
            if(deadline == null)
                continue;
            int left = (int)((deadline - now) / 1000L);
            if(left <= 0) {
                if(until.remove(key) != null) {
                    save();
                    if(key.startsWith("steel:") && told.add(key) && map.ui != null)
                        map.ui.msg("Steel crucible fuel is empty");
                }
                continue;
            }
            if(gob.rc == null)
                continue;
            try {
                Coord3f position = gob.getc();
                Coord3f point = map.screenxf(new Coord3f(position.x, position.y, position.z + 15));
                if(point == null)
                    continue;
                WorldLabels.draw(g, tex(clock(left)), point.round2(), 0.5, 1);
            } catch(Loading ignored) {}
        }
    }

    public static void observe(GameUI gui) {
        if(gui.ui==null || gui.ui.sess==null)return;
        if(Utils.rtime()-scanned<.5)return;
        scanned=Utils.rtime();
        load();
        updateFires(gui);
        scan(gui);
    }

    private static void scan(GameUI gui) {
        for(Widget widget : gui.children()) {
            if(widget instanceof Window && widget.visible())
                read((Window)widget, gui);
        }
    }

    private static void read(Window window, GameUI gui) {
        try{readLoaded(window,gui);}catch(Loading pending){/* Retry this window on the next scan. */}
    }
    private static void readLoaded(Window window, GameUI gui) {
        String caption = window.cap;
        Kind kind = Kind.of(caption);
        if(kind == null)
            return;
        Gob gob = hearthborn.util.ObjectWindows.target(window,gui,target -> kind.matches(resource(target)));
        if(gob == null)
            return;
        observeReady(window,gob);
        if(kind==Kind.CUPBOARD)SilkMothTimers.observe(window,gui);
        if(kind==Kind.TABLE || kind==Kind.CUPBOARD) {
            String location=key(kind,gob);
            if(location!=null){ObjectNames.bind(location,gob);ItemProcessTimers.observe(window,location,kind==Kind.TABLE);}
        }
        int seconds = kind == Kind.STEEL ? steel(window) : kind == Kind.OVEN ? soonest(window) : items(window, kind);
        String key = key(kind, gob);
        if(key == null)
            return;
        ObjectNames.bind(key,gob);
        if(kind == Kind.STEEL) {
            snapshotSteelFuel(key, seconds);
            return;
        }
        if(requiresFire(kind) && !lit(gob, kind)) {
            if(seconds > 0) waiting.put(key, seconds);
            else waiting.remove(key);
            stop(key);
            return;
        }
        if(seconds <= 0)
            return;
        waiting.remove(key);
        track(kind, key, seconds);
    }

    /** Full fuel lasts 12 hours; every server-confirmed refill refreshes the deadline. */
    private static void snapshotSteelFuel(String key, int meter) {
        if(meter < 0) return;
        Integer last = steelMeters.put(key, meter);
        Long deadline = until.get(key);
        long now = System.currentTimeMillis();
        if(deadline == null || deadline <= now) {
            if(meter > 0) track(Kind.STEEL, key, meter);
            return;
        }
        // The bar is a fuel quantity, not a continuously updated countdown.
        // Falling steps only update our baseline; any rise confirms refuelling.
        if(last != null && meter > last) {
            track(Kind.STEEL, key, meter);
        } else if(last == null && meter > (deadline-now)/1000L + 60) {
            // Also notice refuelling since the last login / restored snapshot.
            track(Kind.STEEL, key, meter);
        }
    }

    private static boolean requiresFire(Kind kind) {
        return kind == Kind.SMELTER || kind == Kind.STACK || kind == Kind.KILN || kind == Kind.OVEN;
    }

    private static boolean lit(Gob gob, Kind kind) {
        Drawable drawable = gob.getattr(Drawable.class);
        if(!(drawable instanceof ResDrawable)) return false;
        MessageBuf state = ((ResDrawable)drawable).stateData();
        return !state.eom() && (state.uint8() & (kind == Kind.SMELTER ? 2 : 1)) != 0;
    }

    private static void stop(String key) {
        if(until.remove(key) != null) {
            hearthborn.timers.Timers.pause(key);
            save();
        }
    }

    private static void updateFires(GameUI gui) {
        List<Gob> objects = new ArrayList<>();
        OCache oc = gui.ui.sess.glob.oc;
        synchronized(oc) { for(Gob gob : oc) objects.add(gob); }
        for(Gob gob : objects) {
            Kind kind = Kind.ofGob(resource(gob));
            if(kind == null || !requiresFire(kind)) continue;
            String key = key(kind, gob);
            if(key == null) continue;
            if(lit(gob, kind)) {
                Integer seconds = waiting.remove(key);
                if(seconds != null) track(kind, key, seconds);
            } else stop(key);
        }
    }

    private static void track(Kind kind, String key, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        Long current = until.get(key);
        int tolerance=5000;
        if(current != null && Math.abs(current - deadline) < tolerance)deadline=current;
        String title=L10n.format("%s #%s (estimated)",L10n.text("window",kind.captions[0]),Integer.toHexString(key.hashCode()));
        if(hearthborn.timers.TimerSettings.automatic() && kind!=Kind.TABLE && kind!=Kind.CUPBOARD)hearthborn.timers.Timers.track(key,title,deadline);
        if(current != null && current==deadline)return;
        until.put(key, deadline);
        told.remove(key);
        save();
    }

    private static int items(Window window, Kind kind) {
        int best = 0;
        for(WItem item : window.children(WItem.class)) {
            int seconds = itemSeconds(kind,item.item);
            if(seconds > best)
                best = seconds;
        }
        return best;
    }

    /** The first portion turns to ash if the oven waits for a slower one. */
    private static int soonest(Window window) {
        int best = -1;
        for(WItem item : window.children(WItem.class)) {
            int seconds = itemSeconds(Kind.OVEN, item.item);
            if(seconds > 0 && (best < 0 || seconds < best))
                best = seconds;
        }
        return best;
    }

    public static boolean oven(Window window){return window!=null&&Kind.of(window.cap)==Kind.OVEN;}
    /** Grids that show several marks on each portion, same size as the oven. Food tables stay ordinary. */
    public static boolean largeGrid(Window window){
        if(window==null)return false;
        Kind kind=Kind.of(window.cap);
        return kind==Kind.OVEN||kind==Kind.KILN||kind==Kind.SMELTER||kind==Kind.STACK||kind==Kind.TABLE;
    }
    public static boolean furnaceItem(WItem view){
        Window window=view.getparent(Window.class);if(window==null)return false;
        Kind kind=Kind.of(window.cap);
        return kind==Kind.SMELTER||kind==Kind.STACK||kind==Kind.FINERY||kind==Kind.KILN||kind==Kind.STEEL||kind==Kind.OVEN;
    }
    public static int itemRemaining(WItem view){
        Window window=view.getparent(Window.class);if(window==null)return -1;
        Kind kind=Kind.of(window.cap);
        if(kind!=Kind.SMELTER&&kind!=Kind.STACK&&kind!=Kind.FINERY&&kind!=Kind.KILN&&kind!=Kind.OVEN)return -1;
        GameUI gui=view.getparent(GameUI.class);
        Gob gob=gui==null?null:hearthborn.util.ObjectWindows.target(window,gui,target->kind.matches(resource(target)));
        boolean running=gob==null||lit(gob,kind);
        int nested=nestedSeconds(kind,view.item,running);
        return nested>=0?nested:seconds(kind,view.item,running);
    }

    private static int itemSeconds(Kind kind,GItem item){
        int nested=nestedSeconds(kind,item,true);
        return nested>=0?nested:seconds(kind,item);
    }

    /** A stack shell has no mining note of its own. The pieces inside do. */
    private static int nestedSeconds(Kind kind,GItem item,boolean running){
        if(item.contents==null || !item.contents.getClass().getName().endsWith("ItemStack"))return -1;
        int best=-1;
        for(GItem child:item.contents.children(GItem.class)){
            int seconds=seconds(kind,child,running);
            if(seconds>best)best=seconds;
        }
        return best;
    }

    private static int seconds(Kind kind,GItem item){return seconds(kind,item,true);}
    private static int seconds(Kind kind, GItem item,boolean running) {
        if(item == null)
            return -1;
        try {
            String name = item.resource().name;
            double meter = kind==Kind.OVEN ? ovenMeter(item) : meter(item);
            if(!Double.isFinite(meter) || meter<0 || meter>=1)return -1;
            if(item.meter<=0 && item.info().stream().noneMatch(info -> info instanceof GItem.MeterInfo))return -1;
            double minutes;
            switch(kind) {
                case SMELTER:
                case STACK:
                    minutes = wellMined(item) ? 41.25 : 55;
                    break;
                case KILN:
                    minutes = kiln(name);
                    break;
                case FINERY:
                    minutes = 9;
                    break;
                case OVEN:
                    minutes = bakeMinutes(name);
                    break;
                case TABLE:
                    if("gfx/invobjs/treepot-s".equals(name))
                        return elapsed(item,real(4 * 3600, meter));
                    if("gfx/invobjs/silkegg".equals(name))
                        return elapsed(item,real(86400, meter));
                    return -1;
                case CUPBOARD:
                    if("gfx/invobjs/silkcocoon".equals(name))
                        return elapsed(item,real(2 * 86400, meter));
                    if("gfx/invobjs/silkegg".equals(name))
                        return elapsed(item,real(86400, meter));
                    return -1;
                default:
                    return -1;
            }
            if(minutes <= 0)
                return -1;
            int left=(int)Math.ceil(minutes * 60 * (1 - meter));
            return running?elapsed(item,left):left;
        } catch(Loading e) {
            return -1;
        }
    }

    private static int elapsed(GItem item,int seconds) {
        if(item.meterUpdated<=0)return seconds;
        return Math.max(0,seconds-(int)((System.currentTimeMillis()-item.meterUpdated)/1000));
    }

    /** Real seconds left for a game-time duration, using the server clock ratio. */
    private static int real(int gameSeconds, double meter) {
        return (int)Math.ceil(gameSeconds / RATIO * (1 - meter));
    }

    private static int steel(Window window) {
        double fill = -1;
        for(VMeter meter : window.children(VMeter.class)) {
            for(LayerMeter.Meter layer : meter.meters())
                fill = Math.max(fill, layer.a);
        }
        if(fill < 0)
            return -1;
        return (int)Math.round(fill * STEEL);
    }

    /** Widget progress is how baked the portion is. The low end of the known time rings before ash. */
    static double bakeMinutes(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if(n.contains("honey")) return 4;
        if(n.contains("carrot") || n.contains("raisin")) return 12;
        if(n.contains("blueberry") || n.contains("peapie") || n.contains("pea-pie") || n.contains("plaice")) return 15;
        if(n.contains("pumpkin") && n.contains("pie")) return 15;
        if(n.contains("brodgar")) return 16;
        if(n.contains("bream")) return 15;
        if(n.contains("apple") || n.contains("pirozh") || n.contains("chantrelle")) return 17;
        if(n.contains("bark") || (n.contains("pumpkin") && n.contains("bread"))) return 19;
        if(n.contains("bread") || n.contains("dough") || n.contains("shew")) return 16;
        return 4;
    }

    private static double ovenMeter(GItem item) {
        Double shown = hearthborn.ui.ItemMeter.shown(item, true);
        return shown == null ? meter(item) : shown;
    }

    private static double meter(GItem item) {
        for(ItemInfo info : item.info()) {
            if(info instanceof GItem.MeterInfo)
                return ((GItem.MeterInfo)info).meter();
        }
        return item.meter / 100.0;
    }

    static boolean wellMined(GItem item) {
        try {
            if(wellMined(item.info())) return true;
            if(item.contents!=null && item.contents.getClass().getName().endsWith("ItemStack"))
                for(GItem child:item.contents.children(GItem.class))
                    if(wellMined(child)) return true;
        } catch(Loading ignored) {}
        return false;
    }

    /** The server line stays English. The shown line is translated. A resource class is neither. */
    static boolean wellMined(List<ItemInfo> info) {
        if(info==null) return false;
        String translated=L10n.tr("Well mined");
        for(ItemInfo tip:info) {
            String type=tip.getClass().getName().toLowerCase(Locale.ROOT);
            if(type.contains("wellmined")) return true;
            String text=null;
            if(tip instanceof ItemInfo.AdHoc) text=((ItemInfo.AdHoc)tip).str.text;
            else if(tip instanceof ItemInfo.Name) text=hearthborn.l10n.LocalizedText.shown(((ItemInfo.Name)tip).str);
            if(text!=null && (text.equalsIgnoreCase("Well mined") || text.equalsIgnoreCase(translated))) return true;
            if(tip instanceof ItemInfo.Contents && wellMined(((ItemInfo.Contents)tip).sub)) return true;
        }
        return false;
    }

    private static double kiln(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if(n.contains("coade")) return 109.4167;
        if(n.contains("/clay-") || n.endsWith("/clay")) return 8.9667;
        if(n.contains("gardenpot") || n.contains("/pot-u") || n.contains("urn-u")) return 109.4167;
        if(n.contains("jar-u") || n.contains("mug-u") || n.contains("teapot-u") || n.contains("ceramicknife")) return 54.55;
        if(n.contains("earthenware") || n.contains("porcelain") || n.contains("stoneware") || n.contains("treepot")) return 36.3;
        if(n.contains("pipe-clay") || n.contains("claypipe")) return 21.1167;
        if(n.contains("handimpression") || n.contains("toychariot")) return 21.1667;
        if(n.contains("boneash") || n.contains("/bone") || n.endsWith("bone")) return 25.2;
        if(n.contains("brandingiron") || n.contains("malted") || n.endsWith("/seed-barleymalt") || n.endsWith("/seed-wheatmalt") || n.endsWith("/seed-milletmalt")) return 4.55;
        if(n.contains("fishwrap") || n.contains("fruitroast") || n.contains("nutjerky") || n.contains("stuffed") || n.contains("glutton")) return 18.1167;
        if(n.contains("board")) return 13.6833;
        if(n.contains("block") || n.contains("bark") || n.contains("/branch")) return 36.4833;
        return -1;
    }

    private static Gob target(GameUI gui, Kind kind) {
        if(clicked != null && Utils.rtime() - clickedAt < 30 && kind.matches(resource(clicked)))
            return clicked;
        if(gui.map == null || gui.map.player() == null)
            return null;
        Gob player = gui.map.player();
        Gob best = null;
        double bestD = 11 * 20;
        OCache oc = gui.ui.sess.glob.oc;
        synchronized(oc) {
            for(Gob gob : oc) {
                if(!kind.matches(resource(gob)) || gob.rc == null)
                    continue;
                double dist = gob.rc.dist(player.rc);
                if(dist < bestD) {
                    bestD = dist;
                    best = gob;
                }
            }
        }
        return best;
    }

    private static String key(Gob gob) {
        String name = resource(gob);
        Kind kind = Kind.ofGob(name);
        return kind == null ? null : key(kind, gob);
    }

    private static String key(Kind kind, Gob gob) {
        try {
            MCache.Grid grid = gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));
            Coord local = gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
            return kind.id + ":" + grid.id + ":" + local.x + ":" + local.y;
        } catch(Loading e) {
            return null;
        }
    }

    private static String resource(Gob gob) {
        if(gob == null)
            return null;
        try {
            Drawable draw = gob.getattr(Drawable.class);
            Resource res = draw == null ? null : draw.getres();
            return res == null ? null : res.name;
        } catch(Loading e) {
            return null;
        }
    }

    private static String clock(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, rest)
            : seconds >= 60 ? String.format("%d:%02d", minutes, rest) : String.format("%02d", rest);
    }

    /** The countdown is drawn exactly like the object's name above it: same bold font, black stroke and halo, no plate, with a small hourglass in front. */
    private static Tex tex(String label) {
        Tex tex = cache.get(label);
        if(tex != null && WorldLabels.stale(tex)) {
            tex.dispose();
            cache.remove(label);
            tex = null;
        }
        if(tex == null) {
            if(cache.size() > 300) {
                for(Tex old : cache.values())
                    old.dispose();
                cache.clear();
            }
            tex = WorldLabels.captionWithIcon(label, Color.WHITE, TIMER_ICON);
            cache.put(label, tex);
        }
        return tex;
    }

    private static BufferedImage timerIcon() {
        try(InputStream in = ObjectTimers.class.getResourceAsStream("/hearthborn/assets/icons/meters/action-hourglass.png")) {
            return in == null ? null : ImageIO.read(in);
        } catch(IOException e) {
            return null;
        }
    }

    private static void load() {
        if(loaded)
            return;
        loaded = true;
        for(String part : Utils.getpref(STORE, "").split(";")) {
            int eq = part.lastIndexOf('=');
            if(eq <= 0)
                continue;
            try {
                long deadline = Long.parseLong(part.substring(eq + 1));
                if(deadline > System.currentTimeMillis())
                    until.put(part.substring(0, eq), deadline);
            } catch(NumberFormatException ignored) {}
        }
    }

    private static void save() {
        StringBuilder raw = new StringBuilder();
        long now = System.currentTimeMillis();
        for(Map.Entry<String, Long> entry : until.entrySet()) {
            if(entry.getValue() <= now)
                continue;
            if(raw.length() > 0)
                raw.append(';');
            raw.append(entry.getKey()).append('=').append(entry.getValue());
        }
        Utils.setpref(STORE, raw.toString());
    }

    private enum Kind {
        SMELTER("smelter", "Ore Smelter", "Smith's Smelter"),
        KILN("kiln", "Kiln"),
        FINERY("finery", "Finery Forge"),
        STACK("stack", "Stack furnace", "Stack Furnace"),
        STEEL("steel", "Steelbox", "Steel Crucible"),
        OVEN("oven", "Oven"),
        TABLE("table", "Herbalist Table"),
        CUPBOARD("cupboard", "Cupboard");

        final String id;
        final String[] captions;
        Kind(String id, String... captions) { this.id = id; this.captions = captions; }

        static Kind of(String caption) {
            if(caption == null)
                return null;
            for(Kind kind : values())
                for(String name : kind.captions)
                    if(captionMatches(caption, name) || captionMatches(caption, L10n.text("window", name)))
                        return kind;
            return null;
        }

        private static boolean captionMatches(String caption, String name) {
            if(name == null || name.isEmpty()) return false;
            if(name.equalsIgnoreCase(caption)) return true;
            return caption.length() > name.length() && caption.regionMatches(true, 0, name, 0, name.length()) && caption.charAt(name.length()) == ' ';
        }

        static Kind ofGob(String name) {
            if(name == null)
                return null;
            if(name.equals("gfx/terobjs/smelter")) return SMELTER;
            if(name.equals("gfx/terobjs/kiln")) return KILN;
            if(name.equals("gfx/terobjs/fineryforge")) return FINERY;
            if(name.equals("gfx/terobjs/primsmelter")) return STACK;
            if(name.equals("gfx/terobjs/steelcrucible")) return STEEL;
            if(name.equals("gfx/terobjs/oven")) return OVEN;
            if(name.equals("gfx/terobjs/htable")) return TABLE;
            if(name.equals("gfx/terobjs/cupboard")) return CUPBOARD;
            return null;
        }

        boolean matches(String name) {
            return this == ofGob(name);
        }
    }
}
