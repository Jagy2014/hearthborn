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
import haven.Session;
import java.util.*;
/**
 * World labels supplied by the character list, retained across server handoffs.
 *
 * @author Jagy2014
 */
public final class WorldIdentity {
 private static final Map<Session,String> worlds=Collections.synchronizedMap(new WeakHashMap<>());
 public static void select(Session session,String world){if(session!=null){if(world==null||world.isBlank())worlds.remove(session);else worlds.put(session,world);}}
 public static String name(Session session){return session==null?null:worlds.get(session);}
 public static void transfer(Session from,Session to){select(to,name(from));}
}
