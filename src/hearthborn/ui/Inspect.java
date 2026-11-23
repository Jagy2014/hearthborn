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
import hearthborn.timers.ObjectTimers;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Hover details for the native Inspect action, retaining its server clicks.
 *
 * @author Jagy2014
 */
public final class Inspect {
    private final GameUI gui;
    private boolean pending;
    private boolean mining;
    private boolean destroying;
    private long generation;
    private double elapsed;
    private Coord position;
    private Gob object;
    private Coord2d ground;
    private String previous;
    private Text rendered;
    private static final class Inspected {
        final Gob gob;final double until;
        Inspected(Gob gob){this.gob=gob;until=Utils.rtime()+10;}
    }
    private final Deque<Inspected> inspected=new ArrayDeque<>();
    private Inspected resourceInspect;
    private String priorRefill;
    private GAttrib priorRefillInfo;
    public void clicked(Gob gob){
        if(gob==null||!active())return;
        inspected.addLast(new Inspected(gob));while(inspected.size()>16)inspected.removeFirst();
        hearthborn.timers.MeteorTimers.clicked(gob);
        MapFile.SMarker marker = hearthborn.map.MarkerQuality.naturalMarker(gob);
        clearResourceInspect();
        if(marker != null || ObjectTimers.tarKiln(gob)) {
            resourceInspect = new Inspected(gob);
            if(marker != null) hearthborn.map.MarkerQuality.number(marker);
            priorRefillInfo = attribute(gob, "haven.res.ui.inspect.SavedInfo");
            priorRefill = savedTimerLine(gob, priorRefillInfo);
        }
    }
    private static final java.util.regex.Pattern QUALITY=java.util.regex.Pattern.compile("^(?:Quality|Качество)\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)\\.?$");
    /** The number of a plain "Quality: 44" inspect line, or null for any other message. */
    public static Double qualityValue(String message){
        if(message==null)return null;
        java.util.regex.Matcher match=QUALITY.matcher(message.trim());
        return match.matches()?Double.valueOf(match.group(1).replace(',','.')):null;
    }
    public static String namedQuality(String name,String message){
        if(name==null||message==null)return message;
        java.util.regex.Matcher match=java.util.regex.Pattern.compile("^(?:Quality|Качество)\\s*:\\s*([0-9]+(?:[.,][0-9]+)?)\\.?$").matcher(message.trim());
        return match.matches()?name+" · "+hearthborn.L10n.tr("Quality")+": "+match.group(1):message;
    }
    /** Keep every line of one inspect together, including a note that has no percentage. */
    public static String status(String previous,String next){
        if(next==null)return previous;
        if(previous==null||previous.equals(next))return next;
        java.util.List<String> progress=new java.util.ArrayList<>();
        java.util.List<String> notes=new java.util.ArrayList<>();
        String quality=null;
        for(String piece:(previous+"\n"+next).split("\n")){
            String line=piece.trim();
            if(line.isEmpty())continue;
            if(quality(line))quality=line;
            else if(fullness(line)){if(!progress.contains(line))progress.add(line);}
            else if(!notes.contains(line))notes.add(line);
        }
        if(quality==null){
            if(!notes.isEmpty()||progress.isEmpty())return next;
            StringBuilder progressText=new StringBuilder();
            for(String line:progress)append(progressText,line);
            return progressText.toString();
        }
        StringBuilder text=new StringBuilder();
        for(String line:progress)append(text,line);
        for(String line:notes)append(text,line);
        append(text,quality);
        return text.toString();
    }
    private static void append(StringBuilder text,String line){if(text.length()>0)text.append('\n');text.append(line);}
    private static boolean quality(String line){
        return line.matches("^(?:.*·\\s*)?(?:Quality|Качество)\\s*:\\s*[0-9]+(?:[.,][0-9]+)?\\.?$");
    }
    private static boolean fullness(String line){
        return line.toLowerCase(java.util.Locale.ROOT).matches(".*\\d+\\s*%.*");
    }
    private static final Text.Foundry FONT=new Text.Foundry(Text.sans,14).aa(true);
    /** One plate, with a row for each line of an inspect result. The ink is the notice color, in the quality cut. */
    public static Text plate(String text,java.awt.Color color){
        String[] parts=text==null?new String[0]:text.split("\n",-1);
        java.util.List<String> lines=new java.util.ArrayList<>();
        for(String part:parts)if(!part.isEmpty())lines.add(part);
        if(lines.isEmpty())return new Lines("",hearthborn.QualityOverlay.styled(FONT.render("",color).img));
        Text.Line[] rows=new Text.Line[lines.size()];
        int width=1,height=0;
        for(int i=0;i<lines.size();i++){
            rows[i]=FONT.render(lines.get(i),color);
            width=Math.max(width,rows[i].sz().x);height+=rows[i].sz().y;
        }
        java.awt.image.BufferedImage image=TexI.mkbuf(Coord.of(width,height));
        java.awt.Graphics graphics=image.createGraphics();
        int y=0;
        for(Text.Line row:rows){graphics.drawImage(row.img,0,y,null);y+=row.sz().y;}
        graphics.dispose();
        return new Lines(text,hearthborn.QualityOverlay.styled(image));
    }
    private static final class Lines extends Text {Lines(String text,java.awt.image.BufferedImage image){super(text,image);}}
    public UI.Notice namedNotice(UI.Notice notice){
        if(notice==null||notice.message()==null)return notice;
        if(namedQuality("",notice.message()).equals(notice.message()))return notice;
        while(!inspected.isEmpty()&&inspected.peekFirst().until<Utils.rtime())inspected.removeFirst();
        Gob target=inspected.isEmpty()?(active()?object:null):inspected.removeFirst().gob;
        if(target==null)return notice;
        Double told=qualityValue(notice.message());
        if(told!=null)hearthborn.map.MarkerQuality.remember(target,told);
        try{
            String name=hearthborn.world.ObjectNames.name(target);Resource resource=hearthborn.util.GameObjects.resource(target);
            if(name==null&&resource!=null)name=hearthborn.L10n.objectName(resource,hearthborn.util.GameObjects.name(resource.name));
            final String message=namedQuality(name,notice.message());
            return new UI.Notice(){
                public String message(){return message;}
                public java.awt.Color color(){return notice.color();}
                public Audio.Clip sfx(){return notice.sfx();}
                public boolean handle(Widget widget){return notice.handle(widget);}
                public boolean handler(Widget widget){return notice.handler(widget);}
            };
        }catch(Loading waiting){return notice;}
    }


