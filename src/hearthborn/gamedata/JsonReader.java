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

package hearthborn.gamedata;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, bounded JSON reader for local game data. */
final class JsonReader {
    private static final int MAX_CHARS = 1024 * 1024;
    private static final int MAX_DEPTH = 48;
    private final Reader input;
    private int current;
    private int offset;

    private JsonReader(Reader input) throws IOException {
        this.input = input;
        advance();
    }

    static Object read(Reader input) throws IOException {
        JsonReader reader = new JsonReader(input);
        Object value = reader.value(0);
        reader.whitespace();
        if(reader.current != -1) throw reader.error("unexpected trailing input");
        return value;
    }

    private Object value(int depth) throws IOException {
        if(depth > MAX_DEPTH) throw error("nesting limit exceeded");
        whitespace();
        switch(current) {
        case '{': return object(depth);
        case '[': return array(depth);
        case '"': return string();
        case 't': literal("true"); return Boolean.TRUE;
        case 'f': literal("false"); return Boolean.FALSE;
        case 'n': literal("null"); return null;
        default:
            if(current == '-' || digit(current)) return number();
            throw error("expected a JSON value");
        }
    }

    private Map<String, Object> object(int depth) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        advance();
        whitespace();
        if(take('}')) return result;
        do {
            whitespace();
            String key = string();
            if(result.containsKey(key)) throw error("duplicate key: " + key);
            whitespace();
            expect(':');
            result.put(key, value(depth + 1));
            whitespace();
        } while(take(','));
        expect('}');
        return result;
    }

    private List<Object> array(int depth) throws IOException {
        List<Object> result = new ArrayList<>();
        advance();
        whitespace();
        if(take(']')) return result;
        do {
            result.add(value(depth + 1));
            whitespace();
        } while(take(','));
        expect(']');
        return result;
    }

    private String string() throws IOException {
        expect('"');
        StringBuilder result = new StringBuilder();
        while(current != '"') {
            if(current < 0x20) throw error("invalid character in string");
            if(take('\\')) {
                result.append(escape());
            } else {
                result.append((char)current);
                advance();
            }
        }
        advance();
        for(int i = 0; i < result.length(); i++) {
            char ch = result.charAt(i);
            if(Character.isHighSurrogate(ch)) {
                if(++i == result.length() || !Character.isLowSurrogate(result.charAt(i)))
                    throw error("unpaired Unicode surrogate");
            } else if(Character.isLowSurrogate(ch)) {
                throw error("unpaired Unicode surrogate");
            }
        }
        return result.toString();
    }

    private char escape() throws IOException {
        int kind = current;
        advance();
        switch(kind) {
        case '"': case '\\': case '/': return (char)kind;
        case 'b': return '\b';
        case 'f': return '\f';
        case 'n': return '\n';
        case 'r': return '\r';
        case 't': return '\t';
        case 'u':
            int code = 0;
            for(int i = 0; i < 4; i++) {
                int nibble = current < 128 ? Character.digit(current, 16) : -1;
                if(nibble < 0) throw error("invalid Unicode escape");
                code = code * 16 + nibble;
                advance();
            }
            return (char)code;
        default: throw error("invalid string escape");
        }
    }

    private BigDecimal number() throws IOException {
        StringBuilder result = new StringBuilder();
        appendIf(result, '-');
        if(!appendIf(result, '0')) digits(result);
        if(appendIf(result, '.')) digits(result);
        if(current == 'e' || current == 'E') {
            result.append('e');
            advance();
            if(!appendIf(result, '+')) appendIf(result, '-');
            digits(result);
        }
        try {
            return new BigDecimal(result.toString());
        } catch(NumberFormatException e) {
            throw error("invalid number");
        }
    }

    private void digits(StringBuilder result) throws IOException {
        if(!digit(current)) throw error("expected a digit");
        while(digit(current)) {
            result.append((char)current);
            advance();
        }
    }

    private static boolean digit(int value) {
        return value >= '0' && value <= '9';
    }

    private void literal(String word) throws IOException {
        for(int i = 0; i < word.length(); i++) expect(word.charAt(i));
    }

    private void whitespace() throws IOException {
        while(current == ' ' || current == '\t' || current == '\n' || current == '\r') advance();
    }

    private void advance() throws IOException {
        current = input.read();
        if(current != -1 && ++offset > MAX_CHARS) throw error("document is too large");
    }

    private boolean take(char ch) throws IOException {
        if(current != ch) return false;
        advance();
        return true;
    }

    private boolean appendIf(StringBuilder result, char ch) throws IOException {
        if(!take(ch)) return false;
        result.append(ch);
        return true;
    }

    private void expect(char ch) throws IOException {
        if(!take(ch)) throw error("expected '" + ch + "'");
    }

    private IOException error(String message) {
        return new IOException(message + " at character " + offset);
    }
}
