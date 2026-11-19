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

package hearthborn.map;

import haven.*;
import hearthborn.L10n;
import hearthborn.world.PlayerRelations;
import hearthborn.world.PlayerRelations.Kind;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Live nearby players and server-supplied party coordinates; no invented village tracking.
 *
 * @author Jagy2014
 */
public final class PlayerMarkers implements Disposable {
    private final MiniMap map;
    private final Map<String,Tex> labels=new LinkedHashMap<>();
    private final Map<Long,String> partyNames=new HashMap<>();
    private final Map<Gob,Coord> hits=new LinkedHashMap<>();
    public PlayerMarkers(MiniMap map){this.map=map;}
    public static boolean shown(Kind kind){return Utils.getprefb("hearthborn/map/players/"+kind.name().toLowerCase(Locale.ROOT),true);}
    public static void shown(Kind kind,boolean value){Utils.setprefb("hearthborn/map/players/"+kind.name().toLowerCase(Locale.ROOT),value);}
    public static boolean partyNames(){return MapDisplay.enabled("party-names");}
    public static void partyNames(boolean value){MapDisplay.enabled("party-names",value);}
    public static BufferedImage image(Kind kind){return PlayerArrow.texture(kind.color).back;}
    private void draw(GOut g,Coord at,double angle,Kind kind){
        Tex tex=MapToolbar.playerMarker(kind.color);g.chcolor();g.rotimage(tex,at,tex.sz().div(2),-angle-Math.PI/2);
    }
    private void label(GOut g,String name,Coord at){
        Tex text=labels.get(name);
        if(text==null){
            if(labels.size()>=128){Iterator<Tex> old=labels.values().iterator();old.next().dispose();old.remove();}
            text=new TexI(PUtils.rasterimg(PUtils.blurmask2(Text.render(name,Color.WHITE).img.getRaster(),1,1,Color.BLACK)));labels.put(name,text);
        }
        g.aimage(text,at.add(0,UI.scale(12)),.5,0);
    }
    private java.util.List<Gob> candidates=java.util.Collections.emptyList();
    private OCache scanned;private double nextScan;
    /** The objects that are players. Going through everything in view is costly, so it is done a few times a second; positions are read every frame. */
    private java.util.List<Gob> players(OCache cache){
        double now=Utils.rtime();
        if(cache==scanned&&now<nextScan)return candidates;
        java.util.List<Gob> found=new ArrayList<>();boolean pending=false;
        for(Gob gob:cache.snapshot())try{if(!gob.virtual&&PlayerRelations.player(gob))found.add(gob);}catch(Loading loading){pending=true;}
        candidates=found;scanned=cache;nextScan=now+(pending?0.05:0.2);
        return found;
    }
    public void draw(GOut g){
        hits.clear();
        GameUI gui=map.getparent(GameUI.class);
        if(gui==null||map.ui==null||map.ui.sess==null||map.sessloc==null||map.displayLocation()==null||map.displayLocation().seg!=map.sessloc.seg)return;
        Glob glob=map.ui.sess.glob;java.util.List<Gob> objects=players(glob.oc);
        Map<Long,Party.Member> party=glob.party.memb;
        for(Gob gob:objects)try{
            if(gob.id==gui.plid||gob.removed||gob.virtual||party.containsKey(gob.id)||PlayerRelations.fixture(gob))continue;
            PlayerRelations.Info info=PlayerRelations.info(gui,gob);
            if(shown(info.kind)){Coord at=map.p2c(gob.rc);draw(g,at,gob.a,info.kind);hits.put(gob,at);}
        }catch(Loading pending){}
        partyNames.keySet().retainAll(party.keySet());
        for(Party.Member member:party.values())try{
            Gob gob=member.getgob();PlayerRelations.Info info=gob==null?null:PlayerRelations.info(gui,gob);
            if(info!=null&&info.name!=null&&!info.name.isEmpty())partyNames.put(member.gobid,info.name);
            Coord2d location=member.getc();if(location==null||member.gobid==gui.plid)continue;
            // Party membership is a separate coordinate channel and does not depend on nearby-player filters.
            Coord at=map.p2c(location);
            if(gob!=null)hits.put(gob,at);
            String name=partyNames.get(member.gobid);if(partyNames()&&name!=null)label(g,name,at);
        }catch(Loading pending){}
        g.chcolor();
    }
    public Gob hit(Coord point){
        Gob best=null;double distance=UI.scale(11);
        for(Map.Entry<Gob,Coord> entry:hits.entrySet())if(!entry.getKey().removed&&entry.getValue().dist(point)<distance){best=entry.getKey();distance=entry.getValue().dist(point);}
        return best;
    }
    public Object tooltip(Coord point){
        Gob gob=hit(point);if(gob==null)return null;
        PlayerRelations.Info info=PlayerRelations.info(map.getparent(GameUI.class),gob);
        return info.name==null?L10n.tr(info.kind.title):info.name+" · "+L10n.tr(info.kind.title);
    }
    public void dispose(){for(Tex text:labels.values())text.dispose();labels.clear();partyNames.clear();hits.clear();}
}
