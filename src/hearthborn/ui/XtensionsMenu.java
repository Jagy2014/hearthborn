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
import hearthborn.planner.ClaimPlanStatus;
import hearthborn.timers.Timers;

import haven.*;
import java.io.*;

/**
 * Local action pages; no server menu entries are replaced.
 *
 * @author Jagy2014
 */
public final class XtensionsMenu {
    private static final java.util.List<String> CATEGORY_ORDER=java.util.List.of(
        "combat-tools","farming","animals","mining-building","production","journals");
    public enum ClassicLayout {
        NORMAL("4x4","Normal (4×4)",4,4),
        UP("up","Upward (4×12)",4,12),
        SIDE("side","Sideways (12×4)",12,4);
        private final String key;
        public final String label;
        private final int columns,rows;
        ClassicLayout(String key,String label,int columns,int rows){
            this.key=key;this.label=label;this.columns=columns;this.rows=rows;
        }
    }
    private static final String LAYOUT_PREF="hearthborn/ui/xtensions-layout";
    private static volatile ClassicLayout classicLayout=loadLayout();
    private static ClassicLayout loadLayout(){
        String saved=Utils.getpref(LAYOUT_PREF,"side");
        for(ClassicLayout layout:ClassicLayout.values())if(layout.key.equals(saved))return layout;
        return ClassicLayout.SIDE;
    }
    public static ClassicLayout classicLayout(){return classicLayout;}
    public static void classicLayout(ClassicLayout layout){
        classicLayout=java.util.Objects.requireNonNull(layout);Utils.setpref(LAYOUT_PREF,layout.key);
    }
    public static boolean expanded(MenuGrid.Pagina page){
        if(page==null || Toolbars.enabled("actions") || classicLayout()==ClassicLayout.NORMAL)return false;
        try{return page.res().name.equals("hearthborn/xtensions");}
        catch(Loading waiting){return false;}
    }
    public static Coord gridSize(MenuGrid.Pagina page){
        ClassicLayout layout=expanded(page)?classicLayout():ClassicLayout.NORMAL;
        return Coord.of(layout.columns,layout.rows);
    }
    /** Observe before grabs/normal dispatch; the original click still reaches its target. */
    public static void outsideClick(UI ui,Coord rootPoint){
        GameUI gui=ui.root.getchild(GameUI.class);MenuGrid menu=gui==null?null:gui.menu;
        if(menu==null||menu.parent==null||!menu.tvisible()||!expanded(menu.cur))return;
        Widget area=menu.parent.getchild(ActionMenuFrame.class);if(area==null)area=menu;
        if(!area.rootxlate(rootPoint).isect(Coord.z,area.sz))menu.change(null);
    }
    /** Classic overview reuses the existing command buttons, in category order. */
    public static java.util.List<MenuGrid.PagButton> allCommands(MenuGrid menu){
        java.util.List<MenuGrid.PagButton> result=new java.util.ArrayList<>();
        java.util.Map<MenuGrid.PagButton,Integer> groups=new java.util.IdentityHashMap<>();
        java.util.List<MenuGrid.Pagina> pages;
        synchronized(menu.paginae){pages=new java.util.ArrayList<>(menu.paginae);}
        for(MenuGrid.Pagina page:pages)try{
            String path=page.res().name;
            if(!path.startsWith("hearthborn/") || path.equals("hearthborn/xtensions") || path.equals("hearthborn/cheese-animals"))continue;
            if(CATEGORY_ORDER.contains(path.substring("hearthborn/".length())))continue;
            int group=CATEGORY_ORDER.size();boolean member=false;
            java.util.Set<MenuGrid.Pagina> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for(MenuGrid.Pagina parent=page.parent();parent!=null && seen.add(parent);parent=parent.parent()){
                String above=parent.res().name;
                if(above.equals("hearthborn/xtensions")){member=true;break;}
                if(above.startsWith("hearthborn/")){
                    int rank=CATEGORY_ORDER.indexOf(above.substring("hearthborn/".length()));
                    if(rank>=0)group=rank;
                }
            }
            if(member){MenuGrid.PagButton button=page.button();result.add(button);groups.put(button,group);}
        }catch(Loading waiting){/* Unrelated server pages need not block local commands. */}
        result.sort(java.util.Comparator.<MenuGrid.PagButton>comparingInt(groups::get)
            .thenComparing(MenuGrid.PagButton::sortkey).thenComparing(button->button.res.name));
        return result;
    }
    private static final java.util.Set<String> SCRIPT_IDS=java.util.Collections.unmodifiableSet(new java.util.HashSet<>(java.util.Arrays.asList(
        "coracle","skis","cellar","roastspit","chipstone","drycollect","leathercollect","branchoven","wood5","coal9","coal12",
        "cheesetrayfiller","torch","trellisharvest","trellisdestroy","fish","dismount","shoo","clover","rope","milk","butcher","shear")));
    /** Read compatibility only: no menu page is registered under an old identifier. */
    public static String canonicalAction(String path){
        if("hearthborn/cattle-roster-journals".equals(path))return "hearthborn/cattle-roster";
        return path;
    }
    public static boolean isScript(String path){
        return path!=null&&path.startsWith("hearthborn/")&&SCRIPT_IDS.contains(path.substring("hearthborn/".length()));
    }
    private static void migrateBinding(String id){
        String previousId=id.equals("cattle-roster")?"hearthborn/cattle-roster-journals":null;
        if(previousId==null)return;
        String oldKey="keybind/scm/"+previousId,newKey="keybind/scm/hearthborn/"+id;
        String previous=Utils.getpref(oldKey,null);
        if(previous!=null){
            if(Utils.getpref(newKey,null)==null)Utils.setpref(newKey,previous);
            Utils.setpref(oldKey,null);
        }
    }
    static Resource resource(String id, String name, char hotkey) {
        migrateBinding(id);
        Resource.Virtual res = new Resource.Virtual(Resource.local(), "hearthborn/" + id, 1);
        MessageBuf action = new MessageBuf();
        action.addstring("").adduint16(0).addstring(name).addstring("").adduint16(hotkey).adduint16(0);
        res.add(res.new AButton(new MessageBuf(action.fin())));
        res.add(res.new Tooltip(new MessageBuf(name.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        if(id.equals("cattle-roster")||id.equals("cattle-roster-journals")){finishedIcon(res,"menu-cattle-roster");return res;}
        if(id.equals("auto-shoot")){finishedIcon(res,"menu-auto-shoot");return res;}
        boolean painted=true;
        boolean folder=id.equals("production")||id.equals("farming")||id.equals("animals")||id.equals("journals")||id.equals("mining-building")||id.equals("combat-tools");
        String iconId=id.equals("wheat-fields")?"fields":id.equals("cheese-animals")?"cheese":id;
        String imageName="menu-"+((iconId.equals("record-quality"))?"roster":(iconId.equals("coal9")||iconId.equals("coal12"))?"coal":iconId);
        try {
            String folderImage="menu-category-"+id;
            java.awt.image.BufferedImage source=actionImage(id,(folder?folderImage:imageName));
            if(source==null)throw new IOException("Invalid action icon: "+id);
            if(painted)source=visibleBounds(source);
            int side=painted?128:32;
            java.awt.image.BufferedImage icon=new java.awt.image.BufferedImage(side,side,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics=icon.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if(painted){
                graphics.setPaint(new java.awt.RadialGradientPaint(64,64,80,new float[]{0,0.65f,1},
                    folder?new java.awt.Color[]{new java.awt.Color(32,61,88),new java.awt.Color(16,36,59),new java.awt.Color(10,23,40)}:new java.awt.Color[]{new java.awt.Color(77,166,211),new java.awt.Color(29,106,166),new java.awt.Color(12,44,79)}));
                graphics.fillRect(0,0,side,side);
                try(InputStream frameStream=XtensionsMenu.class.getResourceAsStream("/hearthborn/assets/icons/menu-native-frame.png")){
                    if(frameStream==null)throw new IOException("Missing native menu frame");
                    java.awt.image.BufferedImage frame=javax.imageio.ImageIO.read(frameStream);
                    int edge=8;
                    graphics.drawImage(frame,0,0,side,edge,0,0,128,edge,null);
                    graphics.drawImage(frame,0,side-edge,side,side,0,128-edge,128,128,null);
                    graphics.drawImage(frame,0,edge,edge,side-edge,0,edge,edge,128-edge,null);
                    graphics.drawImage(frame,side-edge,edge,side,side-edge,128-edge,edge,128,128-edge,null);
                }
            }
            double scale=(folder?112.0:painted?110.0:32.0)/Math.max(source.getWidth(),source.getHeight());
            int width=(int)Math.round(source.getWidth()*scale),height=(int)Math.round(source.getHeight()*scale);
            graphics.drawImage(source,(side-width)/2,(side-height)/2,width,height,null);
            String amount=id.equals("coal9")?"9":id.equals("coal12")?"12":id.equals("branchoven")?"4":id.equals("wood5")?"5":null;
            if(amount!=null){
                graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setFont(hearthborn.UiFonts.SANS.deriveFont(java.awt.Font.BOLD,46f));
                java.awt.FontMetrics metrics=graphics.getFontMetrics();int x=side-metrics.stringWidth(amount)-12,y=side-13;
                graphics.setColor(new java.awt.Color(9,26,42,230));
                graphics.fillRoundRect(x-5,y-metrics.getAscent()+3,metrics.stringWidth(amount)+10,metrics.getAscent()+6,10,10);
                graphics.setColor(new java.awt.Color(255,231,144));graphics.drawString(amount,x,y);
            }
            graphics.dispose();
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(icon,"png",png);
            MessageBuf image = new MessageBuf();
            image.addint16((short)0).addint16((short)0).adduint8(painted?4:0).addint16((short)-1).addint16((short)0).addint16((short)0);
            if(painted)image.addstring("scale").adduint8(4).addfloat32(4f).addstring("");
            image.addbytes(png.toByteArray());
            res.add(res.new Image(new MessageBuf(image.fin())));
        } catch(IOException e) { throw new RuntimeException(e); }
        return res;
    }

    private static java.awt.image.BufferedImage actionImage(String id,String imageName)throws IOException{
        String glyph=id.equals("action-search")?"csearch":id.equals("claim-personal")?"lbtn-claim":id.equals("claim-village")?"lbtn-vil":id.equals("claim-provinces")?"lbtn-rlm":null;
        if(glyph!=null)return SystemButtonSkin.glyph(glyph);
        if(id.equals("roster")||id.equals("record-quality"))return qualityIcon(id.equals("record-quality"));
        try(InputStream input=XtensionsMenu.class.getResourceAsStream("/hearthborn/assets/icons/"+imageName+".png")){
            if(input==null)throw new IOException("Missing action icon: "+id);
            return javax.imageio.ImageIO.read(input);
        }
    }

    public static java.awt.image.BufferedImage qualityIcon(boolean record){
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(128,128,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new java.awt.RadialGradientPaint(64,64,85,new float[]{0,1},new java.awt.Color[]{new java.awt.Color(51,139,193),new java.awt.Color(12,44,79)}));g.fillRect(0,0,128,128);
        if(record){
            java.awt.Polygon arrow=new java.awt.Polygon(new int[]{8,15,30,49,62,43,68},new int[]{8,80,60,87,75,49,35},7);
            g.setStroke(new java.awt.BasicStroke(6,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));g.setColor(new java.awt.Color(60,35,8));g.drawPolygon(arrow);
            g.setPaint(new java.awt.GradientPaint(8,8,new java.awt.Color(255,237,150),60,80,new java.awt.Color(205,139,38)));g.fillPolygon(arrow);
        }
        g.setFont(hearthborn.UiFonts.SERIF.deriveFont(java.awt.Font.BOLD,record?100f:112f));
        int x=record?39:15,y=record?111:100;
        g.setColor(new java.awt.Color(31,28,21));g.drawString("Q",x+3,y+4);
        java.awt.font.GlyphVector glyph=g.getFont().createGlyphVector(g.getFontRenderContext(),"Q");java.awt.Shape shape=glyph.getOutline(x,y);
        g.setStroke(new java.awt.BasicStroke(4,java.awt.BasicStroke.CAP_ROUND,java.awt.BasicStroke.JOIN_ROUND));g.draw(shape);
        g.setColor(new java.awt.Color(255,224,114));g.fill(shape);
        g.dispose();return image;
    }

    /** Ignore almost-transparent edge noise when fitting the actual object into a small button. */
    static java.awt.image.BufferedImage visibleBounds(java.awt.image.BufferedImage source){
        int left=source.getWidth(),top=source.getHeight(),right=-1,bottom=-1;
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++)
            if((source.getRGB(x,y)>>>24)>=24){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
        return right<left?source:source.getSubimage(left,top,right-left+1,bottom-top+1);
    }

    private static Resource claimIcon;
    public static synchronized Resource claimPlanIcon(){
        if(claimIcon==null){
            Resource.Virtual claim=(Resource.Virtual)resource("claim-plan","Claim plan",'\0');
            String help=hearthborn.L10n.tr("Left click opens the claim plan. Right click toggles scanning. Scanning pauses outside your claims and resumes on return if enabled.");
            claim.add(claim.new Pagina(new MessageBuf(help.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            claimIcon=claim;
        }
        return claimIcon;
    }

    private static MenuGrid.Pagina page(MenuGrid menu, String id, String name, char key,
                                       MenuGrid.Pagina parent, Runnable action) {
        return page(menu,id,name,key,()->parent,action,null);
    }

    /** Action search, which the 4×4 system layout no longer shows as a button. The claim overlays sit in the system row above the grid. */
    private static void systemEntries(MenuGrid menu){
        menu.paginae.add(page(menu,"action-search","Search actions",'\0',()->null,()->{GameUI gui=menu.getparent(GameUI.class);if(gui!=null)gui.toggleActionSearch();},"\uffff2"));
    }

    private static MenuGrid.Pagina page(MenuGrid menu, String id, String name, char key,
                                       java.util.function.Supplier<MenuGrid.Pagina> parentOf, Runnable action, String order) {
        final boolean claimPlan=id.equals("claim-plan");
        final int categoryOrder=CATEGORY_ORDER.indexOf(id);
        Resource res = claimPlan?claimPlanIcon():resource(id, name, key);
        return new MenuGrid.Pagina(menu, res.indir(), res.indir()) {
            private MenuGrid.PagButton button;
            public MenuGrid.PagButton button() {
                if(button == null) button = new MenuGrid.PagButton(this) {
                    public MenuGrid.Pagina parent() { return parentOf.get(); }
                    public String sortkey(){
                        if(order!=null)return order;
                        if(action!=null)return "\1"+name();
                        return "\0"+(categoryOrder<0?"":Integer.toString(categoryOrder))+name();
                    }
                    public void use(MenuGrid.Interaction iact) {
                        if(claimPlan){ClaimPlanStatus.activate(menu.getparent(GameUI.class),iact.btn);return;}
                        if(action == null) menu.change(pag); else action.run();
                    }
                };
                return button;
            }
        };
    }

    public static void install(MenuGrid menu) {
        MenuGrid.Pagina root = page(menu, "xtensions", "Xtensions", 'X', null, null);
        menu.paginae.add(root);
        systemEntries(menu);
        MenuGrid.Pagina production=category(menu,"production","Furnaces and fuel",root);
        MenuGrid.Pagina farming=category(menu,"farming","Farming",root);
        MenuGrid.Pagina animals=category(menu,"animals","Animals and products",root);
        MenuGrid.Pagina journals=category(menu,"journals","Journals",root);
        MenuGrid.Pagina mining=category(menu,"mining-building","Mining",root);
        MenuGrid.Pagina combat=category(menu,"combat-tools","Combat",root);
        menu.paginae.add(page(menu,"clear-player-damage","Clear player damage",'\0',combat,()->hearthborn.combat.FighterMark.clearPlayer()));
        menu.paginae.add(page(menu,"clear-all-damage","Clear all damage",'\0',combat,()->hearthborn.combat.FighterMark.clearAll()));
        script(menu,root,"coracle","Use or pick up coracle");
        script(menu,root,"skis","Use or pick up skis");
        MenuGrid.Pagina mount=page(menu,"mount-horse","Call and mount horse",'\0',root,()->hearthborn.scripts.ShortcutRunner.run(menu.getparent(GameUI.class),"BOT_MOUNT_HORSE"));
        String mountTip=hearthborn.L10n.tr("Highlights the nearest available horse and mounts it. Calls a distant horse with a whistle first; mounts a nearby horse without whistling.");
        ((Resource.Virtual)mount.res()).add(mount.res().new Pagina(new MessageBuf(mountTip.getBytes(java.nio.charset.StandardCharsets.UTF_8))));menu.paginae.add(mount);
        script(menu,mining,"cellar","Cellar Digging Bot");
        script(menu,animals,"roastspit","Roasting Spit Bot");
        script(menu,mining,"chipstone","Chip stone");
        script(menu,animals,"drycollect","Collect finished drying frames");
        script(menu,animals,"leathercollect","Collect finished leather");
        script(menu,production,"branchoven","Add 4 branches to oven");
        script(menu,production,"wood5","Add 5 wood blocks to smoke shed");
        script(menu,production,"coal9","Add 9 coal to smelter");
        script(menu,production,"coal12","Add 12 coal to smelter");
        script(menu,animals,"cheesetrayfiller","Fill cheese trays");
        script(menu,production,"torch","Light object with torch");
        script(menu,farming,"trellisharvest","Harvest trellis area");
        script(menu,farming,"trellisdestroy","Destroy plants in trellis area");
        script(menu,animals,"fish","Butcher fish");
        script(menu,combat,"dismount","Dismount rider");
        for(int school=1;school<=5;school++){
            final int number=school;
            menu.paginae.add(page(menu,"combat-school-"+number,hearthborn.L10n.format("Combat school %d",number),'\0',combat,()->{
                GameUI gui=menu.getparent(GameUI.class);if(gui==null)return;
                FightWnd fight=gui.chrwdg==null?null:gui.chrwdg.fight;
                if(fight==null||!fight.activateSaved(number-1))gui.error(hearthborn.L10n.format("Combat school %d is not saved.",number));
            }));
        }
        script(menu,animals,"butcher","Butcher animals");
        script(menu,animals,"shoo","Shoo nearest animal");
        script(menu,animals,"clover","Use clover");
        script(menu,animals,"rope","Use rope");
        script(menu,animals,"milk","Collect milk into cistern");
        script(menu,animals,"shear","Shear sheep and goats");
        MenuGrid.Pagina wheat=page(menu,"wheat-fields","Process field",'\0',farming,()->hearthborn.scripts.FieldWindow.activate(menu.getparent(GameUI.class),menu.ui.modshift));
        String wheatTip=hearthborn.L10n.tr("Save field boundaries, planting storage and a harvest stockpile area. Choose the planting crop for an empty or mixed field.")+"\n"
            +hearthborn.L10n.tr("Supports grains, fibre crops, pipeweed, carrots, turnips, onions, garlic and leeks. Harvest only collects the crop; Process also selects planting material and sows empty tiles. Young and unrelated crops are kept.")+"\n"
            +hearthborn.L10n.tr("Starts the saved field containing your character and opens its compact control panel. Shift-click opens the field list without starting work. Outside saved fields or with missing settings, the field list opens instead. Leave eight inventory slots free for a scythe; its swing can affect crops outside the selected boundary.");
        ((Resource.Virtual)wheat.res()).add(wheat.res().new Pagina(new MessageBuf(wheatTip.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        menu.paginae.add(wheat);
        cattle(menu,journals,"cattle-roster");
        menu.paginae.add(page(menu,"quest-help","Quest journal",'\0',journals,()->{GameUI gui=menu.getparent(GameUI.class);if(gui!=null)gui.questHelper.toggleWindow();}));
        Resource.Virtual sweeper=(Resource.Virtual)resource("minesweeper","Mine markers",'\0');
        String description=hearthborn.L10n.resource("pagina","paginae/add/minesweeper","Toggle the mine marking cursor. Left click cycles: cross, check mark, question mark, clear. Right click toggles a dust clue between permanent and temporary. Escape exits. Mining safety options are in Options / Mining safety.");
        sweeper.add(sweeper.new Pagina(new MessageBuf(description.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        menu.paginae.add(new MenuGrid.Pagina(menu,sweeper.indir(),sweeper.indir()) {
            private MenuGrid.PagButton button;
            public MenuGrid.PagButton button(){
                if(button==null)button=new MenuGrid.PagButton(this){
                    public MenuGrid.Pagina parent(){return mining;}
                    public String sortkey(){return "\1"+name();}
                    public void use(MenuGrid.Interaction interaction){hearthborn.mining.Minesweeper.toggle(menu.getparent(GameUI.class),true);}
                };
                return button;
            }
        });
        cheese(menu,journals,"cheese");
        cheese(menu,animals,"cheese-animals");
        menu.paginae.add(page(menu,"record-quality","Record quality",'\0',journals,()->hearthborn.quality.QualityRoster.toggleRecording(menu.getparent(GameUI.class))));
        menu.paginae.add(page(menu, "roster", "Quality roster", '\0', journals, () -> {
            GameUI gui=menu.getparent(GameUI.class);
            if(gui!=null)hearthborn.quality.QualityRoster.toggle(gui);
        }));
        menu.paginae.add(page(menu, "dream", hearthborn.L10n.tr("Harvest dreams"), 'd', animals, () -> {
            GameUI gui = menu.getparent(GameUI.class);
            if(gui != null) gui.dreamHarvester.toggle();
        }));
        menu.paginae.add(page(menu,"claim-plan","Claim plan",'\0',journals,()->hearthborn.planner.ClaimPlan.toggle(menu.getparent(GameUI.class))));
        menu.paginae.add(page(menu, "timers", "Timers", '\0', journals, () -> {
            GameUI gui = menu.getparent(GameUI.class);
            if(gui != null) Timers.toggle(gui);
        }));
    }

    private static void cheese(MenuGrid menu,MenuGrid.Pagina parent,String id){
        menu.paginae.add(page(menu,id,"Cheese chart",'\0',parent,()->{
            GameUI gui=menu.getparent(GameUI.class);
            if(gui!=null)hearthborn.cheese.CheeseChart.toggle(gui);
        }));
    }
    private static void cattle(MenuGrid menu,MenuGrid.Pagina parent,String id){
        MenuGrid.Pagina cattle=page(menu,id,"Cattle roster",'\0',parent,()->openCattle(menu));
        String tip=hearthborn.L10n.resource("pagina","hearthborn/cattle-roster","Open the cattle roster.");
        ((Resource.Virtual)cattle.res()).add(cattle.res().new Pagina(new MessageBuf(tip.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        menu.paginae.add(cattle);
    }

    private static void finishedIcon(Resource.Virtual res,String imageName){
        try(InputStream input=XtensionsMenu.class.getResourceAsStream("/hearthborn/assets/icons/"+imageName+".png")){
            if(input==null)throw new IOException("Missing action icon: "+imageName);
            java.awt.image.BufferedImage icon=javax.imageio.ImageIO.read(input);
            ByteArrayOutputStream png=new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(icon,"png",png);
            MessageBuf image=new MessageBuf();
            image.addint16((short)0).addint16((short)0).adduint8(4).addint16((short)-1).addint16((short)0).addint16((short)0);
            image.addstring("scale").adduint8(4).addfloat32(4f).addstring("");
            image.addbytes(png.toByteArray());
            res.add(res.new Image(new MessageBuf(image.fin())));
        }catch(IOException e){throw new RuntimeException(e);}
    }
    /** Uses the original roster button when the server has sent it, so the old menu entry stays in place. */
    static void openCattle(MenuGrid menu){
        java.util.List<MenuGrid.Pagina> pages;
        synchronized(menu.paginae){pages=new java.util.ArrayList<>(menu.paginae);}
        for(MenuGrid.Pagina page:pages){
            try{
                if(page.res().name.equals("paginae/act/croster")){
                    page.button().use(new MenuGrid.Interaction(1,menu.ui==null?0:menu.ui.modflags()));
                    return;
                }
            }catch(Loading ignored){}
        }
        if(menu.ui!=null&&menu.ui.modshift)menu.wdgmsg("act","croster","a");
        else menu.wdgmsg("act","croster");
    }
    private static MenuGrid.Pagina category(MenuGrid menu,String id,String title,MenuGrid.Pagina parent){
        MenuGrid.Pagina category=page(menu,id,title,'\0',parent,null);menu.paginae.add(category);return category;
    }

    private static void script(MenuGrid menu,MenuGrid.Pagina parent,String id,String title){
        MenuGrid.Pagina script=page(menu,id,title,'\0',parent,()->{
            if(id.equals("milk")&&menu.ui.modshift){hearthborn.scripts.MilkSettings.open(menu.getparent(GameUI.class));return;}
            hearthborn.scripts.ScriptRunner.run(menu.getparent(GameUI.class),menu.ui.modshift?(id.equals("drycollect")?"dryhang":id.equals("leathercollect")?"leatherload":id):id);
        });
        String tip=scriptTip(id);
        ((Resource.Virtual)script.res()).add(script.res().new Pagina(new MessageBuf(tip.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        menu.paginae.add(script);
    }

    private static String scriptTip(String id){
        String english;
        switch(id){
        case "coracle":english="Boards or picks up your $col[186,85,255]{coracle}.\n\n"+
            "1. Already aboard: the script picks it up.\n"+
            "If the cape slot is empty, the coracle is placed there.\n"+
            "If the cape slot is taken, a free $col[186,85,255]{4×3} inventory area is required.\n"+
            "$col[255,64,64]{Warning:} in deep water, pickup works only beside shore. Surrounded by deep water, the script stops.\n\n"+
            "2. Coracle in your inventory: stand in $col[255,160,48]{shallow water} or a $col[255,160,48]{swamp} before you start. The script drops it and boards.\n"+
            "$col[255,64,64]{Warning:} deep water and dry ground stop the script immediately.\n\n"+
            "3. No coracle among your belongings: boards the nearest empty coracle nearby.\n\n"+
            "$col[255,196,48]{Note:} Escape or another automation click stops the script. Empty the cursor before starting.";break;
        case "skis":english="Deploys your wilderness skis and mounts them; when already skiing, picks them up. Can also use empty nearby skis.\nIf the cape slot is occupied, keep a free 2×3 inventory area.";break;
        case "cellar":english="Repeats cellar digging and chips ordinary boulders blocking progress. Requires a loaded cellar door. Attempts to drink at low stamina.\nStops below 25% energy; at critically low health attempts to travel home. Stones on the cursor are dropped on the ground.";break;
        case "roastspit":english="Loads prepared raw food onto the nearest existing roasting spit, roasts it, carves it and repeats.\nRequires a fire with an installed spit within two tiles, suitable food and inventory space. Does not build or light the fire.";break;
        case "chipstone":english="Chips nearby ordinary boulders until none remain in range. Precious gem boulders are excluded.\nStones on the cursor are dropped on the ground.";break;
        case "drycollect":english="Left click: collect finished products from nearby drying frames.\nShift-click: hang raw hides from inventory and open bags.\nKeep free inventory space for collection.";break;
        case "leathercollect":english="Left click: collect finished leather from nearby tanning tubs.\nShift-click: load dried hides from inventory and open bags.\nKeep free inventory space for collection.";break;
        case "branchoven":english="Adds exactly 4 branches to the nearest oven within three tiles.\nPut the branches in your main inventory. Adds fuel only; does not ignite the oven.";break;
        case "wood5":english="Adds exactly 5 wood blocks to the nearest smoke shed within three tiles.\nUses blocks from inventory, equipment bags and open stacks. Adds fuel only; does not ignite the shed.";break;
        case "coal9":english="Adds exactly 9 coal to the nearest ore smelter within three tiles.\nKeep the coal in your main inventory. Adds fuel only; does not ignite the smelter.";break;
        case "coal12":english="Adds exactly 12 coal to the nearest ore smelter within three tiles.\nKeep the coal in your main inventory. Adds fuel only; does not ignite the smelter.";break;
        case "cheesetrayfiller":english="Fills empty cheese trays in open windows, using 4 pieces of the same curd type per tray.\nOpen the trays and keep enough matching curd in your main inventory.";break;
        case "torch":english="Lights the nearest supported oven, kiln, smelter, crucible or cauldron using a lit torch.\nEquip a lit torch in a hand or hold it on the cursor. An equipped torch is returned to its original slot.";break;
        case "trellisharvest":english="Select an area with the crosshair, then harvest every ripe trellis plant inside.\nIncludes multiple trellises in one tile; skips unripe plants. Keep your scythe equipped.\nWaits for each harvest to finish. Escape or manual movement cancels.";break;
        case "trellisdestroy":english="Select an area with the crosshair: destroys every trellis plant inside, including unripe plants.\nTargets plants individually, retaining bare trellises. Waits for each destruction to finish.\nEscape or manual movement cancels; destroyed plants cannot be restored.";break;
        case "fish":english="Butchers fish from your main inventory, repeating while suitable fish remain.\nKeep free inventory space for the products.";break;
        case "dismount":english="Chooses Dismount rider on the nearest standing horse.\nThe action must be available in that horse's menu.";break;
        case "butcher":english="Visits carcasses within 20 tiles of where you start.\nUses pathfinding to approach clear ground beside each body, with alternate approaches if blocked.\nFinishes one carcass before the next: skin, clean, meat, then bones. Uses fresh menus and waits for progress and inventory receipts; no fixed action counts.\nSkips unreachable bodies and animals without butchering actions. Keep free inventory space for the products.\nEscape or manual movement stops the helper.";break;
        case "shoo":english="Chooses Shoo on the nearest standing domestic animal: horse, sheep, cattle, pig or goat.\nThe action must be available in its menu.";break;
        case "clover":english="Offers one clover from your main inventory to the nearest suitable standing animal.\nRequires clover and an animal that accepts it.";break;
        case "milk":english="Stand inside a saved collection area and start: adult females of this pen are milked one by one into a barrel, which is poured into the nearest cistern. Outside all saved areas you select a new one by dragging on the map.\nNeeds an empty barrel nearby. Shift-click: list of areas.";break;
        case "shear":english="Shears the sheep and goats of the pen you stand in (a saved area, as for milking).";break;
        case "rope":english="Uses a rope from your main inventory on the nearest suitable standing animal.\nThe rope remains on the cursor for leading the animal.";break;
        default:throw new IllegalArgumentException("Missing automation instructions: "+id);
        }
        if(!id.equals("coracle"))english+="\n\nEscape or another automation click stops the current script. Empty the cursor before starting, except for a lit torch.";
        return hearthborn.L10n.resource("pagina","hearthborn/"+id,english);
    }

    private XtensionsMenu() {}
}
