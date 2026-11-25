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
import javax.imageio.ImageIO;
import java.util.function.Consumer;

/**
 * @author Jagy2014
 */
public final class IconCategoryPanel extends Widget {
    private static final java.util.Map<IconCategories.Category,Tex> ART=new java.util.EnumMap<>(IconCategories.Category.class);
    public static synchronized Tex image(IconCategories.Category category) {
        Tex image=ART.get(category);
        if(image==null) {
            String art = category == IconCategories.Category.DUNGEONS ? "resources" : category.id;
            try(java.io.InputStream in=IconCategoryPanel.class.getResourceAsStream(category==IconCategories.Category.ALL ? "/hearthborn/assets/icons/common/all.png" : "/hearthborn/assets/icons/categories/"+art+".png")) {
                image=new TexI(PUtils.uiscale(ImageIO.read(in),UI.scale(18,18)));ART.put(category,image);
            } catch(java.io.IOException e){throw new RuntimeException(e);}
        }
        return image;
    }
    private IconCategories.Category selected=IconCategories.Category.ALL;
    public IconCategoryPanel(int width,GobIcon.Settings settings,Consumer<IconCategories.Category> select) {
        super(Coord.of(width,UI.scale(22)*IconCategories.Category.values().length));
        for(IconCategories.Category category:IconCategories.Category.values()) {
            Widget row=add(new Widget(Coord.of(width,UI.scale(22))) {
                public void draw(GOut g) {
                    g.chcolor(selected==category ? 64 : 28,selected==category ? 97 : 35,30,180);
                    g.frect(Coord.z,sz);g.chcolor();super.draw(g);
                }
                public boolean mousedown(MouseDownEvent ev) {
                    if(ev.propagate(this))return true;
                    selected=category;select.accept(category);return true;
                }
            },Coord.of(0,category.ordinal()*UI.scale(22)));
            row.add(new Img(image(category)),UI.scale(1,2));
            Label label=new Label(category.title);row.add(label,Coord.of(UI.scale(25),(row.sz.y-label.sz.y)/2));
        }
    }
}
