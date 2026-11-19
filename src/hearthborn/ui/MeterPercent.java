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
 * Progress as a percent, in place of the white pie fill.
 *
 * @author Jagy2014
 */
public final class MeterPercent implements Disposable {
    private String shown;
    private Tex tex;

    public void draw(GOut g, Coord center, double meter) { draw(g, center, meter, true); }

    /** Drying frames, tanning tubs and the chicken coop show the percent without the dark bar. */
    public static boolean bareCaption(String caption) {
        return titled(caption, "Drying Frame") || titled(caption, "Tanning Tub") || titled(caption, "Chicken Coop")
            || shortTitle(caption, "Frame") || shortTitle(caption, "Tub");
    }

    /** A hatching egg keeps the cut percent and drops the dark bar. Cooked and cake eggs stay as they are. */
    public static boolean egg(haven.GItem item) {
        if(item == null) return false;
        try {
            return eggName(item.resource().name);
        } catch(Loading ignored) {
            return false;
        }
    }

    public static boolean eggName(String name) {
        if(name == null) return false;
        int slash = name.lastIndexOf('/');
        String leaf = (slash < 0 ? name : name.substring(slash + 1)).toLowerCase(java.util.Locale.ROOT);
        if(leaf.contains("cake") || leaf.contains("pickle") || leaf.contains("boiled") || leaf.contains("easter")) return false;
        return leaf.equals("egg") || leaf.startsWith("egg-") || leaf.endsWith("egg");
    }

    /** The open building itself, so a short or renamed caption still drops the bar. */
    public static boolean bare(Window window) {
        if(window == null) return false;
        if(bareCaption(window.cap)) return true;
        Long id = window.objectId();
        if(id == null || window.ui == null || window.ui.sess == null || window.ui.sess.glob == null || window.ui.sess.glob.oc == null)
            return false;
        try {
            return bareResource(hearthborn.util.GameObjects.resourceName(window.ui.sess.glob.oc.getgob(id)));
        } catch(Loading ignored) {
            return false;
        }
    }

    static boolean bareResource(String name) {
        return name != null && (name.endsWith("/dframe") || name.endsWith("/ttub")
            || name.endsWith("/chickencoop") || name.endsWith("/chicken-coop"));
    }

    public void draw(GOut g, Coord center, double meter, boolean plate) {
        if(!(meter > 0)) {
            clear();
            return;
        }
        int percent = (int)Math.round(Math.min(1.0, meter) * 100);
        String value = percent + "%";
        if(!value.equals(shown)) {
            clear();
            shown = value;
            tex = new TexI(face(value));
        }
        if(tex != null) {
            Coord size = tex.sz();
            Coord at = center.sub(size.div(2));
            if(plate) {
                g.chcolor(0, 0, 0, 150);
                g.frect(at.sub(UI.scale(1), 0), size.add(UI.scale(2), 0));
                g.chcolor();
            }
            g.image(tex, at);
        }
    }

    private static boolean titled(String caption, String name) {
        if(caption == null || name == null) return false;
        if(caption.equalsIgnoreCase(name)) return true;
        String translated = hearthborn.L10n.text("window", name);
        if(translated != null && caption.equals(translated)) return true;
        return follows(caption, name) || follows(caption, translated);
    }

    /** "Tub" and "Frame" are the live captions. A longer title such as "Чан для творога" stays plated. */
    private static boolean shortTitle(String caption, String name) {
        if(caption == null) return false;
        if(caption.equalsIgnoreCase(name)) return true;
        String translated = hearthborn.L10n.text("window", name);
        if(translated != null && caption.equals(translated)) return true;
        return numbered(caption, name) || numbered(caption, translated);
    }

    private static boolean numbered(String caption, String name) {
        if(name == null || name.isEmpty() || caption.length() <= name.length() + 1) return false;
        if(!caption.regionMatches(true, 0, name, 0, name.length()) || caption.charAt(name.length()) != ' ') return false;
        for(int i = name.length() + 1; i < caption.length(); i++)
            if(!Character.isDigit(caption.charAt(i))) return false;
        return true;
    }

    private static boolean follows(String caption, String name) {
        return name != null && !name.isEmpty() && caption.length() > name.length()
            && caption.regionMatches(true, 0, name, 0, name.length()) && caption.charAt(name.length()) == ' ';
    }

    private static java.awt.image.BufferedImage face(String value) {
        return hearthborn.QualityOverlay.styled(value);
    }

    public void dispose() { clear(); }

    private void clear() {
        if(tex != null)
            tex.dispose();
        tex = null;
        shown = null;
    }
}
