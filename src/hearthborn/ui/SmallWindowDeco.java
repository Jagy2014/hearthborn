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
import hearthborn.UiFonts;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * The single compact window decoration used by Hearthborn.
 *
 * @author Jagy2014
 */
public final class SmallWindowDeco extends Window.DefaultDeco {
    public static final int HEADER_BUTTON = 18;
    private static final IBox FRAME = new IBox.Scaled("gfx/hud/wnd", "tl", "tr", "bl", "br", "extvl", "extvr", "extht", "exthb");
    private static final Tex LEFT = new TexI(image("caption-left", 1));
    private static final Tex MIDDLE = new TexI(Resource.loadsimg("gfx/hud/wnd/cmain"));
    private static final Tex RIGHT = new TexI(image("caption-right", 1));
    private static final Map<Integer, Text.Forge> LIGHT_TITLES = new java.util.HashMap<>();
    private static final Map<AccentTheme, Text.Forge[]> TITLES = new EnumMap<>(AccentTheme.class);
    /** The title face of the current accent: the gold texture, or the same texture moved to the theme's hue. */
    static synchronized Text.Forge title(boolean active) {
        AccentTheme theme = AccentTheme.current();
        if(UiTheme.light()) {
            // On parchment every title is plain serif ink in the accent colour; the window in focus has the darker line round its tab.
            Color ink = theme.title();
            return LIGHT_TITLES.computeIfAbsent(ink.getRGB(), rgb -> new Text.Foundry(UiFonts.SERIF, 14, ink).aa(true));
        }
        Text.Forge[] forges = TITLES.get(theme);
        if(forges == null) {
            BufferedImage texture = FrameSkin.recolor(Window.ctex, theme);
            Color glow = theme.flat ? new Color(0x10, 0x3C, 0x66) : new Color(96, 96, 0);
            forges = new Text.Forge[] {
                new PUtils.BlurFurn(new PUtils.TexFurn(new Text.Foundry(UiFonts.SERIF, 14).aa(true), texture),
                                    UI.rscale(0.75), UI.rscale(1.0), Color.BLACK),
                new PUtils.BlurFurn(new PUtils.TexFurn(new Text.Foundry(UiFonts.SERIF, 14).aa(true), texture),
                                    UI.rscale(0.75), UI.rscale(1.0), glow)};
            TITLES.put(theme, forges);
        }
        return forges[active ? 1 : 0];
    }
    private static Text.Forge titleForge() { return title(false); }
    public final IButton closeButton;
    private final PencilButton renameButton;
    private boolean active, untitled;
    private int capTheme=-1;
    private int headerHeight;
    private boolean joinedHeader;
    /** A stacked caption belongs entirely to its own window, below the shared edge. */
    public void joinedHeader(boolean joined){
        if(joinedHeader==joined)return;
        joinedHeader=joined;
        if(parent instanceof Window)((Window)parent).refreshDecoration();
    }
    /** Top of the rectangular frame, excluding the protruding caption. */
    public int frameTop(){return cptl.y;}
    /** Vertical center of the caption and the close button, on the top border. */
    private int captionCenter;
    private UI.Grab resizeGrab;
    private Coord resizeFrom, resizeSize, minimum=UI.scale(350,255);
    public void minimum(Coord size){if(size!=null)minimum=size;}

    public static IButton closeButton(Runnable action){return new Close(action);}

