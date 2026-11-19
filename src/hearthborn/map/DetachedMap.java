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
import haven.render.*;
import haven.rs.DrawBuffer;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.*;

/**
 * An ordinary window showing the live map, so it can sit on another monitor.
 *
 * @author Jagy2014
 */
public final class DetachedMap {
    private static final Coord OFFSCREEN = Coord.of(-30000, -30000);
    private static DetachedMap current;
    private final GameUI gui;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private JFrame frame;
    private Panel panel;
    private Host host;
    private DrawBuffer buffer;
    private volatile BufferedImage image;
    private boolean painting, closed;

    public static void toggle(MapWnd source) {
        if(current != null && !current.closed) {
            EventQueue.invokeLater(() -> {current.frame.toFront(); current.frame.requestFocus();});
            return;
        }
        GameUI gui = source.getparent(GameUI.class);
        if(gui == null || source.ui == null) return;
        current = new DetachedMap(gui, source);
    }

    public static Widget domain(Widget widget) {
        if(current == null || current.closed || current.host == null) return null;
        for(Widget w = widget; w != null; w = w.parent) if(w == current.host) return w;
        return null;
    }

    public static void close(GameUI gui) {if(current != null && current.gui == gui) current.close();}

    /** The separate map window, not the corner map. */
    public static boolean showing() {
        DetachedMap map = current;
        if(map == null || map.closed) return false;
        JFrame window = map.frame;
        return window != null && window.isVisible();
    }

    /** Hide or show the window without closing it or writing its place. */
    public static void shown(boolean open) {
        DetachedMap map = current;
        if(map == null || map.closed) return;
        EventQueue.invokeLater(() -> {
            JFrame window = map.frame;
            if(window != null && window.isDisplayable() && window.isVisible() != open)
                window.setVisible(open);
        });
    }

    private DetachedMap(GameUI gui, MapWnd source) {
        this.gui = gui;
        Coord size = Utils.getprefc("hearthborn/map/window-size", UI.scale(900, 600)).max(UI.scale(550, 330));
        host = gui.add(new Host(source, size), OFFSCREEN);
        host.view.loadHidden = true;
        host.hide();
        host.compact(false);
        host.resize(size);
        host.c = OFFSCREEN;
        EventQueue.invokeLater(() -> open(size));
    }

