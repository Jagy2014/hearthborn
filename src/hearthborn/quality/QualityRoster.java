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

package hearthborn.quality;

import haven.*;
import haven.render.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import java.awt.Color;
import java.util.*;
import java.util.List;
import java.util.regex.*;

/**
 * @author Jagy2014
 */
public final class QualityRoster extends Window {
    private static final Map<GameUI,QualityRoster> OPEN=new WeakHashMap<>();
    private final GameUI gui;
    private final Set<String> marked=new HashSet<>();
    private String tab="building", selected;
    private int sort;
    private boolean reverse,adding,editing,ownInspect,quick;
    private volatile boolean finishInspect;
    private final Map<Gob,Double> pendingQuality=new IdentityHashMap<>();
    private final Map<Gob,Double> receivedQuality=new IdentityHashMap<>();
    private final TextEntry comment;
    private final RosterButton add;
    private final int[] columns=new int[4],columnWidths=new int[4];
    private final SListBox<RosterStore.Rec,Widget> list;
    private List<RosterStore.Rec> rows=Collections.emptyList();
    private double refresh;
    private Gob highlighted;

    public static void toggle(GameUI gui) {
        QualityRoster known=OPEN.get(gui);
        if(known!=null && known.parent!=null) {if(!known.visible){known.show();known.raise();}else known.reqdestroy();return;}
        QualityRoster window=new QualityRoster(gui);
        OPEN.put(gui,window);
        gui.addchild(window,"misc",new Coord2d(.35,.35),new Object[]{"id","quality-roster"});
    }
    private static Resource cursor;
    public static synchronized Resource cursor(){
        if(cursor!=null)return cursor;
        Resource.Virtual res=new Resource.Virtual(Resource.local(),"hearthborn/quality-cursor",1);
        try{
            java.awt.image.BufferedImage source=hearthborn.ui.XtensionsMenu.qualityIcon(true);
            java.awt.image.BufferedImage icon=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics=icon.createGraphics();graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source,0,0,32,32,null);graphics.dispose();
            java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(icon,"png",png);
            MessageBuf image=new MessageBuf();image.addint16((short)0).addint16((short)-1).adduint8(0).addint16((short)-1).addint16((short)0).addint16((short)0).addbytes(png.toByteArray());
            res.add(res.new Image(new MessageBuf(image.fin())));
            MessageBuf neg=new MessageBuf();for(int value:new int[]{2,2,0,0,0,0,32,32})neg.addint16((short)value);neg.adduint8(0);
            res.add(res.new Neg(new MessageBuf(neg.fin())));cursor=res;return res;
        }catch(java.io.IOException error){throw new RuntimeException(error);}
    }
    public static boolean recording(GameUI gui){QualityRoster r=OPEN.get(gui);return r!=null&&r.quick&&r.adding;}
    public static void toggleRecording(GameUI gui){
        if(gui==null||gui.map==null)return;
        QualityRoster roster=OPEN.get(gui);
        if(recording(gui)){roster.cancel();return;}
        if(roster==null){toggle(gui);roster=OPEN.get(gui);roster.hide();}
        if(gui.map.minesweeperMarking)hearthborn.mining.Minesweeper.toggle(gui,true);
        roster.quick=true;roster.finishInspect=false;roster.adding=true;roster.ownInspect=true;
        RosterStore.clearVesselPick();gui.act("inspect");
        gui.ui.msg(L10n.tr("Click objects to record quality. Escape exits."));
    }
    public QualityRoster(GameUI gui) {
        super(Coord.z,"Quality roster");
        this.gui=gui;
        int gap=UI.scale(6);
        String[] keys={"building","tree","seed","sapling","barrel"},titles={"Buildings","Trees","Farming","Saplings","Barrels"};
        int tabsRight=0,tabsHeight=0;
        for(int i=0;i<keys.length;i++) {
            String key=keys[i];
            RosterButton tabButton=add(new RosterButton(UI.scale(100),titles[i],() -> {cancel();tab=key;selected=null;refresh();}).active(() -> tab.equals(key)),Coord.of(tabsRight,0));
            if(key.equals("seed"))tabButton.settip("Crop seeds and planting material, including roots, onion bulbs and garlic.");
            tabsRight=tabButton.c.x+tabButton.sz.x+gap;tabsHeight=Math.max(tabsHeight,tabButton.sz.y);
        }
        String[] heads={"Name","Quality","Count / amount","Comment"};
        int[] widths={178,55,125,145};
        RosterButton[] headers=new RosterButton[4];
        columns[0]=UI.scale(22);
        for(int i=0;i<heads.length;i++) {
            final int column=i;
            headers[i]=new RosterButton(UI.scale(widths[i]),heads[i],() -> {reverse=sort==column&&!reverse;sort=column;refresh();});
            columnWidths[i]=headers[i].sz.x;
            if(i>0)columns[i]=columns[i-1]+columnWidths[i-1]+gap;
        }
        RosterButton remove=new RosterButton(UI.scale(150),"Remove selected",() -> {RosterStore.removeAll(marked);marked.clear();refresh();});
        add=new RosterButton(UI.scale(100),"Add",() -> setAdding(!adding));
        RosterButton all=new RosterButton(UI.scale(100),"Select all",() -> {for(RosterStore.Rec r:rows)marked.add(key(r));});
        RosterButton none=new RosterButton(UI.scale(100),"Select none",marked::clear);
        RosterButton[] footer={remove,add,all,none};
        int footerWidth=0;for(RosterButton button:footer)footerWidth+=button.sz.x+gap;
        int width=Math.max(UI.scale(560),Math.max(columns[3]+columnWidths[3]+UI.scale(20),Math.max(tabsRight-gap,footerWidth-gap)));
        comment=add(new TextEntry(width,RosterStore.stamp()) {
            protected void changed() {
                super.changed();
                if(editing)return;
                RosterStore.setStamp(text());
                if(selected!=null)RosterStore.setComment(selected,text());
            }
        },Coord.of(0,tabsHeight+gap));
        comment.settip("The last comment is applied to newly added records.");
        int headerY=comment.c.y+comment.sz.y+gap*2,headerHeight=0;
        for(int i=0;i<headers.length;i++) {add(headers[i],Coord.of(columns[i],headerY));headerHeight=Math.max(headerHeight,headers[i].sz.y);}
        int rowHeight=Math.max(UI.scale(26),Text.std.height()+UI.scale(8));
        list=add(new SListBox<RosterStore.Rec,Widget>(Coord.of(width,UI.scale(240)),rowHeight) {
            protected List<? extends RosterStore.Rec> items(){return rows;}
            protected Widget makeitem(RosterStore.Rec record,int index,Coord size) {
                Widget row=new Widget(size) {
                    public void draw(GOut g) {
                        g.chcolor(key(record).equals(selected)?new Color(120,150,80,95):index%2==0?new Color(255,255,255,16):new Color(255,255,255,28));
                        g.frect(Coord.z,sz);g.chcolor();super.draw(g);
                    }
                    public boolean mousedown(MouseDownEvent event) {
                        if(event.propagate(this))return true;
                        if(event.b==1)select(record);
                        return true;
                    }
                };
                String key=key(record);
                row.add(new CheckBox("").state(() -> marked.contains(key)).set(v -> {if(v)marked.add(key);else marked.remove(key);}),UI.scale(1,3));
                String name=displayName(record);
                row.add(text(name,columnWidths[0]),Coord.of(columns[0],UI.scale(3)));
                row.add(text(RosterStore.fmtQ(record.q),columnWidths[1]),Coord.of(columns[1],UI.scale(3)));
                row.add(text(record.kind.equals("barrel")?record.amount:record.count>0?Integer.toString(record.count):"",columnWidths[2]),Coord.of(columns[2],UI.scale(3)));
                row.add(text(record.comment,columnWidths[3]),Coord.of(columns[3],UI.scale(3)));
                row.settip(name+" · "+RosterStore.fmtQ(record.q)+"\n"+record.comment);
                return row;
            }
        },Coord.of(0,headerY+headerHeight+gap));
        int footerX=0,footerY=list.c.y+list.sz.y+gap;
        for(RosterButton button:footer) {add(button,Coord.of(footerX,footerY));footerX+=button.sz.x+gap;}
        pack();reqclose(this::reqdestroy);refresh();
    }
    /** Native framed buttons, shared with the timer window. */
    private static final class RosterButton extends Button {
        private java.util.function.BooleanSupplier active=()->false;
        private boolean lastDisabled;
        RosterButton(int width,String title,Runnable action){super(width,title,false,action);resize(Coord.of(sz.x,Math.max(sz.y,text.sz().y+UI.scale(6))));}
        RosterButton active(java.util.function.BooleanSupplier state){active=state;return this;}
        public void tick(double dt){boolean value=active.getAsBoolean();if(value!=lastDisabled){lastDisabled=value;disable(value);}super.tick(dt);}
    }
    private Widget text(String value,int width) {
        String text=value==null?"":value;
        return new Widget() {
            final Text.Line label=Text.std.ellipsize(text,width);
            {resize(Coord.of(width,label.sz().y));settip(text);}
            public void draw(GOut g){g.image(label.tex(),Coord.z);}
            public void dispose(){label.dispose();super.dispose();}
        };
    }
    private static String key(RosterStore.Rec record) {return RosterStore.key(record);}
    private static String displayName(RosterStore.Rec record) {
        return record.kind.equals("seed")||record.kind.equals("sapling")?RosterStore.seedDisplayName(record.name)
            :record.kind.equals("building")||record.kind.equals("tree")?RosterStore.buildingDisplayName(record):record.name;
    }
    private void select(RosterStore.Rec record) {
        selected=key(record);editing=true;comment.settext(record.comment==null?"":record.comment);editing=false;
        if(highlighted!=null)highlighted.delattr(Selected.class);
        highlighted=null;
        if(record.gobId!=0 && gui.ui!=null && gui.ui.sess!=null) {
            for(Gob gob:gui.ui.sess.glob.oc.snapshot())if(RosterStore.matches(record,gob)) {highlighted=gob;break;}
            if(highlighted!=null)highlighted.setattr(new Selected(highlighted));
        }
    }
    private void refresh() {
        List<RosterStore.Rec> values=new ArrayList<>(tab.equals("building")?RosterStore.buildings():tab.equals("tree")?RosterStore.trees():tab.equals("barrel")?RosterStore.barrels():tab.equals("seed")?RosterStore.seeds():RosterStore.saplings());
        Comparator<RosterStore.Rec> order=sort==1?Comparator.comparingDouble(r -> -r.q)
            :sort==2?Comparator.comparingInt(r -> r.count):Comparator.comparing(r -> sort==3?Objects.toString(r.comment,""):Objects.toString(displayName(r),""),String.CASE_INSENSITIVE_ORDER);
        values.sort(reverse?order.reversed():order);rows=values;if(list!=null)list.reset();
    }
    public void tick(double dt) {
        for(Iterator<Map.Entry<Gob,Double>> it=pendingQuality.entrySet().iterator();it.hasNext();){
            Map.Entry<Gob,Double> entry=it.next();Gob gob=entry.getKey();
            try {
                if(recordPicked(gob)) {it.remove();receivedQuality.remove(gob);refresh();continue;}
            } catch(Loading pending) {}
            if(Utils.rtime()>=entry.getValue()) {
                it.remove();receivedQuality.remove(gob);if(ui!=null)ui.msg(L10n.tr("The server did not provide this object's quality. Try inspecting it again."));
            }
        }
        if(finishInspect&&pendingQuality.isEmpty())cancel();super.tick(dt);if((refresh+=dt)>1){refresh=0;refresh();}
    }
    private void setAdding(boolean on) {
        if(!on) {cancel();return;}
        finishInspect=false;adding=true;add.change("Done");RosterStore.clearVesselPick();
        if(tab.equals("building")||tab.equals("tree")) {ownInspect=true;gui.act("inspect");}
        if(ui!=null)ui.msg(L10n.tr(tab.equals("seed")?"Click crop seeds or planting material in an inventory, including garlic bulbs. Stacks are supported.":"Click a building, tree, barrel, seed or sapling item to add it."));
    }
    private void cancel() {
        finishInspect=false;adding=false;quick=false;pendingQuality.clear();receivedQuality.clear();add.change("Add");
        if(ownInspect) releaseInspect(gui);
        ownInspect=false;
    }
    /** Drop Inspect without clicking the world or selecting an unrelated cancellable dialog. */
    public static void releaseInspect(GameUI gui) {
        if(gui==null||gui.map==null)return;
        gui.map.cancelPendingClicks();
        Widget owner=inspectOwner(gui.map);
        Widget inspector=hearthborn.ui.Inspect.nativeInspector(gui);
        Widget target=inspector!=null&&inspector.cancancel?inspector
            :owner!=null&&owner.cancancel?owner:cancellable(gui.map);
        if(target==null)target=inspector!=null?inspector:owner!=null?owner:gui.map;
        if(owner!=null)owner.cursor=null;
        if(gui.ui!=null&&gui.ui.widgetid(target)>=0)target.wdgmsg("cancel");
    }
    /** Mine marking paints its own cursor over Inspect. Leaving it must still drop the loupe. */
    public static void releaseIfInspect(GameUI gui) {
        if(gui!=null && gui.map!=null && serverInspect(gui.map)) releaseInspect(gui);
    }
    public static boolean serverInspect(Widget widget) {
        return inspectOwner(widget)!=null;
    }
    private static Widget inspectOwner(Widget widget) {
        try {
            for(Widget current=widget; current!=null; current=current.parent) {
                if(current.cursor==null) continue;
                Resource resource=current.cursor.get();
                return resource!=null && inspectCursor(resource.name)?current:null;
            }
        } catch(Loading ignored) {}
        return null;
    }
    static boolean inspectCursor(String name) {
        return name!=null && (name.endsWith("/inspect") || name.endsWith("/study"));
    }
    static Widget cancellable(Widget widget) {
        for(Widget current=widget;current!=null;current=current.parent)
            if(current.cancancel)return current;
        return null;
    }
    public static boolean cancelAdd(GameUI gui) {
        QualityRoster roster=OPEN.get(gui);
        if(roster==null || !roster.adding)return false;
        roster.cancel();return true;
    }
    private static List<RosterStore.Rec> plantRecords() {
        List<RosterStore.Rec> records=new ArrayList<>(RosterStore.seeds());
        records.addAll(RosterStore.saplings());return records;
    }
    public static boolean pick(WItem item) {
        GameUI gui=item.getparent(GameUI.class);
        QualityRoster roster=OPEN.get(gui);
        if(roster==null || !roster.adding)return false;
        try {
            Map<String,String> before=new HashMap<>();
            for(RosterStore.Rec record:plantRecords())before.put(key(record),record.q+":"+record.count);
            if(!RosterStore.observeSeed(item.item)) {if(item.ui!=null)item.ui.error("This item is not planting material, or its quality is not available yet.");return true;}
            String id=RosterStore.seedIdFromRes(item.item.resource().name);
            boolean reported=false;
            for(RosterStore.Rec record:plantRecords()) {
                String previous=before.get(key(record));
                if(Objects.equals(previous,record.q+":"+record.count))continue;
                roster.tab=record.kind;
                if(item.ui!=null)item.ui.msg(L10n.format(previous==null?
                    "Added: %s. Quality: %s; count: %s.":"Updated: %s. Quality: %s; count: %s.",
                    RosterStore.seedDisplayName(record.name),RosterStore.fmtQ(record.q),Integer.toString(record.count)));
                reported=true;
            }
            if(!reported && item.ui!=null) {
                RosterStore.Rec known=id==null?null:RosterStore.get(RosterStore.KIND_SEED.equals(RosterStore.plantKind(id))?RosterStore.seedKey(id):RosterStore.saplingKey(id,GameObjects.quality(item.item)));
                if(known!=null) {
                    roster.tab=known.kind;
                    item.ui.msg(L10n.format("Unchanged: %s. Best quality: %s; count: %s.",
                        RosterStore.seedDisplayName(known.name),RosterStore.fmtQ(known.q),Integer.toString(known.count)));
                } else item.ui.msg(L10n.tr("No changes: these seeds or saplings are already recorded with equal or better quality."));
            }
            if(!roster.quick)roster.cancel();roster.refresh();return true;
        } catch(Loading e){if(item.ui!=null)item.ui.error("Items are still loading. Try again.");return true;}
    }
    public static void mapPick(MapView map,Coord at) {
        GameUI gui=map.getparent(GameUI.class);
        QualityRoster roster=OPEN.get(gui);
        if(roster==null || !roster.adding)return;
        map.new Hittest(at) {
            protected void hit(Coord pc,Coord2d mc,ClickData data) {
                if(data==null)return;
                Gob gob=null;
                if(data.ci instanceof Gob.GobClick)gob=((Gob.GobClick)data.ci).gob;
                else for(Object node:data.array())if(node instanceof Gob){gob=(Gob)node;break;}
                if(gob!=null)picked(gui,gob);
            }
            protected void nohit(Coord pc) {}
        }.run();
    }
    public static void picked(GameUI gui,Gob gob) {
        QualityRoster roster=OPEN.get(gui);
        if(roster==null || !roster.adding || gob==null)return;
        roster.request(gob);
    }
    /** Native Inspect replies carry their actual object ID; observe before notice wrappers hide it. */
    public static void notice(UI ui,UI.Notice notice) {
        if(ui==null||ui.root==null||ui.sess==null||notice==null
            ||!notice.getClass().getName().equals("haven.res.ui.inspect.Info"))return;
        GameUI gui=ui.root.getchild(GameUI.class);QualityRoster roster=OPEN.get(gui);
        if(roster==null)return;
        Double quality=hearthborn.ui.Inspect.qualityValue(notice.message());
        Number id=GameObjects.field(notice,"gobid",Number.class);
        if(quality==null||!Double.isFinite(quality)||quality<=0||id==null)return;
        Gob gob=ui.sess.glob.oc.getgob(id.longValue());
        if(gob==null||!roster.adding&&!roster.pendingQuality.containsKey(gob))return;
        roster.receivedQuality.put(gob,quality);roster.request(gob);
    }
    private boolean recordPicked(Gob gob) {
        if(RosterStore.isVesselGob(gob)) {
            RosterStore.markVesselPick(gob);tab="barrel";return true;
        }
        if(!RosterStore.isBuildingGob(gob))return true;
        tab=RosterStore.isWoodyPlant(GameObjects.resourceName(gob))?"tree":"building";
        hearthborn.world.ObjectNames.touch(gob);
        Double received=receivedQuality.get(gob);
        double quality=received==null?quality(gob):received;
        RosterStore.noteBuilding(gob,quality,true);return quality>0;
    }
    private void request(Gob gob) {
        // Keep the selected object even if its resource or map grid is still loading.
        pendingQuality.putIfAbsent(gob,Utils.rtime()+10);
        try {
            if(recordPicked(gob)) {pendingQuality.remove(gob);receivedQuality.remove(gob);}
        } catch(Loading pending) {}
        // Let the original Inspect click complete before exiting its mode.
        if(!quick){finishInspect=ownInspect;adding=false;add.change("Add");}refresh();
    }
    public static double quality(Gob gob) {
        for(GAttrib attribute:gob.attributes())if(attribute.getClass().getName().endsWith("inspect.SavedInfo")) {
            Object lines=GameObjects.field(attribute,"lines",Object.class);
            Iterable<?> values=lines instanceof Iterable?(Iterable<?>)lines:lines instanceof Object[]?Arrays.asList((Object[])lines):null;
            if(values!=null)for(Object line:values) {
                Matcher match=Pattern.compile("(?:Quality|"+Pattern.quote(L10n.tr("Quality"))+"):\\s*([0-9]+(?:[.,][0-9]+)?)").matcher(String.valueOf(line));
                if(match.find())return Double.parseDouble(match.group(1).replace(',','.'));
            }
        }
        return 0;
    }
    public static void observe(Gob gob) {
        if(!RosterStore.isBuildingGob(gob))return;
        RosterStore.Rec known=RosterStore.trackedGob(gob);
        if(known==null)return;
        double q=quality(gob);RosterStore.noteBuilding(gob,q,false);
    }
    public void dispose() {
        cancel();if(highlighted!=null)highlighted.delattr(Selected.class);
        OPEN.remove(gui,this);super.dispose();
    }
    private static final class Selected extends GAttrib implements Gob.SetupMod {
        private final MixColor color=new MixColor(255,220,40,150);
        Selected(Gob gob){super(gob);}
        public Pipe.Op gobstate(){return color;}
    }
}
