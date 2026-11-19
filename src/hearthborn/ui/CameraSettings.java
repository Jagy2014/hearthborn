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
import java.util.Arrays;
import java.util.List;

/**
 * @author Jagy2014
 */
public final class CameraSettings {
    /** Vanilla ortho zoom-out: half the view is at most {@code width * sqrt(2) / 8}. */
    public static float maxField(int viewWidth) {
        return viewWidth * (float)Math.sqrt(2) / 8f;
    }

    public static float field(float value, int viewWidth) {
        return Math.max(Math.min(value, maxField(viewWidth)), 50f);
    }

    /** Same on-screen extent as {@link #field}: the perspective frustum is one world unit wide per unit of distance. */
    public static float distance(float value, int viewWidth) {
        return Math.max(Math.min(value, maxField(viewWidth) * 2f), 5f);
    }
    /** The free and simple cameras may pull back this far (the far plane of the view reaches {@link #FAR}). */
    public static final float MAX_PERSPECTIVE=3000f;
    public static final float FAR=8000f;
    public static float perspective(float value) {
        return Math.max(Math.min(value, MAX_PERSPECTIVE), 5f);
    }
    /** How far the free and simple cameras may tilt below the horizon, so that they can look up at the sky. */
    public static final float LOW_TILT=-0.5f;
    public static boolean lowTilt(){return Utils.getprefb("hearthborn/camera/low-tilt",false);}
    public static void lowTilt(boolean on){Utils.setprefb("hearthborn/camera/low-tilt",on);}
    public static float minElevation(){return lowTilt()?LOW_TILT:0f;}
    public static final List<String> MODES=Arrays.asList("ortho","ortho-free","follow","bad","worse");
    public static boolean inverted(boolean vertical) {return Utils.getprefb("hearthborn/camera/invert-"+(vertical ? "y" : "x"),false);}
    public static void inverted(boolean vertical,boolean value) {Utils.setprefb("hearthborn/camera/invert-"+(vertical ? "y" : "x"),value);}
    public static Coord drag(Coord position,Coord origin) {
        if(origin==null)return position;
        return Coord.of(inverted(false) ? 2*origin.x-position.x : position.x,
            inverted(true) ? 2*origin.y-position.y : position.y);
    }
    public static void select(UI ui,String mode) {
        if(!MODES.contains(mode))throw new IllegalArgumentException("Unknown camera: "+mode);
        GameUI gui=ui==null ? null : ui.root.getchild(GameUI.class);
        if(gui!=null&&gui.map!=null) {
            MapView map=gui.map;
            switch(mode) {
                case "ortho-free":map.camera=map.new SOrthoCam("-f");break;
                case "follow":map.camera=map.new FollowCam();break;
                case "bad":map.camera=map.new FreeCam();break;
                case "worse":map.camera=map.new SimpleCam();break;
                default:map.camera=map.new SOrthoCam();break;
            }
        }
        Utils.setpref("defcam",mode.equals("ortho-free") ? "ortho" : mode);
        Utils.setprefb("camargs",Utils.serialize(mode.equals("ortho-free") ? new String[]{"-f"} : new String[0]));
    }
    /** Whether a camera can look up at the sky: every one that is not orthographic. */
    public static boolean lookingUp(String mode){return "bad".equals(mode)||"worse".equals(mode)||"follow".equals(mode);}
    private static void showSkyOptions(Widget[] widgets,String mode){
        for(Widget w:widgets)if(w!=null)w.show(lookingUp(mode));
    }
    public static OptWnd.Panel panel(OptWnd options,OptWnd.Panel back) {
        OptWnd.Panel panel=options.new Panel().resettable("hearthborn/camera/","hearthborn/wheel/","defcam","camargs");
        panel.add(new Label("Camera:"),UI.scale(0,4));
        final Widget[] skyOnly=new Widget[2];
        SDropBox<String,Widget> modes=new SDropBox<String,Widget>(UI.scale(240),UI.scale(80),UI.scale(16)) {
            private boolean initial=true;
            protected boolean listautoscroll(){return false;}
            protected List<? extends String> items(){return MODES;}
            protected Widget makeitem(String mode,int index,Coord size) {
                String[] names={"Orthographic","Orthographic free","Following","Free","Simple"};
                Widget row=new Widget(size);Label label=new Label(names[MODES.indexOf(mode)]);row.add(label,Coord.of(UI.scale(3),(size.y-label.sz.y)/2));return row;
            }
            public void change(String mode) {super.change(mode);if(!initial&&mode!=null)select(ui,mode);initial=false;showSkyOptions(skyOnly,mode);}
        };
        String selected=Utils.getpref("defcam","ortho");
        Object args=Utils.deserialize(Utils.getprefb("camargs",null));
        if(selected.equals("ortho") && args instanceof String[] && Arrays.asList((String[])args).contains("-f"))selected="ortho-free";
        modes.change(MODES.contains(selected) ? selected : "ortho");
        panel.add(modes,UI.scale(100,0));
        Widget x=panel.add(new CheckBox("Invert horizontal camera rotation") {{a=inverted(false);}}.changed(v -> inverted(false,v)),UI.scale(0,42));
        Widget y=panel.add(new CheckBox("Invert vertical camera rotation") {{a=inverted(true);}}.changed(v -> inverted(true,v)),x.pos("bl").adds(0,8));
        Widget low=panel.add(new CheckBox("Tilt the camera below the horizon") {{a=lowTilt();}}.changed(CameraSettings::lowTilt),y.pos("bl").adds(0,8));
        low.settip("Free, Simple and Following cameras. Lets the camera tilt below the horizon, to look at the sky.");
        Widget sky=panel.add(new Widget(Coord.of(UI.scale(340),UI.scale(20))),low.pos("bl").adds(0,8));
        Label skyLabel=sky.add(new Label("Sky:"),UI.scale(0,2));
        SDropBox<String,Widget> skies=new SDropBox<String,Widget>(UI.scale(200),UI.scale(60),UI.scale(16)) {
            protected boolean listautoscroll(){return false;}
            protected List<? extends String> items(){return hearthborn.world.Sky.KINDS;}
            protected Widget makeitem(String kind,int index,Coord size) {
                String[] names={"Day and night","Starry lake","Off"};
                Widget row=new Widget(size);Label label=new Label(names[hearthborn.world.Sky.KINDS.indexOf(kind)]);row.add(label,Coord.of(UI.scale(3),(size.y-label.sz.y)/2));return row;
            }
            public void change(String kind) {super.change(kind);if(kind!=null)hearthborn.world.Sky.kind(kind);}
        };
        skies.change(hearthborn.world.Sky.kind());
        sky.add(skies,Coord.of(skyLabel.sz.x+UI.scale(10),0));
        sky.settip("Orthographic cameras show no sky. Day and night drawn by the graphics card, with the sun and the moon where the game's light comes from, drifting clouds and stars; or a starry lake with a far forest. Seen when the camera looks to the horizon or the view is pulled far out.");
        skyOnly[0]=low;skyOnly[1]=sky;showSkyOptions(skyOnly,modes.sel);
        int bottom=WheelSettings.panel(panel,sky.pos("bl").adds(0,14).y);
        panel.add(options.new PButton(UI.scale(200),"Back",27,back),Coord.of(0,bottom+UI.scale(16)));
        panel.pack();return panel;
    }
    private CameraSettings() {}
}
