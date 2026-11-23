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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.*;

/**
 * Screen-facing text anchored through the object's own render transform.
 *
 * @author Jagy2014
 */
public final class WorldLabels {
    private static final Color BACKGROUND = new Color(0, 0, 0, 84);
    public static final Color CONTENTS = new Color(252, 235, 255);
    private static final Pattern AMOUNT = Pattern.compile("^(.+?)\\s+(\\(\\d[^()]*\\))$");

    public static Coord anchor(Pipe state, Coord size, float height) {
        return anchor(state, size, 0, 0, height);
    }

    public static Coord anchor(Pipe state, Coord size, float x, float y, float height) {
        HomoCoord4f clip = Homo3D.obj2clip(new Coord3f(x, y, height), state);
        if(clip.clipped()) return null;
        Coord point = clip.toview(Area.sized(size)).round2();
        return point.isect(Coord.z, size) ? point : null;
    }

    /** Size a screen-facing icon from one projected tile at its world anchor. */
    public static int iconExtent(Pipe state,Coord viewport,float height,double coverage){
        return iconExtent(Homo3D.prjxf(state).mul(Homo3D.camxf(state)).mul(Homo3D.locxf(state)),viewport,height,coverage);
    }
    /** Same size from a ready object-to-clip matrix, so callers that already have one skip walking the transform chain. */
    public static int iconExtent(Matrix4f transform,Coord viewport,float height,double coverage){
        HomoCoord4f center=transform.mul4(HomoCoord4f.of(new Coord3f(0,0,height)));
        HomoCoord4f x=transform.mul4(HomoCoord4f.of(new Coord3f(11,0,height)));
        HomoCoord4f y=transform.mul4(HomoCoord4f.of(new Coord3f(0,11,height)));
        if(center.clipped()||x.w<=0||y.w<=0)return 0;
        Area area=Area.sized(viewport);Coord3f at=center.toview(area),px=x.toview(area),py=y.toview(area);
        double span=Math.max(Math.hypot(px.x-at.x,px.y-at.y),Math.hypot(py.x-at.x,py.y-at.y));
        return Math.max(1,Math.min(UI.scale(40),(int)Math.round(span*coverage)));
    }

    public static float height(String resource) {
        if(resource.startsWith("gfx/terobjs/cistern")) return 28;
        if(resource.startsWith("gfx/terobjs/demijohn")) return 12;
        return 6;
    }

    public static BufferedImage image(String text, Color ink, boolean split) {
        return image(text, ink, split, 1);
    }

    /** How much the final picture magnifies interface units: the interface scale times the panel scale. */
    public static double density() {
        double value = hearthborn.ui.LiveScale.ratio();
        return Double.isFinite(value) && value > 0 ? value : 1;
    }

    /** A label rasterized at the density it will be shown at, so magnifying the interface does not blur it. */
    public static final class Label extends TexI {
        public final double density;
        /** A vessel caption is made at the interface ratio alone, not the panel scale. */
        final boolean vessel;
        Label(BufferedImage image, double density) { this(image, density, false); }
        Label(BufferedImage image, double density, boolean vessel) { super(image); this.density = density; this.vessel = vessel; }
    }

    public static Label label(String text, Color ink, boolean split) {
        double density = density();
        Label label = new Label(image(text, ink, split, density), density);
        label.filter(haven.render.Texture.Filter.LINEAR);
        return label;
    }

    /**
     * A caption on a world object: a hard four-way black stroke and a one-pixel blurred halo around the text, no plate. Barrels, ovens, houses, piles and cheese racks all use it. It is
     * rasterized at the live interface ratio and drawn 1:1 on screen, so it keeps its size whatever the panel scale is.
     * With {@code split}, a long caption breaks into lines, or into its name and a trailing amount.
     */
    public static Label caption(String text, Color ink, boolean split) {
        double ratio = hearthborn.ui.LiveScale.ratio();
        Text.Foundry foundry = vesselFoundry();
        BufferedImage whole = strokedHalo(foundry, text, ink, ratio);
        if(!split || whole.getWidth() <= UI.scale(60) * ratio)
            return new Label(whole, ratio, true);
        List<String> parts = new ArrayList<>();
        Matcher amount = AMOUNT.matcher(text);
        if(amount.matches()) {
            parts.add(amount.group(1));
            parts.add(amount.group(2));
        } else {
            for(String word : text.split(" ")) if(!word.isEmpty()) parts.add(word);
        }
        return stacked(foundry, parts, ink, ratio);
    }

