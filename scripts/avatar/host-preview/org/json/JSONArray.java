package org.json;

import java.util.List;

public final class JSONArray {
    private final List<?> values;
    JSONArray(Object source) {
        if (!(source instanceof List)) throw new IllegalArgumentException("Expected JSON array");
        values = (List<?>) source;
    }
    public int length() { return values.size(); }
    public boolean isNull(int index) { return index < 0 || index >= values.size() || values.get(index) == null; }
    private Object get(int index) {
        Object result = values.get(index);
        if (result == null) throw new IllegalArgumentException("Null array value at " + index);
        return result;
    }
    public int getInt(int index) { return ((Number) get(index)).intValue(); }
    public double getDouble(int index) { return ((Number) get(index)).doubleValue(); }
    public String getString(int index) {
        Object result = get(index);
        if (!(result instanceof String)) throw new IllegalArgumentException("Expected array string at " + index);
        return (String) result;
    }
    public JSONObject getJSONObject(int index) { return new JSONObject(get(index)); }
}
