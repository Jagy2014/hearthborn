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
/**
 * Common action-button anchor for the login and character screens.
 *
 * @author Jagy2014
 */
public final class LoginControls {
    /** The close button stands well to the right of the login form: centered at 78% of the painting's width. */
    public static Coord position(Coord screen,Coord button){
        Area art=LoginArt.bounds(screen);
        int x=art.ul.x+(int)(art.sz().x*.78)-button.x/2;
        return Coord.of(Math.max(0,Math.min(screen.x-button.x,x)),actionY(screen,button,art));
    }
    /** Leave a clear gap under the action buttons, measured from the painting, not from a letterboxed window. */
    public static int actionY(Coord screen,Coord button){return actionY(screen,button,LoginArt.characterBounds(screen));}
    public static int actionY(Coord screen,Coord button,Area art){
        return Math.max(0,Math.min(screen.y,art.br.y)-button.y-UI.scale(72));
    }
    /** New character stands one button higher than the action row used to: log out and Changelog stack below it. */
    public static int characterCreateY(Coord screen,Coord button){
        return Math.max(0,actionY(screen,button)-button.y-UI.scale(8));
    }
    /** Left edge of the character list on the character screen. */
    public static int listLeft(Coord screen){
        Area art=LoginArt.characterBounds(screen);
        int inset=Math.max(UI.scale(16),(int)(art.sz().x*0.08));
        return Utils.clip(art.ul.x+inset,0,Math.max(0,screen.x-Charlist.bsz.x));
    }
    /** Stack log out directly below New character, aligned to its center. */
    public static Coord characterLogout(Coord screen,Coord button,Coord createAt,Coord createSize){
        int x=createAt.x+(createSize.x-button.x)/2;
        int y=createAt.y+createSize.y+UI.scale(8);
        return Coord.of(Utils.clip(x,0,Math.max(0,screen.x-button.x)),
            Utils.clip(y,0,Math.max(0,Math.min(screen.y,LoginArt.characterBounds(screen).br.y)-button.y-UI.scale(12))));
    }
    public static void close(UI ui){
        try{ui.cons.run(ui.root,"q");}catch(Exception error){ui.error(error.getMessage());}
    }
}