    private void open(Coord size) {
        frame = new JFrame(hearthborn.L10n.tr("Hearthborn map"));
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        panel = new Panel();
        frame.setContentPane(panel);
        frame.setSize(size.x, size.y);
        Coord pos = Utils.getprefc("hearthborn/map/window-pos", null);
        if(pos != null) frame.setLocation(pos.x, pos.y);
        else frame.setLocationByPlatform(true);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {remember(frame);}
            public void windowClosed(WindowEvent e) {tasks.add(DetachedMap.this::close);}
        });
        frame.addComponentListener(new ComponentAdapter() {
            public void componentMoved(ComponentEvent e) {remember(frame);}
            public void componentResized(ComponentEvent e) {remember(frame);}
        });
        frame.setVisible(true);
    }

    private static void remember(JFrame window) {
        if(window == null || !window.isDisplayable()) return;
        Utils.setprefc("hearthborn/map/window-size", Coord.of(Math.max(1, window.getWidth()), Math.max(1, window.getHeight())));
        Utils.setprefc("hearthborn/map/window-pos", Coord.of(window.getX(), window.getY()));
    }

    private void close() {
        if(closed) return;
        closed = true;
        JFrame window = frame;
        if(window != null) {
            remember(window);
            EventQueue.invokeLater(() -> {if(window.isDisplayable()) window.dispose();});
        }
        if(host != null && host.parent != null) host.reqdestroy();
        host = null;
        if(buffer != null) buffer.dispose();
        buffer = null;
        if(current == this) current = null;
    }

    private void sync() {
        if(panel == null || host == null) return;
        Coord want = Coord.of(Math.max(1, panel.getWidth()), Math.max(1, panel.getHeight()));
        if(want.x < 10 || want.y < 10) return;
        Coord border = host.sz.sub(host.csz());
        Coord inner = want.sub(border).max(UI.scale(350, 255));
        if(!host.csz().equals(inner)) host.resize(inner);
        host.c = OFFSCREEN;
    }

    private void blit(Environment env) {
        if(closed || host == null || env == null) return;
        sync();
        Coord size = host.sz;
        if(size.x <= 0 || size.y <= 0) return;
        if(buffer == null || !buffer.sz.equals(size)) {
            if(buffer != null) buffer.dispose();
            buffer = new DrawBuffer(env, size);
        }
        try {
            GOut g = buffer.graphics();
            g.chcolor(Color.BLACK);
            g.frect(Coord.z, size);
            g.chcolor();
            painting = true;
            try {host.draw(g);} finally {painting = false;}
            g.getimage(img -> {
                BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D cg = copy.createGraphics();
                cg.drawImage(img, 0, 0, null);
                cg.dispose();
                image = copy;
                if(panel != null) EventQueue.invokeLater(panel::repaint);
            });
            env.submit(g.out);
        } catch(Loading ignored) {}
    }

    /** Both views use the same map file and marker resources, with their own screen coordinates. */
    static void shareMarkers(MapWnd source,MapWnd target){
        if(source.file!=target.file)throw new IllegalArgumentException("Different map files");
        target.view.markers=source.view.markers;
        target.view.iconconf=source.view.iconconf;
        target.markcfg=source.markcfg;
        target.cmarkers=source.cmarkers;
        target.copyMarkerFiltersFrom(source);
    }

    private class Host extends MapWnd {
        Host(MapWnd source, Coord size) {
            super(source.file, source.mv, size, "Map");
            shareMarkers(source,this);
            setcanfocus(false);
        }
        protected boolean worldMarks() {return false;}
        public void drag(Coord offset) {}
        public void draw(GOut g) {if(painting) super.draw(g);}
        public boolean mousedown(MouseDownEvent event) {return false;}
        public void tick(double dt) {
            super.tick(dt);
            Runnable task;
            while(!closed && (task = tasks.poll()) != null) task.run();
            if(!closed && ui != null) ui.drawafter(g -> blit(g.out.env()));
        }
    }

    private class Panel extends JPanel {
        Panel() {
            setBackground(Color.BLACK);
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if(e.getClickCount() >= 2 && e.getButton() == MouseEvent.BUTTON1) {
                        tasks.add(() -> {if(host != null) host.recenter();});
                        return;
                    }
                    send(e, true);
                }
                public void mouseReleased(MouseEvent e) {send(e, false);}
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {move(e);}
                public void mouseMoved(MouseEvent e) {move(e);}
            });
            addMouseWheelListener(e -> {
                Coord c = at(e.getX(), e.getY());
                int amount = e.getWheelRotation() * hearthborn.ui.WheelSettings.direction();
                double sub = e.getPreciseWheelRotation() * hearthborn.ui.WheelSettings.direction();
                tasks.add(() -> {if(host != null) new Widget.MouseWheelEvent(c, amount, sub).dispatch(host);});
            });
            addComponentListener(new ComponentAdapter() {
                public void componentResized(ComponentEvent e) {tasks.add(DetachedMap.this::sync);}
            });
        }

        private Coord at(int x, int y) {
            BufferedImage img = image;
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            if(img == null || img.getWidth() <= 0) return Coord.of(x, y);
            return Coord.of(x * img.getWidth() / w, y * img.getHeight() / h);
        }

        private void send(MouseEvent e, boolean down) {
            int button = e.getButton() == MouseEvent.BUTTON1 ? 1 : e.getButton() == MouseEvent.BUTTON3 ? 3 : 2;
            Coord c = at(e.getX(), e.getY());
            tasks.add(() -> {
                if(host == null) return;
                if(down) new Widget.MouseDownEvent(c, button).dispatch(host);
                else new Widget.MouseUpEvent(c, button).dispatch(host);
            });
        }

        private void move(MouseEvent e) {
            Coord c = at(e.getX(), e.getY());
            tasks.add(() -> {
                if(host == null) return;
                new Widget.MouseMoveEvent(c).dispatch(host);
                new Widget.MouseHoverEvent(c).dispatch(host);
            });
        }

        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            BufferedImage img = image;
            if(img != null) g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
