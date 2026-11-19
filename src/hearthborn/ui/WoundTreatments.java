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
import java.util.*;

/**
 * Server-discovered treatments first, followed by the remaining reference entries.
 *
 * @author Jagy2014
 */
public final class WoundTreatments {
 private static final Properties treatments=hearthborn.l10n.Catalog.load("/hearthborn/ui/wound-treatments.properties");
 public static final class Entry {
  public final String path,english;
  public Entry(String path,String english){this.path=path;this.english=english;}
  String shown(){return path==null?hearthborn.L10n.text("tooltip",english):hearthborn.L10n.resource("tooltip",path,english);}
 }
 private static boolean same(Entry a,Entry b){
  if(a.path!=null&&b.path!=null)return a.path.equals(b.path);
  return Objects.equals(a.english,b.english)||a.shown().equalsIgnoreCase(b.shown());
 }
 public static String sections(List<Entry> known,List<Entry> reference){
  List<Entry> others=new ArrayList<>();for(Entry candidate:reference)if(known.stream().noneMatch(entry->same(entry,candidate)))others.add(candidate);
  StringBuilder out=new StringBuilder();block(out,"Discovered treatments",known);block(out,"Other treatments",others);return out.toString();
 }
 private static void block(StringBuilder out,String heading,List<Entry> entries){
  if(entries.isEmpty())return;
  if(out.length()>0)out.append("\n\n");
  out.append("$b{").append(RichText.Parser.quote(hearthborn.L10n.tr(heading))).append("}\n\n");
  for(Entry entry:entries){
   if(entry.path!=null)out.append("$img[").append(entry.path).append(",c,h=16]");
   String name=entry.shown();out.append(RichText.Parser.quote(name));
   if(!name.equals(entry.english))out.append(" $col[160,160,160]{(").append(RichText.Parser.quote(entry.english)).append(")}");
   out.append("\n");
  }
 }
 public static String text(Resource wound,String[] names,String[] paths){
  List<Entry> known=new ArrayList<>(),reference=new ArrayList<>();
  if(names!=null)for(int i=0;i<names.length;i++)known.add(new Entry(paths!=null&&i<paths.length?paths[i]:null,names[i]));
  String value=treatments.getProperty(wound.name,"");
  for(String path:value.split(","))if(!path.isEmpty()){
   Resource res=Resource.remote().load(path).get();Resource.Tooltip tip=res.layer(Resource.tooltip);
   Entry entry=new Entry(path,tip==null?path:tip.original);reference.add(entry);
   for(int i=0;i<known.size();i++)if(known.get(i).path==null&&same(known.get(i),entry))known.set(i,entry);
  }
  return sections(known,reference);
 }
 private WoundTreatments(){}
}
