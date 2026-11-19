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
import hearthborn.assets.Assets;
import hearthborn.util.GameObjects;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * One spoken warning per encounter with another loaded player character.
 *
 * @author Jagy2014
 */
public final class PlayerWarnings extends GAttrib {
    private static final String PREFERENCE="hearthborn/world/player-sounds";
    private static volatile boolean enabled=Utils.getprefb(PREFERENCE,true);
    private static volatile GameUI game;
    private static double nextSound;
    private UI screen;
    private boolean announced;
    private long generation;
    private double visibleSince=Double.NaN;

    public PlayerWarnings(Gob gob){super(gob);}

    public static synchronized void attach(GameUI gui){
        if(game!=gui)nextSound=0;
        game=gui;
    }
    public static boolean enabled(){return enabled;}
    public static void enabled(boolean value){enabled=value;Utils.setprefb(PREFERENCE,value);}

    public static boolean ownsIconSound(Gob gob){
        if(!enabled||gob==null)return false;
        try{return "gfx/borka/body".equals(GameObjects.resourceName(gob));}
        catch(Loading pending){return false;}
    }

    public void lostSight(){announced=false;generation++;visibleSince=Double.NaN;}
    public static void lostSight(Gob gob){
        PlayerWarnings warning=gob.getattr(PlayerWarnings.class);
        if(warning!=null)warning.lostSight();
    }

    public enum Voice {
        RU("ru","Russian"),EN("en","English"),FILE("file","Custom sound");
        public final String id,title;
        Voice(String id,String title){this.id=id;this.title=title;}
    }
    static String sound(PlayerRelations.Kind kind){return speech(kind,L10n.language());}
    public static String speech(PlayerRelations.Kind kind,String language){
        return Assets.path("audio/speech/"+("ru".equals(language)?"ru":"en")+"/"+kind.speech+".wav");
    }

    /** The spoken warning stays on until this kind is unchecked in icon settings. */
    public static boolean heard(PlayerRelations.Kind kind){return Utils.getprefb(heardKey(kind),true);}
    public static void heard(PlayerRelations.Kind kind,boolean on){Utils.setprefb(heardKey(kind),on);}
    /** Empty follows the client language. {@code ru} and {@code en} stay fixed. {@code file} plays the chosen WAV. */
    public static String clip(PlayerRelations.Kind kind){String value=Utils.getpref(clipKey(kind),"");return value==null?"":value;}
    public static void clip(PlayerRelations.Kind kind,String value){Utils.setpref(clipKey(kind),value==null?"":value);}
    public static Voice voice(PlayerRelations.Kind kind){
        String value=clip(kind);
        if("file".equals(value)||value.startsWith("wav:"))return Voice.FILE;
        if("ru".equals(value))return Voice.RU;
        if("en".equals(value))return Voice.EN;
        return "ru".equals(L10n.language())?Voice.RU:Voice.EN;
    }
    public static void voice(PlayerRelations.Kind kind,Voice value){if(kind!=null&&value!=null)clip(kind,value.id);}
    public static Path customFile(PlayerRelations.Kind kind){
        String value=Utils.getpref(fileKey(kind),"");
        if(value==null||value.isEmpty()){String legacy=clip(kind);if(legacy.startsWith("wav:"))value=legacy.substring(4);}
        if(value==null||value.isEmpty())return null;
        try{return Utils.path(value);}catch(RuntimeException failure){return null;}
    }
    public static void customFile(PlayerRelations.Kind kind,Path path){
        Utils.setpref(fileKey(kind),path==null?"":path.toString());
        clip(kind,"file");
    }
    public static String customName(PlayerRelations.Kind kind){Path path=customFile(kind);return path==null?null:path.getFileName().toString();}
    public static String choiceName(PlayerRelations.Kind kind){
        if(voice(kind)==Voice.FILE){String name=customName(kind);return name==null?"Custom sound":name;}
        return voice(kind).title;
    }
    public static void preview(UI ui,PlayerRelations.Kind kind){if(ui!=null)play(ui,kind,false);}
    private static String heardKey(PlayerRelations.Kind kind){return "hearthborn/world/player-heard/"+kind.name();}
    private static String clipKey(PlayerRelations.Kind kind){return "hearthborn/world/player-clip/"+kind.name();}
    private static String fileKey(PlayerRelations.Kind kind){return "hearthborn/world/player-file/"+kind.name();}

    private boolean current(GameUI gui){
        return enabled&&game==gui&&gui.ui!=null&&gui.ui==screen&&gui.ui.sess!=null
            &&gui.ui.sess.glob==gob.glob&&gui.plid>=0&&gob.id!=gui.plid
            &&!gob.virtual&&!gob.removed&&!gob.slots.isEmpty();
    }

    public void ctick(double dt){
        GameUI gui=game;
        UI currentScreen=gui==null?null:gui.ui;
        if(screen!=currentScreen){lostSight();screen=currentScreen;}
        if(gui==null||!current(gui)){if(announced)lostSight();return;}
        try{
            if(!PlayerRelations.player(gob)||PlayerRelations.fixture(gob)){if(announced)lostSight();return;}
        }catch(Loading pending){return;}
        if(announced)return;
        double seenNow=Utils.rtime();
        if(Double.isNaN(visibleSince)){visibleSince=seenNow;return;}
        PlayerRelations.Info info=PlayerRelations.info(gui,gob);
        if(seenNow-visibleSince<.35||info.pending||!heard(info.kind))return;
        announced=true;
        long encounter=generation;
        UI ui=screen;
        gob.glob.loader.defer(()->{
            synchronized(gob){
                if(encounter!=generation||!current(gui))return;
                synchronized(PlayerWarnings.class){
                    double now=Utils.rtime();
                    if(now<nextSound)return;
                    nextSound=now+1.2;
                    play(ui,info.kind,true);
                }
            }
        },null);
    }

    private static void play(UI ui,PlayerRelations.Kind kind,boolean warning){
        Voice chosen=voice(kind);
        try{
            Audio.CS clip;
            if(chosen==Voice.FILE){
                Path path=customFile(kind);
                if(path==null)throw new FileNotFoundException(kind.name());
                try(InputStream input=Files.newInputStream(path)){clip=Audio.PCMClip.fromwav(new ByteArrayInputStream(input.readAllBytes()));}
            }else{
                String path=speech(kind,chosen.id);
                try(InputStream input=PlayerWarnings.class.getResourceAsStream(path)){
                    if(input==null)throw new FileNotFoundException(path);
                    clip=Audio.PCMClip.fromwav(new ByteArrayInputStream(input.readAllBytes()));
                }
            }
            if(warning)ui.warning(clip);else ui.sfx(clip);
        }catch(IOException|RuntimeException failure){
            ui.error(L10n.tr("Could not play the other player warning."));
        }
    }
}
