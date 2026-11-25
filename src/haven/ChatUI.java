/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import java.text.*;
import java.util.regex.*;
import haven.iosys.tk.*;
import java.io.IOException;
import java.net.URI;
import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator.Attribute;

public class ChatUI extends Widget {
    public static final int MAX_MESSAGE_CHARS=500;
    public static boolean messageFits(String text){return text.codePointCount(0,text.length())<=MAX_MESSAGE_CHARS;}
    private static String lengthWarning(){return hearthborn.L10n.tr("Message is too long (maximum 500 characters). Text was not sent or inserted.");}
    public static final RichText.Foundry fnd = new RichText.Foundry(new ChatParser(TextAttribute.FONT, Text.dfont.deriveFont(UI.scale(12f)), TextAttribute.FOREGROUND, Color.WHITE)).aa(true);
    public static final Text.Foundry qfnd = new Text.Foundry(Text.dfont, 12, new java.awt.Color(192, 255, 192));
    public static final int selw = UI.scale(130);
    public static final Coord marg = UI.scale(new Coord(6, 6));
    private static final int tabh = UI.scale(26);
    public static final Color[] urgcols = new Color[] {
	null,
	new Color(0, 128, 255),
	new Color(255, 128, 0),
	new Color(255, 0, 0),
    };
    private static final String FRAME="hearthborn/chat/frame";
    private static volatile boolean framed=Utils.getprefb(FRAME,true);
    public static boolean framed(){return framed;}
    public static void framed(boolean on){framed=on;Utils.setprefb(FRAME,on);}
    private static final String FADE="hearthborn/chat/fade-idle";
    /** Without the frame the idle chat grows dim, so it does not draw the eye while nothing happens in it. */
    public static boolean fadeIdle(){return Utils.getprefb(FADE,true);}
    public static void fadeIdle(boolean on){Utils.setprefb(FADE,on);}
    private static final double IDLE_AFTER=8,FADED=0.2,TEXT_FADED=0.6;
    private double quiet=IDLE_AFTER*2,faded=1;
    private boolean hover(){
        Widget at=detachedWindow!=null?detachedWindow:this;
        return ui!=null&&ui.mc!=null&&ui.mc.sub(at.rootpos()).isect(Coord.z,at.sz);
    }
    private boolean idle(){
        if(framed||!fadeIdle()||hasfocus||(detachedWindow!=null&&detachedWindow.hasfocus))return false;
        return quiet>=IDLE_AFTER&&!hover();
    }
    private void fadeTick(double dt){
        quiet+=dt;
        double target=idle()?FADED:1;
        faded+=(target-faded)*Math.min(1,dt*4);
        if(Math.abs(target-faded)<0.01)faded=target;
    }
    /** 1 while the chat is in use, down to one half when it has been left alone. */
    public double fadeLevel(){return faded;}
    /** The opacity of what the chat draws, 0 to 255. */
    public int fadeAlpha(){return (int)Math.round(255*Math.max(faded,TEXT_FADED));}
    private Window detachedWindow;
    private boolean shownFrame=framed;
    private boolean restoreChecked;
    private double nextRemember;
    private Coord savedPosition,savedSize;
    public void rememberPlace(){rememberDetached();}
    private void rememberDetached(){
        if(detachedWindow==null)return;
        Coord position=detachedWindow.c,size=detachedWindow.csz();
        boolean changed=!position.equals(savedPosition)||!size.equals(savedSize);
        if(!position.equals(savedPosition)){Utils.setprefc("hearthborn/chat/window-position",position);savedPosition=position;}
        if(!size.equals(savedSize)){Utils.setprefc("hearthborn/chat/window-size",size);savedSize=size;}
        if(changed)hearthborn.ui.WindowPlaces.flushPreferences();
    }
    public void tick(double dt){
        if(!restoreChecked){GameUI gui=getparent(GameUI.class);if(gui!=null&&gui.chat==this){restoreChecked=true;detach();}}
        if(detachedWindow!=null&&shownFrame!=framed){shownFrame=framed;detachedWindow.chdeco(decoration());resize(detachedWindow.csz());fitWindow();}
        fadeTick(dt);
        if(Utils.rtime()>=nextRemember){nextRemember=Utils.rtime()+1;rememberDetached();}
        super.tick(dt);
    }
    public void dispose(){closeQuick();rememberDetached();super.dispose();}
    private Coord least(){return Coord.of(Math.max(UI.scale(96),chansel.requiredWidth()),UI.scale(90));}
    private Window.Deco decoration(){
        if(!framed)return new hearthborn.ui.PlainDeco(this::fadeLevel);
        return new hearthborn.ui.SmallWindowDeco(false).translucentDark().hudFrame().compactWhenJoined(marg.y+tabh/2);
    }
    public boolean detached(){return detachedWindow!=null;}
    public void detach(){
        if(detached())return;GameUI gui=getparent(GameUI.class);if(gui==null)return;
        restoreChecked=true;Utils.setprefb("hearthborn/chat/detached",true);
        clearanims(Spring.class);
        Coord size=Utils.getprefc("hearthborn/chat/window-size",UI.scale(650,320)).max(Coord.of(Math.max(UI.scale(96),chansel.requiredWidth()),UI.scale(90)));
        Window host=new Window(size,"Chat"){
            protected Deco makedeco(){return decoration();}
            protected boolean escapeCloses(){return false;}
            public void resize(Coord contents){super.resize(contents.max(least()));if(detachedWindow==this)ChatUI.this.resize(csz());}
            public void dispose(){if(detachedWindow==this)rememberDetached();super.dispose();}
        };
        host.reqclose(host::hide);shownFrame=framed;detachedWindow=host;
        host.add(new hearthborn.ui.LeftStretch(host,"hearthborn/chat/corner"));
        host.add(new hearthborn.ui.RightStretch(host));
        gui.add(host,Utils.getprefc("hearthborn/chat/window-position",UI.scale(180,100)));
        unlink();host.add(this,Coord.z);show();targetshow=true;resize(host.csz());for(Channel channel:children(Channel.class))channel.layoutModeChanged();focusChat();
    }
    /** Restore reachable controls after login, a display change, or reopening the chat. */
    public void fitWindow(){
        GameUI gui=getparent(GameUI.class);
        if(detachedWindow==null||gui==null||gui.sz.x<UI.scale(200)||gui.sz.y<UI.scale(200))return;
        Coord chrome=detachedWindow.sz.sub(detachedWindow.csz());
        Coord maximum=hearthborn.ui.HudInterfaceScale.fromParent(detachedWindow,gui.sz).sub(chrome).max(least());
        Coord contents=detachedWindow.csz(),fitted=contents.min(maximum);
        Coord before=detachedWindow.c;
        if(!contents.equals(fitted))detachedWindow.resize(fitted);
        Coord room=gui.sz.sub(hearthborn.ui.HudInterfaceScale.size(detachedWindow)).max(Coord.z);
        Coord at=Coord.of(Utils.clip(before.x,0,room.x),Utils.clip(before.y,0,room.y));
        if(!before.equals(at)||!contents.equals(fitted)){
            // Screen fitting is temporary; keep the user's edge/stack attachment.
            detachedWindow.move(at);
        }
    }
    /** Tabs use channel names when every name fits; otherwise they stay icons. */
    public boolean namedTabs(){return chansel.words();}
    public boolean closed(){return detachedWindow!=null?!detachedWindow.visible:!visible;}
    /** Show the chat after it was closed, or after a message asked for it. */
    public void present(){sshow(true);focusChat();}
    public void focusChat(){GameUI gui=getparent(GameUI.class);if(gui==null)return;
        Channel recent=hearthborn.ui.ChatButton.takeLatest(gui);
        if(recent!=null&&recent.parent==this&&recent!=sel)select(recent,false);
        if(detachedWindow!=null&&detachedWindow.parent!=null){
            if(!visible)show();
            detachedWindow.show();
            fitWindow();
            detachedWindow.raise();gui.setfocus(detachedWindow);detachedWindow.setfocus(this);
        }else gui.setfocus(this);
        if(sel!=null)setfocus(sel);
    }
    public Channel sel = null;
    public int urgency = 0;
    private final Selector chansel;
    private Coord base = Coord.z;
    private Window quick = null;
    private Label quickName;
    private TextEntry quickEntry;
    private EntryChannel quickChannel;
    private final LinkedList<Notification> notifs = new LinkedList<Notification>();

    public ChatUI() {
	super(Coord.of(0, UI.scale(Math.max(minh, Utils.getprefi("chatsize", minh)))));
	chansel = add(new Selector(new Coord(selw, tabh)), marg);

	setfocusctl(true);
	setcanfocus(true);
    }

    protected void added() {
	base = this.c;
	resize(this.sz);
    }

    public static class ChatAttribute extends Attribute {
	private ChatAttribute(String name) {
	    super(name);
	}

	public static final Attribute HYPERLINK = new ChatAttribute("hyperlink");
    }

    public static class ChatParser extends RichText.Parser {
	public static final Pattern urlpat = Pattern.compile("\\b((https?://)|(www\\.[a-z0-9_.-]+\\.[a-z0-9_.-]+))[a-z0-9/_.~#%+?&:*=-]*", Pattern.CASE_INSENSITIVE);
	public static final Map<? extends Attribute, ?> urlstyle = RichText.fillattrs(TextAttribute.FOREGROUND, new Color(64, 64, 255),
										      TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);

	public ChatParser(Object... args) {
	    super(args);
	}

