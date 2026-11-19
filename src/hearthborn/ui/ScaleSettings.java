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
 * The sliders that scale the interface: the portrait, the button panels and the text. They live with the interface scale.
 *
 * @author Jagy2014
 */
public final class ScaleSettings {
    private ScaleSettings() {}

    /** Where the sliders start: right after the longest of the four captions, so they line up in one column. */
    public static int column(){
        int widest=0;
        for(String title:new String[]{"Interface scale","Portrait scale","Panel scale","Font size"}){
            Label label=new Label(title);label.settingsFace();
            widest=Math.max(widest,label.sz.x);
            label.dispose();
        }
        return Math.max(UI.scale(150),widest+UI.scale(8));
    }

    /** Adds the three sliders to the host from {@code y} down and returns the lowest of them. */
    public static Widget add(Widget host,int y){
        int column=column();
        Widget last=slider(host,"Portrait scale","Scales the portrait, health bars and the other meters on their own. Panel scale leaves them as they are.",50,150,(int)Math.round(PortraitScale.current()*100),y,column,value->String.format(java.util.Locale.ROOT,"%.2f\u00d7",value/100.0),value->PortraitScale.apply(host.ui,value/100.0));
        last=slider(host,"Panel scale","Scales the button panels: the 4\u00d74 menu and the strip menu, system buttons, the 0-9 and F panels, extra panels and the equipment strip. Windows keep their size.",75,200,(int)Math.round(HudInterfaceScale.current()*100),last.pos("bl").y+UI.scale(3),column,value->String.format(java.util.Locale.ROOT,"%.2f\u00d7",value/100.0),value->HudInterfaceScale.apply(host.ui,value/100.0));
        return slider(host,"Font size","Scales all text in the client. The new size applies after you close the client window completely and start it again; logging out to the character screen is not enough.",hearthborn.UiFonts.MIN_PERCENT,hearthborn.UiFonts.MAX_PERCENT,hearthborn.UiFonts.percent(),last.pos("bl").y+UI.scale(3),column,value->value+" %",value->{
            hearthborn.UiFonts.percent(value);
            if(host.ui!=null)host.ui.msg(hearthborn.L10n.tr("Font size applies after you close the client window completely and start it again (logging out to the character screen is not enough)."));
        });
    }

    private static Widget slider(Widget host,String title,String tip,int min,int max,int value,int y,int column,java.util.function.IntFunction<String> show,java.util.function.IntConsumer apply){
        Label label=host.add(new Label(title),Coord.of(0,y));
        Label readout=host.add(new Label(show.apply(value)),Coord.of(0,y));
        HSlider slider=host.add(new HSlider(UI.scale(150),min,max,value){
            public void changed(){readout.settext(show.apply(val));}
            public void fchanged(){apply.accept(val);}
        },Coord.of(column,y));
        readout.move(Coord.of(slider.c.x+slider.sz.x+UI.scale(10),y));
        slider.settip(tip);
        return slider.sz.y>=label.sz.y?slider:label;
    }
}
