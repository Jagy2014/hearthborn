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
import java.awt.event.KeyEvent;
import java.util.*;

/**
 * Separate server belt ranges, with positions saved independently of the main belt.
 *
 * @author Jagy2014
 */
public final class Toolbars {
    public enum ChatMode {
        NONE("None"),NUMERIC("Numeric toolbar"),ADDITIONAL("Additional toolbar"),NUMERIC_FUNCTION("Numeric + F toolbar");
        public final String title;
        ChatMode(String title){this.title=title;}
    }
    public static ChatMode chatMode(){
        try{return ChatMode.valueOf(Utils.getpref("hearthborn/toolbar/chat",Utils.getprefb("hearthborn/toolbar/docked",true)?"NUMERIC":"NONE"));}
        catch(IllegalArgumentException ignored){return ChatMode.NUMERIC;}
    }
    public static void chatMode(ChatMode mode){Utils.setpref("hearthborn/toolbar/chat",mode.name());}
    public static int chatWidth(GameUI gui){return Math.max(1,gui.chat.detached()?gui.chatDockWidth():gui.chat.sz.x);}
    public static int chatLeft(GameUI gui){return gui.chat.detached()?gui.chatDockBase().x:gui.chat.c.x;}
    public static int chatTop(GameUI gui){return gui.chat.detached()?gui.sz.y:Math.min(gui.chat.c.y,gui.sz.y);}
    public static Bar attached(GameUI gui){
        if(chatMode()!=ChatMode.ADDITIONAL)return null;
        for(Bar bar:gui.children(Bar.class))if(!bar.function)return bar;
        return null;
    }
    /** A bar pinned to the bottom is not a separate panel. Equipment always is. */
    public static boolean separate(String id){
        ChatMode mode=chatMode();
        if("numeric".equals(id))return mode!=ChatMode.NUMERIC&&mode!=ChatMode.NUMERIC_FUNCTION;
        if("function".equals(id))return mode!=ChatMode.NUMERIC_FUNCTION;
        if("extra".equals(id))return mode!=ChatMode.ADDITIONAL;
        return true;
    }
    /** The bottom bar stays visible. The F row is drawn on Numeric + F, not as its own panel. */
    public static boolean shown(String id){
        if("function".equals(id))return separate(id)&&enabled(id);
        if(!separate(id))return true;
        return enabled(id);
    }
    /** The belt slot under the n-th button of an additional toolbar, or -1 while the toolbar is off or has no such button. */
    public static int panelSlot(GameUI gui,String id,int index){
        if(gui==null)return -1;
        for(Row row:gui.children(Row.class))if(id.equals(row.id))return row.visible()?row.slotOf(index):-1;
        return -1;
    }
    public static void layout(GameUI gui){for(Bar bar:gui.children(Bar.class))bar.layout();}
    public static boolean functionKey(GameUI gui,Widget.GlobKeyEvent event){
        if(gui==null||gui.beltwdg==null||event.mods!=0||event.code<KeyEvent.VK_F1||event.code>KeyEvent.VK_F12)return false;
        gui.beltwdg.keyact(132+event.code-KeyEvent.VK_F1);return true;
    }
    public static boolean stripVertical(){return Utils.getprefb("hearthborn/toolbar/strip/vertical",false);}
    public static void stripVertical(boolean value){Utils.setprefb("hearthborn/toolbar/strip/vertical",value);}
    public static boolean stripMoved(){return Utils.getprefb("hearthborn/toolbar/strip/moved",false);}
    public static void stripMoved(boolean value){Utils.setprefb("hearthborn/toolbar/strip/moved",value);}
    /** Shared compact rows for expanded additional and numeric/F strips. */
    public static int columns(int width,int count){return Math.max(1,Math.min(count,(width-UI.scale(10)+ToolbarFrame.gap())/(Inventory.invsq.sz().x+ToolbarFrame.gap())));}
    /** Top of the first docked icon. The old 22-pixel band held the chat button, which now sits on the chat. */
    public static int dockTop(){return UI.scale(25)-UI.scale(22);}
    public static int dockBody(int vanillaHeight){return Math.max(1,vanillaHeight-UI.scale(22));}
    public static Coord dockCell(int index,int columns){return Coord.of(UI.scale(5),dockTop()).add((index%columns)*(Inventory.invsq.sz().x+ToolbarFrame.gap()),(index/columns)*(Inventory.invsq.sz().y+ToolbarFrame.gap()));}
    public static final int EXTRA_CAPACITY=22;
    public static boolean extra(String id){return "extra".equals(id)||"extra2".equals(id);}
    public static int extraCapacity(String id){return "extra2".equals(id)?12:10;}
    public static final int[] EXTRA_SLOTS=range(144,EXTRA_CAPACITY);
    /** New panels use local bindings, separate from all server-backed belt pages. */
    public static int[] filledExtras(GameUI gui){
        if(gui==null||gui.belt==null)return new int[0];
        int[] found=new int[gui.belt.length];int n=0;
        for(int slot=144;slot<gui.belt.length;slot++)if(occupied(gui,slot))found[n++]=slot;
        int[] result=new int[n];System.arraycopy(found,0,result,0,n);return result;
    }
    /** The free cell after the last extra icon. A hole in front of a filled slot is not reused, so the new icon stays last. */
    public static int emptyExtra(GameUI gui){
        if(gui==null||gui.belt==null)return EXTRA_SLOTS[0];
        ExtraLayout layout=extraLayouts.computeIfAbsent(gui,ExtraLayout::new);layout.reconcile(gui);
        for(int slot:layout.slots)if(!occupied(gui,slot))return slot;
        return -1;
    }
    /** Keep existing icon order and reserve the remaining cells instead of growing the panel. */
    private static int[] fixedExtras(GameUI gui){
        int[] result=new int[EXTRA_CAPACITY],filled=filledExtras(gui);
        int count=Math.min(result.length,filled.length);
        System.arraycopy(filled,0,result,0,count);
        int length=gui==null||gui.belt==null?256:gui.belt.length;
        for(int slot:growth(length)){
            if(count==result.length)break;
            if(gui==null||gui.belt==null||!occupied(gui,slot))result[count++]=slot;
        }
        return result;
    }
    /** Stable presentation slots shared by the two panels; the actual belt bindings stay native. */
    private static final Map<GameUI,ExtraLayout> extraLayouts=new WeakHashMap<>();
    private static final class ExtraLayout {
        final int[] slots;
        final String key;
        ExtraLayout(GameUI gui){
            key="hearthborn/toolbar/extra/slots/"+gui.chrid;
            List<String> saved=Utils.getprefsl(key,new String[0]);
            int[] restored=new int[EXTRA_CAPACITY];Set<Integer> seen=new HashSet<>();
            boolean valid=saved.size()==EXTRA_CAPACITY;
            if(valid)try{
                for(int i=0;i<saved.size();i++){
                    int slot=Integer.parseInt(saved.get(i));
                    if((slot!=106&&(slot<120||slot>131)&&(slot<144||slot>=gui.belt.length))||!seen.add(slot)){valid=false;break;}
                    restored[i]=slot;
                }
            }catch(NumberFormatException ignored){valid=false;}
            slots=valid?restored:fixedExtras(gui);
            if(!valid)save();
        }
        void save(){Utils.setprefsl(key,Arrays.stream(slots).mapToObj(Integer::toString).toList());}
        void reconcile(GameUI gui){
            boolean changed=false;
            // Restore late local bindings. Server pages are imported only through
            // an already saved legacy layout, never automatically on a fresh install.
            for(int slot:filledExtras(gui)){
                boolean present=false;for(int current:slots)if(current==slot){present=true;break;}
                if(present)continue;
                for(int i=0;i<slots.length;i++)if(!occupied(gui,slots[i])){slots[i]=slot;changed=true;break;}
            }
            if(changed)save();
        }
    }
    private static int[] panelExtras(GameUI gui,String id){
        ExtraLayout layout=extraLayouts.computeIfAbsent(gui,ExtraLayout::new);layout.reconcile(gui);
        int from="extra2".equals(id)?10:0;
        return Arrays.copyOfRange(layout.slots,from,from+extraCapacity(id));
    }
    private static int[] growth(int length){
        return range(144,Math.max(0,length-144));
    }
    private static boolean occupied(GameUI gui,int slot){return slot>=0&&slot<gui.belt.length&&gui.belt[slot]!=null;}
    private static final java.util.concurrent.ConcurrentHashMap<String,Boolean> shownBars=new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String,Boolean> upright=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean enabled(String id) {
        Boolean value=shownBars.get(id);
        if(value==null)shownBars.put(id,value=Utils.getprefb("hearthborn/toolbar/"+id,"extra2".equals(id)?enabled("extra"):true));
        return value;
    }
    public static void enabled(String id, boolean value) { shownBars.put(id,value); Utils.setprefb("hearthborn/toolbar/" + id, value); }
    private static volatile Boolean plain;
    public static boolean clean(){if(plain==null)plain=Utils.getprefb("hearthborn/toolbar/clean",true);return plain;}
    public static void clean(boolean value){plain=value;Utils.setprefb("hearthborn/toolbar/clean",value);}
    public static boolean vertical(String id){
        Boolean value=upright.get(id);
        if(value==null)upright.put(id,value=Utils.getprefb("hearthborn/toolbar/"+id+"/vertical",false));
        return value;
    }
    public static void vertical(String id,boolean value){upright.put(id,value);Utils.setprefb("hearthborn/toolbar/"+id+"/vertical",value);}
    /**
     * Nothing may sit under the button menu: when it is a row along the bottom, a panel that would cover its buttons
     * is lifted just above them instead, where it can still be reached.
     */
    static Coord clearOfMenu(GameUI gui,Coord at,Coord size){
        ActionStrip strip=gui.getchild(ActionStrip.class);
        if(strip==null||!strip.visible()||!ActionStrip.horizontal())return at;
        Coord zone=strip.columnAt(),zoneSize=strip.columnSize();
        boolean across=at.x<zone.x+zoneSize.x&&at.x+size.x>zone.x,down=at.y<zone.y+zoneSize.y&&at.y+size.y>zone.y;
        return across&&down?Coord.of(at.x,Math.max(0,zone.y-size.y)):at;
    }
    public static Coord defaultPosition(String id){
        int row=id.equals("function")?0:id.equals("extra")?1:2;
        return UI.scale(50,200).add(0,row*(ToolbarScale.size(ToolbarFrame.size(10,false)).y+UI.scale(12)));
    }
    public static void install(GameUI gui) {
        gui.add(new EquipmentStrip(), Utils.getprefc("hearthborn/toolbar/equipment/position", UI.scale(420,5)));
        gui.add(new ActionStrip(), Utils.getprefc("hearthborn/toolbar/actions/position", UI.scale(12,220)));
        if(gui.children(Row.class).isEmpty()){gui.add(new Row(gui,"function"));gui.add(new Row(gui,"extra"));gui.add(new Row(gui,"extra2"));}
    }
    private static int[] range(int start, int size) {
        int[] result=new int[size]; for(int i=0;i<size;i++) result[i]=start+i; return result;
    }
    public static final class Bar extends GameUI.Belt {
        private final GameUI gui;
        private final String id;
        private final int[] slots;
        private final boolean function;
        private final Tex[] labels;
        private UI.Grab drag;
        private Coord offset;
        private boolean vertical;
        private boolean wasAttached;
        private double scale=ToolbarScale.scale();
        private Coord layoutSize;
        private int columns,slotCount;
        public Bar(GameUI gui, String id, int[] slots, boolean function) {
            gui.super(Coord.z);
            this.gui=gui; this.id=id; this.slots=slots; this.function=function;
            labels=new Tex[function ? slots.length : 0];
            for(int i=0;i<labels.length;i++) labels[i]=ToolbarFrame.keyLabel("F"+(i+1));
            vertical=Utils.getprefb(key("vertical"),false);
            layout();
            updateVisibility();
        }
        private String key(String suffix) { return "hearthborn/toolbar/"+id+"/"+suffix; }
        private boolean isDocked(){return !function&&chatMode()==ChatMode.ADDITIONAL;}
        /** True while the panel stands where the player put it, not glued to the chat. */
        boolean floating(){return !isDocked();}
        private void layout() {
            vertical=vertical(id);
            slotCount=slots.length;
            if(isDocked()&&gui.chat!=null){
                if(!wasAttached)MagneticWindows.detach(this);
                if(drag!=null){drag.remove();drag=null;}
                int width=chatWidth(gui),minimum=dockCell(slots.length-1,slots.length).x+Inventory.invsq.sz().x+UI.scale(5);
                scale=Math.min(ToolbarScale.DOCK*HudInterfaceScale.current(),width/(double)Math.min(minimum,UI.scale(400)));
                int logicalWidth=Math.max(1,(int)Math.floor(width/scale)),capacity=slots.length+gui.belt.length-144;
                columns=columns(logicalWidth,capacity);
                slotCount=Math.min(capacity,((slots.length+columns-1)/columns)*columns);
                layoutSize=Coord.of(logicalWidth,dockCell(slotCount-1,columns).y+Inventory.invsq.sz().y+UI.scale(5));
                resize(Coord.of(width,(int)Math.ceil(layoutSize.y*scale)));
                move(Coord.of(chatLeft(gui),Math.max(0,gui.sz.y-sz.y)));wasAttached=true;gui.layoutChat();
            }else{
                scale=ToolbarScale.scale();layoutSize=ToolbarFrame.size(slots.length,vertical,function?8:0);resize(ToolbarScale.size(layoutSize));
                if(wasAttached){move(Utils.getprefc(key("position"),defaultPosition(id)));wasAttached=false;}
                fit();
            }
            updateVisibility();
        }
        public int headerHeight(){return (int)Math.ceil(dockTop()*scale);}
        private Coord logicalSize(){return layoutSize;}
        private Coord logical(Coord point){return Coord.of((int)Math.floor(point.x/scale),(int)Math.floor(point.y/scale));}
        private void fit(){
            if(parent==null||drag!=null)return;
            Coord limit=parent.sz.sub(sz).max(Coord.z);
            Coord at=Coord.of(Utils.clip(c.x,0,limit.x),Utils.clip(c.y,0,limit.y));
            if(!isDocked())at=clearOfMenu(gui,at,sz);
            move(at);
        }
        public void presize(){layout();}
        private Coord cell(int i){return isDocked()?dockCell(i,Math.max(1,columns)):floatingCell(i);}
        private Coord floatingCell(int i) {
            return ToolbarFrame.cell(i,vertical,function?8:0);
        }
        private int slot(int index){return index<slots.length?slots[index]:144+index-slots.length;}
        public int beltslot(Coord c) {
            c=logical(c);
            for(int i=0;i<slotCount;i++) if(c.isect(cell(i),Inventory.invsq.sz())) return slot(i);
            return -1;
        }
        private void updateVisibility() {
            show(shown(id));
        }
        public void tick(double dt) {
            layout();
            super.tick(dt);
        }
        public void draw(GOut g) {
            GOut content=ToolbarScale.graphics(g,logicalSize(),scale);
            panel(content,logicalSize());
            for(int i=0;i<slotCount;i++) {
                Coord c=cell(i);ToolbarFrame.hotbarSlot(content,c,Inventory.invsq.sz());
                try { if(gui.belt[slot(i)]!=null) gui.belt[slot(i)].draw(content.reclip(c.add(UI.scale(1,1)),Inventory.invsq.sz().sub(UI.scale(2,2)))); }
                catch(Loading ignored) {}
                if(function) {
                    Tex label=labels[i];
                    Coord end=ToolbarFrame.keyAnchor(c,Inventory.invsq.sz());
                    content.aimage(label,end,1,1);
                }
            }
        }
        public boolean globtype(GlobKeyEvent ev) {
            return function&&visible&&functionKey(gui,ev);
        }
        public boolean mousedown(MouseDownEvent ev) {
            if(!isDocked()&&ToolbarFrame.canDrag(ui,ev.b,logical(ev.c),logicalSize(),beltslot(ev.c)>=0)) {
                offset=ev.c;MagneticWindows.begin(this);drag=ui.grabmouse(this);raise();return true;
            }
            if(beltslot(ev.c)>=0) return super.mousedown(ev);
            return true;
        }
        public void mousemove(MouseMoveEvent ev) {
            if(drag!=null){move(c.add(ev.c.sub(offset)));MagneticWindows.drag(this);}else super.mousemove(ev);
        }
        public boolean mouseup(MouseUpEvent ev) {
            if(drag!=null&&ev.b==1) {drag.remove();drag=null;MagneticWindows.end(this);rememberPosition();return true;}
            return super.mouseup(ev);
        }
        public Object tooltip(Coord c, Widget prev) {
            int slot=beltslot(c);
            if(!isDocked()&&(slot<0||ui.modmeta))return ToolbarFrame.moveTip();
            if(slot<0)return null;
            GameUI.BeltSlot item=gui.belt[slot];
            try {
                if(item instanceof GameUI.PagBeltSlot) return ((GameUI.PagBeltSlot)item).pag.res().flayer(Resource.tooltip).t;
                if(item instanceof GameUI.ResBeltSlot) return ((GameUI.ResBeltSlot)item).getres().flayer(Resource.tooltip).t;
            } catch(Loading ignored) {}
            return null;
        }
        void rememberPosition(){Utils.setprefc(key("position"),c);}
        public void dispose() {if(drag!=null)drag.remove();for(Tex label:labels)label.dispose();super.dispose();}
    }
    /** One button panel with its own frame: the F row or the extra icons. */
    public static final class Row extends GameUI.Belt {
        public final String id;
        private final GameUI gui;
        private final Tex[] labels=new Tex[12];
        private final Tex[] keyLabels=new Tex[12];
        private final String[] keyTexts=new String[12];
        private UI.Grab drag;
        private Coord offset;
        private Coord logicalSize=Coord.z;
        /** Where the player left this panel; it is lifted off the button menu while that is in the way, and comes back after. */
        private Coord saved;
        private int dropFrom;
        private int[] slots=new int[0];
        private Coord[] at=new Coord[0];
        public Row(GameUI gui,String id){gui.super(Coord.z);this.gui=gui;this.id=id;}
        private String movedKey(){return "hearthborn/toolbar/panel/"+id+"/moved";}
        private String positionKey(){return "hearthborn/toolbar/panel/"+id+"/position";}
        public boolean moved(){return Utils.getprefb(movedKey(),false);}
        /** Puts the panel somewhere else for good, as if the player had dropped it there. */
        void relocate(Coord at){move(at);Utils.setprefb(movedKey(),true);Utils.setprefc(positionKey(),saved=c);}
        /** The belt slot of the button at this place on the panel, or -1. */
        public int slotOf(int index){return index>=0&&index<slots.length?slots[index]:-1;}
        public Coord slotAt(int index){return index>=0&&index<at.length?at[index]:Coord.z;}
        public void layout(){
            boolean on=enabled(id);
            int[] use=new int[0];
            if(on&&"function".equals(id)){use=new int[12];for(int i=0;i<12;i++)use[i]=132+i;}
            else if(on)use=panelExtras(gui,id);
            Coord slot=Inventory.invsq.sz();Coord pad=ToolbarFrame.inset();
            boolean upright=vertical(id);
            Coord[] next=new Coord[use.length];
            int x=pad.x,y=pad.y;
            for(int i=0;i<use.length;i++){
                next[i]=Coord.of(x,y);if(upright)y+=slot.y+ToolbarFrame.gap();else x+=slot.x+ToolbarFrame.gap();
            }
            slots=use;at=next;
            int right=pad.x,bottom=pad.y;
            for(Coord cell:next){right=Math.max(right,cell.x+slot.x);bottom=Math.max(bottom,cell.y+slot.y);}
            dropFrom=use.length==0?0:right;
            logicalSize=use.length==0?Coord.of(1,1):Coord.of(right+pad.x,bottom+pad.y);
            Coord target=ToolbarScale.size(logicalSize);
            if(!sz.equals(target))resize(target);
            show(on&&use.length>0);
        }
        private Coord stacked(){
            int x=gui.chat==null?UI.scale(8):chatLeft(gui);
            int below=gui.sz.y;
            if(extra(id)){
                Row function=sibling("function");
                if(function!=null&&function.visible())below=function.c.y;
                else if(gui.beltwdg!=null&&gui.beltwdg.visible())below=gui.beltwdg.c.y;
                Row first=sibling("extra"),second=sibling("extra2");
                if("extra2".equals(id)&&first!=null&&first.visible()&&first.moved())return first.c.add(first.sz.x,0);
                int height=sz.y;
                if(first!=null&&first.visible())height=Math.max(height,first.sz.y);
                if(second!=null&&second.visible())height=Math.max(height,second.sz.y);
                if("extra2".equals(id)&&first!=null&&first.visible())x+=first.sz.x;
                return Coord.of(x,Math.max(0,below-height));
            }else if(gui.beltwdg!=null&&gui.beltwdg.visible())below=gui.beltwdg.c.y;
            return Coord.of(x,Math.max(0,below-sz.y));
        }
        private Row sibling(String name){for(Row row:gui.children(Row.class))if(name.equals(row.id))return row;return null;}
        private void fit(){
            if(parent==null||drag!=null||parent.sz==null)return;
            Coord limit=parent.sz.sub(sz).max(Coord.z);
            move(clearOfMenu(gui,Coord.of(Utils.clip(c.x,0,limit.x),Utils.clip(c.y,0,limit.y)),sz));
        }
        public void tick(double dt){
            layout();
            if(drag==null){if(moved()){if(saved==null)saved=Utils.getprefc(positionKey(),stacked());move(saved);}else{saved=null;move(stacked());}}
            fit();
            super.tick(dt);
        }
        public int beltslot(Coord c){
            Coord point=ToolbarScale.logical(c);Coord size=Inventory.invsq.sz();
            for(int i=0;i<slots.length;i++)if(point.isect(at[i],size))return slots[i];
            return -1;
        }
        public boolean mousedown(MouseDownEvent ev){
            Coord point=ToolbarScale.logical(ev.c);
            if(ui!=null&&ToolbarFrame.canDrag(ui,ev.b,point,logicalSize,beltslot(ev.c)>=0)){
                offset=ev.c;MagneticWindows.begin(this);drag=ui.grabmouse(this);raise();return true;
            }
            if(beltslot(ev.c)>=0)return super.mousedown(ev);
            return true;
        }
        public void mousemove(MouseMoveEvent ev){
            if(drag!=null){move(c.add(ev.c.sub(offset)));MagneticWindows.drag(this);}else super.mousemove(ev);
        }
        /** A drop on the panel itself, rather than on a button, claims the next free extra slot. */
        private int receive(Coord c){
            int slot=beltslot(c);if(slot>=0||!extra(id))return slot;
            for(int candidate:slots)if(!occupied(gui,candidate))return candidate;
            return -1;
        }
        public boolean drop(Coord c,Coord ul){
            if(!extra(id)||beltslot(c)>=0)return super.drop(c,ul);
            int slot=receive(c);
            if(slot<0||slot>=144)return false;
            hearthborn.ui.LocalActionBelt.forget(gui,slot);gui.wdgmsg("setbelt",slot,0);return true;
        }
        public boolean dropthing(Coord c,Object thing){
            if(!extra(id)||beltslot(c)>=0||!(thing instanceof MenuGrid.Pagina))return super.dropthing(c,thing);
            int slot=receive(c);if(slot<0)return false;
            MenuGrid.Pagina pag=(MenuGrid.Pagina)thing;
            try{
                if(LocalActionBelt.bind(gui,slot,pag))return true;
                LocalActionBelt.forget(gui,slot);
                if(pag.id instanceof Indir)gui.wdgmsg("setbelt",slot,"res",pag.res().name);
                else gui.wdgmsg("setbelt",slot,"pag",pag.id);
            }catch(Loading ignored){}
            return true;
        }
        public boolean mouseup(MouseUpEvent ev){
            if(drag==null||ev.b!=1)return super.mouseup(ev);
            drag.remove();drag=null;MagneticWindows.end(this);Utils.setprefb(movedKey(),true);Utils.setprefc(positionKey(),saved=c);return true;
        }
        public void draw(GOut g){
            GOut content=ToolbarScale.graphics(g,logicalSize);
            panel(content,logicalSize);
            Coord slot=Inventory.invsq.sz();
            for(int i=0;i<slots.length;i++){
                ToolbarFrame.hotbarSlot(content,at[i],slot);
                try{if(gui.belt!=null&&slots[i]>=0&&slots[i]<gui.belt.length&&gui.belt[slots[i]]!=null)
                    gui.belt[slots[i]].draw(content.reclip(at[i].add(UI.scale(1,1)),slot.sub(UI.scale(2,2))));}catch(Loading ignored){}
                if("function".equals(id)){
                    if(labels[i]==null)labels[i]=ToolbarFrame.keyLabel("F"+(i+1));
                    content.aimage(labels[i],ToolbarFrame.keyAnchor(at[i],slot),1,1);
                }else if(extra(id)&&i<keyLabels.length){
                    String text=hearthborn.input.PanelKeys.label(id,i);
                    if(!java.util.Objects.equals(text,keyTexts[i])){
                        if(keyLabels[i]!=null)keyLabels[i].dispose();
                        keyLabels[i]=text==null?null:ToolbarFrame.keyLabel(text);keyTexts[i]=text;
                    }
                    if(keyLabels[i]!=null)content.aimage(keyLabels[i],ToolbarFrame.keyAnchor(at[i],slot),1,1);
                }
            }
        }
        public Object tooltip(Coord c,Widget prev){
            int slot=beltslot(c);
            if(slot<0&&extra(id)&&(slots.length==0||(dropFrom>0&&ToolbarScale.logical(c).x>=dropFrom)))
                return hearthborn.L10n.tr("Drop an icon here to add it.");
            if(slot<0||(ui!=null&&ui.modmeta))return ToolbarFrame.moveTip();
            return null;
        }
        public void dispose(){if(drag!=null)drag.remove();for(Tex label:labels)if(label!=null)label.dispose();for(Tex label:keyLabels)if(label!=null)label.dispose();super.dispose();}
    }
    /** The backing plate of a separate strip; clean strips let the world show between their cells. */
    static void panel(GOut g,Coord size){if(!clean())ToolbarFrame.background(g,size);}
    private Toolbars() {}
}
