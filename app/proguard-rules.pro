# 保留 WebView 的 @JavascriptInterface 方法（MVP 未使用，占位）
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Kotlin 元数据
-keep class kotlin.Metadata { *; }
