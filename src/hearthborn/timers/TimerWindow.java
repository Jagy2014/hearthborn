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

import haven.*;
import hearthborn.L10n;
import java.util.*;
import java.awt.Color;
import hearthborn.meteor.StarFall;

/**
 * Timers window and controls; no countdown storage lives in this class.
 *
 * @author Jagy2014
 */
final class TimerWindow {
    public static void toggle(GameUI gui) {
        TimerService.load();
        TimerWnd wnd = gui.getchild(TimerWnd.class);
        if(wnd != null) {
            wnd.show(!wnd.visible());
            if(wnd.visible)
                wnd.raise();
            return;
        }
        wnd = new TimerWnd();
        gui.addchild(wnd, "misc", new Coord2d(0.35, 0.35), new Object[] {"id", "timers"});
    }

    private static String clock(long ms) {
        long t = Math.max(0, ms) / 1000L;
        long h = t / 3600L;
        long m = (t % 3600L) / 60L;
        long s = t % 60L;
        if(h >= 24)
            return String.format("%d:%02d:%02d:%02d", h / 24L, h % 24L, m, s);
        return String.format("%d:%02d:%02d", h, m, s);
    }

    private static final int NAME_WIDTH=UI.scale(340),TIME_X=NAME_WIDTH+UI.scale(12),ACTION_X=TIME_X+UI.scale(95),ROW_WIDTH=ACTION_X+UI.scale(132);
    private enum Sort { NONE, NAME, TIME }
    enum Category {
        AUTOMATIC("Automatic"), CHEESE("Cheese"), RESOURCES("Natural resources"), MANUAL("Manual");
        final String title;
        Category(String title){this.title=title;}
        boolean accepts(String source){
            if(source==null)return this==MANUAL;
            if(source.startsWith("cheese:"))return this==CHEESE;
            if(source.startsWith(NaturalResourceTimers.PREFIX))return this==RESOURCES;
            return this==AUTOMATIC;
        }
    }
    static final class TimerWnd extends Window {
        private final Scrollport scroll;
        private final Button addbtn;
        private final Label nameHeader,timeHeader;
        private final List<Row> rows=new ArrayList<>();
        private int built=-1;
        private Category category=Category.AUTOMATIC;
        private final Map<Category,Button> tabs=new EnumMap<>(Category.class);
        private Sort sort=Sort.NONE;
        private boolean descending;
        private long orderedAt;
        private final java.text.Collator names=java.text.Collator.getInstance(Locale.forLanguageTag(L10n.language()));
        TimerWnd() {
            super(Coord.z,"Timers",true);
            reqclose(this::hide);
            try{sort=Sort.valueOf(Utils.getpref("hearthborn/timers/sort","NONE"));}catch(IllegalArgumentException ignored){}
            descending=Utils.getprefb("hearthborn/timers/descending",false);
            int tabX=0;
            for(Category tab:Category.values()){
                Button button=add(new Button(UI.scale(140),tab.title,false,()->select(tab)),Coord.of(tabX,0));
                tabs.put(tab,button);tabX+=UI.scale(145);
            }
            tabs.get(category).disable(true);
            nameHeader=add(header("Timer",Sort.NAME),UI.scale(5,35));
            timeHeader=add(header("Time left",Sort.TIME),Coord.of(TIME_X,UI.scale(35)));
            add(new Label("Actions"),Coord.of(ACTION_X,UI.scale(35)));
            scroll=add(new Scrollport(Coord.of(ROW_WIDTH+UI.scale(15),UI.scale(35))),UI.scale(0,55));
            addbtn=add(new Button(UI.scale(140),"Add timer",false,this::openAdd));
            headers();layoutRows(System.currentTimeMillis());
        }
        private Label header(String title,Sort column){
            return new Label(title){public boolean mousedown(MouseDownEvent event){
                if(event.b!=1)return super.mousedown(event);
                descending=sort==column&&!descending;sort=column;
                Utils.setpref("hearthborn/timers/sort",sort.name());Utils.setprefb("hearthborn/timers/descending",descending);
                headers();layoutRows(System.currentTimeMillis());return true;
            }};
        }
        private void headers(){
            nameHeader.settext(L10n.tr("Timer")+(sort==Sort.NAME?(descending?" ↓":" ↑"):""));
            timeHeader.settext(L10n.tr("Time left")+(sort==Sort.TIME?(descending?" ↓":" ↑"):""));
        }
        public void added(){super.added();refresh(System.currentTimeMillis());}
        private void select(Category next){
            category=next;
            for(Map.Entry<Category,Button> tab:tabs.entrySet())tab.getValue().disable(tab.getKey()==category);
            scroll.bar.val=0;scroll.cont.sy=0;
            built=TimerService.generation();rebuild();
        }
        private void openAdd(){GameUI gui=getparent(GameUI.class);if(gui!=null){select(Category.MANUAL);AddWnd.open(gui, this);}}
        @Override public void tick(double dt) {
            super.tick(dt);
            if(visible())
                refresh(System.currentTimeMillis());
        }
        private void refresh(long now){
            if(built!=TimerService.generation()){built=TimerService.generation();rebuild();}
            for(Row row:rows)row.tick(now);
            if(sort==Sort.TIME&&now-orderedAt>=1000)layoutRows(now);
        }
        private void rebuild(){
            for(Widget child:new ArrayList<>(scroll.cont.children()))child.destroy();
            rows.clear();
            for(TimerEntry item:TimerService.copy())if(category.accepts(item.source))rows.add(scroll.cont.add(new Row(item),Coord.z));
            if(rows.isEmpty())scroll.cont.add(new Label("No timers"),UI.scale(5,5));
            layoutRows(System.currentTimeMillis());
        }
        private void layoutRows(long now){
            orderedAt=now;
            List<Row> ordered=new ArrayList<>(rows);
            Comparator<Row> byName=(a,b)->names.compare(displayName(a.item),displayName(b.item));
            Comparator<Row> order=sort==Sort.TIME?Comparator.comparingLong((Row row)->row.item.remaining(now)).thenComparing(byName):byName;
            if(sort!=Sort.NONE)ordered.sort(descending?order.reversed():order);
            int height=0,index=0;
            for(Row row:ordered){row.c=Coord.of(0,height);row.alternate=(index++%2)!=0;height+=row.sz.y+UI.scale(1);}
            int limit=UI.scale(360);GameUI gui=getparent(GameUI.class);
            if(gui!=null)limit=Math.min(limit,Math.max(UI.scale(90),gui.sz.y-UI.scale(160)));
            scroll.resize(Coord.of(ROW_WIDTH+scroll.bar.sz.x,Math.max(UI.scale(35),Math.min(height+UI.scale(10),limit))));
            scroll.cont.update();scroll.bar.val=Math.max(0,Math.min(scroll.bar.val,scroll.bar.max));scroll.cont.sy=scroll.bar.val;
            addbtn.move(Coord.of(0,scroll.c.y+scroll.sz.y+UI.scale(5)));pack();
        }
    }

