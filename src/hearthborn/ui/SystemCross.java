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
 * The same system controls in a single strip, horizontal or vertical, or around the action grid.
 *
 * @author Jagy2014
 */
public final class SystemCross extends Widget {
    public static final int SEARCH = 0, PERSONAL = 1, VILLAGE = 2, PROVINCES = 3, SETTINGS = 4, INVENTORY = 5, KIN = 6, EQUIPMENT = 7, CHARACTER = 8;
    /** One strip. Kith & Kin is the heart on the portrait, equipment and the character sheet are clicks on the portrait itself, and search is the last item of the sidebar menu and a cell of the 4×4. */
    private static final int[] STRIP = {INVENTORY, PERSONAL, VILLAGE, PROVINCES, SETTINGS};
    /** The one row above the 4×4 grid; its width is the grid's width. */
    private static final int[] TOP = {INVENTORY, PERSONAL, VILLAGE, PROVINCES, SETTINGS};
    /** Not in the row above the 4×4 grid: search is the last grid cell, equipment and the character sheet open from the portrait, and Kith & Kin is the heart on it. */
    private static final int[] MENU_ONLY = {SEARCH, EQUIPMENT, CHARACTER, KIN};
    public enum Layout {
        ROW("horizontal","Strip (horizontal)"),COLUMN("vertical","Strip (vertical)"),EDGES("edges","Around 4×4");
        private final String key;
        public final String label;
        Layout(String key,String label){this.key=key;this.label=label;}
    }
    private static final String LAYOUT="hearthborn/ui/system-menu-layout";
    /** What the sidebar menu last used, kept while the classic menu, which always has the buttons around its grid, is chosen. */
    private static final String SIDEBAR_LAYOUT="hearthborn/ui/system-menu-layout-sidebar";
    private static volatile Layout arrangement=loadLayout();
    private static Layout named(String saved,Layout fallback){
        for(Layout layout:Layout.values())if(layout.key.equals(saved))return layout;
        return fallback;
    }
    /** The sidebar menu has no grid to go around, so it offers the two strips. */
    public static java.util.List<Layout> sidebarLayouts(){return java.util.Arrays.asList(Layout.ROW,Layout.COLUMN);}
    public static boolean strip(Layout layout){return layout==Layout.ROW||layout==Layout.COLUMN;}
    private static Layout sidebarLayout(){
        Layout saved=named(migrated(Utils.getpref(SIDEBAR_LAYOUT,"horizontal")),Layout.ROW);
        return saved==Layout.EDGES?Layout.ROW:saved;
    }
    /** The radial menu is gone; "grid" was the compact 3×3 before the strip replaced it. Both became the horizontal strip. */
    private static String migrated(String saved){return saved.equals("radial")||saved.equals("grid")?"horizontal":saved;}
    private static Layout loadLayout(){
        Layout layout=named(migrated(Utils.getpref(LAYOUT,"horizontal")),Layout.ROW);
        // The classic menu always has the buttons around its grid; the choice made earlier is kept for the sidebar menu.
        if(!Toolbars.enabled("actions")){
            if(layout!=Layout.EDGES&&Utils.getpref(SIDEBAR_LAYOUT,null)==null)Utils.setpref(SIDEBAR_LAYOUT,layout.key);
            layout=Layout.EDGES;
        }else if(layout==Layout.EDGES)layout=sidebarLayout();
        Utils.setpref(LAYOUT,layout.key);
        return layout;
    }
    public static Layout arrangement(){return arrangement;}
    public static void arrangement(Layout layout){arrangement=java.util.Objects.requireNonNull(layout);Utils.setpref(LAYOUT,layout.key);}
    /** Called when the menu kind changes: classic goes around its grid, the sidebar returns to the layout it had. */
    public static void menuChosen(boolean sidebar){
        if(sidebar)arrangement(sidebarLayout());
        else{
            if(arrangement()!=Layout.EDGES)Utils.setpref(SIDEBAR_LAYOUT,arrangement().key);
            arrangement(Layout.EDGES);
        }
    }
    public static boolean gridEdges(){return arrangement()==Layout.EDGES&&!Toolbars.enabled("actions");}
    public static Coord edgeInset(){return edgeInset(ActionMenuFrame.grid());}
    public static Coord edgeInset(double factor){
        if(!gridEdges())return Coord.z;
        Coord padding=UI.scale(3,3);
        int side=edgeSide(ActionMenuFrame.size(MenuGrid.bgsz.mul(4,1),factor).x);
        return Coord.of(0,side+padding.y*2-ActionMenuFrame.pad(factor).y).max(Coord.of(0,1));
    }
    /** Side of a button in the top row when {@code width} pixels hold all of them. */
    static int edgeSide(int width){return Math.max(1,Math.min(iconSize(),width/TOP.length-UI.scale(2)));}
    /** The top row scales with the grid, so no width is needed beyond a pixel for each button. */
    public static Coord edgeMinimum(){return Coord.of(TOP.length*(1+UI.scale(2)),1);}
    private static Layout desiredLayout(){return arrangement()==Layout.EDGES&&!gridEdges()?sidebarLayout():arrangement();}
    private static final String POSITION = "hearthborn/ui/system-cross";
    private static final String MOVED = "hearthborn/ui/system-cross-moved";
    private final Widget[] arms = new Widget[9];
    private UI.Grab drag;
    private Coord offset = Coord.z, origin = Coord.z;
    private boolean docked;
    private boolean attachedEdges;
    private Layout shownLayout;

