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

/**
 * One separator per stacked-window boundary, with continuous outer sides.
 *
 * @author Jagy2014
 */
public final class JoinedWindowFrame {
    public static void draw(GOut g,IBox.Images box,Coord at,Coord size,boolean above,boolean below){
        if(!above&&!below){box.draw(g,at,size);return;}
        if(box.flat(g,at,size,!above,!below))return;
        int left=above?box.bl.sz().x:box.ctl.sz().x;
        int right=above?box.br.sz().x:box.ctr.sz().x;
        g.image(box.bt,at.add(left,0),Coord.of(Math.max(0,size.x-left-right),box.bt.sz().y));
        if(!below)g.image(box.bb,at.add(box.cbl.sz().x,size.y-box.bb.sz().y),
            Coord.of(Math.max(0,size.x-box.cbl.sz().x-box.cbr.sz().x),box.bb.sz().y));
        int start=above?0:box.ctl.sz().y,end=below?size.y:size.y-box.cbl.sz().y;
        g.image(box.bl,at.add(0,start),Coord.of(box.bl.sz().x,Math.max(0,end-start)));
        start=above?0:box.ctr.sz().y;end=below?size.y:size.y-box.cbr.sz().y;
        g.image(box.br,at.add(size.x-box.br.sz().x,start),Coord.of(box.br.sz().x,Math.max(0,end-start)));
        if(!above){g.image(box.ctl,at);g.image(box.ctr,at.add(size.x-box.ctr.sz().x,0));}
        if(!below){g.image(box.cbl,at.add(0,size.y-box.cbl.sz().y));g.image(box.cbr,at.add(size.sub(box.cbr.sz())));}
    }
    private JoinedWindowFrame(){}
}
