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

package hearthborn.meteor;
import hearthborn.timers.Timers;
import hearthborn.ui.StudyProgress;
import hearthborn.ui.WorldIdentity;

import haven.*;
import hearthborn.L10n;
import hearthborn.io.LocalStore;
import java.io.Serializable;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/**
 * Server forecasts are upper bounds, never confirmations of a meteor impact.
 *
 * @author Jagy2014
 */
public final class StarFall extends Widget {
    private static final String EVENT="paginae/exp/fallingstar";
    static final String GAZE="paginae/seid/stargaze";
    private static final Pattern FORECAST=Pattern.compile("^A star will fall in (\\d+) (days?|hours?|minutes?)\\.?$",Pattern.CASE_INSENSITIVE);
    private static final Map<String,Forecast> saved=new HashMap<>();
    private static final Map<Session,Forecast> live=new WeakHashMap<>();
    private static final Map<Session,String> liveKeys=new WeakHashMap<>();
    private static boolean loaded;
    public static boolean enabled(){return Utils.getprefb("hearthborn/star-fall",true);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/star-fall",value);}

    static long duration(String message){
        if(message==null)return -1;
        Matcher match=FORECAST.matcher(message.trim());if(!match.matches())return -1;
        try {
            long count=Long.parseLong(match.group(1));
            String unit=match.group(2).toLowerCase(Locale.ROOT);
            double seconds=count*(unit.startsWith("day")?86400d:unit.startsWith("hour")?3600d:60d);
            if(seconds>365*86400d)return -1;
            return (long)Math.ceil(seconds*1000/StudyProgress.GAME_SECONDS_PER_REAL_SECOND);
        }catch(NumberFormatException invalid){return -1;}
    }
    private static long remainingMinutes(long millis){return Math.max(0,(long)Math.ceil(millis/60000d));}
    static String timeText(long millis){
        long minutes=remainingMinutes(millis);
        return L10n.format("%s h %s min real time",Long.toString(minutes/60),Long.toString(minutes%60));
    }
    static String compactTime(long millis){
        long minutes=remainingMinutes(millis);
        return L10n.format("%dh%dm",minutes/60,minutes%60);
    }
    /** Reuse the retained bound, rather than converting a new rounded server reply. */
    public static String realTime(UI ui,String message){
        if(duration(message)<0)return null;
        Forecast value=current(ui);long now=System.currentTimeMillis();
        return value==null||value.until<=now?null:timeText(value.remaining(now));
    }
    static final class Forecast implements Serializable {
        private static final long serialVersionUID=1L;
        long received,until;
        String source;
        Forecast(long now){received=now;}
        boolean update(String message,long now){
            long duration=duration(message);if(duration<0)return false;
            long candidate=now+duration;
            // Queries for the same rounded estimate must not postpone the existing bound.
            if(until>now&&candidate>=until)return false;
            received=now;until=candidate;source=message;return true;
        }
        long remaining(long now){return Math.max(0,until-now);}
        String caption(long now){
            if(until==0)return L10n.tr("Falling star: time unknown");
            if(until<=now)return L10n.tr("Falling star: forecast elapsed");
            return L10n.format("Falling star: within %s",timeText(remaining(now)));
        }
    }
    private static void load(){
        if(loaded)return;loaded=true;
        Object data=LocalStore.read("star-fall-v2");
        if(data instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)data).entrySet())
            if(entry.getKey() instanceof String&&entry.getValue() instanceof Forecast){
                Forecast value=(Forecast)entry.getValue();
                if(value.received>0&&value.until>=0)saved.put((String)entry.getKey(),value);
            }
    }
    private static String key(UI ui){
        if(ui==null||ui.sess==null)return null;
        String world=WorldIdentity.name(ui.sess);if(world==null)return null;
        NamedSocketAddress server=Bootstrap.authserv.get();
        return server.host.toLowerCase(Locale.ROOT)+":"+server.port+"\n"+world;
    }
    private static void remember(UI ui,Forecast value){
        live.put(ui.sess,value);String key=key(ui);
        if(key!=null){liveKeys.put(ui.sess,key);saved.put(key,value);LocalStore.write("star-fall-v2",new HashMap<>(saved));}
    }
    static Forecast current(UI ui){
        if(ui==null||ui.sess==null)return null;load();
        String key=key(ui),previousKey=liveKeys.get(ui.sess);
        if(previousKey!=null&&key!=null&&!previousKey.equals(key))live.remove(ui.sess);
        if(key!=null)liveKeys.put(ui.sess,key);
        Forecast value=live.get(ui.sess);
        if(value==null&&key!=null){value=saved.get(key);if(value!=null)live.put(ui.sess,value);}
        // World selection can arrive after the event notice; persist when it becomes known.
        if(value!=null&&key!=null&&saved.get(key)!=value)remember(ui,value);
        return value;
    }
    /** The saved upper bound has passed, so the star has already fallen. An unknown time never counts. */
    static boolean forecastElapsed(UI ui){
        Forecast value=current(ui);
        return value!=null&&value.until>0&&value.until<=System.currentTimeMillis();
    }
    public static void notice(UI ui,String message){
        hearthborn.meteor.MeteorTriangulation.notice(ui,message,duration(message)>=0);
        if(ui==null||ui.sess==null||duration(message)<0)return;
        long now=System.currentTimeMillis();Forecast value=current(ui);
        if(value==null)value=new Forecast(now);
        if(value.update(message,now))remember(ui,value);
        track(ui,value);
    }
    static String timerId(UI ui){String world=key(ui);return world==null?null:"star-fall:"+world;}
    public static boolean isTimer(String source){return source!=null&&source.startsWith("star-fall:");}
    public static String timerName(String source){
        String world=source.substring(source.lastIndexOf('\n')+1);
        return L10n.format("Falling star forecast (%s)",world);
    }
    static void track(UI ui,Forecast value){
        String source=timerId(ui);
        if(source!=null&&value!=null&&value.until>System.currentTimeMillis())Timers.track(source,timerName(source),value.until);
    }
    static String announcement(){return L10n.tr("Starfall - use Star Gaze magic to find it");}
    static String announcement(Astronomy astronomy){
        return astronomy!=null&&astronomy.night?announcement():L10n.tr("Starfall - use Star Gaze magic at night to find it");
    }
    static String loginMessage(Forecast forecast,Astronomy astronomy,long now){
        if(forecast==null||forecast.until==0)return announcement(astronomy);
        if(forecast.until<=now)return L10n.tr("Starfall - forecast elapsed; use Star Gaze at night to refresh it");
        return L10n.format("Starfall - forecast: within %s",timeText(forecast.remaining(now)));
    }
    static final class Announcements {
        private boolean announced;
        boolean update(Boolean available,boolean enabled){
            if(Boolean.FALSE.equals(available))announced=false;
            if(enabled&&Boolean.TRUE.equals(available)&&!announced){announced=true;return true;}
            return false;
        }
    }
    static final class MenuState {
        private MenuGrid menu;
        private int sequence=-1;
        private Boolean active;
        Boolean poll(MenuGrid value){
            if(value!=menu){menu=value;sequence=-1;active=null;}
            if(menu==null||menu.pagseq==0)return null;
            if(sequence==menu.pagseq&&active!=null)return active;
            List<MenuGrid.Pagina> pages;
            synchronized(menu.paginae){pages=new ArrayList<>(menu.paginae);}
            boolean pending=false;
            for(MenuGrid.Pagina page:pages)try{
                if(page.res!=null&&GAZE.equals(page.res().name)){sequence=menu.pagseq;return active=true;}
            }catch(Loading loading){pending=true;}catch(Resource.BadResourceException unavailable){}
            if(pending)return null;
            sequence=menu.pagseq;return active=false;
        }
    }
    static void clear(UI ui){
        Forecast previous=current(ui);
        String source=timerId(ui);if(source!=null)Timers.forget(source);
        if(previous==null)return;
        String key=key(ui);if(key!=null)saved.remove(key);
        live.values().removeIf(value->value==previous);
        LocalStore.write("star-fall-v2",new HashMap<>(saved));
    }

    private final GameUI gui;
    private final MenuState menuState=new MenuState();
    private final Announcements announcements=new Announcements();
    private long trackedUntil;
    private String trackedSource;
    private double elapsed=1;
    private Tex icon;
    private boolean iconUnavailable;
    private Forecast forecast;
    private boolean showerAvailable;
    public boolean showerActive(){Astronomy sky=astronomy();return showerAvailable&&sky!=null&&sky.night;}
    private Indicator indicator;
    public StarFall(GameUI gui){this.gui=gui;hide();}
    private Astronomy astronomy(){return ui==null||ui.sess==null||ui.sess.glob==null?null:ui.sess.glob.ast;}
    public void tick(double dt){
        if(ui==null||ui.sess==null||(elapsed+=dt)<.5)return;elapsed=0;
        Boolean available=menuState.poll(gui.menu);
        hearthborn.meteor.MeteorTriangulation.menu(ui,available);
        hearthborn.meteor.MeteorTriangulation.tick(gui);
        forecast=current(ui);
        String source=timerId(ui);
        if(forecast!=null&&(!Objects.equals(trackedSource,source)||trackedUntil!=forecast.until)){
            track(ui,forecast);trackedSource=source;trackedUntil=forecast.until;
        }
        boolean enabled=enabled();
        if(!enabled)showerAvailable=false;else if(available!=null)showerAvailable=available;
        if(gui.syslog!=null&&announcements.update(available,enabled))
            ui.msg(loginMessage(forecast,astronomy(),System.currentTimeMillis()),new java.awt.Color(100,230,255),
                Audio.resclip(Resource.local().loadwait("sfx/hud/mmap/bell1")));
        if(enabled&&Boolean.TRUE.equals(available)&&gui.buffs!=null){
            if(icon==null&&!iconUnavailable)try{
                Resource.Image image=Resource.remote().load(EVENT).get().layer(Resource.imgc);
                if(image!=null)icon=new TexI(PUtils.uiscale(image.img,Buff.frame.sz()));
            }catch(Loading pending){}catch(Resource.LoadException|Resource.BadResourceException unavailable){iconUnavailable=true;}
            if(indicator==null||indicator.parent==null){indicator=new Indicator(this);gui.buffs.addchild(indicator);}
            indicator.update();
        }else if(indicator!=null){indicator.destroy();indicator=null;}
    }
    private String tooltipText(){
        String text=announcement(astronomy());
        if(forecast!=null&&forecast.until>0){
            String received=DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(L10n.language()))
                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(forecast.received));
            text+="\n"+forecast.caption(System.currentTimeMillis())+"\n"+L10n.format("Forecast received: %s",received)+"\n"+
                L10n.tr("Real time. This is an upper estimate, not an exact impact time.")+"\n"+L10n.tr("Right-click to clear this forecast and its timer.");
        }
        return text;
    }
    static final class Indicator extends Buff {
        private static final Text.Foundry FONT=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),8).aa(true);
        private final StarFall owner;
        private Tex timer;
        private String lastTimer;
        Indicator(StarFall owner){super(()->Resource.remote().load(EVENT).get());this.owner=owner;resize(Buff.frame.sz());}
        public int layoutOrder(){return -4;}
        void update(){
            long now=System.currentTimeMillis();
            String text=owner.forecast==null||owner.forecast.until<=now?"":compactTime(owner.forecast.remaining(now));
            if(text.equals(lastTimer))return;lastTimer=text;
            if(timer!=null){timer.dispose();timer=null;}
            if(!text.isEmpty())timer=new TexI(Utils.outline2(FONT.render(text,java.awt.Color.WHITE).img,java.awt.Color.BLACK));
        }
        public void draw(GOut g){
            g.chcolor(255,255,255,a);g.image(owner.icon==null?Buff.frame:owner.icon,Coord.z);g.chcolor();
            if(timer!=null){
                double scale=Math.min(1,(sz.x-UI.scale(1))/(double)timer.sz().x);
                Coord size=Coord.of((int)Math.round(timer.sz().x*scale),(int)Math.round(timer.sz().y*scale));
                g.aimage(timer,Coord.of(sz.x/2,sz.y-UI.scale(1)),.5,1,size);
            }
        }
        public Object tooltip(Coord c,Widget prev){return owner.tooltipText();}
        public boolean mousedown(MouseDownEvent event){
            if(event.b==1)return true;
            if(event.b==3&&owner.forecast!=null){clear(owner.ui);owner.forecast=null;owner.trackedUntil=0;update();return true;}
            return false;
        }
        public void dispose(){if(timer!=null)timer.dispose();super.dispose();}
    }
    public void dispose(){showerAvailable=false;if(indicator!=null&&indicator.parent!=null)indicator.destroy();if(icon!=null)icon.dispose();super.dispose();}
}