    static String displayName(TimerEntry item) {
        if(item.source != null && item.source.startsWith("village-idol:"))
            return L10n.tr("Village idol readiness");
        if(item.source != null && item.source.startsWith(ClaimTimers.EXPANSION_PREFIX))
            return L10n.tr(ClaimTimers.EXPANSION_TITLE);
        if(item.source != null && item.source.startsWith("grill:"))
            return ObjectTimers.grillName(item.source);
        if(NaturalResourceTimers.isRefill(item.source))
            return NaturalResourceTimers.name(item.source,item.name);
        if(StarFall.isTimer(item.source))return StarFall.timerName(item.source);
        if(item.source!=null && item.source.startsWith("siege:")) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("^Siege engine #([^ ]+).*$").matcher(item.name);
            if(m.matches())return L10n.format("Siege engine #%s",m.group(1));
        }
        return hearthborn.world.ObjectNames.timerName(item.source,item.name);
    }
    private static final class Row extends Widget {
        private final TimerEntry item;
        private final LineText clock;
        private final Button toggle;
        private String toggleLabel;
        private boolean alternate;
        Row(TimerEntry item){
            this.item=item;
            String title=displayName(item);
            LineText name=add(new LineText(title,NAME_WIDTH-UI.scale(6),Color.WHITE),UI.scale(3,3));
            clock=add(new LineText("",UI.scale(90),Color.WHITE),Coord.of(TIME_X,UI.scale(3)));
            toggleLabel=item.running()?"Stop":"Start";
            toggle=add(new Button(UI.scale(60),toggleLabel,false,this::toggle),Coord.of(ACTION_X,UI.scale(3)));
            add(new Button(UI.scale(60),"Delete",false,()->TimerService.remove(item)),Coord.of(ACTION_X+UI.scale(66),UI.scale(3)));
            int height=Math.max(toggle.sz.y,Text.std.height())+UI.scale(6);
            resize(Coord.of(ROW_WIDTH,height));tick(System.currentTimeMillis());
            name.move(Coord.of(UI.scale(3),(height-name.sz.y)/2));clock.move(Coord.of(TIME_X,(height-clock.sz.y)/2));
        }
        public Object tooltip(Coord at, Widget previous) {
            String text = displayName(item) + (item.running() ? "\n" + new Date(item.finish) : "");
            if(StarFall.isTimer(item.source))
                return text + "\n" + L10n.tr("Real time. This is an upper estimate, not an exact impact time.");
            if(NaturalResourceTimers.isRefill(item.source))
                text += "\n" + L10n.tr("Estimated refill time. Inspect the resource to check.");
            if(item.source != null)
                return text + "\n" + L10n.tr("Click to highlight the object.");
            return text;
        }
        public void draw(GOut g){g.chcolor(alternate?new Color(8,8,8,128):new Color(0,0,0,128));g.frect(Coord.z,sz);g.chcolor();super.draw(g);}
        public boolean mousedown(MouseDownEvent event){
            if(event.propagate(this))return true;
            if(event.b==1 && item.source!=null&&!StarFall.isTimer(item.source)){hearthborn.world.ObjectNames.highlight(getparent(GameUI.class),item.source);return true;}
            return super.mousedown(event);
        }
        private void toggle(){
            long now=System.currentTimeMillis();
            TimerService.toggle(item,now);
            tick(now);
        }
        private void tick(long now){
            clock.set(TimerWindow.clock(item.remaining(now)));
            String wanted=item.running()?"Stop":"Start";
            if(!wanted.equals(toggleLabel)){toggleLabel=wanted;toggle.change(wanted);}
        }
    }

    private static final class LineText extends Widget {
        private final int width;
        private final Color color;
        private Text.Line line;
        private String shown = "";

        LineText(String text, int width, Color color) {
            this.width = width;
            this.color = color;
            set(text);
        }

        void set(String text) {
            if(text.equals(shown) && line != null)
                return;
            shown = text;
            if(line != null)
                line.dispose();
            Text.Line fitted = Text.std.ellipsize(text, Math.max(1, width));
            if(color.equals(Color.WHITE))
                line = fitted;
            else {
                line = Text.std.render(fitted.text, color);
                fitted.dispose();
            }
            resize(line.sz());
        }

        public void draw(GOut g) {
            if(line != null)
                g.image(line.tex(), Coord.z);
        }

        public void dispose() {
            super.dispose();
            if(line != null)
                line.dispose();
        }
    }

    private static final class AddWnd extends Window {
        private final TextEntry name, hours, minutes, seconds;
        private final TimerWnd owner;

        private AddWnd(TimerWnd owner) {
            super(Coord.z, "Add timer", true);
            this.owner = owner;
            reqclose(() -> { if(ui != null) ui.destroy(this); });
            Widget title = add(new Label("Timer name"), Coord.z);
            name = add(new TextEntry(UI.scale(260), ""), title.pos("bl").adds(0, 4));
            hours = add(new TextEntry(UI.scale(42), "0"), name.pos("bl").adds(0, 8));
            Widget hoursLabel = add(new Label("hours"), hours.pos("ur").adds(4, 2));
            minutes = add(new TextEntry(UI.scale(42), "0"), hoursLabel.pos("ur").adds(8, -2));
            Widget minutesLabel = add(new Label("min"), minutes.pos("ur").adds(4, 2));
            seconds = add(new TextEntry(UI.scale(42), "0"), minutesLabel.pos("ur").adds(8, -2));
            add(new Label("sec"), seconds.pos("ur").adds(4, 2));
            add(new Button(UI.scale(140), "Add timer", false, this::commit), hours.pos("bl").adds(0, 10));
            pack();
        }

        public void added() {
            super.added();
            setfocus(name);
        }

        private void commit() {
            int h = number(hours.text());
            int m = number(minutes.text());
            int s = number(seconds.text());
            if(!TimerService.add(name.text(), h, m, s)) {
                if(ui != null)
                    ui.error("Enter a duration.");
                return;
            }
            owner.select(Category.MANUAL);
            if(ui != null)
                ui.destroy(this);
        }

        private static int number(String text) {
            int value = 0;
            for(int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if(c >= '0' && c <= '9')
                    value = value * 10 + (c - '0');
            }
            return value;
        }

        static void open(GameUI gui, TimerWnd owner) {
            AddWnd wnd = new AddWnd(owner);
            gui.addchild(wnd, "misc", new Coord2d(0.5, 0.4));
        }
    }
    private TimerWindow() {}
}
