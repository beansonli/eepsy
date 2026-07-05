package com.bt.eep_timer;

import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class SleepTimerService extends Service {
    private static final String TAG = "SleepTimerService";
    private static final String CHANNEL_ID = "sleep_timer_channel";
    private static final String PREFS_NAME = "SleepTimerPrefs";
    private static final String KEY_TARGET_MAC = "target_mac";
    private static final int NOTIFICATION_ID = 1;
    private static final long BLOCK_DURATION_MS = 30 * 60 * 1000;

    /** Callback so a bound UI (Activity) can reflect the single source of truth for the timer. */
    public interface TimerCallback {
        void onTick(int remainingSeconds, int totalDurationSeconds);
        void onCompleted();
    }

    /** Local binder - this service always runs in-process, so a plain object reference is fine. */
    public class LocalBinder extends Binder {
        SleepTimerService getService() {
            return SleepTimerService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    @Nullable
    private TimerCallback callback;

    private BluetoothAdapter bluetoothAdapter;
    private AudioManager audioManager;
    private PowerManager.WakeLock wakeLock;
    private Handler tickHandler;

    private boolean timerRunning = false;
    private int remainingSeconds = 0;
    private int totalDurationSeconds = 0;
    @Nullable
    private String activePresetId = null;
    private final Set<String> blockedDevices = new HashSet<>();

    // Event Watchdog
    private final BroadcastReceiver reconnectBlocker = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && blockedDevices.contains(device.getAddress())) {
                    Log.i(TAG, "Watchdog: Auto-reconnect blocked for " + device.getName());
                    disconnectDevice(device);
                }
            }
        }
    };

    // Lifecycle & Initialization
    @Override
    public void onCreate() {
        super.onCreate();
        bluetoothAdapter = ((BluetoothManager) getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        tickHandler = new Handler(Looper.getMainLooper());

        // Prevent Doze Mode from killing our countdown
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SleepTimer::ServiceWakeLock");

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if ("STOP".equals(intent.getAction())) {
            stopTimer();
            return START_NOT_STICKY;
        }

        // Persist target device MAC
        String mac = intent.getStringExtra("mac_address");
        if (mac != null)
            getSharedPreferences(PREFS_NAME, 0).edit().putString(KEY_TARGET_MAC, mac).apply();

        activePresetId = intent.getStringExtra("preset_id");

        startTimer(intent.getIntExtra("duration_seconds", 0));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ---- State accessors used by a (re)bound Activity, e.g. after a theme/config change ----
    public boolean isTimerRunning() {
        return timerRunning;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public int getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    @Nullable
    public String getActivePresetId() {
        return activePresetId;
    }

    public void setTimerCallback(@Nullable TimerCallback callback) {
        this.callback = callback;
    }

    // Core Timer Logic
    private void startTimer(int duration) {
        if (duration <= 0) return;
        remainingSeconds = duration;
        totalDurationSeconds = duration;
        timerRunning = true;
        wakeLock.acquire(BLOCK_DURATION_MS);
        tickHandler.post(tickRunnable);
        startForeground(NOTIFICATION_ID, buildNotification(remainingSeconds));
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;
            if (--remainingSeconds <= 0) {
                initiateShutdown();
            } else {
                updateNotification(remainingSeconds);
                if (callback != null) callback.onTick(remainingSeconds, totalDurationSeconds);
                tickHandler.postDelayed(this, 1000);
            }
        }
    };

    // Shutdown Sequence with Fade-out
    private void initiateShutdown() {
        timerRunning = false;
        if (callback != null) callback.onCompleted();
        Log.i(TAG, "Starting volume fade-out...");
        fadeVolume(5); // Start at volume step 5
    }

    // Recursive volume reduction
    private void fadeVolume(final int currentVolume) {
        if (currentVolume < 0) {
            // Fade complete, now perform the disconnect
            performFinalDisconnect();
            return;
        }

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

        // Schedule next step in 800ms
        tickHandler.postDelayed(() -> fadeVolume(currentVolume - 1), 800);
    }

    private void performFinalDisconnect() {
        // The timer has genuinely finished: pull down the "Timer Running" notification and drop
        // out of the foreground state immediately instead of waiting for the reconnect-blocker
        // window to close. Leaving the ongoing notification up made it look like the timer was
        // still running on several OEM builds.
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);

        // Best-effort: ask whatever app currently holds audio focus to pause, so media doesn't
        // keep playing through the phone speaker after the Bluetooth device disconnects.
        pauseActiveMedia();

        String targetMac = getSharedPreferences(PREFS_NAME, 0).getString(KEY_TARGET_MAC, "");
        boolean hasTarget = targetMac != null && !targetMac.isEmpty();

        try {
            registerReceiver(reconnectBlocker, new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
        } catch (Exception e) {
            Log.w(TAG, "Reconnect blocker was already registered", e);
        }

        int[] profiles = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
        for (int profile : profiles) {
            bluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int p, BluetoothProfile proxy) {
                    for (BluetoothDevice device : proxy.getConnectedDevices()) {
                        boolean shouldDisconnect = !hasTarget || device.getAddress().equals(targetMac);
                        if (shouldDisconnect) {
                            blockedDevices.add(device.getAddress());
                            disconnectDevice(proxy, device);
                        }
                    }
                    bluetoothAdapter.closeProfileProxy(p, proxy);
                }

                @Override
                public void onServiceDisconnected(int p) {
                }
            }, profile);
        }

        // Cleanup after block duration
        tickHandler.postDelayed(this::stopTimer, BLOCK_DURATION_MS);
    }

    /**
     * Sends a synthetic media-button pause event through AudioManager. This reaches whichever
     * app currently holds an active MediaSession (Netflix, YouTube, Spotify, Apple Music,
     * Prime Video, VLC, etc.) without requiring notification-listener access, since it rides on
     * the same mechanism used by real Bluetooth headset buttons.
     */
    private void pauseActiveMedia() {
        try {
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0);
            KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0);
            audioManager.dispatchMediaKeyEvent(down);
            audioManager.dispatchMediaKeyEvent(up);
            Log.i(TAG, "Dispatched media pause command");
        } catch (Exception e) {
            Log.w(TAG, "Failed to dispatch media pause command", e);
        }
    }

    private void disconnectDevice(BluetoothProfile proxy, BluetoothDevice device) {
        try {
            Method m = proxy.getClass().getMethod("disconnect", BluetoothDevice.class);
            m.setAccessible(true);
            m.invoke(proxy, device);
            Log.i(TAG, "Disconnect requested for " + device.getName());
        } catch (Exception e) {
            Log.e(TAG, "Profile disconnect failed", e);
        }
    }


    private void disconnectDevice(BluetoothDevice device) {
        try {
            Method m = BluetoothDevice.class.getMethod("disconnect");
            m.setAccessible(true);
            m.invoke(device);
            Log.i(TAG, "Reconnect watchdog disconnect requested for " + device.getName());
        } catch (Exception e) {
            Log.e(TAG, "Watchdog disconnect failed", e);
        }
    }

    private void stopTimer() {
        try {
            unregisterReceiver(reconnectBlocker);
        } catch (Exception ignored) {
        }
        if (wakeLock.isHeld()) wakeLock.release();
        timerRunning = false;
        activePresetId = null;
        stopForeground(Service.STOP_FOREGROUND_REMOVE);
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        tickHandler.removeCallbacks(tickRunnable);
        callback = null;
        super.onDestroy();
    }

    // Helpers (UI/Notification)
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Sleep Timer", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(int s) {
        PendingIntent stopIntent = PendingIntent.getService(this, 0,
                new Intent(this, SleepTimerService.class).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Timer Running").setContentText(String.format("%02d:%02d", s / 60, s % 60))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).addAction(0, "Cancel", stopIntent)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(int s) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(s));
    }
}
