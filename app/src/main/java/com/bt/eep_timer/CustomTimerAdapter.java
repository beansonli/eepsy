package com.bt.eep_timer;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

class CustomTimerAdapter extends RecyclerView.Adapter<CustomTimerAdapter.TimerViewHolder> {

    interface Listener {
        void onTimerSelected(CustomTimer timer);
        void onTimerLongPressed(CustomTimer timer, View anchor);
    }

    private List<CustomTimer> items = new ArrayList<>();
    private final Listener listener;
    private String selectedId = null;
    private boolean interactionEnabled = true;
    private String activeTimerId = null;

    CustomTimerAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submitList(List<CustomTimer> newItems) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return items.size(); }

            @Override
            public int getNewListSize() { return newItems.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return items.get(oldItemPosition).id.equals(newItems.get(newItemPosition).id);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CustomTimer oldItem = items.get(oldItemPosition);
                CustomTimer newItem = newItems.get(newItemPosition);
                return oldItem.hours == newItem.hours &&
                        oldItem.minutes == newItem.minutes &&
                        Objects.equals(oldItem.label, newItem.label);
            }
        });

        items = new ArrayList<>(newItems);
        diffResult.dispatchUpdatesTo(this);
    }

    public List<CustomTimer> getItems() {
        return new ArrayList<>(items);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void moveItem(int from, int to) {
        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(items, i, i - 1);
            }
        }

        notifyItemMoved(from, to);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).id.hashCode();
    }

    void setSelectedId(@Nullable String id) {
        if (Objects.equals(selectedId, id)) return;
        selectedId = id;
        notifyDataSetChanged();
    }

    @Nullable
    String getSelectedId() {
        return selectedId;
    }

    public void setActiveTimerId(@Nullable String id) {
        if (Objects.equals(activeTimerId, id)) return;
        activeTimerId = id;
        notifyDataSetChanged();
    }

    void setInteractionEnabled(boolean enabled) {
        if (interactionEnabled == enabled) return;
        interactionEnabled = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_custom_timer, parent, false);
        return new TimerViewHolder(view);
    }

    private int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
    @Override
    public void onBindViewHolder(@NonNull TimerViewHolder holder, int position) {
        CustomTimer timer = items.get(position);
        boolean isSelected = timer.id.equals(selectedId);

        // 1. Data Binding (Text, Icons, Listeners)
        holder.bind(timer, isSelected, interactionEnabled, listener);

        // 2. Visual Styling (Theme-aware Highlighting & States)
        if (holder.itemView instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) holder.itemView;
            Context ctx = card.getContext();
            boolean isActive = timer.id.equals(activeTimerId);

            // A. Highlight Active Timer (Uses Primary Color)
            if (isActive) {
                card.setStrokeColor(resolveThemeColor(ctx, com.google.android.material.R.attr.colorPrimary));
                card.setStrokeWidth((int) (2 * ctx.getResources().getDisplayMetrics().density));
            } else {
                card.setStrokeColor(Color.TRANSPARENT);
                card.setStrokeWidth(0);
            }

            // B. Theme-Aware Background Logic
            // colorSurfaceContainer: Used for normal states (Standard)
            // colorSurface: Used for disabled states (Darker/Muted)
            int backgroundColor = interactionEnabled
                    ? resolveThemeColor(ctx, com.google.android.material.R.attr.colorSurfaceContainer)
                    : resolveThemeColor(ctx, com.google.android.material.R.attr.colorSurface);

            card.setCardBackgroundColor(backgroundColor);
        }
    }
    static class TimerViewHolder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView durationText;
        private final TextView labelText;
        private final RadioButton selector;

        TimerViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.presetIcon);
            durationText = itemView.findViewById(R.id.presetDuration);
            labelText = itemView.findViewById(R.id.presetLabel);
            selector = itemView.findViewById(R.id.presetSelector);
        }

        void bind(CustomTimer timer, boolean selected, boolean enabled, Listener listener) {
            String duration = formatDuration(timer);
            durationText.setText(duration);

            boolean hasCustomLabel = timer.label != null && !timer.label.trim().isEmpty();
            if (hasCustomLabel) {
                labelText.setText(timer.label.trim());
                durationText.setVisibility(View.VISIBLE);
            } else {
                labelText.setText(duration);
                durationText.setVisibility(View.GONE);
            }

            icon.setImageResource(iconFor(timer.label));
            selector.setChecked(selected);
            itemView.setEnabled(enabled);

            itemView.setOnClickListener(v -> {
                if (enabled) listener.onTimerSelected(timer);
            });
            itemView.setOnLongClickListener(v -> {
                if (!enabled) return false;
                listener.onTimerLongPressed(timer, v);
                return true;
            });
        }

        private static String formatDuration(CustomTimer timer) {
            int h = timer.hours;
            int m = timer.minutes;
            if (h > 0 && m > 0) return String.format(Locale.US, "%d hr %d min", h, m);
            if (h > 0) return String.format(Locale.US, "%d hr", h);
            return String.format(Locale.US, "%d min", m);
        }

        private static int iconFor(@Nullable String label) {
            if (label == null) return R.drawable.ic_moon;
            String l = label.toLowerCase(Locale.US);
            if (l.contains("read") || l.contains("book")) return R.drawable.ic_book;
            if (l.contains("podcast") || l.contains("music") || l.contains("audio")) return R.drawable.ic_headphones;
            return R.drawable.ic_moon;
        }
    }
}