    public Inspect(GameUI gui) { this.gui = gui; }

    private boolean active(Widget widget) {
        for(Class<?> type = widget.getClass(); type != null; type = type.getSuperclass())
            if(type.getName().equals("haven.res.ui.inspect.LocalInspect")) return true;
        if(widget instanceof Window || widget instanceof Inventory || widget instanceof GItem || widget instanceof MapView)
            return false;
        for(Widget child = widget.child; child != null; child = child.next)
            if(child.visible() && active(child)) return true;
        return false;
    }

    public void action(Object... args) {
        destroying=args.length>0&&"destroy".equals(args[0]);
        clear();
    }

    private boolean active() {
        if(destroying)return false;
        mining = false;
        if(gui.map == null || gui.ui == null) return false;
        Object curs = gui.ui.getcurs(gui.ui.mc);
        if(curs instanceof Resource) {
            String name = ((Resource)curs).name;
            if(name.endsWith("/inspect") || name.endsWith("/study")) return true;
            if(name.endsWith("/mine")) { mining = true; return true; }
        }
        return active(gui);
    }

    public void refillNotice(String message) {
        if(resourceInspect != null && resourceInspect.until >= Utils.rtime()
           && (ObjectTimers.observeTarKiln(resourceInspect.gob, message)
               || hearthborn.timers.NaturalResourceTimers.observe(resourceInspect.gob, message)))
            clearResourceInspect();
    }

