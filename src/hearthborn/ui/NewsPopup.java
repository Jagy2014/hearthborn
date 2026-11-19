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
import java.awt.Color;
import java.util.*;

/**
 * The news of the game on the character screen. The server only asks for it (a message naming the
 * resource {@code ui/news}); the window is ours, on the whole screen rather than inside the server's
 * layout box, and an entry stays in the list after it has been opened, until the window is closed.
 *
 * @author Jagy2014
 */
public final class NewsPopup extends Window {
    public static final String RESOURCE = "ui/news";
    private final NewsFeed.Filtered feed;
    private final List<NewsFeed.Entry> shown;
    private boolean closed;

    private static final int WIDTH = UI.scale(340), SHOWN = 3;
    private static final Text.Foundry TITLE = new Text.Foundry(Text.sans, 14).aa(true);
    private static final Color TITLE_COLOR = new Color(255, 232, 168), DATE_COLOR = new Color(150, 162, 180), ACCENT = new Color(222, 170, 70);
    /** Room for a title of two lines, the date under it and some air around. */
    private static final int ITEM_H = UI.scale(10) + 2 * TITLE.height() + UI.scale(5) + Text.std.height() + UI.scale(10);

    /** One entry: a card with the title, the date a little below it, and a click that opens the topic. */
    static final class Item extends Widget implements CursorQuery.Handler {
	private final NewsFeed.Filtered feed;
	private final NewsFeed.Entry entry;

	Item(NewsFeed.Filtered feed, NewsFeed.Entry entry, Coord sz) {
	    super(sz);
	    this.feed = feed;
	    this.entry = entry;
	    int pad = UI.scale(12), width = sz.x - pad - UI.scale(8);
	    Label title = new Label(fit(entry.name(), width), width, TITLE);
	    title.setcolor(TITLE_COLOR);
	    add(title, Coord.of(pad, UI.scale(8)));
	    Label date = new Label(NewsFeed.timefmt.format(java.time.ZonedDateTime.ofInstant(entry.time(), java.time.ZoneId.systemDefault())));
	    date.setcolor(DATE_COLOR);
	    add(date, Coord.of(pad, title.c.y + title.sz.y + UI.scale(5)));
	}

	/** A title that runs past two lines is cut with dots. */
	static String fit(String text, int width) {
	    String shown = text.trim();
	    int limit = 2 * TITLE.height() + UI.scale(2);
	    for(int n = shown.length(); n > 8; n--) {
		String s = (n == shown.length()) ? shown : shown.substring(0, n).trim() + "...";
		if(TITLE.renderwrap(s, Color.WHITE, width).sz().y <= limit)
		    return(s);
	    }
	    if(TITLE.renderwrap(shown, Color.WHITE, width).sz().y <= limit)
		return(shown);
	    return(shown.substring(0, 8).trim() + "...");
	}

	public void draw(GOut g) {
	    boolean over = (ui != null) && (ui.mc != null) && ui.mc.isect(rootpos(), sz);
	    g.chcolor(255, 255, 255, over ? 34 : 14);
	    g.frect(Coord.z, sz);
	    g.chcolor(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), over ? 255 : 190);
	    g.frect(Coord.z, Coord.of(UI.scale(3), sz.y));
	    g.chcolor();
	    super.draw(g);
	}

	public boolean getcurs(CursorQuery ev) {
	    return(ev.set(NewsFeed.LinkText.hand));
	}

	public boolean mousedown(MouseDownEvent ev) {
	    if(ev.b != 1)
		return(super.mousedown(ev));
	    try {
		ui.wnd.toolkit().browse(entry.href());
	    } catch(Exception e) {
		ui.error("Could not launch web browser: " + e.getMessage());
		return(true);
	    }
	    feed.see(Arrays.asList(entry));
	    feed.save();
	    return(true);
	}
    }

    private NewsPopup(NewsFeed.Filtered feed) {
	super(Coord.z, "News", true);
	this.feed = feed;
	this.shown = new ArrayList<>(feed.unseen);
	int gap = UI.scale(8);
	NewsFeed list = new NewsFeed(Coord.of(WIDTH + Scrollbar.width, SHOWN * ITEM_H + (SHOWN - 1) * gap), ITEM_H, gap, feed) {
		/* The feed's own list drops an entry as soon as it is opened. */
		public List<NewsFeed.Entry> items() {return(shown);}
		public Widget makeitem(NewsFeed.Entry entry, int idx, Coord sz) {return(new Item(NewsPopup.this.feed, entry, Coord.of(WIDTH, ITEM_H)));}
	    };
	list.minimize();
	add(list, Coord.of(0, UI.scale(4)));
	Button dismiss = new Button(UI.scale(110), "Dismiss", false).action(this::finish);
	add(dismiss, Coord.of(list.sz.x - dismiss.sz.x, list.c.y + list.sz.y + UI.scale(12)));
	pack();
	reqclose(this::finish);
    }

    /** The entries shown here count as read once the window is gone, however it was closed. */
    private void finish() {
	if(closed)
	    return;
	closed = true;
	feed.see(new ArrayList<>(shown));
	feed.save();
	reqdestroy();
    }

    public List<NewsFeed.Entry> entries() {return(Collections.unmodifiableList(shown));}

    /** Fetches the feed in the background and shows the window if there is anything the player has not seen. */
    public static void open(UI ui) {
	if(ui == null || ui.root == null || NewsFeed.feed.get() == null)
	    return;
	if(ui.root.getchild(NewsPopup.class) != null || ui.root.getchild(Fetch.class) != null)
	    return;
	ui.root.add(new Fetch());
    }

    /** The top right corner under the server's status, clear of the characters and the account badges. */
    static Coord place(Coord screen, Coord size) {
	return(Coord.of(Math.max(0, screen.x - size.x - UI.scale(24)), Math.min(UI.scale(64), Math.max(0, screen.y - size.y))));
    }

    /** Waits for the feed without touching the interface from another thread. */
    private static final class Fetch extends Widget {
	private volatile NewsFeed.Filtered result;
	private volatile Throwable failure;
	private volatile boolean done;

	Fetch() {
	    super(Coord.z);
	    Thread thread = new Thread(() -> {
		    try {
			result = new NewsFeed.Filtered(NewsFeed.fetch());
		    } catch(Throwable t) {
			failure = t;
		    } finally {
			done = true;
		    }
		}, "News feed");
	    thread.setDaemon(true);
	    thread.start();
	}

	public void tick(double dt) {
	    super.tick(dt);
	    if(!done)
		return;
	    NewsFeed.Filtered feed = result;
	    Throwable error = failure;
	    UI ui = this.ui;
	    destroy();
	    if(error != null) {
		new Warning(error, "could not load the news feed").issue();
	    } else if(feed != null && !feed.unseen.isEmpty()) {
		NewsPopup popup = new NewsPopup(feed);
		ui.root.add(popup, place(ui.root.sz, popup.sz));
		popup.raise();
	    }
	}
    }
}
