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

package hearthborn.planner;
import hearthborn.storage.ContainerContents;
import hearthborn.timers.ObjectTimers;
import hearthborn.world.ObjectHighlight;
import hearthborn.world.ObjectNames;
import hearthborn.scripts.FieldPlans;

import haven.*;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.world.*;
import hearthborn.cheese.CheeseAging;
import hearthborn.scripts.FieldIcon;
import hearthborn.ui.UiTheme;
import java.awt.event.KeyEvent;
import java.awt.Color;
import java.util.*;

/**
 * Uses server claim overlays and loaded objects; it never requests undiscovered terrain.
 *
 * @author Jagy2014
 */
public final class ClaimPlan extends Window {
    private static final Map<GameUI,ClaimPlan> open=new WeakHashMap<>();
    public static java.awt.image.BufferedImage menuIcon(){
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();g.setColor(new Color(20,26,18));g.fillRect(0,0,32,32);
        g.setColor(new Color(191,153,78));g.drawRect(1,1,29,29);g.drawRect(5,5,21,21);
        g.setColor(new Color(72,91,55));g.fillRect(6,6,20,20);g.setColor(new Color(223,199,138));g.fillRect(10,13,11,10);
        g.setColor(new Color(142,87,47));g.fillPolygon(new int[]{8,15,23},new int[]{14,7,14},3);
        g.setColor(new Color(43,32,20));g.fillRect(14,17,4,6);g.dispose();return image;
    }
    private final GameUI gui;
    private ClaimSurvey.Plan selectedPlan;
    private Window frontDialog;
    private void showDialog(Window dialog){gui.add(dialog,c.add(UI.scale(30,60)));frontDialog=dialog;dialog.raise();gui.setfocus(dialog);}
    public boolean mousedown(MouseDownEvent event){boolean handled=super.mousedown(event);if(frontDialog!=null&&frontDialog.parent!=null){frontDialog.raise();gui.setfocus(frontDialog);}return handled;}
    private final Canvas canvas;
    private String tag=Utils.getpref("hearthborn/claim-plan/tab","cplot");
    private boolean addingArea;
    private boolean listShown=Utils.getprefb("hearthborn/claim-plan/list",false),measuring;
    private final Label status,hint,measurement,searchHint;
    private final ObjectList objectList;
    private final TextEntry objectSearch;
    private String objectFilter="";
    private final PlanTabs tabs;
    private final SDropBox<String,Widget> planBox;
    private final FieldIcon refreshIcon,renameIcon,deleteIcon,centerIcon,measureIcon,exportIcon,panelIcon,helpIcon,searchIcon;
    private final java.util.List<PlanChip> chips=new ArrayList<>();
    private final Set<String> hiddenLayers=prefSet("hearthborn/claim-plan/layers-off"),hiddenAreas=prefSet("hearthborn/claim-plan/areas-off"),closedGroups=prefSet("hearthborn/claim-plan/groups-closed");
    private final Set<String> qualityFirst=new HashSet<>(Collections.singleton("ovens"));
    private String renaming;
    private int listVersion;
    static final String[] LAYERS={"fields","trees","ovens","houses","areas"};
    private static final Color[] LAYER_COLORS={new Color(137,200,90),new Color(77,152,83),new Color(222,146,66),new Color(160,134,98),new Color(105,187,240)};
    private static Set<String> prefSet(String name){
        Set<String> result=new HashSet<>();
        for(String part:Utils.getpref(name,"").split(","))if(!part.isEmpty())result.add(part);
        return result;
    }
    private static void savePrefSet(String name,Set<String> values){Utils.setpref(name,String.join(",",new TreeSet<>(values)));}
    /** The layer an object belongs to, or null when it is always shown. */
    static String layer(String type){
        if(type==null)return null;
        switch(type){
        case "Tree":case "Bush":case "Mushroom":return "trees";
        case "Building":return "houses";
        case "Area":return "areas";
        case "Oven":case "Kiln":case "Ore Smelter":case "Tar Kiln":case "Stack Furnace":case "Finery Forge":case "Crucible":case "Steel Crucible":return "ovens";
        default:return null;
        }
    }
    /** Where an object sits in the list on the right. */
    static String group(String type){
        String layer=layer(type);
        return "houses".equals(layer)||"ovens".equals(layer)||"areas".equals(layer)?layer:"other";
    }
    static Color qualityColor(double quality){
        return quality>=100?new Color(110,205,120):quality>=60?new Color(232,192,98):new Color(225,95,85);
    }
    boolean layerShown(String layer){return layer==null||!hiddenLayers.contains(layer);}
    private void layerShown(String layer,boolean value){
        if(value)hiddenLayers.remove(layer);else hiddenLayers.add(layer);
        savePrefSet("hearthborn/claim-plan/layers-off",hiddenLayers);
    }
    private boolean hidden(Entry entry){
        if(entry.area!=null&&hiddenAreas.contains(entry.area.id))return true;
        return !layerShown(layer(entry.type));
    }
    private static String layerTitle(String layer){return L10n.text("button",Character.toUpperCase(layer.charAt(0))+layer.substring(1));}
    public static void toggle(GameUI gui){
        if(gui==null)return;ClaimPlan window=open.get(gui);
        if(window!=null&&window.parent!=null){window.reqdestroy();return;}
        window=new ClaimPlan(gui);open.put(gui,window);gui.add(window,UI.scale(80,60));
    }
    public ClaimPlan(GameUI gui){
        super(UI.scale(920,580),"Claim plan");this.gui=gui;reqclose(this::reqdestroy);
        if(!tag.equals("cplot")&&!tag.equals("vlg"))tag="cplot";
        tabs=add(new PlanTabs(new String[]{"cplot","vlg"},new String[]{L10n.text("button","Personal"),L10n.text("button","Village")},()->tag,this::select),Coord.z);
        planBox=add(new SDropBox<String,Widget>(UI.scale(190),UI.scale(200),UI.scale(25)){
            private String title;
            protected java.util.List<String> items(){return availableLevels();}
            protected Widget makeitem(String id,int index,Coord size){
                String name=id.isEmpty()?L10n.tr("Current location"):planName(id);
                return new Label(Text.std.ellipsize(name,Math.max(1,size.x-UI.scale(8))).text){public Object tooltip(Coord at,Widget previous){return name;}};
            }
            public void change(String id){super.change(id);if(canvas!=null)openPlan(id);}
            public void tick(double dt){
                super.tick(dt);String id=selectedPlan==null?"":selectedPlan.id;String name=id.isEmpty()?L10n.tr("Current location"):planName(id);
                if(!Objects.equals(sel,id)||!Objects.equals(title,name)){sel=null;super.change(id);title=name;}
            }
        },Coord.z);
        refreshIcon=add(new FieldIcon(FieldIcon.Glyph.REFRESH,"Update plan",this::savePlan),Coord.z);
        renameIcon=add(new FieldIcon(FieldIcon.Glyph.EDIT,"Rename plan",()->editPlan(false)),Coord.z);
        deleteIcon=add(new FieldIcon(FieldIcon.Glyph.TRASH,"Delete plan",()->editPlan(true)),Coord.z);
        for(int i=0;i<LAYERS.length;i++){
            String layer=LAYERS[i];
            chips.add(add(new PlanChip(layerTitle(layer),LAYER_COLORS[i],()->layerShown(layer),value->layerShown(layer,value)),Coord.z));
        }
        canvas=add(new Canvas(),Coord.z);
        centerIcon=add(new FieldIcon(FieldIcon.Glyph.LOCATE,"Center",()->{canvas.pan=Coord.z;}),Coord.z);
        measureIcon=add(new FieldIcon(FieldIcon.Glyph.RULER,"Measure tiles",()->L10n.tr(measuring?"Stop measuring":"Measure tiles"),this::toggleMeasure),Coord.z);
        exportIcon=add(new FieldIcon(FieldIcon.Glyph.DOWNLOAD,"Export…",this::chooseExport),Coord.z);
        panelIcon=add(new FieldIcon(FieldIcon.Glyph.PANEL,"Object list",()->{
            listShown=!listShown;Utils.setprefb("hearthborn/claim-plan/list",listShown);layout();
        }),Coord.z);
        measurement=add(new Label(""),Coord.z);
        status=add(new Label(""),Coord.z);status.hide();
        hint=add(new Label(""),Coord.z);hint.hide();
        helpIcon=add(new FieldIcon(FieldIcon.Glyph.HELP,"Help",()->L10n.tr("North ↑ · cell = tile · fields: green/brown · paving: gray · soil: ochre")+"\n"+status.text.text,()->{}),Coord.z);
        searchIcon=add(new FieldIcon(FieldIcon.Glyph.SEARCH),Coord.z);
        objectList=add(new ObjectList(),Coord.z);
        objectSearch=add(new TextEntry(UI.scale(250-FieldIcon.SIDE-4),""){
            protected void changed(){super.changed();objectFilter=text().trim().toLowerCase(Locale.ROOT);listVersion++;searchHint.show(text().isEmpty()&&listShown);}
        },Coord.z);objectSearch.settip(L10n.tr("Search objects by name"));
        searchHint=add(new Label(L10n.tr("Search objects…")),Coord.z);searchHint.setcolor(new Color(140,134,116));
        if(deco instanceof Window.DefaultDeco)((Window.DefaultDeco)deco).dragsize(true);
        resize(Utils.getprefc("hearthborn/claim-plan/size",UI.scale(920,580)));
    }
    public void resize(Coord size){super.resize(size.max(UI.scale(640,380)));if(canvas!=null&&searchHint!=null&&hint!=null)layout();}
    static final int SIDE_PANEL=250;
    private void layout(){
        Coord size=csz();
        int margin=UI.scale(8),gap=UI.scale(6),icon=UI.scale(FieldIcon.SIDE),panel=listShown?UI.scale(SIDE_PANEL):0,panelGap=listShown?margin:0;
        int top=UI.scale(6),box=planBox.sz.y,iconY=top+(box-icon)/2;
        tabs.move(Coord.of(margin,top+(box-tabs.sz.y)/2));
        planBox.move(Coord.of(tabs.c.x+tabs.sz.x+gap*2,top));
        refreshIcon.move(Coord.of(planBox.c.x+planBox.sz.x+gap,iconY));renameIcon.move(Coord.of(refreshIcon.c.x+icon+gap,iconY));
        deleteIcon.move(Coord.of(size.x-margin-icon,iconY));
        int chipsY=top+box+gap,chipX=margin,chipHeight=chips.isEmpty()?0:chips.get(0).sz.y;
        for(PlanChip chip:chips){chip.move(Coord.of(chipX,chipsY));chipX+=chip.sz.x+gap;}
        int canvasY=chipsY+chipHeight+gap;
        canvas.move(Coord.of(margin,canvasY));
        canvas.resize(Coord.of(Math.max(1,size.x-margin*2-panel-panelGap),Math.max(1,size.y-canvasY-margin)));
        int toolX=canvas.c.x+canvas.sz.x-icon-gap,toolY=canvas.c.y+UI.scale(48),step=icon+UI.scale(5);
        FieldIcon[] tools={centerIcon,measureIcon,exportIcon,panelIcon,helpIcon};
        for(int i=0;i<tools.length;i++)tools[i].move(Coord.of(toolX,toolY+i*step));
        int panelX=canvas.c.x+canvas.sz.x+panelGap,searchHeight=objectSearch.sz.y,searchY=canvasY;
        searchIcon.move(Coord.of(panelX,searchY+(searchHeight-icon)/2));searchIcon.show(listShown);
        objectSearch.move(Coord.of(panelX+icon+UI.scale(4),searchY));objectSearch.show(listShown);
        searchHint.move(objectSearch.c.add(UI.scale(6),(searchHeight-searchHint.sz.y)/2));searchHint.show(listShown&&objectSearch.text().isEmpty());
        int listY=searchY+searchHeight+gap;
        objectList.move(Coord.of(panelX,listY));objectList.resize(Coord.of(Math.max(1,panel),Math.max(1,canvas.c.y+canvas.sz.y-listY)));objectList.show(listShown);
        panelIcon.on(listShown);
    }
    public void dispose(){Utils.setprefc("hearthborn/claim-plan/size",csz());open.remove(gui,this);super.dispose();}
    private void toggleMeasure(){measuring=!measuring;measureIcon.on(measuring);if(!measuring){canvas.measureStart=null;canvas.measureEnd=null;measurement.settext("");}}
    private java.util.List<String> availableLevels(){
        java.util.List<String> result=new ArrayList<>();result.add("");for(ClaimSurvey.Plan plan:ClaimSurvey.plans(tag))result.add(plan.id);return result;
    }
    private String planName(String id){for(ClaimSurvey.Plan plan:ClaimSurvey.plans())if(plan.id.equals(id))return plan.name;return id;}
    private void openPlan(String id){
        for(Tex icon:canvas.savedIcons.values())icon.dispose();canvas.savedIcons.clear();
        selectedPlan=null;
        for(ClaimSurvey.Plan plan:ClaimSurvey.plans())if(plan.id.equals(id)){selectedPlan=plan;break;}
        canvas.viewRegion=null;canvas.focusTile=null;
        if(selectedPlan==null){tag=Utils.getpref("hearthborn/claim-plan/tab","cplot");canvas.pan=Coord.z;canvas.zoom=1;}
        if(selectedPlan!=null){
            ClaimSurvey.GridRecord grid=null;for(ClaimSurvey.GridRecord record:planRecords())if(record.id==selectedPlan.grid){grid=record;break;}
            if(grid==null){gui.ui.msg(L10n.tr("No plan to export."));selectedPlan=null;return;}
            tag=selectedPlan.tag;canvas.viewRegion=grid.region;canvas.focusTile=Coord.of(grid.x+selectedPlan.x,grid.y+selectedPlan.y);
        }
        canvas.next=0;canvas.refresh();
    }
    private void savePlan(){
        ClaimSurvey.scan(gui);canvas.refresh();
        if(selectedPlan!=null&&selectedPlan.snapshot!=null){gui.ui.msg(L10n.format("Plan saved: %s",selectedPlan.name));return;}
        if(canvas.territory.isEmpty()){gui.ui.msg(L10n.tr("No plan to export."));return;}
        Coord anchor=canvas.territory.iterator().next();ClaimSurvey.GridRecord grid=null;
        for(ClaimSurvey.GridRecord record:ClaimSurvey.records(canvas.region))if(anchor.isect(Coord.of(record.x,record.y),MCache.cmaps)){grid=record;break;}
        if(grid==null)return;
        java.util.List<ClaimSurvey.Plan> plans=ClaimSurvey.plans();ClaimSurvey.Plan plan=selectedPlan;
        if(plan==null){
            plan=new ClaimSurvey.Plan();plan.id=UUID.randomUUID().toString();
            plan.name=ClaimSurvey.defaultPlanName(tag,plans.size()+1);plans.add(plan);
        }else replace(plans,plan);
        plan.tag=tag;plan.grid=grid.id;plan.x=anchor.x-grid.x;plan.y=anchor.y-grid.y;
        ClaimSurvey.savePlans(plans);selectedPlan=plan;
        gui.ui.msg(L10n.format("Plan saved: %s",plan.name));
    }
    private void editPlan(boolean delete){
        if(selectedPlan==null){gui.ui.msg(L10n.tr("Select a saved plan first."));return;}
        final ClaimSurvey.Plan chosen=selectedPlan;
        Window dialog=new Window(UI.scale(300,100),L10n.tr(delete?"Delete saved plan?":"Plan name"));dialog.reqclose(dialog::reqdestroy);
        final TextEntry name=delete?null:dialog.add(new TextEntry(UI.scale(280),chosen.name),UI.scale(8,8));
        if(delete)dialog.add(new Label(Text.std.ellipsize(chosen.name,UI.scale(280)).text),UI.scale(8,12));
        dialog.add(new Button(UI.scale(130),L10n.tr(delete?"Delete":"Save"),false,()->{
            java.util.List<ClaimSurvey.Plan> plans=ClaimSurvey.plans();
            if(delete)plans.removeIf(plan->plan.id.equals(chosen.id));
            else for(ClaimSurvey.Plan plan:plans)if(plan.id.equals(chosen.id)){plan.name=name.text().trim().isEmpty()?chosen.name:name.text().trim();}
            ClaimSurvey.savePlans(plans);dialog.reqdestroy();openPlan(delete?"":chosen.id);
        }),UI.scale(8,55));
        dialog.add(new Button(UI.scale(130),L10n.tr("Cancel"),false,dialog::reqdestroy),UI.scale(152,55));showDialog(dialog);
    }
    private void chooseExport(){
        Window dialog=new Window(UI.scale(230,85),L10n.tr("Export"));dialog.reqclose(dialog::reqdestroy);
        dialog.add(new Button(UI.scale(100),"Excel",false,()->{dialog.reqdestroy();export(true);}),UI.scale(8,8));
        dialog.add(new Button(UI.scale(100),"HTML",false,()->{dialog.reqdestroy();export(false);}),UI.scale(120,8));
        dialog.add(new Button(UI.scale(100),L10n.tr("Plan file"),false,()->{dialog.reqdestroy();sharePlan();}),UI.scale(8,45));
        dialog.add(new Button(UI.scale(100),L10n.tr("Import…"),false,()->{dialog.reqdestroy();importPlan();}),UI.scale(120,45));
        showDialog(dialog);
    }
    /** Saving a plan keeps its place in the list: the claim view picks among plans by order. */
    static void replace(java.util.List<ClaimSurvey.Plan> plans,ClaimSurvey.Plan plan){
        for(int i=0;i<plans.size();i++)if(plans.get(i).id.equals(plan.id)){plans.set(i,plan);return;}
        plans.add(plan);
    }
    /** Of several saved plans for the claim under the player: the one already shown, else one with areas, else the first. */
    static ClaimSurvey.Plan choose(java.util.List<ClaimSurvey.Plan> matches,String shown){
        ClaimSurvey.Plan chosen=null;
        for(ClaimSurvey.Plan plan:matches){
            if(plan.id.equals(shown))return plan;
            if(chosen==null||(chosen.areas==null||chosen.areas.isEmpty())&&plan.areas!=null&&!plan.areas.isEmpty())chosen=plan;
        }
        return chosen;
    }
    private void beginArea(){
        if(canvas.territory.isEmpty())return;
        if(selectedPlan==null)savePlan();if(selectedPlan==null)return;
        addingArea=true;if(!measuring)toggleMeasure();gui.ui.msg(L10n.tr("Drag a rectangle on the plan to name an area."));
    }
    private Collection<ClaimSurvey.GridRecord> planRecords(){return selectedPlan!=null&&selectedPlan.snapshot!=null?selectedPlan.snapshot:ClaimSurvey.allRecords();}
    private ClaimSurvey.GridRecord anchorGrid(){if(selectedPlan!=null)for(ClaimSurvey.GridRecord grid:planRecords())if(grid.id==selectedPlan.grid)return grid;return null;}
    private void areaName(ClaimSurvey.NamedArea area,boolean create){
        final ClaimSurvey.Plan owner=selectedPlan;
        Window dialog=new Window(UI.scale(290,90),L10n.tr("Area name"));dialog.reqclose(dialog::reqdestroy);
        TextEntry name=dialog.add(new TextEntry(UI.scale(270),area.name==null?"":area.name),UI.scale(8,8));
        dialog.add(new Button(UI.scale(120),L10n.tr("Save"),false,()->{
            if(name.text().trim().isEmpty())return;area.name=name.text().trim();
            if(owner.areas==null)owner.areas=new ArrayList<>();if(create)owner.areas.add(area);
            java.util.List<ClaimSurvey.Plan> plans=ClaimSurvey.plans();replace(plans,owner);ClaimSurvey.savePlans(plans);
            dialog.reqdestroy();canvas.next=0;canvas.refresh();
        }),UI.scale(8,48));
        dialog.add(new Button(UI.scale(120),L10n.tr("Cancel"),false,dialog::reqdestroy),UI.scale(150,48));showDialog(dialog);dialog.setfocus(name);gui.setfocus(dialog);
    }
    private void deleteArea(ClaimSurvey.NamedArea area){
        if(selectedPlan==null)return;final String planId=selectedPlan.id;
        Window dialog=new Window(UI.scale(300,95),L10n.tr("Delete area?"));dialog.reqclose(dialog::reqdestroy);
        dialog.add(new Label(Text.std.ellipsize(area.name,UI.scale(280)).text),UI.scale(8,10));
        dialog.add(new Button(UI.scale(130),L10n.tr("Delete"),false,()->{
            ClaimSurvey.removeArea(planId,area.id);dialog.reqdestroy();canvas.selectedKey=null;canvas.next=0;canvas.refresh();
        }),UI.scale(8,50));
        dialog.add(new Button(UI.scale(130),L10n.tr("Cancel"),false,dialog::reqdestroy),UI.scale(152,50));showDialog(dialog);
    }
    private void sharePlan(){
        if(selectedPlan==null)savePlan();if(selectedPlan==null)return;
        ClaimSurvey.Plan copy=new ClaimSurvey.Plan();copy.name=selectedPlan.name;copy.tag=tag;copy.grid=selectedPlan.grid;copy.x=selectedPlan.x;copy.y=selectedPlan.y;copy.areas=selectedPlan.areas;copy.snapshot=new ArrayList<>();
        for(ClaimSurvey.GridRecord source:planRecords())if(Objects.equals(source.region,canvas.region)){
            ClaimSurvey.GridRecord grid=new ClaimSurvey.GridRecord();grid.id=source.id;grid.x=source.x;grid.y=source.y;grid.region=source.region;grid.terrain=new byte[10000];
            BitSet mask=tag.equals("vlg")?grid.village:grid.personal;
            for(int cell=0;cell<10000;cell++)if(canvas.territory.contains(Coord.of(grid.x+cell%100,grid.y+cell/100))){mask.set(cell);if(source.terrain!=null)grid.terrain[cell]=source.terrain[cell];}
            if(mask.isEmpty())continue;
            for(ClaimSurvey.ObjectRecord object:source.objects.values())if(mask.get((int)(object.x/MCache.tilesz.x)+(int)(object.y/MCache.tilesz.y)*100))grid.objects.put(object.key(),object);
            copy.snapshot.add(grid);
        }
        try{gui.ui.msg(L10n.format("Plan saved: %s",PlanShare.write(copy)));}catch(java.io.IOException e){gui.ui.msg(L10n.format("Could not save plan: %s",e.getMessage()));}
    }
    private void importPlan(){
        Window dialog=new Window(UI.scale(410,95),L10n.tr("Import plan"));dialog.reqclose(dialog::reqdestroy);
        dialog.add(new Label(L10n.tr("Path to .hbplan file")),UI.scale(8,5));TextEntry path=dialog.add(new TextEntry(UI.scale(390),""),UI.scale(8,28));
        dialog.add(new Button(UI.scale(130),L10n.tr("Import"),false,()->{
            try{
                ClaimSurvey.Plan plan=PlanShare.read(java.nio.file.Paths.get(path.text().trim()));
                java.util.List<ClaimSurvey.Plan> plans=ClaimSurvey.plans();plans.add(plan);ClaimSurvey.savePlans(plans);openPlan(plan.id);dialog.reqdestroy();
            }catch(java.io.IOException|RuntimeException e){gui.ui.msg(L10n.format("Could not import plan: %s",e.getMessage()));}
        }),UI.scale(8,62));showDialog(dialog);dialog.setfocus(path);gui.setfocus(dialog);
    }
    private void canvasReset(){if(canvas!=null){canvas.zoom=1;canvas.pan=Coord.z;}}
    private void select(String next){selectedPlan=null;canvas.viewRegion=null;canvas.focusTile=null;tag=next;Utils.setpref("hearthborn/claim-plan/tab",next);canvasReset();canvas.next=0;}
    private void export(boolean excel){
        java.util.List<PlanExport.ObjectCell> objects=new ArrayList<>();
        if(canvas.min==null||canvas.territory.isEmpty()){gui.ui.msg(L10n.tr("No plan to export."));return;}
        for(Entry entry:canvas.entries){double[] b=entry.footprint;objects.add(new PlanExport.ObjectCell(entry.displayName(),L10n.text("window",entry.type),(entry.at.x+b[0])/MCache.tilesz.x-canvas.min.x,(entry.at.y+b[1])/MCache.tilesz.y-canvas.min.y,(b[2]-b[0])/MCache.tilesz.x,(b[3]-b[1])/MCache.tilesz.y));}
        try{java.nio.file.Path path=PlanExport.write(L10n.tr(tag.equals("cplot")?"Personal claim":"Village claim"),canvas.territory,canvas.min,canvas.max,objects,canvas.terrain,excel);gui.ui.msg(L10n.format("Plan saved: %s",path));}
        catch(java.io.IOException|RuntimeException error){gui.ui.msg(L10n.format("Could not save plan: %s",error.getMessage()));}
    }
    private static final String[] GROUPS={"houses","ovens","areas","other"};
    private final class Row {
        final String group,id;Entry entry;int count;
        Row(String group,String id){this.group=group;this.id=id;}
        boolean header(){return id==null;}
    }
    private void startRename(Row row){if(row.entry!=null)renaming=row.id;}
    private void cancelRename(){renaming=null;}
    private void persistAreas(){
        ClaimSurvey.Plan owner=selectedPlan;if(owner==null)return;
        java.util.List<ClaimSurvey.Plan> plans=ClaimSurvey.plans();replace(plans,owner);ClaimSurvey.savePlans(plans);
    }
    private void commitRename(Row row,String text){
        Entry entry=row.entry;renaming=null;if(entry==null)return;
        try{
            if(entry.area!=null){String clean=text.trim();if(!clean.isEmpty()&&!clean.equals(entry.area.name)){entry.area.name=clean;persistAreas();}}
            else if(entry.gob!=null)ObjectNames.name(entry.gob,text);
            else if(entry.ownerKey!=null)ObjectNames.renameKnown(entry.ownerKey,text);
        }catch(Loading pending){}
        listVersion++;canvas.next=0;
    }
    private void resetName(Row row){
        Entry entry=row.entry;renaming=null;if(entry==null||entry.area!=null)return;
        try{if(entry.gob!=null)ObjectNames.name(entry.gob,"");else if(entry.ownerKey!=null)ObjectNames.renameKnown(entry.ownerKey,"");}catch(Loading pending){}
        listVersion++;canvas.next=0;
    }
    private final class ObjectList extends SListBox<Row,Widget>{
        private final Map<String,Row> known=new HashMap<>();
        private java.util.List<Row> built=Collections.emptyList();
        private int builtVersion=-1,builtCanvas=-1;
        ObjectList(){super(UI.scale(SIDE_PANEL,300),UI.scale(28));}
        private Row row(Map<String,Row> next,String group,String id){
            String key=group+"|"+id;Row row=next.get(key);if(row!=null)return row;
            row=known.get(key);if(row==null)row=new Row(group,id);next.put(key,row);return row;
        }
        protected java.util.List<? extends Row> items(){
            if(known==null||canvas==null)return Collections.emptyList();
            if(builtVersion==listVersion&&builtCanvas==canvas.version)return built;
            builtVersion=listVersion;builtCanvas=canvas.version;
            Map<String,java.util.List<Entry>> groups=new LinkedHashMap<>();for(String group:GROUPS)groups.put(group,new ArrayList<>());
            for(Entry entry:canvas.listEntries())if(listedType(entry.type)&&entry.listName().toLowerCase(Locale.ROOT).contains(objectFilter))groups.get(group(entry.type)).add(entry);
            Comparator<Entry> byName=Comparator.comparing(Entry::name).thenComparing(Entry::key);
            Map<String,Row> next=new HashMap<>();java.util.List<Row> rows=new ArrayList<>();
            for(String group:GROUPS){
                java.util.List<Entry> list=groups.get(group);
                if(list.isEmpty()&&(!group.equals("areas")||!objectFilter.isEmpty()))continue;
                list.sort(qualityFirst.contains(group)?Comparator.comparingDouble((Entry entry)->-entry.quality()).thenComparing(byName):byName);
                Row header=row(next,group,null);header.count=list.size();rows.add(header);
                if(closedGroups.contains(group)&&objectFilter.isEmpty())continue;
                for(Entry entry:list){Row item=row(next,group,entry.key());item.entry=entry;rows.add(item);}
            }
            known.clear();known.putAll(next);built=rows;
            if(renaming!=null){boolean found=false;for(Row row:rows)if(renaming.equals(row.id)){found=true;break;}if(!found)renaming=null;}
            return built;
        }
        protected Widget makeitem(Row row,int index,Coord size){return row.header()?new HeaderView(row,size):new RowView(row,size);}
        protected void drawbg(GOut g,Row item,int idx,haven.Area area){}
    }
    private static String groupTitle(String group){return layerTitle(group);}
    private final class HeaderView extends Widget {
        private final Row row;
        private String sortText="";
        HeaderView(Row row,Coord size){
            super(size);this.row=row;
            if(row.group.equals("areas")){
                int side=UI.scale(FieldIcon.SIDE);
                add(new FieldIcon(FieldIcon.Glyph.ADD,"Add area",()->L10n.tr("Add area"),ClaimPlan.this::beginArea),Coord.of(size.x-side-UI.scale(4),(size.y-side)/2));
            }
        }
        private boolean closed(){return closedGroups.contains(row.group)&&objectFilter.isEmpty();}
        private boolean sortable(){return row.group.equals("ovens");}
        private int sortLeft(){return sz.x-UI.scale(8)-Text.std.strsize(sortText).x;}
        public void draw(GOut g){
            g.chcolor(255,255,255,UiTheme.light()?30:22);g.frect(Coord.z,sz);
            Color gold=UiTheme.readable(new Color(232,192,98)),gray=UiTheme.light()?UiTheme.HEADER:new Color(160,153,131);
            g.chcolor(gold);int x=UI.scale(10),y=sz.y/2,s=UI.scale(4);
            if(closed()){g.line(Coord.of(x-s/2,y-s),Coord.of(x+s/2,y),1);g.line(Coord.of(x+s/2,y),Coord.of(x-s/2,y+s),1);}
            else{g.line(Coord.of(x-s,y-s/2),Coord.of(x,y+s/2),1);g.line(Coord.of(x,y+s/2),Coord.of(x+s,y-s/2),1);}
            String title=groupTitle(row.group);
            g.atext(title,Coord.of(UI.scale(22),y),0,.5);
            g.chcolor(gray);g.atext(Integer.toString(row.count),Coord.of(UI.scale(22)+Text.std.strsize(title).x+UI.scale(6),y),0,.5);
            if(sortable()){
                sortText=L10n.tr(qualityFirst.contains(row.group)?"by quality":"by name");
                g.atext(sortText,Coord.of(sz.x-UI.scale(8),y),1,.5);
            }
            g.chcolor();super.draw(g);
        }
        public boolean mousedown(MouseDownEvent event){
            if(event.propagate(this))return true;
            if(event.b!=1)return false;
            if(sortable()&&event.c.x>=sortLeft()-UI.scale(6)){
                if(!qualityFirst.remove(row.group))qualityFirst.add(row.group);
                listVersion++;return true;
            }
            if(!objectFilter.isEmpty())return true;
            if(!closedGroups.remove(row.group))closedGroups.add(row.group);
            savePrefSet("hearthborn/claim-plan/groups-closed",closedGroups);listVersion++;return true;
        }
        public Object tooltip(Coord at,Widget previous){
            if(sortable()&&at.x>=sortLeft()-UI.scale(6))return L10n.tr("Sort by quality");
            return null;
        }
    }
    private final class RowView extends Widget {
        private final Row row;
        private final boolean area;
        private final FieldIcon edit,reset,remove;
        private final TextEntry name;
        private final int textLeft,editLeft;
        private boolean editing;
        private String shownFor="",shown="";private int shownWidth=-1;
        RowView(Row row,Coord size){
            super(size);this.row=row;area=row.entry.area!=null;
            int side=UI.scale(FieldIcon.SIDE),gap=UI.scale(3),y=(size.y-side)/2,right=size.x-UI.scale(4);
            if(area){
                remove=add(new FieldIcon(FieldIcon.Glyph.CLOSE,"Delete area",()->L10n.tr("Delete area"),()->{if(row.entry!=null)deleteArea(row.entry.area);}),Coord.of(right-side,y));
                right=remove.c.x-gap;
            }else remove=null;
            edit=add(new FieldIcon(FieldIcon.Glyph.EDIT,"Rename object",()->L10n.tr(editing?"Save name":area?"Rename area":"Rename object"),()->{
                if(editing)commitRename(row,RowView.this.name.text());else startRename(row);
            }),Coord.of(right-side,y));
            editLeft=edit.c.x;
            reset=add(new FieldIcon(FieldIcon.Glyph.REFRESH,"Reset name",()->L10n.tr("Reset name"),()->resetName(row)),Coord.of(editLeft-gap-side,y));reset.hide();
            textLeft=UI.scale(30);
            if(!area){
                String group=group(row.entry.type);
                add(new FieldIcon("ovens".equals(group)?FieldIcon.Glyph.FLAME:"houses".equals(group)?FieldIcon.Glyph.HOUSE:FieldIcon.Glyph.BOX),Coord.of(UI.scale(4),y));
            }
            int entryRight=(area?editLeft:reset.c.x)-gap;
            name=add(new TextEntry(Math.max(UI.scale(40),entryRight-textLeft),""){
                public void activate(String text){commitRename(row,text);}
                public boolean keydown(KeyDownEvent event){if(event.code==KeyEvent.VK_ESCAPE){cancelRename();return true;}return super.keydown(event);}
            },Coord.of(textLeft,(size.y-UI.scale(24))/2));
            name.canactivate=true;name.hide();
        }
        public void tick(double dt){
            super.tick(dt);
            boolean now=row.id.equals(renaming);
            if(now!=editing){
                editing=now;name.show(editing);edit.glyph(editing?FieldIcon.Glyph.CHECK:FieldIcon.Glyph.EDIT);reset.show(editing&&!area);
                if(editing){name.settext(row.entry.name());name.setcanfocus(true);if(parent!=null)ClaimPlan.this.setfocus(name);}
                else name.setcanfocus(false);
            }
        }
        public void draw(GOut g){
            Entry entry=row.entry;
            if(entry.key().equals(canvas.selectedKey)){g.chcolor(90,94,47,UiTheme.light()?120:200);g.frect(Coord.z,sz);g.chcolor();}
            if(area){
                int box=UI.scale(14);Coord at=Coord.of(UI.scale(8),(sz.y-box)/2);
                g.chcolor(UiTheme.readable(new Color(176,141,70)));g.rect(at,Coord.of(box,box));
                if(!hiddenAreas.contains(entry.area.id)){
                    g.line(at.add(UI.scale(3),box/2),at.add(box/2-1,box-UI.scale(4)),2);g.line(at.add(box/2-1,box-UI.scale(4)),at.add(box-UI.scale(3),UI.scale(3)),2);
                }
                g.chcolor();
            }
            if(!editing){
                String full=entry.listName()+(Objects.equals(entry.region,canvas.region)?"":L10n.tr(" - another level"));
                int limit=(area?editLeft:edit.c.x)-UI.scale(4);
                double quality=area?0:entry.quality();
                if(quality>0){
                    String text=hearthborn.quality.RosterStore.fmtQ(quality);int width=Text.std.strsize(text).x;
                    g.chcolor(UiTheme.readable(qualityColor(quality)));g.atext(text,Coord.of(limit,sz.y/2),1,.5);g.chcolor();
                    limit-=width+UI.scale(8);
                }
                int room=Math.max(UI.scale(20),limit-textLeft);
                if(!full.equals(shownFor)||room!=shownWidth){shownFor=full;shownWidth=room;shown=Text.std.strsize(full).x<=room?full:Text.std.ellipsize(full,room).text;}
                Color ink=entry.gob==null&&!area?new Color(164,164,150):new Color(238,220,175);
                g.chcolor(UiTheme.readable(ink));g.atext(shown,Coord.of(textLeft,sz.y/2),0,.5);g.chcolor();
            }
            super.draw(g);
        }
        public Object tooltip(Coord at,Widget previous){return row.entry.listName();}
        public boolean mousedown(MouseDownEvent event){
            if(event.propagate(this))return true;
            if(event.b!=1)return false;
            if(area&&event.c.x<textLeft){
                String id=row.entry.area.id;if(!hiddenAreas.remove(id))hiddenAreas.add(id);
                savePrefSet("hearthborn/claim-plan/areas-off",hiddenAreas);return true;
            }
            canvas.locate(row.entry);return true;
        }
    }
    /** The claim under the player. A claim elsewhere in the same map segment stays hidden. */
    static Set<Coord> standingClaim(Set<Coord> tiles,Coord player){
        if(player==null||tiles==null||!tiles.contains(player))return Collections.emptySet();
        return component(tiles,player);
    }
    static Set<Coord> component(Set<Coord> tiles,Coord player){
        if(tiles.isEmpty())return Collections.emptySet();
        Coord seed=tiles.contains(player)?player:tiles.stream().min(Comparator.comparingDouble(at->at.dist(player))).get();
        Set<Coord> chosen=new HashSet<>();ArrayDeque<Coord> pending=new ArrayDeque<>();pending.add(seed);
        while(!pending.isEmpty()){
            Coord at=pending.remove();if(!tiles.contains(at)||!chosen.add(at))continue;
            pending.add(at.add(1,0));pending.add(at.add(-1,0));pending.add(at.add(0,1));pending.add(at.add(0,-1));
        }
        return chosen;
    }
    static boolean listedType(String type){return !type.equals("Tree")&&!type.equals("Bush")&&!type.equals("Mushroom")&&!type.equals("Fence")&&!type.equals("Gate")&&!type.equals("Drying Frame")&&!type.equals("Tanning Tub")&&!type.equals("Cheese Tray");}
    public static String buildingType(String resource){return type(resource);}
    static String type(String resource){
        if(resource!=null&&resource.startsWith("gfx/terobjs/bushes/"))return "Bush";
        if(resource!=null&&resource.startsWith("gfx/terobjs/trees/")&&!resource.endsWith("log")&&!resource.endsWith("stump"))return "Tree";

        if(resource!=null){String leaf=resource.substring(resource.lastIndexOf('/')+1);
            if(leaf.equals("chest")||leaf.equals("lchest")||leaf.equals("crate")||leaf.equals("metalcabinet"))return null;
            if(leaf.equals("tarkiln")||leaf.equals("tar-kiln"))return "Tar Kiln";
            if(leaf.equals("well"))return "Well";
            if(leaf.equals("chickencoop")||leaf.equals("chicken-coop"))return "Chicken Coop";
            if(leaf.equals("rabbithutch")||leaf.equals("rabbit-hutch"))return "Rabbit Hutch";
            if(leaf.equals("dframe")||leaf.equals("dryingframe")||leaf.equals("dryingrack"))return "Drying Frame";
            if(leaf.equals("ttub")||leaf.equals("tanningtub"))return "Tanning Tub";
        }
        if(resource!=null&&resource.startsWith("gfx/terobjs/arch/")){
            if(resource.contains("gate"))return "Gate";
            if(resource.contains("palisade")||resource.contains("wall")||resource.contains("fence")||resource.contains("pole")||resource.contains("wattle"))return "Fence";
        }
        String room=RoomPlan.kind(resource);if(room!=null)return room;
        if(resource==null)return null;
        String leaf=resource.substring(resource.lastIndexOf('/')+1);
        if(leaf.equals("minehole")||leaf.equals("minedoor")||leaf.equals("ladder"))return "Mine entrance";
        if(leaf.equals("pclaim"))return "Personal claim";
        if(leaf.equals("villageidol"))return "Village claim";
        if(resource.startsWith("gfx/terobjs/arch/"))return "Building";
        if(leaf.equals("dframe"))return "Drying Frame";if(leaf.equals("ttub"))return "Tanning Tub";
        if(leaf.equals("primsmelter"))return "Stack Furnace";if(leaf.equals("fineryforge"))return "Finery Forge";
        if(leaf.contains("house")||leaf.contains("cabin")||leaf.contains("hall")||leaf.contains("windmill"))return "Building";
        if(leaf.equals("barrel")||leaf.equals("cistern"))return null;
        return null;
    }
    static double[] footprint(Gob gob){
        java.awt.geom.Rectangle2D bounds=null;
        for(java.awt.geom.Area area:hearthborn.world.ObjectGeometry.hitboxes(Collections.singletonList(gob),null)){
            if(bounds==null)bounds=area.getBounds2D();else bounds=bounds.createUnion(area.getBounds2D());
        }
        if(bounds!=null&&bounds.getWidth()>0&&bounds.getHeight()>0)return new double[]{bounds.getMinX()-gob.rc.x,bounds.getMinY()-gob.rc.y,bounds.getMaxX()-gob.rc.x,bounds.getMaxY()-gob.rc.y};
        return tileFootprint(gob.rc);
    }
    private static double[] tileFootprint(Coord2d at){Coord tile=at.floor(MCache.tilesz);return new double[]{tile.x*MCache.tilesz.x-at.x,tile.y*MCache.tilesz.y-at.y,(tile.x+1)*MCache.tilesz.x-at.x,(tile.y+1)*MCache.tilesz.y-at.y};}
    private final class Entry {
        ClaimSurvey.NamedArea area;
        final Gob gob;final String type,resource,ownerKey,region;final long gridId;final boolean ready;final Coord2d at;final ClaimSurvey.ObjectRecord saved;final double[] footprint;Coord screen;haven.Area screenBounds;
        Entry(Gob gob,String type){this.gob=gob;this.type=type;gridId=gui.ui.sess.glob.map.getgridt(gob.rc.floor(MCache.tilesz)).id;region=ClaimSurvey.recordRegion(gui,gridId);resource=GameObjects.resourceName(gob);Coord local=gob.rc.sub(new Coord2d(gui.ui.sess.glob.map.getgridt(gob.rc.floor(MCache.tilesz)).ul).mul(MCache.tilesz)).floor(OCache.posres);ownerKey=resource+":"+gridId+":"+local.x+":"+local.y;at=gob.rc;saved=null;footprint=(type.equals("Tree")||type.equals("Bush"))?tileFootprint(gob.rc):ClaimPlan.footprint(gob);ready=CheeseAging.ready(gob)||ObjectTimers.ready(gob);}
        Entry(ClaimSurvey.GridRecord grid,ClaimSurvey.ObjectRecord record){gob=null;gridId=grid.id;region=grid.region;type=record.type;resource=record.resource;Coord local=new Coord2d(record.x,record.y).floor(OCache.posres);ownerKey=record.resource+":"+grid.id+":"+local.x+":"+local.y;saved=record;ready=record.ready;at=new Coord2d(grid.x*MCache.tilesz.x+record.x,grid.y*MCache.tilesz.y+record.y);footprint=type.equals("Tree")||type.equals("Bush")||record.footprint==null?tileFootprint(at):record.footprint;}
        Entry(ClaimSurvey.NamedArea area,ClaimSurvey.GridRecord grid){
            this.area=area;gob=null;type="Area";resource="area:"+area.id;ownerKey=null;region=grid.region;gridId=grid.id;ready=false;saved=null;
            at=new Coord2d((grid.x+area.x)*MCache.tilesz.x,(grid.y+area.y)*MCache.tilesz.y);footprint=new double[]{0,0,area.w*MCache.tilesz.x,area.h*MCache.tilesz.y};
        }
        String key(){return ownerKey!=null?ownerKey:resource+":"+gridId+":"+at;}
        private double rated=-1;
        double quality(){if(rated<0)rated=computeQuality();return rated;}
        private double computeQuality(){
            if(gob!=null){hearthborn.quality.RosterStore.Rec record=hearthborn.quality.RosterStore.trackedGob(gob);if(record!=null&&record.q>0)return record.q;
                for(ClaimSurvey.GridRecord grid:ClaimSurvey.records(region))if(grid.id==gridId)for(ClaimSurvey.ObjectRecord object:grid.objects.values()){
                    if(Objects.equals(resource,object.resource)&&Math.abs(grid.x*MCache.tilesz.x+object.x-at.x)<OCache.posres.x&&Math.abs(grid.y*MCache.tilesz.y+object.y-at.y)<OCache.posres.y)return object.quality;
                }
                return 0;}
            return saved==null?0:saved.quality;
        }
        String listName(){
            String custom=gob!=null?ObjectNames.name(gob):saved==null?null:ObjectNames.knownName(ownerKey,saved.name);
            return type.equals("Building")&&custom!=null?L10n.tr("House")+" - "+custom:name();
        }
        String displayName(){return name()+(quality()>0?" · Q "+hearthborn.quality.RosterStore.fmtQ(quality()):"");}
        String name(){if(area!=null)return area.name;if(type.equals("Fence")||type.equals("Gate"))return L10n.tr(type);
            String name=gob==null?ObjectNames.knownName(ownerKey,saved.name):ObjectNames.name(gob);if(name!=null)return name;
            if(type.equals("Tree")||type.equals("Bush"))return GameObjects.name(resource.replace("gfx/terobjs/","gfx/terobjs/mm/"));
            String fallback=L10n.text("window",type);return resource==null?fallback:L10n.resource("tooltip",resource,fallback);
        }
    }
    /** At most two centered lines, preferring word boundaries and retaining the full tooltip. */
    private static String[] areaLabelLines(String name,int width,int height){
        if(Text.std.strsize(name).x<=width)return new String[]{name};
        if(height<Text.std.height()*2)return new String[]{Text.std.ellipsize(name,width).text};
        int split=-1,score=Integer.MAX_VALUE;
        for(int i=1;i<name.length()-1;i++)if(Character.isWhitespace(name.charAt(i))){
            int candidate=Math.max(Text.std.strsize(name.substring(0,i).trim()).x,Text.std.strsize(name.substring(i).trim()).x);
            if(candidate<score){score=candidate;split=i;}
        }
        if(split<0){
            // A long unbroken name can still use the available second line.
            split=name.offsetByCodePoints(0,1);
            while(split<name.length()){
                int end=name.offsetByCodePoints(split,1);
                if(Text.std.strsize(name.substring(0,end)).x>width)break;
                split=end;
            }
        }
        return new String[]{Text.std.ellipsize(name.substring(0,split).trim(),width).text,
            Text.std.ellipsize(name.substring(split).trim(),width).text};
    }
    private final class Canvas extends Widget {
        Set<Coord> territory=Collections.emptySet();java.util.List<Entry> entries=new ArrayList<>();
        java.util.List<PlanFields.Field> fields=Collections.emptyList();
        final Map<Coord,Integer> terrain=new HashMap<>();int groundVersion=-1;
        Coord min,max,origin,pan=Coord.z;double zoom=1,scale,next;Tex ground;String groundTag;boolean partial;String message="";
        UI.Grab drag,measureGrab;Coord pressed,oldPan,measureStart,measureEnd;String selectedKey;double selectedUntil;
        String region,viewRegion;Coord focusTile;int version;
        java.util.List<Entry> listEntries(){
            return new ArrayList<>(entries);
        }
        final Map<String,Coord> regionPans=new HashMap<>();final Map<String,Double> regionZooms=new HashMap<>();
        final Map<String,Tex> savedIcons=new HashMap<>();
        final Map<String,String[]> areaLabels=new HashMap<>();
        private void areaLabel(GOut g,String name,Coord upper,Coord size){
            int width=size.x-UI.scale(4),height=size.y-UI.scale(4);
            if(width<=0||height<Text.std.height())return;
            String key=width+":"+height+":"+name;String[] lines=areaLabels.get(key);
            if(lines==null){
                lines=areaLabelLines(name,width,height);
                if(areaLabels.size()>=256)areaLabels.clear();
                areaLabels.put(key,lines);
            }
            GOut clipped=g.reclip(upper,size);
            int lineHeight=Text.std.height(),top=(size.y-lines.length*lineHeight)/2;
            for(int i=0;i<lines.length;i++)clipped.atext(lines[i],Coord.of(size.x/2,top+i*lineHeight),.5,0);
        }
        private void fieldLabel(GOut g,FieldPlans.Field field,Coord upper,Coord size){
            int width=size.x-UI.scale(4),height=size.y-UI.scale(4),lineHeight=Text.std.height();
            if(field.qualityTiles==0||height<lineHeight*2){areaLabel(g,field.heading(),upper,size);return;}
            if(width<=0)return;
            java.util.List<String> lines=new ArrayList<>(Arrays.asList(areaLabelLines(field.heading(),width,height-lineHeight)));
            lines.add(Text.std.ellipsize(field.quality(),width).text);
            GOut clipped=g.reclip(upper,size);int top=(size.y-lines.size()*lineHeight)/2;
            for(int i=0;i<lines.size();i++)clipped.atext(lines.get(i),Coord.of(size.x/2,top+i*lineHeight),.5,0);
        }
        Canvas(){super(UI.scale(704,470));}
        public void tick(double dt){super.tick(dt);renameIcon.disable(selectedPlan==null);deleteIcon.disable(selectedPlan==null);measurement.move(c.add(sz.x-measurement.sz.x-UI.scale(8),sz.y-measurement.sz.y-UI.scale(8)));if(Utils.rtime()>=next){next=Utils.rtime()+1.5;refresh();}}
        private void refresh(){
            Gob player=gui.map==null?null:gui.map.player();if(player==null)return;
            ClaimSurvey.observe(gui);
            if(viewRegion==null&&!territory.isEmpty()&&(ClaimSurvey.inside(gui)||!ClaimSurvey.locationKnown(gui)&&!ClaimSurvey.scanning(gui)))return;
            String shown=selectedPlan==null?null:selectedPlan.id;
            if(viewRegion==null)selectedPlan=null;
            else if(selectedPlan!=null)for(ClaimSurvey.Plan plan:ClaimSurvey.plans())if(plan.id.equals(selectedPlan.id)){selectedPlan=plan;break;}
            String current=viewRegion==null?ClaimSurvey.displayRegion(gui):viewRegion;if(!Objects.equals(region,current)){
                if(region!=null){regionPans.put(region,pan);regionZooms.put(region,zoom);}
                region=current;pan=regionPans.getOrDefault(region,Coord.z);zoom=regionZooms.getOrDefault(region,1.0);
                measureStart=measureEnd=null;measurement.settext("");selectedKey=null;
            }
            Collection<ClaimSurvey.GridRecord> survey=selectedPlan!=null&&selectedPlan.snapshot!=null?selectedPlan.snapshot:ClaimSurvey.records(region);
            if(selectedPlan!=null)for(ClaimSurvey.GridRecord grid:survey)if(grid.id==selectedPlan.grid)focusTile=Coord.of(grid.x+selectedPlan.x,grid.y+selectedPlan.y);
            boolean here=!ClaimSurvey.inside(gui)&&Objects.equals(region,ClaimSurvey.region());

            Set<Coord> all=new HashSet<>(),known=new HashSet<>();boolean loading=false;
            for(ClaimSurvey.GridRecord grid:survey){
                known.add(Coord.of(grid.x,grid.y).div(MCache.cmaps));
                BitSet mask=tag.equals("cplot")?grid.personal:grid.village;
                for(int cell=mask.nextSetBit(0);cell>=0;cell=mask.nextSetBit(cell+1))all.add(Coord.of(grid.x+cell%100,grid.y+cell/100));
            }
            // Older underground snapshots can contain objects but no claim overlay.
            // Show their recorded tile bounds, never project them onto the surface plan.
            boolean objectsOnly=all.isEmpty()&&!here;
            if(objectsOnly)for(ClaimSurvey.GridRecord grid:survey)for(ClaimSurvey.ObjectRecord record:grid.objects.values()){
                Coord at=new Coord2d(grid.x*MCache.tilesz.x+record.x,grid.y*MCache.tilesz.y+record.y).floor(MCache.tilesz);
                for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)all.add(at.add(x,y));
            }
            Coord2d marker=ClaimSurvey.marker(gui,region);
            Coord feet=marker==null?null:marker.floor(MCache.tilesz);
            Coord anchor=viewRegion==null?null:focusTile!=null?focusTile:feet!=null?feet:all.isEmpty()?Coord.z:all.stream().min(Comparator.comparingInt((Coord c)->c.y).thenComparingInt(c->c.x)).get();
            Set<Coord> selected=viewRegion==null?standingClaim(all,feet):component(all,anchor);
            boolean changed=!selected.equals(territory)||!tag.equals(groundTag)||groundVersion!=ClaimSurvey.generation();
            terrain.clear();for(ClaimSurvey.GridRecord grid:survey)if(grid.terrain!=null)for(int cell=0;cell<grid.terrain.length;cell++){
                Coord at=Coord.of(grid.x+cell%100,grid.y+cell/100);if(grid.terrain[cell]!=0&&selected.contains(at))terrain.put(at,(int)grid.terrain[cell]);
            }
            if(selectedPlan==null&&viewRegion==null){
                java.util.List<ClaimSurvey.Plan> matches=new ArrayList<>();
                for(ClaimSurvey.Plan plan:ClaimSurvey.plans())if(plan.snapshot==null&&tag.equals(plan.tag)){
                    boolean found=false;
                    for(ClaimSurvey.GridRecord grid:survey)if(grid.id==plan.grid&&selected.contains(Coord.of(grid.x+plan.x,grid.y+plan.y))){found=true;break;}
                    if(found)matches.add(plan);
                }
                selectedPlan=choose(matches,shown);
            }
            territory=selected;entries.clear();version++;partial=loading;
            fields=PlanFields.project(FieldPlans.list(gui),survey,selected);
            if(changed&&ground!=null){ground.dispose();ground=null;}
            if(selected.isEmpty()){
                min=max=null;message=L10n.tr("No claim boundary loaded. Approach a claim outdoors.");status.settext(message);return;
            }
            int minX=Integer.MAX_VALUE,minY=minX,maxX=Integer.MIN_VALUE,maxY=maxX;
            for(Coord tile:selected){minX=Math.min(minX,tile.x);minY=Math.min(minY,tile.y);maxX=Math.max(maxX,tile.x);maxY=Math.max(maxY,tile.y);
                for(Coord neighbor:new Coord[]{tile.add(1,0),tile.add(-1,0),tile.add(0,1),tile.add(0,-1)})if(!known.contains(neighbor.div(MCache.cmaps)))partial=true;
            }
            min=Coord.of(minX,minY);max=Coord.of(maxX+1,maxY+1);
            if(ground==null){
                Coord extent=max.sub(min);java.awt.image.BufferedImage image=TexI.mkbuf(extent.mul(3));java.awt.Graphics2D graphics=image.createGraphics();
                graphics.setColor(tag.equals("cplot")?new Color(62,68,44):new Color(48,61,70));
                for(Coord tile:territory){
                    int kind=terrain.getOrDefault(tile,0);graphics.setColor(kind==0?(tag.equals("cplot")?new Color(62,68,44):new Color(48,61,70)):new Color(PlanExport.terrainColor(kind)));
                    graphics.fillRect((tile.x-min.x)*3,(tile.y-min.y)*3,3,3);
                }
                graphics.setColor(new Color(210,178,90));
                for(Coord tile:territory){int x=(tile.x-min.x)*3,y=(tile.y-min.y)*3;
                    if(!territory.contains(tile.add(0,-1)))graphics.drawLine(x,y,x+2,y);
                    if(!territory.contains(tile.add(-1,0)))graphics.drawLine(x,y,x,y+2);
                    if(!territory.contains(tile.add(1,0)))graphics.drawLine(x+2,y,x+2,y+2);
                    if(!territory.contains(tile.add(0,1)))graphics.drawLine(x,y+2,x+2,y+2);
                }
                graphics.dispose();ground=new TexI(image);groundTag=tag;groundVersion=ClaimSurvey.generation();
            }
            if(here)synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)try{
                if(!ClaimSurvey.placed(gob)||!selected.contains(gob.rc.floor(MCache.tilesz)))continue;
                long gridId=gui.ui.sess.glob.map.getgridt(gob.rc.floor(MCache.tilesz)).id;
                if(!Objects.equals(region,ClaimSurvey.recordRegion(gui,gridId))||survey.stream().noneMatch(grid->grid.id==gridId))continue;
                String type=type(GameObjects.resourceName(gob));if(type!=null)entries.add(new Entry(gob,type));
            }catch(Loading wait){partial=true;}}
            Set<String> liveKeys=new HashSet<>();for(Entry entry:entries)liveKeys.add(entry.key());
            for(ClaimSurvey.GridRecord grid:survey)for(ClaimSurvey.ObjectRecord record:grid.objects.values()){
                if(type(record.resource)==null&&(selectedPlan==null||selectedPlan.snapshot==null))continue;
                Entry entry=new Entry(grid,record);if(selected.contains(entry.at.floor(MCache.tilesz))&&!liveKeys.contains(entry.key()))entries.add(entry);
            }
            ClaimSurvey.GridRecord anchorGrid=anchorGrid();
            if(anchorGrid!=null&&selectedPlan.areas!=null)for(ClaimSurvey.NamedArea area:selectedPlan.areas)entries.add(new Entry(area,anchorGrid));
            entries.sort(Comparator.comparing(Entry::key));
            message=L10n.tr(tag.equals("cplot")?"Personal claim":"Village claim")+" · "+L10n.format("%d × %d tiles",max.x-min.x,max.y-min.y)+" · "+L10n.format("Objects: %d",entries.size())+" · "+L10n.tr(partial?"Boundary incomplete":"Loaded boundary")+" · "+L10n.tr("Live and saved objects");status.settext(objectsOnly?L10n.tr("Saved objects on another level · claim boundary not saved"):message);
        }
        private Coord point(Coord2d world){
            double x=world.x/MCache.tilesz.x-min.x,y=world.y/MCache.tilesz.y-min.y;
            return origin.add((int)Math.round(x*scale),(int)Math.round(y*scale));
        }
        private void grid(GOut g,int step,Color color,boolean numbers){
            g.chcolor(color);
            for(int x=min.x;x<=max.x;x+=step){
                int start=Integer.MIN_VALUE;
                for(int y=min.y;y<=max.y;y++){
                    boolean inside=y<max.y&&(territory.contains(Coord.of(x,y))||territory.contains(Coord.of(x-1,y)));
                    if(inside&&start==Integer.MIN_VALUE)start=y;
                    if(!inside&&start!=Integer.MIN_VALUE){segment(g,point(new Coord2d(x*MCache.tilesz.x,start*MCache.tilesz.y)),point(new Coord2d(x*MCache.tilesz.x,y*MCache.tilesz.y)));start=Integer.MIN_VALUE;}
                }
                if(numbers){Coord at=point(new Coord2d(x*MCache.tilesz.x,min.y*MCache.tilesz.y));g.atext(Integer.toString(x-min.x),at.add(0,-UI.scale(3)),.5,1);}
            }
            for(int y=min.y;y<=max.y;y+=step){
                int start=Integer.MIN_VALUE;
                for(int x=min.x;x<=max.x;x++){
                    boolean inside=x<max.x&&(territory.contains(Coord.of(x,y))||territory.contains(Coord.of(x,y-1)));
                    if(inside&&start==Integer.MIN_VALUE)start=x;
                    if(!inside&&start!=Integer.MIN_VALUE){segment(g,point(new Coord2d(start*MCache.tilesz.x,y*MCache.tilesz.y)),point(new Coord2d(x*MCache.tilesz.x,y*MCache.tilesz.y)));start=Integer.MIN_VALUE;}
                }
                if(numbers){Coord at=point(new Coord2d(min.x*MCache.tilesz.x,y*MCache.tilesz.y));g.atext(Integer.toString(y-min.y),at.add(-UI.scale(3),0),1,.5);}
            }
            g.chcolor();
        }
        private void axisLabels(GOut g,int step){
            g.chcolor(225,211,170,190);
            for(int x=min.x;x<=max.x;x+=step){Coord at=point(new Coord2d(x*MCache.tilesz.x,min.y*MCache.tilesz.y));g.atext(Integer.toString(x-min.x),at.add(0,-UI.scale(3)),.5,1);}
            for(int y=min.y;y<=max.y;y+=step){Coord at=point(new Coord2d(min.x*MCache.tilesz.x,y*MCache.tilesz.y));g.atext(Integer.toString(y-min.y),at.add(-UI.scale(3),0),1,.5);}
            g.chcolor();
        }
        private void projection(){
            if(min==null||max==null)return;Coord extent=max.sub(min);
            scale=Math.min((double)(sz.x-UI.scale(32))/extent.x,(double)(sz.y-UI.scale(32))/extent.y)*zoom;
            origin=sz.sub(Coord.of((int)(extent.x*scale),(int)(extent.y*scale))).div(2).add(pan);
        }
        private void locate(Entry entry){
            if(!Objects.equals(entry.region,region)&&entry.region!=null){
                viewRegion=entry.region;focusTile=entry.at.floor(MCache.tilesz);refresh();
            }else if(!territory.contains(entry.at.floor(MCache.tilesz))){focusTile=entry.at.floor(MCache.tilesz);refresh();}
            if(min==null||!territory.contains(entry.at.floor(MCache.tilesz))){gui.ui.msg(L10n.tr("No saved plan for this object. Visit its level again."));return;}
            projection();if(origin==null)return;
            selectedKey=entry.key();layout();selectedUntil=Utils.rtime()+8;pan=pan.add(sz.div(2).sub(point(entry.at)));projection();
            if(entry.gob!=null)entry.gob.setattr(new ObjectHighlight(entry.gob));
        }
        private Coord tileAt(Coord at){projection();return Coord.of(min.x+(int)Math.floor((at.x-origin.x)/scale),min.y+(int)Math.floor((at.y-origin.y)/scale));}
        private void measured(){
            int width=Math.abs(measureEnd.x-measureStart.x)+1,height=Math.abs(measureEnd.y-measureStart.y)+1;
            measurement.settext(L10n.format("%d × %d tiles; total: %d",width,height,(long)width*height));
        }
        private void segment(GOut g,Coord a,Coord b){
            Coord at=Coord.of(Math.min(a.x,b.x),Math.min(a.y,b.y));
            g.frect(at,Coord.of(Math.abs(b.x-a.x)+1,Math.abs(b.y-a.y)+1));
        }
        private void outline(GOut g,Coord at,Coord size){
            Coord end=at.add(size).sub(1,1);
            segment(g,at,Coord.of(end.x,at.y));segment(g,Coord.of(end.x,at.y),end);
            segment(g,end,Coord.of(at.x,end.y));segment(g,Coord.of(at.x,end.y),at);
        }
        public void draw(GOut g){
            g=g.reclip(Coord.z,sz);
            g.chcolor(19,24,17,255);g.frect(Coord.z,sz);g.chcolor();
            if(territory.isEmpty()||min==null){g.atext(message,sz.div(2),.5,.5);return;}
            projection();Coord extent=max.sub(min);
            java.util.List<PlanFields.Field> visibleFields=layerShown("fields")?fields:Collections.<PlanFields.Field>emptyList();
            if(ground!=null)g.image(ground,origin,Coord.of(Math.max(1,(int)(extent.x*scale)),Math.max(1,(int)(extent.y*scale))));
            g.chcolor(PlanFields.FILL);
            for(PlanFields.Field field:visibleFields)for(haven.Area patch:field.patches){
                Coord upper=point(new Coord2d(patch.ul).mul(MCache.tilesz)),lower=point(new Coord2d(patch.br).mul(MCache.tilesz));
                g.frect(upper,lower.sub(upper));
            }
            g.chcolor();

            grid(g,1,new Color(157,166,139,65),false);
            int labels=10;while(labels*scale<UI.scale(35))labels*=2;
            axisLabels(g,labels);
            Coord north=Coord.of(sz.x-UI.scale(18),UI.scale(13));g.chcolor(230,215,171,255);
            g.atext(L10n.tr("N"),north,.5,0);g.line(north.add(0,UI.scale(31)),north.add(0,UI.scale(16)),2);
            g.line(north.add(0,UI.scale(16)),north.add(-UI.scale(4),UI.scale(22)),2);g.line(north.add(0,UI.scale(16)),north.add(UI.scale(4),UI.scale(22)),2);g.chcolor();
            for(Entry entry:entries){
                if(hidden(entry)){entry.screenBounds=null;continue;}
                double[] bounds=entry.footprint;
                Coord upper=point(entry.at.add(bounds[0],bounds[1])),lower=point(entry.at.add(bounds[2],bounds[3]));
                Coord size=lower.sub(upper).max(Coord.of(1,1));entry.screenBounds=haven.Area.sized(upper,size);Coord at=upper.add(size.div(2));entry.screen=at;
                Color color=(entry.type.equals("Tree")||entry.type.equals("Bush"))?new Color(77,152,83):entry.ready?new Color(137,225,91):entry.type.equals("Fence")||entry.type.equals("Building")?new Color(146,127,96):new Color(216,182,106);
                if(entry.gob==null)color=new Color((color.getRed()+100)/2,(color.getGreen()+100)/2,(color.getBlue()+100)/2);
                if(entry.area!=null){
                    g.chcolor(105,187,240,45);g.frect(upper,size);g.chcolor(130,210,255,230);outline(g,upper,size);g.chcolor();areaLabel(g,entry.name(),upper,size);continue;
                }
                g.chcolor(color);g.frect(upper,size);g.chcolor(22,26,18,230);if(size.x>2&&size.y>2)outline(g,upper,size);g.chcolor();
                if(entry.key().equals(selectedKey)&&Utils.rtime()<selectedUntil){
                    g.chcolor(255,55,55,255);outline(g,upper.sub(2,2),size.add(4,4));segment(g,at.sub(UI.scale(6),0),at.add(UI.scale(6),0));segment(g,at.sub(0,UI.scale(6)),at.add(0,UI.scale(6)));g.chcolor();
                }
                String custom=entry.gob==null?(entry.saved==null?null:ObjectNames.knownName(entry.ownerKey,entry.saved.name)):ObjectNames.name(entry.gob);
                if(entry.type.equals("Fence"))continue;
                ContainerContents contents=entry.gob==null?null:entry.gob.getattr(ContainerContents.class);Tex icon=contents==null?null:contents.picture();
                if(icon==null&&entry.saved!=null&&entry.saved.icon!=null){
                    icon=savedIcons.get(entry.saved.icon);
                    if(icon==null){java.awt.image.BufferedImage picture=ContainerContents.savedPicture(entry.saved.icon);if(picture!=null){icon=new TexI(picture);savedIcons.put(entry.saved.icon,icon);}}
                }
                int inside=Math.min(size.x,size.y)-2;
                if(entry.type.equals("Building")&&custom!=null){
                    GOut clipped=g.reclip(upper,size);clipped.chcolor(Color.BLACK);clipped.atext(custom,size.div(2),.5,.5);clipped.chcolor();
                }
                else if(icon!=null&&inside>=2){Coord source=icon.sz();double ratio=(double)inside/Math.max(source.x,source.y);Coord drawn=Coord.of(Math.max(1,(int)(source.x*ratio)),Math.max(1,(int)(source.y*ratio)));g.image(icon,at.sub(drawn.div(2)),drawn);}
                else if(size.x>=UI.scale(11)&&size.y>=UI.scale(13)){
                    String glyph=entry.type.equals("Exit")?"↗":RoomPlan.passage(entry.type)?L10n.tr(entry.type):L10n.text("window",entry.type).substring(0,1);
                    GOut clipped=g.reclip(upper,size);clipped.chcolor(Color.BLACK);clipped.atext(glyph,size.div(2),.5,.5);clipped.chcolor();
                }
                if(entry.ready&&size.x>=4&&size.y>=4){g.chcolor(132,255,78,255);g.frect(upper.add(size.x-3,0),Coord.of(3,3));g.chcolor();}
                badge(g,entry,upper,size,at);
            }
            for(PlanFields.Field field:visibleFields){
                g.chcolor(PlanFields.BORDER);
                if(field.patches.size()==1){
                    haven.Area patch=field.patches.get(0);Coord upper=point(new Coord2d(patch.ul).mul(MCache.tilesz));
                    outline(g,upper,point(new Coord2d(patch.br).mul(MCache.tilesz)).sub(upper).max(Coord.of(1,1)));
                }else for(Coord tile:field.tiles){
                    Coord upper=point(new Coord2d(tile).mul(MCache.tilesz)),lower=point(new Coord2d(tile.add(1,1)).mul(MCache.tilesz));
                    if(!field.tiles.contains(tile.add(0,-1)))segment(g,upper,Coord.of(lower.x,upper.y));
                    if(!field.tiles.contains(tile.add(-1,0)))segment(g,upper,Coord.of(upper.x,lower.y));
                    if(!field.tiles.contains(tile.add(1,0)))segment(g,Coord.of(lower.x,upper.y),lower);
                    if(!field.tiles.contains(tile.add(0,1)))segment(g,Coord.of(upper.x,lower.y),lower);
                }
                g.chcolor();Coord upper=point(new Coord2d(field.label.ul).mul(MCache.tilesz));
                fieldLabel(g,field.source,upper,point(new Coord2d(field.label.br).mul(MCache.tilesz)).sub(upper));
            }
            if(measureStart!=null&&measureEnd!=null){
                Coord a=Coord.of(Math.min(measureStart.x,measureEnd.x),Math.min(measureStart.y,measureEnd.y));Coord b=Coord.of(Math.max(measureStart.x,measureEnd.x)+1,Math.max(measureStart.y,measureEnd.y)+1);
                Coord upper=point(new Coord2d(a).mul(MCache.tilesz)),lower=point(new Coord2d(b).mul(MCache.tilesz));
                g.chcolor(92,173,235,65);g.frect(upper,lower.sub(upper));g.chcolor(145,215,255,255);outline(g,upper,lower.sub(upper));g.chcolor();
            }
            Coord2d marker=ClaimSurvey.marker(gui,region);if(marker!=null&&territory.contains(marker.floor(MCache.tilesz))){g.chcolor(255,55,55,255);g.fellipse(point(marker),UI.scale(3,3));g.chcolor();}
            legend(g);
        }
        private boolean rated(Entry entry){return entry.area==null&&!"trees".equals(layer(entry.type))&&!hidden(entry)&&entry.quality()>0;}
        private void badge(GOut g,Entry entry,Coord upper,Coord size,Coord at){
            if(!rated(entry))return;
            double quality=entry.quality();Color color=qualityColor(quality);
            String text=Integer.toString((int)Math.round(quality));
            int pad=UI.scale(3),height=Text.std.height()+UI.scale(2),width=Text.std.strsize(text).x+pad*2,square=UI.scale(6),gap=UI.scale(2);
            Coord mark=Coord.of(upper.x+size.x+gap,at.y-square/2),box=Coord.of(mark.x+square+gap,at.y-height/2);
            g.chcolor(color);g.frect(mark,Coord.of(square,square));
            g.chcolor(20,20,16,225);g.frect(box,Coord.of(width,height));
            g.chcolor(color);g.rect(box,Coord.of(width,height));g.atext(text,box.add(width/2,height/2),.5,.5);g.chcolor();
        }
        private void legend(GOut g){
            boolean any=false;for(Entry entry:entries)if(rated(entry)){any=true;break;}
            if(!any)return;
            String title=L10n.text("tooltip","Quality");String[] labels={"100+","60–99","<60"};double[] samples={100,60,0};
            int pad=UI.scale(6),square=UI.scale(8),gap=UI.scale(10),height=Text.std.height()+pad*2;
            int width=pad*2+Text.std.strsize(title).x;
            for(String label:labels)width+=gap+square+UI.scale(4)+Text.std.strsize(label).x;
            Coord at=Coord.of(UI.scale(8),sz.y-height-UI.scale(8));
            g.chcolor(14,16,12,205);g.frect(at,Coord.of(width,height));g.chcolor(94,80,50,255);g.rect(at,Coord.of(width,height));
            int x=at.x+pad,y=at.y+height/2;
            g.chcolor(210,196,150,255);g.atext(title,Coord.of(x,y),0,.5);x+=Text.std.strsize(title).x;
            for(int i=0;i<labels.length;i++){
                x+=gap;g.chcolor(qualityColor(samples[i]));g.frect(Coord.of(x,y-square/2),Coord.of(square,square));x+=square+UI.scale(4);
                g.chcolor(210,196,150,255);g.atext(labels[i],Coord.of(x,y),0,.5);x+=Text.std.strsize(labels[i]).x;
            }
            g.chcolor();
        }
        private Entry hit(Coord at){if(!at.isect(Coord.z,sz))return null;Entry found=null;double distance=Double.MAX_VALUE;for(Entry entry:entries)if(entry.screenBounds!=null&&entry.screenBounds.contains(at)&&entry.screen.dist(at)<distance){distance=entry.screen.dist(at);found=entry;}return found;}
        public Object tooltip(Coord at,Widget previous){
            Entry entry=hit(at);
            if((entry==null||entry.area!=null)&&min!=null&&at.isect(Coord.z,sz)){
                Coord tile=tileAt(at);if(layerShown("fields"))for(PlanFields.Field field:fields)if(field.tiles.contains(tile))return field.source.tooltip();
            }
            if(entry==null)return null;String name=entry.displayName();if(entry.ready)name+=" - "+L10n.tr("Ready");return name;
        }
        public boolean mousedown(MouseDownEvent event){if(event.b==1&&measuring&&min!=null){measureStart=measureEnd=tileAt(event.c);measureGrab=ui.grabmouse(this);measured();return true;}if(event.b==1){Entry entry=hit(event.c);
                if(entry!=null){
                String key=entry.key();double now=Utils.rtime();
                selectedKey=key;selectedUntil=now+8;
                if(listedType(entry.type)){listShown=true;Utils.setprefb("hearthborn/claim-plan/list",true);closedGroups.remove(group(entry.type));listVersion++;layout();for(Row row:objectList.items())if(row.entry!=null&&row.entry.key().equals(key)){objectList.display(row);break;}}
                if(entry.gob!=null)entry.gob.setattr(new ObjectHighlight(entry.gob));
            }return true;}if(event.b==3){pressed=event.c;oldPan=pan;drag=ui.grabmouse(this);return true;}return false;}
        public void mousemove(MouseMoveEvent event){if(measureGrab!=null){measureEnd=tileAt(event.c);measured();}if(drag!=null)pan=oldPan.add(event.c.sub(pressed));}
        public boolean mouseup(MouseUpEvent event){if(measureGrab!=null&&event.b==1){measureEnd=tileAt(event.c);measured();measureGrab.remove();measureGrab=null;
                if(addingArea){addingArea=false;ClaimSurvey.GridRecord grid=anchorGrid();if(grid!=null){
                    ClaimSurvey.NamedArea area=new ClaimSurvey.NamedArea();area.x=Math.min(measureStart.x,measureEnd.x)-grid.x;area.y=Math.min(measureStart.y,measureEnd.y)-grid.y;
                    area.w=Math.abs(measureStart.x-measureEnd.x)+1;area.h=Math.abs(measureStart.y-measureEnd.y)+1;areaName(area,true);
                }toggleMeasure();}return true;}if(drag!=null&&event.b==3){drag.remove();drag=null;return true;}return super.mouseup(event);}
        public boolean mousewheel(MouseWheelEvent event){zoom=Utils.clip(zoom*Math.pow(1.15,-event.a),.25,12);return true;}
        public void dispose(){if(measureGrab!=null)measureGrab.remove();if(drag!=null)drag.remove();if(ground!=null)ground.dispose();for(Tex icon:savedIcons.values())icon.dispose();super.dispose();}
    }
}
