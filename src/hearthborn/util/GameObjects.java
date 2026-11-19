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

package hearthborn.util;
import haven.*;
import hearthborn.L10n;
import hearthborn.QualityOverlay;
import java.lang.reflect.Field;
import java.util.*;

/**
 * @author Jagy2014
 */
public final class GameObjects {
    private static final Properties names=hearthborn.l10n.Catalog.load("/hearthborn/l10n/resource-names.properties");
    public static Resource resource(Gob gob) {
        Drawable d=gob==null?null:gob.getattr(Drawable.class);
        return d==null?null:d.getres();
    }
    public static String resourceName(Gob gob) { Resource r=resource(gob);return r==null?null:r.name; }
    public static String name(String path) {
        if(path==null)return "";
        String raw=path.substring(path.lastIndexOf('/')+1).replace('-',' ');
        String english=names.getProperty(path,raw.isEmpty()?raw:Character.toUpperCase(raw.charAt(0))+raw.substring(1));
        return L10n.resource("tooltip",path,english);
    }
    public static String overlayName(Gob.Overlay overlay) {
        if(overlay.sm instanceof OCache.OlSprite)return ((OCache.OlSprite)overlay.sm).res.get().name;
        if(overlay.sm instanceof Sprite.Mill.FromRes)return ((Sprite.Mill.FromRes)overlay.sm).res.get().name;
        return overlay.spr==null||overlay.spr.res==null?"":overlay.spr.res.name;
    }
    public static <T>T field(Object object,String name,Class<T> type) {
        if(object==null)return null;
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try {
            Field f=c.getDeclaredField(name);f.setAccessible(true);Object value=f.get(object);
            return type.isInstance(value)?type.cast(value):null;
        } catch(NoSuchFieldException e) {} catch(ReflectiveOperationException e) {return null;}
        return null;
    }
    public static double meter(GItem item) {
        for(ItemInfo info:item.info())if(info instanceof GItem.MeterInfo)return ((GItem.MeterInfo)info).meter();
        return item.meter/100.0;
    }
    public static double quality(GItem item) { Double value=QualityOverlay.qualityOf(item);return value==null?0:value; }
    public static int count(GItem item) {
        for(ItemInfo info:item.info())if(info instanceof GItem.NumberInfo)return Math.max(1,((GItem.NumberInfo)info).itemnum());
        if(item.contents!=null && item.contents.getClass().getName().endsWith("ItemStack")) {
            int count=0;for(GItem child:item.contents.children(GItem.class))count+=count(child);
            return Math.max(1,count);
        }
        return Math.max(1,item.num);
    }
    private GameObjects() {}
}
