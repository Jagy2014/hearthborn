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

// Minesweeper data format and overlay for local mine observations.
package hearthborn.mining;
import hearthborn.map.MapFileUtils;

import haven.*;
import haven.render.Homo3D;
import haven.render.Pipe;
import haven.render.RenderTree;



import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import static haven.MapFile.*;

/**
 * @author Jagy2014
 */
public class Minesweeper {
    private static final String INDEX = "hearthborn-ms-index";
    private static final String GRID_NAME = "hearthborn-ms-grid-%x";
    static final double CLUE_LIFETIME = 30 * 60;
    private static final int TILES = MCache.cmaps.x * MCache.cmaps.y;
    private static final Coord2d TILE_CENTER = MCache.tilesz.div(2);
    private static final RenderTree.Node NIL = RenderTree.Node.nil;
    
    //if this value is passed, count component will be ignored
    private static final byte NO_COUNT		= (byte) 0b0000_1111;
    //if this value is passed, flags component will be ignored
    private static final byte NO_FLAGS		= (byte) 0b1111_0000;
    
    private static final byte COUNT_MASK	= (byte) 0b0000_1111;
    private static final byte FLAGS_MASK	= (byte) 0b1111_0000;


    static final byte PINNED = (byte)0x80;
    public static final byte CLEAR_FLAGS	= (byte) 0b0000_0000;
    public static final byte FLAG_SAFE		= (byte) 0b0001_0000;
    public static final byte FLAG_DANGER	= (byte) 0b0010_0000;
    public static final byte FLAG_MAYBE		= (byte) 0b0011_0000;
    
    //deprecated values, used for parsing old saved data
    private static final byte DEPRECATED_V2_SAFE = (byte) 0xff;
    private static final byte DEPRECATED_V2_DANGER = (byte) 0xfe;

    private final Object lock = new Object();
    private final Set<Long> gridIds = new HashSet<>();
    private final Map<Long, byte[]> values = new HashMap<>();
    private final Map<Long, SweeperNode[]> cuts = new HashMap<>();
    private final MapFile file;
    private final Map<Long,Map<Coord,ClueObservation>> observations=new HashMap<>();
    MineClueHover hover;
    private final Map<Long, byte[]> questions = new HashMap<>();
    private double questionUpdate;

    public Minesweeper(MapFile file) {
	this.file = file;
	MapFileUtils.load(file, this::loadIndex, INDEX);
    }

