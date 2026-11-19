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

package hearthborn.timers;

import hearthborn.L10n;
import java.util.*;

/**
 * Countdown mutations and persistence; UI and production observers use the facade.
 *
 * @author Jagy2014
 */
final class TimerService {
    private static final List<TimerEntry> items = new ArrayList<>();
    private static int generation = 0;
    private static boolean loaded;

    static boolean add(String name, int hours, int minutes, int seconds) {
        load();
        long duration = ((hours * 60L + minutes) * 60L + seconds) * 1000L;
        if(duration <= 0)
            return false;
        String title = name == null ? "" : name.trim().replace('\n', ' ').replace('|', ' ').replace(';', ' ');
        if(title.length() > 48)
            title = title.substring(0, 48);
        if(title.isEmpty())
            title = L10n.tr("Timer");
        synchronized(items) {
            items.add(new TimerEntry(title, duration, System.currentTimeMillis() + duration));
            generation++;
            save();
        }
        return true;
    }

    static void remove(TimerEntry item) {
        synchronized(items) {
            if(items.remove(item)) {
                generation++;
                save();
            }
        }
    }

    static void load() {
        if(loaded)
            return;
        loaded = true;
        synchronized(items) {
            Object saved=hearthborn.io.LocalStore.read("timers-v2");
            if(saved instanceof java.util.List) {
                for(Object value:(java.util.List<?>)saved)if(value instanceof TimerEntry)items.add((TimerEntry)value);
                return;
            }

        }
    }

    private static void save() {
        hearthborn.io.LocalStore.write("timers-v2",new ArrayList<>(items));
    }

    /** One observed process owns one list entry; stable deadlines do not restart it. */
    public static void track(String source,String name,long deadline) {
        if(source==null || deadline<=System.currentTimeMillis())return;
        load();
        synchronized(items) {
            TimerEntry known=null;
            for(TimerEntry item:items)if(source.equals(item.source)){known=item;break;}
            if(known!=null && Math.abs(known.finish-deadline)<5000 && name.equals(known.name))return;
            if(known==null) {known=new TimerEntry(name,deadline-System.currentTimeMillis(),deadline);known.source=source;items.add(known);}
            else {known.name=name;known.duration=deadline-System.currentTimeMillis();known.finish=deadline;}
            generation++;save();
        }
    }

    /** Snapshot only the process identities requested by a world-state observer. */
    public static Set<String> sources(String prefix){
        load();Set<String> result=new HashSet<>();
        synchronized(items){for(TimerEntry item:items)if(item.source!=null&&item.source.startsWith(prefix))result.add(item.source);}
        return result;
    }

    public static long deadline(String source) {
        load();synchronized(items) {for(TimerEntry item:items)if(source.equals(item.source))return item.finish;}
        return 0;
    }
    public static void pause(String source) {
        load();
        synchronized(items) {
            for(TimerEntry item:items)if(source.equals(item.source) && item.running() && item.finish>System.currentTimeMillis()) {
                item.stop(System.currentTimeMillis());generation++;save();return;
            }
        }
    }

    public static void forget(String source) {
        load();
        synchronized(items) {
            if(items.removeIf(item -> source.equals(item.source))){generation++;save();}
        }
    }

    static List<TimerEntry> copy() {
        synchronized(items) {
            return new ArrayList<>(items);
        }
    }

    static int generation() { return generation; }

    static void namesChanged() { synchronized(items) { generation++; } }

    static void toggle(TimerEntry item,long now) {
        synchronized(items) {
            if(!items.contains(item))return;
            if(item.running())item.stop(now);else item.start(now);
            generation++;save();
        }
    }

    static List<TimerEntry> expire(long now) {
        load();
        List<TimerEntry> done=new ArrayList<>();
        synchronized(items) {
            for(Iterator<TimerEntry> it=items.iterator();it.hasNext();) {
                TimerEntry item=it.next();
                if(item.finish>0 && item.finish<=now) {done.add(item);it.remove();}
            }
            if(!done.isEmpty()) {generation++;save();}
        }
        return done;
    }
    private TimerService() {}
}
