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
import hearthborn.planner.ClaimBorders;
import hearthborn.ui.SystemButtonSkin;

import haven.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * @author Jagy2014
 */
public final class MapToolbar extends Widget {
    private static final int SIZE = buttonSize();
    private static final Map<String, Tex> ART = new HashMap<>();
    public static void clearArt(){for(Tex tex:ART.values())if(tex!=null)tex.dispose();ART.clear();}
    private final MapWnd map;
    private final Widget close;
    private final Map<Widget, Integer> ranks = new IdentityHashMap<>();
    private int focus = -1;

    public MapToolbar(MapWnd map) {
        this.map = map;
        keep(uniform(GameUI.mapIconButton(() -> map.getparent(GameUI.class))), 2);
        toggle("view", "Display view distance", null);
        toggle("grid", "Display grid", null);
        toggle("pointer", "Display pointers", null);
        button("claim", () -> "Show personal claim borders on map", () -> ClaimBorders.enabled("cplot", !ClaimBorders.enabled("cplot")), null, () -> ClaimBorders.enabled("cplot"));
        button("village", () -> "Show village claim borders on map", () -> ClaimBorders.enabled("vlg", !ClaimBorders.enabled("vlg")), null, () -> ClaimBorders.enabled("vlg"));
        toggle("trail", "Show walked path: ordinary walking lasts for this session; shore-scout path is saved with sample markers until cleared\nRight click: clear both paths and sample markers", () -> MapDisplay.clearTrail(map.getparent(GameUI.class)));
        if(map.detachedWindow()) {
            close = null;
        } else {
            keep(control("extlist", () -> "Open map in a separate window", () -> DetachedMap.toggle(map), null, () -> false), 13);
            close = keep(control("close", () -> "Close", map::reqclose, null, () -> false), 1000);
        }
    }

    public void mousemove(MouseMoveEvent ev) {focus = indexAt(ev.c);}

    public boolean mousehover(MouseHoverEvent ev, boolean on) {
        focus = on ? indexAt(ev.c) : -1;
        return false;
    }

    public static MapToolbar containing(Widget owner) {
        for(Widget at = owner; at != null; at = at.parent)
            if(at instanceof MapToolbar)
                return (MapToolbar)at;
        return null;
    }

    /** The tip sits under the fixed-size icon so it does not cover the button. */
    public Coord tooltipPosition(Widget owner, Coord tip, Coord screen) {
        Widget slot = owner instanceof Button ? owner : focused();
        if(slot == null)
            slot = owner;
        return belowIcon(slot.rootpos(), slot.sz, tip, screen);
    }

    public static Coord belowIcon(Coord origin, Coord icon, Coord tip, Coord screen) {
        Coord edge = UI.scale(8, 8);
        int gap = UI.scale(6);
        int x = origin.x + icon.x / 2 - tip.x / 2;
        int y = origin.y + icon.y + gap;
        if(y + tip.y > screen.y - edge.y)
            y = origin.y - tip.y - gap;
        return Coord.of(Utils.clip(x, edge.x, Math.max(edge.x, screen.x - edge.x - tip.x)),
                         Utils.clip(y, edge.y, Math.max(edge.y, screen.y - edge.y - tip.y)));
    }

    private Widget focused() {
        List<Widget> row = ordered();
        if(focus < 0 || focus >= row.size())
            return null;
        return row.get(focus);
    }

    /** Every control uses the same square. The number on each button is its place in the row. */
    public void adopt(Widget widget) {
        if(widget.parent != null)
            widget.unlink();
        if(widget.kb_gkey == MapWnd.kb_mark)
            widget.settip("New marker");
        keep(uniform(widget), nativeRank(widget));
        place();
    }

    private static int nativeRank(Widget widget) {
        if(widget.kb_gkey == MapWnd.kb_home) return 1;
        if(widget.kb_gkey == MapWnd.kb_mark) return 3;
        if(widget.kb_gkey == MapWnd.kb_prov) return 12;
        if(widget.kb_gkey == MapWnd.kb_compact) return 14;
        return 100;
    }

    private static int iconRank(String icon) {
        if(icon.equals("view")) return 4;
        if(icon.equals("grid")) return 5;
        if(icon.equals("trail")) return 6;
        if(icon.equals("pointer")) return 8;
        if(icon.equals("claim")) return 10;
        if(icon.equals("village")) return 11;
        return 100;
    }

