package com.bt.eep_timer;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Bottom sheet used both to create a new custom timer preset and to edit an
 * existing one. Validation (range + duplicate + zero-duration) happens here
 * so the Save button is never enabled on an invalid state.
 */
public class AddEditTimerBottomSheet extends BottomSheetDialogFragment {

    interface Callback {
        /** Return an error message if invalid/duplicate, or null if OK to save. */
        @Nullable
        String validate(int hours, int minutes, String label, @Nullable String editingId);
        void onSave(@Nullable String editingId, int hours, int minutes, String label);
    }

    private static final String ARG_ID = "arg_id";
    private static final String ARG_HOURS = "arg_hours";
    private static final String ARG_MINUTES = "arg_minutes";
    private static final String ARG_LABEL = "arg_label";

    private Callback callback;

    static AddEditTimerBottomSheet newInstanceForAdd() {
        return new AddEditTimerBottomSheet();
    }

    static AddEditTimerBottomSheet newInstanceForEdit(CustomTimer timer) {
        AddEditTimerBottomSheet sheet = new AddEditTimerBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ID, timer.id);
        args.putInt(ARG_HOURS, timer.hours);
        args.putInt(ARG_MINUTES, timer.minutes);
        args.putString(ARG_LABEL, timer.label);
        sheet.setArguments(args);
        return sheet;
    }

    void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_timer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String editingId = args != null ? args.getString(ARG_ID) : null;
        boolean isEdit = editingId != null;

        TextView title = view.findViewById(R.id.sheetTitle);
        title.setText(isEdit ? R.string.edit_timer_title : R.string.add_timer_title);

        TextInputEditText hourInput = view.findViewById(R.id.sheetHourInput);
        TextInputEditText minuteInput = view.findViewById(R.id.sheetMinuteInput);
        TextInputEditText labelInput = view.findViewById(R.id.sheetLabelInput);
        TextView errorText = view.findViewById(R.id.sheetErrorText);
        MaterialButton saveButton = view.findViewById(R.id.sheetSaveButton);
        MaterialButton cancelButton = view.findViewById(R.id.sheetCancelButton);

        hourInput.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(2), new RangeInputFilter(0, 23)});
        minuteInput.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(2), new RangeInputFilter(0, 59)});

        if (isEdit) {
            hourInput.setText(String.valueOf(args.getInt(ARG_HOURS, 0)));
            minuteInput.setText(String.valueOf(args.getInt(ARG_MINUTES, 0)));
            labelInput.setText(args.getString(ARG_LABEL, ""));
        } else {
            hourInput.setText("0");
            minuteInput.setText("25");
        }

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                errorText.setVisibility(View.GONE);
                saveButton.setEnabled(parseOrZero(hourInput) != 0 || parseOrZero(minuteInput) != 0);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        hourInput.addTextChangedListener(watcher);
        minuteInput.addTextChangedListener(watcher);
        saveButton.setEnabled(parseOrZero(hourInput) != 0 || parseOrZero(minuteInput) != 0);

        cancelButton.setOnClickListener(v -> dismiss());

        saveButton.setOnClickListener(v -> {
            int hours = parseOrZero(hourInput);
            int minutes = parseOrZero(minuteInput);
            String label = labelInput.getText() == null ? "" : labelInput.getText().toString().trim();

            if (hours == 0 && minutes == 0) {
                errorText.setText(R.string.error_zero_duration);
                errorText.setVisibility(View.VISIBLE);
                return;
            }

            if (callback != null) {
                String error = callback.validate(hours, minutes, label, editingId);
                if (error != null) {
                    errorText.setText(error);
                    errorText.setVisibility(View.VISIBLE);
                    return;
                }
                callback.onSave(editingId, hours, minutes, label);
            }
            dismiss();
        });
    }

    private int parseOrZero(EditText input) {
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