    /** The close button: the gold picture, or the same picture in the hue of the accent. */
    private static final class Close extends IButton {
        private static final Map<AccentTheme, BufferedImage[]> THEMED = new EnumMap<>(AccentTheme.class);
        private int theme = UiTheme.generation();
        Close(Runnable action){super(closeIcon("close"),closeIcon("close-d"),closeIcon("close-h"),action);}
        private static synchronized BufferedImage[] images(){
            AccentTheme accent = AccentTheme.current();
            BufferedImage[] set = THEMED.get(accent);
            if(set == null)
                THEMED.put(accent, set = new BufferedImage[] {FrameSkin.recolor(closeIcon("close"), accent), FrameSkin.recolor(closeIcon("close-d"), accent), FrameSkin.recolor(closeIcon("close-h"), accent)});
            return set;
        }
        @Override
        public void draw(GOut g){
            if(theme != UiTheme.generation()){theme = UiTheme.generation();redraw();}
            super.draw(g);
        }
        /** On parchment the button is as light as the other buttons: a panel face, a thin line, a quiet cross. */
        private static BufferedImage flat(int w, int h, boolean hover, boolean pressed){
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = image.createGraphics();
            try{
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                float line = Math.max(1, UI.scale(1)), r = Math.max(2, UI.scale(3));
                // No face: a thin rim and a cross; the pointer washes the inside a little.
                if(hover || pressed){
                    g.setColor(pressed ? Parchment.TAB : Parchment.SELECTED);
                    g.fill(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, r * 2, r * 2));
                }
                g.setColor(hover || pressed ? Parchment.FRAME : UiPalette.border());
                g.setStroke(new java.awt.BasicStroke(line));
                g.draw(new java.awt.geom.RoundRectangle2D.Float(line / 2, line / 2, w - line, h - line, r * 2, r * 2));
                g.setColor(hover || pressed ? UiPalette.text() : UiPalette.text2());
                g.setStroke(new java.awt.BasicStroke(Math.max(1.4f, UI.scale(1.6f)), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                float pad = w * 0.3f;
                g.draw(new java.awt.geom.Line2D.Float(pad, pad, w - pad, h - pad));
                g.draw(new java.awt.geom.Line2D.Float(w - pad, pad, pad, h - pad));
            }finally{g.dispose();}
            return image;
        }
        @Override
        public void draw(BufferedImage buf){
            if(UiTheme.light()){
                java.awt.Graphics graphics = buf.getGraphics();
                graphics.drawImage(flat(buf.getWidth(), buf.getHeight(), h || d != null, a && h), 0, 0, null);
                graphics.dispose();
                return;
            }
            BufferedImage[] set = images();
            java.awt.Graphics graphics = buf.getGraphics();
            graphics.drawImage(a && h ? set[1] : h || d != null ? set[2] : set[0], 0, 0, null);
            graphics.dispose();
        }
    }
    public static Coord closeSize(){BufferedImage icon=closeIcon("close");return Coord.of(icon.getWidth(),icon.getHeight());}
    public static Tex captionText(String text){return titleForge().render(text).tex();}
    public static int captionPlate(){return Math.max(LEFT.sz().y,Math.max(MIDDLE.sz().y,RIGHT.sz().y));}
    /** Body coordinate where a row can start without sliding under a caption centered at {@code centerY}. */
    public static int belowCaption(int centerY){return centerY+(captionPlate()+1)/2+UI.scale(2);}
    /** How far the framed caption still covers the window body. */
    public int captionHang(){
        int bottom=captionCenter+(captionPlate()+1)/2;
        int body=aa==null?captionCenter:aa.ul.y;
        return Math.max(0,bottom-body+UI.scale(1));
    }
    /** The map's button row starts just under the caption, the way chat tabs sit under theirs. */
    public int buttonRowGap(){return untitled?0:captionHang();}
    /** The map has no title and no corner close button. */
    public void untitled(){untitled=true;closeButton.hide();}
    /** Padding between the plate's edge and the title. */
    private static int end(){return UI.scale(8);}
    public static void caption(GOut g,Tex text,Coord at){caption(g,text,at,false);}
    /** The title sits on a plate drawn like the buttons; the focused window's plate is lit. */
    public static void caption(GOut g,Tex text,Coord at,boolean lit){
        int e=end();
        if(UiTheme.light()){
            // Parchment: a paper tab with the frame's line round it and no bottom edge, so it grows out of the window.
            AccentTheme accent=AccentTheme.current();
            Coord size=Coord.of(e*2+text.sz().x,captionPlate()),tl=at.sub(0,size.y/2);
            int unit=Math.max(1,UI.scale(1));
            g.chcolor(accent.tab());g.frect(tl,size);
            g.chcolor(lit?accent.frame():UiPalette.border());
            g.frect(tl,Coord.of(size.x,unit));g.frect(tl,Coord.of(unit,size.y));g.frect(tl.add(size.x-unit,0),Coord.of(unit,size.y));
            g.chcolor();
            g.aimage(text,at.add(e,-UI.scale(1)),0,.5);
            return;
        }
        g.aimage(PillSkin.plate(e*2+text.sz().x,captionPlate(),lit),at,0,.5);
        g.aimage(text,at.add(e,-UI.scale(1)),0,.5);
    }

