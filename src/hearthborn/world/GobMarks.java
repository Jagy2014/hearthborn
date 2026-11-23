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
import haven.render.*;
import hearthborn.L10n;
import java.awt.Color;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ground circles for objects, dangerous animals, and small critters, plus warning sounds.
 *
 * @author Jagy2014
 */
public final class GobMarks extends GAttrib {
    private static final String OBJECTS = "hearthborn/world/object-radius";
    private static final String SOUNDS = "hearthborn/world/animal-sounds";
    private static final String CALLS = "hearthborn/world/animal-calls";
    private static volatile boolean animalCalls = Utils.getprefb(CALLS, false);
    private static final String DANGER = "hearthborn/world/animal-radius";
    private static final String CRITTERS = "hearthborn/world/critter-circles";
    private static volatile boolean objectRadii = Utils.getprefb(OBJECTS, true);
    private static volatile boolean animalSounds = Utils.getprefb(SOUNDS, true);
    private static volatile boolean dangerRadii = Utils.getprefb(DANGER, true);
    private static volatile boolean critterCircles = Utils.getprefb(CRITTERS, true);
    private static volatile UI screen;
    private static volatile GameUI game;
    private static volatile long player = -1;
    private static volatile boolean widgetKnown, widgetCape, modelKnown, modelCape, capeDirty;
    private static final Set<String> missing = ConcurrentHashMap.newKeySet();
    private static final Pipe.Op UNCLICK = new IgnoreClicks();
    private static final float LIFT = 1.5f;
    private static final int SEGMENTS = 64;
    private static final Spec BEEHIVE = new Spec(150f, new Color(242, 255, 132, 100), new Color(242, 255, 132, 255));
    private static final Spec TROUGH = new Spec(200f, new Color(107, 214, 160, 100), new Color(107, 214, 160, 255));
    private static final Spec SUPPORT = new Spec(100f, new Color(192, 0, 0, 80), new Color(192, 0, 0, 255));
    private static final Spec COLUMN = new Spec(125f, new Color(192, 0, 0, 80), new Color(192, 0, 0, 255));
    private static final Spec BEAM = new Spec(150f, new Color(192,0,0,80),new Color(192,0,0,255));
    private static final Spec[] COVERED = {new Spec(100f,new Color(50,190,120,45),new Color(50,210,130,210)),new Spec(125f,new Color(50,190,120,45),new Color(50,210,130,210)),new Spec(150f,new Color(50,190,120,45),new Color(50,210,130,210))};
    private static final Spec DANGER_MARK = new Spec(120f, new Color(192, 0, 0, 140), new Color(192, 0, 0, 220));
    private static final Spec CRITTER_MARK = new Spec(10f, new Color(150, 230, 255, 140), null);
    /** Same size as critters, clearer blue, for arrows lying on the ground. */
    private static final Spec ARROW_MARK = new Spec(10f, new Color(70, 170, 255, 160), null);
    private static final Map<String, Alarm> ALARMS = alarms();
    /** aggressive animals. Horses, deer, and other tameable beasts are not in it. */
    private static final String[] AGGRESSIVE = {
        "/adder", "/badger", "/bear", "/beaver", "/boar", "/boreworm", "/caveangler", "/cavelouse",
        "/caverat", "/eagleowl", "/goat/wildgoat", "/goldeneagle", "/greenooze", "/lynx",
        "/mammoth", "/moose", "/orca", "/spermwhale", "/troll", "/walrus", "/wolf", "/wolverine"
    };
    private static final String[] PARTS = {
        "mammoth/mammothskull", "/orca/orcabeef", "/spermwhale/spermwhaleskull",
        "/spermwhale/spermwhalesteak", "/spermwhale/spermwhaleheart", "/spermwhale/spermwhaleskeleton"
    };
    private static final String[] CRITTER_NAMES = {
        "/bayshrimp", "/bogturtle", "/brimstonebutterfly", "/bullfinch", "/cavecentipede",
        "/cavemoth", "/chicken", "/crab", "/cranefly", "/dragonfly", "/dumbledore",
        "/earthworm", "/firefly", "/forestlizard", "/forestsnail", "/frog", "/grasshopper",
        "/hedgehog", "/irrbloss", "/items/grub", "/items/hoppedcow", "/items/mandrakespirited",
        "/jellyfish", "/kritter/tick/tick", "/ladybug", "/lobster", "/magpie", "/mallard",
        "/mole", "/monarchbutterfly", "/moonmoth", "/opiumdragon", "/ptarmigan", "/quail",
        "/rabbit", "/rat/rat", "/rockdove", "/sandflea", "/seagull", "/silkmoth",
        "/springbumblebee", "/squirrel", "/stagbeetle", "/stalagoomba", "/toad",
        "/waterstrider", "/whirlingsnowflake", "/woodgrouse-f", "/woodscorpion", "/woodworm"
    };

    private final Held objectMark = new Held();
    private final Held dangerMark = new Held();
    private final Held critterMark = new Held();
    private final Held arrowMark = new Held();
    private String known;
    private boolean knownCritter,knownCreature,knownAggressive,knownBat,knownArrow;
    private Spec knownPlace;
    private boolean dead;
    private int poseWait,unknownPoses;
    private boolean alarmPlayed;
    private long alarmGeneration;

    public GobMarks(Gob gob) { super(gob); }

    public static void attach(GameUI gui) {
        screen = gui.ui;
        game = gui;
        player = gui.plid;
    }

