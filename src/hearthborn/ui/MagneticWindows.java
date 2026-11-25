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
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * Magnets for the minimap, chat, quest journal, screen edges, and extra button panels.
 *
 * @author Jagy2014
 */
public final class MagneticWindows {
    enum Kind { MAP, CHAT, QUESTS, PANEL, WINDOW }
    private static final Map<Widget,Kind> kinds=new WeakHashMap<>();
    private static final Map<Widget,WeakReference<Widget>> above=new WeakHashMap<>();
    private static final Map<Widget,Drag> drags=new WeakHashMap<>();
    private static final Map<Widget,Link[]> links=new WeakHashMap<>();
    private static final Map<Widget,Integer> fittedWidths=new WeakHashMap<>();
    private static final Set<Widget> restored=Collections.newSetFromMap(new WeakHashMap<>());
    private static final String DOCK="hearthborn/ui/window-dock/";
    private static final int GAP=UI.scale(2),DISTANCE=UI.scale(16);
    private static boolean raisingStack;
    private static class Drag {
        final Coord start;boolean moved;Widget candidate;Link[] links;
        Drag(Widget w){start=w.c;}
    }
    private static class Link {
        final WeakReference<Widget> target;
        final int ownEdge,targetEdge,gap;
        Link(Widget target,int ownEdge,int targetEdge,int gap){this.target=new WeakReference<>(target);this.ownEdge=ownEdge;this.targetEdge=targetEdge;this.gap=gap;}
        int position(Widget widget,int axis){
            Widget other=target.get();
            if(other==null)return axis==0?widget.c.x:widget.c.y;
            int origin=other==widget.parent?0:axis==0?edge(other).x:edge(other).y;
            int length=axis==0?span(other).x:span(other).y,own=axis==0?span(widget).x:span(widget).y;
            if(other==widget.parent&&axis==0&&ownEdge==0&&targetEdge==0)
                return Math.min(gap,Math.max(0,length-own));
            // Screen magnets keep the full caption visible, unlike sibling frame joins.
            if(other==widget.parent&&axis==1&&ownEdge==0&&targetEdge==0)
                return frameOffset(widget).y+gap;
            return origin+targetEdge*length-ownEdge*own+gap;
        }
    }
    private MagneticWindows(){}
    static void register(Widget widget,Kind kind){if(widget!=null)kinds.put(widget,kind);}
    private static Widget parent(Widget widget){WeakReference<Widget> ref=above.get(widget);return ref==null?null:ref.get();}
    private static boolean usable(Widget widget){
        return widget!=null&&kinds.containsKey(widget)&&widget.parent instanceof GameUI&&widget.visible()&&widget.c.x>-10000
            &&(!(widget instanceof MapWnd)||(((MapWnd)widget).deco==null&&!((MapWnd)widget).frameBoosted));
    }
    private static final String ENABLED="hearthborn/ui/magnets";
    public static boolean enabled(){return Utils.getprefb(ENABLED,true);}
    public static void enabled(boolean on){
        Utils.setprefb(ENABLED,on);
        if(!on)for(Widget widget:new ArrayList<>(kinds.keySet()))joinedHeader(widget,false);
    }
    public static void tick(GameUI gui){
        if(!enabled())return;
        if(gui.compactUI())return; // Temporary map placement must not alter attachment links.
        recognize(gui);
        restoreDocking(gui);
        for(Widget child=gui.child;child!=null;child=child.next)
            if(child instanceof Toolbars.Bar||child instanceof GameUI.NKeyBelt||child instanceof ActionStrip||child instanceof Toolbars.Row)register(child,Kind.PANEL);
        for(Widget child:new ArrayList<>(above.keySet())){
            Widget top=parent(child);
            if(top==null||child.parent!=top.parent||!kinds.containsKey(child)||!kinds.containsKey(top)){above.remove(child);continue;}
        }
        // At most three nodes: two passes resolve the full chain regardless of map order.
        for(int pass=0;pass<2;pass++)for(Widget child:new ArrayList<>(above.keySet())){
            Widget top=parent(child);if(top!=null&&!drags.containsKey(child))place(child,top);
        }
        followLinks(gui);
        watch(gui);
        separate(gui);
        syncHeaders(gui);
    }
    /** Seconds a panel must stay where it is before it can be pulled off another one; its layout may not have run yet. */
    static double steadyFor=2;
    private static final Map<Widget,double[]> steady=new WeakHashMap<>();
    private static void watch(GameUI gui){
        double now=Utils.rtime();
        for(Widget child=gui.child;child!=null;child=child.next){
            if(kinds.get(child)!=Kind.PANEL)continue;
            double[] seen=steady.get(child);
            if(seen==null||seen[0]!=child.c.x||seen[1]!=child.c.y)steady.put(child,new double[]{child.c.x,child.c.y,now});
        }
    }
    private static boolean steady(Widget widget){
        double[] seen=steady.get(widget);
        return seen==null?steadyFor<=0:Utils.rtime()-seen[2]>=steadyFor;
    }
    public static void begin(Widget widget){
        if(!enabled()||widget==null)return;
        if(widget instanceof Toolbars.Bar||widget instanceof GameUI.NKeyBelt||widget instanceof ActionStrip||widget instanceof Toolbars.Row)register(widget,Kind.PANEL);
        else if(widget instanceof MapWnd)noteMap((MapWnd)widget);
        else if(widget.parent instanceof GameUI)recognize((GameUI)widget.parent);
        if(magnetic(widget)&&usable(widget))drags.put(widget,new Drag(widget));
    }
    /** The large map and ordinary windows are not magnetic. Extra button panels are. */
    private static void recognize(GameUI gui){
        noteMap(gui.mapfile);
        register(gui.questHelper,Kind.QUESTS);
        if(gui.chat==null)return;
        if(gui.chat.detached())register(gui.chat.getparent(Window.class),Kind.CHAT);
        else if(gui.chat.parent==gui)register(gui.chat,Kind.CHAT);
    }
    private static void noteMap(MapWnd map){
        if(map==null)return;
        if(map.deco==null)register(map,Kind.MAP);
        else kinds.remove(map);
    }
    private static boolean magnetic(Widget widget){
        Kind kind=kinds.get(widget);
        if(kind==Kind.CHAT||kind==Kind.QUESTS)return true;
        if(kind==Kind.MAP)return !(widget instanceof MapWnd)||((MapWnd)widget).deco==null;
        if(kind==Kind.PANEL)return true;
        return false;
    }
    /** Minimap, chat and the journal stick to one another. Button panels stick only to button panels. */
    private static boolean snapsTo(Widget widget,Widget other){
        Kind a=kinds.get(widget),b=kinds.get(other);
        if(a==null||b==null)return false;
        if(a==Kind.PANEL||b==Kind.PANEL)return a==Kind.PANEL&&b==Kind.PANEL;
        return stackKind(widget)&&stackKind(other);
    }
    /** Explicit chat docking takes precedence over a previous free-floating magnet. */
    public static void detach(Widget widget){above.remove(widget);links.remove(widget);drags.remove(widget);joinedHeader(widget,false);}
    public static boolean attached(Widget widget){return parent(widget)!=null||links.containsKey(widget);}
    /** Explicit resizing detaches persistently; automatic screen fitting must not forget docking. */
    public static void forget(Widget widget){detach(widget);restored.add(widget);saveDocking(widget);}