    private void clearResourceInspect() {
        resourceInspect = null;
        priorRefillInfo = null;
        priorRefill = null;
    }

    public void tick(double dt) {
        if(resourceInspect != null) {
            if(resourceInspect.until < Utils.rtime() || resourceInspect.gob.removed) clearResourceInspect();
            else {
                GAttrib info = attribute(resourceInspect.gob, "haven.res.ui.inspect.SavedInfo");
                String fresh = savedTimerLine(resourceInspect.gob, info);
                if(fresh != null && (info != priorRefillInfo || !fresh.equals(priorRefill))) refillNotice(fresh);
            }
        }
        if(gui.ui == null || !active()) { clear(); return; }
        MapView map = gui.map;
        Coord at = map.rootxlate(gui.ui.mc);
        if(!at.isect(Coord.z, map.sz)) { clear(); return; }
        position = at;
        elapsed += dt;
        if(pending || elapsed < 0.1) return;
        elapsed = 0;
        pending = true;
        long request = generation;
        map.new Hittest(at) {
            protected void hit(Coord pc, Coord2d mc, ClickData inf) {
                pending = false;
                if(request != generation) return;
                ground = mc;
                object = null;
                if(inf != null) {
                    if(inf.ci instanceof Gob.GobClick) object = ((Gob.GobClick)inf.ci).gob;
                    else for(Object node : inf.array()) if(node instanceof Gob) { object = (Gob)node; break; }
                }
            }
            protected void nohit(Coord pc) {
                pending = false;
                if(request == generation) { object = null; ground = null; }
            }
        }.run();
    }

    private static Object field(Object obj, String name) {
        try { return obj.getClass().getField(name).get(obj); }
        catch(ReflectiveOperationException e) { return null; }
    }

    private static GAttrib attribute(Gob gob, String name) {
        for(GAttrib attr : gob.attributes())
            if(attr.getClass().getName().equals(name)) return attr;
        return null;
    }

    /** Game-time "Will refill in …" as real hours. */
    static String refillHours(String message) {
        long millis = hearthborn.timers.NaturalResourceTimers.remainingMillis(message);
        if(millis <= 0) return null;
        double real = millis / 3600000d;
        if(real >= 1) {
            double shown = Math.round(real * 10) / 10.0;
            if(shown == Math.rint(shown)) return String.format(java.util.Locale.ROOT, "%.0fh", shown);
            return String.format(java.util.Locale.ROOT, "%.1fh", shown);
        }
        int minutes = (int)Math.round(real * 60);
        if(minutes < 1 && real > 0) minutes = 1;
        return minutes + "m";
    }
    public static String withRefill(String shown, String source) {
        String hours = refillHours(source);
        if(hours == null) hours = refillHours(shown);
        if(hours == null || shown == null || shown.contains("(" + hours + ")")) return shown;
        return shown + " (" + hours + ")";
    }
    private static String savedRefill(Gob gob) {
        return refillHours(savedTimerLine(null, attribute(gob, "haven.res.ui.inspect.SavedInfo")));
    }

    private static String savedTimerLine(Gob gob, GAttrib saved) {
        Object lines = saved == null ? null : field(saved, "lines");
        if(!(lines instanceof Iterable)) return null;
        boolean tarKiln = ObjectTimers.tarKiln(gob);
        for(Object line : (Iterable<?>)lines) {
            String raw = String.valueOf(line);
            if(tarKiln ? ObjectTimers.coalProgress(raw) >= 0
                       : hearthborn.timers.NaturalResourceTimers.remainingMillis(raw) > 0) return raw;
        }
        return null;
    }
    private static int state(Gob gob) {
        Drawable drawable = gob.getattr(Drawable.class);
        if(!(drawable instanceof ResDrawable)) return 0;
        try {
            Field field = ResDrawable.class.getDeclaredField("sdt");
            field.setAccessible(true);
            MessageBuf data = ((MessageBuf)field.get(drawable)).clone();
            return data.eom() ? 0 : Sprite.decnum(data);
        } catch(ReflectiveOperationException e) { return 0; }
    }

