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
import java.util.*;
import java.util.function.Predicate;

/**
 * Bind each open object window once; never guess the nearest object.
 *
 * @author Jagy2014
 */
public final class ObjectWindows {
    private static final Map<Window,Gob> bound=new WeakHashMap<>();
    private static final Deque<Click> clicks=new ArrayDeque<>();
    private static class Click {final Gob gob;final double time=Utils.rtime();Click(Gob gob){this.gob=gob;}}
    public static void clicked(Gob gob) {
        if(gob==null)return;
        if(clicks.size()>=32)clicks.removeFirst();
        clicks.addLast(new Click(gob));
    }
    public static Gob target(Window window,GameUI gui,Predicate<Gob> matches) {
        Long id=window.objectId();
        if(id!=null && gui.ui!=null && gui.ui.sess!=null) {
            Gob gob=gui.ui.sess.glob.oc.getgob(id);
            if(gob!=null && matches.test(gob)) {bound.put(window,gob);return gob;}
        }
        Gob known=bound.get(window);
        if(known!=null)return matches.test(known)?known:null;
        Iterator<Click> iterator=clicks.descendingIterator();
        while(iterator.hasNext()) {
            Click click=iterator.next();
            if(Utils.rtime()-click.time>30){iterator.remove();continue;}
            if(matches.test(click.gob)){iterator.remove();bound.put(window,click.gob);return click.gob;}
        }
        return null;
    }
    public static void clear() {bound.clear();clicks.clear();}
    private ObjectWindows() {}
}
