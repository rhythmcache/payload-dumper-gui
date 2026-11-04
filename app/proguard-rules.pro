# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable

# Preserve all native method declarations
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep PayloadDumper and its JNI callback interface
-keep class com.rhythmcache.payloaddumper.PayloadDumper {
    *;
}

-keep class com.rhythmcache.payloaddumper.PayloadDumper$ProgressCallback {
    *;
}

# Keep any classes (including anonymous ones) implementing ProgressCallback and their methods
-keep class * implements com.rhythmcache.payloaddumper.PayloadDumper$ProgressCallback {
    public *;
}

# Keep Kotlinx Serialization data classes and serializer methods
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class com.rhythmcache.payloaddumper.PayloadInfo {
    *;
}

-keepclassmembers class com.rhythmcache.payloaddumper.Partition {
    *;
}
