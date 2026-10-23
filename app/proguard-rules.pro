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

# Line numbers in release stack traces. The mapping file (build/outputs/mapping/release/mapping.txt) turns
# them back into names; an app bundle carries it to Play for its crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
