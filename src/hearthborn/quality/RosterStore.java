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

package hearthborn.quality;
import hearthborn.L10n;
import hearthborn.util.GameObjects;
import hearthborn.io.LocalStore;
import hearthborn.scripts.CropProfile;

import haven.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jagy2014
 */
public class RosterStore {
    private static final String CFG = "roster.cfg";
    private static final Object lock = new Object();
    private static Map<String, Rec> recs = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    public static class Rec implements java.io.Serializable {
        private static final long serialVersionUID=1L;
	public String kind;
	public long gobId;
	public String name = "";
    public String objectKey,resource;
	public double q;
    public String comment = "";
	public int count;
	public String amount = "";
    }

    public static String buildingKey(long id) {
	return("b:" + id);
    }

    public static double tenths(double q) {
	return Math.round(q * 10.0) / 10.0;
    }

    public static String fmtQ(double q) {
	if(q <= 0)
	    return("-");
	return String.format(java.util.Locale.US, "%.1f", tenths(q));
    }

    public static final String KIND_SEED = "seed";
    public static final String KIND_SAPLING = "sapling";

    private static final Set<String> CROP_BASES = new HashSet<>(Arrays.asList(
	"barley", "wheat", "flax", "hemp", "millet", "pipeweed", "pumpkin", "poppy",
	"lettuce", "cucumber", "carrot", "leek", "turnip", "grape",
	"cavefern", "gorse", "swamplily", "towercap", "champignon",
	"beet", "yellowonion", "redonion", "onion", "garlic", "hops", "peapod"
    ));
    private static final Set<String> TREE_FRUITS = new HashSet<>(Arrays.asList(
	"mulberry", "lemon", "pear", "apple", "applegreen", "plum", "cherry", "olive", "quince",
	"almond", "walnut", "chestnut", "hazelnut", "persimmon", "medlar", "fig", "orange",
	"sorbapple", "woodapple", "crabapple", "conker", "kingsoakacorn", "woodstrawberry"
    ));

    public static String seedKey(String id) {
	return("s:" + id);
    }

    public static String seedIdFromRes(String res) {
	if((res == null) || res.isEmpty())
	    return(null);
	int k = res.lastIndexOf('/');
	String base = (k >= 0) ? res.substring(k + 1) : res;
	if(base.startsWith("seed-"))
	    return plantKind(base) != null ? base : null;
	if(base.startsWith("champignon"))
	    return("champignon");
	if(CropProfile.isPlantingMaterial(base) || CROP_BASES.contains(base) || TREE_FRUITS.contains(base))
	    return base;
	String fruit = saplingId(res);
	if(fruit != null)
	    return fruit;
	int herbs = res.indexOf("/herbs/");
	if(herbs >= 0)
	    return("herbs/" + base);
	return(null);
    }

    public static String seedIdFromName(String name) {
	if((name == null) || name.isEmpty())
	    return(null);
	name = name.trim();
	String id = seedIdFromRes(name);
	if(id != null)
	    return id;
	if(name.regionMatches(true, 0, "Seeds of ", 0, 9)) {
	    String crop = name.substring(9).trim().toLowerCase(java.util.Locale.ENGLISH).replace(" ", "");
	    if(!crop.isEmpty())
		return("seed-" + crop);
	}
	return(null);
    }

    public static boolean isCropDisplayName(String name) {
	if((name == null) || name.isEmpty())
	    return(false);
	name = name.trim();
	if(name.regionMatches(true, 0, "Seeds of ", 0, 9))
	    return(true);
	String id=name.toLowerCase(java.util.Locale.ENGLISH).replace(" ", "");
	return CropProfile.isPlantingMaterial(id) || CROP_BASES.contains(id);
    }

    public static boolean isPlantRes(String res) {
	return(res != null) && res.contains("/plants/");
    }