    /** The bottom window's corners resize the whole stack, without changing its links. */
    public static StackResize resizeStack(Window bottom,boolean left){
        if(!enabled()||!usable(bottom)||!stackKind(bottom)||parent(bottom)==null)return null;
        for(Widget child:above.keySet())if(parent(child)==bottom&&usable(child))return null;
        List<Window> windows=new ArrayList<>();
        for(Widget item=bottom;item!=null;item=parent(item)){
            if(!(item instanceof Window)||!usable(item)||!stackKind(item)||windows.contains(item))return null;
            windows.add((Window)item);
        }
        Collections.reverse(windows);
        return new StackResize(windows,left);
    }

    public static final class StackResize {
        private final List<Window> windows;
        private final List<Coord> sizes=new ArrayList<>();
        private final Coord origin;
        private final int width,height,bodyHeight;
        private final boolean left;

        private StackResize(List<Window> windows,boolean left){
            this.windows=windows;
            this.left=left;
            Window root=windows.get(0),bottom=windows.get(windows.size()-1);
            origin=new Coord(edge(root));
            width=span(root).x;
            height=edge(bottom).y+span(bottom).y-origin.y;
            int total=0;
            for(Window window:windows){
                Coord size=HudInterfaceScale.toParent(window,window.csz());
                sizes.add(size);
                total+=size.y;
            }
            bodyHeight=total;
        }

        public void resize(Coord delta){
            Window root=windows.get(0);
            int limit=left?origin.x+width:root.parent.sz.x-origin.x;
            int targetWidth=Math.max(1,Math.min(limit,width+(left?-delta.x:delta.x)));
            int dh=Math.min(delta.y,Math.max(0,root.parent.sz.y-origin.y-height));
            int used=0, apportioned=0, actualWidth=targetWidth;
            for(int index=0;index<windows.size();index++){
                Window window=windows.get(index);
                Coord before=sizes.get(index);
                used+=before.y;
                int share=(int)Math.round((double)dh*used/Math.max(1,bodyHeight));
                resizeWindow(window,targetWidth,before.y+share-apportioned);
                apportioned=share;
                actualWidth=Math.max(actualWidth,span(window).x);
            }
            // If one window reached its minimum width, match it instead of breaking the seam.
            for(Window window:windows)
                if(span(window).x!=actualWidth)
                    resizeWindow(window,actualWidth,HudInterfaceScale.toParent(window,window.csz()).y);
            moveWidget(root,Coord.of(left?origin.x+width-span(root).x:origin.x,origin.y));
            for(int index=1;index<windows.size();index++)
                place(windows.get(index),windows.get(index-1));
        }

        private static void resizeWindow(Window window,int outerWidth,int contentHeight){
            Coord size=HudInterfaceScale.fromParent(window,Coord.of(outerWidth,contentHeight));
            int trim=window.sz.x-window.csz().x;
            window.resize(Coord.of(Math.max(1,size.x-trim),Math.max(1,size.y)));
        }

        public void finish(){for(Window window:windows)remember(window);}
    }

    /** Leave this window and the one stuck under it where they are. A chain below that stays together. */
    public static void release(Widget widget){
        detach(widget);
        if(widget==null)return;
        for(Widget child:new ArrayList<>(above.keySet()))
            if(parent(child)==widget){above.remove(child);saveDocking(child);}
        for(Widget other:new ArrayList<>(links.keySet())){
            Link[] attached=links.get(other);
            if(attached==null)continue;
            boolean changed=false;
            for(int i=0;i<attached.length;i++)
                if(attached[i]!=null&&attached[i].target.get()==widget){attached[i]=null;changed=true;}
            if(!changed)continue;
            boolean any=false;
            for(Link link:attached)if(link!=null)any=true;
            if(!any)links.remove(other);
            saveDocking(other);
        }
        saveDocking(widget);
    }
    private static final Map<Widget,List<Hold>> held=new WeakHashMap<>();
    private static final class Hold {
        final Widget owner,above;final Link[] links;
        Hold(Widget owner,Widget above,Link[] links){this.owner=owner;this.above=above;this.links=links;}
    }
    /** Drop the map's magnets while the large map is open, and keep them for the minimap. */
    public static void hold(Widget widget){
        if(widget==null||held.containsKey(widget))return;
        List<Hold> saved=new ArrayList<Hold>();
        saved.add(new Hold(widget,parent(widget),copy(links.get(widget))));
        above.remove(widget);links.remove(widget);
        for(Widget child:new ArrayList<Widget>(above.keySet())){
            if(parent(child)!=widget)continue;
            saved.add(new Hold(child,widget,null));
            above.remove(child);
        }
        for(Widget other:new ArrayList<Widget>(links.keySet())){
            Link[] attached=links.get(other);if(attached==null)continue;
            boolean hits=false;for(Link link:attached)if(link!=null&&link.target.get()==widget)hits=true;
            if(!hits)continue;
            saved.add(new Hold(other,null,copy(attached)));
            links.remove(other);
        }
        held.put(widget,saved);
    }
    /** A map that opens on top of chat or the journal keeps its place. The covered window sticks under the map. */
    public static void uncover(Widget map){
        if(!enabled()||map==null||!(map.parent instanceof GameUI)||((GameUI)map.parent).compactUI())return;
        if(map instanceof MapWnd&&((MapWnd)map).deco!=null)return;
        GameUI gui=(GameUI)map.parent;
        recognize(gui);
        if(!usable(map)||kinds.get(map)!=Kind.MAP)return;
        List<Widget> covered=new ArrayList<Widget>();
        for(Widget other:new ArrayList<Widget>(kinds.keySet())){
            if(other==map||!stackKind(other)||!usable(other)||other.parent!=map.parent||drags.containsKey(other))continue;
            if(reaches(map,other,Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>())))continue;
            if(reaches(other,map,Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>())))continue;
            if(covers(map,other))covered.add(other);
        }
        Collections.sort(covered,new Comparator<Widget>(){public int compare(Widget a,Widget b){return a.c.y-b.c.y;}});
        Widget tail=stackBottom(map);
        for(Widget other:covered){
            if(!compatible(other,tail))continue;
            above.remove(other);links.remove(other);
            fitWidth(other,span(tail).x);
            above.put(other,new WeakReference<Widget>(tail));
            place(other,tail);
            tail=other;
        }
        follow(map,true);followLinks(map.parent);
    }
    private static boolean covers(Widget top,Widget under){
        Coord a=edge(top),b=edge(under),as=span(top),bs=span(under);
        int overlapX=Math.min(a.x+as.x,b.x+bs.x)-Math.max(a.x,b.x);
        int overlapY=Math.min(a.y+as.y,b.y+bs.y)-Math.max(a.y,b.y);
        return overlapX>=Math.min(as.x,bs.x)/2&&overlapY>GAP;
    }
    private static Widget stackBottom(Widget map){
        Widget node=map;
        for(;;){
            Widget next=null;
            for(Widget child:above.keySet())if(parent(child)==node)next=child;
            if(next==null)return node;
            node=next;
        }
    }
    /** Put the held magnets back and let them place the widget immediately. */
    public static void resume(Widget widget){
        List<Hold> saved=held.remove(widget);if(saved==null||widget==null)return;
        if(widget instanceof MapWnd)noteMap((MapWnd)widget);
        detach(widget);
        for(Widget child:new ArrayList<Widget>(above.keySet()))
            if(parent(child)==widget)above.remove(child);
        for(Hold hold:saved){
            if(hold.owner==null||hold.owner.parent==null)continue;
            if(hold.above!=null)above.put(hold.owner,new WeakReference<Widget>(hold.above));
            if(hold.links!=null)links.put(hold.owner,hold.links);
        }
        if(widget.parent!=null&&enabled())followLinks(widget.parent);
    }
    private static Link[] copy(Link[] attached){return attached==null?null:attached.clone();}
    public static void drag(Widget widget){
        if(!enabled())return;
        Drag drag=drags.get(widget);if(drag==null)return;
        if(!drag.moved&&widget.c.dist(drag.start)<UI.scale(4))return;
        if(!drag.moved){
            drag.moved=true;
            above.remove(widget);
            links.remove(widget);
            fittedWidths.remove(widget);
        }
        drag.candidate=candidate(widget);
        if(drag.candidate!=null)place(widget,drag.candidate);
        else{joinedHeader(widget,false);drag.links=snap(widget);apply(widget,drag.links);}
        follow(widget,false);
        followLinks(widget.parent);
    }
    public static void end(Widget widget){
        if(!enabled()){drags.remove(widget);return;}
        Drag drag=drags.remove(widget);if(drag==null||!drag.moved)return;
        Widget top=drag.candidate;
        if(top!=null&&usable(top)&&compatible(widget,top)){
            fitWidth(widget,span(top).x);above.put(widget,new WeakReference<>(top));place(widget,top);
        }else if(drag.links!=null)links.put(widget,drag.links);
        restored.add(widget);follow(widget,true);
        if(slide(widget))links.remove(widget);
        remember(widget);
        followLinks(widget.parent);
        for(Widget child:new ArrayList<>(links.keySet()))
            if(child!=widget&&reaches(child,widget,Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>())))remember(child);
    }
    /** True when the two rectangles share any area; panels that only touch do not overlap. */
    static boolean overlap(Coord at,Coord size,Coord otherAt,Coord otherSize){
        return at.x<otherAt.x+otherSize.x&&otherAt.x<at.x+size.x&&at.y<otherAt.y+otherSize.y&&otherAt.y<at.y+size.y;
    }
    private static long shared(Coord at,Coord size,Coord otherAt,Coord otherSize){
        long w=Math.min(at.x+size.x,otherAt.x+otherSize.x)-Math.max(at.x,otherAt.x);
        long h=Math.min(at.y+size.y,otherAt.y+otherSize.y)-Math.max(at.y,otherAt.y);
        return w<=0||h<=0?0:w*h;
    }
    /**
     * Where a panel stands when it is pushed off another one: the nearest of the four sides, a gap away from it and inside the
     * screen. When no side fits the panel is put to the right of the other one as far as the screen allows.
     */
    static Coord clear(Coord at,Coord size,Coord otherAt,Coord otherSize,Coord room){
        Coord[] sides={
            Coord.of(otherAt.x+otherSize.x+GAP,at.y),Coord.of(otherAt.x-size.x-GAP,at.y),
            Coord.of(at.x,otherAt.y+otherSize.y+GAP),Coord.of(at.x,otherAt.y-size.y-GAP)};
        Coord best=null;int score=Integer.MAX_VALUE;
        for(Coord side:sides){
            if(side.x<0||side.y<0||side.x>room.x||side.y>room.y)continue;
            int distance=Math.abs(side.x-at.x)+Math.abs(side.y-at.y);
            if(distance<score){score=distance;best=side;}
        }
        return best!=null?best:Coord.of(Utils.clip(sides[0].x,0,room.x),Utils.clip(sides[0].y,0,room.y));
    }
    private static List<Widget> panels(Widget widget){
        List<Widget> result=new ArrayList<>();
        for(Widget other:new ArrayList<>(kinds.keySet()))
            if(other!=widget&&kinds.get(other)==Kind.PANEL&&usable(other)&&other.parent==widget.parent&&!drags.containsKey(other))result.add(other);
        return result;
    }
    /** Button panels never lie on top of one another: a panel dropped on another slides off it to the nearest free side. */
    private static boolean slide(Widget widget){
        if(kinds.get(widget)!=Kind.PANEL||widget.parent==null||widget.parent.sz==null)return false;
        boolean slid=false;
        for(int pass=0;pass<4;pass++){
            Widget under=null;
            for(Widget other:panels(widget))if(overlap(edge(widget),span(widget),edge(other),span(other))){under=other;break;}
            if(under==null)break;
            moveWidget(widget,clear(edge(widget),span(widget),edge(under),span(under),widget.parent.sz.sub(span(widget)).max(Coord.z)));
            slid=true;
        }
        return slid;
    }
    /** A panel the player has placed by hand keeps that place; one still at its default spot is arranged by its own layout. */
    private static boolean placed(Widget widget){
        if(widget instanceof Toolbars.Row)return ((Toolbars.Row)widget).moved();
        if(widget instanceof Toolbars.Bar)return ((Toolbars.Bar)widget).floating();
        if(widget instanceof GameUI.NKeyBelt)return Toolbars.stripMoved();
        return false;
    }
    private static void settle(Widget widget){
        if(widget instanceof Toolbars.Row)((Toolbars.Row)widget).relocate(widget.c);
        else if(widget instanceof Toolbars.Bar)((Toolbars.Bar)widget).rememberPosition();
        else if(widget instanceof GameUI.NKeyBelt){((GameUI.NKeyBelt)widget).rememberPosition();Toolbars.stripMoved(true);}
    }
    private static double separated=-10;
    /**
     * Panels saved on top of each other (a strip dropped exactly over another one in an earlier session) are pulled apart once
     * a second. The one painted underneath moves, unless the player has not placed it by hand; then the other one does.
     */
    private static void separate(GameUI gui){
        if(!drags.isEmpty())return;
        double now=Utils.rtime();if(now<separated+1)return;separated=now;
        pullApartAll(gui);
    }
    static void pullApartAll(GameUI gui){for(int pass=0;pass<8&&pullApart(gui);pass++);}
    /** Moves one panel off another it lies on; false when no such pair is left. */
    private static boolean pullApart(GameUI gui){
        List<Widget> stack=new ArrayList<>();
        for(Widget child=gui.child;child!=null;child=child.next)if(kinds.get(child)==Kind.PANEL&&usable(child))stack.add(child);
        for(int i=0;i<stack.size();i++)for(int j=i+1;j<stack.size();j++){
            Widget under=stack.get(i),over=stack.get(j);
            // Panels not laid out yet all stand at the corner: that is no overlap to repair, and it must never be saved as a place.
            if(!steady(under)||!steady(over)||under.c.equals(Coord.z)||over.c.equals(Coord.z))continue;
            Coord a=edge(under),sa=span(under),b=edge(over),sb=span(over);
            long common=shared(a,sa,b,sb);
            // Panels with no area (not sized yet) or only touching do not overlap.
            if(common==0||common*2<Math.min((long)sa.x*sa.y,(long)sb.x*sb.y))continue;
            Widget victim=placed(under)?under:placed(over)?over:null;
            if(victim==null)continue;
            Widget other=victim==under?over:under;
            moveWidget(victim,clear(edge(victim),span(victim),edge(other),span(other),gui.sz.sub(span(victim)).max(Coord.z)));
            links.remove(victim);settle(victim);
            return true;
        }
        return false;
    }
    private static Widget candidate(Widget widget){
        if(kinds.get(widget)==Kind.PANEL||kinds.get(widget)==Kind.WINDOW)return null;
        Widget best=null;int score=Integer.MAX_VALUE;
        for(Widget top:new ArrayList<>(kinds.keySet())){
            if(!usable(top)||top==widget||top.parent!=widget.parent||drags.containsKey(top)||kinds.get(top)==Kind.PANEL||kinds.get(top)==Kind.WINDOW||!compatible(widget,top))continue;
            Coord at=edge(widget),topAt=edge(top);
            int dy=Math.abs(at.y-(topAt.y+span(top).y+stackGap(widget,top)));
            int overlap=Math.min(at.x+span(widget).x,topAt.x+span(top).x)-Math.max(at.x,topAt.x);
            if(dy>DISTANCE||overlap<Math.min(span(widget).x,span(top).x)/2)continue;
            int distance=dy*4+Math.abs(at.x-topAt.x);if(distance<score){score=distance;best=top;}
        }
        return best;
    }
    private static boolean reaches(Widget start,Widget goal,Set<Widget> seen){return reaches(start,goal,seen,false);}
    /** A toolbar between the map and the journal is not a connection between those windows. */
    private static boolean stackBlocked(Widget start,Widget goal){return reaches(start,goal,Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>()),true);}
    private static boolean stackKind(Widget widget){Kind kind=kinds.get(widget);return kind==Kind.MAP||kind==Kind.CHAT||kind==Kind.QUESTS;}
    private static boolean plain(Widget widget){return widget instanceof Window&&((Window)widget).deco instanceof PlainDeco;}
    private static boolean framed(Widget widget){
        return widget instanceof Window&&(((Window)widget).deco instanceof SmallWindowDeco
            ||((Window)widget).deco instanceof PlainDeco
            ||widget instanceof MapWnd&&((MapWnd)widget).deco==null);
    }
    /** Native framed HUD windows share one separator; unframed windows/strips retain their gap. */
    public static int stackGap(Widget child,Widget top){
        // A frameless chat or journal has no moulding, so its width is a frame's thickness off the framed window it meets.
        int slack=plain(child)||plain(top)?UI.scale(12):1;
        return stackKind(child)&&stackKind(top)&&framed(child)&&framed(top)&&Math.abs(span(child).x-span(top).x)<=slack?0:GAP;
    }
    private static void joinedHeader(Widget widget,boolean joined){
        if(widget instanceof Window&&((Window)widget).deco instanceof SmallWindowDeco)
            ((SmallWindowDeco)((Window)widget).deco).joinedHeader(joined);
    }
    private static boolean joined(Widget child,Widget top){
        if(!enabled()||!usable(child)||!usable(top)||child.parent!=top.parent||stackGap(child,top)!=0)return false;
        Coord a=edge(child),b=edge(top);
        return Math.abs(a.x-b.x)<=1&&Math.abs(a.y-b.y-span(top).y)<=1;
    }
    private static Widget previewOrParent(Widget widget){
        Widget top=parent(widget);
        if(top==null){Drag drag=drags.get(widget);if(drag!=null)top=drag.candidate;}
        return top;
    }
    public static boolean joinedAbove(Widget widget){
        Widget top=previewOrParent(widget);
        return top!=null&&joined(widget,top);
    }
    public static boolean joinedBelow(Widget widget){
        for(Widget child:new ArrayList<>(above.keySet()))if(parent(child)==widget&&joined(child,widget))return true;
        for(Map.Entry<Widget,Drag> entry:drags.entrySet())if(entry.getValue().candidate==widget&&joined(entry.getKey(),widget))return true;
        return false;
    }
    private static void syncHeaders(GameUI gui){
        for(Widget widget:new ArrayList<>(kinds.keySet()))if(widget.parent==gui){
            Widget top=previewOrParent(widget);
            joinedHeader(widget,top!=null&&usable(widget)&&usable(top)&&stackGap(widget,top)==0);
        }
    }
    /** Raise a complete stack in top-to-bottom paint order, without changing keyboard focus. */
    public static boolean raiseStack(Window window){
        if(raisingStack||!enabled()||!(window.parent instanceof GameUI)||!stackKind(window))return false;
        Widget root=window;Set<Widget> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(parent(root)!=null&&usable(parent(root))&&seen.add(root))root=parent(root);
        List<Widget> group=new ArrayList<>();
        for(Widget member:descendants(root))if(member.parent==window.parent&&usable(member))group.add(member);
        if(group.size()<2)return false;
        group.sort(Comparator.comparingInt(member->edge(member).y));
        raisingStack=true;
        try{for(Widget member:group)member.raise();}finally{raisingStack=false;}
        return true;
    }
    private static boolean blocked(Widget other,Widget widget){
        boolean stacking=stackKind(other)&&stackKind(widget);
        return stacking?stackBlocked(other,widget):reaches(other,widget,Collections.newSetFromMap(new IdentityHashMap<Widget,Boolean>()));
    }
    private static boolean reaches(Widget start,Widget goal,Set<Widget> seen,boolean stacking){
        if(start==goal)return true;
        if(start==null||!seen.add(start))return false;
        if(reaches(parent(start),goal,seen,stacking))return true;
        Link[] attached=links.get(start);
        if(attached!=null)for(Link link:attached){
            if(link==null)continue;
            Widget next=link.target.get();
            if(stacking&&kinds.get(next)==Kind.PANEL)continue;
            if(reaches(next,goal,seen,stacking))return true;
        }
        return false;
    }
    private static Link[] snap(Widget widget){
        Link[] result=new Link[2];int[] distance={DISTANCE+1,DISTANCE+1};
        for(int axis=0;axis<2;axis++)for(int edge=0;edge<2;edge++)
            offer(widget,axis,new Link(widget.parent,edge,edge,axis==0&&edge==0?leftInset(widget):0),result,distance);
        boolean atLeft=result[0]!=null&&result[0].ownEdge==0;
        for(Widget other:new ArrayList<>(kinds.keySet())){
            if(other==widget||!snapsTo(widget,other)||!usable(other)||other.parent!=widget.parent||drags.containsKey(other)||blocked(other,widget))continue;
            for(int axis=0;axis<2;axis++){
                Coord wa=edge(widget),oa=edge(other);
                int a=axis==0?wa.y:wa.x,b=axis==0?oa.y:oa.x;
                int length=axis==0?span(widget).y:span(widget).x,otherLength=axis==0?span(other).y:span(other).x;
                if(a>b+otherLength+DISTANCE||b>a+length+DISTANCE)continue;
                offerEdges(widget,other,axis,a,b,length,otherLength,atLeft,result,distance);
            }
        }
        return result;
    }
    private static void offerEdges(Widget widget,Widget other,int axis,int a,int b,int length,int otherLength,boolean atLeft,Link[] result,int[] distance){
        offer(widget,axis,new Link(other,0,1,GAP),result,distance);
        offer(widget,axis,new Link(other,1,0,-GAP),result,distance);
        // Align parallel edges only when the panels are nearly adjacent on the other axis.
        // Prefer the left screen gutter to parallel alignment with an older panel at x=0.
        if((axis!=0||!atLeft)&&Math.min(Math.abs(a-(b+otherLength+GAP)),Math.abs(b-(a+length+GAP)))<=DISTANCE){
            offer(widget,axis,new Link(other,0,0,0),result,distance);
            offer(widget,axis,new Link(other,1,1,0),result,distance);
        }
    }
    private static Coord frameOffset(Widget widget){
        if(widget instanceof Window&&((Window)widget).deco instanceof SmallWindowDeco)
            return HudInterfaceScale.toParent(widget,Coord.of(0,((SmallWindowDeco)((Window)widget).deco).frameTop()));
        return Coord.z;
    }
    private static Coord edge(Widget widget){return widget instanceof ActionStrip?((ActionStrip)widget).columnAt():widget.c.add(frameOffset(widget));}
    private static void moveWidget(Widget widget,Coord at){
        at=at.sub(frameOffset(widget));
        if(widget instanceof ActionStrip)((ActionStrip)widget).moveColumn(at);
        else if(widget instanceof QuestHelper)((QuestHelper)widget).magneticMove(at);
        else widget.move(at);
        if(id(widget)!=null&&widget.parent.sz.x>=UI.scale(200)&&widget.parent.sz.y>=UI.scale(200)){
            Coord room=widget.parent.sz.sub(HudInterfaceScale.size(widget)).max(Coord.z);
            widget.move(Coord.of(Utils.clip(at.x,0,room.x),Utils.clip(at.y,0,room.y)));
        }
    }
    /** The experimental menu's opened rows stay out of the magnet, so unfolding it leaves other panels where they are. */
    private static Coord span(Widget widget){
        if(widget instanceof ActionStrip)return ((ActionStrip)widget).columnSize();
        return HudInterfaceScale.size(widget).sub(frameOffset(widget));
    }
    private static int leftInset(Widget widget){
        GameUI gui=(GameUI)widget.parent;
        return gui.portrait==null||PortraitSide.onRight()?UI.scale(10):gui.portrait.c.x;
    }
    private static void offer(Widget widget,int axis,Link link,Link[] result,int[] distance){
        int delta=Math.abs(link.position(widget,axis)-(axis==0?edge(widget).x:edge(widget).y));
        if(delta<distance[axis]){result[axis]=link;distance[axis]=delta;}
    }
    private static void apply(Widget widget,Link[] attached){
        if(attached==null)return;
        Coord at=new Coord(edge(widget));
        for(int axis=0;axis<2;axis++)if(attached[axis]!=null){
            Widget target=attached[axis].target.get();
            if(target==null){attached[axis]=null;continue;}
            boolean cross=target instanceof SystemCross&&target.visible()&&target.parent==widget.parent;
            if(target!=widget.parent&&((!usable(target)&&!cross)||target.parent!=widget.parent)){
                if(target.parent==widget.parent&&id(target)!=null)continue; // A hidden HUD window can reopen later.
                attached[axis]=null;continue;
            }
            int position=attached[axis].position(widget,axis);if(axis==0)at.x=position;else at.y=position;
        }
        moveWidget(widget,at);
    }
    private static void followLinks(Widget root){
        for(int pass=0;pass<=links.size()+above.size();pass++){
            for(Widget child:new ArrayList<>(links.keySet()))
                if(child.parent==root&&usable(child)&&!drags.containsKey(child))apply(child,links.get(child));
            for(Widget child:new ArrayList<>(above.keySet())){
                Widget top=parent(child);
                if(child.parent==root&&usable(child)&&usable(top)&&!drags.containsKey(child))place(child,top);
            }
        }
    }
    private static boolean compatible(Widget child,Widget top){
        if(stackBlocked(top,child))return false;
        for(Widget attached:new ArrayList<>(above.keySet()))if(attached!=child&&parent(attached)==top)return false;
        Set<Widget> visited=Collections.newSetFromMap(new IdentityHashMap<>());EnumSet<Kind> used=EnumSet.noneOf(Kind.class);
        for(Widget node=top;node!=null;node=parent(node)){
            if(node==child||!visited.add(node)||kinds.get(node)==null||!used.add(kinds.get(node)))return false;
        }
        for(Widget node:descendants(child))if(!visited.add(node)||kinds.get(node)==null||!used.add(kinds.get(node)))return false;
        // Each eligible window type can occur only once in a stack.
        return used.size()<=3;
    }
    private static Set<Widget> descendants(Widget root){
        Set<Widget> result=Collections.newSetFromMap(new IdentityHashMap<>());result.add(root);
        for(int pass=0;pass<2;pass++)for(Widget child:new ArrayList<>(above.keySet()))if(result.contains(parent(child)))result.add(child);
        return result;
    }
    private static void follow(Widget root,boolean save){
        Set<Widget> linked=descendants(root);
        for(int pass=0;pass<2;pass++)for(Widget child:linked){Widget top=parent(child);if(top!=null&&!drags.containsKey(child)){place(child,top);if(save)remember(child);}}
    }
    private static void place(Widget child,Widget top){
        int width=span(top).x;
        if(span(child).x!=width&&!Objects.equals(fittedWidths.get(child),width)){
            fittedWidths.put(child,width);fitWidth(child,width);
        }
        int gap=stackGap(child,top);
        joinedHeader(child,gap==0);
        Coord topAt=edge(top),at=Coord.of(topAt.x,topAt.y+span(top).y+gap);
        if(edge(child).equals(at))return;
        moveWidget(child,at);
    }
    private static void fitWidth(Widget widget,int width){
        if(widget instanceof QuestHelper){((QuestHelper)widget).magneticWidth(width);return;}
        if(!(widget instanceof Window))return;
        Window window=(Window)widget;Coord before=window.csz();int trim=window.sz.x-before.x;
        int nativeWidth=HudInterfaceScale.fromParent(window,Coord.of(width,0)).x;
        window.resize(Coord.of(Math.max(1,nativeWidth-trim),before.y));
        // Respect the window's own minimum width rather than partially changing its size.
        if(Math.abs(HudInterfaceScale.size(window).x-width)>1)window.resize(before);
    }
    private static void remember(Widget widget){
        saveDocking(widget);
        if(widget instanceof QuestHelper)((QuestHelper)widget).magneticRemember();
        else if(widget instanceof Window&&widget.getchild(ChatUI.class)!=null)widget.getchild(ChatUI.class).rememberPlace();
        else if(widget instanceof Window)WindowPlaces.moved((Window)widget);
        else if(widget instanceof Toolbars.Bar)((Toolbars.Bar)widget).rememberPosition();
        else if(widget instanceof EquipmentStrip)((EquipmentStrip)widget).rememberPosition();
        else if(widget instanceof GameUI.NKeyBelt)((GameUI.NKeyBelt)widget).rememberPosition();
    }
    private static String id(Widget widget){
        if(widget==null||!(widget.parent instanceof GameUI))return null;
        GameUI gui=(GameUI)widget.parent;
        if(widget==gui.mapfile)return gui.mapfile.deco==null&&!gui.mapfile.frameBoosted?"map":null;
        if(widget==gui.questHelper)return "quests";
        if(gui.chat!=null&&(widget==gui.chat||gui.chat.detached()&&gui.chat.getparent(Window.class)==widget))return "chat";
        return null;
    }
    private static Widget target(GameUI gui,String name){
        if(name.equals("screen"))return gui;
        if(name.equals("map"))return gui.mapfile!=null&&gui.mapfile.deco==null&&!gui.mapfile.frameBoosted?gui.mapfile:null;
        if(name.equals("quests"))return gui.questHelper!=null&&gui.questHelper.parent==gui?gui.questHelper:null;
        if(name.equals("chat")&&gui.chat!=null)return gui.chat.detached()?gui.chat.getparent(Window.class):gui.chat;
        return null;
    }
    private static void saveDocking(Widget widget){
        String key=id(widget);if(key==null)return;
        String top=id(parent(widget));StringBuilder value=new StringBuilder();
        if(top!=null)value.append("below=").append(top);
        Link[] axes=links.get(widget);
        if(top==null&&axes!=null)for(int axis=0;axis<2;axis++)if(axes[axis]!=null){
            Link link=axes[axis];Widget other=link.target.get();String name=other==widget.parent?"screen":id(other);
            if(name==null)continue;
            value.append(axis).append(',').append(name).append(',').append(link.ownEdge).append(',').append(link.targetEdge).append(',').append(UI.unscale(link.gap)).append(';');
        }
        Utils.setpref(DOCK+key,value.toString());
    }
    private static void restoreDocking(GameUI gui){
        if(gui.sz.x<UI.scale(200)||gui.sz.y<UI.scale(200))return;
        for(String name:new String[]{"map","chat","quests"}){
            Widget widget=target(gui,name);if(widget==null||widget.parent!=gui||restored.contains(widget))continue;
            if(attached(widget)||drags.containsKey(widget)){restored.add(widget);continue;}
            String value=Utils.getpref(DOCK+name,null);
            if(value==null){
                // Adopt a genuine saved edge position, not a coordinate temporarily clamped at login.
                Coord at=widget instanceof QuestHelper?((QuestHelper)widget).preferredPosition():name.equals("map")?hearthborn.map.MapPlaces.position(true):Utils.getprefc("hearthborn/chat/window-position",widget.c);
                Coord room=gui.sz.sub(span(widget));Link[] axes=new Link[2];
                if(room.x>=0&&Math.abs(at.x-room.x)<=DISTANCE)axes[0]=new Link(gui,1,1,0);
                if(at.y>=0&&at.y<=DISTANCE)axes[1]=new Link(gui,0,0,0);
                if(axes[0]!=null||axes[1]!=null){links.put(widget,axes);saveDocking(widget);}
                restored.add(widget);continue;
            }
            if(value.startsWith("below=")){
                Widget top=target(gui,value.substring(6));if(top==null)continue;
                if(compatible(widget,top))above.put(widget,new WeakReference<>(top));
            }else{
                Link[] axes=new Link[2];boolean waiting=false;
                try{for(String entry:value.split(";"))if(!entry.isEmpty()){
                    String[] fields=entry.split(",");if(fields.length!=5)throw new IllegalArgumentException();
                    int axis=Integer.parseInt(fields[0]),own=Integer.parseInt(fields[2]),edge=Integer.parseInt(fields[3]);
                    if(axis<0||axis>1||own<0||own>1||edge<0||edge>1)throw new IllegalArgumentException();
                    Widget other=target(gui,fields[1]);if(other==null){waiting=true;break;}
                    if(other==gui||!blocked(other,widget))axes[axis]=new Link(other,own,edge,UI.scale(Integer.parseInt(fields[4])));
                }}catch(IllegalArgumentException invalid){restored.add(widget);continue;}
                if(waiting)continue;
                if(axes[0]!=null||axes[1]!=null)links.put(widget,axes);
            }
            restored.add(widget);
        }
    }
}
