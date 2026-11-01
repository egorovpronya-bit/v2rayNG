# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name of the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# ---------------------------------------------------------------------------
# App model classes serialized with Gson. Field names are part of the JSON
# wire format: server configs are persisted in MMKV as JSON and the V2Ray
# config JSON is handed to the native libv2ray core, so class and field
# names must survive obfuscation.
# ---------------------------------------------------------------------------
-keep class com.v2ray.ang.dto.** { *; }

# libv2ray (gomobile/JNI): its classes are called from Kotlin and the native
# core invokes CoreCallbackHandler implementations via JNI reflection, so
# callback method names must not be obfuscated.
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.CoreCallbackHandler { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MMKV
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ZXing (QR scanning via quickie-foss)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Quickie QR scanner
-keep class io.github.g00fy2.quickie.** { *; }

# Toasty
-keep class es.dmoral.toasty.** { *; }

# EditorKit (JSON editor in custom config screen)
-keep class com.blacksquircle.ui.** { *; }
