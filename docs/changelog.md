# Changelog

All notable changes to **Hearthborn Client** are listed here, newest first.

## v1.3.0

### Map

- **Grouped markers:** Markers with the same name start collapsed in the full map and the separate map window. Click the group to see individual markers. Search still finds markers by name, number or quality.
- **Russian map labels:** Fixed English names on saved natural markers, from burrows and reefs to springs and mineral deposits. Manual marker names are unchanged.
- **Map display settings:** Removed duplicate herb icons while keeping visibility and sound settings. Wetland water and land now have distinct names, as do other different objects that shared a Russian label.
- **Credo gathering objectives:** Click the item icon next to a supported objective, such as gathering Waybroad, and choose **Show on map**. This highlights biomes where the item can be found.
- **Dungeon entrances:** Wolf and bat lairs and other supported entrances have map icon settings and stay marked after you leave. Ant dungeons are separate from ordinary anthills. Rock Crystal can be enabled before you discover it.
- **Icon visibility:** Individual checkboxes now hide saved markers as well. Natural resources and dungeon entrances are enabled by default without overriding existing choices. Marker names can be hidden automatically when zoomed out. Hover over an icon in map settings to see its English name.
- **Road markers:** Stone and wooden road signs and posts remain visible on the map once discovered. Each type has its own visibility checkbox, and names appear only on hover.
- Updated map icons for mine entrances, ladders up, gems, squirrel caches, and hearth fires. The mine level is still shown on the map and in the marker list, but no longer appears in a circle next to icons.
- **Map indoors:** General settings now lets you choose **Regular map**, **Room plan**, or **World map**. World map keeps the outdoor view and marks where you entered with a gold dot. On the regular map, updating the current room no longer erases previously saved tiles for neighboring houses and cellars.
- Round map buttons have no backing strip, with or without gold frames. Double-click a map resize corner to restore the size before the last resize rather than the size at login.
- **Village plans:** A new plan no longer inherits the name of another village you belong to. The village idol is labeled separately from the claim.

### Notifications

- The Lady’s Mantle indicator disappears silently when the gathering window ends. The start alert is unchanged.
- Battering rams and wrecking balls alert once per session. Entering and leaving a house or mine does not reset the alert: later sightings show only the arrow and map cross. To suppress repeat alerts after logging back in too, turn on **Do not repeat ram alerts after relogging** in General settings.
- Auto-drink now recognizes spring water in flasks and other supported containers.

### Farming

- **Field card:** Running field processing from a saved field opens its own compact card with crop readiness, recent harvest, and controls. The full list remains available with Shift or the All fields button.
- Interrupted field work can be resumed from the field card.
- Milk collection now reports total liters and volume-weighted average quality at the end of each pen. The character also looks for a path around obstacles before trying to lift the barrel.

### Production and timers

- Server wait times create timers for village idol readiness and personal claim expansion.
- Resource inspection shows real-world time alongside in-game refill time and uses the server's more precise estimate when available. The Timers window opens correctly after switching characters.
- An open gridiron takes priority over carried bags when transferring matching meat, including from stacks. Gridirons have numbered cooking timers and a completion notification.
- Added a **Salt all** button next to **Eat** on tables. It can use salt from your backpack or an open barrel nearby.
- The cheese chart now has a translucent background in the dark theme.

### Combat

- **Sword auto-combat:** The existing auto-combat toggle recognizes your sword even if you equip it mid-fight. With the required moves in the active deck, it builds initiative and attacks with the sword. If a move is missing, the client tells you once. It pauses only after three consecutive moves go unconfirmed, not after the first one.
- **Target selection before combat:** Ctrl+K marks the wild animal under the cursor, Ctrl+J and Ctrl+Shift+J cycle to the next and previous animal, and Ctrl+L attacks the marked target. Selecting a target does not start combat, and livestock are skipped. These shortcuts can be reassigned in **Keybindings → Additional**. You can also shoot the marked target or use a separate action to attack the nearest wild animal without marking it first.
- **Draw bow / Draw sword:** Two occupied hands are cleared safely, using one cell per item in the belt and then the backpack when needed. Switching back restores the sword and shield to their previous hands if they are still carried.
- Combat pursuit buttons fit within the panel, and Tab switches combat targets again.
- Added **Keep interface visible in combat** to Combat settings. Alt+Space can still toggle the compact interface manually.

### Interface

