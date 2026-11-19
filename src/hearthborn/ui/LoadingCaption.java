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
import hearthborn.L10n;
import java.awt.Color;

/**
 * Shared gold loading title; optional texture loading never blocks the UI.
 *
 * @author Jagy2014
 */
public final class LoadingCaption {
    private static final Text.Foundry face=new Text.Foundry(Text.fraktur,32).aa(true);
    private static final Color glow=new Color(96,48,0);
    private static final Text.Furnace fallback=new PUtils.BlurFurn(
        new Text.Foundry(Text.fraktur,32,new Color(255,217,141)).aa(true),3,3,glow);
    private static Indir<Resource> source=Resource.remote().load("gfx/hud/chantex",2);
    private static Text.Furnace textured,lastFont;
    private static Text label;
    private static String text;

    private LoadingCaption(){}

    private static Text.Furnace font(){
        if(textured==null&&source!=null)try{
            textured=new PUtils.BlurFurn(new PUtils.TexFurn(face,source.get().flayer(Resource.imgc).img),3,3,glow);
        }catch(Loading pending){
            // The same gold face and glow remain visible while the texture arrives.
        }catch(Resource.LoadException|Resource.LoadFailedException unavailable){source=null;}
        return textured==null?fallback:textured;
    }

    public static Tex texture(){
        Text.Furnace font=font();String next=L10n.tr("Loading...");
        if(font!=lastFont||!next.equals(text)){
            if(label!=null)label.tex().dispose();
            label=font.render(next);text=next;lastFont=font;
        }
        return label.tex();
    }
}
