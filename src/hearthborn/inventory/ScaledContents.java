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

package hearthborn.inventory;

import haven.*;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * The server draws a stack as a bare row of items at native size. This shows it as large as the cells it came out of.
 *
 * @author Jagy2014
 */
public final class ScaledContents extends Widget {
    public final Widget inner;
    private final DoubleSupplier wanted;
    private final Consumer<Widget> gone;
    private double scale;

    public ScaledContents(Widget inner,DoubleSupplier wanted,Consumer<Widget> gone){
        super(Coord.z);
        this.inner=inner;this.wanted=wanted;this.gone=gone;
        scale=wanted.getAsDouble();
        add(inner,Coord.z);
        fit();
    }

    public double scale(){return scale;}

    /** One pixel beyond the items, so the last cell keeps its right and bottom edge, as in an inventory. */
    private Coord logical(){return inner.sz.add(1,1);}

    private void fit(){
        Coord target=InventoryScale.size(logical(),scale);
        if(!target.equals(sz))resize(target);
    }

    public void cresize(Widget child){
        super.cresize(child);
        if(child==inner)fit();
    }

    public void cdestroy(Widget child){
        super.cdestroy(child);
        if(child==inner)gone.accept(child);
    }

    public void tick(double dt){
        super.tick(dt);
        double next=wanted.getAsDouble();
        if(next!=scale)scale=next;
        fit();
    }

    public void draw(GOut g){
        GOut scaled=InventoryScale.graphics(g,logical(),scale);
        Coord step=Inventory.sqsz;
        hearthborn.ui.UiTheme.cells(scaled,Coord.of(Math.max(1,(inner.sz.x+step.x-1)/step.x),Math.max(1,(inner.sz.y+step.y-1)/step.y)),null);
        super.draw(scaled);
    }

    public boolean handle(Event event){
        if(event instanceof PointerEvent){
            PointerEvent pointer=((PointerEvent)event).derive(InventoryScale.logical(((PointerEvent)event).c,scale));
            event.stop();pointer.propagate=true;pointer.handling=this;
            return super.handle(pointer)||pointer.propagate(this);
        }
        return super.handle(event);
    }

    public Coord parentpos(Widget in,Coord c){
        return super.parentpos(in,InventoryScale.size(c,scale));
    }
}