    /** The strip's spacing is a little tighter than the nominal one. */
    private static final double SCALE = 0.95;
    /** Close to the classic grid's cell step, with a small gap between disks. */
    public static int iconSize() { return Math.max(1,ActionMenuFrame.size(MenuGrid.bgsz,ActionMenuFrame.grid()).x-UI.scale(6)); }
    /** A strip keeps this size whatever the panel scale: the size of the 4×4 cell at 1.0. */
    public static int stripIcon() { return Math.max(1,ActionMenuFrame.size(MenuGrid.bgsz,ActionMenuFrame.GRID).x-UI.scale(6)); }
    /** The pictures are drawn once at the largest panel scale and shrunk to {@link #iconSize()}, so the panel scale can grow them too. */
    public static int sourceSize() { return Math.max(1,ActionMenuFrame.size(MenuGrid.bgsz,ActionMenuFrame.GRID*HudInterfaceScale.MAX).x-UI.scale(6)); }
    private double shownPanel=HudInterfaceScale.current();

    public SystemCross(Widget search, Widget personal, Widget village, Widget provinces, Widget settings, Widget inventory, Widget kin, Widget equipment, Widget character) {
        super(Coord.z);
        arms[SEARCH] = search;
        arms[PERSONAL] = personal;
        arms[VILLAGE] = village;
        arms[PROVINCES] = provinces;
        arms[SETTINGS] = settings;
        arms[INVENTORY] = inventory;
        arms[KIN] = kin;
        arms[EQUIPMENT] = equipment;
        arms[CHARACTER] = character;
        for(Widget arm : arms) {
            if(arm == null) throw new IllegalArgumentException("The system menu needs every button");
            add(arm);
        }
        relayout();
    }

    public Widget arm(int index) { return arms[index]; }

    public void relayout() {
        if(drag!=null)return;
        Layout desired=desiredLayout();
        shownLayout=desired;
        if(shownLayout==Layout.EDGES){
            GameUI gui=getparent(GameUI.class);
            ActionMenuFrame frame=gui==null||gui.menu==null?null:gui.menu.parent.getchild(ActionMenuFrame.class);
            if(frame!=null){attachEdges(frame);return;}
        }
        for(Widget arm:arms)if(arm instanceof ICheckBox)((ICheckBox)arm).fit(strip(shownLayout)?stripIcon():iconSize());
        if(attachedEdges){
            attachedEdges=false;docked=false;
            if(moved())move(Utils.getprefc(POSITION,c));
        }
        z(0);
        layoutStrip(shownLayout==Layout.COLUMN);
        buttonVisibility();
    }
    private static boolean inStrip(int arm){for(int i:STRIP)if(i==arm)return true;return false;}
    private static boolean menuOnly(int arm){for(int i:MENU_ONLY)if(i==arm)return true;return false;}
    private void buttonVisibility(){
        for(int i=0;i<arms.length;i++){
            boolean show=!(shownLayout==Layout.EDGES&&menuOnly(i))&&!(strip(shownLayout)&&!inStrip(i));
            arms[i].show(show);
            if(!show&&arms[i] instanceof ICheckBox)((ICheckBox)arms[i]).h=false;
        }
    }
    /** A transparent center lets the existing MenuGrid receive its own pointer events. */
    public void attachEdges(ActionMenuFrame frame){
        if(!gridEdges())return;
        GameUI gui=getparent(GameUI.class);if(gui==null)return;
        shownLayout=Layout.EDGES;docked=true;attachedEdges=true;
        buttonVisibility();
        resize(frame.sz);move(frame.parentpos(gui));z(frame.parent.z);show();
        Coord margin=ActionMenuFrame.pad(frame.factor()),edge=edgeInset(frame.factor());
        int railHeight=margin.y+edge.y,bodyX=margin.x,bodyWidth=sz.x-margin.x*2;
        // The row is as wide as the first four columns, also when Xtensions grows sideways.
        int topWidth=Math.min(bodyWidth,ActionMenuFrame.size(MenuGrid.bgsz.mul(4,1),frame.factor()).x);
        int side=edgeSide(topWidth);
        for(int i=0;i<TOP.length;i++){
            if(arms[TOP[i]] instanceof ICheckBox)((ICheckBox)arms[TOP[i]]).fit(side);
            placeCenter(arms[TOP[i]],bodyX+(2*i+1)*topWidth/(2*TOP.length),railHeight/2);
        }
    }
    private void layoutStrip(boolean vertical){
        int pad=UI.scale(4),spacing=Math.max(1,(int)Math.round(UI.scale(5)*SCALE));
        Coord cell=Coord.z;
        for(Widget arm:arms)cell=cell.max(arm.sz);
        for(int i=0;i<STRIP.length;i++){
            int x=vertical?0:i,y=vertical?i:0;
            placeCenter(arms[STRIP[i]],pad+x*(cell.x+spacing)+cell.x/2,pad+y*(cell.y+spacing)+cell.y/2);
        }
        int along=STRIP.length;
        resize(vertical?cell.x+pad*2:along*cell.x+(along-1)*spacing+pad*2,vertical?along*cell.y+(along-1)*spacing+pad*2:cell.y+pad*2);
    }