    public SmallWindowDeco(boolean large) {
        super(large);
        cbtn.hide();
        closeButton = add(closeButton(() -> ((Window)parent).reqclose()));
        renameButton=add(new PencilButton(()->hearthborn.world.ObjectNames.edit((Window)parent)));
        renameButton.hide();
    }

    private static BufferedImage closeIcon(String name) {
        try(java.io.InputStream in = SmallWindowDeco.class.getResourceAsStream("/hearthborn/assets/icons/small/" + name + ".png")) {
            if(in == null) throw new IllegalStateException("Missing window image: " + name);
            BufferedImage image = ImageIO.read(in);
            return PUtils.convolve(image, UI.scale(HEADER_BUTTON, HEADER_BUTTON), new PUtils.Lanczos(3));
        } catch(IOException e) {throw new RuntimeException(e);}
    }

    private static BufferedImage image(String name, int pixelsPerUnit) {
        try(java.io.InputStream in = SmallWindowDeco.class.getResourceAsStream("/hearthborn/assets/icons/small/" + name + ".png")) {
            if(in == null) throw new IllegalStateException("Missing window image: " + name);
            BufferedImage image = ImageIO.read(in);
            Coord size = UI.scale(Coord.of(image.getWidth() / pixelsPerUnit, image.getHeight() / pixelsPerUnit));
            return PUtils.convolve(image, size, new PUtils.Lanczos(3));
        } catch(IOException e) {throw new RuntimeException(e);}
    }

    @Override
    public void iresize(Coord contents) {
        Coord margin = UI.scale(1, 1);
        headerHeight = untitled ? 0 : Math.max(titleForge().strsize("Ag\u0401").y + UI.scale(2),
            Math.max(MIDDLE.sz().y, closeButton.sz.y + UI.scale(4)));
        captionCenter = headerHeight / 2;
        if(joinedHeader&&!untitled){
            captionCenter=FRAME.btloff().y+UI.scale(1)+captionPlate()/2;
            headerHeight=captionCenter+(captionPlate()+1)/2+UI.scale(1);
        }
        Coord body = contents.add(margin.mul(2));
        Coord origin=FRAME.btloff().add(0,headerHeight);
        if(joinedHeader&&!untitled)origin.y=headerHeight;
        ca = Area.sized(origin, body);
        aa = Area.sized(ca.ul.add(margin), contents);
        resize(ca.br.add(FRAME.bbroff()));
        closeButton.c = Coord.of(sz.x - FRAME.bbroff().x - closeButton.sz.x,
            captionCenter-closeButton.sz.y/2);
        cptl = Coord.of(0, joinedHeader?0:headerHeight / 2);
    }

    private java.util.function.DoubleSupplier fade;
    /** A window that dims when left alone: its backing turns translucent by this level (1 is opaque). */
    public void fade(java.util.function.DoubleSupplier level){fade=level;}

    @Override
    protected void drawbg(GOut g) {
        double level=fade==null?1:Math.max(0,Math.min(1,fade.getAsDouble()));
        if(level<1){
            Color back=UiTheme.BACKGROUND;
            g.chcolor(back.getRed(),back.getGreen(),back.getBlue(),(int)Math.round(back.getAlpha()*level));
            g.frect(cptl,sz.sub(cptl));g.chcolor();
        }else
        UiTheme.background(g,cptl,sz.sub(cptl));
        if(joinedHeader&&!untitled)UiTheme.panel(g,Coord.z,Coord.of(sz.x,ca.ul.y));
    }

