# 代码压缩规则
-keep class com.diary.app.data.** { *; }
-keep class com.diary.app.data.DiaryEntry
-keep class com.diary.app.data.DiaryAttachment
-keep class com.diary.app.data.DiaryLocation
-keep class com.diary.app.data.AttachmentType

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# JGit
-dontwarn org.bouncycastle.**
-keep class org.eclipse.jgit.** { *; }
-keep class org.bouncycastle.** { *; }

# Joda Time
-dontwarn org.joda.**
-keep class org.joda.time.** { *; }
