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

package hearthborn.inventory;

import haven.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Per-container controls; no alternate inventory widget or window decoration is required.
 *
 * @author Jagy2014
 */
public final class ContainerTools {
    private static final int PANEL_WIDTH = 200;
    private static final Set<String> FOOD_TABLES = new HashSet<>(Arrays.asList(
        "Table", "Cottage Table", "Rustic Table", "Stone Table"));
    private static final Set<String> EXCLUDED = new HashSet<>(Arrays.asList(
        "Belt", "Пояс", "Character Sheet", "Study", "Birdhouse", "Chicken Coop", "Rabbit Hutch", "Cauldron", "Finery Forge", "Fireplace",
        "Frame", "Herbalist Table", "Kiln", "Ore Smelter", "Smith's Smelter", "Oven", "Pelican's Pouch",
        "Pane mold", "Rack", "Smoke shed", "Stack Furnace", "Steelbox", "Tub"));
    private static final Map<String, BufferedImage> ICONS = new HashMap<>();
    private static final Map<BufferedImage, Area> BUTTON_BOUNDS = new WeakHashMap<>();
    private final Inventory inv;
    private Toolbar toolbar;
    private SidePanel side;
    private Window sideOwner;
    private Coord baseSize;

    public ContainerTools(Inventory inv) {this.inv = inv;}
    public void collapse(){closeSide();}
    /** Persistent inventory windows must not restore an expanded side panel. */
    public static void closed(Window window){
        for(Inventory inventory:window.children(Inventory.class))
            if(inventory.getparent(Window.class)==window)inventory.collapseContainerTools();
    }

    /** Storage windows use the same slightly larger grid as a furnace. Food tables stay ordinary. */
    public static boolean container(Window window) {
        if(window == null || window instanceof GItem.ContentsWindow || window.cap == null) return false;
        return !food(window.cap) && !excluded(window.cap);
    }
    /** Stack, sort and overview buttons belong to the backpack, the cupboard and the metal cabinet only; elsewhere the header has no room for them. */
    public static boolean toolbarWindow(Window window) {
        if(window == null || window.cap == null) return false;
        if(hearthborn.storage.ContainerContents.supported(window)) return true;
        return window.cap.equals("Inventory") || window.cap.equals(hearthborn.L10n.text("window", "Inventory"));
    }
    private static boolean excluded(String caption) {
        if(caption == null) return false;
        for(String name : EXCLUDED) if(named(caption, name)) return true;
        return false;
    }
    private static boolean food(String caption) {
        for(String name : FOOD_TABLES) if(named(caption, name)) return true;
        return false;
    }
    private static boolean named(String caption, String name) {
        if(caption.equalsIgnoreCase(name)) return true;
        String translated = hearthborn.L10n.text("window", name);
        if(translated != null && caption.equals(translated)) return true;
        return follows(caption, name) || follows(caption, translated);
    }
    private static boolean follows(String caption, String name) {
        return name != null && !name.isEmpty() && caption.length() > name.length()
            && caption.regionMatches(true, 0, name, 0, name.length()) && caption.charAt(name.length()) == ' ';
    }

    private static BufferedImage icon(String name) {
        return ICONS.computeIfAbsent(name, key -> {
            try(java.io.InputStream in = ContainerTools.class.getResourceAsStream("/hearthborn/assets/icons/" + key + ".png")) {
                if(in == null) throw new IllegalStateException("Missing container icon: " + key);
                BufferedImage image = ImageIO.read(in);
                // Source artwork has four pixels per logical UI pixel.
                return PUtils.convolve(image, UI.scale(hearthborn.ui.SmallWindowDeco.HEADER_BUTTON, hearthborn.ui.SmallWindowDeco.HEADER_BUTTON), CharWnd.iconfilter);
            } catch(IOException e) {throw new RuntimeException(e);}
        });
    }

