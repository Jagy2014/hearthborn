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
import hearthborn.io.Logs;
import java.nio.file.*;
import java.util.*;

/**
 * Bounded diagnostics for missing live icons; never changes visibility preferences.
 *
 * @author Jagy2014
 */
public final class MapIconTrace {
    private double next;
    private String previous;
    public void sample(MiniMap map){
        if(map.ui==null||map.ui.sess==null||Utils.rtime()<next)return;next=Utils.rtime()+10;
        int candidates=0,unknown=0,hidden=0,loading=0;
        OCache cache=map.ui.sess.glob.oc;
        synchronized(cache){for(Gob gob:cache){GobIcon icon=gob.getattr(GobIcon.class);if(icon==null)continue;candidates++;
            try{GobIcon.Setting setting=map.iconconf==null?null:map.iconconf.get(icon.icon());if(setting==null)unknown++;else if(!setting.show)hidden++;}catch(Loading pending){loading++;}catch(Resource.NoSuchResourceException missing){RadarIcons.absent(missing.name);unknown++;}
        }}
        int positioned=0;for(MiniMap.DisplayIcon icon:map.icons)if(icon.sc!=null)positioned++;
        MiniMap.Location display=map.displayLocation(),session=map.sessloc;
        int saved=0,sameLevel=0,filtered=0;
        if(map.file!=null&&map.file.lock.readLock().tryLock())try{
            saved=map.file.markers.size();
            if(display!=null)for(MapFile.Marker marker:map.file.markers)if(marker.seg==display.seg.id){
                sameLevel++;if(map.filter(new MiniMap.DisplayMarker(map,marker)))filtered++;
            }
        }finally{map.file.lock.readLock().unlock();}
        String state="view="+map.getClass().getName()+" detached="+map.loadHidden+" settings="+(map.iconconf!=null)+" candidates="+candidates+" unknown="+unknown+" hidden="+hidden+" loading="+loading+" collected="+map.icons.size()+" positioned="+positioned+" saved="+saved+" same-level="+sameLevel+" filtered="+filtered+" zoom="+map.displayZoom()+" display="+(display==null?"none":display.seg.id)+" session="+(session==null?"none":session.seg.id);
        if(!hearthborn.io.DeveloperMode.enabled("debug-logs")||state.equals(previous))return;previous=state;
        try{Logs.append("map-icons.log",java.time.Instant.now()+" "+state+"\n");}catch(java.io.IOException ignored){}
    }
}
