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

package hearthborn.inventory;

import haven.*;
import hearthborn.QualityOverlay;
import java.util.*;

/**
 * Container operations use the normal item take, use and inventory drop messages.
 *
 * @author Jagy2014
 */
public final class InventoryActions {
    /** Shift+Ctrl and a click move everything of the clicked kind; the combination is fixed and has no setting. */
    public static boolean transferMatches(int modifiers){return (modifiers&KeyMatch.MODS)==(KeyMatch.S|KeyMatch.C);}
    public static final int COMBINE = UI.MOD_SHIFT | UI.MOD_CTRL;

    public static List<WItem> items(Inventory inv) {
        List<WItem> result = new ArrayList<>();
        for(Widget child = inv.child; child != null; child = child.next)
            if(child instanceof WItem) result.add((WItem)child);
        return result;
    }

    public static String name(WItem item) {
        ItemInfo.Name name = ItemInfo.find(ItemInfo.Name.class, item.item.info());
        if(name == null) throw new Loading();
        return name.str.text;
    }

    public static String baseName(WItem item) {
        return name(item).replaceFirst(", stack of$", "");
    }

    private static final java.util.regex.Pattern SEED_COUNT=java.util.regex.Pattern.compile("\\s*\\(\\s*[0-9]+\\s+(?:seeds?|семян|семя|семени)\\s*\\)$",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE);
    /** What an item is, whatever it holds: seeds carry their count in the name, "Turnip (35 seeds)", and are one kind at any count. */
    static String kind(WItem item) {
        return SEED_COUNT.matcher(baseName(item)).replaceFirst("");
    }

    public static double quality(WItem item) {
        Double q = QualityOverlay.qualityOf(item.item);
        return q == null ? -1 : q;
    }

    public static int count(WItem item) {
        for(ItemInfo tip : item.item.info())
            if(tip instanceof GItem.Amount) return Math.max(1, ((GItem.Amount)tip).itemnum());
        return Math.max(1, item.item.num);
    }

    public static Coord cell(WItem item) {return item.c.sub(1, 1).div(Inventory.sqsz);}

    public static boolean cursorFree(Inventory inv) {
        GameUI gui = inv.getparent(GameUI.class);
        if(gui != null && ItemStacking.running(gui)) {
            inv.ui.error(hearthborn.L10n.tr("Stacking items is already running."));
            return false;
        }
        if(gui == null || gui.vhand != null) {
            inv.ui.error(hearthborn.L10n.tr("Empty the cursor first."));
            return false;
        }
        return true;
    }

    public static List<WItem> transferOrder(Inventory inv,WItem source,boolean ascending) {
        String resource=source.item.res.get().name, name=kind(source);
        List<WItem> result=new ArrayList<>();
        for(WItem candidate:items(inv))
            if(candidate.visible && candidate.item.res.get().name.equals(resource) && kind(candidate).equals(name)) {
                quality(candidate); // Resolve tooltips before sending any transfer messages.
                result.add(candidate);
            }
        Comparator<WItem> order=Comparator.comparingDouble(InventoryActions::quality);
        if(!ascending)order=order.reversed();
        result.sort(order.thenComparingInt(w -> cell(w).y).thenComparingInt(w -> cell(w).x));
        return result;
    }

    public static boolean transferSame(WItem source,boolean ascending) {
        if(!(source.parent instanceof Inventory))return false;
        try {
            List<WItem> ordered=transferOrder((Inventory)source.parent,source,ascending);
            if(BagTransfers.transfer(source,ordered))return true;
            for(WItem candidate:ordered)
                transfer(candidate,Coord.z,1);
        } catch(Loading loading) {source.ui.error(hearthborn.L10n.tr("Items are still loading. Try again."));}
        return true;
    }

