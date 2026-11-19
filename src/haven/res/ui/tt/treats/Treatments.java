/* Preprocessed source code */
package haven.res.ui.tt.treats;

import haven.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;

/* >tt: Treatments */
@FromResource(name = "ui/tt/wound/treatment", version = 2, override = true)
public class Treatments extends ItemInfo.Tip {
    public static final Text.Foundry hintf = new Text.Foundry(Text.sans, 10, new Color(160, 160, 160)).aa(true);
    public final String[] names;
    public final String[] resources;

    public Treatments(Owner owner, String[] names) {
        this(owner,names,null);
    }

    public Treatments(Owner owner,String[] names,String[] resources){super(owner);this.names=names;this.resources=resources;}

    public static ItemInfo mkinfo(Owner owner, Object... args) {
	Resource.Resolver rr = owner.context(Resource.Resolver.class);
	int a = 1;
	List<String> buf = new ArrayList<String>();
        List<String> resources=new ArrayList<>();
	while(a < args.length) {
	    Indir<Resource> res = rr.getresv(args[a++]);
	    Message sdt = Message.nil;
	    if((a < args.length) && (args[a] instanceof byte[]))
		sdt = new MessageBuf((byte[])args[a++]);
	    ItemSpec spec = new ItemSpec(owner, new ResData(res, sdt), null);
	    buf.add(spec.name());
            resources.add(res.get().name);
	}
	final String[] names = buf.toArray(new String[0]);

	return(new Treatments(owner,names,resources.toArray(new String[0])));
    }

    public BufferedImage tipimg(int width){
        Resource wound=owner instanceof ItemInfo.ResOwner?((ItemInfo.ResOwner)owner).resource():null;
        if(wound==null)return null;
        String text=hearthborn.ui.WoundTreatments.text(wound,names,resources);
        return text.isEmpty()?null:RichText.render(text,width>0?width:UI.scale(300),RichText.IMAGESRC,RichText.ImageSource.res(Resource.remote())).img;
    }
    public BufferedImage tipimg(){return tipimg(UI.scale(300));}

    @Override public void layout(ItemInfo.Layout layout){
        BufferedImage image=tipimg(layout.width);
        if(image!=null)layout.cmp.add(image,Coord.of(0,layout.cmp.sz.y+(layout.cmp.sz.y>0?Text.std.height():0)));
    }

    @Override public int order() {return(1000);}
}
