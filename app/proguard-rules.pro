-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# DTOs applicatifs : le serializer synthétisé ne doit ni être renommé ni retiré.
-keep,includedescriptorclasses class com.footpredict.local.**$$serializer { *; }
-keepclassmembers class com.footpredict.local.** { *** Companion; }
-keepclasseswithmembers class com.footpredict.local.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
# Ktor / OkHttp : signatures génériques requises par la sérialisation.
-keepattributes Signature
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn androidx.room.paging.**
