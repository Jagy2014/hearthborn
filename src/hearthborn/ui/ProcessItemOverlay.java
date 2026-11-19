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
import hearthborn.timers.ObjectTimers;
import java.awt.Color;
import java.awt.image.BufferedImage;
import hearthborn.QualityOverlay;
import java.util.*;
/**
 * Baking marks stay with the food. Quality is drawn separately, like the equipment strip.
 *
 * @author Jagy2014
 */
public final class ProcessItemOverlay implements Disposable {
    private static final Text.Foundry TIME=new Text.Foundry(Text.sans,9).aa(true);
    private static final int PAD=3;
    private final Map<String,Tex> labels=new HashMap<>();
    /** Undo the inventory zoom so a caption keeps its own pixel size. */
    public static Coord fitted(Coord size,double view){
        if(!(view>0))return size;
        return Coord.of(Math.max(1,(int)Math.round(size.x/view)),Math.max(1,(int)Math.round(size.y/view)));
    }
    /** The timer gets the same thick outline and glow as the quality and the percent, and the same zoom correction. */
    private static BufferedImage styled(BufferedImage text){return QualityOverlay.styled(text);}
    private static double view(WItem item){return hearthborn.inventory.InventoryScale.zoomOf(item);}
    /** Time and percent share one face: the small sans with the thick outline and glow. */
    private Tex label(String key,String text){return labels.computeIfAbsent(key,k->new TexI(styled(TIME.render(text,Color.WHITE).img)));}
    public boolean draw(GOut g,WItem view,Coord size,double meter){
        if(!ObjectTimers.furnaceItem(view))return false;
        boolean percent=meter>0&&ProductionSettings.furnacePercent();
        int left=ObjectTimers.itemRemaining(view);
        if(!percent&&left<0)return false;
        if(labels.size()>12)dispose();
        double fit=EquipmentStrip.SCALE/view(view);
        if(left>=0){
            String clock=hearthborn.cheese.CheeseCountdown.format(left);
            Tex time=label("t:"+clock,clock);
            // The outline and glow leave a few empty pixels under the digits; sink them so the digits touch the cell floor.
            Coord at=Coord.of(size.x/2,size.y+(int)Math.round(PAD*fit));
            if(fit==1)g.aimage(time,at,.5,1);
            else g.aimage(time,at,.5,1,time.sz().mul(fit));
        }
        if(percent){
            Tex text=label("p:"+Math.round(meter*100),Math.round(meter*100)+"%");
            g.aimage(text,g.sz().div(2),.5,.5,text.sz().mul(fit));
        }
        return true;
    }
    public void dispose(){for(Tex texture:labels.values())texture.dispose();labels.clear();}
}
