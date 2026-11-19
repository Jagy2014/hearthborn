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

package hearthborn.cheese;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.io.LocalStore;

import haven.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jagy2014
 */
public class CheeseAging {
    public enum Loc {CABIN, CELLAR, MINE, OUTSIDE}

    public enum Milk {COW, SHEEP, GOAT}

    public static class Step {
	public final Milk milk;
	public final String from, to;
	public final Loc loc;
	public final int hours;

	Step(Milk milk, String from, Loc loc, String to, int hours) {
	    this.milk = milk;
	    this.from = from;
	    this.loc = loc;
	    this.to = to;
	    this.hours = hours;
	}
    }

    public static final String GOUDA = "Generic Gouda";
    private static final Map<String, String> ALIAS = new HashMap<>();
    private static final Map<String, Integer> HOURS = new HashMap<>();
    private static final Map<String, Integer> GAME_DAYS = new HashMap<>();
    private static final Map<Long, Pair<Integer, Boolean>> CAVE_GRIDS = new ConcurrentHashMap<>();
    private static final Map<String, Loc> LOCATIONS = new ConcurrentHashMap<>();
    private static boolean locationsLoaded;
    public enum State { UNKNOWN, AGING, READY }
    private static final Map<String, String> ICONS = new HashMap<>();
    private static final Map<String, Integer> PIECES = new HashMap<>();
    private static final Map<String, Fep[]> FEPS = new HashMap<>();
    private static final List<Step> STEPS = new ArrayList<>();
    private static final Map<Long, List<Tray>> RACK = new ConcurrentHashMap<>();
    private static final String RACK_CFG = "cheese-racks.cfg";
    private static boolean racksLoaded;

    public static class Tray implements java.io.Serializable {
        private static final long serialVersionUID=1L;
	public String kind;
	public double meter;
	public long ts;
	public long until;
	public State state;
	public String overlay;
	public Loc location;

	public Tray() {}

	public Tray(String kind, double meter, long ts) {
	    this(kind, meter, ts, 0);
	}

	public Tray(String kind, double meter, long ts, long until) {
	    this.kind = kind;
	    this.meter = meter;
	    this.ts = ts;
	    this.until = until;
	}
    }

    public static class Fep {
	public final String attr;
	public final int a;

	Fep(String attr, int a) {
	    this.attr = attr;
	    this.a = a;
	}

	public boolean plus2() {
	    return attr.endsWith("2");
	}

	public String base() {
	    return plus2() ? attr.substring(0, attr.length() - 1) : attr;
	}

	public String[] statRes() {
	    String b = base();
	    return new String[]{"gfx/hud/chr/" + b, "gfx/hud/chr/fev/" + b, "gfx/hud/chr/fev/" + attr};
	}

	public String[] res() {
	    return new String[]{"gfx/hud/chr/fev/" + attr, "gfx/hud/chr/" + base()};
	}
    }

