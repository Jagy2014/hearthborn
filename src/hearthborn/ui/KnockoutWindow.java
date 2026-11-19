/* Preprocessed source code */
package hearthborn.ui;
import haven.*;

/* >wdg: KnockoutWindow */
public class KnockoutWindow extends Window {
    public static final String[] acts = {"home", "logout", "nil"};
    private final RadioGroup rad;
    private boolean cepe = false;
    
    public KnockoutWindow() {
	super(Coord.z, "Out cold", true);
	Widget prev = add(new Label("You have fainted."));
	prev = add(new Label("Before waking up:"), prev.pos("bl").adds(0, 15));
	rad = new RadioGroup(this) {
		public void changed(int btn, String lbl) {
		    if((ui != null) && !cepe)
			wdgmsg("act", acts[btn]);
		}
	    };
	prev = rad.add("Travel back to your hearth fire", prev.pos("bl").adds(10, 2));
	prev = rad.add("Log out", prev.pos("bl").adds(0, 2));
	prev = rad.add("Remain in place", prev.pos("bl").adds(0, 2));
	rad.check(0);
	pack();
    }

    public static KnockoutWindow mkwidget(UI ui, Object... args) {
	return(new KnockoutWindow());
    }

    private GameUI game(){GameUI gui=getparent(GameUI.class);return gui!=null?gui:ui.root.findchild(GameUI.class);}
    protected void attached(){super.attached();hearthborn.combat.KnockoutState.enter(game());}
    public void destroy(){hearthborn.combat.KnockoutState.leave(game());super.destroy();}

    public void uimsg(String msg, Object... args) {
	if(msg == "waited") {
	} else if(msg == "sel") {
	    for(int i = 0; i < acts.length; i++) {
		if(acts[i].equals((String)args[0])) {
		    cepe = true;
		    rad.check(i);
		    cepe = false;
		    break;
		}
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }
}
