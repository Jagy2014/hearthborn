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
import hearthborn.world.ObjectNames;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The small window for one field: its crop and its three areas. The field list opens it and keeps all the work.
 *
 * @author Jagy2014
 */
final class FieldEditor extends Window implements ObjectNames.Titled {
    private static final int WIDTH=300,ROW=28;
    private final FieldWindow owner;
    final SDropBox<String,Widget> cropChoice;
    final Label land,seeds,straw;
    final FieldIcon pickLand,pickSeeds,pickStraw;
    private final Label seedsName,hint;
    private final Button done;
    private final CheckBox ask;
    boolean refreshing;
    private int theme=-1;
    private String lastKey="";

    FieldEditor(FieldWindow owner){
        super(Coord.z,"Field");this.owner=owner;
        Label cropName=add(new Label("Crop"),Coord.of(0,center(0,UI.scale(16))));
        cropChoice=add(new SDropBox<String,Widget>(UI.scale(180),UI.scale(150),UI.scale(24)){
            protected List<? extends String> items(){ArrayList<String> options=new ArrayList<>();options.add(CropProfile.AUTO);for(CropProfile crop:CropProfile.ALL)options.add(crop.id);return options;}
            protected Widget makeitem(String id,int index,Coord size){Widget row=new Widget(size);CropProfile crop=CropProfile.get(id);row.add(new Label(crop==null?L10n.tr("Detect from plants"):crop.label()),UI.scale(4,2));return row;}
            public void change(String id){if(refreshing){super.change(id);return;}owner.chooseCrop(id);}
        },Coord.of(UI.scale(WIDTH-180),UI.scale(ROW-24)/2));
        cropName.settip("Choose the planting crop manually, or detect it from a single-crop field. Mixed fields keep existing plants and fill empty tiles.");
        add(new Label("Field"),Coord.of(0,center(1,UI.scale(16))));
        seedsName=add(new Label("Seeds"),Coord.of(0,center(2,UI.scale(16))));
        add(new Label("Yield"),Coord.of(0,center(3,UI.scale(16))));
        land=add(areaLabel(0),Coord.z);seeds=add(areaLabel(1),Coord.z);straw=add(areaLabel(2),Coord.z);
        pickLand=add(new FieldIcon(FieldIcon.Glyph.SELECT,"Select field",()->L10n.tr("Change the field boundaries while the script is stopped. Existing plants stay; saved cycle progress resets after selecting the new area."),()->owner.select(0)),Coord.of(UI.scale(WIDTH)-UI.scale(FieldIcon.SIDE),icon(1)));
        pickSeeds=add(new FieldIcon(FieldIcon.Glyph.SELECT,"Seed storage area",()->L10n.tr("Select the area containing the seed cupboards or chests."),()->owner.select(1)),Coord.of(UI.scale(WIDTH)-UI.scale(FieldIcon.SIDE),icon(2)));
        pickStraw=add(new FieldIcon(FieldIcon.Glyph.SELECT,"Harvest stockpile area",()->L10n.tr("Select the harvest stockpile area on the map."),()->owner.select(2)),Coord.of(UI.scale(WIDTH)-UI.scale(FieldIcon.SIDE),icon(3)));
        ask=add(new CheckBox("Ask about seed quality"),Coord.of(0,UI.scale(ROW*4+6)));
        ask.state(FieldQualityCheck::confirmation).set(FieldQualityCheck::confirmation);
        ask.settip("Ask before planting below the quality roster record. Applies to all fields. When off, better recorded quality is reported only in System chat.");
        int bottom=UI.scale(ROW*5+10);
        done=add(new Button(UI.scale(76),"Done",false,owner::closeEditor),Coord.of(UI.scale(WIDTH-76),bottom));
        hint=add(new Label("Select an area: hold LMB and drag · Esc cancels.",UI.scale(WIDTH-88)),Coord.of(0,bottom+UI.scale(4)));
        refresh();pack();
    }
    private static int center(int row,int height){return UI.scale(ROW)*row+(UI.scale(ROW)-height)/2;}
    private static int icon(int row){return UI.scale(ROW)*row+(UI.scale(ROW)-UI.scale(FieldIcon.SIDE))/2;}
    private Label areaLabel(int kind){
        Label label=new Label(""){
            public boolean mousedown(MouseDownEvent event){if(event.b==1&&!owner.selecting()){owner.preview(kind);return true;}return super.mousedown(event);}
        };
        label.settip("Click to highlight this area on the ground.");return label;
    }
    private static String size(SavedArea area){return area.width+"×"+area.height;}
    private void status(Label label,SavedArea area,FieldIcon pick){
        boolean set=area!=null;
        String text=set?size(area):L10n.tr("not set");
        if(!label.texts.equals(text))label.settext(text);
        label.setcolor(set?(UiTheme.light()?UiTheme.BONUS:new Color(120,205,130)):UiTheme.light()?UiTheme.HEADER:new Color(150,142,122));
        label.move(Coord.of(pick.c.x-UI.scale(8)-label.sz.x,pick.c.y+(pick.sz.y-label.sz.y)/2));
    }
    /** Brings every row up to date with the field the list has selected. */
    void refresh(){
        WheatFields.Field field=owner.book().selected();
        String id=field==null?CropProfile.AUTO:field.cropId;
        String key=(field==null?"":field.id+"|"+field.number+"|"+field.cropId+"|"+field.land+"|"+field.seeds+"|"+field.straw)+"|"+owner.busy(false)+"|"+owner.selecting()+"|"+UiTheme.generation()+"|"+FieldQualityCheck.confirmation();
        if(key.equals(lastKey)&&java.util.Objects.equals(cropChoice.sel,id))return;
        lastKey=key;
        if(!java.util.Objects.equals(cropChoice.sel,id)){refreshing=true;try{cropChoice.change(id);}finally{refreshing=false;}}
        CropProfile profile=field==null?null:CropProfile.get(field.cropId);
        boolean roots=profile!=null&&profile.rootCrop;
        String name=roots?"Planting reserve":"Seeds";
        if(!seedsName.texts.equals(name))seedsName.settext(name);
        String tip=L10n.tr("Click to highlight this area on the ground.")+"\n"+L10n.tr(roots
            ?"Root crops: keep the best roots for one planting in these cupboards. Surplus goes to harvest stockpiles."
            :"Seed storage: cupboards or chests; qualities stay separate.");
        seeds.settip(tip,false);
        status(land,field==null?null:field.land,pickLand);status(seeds,field==null?null:field.seeds,pickSeeds);status(straw,field==null?null:field.straw,pickStraw);
        boolean off=owner.busy(false)||owner.selecting()||field==null;
        for(FieldIcon pick:new FieldIcon[]{pickLand,pickSeeds,pickStraw})pick.disable(off);
        done.disable(false);
        if(theme!=UiTheme.generation()){theme=UiTheme.generation();hint.setcolor(UiTheme.light()?UiTheme.HEADER:new Color(150,142,122));}
    }
    public String title(){WheatFields.Field field=owner.book().selected();return field==null?L10n.tr("Field"):L10n.format("Field %d",field.number);}
    public void rename(){owner.promptNumber();}
    public String renameTip(){return L10n.tr("Change the field number");}
    public void reqclose(){owner.closeEditor();}
    public void dispose(){cropChoice.drop(false);super.dispose();}
}