    static {
	alias("curd", "curd");
	alias("cowscurd", "curd");
	alias("cowcurd", "curd");
	alias("sheepscurd", "sheepcurd");
	alias("sheepcurd", "sheepcurd");
	alias("goatscurd", "goatcurd");
	alias("goatcurd", "goatcurd");

	hours("Creamy Camembert", 52);
	hours("Tasty Emmentaler", 73);
	hours("Musky Milben", 205);
	hours("Cellar Cheddar", 44);
	hours("Brodgar Blue Cheese", 30);
	hours("Jorbonzola", 52);
	hours("Midnight Blue Cheese", 154);
	hours("Cave Cheddar", 15);
	hours("Mothzarella", 30);
	hours("Harmesan Cheese", 59);
	hours("Sunlit Stilton", 52);
	hours("Halloumi", 44);
	hours("Caciotta", 146);
	hours("Feta", 44);
	hours("Cabrales", 110);
	hours("Pecorino", 37);
	hours("Manchego", 88);
	hours("Gbejna", 110);
	hours("Roncal", 146);
	hours("Abbaye", 59);
	hours("Zamorano", 183);
	hours("Brique", 219);
	hours("Oscypki", 73);
	hours("Banon", 37);
	hours("Robiola", 30);
	hours("Bucheron", 22);
	hours("Picodon", 59);
	hours("Graviera", 37);
	hours("Gevrik", 52);
	hours("Garrotxa", 117);
	hours("Chabichou", 30);
	hours("Chabis", 44);
	hours("Formaela", 66);
	hours("Majorero", 73);
	hours("Kasseri", 59);
	hours(GOUDA, 66);
	// Exact in-game durations from the individual RoB cheese pages.
	GAME_DAYS.put(norm("Creamy Camembert"), 7);
	GAME_DAYS.put(norm("Tasty Emmentaler"), 10);
	GAME_DAYS.put(norm("Cellar Cheddar"), 6);
	GAME_DAYS.put(norm("Mothzarella"), 4);
	GAME_DAYS.put(norm("Musky Milben"), 28);
	GAME_DAYS.put(norm(GOUDA), 9);

	eat("Curd", null, 0, f("str", 1));
	eat("Sheep's Curd", null, 0, f("str", 1));
	eat("Goat's Curd", null, 0, f("str", 1));
	eat("Creamy Camembert", "creamycamembert", 3, f("agi2", 5), f("csm", 4));
	eat("Tasty Emmentaler", "temmentaler", 4, f("int2", 5), f("csm", 10));
	eat("Musky Milben", "muskymilben", 5, f("str2", 10), f("int", 10));
	eat("Cellar Cheddar", "cellarcheddar", 2, f("str2", 8), f("agi", 4));
	eat("Brodgar Blue Cheese", "brodgarblue", 3, f("str2", 8), f("wil2", 2));
	eat("Jorbonzola", "jorbonzola", 1, f("int2", 15), f("int", 35), f("psy", 10));
	eat("Midnight Blue Cheese", "midnightblue", 1, f("str2", 50));
	eat("Cave Cheddar", "cavecheddar", 2, f("str2", 10), f("prc", 10));
	eat("Mothzarella", "mothzarella", 5, f("prc2", 5), f("csm2", 3));
	eat("Harmesan Cheese", "harmesan", 2, f("con", 10), f("csm2", 15));
	eat("Sunlit Stilton", "sunlitstilton", 0, f("con2", 25), f("csm2", 75));
	eat("Halloumi", "halloumi", 3, f("int", 4), f("con", 4), f("dex", 4));
	eat("Caciotta", "caciotta", 2, f("agi", 8), f("agi2", 2), f("csm", 8));
	eat("Feta", "feta", 3, f("csm", 5), f("dex", 3));
	eat("Cabrales", "cabrales", 4, f("int", 5), f("con", 5), f("dex2", 5));
	eat("Pecorino", "pecorino", 2, f("str", 4), f("csm", 6));
	eat("Manchego", "manchego", 4, f("prc", 10), f("dex", 5));
	eat("Gbejna", "gbejna", 3, f("int", 4), f("con", 8), f("psy2", 2));
	eat("Roncal", "roncal", 3, f("str", 10), f("con2", 5), f("psy2", 5));
	eat("Abbaye", "abbaye", 2, f("int", 5), f("prc", 3));
	eat("Zamorano", "zamorano", 3, f("str2", 3), f("agi2", 3), f("psy2", 3));
	eat("Brique", "brique", 2, f("str2", 10), f("int", 10), f("prc", 10));
	eat("Oscypki", "oscypki", 2, f("agi2", 20), f("con2", 10), f("psy2", 10));
	eat("Banon", "banon", 2, f("agi", 3), f("wil", 8));
	eat("Robiola", "robiola", 5, f("con2", 5), f("prc", 10));
	eat("Bucheron", "bucheron", 3, f("csm", 3), f("dex", 6));
	eat("Picodon", "picodon", 3, f("int", 5), f("dex", 15));
	eat("Graviera", "graviera", 3, f("con", 10), f("wil", 5), f("psy", 5));
	eat("Gevrik", "gevrik", 2, f("str", 15), f("prc2", 15));
	eat("Garrotxa", "garrotxa", 2, f("dex", 30), f("dex2", 20));
	eat("Chabichou", "chabichou", 4, f("agi", 4), f("int", 3), f("psy", 2));
	eat("Chabis", "chabis", 2, f("con", 15));
	eat("Formaela", "formaela", 3, f("agi", 10), f("con", 10), f("wil2", 10));
	eat("Majorero", "majorero", 4, f("prc", 10), f("prc2", 5), f("csm", 15));
	eat("Kasseri", "kasseri", 3, f("str", 4), f("csm", 6), f("csm2", 2));
	eat(GOUDA, "genericgouda", 0, f("con2", 3), f("wil", 2));

	cow("curd", Loc.OUTSIDE, "Creamy Camembert");
	cow("curd", Loc.CABIN, "Tasty Emmentaler");
	cow("curd", Loc.CELLAR, "Cellar Cheddar");
	cow("curd", Loc.MINE, "Mothzarella");
	cow("Tasty Emmentaler", Loc.MINE, "Musky Milben");
	cow("Cellar Cheddar", Loc.CABIN, "Brodgar Blue Cheese");
	cow("Cellar Cheddar", Loc.OUTSIDE, "Brodgar Blue Cheese");
	cow("Cellar Cheddar", Loc.MINE, "Cave Cheddar");
	cow("Mothzarella", Loc.CABIN, "Harmesan Cheese");
	cow("Mothzarella", Loc.CELLAR, "Harmesan Cheese");
	cow("Brodgar Blue Cheese", Loc.MINE, "Jorbonzola");
	cow("Harmesan Cheese", Loc.OUTSIDE, "Sunlit Stilton");
	cow("Jorbonzola", Loc.CELLAR, "Midnight Blue Cheese");

	sheep("sheepcurd", Loc.CABIN, "Halloumi");
	sheep("sheepcurd", Loc.CELLAR, "Feta");
	sheep("sheepcurd", Loc.OUTSIDE, "Pecorino");
	sheep("sheepcurd", Loc.MINE, "Abbaye");
	sheep("Feta", Loc.CABIN, "Caciotta");
	sheep("Feta", Loc.OUTSIDE, "Cabrales");
	sheep("Pecorino", Loc.MINE, "Manchego");
	sheep("Pecorino", Loc.CELLAR, "Gbejna");
	sheep("Abbaye", Loc.CELLAR, "Zamorano");
	sheep("Zamorano", Loc.CABIN, "Brique");
	sheep("Gbejna", Loc.MINE, "Roncal");
	sheep("Brique", Loc.OUTSIDE, "Oscypki");

	goat("goatcurd", Loc.CABIN, "Banon");
	goat("goatcurd", Loc.OUTSIDE, "Bucheron");
	goat("goatcurd", Loc.MINE, "Chabichou");
	goat("goatcurd", Loc.CELLAR, "Kasseri");
	goat("Banon", Loc.MINE, "Robiola");
	goat("Chabichou", Loc.CABIN, "Chabis");
	goat("Chabichou", Loc.OUTSIDE, "Majorero");
	goat("Bucheron", Loc.CELLAR, "Picodon");
	goat("Bucheron", Loc.MINE, "Graviera");
	goat("Chabis", Loc.CELLAR, "Formaela");
	goat("Graviera", Loc.CABIN, "Gevrik");
	goat("Gevrik", Loc.MINE, "Garrotxa");
	loadRacks();
    }

