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

package hearthborn.input;
import haven.*;
import hearthborn.ui.GlobalCommands;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
/**
 * global action catalog, persisted through native KeyBinding.
 *
 * @author Jagy2014
 */
public final class GlobalKeys {
 public static final class Action {
  public final String id,name;public final KeyBinding binding;
  Action(String id,String name,int code,int mods){this.id=id;this.name=name;binding=KeyBinding.get("hearthborn/global/"+id,code==0?KeyMatch.nil:KeyMatch.forcode(code,mods));}
 }
 private static final List<Action> actions=new ArrayList<>();
 private static void add(String id,String name,int code,int mods){actions.add(new Action(id,name,code,mods));}
 static {
        add("ACT_SHOOT","Shoot target",KeyEvent.VK_6,0);
        add("ACT_HAND_0","Left hand",KeyEvent.VK_1,KeyMatch.C);
        add("ACT_HAND_1","Right hand",KeyEvent.VK_2,KeyMatch.C);
        add("ACT_BELT","Belt",KeyEvent.VK_3,KeyMatch.C);
        add("ACT_POUCH_0","Left pouch",0,0);
        add("ACT_POUCH_1","Right pouch",0,0);
        add("BOT_PICK_ALL_HERBS","Auto-pick stuff",KeyEvent.VK_Q,KeyMatch.M);
        add("ACT_DRINK","Drink",KeyEvent.VK_D,KeyMatch.M);
        add("ACT_REFILL_DRINKS","Refill drinks",0,0);
        add("BOT_CELLAR_DIGGING","Cellar Digging Bot",0,0);
        add("BOT_ROASTING_SPIT","Roasting Spit Bot",0,0);
        add("BOT_CORACLE","Use or pick up coracle",0,0);
        add("BOT_SKIS","Use or pick up skis",0,0);
        add("BOT_CHIP_STONE","Chip stone",0,0);
        add("BOT_OPEN_GATE","Toggle closest gate",KeyEvent.VK_W,KeyMatch.M);
        add("BOT_MOUNT_HORSE","Mount nearest domestic horse",KeyEvent.VK_Q,KeyMatch.C);
        add("OPEN_QUICK_CRAFT","Open craft list",KeyEvent.VK_C,KeyMatch.M);
        add("OPEN_QUICK_BUILD","Open building list",KeyEvent.VK_B,KeyMatch.M);
        add("OPEN_QUICK_ACTION","Open actions list",KeyEvent.VK_A,KeyMatch.M);
        add("OPEN_CRAFT_DB","Open crafting DB",KeyEvent.VK_X,KeyMatch.M);
        add("LAST_CRAFT","Repeat last craft",KeyEvent.VK_C,KeyMatch.M | KeyMatch.S);
        add("OPEN_ALCHEMY_DB","Open alchemy",0,0);
        add("OPEN_QUEST_HELP","Open quest helper",0,0);
        add("TOGGLE_CURSOR","Toggle cursor item",KeyEvent.VK_H,KeyMatch.M);
        add("TOGGLE_STUDY","Toggle study window",KeyEvent.VK_S,KeyMatch.M);
        add("FILTER","Show item filter",KeyEvent.VK_F,KeyMatch.M);
        add("SORT_INVENTORY","Sort all opened inventories",0,0);
        add("TOGGLE_INSPECT","Toggle inspect mode",0,0);
        add("TOGGLE_HIDE_TREES","Hide trees",0,0);
        add("TOGGLE_HIDE_OBJECTS","Hide chosen objects",0,0);
        add("TOGGLE_AUTO_DROP","Toggle auto-drop of items",KeyEvent.VK_A,KeyMatch.S);
        add("TOGGLE_GOB_INFO","Display info",KeyEvent.VK_I,KeyMatch.M);
        add("TOGGLE_GOB_HITBOX","Display hitboxes",KeyEvent.VK_H,KeyMatch.C);
        add("TOGGLE_GOB_RADIUS","Display radius",KeyEvent.VK_R,KeyMatch.M);
        add("TOGGLE_ORE_HIGHLIGHT","Highlight ore tiles",0,0);
        add("TOGGLE_TILE_CENTERING","Toggle Tile Centering",0,0);
        add("TOGGLE_PEACE","Toggle Peace",0,0);
        add("AGGRO_ONE_PVE","Aggro closest non-player to cursor",0,0);
        add("AGGRO_ONE_PVP","Aggro closest player to cursor",0,0);
        add("AGGRO_ALL","Aggro all creatures near player",0,0);
        add("EQUIP_BOW","Equip Bow",0,0);
        add("EQUIP_SPEAR","Equip Boar Spear",0,0);
        add("EQUIP_SWORD_N_BOARD","Equip Sword & Shield",0,0);
        add("EQUIP_T_SACKS","Equip Travel Sacks",0,0);
        add("CAM_ZOOM_IN","Camera zoom in",KeyEvent.VK_ADD,0);
        add("CAM_ZOOM_OUT","Camera zoom out",KeyEvent.VK_SUBTRACT,0);
        add("CAM_ROTATE_LEFT","Camera move left",KeyEvent.VK_LEFT,0);
        add("CAM_ROTATE_RIGHT","Camera move right",KeyEvent.VK_RIGHT,0);
        add("CAM_ROTATE_UP","Camera move up",KeyEvent.VK_UP,0);
        add("CAM_ROTATE_DOWN","Camera move down",KeyEvent.VK_DOWN,0);
        add("CAM_SNAP_WEST","Camera snap west",KeyEvent.VK_LEFT,KeyMatch.C);
        add("CAM_SNAP_EAST","Camera snap east",KeyEvent.VK_RIGHT,KeyMatch.C);
        add("CAM_SNAP_NORTH","Camera snap north",KeyEvent.VK_UP,KeyMatch.C);
        add("CAM_SNAP_SOUTH","Camera snap south",KeyEvent.VK_DOWN,KeyMatch.C);
        add("CAM_RESET","Camera reset",KeyEvent.VK_HOME,0);
 }
 public static KeyBinding binding(String id){for(Action action:actions)if(action.id.equals(id))return action.binding;throw new IllegalArgumentException(id);}
 public static List<Action> actions(){return Collections.unmodifiableList(actions);}
 private static final Map<KeyBinding,String> listed=new IdentityHashMap<>();
 /** Remembers the name of a shortcut shown in the lists, so a clash can tell which one already holds the key. */
 public static void name(KeyBinding binding,String name){synchronized(listed){listed.putIfAbsent(binding,name);}}
 /** The combat tab has its own world: those keys work only in a fight, so they are compared with each other only. */
 private static boolean combat(KeyBinding binding){
  if(binding==binding("ACT_SHOOT")||binding==Fightsess.kb_relcycle)return true;
  for(KeyBinding action:Fightsess.kb_acts)if(action==binding)return true;
  return false;
 }
 private static String combatName(KeyBinding binding){
  if(binding==binding("ACT_SHOOT"))return "Shoot target";
  if(binding==Fightsess.kb_relcycle)return "Switch targets";
  for(int i=0;i<Fightsess.kb_acts.length;i++)if(Fightsess.kb_acts[i]==binding)return String.format("Combat action %d",i+1);
  return null;
 }
 /** The name of the shortcut of the same tab that already holds {@code key}, or null when the key is free for {@code binding}. */
 /**
  * What makes two shortcuts the same key: the key and its modifiers. The name it is shown by does not count, and neither does
  * whether it was set in the code (Option+Space) or pressed in the list (the same key, an extended match); a letter or a digit
  * is the same key as its character.
  */
 public static String signature(KeyMatch key){
  String mods=(key.modmask&KeyMatch.MODS)+"/"+(key.modmatch&key.modmask&KeyMatch.MODS);
  if(key instanceof KeyMatch.CodeMatch){
   int code=((KeyMatch.CodeMatch)key).code;
   if(code>='0'&&code<='9'||code>='A'&&code<='Z'||code==' ')return mods+"/c"+(char)code;
   return mods+"/k"+code;
  }
  if(key instanceof KeyMatch.CharMatch){
   // A Russian letter on the key is the same key as the Latin letter of that key, which is how it is matched when pressed.
   char letter=Character.toUpperCase(((KeyMatch.CharMatch)key).chr),latin=LayoutKeys.latin(letter);
   return mods+"/c"+(latin!=0?latin:letter);
  }
  return mods+"/"+KeyMatch.reduce(key);
 }
 public static String holder(KeyBinding binding,KeyMatch key){
  if(key==null||key==KeyMatch.nil)return null;
  String wanted=signature(key);
  List<KeyBinding> others=new ArrayList<>();
  if(combat(binding)){
   others.add(binding("ACT_SHOOT"));others.add(Fightsess.kb_relcycle);others.addAll(Arrays.asList(Fightsess.kb_acts));
  }else{
   for(Action action:actions)if(!action.id.equals("ACT_SHOOT"))others.add(action.binding);
   synchronized(listed){others.addAll(listed.keySet());}
  }
  for(KeyBinding other:others){
   if(other==binding||other.key()==KeyMatch.nil||combat(other)!=combat(binding))continue;
   if(signature(other.key()).equals(wanted))return combat(other)?combatName(other):nameOf(other);
  }
  return null;
 }
 private static String nameOf(KeyBinding binding){
  for(Action action:actions)if(action.binding==binding)return action.name;
  synchronized(listed){return listed.getOrDefault(binding,binding.id);}
 }
 /** Assigns the key unless another shortcut of the same tab already has it; then says so and changes nothing. */
 public static boolean assign(UI ui,KeyBinding binding,KeyMatch key) {
  String taken=holder(binding,key);
  if(taken!=null){
   if(ui!=null)ui.error(hearthborn.L10n.format("%s is already used by \"%s\"",key.name(),hearthborn.L10n.tr(taken)));
   return false;
  }
  binding.set(key);
  return true;
 }
 public static boolean assign(KeyBinding binding,KeyMatch key){return assign(null,binding,key);}
 public static boolean matches(KeyMatch key,int code,int modifiers,Widget.GlobKeyEvent event) {
  if(key==KeyMatch.nil)return false;
  if(key instanceof KeyMatch.CodeMatch) {
   if((modifiers&KeyMatch.SUPER)!=0)modifiers=(modifiers&~KeyMatch.SUPER)|KeyMatch.M;
   KeyMatch.CodeMatch match=(KeyMatch.CodeMatch)key;
   return match.code==code && (modifiers&match.modmask)==match.modmatch;
  }
  return event!=null&&key.match(event);
 }
 public static boolean handle(UI ui,Widget.GlobKeyEvent event) {
  GameUI gui=ui.root.getchild(GameUI.class);if(gui==null)return false;
  for(Action action:actions)if(matches(action.binding.key(),event.code,event.mods,event)) {
   try{
    if(action.id.equals("ACT_SHOOT")){if(!hearthborn.combat.BowCombat.key(gui,event))continue;}
    else GlobalCommands.run(gui,action.id);
   }catch(Loading loading){ui.msg(hearthborn.L10n.tr("Items are still loading. Try again."));}
   return true;
  }
  return false;
 }
 public static OptWnd.Panel panel(OptWnd options) {
  OptWnd.Panel panel=options.new Panel();Tabs tabs=new Tabs(Coord.of(0,UI.scale(32)),UI.scale(400,380),panel);
  Tabs.Tab original=tabs.add(),general=tabs.add(),combat=tabs.add();
  Button[] tabButtons={
   panel.add(new Button(UI.scale(120),"Original",false).action(()->tabs.showtab(original)),Coord.z),
   panel.add(new Button(UI.scale(120),"Additional",false).action(()->tabs.showtab(general)),Coord.z),
   panel.add(new Button(UI.scale(120),"Combat keys",false).action(()->tabs.showtab(combat)),Coord.z)};
  // Side by side by their real width: a translated caption makes a button wider than it was asked to be.
  for(int x=0,i=0;i<tabButtons.length;i++){tabButtons[i].move(Coord.of(x,0));x+=tabButtons[i].sz.x+UI.scale(4);}
  OptWnd.BindingPanel nativeBindings=options.new BindingPanel(options.main);
  for(Widget child:new ArrayList<>(nativeBindings.children()))if(child instanceof OptWnd.PButton)child.destroy();
  nativeBindings.pack();original.add(nativeBindings,UI.scale(10,0));nativeBindings.show();
  Scrollport scroll=general.add(new Scrollport(UI.scale(400,350)),Coord.z);int y=0;
  for(Action action:actions)if(!action.id.equals("ACT_SHOOT")){y=row(scroll.cont,action.name,action.binding,y);}
  scroll.cont.pack();
  Scrollport fighting=combat.add(new Scrollport(UI.scale(400,350)),Coord.z);y=0;
  for(int i=0;i<Fightsess.kb_acts.length;i++){y=row(fighting.cont,String.format("Combat action %d",i+1),Fightsess.kb_acts[i],y);}
  y=row(fighting.cont,"Switch targets",Fightsess.kb_relcycle,y);
  row(fighting.cont,"Shoot target",binding("ACT_SHOOT"),y);fighting.cont.pack();
  Button back=panel.add(options.new PButton(UI.scale(200),"Back",27,options.main),UI.scale(120,425));panel.pack();
  // In the settings window the lists take all the room of the pane. The classic small window keeps its sizes.
  OptWnd.BindingPanel[] bindings=new OptWnd.BindingPanel[]{nativeBindings};
  panel.fitting(width->{
   if(options.pane==null)return;
   int top=UI.scale(32),height=options.pane.sz.y-top-UI.scale(12),listWidth=width;
   tabs.resize(Coord.of(listWidth,height));
   bindings[0].destroy();
   int pointer=UI.scale(10)+UI.scale(24);
   bindings[0]=options.new BindingPanel(options.main,Coord.of(listWidth,height-pointer-UI.scale(4)));
   for(Widget child:new ArrayList<>(bindings[0].children()))if(child instanceof OptWnd.PButton)child.destroy();
   bindings[0].pack();original.add(bindings[0],Coord.z);bindings[0].show();
   for(Scrollport list:new Scrollport[]{scroll,fighting}){list.resize(Coord.of(listWidth,height));list.cont.update();}
   back.move(Coord.z);
   panel.pack();
  });
  return panel;
 }
 private static int row(Widget parent,String name,KeyBinding binding,int y) {
  Text text=Text.std.renderwrap(hearthborn.L10n.tr(name),UI.scale(200));
  Widget label=parent.add(new Img(new TexI(text.img)),Coord.of(UI.scale(5),y+UI.scale(5)));
  KeyMatch.Capture button=parent.add(new KeyMatch.Capture(UI.scale(165),binding.key()) {
   public void set(KeyMatch key){if(assign(ui,binding,key))super.set(key);else super.set(binding.key());}
   protected boolean handle(KeyDownEvent event){if(event.code==KeyEvent.VK_BACK_SPACE){set(binding.defkey);return true;}return super.handle(event);}
   public void draw(GOut g){if(binding.key()!=key)super.set(binding.key());super.draw(g);}
  },Coord.of(UI.scale(215),y));
  button.settip(hearthborn.L10n.tr("Escape cancels; Backspace restores the default; Delete clears the shortcut."));
  return y+Math.max(label.sz.y+UI.scale(8),button.sz.y+UI.scale(4));
 }
}