    private static Resource cursor;
    public static synchronized Resource cursor(){
        if(cursor!=null)return cursor;
        Resource.Virtual res=new Resource.Virtual(Resource.local(),"hearthborn/minesweeper-cursor",1);
        try(java.io.InputStream input=Minesweeper.class.getResourceAsStream("/hearthborn/assets/icons/menu-minesweeper.png")){
            BufferedImage source=javax.imageio.ImageIO.read(input);
            BufferedImage icon=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics=icon.createGraphics();graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Resource pointer=Resource.local().loadwait("gfx/hud/curs/arw");
            graphics.drawImage(pointer.layer(Resource.imgc).img,0,0,null);
            graphics.drawImage(source,9,8,23,23,null);graphics.dispose();
            ByteArrayOutputStream png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(icon,"png",png);
            MessageBuf image=new MessageBuf();image.addint16((short)0).addint16((short)-1).adduint8(0).addint16((short)-1).addint16((short)0).addint16((short)0).addbytes(png.toByteArray());
            res.add(res.new Image(new MessageBuf(image.fin())));
            MessageBuf neg=new MessageBuf();for(int value:new int[]{1,1,0,0,0,0,32,32})neg.addint16((short)value);neg.adduint8(0);
            res.add(res.new Neg(new MessageBuf(neg.fin())));cursor=res;return res;
        }catch(IOException error){throw new RuntimeException(error);}
    }
    private static volatile boolean showClues=Utils.getprefb("hearthborn/mining/dust-clues",false);
    public static boolean clues(){return showClues;}
    public static void clues(boolean value){showClues=value;Utils.setprefb("hearthborn/mining/dust-clues",value);if(value)enabled(true);}
    public static boolean enabled(){return Utils.getprefb("hearthborn/minesweeper/show",false);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/minesweeper/show",value);}
    public static void dust(Sprite.Owner owner,Resource res,byte[] data){
        if(!res.name.equals("gfx/fx/cavewarn") || data.length==0)return;
        Gob gob=owner instanceof Gob?(Gob)owner:owner.ocontext(Gob.class).orElse(null);
        if(gob!=null)addCountAtGob(gob,(byte)Math.round((data[0]&255)/30.0),data[0]&255);
    }
    public static void toggle(GameUI gui,boolean marking){
        if(gui==null || gui.map==null)return;
        if(marking){gui.map.minesweeperMarking=!gui.map.minesweeperMarking;if(gui.map.minesweeperMarking)enabled(true);}
        else {enabled(!enabled());if(!enabled())gui.map.minesweeperMarking=false;}
        hearthborn.quality.QualityRoster.releaseIfInspect(gui);
    }
    public static void pinAtPoint(Coord2d at,GameUI gui){
        if(gui==null||gui.minesweeper==null)return;
        Coord tile=at.floor(MCache.tilesz);MCache.Grid grid=gui.ui.sess.glob.map.getgridt(tile);
        if(grid==null)return;
        Boolean pinned=gui.minesweeper.togglePin(grid.id,tile.sub(grid.gc.mul(MCache.cmaps)));
        gui.ui.msg(hearthborn.L10n.tr(pinned==null?"There is no dust clue on this tile.":pinned?"Dust clue pinned.":"Dust clue is temporary again."));
    }
    public static void cycleAtPoint(Coord2d at,GameUI gui){
        if(gui==null||gui.minesweeper==null)return;
        Coord tile=at.floor(MCache.tilesz);MCache.Grid grid=gui.ui.sess.glob.map.getgridt(tile);
        if(grid!=null)gui.minesweeper.cycleMark(grid.id,tile.sub(grid.gc.mul(MCache.cmaps)));
    }
    byte cycleMark(long grid,Coord tile){
        synchronized(lock){
            byte current=loadGrid(grid)?(byte)(values.get(grid)[index(tile)]&0x30):CLEAR_FLAGS;
            byte next=current==FLAG_DANGER?FLAG_SAFE:current==FLAG_SAFE?FLAG_MAYBE:current==FLAG_MAYBE?CLEAR_FLAGS:FLAG_DANGER;
            addValue(grid,tile,NO_COUNT,next);return next;
        }
    }
    Boolean togglePin(long grid,Coord tile){
        synchronized(lock){
            if(!loadGrid(grid))return null;
            return setPin(grid,tile,(values.get(grid)[index(tile)]&PINNED)==0);
        }
    }
    Boolean setPin(long grid,Coord tile,boolean pin){
        synchronized(lock){
            if(!loadGrid(grid))return null;
            byte[] data=values.get(grid);int index=index(tile);
            if((data[index]&COUNT_MASK)==0)return null;
            if(pin)data[index]|=PINNED;
            else {data[index]&=~PINNED;expiry.computeIfAbsent(grid,key->new HashMap<>()).put(tile,Utils.rtime()+CLUE_LIFETIME);}
            if(gridIds.add(grid))storeIndex();storeGrid(grid,data);return pin;
        }
    }
    public static byte flag(int modifiers){
        return modifiers==0?FLAG_DANGER:modifiers==UI.MOD_CTRL?CLEAR_FLAGS:modifiers==UI.MOD_META?FLAG_SAFE:modifiers==UI.MOD_SHIFT?FLAG_MAYBE:NO_FLAGS;
    }
    public static void markFlagAtPoint(Coord2d rc, byte flags, GameUI gui) {
	markPoint(rc, NO_COUNT, flags, gui);
    }

    private static final Map<Glob,java.util.concurrent.ConcurrentLinkedQueue<Warning>> pending=new WeakHashMap<>();
    static final class Warning {
        final long grid;final Coord tile;final byte count;final double until;ClueObservation observation;
        Warning(long grid,Coord tile,byte count,double until){this.grid=grid;this.tile=tile;this.count=count;this.until=until;}
    }
    private final Map<Long,Map<Coord,Double>> expiry=new HashMap<>();
    private static void addCountAtGob(Gob gob,byte count){addCountAtGob(gob,count,-1);}
    private static void addCountAtGob(Gob gob,byte count,int strength){
        if(gob.glob==null||gob.rc==null||gob.removed||count<1||count>8)return;
        try {
            // Resolve the grid now. Reinterpreting rc after a floor transition corrupts another level.
            Coord world=gob.rc.floor(MCache.tilesz);
            MCache.Grid grid=gob.glob.map.getgridt(world);
            if(grid==null)return;
            Warning warning=new Warning(grid.id,world.sub(grid.gc.mul(MCache.cmaps)),count,Utils.rtime()+CLUE_LIFETIME);
            warning.observation=ClueObservation.capture(gob.glob.map,world,strength);
            if(gob.glob.map.getgridt(world).id!=grid.id)return;
            synchronized(pending){pending.computeIfAbsent(gob.glob,key->new java.util.concurrent.ConcurrentLinkedQueue<>()).add(warning);}
        }catch(Loading unavailable){} // No reliable grid identity: do not invent a location later.
    }
    public static void observe(GameUI gui){
        if(gui==null||gui.ui==null||gui.ui.sess==null||gui.minesweeper==null)return;
        Minesweeper mine=gui.minesweeper;
        java.util.concurrent.ConcurrentLinkedQueue<Warning> queue;
        synchronized(pending){queue=pending.remove(gui.ui.sess.glob);}
        double now=Utils.rtime();
        if(queue!=null)for(Warning warning:queue)if(now<warning.until){
            synchronized(mine.lock){
                mine.addClue(warning.grid,warning.tile,warning.count);
                if(warning.observation!=null&&(mine.values.get(warning.grid)[index(warning.tile)]&PINNED)==0)
                    mine.observations.computeIfAbsent(warning.grid,key->new HashMap<>()).put(warning.tile,warning.observation);
            }
            synchronized(mine.lock){mine.expiry.computeIfAbsent(warning.grid,key->new HashMap<>()).put(warning.tile,warning.until);}
        }
        mine.expire(now);
        clearHover(gui);
        if(now>=mine.questionUpdate){mine.updateQuestions(gui.ui.sess.glob.map.loadedGrids());mine.questionUpdate=now+0.2;}
    }
    void updateQuestions(Collection<MCache.Grid> loaded) {
        synchronized(lock) {
            Map<Coord,MCache.Grid> grids=new HashMap<>();
            for(MCache.Grid grid:loaded){grids.put(grid.gc,grid);loadGrid(grid.id);}
            // Keep arrays stable: attached render nodes see updates without reattachment.
            for(byte[] mask:questions.values())Arrays.fill(mask,(byte)0);
            for(MCache.Grid grid:loaded){
                byte[] data=values.get(grid.id);if(data==null)continue;
                for(int at=0;at<data.length;at++){
                    byte value=data[at];
                    if((value&COUNT_MASK)==0||(!clues()&&(value&PINNED)==0))continue;
                    Coord center=grid.gc.mul(MCache.cmaps).add(at%MCache.cmaps.x,at/MCache.cmaps.x);
                    for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
                        if(x==0&&y==0)continue;
                        Coord tile=center.add(x,y);MCache.Grid neighbor=grids.get(tile.div(MCache.cmaps));
                        if(neighbor==null)continue;
                        questions.computeIfAbsent(neighbor.id,key->new byte[TILES])[index(tile.mod(MCache.cmaps))]=1;
                    }
                }
            }
        }
    }
    void expire(double now){
        synchronized(lock){
            for(Iterator<Map.Entry<Long,Map<Coord,Double>>> grids=expiry.entrySet().iterator();grids.hasNext();){
                Map.Entry<Long,Map<Coord,Double>> grid=grids.next();
                for(Iterator<Map.Entry<Coord,Double>> it=grid.getValue().entrySet().iterator();it.hasNext();){
                    Map.Entry<Coord,Double> entry=it.next();if(now<entry.getValue())continue;
                    byte[] data=values.get(grid.getKey());if(data!=null&&(data[index(entry.getKey())]&PINNED)==0){setValue(data,index(entry.getKey()),(byte)0,NO_FLAGS);Map<Coord,ClueObservation> metadata=observations.get(grid.getKey());if(metadata!=null)metadata.remove(entry.getKey());}it.remove();
                }
                if(grid.getValue().isEmpty())grids.remove();
            }
        }
    }
    void addClue(long grid,Coord tile,byte count){
        synchronized(lock){
            if(!loadGrid(grid))values.put(grid,new byte[TILES]);
            byte[] data=values.get(grid);if((data[index(tile)]&PINNED)==0)setValue(data,index(tile),count,NO_FLAGS);
        }
    }
    public void clearClues(){
        synchronized(lock){for(byte[] data:values.values())for(int i=0;i<data.length;i++)if((data[i]&PINNED)==0)data[i]&=FLAGS_MASK;expiry.clear();}
    }

