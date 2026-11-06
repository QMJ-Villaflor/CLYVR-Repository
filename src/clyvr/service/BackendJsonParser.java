package clyvr.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BackendJsonParser {

    private final String source;
    private int index;

    private BackendJsonParser(String source) {
        this.source = source;
    }

    static Object parse(String source) {
        BackendJsonParser parser = new BackendJsonParser(source);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != source.length()) {
            throw new IllegalArgumentException("Backend returned malformed JSON.");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= source.length()) {
            throw malformed();
        }
        return switch (source.charAt(index)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        index++;
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return result;
        }
        do {
            skipWhitespace();
            if (index >= source.length() || source.charAt(index) != '"') {
                throw malformed();
            }
            String key = readString();
            skipWhitespace();
            require(':');
            if (result.putIfAbsent(key, readValue()) != null) {
                throw malformed();
            }
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            require(',');
        } while (true);
    }

    private List<Object> readArray() {
        index++;
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return result;
        }
        do {
            result.add(readValue());
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            require(',');
        } while (true);
    }

    private String readString() {
        require('"');
        StringBuilder value = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') {
                return value.toString();
            }
            if (current == '\\') {
                if (index >= source.length()) {
                    throw malformed();
                }
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicode());
                    default -> throw malformed();
                }
            } else if (current < 0x20) {
                throw malformed();
            } else {
                value.append(current);
            }
        }
        throw malformed();
    }

    private char readUnicode() {
        if (index + 4 > source.length()) {
            throw malformed();
        }
        try {
            char value = (char) Integer.parseInt(source.substring(index, index + 4), 16);
            index += 4;
            return value;
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private Object readNumber() {
        int start = index;
        if (consume('-')) {
            if (index >= source.length()) {
                throw malformed();
            }
        }
        if (consume('0')) {
            if (index < source.length() && Character.isDigit(source.charAt(index))) {
                throw malformed();
            }
        } else {
            readDigits();
        }
        if (consume('.')) {
            readDigits();
        }
        if (consume('e') || consume('E')) {
            if (!consume('+')) {
                consume('-');
            }
            readDigits();
        }
        if (start == index) {
            throw malformed();
        }
        try {
            return Long.parseLong(source.substring(start, index));
        } catch (NumberFormatException exception) {
            throw malformed();
        }
    }

    private void readDigits() {
        int start = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw malformed();
        }
    }

    private Object readLiteral(String literal, Object result) {
        if (!source.startsWith(literal, index)) {
            throw malformed();
        }
        index += literal.length();
        return result;
    }

    private void skipWhitespace() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
            index++;
        }
    }

    private boolean consume(char expected) {
        if (index < source.length() && source.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!consume(expected)) {
            throw malformed();
        }
    }

    private IllegalArgumentException malformed() {
        return new IllegalArgumentException("Backend returned malformed JSON.");
    }
}
