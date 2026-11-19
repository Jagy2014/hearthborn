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
import hearthborn.L10n;
/**
 * @author Jagy2014
 */
public final class InheritanceHelp {
 public static String summary(){return "ru".equals(L10n.language())?L10n.tr("Inheritance summary"):"Inheritance restores spent LP and FEP: 15% without burial, 25% for the skull, 35% for the body without its skull, 45% for an intact burial. The ancestor must have lived at least eight hours.";}
 public static Window open(UI ui, Runnable continued, Runnable closed){
  String text="ru".equals(L10n.language())?L10n.tr("Inheritance help"):"Create a new character. Before entering the fire, right-click your ancestor's gravestone in the starting area and choose to inherit.\n\n"+summary()+"\n\nA state funeral grants 45% without recovering the body. At an Ancestral Shrine, Lamenting the Departed provides inheritance in steps of 6%, up to 45%; its Numen cost starts at 1 and increases by 2 per use.\n\nPrayers can restore remembered people (without kin status), known Thingwalls and discoveries for 5 Numen each. Memories of War restores one tier of combat moves per prayer for 3 Numen.\n\nYou normally return to your ancestor's Hearth Fire, if it survives. Choosing a Wilderness Beacon overrides that destination. Inheritance can also pass on your ancestor's claim.";
  Window window=new Window(UI.scale(460,330),"Inheritance"){
   public void wdgmsg(Widget sender,String message,Object... args){if(sender==this&&message.equals("close")){destroy();closed.run();return;}super.wdgmsg(sender,message,args);}
  };
  window.add(new RichTextBox(UI.scale(460,330),RichText.Parser.quote(text)),Coord.z);
  window.adda(new Button(UI.scale(200), "Continue").action(() -> {window.destroy();continued.run();}), UI.scale(230), UI.scale(338), 0.5, 0);
  window.pack();
  ui.root.add(window,ui.root.sz.sub(window.sz).div(2).max(Coord.z));window.raise();return window;
 }
}