    static final class Clue {
        final int count,mark;final boolean pinned;final ClueObservation observation;final long grid;
        Clue(int count,boolean pinned,ClueObservation observation,long grid,int mark){this.count=count;this.mark=mark;this.pinned=pinned;this.observation=observation;this.grid=grid;}
    }
    Clue clue(MCache map,Coord tile){
        synchronized(lock){
            MCache.Grid grid=map.getgridt(tile);if(grid==null||!loadGrid(grid.id))return null;
            Coord local=tile.sub(grid.gc.mul(MCache.cmaps));byte value=values.get(grid.id)[index(local)];int count=value&COUNT_MASK;
            if(count==0||(!clues()&&(value&PINNED)==0))return null;
            Map<Coord,ClueObservation> metadata=observations.get(grid.id);
            return new Clue(count,(value&PINNED)!=0,metadata==null?null:metadata.get(local),grid.id,value&0x30);
        }
    }
    public static void clearHover(GameUI gui){if(gui!=null&&gui.minesweeper!=null&&gui.minesweeper.hover!=null)gui.minesweeper.hover.clear();}
    private void storeObservations(long grid,byte[] data){
        Map<Coord,ClueObservation> saved=new HashMap<>();Map<Coord,ClueObservation> metadata=observations.get(grid);
        if(metadata!=null)for(Map.Entry<Coord,ClueObservation> entry:metadata.entrySet())if((data[index(entry.getKey())]&PINNED)!=0)saved.put(entry.getKey(),entry.getValue());
        try(StreamMessage out=new StreamMessage(file.sstore("hearthborn-ms-observations-%x",grid))){
            out.adduint8(1).addint32(saved.size());
            for(Map.Entry<Coord,ClueObservation> entry:saved.entrySet()){
                ClueObservation value=entry.getValue();out.addint32(index(entry.getKey())).addint64(value.time).addint32(value.strength).adduint8(value.open).adduint8(value.walls);
            }
        }catch(IOException error){throw new StreamMessage.IOError(error);}
    }
    private void loadObservations(long grid){
        MapFileUtils.load(file,data->{
            if(data.uint8()!=1)return false;int size=data.int32();if(size<0||size>TILES)return false;
            Map<Coord,ClueObservation> metadata=new HashMap<>();
            for(int i=0;i<size;i++){int at=data.int32();if(at<0||at>=TILES)return false;metadata.put(Coord.of(at%MCache.cmaps.x,at/MCache.cmaps.x),new ClueObservation(data.int64(),data.int32(),data.uint8(),data.uint8()));}
            observations.put(grid,metadata);return true;
        },"hearthborn-ms-observations-%x",grid);
    }
    private static void markPoint(Coord2d rc, byte count, byte flags, GameUI gui) {
	Coord gc = rc.floor(MCache.tilesz);
	MCache.Grid grid = gui.ui.sess.glob.map.getgridt(gc);
	if(grid == null) {return;}

	Coord tc = gc.sub(grid.gc.mul(MCache.cmaps));
	long id = grid.id;

	if(gui.minesweeper!=null)gui.minesweeper.addValue(id, tc, count, flags);
    }