    static Label captionLines(String first, String second, Color ink) {
        return stacked(vesselFoundry(), List.of(first, second), ink, hearthborn.ui.LiveScale.ratio());
    }

    /** Lines of one caption, each centered under the widest. */
    private static Label stacked(Text.Foundry foundry, List<String> parts, Color ink, double ratio) {
        List<BufferedImage> lines = new ArrayList<>();
        for(String part : parts) lines.add(strokedHalo(foundry, part, ink, ratio));
        int width = 0, height = 0;
        for(BufferedImage line : lines) { width = Math.max(width, line.getWidth()); height += line.getHeight(); }
        BufferedImage image = TexI.mkbuf(Coord.of(Math.max(1, width), Math.max(1, height)));
        Graphics2D graphics = image.createGraphics();
        int y = 0;
        for(BufferedImage line : lines) {
            graphics.drawImage(line, (width - line.getWidth()) / 2, y, null);
            y += line.getHeight();
        }
        graphics.dispose();
        return new Label(image, ratio, true);
    }

    /** The same caption with a small picture on its left, made with the same stroke and halo so they read as one label. */
    public static Label captionWithIcon(String text, Color ink, BufferedImage icon) {
        double ratio = hearthborn.ui.LiveScale.ratio();
        BufferedImage line = strokedHalo(vesselFoundry(), text, ink, ratio);
        if(icon == null) return new Label(line, ratio, true);
        BufferedImage trimmed = trim(icon);
        int height = Math.max(1, (int)Math.round(line.getHeight() * 1.15));
        int width = Math.max(1, (int)Math.round(trimmed.getWidth() * (double)height / trimmed.getHeight()));
        int halo = Math.max(1, (int)Math.round(ratio));
        BufferedImage padded = TexI.mkbuf(Coord.of(width + 2 * halo, height + 2 * halo));
        Graphics2D pen = padded.createGraphics();
        pen.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        pen.drawImage(trimmed, halo, halo, width, height, null);
        pen.dispose();
        BufferedImage mark = PUtils.rasterimg(PUtils.blurmask2(padded.getRaster(), halo, halo, Color.BLACK));
        int gap = Math.max(1, (int)Math.round(ratio));
        BufferedImage image = TexI.mkbuf(Coord.of(mark.getWidth() + gap + line.getWidth(), Math.max(mark.getHeight(), line.getHeight())));
        Graphics2D graphics = image.createGraphics();
        graphics.drawImage(mark, 0, (image.getHeight() - mark.getHeight()) / 2, null);
        graphics.drawImage(line, mark.getWidth() + gap, (image.getHeight() - line.getHeight()) / 2, null);
        graphics.dispose();
        return new Label(image, ratio, true);
    }

