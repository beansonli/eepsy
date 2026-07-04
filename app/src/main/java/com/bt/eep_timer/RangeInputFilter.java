package com.bt.eep_timer;

import android.text.InputFilter;
import android.text.Spanned;

/**
 * Restricts an EditText to integers within [min, max]. Extracted from MainActivity
 * so it can be shared with AddEditTimerBottomSheet without changing behaviour.
 */
class RangeInputFilter implements InputFilter {
    private final int min;
    private final int max;

    RangeInputFilter(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
                               int dstart, int dend) {
        String next = dest.subSequence(0, dstart)
                + source.subSequence(start, end).toString()
                + dest.subSequence(dend, dest.length());

        if (next.isEmpty()) return null;

        try {
            int value = Integer.parseInt(next);
            if (value >= min && value <= max) return null;
        } catch (NumberFormatException ignored) {
        }

        return "";
    }
}
