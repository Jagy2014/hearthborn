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
 * Which chat channels may play the message sound, and how long a notice line stays on screen.
 *
 * @author Jagy2014
 */
public final class ChatSounds {
    public enum Kind {
        AREA("Area chat",false), PRIVATE("Private messages",true), PARTY("Party chat",true), VILLAGE("Village and other chats",true);
        public final String label;
        final boolean fallback;
        Kind(String label,boolean fallback){this.label=label;this.fallback=fallback;}
        public boolean enabled(){return Utils.getprefb("hearthborn/chat/sound/"+name().toLowerCase(java.util.Locale.ROOT),fallback);}
        public void enabled(boolean value){Utils.setprefb("hearthborn/chat/sound/"+name().toLowerCase(java.util.Locale.ROOT),value);}
    }
    public static final int MIN_SECONDS=3,MAX_SECONDS=10,DEFAULT_SECONDS=5;

    public static int seconds(){return Utils.clip(Utils.getprefi("hearthborn/chat/notice-seconds",DEFAULT_SECONDS),MIN_SECONDS,MAX_SECONDS);}
    public static void seconds(int value){Utils.setprefi("hearthborn/chat/notice-seconds",Utils.clip(value,MIN_SECONDS,MAX_SECONDS));}

    /** The settings kind of a chat channel, or null for system and log channels that always keep the default behaviour. */
    public static Kind kind(ChatUI.Channel channel) {
        if(channel instanceof ChatUI.PrivChat)return Kind.PRIVATE;
        if(channel instanceof ChatUI.PartyChat)return Kind.PARTY;
        if(channel instanceof ChatUI.MultiChat)return ((ChatUI.MultiChat)channel).isArea()?Kind.AREA:Kind.VILLAGE;
        return null;
    }
    /** Whether a new message plays the chat sound: the switch of its channel decides, whatever urgency the server gave. Your own lines (urgency below zero) never do. */
    public static boolean plays(ChatUI.Channel channel,int urgency) {
        if(urgency<0)return false;
        Kind kind=kind(channel);
        if(kind==null)return urgency>0;
        return kind.enabled();
    }
    private ChatSounds(){}
}