    public enum RadiusType {
        BEEHIVE("Beehives","gfx/terobjs/beehive"),
        TROUGH("Feeding troughs","gfx/terobjs/trough"),
        SUPPORT("Mine supports","gfx/terobjs/minesupport"),
        COLUMN("Mine columns","gfx/terobjs/column"),
        BEAM("Mine beams","gfx/terobjs/minebeam");
        public final String label,resource;
        RadiusType(String label,String resource){this.label=label;this.resource=resource;}
    }
    /** 0 hidden, 1 controlled by the radii command, 2 always visible. */
    private static final int[] radiusModes=new int[RadiusType.values().length];
    private static boolean radiusModesLoaded;
    private static void loadRadiusModes(){
        if(radiusModesLoaded)return;
        radiusModesLoaded=true;
        for(RadiusType type:RadiusType.values())radiusModes[type.ordinal()]=Utils.clip(Utils.getprefi("hearthborn/world/radius-mode/"+type.name(),1),0,2);
    }
    public static int radiusMode(RadiusType type){loadRadiusModes();return radiusModes[type.ordinal()];}
    public static void radiusMode(RadiusType type,int mode){loadRadiusModes();int clipped=Utils.clip(mode,0,2);radiusModes[type.ordinal()]=clipped;Utils.setprefi("hearthborn/world/radius-mode/"+type.name(),clipped);refresh();}
    public static boolean radiusVisible(String resource){
        for(RadiusType type:RadiusType.values())if(type.resource.equals(resource)){
            int mode=radiusMode(type);return mode==2 || (mode==1 && objectRadii);
        }
        if(resource.equals("gfx/terobjs/ladder")){int mode=radiusMode(RadiusType.SUPPORT);return mode==2||(mode==1&&objectRadii);}
        return false;
    }
    /** True while the radii of the mine's supports, columns or beams are on the screen; the shading of covered tiles goes with them. */
    public static boolean supportRadiiShown(){
        return radiusVisible(RadiusType.SUPPORT.resource)||radiusVisible(RadiusType.COLUMN.resource)||radiusVisible(RadiusType.BEAM.resource);
    }
    public static boolean objects() { return objectRadii; }
    public static void objects(boolean value) { objectRadii = value; Utils.setprefb(OBJECTS, value); refresh(); }
    public static boolean sounds() { return animalSounds; }
    public static void sounds(boolean value) { animalSounds = value; Utils.setprefb(SOUNDS, value); }
    public static boolean calls() { return animalCalls; }
    public static void calls(boolean value) { animalCalls=value; Utils.setprefb(CALLS,value); }
    public enum Voice {
        SETTINGS("settings","As in settings"),NAME("name","Animal name"),CALL("call","Animal call"),FILE("file","Custom sound");
        public final String id,title;
        Voice(String id,String title){this.id=id;this.title=title;}
        static Voice of(String id){for(Voice voice:values())if(voice.id.equals(id))return voice;return SETTINGS;}
    }
    private static final String VOICE="hearthborn/world/animal-voice/";
    private static final String HEARD="hearthborn/world/animal-heard/";
    private static final String FILE_PATH="hearthborn/world/animal-file/";
    private static final Map<String,Voice> voiceCache=new HashMap<>();
    private static final Map<String,Boolean> heardCache=new HashMap<>();
    private static final Map<String,String> fileCache=new HashMap<>();
    /** Null when this animal still follows the general warning setting. */
    private static Voice stored(String resource){
        if(resource==null)return null;
        String value=Utils.getpref(VOICE+resource,null);
        return value==null?null:Voice.of(value);
    }
    public static Voice voice(String resource){
        if(resource==null)return Voice.SETTINGS;
        Voice cached=voiceCache.get(resource);
        if(cached!=null)return cached;
        Voice chosen=stored(resource);
        if(chosen==null){
            String icon=warningIcon(resource);
            if(icon!=null&&!icon.equals(resource))chosen=stored(icon);
        }
        if(chosen==null)chosen=Voice.SETTINGS;
        voiceCache.put(resource,chosen);
        return chosen;
    }
    public static void voice(String icon,Voice value){
        if(icon==null||value==null)return;
        Utils.setpref(VOICE+icon,value.id);
        for(String animal:ALARMS.keySet())if(icon.equals(warningIcon(animal)))Utils.setpref(VOICE+animal,value.id);
        voiceCache.clear();
    }
    public static boolean choosesWarning(String icon){
        if(icon==null)return false;
        for(String animal:ALARMS.keySet())if(icon.equals(warningIcon(animal)))return true;
        return false;
    }
    public static boolean warned(String resource){
        if(resource==null)return true;
        Boolean cached=heardCache.get(resource);
        if(cached!=null)return cached;
        String value=Utils.getpref(HEARD+resource,null);
        if(value==null){String icon=warningIcon(resource);if(icon!=null&&!icon.equals(resource))value=Utils.getpref(HEARD+icon,null);}
        boolean on=value==null||Boolean.parseBoolean(value);
        heardCache.put(resource,on);
        return on;
    }
    public static void warned(String icon,boolean on){
        if(icon==null)return;
        Utils.setprefb(HEARD+icon,on);
        heardCache.put(icon,on);
        for(String animal:ALARMS.keySet())if(icon.equals(warningIcon(animal))){Utils.setprefb(HEARD+animal,on);heardCache.put(animal,on);}
    }
    public static Path customFile(String resource){
        if(resource==null)return null;
        String value=fileCache.get(resource);
        if(value==null){
            value=Utils.getpref(FILE_PATH+resource,null);
            if(value==null||value.isEmpty()){String icon=warningIcon(resource);if(icon!=null&&!icon.equals(resource))value=Utils.getpref(FILE_PATH+icon,null);}
            if(value==null)value="";
            fileCache.put(resource,value);
        }
        if(value.isEmpty())return null;
        try{return Utils.path(value);}catch(RuntimeException failure){return null;}
    }
    public static void customFile(String icon,Path path){
        if(icon==null)return;
        String value=path==null?"":path.toString();
        Utils.setpref(FILE_PATH+icon,value);
        fileCache.put(icon,value);
        for(String animal:ALARMS.keySet())if(icon.equals(warningIcon(animal))){Utils.setpref(FILE_PATH+animal,value);fileCache.put(animal,value);}
    }
    public static String customName(String resource){Path path=customFile(resource);return path==null?null:path.getFileName().toString();}
    public static void preview(UI ui,String icon){
        if(ui==null||icon==null)return;
        String gob=null;for(String animal:ALARMS.keySet())if(icon.equals(warningIcon(animal))){gob=animal;break;}
        Alarm alarm=gob==null?null:ALARMS.get(gob);
        try{
            Audio.CS clip;double volume=alarm==null?1:alarm.volume/50.0;
            if(voice(icon)==Voice.FILE){
                Path path=customFile(icon);if(path==null)return;
                try(InputStream in=Files.newInputStream(path)){clip=Audio.PCMClip.fromwav(new ByteArrayInputStream(in.readAllBytes()));}
            }else{
                if(alarm==null)return;
                Voice mode=voice(icon);boolean calls=mode==Voice.CALL||mode!=Voice.NAME&&animalCalls;
                String sound=alarm.sound(calls);if(sound==null)return;
                try(InputStream in=GobMarks.class.getResourceAsStream(sound)){if(in==null)return;clip=Audio.PCMClip.fromwav(new ByteArrayInputStream(in.readAllBytes()));}
            }
            ui.warning(new Audio.VolAdjust(clip,volume));
        }catch(IOException failure){ui.error(L10n.tr("Could not play the animal warning."));}
    }
    public static boolean callsFor(String resource){
        Voice chosen=voice(resource);
        if(chosen==Voice.CALL)return true;
        if(chosen==Voice.NAME)return false;
        return animalCalls;
    }
    private static String warningIcon(String gob){
        if(!ALARMS.containsKey(gob))return null;
        switch(gob){
        case "gfx/kritter/bear/polarbear": return "gfx/kritter/bear/icon-polarbear";
        case "gfx/kritter/rat/caverat": return "gfx/kritter/rat/icon";
        default:
            String mapped=hearthborn.map.RadarIcons.path(gob);
            if(mapped!=null)return mapped;
            int slash=gob.lastIndexOf('/');
            return slash<0?null:gob.substring(0,slash)+"/icon";
        }
    }
    public static boolean danger() { return dangerRadii; }
    public static void danger(boolean value) { dangerRadii = value; Utils.setprefb(DANGER, value); refresh(); }
    public static boolean critters() { return critterCircles; }
    public static void critters(boolean value) { critterCircles = value; Utils.setprefb(CRITTERS, value); refresh(); }

