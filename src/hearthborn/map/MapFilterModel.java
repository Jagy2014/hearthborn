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

package hearthborn.map;

import haven.*;
import hearthborn.L10n;
import hearthborn.ui.IconCategories;
import java.util.*;

/**
 * One catalog view over native icon settings and the existing tile selection.
 *
 * @author Jagy2014
 */
public final class MapFilterModel {
    public enum Type {
        ALL("All types"), OBJECTS("Objects"), TILES("Tiles");
        public final String title;
        Type(String title){this.title=title;}
    }

    public final class Entry {
        public final GobIcon.Setting setting;
        public final String resource,name,group;
        public final hearthborn.world.PlayerRelations.Kind playerKind;
        private final String hay;

        private Entry(GobIcon.Setting setting){
            this.setting=setting;playerKind=null;resource=setting.res.name;
            String original=setting.icon.name();
            name=L10n.text("tooltip",original);group=IconCategories.classify(resource).id;
            Resource.Tooltip tooltip=setting.icon.res.layer(Resource.tooltip);
            hay=searchText(name+" "+original+" "+(tooltip==null?"":tooltip.original)+" "+resource);
        }
        private Entry(String resource,String group){
            setting=null;playerKind=null;this.resource=resource;this.group=group;
            name=TerrainHighlight.name(resource);
            hay=searchText(name+" "+TerrainHighlight.englishName(resource)+" "+resource);
        }
        private Entry(hearthborn.world.PlayerRelations.Kind kind){
            setting=null;playerKind=kind;resource="hearthborn/player/"+kind.name().toLowerCase(Locale.ROOT);group="players";
            name=L10n.tr(kind.title);hay=searchText(name+" "+kind.title+" "+resource);
        }
        public Type type(){return setting==null&&playerKind==null?Type.TILES:Type.OBJECTS;}
        public java.awt.image.BufferedImage image(){return playerKind!=null?PlayerMarkers.image(playerKind):setting==null?null:setting.icon.image();}
        public boolean shown(){return playerKind!=null?PlayerMarkers.shown(playerKind):setting==null?TerrainHighlight.selected(resource):setting.show;}
        public void shown(boolean value){
            if(playerKind!=null)PlayerMarkers.shown(playerKind,value);
            else if(setting==null)TerrainHighlight.choose(Collections.singleton(resource),value);
            else {setting.show=value;settings.dsave();}
        }
        public boolean questSearch(){
            return setting!=null&&hearthborn.ui.QuestForage.searchesFor(resource,null);
        }
        public boolean displayChecked(){return shown()||questSearch();}
        public boolean soundChecked(){return setting!=null&&(setting.notify||questSearch());}
        public boolean corpse(){return setting!=null&&hearthborn.world.GobMarks.corpse(setting.icon.id());}
        public boolean soundEditable(){return setting!=null&&(corpse()||!hearthborn.world.GobMarks.hasDedicatedIconSound(resource));}
    }

    public final GobIcon.Settings settings;
    private final List<Entry> tiles=new ArrayList<>();
    private Map<GobIcon.Setting.ID,Entry> objects=new HashMap<>();
    private List<Entry> entries=Collections.emptyList();
    private Map<GobIcon.Setting.ID,GobIcon.Setting> previous;
    private int count=-1;
    private boolean pending;
    private double retry;

    public MapFilterModel(GobIcon.Settings settings){
        this.settings=settings;
        for(Map.Entry<String,List<String>> category:TerrainHighlight.catalog().entrySet()){
            String group;
            switch(category.getKey()){
                case "Ore": group="cave-ores"; break;
                case "Rocks": group="cave-rocks"; break;
                case "Natural": group="ground"; break;
                case "Paving": group="paving"; break;
                default: group=category.getKey().toLowerCase(Locale.ROOT);
            }
            for(String resource:category.getValue())tiles.add(new Entry(resource,group));
        }
        for(hearthborn.world.PlayerRelations.Kind kind:hearthborn.world.PlayerRelations.Kind.values())tiles.add(new Entry(kind));
        refresh();
    }

    /** Local radar additions mutate the same map; check its size as well as its identity. */
    public boolean refresh(){
        Map<GobIcon.Setting.ID,GobIcon.Setting> current;
        List<GobIcon.Setting> snapshot;
        synchronized(settings){
            current=settings.settings;
            if(current==previous&&current.size()==count&&(!pending||Utils.rtime()<retry))return false;
            count=current.size();snapshot=new ArrayList<>(current.values());
        }
        Map<GobIcon.Setting.ID,Entry> next=new HashMap<>();pending=false;
        for(GobIcon.Setting setting:snapshot)try{
            if(setting.res.name.equals("gfx/hud/mmap/plo")&&!Arrays.asList(setting.icon.id()).contains("dead"))continue;
            Entry cached=objects.get(setting.id);
            next.put(setting.id,cached!=null&&cached.setting==setting?cached:new Entry(setting));
        }catch(Loading waiting){pending=true;}
        objects=next;previous=current;retry=Utils.rtime()+.5;
        List<Entry> combined=new ArrayList<>(tiles);combined.addAll(objects.values());
        combined.sort(Comparator.comparing((Entry entry)->entry.name).thenComparing(entry->entry.type()).thenComparing(entry->entry.resource));
        entries=Collections.unmodifiableList(combined);
        return true;
    }

    public List<Entry> filter(String category,Type type,String query){
        String normalized=searchText(query).trim();
        String[] words=normalized.isEmpty()?new String[0]:normalized.split("\\s+");
        List<Entry> found=new ArrayList<>();
        for(Entry entry:entries){
            if(type!=Type.ALL&&entry.type()!=type)continue;
            if(!category.equals("all")&&!category.equals(entry.group))continue;
            boolean matches=true;for(String word:words)if(!entry.hay.contains(word)){matches=false;break;}
            if(matches)found.add(entry);
        }
        return found;
    }

    /** Bulk selection applies to exactly the rows visible under all three filters. */
    public void show(Collection<Entry> entries,boolean on){
        List<String> resources=new ArrayList<>();boolean icons=false;
        for(Entry entry:entries)if(entry.playerKind!=null)entry.shown(on);else if(entry.setting==null)resources.add(entry.resource);else {entry.setting.show=on;icons=true;}
        TerrainHighlight.choose(resources,on);
        if(icons)settings.dsave();
    }

    public static Map<String,String> categories(){
        Map<String,String> groups=new LinkedHashMap<>();
        groups.put("all","All");
        groups.put("section:cave","Cave");
        groups.put("ores","Ores");
        groups.put("cave-ores","Ores (cave tiles)");
        groups.put("rocks","Rocks");
        groups.put("cave-rocks","Rocks (cave tiles)");
        groups.put("section:ground","Ground");
        groups.put("ground","Terrain");
        groups.put("paving","Paving");
        for(IconCategories.Category category:IconCategories.Category.values())
            if(category!=IconCategories.Category.ALL&&category!=IconCategories.Category.ORES&&category!=IconCategories.Category.ROCKS)groups.put(category.id,category.title);
        groups.put("players","Players");
        return groups;
    }
    public static boolean section(String category){return category!=null&&category.startsWith("section:");}
    public static String searchText(String value){return value.toLowerCase(Locale.ROOT).replace('ё','е');}
}
