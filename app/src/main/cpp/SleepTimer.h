#pragma once
#include <string>

// Abstract interface — Java implements this via JNI callback
class BluetoothController {
public:
    virtual bool disconnect() = 0;   // disconnect the target device
    virtual bool disableAdapter() = 0; // turn off BT adapter entirely
    virtual ~BluetoothController() = default;
};

enum class TimerState {
    IDLE,
    RUNNING,
    EXPIRED
};

class SleepTimer {
public:
    explicit SleepTimer(BluetoothController* controller);

    void setDuration(int seconds);
    void start();
    void reset();
    void tick();  // call every second from Java Handler

    TimerState getState() const;
    int getRemainingSeconds() const;

private:
    BluetoothController* m_controller;
    int m_durationSeconds;
    int m_remainingSeconds;
    TimerState m_state;

    void onExpired();
};