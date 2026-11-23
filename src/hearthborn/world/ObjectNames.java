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

package hearthborn.world;

import haven.*;
import hearthborn.L10n;
import hearthborn.io.LocalStore;
import hearthborn.timers.Timers;
import hearthborn.util.ObjectWindows;
import java.util.*;

/**
 * Local names use map-grid positions rather than transient server object IDs.
 *
 * @author Jagy2014
 */
public final class ObjectNames {
    private static final Map<String,String> names=new java.util.concurrent.ConcurrentHashMap<>(),owners=new java.util.concurrent.ConcurrentHashMap<>(),numbers=new java.util.concurrent.ConcurrentHashMap<>(),counters=new java.util.concurrent.ConcurrentHashMap<>(),places=new java.util.concurrent.ConcurrentHashMap<>(),touched=new java.util.concurrent.ConcurrentHashMap<>();
    private enum Type {
        RACK("cheeserack","Cheese Rack","cheese"),SMELTER("smelter","Ore Smelter","smelter"),OVEN("oven","Oven",null),
        CRUCIBLE("crucible","Crucible",null),KILN("kiln","Kiln","kiln"),TABLE("htable","Herbalist Table","table"),STEEL("steelcrucible","Steel Crucible","steel"),
        STACK("primsmelter","Stack Furnace","stack"),FINERY("fineryforge","Finery Forge","finery"),
        RAM("vehicle/bram","Battering ram","siege"),BALL("vehicle/wreckingball","Wrecking ball","siege");
        final String resource,label,source,prefix;
        Type(String resource,String label,String source){this.resource=resource;this.label=label;this.source=source;this.prefix="gfx/terobjs/"+resource+":";}
        private static final Type[] TYPES=values();
        private static final Map<String,Type> BY_RESOURCE=new HashMap<>();
        static {for(Type type:TYPES)BY_RESOURCE.put("gfx/terobjs/"+type.resource,type);}
        static Type resource(String name){return BY_RESOURCE.get(name);}
        static Type of(String key){for(Type type:TYPES)if(key.startsWith(type.prefix))return type;return null;}
    }
    private static final Set<String> namedResources=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Map<Window,Gob> controls=new WeakHashMap<>();
    // Track a loaded rack through lifting and placing, without persisting transient IDs as names.
    private static final Map<Glob,Map<Long,String>> rackPositions=new WeakHashMap<>();
    private static synchronized String rackKey(Gob gob,String current){
        load();Map<Long,String> seen=rackPositions.computeIfAbsent(gob.glob,world->new HashMap<>());
        String previous=seen.get(gob.id);
        if(gob.getattr(Following.class)!=null){
            if(previous==null)previous=owners.get("cheese:"+gob.id);
            if(previous!=null){seen.put(gob.id,previous);return previous;}
        }
        seen.put(gob.id,current);
        if(previous==null||previous.equals(current)||!previous.startsWith("gfx/terobjs/cheeserack:"))return current;
        boolean changed=false;
        for(Map<String,String> values:Arrays.asList(names,numbers,places,touched)){
            String value=values.remove(previous);
            // A previously used tile must not lend its old name to the moving rack.
            if(value!=null){values.put(current,value);changed=true;}
            else if(values.remove(current)!=null)changed=true;
        }
        for(Map.Entry<String,String> entry:owners.entrySet())if(previous.equals(entry.getValue())){entry.setValue(current);changed=true;}
        hearthborn.planner.ClaimSurvey.moved(previous);
        if(changed){save();Timers.namesChanged();}
        return current;
    }
    private static boolean loaded;
    public static String key(Gob gob){
        if(gob==null || gob.glob==null || gob.rc==null)return null;
        Drawable draw=gob.getattr(Drawable.class);Resource resource=draw==null?null:draw.getres();
        if(resource==null)return null;
        MCache.Grid grid=gob.glob.map.getgridt(gob.rc.floor(MCache.tilesz));
        Coord local=gob.rc.sub(new Coord2d(grid.ul).mul(MCache.tilesz)).floor(OCache.posres);
        String current=resource.name+":"+grid.id+":"+local.x+":"+local.y;
        return resource.name.equals("gfx/terobjs/cheeserack")?rackKey(gob,current):current;
    }
    private static synchronized void load(){
        if(loaded)return;loaded=true;
        Object data=LocalStore.read("object-names");
        if(data instanceof Map) {
            copy(((Map<?,?>)data).get("names"),names);
            for(String key:names.keySet()){int colon=key.indexOf(':');if(colon>0)namedResources.add(key.substring(0,colon));}
            copy(((Map<?,?>)data).get("owners"),owners);
            copy(((Map<?,?>)data).get("numbers"),numbers);copy(((Map<?,?>)data).get("counters"),counters);copy(((Map<?,?>)data).get("places"),places);copy(((Map<?,?>)data).get("touched"),touched);
        }
    }
    private static void copy(Object data,Map<String,String> into){
        if(data instanceof Map)for(Map.Entry<?,?> entry:((Map<?,?>)data).entrySet())
            if(entry.getKey() instanceof String && entry.getValue() instanceof String)into.put((String)entry.getKey(),(String)entry.getValue());
    }
    private static void save(){
        HashMap<String,HashMap<String,String>> data=new HashMap<>();
        data.put("names",new HashMap<>(names));data.put("owners",new HashMap<>(owners));data.put("numbers",new HashMap<>(numbers));data.put("counters",new HashMap<>(counters));data.put("places",new HashMap<>(places));data.put("touched",new HashMap<>(touched));LocalStore.write("object-names",data);
    }
    /** Assigns the next number only after opening, timing, renaming or recording quality. */
    private static boolean number(String key){
        Type type=Type.of(key);if(type==null)return false;
        boolean changed=touched.put(key,"1")==null;
        if(numbers.containsKey(key))return changed;
        int next=1;try{next=Integer.parseInt(counters.getOrDefault(type.name(),"0"))+1;}catch(NumberFormatException ignored){}
        numbers.put(key,Integer.toString(next));counters.put(type.name(),Integer.toString(next));return true;
    }
    private static String automatic(String key){
        Type type=key==null?null:Type.of(key);String number=numbers.get(key);
        return type==null||number==null||!touched.containsKey(key)?null:L10n.text("window",type.label)+" "+number;
    }
    /** Keeps a carried rack's saved identity attached while it moves. Nearby objects are not numbered. */
    public static void discover(List<Gob> objects){
        load();
        for(Gob gob:objects)try{
            if(gob.rc==null||gob.glob==null)continue;
            Drawable drawn=gob.getattr(Drawable.class);Resource resource=drawn==null?null:drawn.getres();
            if(resource==null||!resource.name.equals("gfx/terobjs/cheeserack"))continue;
            key(gob);
        }catch(Loading ignored){}
    }
    public static void bind(String source,Gob gob){
        if(source==null)return;
        try {String key=key(gob);if(key==null)return;load();
            boolean changed=number(key);if(!key.equals(owners.put(source,key)))changed=true;
            if(changed){save();Timers.namesChanged();}
        }catch(Loading ignored){}
    }
    /** Explicit quality recording also counts as interacting with this one object. */
    public static void touch(Gob gob) {
        String key=key(gob);if(key==null)throw new Loading();
        load();if(number(key)){save();Timers.namesChanged();}
    }
    public static boolean hasNames(){load();return !names.isEmpty()||!numbers.isEmpty();}
    /** Stable automatic number, independent of the current language and custom name. */
    public static String automaticNumber(Gob gob){
        try{String key=key(gob);load();return key==null||!touched.containsKey(key)?null:numbers.get(key);}catch(Loading pending){return null;}
    }
    public static String persistentKey(Gob gob){try{return key(gob);}catch(Loading waiting){return null;}}
    public static String name(Gob gob){
        try {String key=key(gob);load();return key==null?null:names.getOrDefault(key,automatic(key));}catch(Loading e){return null;}
    }
    public static String label(Gob gob){
        try{
            load();
            Drawable drawable=gob.getattr(Drawable.class);Resource resource=drawable==null?null:drawable.getres();
            if(resource==null||(!namedResources.contains(resource.name)&&Type.resource(resource.name)==null))return null;
            String key=key(gob);
            if(key==null)return null;
            String custom=names.get(key);
            if(custom!=null)return custom;
            if(!touched.containsKey(key))return null;
            return Type.of(key)==Type.RACK?numbers.get(key):automatic(key);
        }catch(Loading waiting){return null;}
    }
    public static void name(Gob gob,String value){
        String key=key(gob);nameKey(key,value);
    }
    private static void nameKey(String key,String value){
        if(key==null)return;load();number(key);
        String clean=value==null?"":value.replaceAll("[\\p{Cntrl}]"," ").trim();
        if(clean.length()>80)clean=clean.substring(0,80);
        if(clean.isEmpty())names.remove(key);else {names.put(key,clean);int colon=key.indexOf(':');if(colon>0)namedResources.add(key.substring(0,colon));}
        save();Timers.namesChanged();hearthborn.planner.ClaimSurvey.renamed(key,clean.isEmpty()?null:clean);
    }
    public static String timerName(String source,String fallback){
        if(source==null)return fallback;load();
        String owner=owners.get(source),prefix=source;
        if(owner==null)for(Map.Entry<String,String> entry:owners.entrySet())
            if(source.startsWith(entry.getKey()+":") && (owner==null || entry.getKey().length()>prefix.length())){owner=entry.getValue();prefix=entry.getKey();}
        String name=owner==null?null:names.getOrDefault(owner,automatic(owner));
        String result=name==null?fallback:source.equals(prefix)?name:name+" - "+fallback.replaceFirst(" #[0-9a-fA-F]+","");
        String place=owner==null?null:places.get(owner);
        if(source.startsWith("cheese:") && place!=null) {
            String label=place.equals("CELLAR")?L10n.tr("Basement"):place.equals("CABIN")?L10n.tr("Cabin"):place.equals("MINE")?L10n.tr("Mine"):L10n.tr("Outside");
            result+=" ("+label.toLowerCase(Locale.forLanguageTag(L10n.language()))+")";
        }
        return result;
    }
    public static void place(Gob gob,hearthborn.cheese.CheeseAging.Loc location){
        try {String key=key(gob);if(key==null||location==null)return;load();
            if(!location.name().equals(places.put(key,location.name()))){save();Timers.namesChanged();}
        }catch(Loading ignored){}
    }
    public static boolean highlight(GameUI gui,String source){
        if(gui==null||gui.ui==null||gui.ui.sess==null||source==null)return false;load();
        String owner=owners.get(source);int longest=-1;
        if(owner==null)for(Map.Entry<String,String> entry:owners.entrySet())if(source.startsWith(entry.getKey()+":")&&entry.getKey().length()>longest){owner=entry.getValue();longest=entry.getKey().length();}
        if(owner!=null)synchronized(gui.ui.sess.glob.oc){for(Gob gob:gui.ui.sess.glob.oc)try{
            if(owner.equals(key(gob))){gob.setattr(new ObjectHighlight(gob));return true;}
        }catch(Loading ignored){}}
        gui.ui.msg(L10n.tr("The timer's object is not currently loaded."));return false;
    }
    private static boolean matches(String caption,Gob gob){
        Drawable draw=gob.getattr(Drawable.class);Resource res=draw==null?null:draw.getres();if(res==null)return false;
        String[][] types={{"cheeserack","Rack","Cheese Rack","C. Rack"},{"smelter","Ore Smelter","Smith's Smelter"},
            {"oven","Oven"},{"kiln","Kiln"},{"htable","Herbalist Table"},{"steelcrucible","Steelbox","Steel Crucible","SteelCrucible"},
            {"primsmelter","Stack Furnace","Stack furnace"},{"fineryforge","Finery Forge"}};
        for(String[] type:types)if(res.name.startsWith("gfx/terobjs/"+type[0]))
            for(int i=1;i<type.length;i++)if(type[i].equalsIgnoreCase(caption)||L10n.text("window",type[i]).equals(caption))return true;
        return false;
    }
    public static void observe(Window window,GameUI gui){
        controls.entrySet().removeIf(entry->entry.getKey().parent==null);
        if(controls.containsKey(window))return;
        Gob gob=ObjectWindows.target(window,gui,target->matches(window.cap,target));
        if(gob==null)return;
        if(key(gob)==null)return;
        load();String key=key(gob);if(number(key)){save();Timers.namesChanged();}
        controls.put(window,gob);
    }
    /** A window whose own title can be changed by a click on the pencil beside it. */
    public interface Titled {
        String title();
        void rename();
        String renameTip();
    }
    public static boolean editable(Window window){return window instanceof Titled||controls.containsKey(window);}
    public static String title(Window window){
        if(window instanceof Titled)return ((Titled)window).title();
        Gob gob=controls.get(window);String alias=gob==null?null:name(gob);
        return alias==null?L10n.text("window",window.cap):alias;
    }
    public static void edit(Window window){
        if(window instanceof Titled){((Titled)window).rename();return;}
        Gob gob=controls.get(window);GameUI gui=window.getparent(GameUI.class);
        if(gob==null || gui==null)return;
        editKnown(gui,key(gob),name(gob));
    }
    public static String knownName(String key,String fallback){load();if(key==null)return fallback;String alias=names.get(key);if(alias!=null)return alias;String numbered=automatic(key);return numbered==null?fallback:numbered;}
    /** Renames or, with an empty value, resets the name of an object known only by its key. */
    public static void renameKnown(String key,String value){nameKey(key,value);}
    public static void edit(GameUI gui,Gob gob){if(gui!=null&&gob!=null)editKnown(gui,key(gob),name(gob));}
    public static void editKnown(GameUI gui,String key,String fallback){
        if(gui==null||key==null)return;
        Window editor=gui.add(new Window(UI.scale(280,95),"Object name"),UI.scale(100,100));
        editor.reqclose(editor::reqdestroy);
        String alias=knownName(key,fallback);
        TextEntry entry=editor.add(new TextEntry(UI.scale(260),alias==null?"":alias){
            public void activate(String text){nameKey(key,text);editor.reqdestroy();}
        },UI.scale(5,5));
        entry.canactivate=true;
        editor.add(new Button(UI.scale(80),"Save",false,()->{nameKey(key,entry.text());editor.reqdestroy();}),UI.scale(5,45));
        editor.add(new Button(UI.scale(80),"Clear",false,()->{nameKey(key,"");editor.reqdestroy();}),UI.scale(95,45));
        editor.add(new Button(UI.scale(80),"Cancel",false,editor::reqdestroy),UI.scale(185,45));
        editor.pack();editor.setfocus(entry);gui.setfocus(editor);
    }
    private ObjectNames(){}
}
