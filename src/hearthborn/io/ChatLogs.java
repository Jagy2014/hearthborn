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

package hearthborn.io;

import haven.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/**
 * Ordered UTF-8 chat logs; disk writes never block the UI thread.
 *
 * @author Jagy2014
 */
public final class ChatLogs {
    private static final ExecutorService writer=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"Chat log writer");thread.setDaemon(true);return thread;});
    private static boolean warned;
    public static boolean enabled(){return Utils.getprefb("hearthborn/chat/save-logs",false);}
    public static void enabled(boolean value){Utils.setprefb("hearthborn/chat/save-logs",value);}
    public static Path directory(){return Logs.directory().resolve("chat").toAbsolutePath().normalize();}
    private static String safe(String value){String name=value.replaceAll("[^\\p{L}\\p{N}._-]","_");return name.isEmpty()||name.equals(".")||name.equals("..")?"character":name;}
    /** Days a log file is kept; older ones are deleted. Zero keeps them all. */
    public static final int KEEP_DAYS=30;
    public static int keepDays(){return Math.max(0,Utils.getprefi("hearthborn/chat/log-days",KEEP_DAYS));}
    private static boolean pruned;
    /** Deletes the day files of every character that are older than {@code days} days before {@code today}. Returns how many went. */
    public static int prune(Path root,LocalDate today,int days){
        if(days<=0||!Files.isDirectory(root))return 0;
        int removed=0;LocalDate oldest=today.minusDays(days);
        try(java.util.stream.Stream<Path> files=Files.walk(root,2)){
            for(Path file:(Iterable<Path>)files.filter(Files::isRegularFile)::iterator){
                String name=file.getFileName().toString();
                if(!name.matches("\\d{4}-\\d{2}-\\d{2}\\.log"))continue;
                LocalDate day;try{day=LocalDate.parse(name.substring(0,10));}catch(java.time.DateTimeException bad){continue;}
                if(day.isBefore(oldest)){try{Files.delete(file);removed++;}catch(java.io.IOException|SecurityException keep){}}
            }
        }catch(java.io.IOException|SecurityException failed){}
        return removed;
    }
    /** Waits until everything given so far has been written. */
    public static void flush(){try{writer.submit(()->{}).get();}catch(InterruptedException|ExecutionException ignored){}}
    /** System lines are never kept: the log is for what people said. */
    public static void append(GameUI gui,ChatUI.Channel channel,String text){
        if(channel instanceof ChatUI.Log)return;
        append(gui,channel.name(),text);
    }
    public static void append(GameUI gui,String channel,String text){
        if(!enabled() || gui==null || text==null)return;
        if(!pruned){pruned=true;writer.execute(()->prune(directory(),LocalDate.now(),keepDays()));}
        ZonedDateTime now=ZonedDateTime.now();
        Path file=directory().resolve(safe(gui.genus+"-"+gui.chrid)).resolve(now.toLocalDate()+".log");
        String line="["+now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)+"] ["+channel.replace('\n',' ').replace('\r',' ')+"] "+text.replace("\r","\\r").replace("\n","\\n")+System.lineSeparator();
        writer.execute(()->{try{Files.createDirectories(file.getParent());Files.writeString(file,line,StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException|SecurityException error){if(!warned){warned=true;new Warning(error,"Could not write chat log: "+file).issue();}}});
    }
    private ChatLogs(){}
}