    private static void alias(String a, String b) {ALIAS.put(norm(a), b);}

    private static void hours(String name, int h) {HOURS.put(norm(name), h);}

    private static Fep f(String attr, int a) {return new Fep(attr, a);}

    private static void eat(String name, String icon, int pieces, Fep... fep) {
	String n = canonical(name);
	if(icon != null) {
	    ICONS.put(n, "gfx/invobjs/cheese-" + icon);
	    alias(icon, n);
	}
	if(pieces > 0)
	    PIECES.put(n, pieces);
	if((fep != null) && (fep.length > 0))
	    FEPS.put(n, fep);
    }

    public static String iconRes(String name, Milk milk) {
	String[] a = icons(name, milk);
	return a[0];
    }

    public static String[] icons(String name, Milk milk) {
	String n = canonical(name);
	List<String> out = new ArrayList<>();
	if("curd".equals(n) || "sheepcurd".equals(n) || "goatcurd".equals(n)) {
	    if((milk == Milk.SHEEP) || "sheepcurd".equals(n))
		out.add("gfx/invobjs/curd-sheep");
	    else if((milk == Milk.GOAT) || "goatcurd".equals(n))
		out.add("gfx/invobjs/curd-goat");
	    else
		out.add("gfx/invobjs/curd-cow");
	    out.add("gfx/invobjs/curd");
	    out.add("gfx/invobjs/cheesetray-curd");
	    out.add("gfx/terobjs/items/cheesetray-curd");
	} else {
	    String ic = ICONS.get(n);
	    String id = (ic != null) && ic.contains("cheese-") ? ic.substring(ic.lastIndexOf('-') + 1) : n;
	    out.add("gfx/invobjs/cheese-" + id);
	    out.add("gfx/invobjs/cheesetray-" + id);
	    out.add("gfx/terobjs/items/cheesetray-" + id);
	}
	return out.toArray(new String[0]);
    }

    public static String[] locIcons(Loc loc) {
	switch(loc) {
	case CABIN:
	    return new String[]{"paginae/bld/logcabin", "paginae/bld/timberhouse"};
	case CELLAR:
	    return new String[]{"paginae/bld/cellardoor"};
	case MINE:
	    return new String[]{"paginae/bld/minehole", "radar/mine-hole"};
	default:
	    return new String[]{"gfx/hud/calendar/dayscape-1", "gfx/hud/calendar/sun"};
	}
    }

    public static Fep[] feps(String name) {
	Fep[] f = FEPS.get(canonical(name));
	return (f == null) ? new Fep[0] : f;
    }

    public static int pieces(String name) {
	Integer p = PIECES.get(canonical(name));
	return (p == null) ? 0 : p;
    }

    private static void cow(String from, Loc loc, String to) {step(Milk.COW, from, loc, to);}

    private static void sheep(String from, Loc loc, String to) {step(Milk.SHEEP, from, loc, to);}

    private static void goat(String from, Loc loc, String to) {step(Milk.GOAT, from, loc, to);}

