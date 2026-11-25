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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DataValidator {
    private DataValidator() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String name) throws IOException {
        if(!(value instanceof Map)) throw invalid(name, "expected an object");
        return (Map<String, Object>)value;
    }

    static List<?> array(Object value, String name) throws IOException {
        if(!(value instanceof List)) throw invalid(name, "expected an array");
        return (List<?>)value;
    }

    static String string(Object value, String name) throws IOException {
        if(!(value instanceof String) || ((String)value).trim().isEmpty())
            throw invalid(name, "expected non-blank text");
        return (String)value;
    }

    static String matching(Object value, String name, String pattern) throws IOException {
        String text = string(value, name);
        if(!text.matches(pattern)) throw invalid(name, "invalid value: " + text);
        return text;
    }

    static List<String> strings(Object value, String name) throws IOException {
        List<String> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for(Object element : array(value, name)) {
            String text = string(element, name);
            if(!seen.add(text)) throw invalid(name, "duplicate value: " + text);
            values.add(text);
        }
        return values;
    }

    static int positiveInteger(Object value, String name) throws IOException {
        if(value instanceof BigDecimal) {
            try {
                int integer = ((BigDecimal)value).intValueExact();
                if(integer > 0) return integer;
            } catch(ArithmeticException ignored) {}
        }
        throw invalid(name, "expected a positive integer");
    }

    static void keys(Map<String, Object> object, String required, String optional) throws IOException {
        Set<String> available = new HashSet<>(object.keySet());
        for(String key : required.split(" ")) {
            if(!available.remove(key)) throw invalid(key, "missing required field");
        }
        for(String key : optional.split(" ")) available.remove(key);
        if(!available.isEmpty()) throw invalid(available.toString(), "unknown fields");
    }

    static IOException invalid(String field, String message) {
        return new IOException(field + ": " + message);
    }
}
