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
 * The window title: the client name and the release number from the packaged metadata. The build time is not shown.
 *
 * @author Jagy2014
 */
public final class BuildTitle {
    private static final String TITLE=load();
    private static String load(){
        Properties info=new Properties();
        try(InputStream in=BuildTitle.class.getResourceAsStream("/buildinfo")){if(in!=null)info.load(in);}catch(IOException ignored){}
        return format(info.getProperty("version"));
    }
    /** "Haven & Hearth - Hearthborn client v1.0.1"; the number is left out when unknown. */
    static String format(String version){
        String number=version==null||version.trim().isEmpty()||version.contains("${")?null:version.trim();
        return "Haven & Hearth - Hearthborn client"+(number==null?"":" "+number);
    }
    public static String title(){return TITLE;}
    private BuildTitle(){}
}
