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

package hearthborn.audio;

import haven.*;
import haven.render.*;

/**
 * The character whistle follows warning volume instead of positional event volume.
 *
 * @author Jagy2014
 */
public final class Whistle {
    private static final String SOUND="sfx/borka/whistle";
    private Whistle(){}
    public static boolean matches(Resource res){return res!=null&&SOUND.equals(res.name);}
    public static void play(ActAudio audio,Audio.Clip clip){
        if(audio==null||audio.warn==null||clip==null)return;
        audio.warn.add(new Audio.VolAdjust(clip.stream(),clip.bvol()));
    }
    public static class Tone extends Sprite {
        private final Audio.Clip clip;
        private boolean played;
        public Tone(Owner owner,Resource res,Audio.Clip clip){super(owner,res);this.clip=clip;}
        public void added(RenderTree.Slot slot){
            if(played)return;
            played=true;
            play(slot.state().get(ActAudio.audio),clip);
        }
        public boolean tick(double dt){return played;}
    }
}