    private static void step(Milk milk, String from, Loc loc, String to) {
	STEPS.add(new Step(milk, pretty(from), loc, to, hoursOf(to)));
    }

    public static String locLabel(Loc loc) {
	switch(loc) {
	case CABIN: return L10n.tr("Cabin");
	case CELLAR: return L10n.tr("Cellar");
	case MINE: return L10n.tr("Mine");
	default: return L10n.tr("Outside");
	}
    }

    public static List<Step> steps(Milk milk) {
	List<Step> out = new ArrayList<>();
	for(Step s : STEPS) {
	    if(s.milk == milk)
		out.add(s);
	}
	return out;
    }

    public static List<Step> continuations(String kind) {
        List<Step> result=new ArrayList<>();
        for(Step step:STEPS)if(canonical(step.from).equals(canonical(kind)))result.add(step);
        return result;
    }
    public static int stageOf(String kind) {
        if(isCurd(kind))return 0;
        Map<String,Integer> stages=new HashMap<>();
        stages.put(canonical("curd"),0);stages.put(canonical("sheepcurd"),0);stages.put(canonical("goatcurd"),0);
        for(int i=0;i<8;i++)for(Step step:STEPS){Integer from=stages.get(canonical(step.from));if(from!=null)stages.merge(canonical(step.to),from+1,Math::min);}
        return stages.getOrDefault(canonical(kind),0);
    }

    public static String stageLabel(String stage) {
	if(stage == null || !stage.matches("(?i)L[0-4]"))
	    return stage;
	String prefix = L10n.tr("Cheese stage prefix");
	if("Cheese stage prefix".equals(prefix))
	    prefix = "S";
	return prefix + stage.substring(1);
    }

    public static String trayLabel(String kind) {
	if(kind == null)
	    return null;
	if(kind.equals("l1") || kind.equals("l2") || kind.equals("l3") || kind.equals("l4"))
	    return "L" + kind.substring(1);
	return pretty(kind);
    }

    public static String localizeName(String name) {
	if((name == null) || name.isEmpty())
	    return name;
	// Overlay stage codes describe a tray, not its cheese variety.
	if(name.matches("(?i)L[1-4]"))
	    return stageLabel(name);
	String stack = "";
	String base = name;
	if(base.endsWith(", stack of")) {
	    base = base.substring(0, base.length() - 10).trim();
	    stack = L10n.tr(", stack of");
	}
        if("en".equals(L10n.language())){
            String english=pretty(base).replaceAll("(?i)\\bcheese\\b","").trim().replaceAll("\\s+"," ");
            return (english.isEmpty()?english:Character.toUpperCase(english.charAt(0))+english.substring(1))+stack;
        }
	String stripped = base.endsWith(" Cheese") ? base.substring(0, base.length() - 7) : base;
	String t = L10n.tr(stripped);
	if(!t.equals(stripped))
	    return t + stack;
	t = L10n.tr(base);
	if(!t.equals(base))
	    return t + stack;
	String p = pretty(base);
	String ps = p.endsWith(" Cheese") ? p.substring(0, p.length() - 7) : p;
	t = L10n.tr(ps);
	if(!t.equals(ps))
	    return t + stack;
	String[] ic = icons(base, Milk.COW);
	if((ic.length > 0) && ic[0].contains("/cheese-")) {
	    String tt = L10n.resource("tooltip",ic[0], GameObjects.name(ic[0]));
	    if((tt != null) && !tt.equals(ic[0]))
		return tt + stack;
	}
	return base + stack;
    }

    public static String pretty(String key) {
	String n = canonical(key);
	if("curd".equals(n))
	    return "Curd";
	if("sheepcurd".equals(n))
	    return "Sheep's Curd";
	if("goatcurd".equals(n))
	    return "Goat's Curd";
	for(Step s : STEPS) {
	    if(norm(s.to).equals(n))
		return s.to;
	    if(norm(s.from).equals(n))
		return s.from;
	}
	return GameObjects.name(key);
    }

    public static boolean isRack(Gob gob) {
	if(gob == null)
	    return false;
	String n = GameObjects.resourceName(gob);
	return (n != null) && n.startsWith("gfx/terobjs/cheeserack");
    }

    public static void noteRack(long id, List<Tray> trays) {
	noteRack(id, trays, -1);
    }

    public static void noteRack(long id, List<Tray> trays, int overlayCount) {
	loadRacks();
	List<Tray> old = RACK.get(id);
	if(trays == null)
	    return;
	if(trays.isEmpty()) {
	    if((overlayCount > 0) || ((overlayCount < 0) && (old != null) && !old.isEmpty()))
		return;
	    if(old != null) {
		RACK.remove(id);
		saveRacks();
	    }
	    return;
	}
	if((overlayCount > trays.size()) && (old != null) && !old.isEmpty())
	    return;
	List<Tray> merged = mergeTrays(old, trays);
	if(traysEq(old, merged))
	    return;
	RACK.put(id, merged);
	saveRacks();
    }

