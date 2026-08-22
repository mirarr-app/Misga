# R8 rules for Misga (release minification enabled).

# --- kotlinx.serialization ---
# Official rules: https://github.com/Kotlin/kotlinx.serialization#android
-keepattributes *Annotation*, InnerClasses

-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.miss.ga.**$$serializer { *; }
-keepclassmembers class com.miss.ga.** {
    *** Companion;
}
-keepclasseswithmembers class com.miss.ga.** {
    kotlinx.serialization.KSerializer serializer(...);
}