    private void addValue(long id, Coord tc, byte count, byte flags) {
	synchronized (lock) {
	    Map<Long, byte[]> grids = values;
	    byte[] values;
	    if(loadGrid(id)) {
		values = grids.get(id);
	    } else {
		values = new byte[TILES];
		grids.put(id, values);
		gridIds.add(id);
		storeIndex();
	    }
	    if(gridIds.add(id))storeIndex();
            setValue(values, index(tc), count, flags);
	    storeGrid(id, values);
	}
    }

    private void updateGrid(long grid, byte[] newValues) {
	if(gridIds.contains(grid) && loadGrid(grid)) {
	    byte[] curValues = values.get(grid);
	    for (int i = 0; i < newValues.length; i++) {
		byte newValue = newValues[i];
		if(newValue == 0) {continue;}
		byte count = (byte) (newValue & COUNT_MASK);
		if(count == 0) {count = NO_COUNT;}
		setValue(curValues, i, count, (byte) (newValue & FLAGS_MASK));
	    }
	    storeGrid(grid, curValues);
	} else {
	    gridIds.add(grid);
	    storeIndex();
	    storeGrid(grid, newValues);
	}
    }

    private static void setValue(byte[] values, int idx, byte count, byte flags) {
	byte value = values[idx];
	count = (byte) (count & COUNT_MASK);
	flags = (byte) (flags & FLAGS_MASK);
	if(count != NO_COUNT) {value = (byte) ((value & FLAGS_MASK) | count);}
	if(flags != NO_FLAGS) {value = (byte) ((value & (COUNT_MASK|PINNED)) | flags);}
	values[idx] = value;
    }

