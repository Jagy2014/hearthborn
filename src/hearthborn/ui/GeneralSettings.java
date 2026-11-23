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
import hearthborn.meteor.StarFall;
import hearthborn.storage.ContainerSettings;
import hearthborn.timers.Timers;

import haven.*;
import hearthborn.L10n;
import hearthborn.world.GobMarks;
import hearthborn.l10n.Missing;
import java.util.*;
import java.awt.Color;

/**
 * @author Jagy2014
 */
public final class GeneralSettings {
    public static int addButton(OptWnd options, int y) {
        int next=options.main.add(options.new PButton(UI.scale(200), "General settings", 'g',
            () -> panel(options)), Coord.of(UI.scale(220), y)).pos("bl").adds(0, 5).y;
        Button interfaceWindow;
        if(SettingsSidebar.enabled()){
            // In the sidebar layout the UI settings are a section like the others, shown in the pane.
            interfaceWindow=options.new PButton(UI.scale(200),"Interface display",'u',()->UISettings.panel(options));
        }else interfaceWindow=new Button(UI.scale(200),"Interface display",false,()->{
            GameUI gui=options.getparent(GameUI.class);
            if(gui!=null){UISettings.open(gui,options);options.hide();}
        });
        if(!options.ingame){interfaceWindow.disable(true);interfaceWindow.settip("These settings open after you enter the game.");}
        next=options.main.add(interfaceWindow,Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200), "Combat settings", 'b',
            () -> CombatSettings.panel(options)), Coord.of(UI.scale(220), next)).pos("bl").adds(0, 5).y;
        next=options.main.add(options.new PButton(UI.scale(200), "Plants and production", 'p',
            () -> PlantProductionSettings.panel(options, options.main)), Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200), "Containers and contents", 'o',
            () -> ContainerSettings.panel(options, options.main)), Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Auto-Drop Manager",'a',
            () -> hearthborn.inventory.AutoDropSettings.panel(options,options.main)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Object radii",'r',
            () -> RadiusSettings.panel(options,options.main)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Mining safety",'m',
            () -> hearthborn.mining.MiningSafety.settings(options,options.main)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Camera",'c',
            () -> CameraSettings.panel(options,options.main)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Item quality display",'q',()->QualitySettings.panel(options)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"World graphics",'w',()->GraphicsSettings.panel(options)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        next=options.main.add(options.new PButton(UI.scale(200),"Flower menu settings",'f',()->FlowerChoices.panel(options)),Coord.of(UI.scale(220),next)).pos("bl").adds(0,5).y;
        return next;
    }

    private static OptWnd.Panel panel(OptWnd options) {
        OptWnd.Panel panel = options.new Panel().resettable("hearthborn/world/","hearthborn/siege/","hearthborn/walking/","hearthborn/timers/","hearthborn/protection/","hearthborn/speed/","hearthborn/login/","hearthborn/login-toggles/","hearthborn/chat/","hearthborn/star-fall","hearthborn/dewy-notification","hearthborn/pickup-");
        panel.add(new Label("Language:"), UI.scale(0, 4));
        SDropBox<String, Widget> languages = new SDropBox<String, Widget>(UI.scale(160), UI.scale(48), UI.scale(24)) {
            protected boolean listautoscroll() { return false; }
            protected List<? extends String> items() { return Arrays.asList("en", "ru"); }
            protected Widget makeitem(String language, int index, Coord size) {
                Widget row = new Widget(size);
                Label label = new Label(language.equals("ru") ? "Russian" : "English");
                row.add(label, Coord.of(UI.scale(3), (size.y - label.sz.y) / 2));
                return row;
            }
            public void change(String language) {
                super.change(language);
                if(language != null) {
                    L10n.setLanguage(language);
                    if(!language.equals(L10n.language()) && options.ui != null) options.ui.msg(L10n.tr("The language applies after you close the client window completely and start it again (logging out to the character screen is not enough)."));
                }
            }
        };
        languages.change(L10n.language());
        panel.add(languages, UI.scale(245, 0));
        Widget siege=panel.add(new CheckBox("Check for siege engines on login").state(LoginSiegeCheck::enabled).set(LoginSiegeCheck::enabled),Coord.of(0,languages.c.y+languages.sz.y+UI.scale(14)));
        siege.settip("Ask the server whether your claims are under siege when entering the game.");
        Widget nearby=panel.add(new CheckBox("Detect nearby siege machines").state(NearbySiegeCheck::enabled).set(NearbySiegeCheck::enabled),siege.pos("bl").adds(0,5));
        nearby.settip("Reports and highlights loaded rams, catapults and wrecking balls, including your own. Does not scan distant claims or check drying readiness.");
        Widget dewy = panel.add(new CheckBox("Lady's Mantle notification") {{a=DewyTime.enabled();}}.changed(DewyTime::enabled),
            nearby.pos("bl").adds(0,5));
        dewy.settip("Sound at dawn and a Lady's Mantle status icon with a countdown (04:45-07:15 game time).");
        Widget star=panel.add(new CheckBox("Falling star indicator").state(StarFall::enabled).set(StarFall::enabled),dewy.pos("bl").adds(0,5));
        star.settip("Shows starfall as the first status icon, with a login message and sound. Use Star Gaze at night to add the forecast to the icon and Timers. During the event, shooting stars appear inside the calendar at night.");
        Widget marks = worldMarks(options,panel, star);
        Widget combat = panel.add(new CheckBox("Combat assistance with animals") {{a = hearthborn.combat.AutoCombat.enabled();}}.changed(hearthborn.combat.AutoCombat::enabled), marks.pos("bl").adds(0, 5));
        combat.settip("Helps with attacks in an existing animal fight. Does not select or engage animals. With a bow, use Shoot target or its separate combat shortcut (6 by default).");
        Widget boots = panel.add(new CheckBox("Auto-Switch Bunny Slippers/Armor Boots").state(hearthborn.combat.BootSwitch::enabled).set(hearthborn.combat.BootSwitch::enabled), combat.pos("bl").adds(0, 5));
        boots.settip(L10n.tr("Right-click a rabbit to equip bunny slippers. A left click, a click on empty ground or the map, or a right click on anything else equips the best armor boots in the main inventory: plate boots, armored striders, ranger's boots, then leather boots."));
        Widget drinkLabel=panel.add(new Label("Auto-drink below stamina:"),boots.pos("bl").adds(0,12));
        drinkLabel.settip(L10n.tr("Uses Adventures → Drink while stamina is at or below the chosen level. Off leaves drinking manual."));
        Widget drink=panel.add(autoDrink(),Coord.of(UI.scale(220),drinkLabel.c.y-UI.scale(3)));
        int afterDrink=Math.max(drinkLabel.pos("bl").y,drink.pos("bl").y)+UI.scale(5);
        Widget caves = panel.add(new CheckBox("Show cave level on map") {{a = hearthborn.map.CaveLevels.show();}}.changed(hearthborn.map.CaveLevels::show), Coord.of(0,afterDrink));
        caves.settip("Number on cave markers. Left-click the map label to raise the floor, right-click to lower it.");
        Widget cheese = panel.add(new CheckBox("Show cheese rack labels") {{a = hearthborn.cheese.CheeseRacks.enabled();}}.changed(hearthborn.cheese.CheeseRacks::enabled), caves.pos("bl").adds(0, 5));
        cheese.settip("Stage of each tray on a cheese rack.");
        Widget stoneColors=panel.add(new CheckBox("Stockpile materials and contents").state(hearthborn.storage.StonePileColors::enabled).set(hearthborn.storage.StonePileColors::enabled),cheese.pos("bl").adds(0,5));
        stoneColors.settip("Transfer an item through a stockpile window, or fill/create a pile, to remember its material or contents. Ore, trash, flower and wool piles show the last identified item icon.");
        Widget avoidance=panel.add(new CheckBox("Automatic pathfinding").state(hearthborn.navigation.Walking::enabled).set(hearthborn.navigation.Walking::enabled),stoneColors.pos("bl").adds(0,5));
        avoidance.settip("When enabled, ordinary clicks use pathfinding and Ctrl-click moves directly. When disabled, Ctrl-click uses pathfinding on foot. While mounted, Ctrl-click keeps native dismount handling. Pathfinding is unavailable during combat.");
        Widget mounted=panel.add(new CheckBox("Pathfinding while mounted").state(hearthborn.navigation.Walking::mountedRouting).set(hearthborn.navigation.Walking::mountedRouting),avoidance.pos("bl").adds(0,5));
        mounted.settip("Finds routes while riding a horse. When disabled, mounted clicks use the game's own movement.");
        Widget timers = panel.add(new CheckBox("Show automatic timers") {{a = hearthborn.timers.ObjectTimers.enabled();}}.changed(hearthborn.timers.ObjectTimers::enabled), mounted.pos("bl").adds(0, 5));
        timers.settip("Countdown on smelters, kilns, forges, steel, herbalist tables, and silk.");
        Widget autoStack=panel.add(new CheckBox("Automatic item stacking").state(()->hearthborn.inventory.NativeStacking.shown(options.getparent(GameUI.class))).set(value->hearthborn.inventory.NativeStacking.choose(value,options.getparent(GameUI.class))),timers.pos("bl").adds(0,5));
        autoStack.settip("Sets the game's own item stacking, which stacks picked-up items for you, to this state at every login, and now. Until you tick or untick it, the game's setting is left alone.");
        Label player=panel.add(new Label("Speed on login:"),autoStack.pos("bl").adds(0,15));
        Widget playerSpeed=panel.add(speedSelector(false),Coord.of(UI.scale(220),player.c.y-UI.scale(3)));
        Label horse=panel.add(new Label("Default horse speed:"),Coord.of(0,playerSpeed.c.y+playerSpeed.sz.y+UI.scale(12)));
        Widget horseSpeed=panel.add(speedSelector(true),Coord.of(UI.scale(220),horse.c.y-UI.scale(3)));
        Label switches=panel.add(new Label("Switch on at login:"),Coord.of(0,horseSpeed.c.y+horseSpeed.sz.y+UI.scale(15)));
        switches.settip(L10n.tr("The game leaves these switches off at every login. Each ticked one is switched on once after the character appears, if the character has it. Untick to leave it to you."));
        Widget switchRow=switches;
        for(LoginToggles.Toggle toggle:LoginToggles.Toggle.values())
            switchRow=panel.add(new CheckBox(toggle.label).state(toggle::enabled).set(toggle::enabled),switchRow.pos("bl").adds(switchRow==switches?UI.scale(10):0,5));
        Widget structureWear=panel.add(new CheckBox("Show remaining structure health (%)").state(hearthborn.world.StructureWear::enabled).set(hearthborn.world.StructureWear::enabled),UI.scale(435,0));
        structureWear.settip("Shows remaining health above damaged buildings, walls and other structures, and tints them red. 75% means a quarter is damaged. Fully repaired structures have no label.");
        Widget processes=panel.add(new CheckBox("Automatically create process timers").state(hearthborn.timers.TimerSettings::automatic).set(hearthborn.timers.TimerSettings::automatic),structureWear.pos("bl").adds(0,12))
            .settip("Adds observed production and cheese-aging processes to Timers. Existing timers keep running when disabled.",true);
        Widget protection=panel.add(new CheckBox("Item drop protection").state(ItemProtection::drops).set(ItemProtection::drops),processes.pos("bl").adds(0,12));
        protection.settip("Drop items on cursor only when Ctrl is pressed. Shift+Alt also permits dropping.");
        Widget water=panel.add(new CheckBox("Never drop items into water").state(ItemProtection::water).set(ItemProtection::water),protection.pos("bl").adds(0,5));
        water.settip("An item on the cursor is not dropped on water tiles. Hold Ctrl to drop it anyway.");
        Widget pickup=PickupSettings.add(panel,water);
        Widget centering=panel.add(new CheckBox("Click at tile centers").state(()->Utils.getprefb("hearthborn/world/center-clicks",false)).set(value->Utils.setprefb("hearthborn/world/center-clicks",value)),pickup.pos("bl").adds(0,12));
        centering.settip("Ground clicks go to the center of the tile instead of the exact spot. Useful for precise placement. Can also be set to a key in Keybindings (\"Toggle Tile Centering\").");
        Widget chatLog=panel.add(new CheckBox("Save chat logs").state(hearthborn.io.ChatLogs::enabled).set(hearthborn.io.ChatLogs::enabled),centering.pos("bl").adds(0,12));
        chatLog.settip(hearthborn.L10n.tr("Chat log directory:")+" "+hearthborn.io.ChatLogs.directory()+" / "+hearthborn.L10n.tr("character / YYYY-MM-DD.log")+"\n"+hearthborn.L10n.format("A new file every day, kept for %d days. System messages are not saved.",hearthborn.io.ChatLogs.KEEP_DAYS));
        Widget chatTime=panel.add(new CheckBox("Show timestamps in chat").state(ChatStamp::enabled).set(ChatStamp::enabled),chatLog.pos("bl").adds(0,5));
        Widget chatFrame=panel.add(new CheckBox("Show chat frame").state(ChatUI::framed).set(ChatUI::framed),chatTime.pos("bl").adds(0,5));
        chatFrame.settip("Draws the quest-journal frame and the Chat title. Turn it off for the plain chat window, which grows dim when idle.");
        Widget questFrame=panel.add(new CheckBox("Show quest journal frame").state(QuestHelper::framed).set(QuestHelper::framed),chatFrame.pos("bl").adds(0,5));
        questFrame.settip("Draws the window frame and the Quest journal title. Turn it off for the plain journal: a dark panel with a thin strip on top to drag it and a cross to close it.");
        Widget chatDim=panel.add(new CheckBox("Dim the chat when idle").state(ChatUI::fadeIdle).set(ChatUI::fadeIdle),questFrame.pos("bl").adds(0,5));
        chatDim.settip("Only for the chat without the frame. When the chat is not in focus, nothing is under the pointer and no message came for a few seconds, the panel turns almost transparent. The words dim less. A tab with new messages stays bright.");
        Widget chatOpen=panel.add(new CheckBox("Open chat on a new message").state(ChatButton::openOnMessage).set(ChatButton::openOnMessage),chatDim.pos("bl").adds(0,5));
        chatOpen.settip("A message in any tab opens the chat. Turn this off to leave it closed until you open it. The button then shows how many messages arrived, not counting system lines.");
        Widget chatNotice=panel.add(new CheckBox("Show closed-chat messages on the notice line").state(ChatButton::notices).set(ChatButton::notices),chatOpen.pos("bl").adds(0,5));
        chatNotice.settip("When the chat stays closed, each new message appears on the notice line. System lines already appear there, and your own lines stay in the chat.");
        Widget preview=panel.add(new CheckBox("Container previews only with Shift").state(ContainerPreviews::shiftOnly).set(ContainerPreviews::shiftOnly),chatNotice.pos("bl").adds(0,5));
        preview.settip("Hold Shift to show the contents of stacks, belts and bags on hover.");
        Widget room=panel.add(new CheckBox("Show room plan indoors").state(hearthborn.planner.RoomPlan::enabled).set(hearthborn.planner.RoomPlan::enabled),preview.pos("bl").adds(0,5));
        Widget biome=panel.add(new CheckBox("Show biome tooltip on minimap") {{a=Utils.getprefb("hearthborn/map/biome-tooltip",true);}}.changed(v->Utils.setprefb("hearthborn/map/biome-tooltip",v)),room.pos("bl").adds(0,5));
        Widget tileName=panel.add(new CheckBox("Hide current tile name on minimap").state(hearthborn.map.MapDisplay::hideTerrainName).set(hearthborn.map.MapDisplay::hideTerrainName),biome.pos("bl").adds(0,5));
        Widget partyNames=panel.add(new CheckBox("Show party member names").state(hearthborn.map.PlayerMarkers::partyNames).set(hearthborn.map.PlayerMarkers::partyNames),tileName.pos("bl").adds(0,5));
        partyNames.settip("Party names are retained while this map is open after the character leaves local view. Village membership alone does not provide distant coordinates.");
        panel.add(options.new PButton(UI.scale(200), "Back", 27, options.main),Coord.of(0,Math.max(switchRow.pos("bl").y,partyNames.c.y+partyNames.sz.y)+UI.scale(15)));
        panel.pack();
        SettingsColumns.stack(panel,UI.scale(435));
        return panel;
    }
    private static Widget worldMarks(OptWnd options,OptWnd.Panel panel, Widget previous) {
        previous.settip("Beehives, feeding troughs, and mine supports.");
        previous = panel.add(new CheckBox("Animal warning sounds") {{a = GobMarks.sounds();}}.changed(v -> GobMarks.sounds(v)), previous.pos("bl").adds(0, 5));
        previous.settip("Plays a sound the first time each dangerous animal is seen.");
        previous=panel.add(new CheckBox("Other player warning sounds").state(hearthborn.world.PlayerWarnings::enabled).set(hearthborn.world.PlayerWarnings::enabled),previous.pos("bl").adds(0,5));
        previous.settip("Speaks when another player appears. Includes known players and party members.");
        previous=panel.add(new CheckBox("Use animal calls instead of spoken names").state(GobMarks::calls).set(GobMarks::calls),previous.pos("bl").adds(0,5));
        previous.settip("Animal calls are the same in both languages. Otherwise names are spoken in the interface language.");
        previous = panel.add(new CheckBox("Show dangerous animal radius") {{a = GobMarks.danger();}}.changed(v -> GobMarks.danger(v)), previous.pos("bl").adds(0, 5));
        previous.settip("Red circle around animals that can attack.");
        previous = panel.add(new CheckBox("Show critter and arrow circles") {{a = GobMarks.critters();}}.changed(v -> GobMarks.critters(v)), previous.pos("bl").adds(0, 5));
        previous.settip("Small circles under critters and arrows on the ground. Right-click a critter circle to chase it; click an arrow circle to pick the arrow up.");
        return previous;
    }
    private static SDropBox<Integer,Widget> speedSelector(boolean horse) {
        SDropBox<Integer,Widget> box=new SDropBox<Integer,Widget>(UI.scale(180),UI.scale(80),UI.scale(16)) {
            protected boolean listautoscroll(){return false;}
            protected List<? extends Integer> items(){return Arrays.asList(-1,0,1,2,3);}
            protected Widget makeitem(Integer speed,int index,Coord size) {
                Widget row=new Widget(size);
                String[] names={"Unchanged","Crawl","Walk","Run","Sprint"};
                Label label=new Label(names[speed+1]);row.add(label,Coord.of(UI.scale(3),(size.y-label.sz.y)/2));return row;
            }
            public void change(Integer speed){super.change(speed);if(speed!=null)DefaultSpeed.preferred(horse,speed);}
        };
        box.change(DefaultSpeed.preferred(horse));return box;
    }
    private static SDropBox<Integer,Widget> autoDrink(){
        SDropBox<Integer,Widget> box=new SDropBox<Integer,Widget>(UI.scale(180),UI.scale(96),UI.scale(16)){
            protected boolean listautoscroll(){return false;}
            protected List<? extends Integer> items(){return Arrays.asList(0,25,50,75);}
            protected Widget makeitem(Integer value,int index,Coord size){
                Widget row=new Widget(size);
                Label label=new Label(value==0?"Off":value+"%");
                row.add(label,Coord.of(UI.scale(3),(size.y-label.sz.y)/2));return row;
            }
            public void change(Integer value){super.change(value);if(value!=null)AutoDrink.below(value);}
        };
        box.change(AutoDrink.below());return box;
    }
    private GeneralSettings() {}
}
