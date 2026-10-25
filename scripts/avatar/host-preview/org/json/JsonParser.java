package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dependency-free JSON reader for the local avatar manifest, not a general Android JSON replacement. */
final class JsonParser {
    private final String input;
    private int index;
    JsonParser(String input) { this.input = input; }
    Object parse() {
        Object result = value(); space();
        if (index != input.length()) throw error("Trailing input");
        return result;
    }
    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at JSON character " + index);
    }
    private void space() {
        while (index < input.length() && (Character.isWhitespace(input.charAt(index)) || input.charAt(index) == '\ufeff')) index++;
    }
    private boolean take(char expected) {
        space();
        if (index < input.length() && input.charAt(index) == expected) { index++; return true; }
        return false;
    }
    private void require(char expected) { if (!take(expected)) throw error("Expected '" + expected + "'"); }
    private Object value() {
        space();
        if (index >= input.length()) throw error("Missing value");
        char first = input.charAt(index);
        if (first == '{') return object();
        if (first == '[') return array();
        if (first == '"') return string();
        for (String token : new String[] {"true", "false", "null"}) {
            if (input.startsWith(token, index)) {
                index += token.length();
                return token.equals("null") ? null : Boolean.valueOf(token);
            }
        }
        int start = index;
        if (index < input.length() && input.charAt(index) == '-') index++;
        int digits = index;
        while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
        if (index == digits) throw error("Expected number");
        if (index < input.length() && input.charAt(index) == '.') {
            index++; int fraction = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            if (index == fraction) throw error("Missing fractional digits");
        }
        if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
            index++;
            if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
            int exponent = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) index++;
            if (index == exponent) throw error("Missing exponent digits");
        }
        try { return Double.valueOf(input.substring(start, index)); }
        catch (NumberFormatException exception) { throw error("Invalid number"); }
    }
    private Map<String, Object> object() {
        require('{'); Map<String, Object> result = new LinkedHashMap<>();
        if (take('}')) return result;
        do {
            String key = string(); require(':');
            if (result.containsKey(key)) throw error("Duplicate object key " + key);
            result.put(key, value());
        } while (take(','));
        require('}'); return result;
    }
    private List<Object> array() {
        require('['); List<Object> result = new ArrayList<>();
        if (take(']')) return result;
        do { result.add(value()); } while (take(','));
        require(']'); return result;
    }
    private String string() {
        require('"'); StringBuilder result = new StringBuilder();
        while (index < input.length()) {
            char character = input.charAt(index++);
            if (character == '"') return result.toString();
            if (character < 32) throw error("Unescaped control character");
            if (character != '\\') { result.append(character); continue; }
            if (index >= input.length()) throw error("Unfinished escape");
            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"': case '\\': case '/': result.append(escaped); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (index + 4 > input.length()) throw error("Unfinished unicode escape");
                    try { result.append((char) Integer.parseInt(input.substring(index, index + 4), 16)); }
                    catch (NumberFormatException exception) { throw error("Invalid unicode escape"); }
                    index += 4; break;
                default: throw error("Invalid escape");
            }
        }
        throw error("Unfinished string");
    }
}
