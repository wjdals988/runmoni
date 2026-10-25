package org.json;

import java.util.Map;

public final class JSONObject {
    private final Map<?, ?> values;
    public JSONObject(String source) { this(new JsonParser(source).parse()); }
    JSONObject(Object source) {
        if (!(source instanceof Map)) throw new IllegalArgumentException("Expected JSON object");
        values = (Map<?, ?>) source;
    }
    private Object get(String key) {
        if (!values.containsKey(key) || values.get(key) == null) throw new IllegalArgumentException("Missing JSON value: " + key);
        return values.get(key);
    }
    public boolean has(String key) { return values.containsKey(key); }
    public boolean isNull(String key) { return values.get(key) == null; }
    public JSONObject getJSONObject(String key) { return new JSONObject(get(key)); }
    public JSONArray getJSONArray(String key) { return new JSONArray(get(key)); }
    public JSONArray optJSONArray(String key) {
        Object result = values.get(key);
        return result instanceof java.util.List ? new JSONArray(result) : null;
    }
    public String getString(String key) {
        Object result = get(key);
        if (!(result instanceof String)) throw new IllegalArgumentException("Expected JSON string: " + key);
        return (String) result;
    }
    public String optString(String key, String fallback) {
        Object result = values.get(key);
        return result == null ? fallback : result.toString();
    }
    public int optInt(String key, int fallback) {
        Object result = values.get(key); return result instanceof Number ? ((Number) result).intValue() : fallback;
    }
    public double optDouble(String key, double fallback) {
        Object result = values.get(key); return result instanceof Number ? ((Number) result).doubleValue() : fallback;
    }
}
