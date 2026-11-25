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

package hearthborn.storage;

import haven.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import hearthborn.assets.Assets;

/**
 * Compact picture and automatic-mode controls for the container header.
 *
 * @author Jagy2014
 */
public final class CupboardIconButton extends IButton {
    private static final Map<String,BufferedImage> images=new HashMap<>();
    private static BufferedImage image(boolean automatic,int state){
        String path="buttons/container/"+(automatic?"automatic":"choose")+(state==1?"-d":state==2?"-h":"")+".png";
        return images.computeIfAbsent(path,key->{
            try {
                int size=UI.scale(hearthborn.ui.SmallWindowDeco.HEADER_BUTTON);
                return PUtils.convolve(Assets.image(key),Coord.of(size,size),CharWnd.iconfilter);
            }catch(IOException error){throw new IllegalStateException("Missing container button: "+key,error);}
        });
    }
    public CupboardIconButton(boolean automatic,Runnable action){
        super(image(automatic,0),image(automatic,1),image(automatic,2),action);
        settip(hearthborn.L10n.tr(automatic?"Automatic icon":"Choose icon"));
    }
}