    /** Native art is scaled into the shared map-button frame. The original control stays for its action and shortcut. */
    private Widget uniform(Widget widget) {
        BufferedImage art = artwork(widget);
        Tex icon = art == null ? null : glyphTexture(art);
        Coord hit = hitPoint(widget);
        Widget slot = new Button() {
            private boolean indoors() {return widget.kb_gkey == MapWnd.kb_mark && map.indoors();}
            public void paint(GOut g, Coord size, boolean hot) {
                boolean blocked = indoors();
                boolean on = !blocked && widget.kb_gkey != MapWnd.kb_compact && widget instanceof ACheckBox && ((ACheckBox)widget).state();
                if(blocked) g.chcolor(150, 150, 150, 255);
                g.image(image(null, on, hot && !blocked), Coord.z, size);
                if(icon != null) g.image(icon, Coord.z, size);
                g.chcolor();
            }
            public void dispose() {
                if(icon != null) icon.dispose();
                super.dispose();
            }
            public boolean mousedown(MouseDownEvent ev) {
                if(indoors()) return true;
                if(ev.b == 1) {
                    if(widget instanceof IButton) ((IButton)widget).click();
                    else if(widget instanceof ACheckBox) ((ACheckBox)widget).click();
                }
                return true;
            }
            public boolean mousewheel(MouseWheelEvent ev) {return widget.mousewheel(ev.derive(hit));}
            public Object tooltip(Coord c, Widget prev) {return widget.tooltip(c, prev);}
        };
        slot.add(widget);
        widget.hide();
        return slot;
    }

    private static BufferedImage artwork(Widget widget) {
        BufferedImage image = icon(widget);
        return image == null ? null : trimIcon(image);
    }

    private static Tex glyphTexture(BufferedImage art) {
        BufferedImage glyph = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = glyph.createGraphics();
        paintRoundGlyph(graphics, art);
        graphics.dispose();
        return new TexI(glyph);
    }