	protected RichText.Part text(PState s, String text, Map<? extends Attribute, ?> attrs) throws IOException {
	    RichText.Part ret = null;
	    int p = 0;
	    while(true) {
		Matcher m = urlpat.matcher(text);
		if(!m.find(p))
		    break;
		URI uri;
		try {
		    String su = text.substring(m.start(), m.end());
		    if(su.indexOf(':') < 0)
			su = "http://" + su;
		    uri = Utils.uri(su);
		} catch(IllegalArgumentException e) {
		    p = m.end();
		    continue;
		}
		RichText.Part lead = new RichText.TextPart(text.substring(p, m.start()), attrs);
		if(ret == null) ret = lead; else ret.append(lead);
		Map<Attribute, Object> na = new HashMap<Attribute, Object>(attrs);
		na.putAll(urlstyle);
		na.put(ChatAttribute.HYPERLINK, uri);
		ret.append(new RichText.TextPart(text.substring(m.start(), m.end()), na));
		p = m.end();
	    }
	    if(ret == null)
		ret = new RichText.TextPart(text, attrs);
	    else
		ret.append(new RichText.TextPart(text.substring(p), attrs));
	    return(ret);
	}
    }

    public static abstract class Channel extends Widget {
	public final List<RenderedMessage> rmsgs = new ArrayList<>();
	public int urgency = 0;
	/** Messages of other people that arrived while this tab was not in front, or the chat was closed. */
	public int unread = 0;
	/** On the light sheet only the panel dims; the words stay as dark as they are. */
	int fadeAlpha() {ChatUI chat = getparent(ChatUI.class); return(chat == null || hearthborn.ui.UiTheme.light() ? 255 : chat.fadeAlpha());}
	private final Scrollbar sb;
	private final IButton cb;
	private double dy;

        private void layoutModeChanged(){resize(sz);sb.val=newestFirst()?0:Math.max(0,sb.max);dy=sb.val;}
        /** The newest line is on top and older ones go down from it; false for a conversation, where the newest line is at the bottom. */
        protected boolean newestFirst(){return false;}
        protected int messageTop(){return 0;}
        /** Lines that fit the pane start at its top and go down; otherwise the newest line sits at the bottom edge, as in a chat. */
        protected boolean topFirst(){return false;}
        private int scrollMax(int max){return topFirst()?Math.max(0,max):max;}
        private int totalHeight(){if(rmsgs.isEmpty())return 0;RenderedMessage last=rmsgs.get(rmsgs.size()-1);return last.y+last.h();}
        private int messageY(RenderedMessage message){return newestFirst()?totalHeight()-message.y-message.h():message.y;}
        private void drawNewest(GOut g){
            int width=iw(),y=0;
            synchronized(rmsgs){
                for(RenderedMessage message:rmsgs){if(message.w!=width)message.resize(width);message.update();message.y=y;y+=message.h();}
                sb.max=Math.max(0,y-ih());sb.val=Utils.clip(sb.val,0,sb.max);dy=Utils.clip(dy,0,sb.max);
                int scroll=(int)Math.round(dy);GOut out=g.reclip(Coord.of(0,messageTop()),Coord.of(width,Math.max(1,ih())));
                for(int i=rmsgs.size()-1;i>=0;i--){RenderedMessage message=rmsgs.get(i);int top=y-message.y-message.h()-scroll;
                    if(top>=ih())break;if(top+message.h()<0)continue;
                    if(selstart!=null&&selend!=null&&message.idx>=selstart.rm.idx&&message.idx<=selend.rm.idx)drawsel(out,message,top);
                    message.draw(out,Coord.of(0,top));
                }
            }
        }

	public static abstract class Message {
	    public final double time = Utils.ntime();
	    public final String at = hearthborn.ui.ChatStamp.now();

	    public String logText(){
                String text=hearthborn.ui.ChatStamp.read(this,"text"),who=hearthborn.ui.ChatStamp.read(this,"cn");
                return text==null?null:who==null?text:who+": "+text;
            }
            public abstract Indir<Text> render(int w);
	    public boolean valid(Indir<Text> prev) {
		return(true);
	    }
	    public boolean mousedown(Channel chan, CharPos pos, Coord c, int btn) {return(false);}
	    public boolean mouseup(Channel chan, CharPos pos, Coord c, int btn) {return(false);}
	    public boolean clicked(Channel chan, CharPos pos, Coord c, int btn) {return(false);}
	}

	private RenderedMessage soldest = null, snewest = null;
	public class RenderedMessage {
	    public final Message msg;
	    public final int idx;
        private boolean stamped=hearthborn.ui.ChatStamp.enabled();
        private int themed=hearthborn.ui.UiTheme.generation();
	    private Indir<Text> data;
	    private Text text;
        private Tex outlined;
	    RenderedMessage snext = null, sprev = null;
	    double lseen = 0;
	    int w, y;

	    public RenderedMessage(Message msg, int idx, int iw) {
		this.msg = msg;
		this.idx = idx;
		this.w = iw;
	    }

	    /** The width of the time column in front of the text; zero when times are off. */
	    int col() {return(stamped ? hearthborn.ui.ChatStamp.column() : 0);}

	    public Indir<Text> data() {
		if(data == null)
		    data = msg.render(Math.max(1, w - col()));
		return(data);
	    }

	    private void slink() {
		synchronized(Channel.this) {
		    sprev = null;
		    snext = snewest;
		    if(soldest == null)
			soldest = this;
		    if(snewest != null)
			snewest.sprev = this;
		    snewest = this;
		}
	    }

	    private void sunlink() {
		synchronized(Channel.this) {
		    if(sprev != null)
			sprev.snext = snext;
		    if(snext != null)
			snext.sprev = sprev;
		    if(snewest == this)
			snewest = snext;
		    if(soldest == this)
			soldest = sprev;
                    sprev = null;
                    snext = null;
		}
	    }

	    public Text text() {
		lseen = ui.lasttick;
		if(text == null) {
		    text = data().get();
		} else {
		    sunlink();
		}
		slink();
		return(text);
	    }

        public void draw(GOut g,Coord at){
            int alpha=Channel.this.fadeAlpha(),col=col();
            if(alpha<255)g.chcolor(255,255,255,alpha);
            if(col>0){
                Tex stamp=hearthborn.ui.ChatStamp.stamp(msg.at);
                g.image(stamp,at.add(0,Math.max(0,(Math.min(h(),UI.scale(15))-stamp.sz().y)/2)));
            }
            g.image(texture(),(Channel.this instanceof Log?at.sub(1,1):at).add(col,0));
            if(alpha<255)g.chcolor();
        }
        public Tex texture() {
            Text rendered=text();
            if(!(Channel.this instanceof Log))return rendered.tex();
            if(outlined==null)outlined=new TexI(Utils.outline2(rendered.img,Color.BLACK));
            return outlined;
        }

	    private Coord sz = null;
	    public int h() {
		if(sz == null)
		    sz = text().sz();
		return(sz.y);
	    }

	    public void clear() {
                if(outlined!=null){outlined.dispose();outlined=null;}
		if(text != null) {
		    text.dispose();
		    text = null;
		}
                sunlink();
	    }

	    public void invalidate() {
		clear();
		if(data != null) {
		    if(data instanceof Disposable)
			((Disposable)data).dispose();
		    data = null;
		}
		sz = null;
	    }

	    public void resize(int w) {
		if(this.w != w) {
		    this.w = w;
		    invalidate();
		}
	    }

	    public boolean update() {
                if(stamped!=hearthborn.ui.ChatStamp.enabled()||themed!=hearthborn.ui.UiTheme.generation()){stamped=hearthborn.ui.ChatStamp.enabled();themed=hearthborn.ui.UiTheme.generation();invalidate();return true;}
		if((data == null) || msg.valid(data))
		    return(false);
		invalidate();
		return(true);
	    }
	}

	private void trimunseen() {
	    double now = ui.lasttick;
	    while(soldest != null && now - soldest.lseen >= 10) {
		RenderedMessage oldest = soldest;
		oldest.clear();
                // A message that stays at the head would spin this frame forever.
		if(soldest == oldest)
		    break;
	    }
	}

	public static class SimpleMessage extends Message {
	    public final String text;
	    public final Color col;

	    /** True when the line is somebody's words, written "Name: text": only the name is coloured then. */
	    public final boolean speech;

	    public SimpleMessage(String text, Color col) {
		this(text, col, false);
	    }

	    public SimpleMessage(String text, Color col, boolean speech) {
		this.text = text;
		this.col = col;
		this.speech = speech;
	    }

            public String logText(){String who=speaker();return who==null?text:who+": "+text;}

	    public Indir<Text> render(int w) {
		shownSpeaker = speaker();
		String who = shownSpeaker, said = text;
		boolean own = own();
		if(who == null && speech) {
		    String[] parts = splitSpeaker(text);
		    if(parts != null) {
			who = parts[0];
			said = parts[1];
		    }
		}
		String line = hearthborn.ui.ChatStamp.markup(who, said, hearthborn.ui.ChatStamp.speakerColor(who, own));
		/* What people say is in the colour of the text; only the name carries a colour. Notices keep the colour they came in. */
		Color ink = (who != null || col == null || Color.WHITE.equals(col)) ? hearthborn.ui.ChatStamp.TEXT : col;
		return(() -> fnd.render(line, w, TextAttribute.FOREGROUND, hearthborn.ui.UiTheme.readable(ink)));
	    }

	    /** "Name: text" as {name, text}; null when the line does not start with a name. */
	    public static String[] splitSpeaker(String line) {
		java.util.regex.Matcher m = SPEAKER.matcher(line);
		return(m.matches() ? new String[] {m.group(1), m.group(2)} : null);
	    }

	    private static final java.util.regex.Pattern SPEAKER = java.util.regex.Pattern.compile("^([\\p{L}\\p{N}_'\\- ]{1,24}):\\s+(.*)$", java.util.regex.Pattern.DOTALL);

	    /** True for what the player wrote, whose name is light blue. */
	    protected boolean own() {return(false);}

	    private String shownSpeaker;

	    protected String speaker() {return(null);}

	    public boolean valid(Indir<Text> prev) {
		String who = speaker();
		if(who == null)
		    return(shownSpeaker == null);
		return(who.equals(shownSpeaker));
	    }
	}

	public Channel(boolean closable) {
	    sb = add(new hearthborn.ui.JournalScrollport.Grip(0));
	    if(closable)
		cb = add(new IButton("gfx/hud/chat-close", "", "-d", "-h"));
	    else
		cb = null;
	}

