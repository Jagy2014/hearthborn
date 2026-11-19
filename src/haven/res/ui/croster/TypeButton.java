package haven.res.ui.croster;

import haven.*;
import java.awt.image.BufferedImage;

@haven.FromResource(name = "ui/croster", version = 78, override = true)
public class TypeButton extends IButton {
    public final int order;

    public TypeButton(BufferedImage up, BufferedImage down, int order) {
	super(up, down);
	this.order = order;
    }

    protected void depress() {
	ui.sfx(Button.clbtdown.stream());
    }

    protected void unpress() {
	ui.sfx(Button.clbtup.stream());
    }
}