    public void draw(GOut g){
        // A strip has no backing and no frame of its own.
        super.draw(g);
    }

    private static void placeCenter(Widget arm, int x, int y) {
        arm.move(Coord.of(x - arm.sz.x / 2, y - arm.sz.y / 2));
    }

    private boolean overButton(Coord point) {
        for(Widget arm:arms)if(hits(arm,point))return true;
        return false;
    }
    private static boolean hits(Widget arm,Coord point){
        Coord local=point.sub(arm.c);
        return arm.visible()&&local.isect(Coord.z,arm.sz)
            &&(!(arm instanceof ICheckBox)||((ICheckBox)arm).checkhit(local));
    }

    /** Sidebar menu uses the action-menu corner. Classic mode uses the opposite bottom corner. */
    public void dock(Coord at) { docked = true; show(); move(at); raise(); }
    public boolean moved() { return !gridEdges()&&Utils.getprefb(MOVED, false); }
    public boolean dragging() { return drag != null; }
    public void undock() {
        if(!docked) return;
        docked = false;
        show();
        move(Utils.getprefc(POSITION, UI.scale(180, 90)));
    }

    public void tick(double dt) {
        if((shownLayout!=desiredLayout()||shownPanel!=HudInterfaceScale.current())&&drag==null){shownPanel=HudInterfaceScale.current();relayout();}
        GameUI gui = getparent(GameUI.class);
        if(gui != null) gui.syncActionMenu();
        if(parent != null && drag == null && !docked) {
            Coord limit = parent.sz.sub(sz).max(Coord.z);
            Coord next = Coord.of(Utils.clip(c.x, 0, limit.x), Utils.clip(c.y, 0, limit.y));
            if(!next.equals(c)) {
                move(next);
                if(moved()) Utils.setprefc(POSITION, next);
            }
        }
        super.tick(dt);
    }

    public boolean mousedown(MouseDownEvent ev) {
        boolean alt = ui != null && ui.modmeta;
        if(gridEdges())return alt&&ev.b==1&&overButton(ev.c);
        if(ui != null && ev.b == 1 && ev.c.isect(Coord.z, sz) && (alt || !overButton(ev.c))) {
            origin = c;
            offset = ev.c;
            drag = ui.grabmouse(this);
            raise();
            return true;
        }
        return false;
    }

    public void mousemove(MouseMoveEvent ev) {
        if(drag != null) move(c.add(ev.c.sub(offset)));
    }

    public boolean mouseup(MouseUpEvent ev) {
        if(drag != null && ev.b == 1) {
            drag.remove();
            drag = null;
            if(!c.equals(origin)) {
                docked = false;
                if(getparent(GameUI.class) != null) {
                    Utils.setprefb(MOVED, true);
                    Utils.setprefc(POSITION, c);
                }
            }
            return true;
        }
        return false;
    }

    public Object tooltip(Coord c, Widget prev) {
        if(gridEdges())return null;
        if(!overButton(c)) return moveTip();
        return null;
    }

    public boolean tooltip(TooltipQuery ev) {
        if(ui != null && ui.modmeta && checkhit(ev.c)) {
            ev.set(gridEdges()?hearthborn.L10n.tr("These system buttons follow the classic action menu."):moveTip(), this);
            return true;
        }
        return super.tooltip(ev);
    }

    private static String moveTip() {
        return hearthborn.L10n.tr("Drag a gap, or hold Alt/Option and drag a button to move the system menu.");
    }
}
