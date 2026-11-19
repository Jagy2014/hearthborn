/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package hearthborn.ui;

import haven.*;
import java.util.*;
import java.util.function.Consumer;
import java.awt.Color;

/** Combat categories use resource identifiers, independent of interface language. */
public final class CombatCategories extends Widget {
    private Category selected = Category.All;
    public static Color usageColor(int used, int available) {
        return used == 0 ? new Color(160, 160, 160) : used >= available ? new Color(128, 255, 128) : new Color(255, 220, 100);
    }
    public CombatCategories(Consumer<Category> change) {
        super(UI.scale(250, 26));
        int x = 0;
        for(Category category : Category.values()) {
            final Tex icon;
            try {
                java.awt.image.BufferedImage image =
                    javax.imageio.ImageIO.read(CombatCategories.class.getResourceAsStream(
                    category==Category.All ? "/hearthborn/assets/icons/common/all.png" : "/hearthborn/assets/icons/combat/" + category.name().toLowerCase(java.util.Locale.ROOT) + ".png"));
                icon = new TexI(PUtils.convolvedown(image, UI.scale(20, 20), CharWnd.iconfilter));
            } catch(java.io.IOException e) { throw new RuntimeException(e); }
            Widget button = add(new Widget(UI.scale(26, 26)) {
                public void draw(GOut g) {
                    g.chcolor(selected == category ? new Color(85, 130, 55, 220) : new Color(0, 0, 0, 220));
                    g.frect(Coord.z, sz);
                    g.chcolor(new Color(180, 145, 70)); g.rect(Coord.z, sz); g.chcolor();
                    g.aimage(icon, sz.div(2), .5, .5);
                }
                public boolean mousedown(MouseDownEvent event) {
                    if(event.b != 1) return false;
                    selected = category; change.accept(category); return true;
                }
                public void dispose() { icon.dispose(); super.dispose(); }
            }, Coord.of(x, 0));
            button.settip(category.name());
            x += UI.scale(30);
        }
    }
    public enum Category {
	All("gfx/hud/tab/combat/all", null),
	Attacks("gfx/hud/tab/combat/attack", new HashSet<>(Arrays.asList(
	    "paginae/atk/pow",
	    "paginae/atk/lefthook",
	    "paginae/atk/lowblow",
	    "paginae/atk/oppknock",
	    "paginae/atk/ripapart",
	    "paginae/atk/fullcircle",
	    "paginae/atk/cleave",
	    "paginae/atk/barrage",
	    "paginae/atk/sideswipe",
	    "paginae/atk/sting",
	    "paginae/atk/sos",
	    "paginae/atk/knockteeth",
	    "paginae/atk/kick",
	    "paginae/atk/haymaker",
	    "paginae/atk/chop",
	    "paginae/atk/gojug",
	    "paginae/atk/uppercut",
	    "paginae/atk/punchboth",
	    "paginae/atk/stealthunder",
	    "paginae/atk/ravenbite",
	    "paginae/atk/takedown"
	))),
	Defences("gfx/hud/tab/combat/restore", new HashSet<>(Arrays.asList(
	    "paginae/atk/regain",
	    "paginae/atk/dash",
	    "paginae/atk/zigzag",
	    "paginae/atk/yieldground",
	    "paginae/atk/watchmoves",
	    "paginae/atk/sidestep",
	    "paginae/atk/qdodge",
	    "paginae/atk/jump",
	    "paginae/atk/fdodge",
	    "paginae/atk/artevade",
	    "paginae/atk/flex"
	))),
	Maneuvers("gfx/hud/tab/combat/maneuver", new HashSet<>(Arrays.asList(
	    "paginae/atk/toarms",
	    "paginae/atk/shield",
	    "paginae/atk/parry",
	    "paginae/atk/oakstance",
	    "paginae/atk/dorg",
	    "paginae/atk/chinup",
	    "paginae/atk/bloodlust",
	    "paginae/atk/combmed"
	))),
	Moves("gfx/hud/tab/combat/move", new HashSet<>(Arrays.asList(
	    "paginae/atk/think",
	    "paginae/atk/takeaim"
	))),
	Other("gfx/invobjs/missing");

        public final String icon;
        private final java.util.Set<String> resources;
        Category(String icon, java.util.Set<String> resources) { this.icon = icon; this.resources = resources; }
        Category(String icon) { this(icon, null); }
        public boolean matches(String name) {
            if(this == All) return true;
            if(this != Other) return resources.contains(name);
            for(Category type : values()) if(type.resources != null && type.resources.contains(name)) return false;
            return true;
        }
    }
}
