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

import haven.Coord;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;

/**
 * Standalone documents, without game resources or third-party spreadsheet libraries.
 *
 * @author Jagy2014
 */
final class PlanExport {
    static final class ObjectCell {
        final String name,type;final double x,y,w,h;
        ObjectCell(String name,String type,double x,double y,double w,double h){this.name=name;this.type=type;this.x=x;this.y=y;this.w=w;this.h=h;}
    }
    static String xml(String value){return value==null?"":value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static int terrainColor(int kind){switch(kind){case 1:return 0x856139;case 2:return 0x92968b;case 3:return 0xa08150;case 4:return 0x618944;case 5:return 0x69b9e0;default:return 0x34434a;}}
    static Path write(String title,Set<Coord> tiles,Coord min,Coord max,List<ObjectCell> objects,boolean excel)throws IOException{return write(title,tiles,min,max,objects,Collections.emptyMap(),excel);}
    static Path write(String title,Set<Coord> tiles,Coord min,Coord max,List<ObjectCell> objects,Map<Coord,Integer> terrain,boolean excel)throws IOException{
        if(tiles.isEmpty()||min==null||max==null)throw new IOException("Нет данных для экспорта");
        Path directory=hearthborn.io.DataDirectory.path().toAbsolutePath().resolve("exports");Files.createDirectories(directory);
        Path path=Files.createTempFile(directory,"claim-plan-",excel?".xlsx":".html");
        try{if(excel)xlsx(path,title,tiles,min,max,objects,terrain);else html(path,title,tiles,min,max,objects,terrain);}catch(IOException|RuntimeException error){Files.deleteIfExists(path);throw error;}return path;
    }
    private static void html(Path path,String title,Set<Coord> tiles,Coord min,Coord max,List<ObjectCell> objects,Map<Coord,Integer> terrain)throws IOException{
        int width=max.x-min.x,height=max.y-min.y;
        StringBuilder out=new StringBuilder("<!doctype html><meta charset=\"utf-8\"><title>").append(xml(title)).append("</title><style>body{font:16px system-ui;background:#171c16;color:#ead9aa;margin:24px}svg{background:#11170f;max-width:100%;height:auto}table{border-collapse:collapse}td,th{border:1px solid #716344;padding:6px;text-align:left}</style><h1>").append(xml(title)).append("</h1><p>Север ↑ · одна клетка - один тайл · ").append(width).append(" × ").append(height).append(" · сохранённые и загруженные объекты</p><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width*12).append("\" height=\"").append(height*12).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\"><defs><pattern id=\"tile\" width=\"1\" height=\"1\" patternUnits=\"userSpaceOnUse\"><rect width=\"1\" height=\"1\" fill=\"#34434a\" stroke=\"#718075\" stroke-width=\".03\"/></pattern></defs><g fill=\"url(#tile)\">");
        for(Coord tile:tiles)out.append("<rect x=\"").append(tile.x-min.x).append("\" y=\"").append(tile.y-min.y).append("\" width=\"1\" height=\"1\" fill=\"#").append(String.format(java.util.Locale.ROOT,"%06x",terrainColor(terrain.getOrDefault(tile,0)))).append("\" stroke=\"#718075\" stroke-width=\".03\"/>");
        out.append("</g><g fill=\"#c6ac72\" stroke=\"#423722\" stroke-width=\".08\">");
        for(ObjectCell object:objects)out.append("<rect x=\"").append(object.x).append("\" y=\"").append(object.y).append("\" width=\"").append(object.w).append("\" height=\"").append(object.h).append("\"><title>").append(xml(object.name)).append("</title></rect>");
        out.append("</g></svg><h2>Объекты</h2><table><tr><th>Название<th>Тип<th>X<th>Y<th>Ширина<th>Высота");
        for(ObjectCell object:objects)out.append("<tr><td>").append(xml(object.name)).append("<td>").append(xml(object.type)).append("<td>").append(object.x).append("<td>").append(object.y).append("<td>").append(object.w).append("<td>").append(object.h);
        Files.writeString(path,out.append("</table>").toString(),StandardCharsets.UTF_8);
    }
    private static String column(int number){StringBuilder name=new StringBuilder();do{name.insert(0,(char)('A'+number%26));number=number/26-1;}while(number>=0);return name.toString();}
    private static void cell(StringBuilder out,int col,int row,String value,int style){out.append("<c r=\"").append(column(col)).append(row).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}
    private static String sheet(){return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">";}
    private static void zip(ZipOutputStream zip,String name,String content)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    private static void xlsx(Path path,String title,Set<Coord> tiles,Coord min,Coord max,List<ObjectCell> objects,Map<Coord,Integer> terrain)throws IOException{
        int width=max.x-min.x,height=max.y-min.y;if(width>16383||height>1048574)throw new IOException("План превышает размер листа Excel");
        Map<Coord,ObjectCell> occupied=new HashMap<>();
        for(ObjectCell object:objects)for(int y=Math.max(0,(int)Math.floor(object.y));y<Math.min(height,Math.ceil(object.y+object.h));y++)for(int x=Math.max(0,(int)Math.floor(object.x));x<Math.min(width,Math.ceil(object.x+object.w));x++)occupied.put(Coord.of(x,y),object);
        StringBuilder plan=new StringBuilder(sheet()).append("<sheetViews><sheetView workbookViewId=\"0\" zoomScale=\"70\"><pane xSplit=\"1\" ySplit=\"2\" topLeftCell=\"B3\" state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"18\"/><cols><col min=\"1\" max=\"").append(width+1).append("\" width=\"3\" customWidth=\"1\"/></cols><sheetData><row r=\"1\">");
        cell(plan,0,1,title+" · Север ↑ · 1 клетка = 1 тайл",0);plan.append("</row><row r=\"2\">");for(int x=0;x<width;x++)cell(plan,x+1,2,Integer.toString(x),0);plan.append("</row>");
        Set<ObjectCell> named=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int y=0;y<height;y++){int row=y+3;plan.append("<row r=\"").append(row).append("\">");cell(plan,0,row,Integer.toString(y),0);for(int x=0;x<width;x++){Coord at=Coord.of(x,y);ObjectCell object=occupied.get(at);if(object!=null)cell(plan,x+1,row,named.add(object)?object.name:"",2);else if(tiles.contains(at.add(min)))cell(plan,x+1,row,"",terrain.containsKey(at.add(min))?terrain.get(at.add(min))+2:1);}plan.append("</row>");}plan.append("</sheetData></worksheet>");
        StringBuilder list=new StringBuilder(sheet()).append("<cols><col min=\"1\" max=\"2\" width=\"32\" customWidth=\"1\"/><col min=\"3\" max=\"6\" width=\"14\" customWidth=\"1\"/></cols><sheetData><row r=\"1\">");String[] headings={"Название","Тип","X","Y","Ширина","Высота"};for(int i=0;i<headings.length;i++)cell(list,i,1,headings[i],0);list.append("</row>");int row=2;
        for(ObjectCell object:objects){list.append("<row r=\"").append(row).append("\">");String[] values={object.name,object.type,""+object.x,""+object.y,""+object.w,""+object.h};for(int i=0;i<values.length;i++){
            if(i<2)cell(list,i,row,values[i],0);
            else list.append("<c r=\"").append(column(i)).append(row).append("\"><v>").append(values[i]).append("</v></c>");
        }list.append("</row>");row++;}list.append("</sheetData></worksheet>");
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(path))){
            zip(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zip(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            zip(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"План\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Объекты\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>");
            zip(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            zip(zip,"xl/styles.xml",terrainStyles("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"1\"><font><sz val=\"10\"/><name val=\"Calibri\"/></font></fonts><fills count=\"4\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDE6DD\"/><bgColor indexed=\"64\"/></patternFill></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE2C68B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/><xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyAlignment=\"1\"><alignment shrinkToFit=\"1\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>"));
            zip(zip,"xl/worksheets/sheet1.xml",plan.toString());zip(zip,"xl/worksheets/sheet2.xml",list.toString());
        }
    }
    private static String terrainStyles(String base){
        StringBuilder fills=new StringBuilder(),styles=new StringBuilder();
        for(int kind=1;kind<=5;kind++){
            fills.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF").append(String.format(java.util.Locale.ROOT,"%06X",terrainColor(kind))).append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
            styles.append("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"").append(kind+3).append("\" borderId=\"0\" xfId=\"0\" applyFill=\"1\"/>");
        }
        return base.replace("fills count=\"4\"","fills count=\"9\"").replace("</fills>",fills+"</fills>").replace("cellXfs count=\"3\"","cellXfs count=\"8\"").replace("</cellXfs>",styles+"</cellXfs>");
    }
    private PlanExport(){}
}