    public static String plantKind(String id) {
	if((id == null) || id.isEmpty())
	    return(null);
	if(CropProfile.isPlantingMaterial(id))
	    return KIND_SEED;
	String base = id.startsWith("seed-") ? id.substring(5) : id;
	if(base.endsWith("germ") || base.endsWith("malt"))
	    return(null);
	if(CROP_BASES.contains(base))
	    return KIND_SEED;
	if(id.startsWith("seed-") || id.startsWith("herbs/") || TREE_FRUITS.contains(base)
	    || (saplingId(id) != null))
	    return KIND_SAPLING;
	return(null);
    }

    public static String seedDisplayName(String id) {
	if((id == null) || id.isEmpty())
	    return("");
	String name = GameObjects.name("gfx/invobjs/" + id);
	if(!isRawSeedLabel(name, id) || !id.startsWith("seed-"))
	    return name;
	String tree = id.substring(5);
	String t = GameObjects.name("gfx/terobjs/mm/trees/" + tree);
	if((t != null) && !t.isEmpty() && !t.equals("gfx/terobjs/mm/trees/" + tree))
	    return t;
	t = GameObjects.name("gfx/terobjs/mm/bushes/" + tree);
	if((t != null) && !t.isEmpty() && !t.equals("gfx/terobjs/mm/bushes/" + tree))
	    return t;
	return name;
    }

    private static boolean isRawSeedLabel(String name, String id) {
	if((name == null) || (id == null))
	    return(true);
	return name.replace("-", "").replace(" ", "").equalsIgnoreCase(id.replace("-", "").replace("/", ""));
    }

    public static String barrelKey(long id) {
	return("k:" + id);
    }

    private static final String STAMP_KEY = "_stamp";

    public static String stamp() {
	load();
	Rec r = recs.get(STAMP_KEY);
	return((r == null) || (r.comment == null)) ? "" : r.comment;
    }

    public static void setStamp(String comment) {
	load();
	String next = (comment == null) ? "" : comment;
	synchronized(lock) {
	    Rec r = recs.get(STAMP_KEY);
	    if(r == null) {
		r = new Rec();
		r.kind = "meta";
		recs.put(STAMP_KEY, r);
	    } else if(next.equals(r.comment)) {
		return;
	    }
	    r.comment = next;
	    save();
	}
    }

    private static void applyStamp(Rec r) {
	if((r.comment == null) || r.comment.isEmpty())
	    r.comment = stamp();
    }

    public static String gobResName(Gob gob) {
	if(gob == null)
	    return(null);
	String res = GameObjects.resourceName(gob);
	if((res != null) && !res.isEmpty())
	    return(res);
	try {
	    Resource r = GameObjects.resource(gob);
	    if(r != null)
		return(r.name);
	} catch (Loading ignored) {}
	return(null);
    }

    public static boolean isWoodyPlant(String resource){
        return resource!=null&&(resource.startsWith("gfx/terobjs/trees/")||resource.startsWith("gfx/terobjs/bushes/"))&&!resource.endsWith("log")&&!resource.endsWith("stump");
    }
    public static boolean isBuildingGob(Gob gob) {
	if(gob == null)
	    return(false);
	if(isVesselGob(gob))
	    return(false);
	String res = gobResName(gob);
	if((res == null) || res.isEmpty())
	    return(true);
        if(isWoodyPlant(res))return true;
	if(isPlantRes(res))
	    return(false);
	if(res.startsWith("gfx/terobjs/trees/") || res.startsWith("gfx/terobjs/bushes/")
	    || res.startsWith("gfx/terobjs/herbs/") || res.startsWith("gfx/terobjs/items/")
	    || res.startsWith("gfx/kritter/") || res.startsWith("gfx/borka/")
	    || res.startsWith("gfx/terobjs/mm/"))
	    return(false);
	return res.startsWith("gfx/terobjs/");
    }

