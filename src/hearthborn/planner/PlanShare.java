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

package hearthborn.planner;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Portable plan files contain bounded plain data, never serialized Java objects.
 *
 * @author Jagy2014
 */
final class PlanShare {
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("Invalid plan size");return n;}
    private static String text(DataInputStream in)throws IOException{String value=in.readUTF();if(value.length()>4096)throw new IOException("Plan text too long");return value;}
    private static double number(DataInputStream in)throws IOException{double n=in.readDouble();if(!Double.isFinite(n)||Math.abs(n)>100000000)throw new IOException("Invalid plan coordinate");return n;}
    static Path write(ClaimSurvey.Plan plan)throws IOException{
        Path dir=hearthborn.io.DataDirectory.path().resolve("exports").toAbsolutePath();Files.createDirectories(dir);
        Path file=Files.createTempFile(dir,"claim-plan-",".hbplan");
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))){
            out.writeUTF("HEARTHBORN-PLAN-5");out.writeUTF(plan.name);out.writeUTF(plan.tag);out.writeLong(plan.grid);out.writeInt(plan.x);out.writeInt(plan.y);
            out.writeInt(plan.snapshot.size());
            for(ClaimSurvey.GridRecord grid:plan.snapshot){
                out.writeLong(grid.id);out.writeInt(grid.x);out.writeInt(grid.y);
                byte[] mask=(plan.tag.equals("vlg")?grid.village:grid.personal).toByteArray();out.writeInt(mask.length);out.write(mask);
                byte[] terrain=grid.terrain==null?new byte[10000]:grid.terrain;out.write(terrain);
                out.writeInt(grid.objects.size());
                for(ClaimSurvey.ObjectRecord object:grid.objects.values()){
                    out.writeUTF(object.resource);out.writeUTF(object.type==null?"":object.type);out.writeUTF(object.name==null?"":object.name);
                    out.writeDouble(object.x);out.writeDouble(object.y);out.writeDouble(object.quality);out.writeBoolean(object.ready);
                    byte[] picture=object.picture;out.writeInt(picture==null?0:picture.length);if(picture!=null)out.write(picture);
                    out.writeBoolean(object.footprint!=null);
                    if(object.footprint!=null)for(double n:object.footprint)out.writeDouble(n);
                }
            }
            java.util.List<ClaimSurvey.NamedArea> areas=plan.areas==null?Collections.emptyList():plan.areas;out.writeInt(areas.size());
            for(ClaimSurvey.NamedArea area:areas){out.writeUTF(area.name);out.writeInt(area.x);out.writeInt(area.y);out.writeInt(area.w);out.writeInt(area.h);}
        }catch(IOException|RuntimeException e){Files.deleteIfExists(file);throw e;}
        return file;
    }
    static ClaimSurvey.Plan read(Path file)throws IOException{
        if(Files.size(file)>20*1024*1024)throw new IOException("Plan exceeds 20 MB");
        ClaimSurvey.Plan plan=new ClaimSurvey.Plan();plan.id=UUID.randomUUID().toString();plan.snapshot=new ArrayList<>();
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
            String format=in.readUTF();boolean current=format.equals("HEARTHBORN-PLAN-5"),picture=current||format.equals("HEARTHBORN-PLAN-4"),interior=!current&&(picture||format.equals("HEARTHBORN-PLAN-3")),quality=picture||interior||format.equals("HEARTHBORN-PLAN-2");
            if(!quality&&!format.equals("HEARTHBORN-PLAN-1"))throw new IOException("Unsupported plan format");
            plan.name=text(in);plan.tag=text(in);if(!plan.tag.equals("cplot")&&!plan.tag.equals("vlg"))throw new IOException("Invalid claim type");
            plan.grid=in.readLong();plan.x=in.readInt();plan.y=in.readInt();
            if(interior){boolean room=in.readBoolean();count(in,3);if(room)throw new IOException("Room plans are no longer part of claim plans");}
            if(plan.x<0||plan.x>=100||plan.y<0||plan.y>=100)throw new IOException("Invalid plan anchor");
            int grids=count(in,1000),objects=0;boolean anchor=false;
            Set<Long> ids=new HashSet<>();
            for(int i=0;i<grids;i++){
                ClaimSurvey.GridRecord grid=new ClaimSurvey.GridRecord();grid.id=in.readLong();grid.x=in.readInt();grid.y=in.readInt();grid.region="import:"+plan.id;
                if(Math.abs((long)grid.x)>1000000||Math.abs((long)grid.y)>1000000||!ids.add(grid.id))throw new IOException("Invalid grid");
                byte[] mask=new byte[count(in,1250)];in.readFully(mask);BitSet tiles=BitSet.valueOf(mask);
                if(plan.tag.equals("vlg"))grid.village=tiles;else grid.personal=tiles;
                if(grid.id==plan.grid&&tiles.get(plan.x+plan.y*100))anchor=true;
                grid.terrain=new byte[10000];in.readFully(grid.terrain);for(byte terrain:grid.terrain)if(terrain<0||terrain>5)throw new IOException("Invalid terrain");
                int n=count(in,10000);objects+=n;if(objects>100000)throw new IOException("Too many objects");
                for(int j=0;j<n;j++){
                    ClaimSurvey.ObjectRecord object=new ClaimSurvey.ObjectRecord();object.resource=text(in);object.type=text(in);object.name=text(in);if(object.name.isEmpty())object.name=null;
                    object.x=number(in);object.y=number(in);if(quality){object.quality=number(in);if(object.quality<0)throw new IOException("Invalid quality");}if(object.x<0||object.y<0||object.x>=1100||object.y>=1100)throw new IOException("Invalid object position");
                    if(picture){
                        object.ready=in.readBoolean();int length=count(in,262144);
                        if(length>0){object.picture=new byte[length];in.readFully(object.picture);
                            try(javax.imageio.stream.ImageInputStream image=javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(object.picture))){
                                Iterator<javax.imageio.ImageReader> readers=javax.imageio.ImageIO.getImageReaders(image);if(!readers.hasNext())throw new IOException("Invalid plan icon");
                                javax.imageio.ImageReader reader=readers.next();try{reader.setInput(image);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)>512||reader.getHeight(0)>512)throw new IOException("Invalid plan icon");}finally{reader.dispose();}
                            }
                        }
                    }
                    if(in.readBoolean()){object.footprint=new double[4];for(int k=0;k<4;k++)object.footprint[k]=number(in);if(object.footprint[2]<object.footprint[0]||object.footprint[3]<object.footprint[1])throw new IOException("Invalid footprint");}
                    grid.objects.put(object.key(),object);
                }
                plan.snapshot.add(grid);
            }
            int areas=count(in,10000);
            for(int i=0;i<areas;i++){
                ClaimSurvey.NamedArea area=new ClaimSurvey.NamedArea();area.name=text(in);area.x=in.readInt();area.y=in.readInt();area.w=in.readInt();area.h=in.readInt();
                if(Math.abs((long)area.x)>2000000||Math.abs((long)area.y)>2000000||area.w<1||area.h<1||area.w>10000||area.h>10000)throw new IOException("Invalid area");plan.areas.add(area);
            }
            if(!anchor||in.read()!=-1)throw new IOException("Invalid plan data");
            long minX=Long.MAX_VALUE,minY=minX,maxX=Long.MIN_VALUE,maxY=maxX;
            for(ClaimSurvey.GridRecord grid:plan.snapshot){minX=Math.min(minX,grid.x);minY=Math.min(minY,grid.y);maxX=Math.max(maxX,grid.x+100L);maxY=Math.max(maxY,grid.y+100L);}
            if(maxX-minX>10000||maxY-minY>10000||(maxX-minX)*(maxY-minY)>1000000)throw new IOException("Plan bounds too large");
        }
        return plan;
    }
    private PlanShare(){}
}
