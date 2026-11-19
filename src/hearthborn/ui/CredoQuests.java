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

import java.util.Locale;

/**
 * Credo artifact tasks use the wording from the credo list, not the short server line.
 *
 * @author Jagy2014
 */
public final class CredoQuests {
    private static final String[][] ARTIFACTS = {
        {"gem hunter", ""},
        {"pearl diver", "Create an artifact of exploration/constitution/psyche"},
        {"lumberjack", "Craft an artifact of Strength, Lore, or Carpentry"},
        {"fisherman", "Create and gild an artifact of will/intelligence/survival"},
        {"forager", "Create and gild an artifact of exploration/survival/perception"},
        {"hunter", "Create and gild an artifact for marksmanship/agility"},
        {"farmer", "Create and gild an artifact of farming/perception/lore"},
        {"strider", "Create and gild an artifact of marksmanship/survival/will"},
        {"gardener", "Create and gild an artifact of dexterity/lore/farming"},
        {"potter", "Make an artifact for dexterity/masonry/perception"},
        {"scholar", "Create an artifact of lore/intelligence"},
        {"cook", "Create an artifact of lore/intelligence"},
        {"herder", "Create an artifact for Charisma, Exploration or Farming"}
    };

    private CredoQuests() {}

    /** Returns the wiki objective when this credo has one artifact task. Otherwise the server text. */
    public static String objective(String title, String desc) {
        if(desc == null || title == null || !artifactTask(desc)) return desc;
        String lower = title.toLowerCase(Locale.ROOT);
        for(String[] row : ARTIFACTS) {
            if(!lower.contains(row[0])) continue;
            return row[1].isEmpty() ? desc : row[1];
        }
        return desc;
    }

    private static boolean artifactTask(String desc) {
        String lower = desc.toLowerCase(Locale.ROOT);
        return lower.startsWith("create an artifact") || lower.startsWith("create and gild an artifact")
            || lower.startsWith("craft an artifact") || lower.startsWith("make an artifact");
    }
}
