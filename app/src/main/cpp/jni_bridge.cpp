#include <jni.h>
#include "SleepTimer.h"
#include <android/log.h>
#include <memory>

#define LOG_TAG "JNIBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


// Calls back into Java's SleepTimerService to perform the actual BT actions
class JniBluetoothController : public BluetoothController {
public:
    JniBluetoothController(JavaVM* jvm, jobject serviceObj)
            : m_jvm(jvm), m_serviceObj(serviceObj) {}

    bool disconnect() override {
        JNIEnv* env = getEnv();
        if (!env) return false;

        jclass cls = env->GetObjectClass(m_serviceObj);
        jmethodID mid = env->GetMethodID(cls, "javaDisconnect", "()Z");
        if (!mid) { LOGE("javaDisconnect method not found"); return false; }

        jboolean result = env->CallBooleanMethod(m_serviceObj, mid);
        env->DeleteLocalRef(cls);
        return result == JNI_TRUE;
    }

    bool disableAdapter() override {
        JNIEnv* env = getEnv();
        if (!env) return false;

        jclass cls = env->GetObjectClass(m_serviceObj);
        jmethodID mid = env->GetMethodID(cls, "javaDisableAdapter", "()Z");
        if (!mid) { LOGE("javaDisableAdapter method not found"); return false; }

        jboolean result = env->CallBooleanMethod(m_serviceObj, mid);
        env->DeleteLocalRef(cls);
        return result == JNI_TRUE;
    }

private:
    JavaVM* m_jvm;
    jobject m_serviceObj;

    JNIEnv* getEnv() {
        JNIEnv* env = nullptr;
        jint res = m_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            m_jvm->AttachCurrentThread(&env, nullptr);
        }
        return env;
    }
};

// Native state
static std::unique_ptr<JniBluetoothController> g_btController;
static std::unique_ptr<SleepTimer> g_timer;


extern "C" {

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeInit(JNIEnv* env, jobject thiz) {
JavaVM* jvm;
env->GetJavaVM(&jvm);

g_btController = std::make_unique<JniBluetoothController>(jvm, thiz);
g_timer = std::make_unique<SleepTimer>(g_btController.get());
LOGI("Native timer initialised");
}

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeSetDuration(JNIEnv*, jobject, jint seconds) {
if (g_timer) g_timer->setDuration(static_cast<int>(seconds));
}

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeStart(JNIEnv*, jobject) {
if (g_timer) g_timer->start();
}

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeReset(JNIEnv*, jobject) {
if (g_timer) g_timer->reset();
}

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeTick(JNIEnv*, jobject) {
if (g_timer) g_timer->tick();
}

// Returns: 0 = IDLE, 1 = RUNNING, 2 = EXPIRED
JNIEXPORT jint JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeGetState(JNIEnv*, jobject) {
    if (!g_timer) return 0;
    return static_cast<jint>(g_timer->getState());
}

JNIEXPORT jint JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeGetRemaining(JNIEnv*, jobject) {
    if (!g_timer) return 0;
    return static_cast<jint>(g_timer->getRemainingSeconds());
}

JNIEXPORT void JNICALL
Java_com_bt_eep_timer_SleepTimerService_nativeDestroy(JNIEnv*, jobject) {
g_timer.reset();
g_btController.reset();
LOGI("Native timer destroyed");
}

} // extern "C"