    private static List<Tray> mergeTrays(List<Tray> old, List<Tray> neu) {
	if((old == null) || old.isEmpty())
	    return new ArrayList<>(neu);
	List<Tray> out = new ArrayList<>(neu.size());
	for(int i = 0; i < neu.size(); i++) {
	    Tray n = neu.get(i);
	    Tray o = matchTray(old, n, i);
	    if(o != null && o.state == State.AGING && n.state == State.READY && n.meter < 1
		&& Objects.equals(o.overlay, n.overlay) && o.location == n.location) {
		// A temporarily absent meter does not prove completion of this stage.
		n.state = State.AGING;
	    }
	    if((o == null) || o.state != n.state || o.location != n.location || !Objects.equals(o.overlay, n.overlay)
		|| (o.until < System.currentTimeMillis() - 2000L)) {
		out.add(n);
		continue;
	    }
	    if(n.meter + 0.05 < o.meter) {
		out.add(n);
		continue;
	    }
	    Tray merged = new Tray(n.kind, Math.max(n.meter, o.meter), o.ts, o.until);
	    merged.state = n.state;
	    merged.overlay = n.overlay;
	    merged.location = n.location;
	    out.add(merged);
	}
	return out;
    }

    private static Tray matchTray(List<Tray> old, Tray n, int i) {
	if((i < old.size()) && sameKind(old.get(i).kind, n.kind))
	    return old.get(i);
	for(Tray o : old) {
	    if(sameKind(o.kind, n.kind) && (o.until > 0))
		return o;
	}
	return null;
    }

    private static boolean sameKind(String a, String b) {
	return Objects.equals(norm(a), norm(b));
    }

    private static boolean traysEq(List<Tray> a, List<Tray> b) {
	if(a == b)
	    return true;
	if((a == null) || (b == null) || (a.size() != b.size()))
	    return false;
	for(int i = 0; i < a.size(); i++) {
	    Tray x = a.get(i), y = b.get(i);
	    if(!Objects.equals(x.kind, y.kind) || x.state != y.state || x.location != y.location || !Objects.equals(x.overlay, y.overlay)
		|| (x.ts != y.ts) || (x.until != y.until) || (Math.abs(x.meter - y.meter) > 0.001))
		return false;
	}
	return true;
    }

    public static List<String> rackTrays(long id) {
	List<Tray> t = RACK.get(id);
	if(t == null)
	    return Collections.emptyList();
	List<String> out = new ArrayList<>();
	for(Tray tr : t)
	    out.add(tr.kind);
	return out;
    }

    public static boolean emptyTray(GItem item) {
	if(item == null)
	    return false;
	String resource = item.resource().name;
	return "gfx/invobjs/cheesetray".equals(resource) || "gfx/invobjs/cheesetray-empty".equals(resource);
    }

    public static String itemKind(GItem item) {
	if(item == null)
	    return null;
	if(emptyTray(item))
	    return null;
	String content = infoKind(item.info());
	if(content != null)
	    return content;
	String res = item.resource().name;
	if((res == null) || res.isEmpty())
	    return null;
	int s = res.lastIndexOf('/');
	String base = (s >= 0) ? res.substring(s + 1) : res;
	if(base.startsWith("curd")) {
	    if(base.contains("sheep"))
		return "sheepcurd";
	    if(base.contains("goat"))
		return "goatcurd";
	    return "curd";
	}
	if(base.startsWith("cheese-"))
	    return base.substring(7);
	if(base.startsWith("cheesetray-")) {
	    String suf = base.substring(11);
	    if(suf.equals("empty"))
		return null;
	    if(suf.equals("curd") || suf.equals("cheese"))
		return suf.equals("cheese") ? null : "curd";
	    return suf;
	}
	ItemInfo.Name name = ItemInfo.find(ItemInfo.Name.class, item.info());
	String nm = name == null ? null : name.str.text;
	return ((nm == null) || nm.isEmpty() || "???".equals(nm)) ? null : nm;
    }

    private static String infoKind(List<ItemInfo> info) {
	for(ItemInfo tip : info) {
	    if(tip instanceof ItemInfo.Contents) {
		String nested = infoKind(((ItemInfo.Contents)tip).sub);
		if(nested != null)
		    return nested;
	    }
	    String name = tip instanceof ItemInfo.Name ? ((ItemInfo.Name)tip).str.text
		: tip instanceof ItemInfo.AdHoc ? ((ItemInfo.AdHoc)tip).str.text : null;
	    if(name != null && known(name))
		return canonical(name);
	}
	return null;
    }

    public static State observedState(boolean hasProgress, double meter, boolean knownCheese, boolean curd) {
	if(hasProgress)
	    return meter >= 1 ? State.READY : State.AGING;
	return knownCheese && !curd ? State.READY : State.UNKNOWN;
    }

