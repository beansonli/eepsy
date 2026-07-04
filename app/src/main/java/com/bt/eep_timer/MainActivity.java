package com.bt.eep_timer;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.ItemTouchHelper;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private static final int BT_PERMISSION_REQUEST = 100;
    private static final int PROGRESS_MAX = 1000;

    private MaterialSwitch timerToggle;
    private EditText hourPicker;
    private EditText minutePicker;
    private Button startButton;
    private Button cancelButton;
    private TextView countdownLabel;
    private TextView statusLabel;
    private TextView durationSummaryLabel;
    private TextInputLayout hourInputLayout;
    private TextInputLayout minuteInputLayout;
    private CircularProgressIndicator timerProgress;
    private TimerDialOverlayView timerDialOverlay;

    private RecyclerView customTimersRecycler;
    private CustomTimerAdapter customTimerAdapter;
    private CustomTimerStore customTimerStore;
    private List<CustomTimer> customTimers = new ArrayList<>();
    private MaterialButton addTimerButton;
    private TextView customTimersEmptyLabel;
    private TextView maxPresetsLabel;
    private boolean isApplyingPresetSelection = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int uiRemaining = 0;
    private int uiDuration = 0;
    private boolean uiRunning = false;

    private final Runnable uiTick = new Runnable() {
        @Override
        public void run() {
            if (!uiRunning) return;

            uiRemaining--;
            if (uiRemaining <= 0) {
                countdownLabel.setText("00:00:00");
                durationSummaryLabel.setText("0 min");
                timerProgress.setProgressCompat(0, true);
                timerDialOverlay.setProgressFraction(0f);
                statusLabel.setText("Status: Completed");
                setUiStopped();
            } else {
                countdownLabel.setText(formatTime(uiRemaining));
                durationSummaryLabel.setText(formatDurationSummary(uiRemaining));
                updateProgress();
                uiHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timerToggle = findViewById(R.id.timerToggle);
        hourPicker = findViewById(R.id.hourPicker);
        minutePicker = findViewById(R.id.minutePicker);
        startButton = findViewById(R.id.startButton);
        cancelButton = findViewById(R.id.cancelButton);
        countdownLabel = findViewById(R.id.countdownLabel);
        statusLabel = findViewById(R.id.statusLabel);
        durationSummaryLabel = findViewById(R.id.durationSummaryLabel);
        hourInputLayout = findViewById(R.id.hourInputLayout);
        minuteInputLayout = findViewById(R.id.minuteInputLayout);
        timerProgress = findViewById(R.id.timerProgress);
        timerDialOverlay = findViewById(R.id.timerDialOverlay);
        customTimersRecycler = findViewById(R.id.customTimersRecycler);
        addTimerButton = findViewById(R.id.addTimerButton);
        customTimersEmptyLabel = findViewById(R.id.customTimersEmptyLabel);
        maxPresetsLabel = findViewById(R.id.maxPresetsLabel);

        customTimerStore = new CustomTimerStore(this);

        setupInputs();
        setupToggle();
        setupButtons();
        setupCustomTimers();
        requestBluetoothPermission();
        updatePreviewFromInputs();
        setInputsEnabled(false);
        startButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(uiTick);
    }

    private void setupInputs() {
        hourPicker.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2), new RangeInputFilter(0, 23)});
        minutePicker.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2), new RangeInputFilter(0, 59)});
        hourPicker.setSelectAllOnFocus(true);
        minutePicker.setSelectAllOnFocus(true);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!uiRunning) {
                    if (!isApplyingPresetSelection) {
                        clearPresetSelection();
                    }
                    updatePreviewFromInputs();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        hourPicker.addTextChangedListener(watcher);
        minutePicker.addTextChangedListener(watcher);
    }

    private void setupToggle() {
        timerToggle.setOnCheckedChangeListener((btn, isChecked) -> {
            setInputsEnabled(isChecked);
            startButton.setEnabled(isChecked && !uiRunning);

            customTimerAdapter.setInteractionEnabled(isChecked);
            customTimerAdapter.notifyDataSetChanged();

            if (!isChecked) {
                customTimerAdapter.setSelectedId(null);
                customTimerAdapter.setActiveTimerId(null);
                customTimerAdapter.notifyDataSetChanged();

                if (uiRunning) cancelTimer();
            }
        });
    }

    private void setInputsEnabled(boolean enabled) {
        hourPicker.setEnabled(enabled);
        minutePicker.setEnabled(enabled);
        hourInputLayout.setEnabled(enabled);
        minuteInputLayout.setEnabled(enabled);
        hourInputLayout.setAlpha(enabled ? 1f : 0.5f);
        minuteInputLayout.setAlpha(enabled ? 1f : 0.5f);
    }

    private void setupButtons() {
        startButton.setOnClickListener(v -> {
            int total = readDurationSeconds(true);
            if (total <= 0) {
                statusLabel.setText("Status: Please enter a valid duration");
                return;
            }

            startTimer(total);
        });

        cancelButton.setOnClickListener(v -> cancelTimer());
    }

    private void startTimer(int durationSeconds) {
        Intent intent = new Intent(this, SleepTimerService.class);
        intent.putExtra("duration_seconds", durationSeconds);
        startForegroundService(intent);

        uiRemaining = durationSeconds;
        uiDuration = durationSeconds;
        uiRunning = true;
        timerProgress.setMax(PROGRESS_MAX);
        timerProgress.setProgressCompat(PROGRESS_MAX, false);
        timerDialOverlay.setProgressFraction(1f);
        uiHandler.postDelayed(uiTick, 1000);

        countdownLabel.setText(formatTime(uiRemaining));
        durationSummaryLabel.setText(formatDurationSummary(uiRemaining));
        statusLabel.setText("Status: Relaxing...");
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        timerToggle.setEnabled(false);
        setInputsEnabled(false);
        customTimerAdapter.setInteractionEnabled(false);
        updateAddButtonState();
    }

    private void startTimer(CustomTimer timer) {
        int durationSeconds = (timer.hours * 3600) + (timer.minutes * 60);
        customTimerAdapter.setActiveTimerId(timer.id);
        startTimer(durationSeconds);
    }

    private void cancelTimer() {
        Intent intent = new Intent(this, SleepTimerService.class);
        intent.setAction("STOP");
        startService(intent);

        customTimerAdapter.setActiveTimerId(null);

        uiRunning = false;
        uiHandler.removeCallbacks(uiTick);
        setUiStopped();
        statusLabel.setText("Status: Ready");
        customTimerAdapter.setInteractionEnabled(true);
        updatePreviewFromInputs();
    }

    private void setUiStopped() {
        uiRunning = false;
        startButton.setEnabled(timerToggle.isChecked());
        cancelButton.setEnabled(false);
        timerToggle.setEnabled(true);
        setInputsEnabled(timerToggle.isChecked());
        customTimerAdapter.setSelectedId(null);
        customTimerAdapter.setInteractionEnabled(true);
        updateAddButtonState();
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        BT_PERMISSION_REQUEST);
            } else {
                statusLabel.setText("Status: Ready");
            }
        } else {
            statusLabel.setText("Status: Ready");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BT_PERMISSION_REQUEST) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusLabel.setText("Status: Ready");
            } else {
                statusLabel.setText("Status: Bluetooth permission denied");
                startButton.setEnabled(false);
            }
        }
    }

    private int readDurationSeconds(boolean showErrors) {
        hourInputLayout.setError(null);
        minuteInputLayout.setError(null);

        Integer hours = parseInput(hourPicker);
        Integer minutes = parseInput(minutePicker);
        boolean valid = true;

        if (hours == null || hours < 0 || hours > 23) {
            valid = false;
            if (showErrors) hourInputLayout.setError("0-23");
        }

        if (minutes == null || minutes < 0 || minutes > 59) {
            valid = false;
            if (showErrors) minuteInputLayout.setError("0-59");
        }

        if (!valid) return 0;
        return hours * 3600 + minutes * 60;
    }

    private Integer parseInput(EditText input) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return null;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updatePreviewFromInputs() {
        int total = readDurationSeconds(false);
        countdownLabel.setText(formatTime(total));
        durationSummaryLabel.setText(formatDurationSummary(total));
        timerProgress.setProgressCompat(total > 0 ? PROGRESS_MAX : 0, false);
        timerDialOverlay.setProgressFraction(total > 0 ? 1f : 0f);
    }

    private void updateProgress() {
        if (uiDuration <= 0) {
            timerProgress.setProgressCompat(0, true);
            timerDialOverlay.setProgressFraction(0f);
            return;
        }

        int progress = Math.max(0, Math.round((uiRemaining * PROGRESS_MAX) / (float) uiDuration));
        timerProgress.setProgressCompat(progress, true);
        timerDialOverlay.setProgressFraction(progress / (float) PROGRESS_MAX);
    }

    private String formatTime(int total) {
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private String formatDurationSummary(int total) {
        int h = total / 3600;
        int m = (total % 3600) / 60;

        if (h > 0 && m > 0) return String.format("%d hr %d min", h, m);
        if (h > 0) return String.format("%d hr", h);
        return String.format("%d min", m);
    }

    private void setupCustomTimers() {
        customTimerAdapter = new CustomTimerAdapter(new CustomTimerAdapter.Listener() {
            @Override
            public void onTimerSelected(CustomTimer timer) {

                customTimerAdapter.setSelectedId(timer.id);
                isApplyingPresetSelection = true;

                hourPicker.setText(String.valueOf(timer.hours));
                minutePicker.setText(String.valueOf(timer.minutes));

                isApplyingPresetSelection = false;

                timerToggle.setChecked(true);
                setInputsEnabled(true);
            }

            @Override
            public void onTimerLongPressed(CustomTimer timer, View anchor) {
                showTimerOptionsMenu(timer, anchor);
            }
        });

        customTimersRecycler.setLayoutManager(new LinearLayoutManager(this));
        customTimersRecycler.setAdapter(customTimerAdapter);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, // Drag directions
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT // Swipe directions
        ) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                customTimerAdapter.moveItem(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    viewHolder.itemView.animate()
                            .scaleX(1.03f).scaleY(1.03f).translationZ(8f).alpha(0.8f)
                            .setDuration(150).start();
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.animate()
                        .scaleX(1f).scaleY(1f).translationZ(0f).alpha(1f)
                        .setDuration(150).start();

                customTimers = customTimerAdapter.getItems();
                customTimerStore.save(customTimers);
                refreshCustomTimersList();
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                CustomTimer swipedTimer = customTimerAdapter.getItems().get(position);

                if (direction == ItemTouchHelper.RIGHT) {
                    deleteTimer(swipedTimer);
                } else if (direction == ItemTouchHelper.LEFT) {
                    toggleFavourite(swipedTimer);

                    customTimerAdapter.notifyItemChanged(position);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;
                    Paint paint = new Paint();
                    paint.setAntiAlias(true);

                    float cornerRadius = 16 * getResources().getDisplayMetrics().density;

                    if (dX > 0) {

                        paint.setColor(android.graphics.Color.parseColor("#EF5350"));
                        RectF bgRect = new RectF(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + dX, itemView.getBottom());
                        c.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint);


                    } else if (dX < 0) {

                        paint.setColor(android.graphics.Color.parseColor("#ff9eb5"));
                        RectF bgRect = new RectF(itemView.getRight() + dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
                        c.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint);

                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });

        helper.attachToRecyclerView(customTimersRecycler);


        customTimers = customTimerStore.load();
        refreshCustomTimersList();

        addTimerButton.setOnClickListener(v -> {
            if (customTimers.size() >= CustomTimerStore.MAX_PRESETS) return;
            AddEditTimerBottomSheet sheet = AddEditTimerBottomSheet.newInstanceForAdd();
            sheet.setCallback(timerSheetCallback);
            sheet.show(getSupportFragmentManager(), "add_timer");
        });
    }


    private final AddEditTimerBottomSheet.Callback timerSheetCallback =
            new AddEditTimerBottomSheet.Callback() {
        @Nullable
        @Override
        public String validate(int hours, int minutes, String label, @Nullable String editingId) {
            for (CustomTimer existing : customTimers) {
                if (existing.id.equals(editingId)) continue;
                if (existing.hours == hours && existing.minutes == minutes) {
                    return getString(R.string.error_duplicate_timer);
                }
            }
            return null;
        }

        @Override
        public void onSave(@Nullable String editingId, int hours, int minutes, String label) {
            if (editingId == null) {
                customTimers.add(0 , (new CustomTimer(CustomTimerStore.newId(), label, hours, minutes,
                        System.currentTimeMillis(), false)));
            } else {
                for (CustomTimer existing : customTimers) {
                    if (existing.id.equals(editingId)) {
                        existing.hours = hours;
                        existing.minutes = minutes;
                        existing.label = label;
                        break;
                    }
                }
            }
            customTimerStore.save(customTimers);
            refreshCustomTimersList();
            customTimersRecycler.scrollToPosition(0);
        }
    };

    private void selectPreset(CustomTimer timer) {
        isApplyingPresetSelection = true;
        hourPicker.setText(String.valueOf(timer.hours));
        minutePicker.setText(String.valueOf(timer.minutes));
        isApplyingPresetSelection = false;
        customTimerAdapter.setSelectedId(timer.id);
        updatePreviewFromInputs();
    }

    private void clearPresetSelection() {
        if (customTimerAdapter != null && customTimerAdapter.getSelectedId() != null) {
            customTimerAdapter.setSelectedId(null);
        }
    }


    private void showTimerOptionsMenu(CustomTimer timer, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_timer_options, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                AddEditTimerBottomSheet sheet = AddEditTimerBottomSheet.newInstanceForEdit(timer);
                sheet.setCallback(timerSheetCallback);
                sheet.show(getSupportFragmentManager(), "edit_timer");
                return true;
            } else if (id == R.id.action_delete) {
                deleteTimer(timer);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private boolean durationExists(int hours, int minutes) {
        for (CustomTimer existing : customTimers) {
            if (existing.hours == hours && existing.minutes == minutes) return true;
        }
        return false;
    }

    private void deleteTimer(CustomTimer timer) {
        customTimers.removeIf(t -> t.id.equals(timer.id));
        if (timer.id.equals(customTimerAdapter.getSelectedId())) {
            customTimerAdapter.setSelectedId(null);
        }
        customTimerStore.save(customTimers);
        refreshCustomTimersList();
    }

    private void toggleFavourite(CustomTimer timer) {
        timer.isFavourite = !timer.isFavourite;
        customTimerStore.save(customTimers);
        reorderList();

        refreshCustomTimersList();

        String msg = timer.isFavourite ? "Added to Favourites" : "Removed from Favourites";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void reorderList() {
        customTimers.sort((t1, t2) -> {
            if (t1.isFavourite != t2.isFavourite) {
                return t1.isFavourite ? -1 : 1;
            }
            return Long.compare(t1.createdAt, t2.createdAt);
        });
        refreshCustomTimersList();
    }

    private void refreshCustomTimersList() {
        List<CustomTimer> snapshot = new ArrayList<>(customTimers);
        customTimerAdapter.submitList(snapshot);
        customTimersEmptyLabel.setVisibility(snapshot.isEmpty() ? View.VISIBLE : View.GONE);
        customTimersRecycler.setVisibility(snapshot.isEmpty() ? View.GONE : View.VISIBLE);
        updateAddButtonState();
    }

    private void updateAddButtonState() {
        boolean atMax = customTimers.size() >= CustomTimerStore.MAX_PRESETS;
        maxPresetsLabel.setVisibility(atMax && !uiRunning ? View.VISIBLE : View.GONE);
        addTimerButton.setEnabled(!uiRunning && !atMax);
    }
}
