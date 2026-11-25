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
import hearthborn.world.*;
import hearthborn.scripts.ShortcutRunner;
import java.util.*;
import java.util.List;
/**
 * Adapters between global action names and the native client widgets.
 *
 * @author Jagy2014
 */
public final class GlobalCommands {
 private static final Map<GameUI,Map<String,Window>> windows=new WeakHashMap<>();
 private static final Map<GameUI,MenuGrid.Pagina> lastCraft=new WeakHashMap<>();
 public static void recipe(MenuGrid.Pagina page){Resource resource=page.res();if(resource.name.startsWith("paginae/craft/")&&!ActionBrowser.previewing(page.scm)){GameUI gui=page.scm.getparent(GameUI.class);if(gui!=null){lastCraft.put(gui,page);CraftHistory.remember(gui,page);}}}
 public static void dispose(GameUI gui){windows.remove(gui);lastCraft.remove(gui);CraftHistory.forget(gui);}
 public static void run(GameUI gui,String action) {
  if(gui==null)return;
  switch(action) {
   case "BOT_CELLAR_DIGGING":hearthborn.scripts.ScriptRunner.run(gui,"cellar");break;
   case "BOT_ROASTING_SPIT":hearthborn.scripts.ScriptRunner.run(gui,"roastspit");break;
   case "BOT_CORACLE":hearthborn.scripts.ScriptRunner.run(gui,"coracle");break;
   case "BOT_SKIS":hearthborn.scripts.ScriptRunner.run(gui,"skis");break;
   case "BOT_CHIP_STONE":hearthborn.scripts.ScriptRunner.run(gui,"chipstone");break;
   case "ACT_HAND_0":slot(gui,6,false);break;
   case "ACT_HAND_1":slot(gui,7,false);break;
   case "ACT_BELT":slot(gui,5,true);break;
   case "ACT_POUCH_0":slot(gui,19,true);break;
   case "ACT_POUCH_1":slot(gui,20,true);break;
   case "OPEN_QUICK_CRAFT":browser(gui,"craft","Crafting recipes");break;
   case "OPEN_QUICK_BUILD":browser(gui,"bld","Buildings");break;
   case "OPEN_QUICK_ACTION":browser(gui,"act","Actions");break;
   case "OPEN_CRAFT_DB":browser(gui,"craft","Crafting recipes");break;
   case "OPEN_ALCHEMY_DB":browser(gui,"alchemy","Alchemy");break;
   case "LAST_CRAFT":MenuGrid.Pagina recipe=lastCraft.get(gui);gui.repeatCraft(recipe);break;
   case "OPEN_QUEST_HELP":quests(gui);break;
   case "TOGGLE_CURSOR":if(gui.vhand!=null)gui.vhand.show(!gui.vhand.visible());break;
   case "TOGGLE_STUDY":gui.toggleStudy();break;
   case "FILTER":filter(gui);break;
   case "SORT_INVENTORY":sort(gui);break;
   case "TOGGLE_INSPECT":gui.act("inspect");break;
   case "TOGGLE_HIDE_OBJECTS":hearthborn.world.ObjectHiding.toggle();break;
   case "TOGGLE_HIDE_TREES":PlantVisibility.hidden(false,!PlantVisibility.hidden(false));break;
   case "TOGGLE_AUTO_DROP":hearthborn.inventory.AutoDrop.toggle(gui.ui);break;
   case "TOGGLE_GOB_INFO":boolean on=!PlantGrowth.enabled("crops");for(String type:new String[]{"crops","trees","bushes"})PlantGrowth.enabled(type,on);break;
   case "TOGGLE_GOB_HITBOX":hearthborn.map.CollisionOverlay.toggle();break;
   case "TOGGLE_GOB_RADIUS":GobMarks.toggleRadii(gui);break;
   case "TOGGLE_ORE_HIGHLIGHT":if(gui.map!=null){if(gui.map.visol("ore"))gui.map.disol("ore");else gui.map.enol("ore");}break;
   case "TOGGLE_TILE_CENTERING":Utils.setprefb("hearthborn/world/center-clicks",!Utils.getprefb("hearthborn/world/center-clicks",false));break;
   case "TOGGLE_PEACE":if(gui.fv!=null&&gui.fv.current!=null)gui.fv.wdgmsg("give",(int)gui.fv.current.gobid,gui.fv.current.gst==0?1:0);break;
   case "TARGET_PREVIEW_NEXT":hearthborn.combat.TargetPreview.cycle(gui,false);break;
   case "TARGET_PREVIEW_PREVIOUS":hearthborn.combat.TargetPreview.cycle(gui,true);break;
   case "TARGET_PREVIEW_CURSOR":hearthborn.combat.TargetPreview.underCursor(gui);break;
   case "TARGET_PREVIEW_ATTACK":hearthborn.combat.TargetPreview.attack(gui);break;
   default:
    if(action.startsWith("CAM_")){if(gui.map!=null)gui.map.globalCamera(action);}
    else ShortcutRunner.run(gui,action);
  }
 }
 public static void slot(GameUI gui,int slot,boolean interact) {
  Equipory equipment=gui.equipment();if(equipment==null)return;
  if(!interact&&gui.vhand!=null){equipment.wdgmsg("drop",slot);return;}
  Coord at=Equipory.ecoords[slot].add(1,1);
  for(Widget child=equipment.child;child!=null;child=child.next)if(child instanceof WItem && child.c.equals(at)) {
   GItem item=((WItem)child).item;if(interact)item.wdgmsg("iact",Coord.z,0);else item.wdgmsg("take",Coord.z);return;
  }
 }
 private static void browser(GameUI gui,String kind,String title) {
  Map<String,Window> own=windows.computeIfAbsent(gui,key->new HashMap<>());Window previous=own.get(kind);
  if(previous!=null&&previous.parent!=null){previous.reqdestroy();own.remove(kind);return;}
  MenuSearch search=new MenuSearch(title,gui.menu) {
   private int sequence=-1;
   protected boolean generate(List<MenuGrid.PagButton> buttons) {
    boolean loading=false;List<MenuGrid.Pagina> pages;
    synchronized(menu.paginae){pages=new ArrayList<>(menu.paginae);}
    for(MenuGrid.Pagina page:pages)try {
     Resource resource=page.res();boolean include=kind.equals("act");
     for(MenuGrid.Pagina ancestor=page;ancestor!=null&&!include;ancestor=ancestor.parent()) {
      String path=ancestor.res().name;
      include=kind.equals("craft")?path.equals("paginae/act/craft"):kind.equals("bld")?path.equals("paginae/act/bld"):path.contains("alchemy")||path.contains("alch");
     }
     if(include&&page.button().act().ad.length>0)buttons.add(page.button());
    }catch(Loading pending){loading=true;}
    buttons.sort(Comparator.comparing(MenuGrid.PagButton::name));return loading;
   }
   public void tick(TickEvent event){if(sequence!=menu.pagseq){sequence=menu.pagseq;recons();}super.tick(event);}
  };
  gui.add(search,gui.sz.sub(search.sz).div(2).max(Coord.z));search.reqclose(search::reqdestroy);own.put(kind,search);gui.setfocus(search);
 }
 private static void sort(GameUI gui){List<Inventory> inventories=new ArrayList<>();for(Inventory inventory:gui.children(Inventory.class))if(inventory.visible())inventories.add(inventory);for(Inventory inventory:inventories)hearthborn.inventory.InventoryActions.sort(inventory);}
 private static void quests(GameUI gui) {gui.questHelper.toggleWindow();}
 private static void filter(GameUI gui) {
  Map<String,Window> own=windows.computeIfAbsent(gui,key->new HashMap<>());Window previous=own.get("filter");
  if(previous!=null&&previous.parent!=null){previous.reqdestroy();ItemFilter.text="";own.remove("filter");return;}
  Window window=new Window(UI.scale(220,24),"Item filter");window.add(new TextEntry(UI.scale(220),ItemFilter.text){protected void changed(){ItemFilter.text=text().toLowerCase(Locale.ROOT);}},Coord.z);
  window.reqclose(()->{ItemFilter.text="";window.reqdestroy();});gui.add(window,gui.sz.sub(window.sz).div(2).max(Coord.z));window.move(gui.sz.sub(HudInterfaceScale.size(window)).div(2).max(Coord.z));own.put("filter",window);gui.setfocus(window);
 }
 public static final class ItemFilter {
  static String text="";
  public static boolean active() {return !text.isEmpty();}
  public static boolean dim(GItem item) {if(text.isEmpty())return false;try {String name=hearthborn.util.GameObjects.name(item.resource().name);ItemInfo.Name actual=ItemInfo.find(ItemInfo.Name.class,item.info());return !((actual==null?name:hearthborn.l10n.LocalizedText.shown(actual.str))+" "+name+" "+item.resource().name).toLowerCase(Locale.ROOT).contains(text);}catch(Loading loading){return false;}}
 }
}