    /** Called when the equipment window gains or loses an item. */
    public static void equipment(Equipory equipment) {
        GameUI gui = game;
        if(equipment == null || gui == null || equipment != gui.equipment()) return;
        refresh();
    }

    /** The character model's worn gear. This arrives as the cloak comes off. */
    public static void model(Gob gob, java.util.List<Composited.ED> equipped) {
        if(gob == null || gob.id != player || player < 0) return;
        boolean cape = false;
        boolean pending = false;
        for(Composited.ED piece : equipped) {
            if(piece == null || piece.res == null || piece.res.res == null)
                continue;
            try {
                Resource resource = piece.res.res.get();
                if(resource != null && resource.name.contains("batcape"))
                    cape = true;
            } catch(Loading ignored) {
                pending = true;
            }
        }
        if(!cape && pending) {
            capeDirty = true;
            return;
        }
        capeDirty = false;
        boolean before = batCape();
        modelKnown = true;
        modelCape = cape;
        if(batCape() != before)
            refresh();
    }

    public static void poll() {
        if(!capeDirty) return;
        GameUI gui = game;
        if(gui == null || gui.ui == null || gui.ui.sess == null || player < 0) return;
        Gob self = gui.ui.sess.glob.oc.getgob(player);
        Drawable drawable = self == null ? null : self.getattr(Drawable.class);
        if(drawable instanceof Composite)
            model(self, ((Composite)drawable).comp.cequ);
    }

    /** Ctrl+R. Turns object radii on or off. Animal radii stay as they are. */
    public static void toggleRadii(GameUI gui) {
        boolean on = !objectRadii;
        objects(on);
        radiusDiagnostic(gui);
        if(gui != null && gui.ui != null)
            gui.ui.msg(on ? "Radius on" : "Radius off");
    }

