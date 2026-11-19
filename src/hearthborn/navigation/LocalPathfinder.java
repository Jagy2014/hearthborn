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

package hearthborn.navigation;
import hearthborn.world.ObjectGeometry;

import haven.*;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.*;

/**
 * Bounded local paths around the collision polygons of loaded objects.
 *
 * @author Jagy2014
 */
public final class LocalPathfinder {
    private static final double STEP = MCache.tilesz.x / 2;

    static boolean clear(Coord2d from, Coord2d to, List<Area> obstacles) {
        int samples = Math.max(1, (int)Math.ceil(from.dist(to) / 2));
        for(Area area : obstacles) {
            if(!area.getBounds2D().intersectsLine(from.x,from.y,to.x,to.y) && !area.contains(from.x,from.y))continue;
            boolean leaving=area.contains(from.x,from.y);
            if(leaving) {
                java.awt.geom.Rectangle2D bounds=area.getBounds2D();
                Coord2d center=new Coord2d(bounds.getCenterX(),bounds.getCenterY());
                if(to.dist(center)<=from.dist(center)+.001)return false;
            }
            // Point sampling alone can step over a thin fence or the corner of a polygon.
            if(!leaving&&crosses(from,to,area))return false;
            for(int i=1;i<=samples;i++) {
                Coord2d p=from.add(to.sub(from).mul((double)i/samples));
                boolean inside=area.contains(p.x,p.y);
                if(inside && !leaving)return false;
                if(!inside)leaving=false;
            }
        }
        return true;
    }

    private static boolean crosses(Coord2d from,Coord2d to,Area area){
        PathIterator edges=area.getPathIterator(null,.05);
        double[] point=new double[6];double firstX=0,firstY=0,lastX=0,lastY=0;
        while(!edges.isDone()){
            int type=edges.currentSegment(point);
            if(type==PathIterator.SEG_MOVETO){firstX=lastX=point[0];firstY=lastY=point[1];}
            else{
                double x=type==PathIterator.SEG_CLOSE?firstX:point[0],y=type==PathIterator.SEG_CLOSE?firstY:point[1];
                if(Line2D.linesIntersect(from.x,from.y,to.x,to.y,lastX,lastY,x,y)
                        &&Line2D.ptSegDist(lastX,lastY,x,y,from.x,from.y)>1e-7)return true;
                lastX=x;lastY=y;
            }
            edges.next();
        }
        return false;
    }

    private static boolean approach(Coord2d from, Coord2d goal, List<Area> obstacles) {
        List<Area> neighbors = new ArrayList<>();
        for(Area area : obstacles) if(!area.contains(goal.x, goal.y)) neighbors.add(area);
        return clear(from, goal, neighbors);
    }

    private static class Node {
        final Coord grid;
        final Coord2d point;
        final Node parent;
        final double cost, score;
        Node(Coord grid, Coord2d point, Node parent, double cost, Coord2d goal) {
            this.grid = grid; this.point = point; this.parent = parent; this.cost = cost;
            score = cost + Math.max(0, point.dist(goal) - MCache.tilesz.x);
        }
    }

    public static List<Coord2d> route(Coord2d start, Coord2d goal, List<Area> obstacles) {
        return route(start,goal,obstacles,true);
    }
    /** Exact destinations for walking; interactions keep their approach distance. */
    public static List<Coord2d> route(Coord2d start, Coord2d goal, List<Area> obstacles,boolean interaction) {
        if(!interaction && clear(start,goal,obstacles))return new ArrayList<>(Collections.singletonList(goal));
        double margin = 8 * MCache.tilesz.x;
        double minX = Math.min(start.x, goal.x) - margin, maxX = Math.max(start.x, goal.x) + margin;
        double minY = Math.min(start.y, goal.y) - margin, maxY = Math.max(start.y, goal.y) + margin;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.score));
        Map<Coord, Double> costs = new HashMap<>();
        open.add(new Node(Coord.z, start, null, 0, goal)); costs.put(Coord.z, 0.0);
        int visited = 0;
        while(!open.isEmpty() && !Thread.currentThread().isInterrupted() && visited++ < 20000) {
            Node node = open.remove();
            if(node.cost > costs.get(node.grid)) continue;
            if((interaction?node.point.dist(goal)<=MCache.tilesz.x && approach(node.point,goal,obstacles):clear(node.point,goal,obstacles))) {
                LinkedList<Coord2d> path = new LinkedList<>();
                for(Node n = node; n.parent != null; n = n.parent) path.addFirst(n.point);
                List<Coord2d> result = new ArrayList<>();
                Coord2d at = start;
                for(int i = 0; i < path.size();) {
                    int end = i;
                    while(end + 1 < path.size() && clear(at, path.get(end + 1), obstacles)) end++;
                    at = path.get(end); result.add(at); i = end + 1;
                }
                if(!interaction)result.add(goal);
                return result;
            }
            for(int y = -1; y <= 1; y++) for(int x = -1; x <= 1; x++) {
                if(x == 0 && y == 0) continue;
                Coord grid = node.grid.add(x, y);
                Coord2d point = start.add(grid.x * STEP, grid.y * STEP);
                if(point.x < minX || point.x > maxX || point.y < minY || point.y > maxY) continue;
                double cost = node.cost + node.point.dist(point);
                if(cost >= costs.getOrDefault(grid, Double.POSITIVE_INFINITY) || !clear(node.point, point, obstacles)) continue;
                costs.put(grid, cost); open.add(new Node(grid, point, node, cost, goal));
            }
        }
        return null;
    }

    private LocalPathfinder() {}
}
