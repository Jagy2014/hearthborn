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
import java.util.regex.*;
/**
 * Only the selected world name is retained; online and load are not collected.
 *
 * @author Jagy2014
 */
public final class WorldChoices {
 private static final Pattern CHOICE=Pattern.compile("^(?:Alternate world|Main world): (.+) [(][0-9][0-9, ]* players, [0-9.]+% load[)]$");
 public static void observe(UI ui){
  if(ui==null||ui.sess==null)return;
  for(CheckBox option:ui.root.children(CheckBox.class))if(option.a){
   Matcher choice=CHOICE.matcher(option.originalLabel);
   if(choice.matches())WorldIdentity.select(ui.sess,choice.group(1));
  }
 }
}