    public static void noteBuilding(Gob gob,double quality,boolean force){
        String resource=GameObjects.resourceName(gob);
        Rec previous=get(buildingKey(gob.id));
        noteBuilding(gob.id,GameObjects.name(resource),quality>0?quality:previous==null?0:previous.q,force,isWoodyPlant(resource));
        Rec record=get(buildingKey(gob.id));if(record==null)return;
        String objectKey=hearthborn.world.ObjectNames.persistentKey(gob);
        synchronized(lock){
            if(!Objects.equals(record.objectKey,objectKey)||!Objects.equals(record.resource,resource)){
                record.objectKey=objectKey;record.resource=resource;save();
            }
        }
    }
    private static final Properties legacyBuildingNames=hearthborn.l10n.Catalog.load("/hearthborn/l10n/ru/window.properties");
    public static String buildingDisplayName(Rec record){
        if(record.objectKey!=null){String name=hearthborn.world.ObjectNames.knownName(record.objectKey,null);if(name!=null)return name;}
        String source=record.name==null?"":record.name;
        if(record.resource!=null){
            if(isWoodyPlant(record.resource))return GameObjects.name(record.resource.replace("gfx/terobjs/","gfx/terobjs/mm/"));
            String type=hearthborn.planner.ClaimPlan.buildingType(record.resource);
            return type==null?L10n.resource("tooltip",record.resource,source):L10n.text("window",type);
        }
        // Older entries stored the displayed name rather than the resource identity.
        for(String english:new TreeSet<>(legacyBuildingNames.stringPropertyNames()))if(source.equals(legacyBuildingNames.getProperty(english))){source=english;break;}
        if(source.equals("Smelter"))source="Ore Smelter";
        return L10n.text("window",source);
    }
    public static void noteBuilding(long id, String name, double q) {
	noteBuilding(id, name, q, false);
    }

    public static void noteBuilding(long id, String name, double q, boolean force) {
        noteBuilding(id,name,q,force,false);
    }
    private static void noteBuilding(long id,String name,double q,boolean force,boolean woody) {
	if(!woody&&isCropDisplayName(name))
	    return;
	q = tenths(q);
	load();
	String key = buildingKey(id);
	synchronized(lock) {
	    Rec r = recs.get(key);
	    if(r == null) {
		if(!force)
		    return;
		r = new Rec();
		r.kind = woody?"tree":"building";
		r.gobId = id;
		applyStamp(r);
		recs.put(key, r);
	    } else if((r.q == q) && ((name == null) || name.isEmpty() || name.equals(r.name)) && (!woody || "tree".equals(r.kind))) {
		return;
	    }
	    if(woody) r.kind="tree";
	    if((q > 0) || force)
		r.q = q;
	    if((name != null) && !name.isEmpty())
		r.name = name;
	    save();
	}
    }

    public static boolean observeSeed(GItem item) {
        Map<String,Rec> found=new LinkedHashMap<>();
        collectSeeds(item,found);
        for(Rec record:found.values())noteBestSeed(record.name,record.q,record.count);
        return !found.isEmpty();
    }

    private static void collectSeeds(GItem item,Map<String,Rec> found) {
        if(item==null)return;
        // Native stacks may keep the crop resource on their shell. Its displayed
        // average is not a planting-quality record; inspect the actual members first.
        if(item.contents!=null&&!item.contents.children(GItem.class).isEmpty()){
            for(GItem member:item.contents.children(GItem.class))collectSeeds(member,found);
            return;
        }
	String res = item.resource().name;
	if((res == null) || res.isEmpty())
	    res = item.resource().name;
	String id = seedIdFromRes(res);
	if((id == null) || (plantKind(id) == null))
	    return;
	double q;
	try {
	    q = tenths(GameObjects.quality(item));
	} catch (Loading ignored) {
	    return;
	}
	if(q <= 0)
	    return;
	float n = 1f;
	try {
	    Float qn = (float)GameObjects.count(item);
	    if(qn != null)
		n = qn;
	} catch (Loading ignored) {}
	if(item.num > 1)
	    n = Math.max(n, item.num);
        int count=Math.max(1,Math.round(n));Rec best=found.get(id);
        if(best==null||q>best.q){best=new Rec();best.name=id;best.q=q;best.count=count;found.put(id,best);}
        else if(q==best.q)best.count+=count;
    }