	public void append(Message msg, int urgency) {
            if(hearthborn.ui.ChatStamp.realm(msg))msg=new RealmMessage(msg);
            if(hearthborn.io.ChatLogs.enabled())hearthborn.io.ChatLogs.append(getparent(GameUI.class),this,msg.logText());
	    synchronized(rmsgs) {
		RenderedMessage rm = new RenderedMessage(msg, rmsgs.size(), iw());
		if(rmsgs.isEmpty()) {
		    rm.y = 0;
		} else {
		    RenderedMessage lm = rmsgs.get(rmsgs.size() - 1);
		    rm.y = lm.y + lm.h();
		}
		rmsgs.add(rm);
                if(newestFirst()){
                    if(sb.val>0){sb.val+=rm.h();dy+=rm.h();}
                    sb.max=Math.max(0,rm.y+rm.h()-ih());sb.val=Utils.clip(sb.val,0,sb.max);
                }else{
                    boolean b=sb.val>=sb.max;sb.max=scrollMax(rm.y+rm.h()-ih());if(b)sb.val=sb.max;
                }
	    }
	    ChatUI chat = getparent(ChatUI.class);
	    if(urgency >= 0 && (chat.sel != this || chat.closed()))
		unread++;
	    chat.notify(this, msg, urgency);
	    updurgency(Math.max(this.urgency, urgency));
	}

	public void append(Message msg) {
	    append(msg, 0);
	}

	public void append(String line, Color col) {
	    append(new SimpleMessage(line, col));
	}

	public int iw() {
	    return(sz.x - sb.sz.x);
	}

	public int ih() {
	    return(sz.y);
	}

	public void updurgency(int urg) {
	    if(urgency != urg) {
		urgency = urg;
		int mu = 0;
		ChatUI p = getparent(ChatUI.class);
		for(Selector.DarkChannel ch : p.chansel.chls)
		    mu = Math.max(mu, ch.chan.urgency);
		p.urgency = mu;
	    }
	}

	public int messageat(int y, boolean nearest) {
	    synchronized(rmsgs) {
                if(newestFirst()){
                    for(int i=rmsgs.size()-1;i>=0;i--){RenderedMessage message=rmsgs.get(i);int top=messageY(message);if(y>=top&&y<top+message.h())return i;}
                    return nearest&&!rmsgs.isEmpty()?(y<0?rmsgs.size()-1:0):-1;
                }
		int t = 0, b = rmsgs.size();
		while(b > t) {
		    int c = (t + b) / 2;
		    RenderedMessage rm = rmsgs.get(c);
		    if(rm.y > y) {
			b = c;
		    } else if(rm.y + rm.h() < y) {
			t = c + 1;
		    } else {
			return(c);
		    }
		}
		return(nearest ? t : -1);
	    }
	}

	public RenderedMessage messageat(Coord c, Coord hc) {
	    synchronized(rmsgs) {
		int mi = messageat(sb.val + c.y-messageTop(), false);
		if(mi < 0)
		    return(null);
		RenderedMessage rm = rmsgs.get(mi);
		if(hc != null) {
		    hc.x = Math.max(0, c.x - rm.col());
		    hc.y = sb.val+c.y-messageTop()-messageY(rm);
		}
		return(rm);
	    }
	}

	private void updyseq(int mi) {
	    synchronized(rmsgs) {
		mi = Math.min(mi, rmsgs.size() - 1);
		RenderedMessage lm = rmsgs.get(mi++);
		int y = lm.y + lm.h();
		while(mi < rmsgs.size()) {
		    RenderedMessage rm = rmsgs.get(mi++);
		    rm.y = y;
		    y += rm.h();
		}
		boolean b = sb.val >= sb.max;
		sb.max = scrollMax(y - ih());
		if(b)
		    sb.val = sb.max;
		if(sb.val > sb.max)
		    sb.val = sb.max;
	    }
	}

	public void draw(GOut g) {
            if(newestFirst()){drawNewest(g);super.draw(g);updurgency(0);unread=0;trimunseen();return;}
	    g.chcolor(0, 0, 0, 0);
	    g.frect(Coord.z, sz);
	    g.chcolor();
	    int sy = (int)Math.round(dy), h = ih(), w = iw();
	    boolean sel = false;
	    synchronized(rmsgs) {
		int smi = messageat(sy, true);
		if(smi < rmsgs.size()) {
		    int y = rmsgs.get(smi).y;
		    boolean upd = false;
		    for(int mi = smi; mi < rmsgs.size(); mi++) {
			RenderedMessage rm = rmsgs.get(mi);
			if(y > sy + h)
			    break;
			if(rm.w != w) {
			    rm.resize(w);
			    upd = true;
			}
			if(rm.update())
			    upd = true;
			if((selstart != null) && (selend != null)) {
			    if((rm.idx >= selstart.rm.idx) && (rm.idx <= selend.rm.idx))
				drawsel(g, rm, rm.y - sy);
			}
			rm.draw(g,new Coord(0,y-sy));
			y += rm.h();
		    }
		    if(upd)
			updyseq(smi);
		}
	    }
	    super.draw(g);
	    updurgency(0);
	    unread = 0;
	    trimunseen();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    double ty = sb.val;
	    dy = ty + (Math.pow(2, -dt * 40) * (dy - ty));
	}

	public boolean mousewheel(MouseWheelEvent ev) {
	    sb.ch(ev.s * 45);
	    return(true);
	}

	public void resize(Coord sz) {
	    super.resize(sz);
	    if(sb != null) {
		sb.move(new Coord(sz.x,messageTop()));
		sb.resize(Math.max(1,ih()));
		if(!rmsgs.isEmpty()) {
		    RenderedMessage lm = rmsgs.get(rmsgs.size() - 1);
		    boolean b = sb.val >= sb.max && !newestFirst();
		    sb.max = scrollMax(lm.y + lm.h() - ih());
		    if(b)
			sb.val = sb.max;
		}
	    }
            if(newestFirst())sb.val=Utils.clip(sb.val,0,Math.max(0,sb.max));
	    dy = sb.val;
	    if(cb != null) {
		cb.hide();
	    }
	}

	public static class CharPos {
	    public final RenderedMessage rm;
	    public final int pn;
	    public final RichText.TextPart part;
	    public final TextHitInfo ch;

	    public CharPos(RenderedMessage rm, RichText.TextPart part, TextHitInfo ch) {
		this.rm = rm;
		this.pn = partnum((RichText)rm.text(), part);
		this.part = part;
		this.ch = ch;
	    }

	    private static int partnum(RichText text, RichText.TextPart part) {
		int i = 0;
		for(RichText.Part p = text.parts; p != null; p = p.next) {
		    if(p == part)
			return(i);
		    i++;
		}
		throw(new IllegalArgumentException("Text part not part of text"));
	    }

	    public boolean equals(Object oo) {
		if(!(oo instanceof CharPos)) return(false);
		CharPos o = (CharPos)oo;
		return((o.rm == this.rm) && (o.part == this.part) && o.ch.equals(this.ch));
	    }

	    public String toString() {
		return(String.format("#<charpos %s(%s) %s %s>", rm.msg, rm.idx, part, ch));
	    }
	}

	public final Comparator<CharPos> poscmp = new Comparator<CharPos>() {
	    public int compare(CharPos a, CharPos b) {
		if(a.rm != b.rm) {
		    return(a.rm.idx - b.rm.idx);
		} else if(a.pn != b.pn) {
		    return(a.pn - b.pn);
		} else {
		    return(a.ch.getInsertionIndex() - b.ch.getInsertionIndex());
		}
	    }
	};

	public CharPos charat(Coord c) {
            if(newestFirst()&&c.y<messageTop())return null;
	    if(!newestFirst()&&c.y < -sb.val) {
		synchronized(rmsgs) {
		    if(rmsgs.isEmpty())
			return(null);
		    RenderedMessage rm = rmsgs.get(0);
		    if(!(rm.text() instanceof RichText))
			return(null);
		    RichText.TextPart fp = null;
		    for(RichText.Part part = ((RichText)rm.text()).parts; part != null; part = part.next) {
			if(part instanceof RichText.TextPart) {
			    fp = (RichText.TextPart)part;
			    break;
			}
		    }
		    if(fp == null)
			return(null);
		    return(new CharPos(rm, fp, TextHitInfo.leading(0)));
		}
	    }

	    Coord hc = new Coord();
	    RenderedMessage rm = messageat(c, hc);
	    if((rm == null) || !(rm.text() instanceof RichText))
		return(null);
	    RichText rt = (RichText)rm.text();
	    RichText.Part p = rt.partat(hc);
	    if(p == null) {
		RichText.TextPart lp = null;
		for(RichText.Part part = rt.parts; part != null; part = part.next) {
		    if(part instanceof RichText.TextPart)
			lp = (RichText.TextPart)part;
		}
		if(lp == null) return(null);
		return(new CharPos(rm, lp, TextHitInfo.trailing(lp.end - lp.start - 1)));
	    }
	    if(!(p instanceof RichText.TextPart))
		return(null);
	    RichText.TextPart tp = (RichText.TextPart)p;
	    return(new CharPos(rm, tp, tp.charat(hc)));
	}

