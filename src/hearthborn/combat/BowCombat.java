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

package hearthborn.combat;

import haven.*;
import hearthborn.L10n;
import hearthborn.scripts.ShortcutRunner;
import java.awt.Color;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One explicit shot, with automatic confirmation of the chosen animal. Never repeats.
 *
 * @author Jagy2014
 */
public final class BowCombat {
    private enum Weapon { NONE, BOW, LOADING }
    private enum Phase { IDLE, STARTING, AIMING }
    private static final Map<GameUI,BowCombat> states=new WeakHashMap<>();
    private static final Text.Foundry LABEL=new Text.Foundry(Text.sans.deriveFont(java.awt.Font.BOLD),16);
    private Weapon weapon=Weapon.NONE;
    private WeakReference<GItem> bow;
    private Phase phase=Phase.IDLE;
    private long target=-1,lastCombatTarget=-1,pendingShot=-1;
    private boolean issuing,picking,awaitingClick;
    private double since,haltedAt;
    private Coord2d position;
    private Coord shotAt,swapAt,shotSize,swapSize;
    private Text shotLabel,swapLabel;
    private int keyHeld=-1;
    private UI.Grab keyGrab;

    private static BowCombat state(GameUI gui){return states.computeIfAbsent(gui,key->new BowCombat());}
    private static BowCombat existing(GameUI gui){return gui==null?null:states.get(gui);}
    private static GameUI game(Fightsess fight){return fight==null?null:fight.getparent(GameUI.class);}
    private void bow(GameUI gui,GItem item){if((bow==null?null:bow.get())!=item){cancelAim(gui);bow=item==null?null:new WeakReference<>(item);}}
    private Weapon weapon(GameUI gui){
        Equipory gear=gui==null?null:gui.equipment();if(gear==null){bow(gui,null);return Weapon.NONE;}
        boolean loading=false;
        for(WItem view:gear.children(WItem.class)){
            if(!view.c.equals(Equipory.ecoords[6].add(1,1))&&!view.c.equals(Equipory.ecoords[7].add(1,1)))continue;
            try{
                String name=view.item.getres().basename();
                if(name.equals("huntersbow")||name.equals("rangersbow")){bow(gui,view.item);return Weapon.BOW;}
            }catch(Loading pending){loading=true;}
        }
        bow(gui,null);return loading?Weapon.LOADING:Weapon.NONE;
    }
    private static long combatTarget(GameUI gui){return gui.fv==null||gui.fv.current==null?-1:gui.fv.current.gobid;}
    static Gob currentAnimal(GameUI gui){
        return gui.fv==null||gui.fv.current==null||gui.fv.current.invalid?null:animal(gui,gui.fv.current.gobid);
    }
    private static Gob animal(GameUI gui,long id){
        if(gui.ui==null||gui.ui.sess==null)return null;
        Gob gob=gui.ui.sess.glob.oc.getgob(id);
        if(gob==null||gob.removed||gob.id==gui.plid||gob.rc==null)return null;
        GobHealth health=gob.getattr(GobHealth.class);if(health!=null&&health.hp<=0)return null;
        try{
            Drawable draw=gob.getattr(Drawable.class);if(draw==null)return null;
            String name=draw.getres().name;
            if(!name.startsWith("gfx/kritter/")||name.startsWith("gfx/kritter/tick/"))return null;
            if(draw instanceof Composite)for(ResData pose:((Composite)draw).poseResources())try{
                String path=pose.res.get().name,tail=path.substring(path.lastIndexOf('/')+1);
                if(tail.equals("dead")||tail.equals("waterdead")||tail.equals("knock")
                    ||tail.endsWith("-dead")||tail.endsWith("-waterdead")||tail.endsWith("-knock"))return null;
            }catch(Loading pending){}
            return gob;
        }catch(Loading pending){return null;}
    }
    private void stop(){picking=awaitingClick=false;phase=Phase.IDLE;position=null;pendingShot=-1;}
    private void cancelAim(GameUI gui){
        boolean started=phase!=Phase.IDLE;boolean hadPending=pendingShot>=0;stop();
        if((!started&&!hadPending)||gui==null||gui.map==null||gui.vhand!=null||!serverShootCursor(gui))return;
        Gob player=gui.map.player();if(player==null)return;
        issuing=true;
        try{gui.map.wdgmsg("click",Coord.z,player.rc.floor(OCache.posres),3,0);}
        finally{issuing=false;}
    }
    /** Click under the feet to break a flee/path; Shoot then fires once movement ends. */
    private void halt(GameUI gui,Gob body){
        if(gui.map==null||body==null||body.rc==null)return;
        issuing=true;
        try{
            hearthborn.scripts.ScriptRunner.manualFieldInput(gui);ShortcutRunner.cancel(gui);
            hearthborn.navigation.Walking.abandon(gui.map);
            gui.map.wdgmsg("click",Coord.z,body.rc.floor(OCache.posres),1,0);
            haltedAt=Utils.rtime();
        }finally{issuing=false;}
    }
    private Gob refresh(GameUI gui){
        Weapon now=weapon(gui);if(now!=weapon){cancelAim(gui);weapon=now;}
        long selected=combatTarget(gui);Gob gob=animal(gui,target);
        if(phase!=Phase.IDLE){
            if(gob==null||(selected!=lastCombatTarget&&selected!=target)
                ||(selected==target&&gui.fv.current.invalid))cancelAim(gui);
        }else if(!picking){gob=currentAnimal(gui);target=gob==null?-1:gob.id;}
        lastCombatTarget=selected;
        if(now!=Weapon.BOW||KnockoutState.unconscious(gui))cancelAim(gui);
        return gob;
    }
    public static boolean handles(Fightsess fight){
        GameUI gui=game(fight);if(gui==null)return false;
        BowCombat own=state(gui);own.refresh(gui);return own.weapon!=Weapon.NONE;
    }
    private static Gob movementBody(GameUI gui){
        Gob body=gui.map==null?null:gui.map.player();
        for(int i=0;body!=null&&i<4;i++){
            Following following=body.getattr(Following.class);if(following==null)break;
            body=following.tgt();
        }
        return body;
    }
    private static boolean moving(Gob body){
        if(body==null)return true;
        Moving movement=body.getattr(Moving.class);return movement!=null&&movement.getv()>.01;
    }
    public static void tick(GameUI gui){
        BowCombat own=existing(gui);if(own==null)return;
        Gob gob=own.refresh(gui);
        if(own.pendingShot>=0&&own.phase==Phase.IDLE){
            Gob body=movementBody(gui);
            if(moving(body)){
                // Path may still be running after the first halt - nudge again after a beat.
                if(Utils.rtime()-own.haltedAt>.4)own.halt(gui,body);
                return;
            }
            long id=own.pendingShot;own.pendingShot=-1;
            Gob shot=animal(gui,id);
            if(shot!=null)own.shoot(gui,shot);
            return;
        }
        if(own.phase==Phase.IDLE||gob==null)return;
        Gob body=movementBody(gui);
        if(moving(body)||(own.position!=null&&own.position.dist(body.rc)>.05)){own.cancelAim(gui);return;}
        own.position=Coord2d.of(body.rc.x,body.rc.y);
        double now=Utils.rtime();
        if((own.phase==Phase.STARTING&&now-own.since>10)||(own.phase==Phase.AIMING&&now-own.since>180)){
            own.cancelAim(gui);gui.ui.msg(L10n.tr("Shooting did not finish. Press Shoot target to try again."));return;
        }
        if(own.phase==Phase.STARTING&&own.awaitingClick&&gui.prog==null&&serverShootCursor(gui))own.confirmTarget(gui,gob);
    }
    private static boolean serverShootCursor(GameUI gui){
        for(Widget widget=gui.map;widget!=null;widget=widget.parent)if(widget.cursor!=null){
            try{return widget.cursor.get().name.equals("gfx/hud/curs/shoot");}catch(Loading pending){return false;}
        }
        return false;
    }
    private void shoot(GameUI gui,Gob gob){
        target=gob.id;lastCombatTarget=combatTarget(gui);picking=false;
        Gob body=movementBody(gui);
        if(moving(body)){
            pendingShot=gob.id;
            halt(gui,body);
            return;
        }
        pendingShot=-1;
        if(gui.fv!=null&&Utils.rtime()<gui.fv.atkct){gui.ui.msg(L10n.tr("Wait for recovery before shooting."));return;}
        issuing=true;
        try{
            hearthborn.scripts.ScriptRunner.manualFieldInput(gui);ShortcutRunner.cancel(gui);
            hearthborn.navigation.Walking.abandon(gui.map);
            phase=Phase.STARTING;since=Utils.rtime();awaitingClick=true;position=Coord2d.of(body.rc.x,body.rc.y);
            gui.wdgmsg("act","shoot",0);
        }finally{issuing=false;}
    }
    private void confirmTarget(GameUI gui,Gob gob){
        awaitingClick=false;since=Utils.rtime();issuing=true;
        try{
            ClickData clicked=new ClickData(new Gob.GobClick(gob),null);
            gui.map.wdgmsg("click",Utils.extend(new Object[]{Coord.z,gob.rc.floor(OCache.posres),1,0},clicked.clickargs()));
        }finally{issuing=false;}
    }
    public static void request(GameUI gui){
        if(gui==null)return;BowCombat own=state(gui);own.refresh(gui);
        if(own.phase!=Phase.IDLE||own.picking||ShortcutRunner.switchingWeapon(gui))return;
        if(own.weapon!=Weapon.BOW||gui.map==null){gui.ui.error(L10n.tr("Equip a bow before shooting."));return;}
        if(gui.prog!=null||gui.vhand!=null){gui.ui.msg(L10n.tr("Finish the current action and empty the cursor before shooting."));return;}
        boolean preview=TargetPreview.marked(gui);
        Gob gob=preview?TargetPreview.forAction(gui):currentAnimal(gui);
        if(preview&&gob==null)return;
        if(preview&&gui.fv!=null&&Utils.rtime()<gui.fv.atkct){
            gui.ui.msg(L10n.tr("Wait for recovery before shooting."));
            return;
        }
        if(preview)TargetPreview.clear(gui);
        if(gob!=null)own.shoot(gui,gob);
        else{
            hearthborn.scripts.ScriptRunner.manualFieldInput(gui);ShortcutRunner.cancel(gui);
            hearthborn.navigation.Walking.abandon(gui.map);
            own.picking=true;own.target=-1;
            gui.ui.msg(L10n.tr("Select an animal to shoot once. Right-click or Escape cancels."));
        }
    }
    public static void shootMarked(GameUI gui){
        if(gui==null)return;
        if(TargetPreview.marked(gui))request(gui);
        else TargetPreview.forAction(gui);
    }
    /** Overrides the numeric belt only with a bow and a fight or an explicit preview. */
    public static boolean key(GameUI gui,Widget.GlobKeyEvent event){
        Fightsess fight=gui.getchild(Fightsess.class);if(fight==null&&!TargetPreview.marked(gui))return false;
        BowCombat own=state(gui);own.refresh(gui);if(own.weapon!=Weapon.BOW)return false;
        if(own.keyHeld>=0)return true;
        own.keyHeld=event.code;
        own.keyGrab=gui.ui.grab(fight==null?gui:fight,Widget.KbdEvent.class,key->{
            if(key.code!=own.keyHeld)return false;
            if(key instanceof Widget.KeyUpEvent){own.releaseKey();return true;}
            return key instanceof Widget.KeyDownEvent;
        });
        request(gui);return true;
    }
    private void releaseKey(){keyHeld=-1;if(keyGrab!=null){keyGrab.remove();keyGrab=null;}}
    public static boolean picking(GameUI gui){BowCombat own=existing(gui);return own!=null&&own.picking;}
    public static Resource cursor(GameUI gui){
        if(!picking(gui))return null;
        try{return Resource.remote().load("gfx/hud/curs/shoot").get();}catch(Loading pending){return Widget.CursorQuery.defcurs;}
    }
    public static void pick(GameUI gui,Gob gob,int button){
        BowCombat own=existing(gui);if(own==null||!own.picking)return;
        if(button==3){own.stop();return;}if(button!=1)return;
        if(gob==null||animal(gui,gob.id)==null){gui.ui.msg(L10n.tr("Select a living animal to shoot."));return;}
        own.shoot(gui,gob);
    }
    public static void progress(GameUI gui,Object[] args){
        BowCombat own=existing(gui);if(own==null||own.phase==Phase.IDLE)return;
        if(args.length==0)own.cancelAim(gui);
        else if(own.phase==Phase.STARTING){own.awaitingClick=false;own.phase=Phase.AIMING;own.since=Utils.rtime();}
    }
    public static void manual(GameUI gui){
        BowCombat own=existing(gui);if(own!=null&&own.issuing)return;
        if(own!=null)own.cancelAim(gui);ShortcutRunner.manualWeaponInput(gui);
    }
    public static void move(GameUI gui){BowCombat own=existing(gui);if(own!=null&&!own.issuing&&!own.picking)own.cancelAim(gui);}
    public static boolean busy(GameUI gui){BowCombat own=existing(gui);return ShortcutRunner.switchingWeapon(gui)||(own!=null&&(own.picking||own.phase!=Phase.IDLE));}
    public static void input(Widget sender,String message,Object... args){
        if(!message.equals("act")&&!message.equals("use")&&!message.equals("belt")&&!message.equals("click")
            &&!message.equals("itemact")&&!message.equals("place")&&!message.equals("dwalk")&&!message.equals("drop")
            &&!message.equals("take")&&!message.equals("iact"))return;
        if(!(sender instanceof GameUI||sender instanceof MenuGrid||sender instanceof MapView||sender instanceof Fightsess
            ||sender instanceof Equipory||sender instanceof Inventory||sender instanceof GItem))return;
        GameUI gui=sender instanceof GameUI?(GameUI)sender:sender.getparent(GameUI.class);
        if(message.equals("dwalk")||(sender instanceof MapView&&message.equals("click")&&args.length==4&&Integer.valueOf(1).equals(args[2])))move(gui);
        else manual(gui);
    }
    public static void notice(UI ui,UI.Notice notice){
        if(notice!=null&&(notice instanceof UI.ErrorMessage||Color.RED.equals(notice.color()))){GameUI gui=ui.root.getchild(GameUI.class);BowCombat own=existing(gui);if(own!=null)own.cancelAim(gui);}
    }
    public static void ended(Fightsess fight){BowCombat own=existing(game(fight));if(own!=null){own.stop();own.releaseKey();}}
    public static void dispose(GameUI gui){
        BowCombat own=states.remove(gui);if(own==null)return;own.stop();own.releaseKey();
        if(own.shotLabel!=null)own.shotLabel.dispose();if(own.swapLabel!=null)own.swapLabel.dispose();
    }
    private void labels(GameUI gui){
        KeyMatch shootKey=hearthborn.input.GlobalKeys.binding("ACT_SHOOT").key();
        String shot=L10n.tr("Shoot target")+(shootKey==KeyMatch.nil?"":" ["+shootKey.name()+"]"),key=ShortcutRunner.bowSwitchLabel(gui),swap=key==null?null:L10n.tr(key);
        if(shotLabel==null||!shotLabel.text.equals(shot)){if(shotLabel!=null)shotLabel.dispose();shotLabel=LABEL.render(shot);}
        if(swap==null){if(swapLabel!=null)swapLabel.dispose();swapLabel=null;swapAt=null;swapSize=null;}
        else if(swapLabel==null||!swapLabel.text.equals(swap)){if(swapLabel!=null)swapLabel.dispose();swapLabel=LABEL.render(swap);}
        Coord pad=UI.scale(28,12);shotSize=shotLabel.sz().add(pad);
        if(swapLabel!=null)swapSize=swapLabel.sz().add(pad);
    }
    private static int below(Fightsess fight){return UI.scale(125)+(Math.max(1,(fight.actions.length+4)/5)-1)*Fightsess.actpitch+Fightsess.actpitch/2+UI.scale(6);}
    private static void button(GOut g,Fightsess fight,Coord at,Coord size,Text label,boolean enabled){
        boolean hover=fight.rootxlate(fight.ui.mc).isect(at,size);
        g.chcolor(new Color(28,26,23,245));g.frect(at,size);
        g.chcolor(enabled?(hover?new Color(255,235,168):new Color(218,185,112)):new Color(100,91,72));g.rect(at,size);
        g.chcolor(enabled?Color.WHITE:new Color(150,145,132));g.image(label.tex(),at.add(UI.scale(14,6)));g.chcolor();
    }
    public static boolean draw(GOut g,Fightsess fight){
        GameUI gui=game(fight);BowCombat own=state(gui);own.refresh(gui);own.labels(gui);
        if(own.weapon==Weapon.NONE){own.shotAt=own.swapAt=null;return false;}
        if(own.weapon!=Weapon.BOW){own.shotAt=own.swapAt=null;return true;}
        int extra=own.swapSize==null?0:own.swapSize.x+UI.scale(6);
        own.shotAt=fight.moves.add(-(own.shotSize.x+extra)/2,below(fight));
        button(g,fight,own.shotAt,own.shotSize,own.shotLabel,own.phase==Phase.IDLE&&gui.prog==null&&gui.vhand==null&&!ShortcutRunner.switchingWeapon(gui));
        if(own.swapLabel!=null){own.swapAt=own.shotAt.add(own.shotSize.x+UI.scale(6),0);button(g,fight,own.swapAt,own.swapSize,own.swapLabel,!ShortcutRunner.switchingWeapon(gui)&&gui.vhand==null);}
        return true;
    }
    public static int swapWidth(Fightsess fight){
        GameUI gui=game(fight);if(gui==null)return 0;BowCombat own=state(gui);own.labels(gui);return own.swapSize==null?0:own.swapSize.x+UI.scale(6);
    }
    public static void drawSwap(GOut g,Fightsess fight,Coord at){
        BowCombat own=state(game(fight));if(own.swapLabel==null)return;own.swapAt=at;
        GameUI gui=game(fight);button(g,fight,at,own.swapSize,own.swapLabel,!ShortcutRunner.switchingWeapon(gui)&&gui.vhand==null);
    }
    public static boolean clickSwap(Fightsess fight,Widget.MouseDownEvent event){
        GameUI gui=game(fight);if(gui==null||event.b!=1)return false;BowCombat own=state(gui);
        if(own.swapAt==null||own.swapSize==null||!event.c.isect(own.swapAt,own.swapSize))return false;
        if(!ShortcutRunner.switchingWeapon(gui)){own.cancelAim(gui);ShortcutRunner.run(gui,"SWITCH_BOW_MELEE");}return true;
    }
    public static boolean click(Fightsess fight,Widget.MouseDownEvent event){
        if(clickSwap(fight,event))return true;GameUI gui=game(fight);BowCombat own=state(gui);
        if(event.b!=1||own.shotAt==null||!event.c.isect(own.shotAt,own.shotSize))return false;
        request(gui);return true;
    }
    public static String swapTip(Fightsess fight,Coord at){
        GameUI gui=game(fight);BowCombat own=existing(gui);
        return own!=null&&own.swapAt!=null&&own.swapSize!=null&&at.isect(own.swapAt,own.swapSize)?L10n.tr("Switch between your bow and a carried sword. Without a sword, put the bow away."):null;
    }
    public static String tip(Fightsess fight,Coord at){
        String swap=swapTip(fight,at);if(swap!=null)return swap;BowCombat own=existing(game(fight));
        return own!=null&&own.shotAt!=null&&at.isect(own.shotAt,own.shotSize)?L10n.tr("Shoot once at the current animal target. Aim confirmation is automatic; each shot requires a new click."):null;
    }
    private BowCombat(){}
}