    public static void observeBarrelSeeds(String contents, double q, String amount) {
	String id = seedIdFromName(contents);
	if(id == null)
	    return;
	noteBestSeed(id, q, parseCount(amount));
    }

    public static void noteBestSeed(String id, double q, int count) {
	if((id == null) || id.isEmpty())
	    return;
	String kind = plantKind(id);
	if(kind == null)
	    return;
	q = tenths(q);
	if(q <= 0)
	    return;
	if(count < 0)
	    count = 0;
	load();
	String key = seedKey(id);
	synchronized(lock) {
	    Rec r = recs.get(key);
	    if(r == null) {
		r = new Rec();
		r.kind = kind;
		r.name = id;
		applyStamp(r);
		recs.put(key, r);
	    } else if((q < r.q) || ((q == r.q) && (count <= r.count))) {
		if(!kind.equals(r.kind)) {
		    r.kind = kind;
		    save();
		}
		return;
	    }
	    boolean better = (r.q < q);
	    r.kind = kind;
	    r.name = id;
	    r.q = q;
	    r.count = count;
	    if(better)
		applyStamp(r);
	    save();
	}
    }

    private static int parseCount(String amount) {
	if((amount == null) || amount.isEmpty())
	    return(0);
	int sp = amount.trim().indexOf(' ');
	String n = (sp > 0) ? amount.trim().substring(0, sp) : amount.trim();
	try {
	    return Math.max(0, Math.round(Float.parseFloat(n)));
	} catch (NumberFormatException ignored) {
	    return(0);
	}
    }

    private static long pendingVessel;

    public static boolean isVesselGob(Gob gob) {
	if(gob == null)
	    return(false);
	String res = gobResName(gob);
	if(res == null)
	    return(false);
	return res.startsWith("gfx/terobjs/barrel")
	    || res.startsWith("gfx/terobjs/cistern")
	    || res.startsWith("gfx/terobjs/demijohn");
    }

    public static void markVesselPick(long id) {
	pendingVessel = id;
    }

    public static boolean shouldNoteVessel(long id) {
	if(id == 0)
	    return(false);
	if(pendingVessel == id)
	    return(true);
	load();
	return recs.get(barrelKey(id)) != null;
    }

    public static void clearVesselPick() {
	pendingVessel = 0;
    }

    public static boolean isEmptyBarrel(String name) {
	if((name == null) || name.isEmpty())
	    return(true);
	String n = name.trim();
	if(n.equalsIgnoreCase("Empty") || n.equals("Пусто") || n.equals("Содержит: Пусто."))
	    return(true);
	String empty = L10n.tr("Empty");
	return n.equals(empty) || n.contains("Пусто");
    }

    public static void noteBarrel(long id, String contents, double q, String amount) {
	q = tenths(q);
	load();
	String key = barrelKey(id);
	String name = (contents == null) ? "" : contents;
	String amt = (amount == null) ? "" : amount;
	synchronized(lock) {
	    Rec r = recs.get(key);
	    if(isEmptyBarrel(name)) {
		if(r != null) {
		    recs.remove(key);
		    save();
		}
		return;
	    }
	    if(r == null) {
		r = new Rec();
		r.kind = "barrel";
		r.gobId = id;
		applyStamp(r);
		recs.put(key, r);
	    } else if((r.q == q) && name.equals(r.name) && amt.equals(r.amount == null ? "" : r.amount)) {
		if(pendingVessel == id)
		    pendingVessel = 0;
		return;
	    }
	    r.name = name;
	    r.q = q;
	    r.amount = amt;
	    if(pendingVessel == id)
		pendingVessel = 0;
	    save();
	}
    }