    private static Coord hitPoint(Widget widget) {
        BufferedImage image = icon(widget);
        if(image == null)
            return Coord.z;
        boolean alpha = image.getRaster().getNumBands() >= 4;
        for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) {
            int sample = alpha ? image.getRaster().getSample(x, y, 3) : 255;
            if(sample >= 128)
                return Coord.of(x, y);
        }
        return Coord.z;
    }

    private static BufferedImage icon(Widget widget) {
        if(widget instanceof IButton)
            return ((IButton)widget).up;
        if(widget instanceof ICheckBox && ((ICheckBox)widget).up instanceof TexI)
            return ((TexI)((ICheckBox)widget).up).back;
        return null;
    }

    /** Keep every button on one row, shrinking the squares together when the map is narrow. */
    public void place() {
        place(parent == null ? SIZE : parent.sz.x);
    }

    public void place(int width) {
        if(parent == null)
            return;
        if(close != null) close.show(map.deco == null);
        List<Widget> row = ordered();
        int pad = UI.scale(2), gap = UI.scale(2), count = row.size();
        int cell = SIZE, remainder = 0;
        if(count > 0) {
            int room = Math.max(count, width - pad * 2);
            int gaps = count - 1;
            if(count * SIZE + gaps * gap > room) {
                cell = gaps == 0 ? room : (room - gaps * gap) / count;
                if(cell < 1) {
                    gap = 0;
                    cell = Math.max(1, room / count);
                }
                remainder = room - count * cell - gaps * gap;
            }
        }
        int x = pad;
        for(Widget child : row) {
            child.resize(Coord.of(cell, cell));
            child.move(Coord.of(x, pad));
            x += cell + gap;
            // When the cells shrink, spread rounding pixels between them rather than in front of Close.
            if(remainder > 0) {
                x++;
                remainder--;
            }
        }
        int cellHeight = count == 0 ? SIZE : cell;
        int height = pad + cellHeight + pad;
        int used = count == 0 ? pad : x - gap + pad;
        resize(Math.max(width, used), height);
        if(close != null && close.visible)
            close.move(Coord.of(sz.x - pad - cell, pad));
    }

    private List<Widget> ordered() {
        List<Widget> row = new ArrayList<>();
        for(Widget child = this.child; child != null; child = child.next)
            if(child.visible && ranks.containsKey(child)) row.add(child);
        row.sort(Comparator.comparingInt(ranks::get));
        return row;
    }

    private abstract class Button extends Widget {
        private boolean hover;
        Button() {super(Coord.of(SIZE, SIZE));}
        public void draw(GOut g) {paint(g, sz, hover);}
        abstract void paint(GOut g, Coord size, boolean hot);
        public void mousemove(MouseMoveEvent ev) {hover = ev.c.isect(Coord.z, sz);}
        public boolean mousehover(MouseHoverEvent ev, boolean on) {hover = on; return false;}
    }

    /** How far the button row covers the map. Cave level and the tile name start under it. */
    public static int below(MiniMap map) {
        if(map == null) return 0;
        MapWnd window = map.getparent(MapWnd.class);
        if(window == null) return 0;
        MapToolbar tools = window.getchild(MapToolbar.class);
        if(tools == null || !tools.tvisible()) return 0;
        int bottom = tools.parentpos(window).y + tools.occupied();
        int origin = map.parentpos(window).y;
        return Math.max(0, bottom - origin);
    }
    public int occupied() {return stripHeight(ordered());}
    private int stripHeight(List<Widget> row) {
        if(row.isEmpty()) return UI.scale(2) + SIZE + UI.scale(2);
        Widget slot = row.get(0);
        return slot.c.y + slot.sz.y + slot.c.y;
    }

    private int indexAt(Coord c) {
        List<Widget> row = ordered();
        for(int i = 0; i < row.size(); i++) {
            Widget slot = row.get(i);
            if(c.isect(slot.c, slot.sz)) return i;
        }
        return -1;
    }

    private Widget keep(Widget slot, int rank) {
        ranks.put(slot, rank);
        return add(slot);
    }

    private static int buttonSize() {
        return UI.scale(28);
    }

    private void toggle(String icon, String tip, Runnable right) {
        String feature = icon.equals("pointer") ? "pointers" : icon;
        button(icon, () -> tip, () -> MapDisplay.enabled(feature, !MapDisplay.enabled(feature)), right, () -> MapDisplay.enabled(feature));
    }

    private void button(String icon, Supplier<String> tip, Runnable left, Runnable right, Supplier<Boolean> active) {
        keep(control(icon, tip, left, right, active), iconRank(icon));
    }

    private Widget control(String icon, Supplier<String> tip, Runnable left, Runnable right, Supplier<Boolean> active) {
        return new Button() {
            public void paint(GOut g, Coord size, boolean hot) {
                g.image(image(icon, active.get(), hot), Coord.z, size);
            }
            public boolean mousedown(MouseDownEvent ev) {
                if(ev.b == 1) left.run();
                else if(ev.b == 3 && right != null) right.run();
                settip(tip.get());
                return true;
            }
            public Object tooltip(Coord c, Widget prev) {
                settip(tip.get());
                return super.tooltip(c, prev);
            }
        }.settip(tip.get());
    }

    private static synchronized Tex image(String name, boolean on, boolean hover) {
        String key = name + "/" + on + "/" + hover;
        Tex texture = ART.get(key);
        if(texture != null) return texture;
        BufferedImage image=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        SystemButtonSkin.paintDisk(graphics,SIZE,on,hover);
        if("close".equals(name)) {
            int lo=SIZE/4,hi=SIZE-1-lo;
            graphics.setColor(new Color(240,224,183));
            graphics.setStroke(new BasicStroke(Math.max(1f,UI.scale(2f)),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            graphics.drawLine(lo,lo,hi,hi);
            graphics.drawLine(lo,hi,hi,lo);
        } else if(name != null) {
            paintRoundGlyph(graphics,trimIcon(iconSource(name)));
        }
        graphics.dispose();
        ART.put(key,texture=new TexI(image));return texture;
    }
    /** Fill the medallion's interior without stretching the artwork or covering its rim. */
    private static void paintRoundGlyph(Graphics2D g,BufferedImage source) {
        int rim=Math.max(1,UI.scale(3)),extent=Math.max(1,SIZE-rim*2);
        double scale=extent/(double)Math.max(source.getWidth(),source.getHeight());
        int w=Math.max(1,(int)Math.round(source.getWidth()*scale));
        int h=Math.max(1,(int)Math.round(source.getHeight()*scale));
        Shape previous=g.getClip();
        g.clip(new Ellipse2D.Double(rim,rim,extent,extent));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source,(SIZE-w)/2,(SIZE-h)/2,w,h,null);
        g.setClip(previous);
    }
    private static final Map<String,BufferedImage> STANDARD=new HashMap<>();
    private static BufferedImage standard(String resource){
        BufferedImage image=STANDARD.get(resource);
        if(image==null)STANDARD.put(resource,image=hearthborn.ui.SystemControls.buttonImages(SIZE-UI.scale(8),resource)[0]);
        return image;
    }
    private static BufferedImage iconSource(String name) {
        if(name.equals("options"))return standard("gfx/hud/rbtn-opt");
        if(name.equals("claim"))return standard("gfx/hud/lbtn-claim");
        if(name.equals("village"))return standard("gfx/hud/lbtn-vil");
        return png("/hearthborn/assets/icons/map/" + name + ".png");
    }
    private static BufferedImage trimIcon(BufferedImage image){
        int left=image.getWidth(),top=image.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
            if((image.getRGB(x,y)>>>24)>24){
                left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);
            }
        }
        return right<left?image:image.getSubimage(left,top,right-left+1,bottom-top+1);
    }
    private static Tex pointer;
    public static synchronized Tex pointerMarker(){
        if(pointer==null)pointer=new TexI(PUtils.uiscale(trimIcon(png("/hearthborn/assets/icons/map/pointer.png")),UI.scale(24,24)));
        return pointer;
    }
    private static Tex player;
    public static synchronized Tex playerMarker(){
        if(player==null)player=new TexI(PUtils.uiscale(png("/hearthborn/assets/icons/map/player.png"),UI.scale(18,18)));
        return player;
    }
    public static Tex playerMarker(Color color){return PlayerArrow.texture(color);}
    private static BufferedImage png(String path){
        try(java.io.InputStream in=MapToolbar.class.getResourceAsStream(path)){
            if(in==null)throw new java.io.IOException("Missing map icon: "+path);
            return ImageIO.read(in);
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
