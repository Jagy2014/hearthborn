package hearthborn.ui;

import haven.*;
import haven.resutil.Curiosity;
import java.awt.Color;
import static haven.CharWnd.*;

    public class StudyReport extends Widget {
	public final Widget study;
	public int texp, tw, tenc;
        public double tlph;

        public StudyReport(Coord size, Widget study) {
            super(Coord.of(Math.max(size.x, UI.scale(185)), Math.max(size.y, Math.max(UI.scale(115), UI.scale(2) + rowHeight() * 5 + UI.scale(6)))));
            this.study = study;
            int y = UI.scale(2), row = rowHeight(), right = sz.x - UI.scale(4);
            add(big(new Label("Attention:")), UI.scale(2, 2));
            adda(big(new RLabel<String>(() -> tw + "/" + (ui == null || ui.sess == null ? 0 : ui.sess.glob.getcattr("int").comp),
                value -> value, new Color(255,192,255))), Coord.of(right,y),1,0);
            y += row;
            add(big(new Label("Experience cost:")),Coord.of(UI.scale(2),y));
            adda(big(new RLabel<Integer>(() -> tenc,Utils::thformat,new Color(255,255,192))),Coord.of(right,y),1,0);
            y += row;
            add(big(new Label("Learning points:")),Coord.of(UI.scale(2),y));
            adda(big(new RLabel<Integer>(() -> texp,Utils::thformat,new Color(192,192,255))),Coord.of(right,y),1,0);
            y += row;
            add(big(new Label("LP/hour:")),Coord.of(UI.scale(2),y));
            adda(big(new RLabel<Long>(() -> Math.round(tlph),Utils::thformat,new Color(192,255,255))),Coord.of(right,y),1,0);
            CheckBox lock = new CheckBox("Lock study") {{a=Utils.getprefb("hearthborn/study/locked",false);}};
            lock.settingsFace();
            adda(lock.changed(hearthborn.ui.StudyProgress::locked),Coord.of(UI.scale(2),sz.y-UI.scale(3)),0,1);
        }

        /** The report's words use the same larger face as the settings window. */
        static <T extends Label> T big(T label) { label.settingsFace(); return label; }

        /** The distance between two report rows: the height of the larger face, so that rows never overlap. */
        public static int rowHeight() { return big(new Label("Ag")).sz.y + UI.scale(3); }

	private void upd() {
	    int texp = 0, tw = 0, tenc = 0;
            double rate = 0;
	    for(GItem item : study.children(GItem.class)) {
		try {
		    Curiosity ci = ItemInfo.find(Curiosity.class, item.info());
		    if(ci != null) {
			texp += ci.exp;
                        rate += hearthborn.ui.StudyProgress.perHour(ci);
			tw += ci.mw;
			tenc += ci.enc;
		    }
		} catch(Loading l) {
		}
	    }
	    this.texp = texp; this.tw = tw; this.tenc = tenc; this.tlph = rate;
	}

	public void tick(double dt) {
	    upd();
	    super.tick(dt);
	}
    }

