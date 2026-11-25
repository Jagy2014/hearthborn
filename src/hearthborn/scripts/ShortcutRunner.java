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
import hearthborn.ui.DrinkSupply;
import hearthborn.util.GameObjects;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.*;
/**
 * Cancellable UI-thread sequences for inventory and nearby-object shortcuts.
 *
 * @author Jagy2014
 */
public final class ShortcutRunner {
 private static final Map<GameUI,ShortcutRunner> active=new WeakHashMap<>();
 private static final Map<GameUI,MeleeSet> meleeSets=new WeakHashMap<>();
 private final GameUI gui;final Deque<Step> steps=new ArrayDeque<>();private boolean issued,weaponSwitch,executing;private double deadline;private Gob selectedHorse;
 private static class Step {Runnable action;BooleanSupplier ready;double timeout;Step(Runnable action,BooleanSupplier ready,double timeout){this.action=action;this.ready=ready;this.timeout=timeout;}}
 ShortcutRunner(GameUI gui){this.gui=gui;}
 void add(Runnable action,BooleanSupplier ready,double timeout){steps.add(new Step(action,ready,timeout));}
 void add(Runnable action){add(action,()->true,2);}
 /** Plan the next exchange after earlier equipment and inventory updates have arrived. */
 void afterPrevious(Runnable plan) {
  add(()->{
   Step current=steps.removeFirst();
   Deque<Step> following=new ArrayDeque<>(steps);
   steps.clear();
   try{plan.run();}
   finally{steps.addFirst(current);steps.addAll(following);}
  });
 }
 public static void cancel(GameUI gui){
  if(gui==null)return;ShortcutRunner task=active.remove(gui);if(task!=null&&task.selectedHorse!=null)task.selectedHorse.delattr(HorseTarget.class);
 }
 private static final class HorseTarget extends GAttrib implements Gob.SetupMod {
  HorseTarget(Gob horse){super(horse);}
  public haven.render.Pipe.Op gobstate(){return new haven.render.MixColor(255,217,75,150);}
 }
 static boolean needsWhistle(double distance){return distance>=20;}
 static boolean horseAvailable(Gob horse,Collection<Gob> objects){
  if(horse.removed||!name(horse).startsWith("gfx/kritter/horse/")||horse.getattr(Following.class)!=null||!ScriptRunner.standing(horse))return false;
  for(Gob object:objects){Following rider=object.getattr(Following.class);if(rider!=null&&rider.tgt==horse.id)return false;}
  return true;
 }
 public static void tick(GameUI gui) {
  ShortcutRunner bot=active.get(gui);if(bot==null)return;
  if(bot.steps.isEmpty()){cancel(gui);return;}
  if(bot.selectedHorse!=null&&bot.selectedHorse.removed){cancel(gui);gui.ui.msg(hearthborn.L10n.tr("The selected horse is no longer visible."));return;}
  Step step=bot.steps.peek();try {
   if(!bot.issued){bot.executing=true;try{step.action.run();}finally{bot.executing=false;}bot.issued=true;bot.deadline=Utils.rtime()+step.timeout;}
   if(step.ready.getAsBoolean()){bot.steps.remove();bot.issued=false;}
   else if(Utils.rtime()>bot.deadline){cancel(gui);gui.ui.msg(hearthborn.L10n.tr("Shortcut action timed out."));}
  }catch(Loading loading){}catch(RuntimeException failure){cancel(gui);new Warning(failure,"global shortcut failed").issue();gui.ui.msg(hearthborn.L10n.tr("Shortcut action could not be completed."));}
 }
 public static void pickAll(GameUI gui,Gob target) {
  ScriptRunner.cancel(gui);
  cancel(gui);ShortcutRunner bot=new ShortcutRunner(gui);String resource=name(target);
  for(Gob gob:bot.near(g->name(g).equals(resource),Double.MAX_VALUE))bot.add(()->bot.click(gob,3,UI.MOD_SHIFT),()->gob.removed,30);
  if(!bot.steps.isEmpty())active.put(gui,bot);
 }
 public static void run(GameUI gui,String action) {
  if(action.equals("SWITCH_BOW_MELEE")&&switchingWeapon(gui))return;
  ScriptRunner.cancel(gui);
  if(gui.map==null||gui.map.player()==null)return;
  cancel(gui);ShortcutRunner bot=new ShortcutRunner(gui);
  switch(action) {
   case "BOT_OPEN_GATE":for(Gob gob:bot.near(g->name(g).startsWith("gfx/terobjs/arch/")&&name(g).endsWith("gate")&&!visitor(g),35)){bot.add(()->bot.click(gob,3,0));break;}break;
   case "BOT_MOUNT_HORSE":{
    if(gui.map.player().getattr(Following.class)!=null){gui.ui.msg(hearthborn.L10n.tr("You are already mounted or aboard a vehicle."));return;}
    List<Gob> objects=new ArrayList<>();synchronized(gui.ui.sess.glob.oc){for(Gob object:gui.ui.sess.glob.oc)objects.add(object);}
    List<Gob> horses=bot.near(g->horseAvailable(g,objects),330);
    if(horses.isEmpty()){gui.ui.msg(hearthborn.L10n.tr("No available horse nearby."));return;}
    Gob horse=horses.get(0);bot.selectedHorse=horse;horse.setattr(new HorseTarget(horse));
    if(needsWhistle(horse.rc.dist(gui.map.player().rc)))bot.add(()->{Coord at=horse.rc.floor(OCache.posres);gui.menu.wdgmsg("act","pose","whistle",0,at,0,horse.id,at,0,-1);},()->horse.rc.dist(gui.map.player().rc)<20,20);
    bot.add(()->bot.click(horse,3,0));bot.flower("Giddyup!");
    bot.add(()->{},()->{Following riding=gui.map.player().getattr(Following.class);return riding!=null&&riding.tgt==horse.id;},10);
   }break;
   case "BOT_PICK_ALL_HERBS":for(Gob herb:bot.near(g->hearthborn.ui.PickupSettings.eligible(gui,g) && (name(g).startsWith("gfx/terobjs/herbs/")||name(g).startsWith("gfx/terobjs/items/")||name(g).matches("gfx/kritter/(frog|grasshopper|silkmoth|ladybug|dragonfly)/.*")),hearthborn.ui.PickupSettings.radius())) {
    bot.add(()->bot.click(herb,3,UI.MOD_SHIFT),()->herb.removed,30);
   }break;
   case "ACT_DRINK":for(GItem item:owned(gui)) {
    DrinkSupply supply=new DrinkSupply();supply.add(item);if(supply.water+supply.tea>0){bot.add(()->item.wdgmsg("iact",Coord.z,0));bot.flower("Drink");break;}
   }break;
   case "ACT_REFILL_DRINKS":bot.refill();break;
   case "EQUIP_BOW":bot.equip(HandGear.BOWS,6);break;
   case "EQUIP_SPEAR":bot.equip(HandGear.SPEARS,6);break;
   case "EQUIP_SWORD_N_BOARD":bot.equip(HandGear.SHIELDS,6);bot.equip(HandGear.SWORDS,7);break;
   case "EQUIP_T_SACKS":bot.equip(HandGear.SACKS,6);bot.equip(HandGear.SACKS,7);break;
   case "SWITCH_BOW_MELEE":
    try{bot.switchBow();}catch(Loading waiting){gui.ui.msg(hearthborn.L10n.tr("Items are still loading. Try again."));return;}
    break;
   case "AGGRO_ONE_PVE":case "AGGRO_ONE_PVP":case "AGGRO_ALL":bot.aggro(action);break;
   case "AGGRO_NEAREST_WILD":bot.aggroNearestWild();break;
   default:throw new IllegalArgumentException("Unknown global action "+action);
  }
  if(!bot.steps.isEmpty())active.put(gui,bot);
 }
 private static boolean visitor(Gob gob) {
  for(Gob.Overlay overlay:gob.ols) {
   Sprite nested=GameObjects.field(overlay.spr,"espr",Sprite.class);
   if(nested!=null&&nested.res!=null&&nested.res.name.equals("gfx/terobjs/arch/visflag"))return true;
  }
  return false;
 }
 private static String name(Gob gob){try {String path=GameObjects.resourceName(gob);return path==null?"":path;}catch(Loading loading){return "";}}
 private List<Gob> near(Predicate<Gob> filter,double radius) {
  Gob player=gui.map.player();List<Gob> result=new ArrayList<>();synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)if(gob!=player&&!gob.removed&&gob.rc.dist(player.rc)<=radius&&filter.test(gob))result.add(gob);}
  result.sort(Comparator.comparingDouble(gob->gob.rc.dist(player.rc)));return result;
 }
 private void click(Gob gob,int button,int mods){if(!gob.removed){Coord at=gob.rc.floor(OCache.posres);gui.map.wdgmsg("click",Coord.z,at,button,mods,0,(int)gob.id,at,0,-1);}}
 private void flower(String name) {
  add(()->{},()->{
   FlowerMenu menu=gui.ui.root.findchild(FlowerMenu.class);if(menu==null)return false;
   for(FlowerMenu.Petal petal:menu.opts)if(petal.name.equals(name)){menu.choose(petal);return true;}
   return false;
  },3);
 }
 public static List<GItem> owned(GameUI gui) {
  Set<GItem> found=Collections.newSetFromMap(new IdentityHashMap<>());List<GItem> out=new ArrayList<>();scan(gui.maininv,found,out);scan(gui.equipment(),found,out);return out;
 }
 private static void scan(Widget container,Set<GItem> found,List<GItem> out){if(container==null)return;for(Widget w=container.child;w!=null;w=w.next)if(w instanceof GItem){GItem item=(GItem)w;if(found.add(item)){out.add(item);scan(item.contents,found,out);}}}
 final Set<GItem> selected=Collections.newSetFromMap(new IdentityHashMap<>());
 public static boolean switchingWeapon(GameUI gui){ShortcutRunner bot=active.get(gui);return bot!=null&&bot.weaponSwitch;}
 public static void manualWeaponInput(GameUI gui){ShortcutRunner bot=active.get(gui);if(bot!=null&&bot.weaponSwitch&&!bot.executing)cancel(gui);}
 private static boolean isBow(GItem item){String name=item.getres().basename();return name.equals("huntersbow")||name.equals("rangersbow");}
 private static boolean isSword(GItem item){String name=item.getres().basename();return name.equals("bronzesword")||name.equals("fyrdsword")||name.equals("hirdsword");}
 public static boolean swordInHands(GameUI gui){
  WItem left=HandEquip.hand(gui,HandEquip.LEFT),right=HandEquip.hand(gui,HandEquip.RIGHT);
  return left!=null&&isSword(left.item)||right!=null&&isSword(right.item);
 }
 private static boolean isShield(GItem item){return Arrays.asList(HandGear.SHIELDS).contains(item.getres().basename());}
 private static String weaponResource(GItem item){return item.getres().name.replace("gfx/invobjs/small/","gfx/invobjs/");}
 private static final class MeleeSet {
  final String sword,shield;
  final int swordSlot,shieldSlot;
  WeakReference<GItem> bow;
  MeleeSet(WItem left,WItem right){
   boolean swordOnLeft=isSword(left.item);
   sword=weaponResource(swordOnLeft?left.item:right.item);
   shield=weaponResource(swordOnLeft?right.item:left.item);
   swordSlot=swordOnLeft?HandEquip.LEFT:HandEquip.RIGHT;
   shieldSlot=swordOnLeft?HandEquip.RIGHT:HandEquip.LEFT;
  }
 }
 private static GItem handBow(GameUI gui){
  Equipory gear=gui.equipment();if(gear==null)return null;
  for(WItem view:gear.children(WItem.class))if((view.c.equals(Equipory.ecoords[6].add(1,1))||view.c.equals(Equipory.ecoords[7].add(1,1)))&&isBow(view.item))return view.item;
  return null;
 }
 private static GItem carriedWeapon(GameUI gui,boolean bow){
  for(GItem item:owned(gui))if(item.parent instanceof Inventory&&(bow?isBow(item):isSword(item)))return item;
  return null;
 }
 private GItem carriedResource(String resource){
  for(GItem item:owned(gui))if(item.parent instanceof Inventory&&weaponResource(item).equals(resource))return item;
  return null;
 }
 public static String bowSwitchLabel(GameUI gui){
  if(gui==null)return null;
  try{
   if(handBow(gui)!=null)return carriedWeapon(gui,false)==null?"Put away bow":"Draw sword";
   return carriedWeapon(gui,true)==null?null:"Draw bow";
  }catch(Loading waiting){return "Switch weapon";}
 }
 private Pair<Inventory,Coord> weaponSpace(Inventory preferred,Coord size){
  Inventory belt=HandEquip.belt(gui);
  if(belt!=null){
   Coord free=new HandEquip.Space(belt,null,Coord.z,true).reserve(size,null);
   if(free!=null)return new Pair<>(belt,free);
  }
  List<Inventory> bags=new ArrayList<>();if(preferred!=null&&preferred!=belt&&preferred.parent!=null)bags.add(preferred);
  if(gui.maininv!=null&&!bags.contains(gui.maininv))bags.add(gui.maininv);
  for(GItem item:owned(gui))if(item.contents instanceof Inventory&&item.contents!=belt&&!bags.contains(item.contents))bags.add((Inventory)item.contents);
  for(Inventory bag:bags){Coord free=hearthborn.inventory.BagTransfers.free(bag,size);if(free!=null)return new Pair<>(bag,free);}
  return null;
 }
 private void putWeapon(Inventory preferred){
  if(gui.vhand==null)return;
  Coord size=gui.vhand.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
  Pair<Inventory,Coord> room=weaponSpace(preferred,size);if(room!=null){room.a.wdgmsg("drop",room.b);return;}
  gui.ui.msg(hearthborn.L10n.tr("No room to put the weapon away. Make space and place the held item in a bag."));cancel(gui);
 }
 private boolean inHands(String resource){
  Equipory gear=gui.equipment();if(gear==null)return false;
  for(WItem view:gear.children(WItem.class))if(view.c.equals(Equipory.ecoords[6].add(1,1))||view.c.equals(Equipory.ecoords[7].add(1,1)))
   try{if(weaponResource(view.item).equals(resource))return true;}catch(Loading waiting){}
  return false;
 }
 private void switchBow(){
  if(gui.vhand!=null||gui.equipment()==null){gui.ui.msg(hearthborn.L10n.tr("Empty the cursor before switching weapons."));return;}
  GItem bow=handBow(gui),next=carriedWeapon(gui,bow==null);weaponSwitch=true;
  if(next==null){
   if(bow==null){gui.ui.msg(hearthborn.L10n.tr("No bow found in your bags."));return;}
   Coord size=Coord.of(1,1);
   for(WItem view:gui.equipment().children(WItem.class))if(view.item==bow)size=view.sz.add(Inventory.sqsz.sub(1,1)).div(Inventory.sqsz);
   if(weaponSpace(gui.maininv,size)==null){gui.ui.msg(hearthborn.L10n.tr("No room to put the weapon away. Make space and try again."));return;}
   String resource=weaponResource(bow);GItem[] held={null};
   add(()->bow.wdgmsg("take",Coord.z),()->{try{if(gui.vhand==null||!weaponResource(gui.vhand.item).equals(resource))return false;held[0]=gui.vhand.item;return true;}catch(Loading waiting){return false;}},3);
   add(()->{if(gui.vhand==null||gui.vhand.item!=held[0])throw new IllegalStateException("Held weapon changed.");putWeapon(gui.maininv);},()->gui.vhand==null,3);return;
  }
  WItem left=HandEquip.hand(gui,HandEquip.LEFT),right=HandEquip.hand(gui,HandEquip.RIGHT);
  if(bow==null&&left!=null&&right!=null&&left.item!=right.item){
   boolean swordAndShield=isSword(left.item)&&isShield(right.item)||isShield(left.item)&&isSword(right.item);
   MeleeSet previous=swordAndShield?new MeleeSet(left,right):null;
   if(HandEquip.queueBowWithBothHandsOccupied(this,gui,next))add(()->{
    meleeSets.remove(gui);
    GItem drawnBow=handBow(gui);
    if(previous!=null&&drawnBow!=null){
     previous.bow=new WeakReference<>(drawnBow);
     meleeSets.put(gui,previous);
    }
   });
   return;
  }
  MeleeSet previousMelee=meleeSets.get(gui);
  if(bow!=null&&previousMelee!=null&&previousMelee.bow.get()==bow){
   GItem sword=carriedResource(previousMelee.sword);
   if(HandEquip.queue(this,gui,sword==null?next:sword,previousMelee.swordSlot)){
    afterPrevious(()->{
     if(HandEquip.hand(gui,previousMelee.shieldSlot)==null){
      GItem shield=carriedResource(previousMelee.shield);
      if(shield!=null)HandEquip.queue(this,gui,shield,previousMelee.shieldSlot);
     }
    });
   }
   return;
  }
  Set<String> previous=new HashSet<>();
  for(WItem view:gui.equipment().children(WItem.class))if(view.c.equals(Equipory.ecoords[6].add(1,1))||view.c.equals(Equipory.ecoords[7].add(1,1)))previous.add(weaponResource(view.item));
  String resource=weaponResource(next);Inventory source=(Inventory)next.parent;GItem[] held={null},returning={null};
  add(()->{if(next.parent!=source)throw new IllegalStateException("Weapon moved before the switch.");next.wdgmsg("take",Coord.z);},()->{
   if(gui.vhand==null)return false;
   try{if(!weaponResource(gui.vhand.item).equals(resource))return false;}catch(Loading waiting){return false;}
   held[0]=gui.vhand.item;return true;
  },3);
  add(()->{if(gui.vhand==null||gui.vhand.item!=held[0])throw new IllegalStateException("Held weapon changed.");gui.equipment().wdgmsg("drop",bow==null?6:7);},()->{
   if(!inHands(resource)||gui.vhand!=null&&gui.vhand.item==held[0])return false;
   try{if(gui.vhand!=null&&!previous.contains(weaponResource(gui.vhand.item)))return false;}catch(Loading waiting){return false;}
   returning[0]=gui.vhand==null?null:gui.vhand.item;return true;
  },3);
  add(()->{if(gui.vhand!=null&&gui.vhand.item!=returning[0])throw new IllegalStateException("Held item changed.");putWeapon(source);},()->gui.vhand==null,3);
 }
 private void equip(String[] names,int slot) {afterPrevious(()->HandEquip.equip(this,gui,names,slot));}
 /** Starts a prepared shortcut bot whose steps are already queued. */
 static void begin(ShortcutRunner bot){if(bot==null||bot.steps.isEmpty())return;cancel(bot.gui);active.put(bot.gui,bot);}
 private void refill() {
  if(gui.vhand!=null){gui.ui.msg(hearthborn.L10n.tr("You must have empty cursor to refill drinks!"));return;}
  Gob source=null;for(Gob gob:near(g->name(g).equals("gfx/terobjs/barrel")||name(g).startsWith("gfx/terobjs/cistern"),32)) {
   ContainerLookProbe probe=new ContainerLookProbe(gob);if(probe.water()){source=gob;break;}
  }
  Coord2d water=null;MCache map=gui.ui.sess.glob.map;Coord center=gui.map.player().rc.floor(MCache.tilesz);
  if(source==null)for(int y=-2;y<=2;y++)for(int x=-2;x<=2;x++)try {
   Coord tile=center.add(x,y);String path=map.tilesetr(map.gettile(tile)).name;
   if(path.equals("gfx/tiles/water")||path.equals("gfx/tiles/deep")){Coord2d at=Coord2d.of(tile).mul(MCache.tilesz).add(MCache.tilesz.div(2));if(water==null||at.dist(gui.map.player().rc)<water.dist(gui.map.player().rc))water=at;}
  }catch(Loading ignored){}
  if(source==null&&water==null){gui.ui.msg(hearthborn.L10n.tr("You must be near tile or barrel with fresh water to refill drinks!"));return;}
  Gob barrel=source;Coord2d target=barrel==null?water:barrel.rc;
  for(GItem item:owned(gui))try {
   if(!(item.parent instanceof Inventory))continue;DrinkSupply supply=new DrinkSupply();supply.add(item);if(supply.capacity<=0||supply.tea>0||supply.springWater||supply.water>=supply.capacity)continue;
   Inventory inventory=(Inventory)item.parent;Coord cell=null;for(Widget w=inventory.child;w!=null;w=w.next)if(w instanceof WItem&&((WItem)w).item==item)cell=w.c.sub(1,1).div(Inventory.sqsz);
   if(cell==null)continue;Coord original=cell;int[] sequence={item.infoseq};
   add(()->item.wdgmsg("take",Coord.z),()->gui.vhand!=null&&gui.vhand.item==item,2);
   add(()->{sequence[0]=item.infoseq;Coord at=target.floor(OCache.posres);if(barrel==null)gui.map.wdgmsg("itemact",Coord.z,at,0);else gui.map.wdgmsg("itemact",Coord.z,at,UI.MOD_META,0,(int)barrel.id,at,0,-1);},()->item.infoseq!=sequence[0],10);
   add(()->inventory.wdgmsg("drop",original),()->gui.vhand==null,2);
  }catch(Loading ignored){}
 }
 private static class ContainerLookProbe {
  Gob gob;ContainerLookProbe(Gob gob){this.gob=gob;}
  boolean water(){Object look=gob.getattr(hearthborn.storage.ContainerLook.class);String contents=GameObjects.field(look,"observedContents",String.class);return contents!=null&&(contents.toLowerCase(Locale.ROOT).contains("water")||contents.toLowerCase(Locale.ROOT).contains("вода"));}
 }
 public static boolean wildTarget(GameUI gui,Gob gob) {
  if(gob==null||gob.removed||gob.rc==null||gob.getattr(Following.class)!=null||gui.ui.sess.glob.party.memb.containsKey(gob.id))return false;
  String path=name(gob);
  if(!path.startsWith("gfx/kritter/")||path.startsWith("gfx/kritter/tick/")||
      path.startsWith("gfx/kritter/chicken/")||path.startsWith("gfx/kritter/dog/")||path.startsWith("gfx/kritter/cat/"))return false;
  GobHealth health=gob.getattr(GobHealth.class);
  if(health!=null&&health.hp<=0||!ScriptRunner.standing(gob))return false;
  try {
   if(hearthborn.map.RadarIcons.domestic(gob)!=null)return false;
   // Cattle and sheep share a body between wild and domestic animals; wait for the identifying model.
   if(path.equals("gfx/kritter/cattle/cattle"))
    return hearthborn.map.RadarIcons.identity(gob).contains("gfx/kritter/cattle/aurochs");
   if(path.equals("gfx/kritter/sheep/sheep"))
    return hearthborn.map.RadarIcons.identity(gob).contains("gfx/kritter/sheep/mouflon");
   return true;
  } catch(Loading pending) {
   return false;
  }
 }
 private void aggroNearestWild() {
  List<Gob> animals=near(gob->wildTarget(gui,gob),Double.POSITIVE_INFINITY);
  if(animals.isEmpty()){
   gui.ui.msg(hearthborn.L10n.tr("No wild animal nearby."));
   return;
  }
  Gob target=animals.get(0);
  add(()->hearthborn.combat.WildAttack.request(gui,target));
 }
 private void aggro(String action) {
  boolean all=action.equals("AGGRO_ALL"),players=action.equals("AGGRO_ONE_PVP");
  List<Gob> targets=near(g->{String path=name(g);return (all?path.startsWith("gfx/kritter/")||path.startsWith("gfx/borka/"):players?path.startsWith("gfx/borka/"):path.startsWith("gfx/kritter/"))&&!gui.ui.sess.glob.party.memb.containsKey(g.id);},165);
  if(!all)targets.sort(Comparator.comparingDouble(gob->{try{Coord3f screen=gui.map.screenxf(gob.rc);return screen==null?Double.MAX_VALUE:screen.round2().dist(gui.map.rootxlate(gui.ui.mc));}catch(Loading loading){return Double.MAX_VALUE;}}));
  for(Gob target:targets){add(()->{gui.act("atk");click(target,1,0);});if(!all)break;}
  add(()->gui.map.wdgmsg("click",Coord.z,gui.map.player().rc.floor(OCache.posres),3,0));
 }
}