    private static void radiusDiagnostic(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null||!hearthborn.io.DeveloperMode.enabled("debug-logs"))return;
        StringBuilder out=new StringBuilder("\n").append(java.time.Instant.now()).append(" ground-ring-v2 objects=").append(objectRadii).append('\n');
        for(RadiusType type:RadiusType.values())out.append(type).append('=').append(radiusMode(type)).append(' ');
        out.append('\n');
        for(Gob gob:gui.ui.sess.glob.oc)synchronized(gob){
            GobMarks marks=gob.getattr(GobMarks.class);if(marks==null)continue;
            String name=marks.resource();if(name==null||place(name)==null)continue;
            Held held=marks.objectMark;
            out.append(gob.id).append(' ').append(name).append(" at=").append(gob.rc)
                .append(" wanted=").append(radiusVisible(name)).append(" scene=").append(gob.slots.size())
                .append(" overlay=").append(held.overlay!=null&&gob.ols.contains(held.overlay));
            if(held.sprite!=null)out.append(" visible=").append(held.sprite.visible).append(" attachments=").append(held.sprite.slots.size());
            out.append('\n');
        }
        try{
            hearthborn.io.Logs.append("radii.log",out.toString());
        }catch(IOException failure){System.err.println("Hearthborn: could not record radius state: "+failure);}
    }

    public void lostSight(){alarmPlayed=false;alarmGeneration++;}
    public void ctick(double dt) {
        if(gob.slots.isEmpty()||!animalSounds){if(alarmPlayed)lostSight();}
        if(gob.id == player) return;
        String name = resource();
        if(name == null) return;
        if(!name.equals(known)) {
            known = name;
            knownCritter=critter(name);knownCreature=name.startsWith("gfx/kritter/")||knownCritter;
            knownBat=name.contains("/bat");knownAggressive=aggressiveSpecies(name);knownPlace=place(name);
            knownArrow=arrow(name);
            dead = false;
            poseWait = 0;unknownPoses=0;
            clear(objectMark);
            clear(dangerMark);
            clear(critterMark);
            clear(arrowMark);
        }
        boolean creature = knownCreature;
        if(creature && --poseWait <= 0) {
            poseWait = 20;
            Boolean down = fallen();
            if(down == null) {
                dead=false; // An unreadable pose is not confirmation of a corpse.
                unknownPoses++;
                if(unknownPoses <= 30) poseWait = 1;
                else {unknownPoses=0;dead=false;}
            } else {
                unknownPoses = 0;
                dead = down;
            }
        }
        Spec place = knownPlace;
        if(place==SUPPORT||place==COLUMN||place==BEAM){
            GobHealth health=gob.getattr(GobHealth.class);
            if(health==null||health.hp>=1)
                place=COVERED[name.equals("gfx/terobjs/minebeam")?2:name.equals("gfx/terobjs/column")?1:0];
        }
        boolean living = !dead;
        show(objectMark, place != null && radiusVisible(name), place, false);
        boolean aggressive=knownBat?!batCape():knownAggressive;
        show(dangerMark, dangerRadii && living && aggressive, DANGER_MARK, false);
        show(critterMark, critterCircles && living && knownCritter && !aggressive, CRITTER_MARK, true);
        show(arrowMark, critterCircles && knownArrow, ARROW_MARK, true);
        if(living && unknownPoses==0 && knownCreature) alarm(name);
    }

    private Alarm warning(String name) {
        if(name.startsWith("gfx/kritter/bat/")) {
            if(batCape()) return null;
            return ALARMS.get("gfx/kritter/bat/bat");
        }
        return ALARMS.get(name);
    }

    private Drawable drawn;
    private String named;
    private String resource() {
        Drawable drawable = gob.getattr(Drawable.class);
        if(drawable==drawn)return named;
        try {
            Resource resource = drawable == null ? null : drawable.getres();
            drawn=drawable;
            named=resource == null ? null : resource.name;
            return named;
        } catch(Loading loading) {
            drawn=null;named=null;
            return null;
        }
    }

    private void show(Held held, boolean want, Spec spec, boolean pick) {
        if(held.overlay!=null&&!gob.ols.contains(held.overlay)){
            held.sprite.dispose();held.sprite=null;held.overlay=null;held.spec=null;
        }
        if(!want){if(held.sprite!=null)held.sprite.visible(false);return;}
        if(held.sprite!=null&&held.spec.radius==spec.radius){
            if(held.spec!=spec&&held.sprite.visual instanceof Wall)
                ((Wall)held.sprite.visual).colors(spec);
            held.spec=spec;held.sprite.visible(true);return;
        }
        if(held.sprite!=null)clear(held);
        // One vertical band, without an extra ground disk.
        Sprite visual=spec.radius>20f?new Wall(gob,spec):new Disk(gob,spec,pick);
        RadiusVisual sprite=new RadiusVisual(gob,visual);
        Gob.Overlay overlay=new Gob.Overlay(gob,sprite);
        try{gob.addol(overlay,false);}
        catch(RuntimeException failure){sprite.dispose();throw failure;}
        held.overlay=overlay;held.sprite=sprite;held.spec=spec;
    }

    /** Keep buffers alive while hidden; toggling only attaches/detaches the visual. */
    private static final class RadiusVisual extends Sprite {
        final Sprite visual;boolean visible=true;
        final Map<RenderTree.Slot,RenderTree.Slot> slots=new LinkedHashMap<>();
        RadiusVisual(Owner owner,Sprite visual){super(owner,null);this.visual=visual;}
        public void added(RenderTree.Slot slot){slots.put(slot,visible?slot.add(visual):null);}
        public void removed(RenderTree.Slot slot){slots.remove(slot);}
        void visible(boolean value){
            if(visible==value)return;
            if(value){
                List<RenderTree.Slot> parents=new ArrayList<>(slots.keySet());
                Collection<RenderTree.Slot> children=RUtils.multiadd(parents,visual);
                Iterator<RenderTree.Slot> child=children.iterator();
                for(RenderTree.Slot parent:parents)slots.put(parent,child.next());
            }else{
                List<RenderTree.Slot> children=new ArrayList<>();for(RenderTree.Slot child:slots.values())if(child!=null)children.add(child);
                RUtils.multirem(children);for(RenderTree.Slot parent:new ArrayList<>(slots.keySet()))slots.put(parent,null);
            }
            visible=value;
        }
        public void gtick(Render out){if(visible)visual.gtick(out);}
        public void dispose(){visual.dispose();}
    }

    private void clear(Held held) {
        RadiusVisual sprite=held.sprite;
        if(sprite==null)return;
        if(gob.ols.contains(held.overlay))held.overlay.remove(false);
        held.overlay=null;held.sprite=null;held.spec=null;
        gob.defer(sprite::dispose);
    }

    private void alarm(String name) {
        if(!animalSounds||!knownCreature||alarmPlayed||gob.removed||gob.slots.isEmpty()) return;
        Alarm alarm = warning(name);
        if(alarm == null || !warned(name)) return;
        UI ui = screen;
        if(ui == null||ui.sess==null||ui.sess.glob!=gob.glob) return;
        boolean custom=voice(name)==Voice.FILE;
        Path file=custom?customFile(name):null;
        String sound=custom?null:alarm.sound(callsFor(name));
        if(custom?file==null:sound==null) {
            if(!custom&&missing.add(alarm.file))
                System.err.println("Hearthborn: missing animal alarm " + alarm.file);
            return;
        }
        alarmPlayed=true;
        long generation=alarmGeneration;
        double volume = alarm.volume / 50.0;
        gob.glob.loader.defer(() -> {
            try {
                try(InputStream in=custom?Files.newInputStream(file):GobMarks.class.getResourceAsStream(sound)) {
                    if(in==null)throw new FileNotFoundException(custom?file.toString():sound);
                    Audio.CS clip=Audio.PCMClip.fromwav(new ByteArrayInputStream(in.readAllBytes()));
                    synchronized(gob){
                        if(generation!=alarmGeneration||gob.removed||gob.slots.isEmpty()||dead
                            ||!animalSounds||!warned(name)||screen!=ui||ui.sess==null||ui.sess.glob!=gob.glob)return;
                        ui.warning(new Audio.VolAdjust(clip,volume));
                    }
                }
            } catch(IOException e) {
                synchronized(gob){if(generation==alarmGeneration)alarmPlayed=false;}
                ui.error(L10n.tr("Could not play the animal warning."));
            }
        }, null);
    }

    /** Null until a composite has a readable pose. */
    private Boolean fallen() {
        Drawable drawable = gob.getattr(Drawable.class);
        if(!(drawable instanceof Composite)) return false;
        Collection<ResData> poses=((Composite)drawable).poseResources();
        if(poses.isEmpty())return null;
        boolean waiting=false;
        for(ResData pose:poses){
            try{
                String path=pose.res.get().name;
                String name=path.substring(path.lastIndexOf('/')+1);
                if(name.equals("dead")||name.equals("waterdead")||name.equals("knock")
                    ||name.endsWith("-dead")||name.endsWith("-waterdead")||name.endsWith("-knock"))return true;
            }catch(Loading loading){waiting=true;}
        }
        return waiting?null:false;
    }

    private static Spec place(String name) {
        if(name.equals("gfx/terobjs/beehive")) return BEEHIVE;
        if(name.equals("gfx/terobjs/trough")) return TROUGH;
        if(name.equals("gfx/terobjs/minesupport")||name.equals("gfx/terobjs/ladder")) return SUPPORT;
        if(name.equals("gfx/terobjs/column")) return COLUMN;
        if(name.equals("gfx/terobjs/minebeam")) return BEAM;
        return null;
    }

    private static boolean aggressive(String name) {return name.contains("/bat")?!batCape():aggressiveSpecies(name);}
    private static boolean aggressiveSpecies(String name) {
        for(String part : PARTS) {
            if(name.contains(part)) return false;
        }
        for(String fragment : AGGRESSIVE) {
            if(name.endsWith(fragment) || name.contains(fragment + "/")) return true;
        }
        return false;
    }
    /** Aggro circle drawn around dangerous animals; scripts use the same distance to steer clear. */
    public static double dangerRadius(){return DANGER_MARK.radius;}
    /** True for living aggressive animals (boar, bear, orca, …), not horses or corpses. */
    public static boolean livingThreat(Gob gob){
        if(gob==null||gob.id==player)return false;
        String name;
        try{
            Drawable drawable=gob.getattr(Drawable.class);
            Resource res=drawable==null?null:drawable.getres();
            name=res==null?null:res.name;
        }catch(Loading pending){return false;}
        if(name==null||!aggressive(name))return false;
        GobMarks marks=gob.getattr(GobMarks.class);
        if(marks!=null)return !marks.dead;
        Boolean down=fallen(gob);
        return down==null||!down;
    }
    private static Boolean fallen(Gob gob){
        Drawable drawable=gob.getattr(Drawable.class);
        if(!(drawable instanceof Composite))return false;
        Collection<ResData> poses=((Composite)drawable).poseResources();
        if(poses.isEmpty())return null;
        boolean waiting=false;
        for(ResData pose:poses){
            try{
                String path=pose.res.get().name;
                String leaf=path.substring(path.lastIndexOf('/')+1);
                if(leaf.equals("dead")||leaf.equals("waterdead")||leaf.equals("knock")
                    ||leaf.endsWith("-dead")||leaf.endsWith("-waterdead")||leaf.endsWith("-knock"))return true;
            }catch(Loading loading){waiting=true;}
        }
        return waiting?null:false;
    }

    /** The player's equipment window, read again each tick so putting the cape on or taking it off updates at once. */
    private static boolean batCape() {
        GameUI gui = game;
        Equipory equipment = gui == null ? null : gui.equipment();
        return equipment != null && equipment.hasBatCape();
    }

    private static void refresh() {
        GameUI gui = game;
        if(gui == null || gui.ui == null || gui.ui.sess == null) return;
        OCache objects = gui.ui.sess.glob.oc;
        List<Gob> gobs = new ArrayList<>();
        synchronized(objects) {
            for(Gob gob : objects)
                gobs.add(gob);
        }
        for(Gob gob : gobs) {
            synchronized(gob) {
                GobMarks marks = gob.getattr(GobMarks.class);
                if(marks != null)
                    marks.ctick(0);
            }
        }
    }

    private static boolean arrow(String name) {
        if(name == null || !name.startsWith("gfx/terobjs/items/")) return false;
        String leaf = name.substring(name.lastIndexOf('/') + 1);
        return leaf.equals("arrow") || leaf.startsWith("arrow-");
    }

    private static boolean critter(String name) {
        for(String fragment : CRITTER_NAMES) {
            if(name.endsWith(fragment) || name.contains(fragment + "/")) return true;
        }
        return false;
    }

    /** The icon of a dead animal is its own setting. Corpses never warn, so its sound is the ordinary icon sound, apart from the living animal. */
    public static boolean corpse(Object[] id){
        if(id!=null)for(Object part:id)if("dead".equals(part))return true;
        return false;
    }
    public static boolean hasDedicatedIconSound(String icon){
        if(icon==null||!icon.startsWith("gfx/kritter/"))return false;
        int end=icon.indexOf('/',"gfx/kritter/".length());
        if(end<0)return false;
        String family=icon.substring(0,end+1);
        for(String animal:ALARMS.keySet())if(animal.startsWith(family))return true;
        return false;
    }

    private static Map<String, Alarm> alarms() {
        Map<String, Alarm> alarms = new HashMap<String, Alarm>();
        alarms.put("gfx/kritter/adder/adder", new Alarm("Snake", 35));
        alarms.put("gfx/kritter/badger/badger", new Alarm("Badger", 50));
        alarms.put("gfx/kritter/bear/bear", new Alarm("Bear", 50));
        alarms.put("gfx/kritter/bear/polarbear", new Alarm("Bear", 50));
        alarms.put("gfx/kritter/boar/boar", new Alarm("Boar", 50));
        alarms.put("gfx/kritter/boreworm/boreworm", new Alarm("Boreworm", 50));
        alarms.put("gfx/kritter/spermwhale/spermwhale", new Alarm("Cachalot", 50));
        alarms.put("gfx/kritter/caveangler/caveangler", new Alarm("CaveAngler", 50));
        alarms.put("gfx/kritter/cavelouse/cavelouse", new Alarm("CaveLouse", 50));
        alarms.put("gfx/kritter/rat/caverat", new Alarm("CaveRat", 50));
        alarms.put("gfx/kritter/eagleowl/eagleowl", new Alarm("EagleOwl", 50));
        alarms.put("gfx/kritter/greyseal/greyseal", new Alarm("GreySeal", 50));
        alarms.put("gfx/kritter/goldeneagle/goldeneagle", new Alarm("Eagle", 50));
        alarms.put("gfx/kritter/lynx/lynx", new Alarm("Lynx", 50));
        alarms.put("gfx/kritter/mammoth/mammoth", new Alarm("Mammoth", 50));
        alarms.put("gfx/kritter/moose/moose", new Alarm("Moose", 50));
        alarms.put("gfx/kritter/narwhal/narwhal", new Alarm("Narwhal", 10));
        alarms.put("gfx/kritter/nidbane/nidbane", new Alarm("Nidbane", 60));
        alarms.put("gfx/kritter/orca/orca", new Alarm("Orca", 20));
        alarms.put("gfx/kritter/troll/troll", new Alarm("Troll", 50));
        alarms.put("gfx/kritter/walrus/walrus", new Alarm("Walrus", 20));
        alarms.put("gfx/kritter/wolf/wolf", new Alarm("Wolf", 25));
        alarms.put("gfx/kritter/wolverine/wolverine", new Alarm("Wolverine", 50));
        alarms.put("gfx/kritter/bat/bat", new Alarm("Bat", 40));
        return alarms;
    }

    private static final class Held {
        Gob.Overlay overlay;RadiusVisual sprite;Spec spec;
    }

    private static final class Spec {
        final float radius;
        final Color fill;
        final Color edge;
        Spec(float radius, Color fill, Color edge) {
            this.radius = radius;
            this.fill = fill;
            this.edge = edge;
        }
    }

    private static final class Alarm {
        final String file;
        final int volume;
        Alarm(String file, int volume) {
            this.file = file;
            this.volume = volume;
        }
        String sound(boolean calls) {
            if(calls) {
                String own=hearthborn.assets.Assets.path("audio/calls/"+file+".wav");
                if(GobMarks.class.getResource(own)!=null)return own;
                String borrowed=hearthborn.assets.Assets.path("audio/calls/ND_"+file+".wav");
                if(GobMarks.class.getResource(borrowed)!=null)return borrowed;
                return hearthborn.assets.Assets.path("audio/calls/ND_Ambush.wav");
            }
            String mode="speech/"+("ru".equals(L10n.language())?"ru":"en");
            String path=hearthborn.assets.Assets.path("audio/"+mode+"/"+file+".wav");
            if(GobMarks.class.getResource(path)!=null)return path;
            return null;
        }
    }

    private static final class IgnoreClicks implements Pipe.Op {
        public void apply(Pipe pipe) { pipe.put(Clickable.slot, null); }
    }

    /** Animals use a late render pass and two rings at terrain +/- 10;
     * object radii additionally retain the existing ground disk. */
    private static final class Wall extends Sprite {
        final VertexBuf.VertexData posa;
        final VertexBuf vbuf;
        final Model solid;
        final Disk ground;
        final Model edge;
        final Model lowerEdge;
        final int rings;
        final Pipe.Op state;
        Pipe.Op fillState;
        Pipe.Op edgeState;
        final Map<RenderTree.Slot,RenderTree.Slot[]> colorSlots=new LinkedHashMap<>();
        Coord2d placed;
        int terrainVersion=-1;

        Wall(Owner owner, Spec spec) {
            super(owner, null);
            boolean animal=spec==DANGER_MARK;
            rings=animal?2:3;
            int n = Math.max(24, (int)(2 * Math.PI * spec.radius / 11.0));
            FloatBuffer pos = Utils.wfbuf(n * 3 * rings);
            FloatBuffer normals=Utils.wfbuf(n*3*rings);
            for(int i = 0; i < n; i++) {
                float s = (float)Math.sin(2 * Math.PI * i / n);
                float c = (float)Math.cos(2 * Math.PI * i / n);
                pos.put(i * 3, c * spec.radius).put(i * 3 + 1, s * spec.radius).put(i * 3 + 2, 10);
                pos.put((n + i) * 3, c * spec.radius).put((n + i) * 3 + 1, s * spec.radius).put((n + i) * 3 + 2, -10);
                if(rings==3)pos.put((2*n+i)*3,c*spec.radius).put((2*n+i)*3+1,s*spec.radius).put((2*n+i)*3+2,LIFT);
                for(int ring=0;ring<rings;ring++)normals.put((ring*n+i)*3,c).put((ring*n+i)*3+1,s).put((ring*n+i)*3+2,0);
            }
            boolean support=spec==SUPPORT||spec==COLUMN||spec==BEAM
                ||spec==COVERED[0]||spec==COVERED[1]||spec==COVERED[2];
            ground=animal?null:new Disk(owner,spec,false,support);
            posa = new VertexBuf.VertexData(pos);
            vbuf = new VertexBuf(posa,new VertexBuf.NormalData(normals));
            solid = new Model(Model.Mode.TRIANGLES, vbuf.data(), new Model.Indices(n * 6, NumberFormat.UINT16, DataBuffer.Usage.STATIC, (dst, env) -> {
                FillBuffer ret = env.fillbuf(dst);
                ShortBuffer buf = ret.push().asShortBuffer();
                for(int i = 0; i < n; i++) {
                    int b = i * 6;
                    buf.put(b, (short)i).put(b + 1, (short)(i + n)).put(b + 2, (short)((i + 1) % n));
                    buf.put(b + 3, (short)(i + n)).put(b + 4, (short)(((i + 1) % n) + n)).put(b + 5, (short)((i + 1) % n));
                }
                return ret;
            }));
            edge = new Model(Model.Mode.LINE_STRIP, vbuf.data(), new Model.Indices(n + 1, NumberFormat.UINT16, DataBuffer.Usage.STATIC, (dst, env) -> {
                FillBuffer ret = env.fillbuf(dst);
                ShortBuffer buf = ret.push().asShortBuffer();
                for(int i = 0; i < n; i++)
                    buf.put(i, (short)i);
                buf.put(n, (short)0);
                return ret;
            }));
            lowerEdge=support?new Model(Model.Mode.LINE_STRIP,vbuf.data(),new Model.Indices(n+1,NumberFormat.UINT16,DataBuffer.Usage.STATIC,(dst,env)->{
                FillBuffer ret=env.fillbuf(dst);ShortBuffer indices=ret.push().asShortBuffer();
                for(int i=0;i<n;i++)indices.put(i,(short)(2*n+i));indices.put(n,(short)(2*n));return ret;
            })):null;
            state = Pipe.Op.compose(new States.Facecull(States.Facecull.Mode.NONE), animal?null:States.maskdepth,
                animal?Rendered.postpfx:MapMesh.postmap, Location.goback("gobx"), UNCLICK);
            fillState = new BaseColor(spec.fill);
            edgeState = Pipe.Op.compose(new BaseColor(spec.edge == null ? spec.fill : spec.edge), new States.LineWidth(4));
        }

        public void added(RenderTree.Slot slot) {
            slot.ostate(state);
            RenderTree.Slot bottom=null;
            if(ground!=null)slot.add(ground);
            else if(lowerEdge!=null)bottom=slot.add(lowerEdge,edgeState);
            RenderTree.Slot fill=slot.add(solid,fillState);
            RenderTree.Slot top=slot.add(edge,edgeState);
            colorSlots.put(slot,new RenderTree.Slot[]{fill,top,bottom});
        }

        public void removed(RenderTree.Slot slot){colorSlots.remove(slot);}
        void colors(Spec spec){
            if(ground!=null)ground.colors(spec);
            fillState=new BaseColor(spec.fill);
            edgeState=Pipe.Op.compose(new BaseColor(spec.edge==null?spec.fill:spec.edge),new States.LineWidth(4));
            for(RenderTree.Slot[] children:colorSlots.values()){
                children[0].ostate(fillState);children[1].ostate(edgeState);
                if(children[2]!=null)children[2].ostate(edgeState);
            }
        }
        public void gtick(Render g) {
            Gob owner = this.owner.context(Gob.class);
            Coord2d at = owner.rc;
            int version=owner.glob.map.chseq;
            if(placed != null && placed.equals(at)&&terrainVersion==version)
                return;
            int n = posa.size() / rings;
            float[] heights=new float[n];
            try {
                float base = (float)owner.glob.map.getcz(at.x, at.y);
                for(int i = 0; i < n; i++)
                    heights[i]=(float)(owner.glob.map.getcz(at.x+posa.data.get(i*3),at.y-posa.data.get(i*3+1))-base);
            } catch(Loading e) {
                return; // Keep the complete previous geometry until every sample is available.
            }
            for(int i=0;i<n;i++){
                posa.data.put(i*3+2,heights[i]+10);
                posa.data.put((n+i)*3+2,heights[i]-10);
                if(rings==3)posa.data.put((2*n+i)*3+2,heights[i]+LIFT);
            }
            vbuf.update(g);
            placed=at;terrainVersion=version;
        }

        public void dispose() {
            if(ground!=null)ground.dispose();
            solid.va.shared = true;
            solid.dispose();
            edge.dispose();
            if(lowerEdge!=null)lowerEdge.dispose();
            solid.va.dispose();
        }
    }

    private static final class Disk extends Sprite {
        final FastMesh fill;
        final FastMesh edge;
        final Pipe.Op state;
        BaseColor fillColor;
        final BaseColor edgeColor;
        final Map<RenderTree.Slot,RenderTree.Slot> fills=new LinkedHashMap<>();
        boolean thin;

        Disk(Owner owner, Spec spec, boolean pick) {this(owner,spec,pick,false);}
        Disk(Owner owner, Spec spec, boolean pick, boolean thin) {
            super(owner, null);this.thin=thin;
            boolean ring = spec.radius > 20f;
            this.fill = ring ? band(spec.radius - (thin?0.75f:10f), spec.radius) : band(0f, spec.radius);
            this.edge = thin || spec.edge == null ? null : band(spec.radius - 2.5f, spec.radius);
            this.fillColor = new BaseColor(thin&&spec.edge!=null?spec.edge:spec.fill);
            this.edgeColor = spec.edge == null ? null : new BaseColor(spec.edge);
            this.state = Pipe.Op.compose(new States.Facecull(States.Facecull.Mode.NONE), States.maskdepth,
                MapMesh.postmap, pick ? null : UNCLICK);
        }

        public void added(RenderTree.Slot slot) {
            fills.put(slot,slot.add(fill, Pipe.Op.compose(state, fillColor)));
            if(edge != null) slot.add(edge, Pipe.Op.compose(state, edgeColor));
        }

        public void removed(RenderTree.Slot slot){fills.remove(slot);}
        void colors(Spec spec){
            fillColor=new BaseColor(thin&&spec.edge!=null?spec.edge:spec.fill);
            for(RenderTree.Slot slot:fills.values())slot.ostate(Pipe.Op.compose(state,fillColor));
        }
        public void dispose() {
            fill.dispose();
            if(edge != null) edge.dispose();
        }
    }

    private static FastMesh band(float inner, float outer) {
        if(inner <= 0.5f) return disk(outer);
        FloatBuffer pos = Utils.mkfbuf(SEGMENTS * 2 * 3);
        for(int ring = 0; ring < 2; ring++) {
            float radius = ring == 0 ? outer : inner;
            for(int i = 0; i < SEGMENTS; i++) {
                double angle = i * Math.PI * 2.0 / SEGMENTS;
                pos.put((float)(Math.cos(angle) * radius));
                pos.put((float)(Math.sin(angle) * radius));
                pos.put(LIFT);
            }
        }
        pos.rewind();
        ShortBuffer ind = Utils.mksbuf(SEGMENTS * 6);
        for(int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            ind.put((short)i);
            ind.put((short)next);
            ind.put((short)(SEGMENTS + next));
            ind.put((short)i);
            ind.put((short)(SEGMENTS + next));
            ind.put((short)(SEGMENTS + i));
        }
        ind.rewind();
        return new FastMesh(new VertexBuf(new VertexBuf.VertexData(pos)), ind);
    }

    private static FastMesh disk(float radius) {
        FloatBuffer pos = Utils.mkfbuf((SEGMENTS + 1) * 3);
        pos.put(0f).put(0f).put(LIFT);
        for(int i = 0; i < SEGMENTS; i++) {
            double angle = i * Math.PI * 2.0 / SEGMENTS;
            pos.put((float)(Math.cos(angle) * radius));
            pos.put((float)(Math.sin(angle) * radius));
            pos.put(LIFT);
        }
        pos.rewind();
        ShortBuffer ind = Utils.mksbuf(SEGMENTS * 3);
        for(int i = 0; i < SEGMENTS; i++) {
            ind.put((short)0);
            ind.put((short)(1 + i));
            ind.put((short)(1 + ((i + 1) % SEGMENTS)));
        }
        ind.rewind();
        return new FastMesh(new VertexBuf(new VertexBuf.VertexData(pos)), ind);
    }
}