    public static void setComment(String key, String comment) {
	if((key == null) || key.isEmpty())
	    return;
	load();
	synchronized(lock) {
	    Rec r = recs.get(key);
	    if(r == null)
		return;
	    String next = (comment == null) ? "" : comment;
	    if(next.equals(r.comment))
		return;
	    r.comment = next;
	    save();
	}
    }

    public static void remove(String key) {
	load();
	synchronized(lock) {
	    if(recs.remove(key) != null)
		save();
	}
    }

    public static void removeAll(Collection<String> keys) {
	if((keys == null) || keys.isEmpty())
	    return;
	load();
	synchronized(lock) {
	    boolean any = false;
	    for(String key : keys) {
		if(recs.remove(key) != null)
		    any = true;
	    }
	    if(any)
		save();
	}
    }

    public static Rec get(String key) {
	load();
	return recs.get(key);
    }

    public static Rec trackedGob(long id) {
	if(id == 0)
	    return null;
	load();
	Rec r = recs.get(buildingKey(id));
	if(r != null)
	    return r;
	return recs.get(barrelKey(id));
    }

    public static List<Rec> buildings() {
	load();
	List<String> drop = new ArrayList<>();
	List<Rec> out = new ArrayList<>();
	for(Map.Entry<String, Rec> e : recs.entrySet()) {
	    Rec r = e.getValue();
	    if(!listedBuilding(r))
		continue;
	    if(isCropDisplayName(r.name))
		drop.add(e.getKey());
	    else
		out.add(r);
	}
	if(!drop.isEmpty())
	    removeAll(drop);
	return out;
    }

    public static List<Rec> seeds() {
	return list(KIND_SEED);
    }

    public static List<Rec> trees() {
	load();
	List<Rec> out=new ArrayList<>();
	for(Rec record:recs.values()) if(listedTree(record)) out.add(record);
	return out;
    }
    static boolean listedBuilding(Rec record) {
	return record!=null && "building".equals(record.kind) && !isWoodyPlant(record.resource);
    }
    static boolean listedTree(Rec record) {
	return record!=null && ("tree".equals(record.kind) || isWoodyPlant(record.resource));
    }
    public static List<Rec> saplings() {
	return list(KIND_SAPLING);
    }

    public static List<Rec> barrels() {
	load();
	List<String> drop = new ArrayList<>();
	List<Rec> out = new ArrayList<>();
	for(Map.Entry<String, Rec> e : recs.entrySet()) {
	    Rec r = e.getValue();
	    if(r == null || !"barrel".equals(r.kind))
		continue;
	    if(isEmptyBarrel(r.name))
		drop.add(e.getKey());
	    else
		out.add(r);
	}
	if(!drop.isEmpty())
	    removeAll(drop);
	return out;
    }

    private static List<Rec> list(String kind) {
	load();
	List<Rec> out = new ArrayList<>();
	for(Rec r : recs.values()) {
	    if(kind.equals(r.kind))
		out.add(r);
	}
	return out;
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        if(loaded)return;
        loaded=true;
        Object saved=LocalStore.read("quality-roster");
        if(saved instanceof Map)recs.putAll((Map<String,Rec>)saved);
    }

    private static void save() {
	LocalStore.write("quality-roster",new HashMap<>(recs));
    }
    private static final Set<String> SAPLING_ITEMS=new HashSet<>();
    static {
        try(java.io.InputStream in=RosterStore.class.getResourceAsStream("sapling-items.txt")) {
            if(in!=null) {
                java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
                String line;while((line=reader.readLine())!=null)SAPLING_ITEMS.add(line);
            }
        } catch(java.io.IOException e) {System.err.println("Could not read sapling item catalog: "+e.getMessage());}
    }
    private static String saplingId(String resource) {
        if(resource==null)return null;
        String base=resource.substring(resource.lastIndexOf('/')+1);
        if(SAPLING_ITEMS.contains(base))return base;
        int dash=base.lastIndexOf('-');
        return dash>0 && SAPLING_ITEMS.contains(base.substring(0,dash)) ? base.substring(0,dash):null;
    }
}