    /** The picture without its empty margin. */
    static BufferedImage trim(BufferedImage image) {
        int x0 = image.getWidth(), y0 = image.getHeight(), x1 = -1, y1 = -1;
        for(int y = 0; y < image.getHeight(); y++)
            for(int x = 0; x < image.getWidth(); x++)
                if((image.getRGB(x, y) >>> 24) > 16) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
        if(x1 < 0) return image;
        return image.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /** What a vessel shows on its side. A name of two words goes on two lines; anything else stays on one. */
    public static Label vessel(String text) {
        String[] words = text.trim().split("\\s+");
        if(words.length == 2 && !words[0].isEmpty() && !AMOUNT.matcher(text).matches()) {
            double ratio = hearthborn.ui.LiveScale.ratio();
            return stacked(vesselFoundry(), java.util.Arrays.asList(words), Color.WHITE, ratio);
        }
        return caption(text, Color.WHITE, false);
    }

    /** The same caption as a plain picture at {@code ratio}, for things that draw their own texture 1:1. */
    public static BufferedImage captionImage(String text, Color ink, double ratio) {
        return strokedHalo(vesselFoundry(ratio), text, ink, ratio);
    }

    static BufferedImage strokedHalo(Text.Foundry foundry, String text, Color ink, double ratio) {
        Coord size = foundry.strsize(text);
        size = Coord.of(Math.max(1, size.x) + 2, size.y);
        BufferedImage plain = TexI.mkbuf(size);
        java.awt.Graphics2D g = plain.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS, java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(foundry.font);
        int ascent = g.getFontMetrics().getAscent();
        g.setColor(Color.BLACK);
        g.drawString(text, 0, ascent);
        g.drawString(text, 2, ascent);
        g.drawString(text, 1, ascent - 1);
        g.drawString(text, 1, ascent + 1);
        g.setColor(ink);
        g.drawString(text, 1, ascent);
        g.dispose();
        int halo = Math.max(1, (int)Math.round(ratio));
        return PUtils.rasterimg(PUtils.blurmask2(plain.getRaster(), halo, halo, Color.BLACK));
    }

    /** A caption is bold and the size of ordinary world text; it follows the interface ratio only. */
    private static final double VESSEL_BOOST = 1.0;
    private static final Map<Double, Text.Foundry> VESSEL = new HashMap<>();
    private static Text.Foundry vesselFoundry() { return vesselFoundry(hearthborn.ui.LiveScale.ratio()); }
    private static synchronized Text.Foundry vesselFoundry(double ratio) {
        return VESSEL.computeIfAbsent(ratio, r -> new Text.Foundry(Text.std.font.deriveFont(java.awt.Font.BOLD, (float)(Text.std.font.getSize2D() * r * VESSEL_BOOST)), Text.std.defcol).aa(true));
    }

    /** Pixels the vessel caption moves down from the anchor, which sits at the top of the body. */
    public static int vesselDrop() { return UI.scale(17); }

    /** True when the interface was rescaled after this label was drawn. */
    public static boolean stale(Tex tex) {
        if(!(tex instanceof Label)) return false;
        Label label = (Label)tex;
        return Math.abs(label.density - (label.vessel ? hearthborn.ui.LiveScale.ratio() : density())) > 1e-6;
    }

    /** Draw at the interface size the label was made for, which is its pixel size divided by its density. */
    public static void draw(GOut g, Tex tex, Coord at, double ax, double ay) {
        if(tex == null) return;
        double density = tex instanceof Label ? ((Label)tex).density : 1;
        Coord size = tex.sz();
        if(density == 1) { g.aimage(tex, at, ax, ay); return; }
        g.aimage(tex, at, ax, ay, Coord.of((int)Math.round(size.x / density), (int)Math.round(size.y / density)));
    }

    private static final Map<Double, Text.Foundry> FOUNDRIES = new HashMap<>();
    private static synchronized Text.Foundry foundry(double density) {
        if(density == 1) return Text.std;
        return FOUNDRIES.computeIfAbsent(density, d -> new Text.Foundry(Text.std.font.deriveFont((float)(Text.std.font.getSize2D() * d)), Text.std.defcol).aa(Text.std.aa));
    }

    static BufferedImage image(String text, Color ink, boolean split, double density) {
        List<BufferedImage> lines = new ArrayList<>();
        BufferedImage whole = stroked(text, ink, density);
        if(!split || whole.getWidth() <= UI.scale(60) * density) lines.add(whole);
        else {
            Matcher amount = AMOUNT.matcher(text);
            if(amount.matches()) {
                lines.add(stroked(amount.group(1), ink, density));
                lines.add(stroked(amount.group(2), ink, density));
            } else {
                for(String word : text.split(" ")) if(!word.isEmpty()) lines.add(stroked(word, ink, density));
            }
        }
        int width = 0, height = 0;
        for(BufferedImage line : lines) { width = Math.max(width, line.getWidth()); height += line.getHeight(); }
        BufferedImage image = TexI.mkbuf(Coord.of(Math.max(1, width), Math.max(1, height)));
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        int y = 0;
        for(BufferedImage line : lines) {
            graphics.drawImage(line, (width - line.getWidth()) / 2, y, null);
            y += line.getHeight();
        }
        graphics.dispose();
        return image;
    }

    /** Growth labels use a black stroke without a rectangular backing. */
    static BufferedImage growthImage(String text, Color ink) {
        return Utils.outline2(Text.std.render(text,ink).img,Color.BLACK,true);
    }

    /** Crop stage in the quality style: thick black outline and a one-pixel glow. */
    static BufferedImage stageImage(String text, Color ink) {
        BufferedImage drawn = Utils.outline2(Text.render(text, ink).img, Color.BLACK, true);
        return PUtils.rasterimg(PUtils.blurmask2(drawn.getRaster(), UI.scale(1), UI.scale(1), Color.BLACK));
    }

    private static BufferedImage stroked(String text, Color ink, double density) {
        return Utils.outline2(foundry(density).render(text, ink).img, Color.BLACK);
    }
    private WorldLabels() {}
}
