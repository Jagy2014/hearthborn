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
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.regex.*;

/**
 * Show the server's remaining claim presence directly on its bond item.
 *
 * @author Jagy2014
 */
public final class ClaimPresenceOverlay implements Disposable {
    private static final Pattern PRESENCE=Pattern.compile(
        "(?:^|\\n)\\s*(?:Presence|Присутствие)\\s*:\\s*([0-9][0-9, .\\u00a0\\u202f]*)\\s*/\\s*[0-9]",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private String value;
    private Tex label;

    public static String remaining(List<ItemInfo> info) {
        for(ItemInfo tip:info) {
            if(tip instanceof ItemInfo.Contents) {
                String count=remaining(((ItemInfo.Contents)tip).sub);
                if(count!=null)return count;
            } else if(tip instanceof ItemInfo.AdHoc) {
                Matcher match=PRESENCE.matcher(((ItemInfo.AdHoc)tip).str.text);
                if(match.find())return match.group(1).replaceAll("[^0-9]","");
            }
        }
        return null;
    }
    public static String count(GItem item) {
        return item.resource().name.equals("gfx/invobjs/bloodsoil")?remaining(item.info()):null;
    }
    public void draw(GOut g,GItem item,Coord size) {
        try {
            String current=count(item);
            if(!Objects.equals(current,value)) {
                dispose();value=current;
                if(current!=null)label=new TexI(Utils.outline2(Text.render(current,Color.WHITE).img,Color.BLACK));
            }
            if(label!=null)g.aimage(label,size.sub(UI.scale(1),UI.scale(1)),1,1);
        }catch(Loading loading){}
    }
    public void dispose(){if(label!=null){label.dispose();label=null;}value=null;}
}