    public static Coord headerPosition(Coord closePosition, BufferedImage closeImage, Coord toolbarSize) {
        Area bounds = BUTTON_BOUNDS.computeIfAbsent(closeImage, image -> {
            int left = image.getWidth(), top = image.getHeight(), right = -1, bottom = -1;
            for(int y = 0; y < image.getHeight(); y++) for(int x = 0; x < image.getWidth(); x++) {
                if((image.getRGB(x, y) >>> 24) <= 128) continue;
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
            return right < 0 ? Area.sized(Coord.z, Coord.of(image.getWidth(), image.getHeight()))
                : Area.sized(Coord.of(left, top), Coord.of(right - left + 1, bottom - top + 1));
        });
        // Window artwork includes transparent padding above its close glyph.
        // Align to the visible glyph, not the image's top-left corner.
        return Coord.of(Math.max(0, closePosition.x + bounds.ul.x - toolbarSize.x - UI.scale(3)),
            closePosition.y + bounds.ul.y + (bounds.sz().y - toolbarSize.y) / 2);
    }

    public void tick(double dt) {
        Window wnd = inv.getparent(Window.class);
        if(inv.ui == null || wnd == null || inv.getparent(GItem.class) != null)return;
        if(hearthborn.world.ObjectNames.editable(wnd) || excluded(wnd.cap) || !toolbarWindow(wnd)){
            if(toolbar!=null){toolbar.reqdestroy();toolbar=null;}
            closeSide();return;
        }
        if(!(wnd.deco instanceof Window.DefaultDeco)) return;
        if(!wnd.visible()) {collapse();return;}
        if(toolbar == null || toolbar.parent != wnd.deco) {
            if(toolbar != null) toolbar.reqdestroy();
            toolbar = wnd.deco.add(new Toolbar());
        }
        Window.DefaultDeco deco = (Window.DefaultDeco)wnd.deco;
        IButton close = deco instanceof hearthborn.ui.SmallWindowDeco
            ? ((hearthborn.ui.SmallWindowDeco)deco).closeButton : deco.cbtn;
        toolbar.c = headerPosition(close.c, close.up, toolbar.sz);
        if(side != null) layoutSide();
    }

    private void showGroups() {
        if(side != null && side.mode == 0) {closeSide(); return;}
        openSide(0);
    }

    private void openSide(int mode) {
        closeSide();
        sideOwner = inv.getparent(Window.class);
        baseSize = sideOwner.csz();
        side = sideOwner.add(new SidePanel(mode), Coord.of(baseSize.x + UI.scale(10), inv.c.y));
        layoutSide();
    }

    private void layoutSide() {
        if(side == null || sideOwner == null) return;
        side.c = Coord.of(Math.max(baseSize.x, inv.c.x + inv.sz.x) + UI.scale(10), inv.c.y);
        ContainerOverview overview=side.getchild(ContainerOverview.class);
        if(overview!=null&&sideOwner.parent!=null) {
            int available=hearthborn.ui.HudInterfaceScale.fromParent(sideOwner,sideOwner.parent.sz).y
                -(sideOwner.sz.y-sideOwner.csz().y)-side.c.y;
            overview.fitHeight(available);side.pack();
        }
        Coord wanted = Coord.of(side.c.x + side.sz.x, Math.max(baseSize.y, side.c.y + side.sz.y));
        if(!sideOwner.csz().equals(wanted)) sideOwner.resize(wanted);
        if(sideOwner.parent != null) fit(sideOwner);
    }

    private void closeSide() {
        if(side == null) return;
        SidePanel old = side;
        side = null;
        old.reqdestroy();
        if(sideOwner != null && baseSize != null) sideOwner.resize(baseSize);
        sideOwner = null;
        baseSize = null;
    }

    private class SidePanel extends Widget {
        final int mode;
        SidePanel(int mode) {
            super(UI.scale(PANEL_WIDTH, 55));this.mode=mode;
            add(new ContainerOverview(inv),UI.scale(6,0));
            pack();
        }
        public void draw(GOut g) {
            g.chcolor(255,255,255,35);g.frect(Coord.z,Coord.of(UI.scale(1),Math.max(sz.y,inv.sz.y)));g.chcolor();super.draw(g);
        }
    }

    private void fit(Window window) {
        if(window.parent == null)return;
        Coord max = window.parent.sz.sub(hearthborn.ui.HudInterfaceScale.size(window));
        window.c = Coord.of(Math.max(0, Math.min(window.c.x, max.x)), Math.max(0, Math.min(window.c.y, max.y)));
    }

    public void dispose() {
        if(toolbar != null && toolbar.parent != null) toolbar.reqdestroy();
        closeSide();
    }

    private class Toolbar extends Widget {
        Toolbar() {
            int x = 0;
            x = button("stack", hearthborn.L10n.tr("Stack identical items"), () -> InventoryActions.stack(inv), x);
            x = button("unstack", hearthborn.L10n.tr("Unstack all items"), () -> InventoryActions.unstack(inv), x);
            x = button("sort", hearthborn.L10n.tr("Left click: best to worst quality\nRight click: worst to best quality\nGrouped by name; large items, such as cheese trays, are sorted among themselves"), () -> InventoryActions.sort(inv), x);
            x = button("extlist", hearthborn.L10n.tr("Container overview"), ContainerTools.this::showGroups, x);
            Window window=inv.getparent(Window.class);
            if(hearthborn.storage.ContainerContents.supported(window)){
                IButton choose=add(new hearthborn.storage.CupboardIconButton(false,()->hearthborn.storage.ContainerContents.choose(window)),Coord.of(x,0));
                x+=choose.sz.x+UI.scale(2);
                IButton automatic=add(new hearthborn.storage.CupboardIconButton(true,()->hearthborn.storage.ContainerContents.automatic(window)),Coord.of(x,0));
                x+=automatic.sz.x+UI.scale(2);
            }
            resize(Coord.of(x, icon("sort").getHeight()));
        }
        private int button(String name, String tip, Runnable action, int x) {
            IButton b = add(new IButton(icon(name), icon(name + "-d"), icon(name + "-h"), action) {
                private boolean selected;
                public void tick(double dt) {
                    super.tick(dt);
                    boolean open = name.equals("extlist") && side != null && side.mode == 0;
                    if(open != selected) {selected = open; redraw();}
                }
                public void draw(BufferedImage buffer) {
                    if(name.equals("extlist") && side != null && side.mode == 0) {
                        java.awt.Graphics graphics = buffer.getGraphics();
                        graphics.drawImage(down, 0, 0, null);
                        graphics.dispose();
                    } else super.draw(buffer);
                }
                public boolean mousedown(MouseDownEvent ev) {
                    if(name.equals("sort") && ev.b == 3) {
                        InventoryActions.sort(inv, true);
                        return true;
                    }
                    return super.mousedown(ev);
                }
            }, Coord.of(x, 0));
            b.settip(tip);
            return x + b.sz.x + UI.scale(2);
        }
    }
}