    private static int index(Coord tc) {
	return tc.x + tc.y * MCache.cmaps.x;
    }

    public static RenderTree.Node getcut(UI ui, Coord cc) {
	if(!enabled()) {return NIL;}
	GameUI gui = ui.root.findchild(GameUI.class);
	if(gui == null) {return NIL;}
	Minesweeper minesweeper = gui.minesweeper;
	if(minesweeper == null) {return NIL;}

	MCache.Grid grid=ui.sess.glob.map.getgrid(cc.div(MCache.cutn));
        return grid==null?NIL:minesweeper.getcut(grid, cc.mod(MCache.cutn));
    }

    private RenderTree.Node getcut(MCache.Grid grid, Coord cc) {
	SweeperNode[] nodes;
	int index = cc.x + cc.y * MCache.cutn.x;
	synchronized (lock) {
	    if(!cuts.containsKey(grid.id)) {
		if(!loadGrid(grid.id)) {
                    if(!questions.containsKey(grid.id))return NIL;
                    values.put(grid.id,new byte[TILES]);
                }
		nodes = new SweeperNode[MCache.cutn.x * MCache.cutn.y];
		cuts.put(grid.id, nodes);
	    } else {
		nodes = cuts.get(grid.id);
	    }

	    if(nodes[index] == null) {
		byte[] v = values.get(grid.id);
		if(v == null) {return NIL;}
		nodes[index] = new SweeperNode(v, questions.computeIfAbsent(grid.id,key->new byte[TILES]), cc);
	    }
	}
	return nodes[index];
    }

    private void trim(List<Long> removed) {
	synchronized (lock) {
	    if(removed == null) {
		values.clear();
		cuts.clear();
	    } else {
		for (Long id : removed) {
		    values.remove(id);
		    cuts.remove(id);
		}
	    }
	}
    }