    public static State itemState(GItem item, double meter) {
	String kind = itemKind(item);
	boolean progress = item.meter > 0 || item.info().stream().anyMatch(i -> i instanceof GItem.MeterInfo);
	return observedState(progress, meter, item.meterUpdated > 0 && kind != null && known(kind), isCurd(kind));
    }

    /** The tray appearance reports its current stage, independently of aging status. */
    public static String rackStageLabel(String overlay) {
	if(overlay == null || !overlay.matches("(?i)L[1-4]"))
	    return null;
	return stageLabel(overlay);
    }

    public static State trayState(Tray tray) {
	if(tray == null || tray.state == null)
	    return State.UNKNOWN;
	// Reaching an estimated deadline alone is not proof of readiness.
	if(tray.state == State.AGING && tray.until > 0 && tray.until <= System.currentTimeMillis())
	    return State.UNKNOWN;
	return tray.state;
    }

    public static String stateLabel(State state) {
	switch(state) {
	case AGING: return concise("Cheese aging","Aging");
	case READY: return concise("Cheese ready","Ready");
	default: return concise("Cheese status unknown","Unknown");
	}
    }

    public static int hoursToNext(String from, Loc loc) {
	String n = next(from, loc);
	return (n == null) ? 0 : hoursOf(n);
    }

    public static int remainingSeconds(GItem item, Gob gob, double meter) {
	if(item == null)
	    return -1;
	String kind = itemKind(item);
	if(kind == null)
	    return -1;
	if(itemState(item, meter) != State.AGING)
	    return -1;
	long ts = (item.meterUpdated > 0) ? item.meterUpdated : System.currentTimeMillis();
	return leftSeconds(kind, loc(gob), meter, ts);
    }

