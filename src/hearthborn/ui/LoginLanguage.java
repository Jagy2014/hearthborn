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
import hearthborn.L10n;

/**
 * Language preference is applied on the next client start.
 *
 * @author Jagy2014
 */
public final class LoginLanguage extends Button {
    private String selected;

    public LoginLanguage(java.util.function.Consumer<String> changeLanguage) {
        super(UI.scale(100), "English");
        selected = Utils.getpref("hearthborn/language", L10n.language());
        updateCaption();
        action(() -> {
            selected = selected.equals("en") ? "ru" : "en";
            changeLanguage.accept(selected);
        });

    }

    private void updateCaption() {
        change(selected.equals("ru") ? "\u0420\u0443\u0441\u0441\u043a\u0438\u0439" : "English");
    }
}
