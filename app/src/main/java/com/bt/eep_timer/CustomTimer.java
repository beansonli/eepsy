package com.bt.eep_timer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A user-defined "favourite duration" preset. Intentionally a tiny POJO -
 * this app persists these via SharedPreferences (JSON), not Room, since the
 * dataset is small (max 20 items) and doesn't warrant database overhead.
 */
class CustomTimer {
    final String id;
    String label;
    int hours;
    int minutes;
    final long createdAt;
    boolean isFavourite;

    CustomTimer(String id, String label, int hours, int minutes, long createdAt, boolean isFavourite) {
        this.id = id;
        this.label = label == null ? "" : label;
        this.hours = hours;
        this.minutes = minutes;
        this.createdAt = createdAt;
        this.isFavourite = isFavourite;
    }

    int totalMinutes() {
        return hours * 60 + minutes;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label == null ? "" : label);
        o.put("hours", hours);
        o.put("minutes", minutes);
        o.put("createdAt", createdAt);
        o.put("isFavourite", isFavourite);
        return o;
    }

    static CustomTimer fromJson(JSONObject o) throws JSONException {
        return new CustomTimer(
                o.getString("id"),
                o.optString("label", ""),
                o.getInt("hours"),
                o.getInt("minutes"),
                o.optLong("createdAt", System.currentTimeMillis()),
                o.optBoolean("isFavourite", false)
        );
    }
}
