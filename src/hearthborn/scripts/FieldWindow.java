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

package hearthborn.scripts;

import haven.*;
import hearthborn.L10n;
import hearthborn.ui.UiTheme;
import hearthborn.navigation.Walking;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The field list: one row per field with small icon buttons. The active field's row shows its progress, and a click on the pencil opens the editor beside it.
 *
 * @author Jagy2014
 */
public final class FieldWindow extends Window {
    private final GameUI gui;
    private final SavedAreaHighlight highlight;
    private final WheatFields book;
    private final String scope;
    private static final int WIDTH=340,ROW=32,MAX_ROWS=8;
    private final SListBox<WheatFields.Field,Widget> list;
    private final SDropBox<String,Widget> filter;
    private final FieldIcon add;
    private final Label empty;
    private final Widget selectionHint;
    private final Label selectionTitle;
    private final FieldIcon cancelIcon;
    private FieldEditor editor;
    private Window numberPrompt;
    private String cropFilter;
    private Runnable cancelSelection;
    private PendingSelection pendingSelection;
    private final class PendingSelection {
        final WheatFields.Field field;
        final Area area;
        final int kind;
        final MapView map=gui.map;
        final String scope=SavedArea.key(gui,"wheat-fields");
        final double until=Utils.rtime()+15;
        double retry;
        boolean saved;
        PendingSelection(WheatFields.Field field,Area area,int kind){this.field=field;this.area=Area.sized(area.ul,area.sz());this.kind=kind;}
    }
    private int selectionKind=-1;
    private String lastControls="";
    private String lastClickedField;
    private double lastClickedAt;
    private static final String ALL="";
    public static void open(GameUI gui){
        if(gui==null)return;
        FieldWindow existing=gui.getchild(FieldWindow.class);
        if(existing!=null&&(!java.util.Objects.equals(existing.scope,SavedArea.key(gui,"wheat-fields"))||!existing.visible())){existing.destroy();existing=null;}
        if(existing!=null){existing.show();existing.raise();return;}
        try{
            FieldTask active=FieldTask.current(gui);
            gui.add(new FieldWindow(gui,active!=null?active.book:WheatFields.load(gui)),UI.scale(120,100));
        }
        catch(IllegalStateException failure){gui.error(L10n.tr(failure.getMessage()));}
    }
    public static void activate(GameUI gui,boolean settings){
        if(gui==null)return;
        // The list shows the running field in its own row, so there is no separate progress window.
        if(settings||FieldTask.current(gui)!=null){open(gui);return;}
        if(ScriptRunner.running(gui)){gui.error(L10n.tr("Stop the current script first."));return;}
        FieldWindow existing=gui.getchild(FieldWindow.class);
        if(existing!=null&&existing.cancelSelection!=null){existing.show();existing.raise();return;}
        if(gui.map!=null&&gui.map.hasAreaSelection()){gui.error(L10n.tr("Finish or cancel the area selection first."));return;}
        try{
            WheatFields book=existing!=null&&java.util.Objects.equals(existing.scope,SavedArea.key(gui,"wheat-fields"))?existing.book:WheatFields.load(gui);
            WheatFields.Field field=book.here(gui);
            if(field==null){open(gui);return;}
            book.selected=field.id;book.save(gui);
            if(!configured(field)){
                open(gui);FieldWindow window=gui.getchild(FieldWindow.class);window.list.change(window.book.selected());window.openEditor();return;
            }
            FieldTask.launch(gui,book,field,unfinished(field)&&field.harvestOnly);
            open(gui);
        }catch(IllegalStateException failure){open(gui);gui.error(L10n.tr(failure.getMessage()));}
    }
    static boolean unfinished(WheatFields.Field field){return field.phase!=WheatFields.Phase.READY&&field.phase!=WheatFields.Phase.DONE;}
    private FieldWindow(GameUI gui,WheatFields book){
        super(Coord.z,"Crop fields");this.gui=gui;this.book=book;scope=SavedArea.key(gui,"wheat-fields");highlight=new SavedAreaHighlight(gui,this);
        add=add(new FieldIcon(FieldIcon.Glyph.ADD,"Add field",()->L10n.tr(cancelSelection!=null?"Cancel area selection":"Add field"),this::addField));
        filter=add(new SDropBox<String,Widget>(UI.scale(WIDTH)-add.sz.x-UI.scale(6),UI.scale(170),UI.scale(24)){
            protected List<? extends String> items(){ArrayList<String> options=new ArrayList<>();options.add(ALL);for(CropProfile crop:CropProfile.ALL)options.add(crop.id);return options;}
            protected Widget makeitem(String id,int index,Coord size){
                Widget row=new Widget(size);CropProfile crop=CropProfile.get(id);
                int at=UI.scale(4);
                if(index<0){row.add(new FieldIcon(FieldIcon.Glyph.FILTER),Coord.of(UI.scale(2),(size.y-UI.scale(FieldIcon.SIDE))/2));at=UI.scale(26);}
                row.add(new Label(crop==null?L10n.tr("All crops"):crop.label()),Coord.of(at,UI.scale(2)));return row;
            }
            public void change(String id){super.change(id);cropFilter=ALL.equals(id)?null:id;layoutControls();}
        });
        filter.change(ALL);
        filter.settip("Show only the fields of one crop.");
        selectionHint=add(new Widget(UI.scale(WIDTH,FieldIcon.SIDE)));selectionHint.hide();
        selectionHint.settip("On the map: hold LMB and drag. Esc cancels.");
        selectionTitle=selectionHint.add(new Label(""),UI.scale(4,3));
        cancelIcon=selectionHint.add(new FieldIcon(FieldIcon.Glyph.CLOSE,"Cancel area selection",()->L10n.tr("Cancel area selection"),this::cancelSelection),Coord.of(UI.scale(WIDTH-FieldIcon.SIDE),0));
        list=add(new SListBox<WheatFields.Field,Widget>(UI.scale(WIDTH,ROW*2),UI.scale(ROW)){
            protected List<WheatFields.Field> items(){return shown();}
            public void change(WheatFields.Field field){
                if(field==null)return;
                cancelSelection();super.change(field);
                book.selected=field.id;save();refresh();
            }
            protected void drawbg(GOut g,WheatFields.Field field,int index,Area area){
                FieldTask task=FieldTask.current(gui);
                boolean active=task!=null&&task.field==field;
                g.chcolor(UiTheme.light()?UiTheme.PANEL:active?new Color(44,36,22):index%2==0?new Color(30,27,22):new Color(38,34,27));g.frect2(area.ul,area.br);g.chcolor();
            }
            protected void drawsel(GOut g,WheatFields.Field field,int index,Area area){
                g.chcolor(UiTheme.light()?new Color(116,91,43,35):new Color(53,46,32));g.frect2(area.ul,area.br);
                g.chcolor(174,143,76,190);g.frect(area.ul,Coord.of(UI.scale(2),area.sz().y));g.chcolor();
            }
            protected Widget makeitem(WheatFields.Field field,int index,Coord size){return new FieldRow(this,size,field);}
        });
        empty=add(new Label("No fields saved yet."),UI.scale(8,8));
        refresh();updateStatus();
    }
    private static boolean configured(WheatFields.Field field){return field!=null&&field.land!=null&&field.seeds!=null&&field.straw!=null&&CropProfile.get(field.cropId)!=null;}
    WheatFields book(){return book;}
    private List<WheatFields.Field> shown(){
        if(cropFilter==null)return book.ordered();
        List<WheatFields.Field> shown=new ArrayList<>();
        for(WheatFields.Field field:book.ordered())if(cropFilter.equals(field.cropId))shown.add(field);
        return shown;
    }
    boolean selecting(){return cancelSelection!=null;}
    private void addField(){
        if(cancelSelection!=null){cancelSelection();return;}
        if(busy())return;
        if(book.fields.size()>=100)return;
        int number=book.nextNumber();
        WheatFields.Field field=new WheatFields.Field(Integer.toString(number));field.number=number;
        book.fields.add(field);book.selected=field.id;
        if(cropFilter!=null)filter.change(ALL);
        save();refresh();openEditor();list.display(field);select(0);
    }
    /** The editor is a separate small window, kept beside the list; it follows the selected field. */
    void openEditor(){
        if(FieldTask.current(gui)!=null)return;
        if(editor!=null){editor.show();editor.raise();editor.refresh();return;}
        editor=gui.add(new FieldEditor(this),Coord.z);
        Coord outer=hearthborn.ui.HudInterfaceScale.size(this),size=hearthborn.ui.HudInterfaceScale.size(editor);
        Coord at=Coord.of(c.x+outer.x+UI.scale(8),c.y);
        if(parent!=null){
            if(at.x+size.x>parent.sz.x)at=Coord.of(Math.max(0,c.x-size.x-UI.scale(8)),c.y);
            at=at.min(parent.sz.sub(size).max(Coord.z)).max(Coord.z);
        }
        editor.move(at);editor.raise();
        updateStatus();
    }
    void closeEditor(){
        if(editor==null)return;
        cancelSelection();
        FieldEditor closing=editor;editor=null;
        closing.cropChoice.drop(false);closing.ui.destroy(closing);
        updateStatus();
    }
    boolean editing(){return editor!=null;}
    /** Asks for another number for the selected field, in a small window next to the editor. */
    void promptNumber(){
        WheatFields.Field field=book.selected();
        if(field==null)return;
        if(numberPrompt!=null){numberPrompt.raise();return;}
        Window prompt=gui.add(new Window(UI.scale(200,34),"Field number"){
            public void dispose(){if(numberPrompt==this)numberPrompt=null;super.dispose();}
        },editor==null?c.add(UI.scale(30,30)):editor.c.add(UI.scale(20,20)));
        numberPrompt=prompt;
        TextEntry entry=prompt.add(new TextEntry(UI.scale(110),Integer.toString(field.number)){
            public void activate(String text){if(saveFieldNumber(text))prompt.destroy();}
        },UI.scale(5,5));
        entry.canactivate=true;
        prompt.add(new Button(UI.scale(70),"Save",false,()->{if(saveFieldNumber(entry.text()))prompt.destroy();}),UI.scale(125,3));
        prompt.reqclose(prompt::destroy);
        prompt.pack();prompt.setfocus(entry);gui.setfocus(prompt);
    }
    boolean saveFieldNumber(String text){
        if(cancelSelection!=null||busy()||book.selected()==null)return false;
        try{
            text=text.trim();if(!text.matches("[0-9]+"))throw new NumberFormatException();
            book.renumber(book.selected(),Integer.parseInt(text));save();refresh();list.display(book.selected());
            return true;
        }catch(NumberFormatException invalid){gui.error(L10n.tr("Enter a positive whole field number."));}
        catch(IllegalStateException invalid){gui.error(L10n.tr(invalid.getMessage()));}
        return false;
    }
    private void layoutControls(){
        if(list==null||empty==null||selectionHint==null)return;
        int gap=UI.scale(4);
        int top=Math.max(filter.sz.y,add.sz.y);
        filter.move(Coord.of(0,(top-filter.sz.y)/2));add.move(Coord.of(UI.scale(WIDTH)-add.sz.x,(top-add.sz.y)/2));
        int y=top+gap;
        if(selectionHint.visible()){selectionHint.move(Coord.of(0,y));y+=selectionHint.sz.y+gap;}
        int rows=Math.max(1,Math.min(MAX_ROWS,shown().size()));
        if(book.fields.isEmpty())rows=1;
        if(parent!=null){
            int room=hearthborn.ui.HudInterfaceScale.fromParent(this,parent.sz).y-(sz.y-csz().y)-y-UI.scale(12);
            rows=Math.min(rows,Math.max(1,room/UI.scale(ROW)));
        }
        Coord listSize=Coord.of(UI.scale(WIDTH),UI.scale(rows*ROW));if(!list.sz.equals(listSize))list.resize(listSize);
        list.move(Coord.of(0,y));empty.move(Coord.of(UI.scale(8),y+UI.scale(8)));
        empty.show(book.fields.isEmpty());
        if(!contentsz().equals(csz())){
            pack();
            if(parent!=null)move(c.min(parent.sz.sub(hearthborn.ui.HudInterfaceScale.size(this)).max(Coord.z)).max(Coord.z));
        }
    }
    protected void added(){super.added();layoutControls();}
    public void presize(){layoutControls();}
    boolean busy(){return busy(true);}
    boolean busy(boolean report){if(ScriptRunner.running(gui)){if(report)gui.error(L10n.tr("Stop the current script first."));return true;}return false;}
    private void launch(WheatFields.Field field,boolean harvestOnly){
        if(busy()||cancelSelection!=null)return;
        list.change(field);
        if(!configured(field)){openEditor();return;}
        closeEditor();FieldTask.launch(gui,book,field,harvestOnly);updateStatus();
    }
    private static long doubleClickInterval(){
        try{Object value=java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
            if(value instanceof Number)return Math.max(1,((Number)value).longValue());
        }catch(java.awt.HeadlessException ignored){}
        return 500;
    }
    private void walkTo(WheatFields.Field field){
        if(cancelSelection!=null||busy())return;
        if(gui.map==null||gui.map.player()==null||gui.ui.sess==null){gui.error(L10n.tr("The map is not ready yet."));return;}
        Area area=field.land==null?null:field.land.resolve(gui.ui.sess.glob.map);
        if(area==null){gui.error(L10n.tr("This field is not loaded. Move closer to it first."));return;}
        Coord player=gui.map.player().rc.floor(MCache.tilesz);
        if(area.contains(player)){gui.showNotice(L10n.tr("You are already inside this field."),Color.WHITE);return;}
        Coord tile=Coord.of(Math.max(area.ul.x,Math.min(player.x,area.br.x-1)),Math.max(area.ul.y,Math.min(player.y,area.br.y-1)));
        Coord2d goal=new Coord2d(tile).add(.5,.5).mul(MCache.tilesz);
        if(gui.map.player().rc.dist(goal)>440){gui.error(L10n.tr("This field is outside local walking range. Move closer."));return;}
        Object[] click={Coord.z,goal.floor(OCache.posres),1,Walking.enabled()?0:UI.MOD_CTRL};
        // This gesture explicitly requests a route even if automatic ground routing is off.
        // Keep Walking's combat, cursor, mount and terrain guards; never fall back to a blind click.
        if(!Walking.start(gui.map,goal,click))gui.error(L10n.tr("Cannot start a route to this field right now."));
    }
    private static String fit(String text,int width){
        if(Text.std.strsize(text).x<=width)return text;
        while(!text.isEmpty()&&Text.std.strsize(text+"...").x>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
        return text+"...";
    }
    private final class FieldRow extends SListWidget.ItemWidget<WheatFields.Field> {
        private final Label number,title,summary;
        private final FieldIcon process,harvest,edit,pause,stop;
        private final Button configure;
        private int textLeft,textRight;
        private int theme=-1;
        private String controls="";
        FieldRow(SListWidget<WheatFields.Field,?> owner,Coord size,WheatFields.Field field){
            super(owner,size,field);
            number=add(new Label(Integer.toString(field.number)),UI.scale(8,0));
            title=add(new Label(""),UI.scale(40,0));summary=add(new Label(""),UI.scale(40,0));
            int side=UI.scale(FieldIcon.SIDE),right=size.x-UI.scale(8),gap=UI.scale(5),y=(size.y-side)/2;
            edit=add(new FieldIcon(FieldIcon.Glyph.EDIT,"Edit",()->L10n.text("button","Edit"),()->{
                if(busy()||cancelSelection!=null)return;
                boolean collapse=list.sel==field&&editor!=null;
                if(collapse)closeEditor();else{list.change(field);openEditor();}
            }),Coord.of(right-side,y));
            harvest=add(new FieldIcon(FieldIcon.Glyph.SWEEP,"Harvest only",()->L10n.tr("Harvest only"),()->launch(field,true)),Coord.of(edit.c.x-gap-side,y));
            process=add(new FieldIcon(FieldIcon.Glyph.PLAY,"Process",()->L10n.tr("Process"),()->launch(field,false)),Coord.of(harvest.c.x-gap-side,y));
            stop=add(new FieldIcon(FieldIcon.Glyph.STOP,"Stop",()->L10n.text("button","Stop"),()->{
                FieldTask task=FieldTask.current(gui);
                if(task!=null&&task.field==field){ScriptRunner.cancel(gui);updateStatus();}
            }),Coord.of(edit.c.x,y));
            pause=add(new FieldIcon(FieldIcon.Glyph.PAUSE,"Pause",()->{FieldTask task=FieldTask.current(gui);return L10n.tr(task!=null&&task.paused?"Continue":"Pause");},()->{
                FieldTask task=FieldTask.current(gui);
                if(task!=null&&task.field==field){task.paused=!task.paused;updateStatus();}
            }),Coord.of(harvest.c.x,y));
            configure=add(new Button(UI.scale(104),"Configure",false,()->{
                if(busy()||cancelSelection!=null)return;
                list.change(field);openEditor();
            }),Coord.z);
            configure.move(Coord.of(right-configure.sz.x,(size.y-configure.sz.y)/2));
            textLeft=title.c.x;updateRow();
        }
        private void updateRow(){
            FieldTask task=FieldTask.current(gui);boolean active=task!=null&&task.field==item,ready=configured(item);
            textRight=(active?pause.c.x:ready?process.c.x:configure.c.x)-UI.scale(8);
            int width=Math.max(1,textRight-textLeft);
            CropProfile crop=CropProfile.get(item.cropId);
            String name=ready||active?(crop==null?L10n.tr("Choose crop"):crop.label())+(item.land==null?"":" "+item.land.width+"×"+item.land.height):L10n.tr("Not configured");
            String status=active?(task.waitingForQuality()?L10n.tr("Waiting for planting confirmation"):(task.paused?L10n.tr("Paused")+" · ":"")+task.progress()):"";
            String shown=fit(name,width),line=fit(status,width);
            if(!title.texts.equals(shown))title.settext(shown);if(!summary.texts.equals(line))summary.settext(line);
            number.settext(Integer.toString(item.number));
            if(theme!=UiTheme.generation()){
                theme=UiTheme.generation();
                summary.setcolor(UiTheme.light()?UiTheme.HEADER:new Color(160,153,131));number.setcolor(UiTheme.light()?UiTheme.HEADER:new Color(176,150,92));
            }
            title.setcolor(!ready&&!active?(UiTheme.light()?UiTheme.HEADER:new Color(160,153,131)):UiTheme.light()?UiTheme.INK:Color.WHITE);
            Coord at=Coord.of(textLeft,active?UI.scale(2):(sz.y-title.sz.y)/2);
            if(!title.c.equals(at))title.move(at);
            number.move(Coord.of(UI.scale(8),(sz.y-number.sz.y)/2));
            summary.move(Coord.of(textLeft,UI.scale(2)+title.sz.y));
            String next=active+"|"+(active&&task.paused)+"|"+(active&&task.waitingForQuality())+"|"+(editor!=null&&list.sel==item)+"|"+ScriptRunner.running(gui)+"|"+(cancelSelection!=null)+"|"+ready;
            if(next.equals(controls))return;controls=next;
            process.show(!active&&ready);harvest.show(!active&&ready);edit.show(!active&&ready);
            pause.show(active);stop.show(active);summary.show(active);configure.show(!active&&!ready);
            boolean blocked=cancelSelection!=null||ScriptRunner.running(gui)&&!active;
            process.disable(blocked);harvest.disable(blocked);
            edit.disable(ScriptRunner.running(gui)||cancelSelection!=null);
            pause.disable(active&&task.waitingForQuality());
            configure.disable(ScriptRunner.running(gui)||cancelSelection!=null);
        }
        public void tick(double dt){super.tick(dt);updateRow();}
        public boolean mousedown(MouseDownEvent event){
            if(event.b!=1||event.c.x>=textRight||ui.modflags()!=0)lastClickedField=null;
            return super.mousedown(event);
        }
        protected boolean clicked(MouseDownEvent event){
            double now=Utils.rtime();
            boolean twice=ui.modflags()==0&&item.id.equals(lastClickedField)&&now-lastClickedAt<=doubleClickInterval()/1000.0;
            lastClickedField=twice||ui.modflags()!=0?null:item.id;lastClickedAt=now;
            super.clicked(event);
            if(twice)walkTo(item);
            return true;
        }
        public Object tooltip(Coord at,Widget previous){
            FieldTask task=FieldTask.current(gui);
            return item.summary()+"\n"+(task!=null&&task.field==item?task.status():FieldTask.describe(item))+(task!=null&&task.field==item&&!task.reserveStatus.isEmpty()?"\n"+task.reserveStatus:"")
                +"\n"+L10n.tr("Double-click the field number to walk there with pathfinding. The field must be loaded.");
        }
    }
    void chooseCrop(String id){
        WheatFields.Field field=book.selected();if(field==null||busy())return;
        if(field.phase!=WheatFields.Phase.READY&&field.phase!=WheatFields.Phase.DONE){gui.error(L10n.tr("Finish this field cycle before changing its crop."));return;}
        try{
            CropProfile.Detection detected=null;
            // A manual planting choice does not require map or plant resources.
            if(CropProfile.AUTO.equals(id)&&field.land!=null){Area area=field.land.resolve(gui.ui.sess.glob.map);if(area==null)throw new Loading();detected=CropProfile.inspect(gui,area);}
            field.cropPinned=!CropProfile.AUTO.equals(id);
            field.cropId=field.cropPinned?id:detected!=null&&detected.crop!=null?detected.crop.id:CropProfile.AUTO;
            field.reset();
            if(field.cropPinned&&pendingSelection!=null&&pendingSelection.field==field&&pendingSelection.kind==0&&pendingSelection.saved)finishSelection();
            save();refresh();
            if(detected!=null&&detected.mixed)warnMixed();
        }catch(Loading pending){gui.showErrorNotice(L10n.tr("Crop detection is waiting for map or plant data. Choose the planting crop manually or try detection again."));}
        catch(IllegalStateException failure){gui.error(L10n.tr(failure.getMessage()));}
    }
    private void warnMixed(){
        String text=L10n.tr("The selected field contains mixed crops. Choose the crop to plant. Existing plants will be kept; only empty tiles will be planted.");
        java.awt.Color color=new java.awt.Color(235,195,95);gui.showNotice(text,color);
        if(gui.syslog!=null)gui.syslog.append(text,color);
    }
    private void save(){try{book.save(gui);}catch(IllegalStateException failure){gui.error(L10n.tr(failure.getMessage()));}}
    void preview(int kind){
        WheatFields.Field field=book.selected();
        highlight.show(field==null?null:kind==0?field.land:kind==1?field.seeds:field.straw);
    }
    private void refresh(){
        WheatFields.Field field=book.selected();
        list.sel=field;
        if(editor!=null)editor.refresh();
        preview(0);
        updateStatus();
    }
    void select(int kind){
        WheatFields.Field field=book.selected();if(field==null||busy()||gui.map==null)return;
        cancelSelection();
        selectionKind=kind;
        try{cancelSelection=gui.map.selectLocalArea(area->{
            pendingSelection=new PendingSelection(field,area,kind);
            cancelSelection=()->{pendingSelection=null;selectionKind=-1;highlight.suspend(false);updateStatus();};
            pollSelection();
        },()->{cancelSelection=null;selectionKind=-1;highlight.suspend(false);updateStatus();});
        highlight.suspend(true);
        updateStatus();
        gui.showNotice(L10n.tr(kind==0?"Select the field on the map. The crop will be detected from its plants.":kind==1?"Select the area containing the seed cupboards or chests.":"Select the harvest stockpile area on the map."),java.awt.Color.WHITE);
        }catch(IllegalStateException failure){selectionKind=-1;updateStatus();gui.error(L10n.tr(failure.getMessage()));}
    }
    /** Keep the completed gesture while map grids or crop resources arrive. */
    private void pollSelection(){
        PendingSelection pending=pendingSelection;
        if(pending==null||Utils.rtime()<pending.retry)return;
        if(gui.map!=pending.map||!java.util.Objects.equals(pending.scope,SavedArea.key(gui,"wheat-fields"))||!book.fields.contains(pending.field)||busy()){
            cancelSelection();return;
        }
        pending.retry=Utils.rtime()+.25;
        try{
            if(!pending.saved){
                SavedArea saved=WheatFields.capture(gui,pending.area,pending.kind==0);
                if(pending.kind==0){pending.field.land=saved;pending.field.reset();}
                else if(pending.kind==1)pending.field.seeds=saved;else pending.field.straw=saved;
                book.save(gui);pending.saved=true;refresh();preview(pending.kind);
            }
            if(pending.kind==0&&!pending.field.cropPinned){
                Area live=pending.field.land.resolve(gui.ui.sess.glob.map);if(live==null)throw new Loading();
                CropProfile.Detection detected=CropProfile.inspect(gui,live);
                if(!pending.field.cropPinned&&detected.crop!=null){pending.field.cropId=detected.crop.id;book.save(gui);}
                finishSelection();refresh();preview(pending.kind);
                if(detected.mixed)warnMixed();
            }else finishSelection();
        }catch(Loading loading){
            if(Utils.rtime()>=pending.until){
                finishSelection();
                if(pending.saved){
                    String text=L10n.tr("Field boundaries saved. Crop data is still loading; choose the planting crop manually.");
                    gui.showNotice(text,Color.WHITE);if(gui.syslog!=null)gui.syslog.append(text,Color.WHITE);
                }else gui.showErrorNotice(L10n.tr("The selected map area did not load. Move closer and select it again."));
            }
        }catch(IllegalStateException failure){finishSelection();refresh();gui.showErrorNotice(L10n.tr(failure.getMessage()));}
        updateStatus();
    }
    private void finishSelection(){pendingSelection=null;cancelSelection=null;selectionKind=-1;highlight.suspend(false);updateStatus();}
    private void cancelSelection(){if(cancelSelection!=null){cancelSelection.run();cancelSelection=null;}if(filter!=null)updateStatus();}
    public void tick(double dt){
        super.tick(dt);pollSelection();highlight.tick();updateStatus();
        if(editor!=null)editor.refresh();
    }
    private void updateStatus(){
        if(selectionHint==null||list==null)return;
        WheatFields.Field field=book.selected();
        boolean busy=ScriptRunner.running(gui),selecting=cancelSelection!=null;
        String controls=busy+"|"+selecting+"|"+selectionKind+"|"+(pendingSelection==null?"":pendingSelection.saved)+"|"+(editor!=null)+"|"+UiTheme.generation()+"|"+(field==null?"":field.id)+book.fields.size()+"|"+cropFilter;
        if(controls.equals(lastControls))return;lastControls=controls;
        selectionHint.show(selecting);
        if(selecting)selectionTitle.settext(L10n.tr(pendingSelection!=null?(pendingSelection.saved?"Field saved; waiting for crop data":"Loading the selected area"):selectionKind==0?"Waiting for field boundaries":selectionKind==1?"Waiting for planting reserve area":"Waiting for harvest stockpile area"));
        selectionTitle.setcolor(UiTheme.entryText(false));
        add.disable(busy&&!selecting);
        // The editor would cover the field being outlined, so it steps aside until the selection ends.
        if(editor!=null){editor.show(!selecting&&!busy);editor.refresh();}
        layoutControls();
    }
    public void reqclose(){closeEditor();if(numberPrompt!=null)numberPrompt.destroy();cancelSelection();filter.drop(false);highlight.clear();ui.destroy(this);}
    public void dispose(){cancelSelection();filter.drop(false);highlight.clear();super.dispose();}
}