	private CharPos selorig, lasthit, selstart, selend;
	private UI.Grab grab;
	private boolean dragging;
	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.propagate(this) || super.mousedown(ev))
		return(true);
	    if(grab != null)
		return(true);
	    CharPos ch = charat(ev.c);
	    selorig = ch;
	    if(ch != null) {
		if(ch.rm.msg.mousedown(this, ch, ev.c, ev.b))
		    return(true);
	    }
	    if(ev.b == 1) {
		selstart = selend = null;
		if(ch != null) {
		    lasthit = ch;
		    dragging = false;
		    grab = ui.grabmouse(this);
		}
		return(true);
	    }
	    return(true);
	}

	public void mousemove(MouseMoveEvent ev) {
	    super.mousemove(ev);
	    if(grab != null) {
		CharPos ch = charat(ev.c);
		if((ch != null) && !ch.equals(lasthit)) {
		    lasthit = ch;
		    if(!dragging && !ch.equals(selorig))
			dragging = true;
		    int o = poscmp.compare(selorig, ch);
		    if(o < 0) {
			selstart = selorig; selend = ch;
		    } else if(o > 0) {
			selstart = ch; selend = selorig;
		    } else {
			selstart = selend = null;
		    }
		}
	    }
	}

	public boolean mouseup(MouseUpEvent ev) {
	    if(ev.propagate(this) || super.mouseup(ev))
		return(true);
	    if((ev.b == 1) && (grab != null)) {
		grab.remove();
		grab = null;
		dragging = false;
		if(selstart != null) {
		    selected(selstart, selend);
		    return(true);
		}
	    }
	    CharPos ch = charat(ev.c);
	    if(ch != null) {
		if(ch.rm.msg.mouseup(this, ch, ev.c, ev.b))
		    return(true);
		if(!dragging && (selorig != null) && ch.equals(selorig)) {
		    if(ch.rm.msg.clicked(this, ch, ev.c, ev.b))
			return(true);
		    if(clicked(selorig, ev.b))
			return(true);
		}
	    }
	    return(true);
	}

	protected void selected(CharPos start, CharPos end) {
	    StringBuilder buf = new StringBuilder();
	    synchronized(rmsgs) {
		for(int mi = start.rm.idx; mi <= end.rm.idx; mi++) {
		    RenderedMessage rm = rmsgs.get(mi);
		    if(!(rm.text() instanceof RichText))
			continue;
		    RichText rt = (RichText)rm.text();
		    RichText.Part part = null;
		    if(rm == start.rm) {
			for(part = rt.parts; part != null; part = part.next) {
			    if(part == start.part)
				break;
			}
		    } else {
			part = rt.parts;
		    }
		    for(; part != null; part = part.next) {
			if(!(part instanceof RichText.TextPart))
			    continue;
			RichText.TextPart tp = (RichText.TextPart)part;
			CharacterIterator iter = tp.ti();
			int sch;
			if(tp == start.part)
			    sch = tp.start + start.ch.getInsertionIndex();
			else
			    sch = tp.start;
			int ech;
			if(tp == end.part)
			    ech = tp.start + end.ch.getInsertionIndex();
			else
			    ech = tp.end;
			for(int i = sch; i < ech; i++)
			    buf.append(iter.setIndex(i));
			if(part == end.part)
			    break;
			buf.append(' ');
		    }
		    if(rm != end.rm)
			buf.append('\n');
		}
	    }
	    CharPos ownsel = selstart;
	    Clipboard.Item<CharSequence> item = new Clipboard.Item<>(Clipboard.Format.TEXT, buf);
	    Runnable lost = ()-> {
		if(selstart == ownsel)
		    selstart = selend = null;
	    };
	    /* XXX: Putting on CLIPBOARD should be from eg. C-c, but
	     * that requires Channel as a whole to accept keyobard
	     * focus... */
	    for(Object id : new Object[] {Clipboard.Std.PRIMARY, Clipboard.Std.CLIPBOARD}) {
		ui.wnd.clipboard(id).put(new Clipboard.Contents(item), lost);
	    }
	}

	protected boolean clicked(CharPos pos, int btn) {
	    if(btn == 1) {
		AttributedCharacterIterator inf = pos.part.ti();
		inf.setIndex(pos.ch.getCharIndex() + pos.part.start);
		URI uri = (URI)inf.getAttribute(ChatAttribute.HYPERLINK);
		if(uri != null) {
		    try {
			ui.wnd.toolkit().browse(uri);
		    } catch(java.net.MalformedURLException e) {
			getparent(GameUI.class).error("Could not follow link.");
		    } catch(IOException e) {
			getparent(GameUI.class).error("Could not launch web browser: " + e.getMessage());
		    }
		}
		return(true);
	    }
	    return(false);
	}

	public void select() {
	    getparent(ChatUI.class).select(this);
	}

	public void display() {
	    select();
	    ChatUI chat = getparent(ChatUI.class);
	    chat.expand();
	    chat.parent.setfocus(chat);
	}

	private void drawsel(GOut g, RenderedMessage rm, int y) {
	    RichText rt = (RichText)rm.text();
	    boolean sel = rm != selstart.rm;
	    for(RichText.Part part = rt.parts; part != null; part = part.next) {
		if(!(part instanceof RichText.TextPart))
		    continue;
		RichText.TextPart tp = (RichText.TextPart)part;
		if(tp.start == tp.end)
		    continue;
		TextHitInfo a, b;
		if(sel) {
		    a = TextHitInfo.leading(0);
		} else if(tp == selstart.part) {
		    a = selstart.ch;
		    sel = true;
		} else {
		    continue;
		}
		if(tp == selend.part) {
		    sel = false;
		    b = selend.ch;
		} else {
		    b = TextHitInfo.trailing(tp.end - tp.start - 1);
		}
		Coord ul = new Coord(rm.col() + tp.x + (int)tp.advance(0, a.getInsertionIndex()), tp.y + y);
		Coord sz = new Coord((int)tp.advance(a.getInsertionIndex(), b.getInsertionIndex()), tp.height());
		g.chcolor(0, 0, 255, 255);
		g.frect(ul, sz);
		g.chcolor();
		if(!sel)
		    break;
	    }
	}

	public void uimsg(String name, Object... args) {
	    if(name == "sel") {
		select();
	    } else if(name == "dsp") {
		display();
	    } else {
		super.uimsg(name, args);
	    }
	}

	public void wdgmsg(Widget sender, String msg, Object... args) {
	    if(sender == cb) {
		wdgmsg("close");
	    } else {
		super.wdgmsg(sender, msg, args);
	    }
	}

	public abstract String name();

	public boolean selmousedown(Coord c, int btn) {return(false);}
	public boolean selmouseup(Coord c, int btn) {return(false);}
	public boolean selclicked(Coord c, int btn) {return(false);}

	private Indir<Resource> iconres = null;
	public Channel icon(Indir<Resource> res) {iconres = res; return(this);}
	public Resource.Image icon() {
	    return((iconres == null) ? null : iconres.get().layer(Resource.imgc));
	}

	protected String playerName() {
	    GameUI gui = getparent(GameUI.class);
	    if((gui == null) || (gui.buddies == null))
		return(null);
	    return(gui.buddies.presentationName());
	}

	/** Server realm chat already knows the speaker, but renders neither the name's time nor a stamp. */
	public class RealmMessage extends Message {
	    public final Message msg;

	    public RealmMessage(Message msg) {this.msg = msg;}

            private class Rendered implements Indir<Text> {
                final Indir<Text> source;
                final int width;
                Rendered(int width){this.width=width;source=msg.render(width);}
                public Text get(){
                    String text=hearthborn.ui.ChatStamp.read(msg,"text");
                    if(text==null)return source.get();
                    String who=hearthborn.ui.ChatStamp.read(source,"nm");
                    if(who==null)who=hearthborn.ui.ChatStamp.read(msg,"cn");
                    Object sender=hearthborn.ui.ChatStamp.value(msg,"from");
                    if(who==null)who=hearthborn.ui.ChatStamp.read(sender,"name");
                    Object color=hearthborn.ui.ChatStamp.value(sender,"color");
                    String line=hearthborn.ui.ChatStamp.markup(who,text,hearthborn.ui.ChatStamp.nameColor(who));
                    return fnd.render(line,width,TextAttribute.FOREGROUND,hearthborn.ui.UiTheme.readable(hearthborn.ui.ChatStamp.TEXT));
                }
            }
            public Indir<Text> render(int w){return new Rendered(w);}
            public boolean valid(Indir<Text> data){return msg.valid(((Rendered)data).source);}
            public String logText(){
                Object sender=hearthborn.ui.ChatStamp.value(msg,"from");
                String who=hearthborn.ui.ChatStamp.read(msg,"cn");
                if(who==null)who=hearthborn.ui.ChatStamp.read(sender,"name");
                String text=hearthborn.ui.ChatStamp.read(msg,"text");
                return who==null?text:who+": "+text;
            }

	    public boolean mousedown(Channel chan, CharPos pos, Coord c, int btn) {return(msg.mousedown(chan, pos, c, btn));}
	    public boolean mouseup(Channel chan, CharPos pos, Coord c, int btn) {return(msg.mouseup(chan, pos, c, btn));}
	    public boolean clicked(Channel chan, CharPos pos, Coord c, int btn) {return(msg.clicked(chan, pos, c, btn));}
	}
    }

    public static class Log extends Channel {
	private final String name;
	
	public Log(String name) {
	    super(false);
	    this.name = name;
	}
	
        // The system log has no input line, so its text starts right under the tabs; the newest line is on top and older ones go down.
	protected boolean topFirst() {return(true);}
	protected boolean newestFirst() {return(true);}
	public String name() {return("System".equals(name) ? hearthborn.L10n.tr(name) : name);}
    }

    public static abstract class EntryChannel extends Channel {
	private final TextEntry in;
	private List<String> history = new ArrayList<String>();
	private int hpos = 0;
	private String hcurrent;

	public EntryChannel(boolean closable) {
	    super(closable);
	    setfocusctl(true);
	    this.in = new TextEntry(0, "") {
                    protected void drawBackground(GOut g){
                        if(plain) {
                            super.drawBackground(g);
                        } else if(hearthborn.ui.UiTheme.light()) {
                            hearthborn.ui.UiTheme.darkBackground(g,sz);
                        } else {
                            // The chat already has a tinted backing; only shade the field on top of it.
                            g.chcolor(0,0,0,80);
                            g.frect(Coord.z,sz);
                            g.chcolor();
                        }
                    }
                    /** The accent while the line is in use, a quiet grey when it is only waiting. */
                    protected void drawFrame(GOut g){
                        if(plain)super.drawFrame(g);
                        else hearthborn.ui.UiTheme.entryFrame(g,sz,hasfocus);
                    }
                    protected Color textColor(){return hearthborn.ui.UiTheme.readable(super.textColor());}
                    private String accepted="";
                    private boolean restoring;
                    private double nextWarning;
                    public void changed(ReadLine buffer){
                        if(!restoring){
                            if(!messageFits(buffer.line())){
                                int cursor=Math.min(buffer.point(),accepted.length());
                                restoring=true;
                                try{buffer.setline(accepted);buffer.point(cursor);buffer.mark(-1);}finally{restoring=false;}
                                if(ui!=null&&Utils.rtime()>=nextWarning){ui.error(lengthWarning());nextWarning=Utils.rtime()+1;}
                            }else accepted=buffer.line();
                        }
                        super.changed(buffer);
                    }
		    public void activate(String text) {
                        if(!messageFits(text)){if(ui!=null)ui.error(lengthWarning());return;}
			if(text.length() > 0)
			    send(text);
			settext("");
			hpos = history.size();
		    }

		    public boolean keydown(KeyDownEvent ev) {
			if(ConsoleHost.kb_histprev.key().match(ev)) {
			    if(hpos > 0) {
				if(hpos == history.size())
				    hcurrent = text();
				rsettext(history.get(--hpos));
			    }
			    return(true);
			} else if(ConsoleHost.kb_histnext.key().match(ev)) {
			    if(hpos < history.size()) {
				if(++hpos == history.size())
				    rsettext(hcurrent);
				else
				    rsettext(history.get(hpos));
			    }
			    return(true);
			} else {
			    return(super.keydown(ev));
			}
		    }
		};
	    add(this.in);
	}

        protected int messageTop(){return newestFirstEntry()&&in!=null?in.sz.y+UI.scale(4):0;}
        private boolean newestFirstEntry(){return false;}
	public int ih() {
	    return(Math.max(1,sz.y-(in==null?0:in.sz.y)-(newestFirstEntry()?UI.scale(4):0)));
	}

	public void resize(Coord sz) {
	    super.resize(sz);
	    if(in != null) {
                in.plain=newestFirstEntry();
		in.c = new Coord(0,newestFirstEntry()?0:this.sz.y-in.sz.y);
		in.resize(this.sz.x);
	    }
	}

	public void send(String text) {
            if(!messageFits(text)){if(ui!=null)ui.error(lengthWarning());return;}
	    history.add(text);
	    wdgmsg("msg", text);
	}
    }

    public static class SimpleChat extends EntryChannel {
	public final String name;

	public SimpleChat(boolean closable, String name) {
	    super(closable);
	    this.name = name;
	}

	public void uimsg(String msg, Object... args) {
	    if((msg == "msg") || (msg == "log")) {
		String line = (String)args[0];
		Color col = null;
		if(args.length > 1) col = (Color)args[1];
		if(col == null) col = Color.WHITE;
		int urgency = (args.length > 2) ? Utils.iv(args[2]) : 0;
		Message cmsg = new SimpleMessage(line, col, msg == "msg");
		append(cmsg, urgency);
	    } else {
		super.uimsg(msg, args);
	    }
	}

	public String name() {
            return("Area Chat".equals(name) ? hearthborn.L10n.tr(name) : name);
	}
    }

    public static class MultiChat extends EntryChannel {
	public final int urgency;
	private final String name;
	private final Map<Integer, Color> pc = new HashMap<Integer, Color>();
	private Map<Integer, Boolean> muted = null;
	private Integer mutewait = null;

	public class NamedMessage extends Message {
	    public final int from;
	    public final String text;
	    public final Color col;

	    public NamedMessage(int from, String text, Color col) {
		this.from = from;
		this.text = text;
		this.col = col;
	    }

	    public class Rendered implements Indir<Text> {
		public final int w;
		public final String nm;

		public Rendered(int w, String nm) {
		    this.w = w;
		    this.nm = nm;
		}

		public Text get() {
		    return(fnd.render(hearthborn.ui.ChatStamp.markup(nm, text, hearthborn.ui.ChatStamp.nameColor(nm)), w, TextAttribute.FOREGROUND, hearthborn.ui.UiTheme.readable(hearthborn.ui.ChatStamp.TEXT)));
		}
	    }

	    private String nm() {
		BuddyWnd.Buddy b = getparent(GameUI.class).buddies.find(from);
		return((b == null) ? "???" : b.name);
	    }

            public String logText(){return nm()+": "+text;}

	    public Indir<Text> render(int w) {
		return(new Rendered(w, nm()));
	    }

	    public boolean valid(Indir<Text> data) {
		return(((Rendered)data).nm.equals(nm()));
	    }

	    public boolean clicked(Channel chan, CharPos pos, Coord c, int btn) {
		if((btn == 3) && (muted != null)) {
		    Boolean muted = MultiChat.this.muted.get(from);
		    if(muted == null) {
			mutewait = from;
			wdgmsg("muted", from);
		    } else {
			mutemenu(from, muted);
		    }
		    return(true);
		}
		return(super.clicked(chan, pos, c, btn));
	    }
	}

	private void mutemenu(int pl, boolean cur) {
	    SListMenu.Action ma = SListMenu.Action.of(cur ? "Unmute" : "Mute", () -> wdgmsg("mute", pl, cur ? 0 : 1));
	    SListMenu.of(UI.scale(250, 120), null, Arrays.asList(ma)).addat(ui.root, ui.mc);
	}

	public class MyMessage extends SimpleMessage {
	    public MyMessage(String text) {
		super(text, new Color(192, 192, 255));
	    }

	    protected String speaker() {return(playerName());}
	    protected boolean own() {return(true);}
	}

	public MultiChat(boolean closable, String name, int urgency) {
	    super(closable);
	    this.name = name;
	    this.urgency = urgency;
	}

	private float colseq = 0;
	private Color nextcol() {
	    return(new Color(Color.HSBtoRGB(colseq = ((colseq + (float)Math.sqrt(2)) % 1.0f), 0.5f, 1.0f)));
	}

	public Color fromcolor(int from) {
	    synchronized(pc) {
		Color c = pc.get(from);
		if(c == null)
		    pc.put(from, c = nextcol());
		return(c);
	    }
	}

	public void uimsg(String msg, Object... args) {
	    if(msg == "msg") {
		Number from = (Number)args[0];
		String line = (String)args[1];
		if(from == null) {
		    append(new MyMessage(line), -1);
		} else {
		    Message cmsg = new NamedMessage(from.intValue(), line, fromcolor(from.intValue()));
		    append(cmsg, urgency);
		}
	    } else if(msg == "mutable") {
		this.muted = Utils.bv(args[0]) ? new HashMap<>() : null;
	    } else if(msg == "muted") {
		int pl = Utils.iv(args[0]);
		boolean muted = Utils.bv(args[1]);
		this.muted.put(pl, muted);
		if((mutewait != null) && (mutewait == pl)) {
		    mutewait = null;
		    mutemenu(pl, muted);
		}
	    } else {
		super.uimsg(msg, args);
	    }
	}

	public boolean isArea() {return("Area Chat".equals(name));}

	public String name() {
	    if("Area Chat".equals(name) || this instanceof PartyChat)
		return(hearthborn.L10n.tr(name));
	    return(name);
	}
    }
    
    public static class PartyChat extends MultiChat {
	public PartyChat() {
	    super(false, "Party", 2);
	}

	public void uimsg(String msg, Object... args) {
	    if(msg == "msg") {
		Number from = (Number)args[0];
		long gobid = Utils.uiv(args[1]);
		String line = (String)args[2];
		Color col = Color.WHITE;
		synchronized(ui.sess.glob.party.memb) {
		    Party.Member pm = ui.sess.glob.party.memb.get(gobid);
		    if(pm != null)
			col = pm.col;
		}
		if(from == null) {
		    append(new MyMessage(line), -1);
		} else {
		    Message cmsg = new NamedMessage(from.intValue(), line, Utils.blendcol(col, Color.WHITE, 0.5));
		    append(cmsg, urgency);
		}
	    } else {
		super.uimsg(msg, args);
	    }
	}
    }
    
    public static class PrivChat extends EntryChannel {
	private final int other;
	private boolean muted;
	
	public PrivChat(boolean closable, int other) {
	    super(closable);
	    this.other = other;
	}

	private void menu() {
	    SListMenu.Action ma = SListMenu.Action.of(muted ? "Unmute" : "Mute", () -> wdgmsg("mute", muted ? 0 : 1));
	    SListMenu.of(UI.scale(250, 120), null, Arrays.asList(ma)).addat(ui.root, ui.mc);
	}

	public boolean selclicked(Coord c, int btn) {
	    if(btn == 3) {
		menu();
		return(true);
	    }
	    return(super.selclicked(c, btn));
	}

	public class InMessage extends SimpleMessage {
	    public InMessage(String text) {
		super(text, new Color(255, 128, 128, 255));
	    }

	    protected String speaker() {
		GameUI gui = getparent(GameUI.class);
		if(gui == null || gui.buddies == null)
		    return(null);
		BuddyWnd.Buddy b = gui.buddies.find(other);
		return((b == null) ? null : b.name);
	    }

	    public boolean clicked(Channel chan, CharPos pos, Coord c, int btn) {
		if(btn == 3) {
		    menu();
		    return(true);
		}
		return(super.clicked(chan, pos, c, btn));
	    }
	}

	public class OutMessage extends SimpleMessage {
	    public OutMessage(String text) {
		super(text, new Color(128, 128, 255, 255));
	    }

	    protected String speaker() {return(playerName());}
	    protected boolean own() {return(true);}
	}

	public void uimsg(String msg, Object... args) {
	    if(msg == "msg") {
		String t = (String)args[0];
		String line = (String)args[1];
		if(t.equals("in")) {
		    Message cmsg = new InMessage(line);
		    append(cmsg, 3);
		} else if(t.equals("out")) {
		    append(new OutMessage(line), -1);
		}
	    } else if(msg == "err") {
		String err = (String)args[0];
		Message cmsg = new SimpleMessage(err, Color.RED);
		append(cmsg, 3);
	    } else if(msg == "muted") {
		this.muted = Utils.bv(args[0]);
	    } else {
		super.uimsg(msg, args);
	    }
	}
	
	public String name() {
	    BuddyWnd.Buddy b = getparent(GameUI.class).buddies.find(other);
	    if(b == null)
		return("???");
	    else
		return(b.name);
	}
    }
    
    @RName("schan")
    public static class $SChan implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String name = (String)args[0];
	    Channel ret = new SimpleChat(false, name);
	    if(args.length > 1)
		ret.icon(ui.sess.getresv(args[1]));
	    return(ret);
	}
    }
    @RName("mchat")
    public static class $MChat implements Factory {
	public Widget create(UI ui, Object[] args) {
	    String name = (String)args[0];
	    int urgency = Utils.iv(args[1]);
	    Channel ret = new MultiChat(false, name, urgency);
	    if(args.length > 2)
		ret.icon(ui.sess.getresv(args[2]));
	    return(ret);
	}
    }
    @RName("pchat")
    public static class $PChat implements Factory {
	public Widget create(UI ui, Object[] args) {
	    Channel ret = new PartyChat();
	    if(args.length > 0)
		ret.icon(ui.sess.getresv(args[0]));
	    return(ret);
	}
    }
    @RName("pmchat")
    public static class $PMChat implements Factory {
	public Widget create(UI ui, Object[] args) {
	    int other = Utils.iv(args[0]);
	    Channel ret = new PrivChat(true, other);
	    if(args.length > 1)
		ret.icon(ui.sess.getresv(args[1]));
	    return(ret);
	}
    }

    public void addchild(Widget child, Object... args) {
	add(child);
    }

    public <T extends Widget> T add(T w) {
	if(w instanceof Channel) {
	    Channel chan = (Channel)w;
	    chan.c = Coord.of(marg.x, marg.y + tabh);
	    chan.resize(Math.max(1,sz.x - 2*marg.x), Math.max(1,sz.y - chan.c.y - marg.y));
	    super.add(w);
	    chansel.add(chan);
	    if(sel == null) select(chan, false);
	    else chan.hide();
	    return(w);
	} else {
	    return(super.add(w));
	}
    }

    public void cdestroy(Widget w) {
	if(w instanceof Channel) {
	    Channel chan = (Channel)w;
	    if(chan == sel)
		sel = null;
	    chansel.rm(chan);
	}
    }
    
    private class Selector extends Widget {
	public final Text.Foundry tf = new Text.Foundry(Text.serif.deriveFont(Font.BOLD, UI.scale(12))).aa(true);
	public final Color[] uc = {
	    new Color(80, 40, 0),
	    new Color(0, 128, 255),
	    new Color(255, 128, 0),
	    new Color(255, 0, 0),
	};
	private final List<DarkChannel> chls = new ArrayList<DarkChannel>();
	private final int iconsz = UI.scale(20);
        private int iconTab(DarkChannel channel){return UI.scale(28)+(closable(channel.chan)?UI.scale(18):0);}
        private int wordTab(DarkChannel channel){return channel.rname().sz().x+UI.scale(20)+(closable(channel.chan)?UI.scale(18):0);}
        private boolean words(){
            int used=0;
            synchronized(chls){for(DarkChannel channel:chls)used+=wordTab(channel);}
            return !chls.isEmpty()&&used<=Math.max(1,sz.x);
        }
        private int tabwidth(DarkChannel channel){return words()?wordTab(channel):iconTab(channel);}
        private int requiredWidth(){
            int used=0;
            synchronized(chls){for(DarkChannel channel:chls)used+=iconTab(channel);}
            return used+marg.x*2;
        }
        private void fitButtons(){if(detached()&&detachedWindow.csz().x<requiredWidth())detachedWindow.resize(Coord.of(requiredWidth(),detachedWindow.csz().y));}
        private int navwidth(){
            if(words())return 0;
            int used=0;
            synchronized(chls){for(DarkChannel channel:chls)used+=iconTab(channel);}
            return used>Math.max(1,sz.x)?UI.scale(18):0;
        }
        private int viewport(){return Math.max(1,sz.x-2*navwidth());}
        private boolean closable(Channel channel){
            return channel.cb!=null && !(channel instanceof MultiChat) && !hearthborn.ui.ChatStamp.realmChannel(channel);
        }
        private boolean closehit(Channel channel,Coord c){
            int index=chidx(channel);
            return index>=0 && closable(channel) && c.x-navwidth()+(int)Math.round(ds)>=tabstart(index)+tabwidth(chls.get(index))-UI.scale(20);
        }
        private int tabstart(int index){int x=0;for(int i=0;i<index;i++)x+=tabwidth(chls.get(i));return x;}
        private int firstVisible(int scroll){int i=0;while(i<chls.size() && tabstart(i)+tabwidth(chls.get(i))<=scroll)i++;return i;}
        private final Map<String,Tex> fallbackIcons=new HashMap<>();
        private Tex fallbackIcon(Channel channel){
            boolean globe=channel instanceof MultiChat && "Area Chat".equals(((MultiChat)channel).name);
            boolean gear=channel instanceof Log;
            return fallbackIcons.computeIfAbsent((hearthborn.ui.UiTheme.light()?"L/":"D/")+(gear?"gear":globe?"globe":"chat"),key->{
                BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_4BYTE_ABGR);
                java.awt.Graphics2D g=image.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(hearthborn.ui.UiTheme.light()?hearthborn.ui.Parchment.TEXT2:new Color(225,203,145));g.setStroke(new java.awt.BasicStroke(2));
                if(gear){g.drawOval(7,7,18,18);g.drawOval(12,12,8,8);for(int i=0;i<8;i++){double a=i*Math.PI/4;g.drawLine(16+(int)(10*Math.cos(a)),16+(int)(10*Math.sin(a)),16+(int)(14*Math.cos(a)),16+(int)(14*Math.sin(a)));}}
                else if(globe){g.drawOval(3,3,26,26);g.drawOval(10,3,12,26);g.drawLine(3,16,29,16);g.drawArc(5,8,22,10,0,360);}
                else {g.drawRoundRect(3,5,26,19,5,5);g.drawLine(8,24,7,29);g.drawLine(7,29,15,24);g.drawLine(8,11,24,11);g.drawLine(8,17,21,17);}
                g.dispose();return new TexI(PUtils.uiscale(image,Coord.of(iconsz,iconsz)));
            });
        }
	private int ts = 0;
	private double ds = 0;
	private Channel cstart;

        public Text nmrender(String name, Color col) {
            return tf.render(name, col);
        }

	public int chidx(Channel chan) {
	    for(int i = 0; i < chls.size(); i++) {
		if(chls.get(i).chan == chan)
		    return(i);
	    }
	    return(-1);
	}

        public void resize(Coord sz) {
            super.resize(sz);
            ds=clips((int)Math.round(ds)); ts=clips(ts);
            show(chidx(sel));
        }

	private class DarkChannel {
	    public final Channel chan;
	    public Text rname;
	    public Tex ricon;
	    private int urgency = 0;
            private Text shortName;private Text shortSource;private int shortWidth=-1;
	    private Resource.Image icon;

	    private DarkChannel(Channel chan) {
		this.chan = chan;
	    }

	    public Text rname() {
		String name = chan.name();
		int urg = chan.urgency;
		if((rname == null) || !rname.text.equals(name) || (urgency != urg)) {
		    Text.Line raw = tf.render(name);

		    rname = new Text(name, tf.render(raw.text, urg == 0 ? new Color(234,220,179) : urgcols[urg]).img);
                    urgency = urg;
		}
		return(rname);
	    }

            public Tex label(int width){
                Text source=rname();width=Math.max(0,width);
                if(source.sz().x<=width)return source.tex();
                if(shortName==null||shortSource!=source||shortWidth!=width){
                    String text=source.text;
                    while(!text.isEmpty()&&tf.strsize(text+"…").x>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));
                    if(shortName!=null)shortName.dispose();shortSource=source;shortWidth=width;
                    shortName=tf.render(tf.strsize("…").x<=width?text+"…":"",chan.urgency==0?new Color(234,220,179):urgcols[chan.urgency]);
                }
                return shortName.tex();
            }
	    public Tex ricon() {
                Resource.Image img = chan instanceof Log?null:chan instanceof MultiChat && "Area Chat".equals(((MultiChat)chan).name)?null:chan.icon();
                if(img == null) return fallbackIcon(chan);
		if((ricon == null) || (icon != img)) {
                    if(ricon!=null)ricon.dispose();
		    Coord sz;
		    if(img.sz.x > img.sz.y)
			sz = Coord.of(iconsz, (iconsz * img.sz.y) / img.sz.x);
		    else
			sz = Coord.of((iconsz * img.sz.x) / img.sz.y, iconsz);
		    ricon = new TexI(PUtils.uiscale(img.img, sz));
		    icon = img;
		}
		return(ricon);
	    }
	}

        public void dispose(){
            for(Tex texture:fallbackIcons.values())texture.dispose();
            for(DarkChannel channel:chls)if(channel.ricon!=null)channel.ricon.dispose();
            super.dispose();
        }
	public Selector(Coord sz) {
	    super(sz);
	}

	private void add(Channel chan) {
	    synchronized(chls) {
		chls.add(new DarkChannel(chan));
	    }
	}

	private void rm(Channel chan) {
	    synchronized(chls) {
		for(Iterator<DarkChannel> i = chls.iterator(); i.hasNext();) {
		    DarkChannel c = i.next();
		    if(c.chan == chan)
			i.remove();
		}
	    }
	}

        private final Text.Foundry badgeFace=new Text.Foundry(Text.sans.deriveFont(Font.BOLD,UI.scale(10f))).aa(true);
        private final Map<String,Text> badges=new HashMap<>();
        private int badgeGeneration=-1;
        private Tex badge(int count){
            if(badgeGeneration!=hearthborn.ui.UiTheme.generation()){for(Text text:badges.values())text.dispose();badges.clear();badgeGeneration=hearthborn.ui.UiTheme.generation();}
            String label=count>9?"9+":Integer.toString(count);
            return badges.computeIfAbsent(label,key->badgeFace.render(key,new Color(18,14,8))).tex();
        }
        /** How bright a tab is: the tab in front and the ones with something new at their full strength, the rest a little dimmer; all of it fades with the chat. */
        private double strength(DarkChannel ch){
            double fade=fadeLevel();
            if(ch.chan.unread>0)return 1;
            // Ink on parchment is already light, so the light sheet neither dims a tab with the chat nor drops a resting tab below 0.85.
            if(hearthborn.ui.UiTheme.light())return ch.chan==sel?1:0.85;
            return (ch.chan==sel?1:0.6)*Math.max(fade,0.5);
        }
        private Color accent(double alpha){Color c=hearthborn.ui.UiPalette.accent();return new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)Math.round(255*alpha));}
        /** Flat tabs on one thin line: the tab in front has a line of the accent under it, a tab with news a badge with their number. */
        public void draw(GOut g) {
            int scroll=(int)Math.round(ds);
            g.chcolor(hearthborn.ui.UiPalette.border());g.frect(Coord.of(0,sz.y-UI.scale(1)),Coord.of(sz.x,UI.scale(1)));g.chcolor();
            GOut strip=g.reclip(Coord.of(navwidth(),0),Coord.of(viewport(),sz.y));
            synchronized(chls) {
                for(int i=firstVisible(scroll);i<chls.size();i++) {
                    int x=tabstart(i)-scroll;
                    int width=tabwidth(chls.get(i));
                    if(x>=viewport())break;
                    DarkChannel ch=chls.get(i);
                    GOut tab=strip.reclip(Coord.of(x,0),Coord.of(width,sz.y));
                    boolean active=ch.chan==sel;
                    double strength=strength(ch);
                    int alpha=(int)Math.round(255*strength);
                    Tex icon=null;
                    try {icon=ch.ricon();}catch(Loading ignored){}
                    tab.chcolor(255,255,255,alpha);
                    int badgeX;
                    if(!words()){if(icon!=null)tab.aimage(icon,Coord.of(UI.scale(14),sz.y/2-UI.scale(1)),.5,.5);badgeX=UI.scale(24);}
                    else {tab.aimage(ch.label(width-UI.scale(closable(ch.chan)?31:16)),Coord.of(UI.scale(8),sz.y/2-UI.scale(1)),0,.5);badgeX=width-UI.scale(closable(ch.chan)?30:12);}
                    tab.chcolor();
                    if(active){tab.chcolor(accent(Math.max(0.5,strength)));tab.frect(Coord.of(UI.scale(2),sz.y-UI.scale(2)),Coord.of(Math.max(1,width-UI.scale(4)),UI.scale(2)));tab.chcolor();}
                    if(ch.chan.unread>0){
                        Tex count=badge(ch.chan.unread);
                        int r=Math.max(UI.scale(7),count.sz().x/2+UI.scale(3));
                        tab.chcolor(accent(1));tab.fellipse(Coord.of(badgeX,UI.scale(7)),Coord.of(r,UI.scale(7)));tab.chcolor();
                        tab.aimage(count,Coord.of(badgeX,UI.scale(7)),.5,.5);
                    }
                    if(closable(ch.chan)) {
                        int cx=width-UI.scale(11),cy=sz.y/2,r=UI.scale(3);
                        Color quiet=hearthborn.ui.UiPalette.text2();
                        tab.chcolor(quiet.getRed(),quiet.getGreen(),quiet.getBlue(),alpha);
                        tab.line(Coord.of(cx-r,cy-r),Coord.of(cx+r,cy+r),UI.scale(1));
                        tab.line(Coord.of(cx+r,cy-r),Coord.of(cx-r,cy+r),UI.scale(1));tab.chcolor();
                    }
                }
            }
            if(navwidth()>0){navigation(g,false);navigation(g,true);}
        }
        private void navigation(GOut g,boolean right){
            int x=right?sz.x-navwidth():0,w=navwidth();
            boolean enabled=right?ts<clips(Integer.MAX_VALUE):ts>0;
            g.chcolor(32,35,23,235);g.frect(Coord.of(x,0),Coord.of(w,sz.y));
            g.chcolor(enabled?new Color(223,197,133):new Color(102,96,72));
            int center=x+w/2,cy=sz.y/2,r=UI.scale(3),direction=right?1:-1;
            g.line(Coord.of(center-direction*r,cy-r),Coord.of(center+direction*r,cy),UI.scale(1));
            g.line(Coord.of(center+direction*r,cy),Coord.of(center-direction*r,cy+r),UI.scale(1));g.chcolor();
        }
        public Object tooltip(Coord c,Widget prev) {
            if(c.x<navwidth())return hearthborn.L10n.tr("Previous chat tabs");
            if(c.x>=sz.x-navwidth())return hearthborn.L10n.tr("Next chat tabs");
            Channel channel=bypos(c);
            return channel==null?null:closehit(channel,c)?hearthborn.L10n.tr("Close"):channel.name();
        }

	public void tick(double dt) {
	    super.tick(dt);
            fitButtons();if(words()){ts=0;ds=0;}
	    ds = ts + (Math.pow(2, -dt * 20) * (ds - ts));
	}

        public void show(int si) {
            if(si<0)return;
            int x=tabstart(si),width=tabwidth(chls.get(si));
            if(x+width>ts+viewport())ts=x+width-viewport();
            else if(x<ts)ts=x;
            ts=clips(ts);
        }

	public void show(Channel chan) {
	    int si = chidx(chan);
	    if(si >= 0)
		show(si);
	}

	public boolean up() {
	    Channel prev = null;
	    for(DarkChannel ch : chls) {
		if(ch.chan == sel) {
		    if(prev != null) {
			select(prev);
			return(true);
		    } else {
			return(false);
		    }
		}
		prev = ch.chan;
	    }
	    return(false);
	}

	public boolean down() {
	    for(Iterator<DarkChannel> i = chls.iterator(); i.hasNext();) {
		DarkChannel ch = i.next();
		if(ch.chan == sel) {
		    if(i.hasNext()) {
			select(i.next().chan);
			return(true);
		    } else {
			return(false);
		    }
		}
	    }
	    return(false);
	}

	private Channel bypos(Coord c) {
	    int ds = (int)Math.round(this.ds);
            if(c.x<navwidth()||c.x>=sz.x-navwidth())return null;
            int x=c.x-navwidth()+ds;
            for(int i=0;i<chls.size();i++)if(x>=tabstart(i) && x<tabstart(i)+tabwidth(chls.get(i)))return chls.get(i).chan;
	    return(null);
	}

	public boolean mousedown(MouseDownEvent ev) {
            if(ev.b==1&&(ev.c.x<navwidth()||ev.c.x>=sz.x-navwidth())){
                ts=clips(ts+(ev.c.x<navwidth()?-1:1)*Math.max(UI.scale(64),viewport()/2));cstart=null;return true;
            }
	    Channel chan = bypos(ev.c);
            if(chan!=null && ev.b==1 && closehit(chan,ev.c)){chan.wdgmsg("close");cstart=null;return true;}
	    cstart = chan;
	    if(chan != null) {
		if(ev.b == 1) {
		    select(chan);
		} else {
		    chan.selmousedown(ev.c, ev.b);
		}
	    }
	    return(true);
	}

	public boolean mouseup(MouseUpEvent ev) {
	    Channel chan = bypos(ev.c);
	    if(chan != null) {
		if(ev.b != 1) {
		    chan.selmouseup(ev.c, ev.b);
		    if(cstart == chan)
			chan.selclicked(ev.c, ev.b);
		}
	    }
	    cstart = null;
	    return(true);
	}

	private int clips(int s) {
	    int maxh = tabstart(chls.size()) - viewport();
	    return(Math.max(Math.min(s, maxh), 0));
	}

	public boolean mousewheel(MouseWheelEvent ev) {
	    if(!ui.modshift) {
		ts = clips(ts + (ev.a * UI.scale(40)));
	    } else {
		if(ev.a < 0)
		    up();
		else if(ev.a > 0)
		    down();
	    }
	    return(true);
	}
    }

    public void select(Channel chan, boolean focus) {
	Channel prev = sel;
	sel = chan;
	chan.unread = 0;
	if(prev != null)
	    prev.hide();
	sel.show();
	chansel.show(chan);
	resize(sz);
	if(focus || hasfocus)
	    setfocus(chan);
    }

    public void select(Channel chan) {
	select(chan, true);
    }

    private class Notification {
	public final Channel chan;
	public final Channel.Message msg;
	public final Text chnm, rmsg;
	public final double time = Utils.ntime();

	private Notification(Channel chan, Channel.Message msg) {
	    this.chan = chan;
	    this.msg = msg;
	    this.chnm = fnd.render(chan.name(), 0, TextAttribute.FOREGROUND, Color.WHITE);
	    this.rmsg = msg.render(sz.x - selw).get();
	}
    }

    public void drawsmall(GOut g, Coord br, int h) {
	Coord c = br.sub(UI.scale(0, 5));
	double now = Utils.ntime();
	synchronized(notifs) {
	    for(Iterator<Notification> i = notifs.iterator(); i.hasNext();) {
		Notification n = i.next();
		if(now - n.time > 5.0) {
		    i.remove();
		    continue;
		}
		/* The floating chat has its own notices on the plate; only the quick line is drawn for it. */
		if(detached())
		    continue;
		int mh = Math.max(n.chnm.sz().y, n.rmsg.sz().y);
		if((c.y -= mh) < br.y - h)
		    break;
		g.chcolor(0, 0, 0, 192);
		g.frect2(c.add(selw - UI.scale(12) - n.chnm.sz().x, 0),
			 c.add(selw + n.rmsg.sz().x + UI.scale(2), mh));
		g.chcolor(192, 192, 192, 255);
		g.line(c.add(selw - UI.scale(5), 1), c.add(selw - UI.scale(5), mh - 1), 1);
		g.chcolor();
		g.image(n.chnm.tex(),
			c.add(selw - UI.scale(10) - n.chnm.sz().x, (mh - n.chnm.sz().y) / 2),
			br.sub(0, h), br.add(selw - UI.scale(10), 0));
		g.image(n.rmsg.tex(), c.add(selw, (mh - n.rmsg.sz().y) / 2));
	    }
	}
    }

    public void draw(GOut g) {
        if(!detached())hearthborn.ui.HudFrame.draw(g,sz);
        super.draw(g);
    }

    private static final Resource notifsfx = Resource.local().loadwait("sfx/hud/chat");
    public void notify(Channel chan, Channel.Message msg, int urgency) {
        if(urgency>=0)quiet=0;
        GameUI game=getparent(GameUI.class);
        boolean closed=closed();
        if(!closed && game!=null && chan==game.syslog) {
            if(sel!=chan)select(chan,false);
            expand();
        }
        if(closed && hearthborn.ui.ChatButton.openOnMessage()) {
            if(sel!=chan)select(chan,false);
            present();
        } else if(closed) {
            if(game == null || chan != game.syslog) {
                hearthborn.ui.ChatButton.receive(game, msg);
                hearthborn.ui.ChatButton.notice(game, chan, msg);
            }
            hearthborn.ui.ChatButton.remember(game, chan, msg);
        }
	if(urgency > 0) {
	    synchronized(notifs) {
		notifs.addFirst(new Notification(chan, msg));
	    }
	}
	if(hearthborn.ui.ChatSounds.plays(chan, urgency))
	    ui.sfx(notifsfx);
    }

    private class Spring extends NormAnim {
	final int oy = base.y - c.y, ny;
	Spring(int ny) {
	    super(0.15);
	    this.ny = ny;
	    show();
	}

	public void ntick(double a) {
	    double b = Math.cos(Math.PI * 2.5 * a) * Math.exp(-5 * a);
	    int y = base.y + ny + (int)((ny - oy) * b);
	    if(ny >= 0) {
		// The closing bounce would pop the panel back into view.
		y = Math.min(base.y, Math.max(c.y, y));
	    }
	    c = Coord.of(c.x, y);
	    if((a == 1.0 || y >= base.y) && (ny >= 0)) {
		c = Coord.of(c.x, base.y);
		hide();
	    }
	}
    }

    public void resize(Coord sz) {
	super.resize(sz);
	if(detached())this.c=Coord.z;
        else if(visible)
	    this.c = base.add(0, -this.sz.y);
        layoutTabs();

        if(sel != null) {
            sel.c=Coord.of(marg.x,marg.y+tabh);
            sel.resize(Coord.of(Math.max(1,sz.x-2*marg.x),Math.max(1,sz.y-sel.c.y-marg.y)));
        }
    }

    private void layoutTabs(){
        int inset=detachedWindow!=null&&detachedWindow.deco instanceof hearthborn.ui.SmallWindowDeco
            ?((hearthborn.ui.SmallWindowDeco)detachedWindow.deco).inlineCloseInset():0;
        chansel.move(marg);
        chansel.resize(Coord.of(Math.max(1,sz.x-2*marg.x-inset),tabh));
    }

    public void presize() {
        if(detached()){layoutTabs();return;}
        GameUI gui=getparent(GameUI.class);
        int room=parent.sz.y-UI.scale(100)-(gui==null?0:gui.dockedStripHeight());
	if(sz.y > room)
	    hresize(Math.max(UI.scale(minh), room));
    }

    public boolean targetshow = false;
    public void sshow(boolean show) {
        if(detached()){clearanims(Spring.class);if(show){show();detachedWindow.show();}else{hide();detachedWindow.hide();}targetshow=show;return;}
	clearanims(Spring.class);
	new Spring(show ? -sz.y : 0);
	targetshow = show;
    }

    public void hresize(int h) {
	clearanims(Spring.class);
	resize(sz.x, h);
    }

    public void resize(int w) {
	resize(new Coord(Math.max(w, selw + marg.x + UI.scale(10) + marg.x), sz.y));
    }

    public void move(Coord base) {
        if(detached()){super.move(base);return;}
	this.c = (this.base = base).add(0, visible ? -sz.y : 0);
    }

    /** Move the docked bottom edge without interrupting the open/close animation. */
    public void anchor(Coord base) {
        if(detached()||base==null)return;
        boolean open=c.y==this.base.y-sz.y,closed=c.y==this.base.y;
        boolean resting=(visible&&open)||(!visible&&closed);
        Coord delta=base.sub(this.base);
        this.base=base;
        if(resting)this.c=base.add(0,visible?-sz.y:0);
        else if(!delta.equals(Coord.z))this.c=this.c.add(delta);
    }

    public void expand() {
	if(!visible || !targetshow)
	    sshow(true);
    }

    public void show() {
	super.show();
	targetshow = true;
    }

    public void hide() {
	super.hide();
	targetshow = false;
    }

    private UI.Grab dm = null;
    private Coord doff;
    private static final int minh = 111;
    public boolean mousedown(MouseDownEvent ev) {
	Coord c= ev.c;
	if(!detached() && (ev.b == 1) && c.y < UI.scale(5)) {
	    dm = ui.grabmouse(this);
	    doff = c;
	    return(true);
	} else {
	    return(super.mousedown(ev));
	}
    }

    public void mousemove(MouseMoveEvent ev) {
	super.mousemove(ev);
	if(dm != null) {
	    GameUI gui=getparent(GameUI.class);
	    int room=parent.sz.y-UI.scale(100)-(gui==null?0:gui.dockedStripHeight());
	    resize(sz.x, Math.max(UI.scale(minh), Math.min(Math.max(UI.scale(minh),room), sz.y + doff.y - ev.c.y)));
	}
    }

    public boolean mouseup(MouseUpEvent ev) {
	if(dm != null) {
	    dm.remove();
	    dm = null;
	    Utils.setprefi("chatsize", UI.unscale(sz.y));
	    return(true);
	} else {
	    return(super.mouseup(ev));
	}
    }

    public boolean keydown(KeyDownEvent ev) {
	boolean M = (ev.mods & KeyMatch.M) != 0;
	if(M && (ev.code == ev.awt.VK_UP)) {
	    chansel.up();
	    return(true);
	} else if(M && (ev.code == ev.awt.VK_DOWN)) {
	    chansel.down();
	    return(true);
	}
	return(super.keydown(ev));
    }

    public static final KeyBinding kb_quick = KeyBinding.get("chat-quick", KeyMatch.forcode(java.awt.event.KeyEvent.VK_ENTER, 0));
    public boolean globtype(GlobKeyEvent ev) {
	if(kb_quick.key().match(ev)) {
	    EntryChannel target = quickTarget();
	    if(target != null) {
		if(quickOpen()) {
		    quick.raise();
		    getparent(GameUI.class).setfocus(quick);
		    return(true);
		} else if(closed()) {
		    openQuick(target);
		    return(true);
		} else if(!hasfocus) {
		    /* An open chat that does not have the keyboard takes it: the next Enter sends. */
		    if(sel != target)
			select(target, false);
		    focusChat();
		    return(true);
		}
	    }
	}
	return(super.globtype(ev));
    }

    public boolean quickOpen() {
	return((quick != null) && (quick.parent != null));
    }

    public Window quickWindow() {
	return(quick);
    }

    private void closeQuick() {
	Window w = quick;
	quick = null;
	if((w != null) && (w.parent != null))
	    w.destroy();
    }

    /** The quick line is a small window in the middle of the screen, like the item filter, with an ordinary text field. */
    private void openQuick(EntryChannel target) {
	Widget host = getparent(GameUI.class);
	if(host == null)
	    host = ui.root;
	quickChannel = target;
	int width = UI.scale(420), name = UI.scale(120);
	Window w = new Window(Coord.of(width, UI.scale(24)), "Quick chat");
	quickName = w.add(new Label(""), Coord.of(0, UI.scale(3)));
	quickEntry = w.add(new TextEntry(width - name - UI.scale(6), "") {
	    public void activate(String text) {
		if(!text.isEmpty())
		    quickChannel.send(text);
		closeQuick();
	    }

	    public boolean keydown(KeyDownEvent ev) {
		if(key_esc.match(ev)) {
		    closeQuick();
		    return(true);
		}
		boolean up = (ev.mods & KeyMatch.M) != 0 && ev.code == ev.awt.VK_UP, down = (ev.mods & KeyMatch.M) != 0 && ev.code == ev.awt.VK_DOWN;
		if(up || down) {
		    quickStep(up);
		    return(true);
		}
		return(super.keydown(ev));
	    }
	}, Coord.of(name + UI.scale(6), 0));
	w.reqclose(this::closeQuick);
	quick = w;
	showQuickChannel();
	host.add(w, host.sz.sub(hearthborn.ui.HudInterfaceScale.size(w)).div(2).max(Coord.z));
	w.raise();
	host.setfocus(w);
	w.setfocus(quickEntry);
    }

    private void showQuickChannel() {
	quickName.settext(quickChannel.name() + ">");
    }

    /** Alt+Up and Alt+Down move the quick line to the previous and next channel that takes a line. */
    private void quickStep(boolean up) {
	Channel prev = this.sel;
	while(up ? chansel.up() : chansel.down()) {
	    if(this.sel instanceof EntryChannel)
		break;
	}
	if(!(this.sel instanceof EntryChannel)) {
	    select(prev);
	    return;
	}
	quickChannel = (EntryChannel)sel;
	showQuickChannel();
    }


    /** Where a quick line writes: the chat in front, or, when that is a log, the first channel that takes a line. */
    private EntryChannel quickTarget() {
	if(sel instanceof EntryChannel)
	    return((EntryChannel)sel);
	for(EntryChannel channel : children(EntryChannel.class))
	    return(channel);
	return(null);
    }
}
