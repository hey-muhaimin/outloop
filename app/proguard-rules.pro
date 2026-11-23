# Outloop R8 / ProGuard Rules

-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Android entry points registered in AndroidManifest.xml
-keep class com.example.FeedSurgeonApp { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.service.FeedSurgeonService { *; }
-keep class com.example.service.ProtectionActionReceiver { *; }
-keep class com.example.service.TransparentPauseDimOverlay { *; }
-keep class com.example.worker.RulesetSyncWorker { *; }

# Moshi reflection/codegen adapters & JSON DTOs
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
}
-keep class com.example.data.model.** { *; }
-keep class com.example.data.repository.OutloopBackupPayload { *; }
-keep class com.example.data.repository.BackupPreferencesDto { *; }
-keep class com.example.data.repository.BackupRuleDto { *; }
-keep class com.example.data.repository.BackupEventDto { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Room entities, converters, and database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class com.example.data.local.** { *; }

# Coroutines & OkHttp safe warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
