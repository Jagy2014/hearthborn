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

package hearthborn.combat;
import haven.*;
import java.util.*;
/**
 * @author Jagy2014
 */
public final class KnockoutState {
 private static final Set<GameUI> down=Collections.newSetFromMap(new WeakHashMap<>());
 public static boolean unconscious(GameUI gui){return gui!=null&&down.contains(gui);}
 public static void enter(GameUI gui){
  if(gui==null||!down.add(gui))return;
  AutoCombat.enabled(false);CombatPrefs.reaggro(false);
  hearthborn.scripts.ShortcutRunner.cancel(gui);hearthborn.scripts.ScriptRunner.cancel(gui);
  if(gui.map!=null){
   hearthborn.navigation.Walking.cancel(gui.map);
   Gob player=gui.map.player();if(player!=null)gui.map.wdgmsg("click",Coord.z,player.rc.floor(OCache.posres),3,0);
  }
  if(gui.fv!=null)for(Fightview.Relation relation:new ArrayList<>(gui.fv.lsrel))if(relation.gst==0)gui.fv.wdgmsg("give",(int)relation.gobid,1);
 }
 public static void leave(GameUI gui){down.remove(gui);}
}
