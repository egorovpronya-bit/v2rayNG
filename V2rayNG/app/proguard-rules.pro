# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# The gomobile-generated Go<->Kotlin bridge (go.Seq) calls into these classes via
# reflection from native code, not from any Kotlin call site R8 can see — without
# this, R8 strips/renames CoreCallback's startup/shutdown/onEmitStatus and the
# native core can never call back, so the tunnel silently fails to start.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.CoreCallbackHandler { *; }

# Gson reflects on field names to (de)serialize JSON; our dto/config classes
# mostly rely on matching Kotlin property names instead of @SerializedName,
# so R8 must not rename or strip them.
-keepattributes Signature,*Annotation*
-keep class com.v2ray.ang.dto.** { <fields>; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}