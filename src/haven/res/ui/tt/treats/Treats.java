package haven.res.ui.tt.treats;

import haven.*;
import java.awt.image.BufferedImage;
import java.util.*;

/* >tt: Treats */
@FromResource(name = "ui/tt/treats", version = 1, override = true)
public class Treats extends ItemInfo.Tip {
    /** Light enough to read on the dark tooltip. The server color 192,128,128 is not. */
    public static final String COLOR = "255,200,160";
    public final String[] names;

    public Treats(Owner owner, String[] names) {
	super(owner);
	this.names = names;
    }

    public static ItemInfo mkinfo(Owner owner, Object... args) {
	Resource.Resolver rr = owner.context(Resource.Resolver.class);
	int a = 1;
	List<String> buf = new ArrayList<String>();
	while(a < args.length) {
	    Indir<Resource> res = rr.getresv(args[a++]);
	    Message sdt = Message.nil;
	    if((a < args.length) && (args[a] instanceof byte[]))
		sdt = new MessageBuf((byte[])args[a++]);
	    ItemSpec spec = new ItemSpec(owner, new ResData(res, sdt), null);
	    buf.add(spec.name());
	}
	String[] names = buf.toArray(new String[0]);
	Arrays.sort(names);
	return(new Treats(owner, names));
    }

    public static String line(String[] names) {
	StringBuilder buf = new StringBuilder();
	buf.append("Treats: ");
	for(int i = 0; i < names.length; i++) {
	    if(i > 0)
		buf.append((i < names.length - 1) ? ", " : " & ");
	    buf.append("$col[" + COLOR + "]{" + RichText.Parser.quote(names[i]) + "}");
	}
	return(buf.toString());
    }

    public BufferedImage tipimg() {
	return(RichText.render(line(names), UI.scale(300)).img);
    }
}
