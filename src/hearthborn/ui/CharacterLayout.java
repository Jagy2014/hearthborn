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

/**
 * Keep tab controls below the actual content, including translated fonts.
 *
 * @author Jagy2014
 */
public final class CharacterLayout {
    public static void fit(CharWnd window, Tabs tabs, Widget[] buttons) {
        Coord needed = Coord.z;
        for(Tabs.Tab tab : tabs.tabs) {
            for(Widget content : tab.children()) {
                if(!content.visible) continue;
                Coord size = content.sz;
                if(content instanceof SkillWnd)
                    size = Coord.of(size.x, ((SkillWnd)content).naturalHeight());
                needed = needed.max(content.c.add(size));
            }
        }
        int height = Math.max(UI.scale(315), needed.y);
        int width = Math.max(UI.scale(506), needed.x);
        if(window.quest != null) window.quest.fitWidth(width);
        if(window.wound != null) window.wound.fitWidth(width);
        if(window.battr != null) window.battr.fitWidth(width);
        if(window.skill != null) {
            window.skill.fitWidth(width);
            window.skill.fitHeight(height);
        }
        if(window.fight != null) window.fight.fitWidth(width);
        if(tabs.sz.equals(new Coord(width, height))) return;
        tabs.resize(new Coord(width, height));
        int total = 0, maxHeight = 0;
        for(Widget button : buttons) { total += button.sz.x; maxHeight = Math.max(maxHeight, button.sz.y); }
        int x = tabs.c.x;
        for(int i = 0; i < buttons.length; i++) {
            buttons[i].move(new Coord(x, tabs.c.y + height + UI.scale(10)));
            x += buttons[i].sz.x;
            if(i + 1 < buttons.length) x += (width - total) / (buttons.length - 1);
        }
        window.resize(new Coord(tabs.c.x + width + UI.scale(15), tabs.c.y + height + UI.scale(20) + maxHeight));
    }
    private CharacterLayout() {}
}
