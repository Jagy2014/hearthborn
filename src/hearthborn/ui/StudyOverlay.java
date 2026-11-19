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
import haven.resutil.Curiosity;
import java.util.Objects;

/**
 * @author Jagy2014
 */
public final class StudyOverlay implements Disposable {
    private String value;
    private Tex text;
    public boolean draw(GOut g, GItem item, Coord size) {
        if(!StudyProgress.isStudy(item)) return false;
        try {
            Curiosity curiosity = ItemInfo.find(Curiosity.class, item.info());
            if(curiosity == null) return false;
            double remaining = StudyProgress.remaining(curiosity);
            if(remaining < 0) return true;
            String value = StudyProgress.shortTime(remaining);
            if(!Objects.equals(value, this.value)) {
                dispose(); this.value = value; text = new TexI(Utils.outline2(Text.render(value).img, java.awt.Color.BLACK));
            }
            g.chcolor(0, 0, 0, 150);
            g.frect(Coord.of(0, size.y - text.sz().y), Coord.of(size.x, text.sz().y));g.chcolor();
            g.aimage(text, Coord.of(size.x / 2, size.y), .5, 1);
            return true;
        } catch(Loading loading) { return true; }
    }
    public void dispose() { if(text != null) {text.dispose();text = null;} }
}