    private void storeIndex() {
	synchronized (lock) {
	    OutputStream fp;
	    try {
		fp = file.sstore(INDEX);
	    } catch (IOException e) {
		throw (new StreamMessage.IOError(e));
	    }
	    try (StreamMessage out = new StreamMessage(fp)) {
		out.adduint8(1);
		for (Long id : gridIds) {
		    out.addint64(id);
		}
	    }
	}
    }

    private void storeGrid(long id, byte[] grid) {
        storeObservations(id,grid);
	OutputStream fp;
	try {
	    fp = file.sstore(GRID_NAME, id);
	} catch (IOException e) {
	    throw (new StreamMessage.IOError(e));
	}
	try (StreamMessage out = new StreamMessage(fp)) {
	    out.adduint8(4); // flags plus explicitly pinned counts
	    ZMessage zout = new ZMessage(out);
	    byte[] flags=grid.clone();for(int i=0;i<flags.length;i++)if((flags[i]&PINNED)==0)flags[i]&=FLAGS_MASK;
        zout.addbytes(flags);
	    zout.finish();
	}
    }

    private boolean loadIndex(StreamMessage data) {
	synchronized (lock) {
	    Set<Long> ids = doLoadIndex(data);
	    if(ids == null) {return false;}
	    gridIds.addAll(ids);
	}
	return true;
    }

    private static Set<Long> doLoadIndex(StreamMessage data) {
	int ver = data.uint8();
	if(ver == 1) {
	    Set<Long> gridIds = new HashSet<>();
	    while (!data.eom()) {
		gridIds.add(data.int64());
	    }
	    return gridIds;
	} else {
	    warn("unknown mine observations format version: %d", ver);
	}
	return null;
    }

    private boolean loadGrid(long id) {
	synchronized (lock) {
            if(values.containsKey(id)) {return true;}
	    if(!gridIds.contains(id)) {return false;}

	    if(!MapFileUtils.load(file, data -> loadGrid(data, id), GRID_NAME, id)) {
		cuts.remove(id);
		values.remove(id);
		gridIds.remove(id);
		storeIndex();
		return false;
	    }
	}
	return true;
    }

    private boolean loadGrid(StreamMessage data, long id) {
	byte[] v = doLoadGrid(data, id);
	if(v == null) {return false;}
	values.put(id, v);
        loadObservations(id);
	return true;
    }

    private static byte[] doLoadGrid(StreamMessage data, long id) {
	int ver = data.uint8();
	if(ver == 2 || ver == 3 || ver == 4) {
	    byte[] values = new ZMessage(data).bytes(TILES);
	    if(ver == 2) {convertToV3(values);}
            for(int i=0;i<values.length;i++)if(ver<4||(values[i]&PINNED)==0)values[i]&=0x30;
	    return values;
	} else {
	    warn("unknown mine observations format-grid %d version: %d", id, ver);
	}
	return null;
    }

    private static void convertToV3(byte[] values) {
	for (int i = 0; i < values.length; i++) {
	    byte value = values[i];
	    if(value == DEPRECATED_V2_SAFE) {
		values[i] = FLAG_SAFE;
	    } else if(value == DEPRECATED_V2_DANGER) {
		values[i] = FLAG_DANGER;
	    }
	}
    }

    private static class SweeperNode implements RenderTree.Node, PView.Render2D {
	private static final Color SAFE_COL = new Color(32, 220, 80);
	private static final Color DANGER_COL = new Color(240, 32, 100);
	private static final Color MAYBE_COL = new Color(193, 87, 251);
	private static final Color[] COLORS = new Color[]{
	    new Color(150, 200, 245),
	    new Color(142, 225, 207),
	    new Color(182, 210, 127),
	    new Color(233, 225, 34),
	    new Color(250, 195, 56),
	    new Color(255, 150, 65),
	    new Color(230, 80, 32),
	    new Color(235, 20, 16),
	};
	private static final Map<Byte, Tex> CACHE = new HashMap<>();

	private final byte[] values;
        private final byte[] questions;
	private final Coord cc;

	public SweeperNode(byte[] values, byte[] questions, Coord cc) {
	    this.values = values;
            this.questions = questions;
	    this.cc = cc;
	}

