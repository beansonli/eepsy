# Add project specific ProGuard rules here.
# For NDK/JNI — keep all native method declarations so ProGuard doesn't strip them
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep SleepTimerService so the JNI bridge can call back into it
-keep class com.bt.eep_timer.SleepTimerService { *; }