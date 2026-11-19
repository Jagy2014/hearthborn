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
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * A buff-row button that brings the chat back after it was closed.
 *
 * @author Jagy2014
 */
public final class ChatButton extends Buff {
    private static final String OPEN = "hearthborn/chat/open-on-message";
    private static final String NOTICE = "hearthborn/chat/notice-line";
    private static final Map<GameUI,ChatButton> shown = new WeakHashMap<>();
    private static final Map<GameUI,Integer> waiting = new WeakHashMap<>();
    private static final Map<GameUI,ChatUI.Channel> latest = new WeakHashMap<>();
    private static final Text.Foundry COUNT = new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD), 10).aa(true);
    private static Tex bubble;
    private final GameUI gui;
    private int painted = -1;
    private Tex badge;
    private ChatButton(GameUI gui) {super(ChatButton::icon); this.gui = gui;}
    public static boolean openOnMessage() {return Utils.getprefb(OPEN, true);}
    public static void openOnMessage(boolean on) {Utils.setprefb(OPEN, on);}
    public static boolean notices() {return Utils.getprefb(NOTICE, true);}
    public static void notices(boolean on) {Utils.setprefb(NOTICE, on);}
    private static Resource iconResource;
    /** Supplies the buff with an image so a hover that reaches the native tooltip still has one. */
    private static synchronized Resource icon() {
        if(iconResource != null) return iconResource;
        Resource.Virtual resource = new Resource.Virtual(Resource.local(), "hearthborn/chat-button", 1);
        try {
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(picture(128), "png", png);
            MessageBuf image = new MessageBuf();
            image.addint16((short)0).addint16((short)0).adduint8(4).addint16((short)-1).addint16((short)0).addint16((short)0);
            image.addstring("scale").adduint8(4).addfloat32(8f).addstring("").addbytes(png.toByteArray());
            resource.add(resource.new Image(new MessageBuf(image.fin())));
        } catch(java.io.IOException error) {
            throw new IllegalStateException("Could not build the chat button", error);
        }
        iconResource = resource;
        return resource;
    }
    private static BufferedImage picture(int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new RadialGradientPaint(side / 2f, side / 2f, side * 0.7071f,
            new float[] {0f, 0.42f, 0.71f, 1f},
            new Color[] {new Color(62, 170, 214), new Color(50, 158, 206), new Color(11, 87, 145), new Color(5, 43, 84)}));
        g.fillRect(0, 0, side, side);
        int edge = side / 10;
        int body = side - side / 3;
        int outline = Math.max(2, side / 28);
        int arc = side / 4;
        g.setColor(new Color(42, 36, 24));
        g.fillRoundRect(edge - outline, edge - outline, side - edge * 2 + outline * 2, body - edge + outline * 2, arc, arc);
        int tail = side / 5;
        g.fillPolygon(new int[] {tail - outline, tail + side / 6, tail}, new int[] {body, body, side - edge + outline}, 3);
        g.setColor(new Color(232, 214, 156));
        g.fillRoundRect(edge, edge, side - edge * 2, body - edge, arc, arc);
        g.fillPolygon(new int[] {tail, tail + side / 6, tail}, new int[] {body, body, side - edge}, 3);
        g.setColor(new Color(42, 36, 24));
        int bar = Math.max(2, side / 16);
        int text = side / 4;
        g.fillRoundRect(side / 4, text, side / 2, bar, bar, bar);
        g.fillRoundRect(side / 4, text + side / 6, side / 2 - side / 10, bar, bar, bar);
        g.dispose();
        return image;
    }
    private static Tex bubble() {
        if(bubble == null) bubble = new TexI(picture(UI.scale(32)));
        return bubble;
    }
    /** One message that arrived while the chat stayed closed. The player's own lines are not counted. */
    public static void receive(GameUI gui, ChatUI.Channel.Message message) {
        if(gui == null || !incoming(message)) return;
        Integer count = waiting.get(gui);
        waiting.put(gui, count == null ? 1 : count + 1);
    }
    /** The tab of the newest message that arrived while the chat stayed closed. */
    public static void remember(GameUI gui, ChatUI.Channel channel, ChatUI.Channel.Message message) {
        if(gui == null || channel == null || !incoming(message)) return;
        latest.put(gui, channel);
    }
    public static ChatUI.Channel takeLatest(GameUI gui) {
        return gui == null ? null : latest.remove(gui);
    }
    /** Puts one closed-chat line on the notice plate. The player's own lines stay in the chat. */
    public static void notice(GameUI gui, ChatUI.Channel channel, ChatUI.Channel.Message message) {
        if(gui == null || !notices() || !incoming(message)) return;
        String body;
        try { body = message.logText(); }
        catch(RuntimeException failed) { return; }
        if(body == null || body.isEmpty()) return;
        String where = channel == null ? null : channel.name();
        String line = where == null || where.isEmpty() ? body : where + ": " + body;
        gui.showPlainNotice(fitNotice(line), ink(message));
    }
    /** At most 100 characters, folded onto lines of about 50 so the plate stays narrow. */
    static String fitNotice(String line) {
        if(line == null || line.isEmpty()) return line;
        String text = line.length() > 100 ? line.substring(0, 99) + "\u2026" : line;
        StringBuilder out = new StringBuilder();
        int column = 0;
        int at = 0;
        while(at < text.length()) {
            char mark = text.charAt(at);
            if(mark == '\n') {out.append('\n'); column = 0; at++; continue;}
            if(mark == ' ') {at++; continue;}
            int end = at;
            while(end < text.length() && text.charAt(end) != ' ' && text.charAt(end) != '\n') end++;
            int length = end - at;
            if(column > 0 && (length > 50 || column + 1 + length > 50)) {out.append('\n'); column = 0;}
            while(length > 50) {
                out.append(text, at, at + 50).append('\n');
                at += 50; length -= 50; column = 0;
            }
            if(column > 0) {out.append(' '); column++;}
            out.append(text, at, end);
            column += length;
            at = end;
        }
        return out.toString();
    }
    private static Color ink(ChatUI.Channel.Message message) {
        ChatUI.Channel.Message current = message instanceof ChatUI.Channel.RealmMessage ? ((ChatUI.Channel.RealmMessage)message).msg : message;
        if(current instanceof ChatUI.Channel.SimpleMessage) {
            Color color = ((ChatUI.Channel.SimpleMessage)current).col;
            return color == null ? Color.WHITE : color;
        }
        if(current instanceof ChatUI.MultiChat.NamedMessage)
            return ((ChatUI.MultiChat.NamedMessage)current).col;
        return Color.WHITE;
    }
    public static int waiting(GameUI gui) {
        Integer count = gui == null ? null : waiting.get(gui);
        return count == null ? 0 : count;
    }
    private static boolean incoming(ChatUI.Channel.Message message) {
        ChatUI.Channel.Message current = message;
        if(current instanceof ChatUI.Channel.RealmMessage)
            current = ((ChatUI.Channel.RealmMessage)current).msg;
        if(current == null) return false;
        String name = current.getClass().getSimpleName();
        return !"MyMessage".equals(name) && !"OutMessage".equals(name);
    }
    public void draw(GOut g) {
        g.chcolor(255, 255, 255, a);
        g.image(Buff.frame, Coord.z);
        g.image(bubble(), Buff.imgoff);
        g.chcolor();
        int count = waiting(gui);
        if(count <= 0) return;
        if(count != painted) {
            if(badge != null) badge.dispose();
            painted = count;
            badge = new TexI(face(count > 99 ? "99+" : Integer.toString(count)));
        }
        if(badge == null) return;
        Coord size = badge.sz();
        Coord at = sz.sub(size).sub(UI.scale(2), UI.scale(1));
        g.chcolor(0, 0, 0, 160);
        g.frect(at.sub(UI.scale(1), 0), size.add(UI.scale(2), 0));
        g.chcolor();
        g.image(badge, at);
    }
    private static BufferedImage face(String value) {
        BufferedImage text = Utils.outline2(COUNT.render(value, Color.WHITE).img, Color.BLACK, true);
        return PUtils.rasterimg(PUtils.blurmask2(text.getRaster(), UI.scale(1), UI.scale(1), Color.BLACK));
    }
    public static void tick(GameUI gui) {
        if(gui.buffs == null || gui.chat == null) return;
        ChatButton button = shown.get(gui);
        if(!gui.chat.closed()) {
            waiting.remove(gui);
            latest.remove(gui);
            if(button != null) {shown.remove(gui); if(button.parent != null) button.destroy();}
            return;
        }
        if(button == null || button.parent == null) {
            button = new ChatButton(gui);
            gui.buffs.addchild(button);
            shown.put(gui, button);
        }
    }
    public static void dispose(GameUI gui) {
        waiting.remove(gui);
        latest.remove(gui);
        ChatButton button = shown.remove(gui);
        if(button != null && button.parent != null) button.destroy();
    }
    public void dispose() {
        if(badge != null) badge.dispose();
        super.dispose();
    }
    /** Sits with the study brain. Whichever of the two was added first stays on its left. */
    public int layoutOrder() {return -3;}
    public boolean mousedown(MouseDownEvent event) {
        if(event.b != 1) return false;
        gui.chat.present();
        return true;
    }
    public Object tooltip(Coord at, Widget previous) {
        int count = waiting(gui);
        if(count <= 0) return L10n.tr("Open chat");
        return L10n.tr("Open chat") + "\n" + L10n.format("New messages: %d", count);
    }
}