    private static final String[][] ORE = {
        {"hematite", "cast iron"}, {"magnetite", "cast iron"}, {"limonite", "cast iron"}, {"ilmenite", "cast iron"},
        {"malachite", "copper"}, {"chalcopyrite", "copper"}, {"cuprite", "copper"}, {"peacockore", "copper"},
        {"cassiterite", "tin"}, {"galena", "lead"}, {"leadglance", "lead"},
        {"argentite", "silver"}, {"hornsilver", "silver"},
        {"nagyagite", "gold"}, {"petzite", "gold"}, {"sylvanite", "gold"},
        {"blackcoal", "coal"}, {"cinnabar", "mercury"},
    };

    private static String tileText(Resource tile, boolean developer) {
        if(developer) return tile.name;
        Resource.Tooltip name = tile.layer(Resource.tooltip);
        String text = hearthborn.L10n.objectName(tile, name == null ? readableName(tile) : name.t);
        String metal = metal(tile.name);
        if(metal != null && !text.toLowerCase(java.util.Locale.ROOT).contains(metal.toLowerCase(java.util.Locale.ROOT)))
            text = text + " (" + metal + ")";
        return text;
    }

    /** Smelt product corresponding to an ore wall. */
    public static String metal(String resname) {
        if(resname == null || !(resname.startsWith("gfx/tiles/rocks/") || resname.startsWith("gfx/invobjs/"))) return null;
        String key = resname.substring(resname.lastIndexOf('/') + 1);
        String product = null;
        for(String[] row : ORE) if(row[0].equals(key)) { product = row[1]; break; }
        if(product == null) return null;
        String translated = hearthborn.L10n.find("ingredient", product);
        if(translated == null) translated = hearthborn.L10n.find("label", product);
        return translated == null ? product : translated;
    }

