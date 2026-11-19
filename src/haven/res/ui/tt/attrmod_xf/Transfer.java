/* Preprocessed source code */
package haven.res.ui.tt.attrmod_xf;

import java.util.*;
import haven.*;
import haven.res.ui.tt.attrmod.*;

/* >tt: Transfer */
@FromResource(name = "ui/tt/attrmod-xf", version = 2, override = true)
public class Transfer extends Entry {
    public final Attribute from;
    public final double f;

    public Transfer(Attribute attr, Attribute from, double f) {
	super(attr);
	this.from = from;
	this.f = f;
    }

    public String fmtvalue() {
	// A full share used to omit the number, so 100% of Strength rendered as "+Strength".
	return(String.format("%s{%s%d%% %s}", RichText.Parser.col2a((f < 0) ? Attribute.debuff : Attribute.buff),
			     (f < 0) ? "-" : "+", (int)Math.round(Math.abs(f) * 100), from.name()));
    }

    public static ItemInfo mkinfo(ItemInfo.Owner owner, Object... args) {
	Resource.Resolver rr = owner.context(Resource.Resolver.class);
	int a = 1;
	Attribute attr = Attribute.get(rr.getresv(args[a++]).get());
	Attribute from = Attribute.get(rr.getresv(args[a++]).get());
	double f = Utils.dv(args[a++]);
	return(new AttrMod(owner, Collections.singletonList(new Transfer(attr, from, f))));
    }
}