    private static List<GItem> stackContents(GItem item) {
        Widget contents=item.contents;
        if(contents==null)return Collections.emptyList();
        boolean stack=false;
        for(Class<?> type=contents.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals("haven.res.ui.stackinv.ItemStack")){stack=true;break;}
        if(!stack)return Collections.emptyList();
        try {
            Object order=contents.getClass().getField("order").get(contents);
            if(!(order instanceof List))return Collections.emptyList();
            List<GItem> result=new ArrayList<>();
            for(Object entry:(List<?>)order)if(entry instanceof GItem)result.add((GItem)entry);
            return result;
        }catch(ReflectiveOperationException ignored){return Collections.emptyList();}
    }
    private static List<Integer> transferTargets(GameUI gui) {
        Set<Integer> ids=new LinkedHashSet<>();
        for(Widget child=gui.lchild;child!=null;child=child.prev) {
            if(!(child instanceof Window)||!child.visible()||!child.tvisible()||child instanceof GItem.ContentsWindow)continue;
            Window window=(Window)child;
            boolean personal=false;
            for(String name:new String[]{"Character Sheet","Equipment","Study","Belt"})
                if(name.equals(window.cap)||hearthborn.L10n.text("window",name).equals(window.cap)){personal=true;break;}
            if(personal)continue;
            for(Inventory inventory:window.children(Inventory.class))
                if(inventory!=gui.maininv && inventory.tvisible() && inventory.getparent(GItem.ContentsWindow.class)==null
                        && !hearthborn.ui.StudyProgress.isStudy(inventory)) {
                    int id=inventory.wdgid();if(id>=0)ids.add(id);
                }
            for(ISBox box:window.children(ISBox.class))if(box.tvisible()){int id=box.wdgid();if(id>=0)ids.add(id);}
        }
        return new ArrayList<>(ids);
    }
    /** Native transfer, followed by explicit transfer of stack contents. */
    public static void transfer(WItem source,Coord click,int amount) {
        GameUI gui=source.getparent(GameUI.class);
        List<GItem> contents=amount>0 && gui!=null && source.parent==gui.maininv?stackContents(source.item):Collections.emptyList();
        List<Integer> targets=contents.isEmpty()?Collections.emptyList():transferTargets(gui);
        source.item.wdgmsg("transfer",click,amount);
        if(!contents.isEmpty())for(int target:targets)
            contents.get(0).wdgmsg("invxf2",0,contents.size(),target);
    }

    public static void stack(Inventory inv) {
        if(!cursorFree(inv)) return;
        ItemStacking.start(inv);
    }

    public static void unstack(Inventory inv) {
        if(!cursorFree(inv)) return;
        try {
            List<WItem> stacks = new ArrayList<>();
            for(WItem item : items(inv))
                if(name(item).endsWith(", stack of")) stacks.add(item);
            for(WItem item : stacks) item.item.wdgmsg("iact", Coord.z, COMBINE);
        } catch(Loading loading) {inv.ui.error(hearthborn.L10n.tr("Items are still loading. Try again."));}
    }

    public static final class Move {
        public final boolean take;
        public final Coord cell;
        Move(boolean take, Coord cell) {this.take = take; this.cell = cell;}
    }

    /** Sorted source cells and reserved cells form a permutation with possible holes. */
    public static List<Move> plan(int width, int height, boolean[] reserved, List<Coord> sorted) {
        if(width <= 0 || height <= 0 || reserved.length != width * height)
            throw new IllegalArgumentException("Invalid inventory grid");
        List<Coord> goals = new ArrayList<>();
        for(int y = 0; y < height; y++) for(int x = 0; x < width; x++)
            if(!reserved[x + y * width]) goals.add(Coord.of(x, y));
        if(goals.size() < sorted.size()) throw new IllegalArgumentException("Inventory does not fit");
        Map<Coord, Integer> occupied = new HashMap<>();
        List<Coord> positions = new ArrayList<>(sorted);
        for(int i = 0; i < sorted.size(); i++) {
            Coord p = sorted.get(i);
            if(p.x < 0 || p.y < 0 || p.x >= width || p.y >= height || reserved[p.x + p.y * width]
                || occupied.put(p, i) != null) throw new IllegalArgumentException("Invalid item cells");
        }
        List<Move> result = new ArrayList<>();
        for(int i = 0; i < sorted.size(); i++) {
            if(positions.get(i).equals(goals.get(i))) continue;
            result.add(new Move(true, positions.get(i)));
            occupied.remove(positions.get(i));
            Integer hand = i;
            while(hand != null) {
                Coord target = goals.get(hand);
                result.add(new Move(false, target));
                Integer displaced = occupied.put(target, hand);
                positions.set(hand, target);
                hand = displaced;
            }
        }
        return result;
    }

