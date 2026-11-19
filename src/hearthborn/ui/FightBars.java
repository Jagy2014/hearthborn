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
import hearthborn.combat.CombatPrefs;
import java.awt.Color;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draggable copies of the health and stamina meters.
 *
 * @author Jagy2014
 */
public final class FightBars extends Widget {
    private static final Coord BAR = UI.scale(150, 16);
    private static final Text.Foundry FONT = new Text.Foundry(Text.sans.deriveFont(Font.BOLD), 12);
    private final Map<String, Tex> percent = new HashMap<>();
    private final Map<String, String> shown = new HashMap<>();
    private UI.Grab grab;
    private Coord grabbed;

    public FightBars() {
        super(new Coord(BAR.x, BAR.y * 2 + UI.scale(4)));
        hide();
    }

    public static void attach(GameUI gui) {
        Coord at = new Coord(Utils.getprefi("hearthborn/combat/bars-x", UI.scale(12)),
            Utils.getprefi("hearthborn/combat/bars-y", UI.scale(70)));
        gui.add(new FightBars(), at);
    }

    public void tick(double dt) {
        boolean show = CombatPrefs.bars() && (!CombatPrefs.barsCombat() || haven.Fightsess.active);
        if(show && !visible)
            show();
        else if(!show && visible)
            hide();
    }

    public boolean checkhit(Coord c) {
        if(!visible || CombatPrefs.lockBars())
            return(false);
        return(c.isect(Coord.z, sz));
    }

    public void draw(GOut g) {
        bar(g, Coord.z, "hp");
        bar(g, new Coord(0, BAR.y + UI.scale(4)), "stam");
    }

    private void bar(GOut g, Coord c, String part) {
        GameUI gui = getparent(GameUI.class);
        IMeter meter = (gui == null) ? null : gui.meter(part);
        g.chcolor(UiTheme.meterTrack());
        g.frect(c, BAR);
        g.chcolor(255, 255, 255, 140);
        g.rect(c, BAR);
        g.chcolor();
        if(meter == null)
            return;
        List<LayerMeter.Meter> layers = meter.meters();
        for(LayerMeter.Meter layer : layers) {
            int w = (int)Math.ceil(BAR.x * Utils.clip(layer.a, 0, 1));
            g.chcolor(layer.c);
            g.frect(c, new Coord(w, BAR.y));
        }
        g.chcolor();
        if(layers.isEmpty())
            return;
        int constitution = 0;
        if(gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null)
            constitution = gui.ui.sess.glob.getcattr("con").comp;
        String value = MeterReadout.text("gfx/hud/meter/" + part, layers, constitution);
        if(!value.equals(shown.get(part))) {
            Tex old = percent.remove(part);
            if(old != null)
                old.dispose();
            shown.put(part, value);
            percent.put(part, new TexI(Utils.outline2(FONT.render(value, Color.WHITE).img, Color.BLACK)));
        }
        Tex label = percent.get(part);
        if(label != null)
            g.aimage(label, c.add(BAR.div(2)), 0.5, 0.5);
    }

    public void dispose() {
        for(Tex tex : percent.values())
            tex.dispose();
        percent.clear();
        shown.clear();
        super.dispose();
    }

    public boolean mousedown(MouseDownEvent ev) {
        if((ev.b != 1) || CombatPrefs.lockBars())
            return(false);
        grab = ui.grabmouse(this);
        grabbed = ev.c;
        return(true);
    }

    public void mousemove(MouseMoveEvent ev) {
        if(grab != null)
            move(this.c.add(ev.c.sub(grabbed)));
    }

    public boolean mouseup(MouseUpEvent ev) {
        if(grab == null)
            return(false);
        grab.remove();
        grab = null;
        Utils.setprefi("hearthborn/combat/bars-x", c.x);
        Utils.setprefi("hearthborn/combat/bars-y", c.y);
        return(true);
    }
}
