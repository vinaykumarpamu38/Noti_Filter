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

# Phase 4 - Google API Client / Drive: these libraries use reflection for
# JSON (de)serialization of model classes (com.google.api.services.drive.model.*).
# Without these rules, a release/minified build will fail at runtime with
# obscure errors when parsing Drive API responses - this has nothing to do
# with your own app logic, it's purely about keeping the API client's
# reflection targets intact.
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.api.client.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient