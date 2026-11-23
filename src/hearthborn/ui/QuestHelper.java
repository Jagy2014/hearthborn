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
import hearthborn.L10n;
import java.awt.Color;
import java.util.*;
import java.util.List;

/**
 * Current quests in one window. The active quest is expanded; only an explicit click changes server selection.
 *
 * @author Jagy2014
 */
public final class QuestHelper extends Window {
    private static final String PREF="hearthborn/quests/";
    /** The blue of the credo quest on the light sheet. */
    private static final Color CREDO_INK=new Color(0x2F6A9E);
    private static final int ROW=UI.scale(44),GAP=UI.scale(4),FOOTER=UI.scale(18),MIN_WIDTH=UI.scale(140),MIN_HEIGHT=ROW;
    private static final Text.Foundry TITLE_FONT=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),12).aa(true);
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans,12).aa(true);
    private final GameUI gui;
    private final Map<Integer,Snapshot> cache=new HashMap<>();
    private final Map<Integer,Integer> order=new HashMap<>();
    private final List<QuestWnd.Quest> quests=new ArrayList<>();
    private final Cards tasks;
    private Coord home;
    private int wantedWidth,wantedHeight,serial;
    private boolean combatHidden,selectionKnown;
    private Integer selectedQuest,laidSelection;
    private QuestWnd.Quest.Info observedSelection;
    private String signature="";

    public static final class Snapshot {
        final int ncond;
        public final List<QuestWnd.Quest.Condition> conditions;
        Snapshot(QuestWnd.Quest quest,QuestWnd.Quest.Condition[] source){
            ncond=quest.ncond;
            List<QuestWnd.Quest.Condition> copy=new ArrayList<>();
            for(QuestWnd.Quest.Condition condition:source)copy.add(new QuestWnd.Quest.Condition(condition.desc,condition.done,condition.status));
            conditions=Collections.unmodifiableList(copy);
        }
        // The quest summary and its condition details can arrive in either order.
        boolean current(QuestWnd.Quest quest){return ncond==quest.ncond;}
    }
    public QuestHelper(GameUI gui){
        super(UI.scale(300,150),"Quest journal");this.gui=gui;
        settrans(Window.niltrans);
        Coord saved=Utils.getprefc(PREF+"position",null);home=saved==null?null:UI.scale(saved);
        int oldWidth=Utils.getprefi(PREF+"magnetic-width",0);
        wantedWidth=Math.max(MIN_WIDTH,UI.scale(Utils.getprefi(PREF+"width",oldWidth>0?oldWidth-UI.unscale(sz.x-csz().x):300)));
        int savedHeight=Utils.getprefi(PREF+"height",-1);
        if(savedHeight<0)savedHeight=Utils.getprefi(PREF+"rows",3)*44;
        wantedHeight=Math.max(MIN_HEIGHT,UI.scale(savedHeight));
        tasks=add(new Cards(Coord.of(wantedWidth,wantedHeight)),Coord.z);
        add(new RightStretch(this));add(new LeftStretch(this,"hearthborn/quests/corner"));applySize(wantedWidth,wantedHeight);
        show(enabled());
    }
    /** A passive HUD window must not take keyboard focus when GameUI first attaches it. */
    protected void added(){}
    public static boolean framed(){return Utils.getprefb(PREF+"frame",true);}
    public static void framed(boolean value){Utils.setprefb(PREF+"frame",value);}
    private boolean shownFrame=framed();
    protected Deco makedeco(){return framed()?super.makedeco():new PlainDeco();}
    public static boolean enabled(){return Utils.getprefb(PREF+"screen",true);}
    public static void enabled(boolean value){Utils.setprefb(PREF+"screen",value);}
    protected boolean escapeCloses(){return false;}
    public void reqclose(){enabled(false);hide();}
    private void visibility(){boolean shown=enabled()&&!combatHidden;if(visible()!=shown)show(shown);}
    public void combat(boolean hidden){combatHidden=hidden;visibility();}
    private QuestWnd journal(){return gui.chrwdg==null?null:gui.chrwdg.quest;}
    private int credoQuest(){
        SkillWnd skills=gui.chrwdg==null?null:gui.chrwdg.skill;
        return skills!=null&&skills.credos!=null&&skills.credos.pcr!=null?skills.credos.pqid:Integer.MIN_VALUE;
    }
    private boolean currentQuest(QuestWnd.Quest quest){return quest.done==QuestWnd.Quest.QST_PEND||quest.done==QuestWnd.Quest.QST_DISABLED;}
    public List<QuestWnd.Quest> active(){
        QuestWnd journal=journal();if(journal==null)return Collections.emptyList();
        List<QuestWnd.Quest> result=new ArrayList<>();
        for(QuestWnd.Quest quest:journal.cqst.quests)if(currentQuest(quest)){
            if(!order.containsKey(quest.id))order.put(quest.id,serial++);
            result.add(quest);
        }
        int credo=credoQuest();
        result.sort(Comparator.comparingInt((QuestWnd.Quest quest)->Objects.equals(selectedQuest,quest.id)?0:1)
            .thenComparingInt(quest->quest.id==credo?0:1)
            .thenComparingInt(quest->order.get(quest.id)));
        return result;
    }
    public List<Integer> screenQuests(){List<Integer> ids=new ArrayList<>();for(QuestWnd.Quest quest:active())ids.add(quest.id);return ids;}
    public void manualSelection(Integer id){selectedQuest=id;selectionKnown=true;}
    public void capture(QuestWnd.Quest.Box box){
        QuestWnd journal=journal();QuestWnd.Quest quest=journal==null?null:journal.cqst.get(box.id);
        if(quest==null||!currentQuest(quest))return;
        Snapshot previous=cache.put(box.id,new Snapshot(quest,box.conds()));
        if(previous!=null)for(QuestWnd.Quest.Condition condition:box.conds()){
            if(condition.done!=QuestWnd.Quest.QST_DONE)continue;
            boolean completed=previous.conditions.stream().anyMatch(old->old.desc.equals(condition.desc)&&old.done==QuestWnd.Quest.QST_PEND);
            if(completed)gui.msg(new UI.SimpleMessage(L10n.format("Quest completed: %s",L10n.text("label",CredoQuests.objective(box.title,condition.desc))),QuestWnd.Quest.stcol[QuestWnd.Quest.QST_DONE],null));
        }
        if(!selectionKnown)manualSelection(box.id);
    }
    private void select(int id){
        QuestWnd journal=journal();if(journal==null)return;
        if(selectionKnown&&Objects.equals(selectedQuest,id))return;
        manualSelection(id);journal.wdgmsg("qsel",id);
    }
    private void details(int id){
        select(id);if(gui.chrwdg==null)return;
        gui.chrwdg.show();gui.chrwdg.raise();gui.chrwdg.questtab.showtab();
        if(gui.chrwdg.parent!=null)gui.chrwdg.parent.setfocus(gui.chrwdg);
    }
    private String title(QuestWnd.Quest quest){
        String name;try{name=quest.title();}catch(Loading pending){name=L10n.tr("Loading quest objectives…");}
        return (quest.id==credoQuest()?L10n.tr("Credo quest")+": ":"")+name;
    }
    private String progress(QuestWnd.Quest quest){return L10n.format("Objectives: %d / %d",quest.ndcond,quest.ncond);}
    private String objective(QuestWnd.Quest quest,QuestWnd.Quest.Condition condition){
        String line=(condition.done==QuestWnd.Quest.QST_DONE?"✓ ":"• ")+L10n.text("label",CredoQuests.objective(quest.title,condition.desc));
        if(condition.status!=null&&!condition.status.isEmpty())line+=" "+L10n.text("label",condition.status);
        return line;
    }
    private String tooltip(QuestWnd.Quest quest){
        StringBuilder text=new StringBuilder(title(quest)).append('\n').append(progress(quest));
        if(Objects.equals(selectedQuest,quest.id))text.append('\n').append(L10n.tr("Active quest"));
        Snapshot snapshot=cache.get(quest.id);
        if(snapshot!=null&&snapshot.current(quest))for(QuestWnd.Quest.Condition condition:snapshot.conditions)text.append('\n').append(objective(quest,condition));
        else text.append('\n').append(L10n.tr("Activate this quest to load its objectives."));
        return text.append('\n').append(L10n.tr("Left-click to activate. Right-click for details.")).toString();
    }
    private String fingerprint(List<QuestWnd.Quest> current){
        StringBuilder next=new StringBuilder().append(selectedQuest).append(';').append(UiTheme.generation()).append(';');
        for(QuestWnd.Quest quest:current){
            next.append(quest.id).append(':').append(quest.ndcond).append('/').append(quest.ncond).append(':').append(quest.done);
            if(Objects.equals(selectedQuest,quest.id)){
                Snapshot snapshot=cache.get(quest.id);
                if(snapshot!=null&&snapshot.current(quest))for(QuestWnd.Quest.Condition condition:snapshot.conditions){
                    if(condition.done==QuestWnd.Quest.QST_DONE)continue;
                    next.append('|').append(condition.desc).append('\u0000').append(condition.status);
                }
            }
            next.append(';');
        }
        return next.toString();
    }
    public void tick(double dt){
        QuestWnd journal=journal();
        if(journal!=null&&journal.quest!=observedSelection){
            observedSelection=journal.quest;manualSelection(observedSelection==null?null:observedSelection.questid());
        }
        /* The details of the selected quest can reach the journal before the quest itself (a new character's first quest at
         * login), and then the message that carries them is never repeated: take them from the box the journal shows. */
        if(journal!=null&&observedSelection instanceof QuestWnd.Quest.Box){
            QuestWnd.Quest.Box box=(QuestWnd.Quest.Box)observedSelection;
            if(!cache.containsKey(box.id)&&box.conds().length>0)capture(box);
        }
        List<QuestWnd.Quest> current=active();
        Set<Integer> live=new HashSet<>();for(QuestWnd.Quest quest:current)live.add(quest.id);
        cache.keySet().retainAll(live);order.keySet().retainAll(live);
        if(selectedQuest!=null&&!live.contains(selectedQuest)){selectedQuest=null;selectionKnown=false;current=active();}
        String next=fingerprint(current);
        if(!next.equals(signature)){
            int top=tasks.bar.val;Integer anchor=null;int offset=0;
            for(Widget child:tasks.cont.children()){
                if(!(child instanceof Card)||child.c.y+child.sz.y<=top)continue;
                anchor=((Card)child).quest.id;offset=Math.max(0,top-child.c.y);break;
            }
            boolean jumped=!Objects.equals(laidSelection,selectedQuest);
            quests.clear();quests.addAll(current);signature=next;laidSelection=selectedQuest;tasks.rebuild();
            if(jumped)tasks.bar.val=0;
            else if(anchor!=null)for(Widget child:tasks.cont.children())if(child instanceof Card&&((Card)child).quest.id==anchor){tasks.bar.val=child.c.y+offset;break;}
            tasks.sync();
        }
        if(shownFrame!=framed()){shownFrame=framed();chdeco(makedeco());}
        visibility();super.tick(dt);
    }
    private void applySize(int width,int height){
        super.resize(Coord.of(width,height+FOOTER));
        if(tasks!=null)tasks.resize(Coord.of(width,height));
    }
    public void resize(Coord size){
        wantedWidth=Math.max(MIN_WIDTH,size.x);wantedHeight=Math.max(MIN_HEIGHT,size.y-FOOTER);
        applySize(wantedWidth,wantedHeight);
    }
    public void layout(int bottom){
        if(gui.sz.x<=0||gui.sz.y<=0)return;
        Coord trim=sz.sub(csz()),room=HudInterfaceScale.fromParent(this,gui.sz);
        int width=Math.min(wantedWidth,Math.max(UI.scale(100),room.x-trim.x));
        int available=Math.max(MIN_HEIGHT,room.y-trim.y-FOOTER);
        int height=Math.min(wantedHeight,available);
        if(csz().x!=width||tasks.sz.y!=height)applySize(width,height);
        Coord preferred=home==null?Coord.of(UI.scale(10),Math.max(0,bottom-sz.y-UI.scale(10))):home;
        Coord outer=HudInterfaceScale.size(this);
        move(Coord.of(Utils.clip(preferred.x,0,Math.max(0,gui.sz.x-outer.x)),Utils.clip(preferred.y,0,Math.max(0,gui.sz.y-outer.y))));
        visibility();
    }
    public void magneticMove(Coord at){home=new Coord(at);move(at);}
    public Coord preferredPosition(){return home==null?c:new Coord(home);}
    public void magneticRemember(){
        if(home!=null)Utils.setprefc(PREF+"position",UI.unscale(home));
        Utils.setprefi(PREF+"width",UI.unscale(wantedWidth));Utils.setprefi(PREF+"height",UI.unscale(wantedHeight));WindowPlaces.flushPreferences();
    }
    public void magneticWidth(int pixels){
        /* The caller measures in the parent's pixels; the journal may be drawn smaller or larger than its own units. */
        int content=HudInterfaceScale.fromParent(this,Coord.of(pixels,0)).x-(sz.x-csz().x);if(content<MIN_WIDTH)return;
        wantedWidth=content;applySize(content,tasks.sz.y);magneticRemember();
    }
    public void openWindow(){
        enabled(true);combatHidden=false;
        if(parent==null)gui.add(this);
        show();raise();
    }
    public void toggleWindow(){if(visible())reqclose();else openWindow();}
    /** Active quest title and its objectives, for the buff-row launcher. */
    public String hoverSummary(){
        QuestWnd.Quest quest=null;
        if(selectedQuest!=null)for(QuestWnd.Quest candidate:active())if(candidate.id==selectedQuest){quest=candidate;break;}
        if(quest==null)return L10n.tr(journal()==null?"Loading quests…":"No active quest");
        StringBuilder text=new StringBuilder(L10n.tr("Active quest")).append(": ").append(title(quest));
        Snapshot snapshot=cache.get(quest.id);
        if(snapshot!=null&&snapshot.current(quest))for(QuestWnd.Quest.Condition condition:snapshot.conditions)text.append('\n').append(objective(quest,condition));
        else text.append('\n').append(progress(quest)).append('\n').append(L10n.tr("Activate this quest to load its objectives."));
        return text.toString();
    }
    public boolean mousedown(MouseDownEvent event){
        // A card opening Character Sheet must retain that window's focus and stacking order.
        if(event.propagate(this))return true;
        return super.mousedown(event);
    }

    private final class Cards extends Scrollport {
        Cards(Coord size){super(size);}
        protected Scrollbar makebar(int height){return new JournalScrollport.Grip(height){public void changed(){cont.sy=val;}};}
        void rebuild(){
            for(Widget child:new ArrayList<>(cont.children()))child.destroy();
            int y=0;
            for(QuestWnd.Quest quest:quests){
                if(y>0)y+=GAP;
                y+=cont.add(new Card(quest,cont.sz.x),Coord.of(0,y)).sz.y;
            }
        }
        void sync(){
            int content=0;for(Widget child:cont.children())content=Math.max(content,child.c.y+child.sz.y);
            bar.max=Math.max(0,content-sz.y);bar.val=Utils.clip(bar.val,0,bar.max);cont.sy=bar.val;
        }
        public void resize(Coord size){
            int old=cont.sz.x;super.resize(size);
            if(old!=cont.sz.x)rebuild();sync();
        }
        public boolean mousewheel(MouseWheelEvent event){bar.ch(event.s*UI.scale(20));return true;}
        public void draw(GOut g){
            super.draw(g);
            if(quests.isEmpty())g.atext(L10n.tr(journal()==null?"Loading quests…":"No active quests"),Coord.of(UI.scale(6),ROW/2),0,.5);
        }
    }
    private final class Card extends Widget {
        private final QuestWnd.Quest quest;
        private final List<Piece> pieces=new ArrayList<>();
        private String shown="";
        Card(QuestWnd.Quest quest,int width){super(Coord.of(width,ROW));this.quest=quest;compose(width);}
        private void compose(int width){
            boolean open=Objects.equals(selectedQuest,quest.id);
            int inner=Math.max(1,width-UI.scale(14));
            boolean light=UiTheme.light();
            Color titleColor=light?Parchment.TEXT:Color.WHITE;
            if(quest.id==credoQuest())titleColor=light?CREDO_INK:new Color(140,215,255);
            else if(quest.done==QuestWnd.Quest.QST_DISABLED)titleColor=light?Parchment.ACCENT_TEXT:new Color(255,165,70);
            Color lineColor=light?Parchment.TEXT2:new Color(205,199,169);
            String heading=title(quest);
            if(!open)heading=ellipsize(heading,inner);
            Text titleLine=open?TITLE_FONT.renderwrap(heading,titleColor,inner):TITLE_FONT.render(heading,titleColor);
            int y=UI.scale(4);
            pieces.add(new Piece(titleLine.tex(),y,null,0));
            y+=titleLine.sz().y+UI.scale(2);
            List<String> lines=new ArrayList<>();
            List<String> pictures=new ArrayList<>();
            List<String> objectives=new ArrayList<>();
            Snapshot snapshot=open?cache.get(quest.id):null;
            if(snapshot!=null&&snapshot.current(quest))for(QuestWnd.Quest.Condition condition:snapshot.conditions){
                if(condition.done!=QuestWnd.Quest.QST_DONE){lines.add(objective(quest,condition));pictures.add(QuestPictures.resource(condition.desc));objectives.add(condition.desc);}
            }
            if(lines.isEmpty()){lines.add(progress(quest));pictures.add(null);objectives.add(null);}
            shown=String.join("\n",lines);
            if(open){
                for(int i=0;i<lines.size();i++){
                    String picture=pictures.get(i);
                    int reserved=picture==null?0:UI.scale(26);
                    int limit=Math.max(1,inner-reserved);
                    String line=lines.get(i);
                    int textWidth=FONT.strsize(line).x;
                    Text row=textWidth<=limit?FONT.render(line,lineColor):FONT.renderwrap(line,lineColor,limit);
                    if(textWidth>limit)textWidth=row.sz().x;
                    pieces.add(new Piece(row.tex(),y,picture,textWidth,objectives.get(i)));
                    y+=Math.max(row.sz().y,picture==null?0:UI.scale(22))+UI.scale(1);
                }
                resize(Coord.of(width,Math.max(ROW,y+UI.scale(4))));
            }else{
                pieces.add(new Piece(FONT.render(lines.get(0),lineColor).tex(),ROW/2+UI.scale(1),null,0));
            }
        }
        private String ellipsize(String value,int width){
            if(TITLE_FONT.strsize(value).x<=width)return value;
            int low=0,high=value.length();
            while(low<high){int middle=(low+high+1)/2;if(TITLE_FONT.strsize(value.substring(0,middle)+"…").x<=width)low=middle;else high=middle-1;}
            return value.substring(0,low)+"…";
        }
        public void draw(GOut g){
            boolean selected=Objects.equals(selectedQuest,quest.id);
            if(UiTheme.light()){
                // A light card: the panel colour, a thin line round it and the accent bar down its left edge.
                g.chcolor(selected?Parchment.SELECTED:Parchment.PANEL);g.frect(Coord.z,sz);
                g.chcolor(Parchment.LINE);g.rect(Coord.z,sz);
                g.chcolor(selected?Parchment.ACCENT:Parchment.CHECK_LINE);g.frect(Coord.z,Coord.of(UI.scale(3),sz.y));g.chcolor();
            }else{
            g.chcolor(selected?new Color(110,83,34,125):new Color(255,255,255,16));g.frect(Coord.z,sz);
            g.chcolor(selected?new Color(222,186,91,240):new Color(121,96,57,100));g.rect(Coord.z,sz);g.chcolor();
            }
            for(Piece piece:pieces)piece.draw(g,UI.scale(7));
            super.draw(g);
        }
        public boolean mousedown(MouseDownEvent event){
            if(event.b==1){
                for(Piece piece:pieces)
                    if(piece.hit(event.c)&&QuestForage.open(gui,quest.id,piece.objective,piece.picture))return true;
                select(quest.id);
            }else if(event.b==3)details(quest.id);
            return true;
        }
        public Object tooltip(Coord at,Widget previous){return QuestHelper.this.tooltip(quest);}
        public void dispose(){for(Piece piece:pieces)piece.dispose();super.dispose();}
    }
    private static final class Piece {
        final Tex tex;final int y;final String picture,objective;final int textWidth;
        private Tex icon;private boolean missing;
        Piece(Tex tex,int y,String picture,int textWidth){this(tex,y,picture,textWidth,null);}
        Piece(Tex tex,int y,String picture,int textWidth,String objective){
            this.tex=tex;this.y=y;this.picture=picture;this.textWidth=textWidth;this.objective=objective;
        }
        boolean hit(Coord point){
            return picture!=null&&point.isect(Coord.of(UI.scale(7)+UI.scale(4)+textWidth,y),UI.scale(22,22));
        }
        void draw(GOut g,int x){
            int shift=picture==null?0:Math.max(0,(UI.scale(22)-tex.sz().y)/2);
            g.image(tex,Coord.of(x,y+shift));
            if(picture!=null){
                int size=UI.scale(22);Tex image=icon();
                if(image!=null)g.image(image,Coord.of(x+textWidth+UI.scale(4),y),Coord.of(size,size));
            }
        }
        private Tex icon(){
            if(icon!=null||missing)return icon;
            try{
                Resource.Image layer=Resource.remote().load(picture).get().layer(Resource.imgc);
                if(layer==null||layer.img==null){missing=true;return null;}
                return icon=new TexI(PUtils.uiscale(layer.img,UI.scale(22,22)));
            }catch(Loading pending){return null;}
            catch(RuntimeException failure){missing=true;return null;}
        }
        void dispose(){tex.dispose();if(icon!=null)icon.dispose();}
    }
}
