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

package hearthborn.inventory;

import haven.*;
import hearthborn.L10n;

/**
 * One compact component for take amounts, quality thresholds and destructive confirmation.
 *
 * @author Jagy2014
 */
final class OverviewDialog extends Window {
    enum Mode {TAKE,DROP_BELOW,DROP_GROUP,DROP_SELECTION,DROP_ITEM}
    final ContainerOverview overview;final String key;final Mode mode;final boolean worst;
    final TextEntry input;final Label preview,notice;final OverviewTheme.FlatButton ok;
    ContainerDiscard.Plan plan;boolean closed;double elapsed;
    static OverviewDialog open(ContainerOverview overview,String key,Mode mode,boolean worst,Coord at){
        if(overview.actionRow(key)==null)return null;
        OverviewDialog dialog=new OverviewDialog(overview,key,mode,worst);
        UI ui=overview.ui;ui.root.add(dialog,at);dialog.move(at.min(ui.root.sz.sub(dialog.sz).max(Coord.z)).max(Coord.z));
        ui.root.setfocus(dialog);if(dialog.input!=null){dialog.setfocus(dialog.input);dialog.input.buf.select(0,dialog.input.buf.length());}
        return dialog;
    }
    OverviewDialog(ContainerOverview overview,String key,Mode mode,boolean worst){
        super(Coord.z,mode==Mode.TAKE?"Take N":mode==Mode.DROP_BELOW?"Drop below quality":mode==Mode.DROP_SELECTION?"Drop items of this quality":mode==Mode.DROP_ITEM?"Drop selected items":"Drop selected group");
        this.overview=overview;this.key=key;this.mode=mode;this.worst=worst;
        ContainerOverview.Row row=overview.find(key);
        add(new Label(OverviewTheme.fit(row.group.name,UI.scale(340),Text.std)));
        int y=UI.scale(28);
        if(mode==Mode.TAKE||mode==Mode.DROP_BELOW){
            input=add(new OverviewTheme.Entry(340,OverviewSettings.last(mode==Mode.DROP_BELOW)){
                protected void changed(){super.changed();if(ok!=null)refreshPreview();}
                public void activate(String text){confirm();}
            },Coord.of(0,y));y+=input.sz.y+UI.scale(10);
        }else input=null;
        preview=add(new Label(""),Coord.of(0,y));y+=UI.scale(24);
        notice=add(new Label(""),Coord.of(0,y));y+=UI.scale(28);
        ok=add(new OverviewTheme.FlatButton(166,mode==Mode.TAKE?"OK":"Confirm discard",mode!=Mode.TAKE,this::confirm),Coord.of(0,y));
        add(new OverviewTheme.FlatButton(166,"Cancel",false,this::dismiss),Coord.of(UI.scale(174),y));
        pack();refreshPreview();
    }
    static Double number(String text,boolean quality){
        String value=text.trim();if(!(quality?value.matches("[0-9]+(?:[.,][0-9]+)?"):value.matches("[1-9][0-9]*")))return null;
        try{double n=Double.parseDouble(value.replace(',','.'));if(!Double.isFinite(n)||n>Integer.MAX_VALUE)return null;return n;}catch(NumberFormatException invalid){return null;}
    }
    private ContainerOverview.Row current(){
        overview.refresh();if(!overview.usable()){notice.settext(L10n.tr("The source container was closed."));return null;}
        GameUI gui=overview.source().getparent(GameUI.class);
        if(gui==null||gui.vhand!=null||BagTransfers.running(gui)||overview.discarding()){notice.settext(L10n.tr("Wait for the current transfer to finish."));return null;}
        if(ContainerOverview.snapshot(overview.source()).loading){notice.settext(L10n.tr("Some items are still loading."));return null;}
        ContainerOverview.Row row=overview.find(key);
        if(row!=null)for(WItem owner:row.group.owners())if(hearthborn.QualityOverlay.stacked(owner.item)&&(owner.item.contents==null||owner.item.contents.children(GItem.class).isEmpty())){notice.settext(L10n.tr("Stack contents are still loading."));return null;}
        return row;
    }
    void refreshPreview(){
        if(closed)return;notice.settext("");ContainerOverview.Row row=current();ok.disable(true);
        Double n=input==null?null:number(input.text(),mode==Mode.DROP_BELOW);
        if(row==null){preview.settext(L10n.tr("No matching items."));plan=null;return;}
        if(input!=null&&n==null){preview.settext(L10n.tr("Enter a valid number."));plan=null;return;}
        if(mode==Mode.TAKE){int count=overview.previewTake(row,n.intValue(),worst);preview.settext(L10n.format("Will take: %d items",count));ok.disable(count==0);return;}
        plan=ContainerDiscard.Plan.make(row.group,n);
        preview.settext(plan.count==0?L10n.tr("Nothing to drop."):mode==Mode.DROP_BELOW?L10n.format("Will drop: %d items in %d stacks",plan.count,plan.stacks):L10n.format("Will drop: %d items",plan.count));
        ok.disable(plan.count==0);
    }
    void confirm(){
        if(closed||parent==null)return;
        ContainerDiscard.Plan shown=plan;ContainerOverview.Row row=current();if(row==null)return;
        if(mode==Mode.TAKE){
            Double n=number(input.text(),false);if(n==null||overview.previewTake(row,n.intValue(),worst)==0)return;
            OverviewSettings.last(false,input.text());dismiss();overview.move(key,n.intValue(),worst,false);return;
        }
        Double limit=mode==Mode.DROP_BELOW?number(input.text(),true):null;if(mode==Mode.DROP_BELOW&&limit==null)return;
        ContainerDiscard.Plan fresh=ContainerDiscard.Plan.make(row.group,limit);
        if(fresh.count==0)return;
        if(shown==null||!shown.same(fresh)){refreshPreview();notice.settext(L10n.tr("Contents changed. Review the selection."));return;}
        if(mode==Mode.DROP_BELOW)OverviewSettings.last(true,input.text());
        dismiss();execute(overview,fresh);
    }
    static void execute(ContainerOverview overview,ContainerDiscard.Plan plan){
        for(GItem item:plan.targets)item.wdgmsg("drop",Coord.z,1);overview.discarded(plan);
    }
    void dismiss(){if(!closed){closed=true;reqdestroy();}}
    public void reqclose(){dismiss();}
    public boolean keydown(KeyDownEvent e){if(e.code==java.awt.event.KeyEvent.VK_ESCAPE){dismiss();return true;}if(e.code==java.awt.event.KeyEvent.VK_ENTER){confirm();return true;}return super.keydown(e);}
    public void tick(double dt){super.tick(dt);if((elapsed+=dt)>=.2){elapsed=0;refreshPreview();}}
}
