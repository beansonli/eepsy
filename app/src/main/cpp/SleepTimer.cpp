#include "SleepTimer.h"
#include <android/log.h>

#define LOG_TAG "SleepTimer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

SleepTimer::SleepTimer(BluetoothController* controller)
        : m_controller(controller),
          m_durationSeconds(0),
          m_remainingSeconds(0),
          m_state(TimerState::IDLE)
{}

void SleepTimer::setDuration(int seconds) {
    if (m_state != TimerState::IDLE) {
        LOGE("setDuration called while timer is not IDLE — ignoring");
        return;
    }
    if (seconds <= 0) {
        LOGE("setDuration: invalid duration %d", seconds);
        return;
    }
    m_durationSeconds = seconds;
    m_remainingSeconds = seconds;
    LOGI("Duration set to %d seconds", seconds);
}

void SleepTimer::start() {
    if (m_state != TimerState::IDLE) {
        LOGE("start() called but timer is not IDLE");
        return;
    }
    if (m_durationSeconds <= 0) {
        LOGE("start() called with no duration set");
        return;
    }
    m_state = TimerState::RUNNING;
    LOGI("Timer started — %d seconds remaining", m_remainingSeconds);
}

void SleepTimer::reset() {
    m_state = TimerState::IDLE;
    m_remainingSeconds = m_durationSeconds;
    LOGI("Timer reset");
}

void SleepTimer::tick() {
    if (m_state != TimerState::RUNNING) return;

    m_remainingSeconds--;
    LOGI("Tick — %d seconds remaining", m_remainingSeconds);

    if (m_remainingSeconds <= 0) {
        m_remainingSeconds = 0;
        m_state = TimerState::EXPIRED;
        onExpired();
    }
}

void SleepTimer::onExpired() {
    LOGI("Timer expired — triggering Bluetooth disconnect");

    if (m_controller == nullptr) {
        LOGE("BluetoothController is null — cannot disconnect");
        return;
    }

    bool disconnected = m_controller->disconnect();
    if (disconnected) {
        LOGI("Device disconnected successfully");
    } else {
        LOGE("disconnect() returned false — proceeding to disable adapter anyway");
    }

    bool disabled = m_controller->disableAdapter();
    if (disabled) {
        LOGI("Bluetooth adapter disabled");
    } else {
        LOGE("disableAdapter() returned false — user may need to confirm on device");
    }
}

TimerState SleepTimer::getState() const {
    return m_state;
}

int SleepTimer::getRemainingSeconds() const {
    return m_remainingSeconds;
}