    public static String readableName(Resource res) {
        String name = res.basename();
        if(res.name.contains("/trees/") && name.endsWith("log"))
            name = name.substring(0, name.length() - 3) + " log";
        if(name.endsWith("flour")) name = name.substring(0, name.length() - 5) + " flour";
        name = name.replace('-', ' ').replace('_', ' ');
        return hearthborn.L10n.objectName(res, name.isEmpty() ? res.name : Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }

    /** A mannequin is a player body whose model is the wooden dummy, so its own name is Mannequin. */
    static String shownResource(Gob gob, Resource res) {
        if(res != null && "gfx/borka/body".equals(res.name) && mannequin(gob)) return "gfx/terobjs/mannequin";
        return res == null ? null : res.name;
    }
    private static boolean mannequin(Gob gob) {
        Drawable drawable = gob.getattr(Drawable.class);
        if(drawable instanceof Composite) {
            Composite body = (Composite)drawable;
            if(mannequinMods(body.nmod)) return true;
            Composited model = body.comp;
            if(model != null && (mannequinMods(model.cmod) || mannequinGear(model.cequ))) return true;
        }
        if(gob.ols != null) for(Gob.Overlay overlay : gob.ols)
            if(mannequin(hearthborn.util.GameObjects.overlayName(overlay))) return true;
        return false;
    }
    private static boolean mannequinMods(java.util.List<Composited.MD> mods) {
        if(mods == null) return false;
        for(Composited.MD mod : mods) {
            if(mod == null) continue;
            if(mod.mod != null && mannequin(mod.mod.get().name)) return true;
            if(mod.tex == null) continue;
            for(ResData tex : mod.tex) if(tex != null && tex.res != null && mannequin(tex.res.get().name)) return true;
        }
        return false;
    }
    private static boolean mannequinGear(java.util.List<Composited.ED> pieces) {
        if(pieces == null) return false;
        for(Composited.ED piece : pieces) {
            if(piece == null || piece.res == null || piece.res.res == null) continue;
            if(mannequin(piece.res.res.get().name)) return true;
        }
        return false;
    }
    private static boolean mannequin(String name) {
        return name != null && name.contains("gfx/terobjs/mannequin");
    }
    public static String details(Gob gob, boolean developer) {
        Drawable drawable = gob.getattr(Drawable.class);
        Resource res = drawable == null ? null : drawable.getres();
        if(res == null) return null;
        String shown = shownResource(gob, res);
        String hours = savedRefill(gob);
        String technical = shown + " [" + (hours != null ? hours : Integer.toString(state(gob))) + "]";
        if(developer) return technical;
        StringBuilder text = new StringBuilder();
        if(!shown.equals(res.name)) text.append(hearthborn.L10n.resource("tooltip", shown, "Mannequin"));
        else {
            Resource.Tooltip title = res.layer(Resource.tooltip);
            if(title == null) {
                GobIcon icon = gob.getattr(GobIcon.class);
                if(icon != null) title = icon.res.get().layer(Resource.tooltip);
            }
            text.append(hearthborn.L10n.objectName(res, title == null ? readableName(res) : title.t));
        }
        GAttrib saved = attribute(gob, "haven.res.ui.inspect.SavedInfo");
        Object lines = saved == null ? null : field(saved, "lines");
        if(lines instanceof Iterable) for(Object line : (Iterable<?>)lines) {
            String raw = String.valueOf(line);
            text.append('\n').append(withRefill(hearthborn.L10n.text("msg", raw), raw));
        }
        GAttrib materials = attribute(gob, "haven.res.lib.vmat.AttrMats");
        Object mats = materials == null ? null : field(materials, "mats");
        Set<String> names = new LinkedHashSet<>();
        if(mats instanceof Map) for(Object material : ((Map<?, ?>)mats).values()) {
            if(material instanceof Material.ResMaterial) {
                Resource mat = ((Material.ResMaterial)material).res;
                Resource.Tooltip tip = mat.layer(Resource.tooltip);
                names.add(hearthborn.L10n.objectName(mat, tip == null ? mat.basename() : tip.t));
            }
        }
        if(!names.isEmpty()) {
            text.append(hearthborn.L10n.tr("\nMaterials:"));
            for(String name : names) text.append("\n• ").append(name);
        }
        return text.toString();
    }

    public Object tooltip() {
        if(gui.ui == null || !active() || position == null ||
           !gui.map.rootxlate(gui.ui.mc).isect(Coord.z, gui.map.sz)) return null;
        Coord mouse = gui.rootxlate(gui.ui.mc);
        for(Widget child = gui.child; child != null; child = child.next)
            if(child instanceof Window && child.visible() && mouse.isect(child.c, child.sz)) return null;
        try {
            String text = null;
            if(object != null && !mining) text = details(object, gui.ui.modshift);
            else if(ground != null) {
                Resource tile = gui.ui.sess.glob.map.tilesetr(gui.ui.sess.glob.map.gettile(ground.floor(MCache.tilesz)));
                text = tileText(tile, gui.ui.modshift);
            }
            if(!Objects.equals(text, previous)) {
                dispose(); previous = text;
                if(text != null) {
                    int newline = text.indexOf('\n');
                    String heading = newline < 0 ? text : text.substring(0, newline);
                    String body = newline < 0 ? "" : text.substring(newline);
                    rendered = RichText.render("$b{" + RichText.Parser.quote(heading) + "}" +
                                               RichText.Parser.quote(body), UI.scale(280));
                }
            }
            return rendered;
        } catch(Loading e) { return null; }
    }

    private void clear() {
        if(position != null) generation++;
        position = null; object = null; ground = null;
        previous = null; dispose();
    }

    public void dispose() {
        if(rendered != null) { rendered.tex().dispose(); rendered = null; }
    }
}
