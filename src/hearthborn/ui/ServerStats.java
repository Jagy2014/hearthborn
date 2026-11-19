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
import hearthborn.L10n;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Frames per second to the right of the clock, with an optional ping line underneath.
 *
 * @author Jagy2014
 */
public final class ServerStats extends Widget {
    public static boolean covered;
    private static HttpStatus status;
    private Tex text;
    private String last;
    private double elapsed = 1;

    public static boolean fps() {return Utils.getprefb("hearthborn/hud/fps", true);}
    public static void fps(boolean on) {Utils.setprefb("hearthborn/hud/fps", on);}
    public static boolean ping() {return Utils.getprefb("hearthborn/hud/ping", true);}
    public static void ping(boolean on) {Utils.setprefb("hearthborn/hud/ping", on);}

    /** FPS, the ping line, or both. An empty result draws nothing. */
    static String readout(int frames, String delay) {
        String line = fps() ? L10n.format("FPS: %d", frames) : null;
        if(ping() && delay != null) {
            String wait = L10n.format("Ping: %s ms", delay);
            return line == null ? wait : line + "\n" + wait;
        }
        return line == null ? "" : line;
    }

    public void tick(double dt) {
        WorldChoices.observe(ui);
        ensureStatus();
        place();
        if((elapsed += dt) < 0.25) return;
        elapsed = 0;
        String value = readout(ui == null ? 0 : ui.fps, ping() ? delay(ui) : null);
        if(!value.equals(last)) {
            if(text != null) text.dispose();
            text = null;
            last = value;
            if(value.isEmpty()) {resize(Coord.z); return;}
            Text.Foundry font = new Text.Foundry(Text.sans, 12).aa(false);
            String[] rows = value.split("\n", -1);
            Text[] lines = new Text[rows.length];
            int width = 0, height = 0;
            for(int i = 0; i < rows.length; i++) {
                lines[i] = font.render(rows[i], Color.WHITE);
                width = Math.max(width, lines[i].sz().x);
                height += lines[i].sz().y;
            }
            int pad = UI.scale(3);
            BufferedImage image = TexI.mkbuf(Coord.of(width, height).add(pad * 2, pad * 2));
            Graphics g = image.getGraphics();
            int y = pad;
            for(Text line : lines) {
                g.drawImage(line.img, pad, y, null);
                y += line.sz().y;
                line.dispose();
            }
            g.dispose();
            text = new TexI(image);
            resize(text.sz());
            place();
        }
    }

    private static String delay(UI ui) {
        if(ui == null || ui.sess == null || !(ui.sess.conn instanceof Connection)) return "?";
        double value = ((Connection)ui.sess.conn).stats.roundTripMillis();
        return Double.isNaN(value) ? "?" : String.format(Locale.ROOT, "%.3f", value);
    }

    public void presize() {place();}

    /** Top of the clock: to its right, or to its left when the portrait cluster is mirrored. */
    static Coord beside(Coord screen, Coord box, Area clock) {
        if(PortraitSide.onRight()){
            int x=clock.ul.x-UI.scale(6)-box.x;
            return Coord.of(Utils.clip(x,0,Math.max(0,screen.x-box.x)),clock.ul.y);
        }
        int x = clock.br.x + UI.scale(6);
        int y = clock.ul.y;
        return Coord.of(Utils.clip(x, 0, Math.max(0, screen.x - box.x)), y);
    }

    private void place() {
        if(parent == null) return;
        GameUI gui = getparent(GameUI.class);
        Cal clock = null;
        if(gui != null) for(Widget panel = gui.child; panel != null && clock == null; panel = panel.next)
            for(Widget child = panel.child; child != null; child = child.next)
                if(child instanceof Cal) clock = (Cal)child;
        if(clock == null) {
            move(Coord.of(Math.max(0, parent.sz.x - sz.x - UI.scale(4)), UI.scale(4)));
            return;
        }
        move(beside(parent.sz, sz, clock.parentarea(parent)));
    }

    public void draw(GOut g) {
        if(covered || text == null) return;
        g.chcolor(0, 0, 0, 255);
        g.frect(Coord.z, sz);
        g.chcolor();
        g.image(text, Coord.z);
    }

    public static String players() {
        HttpStatus current = status;
        if(current == null) return "?";
        synchronized(current) {
            if(current.syn && "up".equals(current.status)) return Integer.toString(current.users);
        }
        return "?";
    }

    private static void ensureStatus() {
        if(status == null && HttpStatus.mond.get() != null) {
            status = new HttpStatus();
            status.start();
        }
    }

    public void dispose() {
        if(status != null) {status.quit(); status = null;}
        if(text != null) text.dispose();
        super.dispose();
    }
}
