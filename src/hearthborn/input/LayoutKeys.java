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

package hearthborn.input;

import java.awt.event.KeyEvent;

/**
 * Russian ЙЦУКЕН characters on the same physical keys as a Latin keyboard.
 *
 * @author Jagy2014
 */
public final class LayoutKeys {
    public static int keyCode(char c) {
        switch(c) {
        case 'й': case 'Й': return KeyEvent.VK_Q;
        case 'ц': case 'Ц': return KeyEvent.VK_W;
        case 'у': case 'У': return KeyEvent.VK_E;
        case 'к': case 'К': return KeyEvent.VK_R;
        case 'е': case 'Е': return KeyEvent.VK_T;
        case 'н': case 'Н': return KeyEvent.VK_Y;
        case 'г': case 'Г': return KeyEvent.VK_U;
        case 'ш': case 'Ш': return KeyEvent.VK_I;
        case 'щ': case 'Щ': return KeyEvent.VK_O;
        case 'з': case 'З': return KeyEvent.VK_P;
        case 'х': case 'Х': return KeyEvent.VK_OPEN_BRACKET;
        case 'ъ': case 'Ъ': return KeyEvent.VK_CLOSE_BRACKET;
        case 'ф': case 'Ф': return KeyEvent.VK_A;
        case 'ы': case 'Ы': return KeyEvent.VK_S;
        case 'в': case 'В': return KeyEvent.VK_D;
        case 'а': case 'А': return KeyEvent.VK_F;
        case 'п': case 'П': return KeyEvent.VK_G;
        case 'р': case 'Р': return KeyEvent.VK_H;
        case 'о': case 'О': return KeyEvent.VK_J;
        case 'л': case 'Л': return KeyEvent.VK_K;
        case 'д': case 'Д': return KeyEvent.VK_L;
        case 'ж': case 'Ж': return KeyEvent.VK_SEMICOLON;
        case 'э': case 'Э': return KeyEvent.VK_QUOTE;
        case 'я': case 'Я': return KeyEvent.VK_Z;
        case 'ч': case 'Ч': return KeyEvent.VK_X;
        case 'с': case 'С': return KeyEvent.VK_C;
        case 'м': case 'М': return KeyEvent.VK_V;
        case 'и': case 'И': return KeyEvent.VK_B;
        case 'т': case 'Т': return KeyEvent.VK_N;
        case 'ь': case 'Ь': return KeyEvent.VK_M;
        case 'б': case 'Б': return KeyEvent.VK_COMMA;
        case 'ю': case 'Ю': return KeyEvent.VK_PERIOD;
        case 'ё': case 'Ё': return KeyEvent.VK_BACK_QUOTE;
        default: return KeyEvent.VK_UNDEFINED;
        }
    }

    public static char latin(char c) {
        int code = keyCode(c);
        if((code >= KeyEvent.VK_A) && (code <= KeyEvent.VK_Z))
            return((char)('A' + (code - KeyEvent.VK_A)));
        return(0);
    }

    private LayoutKeys() {}
}
