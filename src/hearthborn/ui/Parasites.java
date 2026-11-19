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
import java.util.*;
/**
 * Resource names, rather than translated tooltips, identify equipped parasites.
 *
 * @author Jagy2014
 */
public final class Parasites {
 private final Set<GItem> dropped=Collections.newSetFromMap(new IdentityHashMap<>());
 private double elapsed;
 public static boolean enabled(){return Utils.getprefb("hearthborn/equipment/drop-parasites",false);}
 public static void enabled(boolean value){Utils.setprefb("hearthborn/equipment/drop-parasites",value);}
 public static boolean parasite(String resource){return resource!=null && (resource.endsWith("/leech") || resource.endsWith("/tick"));}
 public void tick(Equipory equipment,double dt) {
  if((elapsed+=dt)<.05)return;elapsed=0;
  if(!enabled()){dropped.clear();return;}
  GameUI gui=equipment.getparent(GameUI.class);if(gui==null || gui.equipment()!=equipment)return;
  List<GItem> items=equipment.equippedItems();dropped.retainAll(items);
  for(GItem item:items)if(!dropped.contains(item))try {
   if(parasite(item.resource().name)){item.wdgmsg("drop",Coord.z);dropped.add(item);}
  }catch(Loading ignored){}
 }
}
