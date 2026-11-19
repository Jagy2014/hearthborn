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
import haven.render.BaseColor;
import haven.render.States;
import java.util.*;

/**
 * Window-owned, client-only ground preview. Resolves stable grid IDs before drawing anything.
 *
 * @author Jagy2014
 */
final class SavedAreaHighlight {
    private static final MCache.OverlayInfo ID=new MCache.OverlayInfo(){
        final Material fill=new Material(new BaseColor(235,183,55,48),States.maskdepth);
        final Material edge=new Material(new BaseColor(255,214,100,230),States.maskdepth);
        public Collection<String> tags(){return Collections.singletonList("show");}
        public Material mat(){return fill;}
        public Material omat(){return edge;}
    };
    private final GameUI gui;
    private final Widget owner;
    private SavedArea selected;
    private String scope;
    private boolean suspended;
    private MCache map;
    private MapView view;
    private Preview overlay;
    SavedAreaHighlight(GameUI gui,Widget owner){this.gui=gui;this.owner=owner;}
    void show(SavedArea area){selected=area;scope=SavedArea.key(gui,"preview");tick();}
    void suspend(boolean value){suspended=value;tick();}
    void clear(){selected=null;remove();}
    private void remove(){if(map!=null&&overlay!=null)map.remove(overlay);overlay=null;map=null;view=null;}
    void tick(){
        MCache current=gui.ui==null||gui.ui.sess==null?null:gui.ui.sess.glob.map;
        Area bounds=selected==null||suspended||owner.parent==null||!owner.visible()||gui.map==null||current==null
            ||scope==null||!scope.equals(SavedArea.key(gui,"preview"))?null:selected.resolve(current);
        if(bounds==null){remove();return;}
        if(overlay!=null&&map==current&&view==gui.map&&overlay.saved==selected&&overlay.bounds.equals(bounds))return;
        remove();map=current;view=gui.map;overlay=new Preview(map,selected,bounds);map.add(overlay);
    }
    private static final class Preview implements MCache.LocalOverlay {
        final MCache map;final SavedArea saved;final Area bounds;
        Preview(MCache map,SavedArea saved,Area bounds){this.map=map;this.saved=saved;this.bounds=bounds;}
        public MCache.OverlayInfo id(){return ID;}
        public boolean filter(Area area){return bounds.overlap(area)==null;}
        public void fill(Area area,boolean[] mask){
            // Never reuse session coordinates on a different grid, even before the next window tick.
            if(!bounds.equals(saved.resolve(map)))return;
            Area overlap=bounds.overlap(area);
            if(overlap!=null)for(Coord tile:overlap)mask[area.ri(tile)]=true;
        }
    }
}
