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
import java.util.prefs.*;

/**
 * Puts one settings section back to its defaults by forgetting what was saved for it.
 *
 * @author Jagy2014
 */
public final class SettingsReset {
    /** Window geometry and similar state is saved next to the settings, but is not one of them. */
    private static boolean keep(String key){
        return key.contains("position")||key.contains("/window-")||key.contains("/size")||key.contains("/moved")||key.contains("/slots/");
    }
    /** Forgets every saved value under the prefixes, returning how many there were. */
    public static int clear(String... prefixes){
        int removed=0;
        try{
            Preferences prefs=Utils.prefs();
            for(String key:prefs.keys()){
                boolean match=false;
                for(String prefix:prefixes)if(key.startsWith(prefix))match=true;
                if(!match||keep(key))continue;
                prefs.remove(key);removed++;
            }
            prefs.flush();Utils.forgetprefs();
        }catch(BackingStoreException|SecurityException e){
            System.err.println("could not reset settings: "+e);
        }
        return removed;
    }

    /** The action behind the reset control of a section that keeps its values under these key prefixes. */
    public static Runnable clearing(String... prefixes){
        return ()->clear(prefixes);
    }

    /** Seconds the question stays up before everything goes back to the quiet button. */
    public static final double PATIENCE=5;
    /** The confirming button ignores clicks this long after the question appears, so a double click cannot confirm. */
    public static final double GUARD=0.8;

    /**
     * "Reset tab" as a quiet button. Pressing it swaps the button for the question "Reset "section"?" with a red
     * "Reset" and a neutral "Cancel" in other places than the button was, and with nothing pressed for a few seconds
     * it goes back. {@code reset} runs on confirmation.
     */
    public static Control control(int width,java.util.function.Supplier<String> section,Runnable reset){
        return new Control(width,section,reset);
    }

    public static final class Control extends Widget{
        private final java.util.function.Supplier<String> section;
        private final Runnable reset;
        public final Button ask,yes,no;
        private final Label question,countdown;
        private double left,age;
        private String shownSection;
        Control(int width,java.util.function.Supplier<String> section,Runnable reset){
            super(Coord.of(width,0));
            this.section=section;this.reset=reset;
            int gap=UI.scale(8);
            ask=new Button(Math.min(width,UI.scale(170)),"Reset tab",false,this::ask).look(Button.Look.QUIET);
            yes=new Button(UI.scale(100),"Reset now",false,this::confirm).look(Button.Look.DANGER);
            no=new Button(UI.scale(100),"Cancel",false,this::cancel).look(Button.Look.OUTLINE);
            countdown=new Label("").tier(UiPalette.NOTE);
            question=new Label("",Math.max(UI.scale(80),width-yes.sz.x-no.sz.x-gap*2)).tier(UiPalette.TITLE);
            int rowHeight=Math.max(yes.sz.y,UI.scale(34));
            int height=rowHeight+UI.scale(6)+UI.scale(16);
            resize(Coord.of(width,height));
            add(ask,Coord.of(0,0));
            // Reset on the right, Cancel at the very end; the quiet button sits at the left, under neither.
            no.c=Coord.of(width-no.sz.x,0);yes.c=Coord.of(no.c.x-gap-yes.sz.x,0);
            add(question,Coord.of(0,0));add(yes,yes.c);add(no,no.c);add(countdown,Coord.of(0,rowHeight+UI.scale(6)));
            mode(false);
        }
        /** True while the question is up. */
        public boolean asking(){return left>0;}
        private void mode(boolean asking){
            ask.visible=!asking;
            question.visible=yes.visible=no.visible=countdown.visible=asking;
            if(asking){
                shownSection=section.get();
                String title=shownSection==null||shownSection.isEmpty()?null:hearthborn.L10n.text("button",shownSection);
                question.settext(title==null?L10n.tr("Reset this tab?"):L10n.format("Reset the \"%s\" tab?",title));
                int rowHeight=Math.max(yes.sz.y,UI.scale(34));
                question.move(Coord.of(0,Math.max(0,(rowHeight-question.sz.y)/2)));
                yes.move(Coord.of(yes.c.x,(rowHeight-yes.sz.y)/2));no.move(Coord.of(no.c.x,(rowHeight-no.sz.y)/2));
                tickCountdown();
            }
        }
        private void tickCountdown(){countdown.settext(L10n.format("No answer: back in %d s",(int)Math.ceil(Math.max(0,left))));}
        public void ask(){left=PATIENCE;age=0;mode(true);}
        public void cancel(){left=0;mode(false);}
        public void confirm(){
            if(left<=0||age<GUARD)return;
            left=0;mode(false);
            reset.run();
            if(ui!=null)ui.msg(L10n.tr("Defaults restored. Close the client window completely and start it again to apply all of them."));
        }
        public void tick(double dt){
            if(left>0){
                age+=dt;
                int before=(int)Math.ceil(left);
                if((left-=dt)<=0)cancel();
                else if((int)Math.ceil(left)!=before)tickCountdown();
            }
            super.tick(dt);
        }
    }
    private SettingsReset(){}
}
