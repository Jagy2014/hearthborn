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
import hearthborn.planner.RoomPlan;

/**
 * Zoom-only entry, with an in-memory outdoor view restored on exit; loading never counts as exit.
 *
 * @author Jagy2014
 */
public final class IndoorCamera {
    private MapView view;
    private RoomPlan room;
    private boolean inside;
    private Float outdoors;
    private MapView.CameraDirection outdoorDirection;
    private Class<?> outdoorType;
    private boolean lastIndoors;

    private boolean prepare(GameUI gui){
        if(gui.map==null||gui.ui==null||gui.ui.sess==null||gui.map.player()==null)return false;
        if(view!=gui.map){view=gui.map;room=new RoomPlan(view);}
        try{
            Gob player=view.player();MCache cache=gui.ui.sess.glob.map;
            Resource floor=cache.tilesetr(cache.gettile(player.rc.floor(MCache.tilesz)));
            return floor!=null&&!floor.name.equals("gfx/tiles/nil")&&!floor.name.equals("gfx/tiles/notile");
        }catch(Loading pending){return false;}
    }
    private boolean locationKnown(){
        boolean known=room.locationKnown();
        if(known)lastIndoors=room.indoors();
        return known;
    }
    /** Share the existing room observer; transient loading preserves the last known environment. */
    public boolean indoors(GameUI gui){
        if(!prepare(gui))return lastIndoors;
        locationKnown();return lastIndoors;
    }
    public void tick(GameUI gui){
        prepareFrame(gui);
    }
    /** Keep drawing through a door or travel move. Zoom changes only after the room is known. */
    public boolean prepareFrame(GameUI gui){
        if(!prepare(gui))return true;
        try{
            if(locationKnown())update(view.camera,true,room.identity());
            return true;
        }catch(Loading pending){return true;}
    }

    void update(MapView.Camera camera,boolean known,String roomId){
        if(!known||camera==null)return;
        if(roomId!=null){
            // Only a previously observed outdoor view establishes a house entry.
            // Room identity and camera replacement can change on stairs without an exit.
            if(!inside&&outdoors!=null){
                if(outdoorType!=camera.getClass())remember(camera);
                camera.roomZoom();
            }
            inside=true;
        }else{
            if(inside&&outdoors!=null&&outdoorType==camera.getClass()){
                camera.zoom(outdoors);
                if(outdoorDirection!=null)camera.orientation(outdoorDirection);
            }
            inside=false;
            remember(camera);
        }
    }
    private void remember(MapView.Camera camera){
        outdoors=camera.zoom();outdoorDirection=camera.orientation();outdoorType=camera.getClass();
    }
}
