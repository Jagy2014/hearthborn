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
 * Ordered native petal names; translations are only used to display and edit the list.
 *
 * @author Jagy2014
 */
public final class FlowerChoices {
 private static final String PREF="hearthborn/flowers/";
 public static final String PICK_ALL="#Pick All";
 private static final List<Rule> rules=new ArrayList<>();
 private static final Map<UI,Gob> targets=new WeakHashMap<>();
 private static final Map<UI,Boolean> itemMenus=new WeakHashMap<>();
 public static final class Rule {
  public final String name;public boolean enabled;
  Rule(String name,boolean enabled){this.name=name;this.enabled=enabled;}
 }
 static {
  for(String saved:Utils.getprefsl(PREF+"rules",new String[]{"0\tPick"})) {
   int tab=saved.indexOf('\t');if(tab>0)rules.add(new Rule(saved.substring(tab+1),saved.startsWith("1")));
  }
  if(!Utils.getprefb(PREF+"defaults-v1",false)) {
   for(String name:new String[]{"Giddyup!","Pick","Light My Fire","Open","Carve","Turn","Flay","Skin","Clean","Butcher","Collect bones"})
    if(rules.stream().noneMatch(rule->rule.name.equals(name)))rules.add(new Rule(name,false));
   save();Utils.setprefb(PREF+"defaults-v1",true);
  }
 }
 public static synchronized List<Rule> rules(){return new ArrayList<>(rules);}
 private static void save(){List<String> encoded=new ArrayList<>();for(Rule rule:rules)encoded.add((rule.enabled?"1":"0")+"\t"+rule.name);Utils.setprefsl(PREF+"rules",encoded);}
 public static synchronized String original(String input) {
  input=input.trim();if(input.equals(hearthborn.L10n.tr("Pick all")))return PICK_ALL;
  Properties names=hearthborn.l10n.Catalog.load("/hearthborn/l10n/ru/flower.properties");
  for(String key:new String[]{"Pick","Open","Harvest","Ride","Skin","Butcher","Gut","Light","Turn"})if(names.getProperty(key,"").equals(input))return key;
  for(String key:new TreeSet<>(names.stringPropertyNames()))if(names.getProperty(key).equals(input))return key.replaceAll("\\\\(.)","$1");
  return input;
 }
 public static synchronized void add(String input) {
  String name=original(input);if(name.isEmpty()||rules.stream().anyMatch(rule->rule.name.equals(name)))return;
  rules.add(new Rule(name,true));save();
 }
 public static synchronized void enable(String name,boolean enabled){for(Rule rule:rules)if(rule.name.equals(name))rule.enabled=enabled;save();}
 public static synchronized void remove(String name){rules.removeIf(rule->rule.name.equals(name));save();}
 public static synchronized void move(String name,int direction){for(int i=0;i<rules.size();i++)if(rules.get(i).name.equals(name)){int to=Utils.clip(i+direction,0,rules.size()-1);Collections.swap(rules,i,to);save();return;}}
 public static boolean single(){return Utils.getprefb(PREF+"single-ctrl",true);}
 public static void single(boolean value){Utils.setprefb(PREF+"single-ctrl",value);}
 public static boolean pickAll(){return Utils.getprefb(PREF+"pick-all",false);}
 public static void pickAll(boolean value){Utils.setprefb(PREF+"pick-all",value);}
 public static int bypass(){return Utils.getprefi(PREF+"bypass",UI.MOD_SHIFT);}
 public static void bypass(int value){Utils.setprefi(PREF+"bypass",value);}
 public static synchronized int choose(String[] options,int modifiers) {
  if(single()&&(modifiers&UI.MOD_CTRL)!=0&&options.length==1)return 0;
  if(bypass()!=0&&modifiers==bypass())return -1;
  for(Rule rule:rules)if(rule.enabled)for(int i=0;i<options.length;i++)if(rule.name.equals(options[i]))return i;
  return -1;
 }
 public static void remember(UI ui,Gob target){if(target==null)targets.remove(ui);else targets.put(ui,target);}
 public static Gob target(UI ui){return targets.remove(ui);}
 /** The next flower menu comes from an inventory item, so it must not walk to the last world object. */
 public static void itemMenu(UI ui){if(ui!=null)itemMenus.put(ui,Boolean.TRUE);}
 public static Gob menuTarget(UI ui){
  if(ui!=null && itemMenus.remove(ui)!=null){targets.remove(ui);return null;}
  return targets.remove(ui);
 }
 public static String shown(String name){return name.equals(PICK_ALL)?hearthborn.L10n.tr("Pick all"):hearthborn.L10n.text("flower",name);}
 public static OptWnd.Panel panel(OptWnd options) {
  OptWnd.Panel panel=options.new Panel();Scrollport scroll=panel.add(new Scrollport(UI.scale(396,250)),Coord.z);
  Runnable[] rebuild=new Runnable[1];rebuild[0]=()->{
   for(Widget child:new ArrayList<>(scroll.cont.children()))child.destroy();int y=0;
   for(Rule rule:rules()) {
    scroll.cont.add(new CheckBox(shown(rule.name)){{a=rule.enabled;}}.changed(value->enable(rule.name,value)),Coord.of(UI.scale(3),y+UI.scale(3)));
    final String name=rule.name;
    scroll.cont.add(new Button(UI.scale(28),"↑",false).action(()->{move(name,-1);rebuild[0].run();}),Coord.of(UI.scale(290),y));
    scroll.cont.add(new Button(UI.scale(28),"↓",false).action(()->{move(name,1);rebuild[0].run();}),Coord.of(UI.scale(322),y));
    scroll.cont.add(new Button(UI.scale(28),"×",false).action(()->{remove(name);rebuild[0].run();}),Coord.of(UI.scale(354),y));y+=UI.scale(28);
   }scroll.cont.pack();scroll.bar.ch(0);
  };rebuild[0].run();
  TextEntry entry=panel.add(new TextEntry(UI.scale(280),""),UI.scale(0,260));
  panel.add(new Button(UI.scale(105),"Add",false).action(()->{add(entry.text());entry.settext("");rebuild[0].run();}),UI.scale(290,260));
  panel.add(new Label("Hold to bypass automatic menu selection:"),UI.scale(0,300));
  SDropBox<Integer,Widget> skip=new SDropBox<Integer,Widget>(UI.scale(140),UI.scale(80),UI.scale(18)) {
   protected List<Integer> items(){return Arrays.asList(UI.MOD_SHIFT,UI.MOD_CTRL,UI.MOD_META,0);}
   protected Widget makeitem(Integer value,int index,Coord size){Widget row=new Widget(size);String name=value==UI.MOD_SHIFT?"Shift":value==UI.MOD_CTRL?"Ctrl":value==UI.MOD_META?"Alt":"None";row.add(new Label(name),Coord.z);return row;}
   public void change(Integer value){super.change(value);if(value!=null)bypass(value);}
  };skip.change(bypass());panel.add(skip,UI.scale(255,297));
  panel.add(new CheckBox("Ctrl selects a single menu option").state(FlowerChoices::single).set(FlowerChoices::single),UI.scale(0,330));
  panel.add(new CheckBox("Add Pick all to gathering menus").state(FlowerChoices::pickAll).set(FlowerChoices::pickAll),UI.scale(0,358));
  panel.add(options.new PButton(UI.scale(200),"Back",27,options.main),UI.scale(0,395));panel.pack();return panel;
 }
}