	private static synchronized Tex getTex(byte val) {
	    if(val == 0) {return null;}
	    if(CACHE.containsKey(val)) {return CACHE.get(val);}
	    BufferedImage flags = flagImg(val);
	    BufferedImage count = countImg(val);
	    Tex tex = null;
	    if(flags != null || count != null) {
		tex = new TexI(ItemInfo.catimgsh(0, flags, count));
	    }
	    CACHE.put(val, tex);
	    return tex;
	}

        private static BufferedImage flagImg(byte flags) {
            flags=(byte)(flags & 0x30);
            Color color;
            if(flags==FLAG_SAFE)color=SAFE_COL;
            else if(flags==FLAG_DANGER)color=DANGER_COL;
            else if(flags==FLAG_MAYBE)color=MAYBE_COL;
            else return null;
            int size=UI.scale(24);
            BufferedImage image=TexI.mkbuf(Coord.of(size,size));
            java.awt.Graphics2D graphics=image.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.geom.Path2D mark=new java.awt.geom.Path2D.Double();
            if(flags==FLAG_SAFE){
                mark.moveTo(size*.18,size*.52);mark.lineTo(size*.40,size*.75);mark.lineTo(size*.83,size*.23);
            }else if(flags==FLAG_DANGER){
                mark.moveTo(size*.23,size*.23);mark.lineTo(size*.77,size*.77);
                mark.moveTo(size*.77,size*.23);mark.lineTo(size*.23,size*.77);
            }else{
                mark.moveTo(size*.28,size*.29);
                mark.curveTo(size*.28,size*.09,size*.75,size*.09,size*.75,size*.32);
                mark.curveTo(size*.75,size*.48,size*.50,size*.48,size*.50,size*.63);
                mark.moveTo(size*.50,size*.83);mark.lineTo(size*.50,size*.84);
            }
            graphics.setColor(Color.BLACK);
            graphics.setStroke(new BasicStroke(size*.22f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));graphics.draw(mark);
            graphics.setColor(color);
            graphics.setStroke(new BasicStroke(size*.12f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));graphics.draw(mark);
            graphics.dispose();return image;
        }

	private static BufferedImage countImg(byte count) {
	    if(count==NO_COUNT)return hearthborn.QualityOverlay.styled("?",COLORS[0]);
            boolean pinned=(count&PINNED)!=0;
            count = (byte) (count & COUNT_MASK);
	    if(count == 0) {return null;}

	    Color color = COLORS[Utils.clip(count - 1, 0, COLORS.length - 1)];
	    String text = (pinned?"":"~") + count;

	    return hearthborn.QualityOverlay.styled(text,color);
	}

	public Coord3f origin(Coord tc) {
	    Coord2d mc = tc.mul(MCache.tilesz).add(TILE_CENTER);
	    return new Coord3f((float) mc.x, (float) -mc.y, 1f);
	}

	@Override
	public void draw(GOut g, Pipe state) {
	    Coord ul = cc.mul(MCache.cutsz);
            boolean visibleClues=clues();
	    Coord o = new Coord();
	    for (o.x = 0; o.x < MCache.cutsz.x; o.x++) {
		for (o.y = 0; o.y < MCache.cutsz.y; o.y++) {

		    int cell=index(ul.add(o));
                    byte value=values[cell];
                    if(!visibleClues&&(value&PINNED)==0)value&=FLAGS_MASK;
                    if(value==0&&questions[cell]!=0)value=NO_COUNT;
                    Tex tex = getTex(value);
		    if(tex == null) {continue;}

		    HomoCoord4f clip=Homo3D.obj2clip(origin(o),state);
                    if(clip.clipped())continue;
                    Coord sc = clip.toview(Area.sized(g.sz())).round2();
		    if(sc == null) {continue;}
		    if(!sc.isect(Coord.z, g.sz())) {continue;}

		    g.aimage(tex, sc, 0.5f, 0.5f);
		}
	    }
	}
    }
}
