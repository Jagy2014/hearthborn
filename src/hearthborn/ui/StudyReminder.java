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
import hearthborn.L10n;
import java.util.*;

/**
 * Free-study-space reminder, updated even while the character sheet is closed.
 *
 * @author Jagy2014
 */
public final class StudyReminder extends Buff {
    private static final Map<GameUI,StudyReminder> shown=new WeakHashMap<>();
    private final GameUI gui;
    private int free;
    private StudyReminder(GameUI gui,int free){super(()->brainIcon());this.gui=gui;this.free=free;}
    private static Resource brain;
    static synchronized Resource brainIcon(){
        if(brain!=null)return brain;
        Resource.Virtual resource=new Resource.Virtual(Resource.local(),"hearthborn/study-brain",1);
        try(java.io.InputStream in=StudyReminder.class.getResourceAsStream("/hearthborn/assets/icons/study-brain.png")){
            if(in==null)throw new java.io.IOException("Missing study icon");
            java.io.ByteArrayOutputStream png=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(javax.imageio.ImageIO.read(in),"png",png);
            MessageBuf image=new MessageBuf();
            image.addint16((short)0).addint16((short)0).adduint8(4).addint16((short)-1).addint16((short)0).addint16((short)0);
            image.addstring("scale").adduint8(4).addfloat32(8f).addstring("").addbytes(png.toByteArray());
            resource.add(resource.new Image(new MessageBuf(image.fin())));
        }catch(java.io.IOException error){throw new IllegalStateException("Could not load study icon",error);}
        brain=resource;return resource;
    }
    public static boolean enabled(){return Utils.getprefb("hearthborn/study/free-slots-buff",false);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/study/free-slots-buff",value);}
    public static int freeSlots(Inventory inventory){
        boolean[] occupied=new boolean[inventory.isz.x*inventory.isz.y];
        if(inventory.sqmask!=null)System.arraycopy(inventory.sqmask,0,occupied,0,Math.min(occupied.length,inventory.sqmask.length));
        for(WItem item:inventory.children(WItem.class)){
            Coord at=item.c.sub(1,1).div(Inventory.sqsz),size=item.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
            for(int y=Math.max(0,at.y);y<Math.min(inventory.isz.y,at.y+size.y);y++)
                for(int x=Math.max(0,at.x);x<Math.min(inventory.isz.x,at.x+size.x);x++)occupied[y*inventory.isz.x+x]=true;
        }
        int free=0;for(boolean taken:occupied)if(!taken)free++;return free;
    }
    public static void tick(GameUI gui){
        if(gui.buffs==null)return;
        int free=0;
        if(enabled())for(Widget widget:StudyProgress.registered(gui))if(widget instanceof Inventory)free+=freeSlots((Inventory)widget);
        StudyReminder reminder=shown.get(gui);
        if(free==0){if(reminder!=null){shown.remove(gui);reminder.destroy();}return;}
        if(reminder==null||reminder.parent==null){reminder=new StudyReminder(gui,free);gui.buffs.addchild(reminder);shown.put(gui,reminder);}
        reminder.free=free;
    }
    public static void dispose(GameUI gui){StudyReminder reminder=shown.remove(gui);if(reminder!=null&&reminder.parent!=null)reminder.destroy();}
    public Object tooltip(Coord c,Widget previous){
        int used=0;boolean loading=false;
        for(Widget study:StudyProgress.registered(gui))if(study instanceof Inventory)
            for(GItem item:study.children(GItem.class))try{
                haven.resutil.Curiosity curiosity=ItemInfo.find(haven.resutil.Curiosity.class,item.info());
                if(curiosity!=null)used+=curiosity.mw;
            }catch(Loading pending){loading=true;}
        int total=gui.ui.sess.glob.getcattr("int").comp;
        return L10n.format("Free study slots: %d",free)+"\n"+L10n.format("Available attention: %d / %d",Math.max(0,total-used),total)+(loading?"\n"+L10n.tr("Study information is still loading."):"")+"\n"+L10n.tr("Left-click to open the study window.");
    }
    public int layoutOrder(){return -3;}
    public boolean mousedown(MouseDownEvent event){if(event.b!=1)return false;gui.toggleStudy();return true;}
}
