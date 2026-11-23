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

package hearthborn.map;

import haven.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;
import javax.imageio.ImageIO;

/**
 * Exports the saved map independently of the current session and screen zoom.
 *
 * @author Jagy2014
 */
public final class MapImageExport {
    public static Coord origin(Collection<Coord> cells){
        if(cells.isEmpty())throw new IllegalArgumentException("No saved map tiles in this segment.");
        int x=Integer.MAX_VALUE,y=Integer.MAX_VALUE;
        for(Coord c:cells){x=Math.min(x,c.x);y=Math.min(y,c.y);}
        return new Coord(x,y);
    }
    public static final class Layout {
        public final Coord origin,size;
        public final long divisor;
        private Layout(Collection<Coord> cells){
            origin=MapImageExport.origin(cells);long maxX=origin.x,maxY=origin.y;
            for(Coord c:cells){maxX=Math.max(maxX,c.x);maxY=Math.max(maxY,c.y);}
            long w=(maxX-origin.x+1)*MCache.cmaps.x,h=(maxY-origin.y+1)*MCache.cmaps.y;
            long limit=Math.max(1,Math.min(16_000_000L,Runtime.getRuntime().maxMemory()/32));
            long d=1;
            while(ceil(w,d)>8192||ceil(h,d)>8192||ceil(w,d)*ceil(h,d)>limit)d*=2;
            divisor=d;size=new Coord((int)ceil(w,d),(int)ceil(h,d));
        }
        private static long ceil(long value,long divisor){return (value+divisor-1)/divisor;}
        public Coord position(Coord cell){
            return new Coord((int)(((long)cell.x-origin.x)*MCache.cmaps.x/divisor),
                             (int)(((long)cell.y-origin.y)*MCache.cmaps.y/divisor));
        }
        public Coord end(Coord cell){
            return new Coord((int)ceil(((long)cell.x-origin.x+1)*MCache.cmaps.x,divisor),
                             (int)ceil(((long)cell.y-origin.y+1)*MCache.cmaps.y,divisor));
        }
    }
    public static Layout layout(Collection<Coord> cells){return new Layout(cells);}
    public static Coord imageSize(Collection<Coord> cells){return layout(cells).size;}
    private static <T> T read(MapFile file,java.util.function.Supplier<T> call)throws InterruptedException{
        return Loading.waitforint(()->{
            file.lock.readLock().lock();
            try{return call.get();}finally{file.lock.readLock().unlock();}
        });
    }
    private static java.util.List<Coord> cells(MapFile file,MapFile.Segment seg)throws InterruptedException{
        java.util.List<Coord> cells=new ArrayList<>(read(file,seg::gridCoordinates));
        cells.sort(Comparator.comparingInt((Coord c)->c.y).thenComparingInt(c->c.x));return cells;
    }
    private static BufferedImage tile(MapFile file,MapFile.Segment seg,Coord cell)throws InterruptedException{
        return read(file,()->{
            MapFile.View view=new MapFile.View(seg);
            for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)view.addgrid(cell.add(x,y));
            view.fin();
            return MapSource.drawmap(view,Area.sized(cell.mul(MCache.cmaps).sub(1,1),MCache.cmaps.add(2,2))).getSubimage(1,1,MCache.cmaps.x,MCache.cmaps.y);
        });
    }
    public static void png(MapFile file,long segment,Path target,Consumer<String> progress)throws IOException,InterruptedException{
        MapFile.Segment seg=read(file,()->file.segments.get(segment));
        if(seg==null)throw new IOException("Saved map segment is unavailable.");
        java.util.List<Coord> cells=cells(file,seg);Layout layout=layout(cells);Coord size=layout.size;
        BufferedImage image=new BufferedImage(size.x,size.y,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        try{
            int done=0;
            for(Coord cell:cells){
                if(Thread.interrupted())throw new InterruptedException();
                progress.accept((++done)+" / "+cells.size()+" - "+size.x+" × "+size.y+(layout.divisor>1?" (1:"+layout.divisor+")":""));
                Coord at=layout.position(cell),end=layout.end(cell);
                g.drawImage(tile(file,seg,cell),at.x,at.y,end.x-at.x,end.y-at.y,null);
            }
        }finally{g.dispose();}
        write(target,out->{if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable.");});
    }
    public static void tiles(MapFile file,Path target,Consumer<String> progress)throws IOException,InterruptedException{
        java.util.List<Long> segments=read(file,()->new ArrayList<>(file.knownsegs));Collections.sort(segments);
        Path tmp=temporary(target);
        try{
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){
                zip.setLevel(Deflater.NO_COMPRESSION);
                zip.putNextEntry(new ZipEntry("map/"));zip.closeEntry();
                zip.putNextEntry(new ZipEntry("cave/"));zip.closeEntry();
                int done=0;
                for(long id:segments){
                    MapFile.Segment seg=read(file,()->file.segments.get(id));if(seg==null)throw new IOException("Saved map segment is unavailable.");
                    java.util.List<Coord> savedCells=cells(file,seg);
                    boolean cave=false;
                    for(Coord cell:savedCells){
                        if(Thread.interrupted())throw new InterruptedException();
                        if(read(file,()->CaveLevels.isCave(seg.grid(cell).get()))){cave=true;break;}
                    }
                    String folder=(cave?"cave/":"map/")+String.format(Locale.ROOT,"segment_%x/",id);
                    zip.putNextEntry(new ZipEntry(folder));zip.closeEntry();
                    for(Coord cell:savedCells){
                        if(Thread.interrupted())throw new InterruptedException();
                        progress.accept(Integer.toString(++done));
                        BufferedImage image=tile(file,seg,cell);
                        zip.putNextEntry(new ZipEntry(folder+String.format(Locale.ROOT,"tile_%d_%d.png",cell.x,cell.y)));
                        ImageIO.write(image,"png",zip);zip.closeEntry();
                    }
                }
            }
            if(Thread.interrupted())throw new InterruptedException();
            Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}
    }
    private interface Writer{void write(OutputStream out)throws IOException;}
    private static Path temporary(Path target)throws IOException{return Files.createTempFile(target.toAbsolutePath().getParent(),"map-export-",".tmp");}
    private static void write(Path target,Writer writer)throws IOException,InterruptedException{
        Path tmp=temporary(target);
        try{
            try(OutputStream out=new BufferedOutputStream(Files.newOutputStream(tmp))){writer.write(out);}
            if(Thread.interrupted())throw new InterruptedException();
            Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}
    }
    private MapImageExport(){}
}