- The language selector is now at the top of Interface display settings.
- **Docked windows:** Resize the map, chat, and quest tracker as a single stack by dragging the bottom corner of the lowest window. Docked chat and quest windows no longer have separate title bars, and their close buttons move into the content row. Both windows use a translucent background in the dark theme.
- Interface display settings can hide the gold frames around the portrait, speed controls, minimap, chat, and quest tracker. The frame around the heart stays visible.
- The Equipment, Character Sheet, and separate Study windows now have translucent backgrounds in the dark theme.
- Sand now falls in the hourglass while an action is in progress.
- The “…” buttons next to the additional toolbars take you directly to their keyboard shortcuts.
- Hotkey and equipment toolbars now always use the compact style without a backing plate. The Clean toolbars checkbox has been removed.
- Crafting controls take up less space, and picking a recipe from crafting history keeps the window in place. Detailed action and inventory tooltips wait one second.
- Added an option to keep the study widget visible when every slot is occupied. The right pane of the character sheet now grows with the window.
- Fixed additional Russian credo objectives, quest icons, experience descriptions, item names, wounds, and server messages. Original objective names are available for looking them up. Trade invitations and additional variants of party and kinship invitations now have Russian translations.

---

## v1.2.1

### Map and quests

- **Find markers** by name, number or quality on the full map and in the separate map window. The clear button resets the search without removing markers. Deleting the selected marker is a separate action.
- **Natural resources** get persistent numbers and labels such as **Salt Basin 1 (q16)** on the map and in the marker list. Quality is recorded from inspection or fresh yields, including tar, ancient roots, fairy mushrooms and foul smoke.
- When inspection reports a refill time, the countdown appears in the **Natural resources** timer tab. Inspecting a meteorite can also start a cooldown timer.
- **Quest gathering hints** highlight suitable terrain. Starting a search collapses the hint into a chip on the map. Click the name to reopen it or the cross to stop searching. Completing the objective clears the highlight and temporary icon settings without changing normal filters.
- Completing one objective no longer collapses the remaining objectives into a progress counter.
- The full map fits inside the game window. The marker list is wider, with full names on hover. The separate map window supports keyboard input and scrolls in the same direction as the main map.
- Map display settings open above the map and use the available height. **Herbs** and other **Forageables** are separate. Added the anthill icon, removed the duplicate snapdragon entry, and suppressed duplicate labels for overlapping markers.

### Scripts and inventory

- **Water, clay, sand and soil quality sampling** in Xtensions takes one sample per click. Water uses an empty waterskin; the other scripts sample the tile under you.
- **Shore Scout** follows the shore by boat, samples water and clay, and can collect mussels. Its route and measurements stay on the map.
- **Hand equipment** in Xtensions uses game icons for available items. Double-click an item in the inventory or belt to swap it into your hands. Removed gear goes back into the belt when possible.
- Added **backpack auto-drop** controls: left-click to toggle; right-click to open the rules and item-type manager.
- In Shift-only preview mode, you can release Shift once the pointer is inside a belt, bag or stack preview.
- Each button on the two additional toolbars can have its own hotkey. General settings and map display settings have a search clear button.

### Other changes

- Furnaces display their saved Quality Journal value, even after logging back in. The numeric countdown has a small static clock icon.
- New village claim plans are no longer named after an unrelated village. The quality journal preserves separate building records.
- Right-clicking wooden, brick or stone fences or palisades no longer starts local pathfinding.
- Fixed meteorite ore pile icons and prevented hidden trees from flashing into view while terrain loads.
- Added Russian translations for quest objectives, natural resources, meteoric iron items and the combat pursuit button.

---

## v1.2.0 - Steam

- **Windows:** fixed a blank or stuck window when restarting the Steam client in fullscreen mode.

---

## v1.1.1

### Language

- The client now starts in English. The language you chose before is kept.
- To switch to Russian: on the login screen press the language button in the bottom-left corner. In the game press Ctrl+O to open the settings, type "language" in the search field, click the Language field (it is the first setting) and choose Russian. In the game the language applies after you close the client window completely and start it again.
- Русский: на экране входа нажмите кнопку языка в левом нижнем углу. В игре нажмите Ctrl+O, введите в поиске «язык» или "language", нажмите на поле «Язык» (оно первое в настройках) и выберите «Russian». В игре язык меняется после полного закрытия клиента и нового запуска.

### Interface

- A map icon for the moon moth was added to the list of icons.

---

## v1.1.0

Fixes and small improvements since v1.0.1.

### Fixes

