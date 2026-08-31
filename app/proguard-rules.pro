# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-dontwarn dagger.hilt.**

# Hilt-generated components
-keep class **_HiltComponents** { *; }
-keep class **_GeneratedInjector { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# Telecom / Telephony (system APIs)
-keep class android.telecom.** { *; }
-keep class android.telephony.** { *; }
-dontwarn android.telecom.**
-dontwarn android.telephony.**

# Keep call-related domain models (used by InCallService over Binder)
-keep class org.librelab.dialer.domain.model.** { *; }
-keep class org.librelab.dialer.data.incall.** { *; }

# Keep service classes (they are referenced from manifest)
-keep class org.librelab.dialer.service.** { *; }

# Coil
-dontwarn coil.**

# Our app entry points
-keep class org.librelab.dialer.LibreDialerApp { *; }
-keep class org.librelab.dialer.ui.MainActivity { *; }
-keep class org.librelab.dialer.ui.incall.InCallActivity { *; }
