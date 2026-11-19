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
import java.util.List;

/**
 * Brief summaries of Ring of Brodgar's Attributes, Abilities and Combat_moves pages, checked 2026-11-12.
 *
 * @author Jagy2014
 */
public final class AttributeHelp {
    private AttributeHelp() {}

    private static String key(String attribute) {
        switch(attribute) {
        case "str": return "Affects melee damage, mining and destruction. With Smithing, limits metalworking quality.";
        case "agi": return "Changes melee attack cooldowns according to the Agility difference between opponents.";
        case "int": return "Increases study attention. With Stealth, helps conceal criminal scents.";
        case "con": return "Increases maximum hit points and lowers the stamina cost of swimming.";
        case "prc": return "With Exploration, reveals forageables and scents. With Cooking, limits dough quality.";
        case "csm": return "Affects quest rewards, authority gain, party size and bonfire feasting bonuses.";
        case "dex": return "Affects the quality of pottery, sausages, sewn items and leather; also lockpicking.";
        case "wil": return "Affects casting-rod fish choices and travel-weariness capacity in worlds that use it.";
        case "psy": return "Affects the quality of jewelry, silk items, crafted curiosities and pigments.";
        case "unarmed": return "Helps unarmed attacks create openings. Strengthens defense in maneuvers based on Unarmed Combat.";
        case "melee": return "Helps weapon attacks create openings. Strengthens defense in maneuvers based on Melee Combat.";
        case "ranged": return "Affects ranged weapon damage and limits the quality of crafted bows, slings and arrows.";
        case "explore": return "With Perception, determines which forageables and criminal scents you can see.";
        case "stealth": return "With Intelligence, makes your criminal scents harder to detect and track.";
        case "sewing": return "With Dexterity, limits the quality of crafted clothing and leather items.";
        case "smithing": return "With Strength, limits metal item quality; with Psyche, jewelry quality.";
        case "masonry": return "Caps gathered clay, stone and ore quality. Also affects pottery and stoneworking.";
        case "carpentry": return "Limits the quality of wooden items and sawn boards.";
        case "cooking": return "With the recipe's attributes, limits the quality of food and some other crafted products.";
        case "farming": return "Affects crop quality improvement and products obtained from butchering domestic animals.";
        case "survive": return "Limits gathered resources and hunted animal quality. Also affects butchering, survival crafts and fishing choices.";
        case "lore": return "Limits the quality of many crafted curiosities, jewelry and medicines.";
        default: return null;
        }
    }

    public static String description(String attribute) {
        String key=key(attribute);
        return key==null?null:L10n.tr(key);
    }

    public static void add(CharWnd.AttrWdg owner,List<ItemInfo> info) {
        if(!(owner instanceof BAttrWnd.Attr) && !(owner instanceof SAttrWnd.SAttr))return;
        String description=description(owner.nm);
        if(description!=null)info.add(new Explanation(owner,description));
    }

    /** The source line: warm amber on the dark plate of a light theme tooltip, soft grey otherwise. */
    static String source() {
        java.awt.Color color=UiTheme.light()?Parchment.TIP_LINK:new java.awt.Color(160,160,150);
        return "$col["+color.getRed()+","+color.getGreen()+","+color.getBlue()+"]{Ring of Brodgar}";
    }

    private static final class Explanation extends ItemInfo.Tip {
        final String description;
        Explanation(ItemInfo.Owner owner,String description) {super(owner);this.description=description;}
        public int order(){return 10010;}
        public void layout(ItemInfo.Layout layout) {
            int width=Math.max(UI.scale(260),layout.cmp.sz.x);
            String text=RichText.Parser.quote(description)+"\n"+source();
            layout.cmp.add(RichText.render(text,width).img,Coord.of(0,layout.cmp.sz.y+UI.scale(8)));
        }
    }
}