- Left and right mouse clicks interrupt an action in progress (digging, chopping, crafting) again, not only Esc.
- A click on a fence or a fence corner is no longer swallowed by pathfinding, so fences can be built again.
- A menu choice such as "Dig clay" is no longer dropped when no route to the object is found: it goes to the server like in the plain game.
- Shearing script: the shears are placed into the hand slot instead of staying on the cursor.
- The wear label above the boat you are sitting in shows the same value as the boat bar.

### Interface

- Button panels no longer lie on top of one another: a panel dropped on another slides off it, and panels saved on top of each other are pulled apart.
- The ortho and free ortho cameras zoom out as far as the simple camera.
- New players start with the Xtensions menu opening sideways and with no terrain search switched on.
- More Russian translations, including the "route from the previous milestone is blocked" message.

---

## v1.0.1

The first public release of **Hearthborn Client**, a modified Haven & Hearth vanilla client inspired by Amber, Ender, and Hurricane.

Hearthborn focuses on improving the user interface, automating repetitive tasks, and making everyday gameplay more convenient while preserving the original game experience.

The client is approximately **98% translated into Russian**.

### User Interface & Inventory

- Redesigned user interface with customizable panels and settings.
- Improved item transfers, inventory management, and sorting.
- Cupboard sorting and automatic content icons displayed above cupboards.
- Additional information about items and objects.
- Quality highlighting with customizable colors and quality thresholds.
- Tree stumps remain visible when trees are hidden.

### Village & Personal Claim Planner

- Automatically generates plans of villages and personal claims.
- Displays buildings, fields, trees, smelters, and other structures.
- Supports custom areas, labels, and building names.
- Named buildings can display their labels directly in the game.
- Shows recorded quality information for supported objects.
- Plans can be exported and imported into another client.

### Quality Journal

- Records and organizes item quality information.
- Automatically remembers seed quality when planting crops.
- Compares planting quality with previously recorded values.
- Notifies you when better-quality seeds are available.
- Helps track quality improvements across production and farming.

### Automatic Timers

Timers are created automatically when supported production processes begin.

- Ore smelting.
- Clay firing and brick production.
- Baking.
- Silk production.
- Steel production.
- Other supported production processes.

The client notifies you when production is complete. Steel production also includes advance reminders to add fuel.

### Cheese Making Assistant

- Cheese production reference table.
- Displays expected cheese results on cheese racks.
- Shows possible next stages and where cheese should be moved.
- Helps plan cheese production without repeatedly checking external references.

### Farming & Animal Care

- Automated field harvesting and replanting.
- Sheep and goat shearing.
- Milk collection into a cistern.
- Milk quality reporting.

### Production & Processing Automation

- Place raw hides onto drying frames and collect dried hides.
- Move hides into tanning tubs and collect them when ready.
- Place cheese into cheese trays.
- Animal carcass processing.
- Automatic removal of unwanted items from inventory.
- Fish processing and inventory cleanup.

### Mining & Resource Management

- Mining assistant with a customized approach to mining safety.
- Rock stockpiles are colored according to their contents.
- Clay and brick stockpiles have distinct colors.
- Ore stockpiles display ore icons.
- Other stockpiles, including flowers, intestines, and entrails, display content icons.
- Hold **Space** to display rock names on stockpiles.
- Meteorite tracking assistant.

### Map in a Separate Window

- Open the live map in its own window and move it to a **second monitor**.
- The separate map stays fully interactive, shares its markers with the map inside the game, and remembers its size and place.

### Pathfinding & Movement

- Improved pathfinding for walking and horseback riding.
- Both pathfinding modes can be disabled in settings.
- Designed to make everyday navigation and travel easier.

### Combat Assistance

**Automatic Combat - Stage 1**

- Basic automatic attacks against animals.
- Particularly useful for beginners fighting ants, bats, and other creatures.
- Currently performs attacks only, without combat maneuvers.
- Further development is planned.

**Archery Assistant**

- Adds a shooting button during ranged combat.
- Automatically shoots at the last selected target.

### Privacy

The client doesn’t send any data anywhere except the default communication with the Haven & Hearth servers.

### And More

This release includes numerous smaller improvements, helpers, and quality-of-life features that are not individually listed here.

Explore the settings and discover what else is available.

---

**Special thanks** to the developers of the vanilla client, Amber, Ender, and Hurricane, whose work and ideas helped inspire Hearthborn.

Thank you to everyone supporting the project!