    private void updateCaption(){
        if(!(parent instanceof Window))return;
        Window window=(Window)parent;
        String title=hearthborn.world.ObjectNames.title(window);
        boolean editable=hearthborn.world.ObjectNames.editable(window);
        renameButton.show(editable);
        String shown=title;
        if(shown!=null){
            int space=closeButton.c.x-UI.scale(5)-end()*2-captionInset();
            if(editable)space-=renameButton.sz.x+UI.scale(4);
            if(titleForge().strsize(shown).x>space){
                while(!shown.isEmpty() && titleForge().strsize(shown+"…").x>space)shown=shown.substring(0,shown.length()-1);
                shown+="…";
            }
        }
        if(cap==null || !Objects.equals(cap.text,shown) || active!=window.hasfocus || capTheme!=UiTheme.generation()){
            capTheme=UiTheme.generation();
            if(cap!=null)cap.dispose();active=window.hasfocus;
            cap=shown==null?null:title(active).render(shown);
        }
        if(editable && cap!=null){
            renameButton.move(Coord.of(captionInset()+end()*2+cap.sz().x+UI.scale(3),
                (headerHeight-renameButton.sz.y)/2));
            renameButton.tooltip=window instanceof hearthborn.world.ObjectNames.Titled?((hearthborn.world.ObjectNames.Titled)window).renameTip():hearthborn.L10n.tr("Rename object")+"\n"+title;
        }
    }

    @Override
    public void tick(double dt){super.tick(dt);if(!untitled)updateCaption();}

    private int captionInset(){return joinedHeader?FRAME.btloff().x+UI.scale(2):0;}

    @Override
    protected void drawframe(GOut g) {
        if(!untitled)updateCaption();
        Window window=(Window)parent;
        boolean above=MagneticWindows.joinedAbove(window);
        JoinedWindowFrame.draw(g,(IBox.Images)FRAME,cptl,sz.sub(cptl),above,MagneticWindows.joinedBelow(window));
        if(above)joint(g);
        if(!untitled && cap != null) {
            caption(g,cap.tex(),Coord.of(captionInset(),captionCenter),window.hasfocus);
        }
        if(dragsize) ResizeMarks.draw(g, sz, false);
    }

    /** Where {@link #joint} is in use: the dark blue look. The other looks keep their plain joins. */
    public static boolean marksJoints(){return AccentTheme.blue()&&!UiTheme.light();}
    /** The seam to the window above: a thin line and a small grip of three dots in the middle of it, as a handle between two panels. */
    private void joint(GOut g){
        if(!marksJoints())return;
        int unit=Math.max(1,UI.scale(1)),dot=Math.max(2,UI.scale(2)),step=UI.scale(6);
        g.chcolor(UiPalette.border());
        g.frect(Coord.of(cptl.x,cptl.y),Coord.of(sz.x-cptl.x,unit));
        g.chcolor(UiPalette.text2());
        int x=cptl.x+(sz.x-cptl.x)/2-step-dot/2,y=cptl.y+unit+UI.scale(1);
        for(int i=0;i<3;i++)g.frect(Coord.of(x+i*step,y),Coord.of(dot,dot));
        g.chcolor();
    }

    @Override
    public boolean mousedown(Widget.MouseDownEvent ev) {
        if(dragsize && ev.b == 1 && ev.c.isect(sz.sub(Window.sizer.sz()), Window.sizer.sz())) {
            resizeGrab = ui.grabmouse(this);
            resizeFrom = ev.c;
            resizeSize = ((Window)parent).csz();
            return true;
        }
        boolean resizing = dragsize;
        dragsize = false;
        try {
            return super.mousedown(ev);
        } finally {
            dragsize = resizing;
        }
    }

    @Override
    public void mousemove(Widget.MouseMoveEvent ev) {
        if(resizeGrab != null) {
            ((Window)parent).resize(resizeSize.add(ev.c.sub(resizeFrom)).max(minimum));
            return;
        }
        super.mousemove(ev);
    }

    @Override
    public boolean mouseup(Widget.MouseUpEvent ev) {
        if(ev.b == 1 && resizeGrab != null) {
            resizeGrab.remove();
            resizeGrab = null;
            if(parent instanceof MapWnd)
                ((MapWnd)parent).storePlace();
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public boolean checkhit(Coord c) {return c.isect(Coord.z, sz);}

    @Override
    public void dispose() {
        if(cap != null) {cap.dispose(); cap = null;}
        super.dispose();
    }
}
