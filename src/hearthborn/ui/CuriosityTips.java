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
import java.awt.image.BufferedImage;

/**
 * @author Jagy2014
 */
public final class CuriosityTips {
    public static BufferedImage render(haven.resutil.Curiosity curiosity) {
        StringBuilder buf = new StringBuilder();
        if(curiosity.exp > 0) buf.append(hearthborn.L10n.tr("Learning points:")).append(" $col[192,192,255]{").append(Utils.thformat(curiosity.exp)).append("}\n");
        if(curiosity.time > 0) buf.append(hearthborn.L10n.tr("Study time:")).append(" $col[192,255,192]{")
            .append(hearthborn.ui.StudyProgress.longTime(hearthborn.ui.StudyProgress.realSeconds(curiosity.time))).append("}\n");
        double remaining = hearthborn.ui.StudyProgress.remaining(curiosity);
        if(remaining >= 0) buf.append(hearthborn.L10n.tr("Remaining:")).append(" $col[255,224,192]{")
            .append(hearthborn.ui.StudyProgress.longTime(remaining)).append("}\n");
        if(curiosity.time > 0) {
            double rate = hearthborn.ui.StudyProgress.perHour(curiosity);
            buf.append(hearthborn.L10n.tr("LP/hour:")).append(" $col[192,255,255]{").append(Utils.thformat(Math.round(rate))).append("}\n");
            if(curiosity.mw > 0) buf.append(hearthborn.L10n.tr("LP/hour/weight:")).append(" $col[192,255,255]{")
                .append(Utils.thformat(Math.round(rate / curiosity.mw))).append("}\n");
        }
        if(curiosity.mw > 0) buf.append(hearthborn.L10n.tr("Mental weight:")).append(" $col[255,192,255]{").append(curiosity.mw).append("}\n");
        if(curiosity.enc > 0) buf.append(hearthborn.L10n.tr("Experience cost:")).append(" $col[255,255,192]{").append(curiosity.enc).append("}\n");
        return RichText.render(buf.toString(), 0).img;
    }
}
