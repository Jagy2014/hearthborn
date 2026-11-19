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
import hearthborn.storage.ContainerContents;

import haven.*;
import haven.render.*;
import java.awt.Color;
import java.util.*;

/**
 * Growth readouts use the server stage and scale bytes, without changing object state.
 *
 * @author Jagy2014
 */
public final class PlantGrowth extends GAttrib implements RenderTree.Node, PView.Render2D {
    private static final String PREF = "hearthborn/plants/";
    private static final Map<Resource, Integer> maximum = new WeakHashMap<>();
    private static final Map<String, Tex> labels = new LinkedHashMap<>();
    private static final Color[] COLORS = {Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN};
    private static final Color READY = new Color(72, 220, 96);
    private static final Color UNREADY = new Color(255, 72, 64);

    private static final Map<String,Boolean> enabledTypes=new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean showBig=Utils.getprefb(PREF+"big-trees",true);
    private static volatile int bigThreshold=Utils.getprefi(PREF+"big-threshold",150);
    public static boolean enabled(String type) { return enabledTypes.computeIfAbsent(type,key->Utils.getprefb(PREF+key,true)); }
    public static void enabled(String type, boolean on) { enabledTypes.put(type,on); Utils.setprefb(PREF + type, on); }
    public static boolean big() { return showBig; }
    public static void big(boolean on) { showBig=on; Utils.setprefb(PREF + "big-trees", on); }
    public static int threshold() { return bigThreshold; }
    public static void threshold(int value) { bigThreshold=Utils.clip(value,101,200);Utils.setprefi(PREF+"big-threshold",bigThreshold); }

    static int percent(int scale, boolean bush) {
        int start = bush ? 30 : 10;
        return (int)((100.0 / (100 - start)) * (scale - start));
    }

    static boolean tree(Sprite sprite) {
        for(Class<?> type = sprite.getClass(); type != null; type = type.getSuperclass())
            if(type.getSimpleName().equals("Tree")) return true;
        return false;
    }

    private static synchronized int maximum(Resource resource) {
        Integer result = maximum.get(resource);
        if(result == null) {
            int value = 0;
            for(FastMesh.MeshRes mesh : resource.layers(FastMesh.MeshRes.class)) value = Math.max(value, mesh.id / 10);
            maximum.put(resource, result = value);
        }
        return result;
    }

    static String cropText(MessageBuf data,int max,boolean reveal){
        if(data.eom()||max<=0)return null;
        int stage=Math.min(data.uint8(),max);
        return stage==max||reveal?stage+"/"+max:null;
    }

    static String text(MessageBuf data, int max, boolean crop, boolean bush) {
        if(data.eom()) return null;
        int first = data.uint8();
        if(crop) return max > 0 ? Math.min(first, max) + "/" + max : null;
        if(data.eom()) return null;
        int growth = percent(data.uint8(), bush);
        return growth >= 0 && (growth < 100 || (big() && growth >= threshold())) ? growth + "%" : null;
    }

    /** Ripe crops are green. Anything still growing is red. */
    static Color stageInk(double progress) {
        return progress >= 1 ? READY : UNREADY;
    }

    static Color color(double progress) {
        double position = Utils.clip(progress, 0, 1) * (COLORS.length - 1);
        int index = Math.min((int)position, COLORS.length - 2);
        double weight = position - index;
        Color a = COLORS[index], b = COLORS[index + 1];
        return new Color((int)(a.getRed() * (1 - weight) + b.getRed() * weight),
            (int)(a.getGreen() * (1 - weight) + b.getGreen() * weight),
            (int)(a.getBlue() * (1 - weight) + b.getBlue() * weight));
    }

    private final DepthLabel label=new DepthLabel();
    private String shown;
    private boolean crop;
    private int max;
    private float labelHeight=1;
    private double elapsed;
    private boolean revealed;
    public PlantGrowth(Gob gob) { super(gob); elapsed=-((gob.id&15)*(0.15/16.0)); }

    public void ctick(double dt) {
        boolean reveal=crop&&ContainerContents.spaceHeld(gob.glob);
        if((elapsed += dt) < .15 && reveal==revealed) return;
        revealed=reveal;elapsed = 0;
        updateGrowth();
        label.set(crop ? texture() : null,labelHeight);
    }
    private void updateGrowth() {
        Drawable drawable = gob.getattr(Drawable.class);
        if(!(drawable instanceof ResDrawable)) { shown = null; return; }
        ResDrawable drawn = (ResDrawable)drawable;
        Resource resource = drawn.rres;
        crop = CropSprites.crop(resource);
        boolean bush = resource.name.startsWith("gfx/terobjs/bushes/");
        if(crop) {
            if(!enabled("crops")) { shown = null; return; }
        } else if((!resource.name.startsWith("gfx/terobjs/trees/") && !bush)
            || !enabled(bush ? "bushes" : "trees") || !tree(drawn.spr)) {
            shown = null; return;
        }
        try {
            max = crop ? maximum(resource) : 0;
            shown = crop?cropText(drawn.stateData(),max,ContainerContents.spaceHeld(gob.glob)):text(drawn.stateData(),max,false,bush);
            labelHeight=1;
        } catch(Loading ignored) {}
    }

    private TexI texture() {
        String value=shown;if(value==null)return null;
        synchronized(labels) {
            Tex tex=labels.get(value);
            if(tex==null) {
                String[] numbers=value.split(crop?"/":"%");
                double progress=Double.parseDouble(numbers[0])/(crop?max:100.0);
                java.awt.image.BufferedImage face=crop?WorldLabels.stageImage(value,stageInk(progress)):WorldLabels.growthImage(value,color(progress));
                tex=new TexI(face);labels.put(value,tex);
            }
            return (TexI)tex;
        }
    }
    public void draw(GOut g, Pipe state) {
        if(crop) return;
        Tex text = texture();
        if(text == null) return;
        Coord at = WorldLabels.anchor(state, g.sz(), 1);
        if(at != null) g.aimage(text, at, .5, .5);
    }
    public void added(RenderTree.Slot slot){slot.add(label);super.added(slot);}
    public void dispose(){label.dispose();}
}
