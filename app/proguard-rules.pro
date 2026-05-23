# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.shuli.reader.core.database.entity.** { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.shuli.reader.**$$serializer { *; }
-keepclassmembers class com.shuli.reader.** {
    *** Companion;
}
-keepclasseswithmembers class com.shuli.reader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