    public static void sort(Inventory inv) {sort(inv, false);}

    /** Items that are alike by name get ordered by what they hold: cheese trays by the cheese on them, empty ones last. */
    static String variant(WItem item) {
        String resource = item.item.res.get().name;
        if(resource == null || !resource.startsWith("gfx/invobjs/cheesetray")) return "";
        String kind = hearthborn.cheese.CheeseAging.itemKind(item.item);
        return kind == null ? "\uffff" : kind.toLowerCase();
    }

    static Comparator<WItem> order(boolean qualityAscending) {
        return Comparator.comparing(InventoryActions::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(InventoryActions::variant)
            .thenComparing(InventoryActions::quality,
                qualityAscending ? Comparator.naturalOrder() : Comparator.reverseOrder());
    }

    /** Items of one size swap only among their own places: every place is where an item of that size already stands. */
    public static List<Move> samePlaces(int width, int height, Coord size, List<Coord> sorted) {
        boolean[] reserved = new boolean[width * height];
        Arrays.fill(reserved, true);
        for(Coord cell : sorted)
            if(cell.x >= 0 && cell.y >= 0 && cell.x < width && cell.y < height) reserved[cell.x + cell.y * width] = false;
        return plan(width, height, reserved, sorted);
    }

    public static void sort(Inventory inv, boolean qualityAscending) {
        if(!cursorFree(inv)) return;
        try {
            List<WItem> small = new ArrayList<>();
            Map<Coord, List<WItem>> large = new LinkedHashMap<>();
            boolean[] reserved = inv.sqmask == null ? new boolean[inv.isz.x * inv.isz.y] : inv.sqmask.clone();
            for(WItem item : items(inv)) {
                GSprite sprite = item.item.spr();
                if(sprite == null) throw new Loading();
                Coord size = sprite.sz().add(Inventory.sqsz.sub(1, 1)).div(Inventory.sqsz);
                Coord cell = cell(item);
                if(size.x == 1 && size.y == 1) small.add(item);
                else {
                    large.computeIfAbsent(size, k -> new ArrayList<>()).add(item);
                    for(int y = 0; y < size.y; y++) for(int x = 0; x < size.x; x++)
                        reserved[cell.x + x + (cell.y + y) * inv.isz.x] = true;
                }
            }
            Comparator<WItem> order = order(qualityAscending);
            List<Move> moves = new ArrayList<>();
            Map<Coord, WItem> originals = new HashMap<>();
            // Big items (trays, buckets) keep their size: they swap among the places of their own size.
            List<Coord> bigCells = new ArrayList<>();
            for(Map.Entry<Coord, List<WItem>> group : large.entrySet()) {
                List<WItem> items = group.getValue();
                if(items.size() < 2) continue;
                items.sort(order);
                bigCells.clear();
                for(WItem item : items) {bigCells.add(cell(item)); originals.put(cell(item), item);}
                moves.addAll(samePlaces(inv.isz.x, inv.isz.y, group.getKey(), bigCells));
            }
            small.sort(order);
            List<Coord> cells = new ArrayList<>();
            for(WItem item : small) {cells.add(cell(item)); originals.put(cell(item), item);}
            moves.addAll(plan(inv.isz.x, inv.isz.y, reserved, cells));
            // Queue complete swap cycles in order, including for a completely full inventory.
            for(Move move : moves) {
                if(move.take) originals.get(move.cell).item.wdgmsg("take", Coord.z);
                else inv.wdgmsg("drop", move.cell);
            }
        } catch(Loading loading) {inv.ui.error(hearthborn.L10n.tr("Items are still loading. Try again."));}
        catch(IllegalArgumentException | IndexOutOfBoundsException changed) {
            inv.ui.error(hearthborn.L10n.tr("The container size changed. Sort again."));
        }
    }

    private InventoryActions() {}
}
