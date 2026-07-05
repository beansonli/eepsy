package com.bt.eep_timer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists custom timer presets in SharedPreferences as a small JSON array.
 * No database, no background work, no polling - reads/writes only happen in
 * direct response to a user action (add/edit/delete/duplicate).
 */
class CustomTimerStore {
    static final int MAX_PRESETS = 20;

    private static final String PREFS_NAME = "eepsy_custom_timers";
    private static final String KEY_TIMERS = "timers_json";
    private static final String KEY_SEEDED = "default_timers_seeded";

    private final SharedPreferences prefs;

    CustomTimerStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    List<CustomTimer> load() {
        List<CustomTimer> result = new ArrayList<>();
        String json = prefs.getString(KEY_TIMERS, null);

        if (json == null) {
            if (!prefs.getBoolean(KEY_SEEDED, false)) {
                result.addAll(defaultTimers());
                save(result);
                prefs.edit().putBoolean(KEY_SEEDED, true).apply();
            }
            return result;
        }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(CustomTimer.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            // Corrupt or unreadable data: fail safe with an empty list rather than crash.
        }

        return result;
    }

    void save(List<CustomTimer> timers) {
        JSONArray arr = new JSONArray();
        try {
            for (CustomTimer timer : timers) {
                arr.put(timer.toJson());
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(KEY_TIMERS, arr.toString()).apply();
    }

    private List<CustomTimer> defaultTimers() {
        List<CustomTimer> defaults = new ArrayList<>();
        long now = System.currentTimeMillis();
        defaults.add(new CustomTimer(newId(), "Power Nap", 0, 25, now, false));
        defaults.add(new CustomTimer(newId(), "Book Reading", 0, 45, now + 1, false));
        defaults.add(new CustomTimer(newId(), "Podcast", 1, 0, now + 2, false));
        return defaults;
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }
}
