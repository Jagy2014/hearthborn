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

package hearthborn.world;

import haven.*;
import hearthborn.util.GameObjects;
import java.awt.Color;

/**
 * Reads server-owned buddy attributes without depending on their world-name rendering cache.
 *
 * @author Jagy2014
 */
public final class PlayerRelations {
    public enum Kind {
        FRIEND("Friends","Friend",new Color(93,230,107)),
        VILLAGER("Village residents","Villager",new Color(88,195,255)),
        KNOWN("Known players","KnownPlayer",new Color(246,200,85)),
        ENEMY("Unknown or hostile players","Enemy",new Color(255,91,80));
        public final String title,speech;public final Color color;
        Kind(String title,String speech,Color color){this.title=title;this.speech=speech;this.color=color;}
    }
    public static final class Info {
        public final Kind kind;public final String name;public final boolean pending;
        Info(Kind kind,String name,boolean pending){this.kind=kind;this.name=name;this.pending=pending;}
    }
    public static boolean player(Gob gob){
        if(!"gfx/borka/body".equals(GameObjects.resourceName(gob)))return false;
        GobIcon icon=gob.getattr(GobIcon.class);
        return icon==null||icon.sdt.length==0||icon.sdt[0]==0||!icon.res.get().name.equals("gfx/hud/mmap/plo");
    }
    /** The starting-area wizard, carved character statues, and mannequins use a player-like body. */
    public static boolean fixture(Gob gob){
        if(gob==null)return false;
        String resource=GameObjects.resourceName(gob);
        if(mannequin(resource)||wooden(gob))return true;
        Drawable drawable=gob.getattr(Drawable.class);
        if(!(drawable instanceof Composite))return false;
        Composite body=(Composite)drawable;
        // A standing statue keeps an ordinary pose and a carving or mannequin model. A knocked carving lies in deadskeletonpose.
        if(body.poseContains("carving")||body.poseContains("skeleton")||statue(body.nmod))return true;
        Composited model=body.comp;
        if(model!=null&&statue(model.cmod))return true;
        if(model==null||model.cequ==null)return false;
        for(Composited.ED piece:model.cequ){
            if(piece==null||piece.res==null||piece.res.res==null)continue;
            if(statue(piece.res.res.get().name))return true;
        }
        return false;
    }
    private static boolean wooden(Gob gob){
        if(gob.ols!=null)for(Gob.Overlay overlay:gob.ols)if(mannequin(GameObjects.overlayName(overlay)))return true;
        GAttrib materials=null;
        for(GAttrib attr:gob.attributes())if("haven.res.lib.vmat.AttrMats".equals(attr.getClass().getName())){materials=attr;break;}
        Object mats=GameObjects.field(materials,"mats",java.util.Map.class);
        if(!(mats instanceof java.util.Map))return false;
        for(Object material:((java.util.Map<?,?>)mats).values()){
            if(!(material instanceof Material.ResMaterial))continue;
            Resource skin=((Material.ResMaterial)material).res;
            if(skin!=null&&mannequin(skin.name))return true;
        }
        return false;
    }
    private static boolean mannequin(String name){
        return name!=null&&name.contains("mannequin")&&!name.contains("mannequinlift");
    }
    private static boolean statue(java.util.List<Composited.MD> mods){
        if(mods==null)return false;
        for(Composited.MD mod:mods){
            if(mod==null)continue;
            if(statue(mod.mod))return true;
            if(mod.tex==null)continue;
            for(ResData tex:mod.tex)if(tex!=null&&statue(tex.res))return true;
        }
        return false;
    }
    private static boolean statue(Indir<Resource> res){return res!=null&&statue(res.get().name);}
    private static boolean statue(String name){
        return mannequin(name)||name!=null&&(name.contains("carving")||name.contains("skeleton")||name.contains("gandalf")||name.contains("wizardscape")||name.contains("wizardstunic"));
    }
    public static Info info(GameUI gui,Gob gob){
        BuddyWnd.Buddy buddy=null;boolean villager=false,pending=false;String name=null;Color namedColor=null;
        for(GAttrib attr:gob.attributes()){
            switch(attr.getClass().getName()){
            case "haven.res.ui.obj.buddy.Buddy":
                Number id=GameObjects.field(attr,"id",Number.class);
                if(id!=null&&gui!=null&&gui.buddies!=null)buddy=gui.buddies.find(id.intValue());
                pending=buddy==null;break;
            case "haven.res.ui.obj.buddy_v.Vilmate":villager=true;break;
            case "haven.res.ui.obj.buddy_n.Named":
                name=GameObjects.field(attr,"nm",String.class);namedColor=GameObjects.field(attr,"col",Color.class);break;
            }
        }
        int group=buddy==null?-1:buddy.group;
        if(buddy!=null&&buddy.name!=null&&!buddy.name.isEmpty())name=buddy.name;
        Kind kind=group==2?Kind.ENEMY:villager?Kind.VILLAGER:
            group==1||Color.GREEN.equals(namedColor)||gob.glob.party!=null&&gob.glob.party.memb.containsKey(gob.id)?Kind.FRIEND:
            buddy!=null||name!=null&&!name.isEmpty()?Kind.KNOWN:Kind.ENEMY;
        return new Info(kind,name,pending);
    }
    private PlayerRelations(){}
}
