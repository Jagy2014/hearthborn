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

package hearthborn.cheese;
import haven.*;
import hearthborn.L10n;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
/**
 * Small cards using the same routes, icons, yields and FEPs as the cheese chart.
 *
 * @author Jagy2014
 */
public final class CheeseFutureTip implements Indir<Tex>,Disposable {
 private final List<CheeseAging.Step> steps=new ArrayList<>();
 private final Map<String,BufferedImage> images=new HashMap<>();
 private Tex texture;private boolean pending;private double retry;
 private final boolean terminal;
 private final boolean aging;
 public CheeseFutureTip(String kind,String expected,CheeseAging.Loc location){this(kind,expected,location,false);}
 public CheeseFutureTip(String kind,String expected,CheeseAging.Loc location,boolean aging){
  this.aging=aging;
  terminal=kind!=null&&!CheeseAging.isCurd(kind)&&CheeseAging.feps(kind).length>0&&CheeseAging.continuations(kind).isEmpty();
  if(expected!=null)for(CheeseAging.Step s:CheeseAging.continuations(kind))if(s.to.equals(expected)&&s.loc==location){steps.add(s);break;}
  collect(expected==null?kind:expected,new HashSet<>());
 }
 private void collect(String kind,Set<String> seen){if(!seen.add(kind))return;for(CheeseAging.Step step:CheeseAging.continuations(kind)){steps.add(step);collect(step.to,seen);}}
 public boolean empty(){return steps.isEmpty()&&!terminal;}
 private BufferedImage icon(String[] paths,int size){
  for(String path:paths){String key=path+"@"+size;if(images.containsKey(key))return images.get(key);
   try {Resource res;try{res=Resource.local().load(path).get();}catch(Loading loading){throw loading;}catch(RuntimeException missing){res=Resource.remote().load(path).get();}
    Resource.Image image=res.layer(Resource.imgc);if(image!=null){BufferedImage scaled=path.startsWith("gfx/hud/calendar/")?CheeseChart.locationImage(image.img,Coord.of(size,size)):PUtils.convolvedown(image.img,Coord.of(size,size),CharWnd.iconfilter);images.put(key,scaled);return scaled;}
   }catch(Loading loading){pending=true;return null;}catch(RuntimeException missing){}
  }return null;
 }
 private void image(Graphics2D g,String[] paths,int x,int y,int size){BufferedImage image=icon(paths,size);if(image!=null)g.drawImage(image,x,y,null);}
 private void text(Graphics2D g,String value,int x,int y,Color color){g.drawImage(Text.render(value,color).img,x,y,null);}
 private static String hours(int h){if(!"ru".equals(L10n.language()))return h+(h==1?" hour":" hours");int n=h%100,d=n%10;return L10n.format(n>=11&&n<=14?"%d hours":d==1?"%d hour":d>=2&&d<=4?"%d hours (few)":"%d hours",h);}
 public Tex get(){
  if(texture!=null&&(!pending||Utils.rtime()<retry))return texture;
  if(terminal)return terminalTip();
  pending=false;int cw=UI.scale(220),pad=UI.scale(6),row=UI.scale(18);
  Map<Integer,List<Choice>> columns=stageChoices();
  for(List<Choice> choices:columns.values())for(Choice choice:choices)cw=Math.max(cw,locationWidth(choice)+2*pad);
  int height=2*pad+row;
  for(List<Choice> choices:columns.values()){
   int needed=2*pad+row;
   for(int i=0;i<choices.size();i++)needed+=UI.scale(26)+locationHeight(choices.get(i))+CheeseAging.feps(choices.get(i).step.to).length*row+(i>0?row+UI.scale(8):0);
   height=Math.max(height,needed);
  }
  int contentHeight=height,totalWidth=Math.max(1,columns.size())*cw;
  Text instruction=Text.std.renderwrap(L10n.tr(aging?"Wait until the cheese is ready before moving the tray.":"To start the next aging stage, remove the cheese tray from its rack and place it in a rack at the required location."),totalWidth-2*pad);
  height+=instruction.sz().y+2*pad;
  BufferedImage canvas=TexI.mkbuf(Coord.of(totalWidth,height));Graphics2D g=canvas.createGraphics();
  int column=0;
  for(Map.Entry<Integer,List<Choice>> entry:columns.entrySet()){
   int x=column*cw+pad,y=pad;
   if(column>0){g.setColor(new Color(170,150,90,180));g.drawLine(x-pad,0,x-pad,contentHeight);}
   text(g,L10n.tr(heading(column,aging))+" "+entry.getKey(),x,y,Color.WHITE);y+=row;
   int choice=0;
   for(Choice option:entry.getValue()){
    CheeseAging.Step s=option.step;
    if(choice++>0){y+=UI.scale(4);text(g,L10n.tr("or"),x,y,new Color(190,190,190));y+=row+UI.scale(4);}
    int locationX=x,locationIndex=0;
    for(CheeseAging.Loc loc:option.locations){
     if(locationIndex++>0){String separator=" "+L10n.tr("or")+" ";text(g,separator,locationX,y,Color.WHITE);locationX+=Text.std.strsize(separator).x;}
     image(g,CheeseAging.locIcons(loc),locationX,y,UI.scale(26));locationX+=UI.scale(30);
     String name=CheeseAging.locLabel(loc);text(g,name,locationX,y,Color.WHITE);locationX+=Text.std.strsize(name).x;
    }
    text(g," ("+hours(s.hours)+")",locationX,y,Color.WHITE);y+=locationHeight(option);
    image(g,CheeseAging.icons(s.to,s.milk),x,y,UI.scale(24));
    int pieces=CheeseAging.pieces(s.to);
    text(g,CheeseAging.localizeName(s.to)+(pieces>0?" ×"+pieces:""),x+UI.scale(28),y,Color.WHITE);y+=UI.scale(26);
    for(CheeseAging.Fep f:CheeseAging.feps(s.to)){
     image(g,f.statRes(),x,y,UI.scale(16));text(g,f.base().toUpperCase(Locale.ROOT)+(f.plus2()?"+2":"")+" – "+f.a,x+UI.scale(20),y,f.plus2()?new Color(255,215,0):Color.WHITE);y+=row;
    }
   }
   column++;
  }
  g.setColor(new Color(170,150,90,180));g.drawLine(pad,contentHeight,totalWidth-pad,contentHeight);
  g.drawImage(instruction.img,pad,contentHeight+pad,null);
  g.dispose();if(texture!=null)texture.dispose();texture=new TexI(canvas);retry=Utils.rtime()+.5;return texture;
 }
 /** The first column is the stage the tray is aging into now while it ages, and the stage to start once it is ready; the others come after. */
 static String heading(int column,boolean aging){return column==0?(aging?"Expected stage":"Next stage"):"Then stage";}
 Map<Integer,List<CheeseAging.Step>> stageColumns(){
  Map<Integer,List<CheeseAging.Step>> columns=new TreeMap<>();
  for(CheeseAging.Step step:steps)columns.computeIfAbsent(CheeseAging.stageOf(step.to),key->new ArrayList<>()).add(step);
  return columns;
 }
 static int locationHeight(Choice choice){return UI.scale(30);}
 static int locationWidth(Choice choice){
  return Text.std.strsize(choice.locationText()+" ("+hours(choice.step.hours)+")").x+choice.locations.size()*UI.scale(30);
 }
 static final class Choice{
  final CheeseAging.Step step;
  final Set<CheeseAging.Loc> locations=new LinkedHashSet<>();
  Choice(CheeseAging.Step step){this.step=step;locations.add(step.loc);}
  String locationText(){List<String> names=new ArrayList<>();for(CheeseAging.Loc loc:locations)names.add(CheeseAging.locLabel(loc));return String.join(" "+L10n.tr("or")+" ",names);}
 }
 Map<Integer,List<Choice>> stageChoices(){
  Map<Integer,List<Choice>> columns=new TreeMap<>();
  for(Map.Entry<Integer,List<CheeseAging.Step>> entry:stageColumns().entrySet()){
   Map<String,Choice> choices=new LinkedHashMap<>();
   for(CheeseAging.Step step:entry.getValue()){
    String key=step.to+"\n"+step.hours;
    Choice choice=choices.computeIfAbsent(key,ignored->new Choice(step));choice.locations.add(step.loc);
   }
   columns.put(entry.getKey(),new ArrayList<>(choices.values()));
  }
  return columns;
 }
 private Tex terminalTip(){
  pending=false;int pad=UI.scale(6),row=UI.scale(20),width=UI.scale(300);
  Text explanation=Text.std.renderwrap((aging?L10n.tr("Wait until the cheese is ready before moving the tray.")+"\n\n":"")+L10n.tr("No further stages. Changing the aging location produces Generic Gouda."),width-2*pad);
  BufferedImage canvas=TexI.mkbuf(Coord.of(width,explanation.sz().y+UI.scale(90)+2*pad));Graphics2D g=canvas.createGraphics();
  g.drawImage(explanation.img,pad,pad,null);int y=pad+explanation.sz().y+UI.scale(10);
  image(g,CheeseAging.icons(CheeseAging.GOUDA,CheeseAging.Milk.COW),pad,y,UI.scale(24));
  text(g,CheeseAging.localizeName(CheeseAging.GOUDA),pad+UI.scale(28),y,Color.WHITE);y+=UI.scale(28);
  for(CheeseAging.Fep stat:CheeseAging.feps(CheeseAging.GOUDA)){
   image(g,stat.statRes(),pad,y,UI.scale(16));
   text(g,stat.base().toUpperCase(Locale.ROOT)+(stat.plus2()?"+2":"")+" – "+stat.a,pad+UI.scale(20),y,stat.plus2()?new Color(255,215,0):Color.WHITE);y+=row;
  }
  g.dispose();if(texture!=null)texture.dispose();texture=new TexI(canvas);retry=Utils.rtime()+.5;return texture;
 }
 public void dispose(){if(texture!=null)texture.dispose();texture=null;images.clear();}
}
