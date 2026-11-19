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
 * Message sounds per chat channel and the time a notice line stays visible; they sit in the sound section.
 *
 * @author Jagy2014
 */
public final class ChatSettings {
    /** Adds the chat sound switches and the notice time under {@code above}, inside the sound section. Returns the lowest widget. */
    public static Widget add(OptWnd.Panel panel, Widget above, int gap) {
        Widget previous = panel.add(new Label("Chat sounds and notices"), above.pos("bl").adds(0, gap));
        previous = panel.add(new Label("Play a sound for new messages in"), previous.pos("bl").adds(0, 5));
        for(ChatSounds.Kind kind : ChatSounds.Kind.values()) {
            previous = panel.add(new CheckBox(kind.label).state(kind::enabled).set(kind::enabled), previous.pos("bl").adds(0, 6));
        }
        previous.settip("Chat channels sound for messages from other people, never for your own lines. Area chat is silent in the original client, so it is off here until you turn it on.");
        Label time = panel.add(new Label(""), previous.pos("bl").adds(0, 18));
        Runnable show = () -> time.settext(hearthborn.L10n.format("Notice line stays for %d s", ChatSounds.seconds()));
        show.run();
        return panel.add(new HSlider(UI.scale(240), ChatSounds.MIN_SECONDS, ChatSounds.MAX_SECONDS, ChatSounds.seconds()) {
            public void changed() { ChatSounds.seconds(val); show.run(); }
        }, time.pos("bl").adds(0, 4));
    }
    private ChatSettings() {}
}
