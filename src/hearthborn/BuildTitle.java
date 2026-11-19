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

package hearthborn;

import java.io.*;
import java.util.Properties;

/**
 * Build time comes from the packaged metadata, never from the launch clock.
 *
 * @author Jagy2014
 */
public final class BuildTitle {
    private static final String TITLE=load();
    private static String load(){
        Properties info=new Properties();
        try(InputStream in=BuildTitle.class.getResourceAsStream("/buildinfo")){if(in!=null)info.load(in);}catch(IOException ignored){}
        return format(info.getProperty("version"),info.getProperty("build-time-eet"));
    }
    /** "Haven & Hearth - Hearthborn client v1.0 (build 2026-11-16 22:37 EET)"; either part is left out when unknown. */
    static String format(String version,String built){
        String number=version==null||version.trim().isEmpty()||version.contains("${")?null:version.trim();
        String time=built==null||built.trim().isEmpty()||built.contains("${")?null:built.trim();
        return "Haven & Hearth - Hearthborn client"+(number==null?"":" "+number)+(time==null?"":" (build "+time+" EET)");
    }
    public static String title(){return TITLE;}
    private BuildTitle(){}
}