    public static int leftSeconds(Gob gob) {
	if(gob == null)
	    return 0;
	Loc loc = loc(gob);
	int min = Integer.MAX_VALUE;
	for(Tray t : rackOf(gob)) {
	    if(t.state != State.AGING)
		continue;
	    int s = leftSeconds(t.kind, loc, t.meter, t.ts, t.until);
	    if(s >= 0)
		min = Math.min(min, s);
	}
	return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    public static boolean ready(Gob gob) {
        for(Tray tray:rackOf(gob))if(trayState(tray)==State.READY)return true;
        return false;
    }

    public static boolean aging(Gob gob) {
	if(gob == null)
	    return false;
	Loc loc = loc(gob);
	for(Tray t : rackOf(gob)) {
	    if(t.state == State.AGING)
		return true;
	}
	return false;
    }

    private static int leftSeconds(String kind, Loc loc, double meter, long ts) {
	return leftSeconds(kind, loc, meter, ts, 0);
    }

    private static int leftSeconds(String kind, Loc loc, double meter, long ts, long until) {
	if(until > 0)
	    return (int) Math.max(0, Math.round((until - System.currentTimeMillis()) / 1000.0));
	int total = secondsToNext(kind, loc);
	if(total <= 0)
	    return -1;
	double left = total * ((meter > 0) ? (1.0 - meter) : 1.0);
	if(ts > 0)
	    left -= (System.currentTimeMillis() - ts) / 1000.0;
	else
	    return -1;
	return (int) Math.max(0, Math.round(left));
    }

    public static int secondsToNext(String kind, Loc loc) {
	String target = next(kind, loc);
	if(target == null)
	    return 0;
	Integer days = GAME_DAYS.get(norm(target));
	return days == null ? hoursOf(target) * 3600
	    : (int)Math.ceil(days * 86400d / 3.29);
    }

    private static List<Tray> rackOf(Gob gob) {
	loadRacks();
	List<Tray> saved = RACK.get(gob.id);
	if((saved != null) && !saved.isEmpty())
	    return saved;
	return Collections.emptyList();
    }

    private static boolean sameRack(List<Tray> saved, List<String> overlay) {
	if(saved.size() != overlay.size())
	    return false;
	for(int i = 0; i < saved.size(); i++) {
	    String a = norm(saved.get(i).kind);
	    String b = norm(overlay.get(i));
	    if(a.equals(b) || genericTray(a) || genericTray(b))
		continue;
	    return false;
	}
	return true;
    }

    private static boolean genericTray(String n) {
	return (n == null) || n.isEmpty() || n.equals("cheese") || n.equals("empty");
    }

    @SuppressWarnings("unchecked")
    private static void loadRacks() {
        if(racksLoaded)return;
        racksLoaded=true;
        Object saved=LocalStore.read("cheese-racks");
        if(saved instanceof Map)RACK.putAll((Map<Long,List<Tray>>)saved);
    }

    private static void saveRacks() {
        LocalStore.write("cheese-racks",new HashMap<>(RACK));
    }

    public static List<String> trays(Gob gob) {
	List<String> out = new ArrayList<>();
	if(gob == null)
	    return out;
	for(Tray t : rackOf(gob))
	    out.add(t.kind);
	return out;
    }

    public static int overlayTrayCount(Gob gob) {
	return traysFromOverlay(gob).size();
    }

    public static List<String> traysFromOverlay(Gob gob) {
	List<String> out = new ArrayList<>();
	if(gob == null)
	    return out;
	try {
	    List<Gob.Overlay> overlays = new ArrayList<>(gob.ols);
	    overlays.sort(Comparator.comparingInt(ol -> ol.id));
	    for(Gob.Overlay ol : overlays) {
		if(!GameObjects.overlayName(ol).startsWith("gfx/fx/eq"))
		    continue;
		Sprite spr = GameObjects.field(ol.spr, "espr", Sprite.class);
		if((spr == null) || (spr.res == null))
		    continue;
		String kind = trayKind(spr.res.name);
		if(kind != null)
		    out.add(kind);
	    }
	} catch (Loading ignored) {}
	return out;
    }

    public static String trayKind(String name) {
	if((name == null) || !name.startsWith("gfx/terobjs/items/cheesetray"))
	    return null;
	int p = name.lastIndexOf('-');
	if(p < 0)
	    return null;
	String suf = name.substring(p + 1);
	if(suf.isEmpty() || suf.equals("empty"))
	    return null;
	return suf;
    }

    public static boolean caveTile(String name) {
	return name != null && (name.endsWith("/cave") || name.endsWith("/mine")
	    || name.endsWith("/lushcave") || name.endsWith("/gleamgrotto"));
    }

    private static boolean caveGrid(MCache map, Coord tile) {
	MCache.Grid grid = map.getgridt(tile);
	Pair<Integer, Boolean> cached = CAVE_GRIDS.get(grid.id);
	if(cached != null && cached.a == grid.seq)
	    return cached.b;
	Set<Integer> types = new HashSet<>();
	for(int id : grid.tiles)
	    types.add(id);
	boolean cave = false;
	for(int id : types) {
	    Resource resource = map.tilesetr(id);
	    if(resource != null && caveTile(resource.name)) {
		cave = true;
		break;
	    }
	}
	CAVE_GRIDS.put(grid.id, new Pair<>(grid.seq, cave));
	return cave;
    }

    @SuppressWarnings("unchecked")
    private static synchronized String locationKey(Gob gob) {
        if(!locationsLoaded) {
            locationsLoaded=true;
            Object saved=LocalStore.read("cheese-locations");
            if(saved instanceof Map)LOCATIONS.putAll((Map<String,Loc>)saved);
        }
        if(gob==null || gob.glob==null)return null;
        try {return "grid:"+gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz)).id;}
        catch(Loading e) {return null;}
    }

    public static Loc selectedLocation(Gob gob) {
	String key = locationKey(gob);
	return key == null ? null : LOCATIONS.get(key);
    }

    public static void cycleLocation(Gob gob) {
	String key = locationKey(gob);
	if(key == null) return;
	Loc old = LOCATIONS.get(key);
	if(old == null) LOCATIONS.put(key, Loc.CABIN);
	else if(old == Loc.OUTSIDE) LOCATIONS.remove(key);
	else LOCATIONS.put(key, Loc.values()[old.ordinal() + 1]);
	LocalStore.write("cheese-locations",new HashMap<>(LOCATIONS));
    }

    public static Loc loc(Gob gob) {
	Loc selected = selectedLocation(gob);
	if(selected != null)
	    return selected;
	if(gob == null)
	    return Loc.OUTSIDE;
	try {
	    Glob glob = gob.glob;
	    if((glob != null) && (glob.map != null) && (gob.rc != null)) {
		Coord tc = gob.rc.floor(MCache.tilesz);
		Resource floor = glob.map.tilesetr(glob.map.gettile(tc));
		if(floor != null && floor.name.contains("/cellar"))
		    return Loc.CELLAR;
		// A paved tile hides the cave floor directly underneath the rack.
		// Inspect the map grid, as the minimap cave detection does.
		if(caveGrid(glob.map, tc))
		    return Loc.MINE;
		Resource ts = glob.map.tilesetr(glob.map.gettile(tc));
		if(ts != null) {
		    String n = ts.name;
		    if((n != null) && (n.contains("/cave") || n.contains("/mine") || n.startsWith("gfx/tiles/rocks/")))
			return Loc.MINE;
		}
	    }
	} catch (Loading ignored) {}
	if(near(gob, "gfx/terobjs/arch/"))
	    return Loc.CABIN;
	try {
	    Glob glob = gob.glob;
	    if((glob != null) && (glob.map != null) && (gob.rc != null)) {
		Coord tc = gob.rc.floor(MCache.tilesz);
		Resource ts = glob.map.tilesetr(glob.map.gettile(tc));
		if(ts != null) {
		    String n = ts.name;
		    if(n != null) {
			if(n.contains("board") || n.contains("floor") || n.contains("paving")
			    || n.contains("wood") || n.contains("plank") || n.contains("brick")
			    || n.contains("cobble") || n.contains("stonehouse"))
			    return Loc.CABIN;
		    }
		}
	    }
	} catch (Loading ignored) {}
	return Loc.OUTSIDE;
    }

    public static int agingSeconds(Gob gob) {
	Loc loc = loc(gob);
	int min = Integer.MAX_VALUE;
	for(String tray : trays(gob)) {
	    String next = next(tray, loc);
	    if((next == null) || next.isEmpty())
		continue;
	    min = Math.min(min, hoursOf(next) * 3600);
	}
	return (min == Integer.MAX_VALUE) ? 0 : min;
    }

    public static String next(String from, Loc loc) {
	String key = canonical(from);
	for(Step s : STEPS) {
	    if((s.loc == loc) && canonical(s.from).equals(key))
		return s.to;
	}
	// RoB: placements outside the listed cheese paths produce Generic Gouda
	// in nine game days. Unknown tray codes are not a known cheese variety.
	return known(from) && !canonical(GOUDA).equals(key) ? GOUDA : null;
    }

    public static boolean isCurd(String key) {
	String n = canonical(key);
	return "curd".equals(n) || "sheepcurd".equals(n) || "goatcurd".equals(n);
    }

    public static Milk milkOf(String kind) {
	String key = canonical(kind);
	if("curd".equals(key)) return Milk.COW;
	if("sheepcurd".equals(key)) return Milk.SHEEP;
	if("goatcurd".equals(key)) return Milk.GOAT;
	for(Step step : STEPS) {
	    if(canonical(step.from).equals(key) || canonical(step.to).equals(key))
		return step.milk;
	}
	return null;
    }

    public static String milkLabel(String kind) {
	Milk milk = milkOf(kind);
	if(milk == null) return L10n.tr("Unknown milk product");
	switch(milk) {
	case SHEEP: return L10n.tr("Sheep milk product");
	case GOAT: return L10n.tr("Goat milk product");
	default: return L10n.tr("Cow milk product");
	}
    }

    public static String productLabel(String kind, String stage) {
	Milk milk = milkOf(kind);
	boolean curd = isCurd(kind);
	String key = curd ? "Curd" : "Cheese product";
	if(milk != null) {
	    switch(milk) {
	    case COW: key = curd ? "Cow curd product" : "Cow cheese product"; break;
	    case SHEEP: key = curd ? "Sheep curd product" : "Sheep cheese product"; break;
	    case GOAT: key = curd ? "Goat curd product" : "Goat cheese product"; break;
	    }
	}
        String english=milk==null?(curd?"Curd":"Unknown milk"):milk==Milk.COW?"Cow":milk==Milk.SHEEP?"Sheep":"Goat";
        if(curd&&milk!=null)english+=" curd";
	return concise(key,english) + (curd ? " " + stageLabel("L0")
	    : stage != null && !stage.isEmpty() ? " " + stageLabel(stage) : "");
    }

    static String concise(String key,String english){String translated=L10n.tr(key);return translated.equals(key)?english:translated;}

    public static String locationPhrase(Loc location) {
	switch(location) {
	case MINE: return concise("Cheese location mine","Mine");
	case CELLAR: return concise("Cheese location cellar","Cellar");
	case CABIN: return concise("Cheese location cabin","Cabin");
	default: return concise("Cheese location outside","Outside");
	}
    }

    private static boolean known(String key) {
	String n = canonical(key);
	if(HOURS.containsKey(n))
	    return true;
	for(Step s : STEPS) {
	    if(canonical(s.from).equals(n) || canonical(s.to).equals(n))
		return true;
	}
	return false;
    }

    private static int hoursOf(String name) {
	Integer h = HOURS.get(norm(name));
	return (h == null) ? 48 : h;
    }

    private static String canonical(String s) {
	String n = norm(s);
	String a = ALIAS.get(n);
	return (a != null) ? a : n;
    }

    private static String norm(String s) {
	if(s == null)
	    return "";
	return s.toLowerCase(Locale.ROOT).replace("'", "").replace("`", "").replace(" ", "").replace("-", "").replace("cheese", "");
    }

    private static boolean near(Gob gob, String part) {
	if((gob == null) || (gob.glob == null) || (gob.rc == null))
	    return false;
	try {
	    OCache oc = gob.glob.oc;
	    synchronized(oc) {
		for(Gob g : oc) {
		    if((g == gob) || (g.rc == null))
			continue;
		    String n = GameObjects.resourceName(g);
		    if((n != null) && n.contains(part) && (g.rc.dist(gob.rc) < 110))
			return true;
		}
	    }
	} catch (Exception ignored) {}
	return false;
    }
}
