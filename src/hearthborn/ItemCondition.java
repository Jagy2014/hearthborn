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

package hearthborn;

import haven.*;
import java.awt.Color;
import java.util.List;

/**
 * Wear bar, with the remaining durability or armor drawn in the quality style.
 *
 * @author Jagy2014
 */
public final class ItemCondition implements Disposable {
    private static final Color[] WEAR = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN};
    private static final int BAR = 4;
    private static final Text.Foundry TABLEWARE_FONT = new Text.Foundry(Text.sans, 8);

    private List<ItemInfo> previous;
    private Tex label;
    private Double wear;
    private Color wearColor;
    private boolean broken;
    private boolean tablewareLabel;

    /** Server wear and armor classes paint their own overlay. Skip those once we can read them. */
    public static boolean replaces(ItemInfo info) {
	return wearOf(info) != null || armorOf(info) != null;
    }

    /** Tableware's Gast counter repeats Wear; keep unrelated counts and native fallbacks. */
    public static boolean replaces(ItemInfo overlay,List<ItemInfo> info) {
        if(replaces(overlay))return true;
        if(!tableware(overlay) || stacked(info))return false;
        int[] wear=null;
        for(ItemInfo tip:info) {
            // Armor takes the custom text slot, leaving the native durability counter useful.
            if(armorOf(tip)!=null)return false;
            if(wear==null)wear=wearOf(tip);
        }
        return wear!=null && ((GItem.NumberInfo)overlay).itemnum()==wear[1]-wear[0];
    }

    private static boolean tableware(ItemInfo tip) {
        return tip instanceof GItem.NumberInfo && tip.getClass().getName().equals("haven.res.ui.tt.gast.Gast");
    }

    static boolean tableware(List<ItemInfo> info) {
        for(ItemInfo tip:info)if(tableware(tip))return true;
        return false;
    }

    public void draw(GOut g, GItem item, Coord size, double fit) {
	try {
	    List<ItemInfo> info = item.info();
	    if(info != previous) {
		rebuild(info);
		previous = info;
	    }
	} catch(Loading loading) {
	    return;
	}
	if(broken) {
	    g.chcolor(Color.RED);
	    g.rect(Coord.z, size);
	    g.chcolor();
	}
	if(wear != null && wear > 0) {
	    g.chcolor(wearColor);
	    int height = (int)(size.y * wear);
	    g.frect(new Coord(0, size.y - height), new Coord(BAR, height));
	    g.chcolor();
	}
	if(label != null) {
	    Coord cell = tablewareLabel ? size : cell(size);
	    int room = Math.max(1, cell.x - BAR - UI.scale(2));
	    int limit = Math.max(1, cell.y - UI.scale(2));
	    Coord box = fitted(label.sz(), room, limit, fit);
	    if(tablewareLabel)
		g.aimage(label, cell.sub(UI.scale(1), UI.scale(1)), 1, 1, box);
	    else
		g.aimage(label, new Coord(BAR + UI.scale(1), cell.y - UI.scale(1)), 0, 1, box);
	}
    }

    private void rebuild(List<ItemInfo> info) {
	dispose();
	int[] wear = null;
	int[] armor = null;
	for(ItemInfo tip : info) {
	    if(wear == null)
		wear = wearOf(tip);
	    if(armor == null)
		armor = armorOf(tip);
	}
	if(wear != null) {
	    double ratio = (double)(wear[1] - wear[0]) / wear[1];
	    this.wear = Math.max(0, Math.min(1, ratio));
	    wearColor = wearColor(this.wear);
	    broken = wear[0] >= wear[1];
	}
	if(stacked(info))
	    return;
	String text = null;
	if(armor != null)
	    text = armor[0] + "/" + armor[1];
	else if(wear != null)
	    text = Integer.toString(wear[1] - wear[0]);
	tablewareLabel = armor == null && wear != null && tableware(info);
	if(text != null)
	    label = new TexI(tablewareLabel ? QualityOverlay.styled(TABLEWARE_FONT.render(text).img) : QualityOverlay.styled(text));
    }

    private static boolean stacked(List<ItemInfo> info) {
	for(ItemInfo tip : info) {
	    if(tip instanceof GItem.Amount)
		return true;
	}
	return false;
    }

    private static int[] wearOf(ItemInfo tip) {
	if(!tip.getClass().getSimpleName().equals("Wear"))
	    return null;
	Integer damage = number(tip, "d");
	Integer max = number(tip, "m");
	if(damage == null || max == null || max <= 0)
	    return null;
	return new int[]{damage, max};
    }

    private static int[] armorOf(ItemInfo tip) {
	if(!tip.getClass().getSimpleName().equals("Armor"))
	    return null;
	Integer hard = number(tip, "hard");
	Integer soft = number(tip, "soft");
	if(hard == null || soft == null)
	    return null;
	return new int[]{hard, soft};
    }

    private static Integer number(ItemInfo tip, String name) {
	for(Class<?> type = tip.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
	    try {
		java.lang.reflect.Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		Object value = field.get(tip);
		if(value instanceof Number)
		    return ((Number)value).intValue();
		return null;
	    } catch(NoSuchFieldException ignored) {
	    } catch(ReflectiveOperationException | ClassCastException ignored) {
		return null;
	    }
	}
	return null;
    }

    /** Capes, cloaks, masks, pants and wings span more than one slot. The number stays in the first cell. */
    public static Coord cell(Coord sprite) {
	Coord cell = Inventory.sqsz;
	return Coord.of(Math.min(Math.max(1, sprite.x), cell.x), Math.min(Math.max(1, sprite.y), cell.y));
    }

    /** Shrink a long armor class so it stays inside the icon. */
    public static Coord fitted(Coord label, int room, int limit, double fit) {
	double scale = fit > 0 ? fit : 1;
	int width = Math.max(1, (int)Math.round(label.x * scale));
	int height = Math.max(1, (int)Math.round(label.y * scale));
	if(width > room) {
	    double shrink = room / (double)width;
	    width = room;
	    height = Math.max(1, (int)Math.round(height * shrink));
	}
	if(height > limit) {
	    double shrink = limit / (double)height;
	    height = limit;
	    width = Math.max(1, (int)Math.round(width * shrink));
	}
	return Coord.of(width, height);
    }

    /** Same stops as before: red, orange, yellow, green. */
    private static Color wearColor(double ratio) {
	int sections = WEAR.length - 1;
	double span = 1.0 / sections;
	int section = (int)(ratio / span);
	if(section >= sections)
	    return WEAR[sections];
	return Utils.blendcol(WEAR[section], WEAR[section + 1], (ratio - section * span) / span);
    }

    public void dispose() {
	if(label != null) {
	    label.dispose();
	    label = null;
	}
	wear = null;
	wearColor = null;
	broken = false;
	tablewareLabel = false;
	previous = null;
    }
}
