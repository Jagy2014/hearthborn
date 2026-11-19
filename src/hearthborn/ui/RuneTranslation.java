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
import hearthborn.L10n;
/**
 * @author Jagy2014
 */
public final class RuneTranslation {
 public static String label(String source){
  if(source!=null&&source.contains("deyr")&&source.contains("deyja"))return source+"\n"+L10n.tr("Cattle die, kinsmen die, you yourself will die.");
  return L10n.text("label",source);
 }
}
