package haven.res.ui.croster;

import haven.*;
import java.util.*;

@haven.FromResource(name = "ui/croster", version = 78, override = true)
public class RosterWindow extends Window {
    public static final Map<Glob, RosterWindow> rosters = new HashMap<>();
    public static int rmseq = 0;
    public int btny = 0;
    public List<TypeButton> buttons = new ArrayList<>();
    private static final Coord DEF_SZ = UI.scale(640, 320);
    private static final String SIZE_PREF = "hearthborn/croster/size";
    private boolean laying, ready;
    private Widget removeDead;

    RosterWindow() {
	super(Coord.z, "Cattle Roster", true);
        removeDead=add(new CheckBox("Automatically remove dead animals").state(hearthborn.ui.CattleCleanup::enabled).set(hearthborn.ui.CattleCleanup::enabled));
        removeDead.settip("Remove roster entries only after the server marks the animal as dead. Applies to existing dead entries too.");
    }

    protected Deco makedeco() {
	return(new hearthborn.ui.SmallWindowDeco(this.large).dragsize(true));
    }

    protected Coord minContentSize() {
	return(UI.scale(350, 255));
    }

    public void show(CattleRoster rost) {
	for(CattleRoster ch : children(CattleRoster.class))
	    ch.show(ch == rost);
    }

    public void addroster(CattleRoster rost) {
	boolean first = buttons.isEmpty();
	add(rost, Coord.z);
	TypeButton btn = this.add(rost.button());
	btn.action(() -> show(rost));
	buttons.add(btn);
	buttons.sort((a, b) -> (a.order - b.order));
	if(first)
	    pack();
	else
	    layoutRosters();
	buttons.get(0).click();
	rmseq++;
    }

    private void layoutRosters() {
	if(laying)
	    return;
	laying = true;
	try {
	    Coord cs = csz();
	    if((cs.x <= 0) || (cs.y <= 0))
		return;
	    int gap = UI.scale(8);
	    int btnH = 0;
	    for(TypeButton b : buttons)
		btnH = Math.max(btnH, b.sz.y);
	    if(btnH == 0)
		btnH = UI.scale(24);
	    int extra = UI.scale(36);
	    int rostH = cs.y - btnH - gap - extra;
	    if(rostH < UI.scale(80))
		rostH = Math.max(UI.scale(80), cs.y - btnH - gap);
	    Coord rostsz = new Coord(Math.max(UI.scale(200), cs.x), rostH);
	    btny = 0;
	    for(CattleRoster ch : children(CattleRoster.class)) {
		ch.resize(rostsz);
		ch.move(Coord.of(0,btnH+gap));
	    }
            if(removeDead!=null)removeDead.move(Coord.of(0,cs.y-extra+gap));
	    int x = 0;
	    for(Widget wdg : buttons) {
		wdg.move(new Coord(x, btny));
		x += wdg.sz.x + gap;
	    }
	} finally {
	    laying = false;
	}
    }

    public void resize(Coord sz) {
	super.resize(sz.max(minContentSize()));
	layoutRosters();
	if(ready)
	    Utils.setprefc(SIZE_PREF, csz());
    }

    protected void added() {
	Coord saved = Utils.getprefc(SIZE_PREF, DEF_SZ);
	ready = true;
	resize(saved);
	super.added();
    }

    public void reqclose() {
	hide();
    }
}
