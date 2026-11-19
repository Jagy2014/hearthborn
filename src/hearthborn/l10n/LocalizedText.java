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

package hearthborn.l10n;

import haven.*;

/**
 * Keep original item names for scripts; translate only their rendered image.
 *
 * @author Jagy2014
 */
public final class LocalizedText extends Text {
    public final String displayText;
    public LocalizedText(String original, Text display) { super(original, display.img); this.displayText = display.text; }

    public static String shown(Text text) {
        return text instanceof LocalizedText ? ((LocalizedText)text).displayText : text.text;
    }

    public static Text item(ItemInfo.Owner owner, String original) {
        String translated;
        if(owner instanceof ItemInfo.ResOwner) {
            Resource res = ((ItemInfo.ResOwner)owner).resource();
            Resource.Tooltip base = res.layer(Resource.tooltip);
            if(base != null && original.equals(base.t)) original = base.original;
            boolean realmName = res.name.equals("gfx/hud/buffs/realm") && base != null && !original.equals(base.original);
            translated = original.equals("Clay Jar") ? hearthborn.L10n.text("tooltip",original)
                : realmName ? original
                : base != null && !original.equals(base.original)
                ? hearthborn.L10n.text("tooltip", original)
                : hearthborn.L10n.resource("tooltip", res.name, original);
        } else translated = hearthborn.L10n.text("tooltip", original);
        java.util.regex.Matcher doses=java.util.regex.Pattern.compile("(?i)^([0-9]+) doses? of (.+)$").matcher(original);
        if(doses.matches())translated=hearthborn.L10n.format("%s (%s doses)",hearthborn.L10n.text("ingredient",doses.group(2)),doses.group(1));
        return new LocalizedText(OriginalNames.item(owner, original), Text.render(translated));
    }
}
